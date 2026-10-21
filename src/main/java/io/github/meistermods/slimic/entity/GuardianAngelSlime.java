package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.registry.ModItems;
import java.util.EnumSet;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;

public final class GuardianAngelSlime extends FriendlySlime {
  private static final EntityDataAccessor<ItemStack> TOTEMS =
      SynchedEntityData.defineId(GuardianAngelSlime.class, EntityDataSerializers.ITEM_STACK);
  private static final EntityDataAccessor<Integer> BEAM =
      SynchedEntityData.defineId(GuardianAngelSlime.class, EntityDataSerializers.INT);
  private LivingEntity retaliation;
  private boolean defendingSelf;
  private int retaliationTicks, attackCooldown, beamTicks;
  private boolean protecting;

  public GuardianAngelSlime(EntityType<? extends GuardianAngelSlime> type, Level level) {
    super(type, level);
    setWaiting(true);
    moveControl = new FlyingMoveControl(this, 20, true);
    setNoGravity(true);
    ensureHealthScale();
    setHealth(getMaxHealth());
  }

  private void ensureHealthScale() {
    ProfessionHealth.apply(this, ProfessionHealth.GUARDIAN);
  }

  @Override
  protected void defineSynchedData() {
    super.defineSynchedData();
    entityData.define(TOTEMS, ItemStack.EMPTY);
    entityData.define(BEAM, -1);
  }

  @Override
  protected PathNavigation createNavigation(Level level) {
    var nav = new LoadedFlyingNavigation(this, level);
    nav.setCanFloat(true);
    nav.setCanPassDoors(true);
    return nav;
  }

