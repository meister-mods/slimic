package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.config.SlimicConfig;
import io.github.meistermods.slimic.registry.ModItems;
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
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;

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

  protected enum Job {
    COLLECT,
    DEPOSIT,
    FETCH_TOOL,
    SUPPLY_TOOL
  }

  private boolean working;
  private BlockPos home;
  protected final TransportTask primaryTask = new TransportTask(this);

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
    if (!value) primaryTask.travelBlocked = false;
    setWaiting(false);
    primaryTask.clearJob();
    primaryTask.searchStarted = false;
    primaryTask.status = Status.IDLE;
  }

  @Override
  public void setWaiting(boolean value) {
    super.setWaiting(value);
    if (value && primaryTask != null) primaryTask.clearJob();
  }

  public Status getTransportStatus() {
    if (isOrderedToSit()) return Status.WAITING;
    return working ? primaryTask.status : Status.FOLLOWING;
  }

  @Override
  protected WorkStatus describeWork() {
    var order = storesCargo() ? WorkStatus.Order.STORE : WorkStatus.Order.HAUL;
    return switch (getTransportStatus()) {
      case NO_CHEST -> WorkStatus.blocked(order, WorkStatus.Reason.NO_STORAGE);
      case CHESTS_FULL -> WorkStatus.blocked(order, WorkStatus.Reason.STORAGE_FULL);
      case RESTING -> WorkStatus.blocked(order, WorkStatus.Reason.EXHAUSTED);
      case UNREACHABLE -> WorkStatus.blocked(order, WorkStatus.Reason.UNREACHABLE);
      default ->
          primaryTask.travelBlocked
              ? WorkStatus.blocked(order, WorkStatus.Reason.UNREACHABLE)
              : WorkStatus.of(
                  order,
                  getTransportStatus() == Status.IDLE
                      ? WorkStatus.State.IDLE
                      : WorkStatus.State.ACTIVE);
    };
  }

  public BlockPos getWorkOrigin() {
    return home == null ? blockPosition() : home;
  }

  @Override
  public void onReleased() {
    super.onReleased();
    home = blockPosition();
    primaryTask.reset();
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
      Status status = getTransportStatus();
      if (isWorking()
          && tickCount % 20 == 0
          && (status == Status.IDLE
              || status == Status.NO_CHEST
              || status == Status.CHESTS_FULL
              || status == Status.RESTING
              || status == Status.UNREACHABLE)) recoverStamina(getStaminaRecoveryPerSecond());
    }
  }

  protected boolean sharesTransportTasks() {
    return false;
  }

  protected boolean permitsJob(
      TransportTask task, Job job, BagSlime worker, WorkStorage.Target storage) {
    return true;
  }

  protected boolean permitsDeposit(TransportTask task, ItemStack stack) {
    return true;
  }

  protected int availableSupply(TransportTask task, IItemHandler inventory, ItemStack stack) {
    return stack.getCount();
  }

  protected boolean canTransportAct(TransportTask task) {
    return isWorking()
        && isTame()
        && getOwnerUUID() != null
        && isAlive()
        && !isRemoved()
        && !isPendingSpawn()
        && task.body.isAlive()
        && !task.body.isRemoved()
        && task.body.level() == level();
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putBoolean("TransportWorking", working);
    tag.putBoolean("TransportResting", primaryTask.resting);
    tag.putBoolean("TransportTravelBlocked", primaryTask.travelBlocked);
    tag.putInt("TransportStatus", primaryTask.status.ordinal());
    tag.putInt("TransportCooldown", primaryTask.cooldown);
    if (home != null) tag.putLong("TransportHome", home.asLong());
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    working = tag.getBoolean("TransportWorking");
    if (!tag.contains("TransportWorking")) setWaiting(true);
    primaryTask.resting = tag.getBoolean("TransportResting");
    primaryTask.cooldown = Math.max(0, Math.min(200, tag.getInt("TransportCooldown")));
    primaryTask.travelBlocked = tag.getBoolean("TransportTravelBlocked");
    int savedStatus = tag.getInt("TransportStatus");
    primaryTask.status =
        savedStatus >= 0 && savedStatus < Status.values().length
            ? Status.values()[savedStatus]
            : Status.IDLE;
    home = tag.contains("TransportHome") ? BlockPos.of(tag.getLong("TransportHome")) : null;
    primaryTask.clearJob();
    primaryTask.searchStarted = false;
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
      return primaryTask != null && primaryTask.canAct();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
      return true;
    }

    @Override
    public void stop() {
      primaryTask.clearJob();
      primaryTask.status = Status.IDLE;
    }

    @Override
    public void tick() {
      primaryTask.tick();
    }
  }

  /** One movement task; cargo, permission, stamina and growth always belong to the carrier. */
  protected final class TransportTask {
    final PathfinderMob body;
    boolean resting, travelBlocked, searchStarted;
    Status status = Status.IDLE;
    Job job;
    BagSlime workerTarget;
    WorkStorage.Target storageTarget;
    ItemStack supplyReservation = ItemStack.EMPTY;
    IItemHandler supplySource;
    int travelTicks, cooldown, nextSearchTick;
    Vec3 lastTravelPosition, pathDestination;
    final Map<WorkStorage.Target, Long> blockedChests = new HashMap<>();
    final Map<UUID, Long> blockedWorkers = new HashMap<>();

    TransportTask(PathfinderMob body) {
      this.body = body;
    }

    boolean canAct() {
      return canTransportAct(this);
    }

    void reset() {
      clearJob();
      searchStarted = false;
      blockedChests.clear();
      blockedWorkers.clear();
    }

    Vec3 destination() {
      if (job == null || !(level() instanceof ServerLevel server)) return null;
      return job == Job.COLLECT || job == Job.SUPPLY_TOOL
          ? workerTarget == null ? null : workerTarget.getBoundingBox().getCenter()
          : storageTarget == null ? null : storageTarget.position(server);
    }

    private boolean canDepositAny(IItemHandler destination) {
      if (destination == null) return false;
      for (int slot : getBag().occupiedSlots()) {
        ItemStack stack = getBag().getStackInSlot(slot);
        if (!stack.isEmpty()
            && permitsDeposit(this, stack)
            && SlimeBag.insertItemStacked(destination, stack.copy(), true).getCount()
                < stack.getCount()) return true;
      }
      return false;
    }

    void clearJob() {
      job = null;
      workerTarget = null;
      storageTarget = null;
      supplyReservation = ItemStack.EMPTY;
      supplySource = null;
      travelTicks = 0;
      lastTravelPosition = null;
      pathDestination = null;
      body.getNavigation().stop();
    }

    private boolean supported(BagSlime worker) {
      double range = SlimeStat.DETECTION_RANGE.value(TransportSlime.this);
      return worker != TransportSlime.this
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
          .sorted(Comparator.comparingDouble(body::distanceToSqr))
          .toList();
    }

    private List<WorkStorage.Target> storages() {
      return WorkStorage.find(TransportSlime.this, getWorkOrigin()).stream()
          .filter(target -> !blockedChests.containsKey(target))
          .sorted(
              Comparator.<WorkStorage.Target>comparingInt(
                      target -> target.warehouse() != null ? 0 : 1)
                  .thenComparingDouble(
                      target ->
                          target.position((ServerLevel) level()).distanceToSqr(body.position())))
          .toList();
    }

    private List<ChunkPos> workChunks() {
      return WorkSearch.loadedChunks(
              (ServerLevel) level(),
              getWorkOrigin(),
              SlimeStat.DETECTION_RANGE.value(TransportSlime.this))
          .stream()
          .map(chunk -> chunk.getPos())
          .toList();
    }

    private IItemHandler targetInventory() {
      return storageTarget == null
          ? null
          : storageTarget.inventory(TransportSlime.this, getWorkOrigin());
    }

    private boolean storageFull() {
      if (getBag().occupiedSlots().stream().filter(slot -> slot < getCapacity()).count()
          < getCapacity()) return false;
      for (int slot : getBag().occupiedSlots()) {
        ItemStack stack = getBag().getStackInSlot(slot);
        if (stack.isEmpty()
            || stack.getCount() < Math.min(stack.getMaxStackSize(), getBag().getSlotLimit(slot)))
          return false;
      }
      return true;
    }

    private int toolSlot(IItemHandler inventory, BagSlime worker) {
      for (int slot : SlimeBag.contentsSlots(inventory))
        if (worker.acceptsWorkTool(inventory.getStackInSlot(slot))
            && availableSupply(this, inventory, inventory.getStackInSlot(slot)) > 0) return slot;
      return -1;
    }

    private boolean reserveSupply(IItemHandler inventory, BagSlime worker, boolean fetching) {
      int slot = fetching ? supplySlot(inventory, worker) : toolSlot(inventory, worker);
      if (slot < 0) return false;
      ItemStack stack = inventory.getStackInSlot(slot);
      supplyReservation =
          stack.copyWithCount(
              Math.min(worker.workSupplyLimit(), availableSupply(this, inventory, stack)));
      supplySource = fetching ? inventory : null;
      return !supplyReservation.isEmpty();
    }

    private int supplySlot(IItemHandler inventory, BagSlime worker) {
      for (int slot : SlimeBag.contentsSlots(inventory)) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (!worker.acceptsWorkTool(stack)) continue;
        ItemStack offered =
            stack.copyWithCount(
                Math.min(worker.workSupplyLimit(), availableSupply(this, inventory, stack)));
        if (offered.isEmpty()) continue;
        if (SlimeBag.insertItemStacked(getBag(), offered.copy(), true).getCount()
            < offered.getCount()) return slot;
      }
      return -1;
    }

    private static boolean canAcceptAny(IItemHandler destination, IItemHandler source) {
      for (int slot : SlimeBag.contentsSlots(source)) {
        ItemStack stack = source.getStackInSlot(slot);
        if (!stack.isEmpty()
            && SlimeBag.insertItemStacked(destination, stack.copy(), true).getCount()
                < stack.getCount()) return true;
      }
      return false;
    }

    private static int transfer(
        IItemHandler source, int slot, IItemHandler destination, int limit) {
      ItemStack sample = source.extractItem(slot, limit, true);
      if (sample.isEmpty()) return 0;
      int moved =
          sample.getCount()
              - SlimeBag.insertItemStacked(destination, sample.copy(), true).getCount();
      if (moved == 0) return 0;
      ItemStack extracted = source.extractItem(slot, moved, false);
      SlimeBag.insertItemStacked(destination, extracted, false);
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
          if (worker.needsWorkSupply()
              && toolSlot(getBag(), worker) >= 0
              && permitsJob(this, Job.SUPPLY_TOOL, worker, null)) {
            workerTarget = worker;
            job = Job.SUPPLY_TOOL;
            reserveSupply(getBag(), worker, false);
            status = Status.SUPPLYING;
            return;
          }
        }
        for (WorkStorage.Target chest : chests) {
          if (canDepositAny(chest.inventory(TransportSlime.this, getWorkOrigin()))
              && permitsJob(this, Job.DEPOSIT, null, chest)) {
            storageTarget = chest;
            job = Job.DEPOSIT;
            status = Status.DELIVERING;
            return;
          }
        }
        if (!storesCargo() && !sharesTransportTasks()) {
          status = chests.isEmpty() ? Status.NO_CHEST : Status.CHESTS_FULL;
          return;
        }
      }
      if (storesCargo() && storageFull()) {
        status = Status.CHESTS_FULL;
        return;
      }
      if (storesCargo() && workers.stream().anyMatch(BagSlime::needsWorkSupply))
        chests = storages();
      for (BagSlime worker : workers) {
        if (!worker.needsWorkSupply()) continue;
        for (WorkStorage.Target chest : chests) {
          if (supplySlot(chest.inventory(TransportSlime.this, getWorkOrigin()), worker) >= 0
              && permitsJob(this, Job.FETCH_TOOL, worker, chest)) {
            workerTarget = worker;
            storageTarget = chest;
            job = Job.FETCH_TOOL;
            reserveSupply(chest.inventory(TransportSlime.this, getWorkOrigin()), worker, true);
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
            && permitsJob(this, Job.COLLECT, worker, null)
            && (storesCargo()
                || chests.stream()
                    .anyMatch(
                        chest ->
                            canAcceptAny(
                                chest.inventory(TransportSlime.this, getWorkOrigin()),
                                worker.getBag())))) {
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
      if (job == null || !canAct() || !permitsJob(this, job, workerTarget, storageTarget))
        return false;
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
                && reserveSupply(getBag(), workerTarget, false);
        case FETCH_TOOL ->
            workerTarget != null
                && supported(workerTarget)
                && workerTarget.needsWorkSupply()
                && targetInventory() != null
                && reserveSupply(targetInventory(), workerTarget, true);
        case DEPOSIT -> hasCargo() && targetInventory() != null && canDepositAny(targetInventory());
      };
    }

    private void performJob() {
      if (!validJob() || destination() == null || !inReach(destination())) {
        clearJob();
        return;
      }
      int actions = 0;
      ItemStack fetched = ItemStack.EMPTY;
      switch (job) {
        case COLLECT -> {
          for (int slot : workerTarget.getBag().occupiedSlots())
            if (getStamina() >= 1 && transfer(workerTarget.getBag(), slot, getBag(), 64) > 0) {
              spendStamina(1);
              if (storesCargo()) recordCompletedWork(SlimicConfig.TRANSPORT_ACTIVITY.get());
              actions++;
            }
        }
        case DEPOSIT -> {
          IItemHandler destination = targetInventory();
          for (int slot : getBag().occupiedSlots())
            if (getStamina() >= 1
                && permitsDeposit(this, getBag().getStackInSlot(slot))
                && transfer(getBag(), slot, destination, 64) > 0) {
              spendStamina(1);
              recordCompletedWork(SlimicConfig.TRANSPORT_ACTIVITY.get());
              actions++;
            }
        }
        case FETCH_TOOL -> {
          IItemHandler source = targetInventory();
          int slot = supplySlot(source, workerTarget);
          ItemStack sample = source.getStackInSlot(slot).copy();
          int moved =
              transfer(
                  source,
                  slot,
                  getBag(),
                  Math.min(workerTarget.workSupplyLimit(), availableSupply(this, source, sample)));
          if (moved > 0) {
            fetched = sample.copyWithCount(moved);
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
                              availableSupply(this, getBag(), getBag().getStackInSlot(slot)))));
          if (accepted > 0) {
            getBag().extractItem(slot, accepted, false);
            spendStamina(1);
            recordCompletedWork(SlimicConfig.TRANSPORT_ACTIVITY.get());
            actions++;
          }
        }
      }
      BagSlime suppliedWorker = job == Job.FETCH_TOOL && actions > 0 ? workerTarget : null;
      clearJob();
      if (suppliedWorker != null && sharesTransportTasks()) {
        workerTarget = suppliedWorker;
        job = Job.SUPPLY_TOOL;
        supplyReservation = fetched;
      }
      cooldown =
          Math.max(1, (int) Math.ceil(20.0 / SlimeStat.WORK_SPEED.value(TransportSlime.this)));
      nextSearchTick = 0;
      status =
          job == Job.SUPPLY_TOOL
              ? Status.SUPPLYING
              : actions > 0 ? Status.IDLE : Status.UNREACHABLE;
      if (actions > 0) travelBlocked = false;
    }

    private boolean inReach(Vec3 destination) {
      double reach = SlimeStat.REACH.value(TransportSlime.this);
      if (body.getEyePosition().distanceToSqr(destination) > reach * reach) return false;
      if (job == Job.COLLECT || job == Job.SUPPLY_TOOL) return body.hasLineOfSight(workerTarget);
      if (storageTarget != null && storageTarget.warehouse() != null)
        return body.hasLineOfSight(storageTarget.entity((ServerLevel) level()));
      var hit =
          LoadedBlockView.clip(
              level(),
              new ClipContext(
                  body.getEyePosition(),
                  destination,
                  ClipContext.Block.COLLIDER,
                  ClipContext.Fluid.NONE,
                  body));
      return hit.getType() == HitResult.Type.BLOCK
          && hit.getBlockPos().equals(storageTarget.chest());
    }

    void tick() {
      if (!canAct()) {
        clearJob();
        status = Status.IDLE;
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
          nextSearchTick = body.tickCount + Math.floorMod(body.getId(), 20);
          searchStarted = true;
        }
        if (body.tickCount < nextSearchTick) return;
        chooseJob(server);
        if (job == null) {
          nextSearchTick = body.tickCount + 20;
          return;
        }
      }
      Vec3 destination = destination();
      if (destination == null) {
        clearJob();
        return;
      }
      if (inReach(destination)) {
        body.getNavigation().stop();
        performJob();
        return;
      }
      body.getLookControl().setLookAt(destination);
      // Ground navigation cannot replace a route in midair. Preserve useful paths
      // and refresh only when they finish or a moving worker leaves the endpoint.
      if (body.tickCount % 10 == 0
          && body.onGround()
          && (body.getNavigation().isDone()
              || pathDestination == null
              || pathDestination.distanceToSqr(destination) > 1)) {
        body.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0);
        pathDestination = destination;
      }
      if (lastTravelPosition == null
          || body.position().subtract(lastTravelPosition).horizontalDistanceSqr() >= .0025
          || (body.onGround() && Math.abs(body.getY() - lastTravelPosition.y) >= .5)) {
        lastTravelPosition = body.position();
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
