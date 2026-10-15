package io.github.meistermods.slimic.entity;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

public final class MiningSlime extends FriendlySlime {
  public enum Status {
    IDLE,
    MOVING,
    MINING,
    NO_TOOL,
    FULL,
    RESTING,
    WAITING,
    FOLLOWING,
    DISABLED
  }

  private final ItemStackHandler bag =
      new ItemStackHandler(27) {
        @Override
        public int getSlotLimit(int slot) {
          return slot < getCapacity() ? 64 : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
          return slot < getCapacity();
        }

        @Override
        protected void onContentsChanged(int slot) {
          bagRevision++;
        }
      };
  private int bagRevision;
  private int fullRevision = -1;
  private boolean working = true;
  private boolean resting;
  private boolean hasAnchor;
  private int chunkX;
  private int chunkZ;
  private Status status = Status.IDLE;
  private BlockPos target;
  private float progress;
  private int targetTicks;
  private Iterator<BlockPos> search;
  private BlockPos searchOrigin;
  private final Map<Long, Long> skipped = new HashMap<>();

  public MiningSlime(EntityType<? extends MiningSlime> type, Level level) {
    super(type, level);
    setDropChance(EquipmentSlot.MAINHAND, 2.0F);
  }

  @Override
  protected void registerGoals() {
    super.registerGoals();
    goalSelector.addGoal(1, new MiningGoal());
  }

  public static boolean isPickaxe(ItemStack stack) {
    return !stack.isEmpty() && stack.canPerformAction(ToolActions.PICKAXE_DIG);
  }

  public ItemStackHandler getBag() {
    return bag;
  }

