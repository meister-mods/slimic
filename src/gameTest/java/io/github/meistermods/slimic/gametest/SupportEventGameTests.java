package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.BrokenSlimeBlockEntity;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SupportEventGameTests {
  private static void tickWork(GameTestHelper h, GoalSelector selector) {
    try {
      selector.tickRunningGoals(true);
    } catch (RuntimeException failure) {
      h.fail("Native work tick must survive command changes in Forge callbacks: " + failure);
    }
  }

  private static GoalSelector goals(Mob mob) {
    try {
      var field = Mob.class.getDeclaredField("goalSelector");
      field.setAccessible(true);
      return (GoalSelector) field.get(mob);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @GameTest(template = "empty", batch = "support_events", timeoutTicks = 500)
  public static void completedHealingSurvivesCommandChangeInHealEvent(GameTestHelper h) {
    healingCommandChange(h, false);
  }

  @GameTest(template = "empty", batch = "support_events", timeoutTicks = 500)
  public static void cancelledHealingSurvivesCommandChangeInHealEvent(GameTestHelper h) {
    healingCommandChange(h, true);
  }

  private static void healingCommandChange(GameTestHelper h, boolean cancel) {
    var area = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var healer = ModEntities.HEALING_SLIME.get().create(h.getLevel());
    healer.tame(owner);
    healer.moveTo(area.point(7.5, 101, 8.5));
    healer.onReleased();
    healer.setWorking(true);
    healer.setTunedVitals(16, 0, 0);
    healer.getBag().setStackInSlot(0, new ItemStack(Items.SLIME_BALL, 5));
    var patient = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    patient.tame(owner);
    patient.moveTo(area.point(8.5, 101, 8.5));
    patient.setNoAi(true);
    patient.setHealth(5);
    int[] calls = {0};
    Consumer<LivingHealEvent> command =
        event -> {
          if (event.getEntity() != patient) return;
          calls[0]++;
          healer.setWorking(false);
          healer.setWaiting(true);
          if (cancel) event.setCanceled(true);
        };
    var selector = goals(healer);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(healer.blockPosition()), "Arena ticks"))
        .thenExecute(
            () -> {
              h.getLevel().addFreshEntity(patient);
              h.getLevel().addFreshEntity(healer);
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    healer.getWorkStatus().reason() == WorkStatus.Reason.EXHAUSTED
                        && selector
                            .getRunningGoals()
                            .anyMatch(
                                g ->
                                    g.getGoal().getClass().getEnclosingClass()
                                        == HealingSlime.class),
                    "Treatment goal is running"))
        .thenExecute(
            () -> {
              healer.setTunedVitals(16, 100, 0);
              MinecraftForge.EVENT_BUS.addListener(command);
              try {
                tickWork(h, selector);
                h.assertTrue(calls[0] == 1, "The native healing hook ran once");
                h.assertTrue(
                    patient.getHealth() == (cancel ? 5 : 9)
                        && healer.getBag().getStackInSlot(0).getCount() == (cancel ? 5 : 4)
                        && healer.getActivityProgress() == (cancel ? 0 : 1),
                    "Costs match the committed heal, even when its hook changes the command");
                h.assertTrue(
                    healer.isOrderedToSit() && !healer.isWorking(),
                    "The event's Wait command remains in effect");
              } finally {
                MinecraftForge.EVENT_BUS.unregister(command);
                healer.discard();
                patient.discard();
                area.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "support_events", timeoutTicks = 500)
  public static void protectionHookCanStopRevivalBeforeWorldChanges(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var healer = ModEntities.HEALING_SLIME.get().create(h.getLevel());
    healer.tame(owner);
    healer.moveTo(area.point(7.5, 101, 8.5));
    healer.onReleased();
    healer.setWorking(true);
    healer.setTunedVitals(16, 0, 0);
    healer.getBag().setStackInSlot(0, new ItemStack(Items.SLIME_BALL, 5));
    var patient = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    patient.tame(owner);
    var pos = BlockPos.containing(area.point(8.5, 101, 8.5));
    h.getLevel().setBlockAndUpdate(pos, ModBlocks.BROKEN_SLIME.get().defaultBlockState());
    ((BrokenSlimeBlockEntity) h.getLevel().getBlockEntity(pos))
        .setSlime(FriendlySlimeItem.fromSlime(patient));
    var selector = goals(healer);
    int[] calls = {0};
    Consumer<BlockEvent.BreakEvent> command =
        event -> {
          if (!event.getPos().equals(pos)) return;
          calls[0]++;
          healer.setWorking(false);
          healer.setWaiting(true);
        };
    h.startSequence()
        .thenWaitUntil(() -> h.assertTrue(h.getLevel().isPositionEntityTicking(pos), "Arena ticks"))
        .thenExecute(() -> h.getLevel().addFreshEntity(healer))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    healer.getWorkStatus().reason() == WorkStatus.Reason.EXHAUSTED
                        && selector
                            .getRunningGoals()
                            .anyMatch(
                                g ->
                                    g.getGoal().getClass().getEnclosingClass()
                                        == HealingSlime.class),
                    "Revival goal is running"))
        .thenExecute(
            () -> {
              healer.setTunedVitals(16, 100, 0);
              MinecraftForge.EVENT_BUS.addListener(command);
              try {
                tickWork(h, selector);
                h.assertTrue(calls[0] == 1, "The native protection hook ran once");
                h.assertTrue(
                    h.getLevel().getBlockEntity(pos) instanceof BrokenSlimeBlockEntity
                        && h.getLevel().getEntity(patient.getUUID()) == null
                        && healer.getBag().getStackInSlot(0).getCount() == 5
                        && healer.getActivityProgress() == 0,
                    "Wait during protection checking preserves the block and all revival"
                        + " resources");
              } finally {
                MinecraftForge.EVENT_BUS.unregister(command);
                healer.discard();
                var restored = h.getLevel().getEntity(patient.getUUID());
                if (restored != null) restored.discard();
                area.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "support_events", timeoutTicks = 500)
  public static void heraldStopsScanningWhenSignalCallbackChangesCommand(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var level = h.getLevel();
    var owner = h.makeMockSurvivalPlayer();
    var herald = ModEntities.HERALD_SLIME.get().create(level);
    herald.tame(owner);
    herald.moveTo(area.point(8.5, 101, 8.5));
    herald.setNoAi(true);
    herald.setDelayMinutes(1);
    herald.setMonitoring(true);
    var workers = new java.util.ArrayList<MiningSlime>();
    for (int i = 0; i < 2; i++) {
      var worker = ModEntities.MINING_SLIME.get().create(level);
      worker.tame(owner);
      worker.moveTo(area.point(7.5 + i * 2, 101, 10.5));
      worker.onReleased();
      worker.setWorking(true);
      var saved = worker.saveWithoutId(new net.minecraft.nbt.CompoundTag());
      saved.putInt("MiningStatus", MiningSlime.Status.NO_TOOL.ordinal());
      saved.putInt("BlockedWorkTicks", 1200);
      worker.load(saved);
      worker.setNoAi(true);
      workers.add(worker);
    }
    int[] calls = {0};
    Consumer<EntityJoinLevelEvent> command =
        event -> {
          if (!(event.getEntity() instanceof FireworkRocketEntity rocket)
              || rocket.getOwner() != herald) return;
          calls[0]++;
          herald.setMonitoring(false);
          herald.setWaiting(true);
        };
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    level.isPositionEntityTicking(herald.blockPosition()), "Signal area ticks"))
        .thenExecute(
            () -> {
              workers.forEach(level::addFreshEntity);
              MinecraftForge.EVENT_BUS.addListener(command);
              try {
                herald.tickCount = 20;
                goals(herald).tick();
                h.assertTrue(
                    calls[0] == 1,
                    "The first signal callback stops later signals in the same scan: " + calls[0]);
                h.assertTrue(
                    herald.isOrderedToSit() && !herald.isWorking(),
                    "Wait remains effective after signaling");
                var saved = herald.saveWithoutId(new net.minecraft.nbt.CompoundTag());
                h.assertTrue(
                    saved.getCompound("HeraldWarnings").size() == 1,
                    "Only the completed signal records a warning");
              } finally {
                MinecraftForge.EVENT_BUS.unregister(command);
                level
                    .getEntitiesOfClass(
                        FireworkRocketEntity.class,
                        new net.minecraft.world.phys.AABB(herald.blockPosition()).inflate(16),
                        rocket -> rocket.getOwner() == herald)
                    .forEach(FireworkRocketEntity::discard);
                workers.forEach(FriendlySlime::discard);
                herald.discard();
                area.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "support_events", timeoutTicks = 500)
  public static void followTrailPlacementCanBeStoppedByItsProtectionHook(GameTestHelper h) {
    trailCommandChange(h, false);
  }

  @GameTest(template = "empty", batch = "support_events", timeoutTicks = 500)
  public static void roamingTrailPlacementCanBeStoppedByItsProtectionHook(GameTestHelper h) {
    trailCommandChange(h, true);
  }

  private static void trailCommandChange(GameTestHelper h, boolean roam) {
    var area = new LoadedWorkArea(h);
    var slime = ModEntities.SOGGY_SLIME.get().create(h.getLevel());
    slime.tame(h.makeMockSurvivalPlayer());
    slime.moveTo(area.point(8.5, 101, 8.5));
    slime.onReleased();
    slime.setNoAi(true);
    slime.setOnGround(true);
    slime.setRoaming(roam);
    slime.setWaiting(false);
    int[] calls = {0};
    Consumer<BlockEvent.EntityPlaceEvent> command =
        event -> {
          if (!event.getPos().equals(slime.blockPosition())) return;
          calls[0]++;
          slime.setRoaming(false);
          slime.setWaiting(true);
        };
    MinecraftForge.EVENT_BUS.addListener(command);
    Runnable cleanup =
        () -> {
          MinecraftForge.EVENT_BUS.unregister(command);
          slime.discard();
          area.close();
        };
    h.runAfterDelay(499, cleanup);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(slime.blockPosition()),
                    "Trail area ticks"))
        .thenExecute(() -> h.getLevel().addFreshEntity(slime))
        .thenWaitUntil(
            () -> h.assertTrue(calls[0] == 1, "Native trail placement reaches protection checking"))
        .thenExecute(
            () -> {
              try {
                h.assertTrue(
                    slime.isOrderedToSit()
                        && !slime.isRoaming()
                        && h.getLevel().getBlockState(slime.blockPosition()).isAir()
                        && slime.getActivityProgress() == 0
                        && slime.getStamina() == 100,
                    "Wait during trail protection restores air and preserves stamina and activity");
              } finally {
                cleanup.run();
              }
            })
        .thenSucceed();
  }
}
