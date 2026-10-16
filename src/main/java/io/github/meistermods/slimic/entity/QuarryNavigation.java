package io.github.meistermods.slimic.entity;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

public final class QuarryNavigation extends GroundPathNavigation {
  private PathFinder quarryPathFinder;

  public QuarryNavigation(MiningSlime slime, Level level) {
    super(slime, level);
  }

  @Override
  protected PathFinder createPathFinder(int maximumNodes) {
    nodeEvaluator =
        new WalkNodeEvaluator() {
          @Override
          public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z, Mob mob) {
            if (mob instanceof MiningSlime miner
                && miner.isWorking()
                && miner.getQuarryPlan() != null) {
              var chunk = miner.getMiningChunk();
              int width = (int) Math.ceil(mob.getBbWidth());
              if ((x >> 4) != chunk.x
                  || (z >> 4) != chunk.z
                  || ((x + width - 1) >> 4) != chunk.x
                  || ((z + width - 1) >> 4) != chunk.z) return BlockPathTypes.BLOCKED;
            }
            return super.getBlockPathType(level, x, y, z, mob);
          }
        };
    nodeEvaluator.setCanPassDoors(true);
    quarryPathFinder = new PathFinder(nodeEvaluator, 4096);
    return new PathFinder(nodeEvaluator, maximumNodes);
  }

  @Override
  protected Path createPath(
      Set<BlockPos> targets, int padding, boolean above, int accuracy, float distance) {
    MiningSlime miner = (MiningSlime) mob;
    if (!miner.isWorking() || miner.getQuarryPlan() == null)
      return super.createPath(targets, padding, above, accuracy, distance);
    if (targets.isEmpty() || !canUpdatePath() || mob.isPassenger()) return null;
    var chunk = miner.getMiningChunk();
    if (!level.hasChunk(chunk.x, chunk.z)) return null;
    // The work area is known already. Detection range limits tool reach, not the
    // length of a route around a wide quarry or its perimeter staircase.
    PathNavigationRegion region =
        new PathNavigationRegion(
            level,
            new BlockPos(chunk.getMinBlockX(), mob.getBlockY() - 64, chunk.getMinBlockZ()),
            new BlockPos(chunk.getMaxBlockX(), mob.getBlockY() + 64, chunk.getMaxBlockZ()));
    return quarryPathFinder.findPath(region, mob, targets, 128, accuracy, 1);
  }

  @Override
  protected void followThePath() {
    if (!((MiningSlime) mob).isWorking()) {
      super.followThePath();
      return;
    }
    // A wide slime must reach a corner before turning. The native half-width
    // tolerance otherwise cuts into the wall beside a two-block-wide tread.
    maxDistanceToWaypoint = 0.2F;
    var position = getTempMobPos();
    var next = path.getNextEntityPos(mob);
    // Floating navigation reports the water surface, one block above a
    // submerged node. Accept that height so the route can advance past it.
    double heightTolerance =
        level.getFluidState(path.getNextNodePos()).is(FluidTags.WATER) ? 1.5 : 1;
    if (Math.abs(position.x - next.x) < maxDistanceToWaypoint
        && Math.abs(position.z - next.z) < maxDistanceToWaypoint
        && Math.abs(position.y - next.y) < heightTolerance) path.advance();
    doStuckDetection(position);
  }

  @Override
  public void recomputePath() {
    if (((MiningSlime) mob).isWorking()) {
      // Mining revalidates standing positions after terrain changes. Do not reuse
      // the native navigation's previous follow-order destination.
      stop();
    } else super.recomputePath();
  }
}
