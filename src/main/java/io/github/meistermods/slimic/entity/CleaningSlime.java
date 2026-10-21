package io.github.meistermods.slimic.entity;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;

public final class CleaningSlime extends BagSlime {
  public enum Status {
    IDLE,
    COLLECTING,
    RETURNING,
    FULL,
    RESTING,
    UNREACHABLE,
    WAITING,
    FOLLOWING
  }

  public static final int RETRY_TICKS = 3600;
  private boolean working;
  private boolean resting;
  private boolean returning;
  private boolean travelBlocked;
  private Vec3 home;
  private UUID targetId;
  private UUID failedId;
  private int failedCooldown;
  private int attemptTicks;
  private int returnTicks;
  private Vec3 lastReturnPosition;
  private Status status = Status.IDLE;

  public CleaningSlime(EntityType<? extends CleaningSlime> type, Level level) {
    super(type, level, 27);
    setMaxUpStep(1);
  }

  @Override
  protected String bagTagName() {
    return "CleaningBag";
  }

  @Override
  public int detectionMultiplier() {
    return 5;
  }

  @Override
  public boolean isWorking() {
    return working && !isOrderedToSit();
  }

  @Override
  public void setWorking(boolean value) {
    working = value;
    setWaiting(false);
    targetId = null;
    attemptTicks = 0;
    returning = false;
    returnTicks = 0;
    lastReturnPosition = null;
    travelBlocked = false;
    getNavigation().stop();
    status = Status.IDLE;
  }

  @Override
  public void onReleased() {
    super.onReleased();
    home = position();
    getNavigation().stop();
  }

  public Vec3 getCleaningHome() {
    return home == null ? position() : home;
  }

  public UUID getPickupTarget() {
    return targetId;
  }

  public int getAttemptTicks() {
    return attemptTicks;
  }

  public Status getCleaningStatus() {
    return isOrderedToSit() ? Status.WAITING : working ? status : Status.FOLLOWING;
  }

  @Override
  protected WorkStatus describeWork() {
    var order = WorkStatus.Order.CLEAN;
    if (status == Status.FULL)
      return WorkStatus.blocked(
          order, WorkStatus.Reason.INVENTORY_FULL, WorkStatus.Request.CARGO_PICKUP);
    if (status == Status.RESTING) return WorkStatus.blocked(order, WorkStatus.Reason.EXHAUSTED);
    if (travelBlocked) return WorkStatus.blocked(order, WorkStatus.Reason.UNREACHABLE);
    return WorkStatus.of(
        order, status == Status.IDLE ? WorkStatus.State.IDLE : WorkStatus.State.ACTIVE);
  }

  @Override
  protected void registerGoals() {
    super.registerGoals();
    goalSelector.addGoal(1, new CleanGoal());
  }

  private boolean inArea(ItemEntity item) {
    double radius = SlimeStat.DETECTION_RANGE.value(this);
    return item.position().distanceToSqr(getCleaningHome()) <= radius * radius;
  }

  private boolean available(ItemEntity item) {
    if (!item.isAlive() || item.hasPickUpDelay() || item.getItem().isEmpty() || !inArea(item))
      return false;
    CompoundTag data = item.saveWithoutId(new CompoundTag());
    return !data.hasUUID("Owner")
        || item.lifespan - item.getAge() <= 200
        || CompanionOwnership.cooperates(level(), data.getUUID("Owner"), getOwnerUUID());
  }

  private boolean fits(ItemEntity item) {
    return SlimeBag.insertItemStacked(getBag(), item.getItem().copy(), true).getCount()
        < item.getItem().getCount();
  }

  private ItemEntity find(ServerLevel level, UUID exclude, boolean requireSpace) {
    double radius = SlimeStat.DETECTION_RANGE.value(this);
    return WorkSearch.entities(
            level,
            ItemEntity.class,
            new net.minecraft.world.phys.AABB(getCleaningHome(), getCleaningHome()).inflate(radius))
        .stream()
        .filter(this::available)
        .filter(item -> !item.getUUID().equals(exclude))
        .filter(item -> failedCooldown == 0 || !item.getUUID().equals(failedId))
        .filter(item -> !requireSpace || fits(item))
        .min(Comparator.comparingDouble(this::distanceToSqr))
        .orElse(null);
  }

