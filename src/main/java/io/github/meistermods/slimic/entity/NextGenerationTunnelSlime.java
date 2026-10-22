package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.registry.ModItems;
import java.util.BitSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** A compact royal tunneler with a configurable square working face. */
public final class NextGenerationTunnelSlime extends TunnelSlime {
  private static final EntityDataAccessor<Integer> SIZE =
      SynchedEntityData.defineId(NextGenerationTunnelSlime.class, EntityDataSerializers.INT);
  private static final int SCAN_BUDGET = 128;
  private final BitSet drained = new BitSet();
  private BlockPos plannedFront;
  private int phase, cursor;

  public NextGenerationTunnelSlime(
      EntityType<? extends NextGenerationTunnelSlime> type, Level level) {
    super(type, level);
  }

  @Override
  protected void defineSynchedData() {
    super.defineSynchedData();
    entityData.define(SIZE, 3);
  }

  @Override
  public int getTunnelSize() {
    return entityData.get(SIZE);
  }

  public int maximumTunnelSize() {
    return Math.max(1, level().getHeight() - 2);
  }

  public boolean setTunnelSize(int size) {
    if (size < 1 || size > maximumTunnelSize()) return false;
    if (size == getTunnelSize()) return true;
    setWorking(false);
    setWaiting(true);
    entityData.set(SIZE, size);
    restart();
    return true;
  }

  private void resetPlane() {
    plannedFront = null;
    phase = cursor = 0;
    if (drained != null) drained.clear();
  }

  @Override
  protected void restart() {
    super.restart();
    resetPlane();
  }

  @Override
  protected boolean spendStamina(float amount) {
    return super.spendStamina(amount * .5F);
  }

  @Override
  public double blockWorkMultiplier(BlockState state) {
    return state.is(AncientTunnelSlime.ANCIENT_MASONRY) ? 1.5 : 1;
  }

  private int fluidIndex(BlockPos pos) {
    var front = getTunnelFront();
    var side = getTunnelDirection().getClockWise();
    int width =
        (pos.getX() - front.getX()) * side.getStepX()
            + (pos.getZ() - front.getZ()) * side.getStepZ()
            - getTunnelMinWidth();
    return (pos.getY() - front.getY()) * getTunnelSize() + width;
  }

  @Override
  protected boolean wasFluidDrained(BlockPos pos) {
    int bit = fluidIndex(pos);
    return bit >= 0 && bit < getTunnelSize() * getTunnelSize() && drained.get(bit);
  }

  @Override
  protected void rememberDrainedFluid(BlockPos pos) {
    int bit = fluidIndex(pos);
    if (bit >= 0 && bit < getTunnelSize() * getTunnelSize()) drained.set(bit);
  }

  @Override
  protected boolean mine(ServerLevel world, BlockPos pos) {
    var chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
    if (chunk == null) return false;
    var before = chunk.getBlockState(pos);
    // A changed fluid cell consumes the action even when extreme stamina cannot represent its cost.
    return super.mine(world, pos) && chunk.getBlockState(pos).equals(before);
  }

  @Override
  protected boolean reach(BlockPos pos) {
    var front = getTunnelFront();
    var side = getTunnelDirection().getClockWise();
    int width =
        (pos.getX() - front.getX()) * side.getStepX()
            + (pos.getZ() - front.getZ()) * side.getStepZ();
    int forward =
        (pos.getX() - front.getX()) * getTunnelDirection().getStepX()
            + (pos.getZ() - front.getZ()) * getTunnelDirection().getStepZ();
    int height = pos.getY() - front.getY();
    // Expanded reach is confined to the active face and its lining ring, from its entrance.
    if (forward == 0
        && width >= getTunnelMinWidth() - 1
        && width <= getTunnelMinWidth() + getTunnelSize()
        && height >= -1
        && height <= getTunnelSize()
        && position().distanceToSqr(Vec3.atBottomCenterOf(front.relative(getTunnelDirection(), -1)))
            < 4) return true;
    return super.reach(pos);
  }

