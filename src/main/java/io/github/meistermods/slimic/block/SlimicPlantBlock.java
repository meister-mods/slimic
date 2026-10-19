package io.github.meistermods.slimic.block;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class SlimicPlantBlock extends BushBlock {
  private final VoxelShape shape;

  public SlimicPlantBlock(Properties properties, int height) {
    super(properties);
    shape = box(2, 0, 2, 14, height, 14);
  }

  @Override
  protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
    return state.is(BlockTags.DIRT)
        || state.is(io.github.meistermods.slimic.registry.ModWorldBlocks.block("slime_silt"))
        || super.mayPlaceOn(state, level, pos);
  }

  @Override
  public VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return shape;
  }
}
