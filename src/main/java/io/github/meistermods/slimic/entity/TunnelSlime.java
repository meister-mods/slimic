package io.github.meistermods.slimic.entity;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;

/** A three-by-three horizontal bore with a nonconsumable lining template. */
public class TunnelSlime extends MiningSlime {
  public enum Status {
    IDLE,
    MINING,
    LINING,
    MOVING,
    FULL,
    NO_TOOL,
    NEEDS_TIER,
    NO_BUCKET,
    NO_MATERIAL,
    NO_TEMPLATE,
    RESTING,
    DISABLED,
    UNREACHABLE,
    WAITING,
    FOLLOWING
  }

  private static final EntityDataAccessor<Integer> DIRECTION =
      SynchedEntityData.defineId(TunnelSlime.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<ItemStack> LINING =
      SynchedEntityData.defineId(TunnelSlime.class, EntityDataSerializers.ITEM_STACK);
  private final SlimeBlockBreaking breaking = new SlimeBlockBreaking();
  private BlockPos anchor;
  private String anchorDimension;
  private int step = 1, requiredTool, retryAt, drainedFluidMask;
  private boolean resting, travelBlocked;
  private int travelTicks, lastTravelTick = -1;
  private BlockPos travelStand;
  private double closestTravelDistance = Double.POSITIVE_INFINITY;
  protected Status tunnelStatus = Status.IDLE;

  public TunnelSlime(EntityType<? extends TunnelSlime> type, Level level) {
    super(type, level);
  }

  @Override
  protected boolean usesQuarry() {
    return false;
  }

  @Override
  protected PathNavigation createNavigation(Level level) {
    return new WorkNavigation(this, level);
  }

  private boolean swimmingToWork() {
    if (!isWorking() || !hasBucket() || resting || getStamina() < 1) return false;
    return switch (tunnelStatus) {
      case FULL,
          NO_TOOL,
          NEEDS_TIER,
          NO_BUCKET,
          NO_MATERIAL,
          NO_TEMPLATE,
          RESTING,
          DISABLED,
          UNREACHABLE ->
          false;
      default -> true;
    };
  }

  @Override
  protected boolean allowsFloating() {
    return !swimmingToWork();
  }

  @Override
  public void travel(Vec3 input) {
    if (!level().isClientSide && isWorking() && isInWater() && anchor != null && !isFleeing()) {
      var motion = getDeltaMovement();
      boolean workingInWater = swimmingToWork();
      boolean moving = workingInWater && tunnelStatus == Status.MOVING;
      // Follow the fixed bore floor rather than the navigation's water-surface height.
      // Waiting for stamina or supplies holds the horizontal position against incoming flow.
      double vertical =
          workingInWater ? Math.max(-.12, Math.min(.08, (anchor.getY() - getY()) * .15)) : motion.y;
      setDeltaMovement(moving ? new Vec3(motion.x, vertical, motion.z) : new Vec3(0, vertical, 0));
      super.travel(moving ? input : Vec3.ZERO);
    } else super.travel(input);
  }

  @Override
  protected void defineSynchedData() {
    super.defineSynchedData();
    entityData.define(DIRECTION, Direction.NORTH.get3DDataValue());
    entityData.define(LINING, new ItemStack(Items.COBBLESTONE));
  }

  public int getTunnelSize() {
    return 3;
  }

  public int getTunnelMinWidth() {
    return -(getTunnelSize() - 1) / 2;
  }

  protected boolean tickTunnelWork(ServerLevel server) {
    return false;
  }

  public Direction getTunnelDirection() {
    return Direction.from3DDataValue(entityData.get(DIRECTION));
  }

  public void setTunnelDirection(Direction direction) {
    if (!direction.getAxis().isHorizontal()) return;
    entityData.set(DIRECTION, direction.get3DDataValue());
    if (!level().isClientSide) {
      setWorking(false);
      setWaiting(true);
    }
    restart();
  }

  public ItemStack getLiningTemplate() {
    return entityData.get(LINING);
  }

  public boolean validLining(ItemStack stack) {
    return stack.getItem() instanceof BlockItem item
        && !(item.getBlock() instanceof FallingBlock)
        && !(item.getBlock() instanceof TntBlock)
        && !item.getBlock().defaultBlockState().hasBlockEntity()
        && item.getBlock().defaultBlockState().isCollisionShapeFullBlock(level(), blockPosition());
  }

  public void setLiningTemplate(ItemStack stack) {
    if (!stack.isEmpty() && !validLining(stack)) return;
    entityData.set(LINING, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    if (!level().isClientSide) {
      setWorking(false);
      setWaiting(true);
    }
  }

  protected void restart() {
    anchor = blockPosition();
    anchorDimension = level().dimension().location().toString();
    step = 1;
    drainedFluidMask = 0;
    madeProgress();
    breaking.reset(this);
    retryAt = 0;
    getNavigation().stop();
  }

  @Override
  public void setWorking(boolean value) {
    super.setWorking(value);
    breaking.reset(this);
    retryAt = 0;
    tunnelStatus = Status.IDLE;
    madeProgress();
  }

  @Override
  public void onReleased() {
    super.onReleased();
    if (anchor == null
        || !level().dimension().location().toString().equals(anchorDimension)
        || getTunnelFront().distToCenterSqr(position()) > 64) restart();
    breaking.reset(this);
    retryAt = 0;
    travelStand = getTunnelFront().relative(getTunnelDirection(), -1);
    closestTravelDistance = travelStand.distToCenterSqr(position());
    lastTravelTick = -1;
  }

  public BlockPos getTunnelFront() {
    return (anchor == null ? blockPosition() : anchor).relative(getTunnelDirection(), step);
  }

  public Status getTunnelStatus() {
    return isOrderedToSit()
        ? Status.WAITING
        : isWorking()
            ? (travelBlocked && tunnelStatus == Status.MOVING ? Status.UNREACHABLE : tunnelStatus)
            : Status.FOLLOWING;
  }

  private int materialSlot() {
    for (int i : getBag().occupiedSlots())
      if (!getLiningTemplate().isEmpty()
          && getBag().getStackInSlot(i).is(getLiningTemplate().getItem())) return i;
    return -1;
  }

  @Override
  public boolean acceptsWorkTool(ItemStack stack) {
    if (tunnelStatus == Status.NO_MATERIAL)
      return !getLiningTemplate().isEmpty()
          && stack.is(getLiningTemplate().getItem())
          && materialSlot() < 0
          && SlimeBag.insertItemStacked(getBag(), stack.copyWithCount(1), true).isEmpty();
    if (tunnelStatus == Status.NO_BUCKET)
      return !hasBucket()
          && stack.is(Items.BUCKET)
          && SlimeBag.insertItemStacked(getBag(), stack.copyWithCount(1), true).isEmpty();
    return matchesToolSlot(requiredTool, stack) && SlimeBlockBreaking.usable(stack);
  }

  @Override
  public boolean receiveWorkTool(ItemStack stack) {
    if (!isWorking() || !acceptsWorkTool(stack)) return false;
    if (tunnelStatus == Status.NO_MATERIAL || tunnelStatus == Status.NO_BUCKET)
      return receiveWorkSupply(stack.copyWithCount(1)) == 1;
    if (!getTool(requiredTool).isEmpty()) return false;
    setTool(requiredTool, stack.copyWithCount(1));
    return true;
  }

  @Override
  public int workSupplyLimit() {
    return tunnelStatus == Status.NO_MATERIAL ? 64 : 1;
  }

  @Override
  public int receiveWorkSupply(ItemStack stack) {
    if (tunnelStatus != Status.NO_MATERIAL && tunnelStatus != Status.NO_BUCKET)
      return receiveWorkTool(stack) ? 1 : 0;
    if (!isWorking() || !acceptsWorkTool(stack)) return 0;
    var offered = stack.copyWithCount(Math.min(workSupplyLimit(), stack.getCount()));
    int moved =
        offered.getCount() - SlimeBag.insertItemStacked(getBag(), offered, false).getCount();
    if (moved > 0) tunnelStatus = Status.IDLE;
    return moved;
  }

  @Override
  protected WorkStatus describeWork() {
    var order = WorkStatus.Order.TUNNEL;
    return switch (tunnelStatus) {
      case FULL ->
          WorkStatus.blocked(
              order, WorkStatus.Reason.INVENTORY_FULL, WorkStatus.Request.CARGO_PICKUP);
      case NO_MATERIAL ->
          WorkStatus.blocked(
              order,
              WorkStatus.Reason.MATERIAL_MISSING,
              materialSlot() < 0
                  ? new WorkStatus.Request[] {
                    WorkStatus.Request.MATERIAL_SUPPLY, WorkStatus.Request.CARGO_PICKUP
                  }
                  : new WorkStatus.Request[] {});
      case NO_BUCKET ->
          WorkStatus.blocked(
              order,
              WorkStatus.Reason.BUCKET_MISSING,
              WorkStatus.Request.BUCKET_SUPPLY,
              WorkStatus.Request.CARGO_PICKUP);
      case NO_TOOL ->
          WorkStatus.blocked(order, WorkStatus.Reason.TOOL_MISSING, WorkStatus.Request.TOOL_SUPPLY);
      case NEEDS_TIER -> WorkStatus.blocked(order, WorkStatus.Reason.TOOL_TIER);
      case NO_TEMPLATE -> WorkStatus.blocked(order, WorkStatus.Reason.MATERIAL_MISSING);
      case RESTING -> WorkStatus.blocked(order, WorkStatus.Reason.EXHAUSTED);
      case DISABLED -> WorkStatus.blocked(order, WorkStatus.Reason.DISABLED);
      case UNREACHABLE -> WorkStatus.blocked(order, WorkStatus.Reason.UNREACHABLE);
      default ->
          travelBlocked
              ? WorkStatus.blocked(order, WorkStatus.Reason.UNREACHABLE)
              : WorkStatus.of(
                  order,
                  tunnelStatus == Status.IDLE ? WorkStatus.State.IDLE : WorkStatus.State.ACTIVE);
    };
  }

  private FakePlayer worker(ServerLevel server) {
    var worker = FakePlayerFactory.get(server, new GameProfile(getOwnerUUID(), "SlimeTunneler"));
    worker.setGameMode(GameType.SURVIVAL);
    worker.moveTo(position());
    worker.setOnGround(true);
    return worker;
  }

  private boolean permitted(ServerLevel server, BlockPos pos) {
    return server.hasChunkAt(pos)
        && !server.isOutsideBuildHeight(pos)
        && server.getWorldBorder().isWithinBounds(pos)
        && server.mayInteract(worker(server), pos);
  }

  protected boolean reach(BlockPos pos) {
    var destination = Vec3.atCenterOf(pos);
    if (getEyePosition().distanceToSqr(destination) > Math.pow(SlimeStat.REACH.value(this), 2))
      return false;
    var hit =
        LoadedBlockView.clip(
            level(),
            new ClipContext(
                getEyePosition(),
                destination,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this));
    return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
  }

  protected List<BlockPos> perimeter(BlockPos center) {
    var side = getTunnelDirection().getClockWise();
    var result = new ArrayList<BlockPos>();
    for (int width = getTunnelMinWidth(); width < getTunnelMinWidth() + getTunnelSize(); width++) {
      result.add(center.relative(side, width).below());
      result.add(center.relative(side, width).above(getTunnelSize()));
    }
    for (int height = 0; height < getTunnelSize(); height++) {
      result.add(center.relative(side, getTunnelMinWidth() - 1).above(height));
      result.add(center.relative(side, getTunnelMinWidth() + getTunnelSize()).above(height));
    }
    return result;
  }

  protected boolean line(ServerLevel server, BlockPos pos) {
    var before = server.getBlockState(pos);
    if (!before.canBeReplaced()) return true;
    if (before.hasBlockEntity() || !permitted(server, pos)) {
      tunnelStatus = Status.UNREACHABLE;
      return false;
    }
    if (getStamina() < 1) {
      tunnelStatus = Status.RESTING;
      return false;
    }
    int slot = materialSlot();
    if (slot < 0) {
      tunnelStatus = Status.NO_MATERIAL;
      return false;
    }
    if (!reach(pos)) {
      tunnelStatus = Status.MOVING;
      approach();
      return false;
    }
    ItemStack reserve = getBag().getStackInSlot(slot).copy();
    var placed = ((BlockItem) reserve.getItem()).getBlock().defaultBlockState();
    if (!placed.canSurvive(server, pos)
        || !server.isUnobstructed(
            placed, pos, net.minecraft.world.phys.shapes.CollisionContext.empty())) {
      tunnelStatus = Status.UNREACHABLE;
      return false;
    }
    var snapshot = BlockSnapshot.create(server.dimension(), server, pos);
    var player = worker(server);
    ItemStack previous = player.getMainHandItem();
    player.setItemInHand(InteractionHand.MAIN_HAND, reserve.copyWithCount(1));
    try {
      if (!server.setBlock(pos, placed, 3)) return false;
      if (ForgeEventFactory.onBlockPlace(player, snapshot, Direction.UP)
          || !canContinueWork(player)
          || !ItemStack.matches(reserve, getBag().getStackInSlot(slot))
          || !server.getBlockState(pos).equals(placed)
          || !getLiningTemplate().is(reserve.getItem())) {
        snapshot.restore(true, false);
        tunnelStatus = Status.UNREACHABLE;
        retryAt = tickCount + 100;
        return false;
      }
      getBag().extractItem(slot, 1, false);
      spendStamina(1);
      recordCompletedWork();
      madeProgress();
      tunnelStatus = Status.LINING;
      return false;
    } finally {
      player.setItemInHand(InteractionHand.MAIN_HAND, previous);
    }
  }

  private void approach() {
    var stand = getTunnelFront().relative(getTunnelDirection(), -1);
    moveToCenter(stand);
  }

  private void madeProgress() {
    travelBlocked = false;
    travelTicks = 0;
    lastTravelTick = -1;
    travelStand = null;
    closestTravelDistance = Double.POSITIVE_INFINITY;
  }

  protected void moveToCenter(BlockPos stand) {
    if (!stand.equals(travelStand)) {
      travelStand = stand;
      closestTravelDistance = Double.POSITIVE_INFINITY;
    }
    if (lastTravelTick != tickCount) {
      lastTravelTick = tickCount;
      double distance = stand.distToCenterSqr(position());
      if (distance < closestTravelDistance - .1) {
        closestTravelDistance = distance;
        travelTicks = 0;
        travelBlocked = false;
      } else if (++travelTicks > 400) travelBlocked = true;
    }
    var point =
        Vec3.atBottomCenterOf(stand)
            .add(-getTunnelDirection().getStepX() * .05, 0, -getTunnelDirection().getStepZ() * .05);
    // Native paths center this wide mob on a two-block node. Finish on the bore's centerline.
    if (position().distanceToSqr(point) < 4
        && level().noCollision(this, getBoundingBox().expandTowards(point.subtract(position())))) {
      getNavigation().stop();
      getMoveControl().setWantedPosition(point.x, point.y, point.z, .5);
    } else if (tickCount % 10 == 0) getNavigation().moveTo(point.x, point.y, point.z, 1);
  }

  private int fluidBit(BlockPos pos) {
    var center = getTunnelFront();
    var side = getTunnelDirection().getClockWise();
    int width =
        (pos.getX() - center.getX()) * side.getStepX()
            + (pos.getZ() - center.getZ()) * side.getStepZ();
    return 1 << ((pos.getY() - center.getY()) * 3 + width + 1);
  }

  protected boolean wasFluidDrained(BlockPos pos) {
    return (drainedFluidMask & fluidBit(pos)) != 0;
  }

  protected void rememberDrainedFluid(BlockPos pos) {
    drainedFluidMask |= fluidBit(pos);
  }

  protected boolean advanceTunnel() {
    var center = getTunnelFront();
    if (position().distanceToSqr(Vec3.atBottomCenterOf(center)) > .5) {
      tunnelStatus = Status.MOVING;
      moveToCenter(center);
      return false;
    }
    step++;
    drainedFluidMask = 0;
    tunnelStatus = Status.IDLE;
    breaking.reset(this);
    return true;
  }

  private boolean canStore(List<ItemStack> drops) {
    var test = getBag().copyForSimulation();
    for (var drop : drops)
      if (!SlimeBag.insertItemStacked(test, drop.copy(), false).isEmpty()) return false;
    return true;
  }

  protected boolean mine(ServerLevel server, BlockPos pos) {
    var state = server.getBlockState(pos);
    if (state.isAir()) return true;
    if (getStamina() < 1) {
      tunnelStatus = Status.RESTING;
      return false;
    }
    if (!permitted(server, pos)
        || state.hasBlockEntity()
        || state.getDestroySpeed(server, pos) < 0
        || state.is(BlockTags.LOGS)
        || state.is(BlockTags.LEAVES)
        || QuarryStaircaseData.get(server).protects(pos)) {
      tunnelStatus = Status.UNREACHABLE;
      return false;
    }
    if (!reach(pos)) {
      breaking.reset(this);
      tunnelStatus = Status.MOVING;
      approach();
      return false;
    }
    if (!state.getFluidState().isEmpty()) {
      breaking.reset(this);
      if (!hasBucket()) {
        tunnelStatus = Status.NO_BUCKET;
        return false;
      }
      // Cleared cells may receive flowing water from the next face while the slime moves.
      // Do not spend another action on that same flow; newly formed sources are still removed.
      if (wasFluidDrained(pos) && !state.getFluidState().isSource()) return true;
      var player = worker(server);
      if (ForgeHooks.onBlockBreakEvent(server, GameType.SURVIVAL, player, pos) < 0
          || !canContinueWork(player)
          || !hasBucket()
          || !server.getBlockState(pos).equals(state)) {
        retryAt = tickCount + 100;
        tunnelStatus = Status.UNREACHABLE;
        return false;
      }
      var replacement =
          state.hasProperty(
                  net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
              ? state.setValue(
                  net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,
                  false)
              : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
      var snapshot = BlockSnapshot.create(server.dimension(), server, pos);
      if (!server.setBlock(pos, replacement, 3)) return false;
      if (ForgeEventFactory.onBlockPlace(player, snapshot, Direction.UP)
          || !canContinueWork(player)
          || !hasBucket()) {
        snapshot.restore(true, false);
        retryAt = tickCount + 100;
        tunnelStatus = Status.UNREACHABLE;
        return false;
      }
      if (!server.getBlockState(pos).equals(replacement)) return false;
      rememberDrainedFluid(pos);
      spendStamina(1);
      recordCompletedWork();
      madeProgress();
      return replacement.isAir();
    }
    int toolSlot = toolFor(state);
    if (toolSlot < 0) {
      requiredTool = state.is(BlockTags.MINEABLE_WITH_SHOVEL) ? 1 : 0;
      tunnelStatus = getTool(requiredTool).isEmpty() ? Status.NO_TOOL : Status.NEEDS_TIER;
      return false;
    }
    var tool = getTool(toolSlot);
    var originalTool = tool.copy();
    var player = worker(server);
    var previous = player.getMainHandItem();
    player.setItemInHand(InteractionHand.MAIN_HAND, tool);
    try {
      tunnelStatus = Status.MINING;
      if (!breaking.advance(this, player, pos, state, tool)) return false;
      int xp = ForgeHooks.onBlockBreakEvent(server, GameType.SURVIVAL, player, pos);
      if (xp < 0
          || !canContinueWork(player)
          || !server.getBlockState(pos).equals(state)
          || !ItemStack.matches(originalTool, getTool(toolSlot))) {
        breaking.reset(this);
        retryAt = tickCount + 100;
        tunnelStatus = Status.UNREACHABLE;
        return false;
      }
      List<ItemStack> drops =
          server.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)
              ? Block.getDrops(state, server, pos, null, player, tool)
              : List.of();
      if (!canStore(drops)) {
        breaking.reset(this);
        tunnelStatus = Status.FULL;
        return false;
      }
      state.getBlock().playerWillDestroy(server, pos, state, player);
      if (!canContinueWork(player)
          || !server.getBlockState(pos).equals(state)
          || !ItemStack.matches(originalTool, getTool(toolSlot))
          || !canStore(drops)
          || !state.onDestroyedByPlayer(server, pos, player, true, server.getFluidState(pos))) {
        breaking.reset(this);
        return false;
      }
      state.getBlock().destroy(server, pos, state);
      for (var drop : drops) SlimeBag.insertItemStacked(getBag(), drop, false);
      tool.mineBlock(server, state, pos, player);
      spendStamina(1);
      recordCompletedWork();
      madeProgress();
      if (xp > 0 && server.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS))
        SlimeToolExperience.award(server, Vec3.atCenterOf(pos), tool, xp);
      server.levelEvent(2001, pos, Block.getId(state));
      breaking.reset(this);
      return false;
    } finally {
      player.setItemInHand(InteractionHand.MAIN_HAND, previous);
    }
  }

  @Override
  protected void registerGoals() {
    super.registerGoals();
    goalSelector.addGoal(1, new TunnelGoal());
  }

  @Override
  public void tick() {
    super.tick();
    if (!level().isClientSide
        && isWorking()
        && tunnelStatus != Status.MINING
        && tunnelStatus != Status.LINING
        && tickCount % 20 == 0) recoverStamina(getStaminaRecoveryPerSecond());
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.TUNNEL_SLIME.get());
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putInt("TunnelDirection", getTunnelDirection().get3DDataValue());
    tag.put("TunnelLining", getLiningTemplate().save(new CompoundTag()));
    tag.putInt("TunnelStep", step);
    tag.putInt("TunnelDrainedFluidMask", drainedFluidMask);
    tag.putBoolean("TunnelTravelBlocked", travelBlocked);
    tag.putInt("TunnelTravelTicks", travelTicks);
    tag.putBoolean("TunnelResting", resting);
    if (anchor != null) tag.putLong("TunnelAnchor", anchor.asLong());
    if (anchorDimension != null) tag.putString("TunnelDimension", anchorDimension);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    var direction = Direction.from3DDataValue(tag.getInt("TunnelDirection"));
    entityData.set(
        DIRECTION,
        direction.getAxis().isHorizontal()
            ? direction.get3DDataValue()
            : Direction.NORTH.get3DDataValue());
    var lining =
        tag.contains("TunnelLining")
            ? ItemStack.of(tag.getCompound("TunnelLining"))
            : new ItemStack(Items.COBBLESTONE);
    entityData.set(LINING, validLining(lining) ? lining.copyWithCount(1) : ItemStack.EMPTY);
    step = Math.max(1, Math.min(30_000_000, tag.getInt("TunnelStep")));
    drainedFluidMask = tag.getInt("TunnelDrainedFluidMask") & 511;
    travelBlocked = tag.getBoolean("TunnelTravelBlocked");
    travelTicks = Math.max(0, Math.min(401, tag.getInt("TunnelTravelTicks")));
    travelStand = getTunnelFront().relative(getTunnelDirection(), -1);
    closestTravelDistance = travelStand.distToCenterSqr(position());
    lastTravelTick = -1;
    anchor = tag.contains("TunnelAnchor") ? BlockPos.of(tag.getLong("TunnelAnchor")) : null;
    anchorDimension = tag.getString("TunnelDimension");
    resting = tag.getBoolean("TunnelResting");
    breaking.reset(this);
    tunnelStatus = Status.IDLE;
  }

