package io.github.meistermods.slimic.entity;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

public final class MiningSlime extends BagSlime {
  public enum Status {
    IDLE,
    MOVING,
    MINING,
    NO_TOOL,
    FULL,
    RESTING,
    WAITING,
    FOLLOWING,
    DISABLED,
    NEEDS_SUPPORT,
    UNREACHABLE,
    COMPLETE,
    NEEDS_TIER,
    NO_BUCKET,
    DRAINING,
    NO_COBBLESTONE,
    WALLING
  }

  private static final EntityDataAccessor<Integer> ACTIVE_TOOL =
      SynchedEntityData.defineId(MiningSlime.class, EntityDataSerializers.INT);
  public static final TagKey<Item> QUARRY_SUPPORTS =
      TagKey.create(
          Registries.ITEM, ResourceLocation.fromNamespaceAndPath("slimic", "quarry_supports"));
  private int fullRevision = -1;
  private boolean working;
  private boolean resting;
  private boolean hasAnchor;
  private int chunkX, chunkZ;
  private QuarryPlan quarry;
  private QuarryWalls walls;
  private BlockPos wallTarget;
  private int requiredTool;
  private boolean travelBlocked;
  private Status status = Status.IDLE;
  private BlockPos target;
  private Vec3 workStand;
  private float progress;
  private BlockState breakingState;
  private int targetTicks;
  private Vec3 lastTravelPosition;
  private double closestTravelDistance = Double.MAX_VALUE;
  private BlockPos supportTarget;
  private long supportRetryAt;
  private int fluidCooldown;
  private int drainedWaterLayer = Integer.MIN_VALUE;
  private static final Map<ServerLevel, Map<Long, Set<MiningSlime>>> WATER_DRAINS =
      new WeakHashMap<>();
  private long trackedDrainChunk = Long.MIN_VALUE;
  private final Map<Long, Long> skipped = new HashMap<>();

  public MiningSlime(EntityType<? extends MiningSlime> type, Level level) {
    super(type, level, 27);
    setDropChance(EquipmentSlot.MAINHAND, 2);
    setDropChance(EquipmentSlot.OFFHAND, 2);
    setMaxUpStep(1.0F);
  }

  @Override
  protected void defineSynchedData() {
    super.defineSynchedData();
    entityData.define(ACTIVE_TOOL, 0);
  }

  @Override
  protected void registerGoals() {
    super.registerGoals();
    goalSelector.addGoal(1, new MiningGoal());
  }

  @Override
  protected PathNavigation createNavigation(Level level) {
    return new QuarryNavigation(this, level);
  }

  @Override
  public int getMaxFallDistance() {
    return isWorking() ? 1 : super.getMaxFallDistance();
  }

  @Override
  public void move(MoverType type, Vec3 movement) {
    if (isWorking() && quarry != null) {
      double radius = getBbWidth() / 2.0;
      double minX = chunkX * 16 + radius, maxX = chunkX * 16 + 16 - radius;
      double minZ = chunkZ * 16 + radius, maxZ = chunkZ * 16 + 16 - radius;
      // Hops retain momentum after a path stops. Keep their landing inside the work area.
      if (getX() >= minX && getX() <= maxX && getZ() >= minZ && getZ() <= maxZ)
        movement =
            new Vec3(
                Math.max(minX - getX(), Math.min(maxX - getX(), movement.x)),
                movement.y,
                Math.max(minZ - getZ(), Math.min(maxZ - getZ(), movement.z)));
    }
    super.move(type, movement);
  }

  public static boolean isPickaxe(ItemStack stack) {
    return !stack.isEmpty() && stack.canPerformAction(ToolActions.PICKAXE_DIG);
  }

  public static boolean isShovel(ItemStack stack) {
    return !stack.isEmpty() && stack.canPerformAction(ToolActions.SHOVEL_DIG);
  }

  public static boolean matchesToolSlot(int slot, ItemStack stack) {
    return slot == 0 ? isPickaxe(stack) : slot == 1 && isShovel(stack);
  }

  public ItemStack getPickaxe() {
    return getMainHandItem();
  }

  public ItemStack getShovel() {
    return getOffhandItem();
  }

  public ItemStack getTool(int slot) {
    return slot == 0 ? getPickaxe() : getShovel();
  }

  public void setTool(int slot, ItemStack stack) {
    setItemSlot(slot == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND, stack);
  }

  public ItemStack getRenderedTool() {
    return getTool(entityData.get(ACTIVE_TOOL));
  }

  public QuarryPlan getQuarryPlan() {
    return quarry;
  }

  @Override
  protected String bagTagName() {
    return "MiningBag";
  }

  @Override
  public boolean acceptsWorkTool(ItemStack stack) {
    if (status == Status.NO_COBBLESTONE)
      return needsWallMaterial()
          && stack.is(Items.COBBLESTONE)
          && ItemHandlerHelper.insertItemStacked(getBag(), stack.copyWithCount(1), true).isEmpty();
    if (status == Status.NO_BUCKET && !hasBucket())
      return stack.is(Items.BUCKET)
          && ItemHandlerHelper.insertItemStacked(getBag(), stack.copyWithCount(1), true).isEmpty();
    return matchesToolSlot(requiredTool, stack)
        && (!stack.isDamageableItem() || stack.getDamageValue() < stack.getMaxDamage());
  }

  @Override
  public boolean receiveWorkTool(ItemStack stack) {
    if (status == Status.NO_COBBLESTONE) return receiveWallMaterial(stack, 1) > 0;
    if (status == Status.NO_BUCKET && !hasBucket()) {
      if (!acceptsWorkTool(stack)) return false;
      if (!ItemHandlerHelper.insertItemStacked(getBag(), stack.copyWithCount(1), false).isEmpty())
        return false;
      status = Status.IDLE;
      fullRevision = -1;
      return true;
    }
    if (!needsReplacementTool() || !acceptsWorkTool(stack)) return false;
    setTool(requiredTool, stack.copyWithCount(1));
    return true;
  }

