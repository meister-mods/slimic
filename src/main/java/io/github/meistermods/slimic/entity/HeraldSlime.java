package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.config.SlimicConfig;
import io.github.meistermods.slimic.item.CreativeSlimeTunerItem;
import io.github.meistermods.slimic.item.SlimeTunerItem;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class HeraldSlime extends FriendlySlime {
  public static final String SIGNAL_MARKER = "SlimicSignal";
  private ItemStack firework = defaultFirework();
  private int delayMinutes;
  private boolean monitoring;
  private CompoundTag warned = new CompoundTag();
  private CompoundTag warningCooldowns = new CompoundTag();
  private int monitoringActivityTicks;
  private boolean watchingWorkers;

  public HeraldSlime(EntityType<? extends HeraldSlime> type, Level level) {
    super(type, level);
    setWaiting(true);
  }

  public static ItemStack defaultFirework() {
    ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
    CompoundTag pattern = rocket.getOrCreateTagElement("Fireworks");
    pattern.putByte("Flight", (byte) 1);
    CompoundTag explosion = new CompoundTag();
    explosion.putByte("Type", (byte) 0);
    explosion.putIntArray("Colors", new int[] {DyeColor.WHITE.getFireworkColor()});
    ListTag explosions = new ListTag();
    explosions.add(explosion);
    pattern.put("Explosions", explosions);
    return rocket;
  }

  public ItemStack getFirework() {
    return firework.copy();
  }

  public void setFirework(ItemStack stack) {
    if (stack.is(Items.FIREWORK_ROCKET)) firework = stack.copyWithCount(1);
  }

  private int manualCooldown;

  public boolean launchSignal() {
    if (level().isClientSide || !isAlive()) return false;
    var rocket =
        new FireworkRocketEntity(level(), this, getX(), getEyeY() + .3, getZ(), firework.copy());
    rocket.getPersistentData().putBoolean(SIGNAL_MARKER, true);
    return level().addFreshEntity(rocket);
  }

  public boolean launchManually() {
    if (manualCooldown > 0) return false;
    if (!launchSignal()) return false;
    manualCooldown = 10;
    return true;
  }

  @Override
  public void tick() {
    super.tick();
    if (!level().isClientSide && manualCooldown > 0) manualCooldown--;
  }

  @Override
  public InteractionResult mobInteract(Player player, InteractionHand hand) {
    var held = player.getItemInHand(hand);
    if (player.isShiftKeyDown()
        || held.getItem() instanceof SlimeTunerItem
        || held.getItem() instanceof CreativeSlimeTunerItem
        || held.getItem() instanceof DyeItem
        || held.is(Items.SLIME_BALL)
        || held.is(Items.WATER_BUCKET)) return super.mobInteract(player, hand);
    if (!player.getUUID().equals(getOwnerUUID())) return InteractionResult.FAIL;
    if (!level().isClientSide) {
      if (held.is(Items.FIREWORK_ROCKET)) {
        setFirework(held);
        if (!player.getAbilities().instabuild) held.shrink(1);
      } else launchManually();
    }
    return InteractionResult.sidedSuccess(level().isClientSide);
  }

  public int getDelayMinutes() {
    return delayMinutes == 0 ? SlimicConfig.HERALD_DELAY_MINUTES.get() : delayMinutes;
  }

  public void setDelayMinutes(int value) {
    delayMinutes = Math.max(1, Math.min(60, value));
  }

  @Override
  public boolean isWorking() {
    return monitoring && !isOrderedToSit();
  }

  public void setMonitoring(boolean value) {
    monitoring = value;
    setWaiting(false);
  }

  @Override
  protected WorkStatus describeWork() {
    return WorkStatus.of(
        WorkStatus.Order.MONITOR,
        watchingWorkers ? WorkStatus.State.ACTIVE : WorkStatus.State.IDLE);
  }

  @Override
  protected void registerGoals() {
    super.registerGoals();
    goalSelector.addGoal(1, new WatchGoal());
  }

  private void watchWorkers() {
    double range = SlimeStat.DETECTION_RANGE.value(this);
    watchingWorkers = false;
    for (FriendlySlime worker :
        level().getEntitiesOfClass(FriendlySlime.class, getBoundingBox().inflate(range))) {
      if (!isWorking()) break;
      if (worker == this
          || !worker.isAlive()
          || !worker.isWorking()
          || getOwnerUUID() == null
          || !getOwnerUUID().equals(worker.getOwnerUUID())
          || distanceToSqr(worker) > range * range) continue;
      WorkStatus state = worker.getWorkStatus();
      if (!(worker instanceof BagSlime)
          || state.state() == WorkStatus.State.COMPLETE
          || state.state() == WorkStatus.State.IDLE) continue;
      watchingWorkers = true;
      if (state.state() != WorkStatus.State.BLOCKED
          || worker.getBlockedWorkTicks() < getDelayMinutes() * 1200) continue;
      String key = worker.getUUID().toString();
      if (warned.contains(key)
          && warned.getLong(key) == worker.getBlockedEpisode()
          && warningCooldowns.getInt(key) > 0) continue;
      if (!launchSignal()) continue;
      warned.putLong(key, worker.getBlockedEpisode());
      warningCooldowns.putInt(key, 200);
    }
    watchingWorkers &= isWorking();
    if (watchingWorkers) {
      monitoringActivityTicks += 20;
      if (monitoringActivityTicks >= SlimicConfig.MONITOR_ACTIVITY_INTERVAL.get()) {
        monitoringActivityTicks -= SlimicConfig.MONITOR_ACTIVITY_INTERVAL.get();
        recordCompletedWork();
      }
    }
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putInt("HeraldManualCooldown", manualCooldown);
    tag.put("HeraldFirework", firework.save(new CompoundTag()));
    tag.putInt("HeraldDelayMinutes", getDelayMinutes());
    tag.putBoolean("HeraldMonitoring", monitoring);
    tag.put("HeraldWarnings", warned.copy());
    tag.put("HeraldWarningCooldowns", warningCooldowns.copy());
    tag.putInt("HeraldMonitoringActivityTicks", monitoringActivityTicks);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    manualCooldown = Math.max(0, Math.min(10, tag.getInt("HeraldManualCooldown")));
    firework = defaultFirework();
    setFirework(ItemStack.of(tag.getCompound("HeraldFirework")));
    delayMinutes =
        tag.contains("HeraldDelayMinutes")
            ? Math.max(1, Math.min(60, tag.getInt("HeraldDelayMinutes")))
            : 0;
    monitoring = tag.getBoolean("HeraldMonitoring");
    if (!tag.contains("HeraldMonitoring")) setWaiting(true);
    warned = new CompoundTag();
    CompoundTag saved = tag.getCompound("HeraldWarnings");
    for (String key : saved.getAllKeys()) {
      if (!saved.contains(key, Tag.TAG_ANY_NUMERIC) || saved.getLong(key) < 0) continue;
      try {
        String identity = UUID.fromString(key).toString();
        warned.putLong(identity, Math.max(warned.getLong(identity), saved.getLong(key)));
      } catch (IllegalArgumentException ignored) {
        // Warning records always identify a worker; ignore malformed legacy keys.
      }
    }
    warningCooldowns = new CompoundTag();
    for (String key : warned.getAllKeys())
      warningCooldowns.putInt(
          key,
          Math.max(
              0,
              Math.min(
                  200,
                  tag.getCompound("HeraldWarningCooldowns").contains(key)
                      ? tag.getCompound("HeraldWarningCooldowns").getInt(key)
                      : tag.getInt("HeraldCooldown"))));
    monitoringActivityTicks =
        Math.max(
            0,
            Math.min(
                SlimicConfig.MONITOR_ACTIVITY_INTERVAL.get() - 1,
                tag.getInt("HeraldMonitoringActivityTicks")));
    watchingWorkers = false;
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.HERALD_SLIME.get());
  }

  private final class WatchGoal extends Goal {
    WatchGoal() {
      setFlags(EnumSet.of(Flag.MOVE));
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
    public void start() {
      getNavigation().stop();
    }

    @Override
    public void tick() {
      if (!canUse()) {
        stop();
        return;
      }
      for (String key : warningCooldowns.getAllKeys())
        if (warningCooldowns.getInt(key) > 0)
          warningCooldowns.putInt(key, warningCooldowns.getInt(key) - 1);
      if (tickCount % 20 == 0) watchWorkers();
    }
  }
}
