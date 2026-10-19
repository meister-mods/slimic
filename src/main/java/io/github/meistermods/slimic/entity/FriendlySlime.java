package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.config.SlimicConfig;
import io.github.meistermods.slimic.item.CreativeSlimeTunerItem;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.item.SlimeScannerItem;
import io.github.meistermods.slimic.item.SlimeTunerItem;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;

public class FriendlySlime extends TamableAnimal {
  private static final int MAX_POINT_COST = 4096;
  private static final EntityDataAccessor<CompoundTag> STAT_LEVELS =
      SynchedEntityData.defineId(FriendlySlime.class, EntityDataSerializers.COMPOUND_TAG);
  private static final EntityDataAccessor<CompoundTag> TUNED_STATS =
      SynchedEntityData.defineId(FriendlySlime.class, EntityDataSerializers.COMPOUND_TAG);
  private static final EntityDataAccessor<Integer> DYE_COLOR =
      SynchedEntityData.defineId(FriendlySlime.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<CompoundTag> OWNER_ACCESS =
      SynchedEntityData.defineId(FriendlySlime.class, EntityDataSerializers.COMPOUND_TAG);
  private static final EntityDataAccessor<java.util.Optional<java.util.UUID>> FOLLOWING_OWNER =
      SynchedEntityData.defineId(FriendlySlime.class, EntityDataSerializers.OPTIONAL_UUID);
  private String companionTag = "";
  private long lastAccessRevision = -1;
  private java.util.UUID lastAccessOwner;
  private int blockedWorkTicks;
  private long blockedEpisode;
  private int statPoints;
  private int activityProgress;
  private int earnedPoints;
  private float stamina = 100.0F;
  public float squish;
  public float oldSquish;
  private float targetSquish;
  private boolean wasOnGround;
  private ResourceLocation workDimension;
  private int followingActivityTicks;
  private Vec3 lastFollowingPosition;
  private LivingEntity danger;
  private int dangerTicks;
  private int defenseActivityCooldown;
  private int escapeActivityTicks;
  private boolean shieldHit;

  public FriendlySlime(EntityType<? extends FriendlySlime> type, Level level) {
    super(type, level);
    moveControl = new HopMoveControl(this);
    setOrderedToSit(true);
    setInSittingPose(true);
    setPersistenceRequired();
  }

  @Override
  protected PathNavigation createNavigation(Level level) {
    return new WorkNavigation(this, level);
  }

  @Override
  public boolean hasLineOfSight(Entity entity) {
    if ((!isWorking() && !(this instanceof GuardianAngelSlime))
        || !(level() instanceof ServerLevel)) return super.hasLineOfSight(entity);
    if (entity.level() != level()) return false;
    var from = getEyePosition();
    var to = entity.getEyePosition();
    return from.distanceToSqr(to) <= 128 * 128
        && LoadedBlockView.clip(
                    level(),
                    new ClipContext(
                        from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                .getType()
            == HitResult.Type.MISS;
  }

  public static AttributeSupplier.Builder createAttributes() {
    return createMobAttributes()
        .add(Attributes.MAX_HEALTH, 16.0D)
        .add(Attributes.ATTACK_DAMAGE, 2.0D)
        .add(Attributes.ARMOR, 0.0D)
        .add(Attributes.MOVEMENT_SPEED, 0.3D)
        .add(Attributes.FOLLOW_RANGE, 8.0D);
  }

  @Override
  protected void defineSynchedData() {
    super.defineSynchedData();
    entityData.define(STAT_LEVELS, new CompoundTag());
    entityData.define(TUNED_STATS, new CompoundTag());
    entityData.define(DYE_COLOR, -1);
    entityData.define(OWNER_ACCESS, new CompoundTag());
    entityData.define(FOLLOWING_OWNER, java.util.Optional.empty());
  }

  public void setFollowingOwner(Player player) {
    if (canManage(player)) entityData.set(FOLLOWING_OWNER, java.util.Optional.of(player.getUUID()));
  }

  @Override
  @Nullable
  public LivingEntity getOwner() {
    var chosen = entityData.get(FOLLOWING_OWNER);
    if (chosen.isPresent()) {
      var player = level().getPlayerByUUID(chosen.get());
      if (player != null && player.isAlive() && canManage(player) && !player.isSpectator())
        return player;
    }
    var primary = super.getOwner();
    if (primary != null && primary.isAlive() && !primary.isSpectator()) return primary;
    return level().players().stream()
        .filter(p -> p.isAlive() && canManage(p) && !p.isSpectator())
        .min(java.util.Comparator.comparingDouble(this::distanceToSqr))
        .orElse(null);
  }

  public final boolean canManage(Player player) {
    return player != null
        && (player.getUUID().equals(getOwnerUUID())
            || (level().isClientSide
                ? entityData.get(OWNER_ACCESS).contains(player.getUUID().toString())
                : CompanionOwnership.owns(level(), getOwnerUUID(), player.getUUID())));
  }

  @Override
  public boolean isOwnedBy(LivingEntity entity) {
    return entity instanceof Player player ? canManage(player) : super.isOwnedBy(entity);
  }

  @Override
  public boolean isAlliedTo(Entity other) {
    if (other instanceof Player player && canManage(player)) return true;
    var primary = super.getOwner();
    if (primary != null && primary.isAlliedTo(other)) return true;
    if (other instanceof net.minecraft.world.entity.TamableAnimal pet
        && CompanionOwnership.shares(level(), getOwnerUUID(), pet.getOwnerUUID())) return true;
    if (other instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse
        && CompanionOwnership.shares(level(), getOwnerUUID(), horse.getOwnerUUID())) return true;
    return super.isAlliedTo(other);
  }

  public final boolean canCooperateWith(FriendlySlime other) {
    return other != null
        && CompanionOwnership.cooperates(level(), getOwnerUUID(), other.getOwnerUUID());
  }

  public void refreshOwnerAccess() {
    if (!(level() instanceof ServerLevel level)) return;
    var registry = CompanionRegistry.get(level.getServer());
    if (lastAccessRevision == registry.relationshipRevision()
        && java.util.Objects.equals(lastAccessOwner, getOwnerUUID())) return;
    var access = new CompoundTag();
    if (getOwnerUUID() != null)
      for (var id : registry.coOwners(getOwnerUUID())) access.putBoolean(id.toString(), true);
    if (!access.equals(entityData.get(OWNER_ACCESS))) entityData.set(OWNER_ACCESS, access);
    lastAccessOwner = getOwnerUUID();
    lastAccessRevision = registry.relationshipRevision();
  }

  public final String getLocalCompanionTag() {
    return companionTag;
  }

  public final String getCompanionTag() {
    if (level() instanceof ServerLevel level) {
      var entry = CompanionRegistry.get(level.getServer()).find(getUUID());
      if (entry != null && java.util.Objects.equals(entry.owner(), getOwnerUUID()))
        return entry.tag();
    }
    return companionTag;
  }

  public static boolean isUtilityItem(ItemStack stack) {
    return stack.getItem() instanceof SlimeTunerItem
        || stack.getItem() instanceof SlimeScannerItem
        || stack.getItem() instanceof CreativeSlimeTunerItem
        || stack.getItem() instanceof DyeItem
        || stack.is(Items.SLIME_BALL)
        || stack.is(Items.WATER_BUCKET)
        || stack.is(Items.LEAD)
        || stack.is(Items.NAME_TAG);
  }

  protected final void refundStamina(float amount) {
    if (Float.isFinite(amount) && amount > 0) recoverStamina(amount);
  }

  public int getDyeColorId() {
    return entityData.get(DYE_COLOR);
  }

  public boolean dye(Player player, ItemStack stack) {
    if (!canManage(player) || !(stack.getItem() instanceof DyeItem dye) || !isAlive()) return false;
    if (!level().isClientSide) {
      entityData.set(DYE_COLOR, dye.getDyeColor().getId());
      if (!player.getAbilities().instabuild) stack.shrink(1);
    }
    return true;
  }

  public boolean hasTunedStat(SlimeStat stat) {
    return entityData.get(TUNED_STATS).contains(stat.key());
  }

  public double tunedStat(SlimeStat stat) {
    return entityData.get(TUNED_STATS).getDouble(stat.key());
  }

  public void setTunedStat(SlimeStat stat, double value) {
    CompoundTag tuned = entityData.get(TUNED_STATS).copy();
    tuned.putDouble(stat.key(), value);
    entityData.set(TUNED_STATS, tuned);
    stamina = Math.min(stamina, getMaxStamina());
    onStatsTuned();
  }

  protected void onStatsTuned() {}

  public void setTunedVitals(float health, float energy, int points) {
    setHealth(Mth.clamp(health, 0, getMaxHealth()));
    stamina = Mth.clamp(energy, 0, getMaxStamina());
    statPoints = Mth.clamp(points, 0, 32767);
  }

  public boolean isWorkBlocked() {
    return getWorkStatus().state() == WorkStatus.State.BLOCKED;
  }

  public final WorkStatus getWorkStatus() {
    if (isOrderedToSit()) return WorkStatus.of(WorkStatus.Order.WAIT, WorkStatus.State.IDLE);
    return isWorking()
        ? describeWork()
        : WorkStatus.of(WorkStatus.Order.FOLLOW, WorkStatus.State.IDLE);
  }

  protected WorkStatus describeWork() {
    return WorkStatus.of(WorkStatus.Order.FOLLOW, WorkStatus.State.IDLE);
  }

  public int getBlockedWorkTicks() {
    return blockedWorkTicks;
  }

  public long getBlockedEpisode() {
    return blockedEpisode;
  }

  public int getStatLevel(SlimeStat stat) {
    return entityData.get(STAT_LEVELS).getInt(stat.key());
  }

  public int getStatPoints() {
    return statPoints;
  }

  public int getActivityProgress() {
    return activityProgress;
  }

  public int getNextPointCost() {
    return (int) Math.min(MAX_POINT_COST, 32L + earnedPoints * 64L);
  }

  public float getStamina() {
    return stamina;
  }

  public float getMaxStamina() {
    return (float) SlimeStat.STAMINA.value(this);
  }

  public int getCapacity() {
    return (int) SlimeStat.CAPACITY.value(this);
  }

  public int detectionMultiplier() {
    return 1;
  }

  public int capacityMultiplier() {
    return 1;
  }

  public boolean isWorking() {
    return false;
  }

  /** Revalidate after callbacks that can change commands, ownership or the entity's lifetime. */
  protected final boolean canContinueWork(Player worker) {
    return isWorking()
        && isTame()
        && isAlive()
        && !isRemoved()
        && worker.level() == level()
        && canManage(worker);
  }

  protected void recoverStamina(float amount) {
    stamina = Math.min(getMaxStamina(), stamina + amount);
  }

  public final float getStaminaRecoveryPerSecond() {
    return getMaxStamina() * .02F;
  }

  public void onReleased() {
    workDimension = level().dimension().location();
    if (level() instanceof ServerLevel level) {
      CompanionRegistry.get(level.getServer()).observe(this);
      refreshOwnerAccess();
    }
  }

  protected final boolean hasChangedWorkDimension() {
    return workDimension != null && !workDimension.equals(level().dimension().location());
  }

  @Nullable
  @Override
  public Entity changeDimension(ServerLevel destination, ITeleporter teleporter) {
    var origin = level().dimension();
    Entity moved = super.changeDimension(destination, teleporter);
    if (moved instanceof FriendlySlime companion && !origin.equals(companion.level().dimension()))
      companion.onReleased();
    return moved;
  }

  public boolean upgradeStat(SlimeStat stat) {
    if (level().isClientSide || statPoints <= 0 || getStatLevel(stat) >= stat.maxLevel())
      return false;
    CompoundTag levels = entityData.get(STAT_LEVELS).copy();
    levels.putInt(stat.key(), getStatLevel(stat) + 1);
    entityData.set(STAT_LEVELS, levels);
    stat.applyUpgrade(this);
    statPoints--;
    return true;
  }

  protected void recordCompletedWork() {
    recordCompletedWork(1);
  }

  protected void recordCompletedWork(int units) {
    if (level().isClientSide) return;
    activityProgress += Mth.clamp(units, 0, 64);
    settleActivity();
  }

  private void settleActivity() {
    while (activityProgress >= getNextPointCost()) {
      activityProgress -= getNextPointCost();
      earnedPoints = Math.min(1_000_000, earnedPoints + 1);
      statPoints = Math.min(32767, statPoints + 1);
    }
  }

  protected boolean spendStamina(float amount) {
    if (stamina < amount) return false;
    stamina -= amount;
    return true;
  }

  public void setWaiting(boolean waiting) {
    setOrderedToSit(waiting);
    setInSittingPose(waiting);
    getNavigation().stop();
  }

  public boolean rename(Player player, String name) {
    if (!canManage(player) || !isAlive() || player.distanceToSqr(this) > 64.0D) return false;
    String clean = SharedConstants.filterText(name).strip();
    if (clean.codePointCount(0, clean.length()) > 32) {
      clean = clean.substring(0, clean.offsetByCodePoints(0, 32));
    }
    setCustomName(clean.isEmpty() ? null : Component.literal(clean));
    setCustomNameVisible(!clean.isEmpty());
    return true;
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putInt(SlimeData.VERSION_KEY, SlimeData.VERSION);
    tag.putInt("FollowingActivityTicks", followingActivityTicks);
    tag.putInt("EscapeActivityTicks", escapeActivityTicks);
    tag.putInt("DefenseActivityCooldown", defenseActivityCooldown);
    tag.put("SlimeStats", entityData.get(STAT_LEVELS).copy());
    tag.putInt("StatPoints", statPoints);
    tag.putInt("ActivityProgress", activityProgress);
    tag.putInt("EarnedPoints", earnedPoints);
    tag.putFloat("Stamina", stamina);
    tag.put("TunedStats", entityData.get(TUNED_STATS).copy());
    tag.putInt("SlimeDyeColor", getDyeColorId());
    entityData.get(FOLLOWING_OWNER).ifPresent(id -> tag.putUUID("FollowingOwner", id));
    tag.putString("CompanionTag", getCompanionTag());
    tag.putInt("BlockedWorkTicks", blockedWorkTicks);
    tag.putLong("BlockedEpisode", blockedEpisode);
    tag.putString(
        "WorkDimension",
        (workDimension == null ? level().dimension().location() : workDimension).toString());
  }

  @Override
  public void readAdditionalSaveData(CompoundTag saved) {
    CompoundTag tag = SlimeData.migrate(saved);
    super.readAdditionalSaveData(tag);
    try {
      companionTag = CompanionRegistry.normalizeTag(tag.getString("CompanionTag"));
    } catch (IllegalArgumentException invalid) {
      companionTag = "";
    }
    entityData.set(
        FOLLOWING_OWNER,
        tag.hasUUID("FollowingOwner")
            ? java.util.Optional.of(tag.getUUID("FollowingOwner"))
            : java.util.Optional.empty());
    lastAccessRevision = -1;
    if (!tag.contains("Sitting")) setWaiting(true);
    followingActivityTicks =
        Mth.clamp(
            tag.getInt("FollowingActivityTicks"),
            0,
            SlimicConfig.FOLLOW_ACTIVITY_INTERVAL.get() - 1);
    lastFollowingPosition = null;
    escapeActivityTicks =
        Mth.clamp(
            tag.getInt("EscapeActivityTicks"), 0, SlimicConfig.FOLLOW_ACTIVITY_INTERVAL.get() - 1);
    defenseActivityCooldown = Mth.clamp(tag.getInt("DefenseActivityCooldown"), 0, 20);
    danger = null;
    dangerTicks = 0;
    String savedDimension = tag.getString("WorkDimension");
    workDimension = savedDimension.isBlank() ? null : ResourceLocation.tryParse(savedDimension);
    if (workDimension == null || workDimension.getPath().isEmpty())
      workDimension = level().dimension().location();
    CompoundTag levels = new CompoundTag();
    for (SlimeStat stat : SlimeStat.values()) {
      levels.putInt(
          stat.key(),
          Mth.clamp(tag.getCompound("SlimeStats").getInt(stat.key()), 0, stat.maxLevel()));
    }
    entityData.set(STAT_LEVELS, levels);
    CompoundTag tuned = new CompoundTag();
    for (SlimeStat stat : SlimeStat.values()) {
      if (tag.getCompound("TunedStats").contains(stat.key())) {
        double value = tag.getCompound("TunedStats").getDouble(stat.key());
        if (stat.acceptsTunedValue(value)) tuned.putDouble(stat.key(), value);
      }
    }
    entityData.set(TUNED_STATS, tuned);
    entityData.set(
        DYE_COLOR,
        tag.contains("SlimeDyeColor") ? Mth.clamp(tag.getInt("SlimeDyeColor"), -1, 15) : -1);
    blockedWorkTicks = Mth.clamp(tag.getInt("BlockedWorkTicks"), 0, Integer.MAX_VALUE);
    blockedEpisode = Math.max(0, tag.getLong("BlockedEpisode"));
    statPoints = Mth.clamp(tag.getInt("StatPoints"), 0, 32767);
    earnedPoints = Mth.clamp(tag.getInt("EarnedPoints"), 0, 1_000_000);
    // Older saves can contain more activity than the reduced threshold. Pay the
    // earned points before retaining the remainder rather than truncating it.
    activityProgress = Mth.clamp(tag.getInt("ActivityProgress"), 0, MAX_POINT_COST - 1);
    settleActivity();
    stamina =
        tag.contains("Stamina") && Float.isFinite(tag.getFloat("Stamina"))
            ? Mth.clamp(tag.getFloat("Stamina"), 0.0F, getMaxStamina())
            : getMaxStamina();
  }

  @Override
  protected void registerGoals() {
    goalSelector.addGoal(-1, new FleeGoal());
    goalSelector.addGoal(
        0,
        new FloatGoal(this) {
          @Override
          public boolean canUse() {
            return allowsFloating() && super.canUse();
          }
        });
    goalSelector.addGoal(
        1,
        new SitWhenOrderedToGoal(this) {
          @Override
          public boolean canUse() {
            // The native goal also sits for an unavailable owner, even with a work order.
            return !FriendlySlime.this.isWorking() && super.canUse();
          }
        });
    goalSelector.addGoal(
        2,
        new FollowOwnerGoal(this, 1.2D, 4.0F, 2.0F, false) {
          private LivingEntity followed;

          @Override
          public void start() {
            followed = FriendlySlime.this.getOwner();
            super.start();
          }

          @Override
          public boolean canContinueToUse() {
            return followed == FriendlySlime.this.getOwner() && super.canContinueToUse();
          }

          @Override
          public void stop() {
            followed = null;
            super.stop();
          }
        });
    goalSelector.addGoal(
        5,
        new WaterAvoidingRandomStrollGoal(this, 1.0D) {
          @Override
          public boolean canUse() {
            return !isOrderedToSit() && !isWorking() && super.canUse();
          }

          @Override
          public boolean canContinueToUse() {
            return !isOrderedToSit() && !isWorking() && super.canContinueToUse();
          }
        });
    goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
    goalSelector.addGoal(7, new RandomLookAroundGoal(this));
  }

  @Override
  public void tick() {
    // Native command teleports can restore a copy without calling changeDimension.
    // Rebase its work area before any AI goal can use the old world's coordinates.
    if (!level().isClientSide && (workDimension == null || hasChangedWorkDimension())) onReleased();
    oldSquish = squish;
    squish += (targetSquish - squish) * 0.5F;
    super.tick();
    if (level() instanceof ServerLevel server && getOwnerUUID() != null && isAlive()) {
      if (tickCount % 20 == 0) refreshOwnerAccess();
      if (tickCount % 100 == Math.floorMod(getUUID().hashCode(), 100))
        CompanionRegistry.get(server.getServer()).observe(this);
    }
    if (!level().isClientSide) {
      if (isWorking() && isWorkBlocked()) {
        if (blockedWorkTicks == 0) blockedEpisode++;
        if (blockedWorkTicks < Integer.MAX_VALUE) blockedWorkTicks++;
      } else blockedWorkTicks = 0;
    }
    if (!level().isClientSide && tickCount % 20 == 0 && canRecoverStamina()) {
      recoverStamina(getStaminaRecoveryPerSecond());
    }
    if (!level().isClientSide) {
      if (defenseActivityCooldown > 0) defenseActivityCooldown--;
      if (dangerTicks > 0) dangerTicks--;
      var owner = getOwner();
      double movement =
          lastFollowingPosition == null
              ? 0
              : position().subtract(lastFollowingPosition).horizontalDistanceSqr();
      boolean following =
          !isFleeing()
              && !isWorking()
              && !isOrderedToSit()
              && owner != null
              && !owner.isSpectator()
              && distanceToSqr(owner) >= 4
              && distanceToSqr(owner) <= 256
              && getNavigation().isInProgress();
      boolean riding = getControllingPassenger() instanceof Player;
      if ((following || riding) && movement > .0001 && movement < 16) {
        if (++followingActivityTicks >= SlimicConfig.FOLLOW_ACTIVITY_INTERVAL.get()) {
          followingActivityTicks = 0;
          recordCompletedWork();
        }
      }
      lastFollowingPosition = position();
    }
    if (onGround() && !wasOnGround) {
      targetSquish = -0.5F;
      playSound(SoundEvents.SLIME_SQUISH, 0.6F, 1.0F);
      if (level().isClientSide) {
        for (int i = 0; i < 8; i++) {
          level()
              .addParticle(
                  ParticleTypes.ITEM_SLIME,
                  getRandomX(0.6D),
                  getY(),
                  getRandomZ(0.6D),
                  0.0D,
                  0.0D,
                  0.0D);
        }
      }
    } else if (!onGround() && wasOnGround) {
      targetSquish = 1.0F;
    }
    wasOnGround = onGround();
    targetSquish *= 0.6F;
  }

  @Override
  protected void jumpFromGround() {
    super.jumpFromGround();
    playSound(SoundEvents.SLIME_JUMP, 0.6F, 1.0F);
  }

  @Override
  public InteractionResult mobInteract(Player player, InteractionHand hand) {
    ItemStack held = player.getItemInHand(hand);
    if (player.isShiftKeyDown()) {
      return capture(player);
    }
    if (held.getItem() instanceof SlimeScannerItem) return SlimeScannerItem.preview(player, this);
    if (held.getItem() instanceof CreativeSlimeTunerItem)
      return CreativeSlimeTunerItem.open(player, this);
    if (held.getItem() instanceof SlimeTunerItem) return SlimeTunerItem.open(player, this);
    if (canManage(player) && isAlive() && held.is(Items.SLIME_BALL)) {
      if (getHealth() >= getMaxHealth())
        return InteractionResult.sidedSuccess(level().isClientSide);
      if (!level().isClientSide) {
        float previousHealth = getHealth();
        heal(4);
        if (getHealth() > previousHealth) {
          if (!player.getAbilities().instabuild) held.shrink(1);
          playSound(SoundEvents.SLIME_SQUISH, 1, 1);
          ((ServerLevel) level())
              .sendParticles(ParticleTypes.HEART, getX(), getEyeY(), getZ(), 4, .3, .2, .3, 0);
        }
      }
      return InteractionResult.sidedSuccess(level().isClientSide);
    }
    if (canManage(player) && isAlive() && held.is(Items.WATER_BUCKET)) {
      if (getDyeColorId() < 0) return InteractionResult.sidedSuccess(level().isClientSide);
      if (!level().isClientSide) {
        entityData.set(DYE_COLOR, -1);
        player.setItemInHand(
            hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.BUCKET)));
        playSound(SoundEvents.BUCKET_EMPTY, 1, 1);
      }
      return InteractionResult.sidedSuccess(level().isClientSide);
    }
    if (held.getItem() instanceof DyeItem)
      return dye(player, held)
          ? InteractionResult.sidedSuccess(level().isClientSide)
          : InteractionResult.FAIL;
    return super.mobInteract(player, hand);
  }

  public InteractionResult capture(Player player) {
    if (!isAlive() || isRemoved() || player.isSpectator() || !canManage(player)) {
      return InteractionResult.FAIL;
    }
    if (level().isClientSide) {
      return InteractionResult.SUCCESS;
    }
    int slot = player.getInventory().getFreeSlot();
    if (slot < 0) {
      player.displayClientMessage(
          Component.translatable("message.slimic.friendly_slime.inventory_full"), true);
      return InteractionResult.FAIL;
    }
    ItemStack captured = FriendlySlimeItem.fromSlime(this);
    // Inventory.add can discard items when a Creative inventory is full.
    player.getInventory().setItem(slot, captured);
    player.getInventory().setChanged();
    dropLeash(true, true);
    gameEvent(GameEvent.ENTITY_INTERACT, player);
    CompanionRegistry.get(level().getServer()).mark(this, CompanionRegistry.Location.ITEM);
    discard();
    player.displayClientMessage(
        Component.translatable("message.slimic.friendly_slime.captured"), true);
    return InteractionResult.CONSUME;
  }

  @Override
  public boolean isFood(ItemStack stack) {
    return false;
  }

  @Nullable
  @Override
  public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
    return null;
  }

