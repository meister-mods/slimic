package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OrderTwelveBoundaryGameTests {
  private static <T extends FriendlySlime> T pet(
      GameTestHelper h,
      LoadedWorkArea a,
      EntityType<T> type,
      Player owner,
      double x,
      double y,
      double z) {
    var pet = type.create(h.getLevel());
    pet.tame(owner);
    pet.moveTo(a.point(x, y, z));
    pet.onReleased();
    h.getLevel().addFreshEntity(pet);
    return pet;
  }

  private static <T extends net.minecraftforge.eventbus.api.Event> void listen(
      GameTestHelper h, Class<T> type, Consumer<T> listener) {
    MinecraftForge.EVENT_BUS.addListener(
        net.minecraftforge.eventbus.api.EventPriority.NORMAL, false, type, listener);
    try {
      var field = GameTestHelper.class.getDeclaredField("testInfo");
      field.setAccessible(true);
      ((GameTestInfo) field.get(h))
          .addListener(
              new GameTestListener() {
                @Override
                public void testStructureLoaded(GameTestInfo info) {}

                @Override
                public void testPassed(GameTestInfo info) {
                  MinecraftForge.EVENT_BUS.unregister(listener);
                }

                @Override
                public void testFailed(GameTestInfo info) {
                  MinecraftForge.EVENT_BUS.unregister(listener);
                }
              });
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static BlockPos pos(LoadedWorkArea a, int x, int y, int z) {
    return BlockPos.containing(a.point(x, y, z));
  }

  private static void pond(GameTestHelper h, LoadedWorkArea a) {
    for (int x = 5; x <= 11; x++)
      for (int z = 4; z <= 10; z++) {
        h.getLevel().setBlock(pos(a, x, 98, z), Blocks.BEDROCK.defaultBlockState(), 2);
        h.getLevel().setBlock(pos(a, x, 99, z), Blocks.WATER.defaultBlockState(), 2);
        h.getLevel().setBlock(pos(a, x, 100, z), Blocks.WATER.defaultBlockState(), 2);
      }
  }

  private static TunnelSlime tunnel(GameTestHelper h, LoadedWorkArea a, Player owner) {
    var tunnel = pet(h, a, ModEntities.TUNNEL_SLIME.get(), owner, 5.5, 101, 8.5);
    tunnel.setTunnelDirection(Direction.EAST);
    var pick = new ItemStack(Items.DIAMOND_PICKAXE);
    pick.enchant(Enchantments.BLOCK_EFFICIENCY, 5);
    tunnel.setTool(0, pick);
    SlimeStat.WORK_SPEED.tune(tunnel, 8);
    SlimeStat.STAMINA.tune(tunnel, 1000);
    tunnel.setTunedVitals(16, 1000, 0);
    return tunnel;
  }

  @GameTest(template = "empty", batch = "order_twelve_boundaries", timeoutTicks = 150)
  public static void protectedTunnelDrainRollsBackWithoutSpendingResources(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var worker = tunnel(h, a, h.makeMockSurvivalPlayer());
    worker.getBag().setStackInSlot(0, new ItemStack(Items.BUCKET));
    worker.getBag().setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 64));
    for (int z = 6; z <= 10; z++) {
      h.getLevel().setBlock(pos(a, 6, 104, z), Blocks.STONE.defaultBlockState(), 2);
      for (int y = 101; y < 104; y++)
        for (int edge : new int[] {6, 10})
          h.getLevel().setBlock(pos(a, 6, y, edge), Blocks.STONE.defaultBlockState(), 2);
    }
    var fluid = pos(a, 6, 101, 8);
    h.getLevel().setBlock(fluid, Blocks.WATER.defaultBlockState(), 2);
    var calls = new AtomicInteger();
    listen(
        h,
        BlockEvent.EntityPlaceEvent.class,
        event -> {
          if (event.getPos().equals(fluid)) {
            calls.incrementAndGet();
            event.setCanceled(true);
          }
        });
    worker.setWorking(true);
    h.startSequence()
        .thenWaitUntil(
            () -> h.assertTrue(calls.get() > 0, "Fluid removal invokes placement protection"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getFluidState(fluid).isSource()
                      && worker.hasBucket()
                      && worker.getStamina() == 1000
                      && worker.getActivityProgress() == 0,
                  "Denied fluid removal restores water and spends no resources");
              worker.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_twelve_boundaries", timeoutTicks = 1000)
  public static void anInaccessibleBoreReportsACommonUnreachableState(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var worker = tunnel(h, a, h.makeMockSurvivalPlayer());
    worker.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
    for (int y = 101; y <= 104; y++)
      for (int x = 0; x <= 4; x++)
        for (int z = 6; z <= 10; z++)
          if (x == 0 || x == 4 || z == 6 || z == 10 || y == 104)
            h.getLevel().setBlock(pos(a, x, y, z), Blocks.BEDROCK.defaultBlockState(), 2);
    worker.moveTo(a.point(2.5, 101, 8.5));
    worker.setWorking(true);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    worker.getWorkStatus().reason() == WorkStatus.Reason.UNREACHABLE,
                    "No approach progress becomes a shared blocked state; status="
                        + worker.getTunnelStatus()
                        + ", at="
                        + worker.position()))
        .thenExecute(
            () -> {
              h.assertTrue(
                  worker.isWorking() && worker.getTunnelStatus() == TunnelSlime.Status.UNREACHABLE,
                  "Herald monitoring can distinguish a blocked active order from Wait");
              worker.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_twelve_boundaries", timeoutTicks = 250)
  public static void miningMendingUsesNativeOreExperienceBeforeSpawningOrbs(GameTestHelper h) {
    mendingOre(h, false);
  }

  @GameTest(template = "empty", batch = "order_twelve_boundaries", timeoutTicks = 250)
  public static void tunnelMendingUsesNativeOreExperienceBeforeSpawningOrbs(GameTestHelper h) {
    mendingOre(h, true);
  }

  private static void mendingOre(GameTestHelper h, boolean bore) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    MiningSlime worker;
    BlockPos ore;
    if (bore) {
      worker = tunnel(h, a, owner);
      for (int y = 101; y <= 104; y++)
        for (int z = 6; z <= 10; z++)
          h.getLevel().setBlock(pos(a, 6, y, z), Blocks.STONE.defaultBlockState(), 2);
      ore = pos(a, 6, 103, 7);
    } else {
      for (int y = 101; y < h.getLevel().getMaxBuildHeight(); y++)
        for (int i = 0; i < 16; i++)
          for (var wall :
              java.util.List.of(
                  pos(a, -1, y, i), pos(a, 16, y, i), pos(a, i, y, -1), pos(a, i, y, 16)))
            h.getLevel().setBlock(wall, Blocks.BEDROCK.defaultBlockState(), 2);
      worker = pet(h, a, ModEntities.MINING_SLIME.get(), owner, 7.5, 101, 7.5);
      ore = pos(a, 8, 102, 8);
    }
    var pick = new ItemStack(Items.DIAMOND_PICKAXE);
    pick.setDamageValue(20);
    pick.enchant(Enchantments.MENDING, 1);
    pick.enchant(Enchantments.BLOCK_EFFICIENCY, 5);
    worker.setTool(0, pick);
    worker.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
    h.getLevel().setBlock(ore, Blocks.DIAMOND_ORE.defaultBlockState(), 2);
    worker.setWorking(true);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    java.util.stream.IntStream.range(0, worker.getCapacity())
                        .anyMatch(i -> worker.getBag().getStackInSlot(i).is(Items.DIAMOND)),
                    "Native ore is mined"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  worker.getPickaxe().getDamageValue() < 20,
                  "Mending spends actual ore experience on the working tool");
              h.assertTrue(
                  h.getLevel()
                      .getEntitiesOfClass(
                          net.minecraft.world.entity.ExperienceOrb.class,
                          new net.minecraft.world.phys.AABB(ore).inflate(4))
                      .isEmpty(),
                  "Experience spent on repair is not duplicated as orbs");
              worker.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_twelve_boundaries", timeoutTicks = 6500)
  public static void fiveMinutesWithoutACatchMovesToADifferentBank(GameTestHelper h) {
    var a = new LoadedWorkArea(h, 6500);
    pond(h, a);
    var owner = h.makeMockSurvivalPlayer();
    var fisher = pet(h, a, ModEntities.FISHING_SLIME.get(), owner, 3.5, 101, 7.5);
    var rod = new ItemStack(Items.FISHING_ROD);
    rod.enchant(Enchantments.UNBREAKING, 3);
    fisher.setRod(rod);
    listen(
        h,
        ItemFishedEvent.class,
        event -> {
          if (event.getHookEntity().getPlayerOwner() != null
              && event.getHookEntity().getPlayerOwner().getUUID().equals(owner.getUUID()))
            event.setCanceled(true);
        });
    fisher.setWorking(true);
    BlockPos[] firstBank = {null};
    long[] started = {0};
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    fisher.getFishingStatus() == FishingSlime.Status.FISHING,
                    "First shore is reached"))
        .thenExecute(
            () -> {
              firstBank[0] = fisher.getFishingBank();
              started[0] = h.getTick();
            })
        .thenIdle(5800)
        .thenExecute(
            () ->
                h.assertTrue(
                    firstBank[0].equals(fisher.getFishingBank()),
                    "An unsuccessful catch does not relocate early"))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    fisher.getFishingStatus() == FishingSlime.Status.FISHING
                        && !firstBank[0].equals(fisher.getFishingBank()),
                    "A full five-minute interval finds and reaches another bank"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getTick() - started[0] >= 5800
                      && firstBank[0].distSqr(fisher.getFishingBank()) >= 4
                      && !fisher.hasCargo(),
                  "Relocation changes the shore without inventing a cancelled catch");
              fisher.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_twelve_boundaries", timeoutTicks = 250)
  public static void activeWaitingMeowUsesNativeCreeperAvoidanceAndDisablingStopsIt(
      GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var cat = pet(h, a, ModEntities.MEOW_SLIME.get(), owner, 6.5, 101, 8.5);
    cat.setNoAi(true);
    for (int dx = -16; dx < 32; dx++)
      for (int dz = -16; dz < 32; dz++) {
        h.getLevel().setBlock(pos(a, dx, 100, dz), Blocks.BEDROCK.defaultBlockState(), 2);
        for (int y = 101; y <= 107; y++)
          h.getLevel().setBlock(pos(a, dx, y, dz), Blocks.AIR.defaultBlockState(), 2);
      }
    var creeper = EntityType.CREEPER.create(h.getLevel());
    creeper.moveTo(a.point(8.5, 101, 8.5));
    h.getLevel().addFreshEntity(creeper);
    boolean[] observed = {false};
    h.onEachTick(
        () -> {
          if (creeper
              .goalSelector
              .getRunningGoals()
              .anyMatch(
                  g ->
                      g.getGoal() instanceof AvoidEntityGoal<?>
                          && g.getGoal().getClass().getEnclosingClass() == MeowSlime.class))
            observed[0] = true;
        });
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    observed[0] && creeper.distanceToSqr(cat) > 9,
                    "Waiting active Meow makes the native goal move the creeper away; active="
                        + cat.isActive()
                        + ", distance="
                        + creeper.distanceToSqr(cat)
                        + ", observed="
                        + observed[0]))
        .thenExecute(
            () -> {
              cat.setActive(false);
              creeper.moveTo(a.point(8.5, 101, 8.5));
            })
        .thenIdle(10)
        .thenExecute(
            () -> {
              h.assertTrue(
                  creeper
                      .goalSelector
                      .getRunningGoals()
                      .noneMatch(
                          g ->
                              g.getGoal() instanceof AvoidEntityGoal<?>
                                  && g.getGoal().getClass().getEnclosingClass() == MeowSlime.class),
                  "Inactive aura releases native avoidance");
              cat.discard();
              creeper.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_twelve_boundaries", timeoutTicks = 300)
  public static void cancelledGrowthPreservesStaminaAndTheCrop(GameTestHelper h) {
    growthHook(h, true);
  }

  @GameTest(template = "empty", batch = "order_twelve_boundaries", timeoutTicks = 300)
  public static void growthCallbackCanPauseTheWorkerBeforeAnyWorldEdit(GameTestHelper h) {
    growthHook(h, false);
  }

  private static void growthHook(GameTestHelper h, boolean cancel) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var crop = pos(a, 8, 101, 8);
    h.getLevel().setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState(), 2);
    h.getLevel().setBlock(crop.above(2), Blocks.SEA_LANTERN.defaultBlockState(), 2);
    var slime = pet(h, a, ModEntities.ABUNDANCE_SLIME.get(), owner, 6.5, 101, 8.5);
    var calls = new AtomicInteger();
    listen(
        h,
        BonemealEvent.class,
        event -> {
          if (!event.getPos().equals(crop)) return;
          calls.incrementAndGet();
          if (cancel) event.setCanceled(true);
          else {
            slime.setWorking(false);
            slime.setWaiting(true);
          }
        });
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    Blocks.WHEAT.defaultBlockState().canSurvive(h.getLevel(), crop),
                    "Crop site supports native survival"))
        .thenExecute(
            () -> {
              h.getLevel().setBlock(crop, Blocks.WHEAT.defaultBlockState(), 2);
              slime.setWorking(true);
            })
        .thenWaitUntil(() -> h.assertTrue(calls.get() > 0, "Native bonemeal callback runs"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockState(crop).getValue(CropBlock.AGE) == 0
                      && slime.getStamina() == 100
                      && slime.getActivityProgress() == 0,
                  "Denied or paused growth changes neither crop nor resources");
              slime.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_twelve_boundaries", timeoutTicks = 250)
  public static void cancelledTunnelLiningRestoresTerrainAndExactMaterialCount(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var tunnel = tunnel(h, a, h.makeMockSurvivalPlayer());
    tunnel.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
    var calls = new AtomicInteger();
    listen(
        h,
        BlockEvent.EntityPlaceEvent.class,
        event -> {
          if (event.getEntity() != null
              && event.getEntity().getUUID().equals(tunnel.getOwnerUUID())
              && event.getPos().getX() == tunnel.getTunnelFront().getX()) {
            calls.incrementAndGet();
            event.setCanceled(true);
          }
        });
    tunnel.setWorking(true);
    h.startSequence()
        .thenWaitUntil(() -> h.assertTrue(calls.get() > 0, "Protected lining callback runs"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  tunnel.getBag().getStackInSlot(0).getCount() == 64
                      && tunnel.getLiningTemplate().getCount() == 1
                      && tunnel.getStamina() == 1000
                      && tunnel.getActivityProgress() == 0,
                  "Cancelled placement consumes no real or template material");
              h.assertTrue(
                  h.getLevel().getBlockState(pos(a, 6, 104, 7)).isAir(),
                  "Original roof gap is restored");
              tunnel.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_twelve_boundaries", timeoutTicks = 400)
  public static void tunnelCanClearAStoneFaceBeforeReachingItsOpenCeiling(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var tunnel = tunnel(h, a, h.makeMockSurvivalPlayer());
    tunnel.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
    for (int y = 101; y <= 103; y++)
      for (int z = 7; z <= 9; z++)
        h.getLevel().setBlock(pos(a, 6, y, z), Blocks.STONE.defaultBlockState(), 2);
    tunnel.setWorking(true);
    h.startSequence()
        .thenWaitUntil(
            () -> {
              for (int y = 101; y <= 103; y++)
                for (int z = 7; z <= 9; z++)
                  h.assertTrue(
                      h.getLevel().getBlockState(pos(a, 6, y, z)).isAir(),
                      "Face clears despite initially occluded roof gaps: "
                          + tunnel.getTunnelStatus());
              for (int z = 7; z <= 9; z++)
                h.assertTrue(
                    h.getLevel().getBlockState(pos(a, 6, 104, z)).is(Blocks.COBBLESTONE),
                    "Open roof receives real lining");
            })
        .thenExecute(
            () -> {
              h.assertTrue(
                  tunnel.getLiningTemplate().getCount() == 1,
                  "Template survives real construction");
              tunnel.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_twelve_boundaries", timeoutTicks = 250)
  public static void tunnelUsesSilkTouchFromItsActualEquippedTool(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var tunnel = tunnel(h, a, h.makeMockSurvivalPlayer());
    tunnel.getPickaxe().enchant(Enchantments.SILK_TOUCH, 1);
    tunnel.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
    for (int y = 101; y <= 104; y++)
      for (int z = 6; z <= 10; z++)
        h.getLevel().setBlock(pos(a, 6, y, z), Blocks.DIAMOND_ORE.defaultBlockState(), 2);
    tunnel.setWorking(true);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    java.util.stream.IntStream.range(0, tunnel.getCapacity())
                        .anyMatch(i -> tunnel.getBag().getStackInSlot(i).is(Items.DIAMOND_ORE)),
                    "Silk Touch produces ore rather than diamonds"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  java.util.stream.IntStream.range(0, tunnel.getCapacity())
                          .noneMatch(i -> tunnel.getBag().getStackInSlot(i).is(Items.DIAMOND))
                      && tunnel.getPickaxe().getDamageValue() > 0,
                  "Native exact-tool loot and wear applied");
              tunnel.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_twelve_boundaries", timeoutTicks = 400)
  public static void tunnelWaitsForACarrierBucketAndNeverConsumesOrFillsIt(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var tunnel = tunnel(h, a, owner);
    tunnel.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
    for (int x = 6; x <= 8; x++)
      for (int y = 101; y <= 104; y++)
        for (int z = 6; z <= 10; z++)
          h.getLevel().setBlock(pos(a, x, y, z), Blocks.STONE.defaultBlockState(), 2);
    var fluid = pos(a, 6, 101, 8);
    h.getLevel().setBlock(fluid, Blocks.WATER.defaultBlockState(), 2);
    var chest = a.chest(3, 101, 6);
    chest.inventory().insertItem(0, new ItemStack(Items.BUCKET), false);
    tunnel.setWorking(true);
    var carrier = pet(h, a, ModEntities.TRANSPORT_SLIME.get(), owner, 4.5, 101, 7.5);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    tunnel.getWorkStatus().requests(WorkStatus.Request.BUCKET_SUPPLY),
                    "Fluid without bucket stops the tunnel and requests supply"))
        .thenExecute(() -> carrier.setWorking(true))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    tunnel.hasBucket()
                        && h.getLevel().getBlockState(fluid).getFluidState().isEmpty(),
                    "One delivered bucket enables fluid removal; miner="
                        + tunnel.getTunnelStatus()
                        + " at="
                        + tunnel.position()
                        + ", bucket="
                        + tunnel.hasBucket()
                        + ", carrier="
                        + carrier.getTransportStatus()
                        + " at="
                        + carrier.position()
                        + ", cargo="
                        + carrier.hasCargo()))
        .thenExecute(
            () -> {
              int buckets =
                  java.util.stream.IntStream.range(0, tunnel.getCapacity())
                      .map(
                          i ->
                              tunnel.getBag().getStackInSlot(i).is(Items.BUCKET)
                                  ? tunnel.getBag().getStackInSlot(i).getCount()
                                  : 0)
                      .sum();
              h.assertTrue(
                  buckets == 1
                      && chest.inventory().getStackInSlot(0).isEmpty()
                      && java.util.stream.IntStream.range(0, tunnel.getCapacity())
                          .noneMatch(i -> tunnel.getBag().getStackInSlot(i).is(Items.WATER_BUCKET)),
                  "Bucket stays empty and is transferred exactly once");
              carrier.discard();
              tunnel.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_twelve_boundaries", timeoutTicks = 300)
  public static void pendingFishingLootSurvivesRecoveryAndTwoSeparateUnloadings(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    pond(h, a);
    var owner = h.makeMockSurvivalPlayer();
    var fisher = pet(h, a, ModEntities.FISHING_SLIME.get(), owner, 3.5, 101, 7.5);
    fisher.setRod(new ItemStack(Items.FISHING_ROD));
    SlimeStat.WORK_SPEED.tune(fisher, 32);
    SlimeStat.CAPACITY.tune(fisher, 1);
    fisher.getBag().setStackInSlot(0, new ItemStack(Items.STONE, 64));
    var calls = new AtomicInteger();
    listen(
        h,
        ItemFishedEvent.class,
        event -> {
          if (event.getHookEntity().getPlayerOwner() == null
              || !event.getHookEntity().getPlayerOwner().getUUID().equals(owner.getUUID())) return;
          calls.incrementAndGet();
          event.getDrops().clear();
          event.getDrops().add(new ItemStack(Items.DIAMOND, 64));
          event.getDrops().add(new ItemStack(Items.EMERALD, 64));
        });
    fisher.setWorking(true);
    ItemStack[] saved = {ItemStack.EMPTY};
    FishingSlime[] restored = {null};
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    fisher.getFishingStatus() == FishingSlime.Status.FULL,
                    "Full bag retains generated catches"))
        .thenExecute(
            () -> {
              saved[0] = FriendlySlimeItem.fromSlime(fisher);
              fisher.discard();
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getEntity(fisher.getUUID()) == null,
                    "Old identity leaves the level"))
        .thenExecute(
            () -> {
              restored[0] = ModEntities.FISHING_SLIME.get().create(h.getLevel());
              var data = saved[0].getTag().getCompound("SlimeData").copy();
              data.putDouble("FishingWait", 1200);
              restored[0].load(data);
              restored[0].moveTo(a.point(3.5, 101, 7.5));
              restored[0].onReleased();
              restored[0].getBag().setStackInSlot(0, ItemStack.EMPTY);
              h.assertTrue(
                  h.getLevel().addFreshEntity(restored[0]), "Same identity can be restored");
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    restored[0].getBag().getStackInSlot(0).is(Items.DIAMOND)
                        && restored[0].getBag().getStackInSlot(0).getCount() == 64,
                    "First pending stack survives recovery"))
        .thenExecute(() -> restored[0].getBag().setStackInSlot(0, ItemStack.EMPTY))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    restored[0].getBag().getStackInSlot(0).is(Items.EMERALD)
                        && restored[0].getBag().getStackInSlot(0).getCount() == 64,
                    "Second pending stack waits for another unloading"))
        .thenExecute(
            () -> {
              h.assertTrue(calls.get() == 1, "Recovery does not reroll or duplicate a catch");
              restored[0].discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_twelve_boundaries", timeoutTicks = 300)
  public static void fishingMendingRepairsItsRodAndNativeTreasureContextCanSeeOpenWater(
      GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    pond(h, a);
    var owner = h.makeMockSurvivalPlayer();
    var fisher = pet(h, a, ModEntities.FISHING_SLIME.get(), owner, 3.5, 101, 7.5);
    var rod = new ItemStack(Items.FISHING_ROD);
    rod.enchant(Enchantments.MENDING, 1);
    rod.enchant(Enchantments.FISHING_LUCK, 3);
    rod.enchant(Enchantments.FISHING_SPEED, 3);
    rod.setDamageValue(14);
    fisher.setRod(rod);
    SlimeStat.WORK_SPEED.tune(fisher, 8);
    boolean[] open = {false};
    listen(
        h,
        ItemFishedEvent.class,
        event -> {
          if (event.getHookEntity().getPlayerOwner() != null
              && event.getHookEntity().getPlayerOwner().getUUID().equals(owner.getUUID()))
            open[0] |= event.getHookEntity().isOpenWaterFishing();
        });
    fisher.setWorking(true);
    h.startSequence()
        .thenWaitUntil(() -> h.assertTrue(fisher.hasCargo(), "Enchanted rod catches native loot"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  fisher.getRod().getDamageValue() < 14 && open[0],
                  "Mending repairs wear and open water reaches the native hook loot context");
              fisher.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_twelve_boundaries")
  public static void aFullCombatBagDoesNotPreventSwordDamageOrItsFireAspect(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var fighter = pet(h, a, ModEntities.COMBAT_SLIME.get(), owner, 6.5, 101, 8.5);
    var sword = new ItemStack(Items.DIAMOND_SWORD);
    sword.enchant(Enchantments.FIRE_ASPECT, 2);
    sword.enchant(Enchantments.SHARPNESS, 3);
    fighter.setEquipment(0, sword);
    fighter.setWorking(true);
    for (int i = 0; i < fighter.getCapacity(); i++)
      fighter.getBag().setStackInSlot(i, new ItemStack(Items.STONE, 64));
    var zombie = EntityType.ZOMBIE.create(h.getLevel());
    zombie.moveTo(a.point(7.5, 101, 8.5));
    zombie.setNoAi(true);
    zombie.setItemSlot(
        net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
    h.getLevel().addFreshEntity(zombie);
    fighter.setNoAi(true);
    h.startSequence()
        .thenIdle(2)
        .thenExecute(
            () -> {
              h.assertTrue(
                  fighter.doHurtTarget(zombie)
                      && zombie.getHealth() < 12
                      && zombie.isOnFire()
                      && fighter.getWorkStatus().requests(WorkStatus.Request.CARGO_PICKUP)
                      && !fighter.isWorkBlocked(),
                  "Full cargo preserves enchanted melee and unloading; hp="
                      + zombie.getHealth()
                      + ", fire="
                      + zombie.isOnFire()
                      + ", status="
                      + fighter.getWorkStatus());
              fighter.discard();
              zombie.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_twelve_boundaries", timeoutTicks = 100)
  public static void dragonBreathCannotCrossSolidWallsOrDisabledTargetFilters(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var dragon = pet(h, a, ModEntities.DRAGON_SLIME.get(), owner, 5.5, 101, 8.5);
    dragon.setEquipment(0, new ItemStack(Items.IRON_SWORD));
    dragon.setWorking(true);
    dragon.setNoAi(true);
    var zombie = EntityType.ZOMBIE.create(h.getLevel());
    zombie.moveTo(a.point(10.5, 101, 8.5));
    zombie.setNoAi(true);
    zombie.setItemSlot(
        net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
    zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
    zombie.setHealth(100);
    h.getLevel().addFreshEntity(zombie);
    for (int y = 101; y <= 103; y++)
      h.getLevel().setBlock(pos(a, 8, y, 8), Blocks.STONE.defaultBlockState(), 2);
    dragon.setTarget(zombie);
    h.startSequence()
        .thenIdle(20)
        .thenExecute(
            () -> {
              h.assertTrue(
                  zombie.getHealth() == 100 && !zombie.isOnFire(),
                  "Breath does not pass through a wall");
              dragon.setAllowed(CombatTarget.HOSTILE, false);
              for (int y = 101; y <= 103; y++)
                h.getLevel().setBlock(pos(a, 8, y, 8), Blocks.AIR.defaultBlockState(), 2);
              dragon.setTarget(zombie);
            })
        .thenIdle(20)
        .thenExecute(
            () -> {
              h.assertTrue(
                  zombie.getHealth() == 100 && dragon.getStamina() == 100,
                  "Disabled targets take no breath or stamina charge");
              dragon.discard();
              zombie.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_twelve_boundaries", timeoutTicks = 300)
  public static void waitStopsAnActiveDragonFlight(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var s = ModEntities.DRAGON_SLIME.get().create(h.getLevel());
    s.tame(owner);
    s.moveTo(a.point(5.5, 104, 8.5));
    s.setEquipment(0, new ItemStack(Items.IRON_SWORD));
    s.setWorking(true);
    s.onReleased();
    h.getLevel().addFreshEntity(s);
    var z = EntityType.ZOMBIE.create(h.getLevel());
    z.moveTo(a.point(12.5, 106, 8.5));
    z.setNoAi(true);
    z.setNoGravity(true);
    z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
        .setBaseValue(1000);
    z.setHealth(1000);
    z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
    h.getLevel().addFreshEntity(z);
    net.minecraft.world.phys.Vec3[] point = {null};
    h.startSequence()
        .thenWaitUntil(() -> h.assertTrue(s.getDeltaMovement().lengthSqr() > .001, "Flight begins"))
        .thenExecute(
            () -> {
              s.setWorking(false);
              s.setWaiting(true);
              point[0] = s.position();
            })
        .thenIdle(40)
        .thenExecute(
            () -> {
              h.assertTrue(
                  s.position().distanceToSqr(point[0]) < .04,
                  "Wait cancels prior flight; moved=" + s.position().distanceTo(point[0]));
              s.discard();
              z.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_twelve_boundaries", timeoutTicks = 1100)
  public static void aFreshWaitingDragonNeverWanders(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var s = ModEntities.DRAGON_SLIME.get().create(h.getLevel());
    s.tame(h.makeMockSurvivalPlayer());
    s.moveTo(a.point(8.5, 104, 8.5));
    s.onReleased();
    h.getLevel().addFreshEntity(s);
    var start = s.position();
    h.startSequence()
        .thenIdle(1000)
        .thenExecute(
            () -> {
              h.assertTrue(
                  s.position().distanceToSqr(start) < .01,
                  "Fresh Wait stays in place; moved=" + s.position().distanceTo(start));
              s.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_twelve_boundaries", timeoutTicks = 8000)
  public static void defaultStaminaTraversesATwentyPlaneWaterVolume(GameTestHelper h) {
    var a = new LoadedWorkArea(h, 8000);
    var s = ModEntities.TUNNEL_SLIME.get().create(h.getLevel());
    s.tame(h.makeMockSurvivalPlayer());
    s.moveTo(a.point(5.5, 101, 8.5));
    s.onReleased();
    s.setTunnelDirection(Direction.EAST);
    s.setTool(0, new ItemStack(Items.DIAMOND_PICKAXE));
    s.getBag().setStackInSlot(0, new ItemStack(Items.BUCKET));
    s.getBag().setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 64));
    SlimeStat.STAMINA.tune(s, 100);
    s.setTunedVitals(16, 100, 0);
    for (int x = 6; x <= 26; x++)
      for (int y = 101; y <= 104; y++)
        for (int z = 6; z <= 10; z++)
          h.getLevel()
              .setBlock(
                  BlockPos.containing(a.point(x, y, z)),
                  (x == 26
                          ? Blocks.BEDROCK
                          : y < 104 && z > 6 && z < 10 ? Blocks.WATER : Blocks.STONE)
                      .defaultBlockState(),
                  2);
    for (int x = 6; x <= 26; x++)
      for (int z = 6; z <= 10; z++)
        h.getLevel()
            .setBlock(
                BlockPos.containing(a.point(x, 100, z)), Blocks.BEDROCK.defaultBlockState(), 2);
    s.setWorking(true);
    h.getLevel().addFreshEntity(s);
    h.startSequence()
        .thenWaitUntil(
            () -> {
              for (int x = 6; x <= 25; x++)
                for (int y = 101; y <= 103; y++)
                  for (int z = 7; z <= 9; z++)
                    h.assertTrue(
                        h.getLevel().getBlockState(BlockPos.containing(a.point(x, y, z))).isAir(),
                        "Whole volume clears; status="
                            + s.getTunnelStatus()
                            + ", pos="
                            + s.position()
                            + ", front="
                            + s.getTunnelFront()
                            + ", energy="
                            + s.getStamina());
            })
        .thenExecute(
            () -> {
              h.assertTrue(s.hasBucket(), "Bucket remains reusable");
              s.discard();
              a.close();
            })
        .thenSucceed();
  }
}
