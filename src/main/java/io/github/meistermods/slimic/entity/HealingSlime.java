package io.github.meistermods.slimic.entity;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.block.BrokenSlimeBlock;
import io.github.meistermods.slimic.block.BrokenSlimeBlockEntity;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.items.ItemHandlerHelper;

public class HealingSlime extends BagSlime {
  public enum Status {
    IDLE,
    HEALING,
    REVIVING,
    NO_SLIMEBALLS,
    RESTING,
    UNREACHABLE,
    WAITING,
    FOLLOWING,
    FULL,
    GROWING,
    DISABLED
  }

  private boolean working, resting, treatmentBlocked;
  private BlockPos home, brokenTarget;
  private FriendlySlime patient;
  protected Status status = Status.IDLE;
  private int nextSearch, cooldown, stuckTicks;
  private Vec3 lastPosition, pathDestination;
  private final Map<Object, Long> blocked = new HashMap<>();

  public HealingSlime(EntityType<? extends HealingSlime> type, Level level) {
    super(type, level, 27);
  }

  @Override
  public int detectionMultiplier() {
    return 2;
  }

  protected boolean performSecondaryWork(ServerLevel server) {
    return false;
  }

  @Override
  protected String bagTagName() {
    return "HealingBag";
  }

  @Override
  public boolean isWorking() {
    return working && !isOrderedToSit();
  }

  @Override
  public void setWorking(boolean value) {
    working = value;
    treatmentBlocked = false;
    setWaiting(false);
    clearTarget();
    nextSearch = 0;
    status = Status.IDLE;
  }

  public Status getHealingStatus() {
    if (isOrderedToSit()) return Status.WAITING;
    if (!working) return Status.FOLLOWING;
    if (status == Status.NO_SLIMEBALLS) {
      if (ballSlot() >= 0) return Status.IDLE;
      if (!ItemHandlerHelper.insertItemStacked(getBag(), new ItemStack(Items.SLIME_BALL), true)
          .isEmpty()) return Status.FULL;
    }
    if (treatmentBlocked
        && (status == Status.IDLE || status == Status.HEALING || status == Status.REVIVING))
      return Status.UNREACHABLE;
    return status;
  }

  public BlockPos getWorkOrigin() {
    return home == null ? blockPosition() : home;
  }

  @Override
  protected WorkStatus describeWork() {
    Status current = getHealingStatus();
    return switch (current) {
      case NO_SLIMEBALLS ->
          WorkStatus.blocked(
              WorkStatus.Order.HEAL,
              WorkStatus.Reason.SLIMEBALLS_MISSING,
              WorkStatus.Request.SLIMEBALL_SUPPLY);
      case FULL ->
          WorkStatus.blocked(
              WorkStatus.Order.HEAL,
              WorkStatus.Reason.INVENTORY_FULL,
              WorkStatus.Request.CARGO_PICKUP);
      case RESTING -> WorkStatus.blocked(WorkStatus.Order.HEAL, WorkStatus.Reason.EXHAUSTED);
      case UNREACHABLE -> WorkStatus.blocked(WorkStatus.Order.HEAL, WorkStatus.Reason.UNREACHABLE);
      case DISABLED -> WorkStatus.blocked(WorkStatus.Order.HEAL, WorkStatus.Reason.DISABLED);
      default ->
          WorkStatus.of(
              WorkStatus.Order.HEAL,
              current == Status.IDLE ? WorkStatus.State.IDLE : WorkStatus.State.ACTIVE);
    };
  }

  @Override
  public boolean acceptsWorkTool(ItemStack stack) {
    return isWorking() && ballSlot() < 0 && stack.is(Items.SLIME_BALL);
  }

  @Override
  public int workSupplyLimit() {
    return 64;
  }

  @Override
  public int receiveWorkSupply(ItemStack stack) {
    if (!acceptsWorkTool(stack)) return 0;
    ItemStack offered = stack.copyWithCount(Math.min(workSupplyLimit(), stack.getCount()));
    return offered.getCount()
        - ItemHandlerHelper.insertItemStacked(getBag(), offered, false).getCount();
  }

  protected int ballSlot() {
    for (int slot = 0; slot < getCapacity(); slot++)
      if (getBag().getStackInSlot(slot).is(Items.SLIME_BALL)) return slot;
    return -1;
  }

  @Override
  public void onReleased() {
    super.onReleased();
    home = blockPosition();
    treatmentBlocked = false;
    clearTarget();
    blocked.clear();
    nextSearch = 0;
  }

