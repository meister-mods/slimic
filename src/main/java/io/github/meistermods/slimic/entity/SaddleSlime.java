package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.item.CreativeSlimeTunerItem;
import io.github.meistermods.slimic.item.SlimeArmorItem;
import io.github.meistermods.slimic.item.SlimeTunerItem;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SaddleSlime extends FriendlySlime {
  private boolean ascending;
  private boolean boostRequested;
  private boolean energyExhausted;
  private static final EntityDataAccessor<Boolean> EXHAUSTED =
      SynchedEntityData.defineId(SaddleSlime.class, EntityDataSerializers.BOOLEAN);
  private static final EntityDataAccessor<Boolean> BOOSTING =
      SynchedEntityData.defineId(SaddleSlime.class, EntityDataSerializers.BOOLEAN);
  private int inputAge;
  private int jumpCooldown;
  private boolean serverFlightPrediction;

  public SaddleSlime(EntityType<? extends SaddleSlime> type, Level level) {
    super(type, level);
    setMaxUpStep(1);
    setDropChance(EquipmentSlot.MAINHAND, 2);
    setDropChance(EquipmentSlot.CHEST, 2);
    setDropChance(EquipmentSlot.OFFHAND, 2);
  }

  @Override
  protected void defineSynchedData() {
    super.defineSynchedData();
    entityData.define(BOOSTING, false);
    entityData.define(EXHAUSTED, false);
  }

  public boolean isBoosting() {
    return entityData.get(BOOSTING);
  }

  public boolean isGlidingDown() {
    return isFlyingMount() && entityData.get(EXHAUSTED);
  }

  public boolean isGroundedMount() {
    return onGround()
        || level().getBlockCollisions(this, getBoundingBox().move(0, -.05, 0)).iterator().hasNext();
  }

  @Override
  protected boolean canRecoverStamina() {
    return super.canRecoverStamina() && !isBoosting() && (!isFlyingMount() || isGroundedMount());
  }

  public static boolean matchesEquipment(int slot, ItemStack stack) {
    return switch (slot) {
      case 0 -> stack.is(Items.SADDLE);
      case 1 -> stack.is(Items.ELYTRA);
      case 2 -> stack.getItem() instanceof SlimeArmorItem;
      default -> false;
    };
  }

  public ItemStack getEquipment(int slot) {
    return getItemBySlot(equipmentSlot(slot));
  }

  public void setEquipment(int slot, ItemStack stack) {
    setItemSlot(equipmentSlot(slot), stack);
    if (!hasWings()) clearServerFlightPrediction();
    if (slot == 0 && !isSaddled()) {
      ejectPassengers();
      clearRiderInput();
    }
  }

  private static EquipmentSlot equipmentSlot(int slot) {
    return switch (slot) {
      case 0 -> EquipmentSlot.MAINHAND;
      case 1 -> EquipmentSlot.OFFHAND;
      case 2 -> EquipmentSlot.CHEST;
      default -> throw new IllegalArgumentException("Invalid saddle equipment slot: " + slot);
    };
  }

  @Override
  protected void hurtArmor(DamageSource source, float damage) {
    ItemStack armor = getEquipment(2);
    if (armor.getItem() instanceof SlimeArmorItem
        && (!source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
            || !SlimeArmorItem.baseArmor(armor).getItem().isFireResistant()))
      armor.hurtAndBreak(
          Math.max(1, (int) (damage / 4)),
          this,
          slime -> slime.broadcastBreakEvent(EquipmentSlot.CHEST));
  }

  public boolean isSaddled() {
    return getEquipment(0).is(Items.SADDLE);
  }

  public boolean hasWings() {
    return getEquipment(1).is(Items.ELYTRA) && ElytraItem.isFlyEnabled(getEquipment(1));
  }

  public boolean isFlyingMount() {
    return hasWings() && getControllingPassenger() != null;
  }

  public boolean mount(Player player) {
    if (!isAlive()
        || isRemoved()
        || !isSaddled()
        || !isTame()
        || player.isSpectator()
        || !player.getUUID().equals(getOwnerUUID())
        || player.level() != level()
        || player.distanceToSqr(this) > 64
        || isVehicle()
        || player.isPassenger()) return false;
    if (level().isClientSide) return true;
    setWaiting(false);
    getNavigation().stop();
    clearRiderInput();
    return player.startRiding(this);
  }

  @Override
  protected boolean canAddPassenger(Entity passenger) {
    return isSaddled()
        && !isVehicle()
        && passenger instanceof Player player
        && !player.isSpectator()
        && player.getUUID().equals(getOwnerUUID());
  }

  @Nullable
  @Override
  public LivingEntity getControllingPassenger() {
    return isSaddled()
            && getFirstPassenger() instanceof Player player
            && !player.isSpectator()
            && player.getUUID().equals(getOwnerUUID())
        ? player
        : null;
  }

  @Override
  public double getPassengersRidingOffset() {
    return getBbHeight() + .08;
  }

  public boolean setRiderInput(Player player, boolean up, boolean boost) {
    if (getControllingPassenger() != player || player.getVehicle() != this) return false;
    ascending = up;
    boostRequested = boost;
    inputAge = 0;
    return true;
  }

  private void clearRiderInput() {
    ascending = boostRequested = false;
    if (!level().isClientSide) entityData.set(BOOSTING, false);
    inputAge = 0;
  }

  private void clearServerFlightPrediction() {
    if (!serverFlightPrediction) return;
    serverFlightPrediction = false;
    setDeltaMovement(Vec3.ZERO);
  }

  @Override
  protected void removePassenger(Entity passenger) {
    super.removePassenger(passenger);
    clearServerFlightPrediction();
    clearRiderInput();
  }

  @Override
  protected void registerGoals() {
    super.registerGoals();
    goalSelector.addGoal(-1, new RideGoal());
  }

  @Override
  public InteractionResult mobInteract(Player player, InteractionHand hand) {
    if (player.isShiftKeyDown()) return super.mobInteract(player, hand);
    if (player.isSpectator() || !player.getUUID().equals(getOwnerUUID()))
      return InteractionResult.FAIL;
    ItemStack held = player.getItemInHand(hand);
    int slot =
        held.is(Items.SADDLE)
            ? 0
            : held.is(Items.ELYTRA) ? 1 : held.getItem() instanceof SlimeArmorItem ? 2 : -1;
    if (slot >= 0 && getEquipment(slot).isEmpty()) {
      if (!level().isClientSide) {
        setEquipment(slot, held.copyWithCount(1));
        if (!player.getAbilities().instabuild) held.shrink(1);
        playSound(
            slot == 0
                ? SoundEvents.HORSE_SADDLE
                : slot == 1 ? SoundEvents.ARMOR_EQUIP_ELYTRA : SoundEvents.ARMOR_EQUIP_IRON,
            1,
            1);
      }
      return InteractionResult.sidedSuccess(level().isClientSide);
    }
    if (held.getItem() instanceof SlimeTunerItem
        || held.getItem() instanceof CreativeSlimeTunerItem
        || held.getItem() instanceof DyeItem
        || held.is(Items.SLIME_BALL)
        || held.is(Items.WATER_BUCKET)) return super.mobInteract(player, hand);
    if (isSaddled())
      return mount(player)
          ? InteractionResult.sidedSuccess(level().isClientSide)
          : InteractionResult.FAIL;
    return super.mobInteract(player, hand);
  }

  @Override
  protected Vec3 getRiddenInput(Player rider, Vec3 input) {
    return new Vec3(
        rider.xxa * (isFlyingMount() ? 1 : .5),
        ascending ? 1 : 0,
        rider.zza * (!isFlyingMount() && rider.zza < 0 ? .25 : 1));
  }

  @Override
  protected float getRiddenSpeed(Player rider) {
    if (!isFlyingMount()) return isBoosting() ? .6F : .3F;
    double movement = getAttributeValue(Attributes.MOVEMENT_SPEED);
    if (!Double.isFinite(movement) || movement <= 0) return 0;
    double cruise = Math.min(.45, .24 * Math.sqrt(movement / .3));
    return (float) (cruise * (isBoosting() ? 1.75 : 1));
  }

  @Override
  protected void tickRidden(Player rider, Vec3 input) {
    setYRot(rider.getYRot());
    yRotO = yBodyRot = yHeadRot = getYRot();
    setXRot(rider.getXRot() * .5F);
    if (!isFlyingMount()
        && (ascending || Math.abs(rider.zza) + Math.abs(rider.xxa) > .01F)
        && getAttributeValue(Attributes.MOVEMENT_SPEED) > 0
        && onGround()
        && jumpCooldown == 0
        && isControlledByLocalInstance()) {
      jumpFromGround();
      jumpCooldown = Math.max(1, hopDelayTicks() / (isBoosting() ? 2 : 1));
    }
  }

  @Override
  public boolean isNoGravity() {
    return isFlyingMount() || super.isNoGravity();
  }

  @Override
  public void travel(Vec3 input) {
    if (isFlyingMount() && getControllingPassenger() instanceof Player rider) {
      if (isControlledByLocalInstance()) {
        setDeltaMovement(flightMovement(rider, input));
        move(MoverType.SELF, getDeltaMovement());
      }
      resetFallDistance();
      calculateEntityAnimation(true);
    } else super.travel(input);
  }

  private Vec3 flightMovement(Player rider, Vec3 input) {
    float yaw = rider.getYRot() * Mth.DEG_TO_RAD;
    Vec3 right = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
    Vec3 direction =
        rider.getLookAngle().scale(input.z).add(right.scale(input.x)).add(0, input.y, 0);
    if (direction.lengthSqr() > 1) direction = direction.normalize();
    return isGlidingDown()
        ? new Vec3(direction.x * .15, -.12, direction.z * .15)
        : direction.scale(getRiddenSpeed(rider));
  }

  @Override
  public void tick() {
    // This velocity is for native vehicle-packet validation, not a second physics step.
    clearServerFlightPrediction();
    if (isVehicle() && getControllingPassenger() == null && !level().isClientSide)
      ejectPassengers();
    if (getControllingPassenger() == null || ++inputAge > 30) clearRiderInput();
    if (jumpCooldown > 0) jumpCooldown--;
    super.tick();
    if (isFlyingMount()) resetFallDistance();
    if (!level().isClientSide) {
      if (energyExhausted && isGroundedMount() && getStamina() >= getMaxStamina() * .25F)
        energyExhausted = false;
      boolean airborne = isFlyingMount() && (!isGroundedMount() || ascending);
      boolean boost = getControllingPassenger() != null && boostRequested && !energyExhausted;
      float cost = airborne ? (boost ? .75F : .1F) : boost ? .5F : 0;
      if (cost > 0 && !energyExhausted && !spendStamina(cost)) {
        spendStamina(getStamina());
        energyExhausted = true;
        boost = false;
      }
      entityData.set(EXHAUSTED, energyExhausted);
      entityData.set(BOOSTING, boost);
      if (isFlyingMount() && getControllingPassenger() instanceof Player rider) {
        setDeltaMovement(flightMovement(rider, getRiddenInput(rider, Vec3.ZERO)));
        serverFlightPrediction = true;
      }
    }
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putBoolean("SaddleEnergyExhausted", energyExhausted);
    tag.putInt("SaddleEquipmentVersion", 1);
    if (serverFlightPrediction) tag.put("Motion", newDoubleList(0, 0, 0));
    // Flight is a transient consequence of the rider and wings, not a saved gravity override.
    if (isFlyingMount() && !super.isNoGravity()) tag.remove("NoGravity");
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    if (tag.getInt("SaddleEquipmentVersion") < 1
        && getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
      ItemStack wings = getItemBySlot(EquipmentSlot.CHEST);
      ItemStack previous = getItemBySlot(EquipmentSlot.OFFHAND);
      setItemSlot(EquipmentSlot.CHEST, previous);
      setItemSlot(EquipmentSlot.OFFHAND, wings);
    }
    serverFlightPrediction = false;
    energyExhausted = tag.getBoolean("SaddleEnergyExhausted");
    entityData.set(EXHAUSTED, energyExhausted);
    clearRiderInput();
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.SADDLE_SLIME.get());
  }

  private final class RideGoal extends Goal {
    RideGoal() {
      setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
      return getControllingPassenger() != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
      return true;
    }

    @Override
    public void tick() {
      getNavigation().stop();
    }

    @Override
    public void stop() {
      clearRiderInput();
    }
  }
}
