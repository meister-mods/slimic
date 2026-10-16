package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.CombatTarget;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.entity.TransportSlime;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementAuditGameTests {
  private MovementAuditGameTests() {}

  @GameTest(template = "empty", batch = "movement_audit", timeoutTicks = 400)
  public static void shrinkingDetectionCancelsAnOutsideDeposit(GameTestHelper h) {
    rangeChange(h, false);
  }

  @GameTest(template = "empty", batch = "movement_audit", timeoutTicks = 400)
  public static void shrinkingDetectionCancelsAnOutsideToolPickup(GameTestHelper h) {
    rangeChange(h, true);
  }

  private static void rangeChange(GameTestHelper h, boolean tools) {
    LoadedWorkArea arena = new LoadedWorkArea(h);
    var carrier = arena.carrier(new Vec3(8, 101, 8));
    SlimeStat.MOVEMENT_SPEED.tune(carrier, 0);
    carrier.moveTo(arena.point(1, 101, 8));
    var chest = arena.chest(14, 101, 8);
    MiningSlime worker = ModEntities.MINING_SLIME.get().create(h.getLevel());
    if (tools) {
      worker.moveTo(arena.point(8, 101, 10));
      worker.setOwnerUUID(carrier.getOwnerUUID());
      worker.setTame(true);
      worker.setNoAi(true);
      worker.onReleased();
      CompoundTag saved = worker.saveWithoutId(new CompoundTag());
      saved.putInt("MiningStatus", MiningSlime.Status.NO_TOOL.ordinal());
      worker.load(saved);
      h.getLevel().addFreshEntity(worker);
      chest.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
    } else carrier.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 11));
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    carrier.getTransportStatus()
                        == (tools
                            ? TransportSlime.Status.FETCHING_TOOL
                            : TransportSlime.Status.DELIVERING),
                    "The carrier must select the initially valid storage target"))
        .thenExecute(
            () -> {
              SlimeStat.DETECTION_RANGE.tune(carrier, 4);
              carrier.moveTo(arena.point(13, 101, 8));
            })
        .thenIdle(40)
        .thenExecute(
            () -> {
              h.assertTrue(
                  tools
                      ? chest.getItem(0).is(Items.DIAMOND_PICKAXE)
                          && !carrier.hasCargo()
                          && worker.getPickaxe().isEmpty()
                      : chest.isEmpty() && carrier.getBag().getStackInSlot(0).getCount() == 11,
                  "A previously selected chest outside the new work radius must not exchange"
                      + " items");
              carrier.discard();
              worker.discard();
              arena.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "movement_audit", timeoutTicks = 5000)
  public static void slowCleanerReturnsWithoutAFalseBlockedState(GameTestHelper h) {
    cleaningReturn(h, false);
  }

  @GameTest(template = "empty", batch = "movement_audit", timeoutTicks = 5000)
  public static void stalledCleanerCanResumeAfterMovementIsRestored(GameTestHelper h) {
    cleaningReturn(h, true);
  }

  private static void cleaningReturn(GameTestHelper h, boolean stalled) {
    LoadedWorkArea arena = new LoadedWorkArea(h);
    var cleaner = ModEntities.CLEANING_SLIME.get().create(h.getLevel());
    cleaner.tame(h.makeMockSurvivalPlayer());
    cleaner.moveTo(arena.point(14, 101, 8));
    cleaner.onReleased();
    cleaner.moveTo(arena.point(1, 101, 8));
    SlimeStat.MOVEMENT_SPEED.tune(cleaner, stalled ? 0 : .2);
    h.getLevel().addFreshEntity(cleaner);
    boolean[] checked = {false};
    boolean[] resumed = {false};
    h.onEachTick(
        () -> {
          if (!checked[0] && cleaner.tickCount >= 450) {
            if (stalled) {
              h.assertTrue(
                  cleaner.isWorkBlocked(), "A stationary cleaner must report its blocked return");
              SlimeStat.MOVEMENT_SPEED.tune(cleaner, .3);
            } else {
              h.assertTrue(
                  !cleaner.isWorkBlocked()
                      && cleaner.position().distanceToSqr(arena.point(1, 101, 8)) > .01,
                  "An advancing cleaner must not report a blocked return after 400 elapsed ticks: "
                      + cleaner.getCleaningStatus()
                      + " "
                      + cleaner.position());
            }
            checked[0] = true;
          }
          if ((!stalled || checked[0])
              && !cleaner.isWorkBlocked()
              && cleaner.position().distanceToSqr(arena.point(1, 101, 8)) > .01
              && cleaner.position().distanceToSqr(cleaner.getCleaningHome()) > 9) resumed[0] = true;
        });
    h.succeedWhen(
        () -> {
          h.assertTrue(
              checked[0]
                  && resumed[0]
                  && cleaner.position().distanceToSqr(cleaner.getCleaningHome()) <= .0625
                  && !cleaner.isWorkBlocked(),
              "The cleaner must clear a resolved movement obstruction before reaching its exact"
                  + " home");
          cleaner.discard();
          arena.close();
        });
  }

  @GameTest(template = "empty", batch = "movement_audit", timeoutTicks = 1600)
  public static void slowFighterDoesNotReportBlockedWhileAdvancing(GameTestHelper h) {
    combatTravel(h, false);
  }

  @GameTest(template = "empty", batch = "movement_audit", timeoutTicks = 1600)
  public static void stalledFighterClearsBlockedStateWhenItAdvances(GameTestHelper h) {
    combatTravel(h, true);
  }

  @GameTest(template = "empty", batch = "movement_audit", timeoutTicks = 1000)
  public static void fighterRefreshesItsRouteAfterTheTargetMoves(GameTestHelper h) {
    LoadedWorkArea arena = new LoadedWorkArea(h);
    var fighter = ModEntities.COMBAT_SLIME.get().create(h.getLevel());
    fighter.setNoAi(true);
    fighter.tame(h.makeMockSurvivalPlayer());
    fighter.moveTo(arena.point(1, 101, 8));
    fighter.setAllowed(CombatTarget.PASSIVE, true);
    fighter.setEquipment(0, new ItemStack(Items.WOODEN_SWORD));
    SlimeStat.DETECTION_RANGE.tune(fighter, 16);
    SlimeStat.MOVEMENT_SPEED.tune(fighter, .6);
    var cow = EntityType.COW.create(h.getLevel());
    cow.moveTo(arena.point(14, 101, 8));
    cow.setNoAi(true);
    h.getLevel().addFreshEntity(cow);
    h.getLevel().addFreshEntity(fighter);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    fighter.tickCount > 0 && cow.tickCount > 0,
                    "Both native entities must be ticking before pursuit"))
        .thenExecute(() -> fighter.setNoAi(false))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    fighter.getTarget() == cow && !fighter.getNavigation().isDone(),
                    "The initial pursuit must have a route before the target moves: target="
                        + fighter.getTarget()
                        + ", fighter="
                        + fighter.position()
                        + ", cow="
                        + cow.position()
                        + ", cowAlive="
                        + cow.isAlive()
                        + ", mayAttack="
                        + fighter.mayAttack(cow)
                        + ", status="
                        + fighter.getCombatStatus()
                        + ", path="
                        + fighter.getNavigation().getPath()))
        .thenExecute(() -> cow.moveTo(arena.point(1, 101, 14)))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    cow.getHealth() < 10 && fighter.distanceToSqr(cow) < 16,
                    "The fighter must reach and damage the relocated target"))
        .thenExecute(
            () -> {
              fighter.discard();
              cow.discard();
              arena.close();
            })
        .thenSucceed();
  }

  private static void combatTravel(GameTestHelper h, boolean stalled) {
    LoadedWorkArea arena = new LoadedWorkArea(h);
    var fighter = ModEntities.COMBAT_SLIME.get().create(h.getLevel());
    fighter.tame(h.makeMockSurvivalPlayer());
    fighter.moveTo(arena.point(1, 101, 8));
    fighter.setAllowed(CombatTarget.PASSIVE, true);
    fighter.setEquipment(0, new ItemStack(Items.WOODEN_SWORD));
    SlimeStat.DETECTION_RANGE.tune(fighter, 16);
    SlimeStat.MOVEMENT_SPEED.tune(fighter, stalled ? 0 : .05);
    var cow = EntityType.COW.create(h.getLevel());
    cow.moveTo(arena.point(14, 101, 8));
    cow.setNoAi(true);
    h.getLevel().addFreshEntity(cow);
    h.getLevel().addFreshEntity(fighter);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    fighter.tickCount >= 500 && fighter.getTarget() == cow,
                    "The fighter must pursue its eligible target for at least 400 ticks"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  stalled
                      ? fighter.isWorkBlocked()
                      : !fighter.isWorkBlocked()
                          && fighter.position().distanceToSqr(arena.point(1, 101, 8)) > .01,
                  "Only a stalled pursuit may be blocked: "
                      + fighter.getCombatStatus()
                      + " "
                      + fighter.position());
              if (stalled) SlimeStat.MOVEMENT_SPEED.tune(fighter, .3);
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    !fighter.isWorkBlocked()
                        && fighter.position().distanceToSqr(arena.point(1, 101, 8)) > .01
                        && fighter.distanceToSqr(cow) > 9,
                    "Resolved pursuit must clear its blocked state before entering attack reach"))
        .thenExecute(
            () -> {
              fighter.discard();
              cow.discard();
              arena.close();
            })
        .thenSucceed();
  }
}
