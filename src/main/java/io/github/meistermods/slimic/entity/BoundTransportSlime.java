package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class BoundTransportSlime extends TransportSlime implements LinkedColony {
  private static final EntityDataAccessor<Integer> LINKED_BODY =
      SynchedEntityData.defineId(BoundTransportSlime.class, EntityDataSerializers.INT);
  private final ColonyMembers members = new ColonyMembers(this);
  private TransportColonyBody linkedBody;
  private TransportTask linkedTask;
  private int nextBodyAttempt;
  private int returnTicks;
  private boolean admittingBody;

  public BoundTransportSlime(EntityType<? extends BoundTransportSlime> type, Level level) {
    super(type, level);
  }

  @Override
  protected void defineSynchedData() {
    super.defineSynchedData();
    entityData.define(LINKED_BODY, -1);
  }

  /** Only the representative is saved; the second body never owns a copy of the cargo. */
  @Nullable
  public TransportColonyBody getLinkedBody() {
    if (level().isClientSide) {
      var entity = level().getEntity(entityData.get(LINKED_BODY));
      return entity instanceof TransportColonyBody body && !body.isRemoved() ? body : null;
    }
    return linkedBody != null && !linkedBody.isRemoved() && linkedBody.level() == level()
        ? linkedBody
        : null;
  }

  public double getLinkRange() {
    return Math.min(64, Math.max(16, SlimeStat.DETECTION_RANGE.value(this) * 2));
  }

  public Status getPrimaryTransportStatus() {
    return super.getTransportStatus();
  }

  public Status getLinkedTransportStatus() {
    if (isOrderedToSit()) return Status.WAITING;
    if (!isWorking()) return Status.FOLLOWING;
    return linkedTask == null ? Status.IDLE : linkedTask.status;
  }

  @Nullable
  public Vec3 getPrimaryTransportDestination() {
    return primaryTask.destination();
  }

  @Nullable
  public Vec3 getLinkedTransportDestination() {
    return linkedTask == null ? null : linkedTask.destination();
  }

  private static boolean active(Status status) {
    return status == Status.COLLECTING
        || status == Status.DELIVERING
        || status == Status.FETCHING_TOOL
        || status == Status.SUPPLYING;
  }

  @Override
  public Status getTransportStatus() {
    Status first = super.getTransportStatus();
    if (!isWorking() || linkedTask == null || active(first)) return first;
    return active(linkedTask.status) ? linkedTask.status : first;
  }

  @Override
  protected WorkStatus describeWork() {
    if (active(getTransportStatus()))
      return WorkStatus.of(WorkStatus.Order.HAUL, WorkStatus.State.ACTIVE);
    return super.describeWork();
  }

  @Override
  public boolean isWithinManagementRange(Player player, double squaredRange) {
    if (super.isWithinManagementRange(player, squaredRange)) return true;
    var body = getLinkedBody();
    return body != null
        && body.isAddedToWorld()
        && body.getColony() == this
        && player.level() == level()
        && body.distanceToSqr(player) <= squaredRange
        && distanceToSqr(body) <= getLinkRange() * getLinkRange();
  }

  @Override
  protected boolean sharesTransportTasks() {
    return linkedTask != null && getLinkedBody() != null;
  }

  @Override
  protected boolean canTransportAct(TransportTask task) {
    if (!super.canTransportAct(task) || isFleeing()) return false;
    var body = getLinkedBody();
    if (body != null && distanceToSqr(body) > getLinkRange() * getLinkRange()) return false;
    return task == primaryTask
        || body != null
            && task == linkedTask
            && body.isAddedToWorld()
            && body.getColony() == this
            && level() instanceof ServerLevel server
            && server.isPositionEntityTicking(body.blockPosition());
  }

  @Nullable
  private TransportTask peer(TransportTask task) {
    return task == primaryTask ? linkedTask : primaryTask;
  }

  @Override
  protected boolean permitsJob(
      TransportTask task, Job job, BagSlime worker, WorkStorage.Target storage) {
    TransportTask other = peer(task);
    if (other == null || getLinkedBody() == null) return true;
    // A worker's request and the deposit of the shared cargo have a single claimant.
    if (worker != null && other.workerTarget == worker && other.job != null) return false;
    if (job == Job.DEPOSIT && other.job == Job.DEPOSIT) return false;
    Vec3 destination =
        job == Job.COLLECT || job == Job.SUPPLY_TOOL
            ? worker == null ? null : worker.getBoundingBox().getCenter()
            : storage == null ? null : storage.position((ServerLevel) level());
    if (destination == null) return false;
    Vec3 peerDestination = other.destination();
    if (peerDestination != null
        && destination.distanceToSqr(peerDestination) > getLinkRange() * getLinkRange())
      return false;
    // Keep an existing commitment; select the nearer free body when assigning a new one.
    if (task.job != null
        || other.job != null
        || other.cooldown > 0
        || other.resting
        || !canTransportAct(other)) return true;
    boolean peerBlocked =
        worker != null
            ? other.blockedWorkers.getOrDefault(worker.getUUID(), 0L) > level().getGameTime()
            : other.blockedChests.getOrDefault(storage, 0L) > level().getGameTime();
    return peerBlocked
        || task.body.position().distanceToSqr(destination)
            <= other.body.position().distanceToSqr(destination) + .25;
  }

  @Override
  protected boolean permitsDeposit(TransportTask task, ItemStack stack) {
    // Reserve by the live request and item kind, not a slot that a bag sort can move.
    for (TransportTask current :
        linkedTask == null
            ? java.util.List.of(primaryTask)
            : java.util.List.of(primaryTask, linkedTask))
      if ((current.job == Job.FETCH_TOOL || current.job == Job.SUPPLY_TOOL)
          && current.workerTarget != null
          && current.workerTarget.isAlive()
          && current.workerTarget.level() == level()
          && canCooperateWith(current.workerTarget)
          && current.workerTarget.needsWorkSupply()
          && current.workerTarget.acceptsWorkTool(stack)) return false;
    return true;
  }

  @Override
  protected int availableSupply(
      TransportTask task, net.minecraftforge.items.IItemHandler inventory, ItemStack stack) {
    TransportTask other = peer(task);
    if (other == null
        || other.workerTarget == null
        || !other.workerTarget.isAlive()
        || !other.workerTarget.needsWorkSupply()
        || !canCooperateWith(other.workerTarget)
        || !ItemStack.isSameItemSameTags(stack, other.supplyReservation)
        || !(other.job == Job.SUPPLY_TOOL && inventory == getBag()
            || other.job == Job.FETCH_TOOL && inventory == other.supplySource))
      return stack.getCount();
    long available = 0;
    for (int slot : SlimeBag.contentsSlots(inventory)) {
      ItemStack candidate = inventory.getStackInSlot(slot);
      if (ItemStack.isSameItemSameTags(candidate, stack)) available += candidate.getCount();
    }
    return (int)
        Math.min(stack.getCount(), Math.max(0, available - other.supplyReservation.getCount()));
  }

  @Override
  public void setWaiting(boolean waiting) {
    super.setWaiting(waiting);
    if (waiting) stopLinkedWork();
  }

  @Override
  public void setWorking(boolean working) {
    super.setWorking(working);
    stopLinkedWork();
    if (linkedTask != null) linkedTask.reset();
  }

  private void stopLinkedWork() {
    if (linkedTask != null) {
      linkedTask.clearJob();
      linkedTask.status = Status.IDLE;
    }
    if (linkedBody != null) {
      linkedBody.getNavigation().stop();
      linkedBody.setDeltaMovement(linkedBody.getDeltaMovement().multiply(0, 1, 0));
    }
  }

  private void removeLinkedBody() {
    stopLinkedWork();
    var previous = linkedBody;
    linkedBody = null;
    linkedTask = null;
    entityData.set(LINKED_BODY, -1);
    if (previous != null && !previous.isRemoved()) previous.discard();
    returnTicks = 0;
  }

  @Override
  public void remove(RemovalReason reason) {
    // Removing the body publishes leave callbacks; retire the authority before those can reenter.
    super.remove(reason);
    if (!level().isClientSide) removeLinkedBody();
  }

  @Override
  public void onReleased() {
    super.onReleased();
    if (!level().isClientSide) {
      removeLinkedBody();
      nextBodyAttempt = tickCount;
    }
  }

  private void ensureLinkedBody(ServerLevel server) {
    if (getLinkedBody() != null
        || admittingBody
        || tickCount < nextBodyAttempt
        || !members.ready()
        || isPendingSpawn()
        || !isAddedToWorld()
        || !isAlive()
        || !io.github.meistermods.slimic.world.ColonyBondingData.get(server).mayJoin(this)) return;
    removeLinkedBody();
    nextBodyAttempt = tickCount + 20;
    var body = ModEntities.TRANSPORT_COLONY_BODY.get().create(server);
    if (body == null) return;
    Vec3 landing = CompanionTeleport.landing(server, body, blockPosition(), getId(), 3);
    if (landing == null) return;
    var owner = getOwnerUUID();
    body.moveTo(landing.x, landing.y, landing.z, getYRot(), 0);
    body.setColony(this);
    linkedBody = body;
    admittingBody = true;
    try {
      if (!server.addFreshEntity(body)
          || !body.isAddedToWorld()
          || body.isRemoved()
          || body.level() != server
          || getLinkedBody() != body
          || body.getColony() != this
          || !isAddedToWorld()
          || !isAlive()
          || isRemoved()
          || isPendingSpawn()
          || !java.util.Objects.equals(owner, getOwnerUUID())
          || body.distanceToSqr(this) > getLinkRange() * getLinkRange()
          || !CompanionTeleport.safe(server, body, body.position())) {
        if (linkedBody == body) removeLinkedBody();
        else body.discard();
        return;
      }
      linkedTask = new TransportTask(body);
      entityData.set(LINKED_BODY, body.getId());
    } finally {
      admittingBody = false;
    }
  }

  private void followLinkedBody() {
    var body = getLinkedBody();
    if (body == null || linkedTask == null) return;
    if (isOrderedToSit() && !isFleeing()) {
      stopLinkedWork();
      if (body.distanceToSqr(this) > getLinkRange() * getLinkRange()) {
        if (++returnTicks >= 100) {
          removeLinkedBody();
          nextBodyAttempt = tickCount + 1;
        }
      } else returnTicks = 0;
      return;
    }
    double distance = body.distanceToSqr(this);
    boolean stretched = distance > getLinkRange() * getLinkRange();
    if (stretched || isFleeing() || !isWorking()) linkedTask.clearJob();
    if (stretched) primaryTask.clearJob();
    if (linkedTask.job != null && !stretched && !isFleeing() && isWorking()) return;
    if (distance <= 9) {
      body.getNavigation().stop();
      returnTicks = 0;
      return;
    }
    if (body.tickCount % 10 == 0
        && body.onGround()
        && (body.getNavigation().isDone()
            || body.getNavigation().getTargetPos() == null
            || body.getNavigation().getTargetPos().distSqr(blockPosition()) > 4))
      body.getNavigation().moveTo(getX(), getY(), getZ(), 1.1);
    if (stretched && ++returnTicks >= 100) {
      // Reconstruct only the body at a checked, loaded landing; the cargo never leaves its owner.
      removeLinkedBody();
      nextBodyAttempt = tickCount + 1;
    } else if (!stretched) returnTicks = 0;
  }

  @Override
  public ColonyMembers members() {
    return members;
  }

  @Override
  protected SlimeBag createBag(int slots) {
    return new ColonyBag(
        slots * 2,
        () -> members == null ? java.util.List.of() : members.bags(),
        () -> members == null ? java.util.List.of() : members.spans(),
        () -> bagRevision++,
        () -> {
          if (members != null) members.compactBagSpans();
        });
  }

  @Override
  public int capacityMultiplier() {
    return 1;
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.BOUND_TRANSPORT_SLIME.get());
  }

  @Override
  public void tick() {
    super.tick();
    if (level() instanceof ServerLevel server && isAlive() && !isRemoved()) {
      ensureLinkedBody(server);
      if (linkedTask != null) linkedTask.tick();
      followLinkedBody();
    }
    members.syncVitals();
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    members.syncVitals();
    super.addAdditionalSaveData(tag);
    tag.put("ColonyMembers", members.save());
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    if (!level().isClientSide) removeLinkedBody();
    members.clear();
    super.readAdditionalSaveData(tag);
    members.load(tag.getCompound("ColonyMembers"));
  }
}
