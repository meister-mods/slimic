package io.github.meistermods.slimic.entity;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.tags.TagKey;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
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
    NEEDS_TIER
  }

  private static final EntityDataAccessor<Integer> ACTIVE_TOOL =
      SynchedEntityData.defineId(MiningSlime.class, EntityDataSerializers.INT);
  public static final TagKey<Item> QUARRY_SUPPORTS =
      TagKey.create(
          Registries.ITEM, ResourceLocation.fromNamespaceAndPath("slimic", "quarry_supports"));
  private int fullRevision = -1;
  private boolean working = true;
  private boolean resting;
  private boolean hasAnchor;
  private int chunkX, chunkZ;
  private QuarryPlan quarry;
  private int requiredTool;
  private boolean travelBlocked;
  private Status status = Status.IDLE;
  private BlockPos target;
  private Vec3 workStand;
  private float progress;
  private int targetTicks;
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
  public boolean needsUnloading() {
    return isWorking() && status == Status.FULL;
  }

  @Override
  public boolean needsReplacementTool() {
    return isWorking() && status == Status.NO_TOOL && getTool(requiredTool).isEmpty();
  }

  @Override
  public boolean acceptsWorkTool(ItemStack stack) {
    return matchesToolSlot(requiredTool, stack)
        && (!stack.isDamageableItem() || stack.getDamageValue() < stack.getMaxDamage());
  }

  @Override
  public boolean receiveWorkTool(ItemStack stack) {
    if (!needsReplacementTool() || !acceptsWorkTool(stack)) return false;
    setTool(requiredTool, stack.copyWithCount(1));
    return true;
  }

  @Override
  public boolean isWorkBlocked() {
    return isWorking()
        && (travelBlocked
            || status == Status.NO_TOOL
            || status == Status.FULL
            || status == Status.RESTING
            || status == Status.DISABLED
            || status == Status.NEEDS_SUPPORT
            || status == Status.UNREACHABLE
            || status == Status.NEEDS_TIER);
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
    clearTarget();
    status = Status.IDLE;
  }

  @Override
  public void onReleased() {
    ChunkPos chunk = chunkPosition();
    boolean changed = hasAnchor && (chunk.x != chunkX || chunk.z != chunkZ);
    if (!hasAnchor || changed || quarry == null) {
      chunkX = chunk.x;
      chunkZ = chunk.z;
      int top =
          Math.max(
              level().getMinBuildHeight(),
              Math.min(level().getMaxBuildHeight() - 3, blockPosition().getY() - 1));
      quarry = new QuarryPlan(chunk, top, top + 2);
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
  }

  @Override
  public void tick() {
    super.tick();
    if (!level().isClientSide) {
      if (!hasAnchor || quarry == null) onReleased();
      if (isWorking() && status != Status.MINING && tickCount % 20 == 0) recoverStamina(2);
    }
  }

  private void clearTarget() {
    if (target != null && level() instanceof ServerLevel server)
      server.destroyBlockProgress(getId(), target, -1);
    target = null;
    workStand = null;
    progress = 0;
    targetTicks = 0;
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
    var hit =
        level()
            .clip(
                new ClipContext(
                    eye,
                    Vec3.atCenterOf(pos),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    this));
    return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
  }

  private void approachTarget() {
    double reach = Math.min(SlimeStat.REACH.value(this), SlimeStat.DETECTION_RANGE.value(this));
    // Navigation accepts a waypoint within roughly half the slime's width.
    // Keep the requested standing point comfortably inside the tool's reach.
    double arrivalReach = Math.max(0, reach - 0.15);
    int radius = Math.min(3, (int) Math.ceil(reach));
    List<BlockPos> positions = new ArrayList<>();
    double offset = Math.floor(getBbWidth() + 1) * 0.5;
    for (int dx = -radius; dx <= radius; dx++)
      for (int dz = -radius; dz <= radius; dz++)
        for (int dy = -1; dy <= 2; dy++) {
          BlockPos feet = target.offset(dx, dy, dz);
          Vec3 stand = Vec3.atLowerCornerOf(feet).add(offset, 0, offset);
          double half = getBbWidth() / 2;
          if (stand.x - half < chunkX * 16
              || stand.x + half > chunkX * 16 + 16
              || stand.z - half < chunkZ * 16
              || stand.z + half > chunkZ * 16 + 16
              || level()
                  .getBlockState(feet.below())
                  .getCollisionShape(level(), feet.below())
                  .isEmpty()
              || !level().noCollision(this, getBoundingBox().move(stand.subtract(position()))))
            continue;
          Vec3 eye = stand.add(0, getEyeHeight(), 0);
          if (eye.distanceToSqr(Vec3.atCenterOf(target)) <= arrivalReach * arrivalReach
              && canSeeFrom(eye, target)) positions.add(feet);
        }
    positions.sort(
        Comparator.comparingDouble(
            feet -> position().distanceToSqr(Vec3.atLowerCornerOf(feet).add(offset, 0, offset))));
    for (BlockPos feet : positions) {
      var path = getNavigation().createPath(feet, 0);
      if (path != null && path.canReach()) {
        workStand = path.getEntityPosAtNode(this, path.getNodeCount() - 1);
        getNavigation().moveTo(path, 1);
        return;
      }
    }
    getNavigation().moveTo(target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5, 1);
  }

  private FakePlayer worker(ServerLevel server) {
    FakePlayer worker =
        FakePlayerFactory.get(server, new GameProfile(getOwnerUUID(), "SlimeWorker"));
    worker.setGameMode(GameType.SURVIVAL);
    worker.moveTo(getX(), getY(), getZ(), getYRot(), getXRot());
    worker.setOnGround(true);
    return worker;
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
      double reach = SlimeStat.REACH.value(this);
      if (getEyePosition().distanceToSqr(Vec3.atCenterOf(floor)) > reach * reach) {
        status = Status.MOVING;
        if (tickCount % 10 == 0)
          getNavigation().moveTo(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5, 1);
        return false;
      }
      if (!server.isUnobstructed(support, floor, CollisionContext.empty())) {
        getJumpControl().jump();
        status = Status.MOVING;
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
    }
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
      if (!ensureRamp(server)) return null;
      BlockPos best = null;
      double nearest = Double.MAX_VALUE;
      boolean blocked = false, needsTool = false, needsTier = false;
      int barriers = 0;
      for (int x = 0; x < 16; x++)
        for (int z = 0; z < 16; z++) {
          BlockPos pos = new BlockPos(chunkX * 16 + x, quarry.layer(), chunkZ * 16 + z);
          BlockState state = server.getBlockState(pos);
          if (state.getDestroySpeed(server, pos) < 0
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
    int toolSlot = toolFor(state);
    if (!terrain(target, state) || toolSlot < 0) {
      clearTarget();
      return;
    }
    double reach = Math.min(SlimeStat.REACH.value(this), SlimeStat.DETECTION_RANGE.value(this));
    if (getEyePosition().distanceToSqr(Vec3.atCenterOf(target)) > reach * reach
        || !canSee(target)) {
      status = Status.MOVING;
      if (++targetTicks > 400) {
        travelBlocked = true;
        skipped.put(target.asLong(), server.getGameTime() + 200);
        clearTarget();
        return;
      }
      if (tickCount % 10 == 0) approachTarget();
      if (workStand != null && getNavigation().isDone())
        getMoveControl().setWantedPosition(workStand.x, workStand.y, workStand.z, 0.5);
      return;
    }
    targetTicks = 0;
    entityData.set(ACTIVE_TOOL, toolSlot);
    getNavigation().stop();
    getLookControl().setLookAt(Vec3.atCenterOf(target));
    ItemStack tool = getTool(toolSlot);
    FakePlayer worker = worker(server);
    ItemStack previous = worker.getMainHandItem();
    worker.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tool);
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
      worker.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, previous);
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
    if (quarry != null) tag.put("MiningQuarry", quarry.save());
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    working = !tag.contains("MiningWorking") || tag.getBoolean("MiningWorking");
    resting = tag.getBoolean("MiningResting");
    hasAnchor = tag.getBoolean("MiningHasAnchor");
    chunkX = tag.getInt("MiningChunkX");
    chunkZ = tag.getInt("MiningChunkZ");
    requiredTool = Math.max(0, Math.min(1, tag.getInt("MiningRequiredTool")));
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
    clearTarget();
  }

  @Override
  public void remove(Entity.RemovalReason reason) {
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
      if (getPickaxe().isEmpty() && getShovel().isEmpty()) {
        status = Status.NO_TOOL;
        clearTarget();
        return;
      }
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
      if (target == null) {
        if (tickCount % 5 != 0) return;
        target = findTarget(server);
        if (target == null) return;
      }
      mine(server);
    }
  }
}
