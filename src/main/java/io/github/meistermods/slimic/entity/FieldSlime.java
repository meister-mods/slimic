package io.github.meistermods.slimic.entity;

import com.mojang.authlib.GameProfile;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

/** Shared equipment, protected world edits and logistics for surface workers. */
public abstract class FieldSlime extends BagSlime {
  public enum Status {
    IDLE,
    SURVEYING,
    MOVING,
    WORKING,
    FULL,
    NO_TOOL,
    NO_SEEDS,
    RESTING,
    DISABLED,
    UNREACHABLE,
    WAITING,
    FOLLOWING
  }

  protected boolean working;
  protected BlockPos home;
  protected BlockPos target;
  protected Status status = Status.IDLE;
  protected boolean originChanged;
  private String areaDimension;
  private boolean resting;
  private int cooldown;
  private int travelTicks;
  private Vec3 lastPosition;
  private int retryTicks;

  protected FieldSlime(EntityType<? extends FieldSlime> type, Level level) {
    super(type, level, 27);
    setMaxUpStep(1);
  }

  @Override
  public void onReleased() {
    originChanged =
        home != null
            && (!home.equals(blockPosition())
                || (areaDimension != null
                    && !areaDimension.equals(level().dimension().location().toString())));
    super.onReleased();
    areaDimension = level().dimension().location().toString();
    getNavigation().stop();
    target = null;
  }

  public abstract boolean matchesTool(ItemStack stack);

  protected abstract WorkStatus.Order workOrder();

  protected abstract void workTick(ServerLevel level);

  protected abstract String workTag();

  public final ItemStack getTool() {
    return getMainHandItem();
  }

  public final void setTool(ItemStack stack) {
    setItemSlot(EquipmentSlot.MAINHAND, stack);
  }

  @Override
  public final int detectionMultiplier() {
    return 2;
  }

  @Override
  public final boolean isWorking() {
    return working && !isOrderedToSit();
  }

  @Override
  public void setWorking(boolean value) {
    working = value;
    setWaiting(false);
    target = null;
    travelTicks = 0;
    lastPosition = null;
    getNavigation().stop();
    status = Status.IDLE;
  }

  public final Status getFieldStatus() {
    return isOrderedToSit() ? Status.WAITING : working ? status : Status.FOLLOWING;
  }

  public final BlockPos getWorkHome() {
    return home == null ? blockPosition() : home;
  }

  protected final boolean inArea(BlockPos pos) {
    double r = SlimeStat.DETECTION_RANGE.value(this);
    return pos.distSqr(getWorkHome()) <= r * r;
  }

  @Override
  public final boolean acceptsWorkTool(ItemStack stack) {
    return matchesTool(stack)
        && (!stack.isDamageableItem() || stack.getDamageValue() < stack.getMaxDamage());
  }

  @Override
  public final boolean receiveWorkTool(ItemStack stack) {
    if (!needsReplacementTool() || !getTool().isEmpty() || !acceptsWorkTool(stack)) return false;
    setTool(stack.copyWithCount(1));
    return true;
  }

  @Override
  protected final WorkStatus describeWork() {
    return switch (status) {
      case FULL ->
          WorkStatus.blocked(
              workOrder(), WorkStatus.Reason.INVENTORY_FULL, WorkStatus.Request.CARGO_PICKUP);
      case NO_TOOL ->
          getTool().isEmpty()
              ? WorkStatus.blocked(
                  workOrder(), WorkStatus.Reason.TOOL_MISSING, WorkStatus.Request.TOOL_SUPPLY)
              : WorkStatus.blocked(workOrder(), WorkStatus.Reason.TOOL_MISSING);
      case NO_SEEDS -> WorkStatus.blocked(workOrder(), WorkStatus.Reason.SEEDS_MISSING);
      case RESTING -> WorkStatus.blocked(workOrder(), WorkStatus.Reason.EXHAUSTED);
      case DISABLED -> WorkStatus.blocked(workOrder(), WorkStatus.Reason.DISABLED);
      case UNREACHABLE -> WorkStatus.blocked(workOrder(), WorkStatus.Reason.UNREACHABLE);
      case IDLE -> WorkStatus.of(workOrder(), WorkStatus.State.IDLE);
      default -> WorkStatus.of(workOrder(), WorkStatus.State.ACTIVE);
    };
  }

  @Override
  protected void registerGoals() {
    super.registerGoals();
    goalSelector.addGoal(
        1,
        new Goal() {
          {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
          }

          @Override
          public boolean canUse() {
            return isTame() && isWorking();
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
            if (!(level() instanceof ServerLevel level)) return;
            if (home == null) home = blockPosition();
            if (areaDimension == null) areaDimension = level.dimension().location().toString();
            if (retryTicks > 0) {
              retryTicks--;
              return;
            }
            if (!ForgeEventFactory.getMobGriefingEvent(level, FieldSlime.this)) {
              status = Status.DISABLED;
              getNavigation().stop();
              return;
            }
            if (!acceptsWorkTool(getTool())) {
              for (int i = 0; i < getCapacity(); i++)
                if (acceptsWorkTool(getBag().getStackInSlot(i))) {
                  setTool(getBag().extractItem(i, 1, false));
                  break;
                }
              if (!acceptsWorkTool(getTool())) {
                status = Status.NO_TOOL;
                getNavigation().stop();
                return;
              }
            }
            if (getStamina() < 1) resting = true;
            if (resting && getStamina() < getMaxStamina() * .25F) {
              status = Status.RESTING;
              getNavigation().stop();
              return;
            }
            resting = false;
            if (cooldown > 0) {
              cooldown--;
              return;
            }
            workTick(level);
          }
        });
  }

