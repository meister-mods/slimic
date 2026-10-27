package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.BrokenSlimeBlockEntity;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.*;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.*;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EnderBoundaryGameTests {
  private static ServerPlayer player(ServerLevel level) {
    var actor =
        new ServerPlayer(
            level.getServer(), level, new GameProfile(UUID.randomUUID(), "EnderBoundary"));
    var connection =
        new Connection(PacketFlow.SERVERBOUND) {
          @Override
          public void send(Packet<?> packet) {}
        };
    actor.connection = new ServerGamePacketListenerImpl(level.getServer(), connection, actor);
    actor.setGameMode(GameType.SURVIVAL);
    return actor;
  }

  private static EnderSlime anchor(
      GameTestHelper h, LoadedWorkArea area, ServerPlayer actor, int pearls) {
    var slime = ModEntities.ENDER_SLIME.get().create(h.getLevel());
    slime.moveTo(area.point(8, 101, 8));
    slime.tame(actor);
    slime.setNoAi(true);
    slime.setEquipment(0, new ItemStack(Items.ENDER_PEARL, pearls));
    h.getLevel().addFreshEntity(slime);
    slime.onReleased();
    actor.moveTo(slime.position().add(1, 0, 0));
    h.assertTrue(slime.bind(actor), "A live test anchor binds");
    slime.setTunedVitals(7, slime.getMaxStamina(), 11);
    return slime;
  }

  private static int tickets(ServerLevel level) {
    var data = level.getDataStorage().get(ForcedChunksSavedData::load, "chunks");
    return data == null ? 0 : data.getEntityForcedChunks().getTickingChunks().size();
  }

  @GameTest(template = "empty", batch = "ender_boundaries")
  public static void eventCannotChangeTheDepartureDimensionToBypassTheSeal(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var actor = player(h.getLevel());
    var slime = anchor(h, a, actor, 3);
    var nether = h.getLevel().getServer().getLevel(Level.NETHER);
    var point = BlockPos.containing(a.point(8, 201, 8));
    var chunk = new ChunkPos(point);
    nether.setChunkForced(chunk.x, chunk.z, true);
    nether.getChunk(chunk.x, chunk.z);
    nether.setBlock(point.below(), Blocks.BEDROCK.defaultBlockState(), 2);
    boolean[] redirected = {false};
    Consumer<EntityTeleportEvent> redirect =
        event -> {
          if (event.getEntity() == actor && !redirected[0]) {
            redirected[0] = true;
            actor.teleportTo(nether, point.getX() + .5, point.getY(), point.getZ() + .5, 0, 0);
          }
        };
    MinecraftForge.EVENT_BUS.addListener(redirect);
    try {
      boolean moved = SlimeTeleporterItem.teleport(actor, SlimeTeleporterItem.bound(slime));
      h.assertTrue(
          redirected[0]
              && !moved
              && actor.serverLevel() == nether
              && slime.getStamina() == slime.getMaxStamina()
              && slime.getEquipment(0).getCount() == 3,
          "A dimension change during a same-dimension request must cancel it without charging or"
              + " bypassing the missing seal");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(redirect);
      actor.serverLevel().removePlayerImmediately(actor, Entity.RemovalReason.DISCARDED);
      slime.discard();
      nether.setChunkForced(chunk.x, chunk.z, false);
      a.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ender_boundaries")
  public static void liveNbtUnpinReleasesThePreviouslyTrackedTicket(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var actor = player(h.getLevel());
    var slime = anchor(h, a, actor, 3);
    try {
      int before = tickets(h.getLevel());
      var data = slime.saveWithoutId(new CompoundTag());
      data.putBoolean("EnderPinned", false);
      slime.load(data);
      slime.tick();
      h.assertTrue(
          !slime.isPinned() && !slime.isNoGravity() && tickets(h.getLevel()) == before - 1,
          "Reloading an existing live anchor as unpinned releases its ticket and pinned gravity"
              + " state");
      h.assertTrue(
          slime.getHealth() == 7
              && slime.getStatPoints() == 11
              && slime.getEquipment(0).getCount() == 3,
          "Reload keeps its life, growth and equipment");
    } finally {
      slime.discard();
      a.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ender_boundaries")
  public static void liveNbtPearlRemovalRevokesPersistedLoadingPermission(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var actor = player(h.getLevel());
    var slime = anchor(h, a, actor, 3);
    try {
      int before = tickets(h.getLevel());
      var data = slime.saveWithoutId(new CompoundTag());
      data.put("EnderPearls", new CompoundTag());
      slime.load(data);
      h.assertTrue(
          slime.isPinned()
              && slime.getEquipment(0).isEmpty()
              && tickets(h.getLevel()) == before - 1
              && !CompanionRegistry.get(h.getLevel()).find(slime.getUUID()).chunkLoading(),
          "Live equipment reload immediately revokes the ticket and its saved permission");
    } finally {
      slime.discard();
      a.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ender_boundaries")
  public static void lossOfIndependentLoadingDuringTheEventChargesOnePearl(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var actor = player(h.getLevel());
    var slime = anchor(h, a, actor, 3);
    Consumer<EntityTeleportEvent> remove =
        e -> {
          if (e.getEntity() == actor) a.close();
        };
    MinecraftForge.EVENT_BUS.addListener(remove);
    try {
      h.assertTrue(
          SlimeTeleporterItem.teleport(actor, SlimeTeleporterItem.bound(slime))
              && slime.getEquipment(0).getCount() == 2,
          "A loader removed by a teleport callback makes the still-valid self-loaded trip cost"
              + " exactly one pearl");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(remove);
      slime.discard();
      a.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ender_boundaries")
  public static void independentLoadingAddedDuringTheEventWaivesThePearl(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var actor = player(h.getLevel());
    var slime = anchor(h, a, actor, 3);
    var chunk = slime.chunkPosition();
    a.close();
    Consumer<EntityTeleportEvent> add =
        e -> {
          if (e.getEntity() == actor) h.getLevel().setChunkForced(chunk.x, chunk.z, true);
        };
    MinecraftForge.EVENT_BUS.addListener(add);
    try {
      h.assertTrue(
          SlimeTeleporterItem.teleport(actor, SlimeTeleporterItem.bound(slime))
              && slime.getEquipment(0).getCount() == 3,
          "A loader established before arrival waives the pearl cost");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(add);
      slime.discard();
      h.getLevel().setChunkForced(chunk.x, chunk.z, false);
      a.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ender_boundaries")
  public static void spectatorExemptionRequiresTheChunkGenerationGamerule(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var actor = player(h.getLevel());
    var slime = anchor(h, a, actor, 3);
    var spectator = player(h.getLevel());
    var rule = h.getLevel().getGameRules().getRule(GameRules.RULE_SPECTATORSGENERATECHUNKS);
    boolean previous = rule.get();
    rule.set(false, h.getLevel().getServer());
    spectator.setGameMode(GameType.SPECTATOR);
    spectator.moveTo(slime.position().add(2, 0, 0));
    h.getLevel().addNewPlayer(spectator);
    a.close();
    try {
      h.assertTrue(
          !EnderChunkLoading.independentlyLoaded(
              h.getLevel(), slime.chunkPosition(), slime.getUUID()),
          "An ignored spectator does not keep the anchor loaded");
      h.assertTrue(
          SlimeTeleporterItem.teleport(actor, SlimeTeleporterItem.bound(slime))
              && slime.getEquipment(0).getCount() == 2,
          "Ignored spectators do not waive pearl costs");
      rule.set(true, h.getLevel().getServer());
      h.getLevel().getChunkSource().move(spectator);
      slime.setTunedVitals(7, slime.getMaxStamina(), 11);
      h.assertTrue(
          SlimeTeleporterItem.teleport(actor, SlimeTeleporterItem.bound(slime))
              && slime.getEquipment(0).getCount() == 2,
          "A spectator that generates chunks can waive the cost");
    } finally {
      h.getLevel().removePlayerImmediately(spectator, Entity.RemovalReason.DISCARDED);
      rule.set(previous, h.getLevel().getServer());
      slime.discard();
      a.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ender_boundaries")
  public static void splittingTheLastPearlThroughTheMenuReleasesLoading(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var actor = player(h.getLevel());
    var slime = anchor(h, a, actor, 1);
    actor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
    var menu = new SlimeMenu(4, actor.getInventory(), slime);
    actor.containerMenu = menu;
    try {
      int before = tickets(h.getLevel());
      menu.clicked(0, 1, ClickType.PICKUP, actor);
      h.assertTrue(
          menu.getCarried().is(Items.ENDER_PEARL)
              && menu.getCarried().getCount() == 1
              && slime.getEquipment(0).isEmpty()
              && tickets(h.getLevel()) == before - 1,
          "Native right-click extraction of the final pearl updates both ownership and the chunk"
              + " ticket");
      menu.clicked(0, 0, ClickType.PICKUP, actor);
      h.assertTrue(
          menu.getCarried().isEmpty()
              && slime.getEquipment(0).getCount() == 1
              && tickets(h.getLevel()) == before,
          "Putting the same pearl back restores exactly one ticket");
    } finally {
      slime.discard();
      a.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ender_boundaries")
  public static void collapseAndRevivalRetainPearlsSealAndIdentity(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var actor = player(h.getLevel());
    var slime = anchor(h, a, actor, 7);
    var id = slime.getUUID();
    slime.setEquipment(1, new ItemStack(ModItems.DIMENSIONAL_ATTUNEMENT_SEAL.get()));
    var pos = slime.blockPosition();
    try {
      int before = tickets(h.getLevel());
      slime.hurt(h.getLevel().damageSources().generic(), 10000);
      h.assertTrue(
          h.getLevel().getBlockEntity(pos) instanceof BrokenSlimeBlockEntity
              && tickets(h.getLevel()) == before - 1,
          "Collapse stores the anchor and releases its live ticket");
      var broken = (BrokenSlimeBlockEntity) h.getLevel().getBlockEntity(pos);
      var stored = broken.getSlime();
      var data = stored.getTag().getCompound("SlimeData");
      h.assertTrue(
          ItemStack.of(data.getCompound("EnderPearls")).getCount() == 7
              && ItemStack.of(data.getCompound("EnderDimensionalSeal"))
                  .is(ModItems.DIMENSIONAL_ATTUNEMENT_SEAL.get()),
          "Both equipment stacks live in the broken companion snapshot");
      h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
      h.assertTrue(
          ((FriendlySlimeItem) stored.getItem()).revive(h.getLevel(), actor, stored, pos),
          "The preserved snapshot revives");
      var restored = (EnderSlime) h.getLevel().getEntity(id);
      h.assertTrue(
          restored != null
              && restored.getHealth() == 1
              && restored.isOrderedToSit()
              && restored.isPinned()
              && restored.getEquipment(0).getCount() == 7
              && restored.allowsDimensionalTravel()
              && tickets(h.getLevel()) == before,
          "Revival keeps identity, waiting pin and both stacks, revives at one health and restores"
              + " one ticket");
    } finally {
      var restored = h.getLevel().getEntity(id);
      if (restored != null) restored.discard();
      slime.discard();
      h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
      a.close();
    }
    h.succeed();
  }
}