  private boolean loaded(ServerLevel world, BlockPos pos) {
    if (world.isOutsideBuildHeight(pos) || !world.getWorldBorder().isWithinBounds(pos))
      return false;
    // Native break/place notifications also read neighboring blocks.
    for (var direction : net.minecraft.core.Direction.values()) {
      var neighbor = pos.relative(direction);
      if (world.getChunkSource().getChunkNow(neighbor.getX() >> 4, neighbor.getZ() >> 4) == null)
        return false;
    }
    return world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
  }

  @Override
  protected boolean tickTunnelWork(ServerLevel world) {
    var front = getTunnelFront();
    if (!front.equals(plannedFront)) {
      resetPlane();
      plannedFront = front;
    }
    var ring = perimeter(front);
    int size = getTunnelSize();
    for (int budget = 0; budget < SCAN_BUDGET; budget++) {
      if (phase == 0 || phase == 2) {
        if (cursor == ring.size()) {
          cursor = 0;
          phase++;
          continue;
        }
        var pos = ring.get(cursor);
        if (!loaded(world, pos)) {
          tunnelStatus = Status.UNREACHABLE;
          getNavigation().stop();
          return true;
        }
        if (!line(world, pos)) return true;
        cursor++;
      } else if (phase == 1) {
        if (cursor == size * size) {
          cursor = 0;
          phase = 2;
          continue;
        }
        var pos =
            front
                .relative(getTunnelDirection().getClockWise(), getTunnelMinWidth() + cursor % size)
                .above(size - 1 - cursor / size);
        if (!loaded(world, pos)) {
          tunnelStatus = Status.UNREACHABLE;
          getNavigation().stop();
          return true;
        }
        if (!mine(world, pos)) return true;
        cursor++;
      } else if (phase == 3) {
        if (cursor == size * size) {
          cursor = 0;
          phase = 4;
          continue;
        }
        var pos =
            front
                .relative(getTunnelDirection().getClockWise(), getTunnelMinWidth() + cursor % size)
                .above(size - 1 - cursor / size);
        if (!loaded(world, pos)) {
          tunnelStatus = Status.UNREACHABLE;
          getNavigation().stop();
          return true;
        }
        var state =
            world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4).getBlockState(pos);
        if (!state.isAir()
            && !(wasFluidDrained(pos)
                && !state.getFluidState().isSource()
                && !state.getFluidState().isEmpty())) {
          phase = 1;
          return true;
        }
        cursor++;
      } else {
        if (position().distanceToSqr(Vec3.atBottomCenterOf(front)) > .09) {
          tunnelStatus = Status.MOVING;
          moveToCenter(front);
          return true;
        }
        if (advanceTunnel()) resetPlane();
        return true;
      }
    }
    return true;
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putInt("NextGenerationTunnelSize", getTunnelSize());
    tag.putInt("NextGenerationTunnelPhase", phase);
    tag.putInt("NextGenerationTunnelCursor", cursor);
    tag.putLongArray("NextGenerationTunnelDrained", drained.toLongArray());
    if (plannedFront != null) tag.putLong("NextGenerationTunnelFront", plannedFront.asLong());
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    int size =
        tag.contains("NextGenerationTunnelSize") ? tag.getInt("NextGenerationTunnelSize") : 3;
    entityData.set(SIZE, Math.max(1, Math.min(maximumTunnelSize(), size)));
    phase = Math.max(0, Math.min(4, tag.getInt("NextGenerationTunnelPhase")));
    cursor =
        Math.max(
            0,
            Math.min(
                (phase == 1 || phase == 3)
                    ? getTunnelSize() * getTunnelSize()
                    : getTunnelSize() * 4,
                tag.getInt("NextGenerationTunnelCursor")));
    plannedFront =
        tag.contains("NextGenerationTunnelFront")
            ? BlockPos.of(tag.getLong("NextGenerationTunnelFront"))
            : null;
    drained.clear();
    long[] saved = tag.getLongArray("NextGenerationTunnelDrained");
    int words = (getTunnelSize() * getTunnelSize() + 63) / 64;
    if (saved.length <= words) drained.or(BitSet.valueOf(saved));
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.NEXT_GENERATION_TUNNEL_SLIME.get());
  }
}
