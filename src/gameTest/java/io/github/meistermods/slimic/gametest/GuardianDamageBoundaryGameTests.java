package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuardianDamageBoundaryGameTests {
  @GameTest(template = "empty", batch = "guardian_damage")
  public static void untamedGuardianCanTakeDamageFromATamedAttackerWithoutAnOwner(
      GameTestHelper h) {
    var guard = ModEntities.GUARDIAN_ANGEL_SLIME.get().create(h.getLevel());
    var wolf = EntityType.WOLF.create(h.getLevel());
    wolf.tame(h.makeMockSurvivalPlayer());
    h.assertTrue(
        guard.hurt(h.getLevel().damageSources().mobAttack(wolf), 2)
            && guard.getOwnerUUID() == null
            && guard.getHealth() < guard.getMaxHealth()
            && guard.getBeamTargetId() < 0,
        "Untamed guardian damage is safe and does not create an owner-dependent retaliation");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "guardian_damage", timeoutTicks = 800)
  public static void absorbedDamageStillTriggersGuardianSelfDefense(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var guard = ModEntities.GUARDIAN_ANGEL_SLIME.get().create(h.getLevel());
    guard.tame(h.makeMockSurvivalPlayer());
    guard.moveTo(a.point(6, 103, 8));
    guard.setWaiting(true);
    guard.setAbsorptionAmount(6);
    var attacker = EntityType.IRON_GOLEM.create(h.getLevel());
    attacker.setNoAi(true);
    attacker.moveTo(a.point(9, 101, 8));
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(guard.blockPosition()),
                    "Guardian area is entity-ticking"))
        .thenExecute(
            () -> {
              h.getLevel().addFreshEntity(guard);
              h.getLevel().addFreshEntity(attacker);
            })
        .thenWaitUntil(() -> h.assertTrue(guard.tickCount > 0, "Guard is ticking"))
        .thenExecute(
            () -> {
              var health = guard.getHealth();
              guard.hurt(h.getLevel().damageSources().mobAttack(attacker), 2);
              h.assertTrue(
                  guard.getHealth() == health && guard.getAbsorptionAmount() == 4,
                  "Attack consumed absorption without reducing health");
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    attacker.getHealth() < 100,
                    "Actual absorbed damage still causes beam retaliation during Wait"))
        .thenExecute(
            () -> {
              attacker.discard();
              guard.discard();
              a.close();
            })
        .thenSucceed();
  }
}
