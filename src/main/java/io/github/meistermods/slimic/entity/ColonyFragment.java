package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.world.*;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/** A borrowed part of one encounter, never an independent source of loot or mass. */
public final class ColonyFragment extends Monster {
  public enum Role {
    ATTACK,
    COHESION,
    RETURNING
  }

  private UUID boss, siteId, encounter;
  private int sequence, age, attackDelay;

  public ColonyFragment(EntityType<? extends ColonyFragment> type, Level level) {
    super(type, level);
    setPersistenceRequired();
    setMaxUpStep(1);
    xpReward = 0;
  }

  public static AttributeSupplier.Builder createAttributes() {
    return Monster.createMonsterAttributes()
        .add(Attributes.MAX_HEALTH, 16)
        .add(Attributes.ATTACK_DAMAGE, 3)
        .add(Attributes.MOVEMENT_SPEED, .23)
        .add(Attributes.FOLLOW_RANGE, 20);
  }

  @Override
  protected void registerGoals() {
    goalSelector.addGoal(0, new FloatGoal(this));
  }

  public Role role() {
    return getType() == ModEntities.ATTACK_COLONY_FRAGMENT.get()
        ? Role.ATTACK
        : getType() == ModEntities.COHESION_COLONY_FRAGMENT.get() ? Role.COHESION : Role.RETURNING;
  }

  public void bind(AimlessColony body, int sequence) {
    boss = body.getUUID();
    siteId = body.siteId();
    encounter = body.encounterId();
    this.sequence = sequence;
  }

  public boolean belongsTo(AimlessColony body) {
    return body.getUUID().equals(boss) && java.util.Objects.equals(encounter, body.encounterId());
  }

  @Override
  public boolean removeWhenFarAway(double distance) {
    return false;
  }

  @Override
  protected void dropAllDeathLoot(DamageSource source) {}

  @Override
  public SpawnGroupData finalizeSpawn(
      net.minecraft.world.level.ServerLevelAccessor world,
      net.minecraft.world.DifficultyInstance difficulty,
      MobSpawnType reason,
      SpawnGroupData group,
      CompoundTag tag) {
    SpawnGroupData result = super.finalizeSpawn(world, difficulty, reason, group, tag);
    if (siteId == null && (reason == MobSpawnType.SPAWN_EGG || reason == MobSpawnType.COMMAND)) {
      var level = world.getLevel();
      var bodies =
          level.getEntitiesOfClass(
              AimlessColony.class, getBoundingBox().inflate(32), Entity::isAlive);
      bodies.sort(java.util.Comparator.comparingDouble(this::distanceToSqr));
      for (AimlessColony body : bodies) {
        var site = body.site();
        if (site == null || !site.active(body.encounterId(), body.getUUID())) continue;
        int[] allocated = {-1};
        if (AssemblySites.get(level)
                .changeMass(
                    level,
                    site,
                    pool ->
                        allocated[0] =
                            pool.reserve(
                                getUUID(),
                                io.github.meistermods.slimic.config.SlimicConfig.COLONY_FRAGMENTS
                                    .get()))
            && allocated[0] >= 0) {
          bind(body, allocated[0]);
          break;
        }
      }
    }
    return result;
  }

  @Override
  protected void customServerAiStep() {
    super.customServerAiStep();
    if (!(level() instanceof ServerLevel level)) return;
    if (siteId == null || boss == null || encounter == null) {
      discard();
      return;
    }
    var site = AssemblySites.get(level).find(siteId);
    if (site == null || !site.active(encounter, boss) || !site.cohesion.owns(getUUID(), sequence)) {
      discard();
      return;
    }
    // An unloaded body is still alive in the ledger. Do not refund its mass or force its chunk.
    if (!(level.getEntity(boss) instanceof AimlessColony body) || !body.isAlive()) {
      navigation.stop();
      return;
    }
    age++;
    if (attackDelay > 0) attackDelay--;
    if (tickCount % 10 != 0) return;
    boolean returning =
        body.pattern() == AimlessColony.Pattern.RALLY || role() == Role.RETURNING && age >= 160;
    if (returning || role() == Role.COHESION) {
      boolean assisted =
          returning
              && level
                      .getEntitiesOfClass(
                          ColonyFragment.class,
                          getBoundingBox().inflate(5),
                          other ->
                              other != this
                                  && other.isAlive()
                                  && other.belongsTo(body)
                                  && other.role() == Role.COHESION)
                      .size()
                  > 0;
      navigation.moveTo(body, returning ? assisted ? 1.3 : 1.15 : .8);
      setTarget(null);
    } else {
      var target =
          level
              .getEntitiesOfClass(
                  LivingEntity.class, getBoundingBox().inflate(12), body::validTarget)
              .stream()
              .min(java.util.Comparator.comparingDouble(this::distanceToSqr))
              .orElse(null);
      setTarget(target);
      if (target != null) {
        navigation.moveTo(target, 1);
        if (distanceToSqr(target) < 2.5 && attackDelay == 0 && hasLineOfSight(target)) {
          doHurtTarget(target);
          attackDelay = 30;
        }
      }
    }
    if (distanceToSqr(body) > 28 * 28) navigation.moveTo(body, 1);
  }

  @Override
  public void die(DamageSource source) {
    super.die(source);
    if (dead) recordDeath();
  }

  private void recordDeath() {
    if (!(level() instanceof ServerLevel level) || siteId == null) return;
    var site = AssemblySites.get(level).find(siteId);
    if (site != null && site.active(encounter, boss) && site.cohesion.owns(getUUID(), sequence))
      AssemblySites.get(level).changeMass(level, site, pool -> pool.killed(getUUID(), sequence));
  }

  @Override
  protected void tickDeath() {
    recordDeath();
    super.tickDeath();
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    if (boss != null) tag.putUUID("ColonyBoss", boss);
    if (siteId != null) tag.putUUID("AssemblySite", siteId);
    if (encounter != null) tag.putUUID("AssemblyEncounter", encounter);
    tag.putInt("ColonySequence", sequence);
    tag.putInt("FragmentAge", age);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    boss = tag.hasUUID("ColonyBoss") ? tag.getUUID("ColonyBoss") : null;
    siteId = tag.hasUUID("AssemblySite") ? tag.getUUID("AssemblySite") : null;
    encounter = tag.hasUUID("AssemblyEncounter") ? tag.getUUID("AssemblyEncounter") : null;
    sequence = tag.getInt("ColonySequence");
    age = Math.max(0, tag.getInt("FragmentAge"));
  }
}