  public ItemStack getPickaxe() {
    return getMainHandItem();
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

  public void setWorking(boolean value) {
    working = value;
    setWaiting(false);
    clearTarget();
    status = Status.IDLE;
  }

  @Override
  public boolean upgradeStat(SlimeStat stat) {
    boolean upgraded = super.upgradeStat(stat);
    if (upgraded && stat == SlimeStat.CAPACITY) bagRevision++;
    return upgraded;
  }

  @Override
  public void onReleased() {
    ChunkPos chunk = chunkPosition();
    chunkX = chunk.x;
    chunkZ = chunk.z;
    hasAnchor = true;
    skipped.clear();
    search = null;
    clearTarget();
  }

  @Override
  public void tick() {
    super.tick();
    if (!level().isClientSide) {
      if (!hasAnchor) onReleased();
      if (isWorking() && status != Status.MINING && tickCount % 20 == 0) recoverStamina(2.0F);
    }
  }

  private void clearTarget() {
    if (target != null && level() instanceof ServerLevel server)
      server.destroyBlockProgress(getId(), target, -1);
    target = null;
    progress = 0.0F;
    targetTicks = 0;
    getNavigation().stop();
  }

  private boolean validBlock(BlockPos position, BlockState state) {
    if (!hasAnchor
        || (position.getX() >> 4) != chunkX
        || (position.getZ() >> 4) != chunkZ
        || !level().hasChunkAt(position)
        || !state.is(BlockTags.MINEABLE_WITH_PICKAXE)
        || state.hasBlockEntity()
        || state.getDestroySpeed(level(), position) < 0.0F
        || !isPickaxe(getPickaxe())) return false;
    if (state.requiresCorrectToolForDrops() && !getPickaxe().isCorrectToolForDrops(state))
      return false;
    double detection = SlimeStat.DETECTION_RANGE.value(this);
    if (position.distToCenterSqr(position()) > detection * detection) return false;
    var hit =
        level()
            .clip(
                new ClipContext(
                    getEyePosition(),
                    Vec3.atCenterOf(position),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    this));
    return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(position);
  }

  private BlockPos findTarget() {
    int radius = (int) SlimeStat.DETECTION_RANGE.value(this);
    if (search == null || searchOrigin.distSqr(blockPosition()) > 4) {
      searchOrigin = blockPosition();
      search = BlockPos.withinManhattan(searchOrigin, radius, radius, radius).iterator();
    }
    long now = level().getGameTime();
    skipped.entrySet().removeIf(entry -> entry.getValue() <= now);
    for (int i = 0; i < 256 && search.hasNext(); i++) {
      BlockPos candidate = search.next().immutable();
      if (skipped.containsKey(candidate.asLong()) || !level().hasChunkAt(candidate)) continue;
      if (validBlock(candidate, level().getBlockState(candidate))) return candidate;
    }
    if (!search.hasNext()) search = null;
    return null;
  }

  private boolean canStore(List<ItemStack> drops) {
    ItemStackHandler simulation = new ItemStackHandler(getCapacity());
    for (int slot = 0; slot < simulation.getSlots(); slot++)
      simulation.setStackInSlot(slot, bag.getStackInSlot(slot).copy());
    for (ItemStack drop : drops) {
      if (!ItemHandlerHelper.insertItemStacked(simulation, drop.copy(), false).isEmpty())
        return false;
    }
    return true;
  }

  private FakePlayer worker(ServerLevel server) {
    FakePlayer worker =
        FakePlayerFactory.get(server, new GameProfile(getOwnerUUID(), "SlimeWorker"));
    worker.setGameMode(GameType.SURVIVAL);
    worker.moveTo(getX(), getY(), getZ(), getYRot(), getXRot());
    worker.setOnGround(true);
    return worker;
  }

  private void mine(ServerLevel server) {
    if (!validBlock(target, server.getBlockState(target))) {
      clearTarget();
      return;
    }
    double reach = SlimeStat.REACH.value(this);
    if (getEyePosition().distanceToSqr(Vec3.atCenterOf(target)) > reach * reach) {
      if (++targetTicks > 400) {
        skipped.put(target.asLong(), server.getGameTime() + 200);
        clearTarget();
        return;
      }
      status = Status.MOVING;
      if (tickCount % 10 == 0)
        getNavigation().moveTo(target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5, 1.0);
      return;
    }
    targetTicks = 0;
    getNavigation().stop();
    getLookControl().setLookAt(Vec3.atCenterOf(target));
    BlockState state = server.getBlockState(target);
    FakePlayer worker = worker(server);
    ItemStack previous = worker.getMainHandItem();
    worker.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, getPickaxe());
    try {
      status = Status.MINING;
      progress +=
          state.getDestroyProgress(worker, server, target) * SlimeStat.WORK_SPEED.value(this);
      server.destroyBlockProgress(getId(), target, Math.min(9, (int) (progress * 10.0F)));
      if (progress < 1.0F) return;
      int experience = ForgeHooks.onBlockBreakEvent(server, GameType.SURVIVAL, worker, target);
      if (experience < 0 || !server.getBlockState(target).equals(state)) {
        skipped.put(target.asLong(), server.getGameTime() + 200);
        clearTarget();
        return;
      }
      List<ItemStack> drops =
          server.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)
              ? Block.getDrops(state, server, target, null, worker, getPickaxe())
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
      for (ItemStack drop : drops) ItemHandlerHelper.insertItemStacked(bag, drop, false);
      getPickaxe().mineBlock(server, state, mined, worker);
      spendStamina(1.0F);
      recordCompletedWork();
      server.levelEvent(2001, mined, Block.getId(state));
      if (experience > 0 && server.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
        ExperienceOrb.award(server, Vec3.atCenterOf(mined), experience);
      }
      clearTarget();
      status = Status.IDLE;
    } finally {
      worker.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, previous);
    }
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.put("MiningBag", bag.serializeNBT());
    tag.putBoolean("MiningWorking", working);
    tag.putBoolean("MiningResting", resting);
    tag.putBoolean("MiningHasAnchor", hasAnchor);
    tag.putInt("MiningChunkX", chunkX);
    tag.putInt("MiningChunkZ", chunkZ);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    CompoundTag inventory = tag.getCompound("MiningBag").copy();
    inventory.putInt("Size", 27);
    bag.deserializeNBT(inventory);
    working = !tag.contains("MiningWorking") || tag.getBoolean("MiningWorking");
    resting = tag.getBoolean("MiningResting");
    hasAnchor = tag.getBoolean("MiningHasAnchor");
    chunkX = tag.getInt("MiningChunkX");
    chunkZ = tag.getInt("MiningChunkZ");
    clearTarget();
  }

  @Override
  protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
    super.dropCustomDeathLoot(source, looting, recentlyHit);
    for (int slot = 0; slot < bag.getSlots(); slot++) {
      if (!bag.getStackInSlot(slot).isEmpty()) spawnAtLocation(bag.getStackInSlot(slot).copy());
      bag.setStackInSlot(slot, ItemStack.EMPTY);
    }
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
      if (!ForgeEventFactory.getMobGriefingEvent(server, MiningSlime.this)) {
        status = Status.DISABLED;
        clearTarget();
        return;
      }
      if (!isPickaxe(getPickaxe())) {
        status = Status.NO_TOOL;
        clearTarget();
        return;
      }
      if (status == Status.FULL && fullRevision == bagRevision) return;
      if (getStamina() < 1.0F) resting = true;
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
        target = findTarget();
        if (target == null) {
          status = Status.IDLE;
          return;
        }
      }
      mine(server);
    }
  }
}
