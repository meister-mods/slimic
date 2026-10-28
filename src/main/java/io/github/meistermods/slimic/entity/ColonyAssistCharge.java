package io.github.meistermods.slimic.entity;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.phys.*;

/** A short, collision-checked joining leap; one colony schedules at most one critical at a time. */
final class ColonyAssistCharge {
  static final int WINDUP = 6, COOLDOWN = 100;
  private static final UUID CRITICAL = UUID.fromString("50d8f4d1-22ae-4b42-95a0-6d8943c398fe");
  private final BoundCombatSlime colony;
  private LivingEntity target;
  private Mob actor;
  private UUID owner;
  private Vec3 start, end;
  private int ticks, flightTicks, cooldown;
  private int retryAt;

  ColonyAssistCharge(BoundCombatSlime colony) {
    this.colony = colony;
  }

  static AttributeModifier criticalModifier() {
    return new AttributeModifier(
        CRITICAL, "Colony joining critical", .5, AttributeModifier.Operation.MULTIPLY_TOTAL);
  }

  void begin(int initiatingMember, LivingEntity victim) {
    if (actor != null
        || cooldown > 0
        || colony.tickCount < retryAt
        || colony.isRegrouping()
        || colony.isFleeing()
        || !colony.isWorking()
        || !colony.isAlive()
        || !colony.mayAttack(victim)
        || colony.getStamina() < 4
        || !colony.activeBody(colony.getLinkedBody())) return;
    Mob joining = initiatingMember == 0 ? colony.getLinkedBody() : colony;
    double distance = joining.distanceToSqr(victim);
    if (distance < 4
        || distance > 12 * 12
        || !CombatSlime.isSword(joining.getMainHandItem())
        || !joining.hasLineOfSight(victim)) return;
    retryAt = colony.tickCount + 10;
    var level = (ServerLevel) colony.level();
    Vec3 landing = victim.position();
    if (!CompanionTeleport.safe(level, joining, landing)) return;
    actor = joining;
    target = victim;
    owner = colony.getOwnerUUID();
    ticks = 0;
    start = actor.position();
    end = landing;
    flightTicks = Math.max(6, Math.min(16, (int) Math.ceil(Math.sqrt(distance) / .8)));
    // Check the complete announced arc before leaving the floor; never cross unloaded terrain.
    for (int i = 1; i <= flightTicks; i++)
      if (!safeStep(
          level, point((i - 1) / (double) flightTicks), point(i / (double) flightTicks))) {
        actor = null;
        target = null;
        return;
      }
    cooldown = COOLDOWN;
    actor.getNavigation().stop();
    actor.stopUsingItem();
    actor.setDeltaMovement(Vec3.ZERO);
    colony.setAssistState(initiatingMember == 0 ? 2 : 1, 0);
    actor.playSound(SoundEvents.SLIME_JUMP, .75F, .7F);
  }

  private Vec3 point(double progress) {
    return start.lerp(end, progress).add(0, Math.sin(Math.PI * progress) * .9, 0);
  }

  private boolean valid() {
    return actor != null
        && !actor.isRemoved()
        && actor.isAlive()
        && actor.level() == colony.level()
        && colony.isWorking()
        && !colony.isFleeing()
        && colony.isAlive()
        && !colony.isRemoved()
        && !colony.isPendingSpawn()
        && java.util.Objects.equals(owner, colony.getOwnerUUID())
        && target != null
        && colony.mayAttack(target)
        && colony.activeBody(colony.getLinkedBody())
        && CombatSlime.isSword(actor.getMainHandItem())
        && colony.getStamina() >= 1;
  }

  int cooldown() {
    return cooldown;
  }

  void loadCooldown(int value) {
    cooldown = Math.max(0, Math.min(COOLDOWN, value));
  }

  void cancelIfInvalid() {
    if (actor != null && !valid()) cancel();
  }

