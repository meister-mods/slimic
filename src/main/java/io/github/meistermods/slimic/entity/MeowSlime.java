package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Slimic.MOD_ID)
public final class MeowSlime extends FriendlySlime {
  private static final EntityDataAccessor<Boolean> ACTIVE =
      SynchedEntityData.defineId(MeowSlime.class, EntityDataSerializers.BOOLEAN);
  private static final EntityDataAccessor<Boolean> ROAMING =
      SynchedEntityData.defineId(MeowSlime.class, EntityDataSerializers.BOOLEAN);

  public MeowSlime(EntityType<? extends MeowSlime> type, Level level) {
    super(type, level);
    setWaiting(true);
  }

  @Override
  protected void defineSynchedData() {
    super.defineSynchedData();
    entityData.define(ACTIVE, true);
    entityData.define(ROAMING, false);
  }

  public boolean isActive() {
    return entityData.get(ACTIVE);
  }

  public void setActive(boolean value) {
    entityData.set(ACTIVE, value);
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
        new net.minecraft.world.entity.ai.goal.Goal() {
          {
            setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
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
          }

          @Override
          public void tick() {
            if (tickCount % 40 != 0 || !getNavigation().isDone()) return;
            for (int attempt = 0; attempt < 16; attempt++) {
              var pos =
                  blockPosition()
                      .offset(
                          random.nextInt(13) - 6, random.nextInt(5) - 2, random.nextInt(13) - 6);
              if (!level().hasChunkAt(pos) || !level().hasChunkAt(pos.offset(1, 1, 1))) continue;
              if (!level()
                      .getBlockState(pos.below())
                      .isFaceSturdy(level(), pos.below(), net.minecraft.core.Direction.UP)
                  || !level().getFluidState(pos).isEmpty()) continue;
              var path = getNavigation().createPath(pos, 0);
              if (path != null && path.canReach()) {
                getNavigation().moveTo(path, 1);
                return;
              }
            }
          }
        });
  }

  @SubscribeEvent
  public static void avoidCreepers(EntityJoinLevelEvent event) {
    if (!event.getLevel().isClientSide && event.getEntity() instanceof Creeper creeper)
      creeper.goalSelector.addGoal(
          3,
          new AvoidEntityGoal<>(
              creeper,
              MeowSlime.class,
              slime -> slime instanceof MeowSlime cat && cat.isActive() && cat.isTame(),
              6,
              1,
              1.2,
              slime -> true) {
            @Override
            public boolean canContinueToUse() {
              return toAvoid != null && toAvoid.isActive() && super.canContinueToUse();
            }
          });
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.MEOW_SLIME.get());
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putBoolean("MeowActive", isActive());
    tag.putBoolean("MeowRoaming", isRoaming());
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    setActive(!tag.contains("MeowActive") || tag.getBoolean("MeowActive"));
    entityData.set(ROAMING, tag.getBoolean("MeowRoaming") && !isOrderedToSit());
    if (!tag.contains("MeowActive")) setWaiting(true);
  }
}
