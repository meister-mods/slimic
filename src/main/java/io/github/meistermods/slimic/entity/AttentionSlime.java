package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.registry.ModItems;
import java.util.EnumSet;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class AttentionSlime extends FriendlySlime {
  private static final EntityDataAccessor<Boolean> ROAMING =
      SynchedEntityData.defineId(AttentionSlime.class, EntityDataSerializers.BOOLEAN);

  private int roamingActivity;
  private net.minecraft.world.phys.Vec3 previousPosition;

  public AttentionSlime(EntityType<? extends AttentionSlime> type, Level level) {
    super(type, level);
    ensureSpeedScale();
  }

  private static final java.util.UUID SPEED_SCALE =
      java.util.UUID.fromString("9bd346c1-5c11-4af6-b4a8-ae79c9c36374");

  /** Removes profession-only state when this companion becomes another specialization. */
  public static void removeProfessionData(CompoundTag data) {
    data.remove("AttentionRoaming");
    data.remove("AttentionActivity");
    for (var element : data.getList("Attributes", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
      var attribute = (CompoundTag) element;
      attribute
          .getList("Modifiers", net.minecraft.nbt.Tag.TAG_COMPOUND)
          .removeIf(
              modifier -> {
                var saved = (CompoundTag) modifier;
                return saved.hasUUID("UUID") && SPEED_SCALE.equals(saved.getUUID("UUID"));
              });
    }
    data.putBoolean("Sitting", true);
  }

  private void ensureSpeedScale() {
    var attribute =
        getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
    if (attribute.getModifier(SPEED_SCALE) == null)
      attribute.addPermanentModifier(
          new net.minecraft.world.entity.ai.attributes.AttributeModifier(
              SPEED_SCALE,
              "Attention speed",
              .5,
              net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
  }

  @Override
  protected void defineSynchedData() {
    super.defineSynchedData();
    entityData.define(ROAMING, false);
  }

  public boolean isRoaming() {
    return entityData.get(ROAMING)
        && (level().isClientSide ? !isInSittingPose() : !isOrderedToSit());
  }

  public void setRoaming(boolean value) {
    entityData.set(ROAMING, value);
    super.setWaiting(!value);
  }

  @Override
  public void setWaiting(boolean value) {
    entityData.set(ROAMING, false);
    super.setWaiting(value);
  }

  @Override
  public boolean isWorking() {
    return isRoaming();
  }

  @Override
  protected WorkStatus describeWork() {
    return WorkStatus.of(WorkStatus.Order.ROAM, WorkStatus.State.ACTIVE);
  }

  @Override
  protected void registerGoals() {
    super.registerGoals();
    goalSelector.addGoal(
        1,
        new Goal() {
          private int nextDestinationTick;

          {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
          }

          @Override
          public boolean canUse() {
            return isRoaming();
          }

          @Override
          public boolean requiresUpdateEveryTick() {
            return true;
          }

          @Override
          public void stop() {
            getNavigation().stop();
            nextDestinationTick = 0;
          }

          @Override
          public void tick() {
            if (tickCount < nextDestinationTick) return;
            nextDestinationTick = tickCount + 10;
            var owner = getOwner();
            var center =
                owner != null && owner.level() == level() && distanceToSqr(owner) <= 24 * 24
                    ? owner.blockPosition()
                    : blockPosition();
            if (!chooseDestination(center) && !center.equals(blockPosition()))
              chooseDestination(blockPosition());
          }

          private boolean chooseDestination(net.minecraft.core.BlockPos center) {
            for (int i = 0; i < 12; i++) {
              var pos =
                  center.offset(
                      random.nextInt(7) - 3, random.nextInt(5) - 2, random.nextInt(7) - 3);
              if (!level().hasChunkAt(pos)
                  || !level().hasChunkAt(pos.offset(1, 1, 1))
                  || !level()
                      .getBlockState(pos.below())
                      .isFaceSturdy(level(), pos.below(), Direction.UP)
                  || !level().getFluidState(pos).isEmpty()) continue;
              var path = getNavigation().createPath(pos, 0);
              if (path != null && path.canReach()) {
                getNavigation().moveTo(path, 1.2);
                return true;
              }
            }
            return false;
          }
        });
  }

  @Override
  public void tick() {
    super.tick();
    if (!level().isClientSide) {
      double step =
          previousPosition == null
              ? 0
              : position().subtract(previousPosition).horizontalDistanceSqr();
      if (isRoaming()
          && step > .0001
          && step < 16
          && ++roamingActivity
              >= io.github.meistermods.slimic.config.SlimicConfig.FOLLOW_ACTIVITY_INTERVAL.get()) {
        roamingActivity = 0;
        recordCompletedWork();
      }
      previousPosition = position();
    }
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.ATTENTION_SLIME.get());
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putBoolean("AttentionRoaming", isRoaming());
    tag.putInt("AttentionActivity", roamingActivity);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    ensureSpeedScale();
    roamingActivity =
        Math.max(
            0,
            Math.min(
                io.github.meistermods.slimic.config.SlimicConfig.FOLLOW_ACTIVITY_INTERVAL.get() - 1,
                tag.getInt("AttentionActivity")));
    previousPosition = null;
    entityData.set(ROAMING, tag.getBoolean("AttentionRoaming") && !isOrderedToSit());
    if (!tag.contains("AttentionRoaming")) setWaiting(true);
  }
}