  @Override
  public void tick() {
    super.tick();
    if (!level().isClientSide && isWorking() && tickCount % 20 == 0)
      recoverStamina(getStaminaRecoveryPerSecond());
  }

  protected final FakePlayer worker(ServerLevel level) {
    var player = FakePlayerFactory.get(level, new GameProfile(getOwnerUUID(), "SlimeGardener"));
    player.setGameMode(GameType.SURVIVAL);
    player.moveTo(getX(), getY(), getZ(), getYRot(), getXRot());
    player.setOnGround(true);
    return player;
  }

  protected final boolean permitted(ServerLevel level, BlockPos pos) {
    return level.hasChunkAt(pos)
        && inArea(pos)
        && level.getWorldBorder().isWithinBounds(pos)
        && level.mayInteract(worker(level), pos);
  }

  protected final boolean approach(BlockPos pos) {
    if (!pos.equals(target)) {
      target = pos;
      travelTicks = 0;
      lastPosition = position();
    }
    double reach = SlimeStat.REACH.value(this);
    var hit =
        level()
            .clip(
                new ClipContext(
                    getEyePosition(),
                    Vec3.atCenterOf(pos),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    this));
    if (getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= reach * reach
        && (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos))) {
      getNavigation().stop();
      getLookControl().setLookAt(Vec3.atCenterOf(pos));
      travelTicks = 0;
      status = Status.WORKING;
      return true;
    }
    status = Status.MOVING;
    if (lastPosition == null
        || position().subtract(lastPosition).horizontalDistanceSqr() >= .0025
        || (onGround() && Math.abs(getY() - lastPosition.y) >= .5)) {
      travelTicks = 0;
      lastPosition = position();
    } else if (++travelTicks > 400) {
      blocked();
      return false;
    }
    if (tickCount % 10 == 0)
      getNavigation().moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 1);
    return false;
  }

  protected final void blocked() {
    status = Status.UNREACHABLE;
    target = null;
    getNavigation().stop();
    retryTicks = 100;
  }

  protected final void completed() {
    spendStamina(1);
    recordCompletedWork();
    cooldown = Math.max(1, (int) Math.ceil(20 / SlimeStat.WORK_SPEED.value(this)));
    target = null;
    status = Status.IDLE;
  }

  protected final boolean canStore(List<ItemStack> drops) {
    var simulation = new ItemStackHandler(getCapacity());
    for (int i = 0; i < getCapacity(); i++)
      simulation.setStackInSlot(i, getBag().getStackInSlot(i).copy());
    for (var stack : drops)
      if (!ItemHandlerHelper.insertItemStacked(simulation, stack.copy(), false).isEmpty())
        return false;
    return true;
  }

  protected final boolean harvest(ServerLevel level, BlockPos pos) {
    if (!permitted(level, pos)) {
      blocked();
      return false;
    }
    BlockState state = level.getBlockState(pos);
    if (state.hasBlockEntity() || !state.canEntityDestroy(level, pos, this)) {
      blocked();
      return false;
    }
    FakePlayer player = worker(level);
    ItemStack previous = player.getMainHandItem();
    player.setItemInHand(InteractionHand.MAIN_HAND, getTool());
    try {
      if (ForgeHooks.onBlockBreakEvent(level, GameType.SURVIVAL, player, pos) < 0
          || level.getBlockState(pos) != state) {
        blocked();
        return false;
      }
      List<ItemStack> drops =
          level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)
              ? Block.getDrops(state, level, pos, null, player, getTool())
              : List.of();
      if (!canStore(drops)) {
        status = Status.FULL;
        getNavigation().stop();
        return false;
      }
      state.getBlock().playerWillDestroy(level, pos, state, player);
      if (!state.onDestroyedByPlayer(level, pos, player, true, level.getFluidState(pos))) {
        blocked();
        return false;
      }
      state.getBlock().destroy(level, pos, state);
      for (var stack : drops) ItemHandlerHelper.insertItemStacked(getBag(), stack, false);
      getTool().mineBlock(level, state, pos, player);
      level.levelEvent(2001, pos, Block.getId(state));
      completed();
      return true;
    } finally {
      player.setItemInHand(InteractionHand.MAIN_HAND, previous);
    }
  }

  protected final boolean place(ServerLevel level, BlockPos pos, BlockState state) {
    if (!permitted(level, pos) || !state.canSurvive(level, pos)) {
      blocked();
      return false;
    }
    var snapshot = BlockSnapshot.create(level.dimension(), level, pos);
    if (!level.setBlock(pos, state, 3)) return false;
    if (ForgeEventFactory.onBlockPlace(worker(level), snapshot, Direction.UP)) {
      snapshot.restore(true, false);
      blocked();
      return false;
    }
    return level.getBlockState(pos).equals(state);
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    var data = new CompoundTag();
    data.putString(
        "Dimension",
        areaDimension == null ? level().dimension().location().toString() : areaDimension);
    data.putBoolean("Working", working);
    data.putBoolean("Resting", resting);
    data.putLong("Home", getWorkHome().asLong());
    data.putInt("Cooldown", cooldown);
    data.putInt("Status", status.ordinal());
    tag.put(workTag(), data);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    var data = tag.getCompound(workTag());
    areaDimension =
        data.contains("Dimension")
            ? data.getString("Dimension")
            : level().dimension().location().toString();
    working = data.getBoolean("Working");
    resting = data.getBoolean("Resting");
    home = data.contains("Home") ? BlockPos.of(data.getLong("Home")) : null;
    cooldown = Math.max(0, Math.min(200, data.getInt("Cooldown")));
    int i = data.getInt("Status");
    status = i >= 0 && i < Status.values().length ? Status.values()[i] : Status.IDLE;
  }
}
