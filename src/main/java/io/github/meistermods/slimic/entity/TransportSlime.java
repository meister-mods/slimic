package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.config.SlimicConfig;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

public class TransportSlime extends BagSlime {
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

  private boolean working;
  private boolean resting;
  private boolean travelBlocked;
  private BlockPos home;
  private Status status = Status.IDLE;
  private Job job;
  private BagSlime workerTarget;
  private WorkStorage.Target storageTarget;
  private int travelTicks;
  private Vec3 lastTravelPosition;
  private Vec3 pathDestination;
  private int cooldown;
  private int nextSearchTick;
  private boolean searchStarted;
  private final Map<WorkStorage.Target, Long> blockedChests = new HashMap<>();
  private final Map<UUID, Long> blockedWorkers = new HashMap<>();

  public TransportSlime(EntityType<? extends TransportSlime> type, Level level) {
    this(type, level, 54);
  }

  protected TransportSlime(EntityType<? extends TransportSlime> type, Level level, int slots) {
    super(type, level, slots);
  }

  protected boolean storesCargo() {
    return false;
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
    if (!value) travelBlocked = false;
    setWaiting(false);
    clearJob();
    searchStarted = false;
    status = Status.IDLE;
  }

  public Status getTransportStatus() {
    if (isOrderedToSit()) return Status.WAITING;
    return working ? status : Status.FOLLOWING;
  }

  @Override
  protected WorkStatus describeWork() {
    var order = storesCargo() ? WorkStatus.Order.STORE : WorkStatus.Order.HAUL;
    return switch (status) {
      case NO_CHEST -> WorkStatus.blocked(order, WorkStatus.Reason.NO_STORAGE);
      case CHESTS_FULL -> WorkStatus.blocked(order, WorkStatus.Reason.STORAGE_FULL);
      case RESTING -> WorkStatus.blocked(order, WorkStatus.Reason.EXHAUSTED);
      case UNREACHABLE -> WorkStatus.blocked(order, WorkStatus.Reason.UNREACHABLE);
      default ->
          travelBlocked
              ? WorkStatus.blocked(order, WorkStatus.Reason.UNREACHABLE)
              : WorkStatus.of(
                  order, status == Status.IDLE ? WorkStatus.State.IDLE : WorkStatus.State.ACTIVE);
    };
  }

  public BlockPos getWorkOrigin() {
    return home == null ? blockPosition() : home;
  }

