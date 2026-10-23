package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.config.SlimicConfig;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.world.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A broken assembly order given a single body. Its fragments borrow conserved encounter mass. */
public final class AimlessColony extends Monster {
  public enum Pattern {
    IDLE(0),
    DASH(20),
    SLAM(24),
    PULL(24),
    SPLIT(20),
    RALLY(50),
    DASHING(12);
    public final int warning;

    Pattern(int warning) {
      this.warning = warning;
    }
  }

  private static final EntityDataAccessor<Integer> PATTERN =
      SynchedEntityData.defineId(AimlessColony.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<Integer> TIMER =
      SynchedEntityData.defineId(AimlessColony.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<Integer> COHESION =
      SynchedEntityData.defineId(AimlessColony.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<Boolean> EXPOSED =
      SynchedEntityData.defineId(AimlessColony.class, EntityDataSerializers.BOOLEAN);
  private static final EntityDataAccessor<BlockPos> AIM =
      SynchedEntityData.defineId(AimlessColony.class, EntityDataSerializers.BLOCK_POS);
  private final ServerBossEvent bossBar =
      new ServerBossEvent(
          getDisplayName(), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
  private UUID siteId, encounter;
  private int actionDelay = 60, splitDelay = 60, rallyDelay = 300, exposureDelay, exposedTicks;
  private Vec3 dash = Vec3.ZERO;
  private final Set<UUID> hit = new HashSet<>();

  public AimlessColony(EntityType<? extends AimlessColony> type, Level level) {
    super(type, level);
    setPersistenceRequired();
    setMaxUpStep(1);
    xpReward = 0;
    getAttribute(Attributes.MAX_HEALTH).setBaseValue(SlimicConfig.COLONY_HEALTH.get());
    getAttribute(Attributes.ARMOR).setBaseValue(SlimicConfig.COLONY_ARMOR.get());
    getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(SlimicConfig.COLONY_DAMAGE.get());
    setHealth(getMaxHealth());
  }

  public static AttributeSupplier.Builder createAttributes() {
    return Monster.createMonsterAttributes()
        .add(Attributes.MAX_HEALTH, 320)
        .add(Attributes.ARMOR, 6)
        .add(Attributes.ATTACK_DAMAGE, 8)
        .add(Attributes.MOVEMENT_SPEED, .17)
        .add(Attributes.KNOCKBACK_RESISTANCE, .85)
        .add(Attributes.FOLLOW_RANGE, 48);
  }

  @Override
  protected void defineSynchedData() {
    super.defineSynchedData();
    entityData.define(PATTERN, 0);
    entityData.define(TIMER, 0);
    entityData.define(COHESION, 100);
    entityData.define(EXPOSED, false);
    entityData.define(AIM, BlockPos.ZERO);
  }

  @Override
  protected void registerGoals() {
    goalSelector.addGoal(0, new FloatGoal(this));
  }

  public Pattern pattern() {
    return Pattern.values()[Math.floorMod(entityData.get(PATTERN), Pattern.values().length)];
  }

  public int patternTicks() {
    return entityData.get(TIMER);
  }

  public int cohesion() {
    return entityData.get(COHESION);
  }

  public boolean exposed() {
    return entityData.get(EXPOSED);
  }

  public BlockPos aim() {
    return entityData.get(AIM);
  }

  public UUID siteId() {
    return siteId;
  }

  public UUID encounterId() {
    return encounter;
  }

  public int phase() {
    return getHealth() > getMaxHealth() * .7 ? 1 : getHealth() > getMaxHealth() * .35 ? 2 : 3;
  }

  public void bind(AssemblySites.Site site) {
    siteId = site.id;
    encounter = site.encounter;
  }

  public AssemblySites.Site site() {
    return level() instanceof ServerLevel server && siteId != null
        ? AssemblySites.get(server).find(siteId)
        : null;
  }

  public boolean validTarget(LivingEntity target) {
    if (!target.isAlive() || target.isSpectator() || isAlliedTo(target) || target.isAlliedTo(this))
      return false;
    if (target instanceof Player player) return !player.isCreative();
    return target instanceof CombatSlime slime && slime.isTame() && slime.getOwnerUUID() != null;
  }

  @Override
  public boolean canAttack(LivingEntity entity) {
    return validTarget(entity) && super.canAttack(entity);
  }

  @Override
  public boolean removeWhenFarAway(double distance) {
    return false;
  }

  @Override
  protected void dropAllDeathLoot(DamageSource source) {}

  @Override
  public void startSeenByPlayer(ServerPlayer player) {
    super.startSeenByPlayer(player);
    bossBar.addPlayer(player);
  }

  @Override
  public void stopSeenByPlayer(ServerPlayer player) {
    super.stopSeenByPlayer(player);
    bossBar.removePlayer(player);
  }

  private void begin(Pattern pattern) {
    entityData.set(PATTERN, pattern.ordinal());
    entityData.set(TIMER, pattern.warning);
    navigation.stop();
    hit.clear();
    if (getTarget() != null) entityData.set(AIM, getTarget().blockPosition());
    Vec3 direction = Vec3.atCenterOf(aim()).subtract(position()).multiply(1, 0, 1);
    dash = direction.lengthSqr() < .01 ? new Vec3(0, 0, 1) : direction.normalize();
    playSound(
        pattern == Pattern.RALLY ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.SLIME_SQUISH,
        1.5F,
        pattern == Pattern.RALLY ? .65F : .8F);
    if (level() instanceof ServerLevel server)
      for (ServerPlayer player : server.players())
        if (player.distanceToSqr(this) <= 48 * 48)
          player.displayClientMessage(
              Component.translatable(
                  "message.slimic.colony_" + pattern.name().toLowerCase(Locale.ROOT)),
              true);
  }

  @Override
  protected void customServerAiStep() {
    super.customServerAiStep();
    if (!(level() instanceof ServerLevel server)) return;
    // Spawn eggs create their own encounter; natural bodies are bound by the altar before joining.
    if (siteId == null) {
      var data = AssemblySites.get(server);
      var created = data.register(server, blockPosition());
      if (created == null || !data.awaken(server, created)) {
        discard();
        return;
      }
      created.state = AssemblySites.State.ACTIVE;
      created.boss = getUUID();
      bind(created);
      created.body = saveWithoutId(new CompoundTag());
      if (!data.flush(server)) {
        discard();
        return;
      }
      bind(created);
    }
    var site = site();
    if (site == null || !site.active(encounter, getUUID())) {
      discard();
      return;
    }
    bossBar.setName(getDisplayName());
    bossBar.setProgress(getHealth() / getMaxHealth());
    entityData.set(COHESION, site.cohesion.remaining());
    if (exposedTicks > 0) exposedTicks--;
    if (exposureDelay > 0) exposureDelay--;
    if (site.cohesion.remaining() <= 40 && exposureDelay == 0) {
      exposedTicks = 100;
      exposureDelay = 500;
    }
    entityData.set(EXPOSED, exposedTicks > 0);
    if (tickCount % 20 == 0) {
      site.body = saveWithoutId(new CompoundTag());
      AssemblySites.get(server).setDirty();
      AssemblySites.get(server).presence(server, site);
      if (site.absent >= 1200 && AssemblySites.get(server).reset(server, site)) {
        discard();
        return;
      }
      if (blockPosition().distSqr(site.anchor) > 44D * 44) {
        navigation.moveTo(site.anchor.getX() + .5, site.anchor.getY(), site.anchor.getZ() + .5, 1);
        setTarget(null);
      } else {
        LivingEntity nearest =
            server
                .getEntitiesOfClass(
                    LivingEntity.class, getBoundingBox().inflate(36), this::validTarget)
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
        setTarget(nearest);
      }
    }
    if (splitDelay > 0) splitDelay--;
    if (rallyDelay > 0) rallyDelay--;
    if (pattern() != Pattern.IDLE) {
      patternStep(server, site);
      return;
    }
    if (getTarget() == null) return;
    if (tickCount % 10 == 0) navigation.moveTo(getTarget(), .8);
    if (--actionDelay > 0) return;
    if (site.cohesion.count() > 0
        && rallyDelay == 0
        && (phase() >= 2 || getHealth() < getMaxHealth() * .9)) {
      begin(Pattern.RALLY);
      rallyDelay = 300;
    } else if (splitDelay == 0
        && site.cohesion.free() >= 12
        && site.cohesion.count() < SlimicConfig.COLONY_FRAGMENTS.get()) {
      begin(Pattern.SPLIT);
      splitDelay = 300;
    } else if (phase() == 3 && random.nextInt(3) == 0) begin(Pattern.PULL);
    else begin(distanceToSqr(getTarget()) < 49 ? Pattern.SLAM : Pattern.DASH);
    actionDelay = phase() == 3 ? 55 : 85;
  }

  private void patternStep(ServerLevel server, AssemblySites.Site site) {
    Pattern pattern = pattern();
    int ticks = patternTicks();
    entityData.set(TIMER, Math.max(0, ticks - 1));
    navigation.stop();
    if (pattern == Pattern.DASHING) {
      if (safeStep(position().add(dash.scale(.65))))
        setDeltaMovement(dash.x * .65, getDeltaMovement().y, dash.z * .65);
      else entityData.set(TIMER, 0);
      strike(1.2, 1, true);
    } else {
      setDeltaMovement(getDeltaMovement().multiply(.25, 1, .25));
      if (pattern == Pattern.PULL && ticks <= 8) pull();
      if (ticks % 4 == 0)
        server.sendParticles(
            pattern == Pattern.RALLY ? ParticleTypes.END_ROD : ParticleTypes.HAPPY_VILLAGER,
            getX(),
            getY() + .2,
            getZ(),
            4,
            2,
            .1,
            2,
            .01);
    }
    if (patternTicks() > 0) return;
    switch (pattern) {
      case DASH -> {
        entityData.set(PATTERN, Pattern.DASHING.ordinal());
        entityData.set(TIMER, 12);
        return;
      }
      case SLAM -> strike(5.5, 1, false);
      case PULL -> strike(4, .8, false);
      case SPLIT -> split(server, site);
      case RALLY -> absorb(server, site);
      default -> {}
    }
    entityData.set(PATTERN, Pattern.IDLE.ordinal());
    entityData.set(TIMER, 0);
  }

  private boolean safeStep(Vec3 position) {
    BlockPos feet = BlockPos.containing(position);
    return level().hasChunkAt(feet)
        && level().getFluidState(feet).isEmpty()
        && level().getFluidState(feet.below()).isEmpty()
        && level()
            .getBlockState(feet.below())
            .isFaceSturdy(level(), feet.below(), net.minecraft.core.Direction.UP);
  }

  private void pull() {
    for (LivingEntity target :
        level()
            .getEntitiesOfClass(
                LivingEntity.class, getBoundingBox().inflate(7, 2, 7), this::validTarget)) {
      Vec3 direction =
          position().subtract(target.position()).multiply(1, 0, 1).normalize().scale(.07);
      if (safeStep(target.position().add(direction.scale(5)))
          && level().noCollision(target, target.getBoundingBox().move(direction.scale(5)))) {
        target.push(direction.x, 0, direction.z);
        if (target instanceof FriendlySlime slime) slime.getNavigation().stop();
      }
    }
  }

  private void strike(double range, double scale, boolean contact) {
    for (LivingEntity target :
        level()
            .getEntitiesOfClass(
                LivingEntity.class, getBoundingBox().inflate(range, 1, range), this::validTarget)) {
      if (!contact && target.distanceToSqr(this) > range * range
          || hit.contains(target.getUUID())
          || !hasLineOfSight(target)) continue;
      hit.add(target.getUUID());
      if (target.hurt(
          damageSources().mobAttack(this),
          (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * scale))) {
        Vec3 direction =
            target.position().subtract(position()).multiply(1, 0, 1).normalize().scale(.08);
        if (safeStep(target.position().add(direction.scale(4))))
          target.push(direction.x, .03, direction.z);
      }
    }
  }

  private void split(ServerLevel level, AssemblySites.Site site) {
    int count = phase() == 1 ? 2 : phase() == 2 ? 3 : 4;
    for (int i = 0; i < count; i++) {
      int role = (tickCount + i) % 3;
      ColonyFragment fragment =
          (role == 0
                  ? ModEntities.ATTACK_COLONY_FRAGMENT
                  : role == 1
                      ? ModEntities.COHESION_COLONY_FRAGMENT
                      : ModEntities.RETURNING_COLONY_FRAGMENT)
              .get()
              .create(level);
      if (fragment == null) break;
      UUID id = fragment.getUUID();
      int[] sequence = {-1};
      if (!AssemblySites.get(level)
              .changeMass(
                  level,
                  site,
                  pool -> sequence[0] = pool.reserve(id, SlimicConfig.COLONY_FRAGMENTS.get()))
          || sequence[0] < 0) break;
      fragment.bind(this, sequence[0]);
      double angle = i * Math.PI * 2 / count;
      fragment.moveTo(
          getX() + Math.cos(angle) * 2.7,
          getY() + .1,
          getZ() + Math.sin(angle) * 2.7,
          getYRot(),
          0);
      net.minecraftforge.event.ForgeEventFactory.onFinalizeSpawn(
          fragment,
          level,
          level.getCurrentDifficultyAt(fragment.blockPosition()),
          MobSpawnType.MOB_SUMMONED,
          null,
          null);
      boolean admitted =
          !fragment.isSpawnCancelled()
              && fragment.isAlive()
              && !fragment.isRemoved()
              && id.equals(fragment.getUUID())
              && fragment.belongsTo(this)
              && isAlive()
              && site.active(encounter, getUUID())
              && level.noCollision(fragment)
              && level.addFreshEntity(fragment)
              && fragment.isAddedToWorld()
              && fragment.isAlive()
              && !fragment.isRemoved()
              && fragment.level() == level
              && id.equals(fragment.getUUID())
              && fragment.belongsTo(this)
              && isAlive()
              && site.active(encounter, getUUID());
      if (!admitted) {
        if (fragment.isAddedToWorld() && fragment.isRemoved()) fragment.revive();
        fragment.discard();
        AssemblySites.get(level).changeMass(level, site, pool -> pool.cancel(id, sequence[0]));
      }
    }
  }

  private void absorb(ServerLevel level, AssemblySites.Site site) {
    List<ColonyFragment> survivors =
        level.getEntitiesOfClass(
            ColonyFragment.class,
            getBoundingBox().inflate(7),
            fragment -> fragment.belongsTo(this) && fragment.isAlive());
    int[] heal = {0};
    if (AssemblySites.get(level)
        .changeMass(
            level,
            site,
            pool -> heal[0] = pool.absorb(survivors.stream().map(Entity::getUUID).toList()))) {
      survivors.forEach(Entity::discard);
      heal(heal[0]);
      site.body = saveWithoutId(new CompoundTag());
      AssemblySites.get(level).flush(level);
      if (survivors.isEmpty()) exposedTicks = Math.max(exposedTicks, 60);
    }
  }

  @Override
  public boolean hurt(DamageSource source, float amount) {
    float before = getHealth();
    var site = site();
    float multiplier = exposed() ? 1.2F : site != null && site.cohesion.remaining() >= 60 ? .9F : 1;
    boolean result = super.hurt(source, amount * multiplier);
    if (result && getHealth() < before && source.getEntity() != null)
      AssemblySites.contribute(source.getEntity());
    if (result
        && isAlive()
        && level() instanceof ServerLevel server
        && site != null
        && site.state == AssemblySites.State.ACTIVE) {
      site.body = saveWithoutId(new CompoundTag());
      AssemblySites.get(server).flush(server);
    }
    return result;
  }

  @Override
  public void die(DamageSource source) {
    super.die(source);
    if (!dead || !(level() instanceof ServerLevel server)) return;
    if (source.getEntity() != null) AssemblySites.contribute(source.getEntity());
    var site = site();
    if (site != null && site.active(encounter, getUUID())) {
      if (!AssemblySites.get(server).defeated(server, site)) {
        // A failed durable defeat must remain retryable, rather than losing everyone's rewards.
        dead = false;
        deathTime = 0;
        setPose(Pose.STANDING);
        setHealth(1);
        return;
      }
      server
          .getEntitiesOfClass(
              ColonyFragment.class, getBoundingBox().inflate(64), f -> f.belongsTo(this))
          .forEach(Entity::discard);
    }
    bossBar.removeAllPlayers();
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    if (siteId != null) tag.putUUID("AssemblySite", siteId);
    if (encounter != null) tag.putUUID("AssemblyEncounter", encounter);
    tag.putInt("ActionDelay", actionDelay);
    tag.putInt("SplitDelay", splitDelay);
    tag.putInt("RallyDelay", rallyDelay);
    tag.putInt("ExposureDelay", exposureDelay);
    tag.putInt("ExposedTicks", exposedTicks);
    // Reload begins a fresh telegraph; no partially persisted charge may silently hit twice.
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    siteId = tag.hasUUID("AssemblySite") ? tag.getUUID("AssemblySite") : null;
    encounter = tag.hasUUID("AssemblyEncounter") ? tag.getUUID("AssemblyEncounter") : null;
    actionDelay = Math.max(20, tag.getInt("ActionDelay"));
    splitDelay = Math.max(20, tag.getInt("SplitDelay"));
    rallyDelay = Math.max(20, tag.getInt("RallyDelay"));
    exposureDelay = Math.max(0, tag.getInt("ExposureDelay"));
    exposedTicks = Math.max(0, Math.min(100, tag.getInt("ExposedTicks")));
    entityData.set(PATTERN, 0);
    entityData.set(TIMER, 0);
  }
}
