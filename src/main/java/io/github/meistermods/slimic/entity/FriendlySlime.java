package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

public final class FriendlySlime extends TamableAnimal {
  public float squish;
  public float oldSquish;
  private float targetSquish;
  private boolean wasOnGround;

  public FriendlySlime(EntityType<? extends FriendlySlime> type, Level level) {
    super(type, level);
    moveControl = new HopMoveControl(this);
    setPersistenceRequired();
  }

  public static AttributeSupplier.Builder createAttributes() {
    return createMobAttributes()
        .add(Attributes.MAX_HEALTH, 16.0D)
        .add(Attributes.MOVEMENT_SPEED, 0.3D)
        .add(Attributes.FOLLOW_RANGE, 32.0D);
  }

  @Override
  protected void registerGoals() {
    goalSelector.addGoal(0, new FloatGoal(this));
    goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
    goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.2D, 4.0F, 2.0F, false));
    goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0D));
    goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
    goalSelector.addGoal(7, new RandomLookAroundGoal(this));
  }

  @Override
  public void tick() {
    oldSquish = squish;
    squish += (targetSquish - squish) * 0.5F;
    super.tick();
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
    if (player.isShiftKeyDown()) {
      return capture(player);
    }
    if (player.getUUID().equals(getOwnerUUID()) && player.getItemInHand(hand).isEmpty()) {
      if (!level().isClientSide) {
        setOrderedToSit(!isOrderedToSit());
        setInSittingPose(isOrderedToSit());
        getNavigation().stop();
        player.displayClientMessage(
            Component.translatable(
                isOrderedToSit()
                    ? "message.slimic.friendly_slime.waiting"
                    : "message.slimic.friendly_slime.following"),
            true);
      }
      return InteractionResult.sidedSuccess(level().isClientSide);
    }
    return super.mobInteract(player, hand);
  }

  public InteractionResult capture(Player player) {
    if (!isAlive()
        || isRemoved()
        || player.isSpectator()
        || !player.getUUID().equals(getOwnerUUID())) {
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

  private static final class HopMoveControl extends MoveControl {
    private final FriendlySlime slime;
    private int jumpDelay;

    private HopMoveControl(FriendlySlime slime) {
      super(slime);
      this.slime = slime;
    }

    @Override
    public void tick() {
      boolean moving = hasWanted();
      super.tick();
      if (slime.isOrderedToSit()) {
        slime.setSpeed(0.0F);
        slime.setZza(0.0F);
        return;
      }
      if (moving && slime.onGround()) {
        if (--jumpDelay <= 0) {
          jumpDelay = 10 + slime.getRandom().nextInt(10);
          slime.getJumpControl().jump();
        } else {
          slime.setSpeed(0.0F);
          slime.setZza(0.0F);
        }
      }
    }
  }
}
