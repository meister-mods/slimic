package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.config.SlimicConfig;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.EnumSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class HeraldSlime extends FriendlySlime {
  public static final String SIGNAL_MARKER = "SlimicSignal";
  private ItemStack firework = defaultFirework();
  private int delayMinutes;
  private boolean monitoring = true;
  private CompoundTag warned = new CompoundTag();
  private int signalCooldown;

  public HeraldSlime(EntityType<? extends HeraldSlime> type, Level level) {
    super(type, level);
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
  protected void registerGoals() {
    super.registerGoals();
    goalSelector.addGoal(1, new WatchGoal());
  }

  private void signal() {
    double range = SlimeStat.DETECTION_RANGE.value(this);
    for (FriendlySlime worker :
        level().getEntitiesOfClass(FriendlySlime.class, getBoundingBox().inflate(range))) {
      if (worker == this
          || !worker.isAlive()
          || !worker.isWorking()
          || !worker.isWorkBlocked()
          || getOwnerUUID() == null
          || !getOwnerUUID().equals(worker.getOwnerUUID())
          || distanceToSqr(worker) > range * range
          || worker.getBlockedWorkTicks() < getDelayMinutes() * 1200) continue;
      String key = worker.getUUID().toString();
      if (warned.contains(key) && warned.getLong(key) == worker.getBlockedEpisode()) continue;
      FireworkRocketEntity rocket =
          new FireworkRocketEntity(level(), this, getX(), getEyeY() + 0.3, getZ(), firework.copy());
      rocket.getPersistentData().putBoolean(SIGNAL_MARKER, true);
      if (!level().addFreshEntity(rocket)) return;
      if (warned.getAllKeys().size() >= 128) warned.remove(warned.getAllKeys().iterator().next());
      warned.putLong(key, worker.getBlockedEpisode());
      signalCooldown = 60;
      return;
    }
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.put("HeraldFirework", firework.save(new CompoundTag()));
    tag.putInt("HeraldDelayMinutes", getDelayMinutes());
    tag.putBoolean("HeraldMonitoring", monitoring);
    tag.put("HeraldWarnings", warned.copy());
    tag.putInt("HeraldCooldown", signalCooldown);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    firework = defaultFirework();
    setFirework(ItemStack.of(tag.getCompound("HeraldFirework")));
    delayMinutes =
        tag.contains("HeraldDelayMinutes")
            ? Math.max(1, Math.min(60, tag.getInt("HeraldDelayMinutes")))
            : 0;
    monitoring = !tag.contains("HeraldMonitoring") || tag.getBoolean("HeraldMonitoring");
    warned = new CompoundTag();
    CompoundTag saved = tag.getCompound("HeraldWarnings");
    saved.getAllKeys().stream().limit(128).forEach(key -> warned.putLong(key, saved.getLong(key)));
    signalCooldown = Math.max(0, Math.min(60, tag.getInt("HeraldCooldown")));
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
      if (signalCooldown > 0) signalCooldown--;
      else if (tickCount % 20 == 0) signal();
    }
  }
}
