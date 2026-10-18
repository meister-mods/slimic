package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Flying combat companion. Breath targets use the same permissions and filters as its sword. */
public final class DragonSlime extends CombatSlime {
  private int breathCooldown;

  public DragonSlime(EntityType<? extends DragonSlime> type, Level level) {
    super(type, level);
    moveControl =
        new FlyingMoveControl(this, 20, true) {
          @Override
          public void tick() {
            if (isOrderedToSit() && !isFleeing()) {
              operation = Operation.WAIT;
              setSpeed(0);
              setZza(0);
              setYya(0);
              setDeltaMovement(Vec3.ZERO);
            } else super.tick();
          }
        };
    setNoGravity(true);
  }

  @Override
  public void setWaiting(boolean waiting) {
    super.setWaiting(waiting);
    if (waiting) {
      setDeltaMovement(Vec3.ZERO);
      getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0);
    }
  }

  @Override
  protected PathNavigation createNavigation(Level level) {
    var nav = new LoadedFlyingNavigation(this, level);
    nav.setCanFloat(true);
    nav.setCanPassDoors(true);
    return nav;
  }

  @Override
  protected boolean mayNavigate() {
    return true;
  }

  @Override
  public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
    return false;
  }

  @Override
  public void travel(Vec3 input) {
    if (isEffectiveAi()) {
      moveRelative(.1F, input);
      move(MoverType.SELF, getDeltaMovement());
      setDeltaMovement(getDeltaMovement().scale(.8));
    } else super.travel(input);
    calculateEntityAnimation(false);
  }

  @Override
  public void tick() {
    super.tick();
    setNoGravity(true);
    getAttribute(Attributes.FLYING_SPEED)
        .setBaseValue(getAttributeValue(Attributes.MOVEMENT_SPEED));
    if (!(level() instanceof ServerLevel server)) return;
    if (breathCooldown > 0) breathCooldown--;
    var victim = getTarget();
    if (!isWorking()
        || isFleeing()
        || breathCooldown > 0
        || getStamina() < 4
        || victim == null
        || !mayAttack(victim)
        || !isSword(getMainHandItem())
        || distanceToSqr(victim) > Math.pow(SlimeStat.DETECTION_RANGE.value(this), 2)
        || !hasLineOfSight(victim)) return;
    Vec3 start = getEyePosition(),
        end = victim.getBoundingBox().getCenter(),
        direction = end.subtract(start);
    if (LoadedBlockView.clip(
                server,
                new net.minecraft.world.level.ClipContext(
                    start,
                    end,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    this))
            .getType()
        != net.minecraft.world.phys.HitResult.Type.MISS) return;
    // A direct flame ray cannot hit an allied bystander or ignite protected terrain.
    var fire = new SmallFireball(server, this, direction.x, direction.y, direction.z);
    float health = victim.getHealth();
    boolean hit =
        victim.hurt(
            damageSources().fireball(fire, this),
            (float) Math.max(2, SlimeStat.ATTACK.value(this)));
    if (hit && victim.getHealth() < health) {
      if (victim.isAlive()) victim.setSecondsOnFire(4);
      spendStamina(4);
      recordCompletedWork();
      for (int step = 0; step <= 16; step++) {
        var pos = start.add(direction.scale(step / 16.0));
        server.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 2, .06, .06, .06, .01);
      }
    }
    breathCooldown = Math.max(10, (int) Math.ceil(80 / SlimeStat.WORK_SPEED.value(this)));
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.DRAGON_SLIME.get());
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putInt("DragonBreathCooldown", breathCooldown);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    breathCooldown = Math.max(0, Math.min(800, tag.getInt("DragonBreathCooldown")));
    setNoGravity(true);
  }
}
