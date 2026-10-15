package io.github.meistermods.slimic.entity;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.block.SlimeChestBlockEntity;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

public final class TransportSlime extends BagSlime {
  public enum Status {
    IDLE,
    COLLECTING,
    DELIVERING,
    FETCHING_TOOL,
    SUPPLYING,
    NO_CHEST,
    CHESTS_FULL,
    RESTING,
    WAITING,
    FOLLOWING,
    UNREACHABLE
  }

  private enum Job {
    COLLECT,
    DEPOSIT,
    FETCH_TOOL,
    SUPPLY_TOOL
  }

  private boolean working = true;
  private boolean resting;
  private BlockPos home;
  private Status status = Status.IDLE;
  private Job job;
  private BagSlime workerTarget;
  private BlockPos chestTarget;
  private int travelTicks;
  private int cooldown;
  private int nextSearchTick;
  private final Map<BlockPos, Long> blockedChests = new HashMap<>();
  private final Map<UUID, Long> blockedWorkers = new HashMap<>();

  public TransportSlime(EntityType<? extends TransportSlime> type, Level level) {
    super(type, level, 54);
  }

  @Override
  public int capacityMultiplier() {
    return 2;
  }

  @Override
  protected String bagTagName() {
    return "TransportBag";
  }

  @Override
  public boolean isWorking() {
    return working && !isOrderedToSit();
  }

  @Override
  public void setWorking(boolean value) {
    working = value;
    setWaiting(false);
    clearJob();
    status = Status.IDLE;
  }

  public Status getTransportStatus() {
    if (isOrderedToSit()) return Status.WAITING;
    return working ? status : Status.FOLLOWING;
  }

  public BlockPos getWorkOrigin() {
    return home == null ? blockPosition() : home;
  }

  @Override
  public void onReleased() {
    home = blockPosition();
    clearJob();
    blockedChests.clear();
    blockedWorkers.clear();
  }

  @Override
  protected void registerGoals() {
    super.registerGoals();
    goalSelector.addGoal(1, new TransportGoal());
  }

  @Override
  public void tick() {
    super.tick();
    if (!level().isClientSide) {
      if (home == null) onReleased();
      if (isWorking()
          && tickCount % 20 == 0
          && (status == Status.IDLE
              || status == Status.NO_CHEST
              || status == Status.CHESTS_FULL
              || status == Status.RESTING
              || status == Status.UNREACHABLE)) recoverStamina(2);
    }
  }

  private void clearJob() {
    job = null;
    workerTarget = null;
    chestTarget = null;
    travelTicks = 0;
    getNavigation().stop();
  }

  private boolean supported(BagSlime worker) {
    double range = SlimeStat.DETECTION_RANGE.value(this);
    return worker != this
        && worker.isAlive()
        && !worker.isRemoved()
        && worker.level() == level()
        && getOwnerUUID() != null
        && getOwnerUUID().equals(worker.getOwnerUUID())
        && worker.position().distanceToSqr(Vec3.atCenterOf(getWorkOrigin())) <= range * range;
  }

  private List<BagSlime> workers() {
    double range = SlimeStat.DETECTION_RANGE.value(this);
    return level()
        .getEntitiesOfClass(
            BagSlime.class,
            new AABB(getWorkOrigin()).inflate(range),
            worker -> supported(worker) && !blockedWorkers.containsKey(worker.getUUID()))
        .stream()
        .sorted(Comparator.comparingDouble(this::distanceToSqr))
        .toList();
  }

  private boolean available(SlimeChestBlockEntity chest) {
    if (chest.isRemoved() || getOwnerUUID() == null || !(level() instanceof ServerLevel server))
      return false;
    return chest.canOpen(
        FakePlayerFactory.get(server, new GameProfile(getOwnerUUID(), "SlimeWorker")));
  }

  private List<SlimeChestBlockEntity> chests(ServerLevel server) {
    List<SlimeChestBlockEntity> result = new ArrayList<>();
    BlockPos center = getWorkOrigin();
    double range = SlimeStat.DETECTION_RANGE.value(this);
    int radius = (int) Math.ceil(range);
    for (int x = (center.getX() - radius) >> 4; x <= (center.getX() + radius) >> 4; x++)
      for (int z = (center.getZ() - radius) >> 4; z <= (center.getZ() + radius) >> 4; z++) {
        var chunk = server.getChunkSource().getChunkNow(x, z);
        if (chunk == null) continue;
        for (var entity : chunk.getBlockEntities().values()) {
          if (entity instanceof SlimeChestBlockEntity chest
              && center.distSqr(chest.getBlockPos()) <= range * range
              && !blockedChests.containsKey(chest.getBlockPos())
              && available(chest)) result.add(chest);
        }
      }
    result.sort(
        Comparator.comparingDouble(chest -> chest.getBlockPos().distToCenterSqr(position())));
    return result;
  }

