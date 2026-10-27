package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.*;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.recipe.GuardianAngelSlimeRecipe;
import io.github.meistermods.slimic.registry.*;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EnderEquipmentGameTests {
  private static ServerPlayer owner(ServerLevel level) {
    var id = UUID.randomUUID();
    var player = new ServerPlayer(level.getServer(), level, new GameProfile(id, "EnderTest"));
    var connection =
        new Connection(PacketFlow.SERVERBOUND) {
          @Override
          public void send(Packet<?> packet) {}
        };
    player.connection = new ServerGamePacketListenerImpl(level.getServer(), connection, player);
    player.setGameMode(GameType.SURVIVAL);
    return player;
  }

  private static EnderSlime anchor(
      GameTestHelper h, LoadedWorkArea area, ServerPlayer owner, int pearls) {
    var slime = ModEntities.ENDER_SLIME.get().create(h.getLevel());
    slime.tame(owner);
    slime.moveTo(area.point(8, 101, 8));
    slime.setNoAi(true);
    slime.setEquipment(0, new ItemStack(Items.ENDER_PEARL, pearls));
    h.assertTrue(
        CompanionRegistry.get(h.getLevel()).find(slime.getUUID()) == null,
        "Equipment on an unadmitted entity must not register a world companion");
    h.getLevel().addFreshEntity(slime);
    owner.moveTo(slime.position().add(2, 0, 0));
    h.assertTrue(slime.bind(owner), "Anchor links at full stamina");
    slime.setTunedVitals(slime.getHealth(), slime.getMaxStamina(), 0);
    owner.moveTo(area.point(2, 101, 2));
    return slime;
  }

  private static int tickets(ServerLevel level) {
    var data = level.getDataStorage().get(ForcedChunksSavedData::load, "chunks");
    return data == null ? 0 : data.getEntityForcedChunks().getTickingChunks().size();
  }

  @GameTest(template = "empty", batch = "ender_equipment")
  public static void guardianRecipeRequiresHealingAndPreservesItsState(GameTestHelper h) {
    var healer = ModEntities.HEALING_SLIME.get().create(h.getLevel());
    healer.tame(h.makeMockSurvivalPlayer());
    healer.setHealth(7);
    healer.setTunedVitals(7, 63, 11);
    var input = FriendlySlimeItem.fromSlime(healer);
    var original = input.copy();
    var grid =
        new TransientCraftingContainer(
            new AbstractContainerMenu(null, 0) {
              @Override
              public boolean stillValid(Player p) {
                return true;
              }

              @Override
              public ItemStack quickMoveStack(Player p, int i) {
                return ItemStack.EMPTY;
              }
            },
            2,
            2);
    grid.setItem(0, input);
    grid.setItem(1, new ItemStack(Items.NETHER_STAR));
    var totem = new ItemStack(Items.TOTEM_OF_UNDYING);
    totem.getOrCreateTag().putString("ReserveMark", "kept");
    grid.setItem(2, totem);
    var recipe =
        new GuardianAngelSlimeRecipe(
            ResourceLocation.fromNamespaceAndPath("slimic", "healing_test"),
            CraftingBookCategory.MISC);
    h.assertTrue(recipe.matches(grid, h.getLevel()), "Healing is the required base");
    var result = recipe.assemble(grid, h.getLevel().registryAccess());
    var data = result.getTag().getCompound("SlimeData");
    h.assertTrue(
        result.is(ModItems.GUARDIAN_ANGEL_SLIME.get())
            && data.getUUID("UUID").equals(healer.getUUID())
            && data.getFloat("Health") == 7
            && data.getLong("StatPoints") == 11
            && ItemStack.matches(input, original)
            && ItemStack.of(result.getTag().getCompound("InitialGuardianTotem"))
                .getTag()
                .getString("ReserveMark")
                .equals("kept"),
        "Identity, current health, points, exact totem and source item survive specialization");
    for (var rejected :
        new Item[] {ModItems.FRIENDLY_SLIME.get(), ModItems.ATTENTION_SLIME.get()}) {
      grid.setItem(0, new ItemStack(rejected));
      h.assertTrue(
          !recipe.matches(grid, h.getLevel())
              && recipe.assemble(grid, h.getLevel().registryAccess()).isEmpty(),
          "Old bases do not craft a guardian");
    }
    healer.getBag().setStackInSlot(0, new ItemStack(Items.SLIME_BALL, 3));
    var loaded = FriendlySlimeItem.fromSlime(healer);
    var loadedCopy = loaded.copy();
    grid.setItem(0, loaded);
    h.assertTrue(
        !recipe.matches(grid, h.getLevel())
            && recipe.assemble(grid, h.getLevel().registryAccess()).isEmpty()
            && ItemStack.matches(loaded, loadedCopy),
        "A loaded healer cannot lose its supplies by specializing into a bagless guardian");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ender_equipment")
  public static void equipmentMenuAcceptsFullPearlStackAndOneSeal(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var actor = owner(h.getLevel());
    var slime = anchor(h, area, actor, 0);
    try {
      actor.moveTo(slime.position().add(1, 0, 0));
      actor.setItemInHand(
          net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
      var menu = new SlimeMenu(7, actor.getInventory(), slime);
      h.assertTrue(
          menu.slots.get(0).getMaxStackSize() == 16
              && menu.slots.get(1).getMaxStackSize() == 1
              && menu.slots.get(0).mayPlace(new ItemStack(Items.ENDER_PEARL))
              && !menu.slots.get(0).mayPlace(new ItemStack(Items.ENDER_EYE))
              && menu.slots
                  .get(1)
                  .mayPlace(new ItemStack(ModItems.DIMENSIONAL_ATTUNEMENT_SEAL.get())),
          "Typed slots expose correct stack limits");
      actor.getInventory().setItem(9, new ItemStack(Items.ENDER_PEARL, 16));
      menu.quickMoveStack(actor, 2);
      h.assertTrue(
          slime.getEquipment(0).getCount() == 16 && slime.isChunkLoading(),
          "Shift insertion fills the pearl slot and starts loading");
      menu.quickMoveStack(actor, 0);
      h.assertTrue(
          slime.getEquipment(0).isEmpty() && !slime.isChunkLoading(),
          "Shift removal stops loading immediately");
    } finally {
      slime.discard();
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ender_equipment")
  public static void recoveryPreservesBothSlotsAndDeploysWaiting(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var actor = owner(h.getLevel());
    var slime = anchor(h, area, actor, 7);
    slime.setEquipment(1, new ItemStack(ModItems.DIMENSIONAL_ATTUNEMENT_SEAL.get()));
    var id = slime.getUUID();
    var before = tickets(h.getLevel());
    try {
      actor.moveTo(slime.position().add(1, 0, 0));
      h.assertTrue(
          slime.capture(actor).consumesAction() && tickets(h.getLevel()) == before - 1,
          "Recovery releases its ticket");
      ItemStack stored = ItemStack.EMPTY;
      for (var item : actor.getInventory().items)
        if (item.is(ModItems.ENDER_SLIME.get())) stored = item;
      h.assertTrue(!stored.isEmpty(), "Captured item exists");
      h.assertTrue(
          ((FriendlySlimeItem) stored.getItem())
              .revive(h.getLevel(), actor, stored, BlockPos.containing(area.point(9, 101, 9))),
          "Redeployment succeeds");
      var restored = (EnderSlime) h.getLevel().getEntity(id);
      h.assertTrue(
          restored != null
              && restored.isOrderedToSit()
              && restored.isPinned()
              && restored.getEquipment(0).getCount() == 7
              && restored.allowsDimensionalTravel()
              && CompanionRegistry.get(h.getLevel()).find(id).chunkLoading(),
          "Both slots, pinning and identity survive, with Wait as the command");
      restored.discard();
    } finally {
      slime.discard();
      var restored = h.getLevel().getEntity(id);
      if (restored != null) restored.discard();
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ender_equipment")
  public static void soleAnchorTravelConsumesLastPearlAndReleasesTicket(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var actor = owner(h.getLevel());
    var slime = anchor(h, area, actor, 1);
    area.close();
    try {
      h.assertTrue(
          !EnderChunkLoading.independentlyLoaded(
              h.getLevel(), slime.chunkPosition(), slime.getUUID()),
          "Fixture has only the anchor loader");
      int before = tickets(h.getLevel());
      h.assertTrue(
          SlimeTeleporterItem.teleport(actor, SlimeTeleporterItem.bound(slime)),
          "Own-loaded destination accepts travel");
      h.assertTrue(
          slime.getEquipment(0).isEmpty()
              && slime.getStamina() == 0
              && tickets(h.getLevel()) == before - 1
              && !CompanionRegistry.get(h.getLevel()).find(slime.getUUID()).chunkLoading(),
          "Successful travel spends exactly the last pearl and ends its persistent loading");
    } finally {
      slime.discard();
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ender_equipment")
  public static void vanillaForcedDestinationDoesNotConsumePearls(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var actor = owner(h.getLevel());
    var slime = anchor(h, area, actor, 3);
    try {
      h.assertTrue(
          SlimeTeleporterItem.teleport(actor, SlimeTeleporterItem.bound(slime))
              && slime.getEquipment(0).getCount() == 3,
          "Independent forced chunks waive pearl use");
      slime.setEquipment(0, ItemStack.EMPTY);
      slime.setTunedVitals(slime.getHealth(), slime.getMaxStamina(), 0);
      h.assertTrue(
          SlimeTeleporterItem.teleport(actor, SlimeTeleporterItem.bound(slime)),
          "An externally loaded anchor works without pearls");
    } finally {
      slime.discard();
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ender_equipment")
  public static void forgeBlockAndOtherEntityLoadersWaivePearlCost(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var actor = owner(h.getLevel());
    var slime = anchor(h, area, actor, 3);
    var pos = slime.blockPosition().below();
    var chunk = slime.chunkPosition();
    var other = UUID.randomUUID();
    area.close();
    try {
      ForgeChunkManager.forceChunk(h.getLevel(), Slimic.MOD_ID, pos, chunk.x, chunk.z, true, true);
      h.assertTrue(
          SlimeTeleporterItem.teleport(actor, SlimeTeleporterItem.bound(slime))
              && slime.getEquipment(0).getCount() == 3,
          "A block chunk loader waives cost");
      ForgeChunkManager.forceChunk(h.getLevel(), Slimic.MOD_ID, pos, chunk.x, chunk.z, false, true);
      ForgeChunkManager.forceChunk(
          h.getLevel(), Slimic.MOD_ID, other, chunk.x + 1, chunk.z, true, true);
      slime.setTunedVitals(slime.getHealth(), slime.getMaxStamina(), 0);
      h.assertTrue(
          SlimeTeleporterItem.teleport(actor, SlimeTeleporterItem.bound(slime))
              && slime.getEquipment(0).getCount() == 3,
          "Another entity's neighboring ticket also maintains the destination");
    } finally {
      ForgeChunkManager.forceChunk(h.getLevel(), Slimic.MOD_ID, pos, chunk.x, chunk.z, false, true);
      ForgeChunkManager.forceChunk(
          h.getLevel(), Slimic.MOD_ID, other, chunk.x + 1, chunk.z, false, true);
      slime.discard();
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ender_equipment")
  public static void nearbyTrackingPlayerWaivesPearlCost(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var actor = owner(h.getLevel());
    var slime = anchor(h, area, actor, 3);
    var nearby = owner(h.getLevel());
    nearby.moveTo(slime.position().add(3, 0, 0));
    h.getLevel().addNewPlayer(nearby);
    area.close();
    try {
      h.assertTrue(
          EnderChunkLoading.independentlyLoaded(
              h.getLevel(), slime.chunkPosition(), slime.getUUID()),
          "A native tracking player maintains the destination");
      h.assertTrue(
          SlimeTeleporterItem.teleport(actor, SlimeTeleporterItem.bound(slime))
              && slime.getEquipment(0).getCount() == 3,
          "Player-loaded travel is free of pearl cost");
    } finally {
      h.getLevel().removePlayerImmediately(nearby, Entity.RemovalReason.DISCARDED);
      slime.discard();
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ender_equipment")
  public static void cancelledOrInvalidatedTravelKeepsPearlsAndStamina(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var actor = owner(h.getLevel());
    var slime = anchor(h, area, actor, 3);
    area.close();
    var before = actor.position();
    Consumer<EntityTeleportEvent> cancel =
        e -> {
          if (e.getEntity() == actor) e.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(cancel);
    try {
      h.assertTrue(
          !SlimeTeleporterItem.teleport(actor, SlimeTeleporterItem.bound(slime))
              && slime.getEquipment(0).getCount() == 3
              && slime.getStamina() == slime.getMaxStamina()
              && actor.position().equals(before),
          "Cancelled travel charges neither resource");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(cancel);
    }
    Consumer<EntityTeleportEvent> remove =
        e -> {
          if (e.getEntity() == actor) slime.setEquipment(0, ItemStack.EMPTY);
        };
    MinecraftForge.EVENT_BUS.addListener(remove);
    try {
      h.assertTrue(
          !SlimeTeleporterItem.teleport(actor, SlimeTeleporterItem.bound(slime))
              && slime.getStamina() == slime.getMaxStamina()
              && actor.position().equals(before),
          "Removing the only loading supply during the event invalidates travel");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(remove);
      slime.discard();
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ender_equipment")
  public static void dimensionTravelRequiresReusableSeal(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var actor = owner(h.getLevel());
    var slime = anchor(h, area, actor, 2);
    var other = h.getLevel().getServer().getLevel(Level.NETHER);
    var source = owner(other);
    try {
      var token = SlimeTeleporterItem.bound(slime);
      source.setUUID(actor.getUUID());
      h.assertTrue(
          !slime.canChangeDimensions()
              && !SlimeTeleporterItem.teleport(source, token)
              && CompanionTeleport.relocate(slime, other, slime.position(), p -> true) == null,
          "Default restriction covers teleporter and companion relocation");
      slime.setEquipment(1, new ItemStack(ModItems.DIMENSIONAL_ATTUNEMENT_SEAL.get()));
      h.assertTrue(
          slime.canChangeDimensions() && SlimeTeleporterItem.teleport(source, token),
          "The seal enables an actual cross-dimension player transfer");
      h.assertTrue(
          source.serverLevel() == h.getLevel()
              && slime.allowsDimensionalTravel()
              && slime.getEquipment(1).getCount() == 1,
          "Successful transfer does not consume the seal");
    } finally {
      source.serverLevel().removePlayerImmediately(source, Entity.RemovalReason.DISCARDED);
      slime.discard();
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ender_equipment")
  public static void legacyRegistryNeverRetainsImplicitChunkLoading(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var actor = owner(h.getLevel());
    var slime = anchor(h, area, actor, 0);
    try {
      var saved = CompanionRegistry.get(h.getLevel()).save(new CompoundTag());
      for (var tag : saved.getList("Companions", Tag.TAG_COMPOUND))
        ((CompoundTag) tag).remove("ChunkLoading");
      var old = CompanionRegistry.load(saved).find(slime.getUUID());
      h.assertTrue(
          old != null && old.pinned() && !old.chunkLoading(),
          "A legacy record retains its companion and link, with no implicit ticket rights");
      slime.setEquipment(0, new ItemStack(Items.ENDER_PEARL, 2));
      var entry =
          CompanionRegistry.load(CompanionRegistry.get(h.getLevel()).save(new CompoundTag()))
              .find(slime.getUUID());
      h.assertTrue(
          entry.chunkLoading()
              && !entry.withLocation(CompanionRegistry.Location.ITEM).chunkLoading(),
          "Current loading permission survives save and is removed for stored companions");
    } finally {
      slime.discard();
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ender_equipment")
  public static void startupCallbackRemovesLegacyTicketsAndRetainsEquippedAnchors(
      GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var actor = owner(h.getLevel());
    var slime = anchor(h, area, actor, 0);
    try {
      var ticket = new CompoundTag();
      ticket.putLong("Chunk", slime.chunkPosition().toLong());
      var identities = new ListTag();
      identities.add(NbtUtils.createUUID(slime.getUUID()));
      ticket.put("TickingEntities", identities);
      var chunks = new ListTag();
      chunks.add(ticket);
      var mod = new CompoundTag();
      mod.putString("Mod", Slimic.MOD_ID);
      mod.put("ModForced", chunks);
      var mods = new ListTag();
      mods.add(mod);
      var saved = new CompoundTag();
      saved.put("ForgeForced", mods);
      var legacy = new ForcedChunksSavedData();
      ForgeChunkManager.readForgeForcedChunks(
          saved, legacy.getBlockForcedChunks(), legacy.getEntityForcedChunks());
      ForgeChunkManager.reinstatePersistentChunks(h.getLevel(), legacy);
      h.assertTrue(
          legacy.getEntityForcedChunks().isEmpty(),
          "Startup discards old ticket permissions for anchors without pearls");
      slime.setEquipment(0, new ItemStack(Items.ENDER_PEARL, 2));
      var equipped = new ForcedChunksSavedData();
      ForgeChunkManager.readForgeForcedChunks(
          saved, equipped.getBlockForcedChunks(), equipped.getEntityForcedChunks());
      ForgeChunkManager.reinstatePersistentChunks(h.getLevel(), equipped);
      h.assertTrue(
          !equipped.getEntityForcedChunks().isEmpty(),
          "A current equipped anchor retains its restart ticket");
    } finally {
      slime.discard();
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ender_equipment")
  public static void movedAnchorInvalidatesTravelWithoutCharging(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var actor = owner(h.getLevel());
    var slime = anchor(h, area, actor, 3);
    var before = actor.position();
    Consumer<EntityTeleportEvent> move =
        event -> {
          if (event.getEntity() == actor)
            slime.teleportTo(slime.getX() + 1, slime.getY(), slime.getZ());
        };
    MinecraftForge.EVENT_BUS.addListener(move);
    try {
      h.assertTrue(
          !SlimeTeleporterItem.teleport(actor, SlimeTeleporterItem.bound(slime))
              && slime.getEquipment(0).getCount() == 3
              && slime.getStamina() == slime.getMaxStamina()
              && actor.position().equals(before),
          "A moving endpoint cannot send the player to its old position or charge resources");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(move);
      slime.discard();
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ender_equipment")
  public static void sealRemovalDuringTravelRevokesBothPlayerAndEntityCrossing(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var actor = owner(h.getLevel());
    var slime = anchor(h, area, actor, 3);
    var other = h.getLevel().getServer().getLevel(Level.NETHER);
    var source = owner(other);
    source.setUUID(actor.getUUID());
    Consumer<net.minecraftforge.event.entity.EntityTravelToDimensionEvent> remove =
        event -> {
          if (event.getEntity() == source || event.getEntity() == slime)
            slime.setEquipment(1, ItemStack.EMPTY);
        };
    MinecraftForge.EVENT_BUS.addListener(remove);
    try {
      slime.setEquipment(1, new ItemStack(ModItems.DIMENSIONAL_ATTUNEMENT_SEAL.get()));
      h.assertTrue(
          !SlimeTeleporterItem.teleport(source, SlimeTeleporterItem.bound(slime))
              && source.serverLevel() == other
              && slime.getEquipment(0).getCount() == 3
              && slime.getStamina() == slime.getMaxStamina(),
          "Removing the seal in a dimension event cancels player travel before charging");
      slime.setEquipment(1, new ItemStack(ModItems.DIMENSIONAL_ATTUNEMENT_SEAL.get()));
      h.assertTrue(
          slime.changeDimension(other, other.getPortalForcer()) == null
              && slime.isAlive()
              && slime.level() == h.getLevel(),
          "Native entity travel also revalidates the seal after dimension callbacks");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(remove);
      slime.discard();
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ender_equipment")
  public static void destinationCopyCannotDropItsSealDuringRelocation(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var actor = owner(h.getLevel());
    var slime = anchor(h, area, actor, 3);
    slime.setEquipment(1, new ItemStack(ModItems.DIMENSIONAL_ATTUNEMENT_SEAL.get()));
    var other = h.getLevel().getServer().getLevel(Level.NETHER);
    var point = BlockPos.containing(area.point(8, 201, 8));
    var chunk = new ChunkPos(point);
    other.setChunkForced(chunk.x, chunk.z, true);
    other.getChunk(chunk.x, chunk.z);
    for (int x = -2; x <= 2; x++)
      for (int z = -2; z <= 2; z++) {
        other.setBlock(point.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
        for (int y = 0; y < 4; y++)
          other.setBlock(point.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
      }
    boolean[] admitted = {false};
    Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> remove =
        event -> {
          if (event.getLevel() == other
              && event.getEntity() instanceof EnderSlime copy
              && copy.getUUID().equals(slime.getUUID())) {
            admitted[0] = true;
            copy.setEquipment(1, ItemStack.EMPTY);
          }
        };
    MinecraftForge.EVENT_BUS.addListener(remove);
    try {
      h.assertTrue(
          CompanionTeleport.relocate(slime, other, Vec3.atBottomCenterOf(point), p -> true) == null
              && admitted[0]
              && slime.isAlive()
              && slime.allowsDimensionalTravel()
              && other.getEntity(slime.getUUID()) == null,
          "A destination callback that removes the seal rolls back the copy and preserves the"
              + " original");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(remove);
      slime.discard();
      other.setChunkForced(chunk.x, chunk.z, false);
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ender_native_dimension")
  public static void nativeDimensionTransferKeepsIdentityEquipmentAndOneAnchorTicket(
      GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var actor = owner(h.getLevel());
    var slime = anchor(h, area, actor, 3);
    slime.setEquipment(1, new ItemStack(ModItems.DIMENSIONAL_ATTUNEMENT_SEAL.get()));
    slime.setTunedVitals(7, 83, 11);
    var other = h.getLevel().getServer().getLevel(Level.NETHER);
    var point = BlockPos.containing(area.point(8, 201, 8));
    var chunk = new ChunkPos(point);
    other.setChunkForced(chunk.x, chunk.z, true);
    other.getChunk(chunk.x, chunk.z);
    for (int x = -2; x <= 2; x++)
      for (int z = -2; z <= 2; z++) {
        other.setBlock(point.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
        for (int y = 0; y < 4; y++)
          other.setBlock(point.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
      }
    int originTickets = tickets(h.getLevel()), targetTickets = tickets(other);
    EnderSlime moved = null;
    try {
      var result =
          slime.changeDimension(
              other,
              new net.minecraftforge.common.util.ITeleporter() {
                @Override
                public Entity placeEntity(
                    Entity original,
                    ServerLevel from,
                    ServerLevel to,
                    float yaw,
                    java.util.function.Function<Boolean, Entity> reposition) {
                  return reposition.apply(false);
                }

                @Override
                public net.minecraft.world.level.portal.PortalInfo getPortalInfo(
                    Entity original,
                    ServerLevel target,
                    java.util.function.Function<
                            ServerLevel, net.minecraft.world.level.portal.PortalInfo>
                        fallback) {
                  return new net.minecraft.world.level.portal.PortalInfo(
                      Vec3.atBottomCenterOf(point), Vec3.ZERO, 0, 0);
                }
              });
      h.assertTrue(
          result instanceof EnderSlime,
          "A sealed companion completes native entity dimension transfer");
      moved = (EnderSlime) result;
      h.assertTrue(
          moved.getUUID().equals(slime.getUUID())
              && moved.getOwnerUUID().equals(actor.getUUID())
              && moved.getHealth() == 7
              && moved.getStamina() == 83
              && moved.getStatPoints() == 11
              && moved.getEquipment(0).getCount() == 3
              && moved.allowsDimensionalTravel()
              && moved.isPinned()
              && moved.isOrderedToSit(),
          "Native transfer preserves identity, current vitals, growth, both equipment slots and"
              + " waiting anchor state");
      h.assertTrue(
          tickets(h.getLevel()) == originTickets - 1
              && tickets(other) == targetTickets + 1
              && CompanionRegistry.get(other)
                  .find(moved.getUUID())
                  .dimension()
                  .equals(other.dimension().location().toString()),
          "The old anchor ticket is replaced by exactly one ticket in the destination dimension:"
              + " origin="
              + tickets(h.getLevel())
              + "/"
              + (originTickets - 1)
              + ", target="
              + tickets(other)
              + "/"
              + (targetTickets + 1));
    } finally {
      if (moved != null) moved.discard();
      slime.discard();
      other.setChunkForced(chunk.x, chunk.z, false);
      area.close();
    }
    h.succeed();
  }
}
