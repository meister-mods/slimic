package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.item.CreativeSlimeTunerItem;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
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
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkHooks;

/** A transient second body; its linked colony alone owns cargo, life and stored identity. */
public abstract class LinkedColonyBody<C extends FriendlySlime & LinkedColony>
    extends PathfinderMob {
  private static final EntityDataAccessor<Integer> COLONY =
      SynchedEntityData.defineId(LinkedColonyBody.class, EntityDataSerializers.INT);

  public float oldSquish;
  public float squish;
  private float targetSquish;
  private boolean wasOnGround;

  private final Class<C> colonyClass;

  protected LinkedColonyBody(
      EntityType<? extends LinkedColonyBody<C>> type, Level level, Class<C> colonyClass) {
    super(type, level);
    this.colonyClass = colonyClass;
    moveControl = new HopMoveControl();
    setPersistenceRequired();
  }

  @Override
  protected void defineSynchedData() {
    super.defineSynchedData();
    entityData.define(COLONY, -1);
  }

  public void setColony(C colony) {
    if (colony.level() != level()) throw new IllegalArgumentException("Colony is in another level");
    entityData.set(COLONY, colony.getId());
  }

  @Nullable
  public C getColony() {
    if (!isAddedToWorld() || isRemoved()) return null;
    Entity entity = level().getEntity(entityData.get(COLONY));
    if (!colonyClass.isInstance(entity)) return null;
    C colony = colonyClass.cast(entity);
    return colony.level() == level()
            && colony.isAlive()
            && !colony.isRemoved()
            && colony.isAddedToWorld()
            && !colony.isPendingSpawn()
            && colony.getLinkedBody() == this
        ? colony
        : null;
  }

  @Override
  public net.minecraft.network.chat.Component getName() {
    C colony = getColony();
    return colony == null ? super.getName() : colony.getName();
  }

  @Override
  public net.minecraft.network.chat.Component getDisplayName() {
    C colony = getColony();
    return colony == null ? super.getDisplayName() : colony.getDisplayName();
  }

  @Override
  public float getHealth() {
    C colony = getColony();
    return colony == null ? super.getHealth() : colony.getHealth();
  }

  @Override
  public void setHealth(float health) {
    C colony = getColony();
    if (colony == null) super.setHealth(health);
    else colony.setHealth(health);
  }

  @Override
  public double getAttributeValue(net.minecraft.world.entity.ai.attributes.Attribute attribute) {
    C colony = getColony();
    return attribute == Attributes.MAX_HEALTH && colony != null
        ? colony.getMaxHealth()
        : super.getAttributeValue(attribute);
  }

  public float getStamina() {
    C colony = getColony();
    return colony == null ? 0 : colony.getStamina();
  }

  public float getMaxStamina() {
    C colony = getColony();
    return colony == null ? 0 : colony.getMaxStamina();
  }

  @Override
  protected PathNavigation createNavigation(Level level) {
    return new WorkNavigation(this, level, () -> true);
  }

  @Override
  protected void registerGoals() {
    goalSelector.addGoal(0, new FloatGoal(this));
  }

  @Override
  public void tick() {
    C colony = getColony();
    if (!level().isClientSide && colony == null) {
      discard();
      return;
    }
    if (colony != null) {
      // Keep vanilla attribute/data packets consistent for HUDs that inspect the second body.
      if (!level().isClientSide) {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(colony.getMaxHealth());
        super.setHealth(colony.getHealth());
      }
      getAttribute(Attributes.MOVEMENT_SPEED)
          .setBaseValue(colony.getAttributeValue(Attributes.MOVEMENT_SPEED));
      if ((colony.isOrderedToSit() && !colony.isFleeing()) || colony.isBodyInMotion(this)) {
        getNavigation().stop();
        setSpeed(0);
        setZza(0);
        setXxa(0);
      }
    }
    oldSquish = squish;
    squish += (targetSquish - squish) * .5F;
    super.tick();
    if (onGround() && !wasOnGround) {
      targetSquish = -.5F;
      playSound(SoundEvents.SLIME_SQUISH, .5F, 1);
    } else if (!onGround() && wasOnGround) targetSquish = 1;
    wasOnGround = onGround();
    targetSquish *= .6F;
  }

  @Override
  public boolean hasLineOfSight(Entity target) {
    return target.level() == level()
        && getEyePosition().distanceToSqr(target.getEyePosition()) <= 128 * 128
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

  /** Forward the complete native interaction, including the held item's fallback, once. */
  public InteractionResult interactWithColony(Player player, InteractionHand hand) {
    C colony = getColony();
    if (colony == null
        || isRemoved()
        || player.isSpectator()
        || player.level() != level()
        || player.distanceToSqr(this) > 64
        || distanceToSqr(colony) > colony.getLinkRange() * colony.getLinkRange()
        || (!colony.canManage(player)
            && !(player.getAbilities().instabuild
                && player.getItemInHand(hand).getItem() instanceof CreativeSlimeTunerItem)))
      return InteractionResult.FAIL;
    return player.interactOn(colony, hand);
  }

  @Override
  public InteractionResult mobInteract(Player player, InteractionHand hand) {
    return interactWithColony(player, hand);
  }

  @Override
  public boolean hurt(DamageSource source, float amount) {
    C colony = getColony();
    if (level().isClientSide || colony == null || isRemoved()) return false;
    boolean damaged = colony.hurt(source, amount);
    if (damaged && !isRemoved()) {
      hurtDuration = hurtTime = 10;
      level().broadcastEntityEvent(this, (byte) 2);
    }
    return damaged;
  }

  @Override
  public boolean isAlliedTo(Entity other) {
    C colony = getColony();
    return colony != null && (other == colony || colony.isAlliedTo(other));
  }

  @Override
  public void die(DamageSource source) {
    discard();
  }

  @Override
  protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {}

  @Override
  protected boolean shouldDropLoot() {
    return false;
  }

  @Override
  public boolean canTakeItem(ItemStack stack) {
    return false;
  }

  @Override
  public boolean canHoldItem(ItemStack stack) {
    return false;
  }

  @Override
  public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
      net.minecraftforge.common.capabilities.Capability<T> capability,
      @Nullable net.minecraft.core.Direction side) {
    if (capability == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER)
      return net.minecraftforge.common.util.LazyOptional.empty();
    return super.getCapability(capability, side);
  }

  @Override
  public boolean shouldBeSaved() {
    return false;
  }

  @Override
  public boolean canChangeDimensions() {
    return false;
  }

  @Override
  public boolean canBeLeashed(Player player) {
    return false;
  }

  @Override
  public boolean startRiding(Entity vehicle, boolean force) {
    return false;
  }

  @Override
  protected boolean canAddPassenger(Entity passenger) {
    return false;
  }

  @Override
  public ItemStack getPickResult() {
    return ItemStack.EMPTY;
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {}

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    entityData.set(COLONY, -1);
  }

  @Override
  public Packet<ClientGamePacketListener> getAddEntityPacket() {
    return NetworkHooks.getEntitySpawningPacket(this);
  }

  @Override
  protected void jumpFromGround() {
    super.jumpFromGround();
    playSound(SoundEvents.SLIME_JUMP, .5F, 1);
  }

  private final class HopMoveControl extends MoveControl {
    private int jumpDelay;

    private HopMoveControl() {
      super(LinkedColonyBody.this);
    }

    @Override
    public void setWantedPosition(double x, double y, double z, double speed) {
      // A hop can pass several path nodes. Accept the next heading while airborne instead of
      // continuing toward a node already behind the body until the next landing.
      if (operation == Operation.JUMPING) operation = Operation.WAIT;
      super.setWantedPosition(x, y, z, speed);
    }

    @Override
    public void tick() {
      C colony = getColony();
      if (colony == null
          || (colony.isOrderedToSit() && !colony.isFleeing())
          || colony.isBodyInMotion(LinkedColonyBody.this)
          || colony.getAttributeValue(Attributes.MOVEMENT_SPEED) <= 0) {
        operation = Operation.WAIT;
        setSpeed(0);
        setZza(0);
        return;
      }
      if (isInWater() && operation == Operation.JUMPING) operation = Operation.WAIT;
      boolean moving = hasWanted() || operation == Operation.JUMPING;
      super.tick();
      if (moving) setSpeed((float) (.3 * Math.min(1.5, speedModifier)));
      if (moving && onGround()) {
        if (--jumpDelay <= 0) {
          jumpDelay = Mth.clamp(colony.hopDelayTicks(), 1, 200);
          getJumpControl().jump();
        } else {
          setSpeed(0);
          setZza(0);
        }
      }
      if (moving && isInWater()) setZza(1);
    }
  }
}
