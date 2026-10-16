package io.github.meistermods.slimic.entity;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Flying companions route around unavailable terrain without loading it. */
final class LoadedFlyingNavigation extends FlyingPathNavigation {
  private static final int MAX_NODES = 4096;
  private PathFinder loadedFinder;
  private BlockPos loadedTarget;

  LoadedFlyingNavigation(Mob mob, Level level) {
    super(mob, level);
  }

  @Override
  protected PathFinder createPathFinder(int nodes) {
    PathFinder nativeFinder = super.createPathFinder(nodes);
    loadedFinder = new PathFinder(nodeEvaluator, MAX_NODES);
    return nativeFinder;
  }

  @Override
  public BlockPos getTargetPos() {
    return level instanceof ServerLevel ? loadedTarget : super.getTargetPos();
  }

  @Override
  public void recomputePath() {
    if (level instanceof ServerLevel) stop();
    else super.recomputePath();
  }

  @Override
  public Path createPath(BlockPos target, int accuracy) {
    if (level instanceof ServerLevel && !level.hasChunkAt(target)) return null;
    return super.createPath(target, accuracy);
  }

  @Override
  protected Path createPath(
      Set<BlockPos> targets, int padding, boolean above, int accuracy, float distance) {
    if (!(level instanceof ServerLevel server))
      return super.createPath(targets, padding, above, accuracy, distance);
    if (targets.isEmpty()
        || !canUpdatePath()
        || mob.isPassenger()
        || !level.hasChunkAt(mob.blockPosition())) return null;
    for (var target : targets) if (!level.hasChunkAt(target)) return null;
    float range = distance;
    for (var target : targets)
      range = Math.max(range, (float) Math.sqrt(target.distToCenterSqr(mob.position())) * 2 + 8);
    range = Math.max(24, range);
    var region =
        new LoadedPathRegion(server, mob.blockPosition(), (int) Math.ceil(range) + padding);
    float effort = Math.min(1, Math.max(512, range * 64) / MAX_NODES);
    Path result = loadedFinder.findPath(region, mob, targets, range, accuracy, effort);
    if (result != null) {
      loadedTarget = result.getTarget();
      timeoutCachedNode = Vec3i.ZERO;
      timeoutTimer = 0;
      timeoutLimit = 0;
    }
    return result;
  }

  @Override
  protected boolean canMoveDirectly(Vec3 from, Vec3 to) {
    if (!(level instanceof ServerLevel)) return super.canMoveDirectly(from, to);
    return LoadedBlockView.clip(
                level,
                new ClipContext(
                    from,
                    to.add(0, mob.getBbHeight() * .5, 0),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.ANY,
                    mob))
            .getType()
        == HitResult.Type.MISS;
  }

  @Override
  public boolean isStableDestination(BlockPos pos) {
    return (!(level instanceof ServerLevel) || level.hasChunkAt(pos))
        && super.isStableDestination(pos);
  }
}
