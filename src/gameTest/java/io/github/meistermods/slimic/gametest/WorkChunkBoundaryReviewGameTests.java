package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.FieldSlime;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkChunkBoundaryReviewGameTests {
  @GameTest(template = "empty", batch = "work_chunk_boundary_review", timeoutTicks = 600)
  public static void activeHarvestAtADistantLoadedPlotDoesNotAcquireItsColdNeighbor(
      GameTestHelper h) {
    var area = new LoadedWorkArea(h, 1000);
    var level = h.getLevel();
    var worker = ModEntities.FARMING_SLIME.get().create(level);
    worker.tame(h.makeMockSurvivalPlayer());
    // The worker's own ticking neighborhood is separate from the remotely loaded work cell.
    int y = level.getMaxBuildHeight() - 9;
    // A solid platform keeps the real hopping AI grounded throughout its retry delay.
    for (int x = 6; x <= 10; x++)
      for (int z = 6; z <= 10; z++)
        level.setBlock(
            BlockPos.containing(area.point(x, y - 1, z)), Blocks.BEDROCK.defaultBlockState(), 18);
    worker.moveTo(area.point(8.5, y, 8.5));
    worker.setOnGround(true);
    worker.onReleased();
    worker.setTool(new ItemStack(Items.DIAMOND_HOE));
    SlimeStat.DETECTION_RANGE.tune(worker, 128);
    SlimeStat.REACH.tune(worker, 128);
    SlimeStat.WORK_SPEED.tune(worker, 32);
    var chunk = worker.chunkPosition();
    for (int offset = 1; offset <= 4; offset++) level.getChunk(chunk.x + offset, chunk.z);
    var crop = new BlockPos(((chunk.x + 4) << 4) + 8, y, (chunk.z << 4) + 15);
    var neighbor = crop.south();
    var mature = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7);
    // Known-shape writes prepare the fixture without invoking the action under review.
    level.setBlock(
        crop.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 18);
    level.setBlock(crop, mature, 18);
    h.assertTrue(
        !level.hasChunkAt(neighbor),
        "The remotely loaded plot borders a genuinely unavailable chunk");
    worker.setWorking(true);
    h.runAfterDelay(590, worker::discard);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    level.isPositionEntityTicking(worker.blockPosition()),
                    "The real worker is in an entity-ticking chunk"))
        .thenExecute(() -> h.assertTrue(level.addFreshEntity(worker), "The worker joins the level"))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    !level.getBlockState(crop).equals(mature)
                        || worker.getFieldStatus() == FieldSlime.Status.UNREACHABLE,
                    "The native farming goal reaches the prepared remote crop"))
        .thenExecute(
            () -> {
              boolean neighborLoaded = level.hasChunkAt(neighbor);
              boolean matureStillPresent = level.getBlockState(crop).equals(mature);
              h.assertTrue(
                  !neighborLoaded,
                  "Native harvest must not acquire an unavailable neighbor: neighborLoaded="
                      + neighborLoaded
                      + ", matureStillPresent="
                      + matureStillPresent
                      + ", cargo="
                      + worker.hasCargo()
                      + ", toolDamage="
                      + worker.getTool().getDamageValue());
              h.assertTrue(
                  !matureStillPresent
                      || !worker.hasCargo() && worker.getTool().getDamageValue() == 0,
                  "A paused harvest preserves its drops and tool; loaded-only successful harvest is"
                      + " valid");
              level.getChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4);
            })
        .thenWaitUntil(
            () -> {
              // A one-off getChunk request need not survive the worker's 100-tick retry delay.
              // Keep the positive-control work cell and sight corridor available until it resumes.
              for (int offset = 1; offset <= 4; offset++) level.getChunk(chunk.x + offset, chunk.z);
              level.getChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4);
              h.assertTrue(
                  !level.getBlockState(crop).equals(mature) && worker.hasCargo(),
                  "The same work order resumes with loaded terrain: status="
                      + worker.getFieldStatus()
                      + ", position="
                      + worker.position()
                      + ", crop="
                      + level.getBlockState(crop)
                      + ", neighborLoaded="
                      + level.hasChunkAt(neighbor)
                      + ", plots="
                      + worker.getFarmPlots().size()
                      + ", removed="
                      + worker.isRemoved());
            })
        .thenExecute(worker::discard)
        .thenSucceed();
  }
}