  private void clearTarget() {
    patient = null;
    brokenTarget = null;
    stuckTicks = 0;
    lastPosition = pathDestination = null;
    getNavigation().stop();
  }

  private boolean inArea(Vec3 pos) {
    double range = SlimeStat.DETECTION_RANGE.value(this);
    return pos.distanceToSqr(Vec3.atCenterOf(getWorkOrigin())) <= range * range;
  }

  private boolean eligible(FriendlySlime slime) {
    return slime.isAlive()
        && !slime.isRemoved()
        && slime.level() == level()
        && slime.isTame()
        && getOwnerUUID() != null
        && getOwnerUUID().equals(slime.getOwnerUUID())
        && slime.getHealth() < slime.getMaxHealth()
        && inArea(slime.position())
        && level().hasChunkAt(slime.blockPosition());
  }

  private boolean eligible(BlockPos pos) {
    if (!inArea(Vec3.atCenterOf(pos))
        || !level().hasChunkAt(pos)
        || !(level().getBlockEntity(pos) instanceof BrokenSlimeBlockEntity broken)) return false;
    ItemStack stack = broken.getSlime();
    if (!(stack.getItem() instanceof FriendlySlimeItem) || !stack.hasTag()) return false;
    CompoundTag data = stack.getTag().getCompound("SlimeData");
    return getOwnerUUID() != null
        && data.hasUUID("Owner")
        && getOwnerUUID().equals(data.getUUID("Owner"));
  }

  private boolean stillNeedsTreatment(ServerLevel server, Object target) {
    if (target instanceof BlockPos pos) return eligible(pos);
    return target instanceof UUID id
        && server.getEntity(id) instanceof FriendlySlime slime
        && eligible(slime);
  }

  private void search(ServerLevel server) {
    long now = server.getGameTime();
    blocked
        .entrySet()
        .removeIf(entry -> entry.getValue() <= now || !stillNeedsTreatment(server, entry.getKey()));
    int radius = (int) Math.ceil(SlimeStat.DETECTION_RANGE.value(this));
    BlockPos origin = getWorkOrigin();
    for (int x = (origin.getX() - radius) >> 4; x <= (origin.getX() + radius) >> 4; x++)
      for (int z = (origin.getZ() - radius) >> 4; z <= (origin.getZ() + radius) >> 4; z++) {
        var chunk = server.getChunkSource().getChunkNow(x, z);
        if (chunk == null) continue;
        for (var entity : chunk.getBlockEntities().values())
          if (entity instanceof BrokenSlimeBlockEntity && !entity.isRemoved()) {
            BlockPos pos = entity.getBlockPos();
            if (!blocked.containsKey(pos)
                && eligible(pos)
                && (brokenTarget == null
                    || pos.distToCenterSqr(position()) < brokenTarget.distToCenterSqr(position())))
              brokenTarget = pos.immutable();
          }
      }
    if (brokenTarget != null) return;
    patient =
        server.getEntitiesOfClass(FriendlySlime.class, new AABB(origin).inflate(radius)).stream()
            .filter(this::eligible)
            .filter(slime -> !blocked.containsKey(slime.getUUID()))
            .min(Comparator.comparingDouble(this::distanceToSqr))
            .orElse(null);
  }

  private boolean visible(Vec3 destination) {
    if (patient != null) return patient == this || hasLineOfSight(patient);
    var hit =
        LoadedBlockView.clip(
            level(),
            new ClipContext(
                getEyePosition(),
                destination,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this));
    return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(brokenTarget);
  }

