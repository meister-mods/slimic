package io.github.meistermods.slimic.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

public final class QuarryNavigation extends GroundPathNavigation {
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
    return new PathFinder(nodeEvaluator, maximumNodes);
  }
}
