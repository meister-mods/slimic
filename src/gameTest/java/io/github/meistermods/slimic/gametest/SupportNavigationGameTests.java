package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SupportNavigationGameTests {
  private static int area;

  private static void check(
      GameTestHelper h, EntityType<? extends BagSlime> type, boolean loadedTarget) {
    int x = (4_000_000 + area++ * 256) >> 4, z = -(4_000_000 >> 4);
    var level = h.getLevel();
    level.getChunk(x, z);
    BlockPos start = new BlockPos((x << 4) + 8, 101, (z << 4) + 8);
    for (int dx = -1; dx <= 1; dx++)
      for (int dz = -1; dz <= 1; dz++) {
        level.setBlockAndUpdate(start.offset(dx, -1, dz), Blocks.BEDROCK.defaultBlockState());
        for (int y = 0; y < 4; y++)
          level.setBlockAndUpdate(start.offset(dx, y, dz), Blocks.AIR.defaultBlockState());
      }
    BlockPos target = new BlockPos(((x + (loadedTarget ? 2 : 1)) << 4) + 8, 101, (z << 4) + 8);
    if (loadedTarget) {
      level.getChunk(x + 2, z);
      level.setBlockAndUpdate(target.below(), Blocks.BEDROCK.defaultBlockState());
      level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
    }
    BlockPos cold = new BlockPos(((x + 1) << 4) + 8, 101, (z << 4) + 8);
    h.assertTrue(!level.hasChunkAt(cold), "Cold path region begins unloaded");
    var slime = type.create(level);
    slime.tame(h.makeMockSurvivalPlayer());
    slime.moveTo(start.getX() + .5, 101, start.getZ() + .5);
    slime.setOnGround(true);
    slime.onReleased();
    slime.setWorking(true);
    if (loadedTarget) SlimeStat.DETECTION_RANGE.tune(slime, 32);
    var path = slime.getNavigation().createPath(target, 0);
    h.assertTrue(
        !level.hasChunkAt(cold),
        "Work navigation must not force-load "
            + (loadedTarget ? "a cold gap toward loaded storage" : "an unloaded destination")
            + ": "
            + type);
    h.assertTrue(path == null || !path.canReach(), "No complete route crosses unavailable terrain");
    if (path != null)
      for (int i = 0; i < path.getNodeCount(); i++)
        h.assertTrue(
            level.hasChunkAt(path.getNodePos(i)), "Partial routes remain in loaded terrain");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "support_navigation")
  public static void transportRejectsUnloadedDestination(GameTestHelper h) {
    check(h, ModEntities.TRANSPORT_SLIME.get(), false);
  }

  @GameTest(template = "empty", batch = "support_navigation")
  public static void healerRejectsUnloadedDestination(GameTestHelper h) {
    check(h, ModEntities.HEALING_SLIME.get(), false);
  }

  @GameTest(template = "empty", batch = "support_navigation")
  public static void warehouseRejectsUnloadedDestination(GameTestHelper h) {
    check(h, ModEntities.WAREHOUSE_SLIME.get(), false);
  }

  @GameTest(template = "empty", batch = "support_navigation")
  public static void transportRejectsUnloadedGapTowardLoadedStorage(GameTestHelper h) {
    check(h, ModEntities.TRANSPORT_SLIME.get(), true);
  }

  @GameTest(template = "empty", batch = "support_navigation")
  public static void healerRejectsUnloadedGapTowardLoadedPatient(GameTestHelper h) {
    check(h, ModEntities.HEALING_SLIME.get(), true);
  }

  @GameTest(template = "empty", batch = "support_navigation")
  public static void warehouseRejectsUnloadedGapTowardLoadedWorker(GameTestHelper h) {
    check(h, ModEntities.WAREHOUSE_SLIME.get(), true);
  }

  @GameTest(template = "empty", batch = "support_navigation")
  public static void farmerRejectsUnloadedDestination(GameTestHelper h) {
    check(h, ModEntities.FARMER_SLIME.get(), false);
  }

  @GameTest(template = "empty", batch = "support_navigation")
  public static void farmerRejectsUnloadedGap(GameTestHelper h) {
    check(h, ModEntities.FARMER_SLIME.get(), true);
  }

  @GameTest(template = "empty", batch = "support_navigation")
  public static void lumberjackRejectsUnloadedDestination(GameTestHelper h) {
    check(h, ModEntities.LUMBERJACK_SLIME.get(), false);
  }

  @GameTest(template = "empty", batch = "support_navigation")
  public static void lumberjackRejectsUnloadedGap(GameTestHelper h) {
    check(h, ModEntities.LUMBERJACK_SLIME.get(), true);
  }

  @GameTest(template = "empty", batch = "support_navigation")
  public static void combatRejectsUnloadedDestination(GameTestHelper h) {
    check(h, ModEntities.COMBAT_SLIME.get(), false);
  }

  @GameTest(template = "empty", batch = "support_navigation")
  public static void combatRejectsUnloadedGap(GameTestHelper h) {
    check(h, ModEntities.COMBAT_SLIME.get(), true);
  }

  @GameTest(template = "empty", batch = "support_navigation")
  public static void cleaningRejectsUnloadedDestination(GameTestHelper h) {
    check(h, ModEntities.CLEANING_SLIME.get(), false);
  }

  @GameTest(template = "empty", batch = "support_navigation")
  public static void cleaningRejectsUnloadedGap(GameTestHelper h) {
    check(h, ModEntities.CLEANING_SLIME.get(), true);
  }

  @GameTest(template = "empty", batch = "support_navigation", timeoutTicks = 100)
  public static void loadedWorkRouteCanDetourAroundAWideWall(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var level = h.getLevel();
    var slime = ModEntities.TRANSPORT_SLIME.get().create(level);
    slime.tame(h.makeMockSurvivalPlayer());
    slime.moveTo(a.point(4.5, 101, 8.5));
    slime.setOnGround(true);
    slime.onReleased();
    slime.setWorking(true);
    for (int z = 4; z <= 12; z++)
      for (int y = 101; y <= 104; y++)
        level.setBlockAndUpdate(
            BlockPos.containing(a.point(8, y, z)), Blocks.BEDROCK.defaultBlockState());
    var target = BlockPos.containing(a.point(12, 101, 8));
    var path = slime.getNavigation().createPath(target, 0);
    h.assertTrue(
        path != null && path.canReach(),
        "Loaded routes may detour farther than a narrow straight corridor");
    h.assertTrue(
        slime.getNavigation().getTargetPos().equals(path.getTarget()),
        "Work target tracks the returned path");
    a.close();
    h.succeed();
  }
}