  @Override
  public void onReleased() {
    super.onReleased();
    home = blockPosition();
    clearJob();
    searchStarted = false;
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
              || status == Status.UNREACHABLE)) recoverStamina(getStaminaRecoveryPerSecond());
    }
  }

  private void clearJob() {
    job = null;
    workerTarget = null;
    storageTarget = null;
    travelTicks = 0;
    lastTravelPosition = null;
    pathDestination = null;
    getNavigation().stop();
  }

  private boolean supported(BagSlime worker) {
    double range = SlimeStat.DETECTION_RANGE.value(this);
    return worker != this
        && worker.isAlive()
        && !worker.isRemoved()
        && worker.level() == level()
        && getOwnerUUID() != null
        && canCooperateWith(worker)
        && worker.position().distanceToSqr(Vec3.atCenterOf(getWorkOrigin())) <= range * range;
  }

  private List<BagSlime> workers() {
    var candidates = new LinkedHashSet<BagSlime>();
    for (var chunk : workChunks())
      candidates.addAll(WorkSearch.inChunk((ServerLevel) level(), chunk).workers());
    return candidates.stream()
        .filter(worker -> supported(worker) && !blockedWorkers.containsKey(worker.getUUID()))
        .filter(worker -> !worker.getWorkStatus().requests().isEmpty())
        .sorted(Comparator.comparingDouble(this::distanceToSqr))
        .toList();
  }

  private List<WorkStorage.Target> storages() {
    return WorkStorage.find(this, getWorkOrigin()).stream()
        .filter(target -> !blockedChests.containsKey(target))
        .toList();
  }

  private List<ChunkPos> workChunks() {
    BlockPos center = getWorkOrigin();
    int radius = (int) Math.ceil(SlimeStat.DETECTION_RANGE.value(this));
    List<ChunkPos> chunks = new ArrayList<>();
    for (int x = (center.getX() - radius) >> 4; x <= (center.getX() + radius) >> 4; x++)
      for (int z = (center.getZ() - radius) >> 4; z <= (center.getZ() + radius) >> 4; z++)
        chunks.add(new ChunkPos(x, z));
    return chunks;
  }

  private IItemHandler targetInventory() {
    return storageTarget == null ? null : storageTarget.inventory(this, getWorkOrigin());
  }

  private boolean storageFull() {
    for (int slot = 0; slot < getCapacity(); slot++) {
      ItemStack stack = getBag().getStackInSlot(slot);
      if (stack.isEmpty()
          || stack.getCount() < Math.min(stack.getMaxStackSize(), getBag().getSlotLimit(slot)))
        return false;
    }
    return true;
  }

  private static int toolSlot(IItemHandler inventory, BagSlime worker) {
    for (int slot = 0; slot < inventory.getSlots(); slot++)
      if (worker.acceptsWorkTool(inventory.getStackInSlot(slot))) return slot;
    return -1;
  }

  private int supplySlot(IItemHandler inventory, BagSlime worker) {
    for (int slot = 0; slot < inventory.getSlots(); slot++) {
      ItemStack stack = inventory.getStackInSlot(slot);
      if (!worker.acceptsWorkTool(stack)) continue;
      ItemStack offered = stack.copyWithCount(Math.min(worker.workSupplyLimit(), stack.getCount()));
      if (ItemHandlerHelper.insertItemStacked(getBag(), offered.copy(), true).getCount()
          < offered.getCount()) return slot;
    }
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
    List<WorkStorage.Target> chests = storesCargo() ? List.of() : storages();
    if (hasCargo()) {
      for (BagSlime worker : workers) {
        if (worker.needsWorkSupply() && toolSlot(getBag(), worker) >= 0) {
          workerTarget = worker;
          job = Job.SUPPLY_TOOL;
          status = Status.SUPPLYING;
          return;
        }
      }
      for (WorkStorage.Target chest : chests) {
        if (canAcceptAny(chest.inventory(this, getWorkOrigin()), getBag())) {
          storageTarget = chest;
          job = Job.DEPOSIT;
          status = Status.DELIVERING;
          return;
        }
      }
      if (!storesCargo()) {
        status = chests.isEmpty() ? Status.NO_CHEST : Status.CHESTS_FULL;
        return;
      }
    }
    if (storesCargo() && storageFull()) {
      status = Status.CHESTS_FULL;
      return;
    }
    if (storesCargo() && workers.stream().anyMatch(BagSlime::needsWorkSupply)) chests = storages();
    for (BagSlime worker : workers) {
      if (!worker.needsWorkSupply()) continue;
      for (WorkStorage.Target chest : chests) {
        if (supplySlot(chest.inventory(this, getWorkOrigin()), worker) >= 0) {
          workerTarget = worker;
          storageTarget = chest;
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
      if (canAcceptAny(getBag(), worker.getBag())
          && (storesCargo()
              || chests.stream()
                  .anyMatch(
                      chest ->
                          canAcceptAny(chest.inventory(this, getWorkOrigin()), worker.getBag())))) {
        workerTarget = worker;
        job = Job.COLLECT;
        status = Status.COLLECTING;
        return;
      }
    }
    status =
        storesCargo()
            ? Status.IDLE
            : chests.isEmpty() ? Status.NO_CHEST : fullWorker ? Status.CHESTS_FULL : Status.IDLE;
    if (status == Status.IDLE) travelBlocked = false;
  }

  private boolean validJob() {
    if (job == null) return false;
    return switch (job) {
      case COLLECT ->
          workerTarget != null
              && supported(workerTarget)
              && workerTarget.needsUnloading()
              && canAcceptAny(getBag(), workerTarget.getBag());
      case SUPPLY_TOOL ->
          workerTarget != null
              && supported(workerTarget)
              && workerTarget.needsWorkSupply()
              && toolSlot(getBag(), workerTarget) >= 0;
      case FETCH_TOOL ->
          workerTarget != null
              && supported(workerTarget)
              && workerTarget.needsWorkSupply()
              && targetInventory() != null
              && supplySlot(targetInventory(), workerTarget) >= 0;
      case DEPOSIT ->
          hasCargo() && targetInventory() != null && canAcceptAny(targetInventory(), getBag());
    };
  }

  private void performJob() {
    int actions = 0;
    switch (job) {
      case COLLECT -> {
        for (int slot = 0; slot < workerTarget.getBag().getSlots() && getStamina() >= 1; slot++)
          if (transfer(workerTarget.getBag(), slot, getBag(), 64) > 0) {
            spendStamina(1);
            if (storesCargo()) recordCompletedWork(SlimicConfig.TRANSPORT_ACTIVITY.get());
            actions++;
          }
      }
      case DEPOSIT -> {
        IItemHandler destination = targetInventory();
        for (int slot = 0; slot < getBag().getSlots() && getStamina() >= 1; slot++)
          if (transfer(getBag(), slot, destination, 64) > 0) {
            spendStamina(1);
            recordCompletedWork(SlimicConfig.TRANSPORT_ACTIVITY.get());
            actions++;
          }
      }
      case FETCH_TOOL -> {
        IItemHandler source = targetInventory();
        if (transfer(
                source, supplySlot(source, workerTarget), getBag(), workerTarget.workSupplyLimit())
            > 0) {
          spendStamina(1);
          actions++;
        }
      }
      case SUPPLY_TOOL -> {
        int slot = toolSlot(getBag(), workerTarget);
        int accepted =
            workerTarget.receiveWorkSupply(
                getBag()
                    .getStackInSlot(slot)
                    .copyWithCount(
                        Math.min(
                            workerTarget.workSupplyLimit(),
                            getBag().getStackInSlot(slot).getCount())));
        if (accepted > 0) {
          getBag().extractItem(slot, accepted, false);
          spendStamina(1);
          recordCompletedWork(SlimicConfig.TRANSPORT_ACTIVITY.get());
          actions++;
        }
      }
    }
    clearJob();
    cooldown = Math.max(1, (int) Math.ceil(20.0 / SlimeStat.WORK_SPEED.value(this)));
    nextSearchTick = 0;
    status = actions > 0 ? Status.IDLE : Status.UNREACHABLE;
    if (actions > 0) travelBlocked = false;
  }

  private boolean inReach(Vec3 destination) {
    double reach = SlimeStat.REACH.value(this);
    if (getEyePosition().distanceToSqr(destination) > reach * reach) return false;
    if (job == Job.COLLECT || job == Job.SUPPLY_TOOL) return hasLineOfSight(workerTarget);
    if (storageTarget != null && storageTarget.warehouse() != null)
      return hasLineOfSight(storageTarget.entity((ServerLevel) level()));
    var hit =
        LoadedBlockView.clip(
            level(),
            new ClipContext(
                getEyePosition(),
                destination,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this));
    return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(storageTarget.chest());
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putBoolean("TransportWorking", working);
    tag.putBoolean("TransportResting", resting);
    tag.putBoolean("TransportTravelBlocked", travelBlocked);
    tag.putInt("TransportStatus", status.ordinal());
    tag.putInt("TransportCooldown", cooldown);
    if (home != null) tag.putLong("TransportHome", home.asLong());
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    working = tag.getBoolean("TransportWorking");
    if (!tag.contains("TransportWorking")) setWaiting(true);
    resting = tag.getBoolean("TransportResting");
    cooldown = Math.max(0, Math.min(200, tag.getInt("TransportCooldown")));
    travelBlocked = tag.getBoolean("TransportTravelBlocked");
    int savedStatus = tag.getInt("TransportStatus");
    status =
        savedStatus >= 0 && savedStatus < Status.values().length
            ? Status.values()[savedStatus]
            : Status.IDLE;
    home = tag.contains("TransportHome") ? BlockPos.of(tag.getLong("TransportHome")) : null;
    clearJob();
    searchStarted = false;
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
      if (!canUse()) {
        stop();
        return;
      }
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
        if (!searchStarted) {
          nextSearchTick = tickCount + Math.floorMod(getId(), 20);
          searchStarted = true;
        }
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
              : storageTarget.position(server);
      if (inReach(destination)) {
        getNavigation().stop();
        performJob();
        return;
      }
      getLookControl().setLookAt(destination);
      // Ground navigation cannot replace a route in midair. Preserve useful paths
      // and refresh only when they finish or a moving worker leaves the endpoint.
      if (tickCount % 10 == 0
          && onGround()
          && (getNavigation().isDone()
              || pathDestination == null
              || pathDestination.distanceToSqr(destination) > 1)) {
        getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0);
        pathDestination = destination;
      }
      if (lastTravelPosition == null
          || position().subtract(lastTravelPosition).horizontalDistanceSqr() >= .0025
          || (onGround() && Math.abs(getY() - lastTravelPosition.y) >= .5)) {
        lastTravelPosition = position();
        travelTicks = 0;
      } else travelTicks++;
      if (travelTicks > 400) {
        travelBlocked = true;
        if (job == Job.DEPOSIT || job == Job.FETCH_TOOL)
          blockedChests.put(storageTarget, server.getGameTime() + 200);
        else blockedWorkers.put(workerTarget.getUUID(), server.getGameTime() + 200);
        clearJob();
        status = Status.UNREACHABLE;
      }
    }
  }
}
