package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GeneratedMangroveGameTests {
  @GameTest(template = "empty", batch = "generated_mangrove", timeoutTicks = 2400)
  public static void nativeGeneratedMangroveCompletesRealLoggingAndReplanting(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    a.clearSurroundingTrees();
    var level = h.getLevel();
    for (int x = -4; x < 20; x++)
      for (int z = -4; z < 20; z++) {
        level.setBlock(BlockPos.containing(a.point(x, 99, z)), Blocks.DIRT.defaultBlockState(), 18);
        level.setBlock(BlockPos.containing(a.point(x, 100, z)), Blocks.MUD.defaultBlockState(), 18);
      }
    var base = BlockPos.containing(a.point(8, 101, 8));
    var sapling = (SaplingBlock) Blocks.MANGROVE_PROPAGULE;
    var logs = new HashSet<BlockPos>();
    var roots = new HashSet<BlockPos>();
    for (int attempt = 0; attempt < 20 && logs.isEmpty(); attempt++) {
      var state =
          Blocks.MANGROVE_PROPAGULE
              .defaultBlockState()
              .setValue(SaplingBlock.STAGE, 1)
              .setValue(MangrovePropaguleBlock.AGE, 4);
      level.setBlock(base, state, 18);
      sapling.advanceTree(level, base, state, net.minecraft.util.RandomSource.create(42 + attempt));
      for (int x = -4; x < 20; x++)
        for (int z = -4; z < 20; z++)
          for (int y = 101; y < 141; y++) {
            var p = BlockPos.containing(a.point(x, y, z));
            var s = level.getBlockState(p);
            if (s.is(Blocks.MANGROVE_LOG)) logs.add(p);
            if (s.is(Blocks.MANGROVE_ROOTS)) roots.add(p);
          }
    }
    h.assertTrue(
        !logs.isEmpty() && !roots.isEmpty(),
        "Native sapling generated a real raised-trunk mangrove");
    System.out.println(
        "GENERATED MANGROVE START: logs="
            + logs.size()
            + ", roots="
            + roots.size()
            + ", base="
            + level.getBlockState(base)
            + ", lowest="
            + logs.stream().min(Comparator.comparingInt(BlockPos::getY)));
    var logger = ModEntities.LUMBERJACK_SLIME.get().create(level);
    logger.tame(h.makeMockSurvivalPlayer());
    logger.moveTo(a.point(3.5, 101, 8.5));
    logger.onReleased();
    SlimeStat.DETECTION_RANGE.tune(logger, 16);
    logger.setTool(new ItemStack(Items.DIAMOND_AXE));
    logger.getBag().setStackInSlot(0, new ItemStack(Items.MANGROVE_PROPAGULE, 16));
    logger.setWorking(true);
    var planned = new HashSet<BlockPos>();
    var planting = new HashSet<BlockPos>();
    h.onEachTick(
        () -> {
          if (logger.tickCount == 0) return;
          var data = logger.saveWithoutId(new CompoundTag());
          for (long p : data.getLongArray("LoggingRootColumns")) planned.add(BlockPos.of(p));
          for (var tag : data.getList("LoggingPlantings", net.minecraft.nbt.Tag.TAG_COMPOUND))
            planting.add(BlockPos.of(((CompoundTag) tag).getLong("Pos")));
          if (logger.tickCount % 100 == 0)
            System.out.println(
                "GENERATED MANGROVE PROGRESS: ticks="
                    + logger.tickCount
                    + ", status="
                    + logger.getWorkStatus()
                    + ", pos="
                    + logger.position()
                    + ", logs="
                    + MangroveForestryGameTests.count(logger, Items.MANGROVE_LOG)
                    + ", pending="
                    + logger.getPendingPlantings()
                    + ", plannedRoots="
                    + data.getLongArray("LoggingRootColumns").length);
        });
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    level.isPositionEntityTicking(logger.blockPosition()), "Tree area ticks"))
        .thenExecute(() -> level.addFreshEntity(logger))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    !planting.isEmpty()
                        && logger.getPendingPlantings() == 0
                        && logs.stream()
                            .noneMatch(p -> level.getBlockState(p).is(Blocks.MANGROVE_LOG))
                        && planting.stream()
                            .allMatch(p -> level.getBlockState(p).is(Blocks.MANGROVE_PROPAGULE)),
                    "Actual generated mangrove is fully logged and replanted; state="
                        + logger.getWorkStatus()
                        + ", position="
                        + logger.position()
                        + ", pending="
                        + logger.getPendingPlantings()))
        .thenExecute(
            () -> {
              h.assertTrue(
                  MangroveForestryGameTests.count(logger, Items.MANGROVE_LOG) == logs.size()
                      && MangroveForestryGameTests.count(logger, Items.MANGROVE_ROOTS)
                          == planned.size(),
                  "Generated tree and central root drops are conserved exactly");
              h.assertTrue(
                  roots.stream()
                      .filter(p -> !planned.contains(p))
                      .allMatch(p -> level.getBlockState(p).is(Blocks.MANGROVE_ROOTS)),
                  "Outer root structures remain intact");
              logger.discard();
              a.close();
            })
        .thenSucceed();
  }
}
