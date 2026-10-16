package io.github.meistermods.slimic.entity;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

/** A one-block exterior lining leaves both columns of the quarry staircase usable. */
final class QuarryWalls {
  private final ChunkPos chunk;
  private final Set<BlockPos> pending = new LinkedHashSet<>();
  private long nextScan;
  private int scannedLayer = Integer.MIN_VALUE;

  QuarryWalls(ChunkPos chunk) {
    this.chunk = chunk;
  }

  static boolean fluid(BlockState state) {
    return state.getFluidState().is(FluidTags.WATER) || state.getFluidState().is(FluidTags.LAVA);
  }

  private boolean boundary(BlockPos pos) {
    int x = pos.getX() - chunk.getMinBlockX(), z = pos.getZ() - chunk.getMinBlockZ();
    return ((x == -1 || x == 16) && z >= 0 && z < 16) || ((z == -1 || z == 16) && x >= 0 && x < 16);
  }

  boolean exposed(ServerLevel level, BlockPos pos) {
    int x = pos.getX() - chunk.getMinBlockX();
    int z = pos.getZ() - chunk.getMinBlockZ();
    var inside =
        pos.relative(
            x == -1
                ? Direction.EAST
                : x == 16 ? Direction.WEST : z == -1 ? Direction.SOUTH : Direction.NORTH);
    return level.hasChunkAt(inside)
        && !level.getBlockState(inside).isCollisionShapeFullBlock(level, inside);
  }

  private static boolean open(ServerLevel level, BlockPos pos) {
    return level.hasChunkAt(pos) && level.getBlockState(pos).isAir();
  }

  void discover(ServerLevel level, QuarryPlan plan) {
    pending.removeIf(
        p ->
            p.getY() < plan.layer()
                || p.getY() > plan.topFloor() + 2
                || (p.getY() > plan.layer() && !exposed(level, p))
                || (level.hasChunkAt(p)
                    && level.getBlockState(p).isCollisionShapeFullBlock(level, p)
                    && !fluid(level.getBlockState(p))));
    if (plan.layer() == scannedLayer && level.getGameTime() < nextScan) return;
    nextScan = level.getGameTime() + 20;
    scannedLayer = plan.layer();
    int top = Math.min(plan.topFloor() + 2, plan.layer() + 2);
    for (var side : Direction.Plane.HORIZONTAL) {
      var along = side.getClockWise();
      for (int i = 0; i < 16; i++) {
        int x = side == Direction.WEST ? -1 : side == Direction.EAST ? 16 : i;
        int z = side == Direction.NORTH ? -1 : side == Direction.SOUTH ? 16 : i;
        var base = new BlockPos(chunk.getMinBlockX() + x, plan.layer(), chunk.getMinBlockZ() + z);
        for (int y = plan.layer(); y <= top; y++) {
          var pos = base.atY(y);
          if (!level.hasChunkAt(pos)) continue;
          var state = level.getBlockState(pos);
          if (fluid(state)) pending.add(pos);
          // A 2x2 opening belongs to the whole lining job, even after its first cell is filled.
          if (y >= plan.topFloor() || y >= top || !open(level, pos)) continue;
          for (var step : new Direction[] {along, along.getOpposite()}) {
            var neighbour = pos.relative(step);
            if (boundary(neighbour)
                && open(level, neighbour)
                && open(level, pos.above())
                && open(level, neighbour.above())) {
              pending.add(pos);
              pending.add(neighbour);
              pending.add(pos.above());
              pending.add(neighbour.above());
            }
          }
        }
      }
    }
  }

  Set<BlockPos> pending() {
    return pending;
  }

  long[] save() {
    return pending.stream().mapToLong(BlockPos::asLong).toArray();
  }

  void load(long[] saved, QuarryPlan plan, int minY, int maxY) {
    for (int i = 0; i < Math.min(256, saved.length); i++) {
      var pos = BlockPos.of(saved[i]);
      if (boundary(pos)
          && pos.getY() >= Math.max(minY, plan.layer())
          && pos.getY() <= Math.min(maxY - 1, Math.min(plan.topFloor() + 2, plan.layer() + 2)))
        pending.add(pos);
    }
  }
}
