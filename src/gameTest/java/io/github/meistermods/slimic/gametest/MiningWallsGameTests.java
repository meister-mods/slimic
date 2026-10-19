package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;
import net.minecraftforge.items.IItemHandler;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MiningWallsGameTests {
  private record Site(LoadedWorkArea area, MiningSlime miner, BlockPos inlet) {
    void close() {
      miner.discard();
      area.close();
    }
  }

  private static Site site(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    for (int i = -1; i <= 16; i++)
      for (int y = 98; y <= 104; y++)
        for (var p :
            List.of(a.point(-1, y, i), a.point(16, y, i), a.point(i, y, -1), a.point(i, y, 16)))
          h.getLevel().setBlock(BlockPos.containing(p), Blocks.BEDROCK.defaultBlockState(), 2);
    var m = ModEntities.MINING_SLIME.get().create(h.getLevel());
    m.tame(h.makeMockSurvivalPlayer());
    m.moveTo(a.point(14, 101, 8.5));
    m.setTool(0, new ItemStack(Items.IRON_PICKAXE));
    m.onReleased();
    m.setWorking(true);
    return new Site(a, m, BlockPos.containing(a.point(16, 101, 8)));
  }

  private static GameTestSequence start(GameTestHelper h, Site s) {
    return h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(s.inlet), "Work and adjacent chunks tick"))
        .thenExecute(() -> h.getLevel().addFreshEntity(s.miner));
  }

  private static int count(IItemHandler bag) {
    int n = 0;
    for (int i = 0; i < bag.getSlots(); i++)
      if (bag.getStackInSlot(i).is(Items.COBBLESTONE)) n += bag.getStackInSlot(i).getCount();
    return n;
  }

  private static void fluid(GameTestHelper h, Site s, BlockState state) {
    h.getLevel().setBlockAndUpdate(s.inlet, state);
  }

  @GameTest(template = "empty", batch = "walls", timeoutTicks = 180)
  public static void missingCobblestoneStopsForAnExplicitSupplyRequest(GameTestHelper h) {
    var s = site(h);
    fluid(h, s, Blocks.WATER.defaultBlockState());
    start(h, s)
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    s.miner.getWorkStatus().requests(WorkStatus.Request.COBBLESTONE_SUPPLY),
                    "Missing wall material request"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  s.miner.getWorkStatus().reason() == WorkStatus.Reason.COBBLESTONE_MISSING
                      && s.miner.getActivityProgress() == 0
                      && h.getLevel().getBlockState(s.inlet).is(Blocks.WATER),
                  "Pause without removing the inlet or charging work");
              h.assertTrue(
                  s.miner.workSupplyLimit() == 64
                      && !s.miner.acceptsWorkTool(new ItemStack(Items.DIRT)),
                  "Only cobblestone, up to a stack");
              s.miner.setWaiting(true);
              h.assertTrue(!s.miner.needsWorkSupply(), "Wait withdraws wall supply requests");
              s.close();
            })
        .thenSucceed();
  }

  private static void sealsFluid(GameTestHelper h, BlockState state) {
    var s = site(h);
    fluid(h, s, state);
    s.miner.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 3));
    start(h, s)
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getBlockState(s.inlet).is(Blocks.COBBLESTONE),
                    "Side inlet sealed: " + s.miner.getMiningStatus() + " " + s.miner.position()))
        .thenExecute(
            () -> {
              h.assertTrue(
                  count(s.miner.getBag()) == 2
                      && s.miner.getActivityProgress() == 1
                      && s.miner.getStamina() == 99
                      && !s.miner.hasBucket(),
                  "One wall cell costs one cobblestone and stamina; no bucket required to place a"
                      + " wall");
              h.assertTrue(
                  s.miner.getPickaxe().getDamageValue() == 0
                      && s.miner.getQuarryPlan().contains(s.miner.blockPosition()),
                  "No tool wear or movement outside quarry");
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "walls", timeoutTicks = 240)
  public static void sealsSideWaterWithOneCobblestone(GameTestHelper h) {
    sealsFluid(h, Blocks.WATER.defaultBlockState());
  }

  @GameTest(template = "empty", batch = "walls", timeoutTicks = 240)
  public static void sealsSideLavaWithOneCobblestone(GameTestHelper h) {
    sealsFluid(h, Blocks.LAVA.defaultBlockState());
  }

  @GameTest(template = "empty", batch = "walls", timeoutTicks = 200)
  public static void canceledWallPlacementRestoresFluidAndChargesNothing(GameTestHelper h) {
    var s = site(h);
    fluid(h, s, Blocks.WATER.defaultBlockState());
    s.miner.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 3));
    int[] calls = {0};
    Consumer<BlockEvent.EntityPlaceEvent> cancel =
        e -> {
          if (e.getPos().equals(s.inlet)) {
            calls[0]++;
            e.setCanceled(true);
          }
        };
    MinecraftForge.EVENT_BUS.addListener(cancel);
    h.runAfterDelay(195, () -> MinecraftForge.EVENT_BUS.unregister(cancel));
    start(h, s)
        .thenWaitUntil(() -> h.assertTrue(calls[0] > 0, "Wall placement event"))
        .thenExecute(
            () -> {
              MinecraftForge.EVENT_BUS.unregister(cancel);
              h.assertTrue(
                  h.getLevel().getBlockState(s.inlet).is(Blocks.WATER)
                      && count(s.miner.getBag()) == 3
                      && s.miner.getActivityProgress() == 0
                      && s.miner.getStamina() == 100,
                  "Protection restores the exact inlet without resource costs");
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "walls", timeoutTicks = 180)
  public static void openSurfaceAndSingleUndergroundGapNeedNoWalls(GameTestHelper h) {
    var s = site(h);
    h.getLevel().setBlockAndUpdate(s.inlet, Blocks.AIR.defaultBlockState());
    h.getLevel().setBlockAndUpdate(s.inlet.below(), Blocks.AIR.defaultBlockState());
    for (int z = 4; z < 7; z++)
      for (int y = 101; y <= 102; y++)
        h.getLevel()
            .setBlockAndUpdate(
                BlockPos.containing(s.area.point(16, y, z)), Blocks.AIR.defaultBlockState());
    start(h, s)
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    s.miner.getMiningStatus() == MiningSlime.Status.COMPLETE,
                    "Open air above entry and a one-wide gap do not demand materials: "
                        + s.miner.getMiningStatus()))
        .thenExecute(
            () -> {
              h.assertTrue(
                  s.miner.getActivityProgress() == 0 && h.getLevel().getBlockState(s.inlet).isAir(),
                  "No surface fences or gratuitous wall work");
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "walls", timeoutTicks = 360)
  public static void completesTwoByTwoCaveWallAfterRecovery(GameTestHelper h) {
    var s = site(h);
    var cells = new ArrayList<BlockPos>();
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++)
        h.getLevel()
            .setBlock(
                BlockPos.containing(s.area.point(x, 99, z)), Blocks.BEDROCK.defaultBlockState(), 2);
    for (int x = 14; x < 16; x++)
      for (int z = 7; z < 11; z++) {
        h.getLevel()
            .setBlock(
                BlockPos.containing(s.area.point(x, 98, z)), Blocks.BEDROCK.defaultBlockState(), 2);
        for (int y = 99; y <= 100; y++)
          h.getLevel()
              .setBlock(
                  BlockPos.containing(s.area.point(x, y, z)), Blocks.AIR.defaultBlockState(), 2);
      }
    for (int z = 8; z <= 9; z++)
      for (int y = 99; y <= 100; y++) {
        var pos = BlockPos.containing(s.area.point(16, y, z));
        cells.add(pos);
        h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
      }
    while (s.miner.getQuarryPlan().layer() > 99) s.miner.getQuarryPlan().descend();
    s.miner.moveTo(s.area.point(15, 99, 8.5));
    s.miner.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 4));
    var restored = ModEntities.MINING_SLIME.get().create(h.getLevel());
    start(h, s)
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    s.miner.getActivityProgress() == 1,
                    "First cave cell placed: " + s.miner.getMiningStatus()))
        .thenExecute(
            () -> {
              var tag = FriendlySlimeItem.fromSlime(s.miner).getTag().getCompound("SlimeData");
              h.assertTrue(
                  tag.getLongArray("MiningWallPending").length == 3,
                  "Remaining L-shaped job saved");
              restored.load(tag);
              s.miner.discard();
              restored.onReleased();
              h.getLevel().addFreshEntity(restored);
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    cells.stream()
                        .allMatch(p -> h.getLevel().getBlockState(p).is(Blocks.COBBLESTONE)),
                    "Restored complete cave lining: "
                        + restored.getMiningStatus()
                        + " at "
                        + restored.position()))
        .thenExecute(
            () -> {
              h.assertTrue(
                  restored.getActivityProgress() == 4
                      && count(restored.getBag()) == 0
                      && restored.getUUID().equals(s.miner.getUUID()),
                  "All four cells, exact costs and same recovered identity");
              restored.discard();
              s.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "walls", timeoutTicks = 600)
  public static void transportDeliversAtMostOneStackAndResumesWallWork(GameTestHelper h) {
    deliver(h, 64);
  }

  @GameTest(template = "empty", batch = "walls", timeoutTicks = 600)
  public static void transportUsesAvailablePartialStockWithoutWaitingForSixtyFour(
      GameTestHelper h) {
    deliver(h, 7);
  }

  private static void deliver(GameTestHelper h, int stock) {
    var s = site(h);
    fluid(h, s, Blocks.WATER.defaultBlockState());
    var chest = s.area.chest(10, 101, 7);
    var supply = new ItemStack(Items.COBBLESTONE, stock);
    supply.getOrCreateTag().putString("Supply", "original");
    chest.setItem(0, supply.copy());
    chest.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
    var carrier = s.area.carrier(new Vec3(11.5, 101, 8.5));
    carrier.setOwnerUUID(s.miner.getOwnerUUID());
    start(h, s)
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getBlockState(s.inlet).is(Blocks.COBBLESTONE),
                    "Wall supply and placement: "
                        + s.miner.getMiningStatus()
                        + " / "
                        + carrier.getTransportStatus()))
        .thenExecute(
            () -> {
              s.miner.setWaiting(true);
              h.assertTrue(
                  count(s.miner.getBag()) == stock - 1
                      && count(chest.inventory()) == 64
                      && !carrier.hasCargo(),
                  "Exactly one source stack delivered; spare stack stays in chest");
              h.assertTrue(
                  s.miner.getBag().getStackInSlot(0).getTag().equals(supply.getTag()),
                  "Cobblestone data preserved");
              carrier.discard();
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "walls", timeoutTicks = 240)
  public static void limitedWorkerSpaceAcceptsOnlyWhatFits(GameTestHelper h) {
    var s = site(h);
    fluid(h, s, Blocks.WATER.defaultBlockState());
    start(h, s)
        .thenWaitUntil(() -> h.assertTrue(s.miner.needsWorkSupply(), "Wall supply needed"))
        .thenExecute(
            () -> {
              for (int i = 0; i < s.miner.getCapacity() - 1; i++)
                s.miner.getBag().setStackInSlot(i, new ItemStack(Items.DIRT, 64));
              h.getLevel().setBlockAndUpdate(s.inlet.below(), Blocks.DIRT.defaultBlockState());
              h.getLevel().setBlockAndUpdate(s.inlet, Blocks.GRASS.defaultBlockState());
              h.assertTrue(
                  h.getLevel().getBlockState(s.inlet).is(Blocks.GRASS) && s.miner.needsWorkSupply(),
                  "Replaceable vegetation does not invalidate the unfinished wall job");
              var offered = new ItemStack(Items.COBBLESTONE, 64);
              h.assertTrue(
                  s.miner.receiveWorkSupply(offered) == 64
                      && offered.getCount() == 64
                      && count(s.miner.getBag()) == 64,
                  "One free slot accepts one stack without mutating source");
              h.assertTrue(
                  s.miner.receiveWorkSupply(offered) == 0,
                  "Satisfied request cannot accept a duplicate stack");
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "walls", timeoutTicks = 800)
  public static void fullBagIsUnloadedBeforeWallMaterialDelivery(GameTestHelper h) {
    var s = site(h);
    // Keep the handoff within reach; water navigation has its own default-stat regression.
    SlimeStat.REACH.tune(s.miner, 8);
    fluid(h, s, Blocks.WATER.defaultBlockState());
    for (int i = 0; i < s.miner.getCapacity(); i++)
      s.miner.getBag().setStackInSlot(i, new ItemStack(Items.DIRT, 64));
    var chest = s.area.chest(10, 101, 7);
    chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
    var carrier = s.area.carrier(new Vec3(11.5, 101, 8.5));
    SlimeStat.REACH.tune(carrier, 8);
    carrier.setOwnerUUID(s.miner.getOwnerUUID());
    start(h, s)
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getBlockState(s.inlet).is(Blocks.COBBLESTONE),
                    "Full bag unloaded and wall material delivered: "
                        + s.miner.getMiningStatus()
                        + " / "
                        + carrier.getTransportStatus()))
        .thenExecute(
            () -> {
              h.assertTrue(
                  count(s.miner.getBag()) == 63 && !carrier.hasCargo(),
                  "Exactly one stack supplied after unloading");
              int dirt = 0;
              for (int i = 0; i < chest.inventory().getSlots(); i++)
                if (chest.inventory().getStackInSlot(i).is(Items.DIRT))
                  dirt += chest.inventory().getStackInSlot(i).getCount();
              h.assertTrue(dirt == 9 * 64, "All original cargo conserved");
              carrier.discard();
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "walls", timeoutTicks = 800)
  public static void defaultReachSealsWallsFromFlowDisplacedPoses(GameTestHelper h) {
    var sites = new ArrayList<Site>();
    for (int heading = 0; heading < 8; heading++) {
      var s = site(h);
      fluid(h, s, Blocks.WATER.defaultBlockState());
      s.miner.moveTo(s.area.point(13.91197977963, 101.36437599669, 10.16811488174));
      s.miner.setYRot(heading * 45);
      s.miner.setOnGround(false);
      s.miner.setDeltaMovement(.0282957786, .0571248014, .0038788263);
      s.miner.getQuarryPlan().descend();
      s.miner.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
      sites.add(s);
    }
    h.startSequence()
        .thenWaitUntil(
            () -> {
              for (var s : sites)
                h.assertTrue(h.getLevel().isPositionEntityTicking(s.inlet), "Flow arena ticks");
            })
        .thenExecute(() -> sites.forEach(s -> h.getLevel().addFreshEntity(s.miner)))
        .thenWaitUntil(
            () -> {
              for (var s : sites)
                h.assertTrue(
                    h.getLevel().getBlockState(s.inlet).is(Blocks.COBBLESTONE),
                    "Default reach finishes the displaced approach: "
                        + s.miner.getMiningStatus()
                        + " at "
                        + s.miner.position());
            })
        .thenExecute(
            () -> {
              for (var s : sites) {
                h.assertTrue(
                    count(s.miner.getBag()) == 63
                        && !s.miner.hasBucket()
                        && s.miner.getActivityProgress() == 1
                        && SlimeStat.REACH.value(s.miner) == 3,
                    "Each default-stat approach consumes one real wall block");
                s.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "walls", timeoutTicks = 800)
  public static void concurrentCarriersDoNotOverSupplyTheSameWallJob(GameTestHelper h) {
    var s = site(h);
    fluid(h, s, Blocks.WATER.defaultBlockState());
    var chest = s.area.chest(10, 101, 7);
    chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
    chest.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
    var c1 = s.area.carrier(new Vec3(11.5, 101, 8.5));
    var c2 = s.area.carrier(new Vec3(11.5, 101, 10.5));
    c1.setOwnerUUID(s.miner.getOwnerUUID());
    c2.setOwnerUUID(s.miner.getOwnerUUID());
    start(h, s)
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getBlockState(s.inlet).is(Blocks.COBBLESTONE)
                        && !c1.hasCargo()
                        && !c2.hasCargo(),
                    "Concurrent supply settles and surplus returns to storage"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  count(s.miner.getBag()) == 63 && count(chest.inventory()) == 64,
                  "One accepted stack, exactly one consumed block, no duplication or extra supply");
              c1.discard();
              c2.discard();
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "walls", timeoutTicks = 160)
  public static void mobGriefingDenialPreventsWallChanges(GameTestHelper h) {
    var s = site(h);
    fluid(h, s, Blocks.WATER.defaultBlockState());
    s.miner.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 3));
    Consumer<net.minecraftforge.event.entity.EntityMobGriefingEvent> deny =
        e -> {
          if (e.getEntity() == s.miner)
            e.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        };
    MinecraftForge.EVENT_BUS.addListener(deny);
    h.runAfterDelay(155, () -> MinecraftForge.EVENT_BUS.unregister(deny));
    start(h, s)
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    s.miner.getMiningStatus() == MiningSlime.Status.DISABLED,
                    "Work permission denied"))
        .thenExecute(
            () -> {
              MinecraftForge.EVENT_BUS.unregister(deny);
              h.assertTrue(
                  !h.getLevel().getBlockState(s.inlet).is(Blocks.COBBLESTONE)
                      && count(s.miner.getBag()) == 3
                      && s.miner.getActivityProgress() == 0,
                  "No wall changes or costs when work disabled");
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "walls", timeoutTicks = 180)
  public static void relocatingQuarryDiscardsPreviousWallPositions(GameTestHelper h) {
    var s = site(h);
    fluid(h, s, Blocks.WATER.defaultBlockState());
    start(h, s)
        .thenWaitUntil(() -> h.assertTrue(s.miner.needsWorkSupply(), "Old quarry wall job"))
        .thenExecute(
            () -> {
              var tag = FriendlySlimeItem.fromSlime(s.miner).getTag().getCompound("SlimeData");
              h.assertTrue(
                  tag.getLongArray("MiningWallPending").length == 1, "Old inlet persisted");
              s.miner.moveTo(s.area.point(20, 105, 8));
              s.miner.onReleased();
              var moved = FriendlySlimeItem.fromSlime(s.miner).getTag().getCompound("SlimeData");
              h.assertTrue(
                  moved.getLongArray("MiningWallPending").length == 0
                      && s.miner.getMiningChunk().equals(s.miner.chunkPosition()),
                  "New chunk clears previous wall job");
              s.close();
            })
        .thenSucceed();
  }
}
