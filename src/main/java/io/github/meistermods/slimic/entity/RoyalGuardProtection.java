package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.Slimic;
import java.util.Comparator;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Slimic.MOD_ID)
public final class RoyalGuardProtection {
  private static final ThreadLocal<Boolean> INTERCEPTING = ThreadLocal.withInitial(() -> false);

  private RoyalGuardProtection() {}

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void protect(LivingDamageEvent event) {
    var ally = event.getEntity();
    if (ally.level().isClientSide
        || !(ally instanceof net.minecraft.world.entity.player.Player
            || ally instanceof FriendlySlime)
        || INTERCEPTING.get()
        || !Float.isFinite(event.getAmount())
        || event.getAmount() <= 0
        || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
        || !(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
    var guards =
        ally.level()
            .getEntitiesOfClass(
                RoyalGuardSlime.class,
                ally.getBoundingBox().inflate(4),
                guard -> guard.canProtect(ally, attacker));
    guards.sort(Comparator.comparingDouble(ally::distanceToSqr));
    for (var guard : guards) {
      float diverted = Math.min(event.getAmount() * .35F, guard.getHealth() - 1);
      if (diverted <= 0) continue;
      INTERCEPTING.set(true);
      boolean accepted;
      float healthBefore = guard.getHealth(), absorptionBefore = guard.getAbsorptionAmount();
      var shieldBefore = guard.getOffhandItem().copy();
      try {
        accepted = guard.hurt(event.getSource(), diverted);
      } finally {
        INTERCEPTING.remove();
      }
      boolean paid =
          guard.getHealth() < healthBefore
              || guard.getAbsorptionAmount() < absorptionBefore
              || (!shieldBefore.isEmpty()
                  && (guard.getOffhandItem().isEmpty()
                      || guard.getOffhandItem().getDamageValue() > shieldBefore.getDamageValue()));
      if ((accepted && paid || guard.blockedInterceptedHit())
          && guard.isAlive()
          && guard.isWorking()) {
        guard.recordInterception(diverted);
        io.github.meistermods.slimic.world.AssemblySites.contribute(guard);
        event.setAmount(Math.max(0, event.getAmount() - diverted));
        return;
      }
    }
  }
}