  private boolean hasCobblestone() {
    for (int slot = 0; slot < getCapacity(); slot++)
      if (getBag().getStackInSlot(slot).is(Items.COBBLESTONE)) return true;
    return false;
  }

  private boolean needsWallMaterial() {
    return !hasCobblestone()
        && walls != null
        && level() instanceof ServerLevel server
        && walls.pending().stream()
            .anyMatch(
                p ->
                    walls.exposed(server, p)
                        && level().hasChunkAt(p)
                        && level().getBlockState(p).canBeReplaced()
                        && !level().getBlockState(p).hasBlockEntity());
  }

  @Override
  public int workSupplyLimit() {
    return status == Status.NO_COBBLESTONE ? 64 : 1;
  }

  private int receiveWallMaterial(ItemStack stack, int limit) {
    if (!isWorking() || !acceptsWorkTool(stack)) return 0;
    var offered = stack.copyWithCount(Math.min(limit, stack.getCount()));
    int moved =
        offered.getCount()
            - ItemHandlerHelper.insertItemStacked(getBag(), offered, false).getCount();
    if (moved > 0) {
      status = Status.IDLE;
      fullRevision = -1;
    }
    return moved;
  }

  @Override
  public int receiveWorkSupply(ItemStack stack) {
    return status == Status.NO_COBBLESTONE
        ? receiveWallMaterial(stack, 64)
        : super.receiveWorkSupply(stack);
  }

  @Override
  protected WorkStatus describeWork() {
    var order = WorkStatus.Order.MINE;
    return switch (status) {
      case NO_COBBLESTONE ->
          !needsWallMaterial()
              ? WorkStatus.of(order, WorkStatus.State.ACTIVE)
              : WorkStatus.blocked(
                  order,
                  WorkStatus.Reason.COBBLESTONE_MISSING,
                  ItemHandlerHelper.insertItemStacked(
                              getBag(), new ItemStack(Items.COBBLESTONE), true)
                          .isEmpty()
                      ? new WorkStatus.Request[] {WorkStatus.Request.COBBLESTONE_SUPPLY}
                      : new WorkStatus.Request[] {
                        WorkStatus.Request.COBBLESTONE_SUPPLY, WorkStatus.Request.CARGO_PICKUP
                      });
      case NO_BUCKET ->
          hasBucket()
              ? WorkStatus.of(order, WorkStatus.State.ACTIVE)
              : WorkStatus.blocked(
                  order,
                  WorkStatus.Reason.BUCKET_MISSING,
                  ItemHandlerHelper.insertItemStacked(getBag(), new ItemStack(Items.BUCKET), true)
                          .isEmpty()
                      ? new WorkStatus.Request[] {WorkStatus.Request.BUCKET_SUPPLY}
                      : new WorkStatus.Request[] {
                        WorkStatus.Request.BUCKET_SUPPLY, WorkStatus.Request.CARGO_PICKUP
                      });
      case FULL ->
          WorkStatus.blocked(
              order, WorkStatus.Reason.INVENTORY_FULL, WorkStatus.Request.CARGO_PICKUP);
      case NO_TOOL ->
          getTool(requiredTool).isEmpty()
              ? WorkStatus.blocked(
                  order, WorkStatus.Reason.TOOL_MISSING, WorkStatus.Request.TOOL_SUPPLY)
              : WorkStatus.blocked(order, WorkStatus.Reason.TOOL_MISSING);
      case RESTING -> WorkStatus.blocked(order, WorkStatus.Reason.EXHAUSTED);
      case DISABLED -> WorkStatus.blocked(order, WorkStatus.Reason.DISABLED);
      case NEEDS_SUPPORT -> WorkStatus.blocked(order, WorkStatus.Reason.SUPPORT_MISSING);
      case NEEDS_TIER -> WorkStatus.blocked(order, WorkStatus.Reason.TOOL_TIER);
      case UNREACHABLE -> WorkStatus.blocked(order, WorkStatus.Reason.UNREACHABLE);
      case COMPLETE -> WorkStatus.of(order, WorkStatus.State.COMPLETE);
      default ->
          travelBlocked
              ? WorkStatus.blocked(order, WorkStatus.Reason.UNREACHABLE)
              : WorkStatus.of(
                  order, status == Status.IDLE ? WorkStatus.State.IDLE : WorkStatus.State.ACTIVE);
    };
  }

  public Status getMiningStatus() {
    if (isOrderedToSit()) return Status.WAITING;
    return working ? status : Status.FOLLOWING;
  }

  public ChunkPos getMiningChunk() {
    return new ChunkPos(chunkX, chunkZ);
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
    if (value && hasAnchor && !getMiningChunk().equals(chunkPosition())) onReleased();
    clearTarget();
    status = Status.IDLE;
    refreshFluidSourceGuard();
  }

  @Override
  public void onReleased() {
    ChunkPos chunk = chunkPosition();
    boolean changed =
        hasAnchor && (chunk.x != chunkX || chunk.z != chunkZ || hasChangedWorkDimension());
    super.onReleased();
    if (!hasAnchor || changed || quarry == null) {
      fluidCooldown = 0;
      drainedWaterLayer = Integer.MIN_VALUE;
      chunkX = chunk.x;
      chunkZ = chunk.z;
      int top =
          Math.max(
              level().getMinBuildHeight(),
              Math.min(level().getMaxBuildHeight() - 3, blockPosition().getY() - 1));
      quarry = new QuarryPlan(chunk, top, top + 2);
      walls = new QuarryWalls(chunk);
      if (changed) {
        working = true;
        travelBlocked = false;
        status = Status.IDLE;
        fullRevision = -1;
        setWaiting(false);
      }
    }
    hasAnchor = true;
    skipped.clear();
    clearTarget();
    refreshFluidSourceGuard();
  }

