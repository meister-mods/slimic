package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PrecisionStabilityGameTests {
  private record Fixture(LoadedWorkArea area, NextGenerationMiningSlime slime, BlockPos base) {}

  private static Fixture fixture(GameTestHelper h, int y) {
    var area = new LoadedWorkArea(h);
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++)
        for (int height = y - 8; height < y; height++)
          h.getLevel()
              .setBlock(
                  BlockPos.containing(area.point(x, height, z)),
                  Blocks.STONE.defaultBlockState(),
                  2);
    var slime = ModEntities.NEXT_GENERATION_MINING_SLIME.get().create(h.getLevel());
    slime.tame(h.makeMockSurvivalPlayer());
    slime.moveTo(area.point(5, y, 7));
    slime.onReleased();
    slime.setPolicy(NextGenerationMiningSlime.Policy.PRECISION_PROSPECTING);
    for (int i = 0; i < NextGenerationMiningSlime.ORES.size(); i++) if (i != 6) slime.toggleOre(i);
    slime.setTool(0, new ItemStack(Items.IRON_PICKAXE));
    slime.setWorking(true);
    return new Fixture(area, slime, BlockPos.containing(area.point(4, y, 6)));
  }

  private static CompoundTag plan(BlockPos cell, BlockPos shaft, BlockPos pending) {
    var tag = new CompoundTag();
    tag.putInt("Version", 1);
    tag.putBoolean("Descended", true);
    tag.putLong("Cell", cell.asLong());
    tag.putLong("Shaft", shaft.asLong());
    tag.putLong("Origin", cell.asLong());
    if (pending != null) tag.putLong("Pending", pending.asLong());
    return tag;
  }

  private static void step(Fixture f, GameTestHelper h) {
    f.slime.tickCount++;
    f.slime.prospecting().tick(h.getLevel());
  }

  private static BlockPos survey(Fixture f, GameTestHelper h) throws Exception {
    var scan =
        PrecisionProspecting.class.getDeclaredMethod(
            "scan", net.minecraft.server.level.ServerLevel.class);
    scan.setAccessible(true);
    for (int tick = 0; tick < 300; tick++) {
      if ((boolean) scan.invoke(f.slime.prospecting(), h.getLevel())) {
        var target = PrecisionProspecting.class.getDeclaredField("ore");
        target.setAccessible(true);
        return (BlockPos) target.get(f.slime.prospecting());
      }
    }
    throw new AssertionError("A bounded survey completes");
  }

  @GameTest(template = "empty", batch = "precision_stability")
  public static void sectionSkippingRetainsLiveOreFiltersAndFindsNewBoundaryDeposits(
      GameTestHelper h) throws Exception {
    var f = fixture(h, 280);
    SlimeStat.DETECTION_RANGE.tune(f.slime, 32);
    var first = f.base.offset(16, 16, 0);
    var second = f.base.offset(-16, 8, 0);
    h.getLevel().setBlock(f.base.east(2), Blocks.IRON_ORE.defaultBlockState(), 18);
    h.assertTrue(survey(f, h) == null, "An unselected ore does not defeat section skipping");
    h.getLevel().setBlock(first, Blocks.DIAMOND_ORE.defaultBlockState(), 18);
    h.assertTrue(
        first.equals(survey(f, h)), "A new selected ore crosses both chunk and section edges");
    h.getLevel().setBlock(first, Blocks.AIR.defaultBlockState(), 18);
    h.getLevel().setBlock(second, Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState(), 18);
    h.assertTrue(
        second.equals(survey(f, h)),
        "The next survey observes changed palettes and deepslate ores");
    f.slime.toggleOre(6);
    f.slime.toggleOre(2);
    h.assertTrue(
        f.base.east(2).equals(survey(f, h)), "Filter changes invalidate the previous survey");
    f.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "precision_stability")
  public static void largeEmptySurveyDoesNotPauseForTheEntireBlockCube(GameTestHelper h) {
    var f = fixture(h, 280);
    SlimeStat.DETECTION_RANGE.tune(f.slime, 32);
    f.slime.prospecting().load(plan(f.base, f.base, null));
    var tickets = java.util.Set.copyOf(h.getLevel().getForcedChunks());
    int chunks = h.getLevel().getChunkSource().getLoadedChunksCount();
    for (int tick = 0; tick < 8 && f.slime.prospecting().nextCell() == null; tick++) step(f, h);
    h.assertTrue(
        f.base.below().equals(f.slime.prospecting().nextCell()),
        "Ore-free section palettes let a 32-block survey continue descending promptly");
    h.assertTrue(
        tickets.equals(h.getLevel().getForcedChunks())
            && chunks == h.getLevel().getChunkSource().getLoadedChunksCount(),
        "Survey never acquires missing chunks");
    f.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "precision_stability")
  public static void aStalledDescentPreservesThePendingLevelAndCanResume(GameTestHelper h) {
    var f = fixture(h, 280);
    var next = f.base.below();
    for (int x = 0; x < 2; x++)
      for (int z = 0; z < 2; z++)
        h.getLevel().setBlock(next.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 2);
    f.slime.prospecting().load(plan(f.base, next, next));
    for (int tick = 0; tick < 220; tick++) step(f, h);
    h.assertTrue(
        f.slime.getMiningStatus() == MiningSlime.Status.UNREACHABLE
            && next.equals(f.slime.prospecting().nextCell()),
        "A temporary movement timeout retains the excavated next level");
    f.slime.moveTo(next.getX() + 1, next.getY(), next.getZ() + 1);
    f.slime.tickCount += 61;
    step(f, h);
    h.assertTrue(
        BlockPos.of(f.slime.prospecting().save().getLong("Cell")).equals(next),
        "After the obstruction clears, the pending descent resumes instead of rejecting its empty"
            + " old floor");
    f.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "precision_stability")
  public static void legacyClearedShaftWithoutAPendingRouteRecoversSafely(GameTestHelper h) {
    var f = fixture(h, 280);
    var next = f.base.below();
    for (int x = 0; x < 2; x++)
      for (int z = 0; z < 2; z++)
        h.getLevel().setBlock(next.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 2);
    f.slime.prospecting().load(plan(f.base, next, null));
    f.slime.moveTo(next.getX() + 1, next.getY(), next.getZ() + 1);
    float stamina = f.slime.getStamina();
    step(f, h);
    h.assertTrue(
        BlockPos.of(f.slime.prospecting().save().getLong("Cell")).equals(next)
            && f.slime.getPickaxe().getDamageValue() == 0
            && f.slime.getBag().occupiedSlots().isEmpty()
            && f.slime.getStamina() == stamina,
        "A legacy cleared descent resumes without another break, drop, or work cost");
    f.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "precision_stability")
  public static void legacyRecoveryCannotSkipUnfinishedOrUnsafeDescent(GameTestHelper h) {
    for (boolean unsafe : new boolean[] {false, true}) {
      var f = fixture(h, 280);
      var next = f.base.below();
      for (int x = 0; x < 2; x++)
        for (int z = 0; z < 2; z++)
          if (unsafe || x != 0 || z != 0)
            h.getLevel().setBlock(next.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 18);
      if (unsafe) h.getLevel().setBlock(next.below(), Blocks.LAVA.defaultBlockState(), 18);
      f.slime.prospecting().load(plan(f.base, next, null));
      step(f, h);
      h.assertTrue(
          f.slime.getMiningStatus() == MiningSlime.Status.UNREACHABLE
              && f.slime.prospecting().nextCell() == null
              && BlockPos.of(f.slime.prospecting().save().getLong("Cell")).equals(f.base)
              && f.slime.getPickaxe().getDamageValue() == 0
              && f.slime.getBag().occupiedSlots().isEmpty(),
          "Legacy recovery rejects incomplete or lava-exposed next levels without work");
      f.area.close();
    }
    h.succeed();
  }

  private static void descend(GameTestHelper h, boolean exhausted) {
    var f = fixture(h, 101);
    if (exhausted) f.slime.setTunedVitals(f.slime.getMaxHealth(), 1.5F, 0);
    h.getLevel().addFreshEntity(f.slime);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    f.slime.getY() < 97.5,
                    "Default-speed worker continues through four shaft levels: "
                        + f.slime.getMiningStatus()))
        .thenExecute(
            () -> {
              h.assertTrue(
                  f.slime.getHealth() == f.slime.getMaxHealth()
                      && f.slime.getPickaxe().getDamageValue() >= 16,
                  "Physical descent uses real tools and retains health");
              f.slime.discard();
              f.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "precision_stability", timeoutTicks = 1800)
  public static void defaultSpeedContinuesAcrossSeveralShaftLevels(GameTestHelper h) {
    descend(h, false);
  }

  @GameTest(template = "empty", batch = "precision_stability", timeoutTicks = 2200)
  public static void exhaustionDuringPartialExcavationResumesWithoutANewOrder(GameTestHelper h) {
    descend(h, true);
  }
}