  private void treat(ServerLevel server) {
    int slot = ballSlot();
    if (slot < 0 || getStamina() < 1) return;
    FriendlySlime treatingPatient = patient;
    BlockPos treatingBroken = brokenTarget;
    boolean success;
    if (treatingPatient != null) {
      float before = treatingPatient.getHealth();
      treatingPatient.heal(4);
      success = treatingPatient.getHealth() > before;
    } else {
      var player = FakePlayerFactory.get(server, new GameProfile(getOwnerUUID(), "SlimeHealer"));
      player.moveTo(position());
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SLIME_BALL));
      success =
          server.mayInteract(player, treatingBroken)
              && ForgeHooks.onBlockBreakEvent(server, GameType.SURVIVAL, player, treatingBroken)
                  >= 0
              && canContinueWork(player)
              && eligible(treatingBroken)
              && BrokenSlimeBlock.revive(server, treatingBroken, player);
    }
    if (success) {
      treatmentBlocked = false;
      getBag().extractItem(slot, 1, false);
      spendStamina(1);
      recordCompletedWork();
    } else if (isWorking()) {
      treatmentBlocked = true;
      blocked.put(
          treatingPatient == null ? treatingBroken : treatingPatient.getUUID(),
          server.getGameTime() + 200);
      status = Status.UNREACHABLE;
    }
    clearTarget();
    cooldown = Math.max(1, (int) Math.ceil(20 / SlimeStat.WORK_SPEED.value(this)));
  }

  @Override
  protected void registerGoals() {
    super.registerGoals();
    goalSelector.addGoal(1, new HealGoal());
  }

  @Override
  public void tick() {
    super.tick();
    if (!level().isClientSide
        && isWorking()
        && tickCount % 20 == 0
        && (status == Status.IDLE
            || status == Status.RESTING
            || status == Status.NO_SLIMEBALLS
            || status == Status.UNREACHABLE)) recoverStamina(getStaminaRecoveryPerSecond());
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.HEALING_SLIME.get());
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putBoolean("HealingWorking", working);
    tag.putBoolean("HealingResting", resting);
    tag.putBoolean("HealingTreatmentBlocked", treatmentBlocked);
    tag.putInt("HealingCooldown", cooldown);
    if (home != null) tag.putLong("HealingHome", home.asLong());
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    working = tag.getBoolean("HealingWorking");
    if (!tag.contains("HealingWorking")) setWaiting(true);
    resting = tag.getBoolean("HealingResting");
    treatmentBlocked = tag.getBoolean("HealingTreatmentBlocked");
    blocked.clear();
    cooldown = Math.max(0, Math.min(200, tag.getInt("HealingCooldown")));
    home = tag.contains("HealingHome") ? BlockPos.of(tag.getLong("HealingHome")) : null;
    clearTarget();
    nextSearch = 0;
    status = Status.IDLE;
  }

  private final class HealGoal extends Goal {
    HealGoal() {
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
      clearTarget();
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
      if (resting && getStamina() < getMaxStamina() * .25F) {
        clearTarget();
        status = Status.RESTING;
        return;
      }
      resting = false;
      if (ballSlot() < 0) {
        if (patient != null || brokenTarget != null) clearTarget();
        if (performSecondaryWork(server)) return;
        status = Status.NO_SLIMEBALLS;
        return;
      }
      if (cooldown > 0 && --cooldown > 0) return;
      if ((patient != null && !eligible(patient))
          || (brokenTarget != null && !eligible(brokenTarget))) clearTarget();
      if (patient == null && brokenTarget == null) {
        if (tickCount >= nextSearch) {
          nextSearch = tickCount + 20 + Math.floorMod(getId(), 5);
          search(server);
        }
        if (patient == null && brokenTarget == null) {
          if (performSecondaryWork(server)) return;
          if (blocked.isEmpty()) treatmentBlocked = false;
          status = blocked.isEmpty() ? Status.IDLE : Status.UNREACHABLE;
          return;
        }
      }
      status = patient == null ? Status.REVIVING : Status.HEALING;
      Vec3 destination =
          patient != null ? patient.getBoundingBox().getCenter() : Vec3.atCenterOf(brokenTarget);
      double reach = SlimeStat.REACH.value(HealingSlime.this);
      if (getEyePosition().distanceToSqr(destination) <= reach * reach && visible(destination)) {
        treat(server);
        return;
      }
      getLookControl().setLookAt(destination);
      if (tickCount % 10 == 0
          && onGround()
          && (getNavigation().isDone()
              || pathDestination == null
              || pathDestination.distanceToSqr(destination) > 1)) {
        getNavigation().moveTo(destination.x, destination.y, destination.z, 1);
        pathDestination = destination;
      }
      if (lastPosition == null
          || position().subtract(lastPosition).horizontalDistanceSqr() >= .0025
          || (onGround() && Math.abs(getY() - lastPosition.y) >= .5)) {
        lastPosition = position();
        stuckTicks = 0;
      } else stuckTicks++;
      if (stuckTicks > 400) {
        treatmentBlocked = true;
        blocked.put(patient == null ? brokenTarget : patient.getUUID(), server.getGameTime() + 200);
        clearTarget();
        status = Status.UNREACHABLE;
      }
    }
  }
}
