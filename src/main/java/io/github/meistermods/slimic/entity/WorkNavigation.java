package io.github.meistermods.slimic.entity;

import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;

/** Work routes use loaded terrain; ordinary following retains native navigation. */
class WorkNavigation extends GroundPathNavigation {
  private static final int MAX_WORK_NODES = 4096;
  private PathFinder loadedFinder;
  private BlockPos workTarget;
  private final BooleanSupplier workMode;

  WorkNavigation(FriendlySlime slime, Level level) {
    this(
        slime,
        level,
        () ->
            slime.isWorking()
                || (slime.hasTunedStat(SlimeStat.DETECTION_RANGE)
                    && SlimeStat.DETECTION_RANGE.value(slime) > 1024));
  }

  WorkNavigation(Mob mob, Level level, BooleanSupplier workMode) {
    super(mob, level);
    this.workMode = workMode;
  }

  private boolean working() {
    return level instanceof ServerLevel && workMode.getAsBoolean();
  }

  @Override
  public BlockPos getTargetPos() {
    return working() ? workTarget : super.getTargetPos();
  }

  @Override
  public void recomputePath() {
    if (working()) stop();
    else super.recomputePath();
  }

  @Override
  public Path createPath(BlockPos target, int accuracy) {
    if (working() && !level.hasChunkAt(target)) return null;
    return super.createPath(target, accuracy);
  }

  @Override
  protected PathFinder createPathFinder(int nodes) {
    PathFinder nativeFinder = super.createPathFinder(nodes);
    loadedFinder = new PathFinder(nodeEvaluator, MAX_WORK_NODES);
    return nativeFinder;
  }

  @Override
  protected Path createPath(
      Set<BlockPos> targets, int padding, boolean above, int accuracy, float distance) {
    if (!working()) return super.createPath(targets, padding, above, accuracy, distance);
    if (targets.isEmpty()
        || !canUpdatePath()
        || mob.isPassenger()
        || !level.hasChunkAt(mob.blockPosition())) return null;
    for (var target : targets) if (!level.hasChunkAt(target)) return null;
    float range = distance;
    for (var target : targets) {
      range = Math.max(range, (float) Math.sqrt(target.distToCenterSqr(mob.position())) * 2 + 8);
      // Perimeter stairs require a long horizontal detour even when the lower target is close.
      range = Math.max(range, (float) Math.abs(target.getY() - mob.getY()) * 4 + 16);
    }
    range = Math.max(24, range);
    int radius = CreativeStats.asInt(Math.ceil(range) + padding);
    var origin = mob.blockPosition();
    var region = new LoadedPathRegion((ServerLevel) level, origin, radius);
    float effort = Math.min(1, Math.max(512, range * 64) / MAX_WORK_NODES);
    Path result = loadedFinder.findPath(region, mob, targets, range, accuracy, effort);
    if (result != null) {
      workTarget = result.getTarget();
      timeoutCachedNode = Vec3i.ZERO;
      timeoutTimer = 0;
      timeoutLimit = 0;
    }
    return result;
  }
}