  @Override
  protected void registerGoals() {
    goalSelector.addGoal(0, new GuardGoal());
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

  public ItemStack getTotems() {
    return entityData.get(TOTEMS);
  }

  public void setTotems(ItemStack stack) {
    entityData.set(
        TOTEMS,
        stack.is(Items.TOTEM_OF_UNDYING)
            ? stack.copyWithCount(Math.min(64, stack.getCount()))
            : ItemStack.EMPTY);
  }

  public int addTotems(ItemStack stack) {
    if (!stack.is(Items.TOTEM_OF_UNDYING)
        || (!getTotems().isEmpty() && !ItemStack.isSameItemSameTags(getTotems(), stack))) return 0;
    int moved = Math.min(64 - getTotems().getCount(), stack.getCount());
    if (moved > 0) setTotems(stack.copyWithCount(getTotems().getCount() + moved));
    return moved;
  }

  public int getBeamTargetId() {
    return entityData.get(BEAM);
  }

  private boolean hostileToOwner(LivingEntity attacker) {
    if (getOwnerUUID() == null) return false;
    var owner = getOwner();
    if (attacker == this
        || attacker == owner
        || attacker.getUUID().equals(getOwnerUUID())
        || !attacker.isAlive()
        || attacker.isSpectator()
        || attacker.level() != level()
        || !canAttack(attacker)
        || isAlliedTo(attacker)
        || (owner != null && owner.isAlliedTo(attacker))) return false;
    if (attacker instanceof Player player
        && (player.getAbilities().instabuild
            || !level().getServer().isPvpAllowed()
            || (owner instanceof Player playerOwner && !playerOwner.canHarmPlayer(player))))
      return false;
    if (attacker instanceof TamableAnimal pet
        && CompanionOwnership.shares(level(), getOwnerUUID(), pet.getOwnerUUID())) return false;
    return !(attacker instanceof AbstractHorse horse
        && CompanionOwnership.shares(level(), getOwnerUUID(), horse.getOwnerUUID()));
  }

  public void defendAgainst(LivingEntity attacker) {
    if (hostileToOwner(attacker)) {
      retaliation = attacker;
      retaliationTicks = 200;
      defendingSelf = false;
    }
  }

  @Override
  public boolean hurt(DamageSource source, float amount) {
    float before = getHealth();
    float absorptionBefore = getAbsorptionAmount();
    boolean damaged = super.hurt(source, amount);
    if (!level().isClientSide
        && isAlive()
        && (getHealth() < before || getAbsorptionAmount() < absorptionBefore)
        && source.getEntity() instanceof LivingEntity attacker
        && hostileToOwner(attacker)) {
      retaliation = attacker;
      retaliationTicks = 200;
      defendingSelf = true;
    }
    return damaged;
  }

  private boolean canProtect(Player player, DamageSource source) {
    return !level().isClientSide
        && isAlive()
        && !isRemoved()
        && isTame()
        && !getTotems().isEmpty()
        && player.level() == level()
        && canManage(player)
        && distanceToSqr(player) <= 256
        && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
  }

  public boolean protect(Player player, DamageSource source) {
    if (protecting || !canProtect(player, source)) return false;
    ItemStack reserve = getTotems().copy();
    ItemStack used = getTotems().copyWithCount(1);
    protecting = true;
    try {
      if (!ForgeHooks.onLivingUseTotem(player, source, used, InteractionHand.OFF_HAND)
          || !canProtect(player, source)
          || !ItemStack.matches(reserve, getTotems())) return false;
      ItemStack remaining = getTotems().copy();
      remaining.shrink(1);
      setTotems(remaining);
      player.setHealth(1);
      player.removeAllEffects();
      player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
      player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
      player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
      level().broadcastEntityEvent(player, (byte) 35);
      if (player instanceof ServerPlayer server) {
        server.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));
        CriteriaTriggers.USED_TOTEM.trigger(server, used);
      }
      recordCompletedWork(4);
      return true;
    } finally {
      protecting = false;
    }
  }

  @Override
  public void tick() {
    super.tick();
    setNoGravity(true);
    getAttribute(Attributes.FLYING_SPEED)
        .setBaseValue(getAttributeValue(Attributes.MOVEMENT_SPEED));
    if (!level().isClientSide) {
      if (attackCooldown > 0) attackCooldown--;
      if (retaliationTicks > 0) retaliationTicks--;
      if (beamTicks > 0 && --beamTicks == 0) entityData.set(BEAM, -1);
    }
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.GUARDIAN_ANGEL_SLIME.get());
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.put("GuardianTotems", getTotems().save(new CompoundTag()));
    tag.putInt("GuardianAttackCooldown", attackCooldown);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    if (!tag.contains("GuardianTotems")) setWaiting(true);
    ensureHealthScale();
    setTotems(ItemStack.of(tag.getCompound("GuardianTotems")));
    attackCooldown = Math.max(0, Math.min(200, tag.getInt("GuardianAttackCooldown")));
    retaliation = null;
    defendingSelf = false;
    retaliationTicks = 0;
    setNoGravity(true);
  }

  private final class GuardGoal extends Goal {
    GuardGoal() {
      setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
      return isTame() && (getOwner() != null || (defendingSelf && retaliationTicks > 0));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
      return true;
    }

    @Override
    public void stop() {
      getNavigation().stop();
      setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void tick() {
      LivingEntity owner = getOwner();
      if (retaliationTicks > 0
          && retaliation != null
          && hostileToOwner(retaliation)
          && (defendingSelf || (owner != null && distanceToSqr(owner) <= 256))) {
        double range =
            Math.min(
                SlimeStat.DETECTION_RANGE.value(GuardianAngelSlime.this),
                SlimeStat.REACH.value(GuardianAngelSlime.this) * 4);
        getLookControl().setLookAt(retaliation);
        if (distanceToSqr(retaliation) <= range * range
            && getSensing().hasLineOfSight(retaliation)
            && attackCooldown == 0
            && getStamina() >= 1) {
          entityData.set(BEAM, retaliation.getId());
          beamTicks = 10;
          attackCooldown =
              Math.max(
                  5, (int) Math.ceil(20 / SlimeStat.WORK_SPEED.value(GuardianAngelSlime.this)));
          if (retaliation.hurt(
              damageSources().mobAttack(GuardianAngelSlime.this),
              (float) SlimeStat.ATTACK.value(GuardianAngelSlime.this))) {
            spendStamina(1);
            recordCompletedWork();
          }
        }
      }
      if (owner == null) {
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().scale(.5));
        return;
      }
      if (isOrderedToSit()) {
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().scale(.5));
        return;
      }
      Vec3 goal = owner.position().add(1.5, 1.8, 1.5);
      if (position().distanceToSqr(goal) < 1) {
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().scale(.5));
        return;
      }
      if (tickCount % 10 == 0) getNavigation().moveTo(goal.x, goal.y, goal.z, 1);
      if (distanceToSqr(owner) > 576
          && level().hasChunkAt(owner.blockPosition())
          && level().hasChunkAt(BlockPos.containing(goal).offset(1, 0, 1))
          && level()
              .getWorldBorder()
              .isWithinBounds(getBoundingBox().move(goal.subtract(position())))
          && level()
              .noCollision(
                  GuardianAngelSlime.this, getBoundingBox().move(goal.subtract(position()))))
        teleportTo(goal.x, goal.y, goal.z);
    }
  }
}
