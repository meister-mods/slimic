package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MangroveForestryGameTests {
  @GameTest(template = "empty", batch = "mangrove_forestry", timeoutTicks = 500)
  public static void raisedMangroveTrunkIsFelledAndReplanted(GameTestHelper h) {
    var site = new Site(h, false, 2);
    h.succeedWhen(
        () -> {
          site.completed();
          site.close();
        });
  }

  @GameTest(template = "empty", batch = "mangrove_forestry", timeoutTicks = 500)
  public static void shallowMangroveStumpRetainsWaterWhenReplanted(GameTestHelper h) {
    var site = new Site(h, true, 2);
    h.succeedWhen(
        () -> {
          site.completed();
          site.close();
        });
  }

  @GameTest(template = "empty", batch = "mangrove_forestry", timeoutTicks = 700)
  public static void raisedMangroveRetainsItsPropaguleRequestUntilSupplied(GameTestHelper h) {
    var site = new Site(h, false, 0);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    site.worker.getRequiredSaplingCount() == 1,
                    "Raised mangrove requests one planting"))
        .thenIdle(20)
        .thenExecute(
            () -> {
              h.assertTrue(
                  site.worker.getRequiredSaplingCount() == 1
                      && site.worker.getRequiredSapling().is(Items.MANGROVE_PROPAGULE)
                      && site.worker.getWorkStatus().reason() == WorkStatus.Reason.SEEDS_MISSING,
                  "The raised stump remains a valid, specific material request");
              site.worker.getBag().setStackInSlot(0, new ItemStack(Items.MANGROVE_PROPAGULE, 2));
            })
        .thenWaitUntil(site::completed)
        .thenExecute(site::close)
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "mangrove_forestry", timeoutTicks = 700)
  public static void recoveredMangroveWorkRetainsItsPlannedRootKinds(GameTestHelper h) {
    var site = new Site(h, true, 2);
    site.worker.setTunedVitals(7, 73, 13);
    site.worker.setCustomName(Component.literal("Rooted companion"));
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    count(site.worker, Items.MANGROVE_LOG) == 3,
                    "Canopy logs have been collected before root work"))
        .thenExecute(
            () -> {
              var saved =
                  FriendlySlimeItem.fromSlime(site.worker).getTag().getCompound("SlimeData");
              h.assertTrue(
                  saved.getLongArray("LoggingRootColumns").length == 3
                      && site.worker.getPendingPlantings() == 1,
                  "Remaining root kinds and reserved planting are stored separately");
              var tool = site.worker.getTool().copy();
              var next = ModEntities.LOGGING_SLIME.get().create(h.getLevel());
              next.load(saved);
              next.onReleased();
              h.assertTrue(
                  next.getHealth() == 7
                      && next.getStatPoints() == 13
                      && next.getCustomName().getString().equals("Rooted companion")
                      && ItemStack.matches(tool, next.getTool())
                      && next.getPendingPlantings() == 1,
                  "Recovery keeps health, points, identity traits, tool and pending planting");
              site.worker.discard();
              site.worker = next;
              site.goals = goals(next);
            })
        .thenWaitUntil(site::completed)
        .thenExecute(
            () -> {
              h.assertTrue(
                  site.worker.getHealth() == 7 && site.worker.getStatPoints() == 13,
                  "Finishing recovered work retains permanent traits");
              site.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "mangrove_forestry", timeoutTicks = 700)
  public static void aPlayerRootReplacingAPlannedLogRemainsUntouched(GameTestHelper h) {
    var site = new Site(h, false, 2);
    var changed = site.base.above(3);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    site.worker.getPendingPlantings() == 1, "A protected tree plan exists"))
        .thenExecute(
            () -> h.getLevel().setBlock(changed, Blocks.MANGROVE_ROOTS.defaultBlockState(), 18))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getBlockState(site.base).is(Blocks.MANGROVE_PROPAGULE)
                        && site.worker.getPendingPlantings() == 0,
                    "The rest of the plan completes"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockState(changed).is(Blocks.MANGROVE_ROOTS)
                      && count(site.worker, Items.MANGROVE_LOG) == 2
                      && count(site.worker, Items.MANGROVE_ROOTS) == 3,
                  "Only explicitly planned roots are harvested; a changed log position is"
                      + " preserved");
              site.close();
            })
        .thenSucceed();
  }

  static int count(BagSlime bag, Item item) {
    int count = 0;
    for (int i = 0; i < bag.getBag().getSlots(); i++)
      if (bag.getBag().getStackInSlot(i).is(item))
        count += bag.getBag().getStackInSlot(i).getCount();
    return count;
  }

  private static GoalSelector goals(Mob mob) {
    try {
      var field = Mob.class.getDeclaredField("goalSelector");
      field.setAccessible(true);
      return (GoalSelector) field.get(mob);
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static final class Site {
    final GameTestHelper helper;
    final LoadedWorkArea area;
    final BlockPos base;
    final boolean wet;
    LoggingSlime worker;
    GoalSelector goals;
    boolean active = true;

    Site(GameTestHelper h, boolean wet, int propagules) {
      helper = h;
      this.wet = wet;
      area = new LoadedWorkArea(h);
      area.clearSurroundingTrees();
      var level = h.getLevel();
      base = BlockPos.containing(area.point(8, 101, 8));
      level.setBlock(base.below(), Blocks.MUD.defaultBlockState(), 18);
      for (int y = 0; y < 3; y++)
        level.setBlock(
            base.above(y),
            Blocks.MANGROVE_ROOTS
                .defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, wet && y == 0),
            18);
      for (int y = 3; y < 6; y++)
        level.setBlock(base.above(y), Blocks.MANGROVE_LOG.defaultBlockState(), 18);
      level.setBlock(
          base.above(6),
          Blocks.MANGROVE_LEAVES
              .defaultBlockState()
              .setValue(LeavesBlock.PERSISTENT, false)
              .setValue(LeavesBlock.DISTANCE, 1),
          18);
      h.assertTrue(
          level.getBlockState(base.below()).is(BlockTags.DIRT),
          "Native mud supports planted propagules");
      worker = ModEntities.LOGGING_SLIME.get().create(level);
      worker.tame(h.makeMockSurvivalPlayer());
      worker.moveTo(area.point(6.5, 101, 8.5));
      worker.onReleased();
      worker.setWorking(true);
      worker.setNoAi(true);
      worker.setTool(new ItemStack(Items.DIAMOND_AXE));
      if (propagules > 0)
        worker.getBag().setStackInSlot(0, new ItemStack(Items.MANGROVE_PROPAGULE, propagules));
      goals = goals(worker);
      h.onEachTick(
          () -> {
            if (active) {
              worker.tickCount++;
              worker.setOnGround(true);
              goals.tick();
            }
          });
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
                    close();
                  }

                  @Override
                  public void testFailed(GameTestInfo info) {
                    close();
                  }
                });
      } catch (ReflectiveOperationException failure) {
        throw new IllegalStateException(failure);
      }
    }

    void completed() {
      var state = helper.getLevel().getBlockState(base);
      helper.assertTrue(
          state.is(Blocks.MANGROVE_PROPAGULE)
              && count(worker, Items.MANGROVE_LOG) == 3
              && worker.getPendingPlantings() == 0,
          "Raised logs and their central root column are harvested and replanted; status="
              + worker.getWorkStatus());
      helper.assertTrue(
          count(worker, Items.MANGROVE_PROPAGULE) == 1
              && count(worker, Items.MANGROVE_ROOTS) == 3
              && state.getValue(BlockStateProperties.WATERLOGGED) == wet
              && state.getValue(MangrovePropaguleBlock.AGE) == 4,
          "One propagule is consumed, roots remain cargo, and native planted age and waterlogging"
              + " match");
    }

    void close() {
      if (!active) return;
      active = false;
      worker.discard();
      area.close();
    }
  }
}
