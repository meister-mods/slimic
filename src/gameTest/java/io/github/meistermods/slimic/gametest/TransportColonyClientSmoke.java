package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.block.ColonyBondingStationBlockEntity;
import io.github.meistermods.slimic.client.BoundColonyRenderer;
import io.github.meistermods.slimic.client.BoundTransportSlimeRenderer;
import io.github.meistermods.slimic.client.TransportColonyBodyRenderer;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.item.SlimeArmorItem;
import io.github.meistermods.slimic.registry.*;
import io.github.meistermods.slimic.world.ColonyBonding;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Native separate-body rendering, linked movement, waiting, appearance and single recovery. */
public final class TransportColonyClientSmoke {
  private static final BlockPos ORIGIN = new BlockPos(100008, 101, 100008);
  private static int phase, ticks;
  private static volatile int rootId, bodyId, combatId;
  private static CompletableFuture<?> action = CompletableFuture.completedFuture(null);
  private static Vec3 initialRoot, initialBody, stoppedRoot, stoppedBody;

  private TransportColonyClientSmoke() {}

  private static void require(boolean condition, String reason) {
    if (!condition) throw new IllegalStateException(reason);
  }

  private static ServerPlayer actor(Minecraft client) {
    return client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
  }

  private static FriendlySlime deploy(
      ServerPlayer player, ColonyBondingStationBlockEntity station, boolean combat, BlockPos at) {
    var level = player.serverLevel();
    for (int index = 0; index < 2; index++) {
      BagSlime member =
          combat
              ? ModEntities.COMBAT_SLIME.get().create(level)
              : ModEntities.TRANSPORT_SLIME.get().create(level);
      member.tame(player);
      member.setDyeColorId((index == 0 ? DyeColor.LIME : DyeColor.CYAN).getId());
      member.setCustomName(Component.literal((combat ? "Blade " : "Cargo ") + index));
      member.getBag().setStackInSlot(0, new ItemStack(Items.EMERALD, 7));
      if (member instanceof CombatSlime fighter) {
        fighter.setEquipment(0, new ItemStack(index == 0 ? Items.IRON_SWORD : Items.GOLDEN_SWORD));
        fighter.setEquipment(1, new ItemStack(Items.SHIELD));
        fighter.setEquipment(
            2,
            SlimeArmorItem.fromChestplate(
                new ItemStack(index == 0 ? Items.IRON_CHESTPLATE : Items.GOLDEN_CHESTPLATE)));
      }
      station.setItem(index, FriendlySlimeItem.fromSlime(member));
    }
    require(
        ColonyBonding.execute(player, station, false) == ColonyBonding.Problem.COMPLETE,
        "The native station binds two actual stored members");
    int slot = -1;
    for (int i = 0; i < player.getInventory().getContainerSize(); i++)
      if (ColonyBonding.bound(player.getInventory().getItem(i))) {
        slot = i;
        break;
      }
    require(slot >= 0, "The station delivers its sole bound item");
    var stack = player.getInventory().removeItemNoUpdate(slot);
    var identity = stack.getTag().getCompound("SlimeData").getUUID("UUID");
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    var floor = at.below();
    require(
        stack
            .getItem()
            .useOn(
                new UseOnContext(
                    player,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)))
            .consumesAction(),
        "The stored colony enters the actual world through its item");
    var root = (FriendlySlime) level.getEntity(identity);
    require(root != null && root.isOrderedToSit(), "Both colony families deploy in Wait");
    root.setNoAi(true);
    return root;
  }

  private static BoundTransportSlime root(Minecraft client) {
    return (BoundTransportSlime) client.level.getEntity(rootId);
  }

  private static TransportColonyBody body(Minecraft client) {
    return (TransportColonyBody) client.level.getEntity(bodyId);
  }

  private static void look(Minecraft client) {
    var root = root(client);
    var body = body(client);
    if (root == null || body == null) return;
    var middle = root.position().add(body.position()).scale(.5).add(0, .55, 0);
    var delta = middle.subtract(client.player.getEyePosition());
    client.player.setYRot((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90));
    client.player.setXRot(
        (float)
            -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
  }

  private static void shot(Minecraft client, String name) {
    try {
      var folder = client.gameDirectory.toPath().resolve("transport-colony-audit");
      Files.createDirectories(folder);
      try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
        image.writeToFile(folder.resolve(name + ".png"));
      }
    } catch (java.io.IOException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static void appearances(Minecraft client) throws ReflectiveOperationException {
    var dispatcher = client.getEntityRenderDispatcher();
    require(
        dispatcher.getRenderer(root(client)) instanceof BoundTransportSlimeRenderer,
        "Transport root uses one body and the cord renderer");
    var renderer = dispatcher.getRenderer(body(client));
    require(renderer instanceof TransportColonyBodyRenderer, "The real helper owns its renderer");
    var field = TransportColonyBodyRenderer.class.getDeclaredField("visual");
    field.setAccessible(true);
    var visual = (TransportSlime) field.get(renderer);
    require(
        visual != null
            && !visual.isAddedToWorld()
            && visual.getDyeColorId() == DyeColor.CYAN.getId()
            && root(client).getDyeColorId() == DyeColor.LIME.getId(),
        "One unspawned visual proxy preserves the second dye independently of the root");
    var combat = (BoundCombatSlime) client.level.getEntity(combatId);
    require(
        combat != null && dispatcher.getRenderer(combat) instanceof BoundColonyRenderer,
        "The combat colony uses the common cord renderer");
    var shared = combat.memberEquipment(0, EquipmentSlot.CHEST).copy();
    var appearance = combat.colonyAppearance().copy();
    combat.setRenderingSecondMember(true);
    try {
      require(
          ItemStack.matches(shared, combat.memberEquipment(0, EquipmentSlot.CHEST))
              && !ItemStack.isSameItemSameTags(shared, combat.getItemBySlot(EquipmentSlot.CHEST)),
          "Both original armor records stay distinct and independently accessible");
    } finally {
      combat.setRenderingSecondMember(false);
    }
    require(
        appearance.equals(combat.colonyAppearance()),
        "Appearance rendering cannot mutate member data");
  }

  public static boolean tick(Minecraft client) {
    try {
      if (!action.isDone()) return false;
      action.join();
      if (phase == 0) {
        client.setScreen(null);
        client.options.hideGui = true;
        action =
            client
                .getSingleplayerServer()
                .submit(
                    () -> {
                      var player = actor(client);
                      var level = player.serverLevel();
                      player.setGameMode(GameType.CREATIVE);
                      player.getInventory().clearContent();
                      level.setDayTime(6000);
                      level.setWeatherParameters(6000, 0, false, false);
                      for (int x = -12; x <= 12; x++)
                        for (int z = -10; z <= 12; z++) {
                          level.setBlock(
                              ORIGIN.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
                          for (int y = 0; y < 9; y++)
                            level.setBlock(
                                ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                        }
                      player.teleportTo(ORIGIN.getX() + .5, ORIGIN.getY(), ORIGIN.getZ() + 3.5);
                      var stationPos = ORIGIN.offset(0, 0, -4);
                      level.setBlockAndUpdate(
                          stationPos, ModBlocks.COLONY_BONDING_STATION.get().defaultBlockState());
                      var station =
                          (ColonyBondingStationBlockEntity) level.getBlockEntity(stationPos);
                      rootId = deploy(player, station, false, ORIGIN.offset(-2, 0, 0)).getId();
                      combatId = deploy(player, station, true, ORIGIN.offset(4, 0, -3)).getId();
                      player.getAbilities().flying = true;
                      player.onUpdateAbilities();
                      player.teleportTo(
                          ORIGIN.getX() + .5, ORIGIN.getY() + 1.5, ORIGIN.getZ() + 9.5);
                    });
        phase = 1;
        return false;
      }
      if (phase == 1) {
        if (root(client) == null || root(client).getLinkedBody() == null) return false;
        bodyId = root(client).getLinkedBody().getId();
        action =
            client
                .getSingleplayerServer()
                .submit(
                    () -> {
                      var primary =
                          (BoundTransportSlime) actor(client).serverLevel().getEntity(rootId);
                      var secondary = primary.getLinkedBody();
                      require(
                          secondary != null, "Native deployment creates exactly one linked body");
                      secondary.setNoAi(true);
                      secondary.teleportTo(primary.getX() + 4, primary.getY(), primary.getZ());
                    });
        ticks = 0;
        phase = 2;
        return false;
      }
      look(client);
      if (phase == 2) {
        if (++ticks < 30) return false;
        require(
            root(client).distanceToSqr(body(client)) > 9, "Two separate actual bodies are visible");
        appearances(client);
        shot(client, "01-wait-separated-cord");
        initialRoot = root(client).position();
        initialBody = body(client).position();
        action =
            client
                .getSingleplayerServer()
                .submit(
                    () -> {
                      var primary =
                          (BoundTransportSlime) actor(client).serverLevel().getEntity(rootId);
                      primary.setNoAi(false);
                      primary.getLinkedBody().setNoAi(false);
                      primary.setWorking(false);
                      primary.setWaiting(false);
                    });
        ticks = 0;
        phase = 3;
        return false;
      }
      if (phase == 3) {
        if (++ticks == 30) shot(client, "02-moving-cord");
        if (ticks < 80) return false;
        require(
            root(client).position().distanceToSqr(initialRoot) > .25
                && body(client).position().distanceToSqr(initialBody) > .25,
            "Both native bodies actually move after Follow is assigned");
        require(
            root(client).getLinkedBody() == body(client), "Movement preserves one linked identity");
        shot(client, "03-followed-cord");
        action =
            client
                .getSingleplayerServer()
                .submit(
                    () ->
                        ((BoundTransportSlime) actor(client).serverLevel().getEntity(rootId))
                            .setWaiting(true));
        ticks = 0;
        phase = 4;
        return false;
      }
      if (phase == 4) {
        if (++ticks < 25) return false;
        stoppedRoot = root(client).position();
        stoppedBody = body(client).position();
        ticks = 0;
        phase = 5;
        return false;
      }
      if (phase == 5) {
        if (++ticks < 25) return false;
        require(
            root(client).position().distanceToSqr(stoppedRoot) < .04
                && body(client).position().distanceToSqr(stoppedBody) < .04,
            "Wait stops both linked bodies after their last hop settles");
        shot(client, "04-wait-after-movement");
        action =
            client
                .getSingleplayerServer()
                .submit(
                    () -> {
                      var player = actor(client);
                      var primary = (BoundTransportSlime) player.serverLevel().getEntity(rootId);
                      require(
                          primary.capture(player).consumesAction(),
                          "The owner captures the complete colony once");
                      require(
                          player.getInventory().countItem(ModItems.BOUND_TRANSPORT_SLIME.get())
                              == 1,
                          "One captured colony item owns both original members");
                    });
        ticks = 0;
        phase = 6;
        return false;
      }
      if (phase == 6) {
        if (++ticks < 20) return false;
        require(
            root(client) == null && body(client) == null, "Recovery removes both actual bodies");
        System.out.println("SLIMIC TRANSPORT COLONY CLIENT SMOKE PASSED");
        return true;
      }
      return false;
    } catch (Exception failure) {
      throw new IllegalStateException("Transport colony client smoke phase " + phase, failure);
    }
  }
}
