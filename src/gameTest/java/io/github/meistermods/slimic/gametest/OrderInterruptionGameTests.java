package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OrderInterruptionGameTests {
  private static int nextId = 200000;

  private static GoalSelector selector(Mob mob) {
    try {
      var field = Mob.class.getDeclaredField("goalSelector");
      field.setAccessible(true);
      return (GoalSelector) field.get(mob);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static int count(BagSlime slime, Item item) {
    int total = 0;
    for (int i = 0; i < slime.getBag().getSlots(); i++)
      if (slime.getBag().getStackInSlot(i).is(item))
        total += slime.getBag().getStackInSlot(i).getCount();
    return total;
  }

  @GameTest(template = "empty", batch = "order_interruption", timeoutTicks = 180)
  public static void cancelledTransportCannotCollectFromItsStillRunningGoal(GameTestHelper h) {
    cancellation(h, 0);
  }

  @GameTest(template = "empty", batch = "order_interruption", timeoutTicks = 180)
  public static void cancelledWarehouseCannotCollectFromItsStillRunningGoal(GameTestHelper h) {
    cancellation(h, 1);
  }

  @GameTest(template = "empty", batch = "order_interruption", timeoutTicks = 180)
  public static void cancelledHealerCannotTreatFromItsStillRunningGoal(GameTestHelper h) {
    cancellation(h, 2);
  }

  private static void cancellation(GameTestHelper h, int kind) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    BagSlime actor =
        kind == 0
            ? ModEntities.TRANSPORT_SLIME.get().create(h.getLevel())
            : kind == 1
                ? ModEntities.WAREHOUSE_SLIME.get().create(h.getLevel())
                : ModEntities.HEALING_SLIME.get().create(h.getLevel());
    actor.setId(nextId);
    nextId += 20;
    actor.tame(owner);
    actor.moveTo(a.point(6.5, 101, 8.5));
    actor.onReleased();
    actor.setWorking(true);
    actor.setTunedVitals(16, 0, 0);
    var target = ModEntities.HEALING_SLIME.get().create(h.getLevel());
    target.tame(owner);
    target.moveTo(a.point(8.5, 101, 8.5));
    target.onReleased();
    if (kind == 2) {
      target.setNoAi(true);
      target.setHealth(5);
      actor.getBag().setStackInSlot(0, new ItemStack(Items.SLIME_BALL));
    } else {
      target.setWorking(true);
      for (int i = 0; i < target.getCapacity(); i++)
        target.getBag().setStackInSlot(i, new ItemStack(Items.DIRT, 64));
      a.chest(6, 101, 10);
    }
    var goals = selector(actor);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(actor.blockPosition()), "Arena ticks"))
        .thenExecute(
            () -> {
              h.getLevel().addFreshEntity(target);
              h.getLevel().addFreshEntity(actor);
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    actor.getWorkStatus().reason() == WorkStatus.Reason.EXHAUSTED
                        && goals
                            .getRunningGoals()
                            .anyMatch(
                                g ->
                                    g.getGoal().getClass().getEnclosingClass()
                                        == (kind == 2 ? HealingSlime.class : TransportSlime.class))
                        && (kind == 2 || target.needsUnloading()),
                    "Work goal is running before command cancellation"))
        .thenExecute(
            () -> {
              actor.setWorking(false);
              if (kind != 2) actor.setWaiting(true);
              actor.setTunedVitals(16, 100, 0);
              // Native goal scheduling can tick a running goal before its next eligibility refresh.
              goals.tickRunningGoals(true);
              h.assertTrue(
                  kind == 2
                      ? target.getHealth() == 5 && count(actor, Items.SLIME_BALL) == 1
                      : !actor.hasCargo() && count(target, Items.DIRT) == 576,
                  "Follow/Wait cancels resource changes immediately, including an already-running"
                      + " goal");
            })
        .thenIdle(3)
        .thenExecute(
            () -> {
              actor.discard();
              target.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_interruption", timeoutTicks = 220)
  public static void cancelledMinerPreservesTerrainAndTool(GameTestHelper h) {
    terrainCancellation(h, 0);
  }

  @GameTest(template = "empty", batch = "order_interruption", timeoutTicks = 220)
  public static void cancelledFarmerPreservesCropAndTool(GameTestHelper h) {
    terrainCancellation(h, 1);
  }

  @GameTest(template = "empty", batch = "order_interruption", timeoutTicks = 220)
  public static void cancelledLumberjackPreservesPreparedTreeAndTool(GameTestHelper h) {
    terrainCancellation(h, 2);
  }

  private static void terrainCancellation(GameTestHelper h, int kind) {
    var a = new LoadedWorkArea(h);
    var level = h.getLevel();
    if (kind == 2) a.clearSurroundingTrees();
    var target = BlockPos.containing(a.point(8, kind == 0 ? 102 : 101, 8));
    var original =
        kind == 0
            ? Blocks.DIRT.defaultBlockState()
            : kind == 1
                ? Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7)
                : Blocks.OAK_LOG.defaultBlockState();
    if (kind == 1) {
      level.setBlockAndUpdate(
          target.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
      level.setBlockAndUpdate(target.below().east(), Blocks.WATER.defaultBlockState());
    } else if (kind == 2) {
      level.setBlockAndUpdate(target.below(), Blocks.DIRT.defaultBlockState());
      level.setBlockAndUpdate(
          target.above(),
          Blocks.OAK_LEAVES
              .defaultBlockState()
              .setValue(LeavesBlock.PERSISTENT, false)
              .setValue(LeavesBlock.DISTANCE, 1));
    }
    level.setBlockAndUpdate(target, original);
    BagSlime actor =
        kind == 0
            ? ModEntities.MINING_SLIME.get().create(level)
            : kind == 1
                ? ModEntities.FARMER_SLIME.get().create(level)
                : ModEntities.LUMBERJACK_SLIME.get().create(level);
    actor.tame(h.makeMockSurvivalPlayer());
    actor.moveTo(a.point(6.5, 101, 8.5));
    actor.onReleased();
    if (actor instanceof MiningSlime miner) {
      miner.setTool(0, new ItemStack(Items.DIAMOND_PICKAXE));
      miner.setTool(1, new ItemStack(Items.DIAMOND_SHOVEL));
    } else
      ((FieldSlime) actor).setTool(new ItemStack(kind == 1 ? Items.IRON_HOE : Items.DIAMOND_AXE));
    if (kind == 2) actor.getBag().setStackInSlot(0, new ItemStack(Items.OAK_SAPLING));
    actor.setWorking(true);
    actor.saveWithoutId(new CompoundTag());
    actor.setTunedVitals(16, kind == 2 ? 100 : 0, 0);
    SlimeStat.REACH.tune(actor, 32);
    SlimeStat.WORK_SPEED.tune(actor, kind == 2 ? .1 : 32);
    var goals = selector(actor);
    h.startSequence()
        .thenWaitUntil(
            () -> h.assertTrue(level.isPositionEntityTicking(actor.blockPosition()), "Arena ticks"))
        .thenExecute(() -> level.addFreshEntity(actor))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    kind == 2
                        ? ((LumberjackSlime) actor).getPendingPlantings() == 1
                            && actor.getActivityProgress() == 0
                        : actor.getWorkStatus().reason() == WorkStatus.Reason.EXHAUSTED,
                    "Native work goal has prepared its next action"))
        .thenExecute(
            () -> {
              actor.setWorking(false);
              actor.setWaiting(true);
              actor.setTunedVitals(16, 100, 0);
              actor.tickCount = 100;
              SlimeStat.WORK_SPEED.tune(actor, 32);
              goals.tickRunningGoals(true);
              h.assertTrue(
                  level.getBlockState(target).equals(original) && actor.getActivityProgress() == 0,
                  "A stopped goal cannot perform its prepared world edit");
              h.assertTrue(
                  actor.getMainHandItem().getDamageValue() == 0
                      && actor.getOffhandItem().getDamageValue() == 0,
                  "Command cancellation consumes no tool durability");
            })
        .thenIdle(3)
        .thenExecute(
            () -> {
              actor.discard();
              a.close();
            })
        .thenSucceed();
  }
}