  @Override
  public void tick() {
    super.tick();
    if (!level().isClientSide) {
      if (!hasAnchor || quarry == null) onReleased();
      refreshFluidSourceGuard();
      if (isWorking() && status != Status.MINING && tickCount % 20 == 0)
        recoverStamina(getStaminaRecoveryPerSecond());
    }
  }

  private void clearTarget() {
    if (target != null && level() instanceof ServerLevel server)
      server.destroyBlockProgress(getId(), target, -1);
    target = null;
    wallTarget = null;
    workStand = null;
    progress = 0;
    breakingState = null;
    targetTicks = 0;
    lastTravelPosition = null;
    supportTarget = null;
    supportRetryAt = 0;
    getNavigation().stop();
  }

  private void restockTools() {
    for (int tool = 0; tool < 2; tool++) {
      if (!getTool(tool).isEmpty()) continue;
      for (int slot = 0; slot < getBag().getSlots(); slot++) {
        ItemStack stack = getBag().getStackInSlot(slot);
        if (matchesToolSlot(tool, stack)
            && (!stack.isDamageableItem() || stack.getDamageValue() < stack.getMaxDamage())) {
          setTool(tool, getBag().extractItem(slot, 1, false));
          break;
        }
      }
    }
  }

  private boolean terrain(BlockPos pos, BlockState state) {
    return quarry != null
        && quarry.contains(pos)
        && !quarry.isRampFloor(pos)
        && level().hasChunkAt(pos)
        && !state.is(BlockTags.LOGS)
        && !state.is(BlockTags.LEAVES)
        && !state.is(BlockTags.MINEABLE_WITH_AXE)
        && !state.is(BlockTags.MINEABLE_WITH_HOE)
        && !state.isAir()
        && state.getFluidState().isEmpty()
        && !state.hasBlockEntity()
        && state.getDestroySpeed(level(), pos) >= 0;
  }

