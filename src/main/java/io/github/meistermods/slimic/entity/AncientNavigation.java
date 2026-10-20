package io.github.meistermods.slimic.entity;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;

/** Ruin patrols cannot request terrain outside the loaded neighborhood. */
final class AncientNavigation extends GroundPathNavigation {
  private PathFinder loadedFinder;
  private BlockPos loadedTarget;

  AncientNavigation(Mob mob, Level level) {
    super(mob, level);
  }

  @Override
  protected PathFinder createPathFinder(int nodes) {
    var nativeFinder = super.createPathFinder(nodes);
    loadedFinder = new PathFinder(nodeEvaluator, 2048);
    return nativeFinder;
  }

  @Override
  public void recomputePath() {
    stop();
  }

  @Override
  public BlockPos getTargetPos() {
    return loadedTarget;
  }

  @Override
  protected Path createPath(
      Set<BlockPos> targets, int padding, boolean above, int accuracy, float distance) {
    if (!(level instanceof ServerLevel server)
        || !level.hasChunkAt(mob.blockPosition())
        || targets.isEmpty()
        || !canUpdatePath()
        || mob.isPassenger()) return null;
    for (var target : targets)
      if (!level.hasChunkAt(target)
          || mob.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target)) > 48 * 48)
        return null;
    var region = new LoadedPathRegion(server, mob.blockPosition(), 48);
    Path result = loadedFinder.findPath(region, mob, targets, 48, accuracy, .5F);
    if (result != null) {
      loadedTarget = result.getTarget();
      timeoutCachedNode = Vec3i.ZERO;
      timeoutTimer = 0;
      timeoutLimit = 0;
    }
    return result;
  }
}
