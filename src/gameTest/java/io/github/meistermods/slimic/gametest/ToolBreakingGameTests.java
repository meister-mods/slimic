package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ToolBreakingGameTests {
  private record Rig(LoadedWorkArea area, BagSlime pet, BlockPos block) {
    void close() {
      pet.discard();
      area.close();
    }
  }

  private static final class Attempts implements AutoCloseable {
    int first = -1;
    int count;
    final Consumer<PlayerEvent.BreakSpeed> listener;

    Attempts(GameTestHelper h, Rig rig) {
      listener =
          event -> {
            if (event.getPosition().filter(rig.block::equals).isPresent()) {
              if (first < 0) first = rig.pet.tickCount;
              count++;
            }
          };
      MinecraftForge.EVENT_BUS.addListener(listener);
      h.runAfterDelay(1190, this::close);
    }

    @Override
    public void close() {
      MinecraftForge.EVENT_BUS.unregister(listener);
    }
  }

  private static GameTestSequence start(GameTestHelper h, Rig rig) {
    return h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(rig.pet.blockPosition()),
                    "Worker arena is entity-ticking"))
        .thenExecute(() -> h.getLevel().addFreshEntity(rig.pet));
  }

  private static ItemStack efficient(Item item) {
    var tool = new ItemStack(item);
    tool.enchant(Enchantments.BLOCK_EFFICIENCY, 5);
    return tool;
  }

  private static Rig miner(
      GameTestHelper h, ItemStack pick, ItemStack shovel, Block block, double speed) {
    var a = new LoadedWorkArea(h);
    var m = ModEntities.MINING_SLIME.get().create(h.getLevel());
    m.tame(h.makeMockSurvivalPlayer());
    m.moveTo(a.point(7.5, 101, 8.5));
    m.setTool(0, pick);
    m.setTool(1, shovel);
    m.onReleased();
    m.setWorking(true);
    SlimeStat.WORK_SPEED.tune(m, speed);
    var target = BlockPos.containing(a.point(9, 101, 8));
    h.getLevel().setBlockAndUpdate(target, block.defaultBlockState());
    return new Rig(a, m, target);
  }

  private static Rig logger(GameTestHelper h, ItemStack axe, double speed) {
    var a = new LoadedWorkArea(h);
    a.clearSurroundingTrees();
    var target = BlockPos.containing(a.point(8, 101, 8));
    h.getLevel().setBlockAndUpdate(target.below(), Blocks.DIRT.defaultBlockState());
    h.getLevel().setBlockAndUpdate(target, Blocks.OAK_LOG.defaultBlockState());
    h.getLevel()
        .setBlockAndUpdate(
            target.above(),
            Blocks.OAK_LEAVES
                .defaultBlockState()
                .setValue(LeavesBlock.PERSISTENT, false)
                .setValue(LeavesBlock.DISTANCE, 1));
    var m = ModEntities.LUMBERJACK_SLIME.get().create(h.getLevel());
    m.tame(h.makeMockSurvivalPlayer());
    m.moveTo(a.point(6.5, 101, 8.5));
    m.setTool(axe);
    m.getBag().setStackInSlot(0, new ItemStack(Items.OAK_SAPLING));
    m.onReleased();
    m.setWorking(true);
    SlimeStat.WORK_SPEED.tune(m, speed);
    return new Rig(a, m, target);
  }

  private static Rig farmer(GameTestHelper h, ItemStack hoe) {
    var a = new LoadedWorkArea(h);
    var target = BlockPos.containing(a.point(8, 101, 8));
    h.getLevel()
        .setBlockAndUpdate(
            target.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
    h.getLevel().setBlockAndUpdate(target.below().east(), Blocks.WATER.defaultBlockState());
    h.getLevel()
        .setBlockAndUpdate(target, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
    var m = ModEntities.FARMER_SLIME.get().create(h.getLevel());
    m.tame(h.makeMockSurvivalPlayer());
    m.moveTo(a.point(6.5, 101, 8.5));
    m.setTool(hoe);
    m.onReleased();
    m.setWorking(true);
    SlimeStat.WORK_SPEED.tune(m, 32);
    return new Rig(a, m, target);
  }

  private static void duration(GameTestHelper h, Rig rig, int minimum, int maximum) {
    var attempts = new Attempts(h, rig);
    start(h, rig)
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getBlockState(rig.block).isAir(),
                    "Block finishes after actual work: "
                        + rig.pet.getWorkStatus()
                        + " calls="
                        + attempts.count))
        .thenExecute(
            () -> {
              int elapsed = rig.pet.tickCount - attempts.first;
              h.assertTrue(
                  attempts.first >= 0 && elapsed >= minimum && elapsed <= maximum,
                  "Breaking interval respects tool effectiveness: "
                      + elapsed
                      + " ticks, expected "
                      + minimum
                      + ".."
                      + maximum);
              h.assertTrue(
                  rig.pet.getActivityProgress() == 1, "Exactly one completed block earns activity");
              attempts.close();
              rig.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "tool_breaking", timeoutTicks = 1200)
  public static void efficientPickaxeCannotSpeedUpDirtWithoutAShovel(GameTestHelper h) {
    duration(
        h, miner(h, efficient(Items.DIAMOND_PICKAXE), ItemStack.EMPTY, Blocks.DIRT, 32), 14, 25);
  }

  @GameTest(template = "empty", batch = "tool_breaking", timeoutTicks = 1200)
  public static void efficientShovelCannotSpeedUpGlassWithoutAPickaxe(GameTestHelper h) {
    duration(
        h, miner(h, ItemStack.EMPTY, efficient(Items.DIAMOND_SHOVEL), Blocks.GLASS, 32), 8, 18);
  }

  @GameTest(template = "empty", batch = "tool_breaking", timeoutTicks = 1200)
  public static void correctShovelStillReceivesEfficiencyAndWorkSpeed(GameTestHelper h) {
    duration(
        h,
        miner(
            h, new ItemStack(Items.IRON_PICKAXE), efficient(Items.DIAMOND_SHOVEL), Blocks.DIRT, 32),
        0,
        0);
  }

  @GameTest(template = "empty", batch = "tool_breaking", timeoutTicks = 1200)
  public static void toolDowngradeRestartsMiningInsteadOfKeepingFastProgress(GameTestHelper h) {
    var rig = miner(h, new ItemStack(Items.DIAMOND_PICKAXE), ItemStack.EMPTY, Blocks.STONE, 1);
    var m = (MiningSlime) rig.pet;
    var attempts = new Attempts(h, rig);
    int[] changed = {-1};
    start(h, rig)
        .thenWaitUntil(() -> h.assertTrue(attempts.count >= 4, "Partly mined with diamond pickaxe"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockState(rig.block).is(Blocks.STONE),
                  "Original block still intact");
              changed[0] = m.tickCount;
              m.setTool(0, new ItemStack(Items.WOODEN_PICKAXE));
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getBlockState(rig.block).isAir(),
                    "Wooden pickaxe finishes its own interval"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  m.tickCount - changed[0] >= 22
                      && m.getPickaxe().getDamageValue() == 1
                      && m.getActivityProgress() == 1,
                  "New tool restarts timing with exactly one wear and reward");
              attempts.close();
              rig.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "tool_breaking", timeoutTicks = 1200)
  public static void removingEfficiencyFromTheSameToolRestartsItsProgress(GameTestHelper h) {
    var rig = miner(h, efficient(Items.DIAMOND_PICKAXE), ItemStack.EMPTY, Blocks.STONE, .1);
    var m = (MiningSlime) rig.pet;
    var attempts = new Attempts(h, rig);
    int[] changed = {-1};
    start(h, rig)
        .thenWaitUntil(
            () -> h.assertTrue(attempts.count >= 10, "Enchanted tool made partial progress"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockState(rig.block).is(Blocks.STONE),
                  "Original enchanted interval incomplete");
              changed[0] = m.tickCount;
              m.getPickaxe().getTag().remove("Enchantments");
              SlimeStat.WORK_SPEED.tune(m, 1);
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getBlockState(rig.block).isAir(), "Unenchanted tool finishes"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  m.tickCount - changed[0] >= 5,
                  "Removing an enchantment cannot retain the old tool bonus");
              attempts.close();
              rig.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "tool_breaking", timeoutTicks = 1200)
  public static void emptyEquipmentCannotBreakTerrainAtMaximumWorkSpeed(GameTestHelper h) {
    var rig = miner(h, ItemStack.EMPTY, ItemStack.EMPTY, Blocks.DIRT, 32);
    start(h, rig)
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    ((MiningSlime) rig.pet).getMiningStatus() == MiningSlime.Status.NO_TOOL,
                    "Missing mining tools"))
        .thenIdle(35)
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockState(rig.block).is(Blocks.DIRT)
                      && rig.pet.getActivityProgress() == 0,
                  "No tool means no destruction or growth");
              rig.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "tool_breaking", timeoutTicks = 1200)
  public static void shovelCannotReplaceARequiredPickaxeTier(GameTestHelper h) {
    var rig = miner(h, ItemStack.EMPTY, efficient(Items.NETHERITE_SHOVEL), Blocks.DIAMOND_ORE, 32);
    start(h, rig)
        .thenWaitUntil(() -> h.assertTrue(rig.pet.isWorkBlocked(), "Pickaxe required for ore"))
        .thenIdle(35)
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockState(rig.block).is(Blocks.DIAMOND_ORE)
                      && rig.pet.getActivityProgress() == 0
                      && ((MiningSlime) rig.pet).getShovel().getDamageValue() == 0,
                  "Wrong tool cannot bypass tier protection");
              rig.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "tool_breaking", timeoutTicks = 1200)
  public static void woodenAxePaysTheLogsNativeBreakingTime(GameTestHelper h) {
    duration(h, logger(h, new ItemStack(Items.WOODEN_AXE), 1), 29, 45);
  }

  @GameTest(template = "empty", batch = "tool_breaking", timeoutTicks = 1200)
  public static void diamondAxeBreaksLogsFasterThanWoodenAxe(GameTestHelper h) {
    duration(h, logger(h, new ItemStack(Items.DIAMOND_AXE), 1), 7, 16);
  }

  @GameTest(template = "empty", batch = "tool_breaking", timeoutTicks = 1200)
  public static void efficientAxeStillReceivesItsWorkSpeedBonus(GameTestHelper h) {
    duration(h, logger(h, efficient(Items.DIAMOND_AXE), 32), 0, 0);
  }

  @GameTest(template = "empty", batch = "tool_breaking", timeoutTicks = 1200)
  public static void missingAxePausesAndResumesWithFreshBreakingProgress(GameTestHelper h) {
    var rig = logger(h, new ItemStack(Items.DIAMOND_AXE), 1);
    var m = (LumberjackSlime) rig.pet;
    var attempts = new Attempts(h, rig);
    int[] resumed = {-1};
    start(h, rig)
        .thenWaitUntil(() -> h.assertTrue(attempts.count >= 5, "Tree partly cut"))
        .thenExecute(() -> m.setTool(ItemStack.EMPTY))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    m.getFieldStatus() == FieldSlime.Status.NO_TOOL, "Missing axe pauses logging"))
        .thenIdle(30)
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockState(rig.block).is(Blocks.OAK_LOG)
                      && m.getActivityProgress() == 0,
                  "Partial work cannot complete after losing its axe");
              resumed[0] = m.tickCount;
              m.setTool(new ItemStack(Items.WOODEN_AXE));
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getBlockState(rig.block).isAir(), "Replacement axe resumes"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  m.tickCount - resumed[0] >= 29 && m.getTool().getDamageValue() == 1,
                  "Replacement starts its own interval and is charged once");
              attempts.close();
              rig.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "tool_breaking", timeoutTicks = 1200)
  public static void lumberjackDoesNotCutWithAnUnrelatedPickaxe(GameTestHelper h) {
    var rig = logger(h, efficient(Items.DIAMOND_PICKAXE), 32);
    var m = (LumberjackSlime) rig.pet;
    start(h, rig)
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    m.getFieldStatus() == FieldSlime.Status.NO_TOOL, "Lumberjack requires an axe"))
        .thenIdle(35)
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockState(rig.block).is(Blocks.OAK_LOG)
                      && m.getActivityProgress() == 0
                      && m.getTool().getDamageValue() == 0,
                  "Wrong equipment cannot cut or wear");
              rig.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "tool_breaking", timeoutTicks = 1200)
  public static void farmingRequiresAHoeAndResumesItsInstantCropHarvest(GameTestHelper h) {
    var rig = farmer(h, ItemStack.EMPTY);
    var m = (FarmerSlime) rig.pet;
    start(h, rig)
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    m.getFieldStatus() == FieldSlime.Status.NO_TOOL, "Farmer requires a hoe"))
        .thenIdle(35)
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockState(rig.block).is(Blocks.WHEAT)
                      && m.getActivityProgress() == 0,
                  "No hoe cannot harvest");
              m.setTool(new ItemStack(Items.IRON_HOE));
            })
        .thenWaitUntil(
            () -> h.assertTrue(m.getActivityProgress() > 0, "Provided hoe resumes crop work"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  !h.getLevel().getBlockState(rig.block).is(Blocks.WHEAT), "Mature crop harvested");
              rig.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "tool_breaking", timeoutTicks = 1200)
  public static void farmerDoesNotHarvestWithAnUnrelatedAxe(GameTestHelper h) {
    var rig = farmer(h, efficient(Items.DIAMOND_AXE));
    var m = (FarmerSlime) rig.pet;
    start(h, rig)
        .thenWaitUntil(
            () ->
                h.assertTrue(m.getFieldStatus() == FieldSlime.Status.NO_TOOL, "Wrong farming tool"))
        .thenIdle(35)
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockState(rig.block).is(Blocks.WHEAT)
                      && m.getActivityProgress() == 0,
                  "Axe cannot replace hoe at maximum Work Speed");
              rig.close();
            })
        .thenSucceed();
  }
}