  private int toolFor(BlockState state) {
    int preferred = state.is(BlockTags.MINEABLE_WITH_SHOVEL) ? 1 : 0;
    ItemStack tool = getTool(preferred);
    if (matchesToolSlot(preferred, tool)
        && (!state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state)))
      return preferred;
    int other = 1 - preferred;
    tool = getTool(other);
    if (matchesToolSlot(other, tool)
        && (!state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state)))
      return other;
    return -1;
  }

  private boolean canSee(BlockPos pos) {
    return canSeeFrom(getEyePosition(), pos);
  }

  private boolean canSeeFrom(Vec3 eye, BlockPos pos) {
    return canSeeFrom(eye, pos, false);
  }

  private boolean canSeeFrom(Vec3 eye, BlockPos pos, boolean placement) {
    boolean fluid = !placement && isRemovableFluid(level().getBlockState(pos));
    var hit =
        level()
            .clip(
                new ClipContext(
                    eye,
                    Vec3.atCenterOf(pos).add(0, placement ? 0.501 : 0, 0),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    this));
    return hit.getType() == HitResult.Type.BLOCK
        ? hit.getBlockPos().equals(pos)
        : placement || fluid;
  }

  public boolean hasBucket() {
    for (int slot = 0; slot < getCapacity(); slot++)
      if (getBag().getStackInSlot(slot).is(Items.BUCKET)) return true;
    return false;
  }

  private static boolean isRemovableFluid(BlockState state) {
    return state.getFluidState().is(FluidTags.WATER) || state.getFluidState().is(FluidTags.LAVA);
  }

  private int fluidNeighbours(ServerLevel server, BlockPos pos) {
    int count = 0;
    for (var direction : Direction.Plane.HORIZONTAL) {
      var neighbour = pos.relative(direction);
      if (server.hasChunkAt(neighbour) && isRemovableFluid(server.getBlockState(neighbour)))
        count++;
    }
    return count;
  }

  private BlockPos findFluid(ServerLevel server) {
    BlockPos best = null;
    int bestPriority = Integer.MAX_VALUE;
    double nearest = Double.MAX_VALUE;
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) {
        BlockPos pos = new BlockPos(chunkX * 16 + x, quarry.layer(), chunkZ * 16 + z);
        var state = server.getBlockState(pos);
        if (!isRemovableFluid(state)) continue;
        boolean visible = canSee(pos);
        double reach = Math.min(SlimeStat.REACH.value(this), SlimeStat.DETECTION_RANGE.value(this));
        boolean inReach =
            visible && getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= reach * reach;
        // Finish sources before clearing flow that they can replace indefinitely.
        int priority =
            (skipped.containsKey(pos.asLong()) ? 1000 : 0)
                + (state.getFluidState().isSource() ? 0 : 10000)
                + (inReach ? 0 : 10)
                + (inReach ? fluidNeighbours(server, pos) : 0);
        double distance = pos.distToCenterSqr(position()) + (visible ? 0 : 1024);
        if (priority < bestPriority || (priority == bestPriority && distance < nearest)) {
          bestPriority = priority;
          nearest = distance;
          best = pos;
        }
      }
    return best;
  }

  public void refreshFluidSourceGuard() {
    if (!(level() instanceof ServerLevel server)) return;
    long chunk =
        isAlive() && isWorking() && quarry != null && drainedWaterLayer == quarry.layer()
            ? getMiningChunk().toLong()
            : Long.MIN_VALUE;
    if (trackedDrainChunk == chunk) return;
    forgetFluidSourceGuard();
    if (chunk != Long.MIN_VALUE) {
      WATER_DRAINS
          .computeIfAbsent(server, key -> new HashMap<>())
          .computeIfAbsent(chunk, key -> Collections.newSetFromMap(new WeakHashMap<>()))
          .add(this);
      trackedDrainChunk = chunk;
    }
  }

  private void forgetFluidSourceGuard() {
    if (trackedDrainChunk == Long.MIN_VALUE || !(level() instanceof ServerLevel server)) return;
    var chunks = WATER_DRAINS.get(server);
    if (chunks != null) {
      var workers = chunks.get(trackedDrainChunk);
      if (workers != null) {
        workers.remove(this);
        if (workers.isEmpty()) chunks.remove(trackedDrainChunk);
      }
      if (chunks.isEmpty()) WATER_DRAINS.remove(server);
    }
    trackedDrainChunk = Long.MIN_VALUE;
  }

  public static void clearFluidSourceGuards(ServerLevel server) {
    WATER_DRAINS.remove(server);
  }

  public static boolean preventsFluidSourceFormation(ServerLevel server, BlockPos pos) {
    var chunks = WATER_DRAINS.get(server);
    if (chunks == null) return false;
    var workers = chunks.get(new ChunkPos(pos).toLong());
    if (workers == null) return false;
    for (var miner : workers)
      if (server.getEntity(miner.getUUID()) == miner && miner.preventsFluidSourceFormation(pos))
        return true;
    return false;
  }

  /** Prevent source regeneration only while this quarry layer is being drained. */
  public boolean preventsFluidSourceFormation(BlockPos pos) {
    return isAlive()
        && isTame()
        && getOwnerUUID() != null
        && isWorking()
        && quarry != null
        && drainedWaterLayer == quarry.layer()
        && pos.getY() == drainedWaterLayer
        && getMiningChunk().equals(new ChunkPos(pos))
        && ForgeEventFactory.getMobGriefingEvent(level(), this);
  }

  private boolean canDrain(ServerLevel server, BlockPos pos, double reach) {
    return getMiningChunk().equals(new ChunkPos(pos))
        && server.hasChunkAt(pos)
        && !skipped.containsKey(pos.asLong())
        && isRemovableFluid(server.getBlockState(pos))
        && getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= reach * reach
        && canSee(pos);
  }

  private List<BlockPos> fluidPatch(ServerLevel server, BlockPos origin, double reach) {
    List<BlockPos> best = new ArrayList<>(List.of(origin));
    int bestSources = -1;
    boolean sourcesOnly = server.getFluidState(origin).isSource();
    for (int x = -2; x <= 0; x++)
      for (int z = -2; z <= 0; z++) {
        List<BlockPos> patch = new ArrayList<>();
        int sources = 0;
        for (int dx = 0; dx < 3; dx++)
          for (int dz = 0; dz < 3; dz++) {
            var pos = origin.offset(x + dx, 0, z + dz);
            if (!canDrain(server, pos, reach)
                || (sourcesOnly && !server.getFluidState(pos).isSource())) continue;
            patch.add(pos);
            if (server.getFluidState(pos).isSource()) sources++;
          }
        if (sources > bestSources || (sources == bestSources && patch.size() > best.size())) {
          bestSources = sources;
          best = patch;
        }
      }
    best.sort(
        Comparator.comparingInt((BlockPos pos) -> server.getFluidState(pos).isSource() ? 0 : 1)
            .thenComparingInt(pos -> fluidNeighbours(server, pos))
            .thenComparingDouble(pos -> pos.distToCenterSqr(position())));
    return best;
  }

  private void drainFluid(ServerLevel server) {
    if (!hasBucket()) {
      status = Status.NO_BUCKET;
      clearTarget();
      return;
    }
    if (skipped.containsKey(target.asLong())) {
      status = Status.UNREACHABLE;
      clearTarget();
      return;
    }
    double reach = Math.min(SlimeStat.REACH.value(this), SlimeStat.DETECTION_RANGE.value(this));
    if (tickCount % 10 == 0) {
      var nearby = findFluid(server);
      if (nearby != null && !nearby.equals(target) && canDrain(server, nearby, reach)) {
        clearTarget();
        target = nearby;
      }
    }
    if (getEyePosition().distanceToSqr(Vec3.atCenterOf(target)) > reach * reach
        || !canSee(target)) {
      status = Status.MOVING;
      if (travelStalled(1)) {
        skipped.put(target.asLong(), server.getGameTime() + 200);
        travelBlocked = true;
        clearTarget();
      } else {
        if (tickCount % 10 == 0) approachTarget(target, false);
        finishApproach();
      }
      return;
    }
    getNavigation().stop();
    int removed = 0;
    var patch = fluidPatch(server, target, reach);
    for (var pos : patch) {
      if (getStamina() < 1 || !hasBucket() || !isWorking()) break;
      if (canDrain(server, pos, reach) && drainFluidCell(server, pos)) removed++;
    }
    if (removed > 0) {
      fluidCooldown = Math.max(1, (int) Math.ceil(20 / SlimeStat.WORK_SPEED.value(this)));
      travelBlocked = false;
      status = Status.DRAINING;
    } else {
      status = Status.UNREACHABLE;
      travelBlocked = true;
    }
    clearTarget();
  }

  private boolean drainFluidCell(ServerLevel server, BlockPos pos) {
    var state = server.getBlockState(pos);
    var player = worker(server);
    if (!server.getWorldBorder().isWithinBounds(pos)
        || !server.mayInteract(player, pos)
        || ForgeHooks.onBlockBreakEvent(server, GameType.SURVIVAL, player, pos) < 0
        || !server.getBlockState(pos).equals(state)
        || !hasBucket()) {
      skipped.put(pos.asLong(), server.getGameTime() + 200);
      return false;
    }
    var snapshot = BlockSnapshot.create(server.dimension(), server, pos);
    BlockState dry =
        state.hasProperty(BlockStateProperties.WATERLOGGED)
            ? state.setValue(BlockStateProperties.WATERLOGGED, false)
            : Blocks.AIR.defaultBlockState();
    if (!server.setBlock(pos, dry, 3)) return false;
    if (ForgeEventFactory.onBlockPlace(player, snapshot, Direction.UP)) {
      snapshot.restore(true, false);
      skipped.put(pos.asLong(), server.getGameTime() + 200);
      return false;
    }
    if (!server.getBlockState(pos).equals(dry)) return false;
    if (state.getFluidState().is(FluidTags.WATER)) {
      drainedWaterLayer = quarry.layer();
      refreshFluidSourceGuard();
    }
    spendStamina(1);
    recordCompletedWork();
    return true;
  }

  private void approachTarget(BlockPos destination, boolean placement) {
    approachTarget(destination, placement, false);
  }

  private void approachTarget(BlockPos destination, boolean placement, boolean wall) {
    // Keep following an existing route during a hop. Ground navigation cannot
    // create a replacement in midair, which must not cancel the valid old path.
    if (!onGround() && !isInWater()) return;
    if (workStand != null
        && getNavigation().isInProgress()
        && level().noCollision(this, getBoundingBox().move(workStand.subtract(position())))) return;
    double reach = Math.min(SlimeStat.REACH.value(this), SlimeStat.DETECTION_RANGE.value(this));
    // Navigation accepts a waypoint within roughly half the slime's width.
    // Keep the standing point inside tool reach, including the extra height
    // from floating over a submerged standing point while draining.
    double arrivalReach =
        Math.max(0, reach - (isRemovableFluid(level().getBlockState(destination)) ? 0.75 : 0.15));
    int radius = Math.min(3, (int) Math.ceil(reach));
    var positions = new HashSet<BlockPos>();
    double offset = Math.floor(getBbWidth() + 1) * 0.5;
    for (int dx = -radius; dx <= radius; dx++)
      for (int dz = -radius; dz <= radius; dz++)
        for (int dy = -1; dy <= 2; dy++) {
          BlockPos feet = destination.offset(dx, dy, dz);
          Vec3 stand = Vec3.atLowerCornerOf(feet).add(offset, 0, offset);
          double half = getBbWidth() / 2;
          var body = getBoundingBox().move(stand.subtract(position()));
          if (stand.x - half < chunkX * 16
              || stand.x + half > chunkX * 16 + 16
              || stand.z - half < chunkZ * 16
              || stand.z + half > chunkZ * 16 + 16
              || !level().noCollision(this, body)
              || !level().getBlockCollisions(this, body.move(0, -0.05, 0)).iterator().hasNext())
            continue;
          Vec3 eye = stand.add(0, getEyeHeight(), 0);
          if (eye.distanceToSqr(Vec3.atCenterOf(destination)) <= arrivalReach * arrivalReach
              && (wall ? canSeeWall(eye, destination) : canSeeFrom(eye, destination, placement)))
            positions.add(feet);
        }
    // Search all valid standing positions together instead of running a separate
    // pathfinder for every candidate. Never walk toward the solid target itself.
    Vec3 finish = null;
    double nearest = 2.25;
    for (BlockPos feet : positions) {
      Vec3 stand = Vec3.atLowerCornerOf(feet).add(offset, 0, offset);
      Vec3 delta = stand.subtract(position());
      double distance = delta.lengthSqr();
      if (distance < nearest
          && Math.abs(delta.y) < 0.1
          && level().noCollision(this, getBoundingBox().expandTowards(delta))
          && level()
              .getBlockCollisions(this, getBoundingBox().move(delta.scale(0.5)).move(0, -0.05, 0))
              .iterator()
              .hasNext()) {
        finish = stand;
        nearest = distance;
      }
    }
    if (finish != null) {
      // A wide-mob node can reject the last step beside a small floor gap.
      // Finish only a short, level, collision-free approach with support below it.
      workStand = finish;
      getNavigation().stop();
      finishApproach();
      return;
    }
    var path = getNavigation().createPath(positions, 0);
    if (path != null && path.canReach()) {
      workStand = path.getEntityPosAtNode(this, path.getNodeCount() - 1);
      getNavigation().moveTo(path, 1);
    } else {
      workStand = null;
      getNavigation().stop();
    }
  }

  private void finishApproach() {
    if (workStand != null
        && getNavigation().isDone()
        && position().subtract(workStand).horizontalDistanceSqr() <= 2.25)
      getMoveControl().setWantedPosition(workStand.x, workStand.y, workStand.z, 0.5);
  }

  private boolean travelStalled(int elapsed) {
    Vec3 destination =
        target != null
            ? Vec3.atCenterOf(target)
            : supportTarget != null ? Vec3.atCenterOf(supportTarget) : workStand;
    double distance =
        destination == null ? Double.MAX_VALUE : position().distanceToSqr(destination);
    if (lastTravelPosition == null || distance < closestTravelDistance - .05) {
      lastTravelPosition = position();
      closestTravelDistance = distance;
      targetTicks = 0;
    } else targetTicks += elapsed;
    return targetTicks > 400;
  }

  private FakePlayer worker(ServerLevel server) {
    FakePlayer worker =
        FakePlayerFactory.get(server, new GameProfile(getOwnerUUID(), "SlimeWorker"));
    worker.setGameMode(GameType.SURVIVAL);
    worker.moveTo(getX(), getY(), getZ(), getYRot(), getXRot());
    worker.setOnGround(true);
    return worker;
  }

  private boolean canSeeWall(Vec3 eye, BlockPos pos) {
    var hit =
        level()
            .clip(
                new ClipContext(
                    eye,
                    Vec3.atCenterOf(pos),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    this));
    return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
  }

  private boolean ensureWalls(ServerLevel server) {
    if (quarry.layer() < server.getMinBuildHeight()) return true;
    if (walls == null) walls = new QuarryWalls(getMiningChunk());
    walls.discover(server, quarry);
    if (walls.pending().isEmpty()) {
      if (wallTarget != null) clearTarget();
      return true;
    }
    var selected =
        walls.pending().stream()
            .filter(p -> walls.exposed(server, p))
            .filter(server::hasChunkAt)
            .min(
                Comparator.comparingInt(
                        (BlockPos p) -> QuarryWalls.fluid(server.getBlockState(p)) ? 0 : 1)
                    .thenComparingInt(p -> -p.getY())
                    .thenComparingDouble(p -> p.distToCenterSqr(position())))
            .orElse(null);
    if (selected == null) {
      if (walls.pending().stream().noneMatch(p -> walls.exposed(server, p))) {
        if (wallTarget != null) clearTarget();
        return true;
      }
      status = Status.UNREACHABLE;
      getNavigation().stop();
      return false;
    }
    if (!selected.equals(wallTarget)) {
      clearTarget();
      wallTarget = selected;
      supportTarget = selected;
    }
    var previous = server.getBlockState(selected);
    if (!previous.canBeReplaced() || previous.hasBlockEntity()) {
      status = Status.UNREACHABLE;
      travelBlocked = true;
      getNavigation().stop();
      return false;
    }
    int material = -1;
    for (int i = 0; i < getCapacity(); i++)
      if (getBag().getStackInSlot(i).is(Items.COBBLESTONE)) {
        material = i;
        break;
      }
    if (material < 0) {
      status = Status.NO_COBBLESTONE;
      getNavigation().stop();
      return false;
    }
    if (server.getGameTime() < supportRetryAt) {
      status = Status.UNREACHABLE;
      return false;
    }
    double reach = Math.min(SlimeStat.REACH.value(this), SlimeStat.DETECTION_RANGE.value(this));
    if (getEyePosition().distanceToSqr(Vec3.atCenterOf(selected)) > reach * reach
        || !canSeeWall(getEyePosition(), selected)) {
      status = Status.MOVING;
      if (travelStalled(1)) {
        travelBlocked = true;
        status = Status.UNREACHABLE;
        supportRetryAt = server.getGameTime() + 100;
        targetTicks = 0;
        workStand = null;
        getNavigation().stop();
      } else {
        if (tickCount % 10 == 0) approachTarget(selected, true, true);
        finishApproach();
      }
      return false;
    }
    var block = Blocks.COBBLESTONE.defaultBlockState();
    var player = worker(server);
    if (!server.getWorldBorder().isWithinBounds(selected)
        || !server.mayInteract(player, selected)
        || !server.isUnobstructed(block, selected, CollisionContext.empty())) {
      status = Status.UNREACHABLE;
      travelBlocked = true;
      getNavigation().stop();
      return false;
    }
    var snapshot = BlockSnapshot.create(server.dimension(), server, selected);
    getNavigation().stop();
    if (!server.setBlock(selected, block, 3)) {
      status = Status.UNREACHABLE;
      return false;
    }
    if (ForgeEventFactory.onBlockPlace(player, snapshot, Direction.UP)) {
      snapshot.restore(true, false);
      status = Status.UNREACHABLE;
      travelBlocked = true;
      supportRetryAt = server.getGameTime() + 100;
      return false;
    }
    if (!server.getBlockState(selected).equals(block)) {
      status = Status.UNREACHABLE;
      return false;
    }
    getBag().extractItem(material, 1, false);
    spendStamina(1);
    recordCompletedWork();
    walls.pending().remove(selected);
    clearTarget();
    fluidCooldown = Math.max(1, (int) Math.ceil(20 / SlimeStat.WORK_SPEED.value(this)));
    status = Status.WALLING;
    travelBlocked = false;
    return false;
  }

  private boolean ensureRamp(ServerLevel server) {
    for (BlockPos floor : quarry.floorsAt(quarry.layer())) {
      BlockState previous = server.getBlockState(floor);
      if (previous.isCollisionShapeFullBlock(server, floor) && !previous.hasBlockEntity()) continue;
      if (!previous.canBeReplaced() || previous.hasBlockEntity()) {
        travelBlocked = true;
        status = Status.UNREACHABLE;
        return false;
      }
      int material = -1;
      for (int slot = 0; slot < getBag().getSlots(); slot++) {
        ItemStack stack = getBag().getStackInSlot(slot);
        if (stack.is(QUARRY_SUPPORTS)
            && stack.getItem() instanceof BlockItem block
            && !(block.getBlock() instanceof FallingBlock)
            && block.getBlock().defaultBlockState().isCollisionShapeFullBlock(server, floor)) {
          material = slot;
          break;
        }
      }
      if (material < 0) {
        status = Status.NEEDS_SUPPORT;
        return false;
      }
      ItemStack stack = getBag().getStackInSlot(material);
      BlockState support = ((BlockItem) stack.getItem()).getBlock().defaultBlockState();
      if (!floor.equals(supportTarget)) {
        supportTarget = floor;
        supportRetryAt = 0;
        targetTicks = 0;
        lastTravelPosition = null;
        workStand = null;
      }
      if (server.getGameTime() < supportRetryAt) {
        status = Status.UNREACHABLE;
        return false;
      }
      double reach = Math.min(SlimeStat.REACH.value(this), SlimeStat.DETECTION_RANGE.value(this));
      if (getEyePosition().distanceToSqr(Vec3.atCenterOf(floor)) > reach * reach
          || !canSeeFrom(getEyePosition(), floor, true)) {
        status = Status.MOVING;
        if (travelStalled(5)) {
          travelBlocked = true;
          status = Status.UNREACHABLE;
          supportRetryAt = server.getGameTime() + 100;
          targetTicks = 0;
          workStand = null;
          getNavigation().stop();
        } else {
          if (tickCount % 10 == 0) approachTarget(floor, true);
          finishApproach();
        }
        return false;
      }
      if (!server.isUnobstructed(support, floor, CollisionContext.empty())) {
        if (travelStalled(5)) {
          travelBlocked = true;
          status = Status.UNREACHABLE;
          supportRetryAt = server.getGameTime() + 100;
          targetTicks = 0;
          workStand = null;
        } else {
          getJumpControl().jump();
          status = Status.MOVING;
        }
        return false;
      }
      BlockSnapshot snapshot = BlockSnapshot.create(server.dimension(), server, floor);
      if (!server.setBlockAndUpdate(floor, support)) {
        status = Status.UNREACHABLE;
        return false;
      }
      if (ForgeEventFactory.onBlockPlace(worker(server), snapshot, Direction.UP)) {
        snapshot.restore(true, false);
        travelBlocked = true;
        status = Status.UNREACHABLE;
        return false;
      }
      getBag().extractItem(material, 1, false);
      travelBlocked = false;
    }
    supportTarget = null;
    lastTravelPosition = null;
    targetTicks = 0;
    return true;
  }

  private BlockPos findTarget(ServerLevel server) {
    long now = server.getGameTime();
    skipped.values().removeIf(until -> until <= now);
    for (int pass = 0; pass < 4; pass++) {
      if (quarry.layer() < server.getMinBuildHeight()) {
        status = Status.COMPLETE;
        travelBlocked = false;
        return null;
      }
      if (!ensureWalls(server)) return null;
      BlockPos fluid = findFluid(server);
      if (fluid != null) {
        if (!hasBucket()) {
          status = Status.NO_BUCKET;
          getNavigation().stop();
          return null;
        }
        if (skipped.containsKey(fluid.asLong())) {
          status = Status.UNREACHABLE;
          return null;
        }
        return fluid;
      }
      if (getPickaxe().isEmpty() && getShovel().isEmpty()) {
        status = Status.NO_TOOL;
        return null;
      }
      if (!ensureRamp(server)) return null;
      BlockPos best = null;
      double nearest = Double.MAX_VALUE;
      boolean blocked = false, needsTool = false, needsTier = false;
      int barriers = 0;
      for (int x = 0; x < 16; x++)
        for (int z = 0; z < 16; z++) {
          BlockPos pos = new BlockPos(chunkX * 16 + x, quarry.layer(), chunkZ * 16 + z);
          BlockState state = server.getBlockState(pos);
          if ((state.getDestroySpeed(server, pos) < 0 || quarry.isRampFloor(pos))
              && state.isCollisionShapeFullBlock(server, pos)) barriers++;
          if (!terrain(pos, state)) continue;
          if (skipped.containsKey(pos.asLong())) {
            blocked = true;
            continue;
          }
          int tool = toolFor(state);
          if (tool < 0) {
            int desired = state.is(BlockTags.MINEABLE_WITH_SHOVEL) ? 1 : 0;
            if (getTool(desired).isEmpty()) {
              requiredTool = desired;
              needsTool = true;
            } else needsTier = true;
            continue;
          }
          double distance = pos.distToCenterSqr(position());
          if (!canSee(pos)) distance += 1024;
          if (distance < nearest) {
            best = pos;
            nearest = distance;
          }
        }
      if (best != null) return best;
      if (barriers == 256) {
        status = Status.COMPLETE;
        travelBlocked = false;
        return null;
      }
      if (needsTool || needsTier || blocked) {
        status = needsTool ? Status.NO_TOOL : needsTier ? Status.NEEDS_TIER : Status.UNREACHABLE;
        return null;
      }
      quarry.descend();
    }
    status = Status.IDLE;
    return null;
  }

  private boolean canStore(List<ItemStack> drops) {
    ItemStackHandler simulation = new ItemStackHandler(getCapacity());
    for (int slot = 0; slot < simulation.getSlots(); slot++)
      simulation.setStackInSlot(slot, getBag().getStackInSlot(slot).copy());
    for (ItemStack drop : drops)
      if (!ItemHandlerHelper.insertItemStacked(simulation, drop.copy(), false).isEmpty())
        return false;
    return true;
  }

  private void mine(ServerLevel server) {
    BlockState state = server.getBlockState(target);
    if (isRemovableFluid(state)) {
      drainFluid(server);
      return;
    }
    int toolSlot = toolFor(state);
    if (!terrain(target, state) || toolSlot < 0) {
      clearTarget();
      return;
    }
    if (breakingState != state) {
      breakingState = state;
      progress = 0;
      server.destroyBlockProgress(getId(), target, -1);
    }
    double reach = Math.min(SlimeStat.REACH.value(this), SlimeStat.DETECTION_RANGE.value(this));
    if (getEyePosition().distanceToSqr(Vec3.atCenterOf(target)) > reach * reach
        || !canSee(target)) {
      status = Status.MOVING;
      if (travelStalled(1)) {
        travelBlocked = true;
        skipped.put(target.asLong(), server.getGameTime() + 200);
        clearTarget();
        return;
      }
      if (tickCount % 10 == 0) approachTarget(target, false);
      finishApproach();
      return;
    }
    targetTicks = 0;
    entityData.set(ACTIVE_TOOL, toolSlot);
    getNavigation().stop();
    getLookControl().setLookAt(Vec3.atCenterOf(target));
    ItemStack tool = getTool(toolSlot);
    FakePlayer worker = worker(server);
    ItemStack previous = worker.getMainHandItem();
    worker.setItemInHand(InteractionHand.MAIN_HAND, tool);
    try {
      status = Status.MINING;
      progress +=
          state.getDestroyProgress(worker, server, target) * SlimeStat.WORK_SPEED.value(this);
      server.destroyBlockProgress(getId(), target, Math.min(9, (int) (progress * 10)));
      if (progress < 1) return;
      int experience = ForgeHooks.onBlockBreakEvent(server, GameType.SURVIVAL, worker, target);
      if (experience < 0 || !server.getBlockState(target).equals(state)) {
        travelBlocked = true;
        skipped.put(target.asLong(), server.getGameTime() + 200);
        clearTarget();
        return;
      }
      List<ItemStack> drops =
          server.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)
              ? Block.getDrops(state, server, target, null, worker, tool)
              : List.of();
      if (!canStore(drops)) {
        status = Status.FULL;
        fullRevision = bagRevision;
        clearTarget();
        return;
      }
      BlockPos mined = target;
      state.getBlock().playerWillDestroy(server, mined, state, worker);
      if (!state.onDestroyedByPlayer(server, mined, worker, true, server.getFluidState(mined))) {
        clearTarget();
        return;
      }
      state.getBlock().destroy(server, mined, state);
      for (ItemStack drop : drops) ItemHandlerHelper.insertItemStacked(getBag(), drop, false);
      tool.mineBlock(server, state, mined, worker);
      spendStamina(1);
      recordCompletedWork();
      travelBlocked = false;
      server.levelEvent(2001, mined, Block.getId(state));
      if (experience > 0 && server.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS))
        ExperienceOrb.award(server, Vec3.atCenterOf(mined), experience);
      clearTarget();
      status = Status.IDLE;
    } finally {
      worker.setItemInHand(InteractionHand.MAIN_HAND, previous);
    }
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putBoolean("MiningWorking", working);
    tag.putBoolean("MiningResting", resting);
    tag.putBoolean("MiningHasAnchor", hasAnchor);
    tag.putInt("MiningChunkX", chunkX);
    tag.putInt("MiningChunkZ", chunkZ);
    tag.putInt("MiningRequiredTool", requiredTool);
    tag.putBoolean("MiningTravelBlocked", travelBlocked);
    tag.putInt("MiningStatus", status.ordinal());
    tag.putInt("MiningFluidCooldown", fluidCooldown);
    if (walls != null) tag.putLongArray("MiningWallPending", walls.save());
    if (drainedWaterLayer != Integer.MIN_VALUE)
      tag.putInt("MiningDrainedWaterLayer", drainedWaterLayer);
    if (quarry != null) tag.put("MiningQuarry", quarry.save());
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    working = tag.getBoolean("MiningWorking");
    if (!tag.contains("MiningWorking")) setWaiting(true);
    resting = tag.getBoolean("MiningResting");
    hasAnchor = tag.getBoolean("MiningHasAnchor");
    chunkX = tag.getInt("MiningChunkX");
    chunkZ = tag.getInt("MiningChunkZ");
    requiredTool = Math.max(0, Math.min(1, tag.getInt("MiningRequiredTool")));
    fluidCooldown = Math.max(0, Math.min(200, tag.getInt("MiningFluidCooldown")));
    travelBlocked = tag.getBoolean("MiningTravelBlocked");
    int savedStatus = tag.getInt("MiningStatus");
    status =
        savedStatus >= 0 && savedStatus < Status.values().length
            ? Status.values()[savedStatus]
            : Status.IDLE;
    if (status == Status.FULL) fullRevision = bagRevision;
    quarry =
        tag.contains("MiningQuarry")
            ? QuarryPlan.load(
                getMiningChunk(),
                tag.getCompound("MiningQuarry"),
                level().getMinBuildHeight(),
                level().getMaxBuildHeight())
            : null;
    walls = quarry == null ? null : new QuarryWalls(getMiningChunk());
    if (walls != null)
      walls.load(
          tag.getLongArray("MiningWallPending"),
          quarry,
          level().getMinBuildHeight(),
          level().getMaxBuildHeight());
    drainedWaterLayer =
        tag.contains("MiningDrainedWaterLayer")
            ? tag.getInt("MiningDrainedWaterLayer")
            : Integer.MIN_VALUE;
    if (quarry == null || drainedWaterLayer != quarry.layer())
      drainedWaterLayer = Integer.MIN_VALUE;
    clearTarget();
  }

  @Override
  public void remove(Entity.RemovalReason reason) {
    forgetFluidSourceGuard();
    clearTarget();
    super.remove(reason);
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.MINING_SLIME.get());
  }

  private final class MiningGoal extends Goal {
    MiningGoal() {
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
      if (!(level() instanceof ServerLevel server)) return;
      if (!hasAnchor || quarry == null) onReleased();
      if (!server.hasChunk(chunkX, chunkZ)) {
        status = Status.UNREACHABLE;
        return;
      }
      if (!ForgeEventFactory.getMobGriefingEvent(server, MiningSlime.this)) {
        status = Status.DISABLED;
        clearTarget();
        return;
      }
      restockTools();
      if (status == Status.FULL && fullRevision == bagRevision) return;
      if (getStamina() < 1) resting = true;
      if (resting) {
        if (getStamina() < getMaxStamina() * 0.25F) {
          status = Status.RESTING;
          clearTarget();
          return;
        }
        resting = false;
      }
      if (fluidCooldown > 0) {
        fluidCooldown--;
        return;
      }
      if ((tickCount % 5 == 0 || wallTarget != null) && !ensureWalls(server)) return;
      if (target == null) {
        if (tickCount % 5 != 0) {
          if (supportTarget != null) finishApproach();
          return;
        }
        target = findTarget(server);
        if (target == null) return;
      }
      mine(server);
    }
  }
}