  @Override
  protected SoundEvent getHurtSound(DamageSource source) {
    return SoundEvents.SLIME_HURT;
  }

  @Override
  protected SoundEvent getDeathSound() {
    return SoundEvents.SLIME_DEATH;
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.FRIENDLY_SLIME.get());
  }

  protected boolean canRecoverStamina() {
    return !isWorking() && !isFleeing();
  }

  protected boolean allowsFloating() {
    return true;
  }

  public boolean isFleeing() {
    return !(this instanceof CombatSlime)
        && !(this instanceof GuardianAngelSlime)
        && !isVehicle()
        && dangerTicks > 0
        && danger != null
        && danger.isAlive()
        && danger.level() == level()
        && distanceToSqr(danger) < 256;
  }

  @Override
  public boolean hurt(DamageSource source, float amount) {
    float before = getHealth();
    shieldHit = false;
    boolean damaged = super.hurt(source, amount);
    if (!level().isClientSide
        && isAlive()
        && source.getEntity() instanceof LivingEntity attacker
        && attacker != this
        && !attacker.getUUID().equals(getOwnerUUID())
        && !isAlliedTo(attacker)
        && (getHealth() < before || (shieldHit && amount > 0))) {
      if (defenseActivityCooldown == 0) {
        recordCompletedWork();
        defenseActivityCooldown = 20;
      }
      if (!(this instanceof CombatSlime)) {
        danger = attacker;
        dangerTicks = 200;
      }
    }
    return damaged;
  }

  @Override
  protected void hurtCurrentlyUsedShield(float damage) {
    shieldHit = true;
    super.hurtCurrentlyUsedShield(damage);
  }

  public int hopDelayTicks() {
    double speed = getAttributeValue(Attributes.MOVEMENT_SPEED);
    return speed <= 0 ? 200 : Mth.clamp((int) Math.ceil(15 * .3 / speed), 1, 200);
  }

  private final class FleeGoal extends Goal {
    private int repathTicks;

    FleeGoal() {
      setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
      return isFleeing();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
      return true;
    }

    @Override
    public void start() {
      repathTicks = 0;
      getNavigation().stop();
    }

    @Override
    public void stop() {
      getNavigation().stop();
    }

    @Override
    public void tick() {
      if (isInWaterOrBubble() || isInLava()) getJumpControl().jump();
      if (--repathTicks <= 0 && (onGround() || isInWater())) {
        repathTicks = 10;
        Vec3 away = DefaultRandomPos.getPosAway(FriendlySlime.this, 12, 4, danger.position());
        if (away != null && away.distanceToSqr(danger.position()) > distanceToSqr(danger)) {
          var path = getNavigation().createPath(BlockPos.containing(away), 0);
          if (path != null
              && path.getNodeCount() > 0
              && path.getEntityPosAtNode(FriendlySlime.this, path.getNodeCount() - 1)
                      .distanceToSqr(danger.position())
                  > distanceToSqr(danger)) getNavigation().moveTo(path, 1.2);
        }
      }
      if (getNavigation().isInProgress()
          && getDeltaMovement().horizontalDistanceSqr() > .0001
          && ++escapeActivityTicks >= SlimicConfig.FOLLOW_ACTIVITY_INTERVAL.get()) {
        escapeActivityTicks = 0;
        recordCompletedWork();
      }
    }
  }

  private static final class HopMoveControl extends MoveControl {
    private final FriendlySlime slime;
    private int jumpDelay;

    private HopMoveControl(FriendlySlime slime) {
      super(slime);
      this.slime = slime;
    }

    @Override
    public void setWantedPosition(double x, double y, double z, double speed) {
      // Ground jumps end on entering water. Otherwise native control retains the
      // old heading until ground contact and ignores new swimming destinations.
      if (slime.isInWater() && operation == Operation.JUMPING) operation = Operation.WAIT;
      super.setWantedPosition(x, y, z, speed);
    }

    @Override
    public void tick() {
      if (slime.getControllingPassenger() instanceof Player) return;
      if (slime.isInWater() && operation == Operation.JUMPING) operation = Operation.WAIT;
      boolean moving = hasWanted() || operation == Operation.JUMPING;
      super.tick();
      if ((slime.isOrderedToSit() && !slime.isFleeing())
          || slime.getAttributeValue(Attributes.MOVEMENT_SPEED) <= 0) {
        slime.setSpeed(0);
        slime.setZza(0);
        return;
      }
      // Keep propulsion fixed; the stat changes the pause between hops instead.
      if (moving) slime.setSpeed((float) (.3 * Math.min(1.5, speedModifier)));
      if (moving && slime.onGround()) {
        if (--jumpDelay <= 0) {
          jumpDelay = slime.hopDelayTicks();
          slime.getJumpControl().jump();
        } else {
          slime.setSpeed(0);
          slime.setZza(0);
        }
      }
      // Use full swimming input so flowing water cannot pin a worker to the
      // basin wall. Ground hop timing and capped propulsion remain separate.
      if (moving && slime.isInWater()) slime.setZza(1);
    }
  }
}
