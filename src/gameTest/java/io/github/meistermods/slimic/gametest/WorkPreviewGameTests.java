package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.SlimeTunerItem;
import io.github.meistermods.slimic.network.WorkAreaPacket;
import io.github.meistermods.slimic.registry.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkPreviewGameTests {
  private static final class Receiver extends ServerPlayer {
    final List<Component> messages = new ArrayList<>();

    Receiver(GameTestHelper h) {
      super(
          h.getLevel().getServer(),
          h.getLevel(),
          new GameProfile(UUID.randomUUID(), "AlertReceiver"));
      var connection =
          new net.minecraft.network.Connection(
              net.minecraft.network.protocol.PacketFlow.SERVERBOUND) {
            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet) {}
          };
      this.connection =
          new net.minecraft.server.network.ServerGamePacketListenerImpl(
              h.getLevel().getServer(), connection, this);
    }

    @Override
    public void sendSystemMessage(Component message) {
      messages.add(message);
    }
  }

  @SuppressWarnings("unchecked")
  private static List<ServerPlayer> receivers(GameTestHelper h, List<Receiver> added) {
    try {
      var listField = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
      listField.setAccessible(true);
      var list = (List<ServerPlayer>) listField.get(h.getLevel().getServer().getPlayerList());
      var infoField = GameTestHelper.class.getDeclaredField("testInfo");
      infoField.setAccessible(true);
      ((GameTestInfo) infoField.get(h))
          .addListener(
              new GameTestListener() {
                @Override
                public void testStructureLoaded(GameTestInfo info) {}

                @Override
                public void testPassed(GameTestInfo info) {
                  list.removeAll(added);
                }

                @Override
                public void testFailed(GameTestInfo info) {
                  list.removeAll(added);
                }
              });
      list.addAll(added);
      return list;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @GameTest(template = "empty")
  public static void allProfessionPreviewsAreBoundedAndDoNotStartWork(GameTestHelper h) {
    for (var type : ModEntities.ENTITY_TYPES.getEntries()) {
      var slime = (FriendlySlime) type.get().create(h.getLevel());
      slime.moveTo(123.5, 101, 234.5);
      slime.setWaiting(true);
      var area = SlimeWorkArea.of(slime);
      boolean supported =
          slime instanceof BagSlime
              || slime instanceof HeraldSlime
              || slime instanceof GuardianAngelSlime
              || slime instanceof SoggySlime;
      h.assertTrue(
          area.shapes().isEmpty() != supported
              && area.planned()
              && area.shapes().size() <= SlimeWorkArea.MAX_SHAPES
              && slime.isOrderedToSit()
              && !slime.isWorking(),
          "Read-only planned area for " + type.getId());
    }
    var cleaner = ModEntities.CLEANING_SLIME.get().create(h.getLevel());
    SlimeStat.DETECTION_RANGE.tune(cleaner, 64);
    h.assertTrue(
        SlimeWorkArea.of(cleaner).shapes().get(0).bounds().getXsize() == 640,
        "Preview includes the Cleaning Slime's full profession range multiplier");
    var miner = ModEntities.MINING_SLIME.get().create(h.getLevel());
    miner.moveTo(123.5, 101, 234.5);
    miner.onReleased();
    while (miner.getQuarryPlan().layer() >= h.getLevel().getMinBuildHeight())
      miner.getQuarryPlan().descend();
    h.assertTrue(
        SlimeWorkArea.of(miner).shapes().size() == 1
            && !SlimeWorkArea.of(miner).shapes().get(0).next(),
        "Completed quarries keep their outline without inventing a next layer");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void tunnelPreviewShowsEightSectionsInEveryHorizontalDirection(GameTestHelper h) {
    var tunnel = ModEntities.TUNNEL_SLIME.get().create(h.getLevel());
    tunnel.moveTo(-33.5, 80, 17.5);
    for (var direction : Direction.Plane.HORIZONTAL) {
      tunnel.setTunnelDirection(direction);
      var area = SlimeWorkArea.of(tunnel);
      var front = tunnel.getTunnelFront();
      var next = area.shapes().get(1).bounds();
      h.assertTrue(
          area.planned()
              && area.shapes().size() == 2
              && area.shapes().get(1).next()
              && next.contains(Vec3.atCenterOf(front))
              && next.getYsize() == 3
              && (direction.getAxis() == Direction.Axis.X
                  ? next.getXsize() == 1 && next.getZsize() == 3
                  : next.getXsize() == 3 && next.getZsize() == 1)
              && area.shapes()
                  .get(0)
                  .bounds()
                  .contains(Vec3.atCenterOf(front.relative(direction, 7)))
              && tunnel.isOrderedToSit(),
          "Directional front and eight planned sections: " + direction);
    }
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void workAreaPacketsRoundTripAndRejectOversizedOrNonfiniteData(GameTestHelper h) {
    var miner = ModEntities.MINING_SLIME.get().create(h.getLevel());
    miner.moveTo(-33.5, 80, 17.5);
    var packet =
        new WorkAreaPacket(
            h.getLevel().dimension().location(), 42, miner.getUUID(), SlimeWorkArea.of(miner));
    var buffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
      packet.encode(buffer);
      h.assertTrue(
          WorkAreaPacket.decode(buffer).equals(packet),
          "Dimension, identity and exact bounds round-trip");
      buffer.clear();
      buffer.writeResourceLocation(packet.dimension());
      buffer.writeVarInt(42);
      buffer.writeUUID(packet.companion());
      buffer.writeBoolean(true);
      buffer.writeByte(SlimeWorkArea.MAX_SHAPES + 1);
      boolean rejected = false;
      try {
        WorkAreaPacket.decode(buffer);
      } catch (IllegalArgumentException expected) {
        rejected = true;
      }
      h.assertTrue(rejected, "Oversized payload rejected before reading shapes");
      rejected = false;
      try {
        new SlimeWorkArea.Shape(new AABB(Double.NaN, 0, 0, 1, 1, 1), false, false);
      } catch (IllegalArgumentException expected) {
        rejected = true;
      }
      h.assertTrue(rejected, "Nonfinite world coordinates are rejected");
    } finally {
      buffer.release();
    }
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void tunerShiftInteractionPreviewsInsteadOfCapturingAndChecksOwnership(
      GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var miner = ModEntities.MINING_SLIME.get().create(h.getLevel());
    miner.tame(owner);
    owner.moveTo(miner.position());
    owner.setShiftKeyDown(true);
    owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
    var event = new PlayerInteractEvent.EntityInteract(owner, InteractionHand.MAIN_HAND, miner);
    FriendlySlimeInteractions.captureOnSneakInteract(event);
    h.assertTrue(
        event.isCanceled()
            && event.getCancellationResult().consumesAction()
            && !miner.isRemoved()
            && owner.getMainHandItem().is(ModItems.SLIME_TUNER.get()),
        "Tuner Shift-click cannot capture or consume gear");
    var stranger = h.makeMockSurvivalPlayer();
    stranger.moveTo(miner.position());
    stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
    h.assertTrue(
        !SlimeTunerItem.preview(stranger, miner).consumesAction(),
        "Unauthorized viewers are rejected");
    owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
    h.assertTrue(
        SlimeTunerItem.preview(owner, miner).consumesAction(), "Offhand tuner also previews");
    owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    event = new PlayerInteractEvent.EntityInteract(owner, InteractionHand.MAIN_HAND, miner);
    FriendlySlimeInteractions.captureOnSneakInteract(event);
    h.assertTrue(
        event.isCanceled() && event.getCancellationResult().consumesAction() && miner.isRemoved(),
        "Shift-click without a tuner still recovers the companion");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void workWarningsIncludeCustomIdentityAndProfessionSpecificCauses(
      GameTestHelper h) {
    var miner = ModEntities.MINING_SLIME.get().create(h.getLevel());
    miner.setCustomName(Component.literal("Qurry"));
    var missing = WorkStatus.blocked(WorkStatus.Order.MINE, WorkStatus.Reason.TOOL_MISSING);
    String message = Component.Serializer.toJson(SlimeWorkWarning.message(miner, missing));
    h.assertTrue(
        message.contains("Qurry")
            && message.contains("entity.slimic.mining_slime")
            && message.contains("blocked.tool_missing.pickaxe"),
        "A named miner reports its identity and missing pickaxe");
    var saved = miner.saveWithoutId(new CompoundTag());
    saved.putInt("MiningRequiredTool", 1);
    miner.load(saved);
    h.assertTrue(
        Component.Serializer.toJson(SlimeWorkWarning.reason(miner, missing))
            .contains("tool_missing.shovel"),
        "Missing shovel is distinct from missing pickaxe");
    for (var reason : WorkStatus.Reason.values())
      h.assertTrue(
          Component.Serializer.toJson(
                  SlimeWorkWarning.reason(miner, WorkStatus.blocked(WorkStatus.Order.MINE, reason)))
              .contains("message.slimic.blocked."),
          "Every blocked cause has a localized message: " + reason);
    var lumberjack = ModEntities.LUMBERJACK_SLIME.get().create(h.getLevel());
    var farmer = ModEntities.FARMER_SLIME.get().create(h.getLevel());
    var seeds = WorkStatus.blocked(WorkStatus.Order.LOG, WorkStatus.Reason.SEEDS_MISSING);
    h.assertTrue(
        Component.Serializer.toJson(SlimeWorkWarning.reason(lumberjack, seeds))
                .contains("saplings_missing")
            && Component.Serializer.toJson(SlimeWorkWarning.reason(farmer, seeds))
                .contains("seeds_missing"),
        "Lumberjack saplings and Farming seeds remain distinct");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 160)
  public static void heraldMessagesOwnersAndSharedOwnersOncePerSignalCooldown(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var owner = new Receiver(h);
    var shared = new Receiver(h);
    var observer = new Receiver(h);
    receivers(h, List.of(owner, shared, observer));
    CompanionRegistry.get(h.getLevel()).setRelation(owner.getUUID(), shared.getUUID(), true, true);
    var miner = ModEntities.MINING_SLIME.get().create(h.getLevel());
    miner.tame(owner);
    miner.moveTo(area.point(8, 101, 8));
    miner.onReleased();
    miner.setWorking(true);
    miner.setNoAi(true);
    miner.setCustomName(Component.literal("Qurry"));
    var saved = miner.saveWithoutId(new CompoundTag());
    saved.putInt("MiningStatus", MiningSlime.Status.NO_TOOL.ordinal());
    saved.putInt("BlockedWorkTicks", 1200);
    saved.putLong("BlockedEpisode", 5);
    miner.load(saved);
    h.getLevel().addFreshEntity(miner);
    var herald = ModEntities.HERALD_SLIME.get().create(h.getLevel());
    herald.moveTo(area.point(8, 101, 10));
    herald.tame(owner);
    herald.setMonitoring(true);
    herald.setDelayMinutes(1);
    h.getLevel().addFreshEntity(herald);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    owner.messages.size() == 1 && shared.messages.size() == 1,
                    "An actual herald signal informs primary and shared owners"))
        .thenIdle(40)
        .thenExecute(
            () -> {
              h.assertTrue(
                  owner.messages.size() == 1
                      && shared.messages.size() == 1
                      && observer.messages.isEmpty(),
                  "Cooldown prevents repeated chat and unrelated players receive nothing");
              String message = Component.Serializer.toJson(owner.messages.get(0));
              h.assertTrue(
                  message.contains("Qurry")
                      && message.contains("tool_missing.pickaxe")
                      && herald
                              .saveWithoutId(new CompoundTag())
                              .getCompound("HeraldWarnings")
                              .size()
                          == 1,
                  "A completed automatic signal sends the named worker and concrete cause");
              miner.setWaiting(true);
              SlimeWorkWarning.notifyOwners(miner);
              h.assertTrue(
                  owner.messages.size() == 1,
                  "Manual waiting does not issue blocked-work messages");
              area.close();
            })
        .thenSucceed();
  }
}
