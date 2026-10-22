package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlacementAccountingGameTests {
  @GameTest(template = "empty", batch = "placement_accounting")
  public static void acceptedCropStateAdjustmentConsumesOneSeed(GameTestHelper h) {
    farm(h, 0, false);
  }

  @GameTest(template = "empty", batch = "placement_accounting")
  public static void acceptedCropReplacementConsumesOneSeed(GameTestHelper h) {
    farm(h, 1, false);
  }

  @GameTest(template = "empty", batch = "placement_accounting")
  public static void acceptedSoilAdjustmentChargesOneHoeUse(GameTestHelper h) {
    farm(h, 2, false);
  }

  @GameTest(template = "empty", batch = "placement_accounting")
  public static void cancelledAdjustedCropPreservesItsSeed(GameTestHelper h) {
    farm(h, 0, true);
  }

  static void invoke(FieldSlime slime, ServerLevel level) {
    try {
      var method = slime.getClass().getDeclaredMethod("workTick", ServerLevel.class);
      method.setAccessible(true);
      method.invoke(slime, level);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  static void farm(GameTestHelper h, int kind, boolean cancel) {
    var a = new LoadedWorkArea(h);
    var level = h.getLevel();
    var soil = BlockPos.containing(a.point(8, 100, 8));
    var pos = soil.above();
    level.setBlock(soil, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 18);
    var farming = ModEntities.FARMING_SLIME.get().create(level);
    farming.tame(h.makeMockSurvivalPlayer());
    farming.moveTo(a.point(7.5, 101, 8.5));
    // Keep this transaction fixture inside its cleared chunk, away from natural village plots.
    SlimeStat.DETECTION_RANGE.tune(farming, 2);
    farming.onReleased();
    farming.setTool(new ItemStack(Items.IRON_HOE));
    farming.setWorking(true);
    farming.saveWithoutId(new CompoundTag());
    farming.setNoAi(true);
    farming.getBag().setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS, 1));
    if (kind == 2) level.setBlock(soil, Blocks.DIRT.defaultBlockState(), 18);
    int[] calls = {0};
    Consumer<BlockEvent.EntityPlaceEvent> adjust =
        event -> {
          if (!event.getPos().equals(kind == 2 ? soil : pos)) return;
          calls[0]++;
          var state =
              kind == 2
                  ? Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7)
                  : kind == 1
                      ? Blocks.CARROTS.defaultBlockState()
                      : Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3);
          level.setBlock(event.getPos(), state, 18);
          if (cancel) event.setCanceled(true);
        };
    h.assertTrue(
        farming.getFarmPlots().equals(java.util.Set.of(soil)),
        "The transaction fixture registers exactly its prepared plot");
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    kind == 2 || Blocks.WHEAT.defaultBlockState().canSurvive(level, pos),
                    "The prepared crop has valid native soil and lighting"))
        .thenExecute(
            () -> {
              MinecraftForge.EVENT_BUS.addListener(adjust);
              try {
                invoke(farming, level);
                h.assertTrue(
                    calls[0] == 1, "One native placement event is observed: calls=" + calls[0]);
                h.assertTrue(
                    farming.getBag().getStackInSlot(0).getCount() == (kind == 2 || cancel ? 1 : 0)
                        && farming.getTool().getDamageValue() == (kind == 2 && !cancel ? 1 : 0)
                        && farming.getActivityProgress() == (cancel ? 0 : 1)
                        && farming.getStamina() == (cancel ? 100 : 99),
                    "Accepted event adjustments charge the committed native action exactly once;"
                        + " kind="
                        + kind
                        + ", seed="
                        + farming.getBag().getStackInSlot(0)
                        + ", tool="
                        + farming.getTool().getDamageValue()
                        + ", activity="
                        + farming.getActivityProgress());
                h.assertTrue(
                    cancel
                        ? level.getBlockState(pos).isAir()
                        : kind == 2
                            ? level.getBlockState(soil).getValue(FarmBlock.MOISTURE) == 7
                            : kind == 1
                                ? level.getBlockState(pos).is(Blocks.CARROTS)
                                : level.getBlockState(pos).getValue(CropBlock.AGE) == 3,
                    "Successful adjustments survive; cancelled changes roll back");
              } finally {
                MinecraftForge.EVENT_BUS.unregister(adjust);
                farming.discard();
                a.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "placement_accounting")
  public static void acceptedInstantSaplingGrowthConsumesTheReservedSeed(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var level = h.getLevel();
    var pos = BlockPos.containing(a.point(8, 101, 8));
    level.setBlock(pos.below(), Blocks.DIRT.defaultBlockState(), 18);
    var logger = ModEntities.LOGGING_SLIME.get().create(level);
    logger.tame(h.makeMockSurvivalPlayer());
    logger.moveTo(a.point(7.5, 101, 8.5));
    logger.onReleased();
    logger.setWorking(true);
    logger.setTool(new ItemStack(Items.IRON_AXE));
    logger.setNoAi(true);
    var data = logger.saveWithoutId(new CompoundTag());
    var plantings = new ListTag();
    var entry = new CompoundTag();
    entry.putLong("Pos", pos.asLong());
    entry.put("Seed", new ItemStack(Items.OAK_SAPLING).save(new CompoundTag()));
    plantings.add(entry);
    data.put("LoggingPlantings", plantings);
    logger.load(data);
    Consumer<BlockEvent.EntityPlaceEvent> grow =
        event -> {
          if (event.getPos().equals(pos))
            level.setBlock(pos, Blocks.OAK_LOG.defaultBlockState(), 18);
        };
    MinecraftForge.EVENT_BUS.addListener(grow);
    try {
      invoke(logger, level);
      h.assertTrue(
          level.getBlockState(pos).is(Blocks.OAK_LOG)
              && logger.getPendingPlantings() == 0
              && logger.getActivityProgress() == 1
              && logger.getStamina() == 99,
          "Accepted instant growth consumes exactly one reserved planting and grants one completed"
              + " action");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(grow);
      logger.discard();
      a.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "placement_accounting")
  public static void acceptedWallConversionConsumesOneCobblestone(GameTestHelper h) {
    wall(h, true);
  }

  @GameTest(template = "empty", batch = "placement_accounting")
  public static void ineffectiveWallConversionRestoresTheFluidInlet(GameTestHelper h) {
    wall(h, false);
  }

  static void wall(GameTestHelper h, boolean sealed) {
    var a = new LoadedWorkArea(h);
    var level = h.getLevel();
    for (int i = -1; i <= 16; i++)
      for (int y = 98; y <= 104; y++)
        for (var point :
            java.util.List.of(
                a.point(-1, y, i), a.point(16, y, i), a.point(i, y, -1), a.point(i, y, 16)))
          level.setBlock(BlockPos.containing(point), Blocks.BEDROCK.defaultBlockState(), 2);
    var pos = BlockPos.containing(a.point(16, 101, 8));
    level.setBlock(pos, Blocks.WATER.defaultBlockState(), 18);
    var miner = ModEntities.MINING_SLIME.get().create(level);
    miner.tame(h.makeMockSurvivalPlayer());
    miner.moveTo(a.point(14, 101, 8.5));
    miner.onReleased();
    miner.setWorking(true);
    miner.setNoAi(true);
    miner.getQuarryPlan().descend();
    miner.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 3));
    int[] calls = {0};
    Consumer<BlockEvent.EntityPlaceEvent> convert =
        event -> {
          if (event.getPos().equals(pos)) {
            calls[0]++;
            level.setBlock(
                pos,
                sealed
                    ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                    : Blocks.AIR.defaultBlockState(),
                18);
          }
        };
    MinecraftForge.EVENT_BUS.addListener(convert);
    try {
      var method = MiningSlime.class.getDeclaredMethod("ensureWalls", ServerLevel.class);
      method.setAccessible(true);
      method.invoke(miner, level);
      h.assertTrue(calls[0] == 1, "Native wall event was reached exactly once");
      h.assertTrue(
          miner.getBag().getStackInSlot(0).getCount() == (sealed ? 2 : 3)
              && miner.getActivityProgress() == (sealed ? 1 : 0)
              && miner.getStamina() == (sealed ? 99 : 100),
          "A valid converted barrier consumes one source material; an ineffective conversion"
              + " consumes none");
      h.assertTrue(
          level.getBlockState(pos).is(sealed ? Blocks.MOSSY_COBBLESTONE : Blocks.WATER),
          "Valid barrier conversion remains; an unsealed inlet returns to its snapshot");
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    } finally {
      MinecraftForge.EVENT_BUS.unregister(convert);
      miner.discard();
      a.close();
    }
    h.succeed();
  }
}