  private boolean visible(ItemEntity item) {
    return LoadedBlockView.clip(
                level(),
                new net.minecraft.world.level.ClipContext(
                    getEyePosition(),
                    item.position().add(0, .1, 0),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    this))
            .getType()
        == HitResult.Type.MISS;
  }

  private boolean collect(ServerLevel level, ItemEntity item) {
    var player = FakePlayerFactory.get(level, new GameProfile(getOwnerUUID(), "SlimeCollector"));
    player.moveTo(getX(), getY(), getZ(), getYRot(), getXRot());
    if (ForgeEventFactory.onItemPickup(item, player) < 0
        || !canContinueWork(player)
        || !available(item)) return false;
    ItemStack before = item.getItem().copy();
    ItemStack left = SlimeBag.insertItemStacked(getBag(), before.copy(), false);
    int count = before.getCount() - left.getCount();
    if (count == 0) return false;
    take(item, count);
    item.setItem(left);
    ForgeEventFactory.firePlayerItemPickupEvent(player, item, before.copyWithCount(count));
    if (left.isEmpty()) item.discard();
    playSound(SoundEvents.ITEM_PICKUP, .3F, 1);
    spendStamina(1);
    recordCompletedWork();
    travelBlocked = false;
    return true;
  }

  private void beginReturn() {
    returning = true;
    returnTicks = 0;
    lastReturnPosition = null;
    travelBlocked = false;
  }

