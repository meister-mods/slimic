package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkCallbackGameTests {
  private static GoalSelector selector(Mob mob) {
    try {
      var field = Mob.class.getDeclaredField("goalSelector");
      field.setAccessible(true);
      return (GoalSelector) field.get(mob);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void stop(BagSlime actor) {
    actor.setWorking(false);
    actor.setWaiting(true);
  }

  private static void drive(
      GameTestHelper h, BagSlime actor, int[] calls, Runnable cleanup, Runnable check) {
    var goals = selector(actor);
    boolean[] active = {false};
    h.onEachTick(
        () -> {
          if (!active[0] || calls[0] > 0) return;
          actor.tickCount++;
          actor.setOnGround(true);
          try {
            goals.tick();
          } catch (RuntimeException failure) {
            active[0] = false;
            cleanup.run();
            h.fail("Native work callback must remain safe when its order changes: " + failure);
          }
        });
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(actor.blockPosition()), "Work area ticks"))
        .thenExecute(() -> active[0] = true)
        .thenWaitUntil(
            () -> h.assertTrue(calls[0] == 1, "Native action reaches its Forge callback"))
        .thenExecute(
            () -> {
              active[0] = false;
              try {
                check.run();
                h.assertTrue(
                    actor.isOrderedToSit() && !actor.isWorking(),
                    "Wait command survives the callback");
                h.assertTrue(
                    actor.getNavigation().isDone(),
                    "Wait stops the route before returning from its callback");
              } finally {
                cleanup.run();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "work_callbacks", timeoutTicks = 700)
  public static void miningBreakHookCanStopPreparedMining(GameTestHelper h) {
    blockCommandChange(h, 0);
  }

  @GameTest(template = "empty", batch = "work_callbacks", timeoutTicks = 700)
  public static void farmingBreakHookCanStopPreparedHarvest(GameTestHelper h) {
    blockCommandChange(h, 1);
  }

  @GameTest(template = "empty", batch = "work_callbacks", timeoutTicks = 700)
  public static void loggingBreakHookCanStopPreparedHarvest(GameTestHelper h) {
    blockCommandChange(h, 2);
  }

  private static void blockCommandChange(GameTestHelper h, int kind) {
    var area = new LoadedWorkArea(h);
    var level = h.getLevel();
    if (kind == 2) area.clearSurroundingTrees();
    var pos = BlockPos.containing(area.point(8, kind == 0 ? 102 : 101, 8));
    var state =
        kind == 0
            ? Blocks.STONE.defaultBlockState()
            : kind == 1
                ? Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7)
                : Blocks.OAK_LOG.defaultBlockState();
    if (kind == 1) {
      level.setBlockAndUpdate(
          pos.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
      level.setBlockAndUpdate(pos.below().east(), Blocks.WATER.defaultBlockState());
    } else if (kind == 2) {
      level.setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState());
      level.setBlockAndUpdate(
          pos.above(),
          Blocks.OAK_LEAVES
              .defaultBlockState()
              .setValue(LeavesBlock.PERSISTENT, false)
              .setValue(LeavesBlock.DISTANCE, 1));
    }
    level.setBlockAndUpdate(pos, state);
    BagSlime actor =
        kind == 0
            ? ModEntities.MINING_SLIME.get().create(level)
            : kind == 1
                ? ModEntities.FARMING_SLIME.get().create(level)
                : ModEntities.LOGGING_SLIME.get().create(level);
    actor.tame(h.makeMockSurvivalPlayer());
    actor.moveTo(area.point(6.5, 101, 8.5));
    actor.onReleased();
    actor.setNoAi(true);
    if (actor instanceof MiningSlime miner) {
      miner.setTool(0, new ItemStack(Items.DIAMOND_PICKAXE));
      miner.setTool(1, new ItemStack(Items.DIAMOND_SHOVEL));
    } else
      ((FieldSlime) actor)
          .setTool(new ItemStack(kind == 1 ? Items.DIAMOND_HOE : Items.DIAMOND_AXE));
    if (kind == 2) actor.getBag().setStackInSlot(0, new ItemStack(Items.OAK_SAPLING));
    SlimeStat.REACH.tune(actor, 32);
    SlimeStat.WORK_SPEED.tune(actor, 32);
    actor.setWorking(true);
    int[] calls = {0};
    Consumer<BlockEvent.BreakEvent> command =
        event -> {
          if (!event.getPos().equals(pos)) return;
          calls[0]++;
          stop(actor);
        };
    MinecraftForge.EVENT_BUS.addListener(command);
    Runnable cleanup =
        () -> {
          MinecraftForge.EVENT_BUS.unregister(command);
          actor.discard();
          area.close();
        };
    h.runAfterDelay(699, cleanup);
    drive(
        h,
        actor,
        calls,
        cleanup,
        () ->
            h.assertTrue(
                level.getBlockState(pos).equals(state)
                    && actor.getActivityProgress() == 0
                    && actor.getMainHandItem().getDamageValue() == 0,
                "Wait during the break hook preserves terrain, work progress and tool durability"));
  }

  @GameTest(template = "empty", batch = "work_callbacks", timeoutTicks = 700)
  public static void cleaningPickupHookCanStopPreparedCollection(GameTestHelper h) {
    pickupCommandChange(h, false);
  }

  @GameTest(template = "empty", batch = "work_callbacks", timeoutTicks = 700)
  public static void loggingPickupHookCanStopPreparedCollection(GameTestHelper h) {
    pickupCommandChange(h, true);
  }

  private static void pickupCommandChange(GameTestHelper h, boolean logger) {
    var area = new LoadedWorkArea(h);
    if (logger) area.clearSurroundingTrees();
    BagSlime actor =
        logger
            ? ModEntities.LOGGING_SLIME.get().create(h.getLevel())
            : ModEntities.CLEANING_SLIME.get().create(h.getLevel());
    actor.tame(h.makeMockSurvivalPlayer());
    actor.moveTo(area.point(6.5, 101, 8.5));
    actor.onReleased();
    actor.setNoAi(true);
    if (logger) ((LoggingSlime) actor).setTool(new ItemStack(Items.DIAMOND_AXE));
    SlimeStat.REACH.tune(actor, 32);
    SlimeStat.WORK_SPEED.tune(actor, 32);
    actor.setWorking(true);
    var item =
        new ItemEntity(
            h.getLevel(),
            actor.getX() + 2,
            101,
            actor.getZ(),
            new ItemStack(logger ? Items.OAK_SAPLING : Items.DIAMOND, 3));
    item.setNoPickUpDelay();
    item.setNoGravity(true);
    h.getLevel().addFreshEntity(item);
    int[] calls = {0};
    Consumer<EntityItemPickupEvent> command =
        event -> {
          if (event.getItem() != item) return;
          calls[0]++;
          stop(actor);
        };
    MinecraftForge.EVENT_BUS.addListener(command);
    Runnable cleanup =
        () -> {
          MinecraftForge.EVENT_BUS.unregister(command);
          actor.discard();
          item.discard();
          area.close();
        };
    h.runAfterDelay(699, cleanup);
    drive(
        h,
        actor,
        calls,
        cleanup,
        () ->
            h.assertTrue(
                !actor.hasCargo()
                    && item.isAlive()
                    && item.getItem().getCount() == 3
                    && actor.getActivityProgress() == 0,
                "Wait during pickup leaves the world item and bag untouched"));
  }

  @GameTest(template = "empty", batch = "work_callbacks", timeoutTicks = 700)
  public static void plantingHookCanStopAndRestoreTheOriginalPlot(GameTestHelper h) {
    farmPlacementChange(h, false);
  }

  @GameTest(template = "empty", batch = "work_callbacks", timeoutTicks = 700)
  public static void tillingHookCanStopAndRestoreTheOriginalSoil(GameTestHelper h) {
    farmPlacementChange(h, true);
  }

  private static void farmPlacementChange(GameTestHelper h, boolean till) {
    var area = new LoadedWorkArea(h);
    var level = h.getLevel();
    var soil = BlockPos.containing(area.point(8, 100, 8));
    level.setBlockAndUpdate(
        soil, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
    level.setBlockAndUpdate(soil.east(), Blocks.WATER.defaultBlockState());
    var actor = ModEntities.FARMING_SLIME.get().create(level);
    actor.tame(h.makeMockSurvivalPlayer());
    actor.moveTo(area.point(till ? 8.5 : 6.5, 101, 8.5));
    actor.onReleased();
    actor.setNoAi(true);
    actor.setTool(new ItemStack(Items.DIAMOND_HOE));
    actor.getBag().setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS, 3));
    SlimeStat.REACH.tune(actor, 32);
    SlimeStat.WORK_SPEED.tune(actor, 32);
    actor.setWorking(true);
    actor.saveWithoutId(new CompoundTag());
    if (till) level.setBlockAndUpdate(soil, Blocks.DIRT.defaultBlockState());
    var pos = till ? soil : soil.above();
    var original = level.getBlockState(pos);
    int[] calls = {0};
    Consumer<BlockEvent.EntityPlaceEvent> command =
        event -> {
          if (!event.getPos().equals(pos)) return;
          calls[0]++;
          stop(actor);
        };
    MinecraftForge.EVENT_BUS.addListener(command);
    Runnable cleanup =
        () -> {
          MinecraftForge.EVENT_BUS.unregister(command);
          actor.discard();
          area.close();
        };
    h.runAfterDelay(699, cleanup);
    drive(
        h,
        actor,
        calls,
        cleanup,
        () ->
            h.assertTrue(
                level.getBlockState(pos).equals(original)
                    && actor.getBag().getStackInSlot(0).getCount() == 3
                    && actor.getActivityProgress() == 0
                    && actor.getTool().getDamageValue() == 0,
                "Wait in placement restores soil or air without seed, activity or durability"
                    + " costs"));
  }

  @GameTest(template = "empty", batch = "work_callbacks", timeoutTicks = 700)
  public static void drainageBreakHookCanStopBeforeRemovingWater(GameTestHelper h) {
    drainageCommandChange(h, false);
  }

  @GameTest(template = "empty", batch = "work_callbacks", timeoutTicks = 700)
  public static void drainagePlacementHookCanStopAndRestoreWater(GameTestHelper h) {
    drainageCommandChange(h, true);
  }

  private static void drainageCommandChange(GameTestHelper h, boolean placement) {
    var area = new LoadedWorkArea(h);
    var level = h.getLevel();
    var pos = BlockPos.containing(area.point(8, 100, 8));
    level.setBlockAndUpdate(pos.below(), Blocks.BEDROCK.defaultBlockState());
    level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
    var actor = ModEntities.MINING_SLIME.get().create(level);
    actor.tame(h.makeMockSurvivalPlayer());
    actor.moveTo(area.point(8.5, 101, 8.5));
    actor.onReleased();
    actor.setNoAi(true);
    actor.setTool(0, new ItemStack(Items.DIAMOND_PICKAXE));
    actor.getBag().setStackInSlot(0, new ItemStack(Items.BUCKET, 3));
    SlimeStat.REACH.tune(actor, 32);
    SlimeStat.WORK_SPEED.tune(actor, 32);
    actor.setWorking(true);
    int[] calls = {0};
    Consumer<BlockEvent.BreakEvent> breaking =
        event -> {
          if (placement || !event.getPos().equals(pos)) return;
          calls[0]++;
          stop(actor);
        };
    Consumer<BlockEvent.EntityPlaceEvent> placing =
        event -> {
          if (!placement || !event.getPos().equals(pos)) return;
          calls[0]++;
          stop(actor);
        };
    MinecraftForge.EVENT_BUS.addListener(breaking);
    MinecraftForge.EVENT_BUS.addListener(placing);
    Runnable cleanup =
        () -> {
          MinecraftForge.EVENT_BUS.unregister(breaking);
          MinecraftForge.EVENT_BUS.unregister(placing);
          actor.discard();
          area.close();
        };
    h.runAfterDelay(699, cleanup);
    drive(
        h,
        actor,
        calls,
        cleanup,
        () ->
            h.assertTrue(
                level.getBlockState(pos).is(Blocks.WATER)
                    && actor.getBag().getStackInSlot(0).is(Items.BUCKET)
                    && actor.getBag().getStackInSlot(0).getCount() == 3
                    && actor.getActivityProgress() == 0,
                "Wait inside drainage protection retains water and the exact bucket count without"
                    + " work costs"));
  }

  @GameTest(template = "empty", batch = "work_callbacks", timeoutTicks = 700)
  public static void wallPlacementHookCanStopAndRestoreTheFluidInlet(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var level = h.getLevel();
    for (int i = -1; i <= 16; i++)
      for (int y = 98; y <= 104; y++)
        for (var point :
            java.util.List.of(
                area.point(-1, y, i),
                area.point(16, y, i),
                area.point(i, y, -1),
                area.point(i, y, 16)))
          level.setBlock(BlockPos.containing(point), Blocks.BEDROCK.defaultBlockState(), 2);
    var pos = BlockPos.containing(area.point(16, 101, 8));
    level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
    var actor = ModEntities.MINING_SLIME.get().create(level);
    actor.tame(h.makeMockSurvivalPlayer());
    actor.moveTo(area.point(14, 101, 8.5));
    actor.onReleased();
    actor.setNoAi(true);
    actor.setTool(0, new ItemStack(Items.DIAMOND_PICKAXE));
    actor.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 3));
    actor.setWorking(true);
    int[] calls = {0};
    Consumer<BlockEvent.EntityPlaceEvent> command =
        event -> {
          if (!event.getPos().equals(pos)) return;
          calls[0]++;
          stop(actor);
        };
    MinecraftForge.EVENT_BUS.addListener(command);
    Runnable cleanup =
        () -> {
          MinecraftForge.EVENT_BUS.unregister(command);
          actor.discard();
          area.close();
        };
    h.runAfterDelay(699, cleanup);
    drive(
        h,
        actor,
        calls,
        cleanup,
        () ->
            h.assertTrue(
                level.getBlockState(pos).is(Blocks.WATER)
                    && actor.getBag().getStackInSlot(0).getCount() == 3
                    && actor.getActivityProgress() == 0,
                "Wait inside wall placement restores the inlet without consuming cobblestone"));
  }
}
