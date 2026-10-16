package io.github.meistermods.slimic.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A thin, solid-ground slime residue that breaks into one slag. */
public final class SlimeTrailBlock extends CarpetBlock {
  public SlimeTrailBlock(Properties properties) {
    super(properties);
  }

  @Override
  public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
    return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
  }
}
