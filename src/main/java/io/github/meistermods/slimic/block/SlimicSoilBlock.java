package io.github.meistermods.slimic.block;

import javax.annotation.Nullable;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

/** Soil that supports ordinary planting and the native hoe/shovel tool actions. */
public final class SlimicSoilBlock extends Block {
  public SlimicSoilBlock(Properties properties) {
    super(properties);
  }

  @Override
  @Nullable
  public BlockState getToolModifiedState(
      BlockState state, UseOnContext context, ToolAction action, boolean simulate) {
    if (context.getClickedFace() != net.minecraft.core.Direction.DOWN
        && context.getLevel().getBlockState(context.getClickedPos().above()).isAir()) {
      if (action == ToolActions.HOE_TILL) return Blocks.FARMLAND.defaultBlockState();
      if (action == ToolActions.SHOVEL_FLATTEN) return Blocks.DIRT_PATH.defaultBlockState();
    }
    return super.getToolModifiedState(state, context, action, simulate);
  }
}
