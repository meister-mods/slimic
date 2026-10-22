package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForestrySurfaceGameTests {
  @GameTest(template = "empty", batch = "forestry_surface", timeoutTicks = 1000)
  public static void loggingApproachesAStumpAcrossMud(GameTestHelper h) {
    surface(h, false);
  }

  @GameTest(template = "empty", batch = "forestry_surface", timeoutTicks = 1000)
  public static void loggingApproachesAStumpAcrossThinSnow(GameTestHelper h) {
    surface(h, true);
  }

  static void surface(GameTestHelper h, boolean snow) {
    var a = new LoadedWorkArea(h);
    a.clearSurroundingTrees();
    var level = h.getLevel();
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) {
        level.setBlock(
            BlockPos.containing(a.point(x, 100, z)),
            (snow ? Blocks.DIRT : Blocks.MUD).defaultBlockState(),
            18);
        if (snow)
          level.setBlock(
              BlockPos.containing(a.point(x, 101, z)), Blocks.SNOW.defaultBlockState(), 18);
      }
    var stump = BlockPos.containing(a.point(8, 101, 8));
    level.setBlock(stump.below(), Blocks.DIRT.defaultBlockState(), 18);
    for (int y = 0; y < 3; y++)
      level.setBlock(stump.above(y), Blocks.OAK_LOG.defaultBlockState(), 18);
    level.setBlock(
        stump.above(3),
        Blocks.OAK_LEAVES
            .defaultBlockState()
            .setValue(LeavesBlock.PERSISTENT, false)
            .setValue(LeavesBlock.DISTANCE, 1),
        18);
    var logger = ModEntities.LOGGING_SLIME.get().create(level);
    logger.tame(h.makeMockSurvivalPlayer());
    logger.moveTo(a.point(3.5, snow ? 101.125 : 101, 8.5));
    logger.onReleased();
    logger.setTool(new ItemStack(Items.DIAMOND_AXE));
    logger.getBag().setStackInSlot(0, new ItemStack(Items.OAK_SAPLING));
    logger.setWorking(true);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    level.isPositionEntityTicking(logger.blockPosition()), "Surface area ticks"))
        .thenExecute(() -> level.addFreshEntity(logger))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    level.getBlockState(stump).is(Blocks.OAK_SAPLING)
                        && logger.getPendingPlantings() == 0,
                    "Logger traverses the actual lowered surface and replants; snow="
                        + snow
                        + ", status="
                        + logger.getWorkStatus()
                        + ", pos="
                        + logger.position()))
        .thenExecute(
            () -> {
              int logs = 0;
              for (int i = 0; i < logger.getBag().getSlots(); i++)
                if (logger.getBag().getStackInSlot(i).is(Items.OAK_LOG))
                  logs += logger.getBag().getStackInSlot(i).getCount();
              h.assertTrue(logs == 3, "Exact three-log yield survives surface routing");
              logger.discard();
              a.close();
            })
        .thenSucceed();
  }
}