  @Override
  public void tick() {
    super.tick();
    if (!level().isClientSide) {
      if (home == null) home = position();
      if (isWorking() && tickCount % 20 == 0) recoverStamina(getStaminaRecoveryPerSecond());
      if (isWorking() && failedCooldown > 0) failedCooldown--;
    }
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.CLEANING_SLIME.get());
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putBoolean("CleaningWorking", working);
    tag.putBoolean("CleaningResting", resting);
    tag.putBoolean("CleaningReturning", returning);
    tag.putBoolean("CleaningTravelBlocked", travelBlocked);
    CompoundTag origin = new CompoundTag();
    Vec3 center = getCleaningHome();
    origin.putDouble("X", center.x);
    origin.putDouble("Y", center.y);
    origin.putDouble("Z", center.z);
    tag.put("CleaningHome", origin);
    if (targetId != null) tag.putUUID("CleaningTarget", targetId);
    if (failedId != null) tag.putUUID("CleaningFailed", failedId);
    tag.putInt("CleaningReturnTicks", returnTicks);
    tag.putInt("CleaningAttemptTicks", attemptTicks);
    tag.putInt("CleaningFailedCooldown", failedCooldown);
    tag.putInt("CleaningStatus", status.ordinal());
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    working = tag.getBoolean("CleaningWorking");
    if (!tag.contains("CleaningWorking")) setWaiting(true);
    resting = tag.getBoolean("CleaningResting");
    returning = tag.getBoolean("CleaningReturning");
    travelBlocked = tag.getBoolean("CleaningTravelBlocked");
    CompoundTag origin = tag.getCompound("CleaningHome");
    home =
        tag.contains("CleaningHome")
            ? new Vec3(origin.getDouble("X"), origin.getDouble("Y"), origin.getDouble("Z"))
            : null;
    if (home != null
        && (!Double.isFinite(home.x) || !Double.isFinite(home.y) || !Double.isFinite(home.z)))
      home = null;
    targetId = tag.hasUUID("CleaningTarget") ? tag.getUUID("CleaningTarget") : null;
    failedId = tag.hasUUID("CleaningFailed") ? tag.getUUID("CleaningFailed") : null;
    returnTicks = Math.max(0, Math.min(401, tag.getInt("CleaningReturnTicks")));
    lastReturnPosition = null;
    attemptTicks = Math.max(0, Math.min(RETRY_TICKS, tag.getInt("CleaningAttemptTicks")));
    failedCooldown = Math.max(0, Math.min(RETRY_TICKS, tag.getInt("CleaningFailedCooldown")));
    int saved = tag.getInt("CleaningStatus");
    status = saved >= 0 && saved < Status.values().length ? Status.values()[saved] : Status.IDLE;
  }

  private final class CleanGoal extends Goal {
    CleanGoal() {
      setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
      return isWorking() && isTame();
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
      if (!canUse()) {
        stop();
        return;
      }
      if (!(level() instanceof ServerLevel server)) return;
      if (home == null) home = position();
      if (returning || (targetId == null && position().distanceToSqr(home) > .0625)) {
        if (!returning) beginReturn();
        status = Status.RETURNING;
        if (position().distanceToSqr(home) <= .0625) {
          returning = false;
          returnTicks = 0;
          lastReturnPosition = null;
          travelBlocked = false;
          getNavigation().stop();
          getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0);
          setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
          status = Status.IDLE;
        } else {
          if (lastReturnPosition == null) lastReturnPosition = position();
          else if (position().subtract(lastReturnPosition).horizontalDistanceSqr() >= .0025
              || (onGround() && Math.abs(getY() - lastReturnPosition.y) >= .5)) {
            lastReturnPosition = position();
            returnTicks = 0;
            travelBlocked = false;
          } else returnTicks++;
          if (returnTicks > 400) {
            travelBlocked = true;
            status = Status.UNREACHABLE;
          }
          if (position().distanceToSqr(home) < 9
              && level()
                  .noCollision(
                      CleaningSlime.this,
                      getBoundingBox().expandTowards(home.subtract(position())))) {
            // Finish at the actual release point rather than the wide-mob path node center.
            getNavigation().stop();
            getMoveControl().setWantedPosition(home.x, home.y, home.z, .75);
          } else if (tickCount % 10 == 0 && onGround() && getNavigation().isDone())
            getNavigation()
                .moveTo(
                    getNavigation().createPath(net.minecraft.core.BlockPos.containing(home), 0), 1);
        }
        return;
      }
      if (getStamina() < 1) resting = true;
      if (resting && getStamina() < getMaxStamina() * .25F) {
        status = Status.RESTING;
        getNavigation().stop();
        return;
      }
      resting = false;
      ItemEntity target =
          targetId != null
                  && server.getEntity(targetId) instanceof ItemEntity item
                  && available(item)
              ? item
              : null;
      if (target == null) {
        targetId = null;
        attemptTicks = 0;
        if (tickCount
                % Math.max(1, (int) Math.ceil(20 / SlimeStat.WORK_SPEED.value(CleaningSlime.this)))
            != 0) return;
        target = find(server, null, true);
        if (target == null) {
          status = find(server, null, false) != null ? Status.FULL : Status.IDLE;
          if (status == Status.IDLE) travelBlocked = false;
          getNavigation().stop();
          return;
        }
        targetId = target.getUUID();
      }
      if (!fits(target)) {
        status = Status.FULL;
        targetId = null;
        beginReturn();
        getNavigation().stop();
        return;
      }
      if (++attemptTicks >= RETRY_TICKS) {
        ItemEntity alternative = find(server, target.getUUID(), true);
        attemptTicks = 0;
        if (alternative != null) {
          failedId = target.getUUID();
          failedCooldown = RETRY_TICKS;
          target = alternative;
          targetId = alternative.getUUID();
          getNavigation().stop();
        }
      }
      double reach = SlimeStat.REACH.value(CleaningSlime.this);
      if (getEyePosition().distanceToSqr(target.position().add(0, .1, 0)) <= reach * reach
          && visible(target)) {
        boolean collected = collect(server, target);
        if (!canUse()) {
          stop();
          return;
        }
        if (collected) {
          targetId = null;
          attemptTicks = 0;
          beginReturn();
          status = Status.RETURNING;
          getNavigation().stop();
          return;
        }
      }
      status = travelBlocked ? Status.UNREACHABLE : Status.COLLECTING;
      if (tickCount % 10 == 0 && !getNavigation().moveTo(target, 1)) travelBlocked = true;
      if (getNavigation().isDone() && distanceToSqr(target) < 16 && visible(target))
        getMoveControl().setWantedPosition(target.getX(), getY(), target.getZ(), .75);
    }
  }
}