  void cancel() {
    if (actor != null) {
      actor.getNavigation().stop();
      actor.setDeltaMovement(actor.getDeltaMovement().multiply(0, 1, 0));
    }
    actor = null;
    target = null;
    colony.setAssistState(0, 0);
  }

  private boolean safeStep(ServerLevel level, Vec3 from, Vec3 next) {
    AABB box = actor.getBoundingBox().move(next.subtract(actor.position()));
    AABB swept = box.minmax(actor.getBoundingBox().move(from.subtract(actor.position())));
    if (box.minY < level.getMinBuildHeight() + 1
        || box.maxY >= level.getMaxBuildHeight()
        || !level.getWorldBorder().isWithinBounds(swept)) return false;
    for (var pos :
        BlockPos.betweenClosed(
            BlockPos.containing(swept.minX, swept.minY - 1, swept.minZ),
            BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ)))
      if (!level.hasChunkAt(pos)) return false;
    if (!level.noCollision(actor, swept) || level.containsAnyLiquid(swept)) return false;
    // An interrupted leap must still have a safe place to fall, even over a changing work site.
    for (int drop = 0; drop <= 3; drop++) {
      Vec3 fallback = new Vec3(next.x, Math.floor(next.y) - drop, next.z);
      if (CompanionTeleport.safe(level, actor, fallback)) return true;
    }
    return false;
  }

  void tick(ServerLevel level) {
    if (cooldown > 0) cooldown--;
    if (actor == null) return;
    if (!valid()) {
      cancel();
      return;
    }
    actor.getNavigation().stop();
    actor.stopUsingItem();
    actor.setSpeed(0);
    actor.setZza(0);
    actor.setXxa(0);
    float yaw = (float) (Math.toDegrees(Math.atan2(end.z - start.z, end.x - start.x)) - 90);
    actor.setYRot(yaw);
    actor.yBodyRot = actor.yHeadRot = yaw;
    ticks++;
    colony.setAssistState(actor == colony ? 1 : 2, ticks);
    if (ticks <= WINDUP) {
      actor.setDeltaMovement(actor.getDeltaMovement().multiply(0, 1, 0));
      return;
    }
    int flight = ticks - WINDUP;
    if (flight == 1 && !colony.spendStamina(3)) {
      cancel();
      return;
    }
    Vec3 next = point(Math.min(1, flight / (double) flightTicks));
    if (!safeStep(level, actor.position(), next)) {
      cancel();
      return;
    }
    Vec3 delta = next.subtract(actor.position());
    Mob moving = actor;
    moving.move(MoverType.SELF, delta);
    if (actor != moving || !valid()) {
      cancel();
      return;
    }
    actor.setDeltaMovement(Vec3.ZERO);
    actor.fallDistance = 0;
    actor.hasImpulse = true;
    if (ticks % 2 == 0)
      level.sendParticles(
          ParticleTypes.ITEM_SLIME,
          actor.getX(),
          actor.getY() + .4,
          actor.getZ(),
          3,
          .16,
          .1,
          .16,
          .02);
    if (actor.distanceToSqr(target) <= 2.25 && actor.hasLineOfSight(target)) {
      Mob striker = actor;
      LivingEntity victim = target;
      striker.swing(InteractionHand.MAIN_HAND);
      boolean hit = colony.assistStrike(victim, striker != colony);
      // Damage callbacks may capture the colony, change its order or remove either body.
      if (hit
          && !striker.isRemoved()
          && colony.isAlive()
          && !colony.isRemoved()
          && java.util.Objects.equals(owner, colony.getOwnerUUID())) {
        level.sendParticles(
            ParticleTypes.CRIT,
            victim.getX(),
            victim.getY() + victim.getBbHeight() * .5,
            victim.getZ(),
            18,
            .4,
            .35,
            .4,
            .15);
        striker.playSound(SoundEvents.PLAYER_ATTACK_CRIT, .9F, 1.15F);
      }
      cancel();
    } else if (flight >= flightTicks) cancel();
  }
}