  private final class TunnelGoal extends Goal {
    TunnelGoal() {
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
      breaking.reset(TunnelSlime.this);
      getNavigation().stop();
    }

    @Override
    public void tick() {
      if (!canUse() || !(level() instanceof ServerLevel server)) return;
      if (!ForgeEventFactory.getMobGriefingEvent(server, TunnelSlime.this)) {
        tunnelStatus = Status.DISABLED;
        stop();
        return;
      }
      if (anchor == null) restart();
      restockTools();
      if (getStamina() < 1) resting = true;
      if (resting && getStamina() < getMaxStamina() * .25F) {
        tunnelStatus = Status.RESTING;
        stop();
        return;
      }
      resting = false;
      if (tickCount < retryAt) return;
      if (getLiningTemplate().isEmpty() || !validLining(getLiningTemplate())) {
        tunnelStatus = Status.NO_TEMPLATE;
        stop();
        return;
      }
      if (tickTunnelWork(server)) return;
      var center = getTunnelFront();
      boolean lined = true;
      for (var pos : perimeter(center)) {
        if (!server.hasChunkAt(pos)) {
          tunnelStatus = Status.UNREACHABLE;
          stop();
          return;
        }
        if (!line(server, pos)) {
          if (tunnelStatus == Status.MOVING) lined = false;
          else return;
        }
      }
      for (int height = 2; height >= 0; height--)
        for (int width = -1; width <= 1; width++)
          if (!mine(
              server, center.relative(getTunnelDirection().getClockWise(), width).above(height)))
            return;
      if (!lined) return;
      advanceTunnel();
    }
  }
}