  private SlimeChestBlockEntity targetChest() {
    if (chestTarget == null || !level().hasChunkAt(chestTarget)) return null;
    return level().getBlockEntity(chestTarget) instanceof SlimeChestBlockEntity chest
            && available(chest)
        ? chest
        : null;
  }

  private static int toolSlot(IItemHandler inventory, BagSlime worker) {
    for (int slot = 0; slot < inventory.getSlots(); slot++)
      if (worker.acceptsWorkTool(inventory.getStackInSlot(slot))) return slot;
    return -1;
  }

  private static boolean canAcceptAny(IItemHandler destination, IItemHandler source) {
    for (int slot = 0; slot < source.getSlots(); slot++) {
      ItemStack stack = source.getStackInSlot(slot);
      if (!stack.isEmpty()
          && ItemHandlerHelper.insertItemStacked(destination, stack.copy(), true).getCount()
              < stack.getCount()) return true;
    }
    return false;
  }

  private static int transfer(IItemHandler source, int slot, IItemHandler destination, int limit) {
    ItemStack sample = source.extractItem(slot, limit, true);
    if (sample.isEmpty()) return 0;
    int moved =
        sample.getCount()
            - ItemHandlerHelper.insertItemStacked(destination, sample.copy(), true).getCount();
    if (moved == 0) return 0;
    ItemStack extracted = source.extractItem(slot, moved, false);
    ItemHandlerHelper.insertItemStacked(destination, extracted, false);
    return moved;
  }

  private void chooseJob(ServerLevel server) {
    long now = server.getGameTime();
    blockedChests.values().removeIf(until -> until <= now);
    blockedWorkers.values().removeIf(until -> until <= now);
    List<BagSlime> workers = workers();
    List<SlimeChestBlockEntity> chests = chests(server);
    if (hasCargo()) {
      for (BagSlime worker : workers) {
        if (worker.needsReplacementTool() && toolSlot(getBag(), worker) >= 0) {
          workerTarget = worker;
          job = Job.SUPPLY_TOOL;
          status = Status.SUPPLYING;
          return;
        }
      }
      for (SlimeChestBlockEntity chest : chests) {
        if (canAcceptAny(chest.inventory(), getBag())) {
          chestTarget = chest.getBlockPos();
          job = Job.DEPOSIT;
          status = Status.DELIVERING;
          return;
        }
      }
      status = chests.isEmpty() ? Status.NO_CHEST : Status.CHESTS_FULL;
      return;
    }
    for (BagSlime worker : workers) {
      if (!worker.needsReplacementTool()) continue;
      for (SlimeChestBlockEntity chest : chests) {
        if (toolSlot(chest.inventory(), worker) >= 0) {
          workerTarget = worker;
          chestTarget = chest.getBlockPos();
          job = Job.FETCH_TOOL;
          status = Status.FETCHING_TOOL;
          return;
        }
      }
    }
    boolean fullWorker = false;
    for (BagSlime worker : workers) {
      if (!worker.needsUnloading()) continue;
      fullWorker = true;
      if (chests.stream().anyMatch(chest -> canAcceptAny(chest.inventory(), worker.getBag()))) {
        workerTarget = worker;
        job = Job.COLLECT;
        status = Status.COLLECTING;
        return;
      }
    }
    status = chests.isEmpty() ? Status.NO_CHEST : fullWorker ? Status.CHESTS_FULL : Status.IDLE;
  }

  private boolean validJob() {
    if (job == null) return false;
    return switch (job) {
      case COLLECT ->
          workerTarget != null && supported(workerTarget) && workerTarget.needsUnloading();
      case SUPPLY_TOOL ->
          workerTarget != null
              && supported(workerTarget)
              && workerTarget.needsReplacementTool()
              && toolSlot(getBag(), workerTarget) >= 0;
      case FETCH_TOOL ->
          workerTarget != null
              && supported(workerTarget)
              && workerTarget.needsReplacementTool()
              && targetChest() != null
              && toolSlot(targetChest().inventory(), workerTarget) >= 0;
      case DEPOSIT ->
          hasCargo() && targetChest() != null && canAcceptAny(targetChest().inventory(), getBag());
    };
  }

