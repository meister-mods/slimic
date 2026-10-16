package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.registry.ModItems;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SaddleSlime extends FriendlySlime {
  private boolean ascending;
  private boolean descending;
  private int inputAge;
  private int jumpCooldown;

  public SaddleSlime(EntityType<? extends SaddleSlime> type, Level level) {
    super(type, level);
    setMaxUpStep(1);
    setDropChance(EquipmentSlot.MAINHAND, 2);
    setDropChance(EquipmentSlot.CHEST, 2);
  }

  public static boolean matchesEquipment(int slot, ItemStack stack) {
    return slot == 0 ? stack.is(Items.SADDLE) : slot == 1 && stack.is(Items.ELYTRA);
  }

  public ItemStack getEquipment(int slot) {
    return getItemBySlot(slot == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.CHEST);
  }

  public void setEquipment(int slot, ItemStack stack) {
    setItemSlot(slot == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.CHEST, stack);
    if (slot == 0 && !isSaddled()) {
      ejectPassengers();
      clearRiderInput();
    }
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

  public boolean setRiderInput(Player player, boolean up, boolean down) {
    if (getControllingPassenger() != player || player.getVehicle() != this) return false;
    ascending = up;
    descending = down;
    inputAge = 0;
    return true;
  }

  private void clearRiderInput() {
    ascending = descending = false;
    inputAge = 0;
  }

  @Override
  protected void registerGoals() {
    super.registerGoals();
    goalSelector.addGoal(-1, new RideGoal());
  }

  @Override
  public InteractionResult mobInteract(Player player, InteractionHand hand) {
    if (player.isShiftKeyDown()) return super.mobInteract(player, hand);
    if (!player.getUUID().equals(getOwnerUUID())) return InteractionResult.FAIL;
    ItemStack held = player.getItemInHand(hand);
    int slot = held.is(Items.SADDLE) ? 0 : held.is(Items.ELYTRA) ? 1 : -1;
    if (slot >= 0 && getEquipment(slot).isEmpty()) {
      if (!level().isClientSide) {
        setEquipment(slot, held.copyWithCount(1));
        if (!player.getAbilities().instabuild) held.shrink(1);
        playSound(slot == 0 ? SoundEvents.HORSE_SADDLE : SoundEvents.ARMOR_EQUIP_ELYTRA, 1, 1);
      }
      return InteractionResult.sidedSuccess(level().isClientSide);
    }
    if (held.isEmpty() && isSaddled())
      return mount(player)
          ? InteractionResult.sidedSuccess(level().isClientSide)
          : InteractionResult.FAIL;
    return super.mobInteract(player, hand);
  }

  @Override
  protected Vec3 getRiddenInput(Player rider, Vec3 input) {
    return new Vec3(
        rider.xxa * (isFlyingMount() ? 1 : .5),
        (ascending ? 1 : 0) - (descending ? 1 : 0),
        rider.zza * (!isFlyingMount() && rider.zza < 0 ? .25 : 1));
  }

  @Override
  protected float getRiddenSpeed(Player rider) {
    return (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
  }

  @Override
  protected void tickRidden(Player rider, Vec3 input) {
    setYRot(rider.getYRot());
    yRotO = yBodyRot = yHeadRot = getYRot();
    setXRot(rider.getXRot() * .5F);
    if (!isFlyingMount()
        && ascending
        && onGround()
        && jumpCooldown == 0
        && isControlledByLocalInstance()) {
      jumpFromGround();
      jumpCooldown = 10;
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
        float yaw = rider.getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        Vec3 direction =
            rider.getLookAngle().scale(input.z).add(right.scale(input.x)).add(0, input.y, 0);
        if (direction.lengthSqr() > 1) direction = direction.normalize();
        setDeltaMovement(direction.scale(getRiddenSpeed(rider) * 1.5));
        move(MoverType.SELF, getDeltaMovement());
      }
      resetFallDistance();
      calculateEntityAnimation(true);
    } else super.travel(input);
  }

  @Override
  public void tick() {
    if (isVehicle() && getControllingPassenger() == null && !level().isClientSide)
      ejectPassengers();
    if (getControllingPassenger() == null || ++inputAge > 30) clearRiderInput();
    if (jumpCooldown > 0) jumpCooldown--;
    super.tick();
    if (isFlyingMount()) resetFallDistance();
  }

  @Override
  public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    // Flight is a transient consequence of the rider and wings, not a saved gravity override.
    if (isFlyingMount() && !super.isNoGravity()) tag.remove("NoGravity");
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
