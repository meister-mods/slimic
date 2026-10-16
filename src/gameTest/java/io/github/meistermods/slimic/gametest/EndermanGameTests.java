package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.*;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EndermanGameTests {
  @GameTest(template = "empty", batch = "enderman")
  public static void endermenReceiveNoAddedCompanionInteraction(GameTestHelper h) {
    var e = EntityType.ENDERMAN.create(h.getLevel());
    var creeper = EntityType.CREEPER.create(h.getLevel());
    var zombie = EntityType.HUSK.create(h.getLevel());
    int end = e.targetSelector.getAvailableGoals().size(),
        creep = creeper.targetSelector.getAvailableGoals().size(),
        hostile = zombie.targetSelector.getAvailableGoals().size();
    FriendlySlimeInteractions.hostileTargets(new EntityJoinLevelEvent(e, h.getLevel()));
    FriendlySlimeInteractions.hostileTargets(new EntityJoinLevelEvent(creeper, h.getLevel()));
    FriendlySlimeInteractions.hostileTargets(new EntityJoinLevelEvent(zombie, h.getLevel()));
    h.assertTrue(
        e.targetSelector.getAvailableGoals().size() == end
            && creeper.targetSelector.getAvailableGoals().size() == creep
            && zombie.targetSelector.getAvailableGoals().size() == hostile + 1,
        "Endermen and creepers retain native targeting; ordinary hostile mobs still target"
            + " companions");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "enderman")
  public static void endermanAttackingCompanionUsesHostileFilter(GameTestHelper h) {
    var fighter = ModEntities.COMBAT_SLIME.get().create(h.getLevel());
    fighter.tame(h.makeMockSurvivalPlayer());
    var companion = ModEntities.SOGGY_SLIME.get().create(h.getLevel());
    companion.tame(h.makeMockSurvivalPlayer());
    var e = EntityType.ENDERMAN.create(h.getLevel());
    h.assertTrue(
        CombatSlime.targetKind(e) == CombatTarget.NEUTRAL && !fighter.mayAttack(e),
        "Calm enderman remains neutral");
    e.setTarget(companion);
    h.assertTrue(
        CombatSlime.targetKind(e) == CombatTarget.HOSTILE && fighter.mayAttack(e),
        "Enderman threatening a companion is hostile without enabling neutral targets");
    fighter.setAllowed(CombatTarget.NEUTRAL, true);
    fighter.setAllowed(CombatTarget.HOSTILE, false);
    h.assertTrue(!fighter.mayAttack(e), "Hostile filter remains authoritative during aggression");
    e.setTarget(null);
    h.assertTrue(
        CombatSlime.targetKind(e) == CombatTarget.NEUTRAL && fighter.mayAttack(e),
        "Calm enderman follows neutral filter again");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "enderman")
  public static void otherEndermanTargetsDoNotTriggerCompanionDefense(GameTestHelper h) {
    var fighter = ModEntities.COMBAT_SLIME.get().create(h.getLevel());
    fighter.tame(h.makeMockSurvivalPlayer());
    var e = EntityType.ENDERMAN.create(h.getLevel());
    e.setTarget(EntityType.COW.create(h.getLevel()));
    h.assertTrue(
        CombatSlime.targetKind(e) == CombatTarget.NEUTRAL && !fighter.mayAttack(e),
        "Unrelated aggression remains neutral");
    e.setTarget(h.makeMockSurvivalPlayer());
    h.assertTrue(
        CombatSlime.targetKind(e) == CombatTarget.NEUTRAL && !fighter.mayAttack(e),
        "Player aggression alone does not imply a slime threat");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "enderman", timeoutTicks = 240)
  public static void combatFindsAggressorThenStopsAfterItDisengages(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var fighter = ModEntities.COMBAT_SLIME.get().create(h.getLevel());
    fighter.tame(h.makeMockSurvivalPlayer());
    fighter.moveTo(a.point(8, 101, 8));
    fighter.setEquipment(0, new ItemStack(Items.IRON_SWORD));
    fighter.setWorking(true);
    EnderMan e = EntityType.ENDERMAN.create(h.getLevel());
    e.moveTo(a.point(10, 101, 8));
    e.setNoAi(true);
    e.setTarget(fighter);
    e.setPersistenceRequired();
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(fighter.blockPosition()),
                    "Combat arena ticks"))
        .thenExecute(
            () -> {
              h.getLevel().addFreshEntity(fighter);
              h.getLevel().addFreshEntity(e);
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    e.getHealth() < e.getMaxHealth() && fighter.getActivityProgress() > 0,
                    "Default hostile combat engages enderman attacking slime"))
        .thenExecute(() -> e.setTarget(null))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    fighter.getTarget() == null && !fighter.mayAttack(e),
                    "Live aggression change releases neutral target"))
        .thenExecute(
            () -> {
              fighter.discard();
              e.discard();
              a.close();
            })
        .thenSucceed();
  }
}