  private void performJob() {
    int actions = 0;
    switch (job) {
      case COLLECT -> {
        for (int slot = 0; slot < workerTarget.getBag().getSlots() && getStamina() >= 1; slot++)
          if (transfer(workerTarget.getBag(), slot, getBag(), 64) > 0) {
            spendStamina(1);
            actions++;
          }
      }
      case DEPOSIT -> {
        IItemHandler destination = targetChest().inventory();
        for (int slot = 0; slot < getBag().getSlots() && getStamina() >= 1; slot++)
          if (transfer(getBag(), slot, destination, 64) > 0) {
            spendStamina(1);
            recordCompletedWork();
            actions++;
          }
      }
      case FETCH_TOOL -> {
        IItemHandler source = targetChest().inventory();
        if (transfer(source, toolSlot(source, workerTarget), getBag(), 1) > 0) {
          spendStamina(1);
          actions++;
        }
      }
      case SUPPLY_TOOL -> {
        int slot = toolSlot(getBag(), workerTarget);
        if (workerTarget.receiveWorkTool(getBag().getStackInSlot(slot))) {
          getBag().extractItem(slot, 1, false);
          spendStamina(1);
          recordCompletedWork();
          actions++;
        }
      }
    }
    clearJob();
    cooldown = Math.max(1, (int) Math.ceil(20.0 / SlimeStat.WORK_SPEED.value(this)));
    nextSearchTick = 0;
    status = actions > 0 ? Status.IDLE : Status.UNREACHABLE;
  }

  private boolean inReach(Vec3 destination) {
    double reach = SlimeStat.REACH.value(this);
    if (getEyePosition().distanceToSqr(destination) > reach * reach) return false;
    if (job == Job.COLLECT || job == Job.SUPPLY_TOOL) return hasLineOfSight(workerTarget);
    var hit =
        level()
            .clip(
                new ClipContext(
                    getEyePosition(),
                    destination,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    this));
    return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(chestTarget);
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putBoolean("TransportWorking", working);
    tag.putBoolean("TransportResting", resting);
    if (home != null) tag.putLong("TransportHome", home.asLong());
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    working = !tag.contains("TransportWorking") || tag.getBoolean("TransportWorking");
    resting = tag.getBoolean("TransportResting");
    home = tag.contains("TransportHome") ? BlockPos.of(tag.getLong("TransportHome")) : null;
    clearJob();
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.TRANSPORT_SLIME.get());
  }

  private final class TransportGoal extends Goal {
    TransportGoal() {
      setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
      return isWorking() && isTame() && getOwnerUUID() != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
      return true;
    }

    @Override
    public void stop() {
      clearJob();
      status = Status.IDLE;
    }

    @Override
    public void tick() {
      if (!(level() instanceof ServerLevel server)) return;
      if (getStamina() < 1) resting = true;
      if (resting) {
        if (getStamina() < getMaxStamina() * 0.25F) {
          clearJob();
          status = Status.RESTING;
          return;
        }
        resting = false;
      }
      if (cooldown > 0 && --cooldown > 0) return;
      if (job != null && !validJob()) clearJob();
      if (job == null) {
        if (tickCount < nextSearchTick) return;
        chooseJob(server);
        if (job == null) {
          nextSearchTick = tickCount + 20;
          return;
        }
      }
      Vec3 destination =
          job == Job.COLLECT || job == Job.SUPPLY_TOOL
              ? workerTarget.getBoundingBox().getCenter()
              : Vec3.atCenterOf(chestTarget);
      if (inReach(destination)) {
        getNavigation().stop();
        performJob();
        return;
      }
      getLookControl().setLookAt(destination);
      if (tickCount % 10 == 0)
        getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0);
      if (++travelTicks > 400) {
        if (job == Job.DEPOSIT || job == Job.FETCH_TOOL)
          blockedChests.put(chestTarget, server.getGameTime() + 200);
        else blockedWorkers.put(workerTarget.getUUID(), server.getGameTime() + 200);
        clearJob();
        status = Status.UNREACHABLE;
      }
    }
  }
}
