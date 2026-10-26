package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.config.SlimicConfig;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.world.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;

/** A broken assembly order given a single body. Its fragments borrow conserved encounter mass. */
public final class AimlessColony extends Monster {
  public enum Pattern {
    IDLE(0),
    DASH(20),
    SLAM(24),
    PULL(24),
    SPLIT(20),
    RALLY(50),
    DASHING(12),
    RING(30),
    CROSS(32),
    SWEEP(32),
    ERUPTION(40),
    SWEEPING(10),
    RECOVERY(12);
    public final int warning;

    Pattern(int warning) {
      this.warning = warning;
    }
  }

  public static final double DASH_SPEED = .65;
  public static final double SWEEP_RADIUS = 8;
  public static final double SWEEP_HALF_ANGLE = Math.toRadians(70);
  public static final double SWEEP_VERTICAL_REACH = 2.5;
  public static final double ERUPTION_RADIUS = 1.6;
  public static final double ERUPTION_SPACING = 3.5;
  public static final double ERUPTION_VERTICAL_REACH = 2.5;

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
  private static final EntityDataAccessor<org.joml.Vector3f> DASH_DIRECTION =
      SynchedEntityData.defineId(AimlessColony.class, EntityDataSerializers.VECTOR3);
  private static final EntityDataAccessor<Integer> SWEEP_SIGN =
      SynchedEntityData.defineId(AimlessColony.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<Integer> RECOVERY_MOTION =
      SynchedEntityData.defineId(AimlessColony.class, EntityDataSerializers.INT);
  private final ServerBossEvent bossBar =
      new ServerBossEvent(
          getDisplayName(), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
  private UUID siteId, encounter;
  private int actionDelay = 60, splitDelay = 60, rallyDelay = 300, exposureDelay, exposedTicks;
  private int attackCycle;
  private Pattern followUp = Pattern.IDLE;
  private Pattern lastAttack = Pattern.IDLE;
  private int movementTicks, flankDirection = 1;
  private boolean returningHome;
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
    entityData.define(SWEEP_SIGN, 1);
    entityData.define(RECOVERY_MOTION, 0);
    entityData.define(PATTERN, 0);
    entityData.define(TIMER, 0);
    entityData.define(COHESION, 100);
    entityData.define(EXPOSED, false);
    entityData.define(AIM, BlockPos.ZERO);
    entityData.define(DASH_DIRECTION, new org.joml.Vector3f(0, 0, 1));
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

  /**
   * Movement and its warning use the same frozen direction, even after a shove or target change.
   */
  public Vec3 dashDirection() {
    var direction = entityData.get(DASH_DIRECTION);
    return new Vec3(direction.x(), 0, direction.z()).normalize();
  }

  public double dashHalfWidth() {
    return getBbWidth() * .5;
  }

  public double dashRemainingDistance() {
    return DASH_SPEED
        * (pattern() == Pattern.DASH
            ? Pattern.DASHING.warning
            : pattern() == Pattern.DASHING ? patternTicks() : 0);
  }

  public int sweepSign() {
    return entityData.get(SWEEP_SIGN) < 0 ? -1 : 1;
  }

  public Pattern recoveryMotion() {
    return Pattern.values()[
        Math.floorMod(entityData.get(RECOVERY_MOTION), Pattern.values().length)];
  }

  public boolean facingAttack() {
    return switch (pattern()) {
      case DASH, DASHING, SWEEP, SWEEPING, RECOVERY -> true;
      default -> false;
    };
  }

  public double sweepAngle(double progress) {
    return sweepSign() * SWEEP_HALF_ANGLE * (2 * Mth.clamp(progress, 0, 1) - 1);
  }

  public double sweepProgress(float partial) {
    return pattern() == Pattern.SWEEPING
        ? Mth.clamp(1 - (patternTicks() - partial) / Pattern.SWEEPING.warning, 0, 1)
        : 0;
  }

  private boolean inDashFootprint(Vec3 point, Vec3 start, Vec3 end) {
    Vec3 offset = point.subtract(start), direction = dashDirection();
    double forward = offset.x * direction.x + offset.z * direction.z;
    double side = offset.x * direction.z - offset.z * direction.x;
    double travel = end.subtract(start).dot(direction);
    return forward >= -dashHalfWidth()
        && forward <= travel + dashHalfWidth()
        && Math.abs(side) <= dashHalfWidth();
  }

  /** New attack warnings and damage share a fixed origin throughout the attack. */
  public Vec3 attackOrigin() {
    return Vec3.atBottomCenterOf(aim());
  }

  public boolean inSweepFootprint(Vec3 point) {
    Vec3 offset = point.subtract(attackOrigin());
    double distance = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
    return Math.abs(offset.y) <= SWEEP_VERTICAL_REACH
        && distance <= SWEEP_RADIUS
        && offset.dot(dashDirection()) >= distance * Math.cos(SWEEP_HALF_ANGLE);
  }

  public List<Vec3> eruptionCenters() {
    Vec3 origin = attackOrigin(), forward = dashDirection().scale(ERUPTION_SPACING);
    Vec3 side = new Vec3(-forward.z, 0, forward.x);
    return List.of(
        origin,
        origin.add(forward),
        origin.subtract(forward),
        origin.add(side),
        origin.subtract(side));
  }

  public boolean inEruptionFootprint(Vec3 point) {
    if (Math.abs(point.y - attackOrigin().y) > ERUPTION_VERTICAL_REACH) return false;
    for (Vec3 center : eruptionCenters()) {
      double dx = point.x - center.x, dz = point.z - center.z;
      if (dx * dx + dz * dz <= ERUPTION_RADIUS * ERUPTION_RADIUS) return true;
    }
    return false;
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

  private boolean canUseAttackTarget() {
    LivingEntity target = getTarget();
    return target != null
        && target.level() == level()
        && validTarget(target)
        && distanceToSqr(target) <= 36 * 36;
  }

  private void begin(Pattern pattern) {
    entityData.set(PATTERN, pattern.ordinal());
    entityData.set(TIMER, pattern.warning);
    navigation.stop();
    hit.clear();
    if (pattern != Pattern.IDLE && pattern != Pattern.DASHING) lastAttack = pattern;
    if (getTarget() != null) entityData.set(AIM, getTarget().blockPosition());
    if (pattern == Pattern.DASH
        || pattern == Pattern.DASHING
        || pattern == Pattern.SWEEP
        || pattern == Pattern.ERUPTION) {
      Vec3 direction = Vec3.atCenterOf(aim()).subtract(position()).multiply(1, 0, 1);
      direction = direction.lengthSqr() < .01 ? new Vec3(0, 0, 1) : direction.normalize();
      entityData.set(DASH_DIRECTION, direction.toVector3f());
    }
    if (pattern == Pattern.SWEEP) {
      entityData.set(AIM, blockPosition());
      entityData.set(SWEEP_SIGN, random.nextBoolean() ? 1 : -1);
    }
    if (facingAttack()) setDeltaMovement(0, getDeltaMovement().y, 0);
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
    moveBetweenAttacks(site);
    if (returningHome || !canUseAttackTarget()) {
      setTarget(null);
      return;
    }
    if (--actionDelay > 0) return;
    if (site.cohesion.count() > 0
        && rallyDelay == 0
        && lastAttack != Pattern.RALLY
        && (phase() >= 2 || getHealth() < getMaxHealth() * .9)) {
      begin(Pattern.RALLY);
      rallyDelay = 300;
    } else if (splitDelay == 0
        && lastAttack != Pattern.SPLIT
        && site.cohesion.free() >= 12
        && site.cohesion.count() < SlimicConfig.COLONY_FRAGMENTS.get()) {
      begin(Pattern.SPLIT);
      splitDelay = 300;
    } else begin(chooseAttack());
    followUp =
        phase() >= 2 && pattern() == Pattern.DASH
            ? Pattern.RING
            : phase() == 3 && pattern() == Pattern.PULL ? Pattern.CROSS : Pattern.IDLE;
    attackCycle = (attackCycle + 1) % 6;
    actionDelay = phase() == 3 ? 55 : 85;
  }

  private Pattern chooseAttack() {
    double distance = distanceToSqr(getTarget());
    List<Pattern> choices = new ArrayList<>();
    if (distance < 49) choices.addAll(List.of(Pattern.SLAM, Pattern.SWEEP, Pattern.SWEEP));
    else if (distance < 100) choices.addAll(List.of(Pattern.DASH, Pattern.SWEEP, Pattern.ERUPTION));
    else choices.addAll(List.of(Pattern.DASH, Pattern.ERUPTION, Pattern.ERUPTION));
    if (phase() >= 2) {
      choices.add(Pattern.ERUPTION);
      if (distance < 100) choices.add(Pattern.RING);
    }
    if (phase() == 3) {
      choices.add(Pattern.CROSS);
      if (distance < 81) choices.add(Pattern.PULL);
    }
    choices.removeIf(pattern -> pattern == lastAttack);
    return choices.get(random.nextInt(choices.size()));
  }

  /** Short, bounded steps let the body approach, circle, or give space without acquiring chunks. */
  private void moveBetweenAttacks(AssemblySites.Site site) {
    navigation.stop();
    Vec3 home = Vec3.atBottomCenterOf(site.anchor);
    double homeDistance = position().distanceToSqr(home);
    returningHome = homeDistance > (returningHome ? 8 * 8 : 38 * 38);
    if (!returningHome && !canUseAttackTarget()) {
      setDeltaMovement(0, getDeltaMovement().y, 0);
      return;
    }
    Vec3 toward =
        (returningHome ? home : getTarget().position()).subtract(position()).multiply(1, 0, 1);
    if (toward.lengthSqr() < .01) {
      setDeltaMovement(0, getDeltaMovement().y, 0);
      return;
    }
    if (--movementTicks <= 0) {
      movementTicks = 24 + random.nextInt(25);
      flankDirection = random.nextBoolean() ? 1 : -1;
    }
    double distance = toward.length();
    Vec3 forward = toward.normalize();
    Vec3 side = new Vec3(-forward.z, 0, forward.x).scale(flankDirection);
    double approach = returningHome || distance > 10 ? 1 : distance < 4 ? -.55 : .2;
    Vec3 desired = forward.scale(approach).add(side.scale(returningHome ? 0 : .65)).normalize();
    double speed = getAttributeValue(Attributes.MOVEMENT_SPEED) * .8;
    for (double angle : new double[] {0, Math.PI / 4, -Math.PI / 4, Math.PI / 2, -Math.PI / 2}) {
      Vec3 step = desired.yRot((float) angle).scale(speed);
      Vec3 next = position().add(step);
      if (next.distanceToSqr(home) > 44 * 44
          && next.distanceToSqr(home) >= position().distanceToSqr(home)) continue;
      if (safeStep(next)) {
        setDeltaMovement(step.x, getDeltaMovement().y, step.z);
        if (canUseAttackTarget()) getLookControl().setLookAt(getTarget(), 25, 25);
        return;
      }
    }
    setDeltaMovement(0, getDeltaMovement().y, 0);
  }

  private void patternStep(ServerLevel server, AssemblySites.Site site) {
    Pattern pattern = pattern();
    int ticks = patternTicks();
    entityData.set(TIMER, Math.max(0, ticks - 1));
    navigation.stop();
    if (pattern == Pattern.DASHING) {
      Vec3 start = position(), step = dashDirection().scale(DASH_SPEED);
      Vec3 next = start.add(step);
      if (safeStep(next)
          && (site == null || next.distanceToSqr(Vec3.atBottomCenterOf(site.anchor)) <= 44 * 44)) {
        move(MoverType.SELF, step);
      } else entityData.set(TIMER, 0);
      // Damage follows actual collision-clipped travel, including the final step. Normal travel
      // still handles gravity, but must not apply the horizontal charge a second time.
      setDeltaMovement(0, getDeltaMovement().y, 0);
      dashStrike(start, position());
    } else if (pattern == Pattern.SWEEPING) {
      setDeltaMovement(0, getDeltaMovement().y, 0);
      sweepStrike(
          server,
          1 - ticks / (double) Pattern.SWEEPING.warning,
          1 - patternTicks() / (double) Pattern.SWEEPING.warning);
    } else if (pattern == Pattern.RECOVERY) {
      setDeltaMovement(0, getDeltaMovement().y, 0);
    } else {
      setDeltaMovement(0, getDeltaMovement().y, 0);
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
        entityData.set(TIMER, Pattern.DASHING.warning);
        playSound(SoundEvents.SLIME_JUMP, 1.5F, .65F);
        return;
      }
      case SLAM -> strike(5.5, 1);
      case PULL -> strike(4, .8);
      case RING -> shapedStrike(false);
      case CROSS -> shapedStrike(true);
      case SWEEP -> {
        entityData.set(PATTERN, Pattern.SWEEPING.ordinal());
        entityData.set(TIMER, Pattern.SWEEPING.warning);
        playSound(SoundEvents.SLIME_ATTACK, 1.7F, .8F);
        return;
      }
      case DASHING, SWEEPING -> {
        entityData.set(RECOVERY_MOTION, pattern.ordinal());
        entityData.set(PATTERN, Pattern.RECOVERY.ordinal());
        entityData.set(TIMER, Pattern.RECOVERY.warning);
        playSound(SoundEvents.SLIME_SQUISH, 1.2F, .65F);
        return;
      }
      case ERUPTION -> fixedStrike(server);
      case SPLIT -> split(server, site);
      case RALLY -> absorb(server, site);
      default -> {}
    }
    if (followUp != Pattern.IDLE
        && canUseAttackTarget()
        && !returningHome
        && (site == null
            || position().distanceToSqr(Vec3.atBottomCenterOf(site.anchor)) <= 38 * 38)) {
      Pattern next = followUp;
      followUp = Pattern.IDLE;
      begin(next);
      return;
    }
    followUp = Pattern.IDLE;
    entityData.set(PATTERN, Pattern.IDLE.ordinal());
    entityData.set(TIMER, 0);
  }

  private boolean safeStep(Vec3 position) {
    return safeStep(this, position);
  }

  private boolean safeStep(Entity mover, Vec3 position) {
    if (!(level() instanceof ServerLevel server) || mover.level() != level()) return false;
    AABB box = mover.getBoundingBox().move(position.subtract(mover.position())).deflate(1.0E-5);
    if (box.minY < level().getMinBuildHeight()
        || box.maxY > level().getMaxBuildHeight()
        || !level().getWorldBorder().isWithinBounds(box)) return false;
    LoadedBlockView blocks = new LoadedBlockView(server);
    int minX = Mth.floor(box.minX), maxX = Mth.floor(box.maxX);
    int minZ = Mth.floor(box.minZ), maxZ = Mth.floor(box.maxZ);
    int groundY = Mth.floor(box.minY - .125);
    // Include adjacent blocks because fences and other collision shapes can extend beyond a cell.
    for (int cx = (minX - 1) >> 4; cx <= (maxX + 1) >> 4; cx++)
      for (int cz = (minZ - 1) >> 4; cz <= (maxZ + 1) >> 4; cz++)
        if (blocks.chunk(cx, cz) == null) return false;
    for (int x = minX; x <= maxX; x++)
      for (int z = minZ; z <= maxZ; z++) {
        BlockPos floor = new BlockPos(x, groundY, z);
        if (!blocks.getFluidState(floor).isEmpty()
            || !blocks.getBlockState(floor).isFaceSturdy(blocks, floor, Direction.UP)) return false;
      }
    var occupied = Shapes.create(box);
    var context = CollisionContext.of(mover);
    for (BlockPos pos :
        BlockPos.betweenClosed(
            minX - 1, groundY, minZ - 1, maxX + 1, Mth.floor(box.maxY) + 1, maxZ + 1)) {
      if (!blocks.getFluidState(pos).isEmpty() && box.intersects(new AABB(pos))) return false;
      var collision = blocks.getBlockState(pos).getCollisionShape(blocks, pos, context);
      if (!collision.isEmpty()
          && Shapes.joinIsNotEmpty(
              occupied, collision.move(pos.getX(), pos.getY(), pos.getZ()), BooleanOp.AND))
        return false;
    }
    for (var collision : level().getEntityCollisions(mover, box))
      if (Shapes.joinIsNotEmpty(occupied, collision, BooleanOp.AND)) return false;
    return true;
  }

  @Override
  public boolean hasLineOfSight(Entity target) {
    return target.level() == level()
        && distanceToSqr(target) < 128 * 128
        && LoadedBlockView.clip(
                    level(),
                    new ClipContext(
                        getEyePosition(),
                        target.getEyePosition(),
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        this))
                .getType()
            == HitResult.Type.MISS;
  }

  private void pull() {
    for (LivingEntity target :
        level()
            .getEntitiesOfClass(
                LivingEntity.class, getBoundingBox().inflate(7, 2, 7), this::validTarget)) {
      if (!hasLineOfSight(target)) continue;
      Vec3 direction =
          position().subtract(target.position()).multiply(1, 0, 1).normalize().scale(.07);
      if (safeStep(target, target.position().add(direction.scale(5)))) {
        target.push(direction.x, 0, direction.z);
        if (target instanceof FriendlySlime slime) slime.getNavigation().stop();
      }
    }
  }

  private void strike(double range, double scale) {
    for (LivingEntity target :
        level()
            .getEntitiesOfClass(
                LivingEntity.class, getBoundingBox().inflate(range, 1, range), this::validTarget)) {
      if (target.distanceToSqr(this) > range * range
          || hit.contains(target.getUUID())
          || !hasLineOfSight(target)) continue;
      hit.add(target.getUUID());
      if (target.hurt(
          damageSources().mobAttack(this),
          (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * scale))) {
        Vec3 direction =
            target.position().subtract(position()).multiply(1, 0, 1).normalize().scale(.08);
        if (safeStep(target, target.position().add(direction.scale(4))))
          target.push(direction.x, .03, direction.z);
      }
    }
  }

  private void dashStrike(Vec3 start, Vec3 end) {
    double diagonal = dashHalfWidth() * (Math.sqrt(2) - 1);
    AABB swept =
        getBoundingBox()
            .minmax(getBoundingBox().move(start.subtract(end)))
            .inflate(diagonal, 0, diagonal);
    for (LivingEntity target :
        level().getEntitiesOfClass(LivingEntity.class, swept, this::validTarget)) {
      if (!inDashFootprint(target.position(), start, end)
          || hit.contains(target.getUUID())
          || !hasLineOfSight(target)) continue;
      hit.add(target.getUUID());
      if (target.hurt(
          damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE))) {
        Vec3 push = dashDirection().scale(.08);
        if (safeStep(target, target.position().add(push.scale(4))))
          target.push(push.x, .03, push.z);
      }
    }
  }

  private void sweepStrike(ServerLevel server, double before, double after) {
    Vec3 origin = attackOrigin(), direction = dashDirection();
    double low = SWEEP_HALF_ANGLE * (2 * before - 1), high = SWEEP_HALF_ANGLE * (2 * after - 1);
    for (LivingEntity target :
        server.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(origin, origin).inflate(SWEEP_RADIUS, SWEEP_VERTICAL_REACH, SWEEP_RADIUS),
            this::validTarget)) {
      Vec3 offset = target.position().subtract(origin);
      double angle =
          sweepSign()
              * Math.atan2(
                  offset.x * direction.z - offset.z * direction.x,
                  offset.x * direction.x + offset.z * direction.z);
      if (!inSweepFootprint(target.position())
          || angle < low - 1E-6
          || angle > high + 1E-6
          || hit.contains(target.getUUID())
          || !hasLineOfSight(target)) continue;
      hit.add(target.getUUID());
      target.hurt(
          damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
    }
    Vec3 ray = direction.yRot((float) sweepAngle(after));
    for (double radius = 2; radius <= SWEEP_RADIUS; radius += 1.5) {
      Vec3 point = origin.add(ray.scale(radius));
      if (server.hasChunkAt(BlockPos.containing(point)))
        server.sendParticles(
            ParticleTypes.ITEM_SLIME, point.x, point.y + .35, point.z, 2, .15, .2, .15, .03);
    }
  }

  private void fixedStrike(ServerLevel server) {
    Vec3 origin = attackOrigin();
    for (LivingEntity target :
        server.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(origin, origin)
                .inflate(
                    ERUPTION_SPACING + ERUPTION_RADIUS,
                    ERUPTION_VERTICAL_REACH,
                    ERUPTION_SPACING + ERUPTION_RADIUS),
            this::validTarget)) {
      if (!inEruptionFootprint(target.position())
          || hit.contains(target.getUUID())
          || !hasLineOfSight(target)) continue;
      hit.add(target.getUUID());
      target.hurt(
          damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
    }
    for (Vec3 point : eruptionCenters()) {
      if (!server.hasChunkAt(BlockPos.containing(point))) continue;
      server.sendParticles(
          ParticleTypes.ITEM_SLIME,
          point.x,
          point.y + .2,
          point.z,
          18,
          ERUPTION_RADIUS * .5,
          .8,
          ERUPTION_RADIUS * .5,
          .08);
    }
    playSound(SoundEvents.SLIME_ATTACK, 1.7F, .6F);
  }

  /** Annular waves leave the center safe; crossed lanes leave all four diagonal sectors open. */
  private void shapedStrike(boolean cross) {
    Vec3 center = cross ? Vec3.atBottomCenterOf(aim()) : position();
    for (LivingEntity target :
        level()
            .getEntitiesOfClass(
                LivingEntity.class,
                new net.minecraft.world.phys.AABB(center, center).inflate(8, 2, 8),
                this::validTarget)) {
      double dx = Math.abs(target.getX() - center.x), dz = Math.abs(target.getZ() - center.z);
      double distance = dx * dx + dz * dz;
      boolean inside =
          cross
              ? dx <= 8 && dz <= 8 && Math.min(dx, dz) <= 1.25
              : distance >= 3.5 * 3.5 && distance <= 64;
      if (!inside || hit.contains(target.getUUID()) || !hasLineOfSight(target)) continue;
      hit.add(target.getUUID());
      target.hurt(
          damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
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
      if (!safeStep(fragment, fragment.position())) {
        fragment.discard();
        AssemblySites.get(level).changeMass(level, site, pool -> pool.cancel(id, sequence[0]));
        continue;
      }
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
              && safeStep(fragment, fragment.position())
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
    tag.putInt("AttackCycle", attackCycle);
    tag.putString("LastAttack", lastAttack.name());
    tag.putBoolean("ReturningHome", returningHome);
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
    attackCycle = Math.floorMod(tag.getInt("AttackCycle"), 6);
    followUp = Pattern.IDLE;
    try {
      lastAttack = Pattern.valueOf(tag.getString("LastAttack"));
    } catch (IllegalArgumentException ignored) {
      lastAttack = Pattern.IDLE;
    }
    returningHome = tag.getBoolean("ReturningHome");
    movementTicks = 0;
    hit.clear();
    navigation.stop();
    setDeltaMovement(Vec3.ZERO);
    actionDelay = Math.max(20, tag.getInt("ActionDelay"));
    splitDelay = Math.max(20, tag.getInt("SplitDelay"));
    rallyDelay = Math.max(20, tag.getInt("RallyDelay"));
    exposureDelay = Math.max(0, tag.getInt("ExposureDelay"));
    exposedTicks = Math.max(0, Math.min(100, tag.getInt("ExposedTicks")));
    entityData.set(SWEEP_SIGN, 1);
    entityData.set(RECOVERY_MOTION, 0);
    entityData.set(PATTERN, 0);
    entityData.set(TIMER, 0);
    entityData.set(AIM, blockPosition());
    entityData.set(DASH_DIRECTION, new org.joml.Vector3f(0, 0, 1));
  }
}
