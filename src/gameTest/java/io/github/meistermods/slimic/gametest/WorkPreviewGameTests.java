package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.SlimeScannerItem;
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
      this(h, h.getLevel());
    }

    Receiver(GameTestHelper h, net.minecraft.server.level.ServerLevel level) {
      super(h.getLevel().getServer(), level, new GameProfile(UUID.randomUUID(), "AlertReceiver"));
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
    for (var type : ModEntities.companionTypes()) {
      var slime = (FriendlySlime) type.get().create(h.getLevel());
      slime.moveTo(123.5, 101, 234.5);
      slime.setWaiting(true);
      var area = SlimeWorkArea.of(slime);
      boolean supported =
          slime instanceof BagSlime
              || slime instanceof SignalingSlime
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
  public static void proposedQuarryLayerMatchesActualPlanAtWorldHeightLimits(GameTestHelper h) {
    int site = 0;
    for (var level : h.getLevel().getServer().getAllLevels()) {
      int min = level.getMinBuildHeight(), max = level.getMaxBuildHeight();
      for (int y : new int[] {min, min + 1, 101, max - 1}) {
        var miner = ModEntities.MINING_SLIME.get().create(level);
        // Starting work registers staircase protection; keep it away from other fixtures.
        int x = 18_000_008 + site++ * 64;
        miner.moveTo(x + .5, 101, -18_000_008.5);
        miner.onReleased();
        miner.setWaiting(true);
        miner.moveTo(x + 16.5, y, -18_000_008.5);
        var proposed = SlimeWorkArea.of(miner).shapes();
        h.assertTrue(miner.isOrderedToSit(), "A proposed area keeps the miner waiting");
        miner.setWorking(true);
        h.assertTrue(
            proposed.equals(SlimeWorkArea.of(miner).shapes()),
            "Proposed and actual quarry agree at " + level.dimension().location() + " y=" + y);
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void allProfessionAreasRemainFiniteAtWorldBorderAndZeroRange(GameTestHelper h) {
    for (var type : ModEntities.companionTypes()) {
      for (int sign : new int[] {-1, 1}) {
        var pet = (FriendlySlime) type.get().create(h.getLevel());
        pet.moveTo(sign * 29_999_980.5, 101, sign * 29_999_980.5);
        for (int range : new int[] {0, 64}) {
          SlimeStat.DETECTION_RANGE.tune(pet, range);
          var area = SlimeWorkArea.of(pet);
          h.assertTrue(
              area.shapes().size() <= SlimeWorkArea.MAX_SHAPES,
              "Bounded shapes for " + type.getId());
          for (var shape : area.shapes()) {
            var center = shape.bounds().getCenter();
            h.assertTrue(
                Double.isFinite(center.x)
                    && Double.isFinite(center.y)
                    && Double.isFinite(center.z)
                    && shape.bounds().getXsize() <= 640,
                "Finite small area at world border for " + type.getId());
          }
          h.assertTrue(pet.isOrderedToSit() && !pet.isWorking(), "Preview keeps Wait");
        }
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void previewsNeverLoadColdTerrainOrAdvanceAnActiveFarmSurvey(GameTestHelper h) {
    var level = h.getLevel();
    var soil = h.absolutePos(new BlockPos(1, 1, 1));
    var original = level.getBlockState(soil);
    level.setBlock(soil, net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState(), 2);
    var farming = ModEntities.FARMING_SLIME.get().create(level);
    farming.moveTo(Vec3.atBottomCenterOf(soil.above()));
    farming.setWorking(true);
    h.assertTrue(farming.getFarmPlots().isEmpty(), "The new survey has not inspected its palette");
    int[] loads = {0};
    java.util.function.Consumer<net.minecraftforge.event.level.ChunkEvent.Load> listener =
        event -> {
          if (event.getLevel() == level) loads[0]++;
        };
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener);
    try {
      for (int query = 0; query < 100; query++) SlimeWorkArea.of(farming);
      h.assertTrue(
          farming.getFarmPlots().isEmpty()
              && farming.getFieldStatus() == FieldSlime.Status.SURVEYING,
          "Previewing an active farmer does not serialize or complete its survey");
      var cold = new BlockPos(15_000_008, 101, -15_000_008);
      h.assertTrue(!level.hasChunkAt(cold), "Preview terrain starts unloaded");
      for (var type : ModEntities.companionTypes()) {
        var pet = (FriendlySlime) type.get().create(level);
        pet.moveTo(Vec3.atCenterOf(cold));
        for (int query = 0; query < 100; query++) SlimeWorkArea.of(pet);
        h.assertTrue(pet.isOrderedToSit() && !pet.isWorking(), "Repeated previews retain Wait");
      }
      h.assertTrue(loads[0] == 0 && !level.hasChunkAt(cold), "No preview acquires any chunk");
    } finally {
      net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);
      level.setBlock(soil, original, 2);
    }
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void relocatedFieldsKeepTheirRegisteredCenterDuringTravel(GameTestHelper h) {
    for (var type : List.of(ModEntities.FARMING_SLIME.get(), ModEntities.LOGGING_SLIME.get())) {
      FieldSlime field = type.create(h.getLevel());
      field.moveTo(17_000_008.5, 101, -17_000_008.5);
      field.onReleased();
      field.setWorking(true);
      field.setWaiting(true);
      field.moveTo(17_000_040.5, 101, -17_000_008.5);
      field.onReleased();
      field.setWorking(true);
      Vec3 center = Vec3.atCenterOf(field.getWorkHome());
      field.moveTo(field.position().add(3, 0, 0));
      h.assertTrue(
          SlimeWorkArea.of(field).shapes().get(0).bounds().getCenter().equals(center),
          "A relocated worker's movement does not move its registered field area: " + type);
      field.setWaiting(true);
      var plannedCenter =
          field instanceof FarmingSlime ? Vec3.atCenterOf(field.blockPosition()) : center;
      h.assertTrue(
          SlimeWorkArea.of(field).shapes().get(0).bounds().getCenter().equals(plannedCenter),
          "Planned farming adopts its next survey center while logging keeps its area: " + type);
    }
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
  public static void ordinaryScannerInteractionPreviewsAndShiftStillRecovers(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var miner = ModEntities.MINING_SLIME.get().create(h.getLevel());
    miner.tame(owner);
    owner.moveTo(miner.position());
    owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_SCANNER.get()));
    h.assertTrue(
        miner.mobInteract(owner, InteractionHand.MAIN_HAND).consumesAction()
            && !miner.isRemoved()
            && owner.getMainHandItem().is(ModItems.SLIME_SCANNER.get()),
        "Ordinary scanner right-click previews without capturing or consuming the tool");
    var stranger = h.makeMockSurvivalPlayer();
    stranger.moveTo(miner.position());
    stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_SCANNER.get()));
    h.assertTrue(
        !SlimeScannerItem.preview(stranger, miner).consumesAction(),
        "Unauthorized viewers are rejected");
    owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.SLIME_SCANNER.get()));
    h.assertTrue(
        miner.mobInteract(owner, InteractionHand.OFF_HAND).consumesAction() && !miner.isRemoved(),
        "Offhand scanner also previews with ordinary right-click");
    owner.setShiftKeyDown(true);
    var event = new PlayerInteractEvent.EntityInteract(owner, InteractionHand.OFF_HAND, miner);
    FriendlySlimeInteractions.captureOnSneakInteract(event);
    h.assertTrue(
        event.isCanceled()
            && event.getCancellationResult().consumesAction()
            && miner.isRemoved()
            && owner.getOffhandItem().is(ModItems.SLIME_SCANNER.get()),
        "Shift-click with a scanner uses common recovery and preserves the tool");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void previewsRespectRangeSharedAccessAndRejectBothTuners(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var shared = h.makeMockSurvivalPlayer();
    var miner = ModEntities.MINING_SLIME.get().create(h.getLevel());
    miner.tame(owner);
    shared.moveTo(miner.position());
    shared.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_SCANNER.get()));
    var registry = CompanionRegistry.get(h.getLevel());
    registry.setRelation(owner.getUUID(), shared.getUUID(), true, true);
    h.assertTrue(
        SlimeScannerItem.preview(shared, miner).consumesAction(), "A delegated owner can preview");
    registry.setRelation(owner.getUUID(), shared.getUUID(), true, false);
    h.assertTrue(
        !SlimeScannerItem.preview(shared, miner).consumesAction(),
        "Revocation applies immediately");
    owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_SCANNER.get()));
    owner.moveTo(miner.position().add(8.01, 0, 0));
    h.assertTrue(!SlimeScannerItem.preview(owner, miner).consumesAction(), "Range is enforced");
    owner.moveTo(miner.position().add(8, 0, 0));
    h.assertTrue(
        SlimeScannerItem.preview(owner, miner).consumesAction(), "Eight blocks is allowed");
    owner.moveTo(miner.position());
    for (var tool : List.of(ModItems.SLIME_TUNER.get(), ModItems.CREATIVE_SLIME_TUNER.get())) {
      owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
      for (boolean creative : new boolean[] {false, true}) {
        owner.getAbilities().instabuild = creative;
        h.assertTrue(
            !SlimeScannerItem.preview(owner, miner).consumesAction(),
            "Management tools never authorize a preview, in either game mode");
      }
    }
    h.assertTrue(miner.isOrderedToSit() && !miner.isWorking(), "Authorization never starts work");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void scannerTakesPriorityOverEveryProfessionRightClickAction(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_SCANNER.get()));
    int checked = 0;
    for (var registered : ModEntities.companionTypes()) {
      var slime = (FriendlySlime) registered.get().create(h.getLevel());
      slime.tame(owner);
      owner.moveTo(slime.position());
      if (slime instanceof SaddleSlime saddle) saddle.setEquipment(0, new ItemStack(Items.SADDLE));
      var menu = owner.containerMenu;
      var before = slime.saveWithoutId(new CompoundTag());
      h.assertTrue(
          slime.mobInteract(owner, InteractionHand.MAIN_HAND).consumesAction(),
          "Ordinary scanner click is handled by " + registered.getId());
      h.assertTrue(
          before.equals(slime.saveWithoutId(new CompoundTag()))
              && owner.containerMenu == menu
              && !slime.isVehicle()
              && !slime.isRemoved(),
          "Scanning does not mount, signal, toggle abilities, open menus or change saved state");
      slime.discard();
      checked++;
    }
    h.assertTrue(checked == 26, "Every companion profession is tested");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void shiftWithManagementTunersUsesCommonRecovery(GameTestHelper h) {
    for (var tool : List.of(ModItems.SLIME_TUNER.get(), ModItems.CREATIVE_SLIME_TUNER.get())) {
      var owner = h.makeMockSurvivalPlayer();
      owner.setShiftKeyDown(true);
      owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
      var miner = ModEntities.MINING_SLIME.get().create(h.getLevel());
      miner.tame(owner);
      miner.setHealth(3.5F);
      var event = new PlayerInteractEvent.EntityInteract(owner, InteractionHand.MAIN_HAND, miner);
      FriendlySlimeInteractions.captureOnSneakInteract(event);
      h.assertTrue(
          event.isCanceled()
              && event.getCancellationResult().consumesAction()
              && miner.isRemoved()
              && owner.getMainHandItem().is(tool),
          "Shift recovery is independent of the held management tool");
      h.assertTrue(
          owner.getInventory().items.stream()
              .anyMatch(
                  stack ->
                      stack.is(ModItems.MINING_SLIME.get())
                          && stack.getTag().getCompound("SlimeData").getFloat("Health") == 3.5F),
          "Recovery stores the companion with its health");
    }
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void scannerRecipeRequiresATunerCenteredInAGlassCross(GameTestHelper h) {
    var grid =
        new net.minecraft.world.inventory.TransientCraftingContainer(
            new net.minecraft.world.inventory.AbstractContainerMenu(null, -1) {
              @Override
              public boolean stillValid(net.minecraft.world.entity.player.Player p) {
                return true;
              }

              @Override
              public ItemStack quickMoveStack(
                  net.minecraft.world.entity.player.Player p, int slot) {
                return ItemStack.EMPTY;
              }
            },
            3,
            3);
    grid.setItem(4, new ItemStack(ModItems.SLIME_TUNER.get()));
    for (int slot : new int[] {1, 3, 5, 7}) grid.setItem(slot, new ItemStack(Items.GLASS));
    var manager = h.getLevel().getRecipeManager();
    var type = net.minecraft.world.item.crafting.RecipeType.CRAFTING;
    var recipe = manager.getRecipeFor(type, grid, h.getLevel());
    h.assertTrue(
        recipe.isPresent()
            && recipe.get() instanceof net.minecraft.world.item.crafting.ShapedRecipe,
        "A centered tuner with four cardinal glass blocks uses a shaped recipe");
    var output = recipe.orElseThrow().assemble(grid, h.getLevel().registryAccess());
    h.assertTrue(
        output.is(ModItems.SLIME_SCANNER.get())
            && output.getCount() == 1
            && output.getMaxStackSize() == 1,
        "The cross makes one nonstackable scanner");
    grid.setItem(0, new ItemStack(Items.GLASS));
    h.assertTrue(manager.getRecipeFor(type, grid, h.getLevel()).isEmpty(), "Corners stay empty");
    grid.setItem(0, ItemStack.EMPTY);
    grid.setItem(1, ItemStack.EMPTY);
    h.assertTrue(
        manager.getRecipeFor(type, grid, h.getLevel()).isEmpty(),
        "All four glass blocks are required");
    grid.setItem(1, new ItemStack(Items.GLASS_PANE));
    h.assertTrue(
        manager.getRecipeFor(type, grid, h.getLevel()).isEmpty(), "A pane cannot replace glass");
    grid.setItem(1, new ItemStack(Items.GLASS));
    grid.setItem(4, new ItemStack(ModItems.CREATIVE_SLIME_TUNER.get()));
    h.assertTrue(
        manager.getRecipeFor(type, grid, h.getLevel()).isEmpty(),
        "A Creative tuner is not a crafting ingredient");
    grid.setItem(4, ItemStack.EMPTY);
    grid.setItem(0, new ItemStack(ModItems.SLIME_TUNER.get()));
    h.assertTrue(
        manager.getRecipeFor(type, grid, h.getLevel()).isEmpty(),
        "The tuner must occupy the center");
    grid.clearContent();
    grid.setItem(4, new ItemStack(ModItems.SLIME_TUNER.get()));
    for (int slot : new int[] {0, 2, 6, 8}) grid.setItem(slot, new ItemStack(Items.GLASS));
    h.assertTrue(
        manager.getRecipeFor(type, grid, h.getLevel()).isEmpty(),
        "Diagonal glass does not make the cross");
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
    var logging = ModEntities.LOGGING_SLIME.get().create(h.getLevel());
    var farming = ModEntities.FARMING_SLIME.get().create(h.getLevel());
    var seeds = WorkStatus.blocked(WorkStatus.Order.LOG, WorkStatus.Reason.SEEDS_MISSING);
    h.assertTrue(
        Component.Serializer.toJson(SlimeWorkWarning.reason(logging, seeds))
                .contains("saplings_missing")
            && Component.Serializer.toJson(SlimeWorkWarning.reason(farming, seeds))
                .contains("seeds_missing"),
        "Lumberjack saplings and Farming seeds remain distinct");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 160)
  public static void missingFishingSpotsHaveTheirOwnWarning(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var fishing = ModEntities.FISHING_SLIME.get().create(h.getLevel());
    fishing.tame(h.makeMockSurvivalPlayer());
    fishing.moveTo(area.point(8.5, 101, 8.5));
    fishing.onReleased();
    fishing.setItemSlot(
        net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
    SlimeStat.DETECTION_RANGE.tune(fishing, 0);
    fishing.setWorking(true);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(fishing.blockPosition()),
                    "Fishing area ticks"))
        .thenExecute(() -> h.getLevel().addFreshEntity(fishing))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    fishing.getFishingStatus() == FishingSlime.Status.NO_WATER,
                    "An empty search reports no suitable water"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  Component.Serializer.toJson(
                          SlimeWorkWarning.message(fishing, fishing.getWorkStatus()))
                      .contains("blocked.fishing_site_missing"),
                  "Missing water is distinct from failed travel");
              try {
                var status = FishingSlime.class.getDeclaredField("status");
                status.setAccessible(true);
                status.set(fishing, FishingSlime.Status.UNREACHABLE);
              } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
              }
              h.assertTrue(
                  Component.Serializer.toJson(
                          SlimeWorkWarning.message(fishing, fishing.getWorkStatus()))
                      .contains("blocked.unreachable"),
                  "A real travel failure retains its route warning");
              fishing.discard();
              area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", timeoutTicks = 160)
  public static void signalingMessagesOwnersAndSharedOwnersOncePerSignalCooldown(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var owner = new Receiver(h);
    var shared =
        new Receiver(h, h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER));
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
    var signaling = ModEntities.SIGNALING_SLIME.get().create(h.getLevel());
    signaling.moveTo(area.point(8, 101, 10));
    signaling.tame(owner);
    signaling.setMonitoring(true);
    signaling.setDelayMinutes(1);
    h.getLevel().addFreshEntity(signaling);
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
                      && signaling
                              .saveWithoutId(new CompoundTag())
                              .getCompound("HeraldWarnings")
                              .size()
                          == 1,
                  "A completed automatic signal sends the named worker and concrete cause");
              CompanionRegistry.get(h.getLevel())
                  .setRelation(owner.getUUID(), shared.getUUID(), true, false);
              SlimeWorkWarning.notifyOwners(miner);
              h.assertTrue(
                  owner.messages.size() == 2 && shared.messages.size() == 1,
                  "Current access is rechecked even for recipients in another dimension");
              miner.setWaiting(true);
              SlimeWorkWarning.notifyOwners(miner);
              h.assertTrue(
                  owner.messages.size() == 2,
                  "Manual waiting does not issue blocked-work messages");
              area.close();
            })
        .thenSucceed();
  }
}
