package io.github.meistermods.slimic.world;

import io.github.meistermods.slimic.registry.ModWorldBlocks;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Nether-portal geometry with vanilla slime blocks substituted for the frame. */
public record SlimicPortalShape(BlockPos bottom, Direction.Axis axis, int width, int height) {
  public Direction across() {
    return axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
  }

  public BlockPos cell(int x, int y) {
    return bottom.relative(across(), x).above(y);
  }

  private static boolean frame(BlockState state) {
    return state.is(Blocks.SLIME_BLOCK);
  }

  private static boolean interior(BlockState state, Direction.Axis axis) {
    return state.isAir()
        || state.getBlock() instanceof io.github.meistermods.slimic.block.SlimeLightBlock
        || state.is(BlockTags.FIRE)
        || state.is(ModWorldBlocks.PORTAL.get()) && state.getValue(SlimicPortalBlock.AXIS) == axis;
  }

  @Nullable
  public static SlimicPortalShape find(LevelAccessor level, BlockPos start, Direction.Axis axis) {
    if (axis == Direction.Axis.Y || !interior(level.getBlockState(start), axis)) return null;
    Direction across = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
    BlockPos bottom = start;
    for (int i = 0;
        i < 21
            && bottom.getY() > level.getMinBuildHeight()
            && interior(level.getBlockState(bottom.below()), axis);
        i++) bottom = bottom.below();
    if (!frame(level.getBlockState(bottom.below()))) return null;
    for (int i = 0;
        i < 21 && interior(level.getBlockState(bottom.relative(across.getOpposite())), axis);
        i++) bottom = bottom.relative(across.getOpposite());
    if (!frame(level.getBlockState(bottom.relative(across.getOpposite())))) return null;
    int width = 0;
    while (width <= 21 && interior(level.getBlockState(bottom.relative(across, width)), axis))
      width++;
    if (width < 2 || width > 21 || !frame(level.getBlockState(bottom.relative(across, width))))
      return null;
    for (int x = 0; x < width; x++)
      if (!frame(level.getBlockState(bottom.relative(across, x).below()))) return null;
    for (int height = 0;
        height <= 21 && bottom.getY() + height < level.getMaxBuildHeight();
        height++) {
      boolean top = true;
      for (int x = 0; x < width; x++)
        top &= frame(level.getBlockState(bottom.relative(across, x).above(height)));
      if (top)
        return height >= 3 ? new SlimicPortalShape(bottom.immutable(), axis, width, height) : null;
      if (!frame(level.getBlockState(bottom.relative(across.getOpposite()).above(height)))
          || !frame(level.getBlockState(bottom.relative(across, width).above(height)))) return null;
      for (int x = 0; x < width; x++)
        if (!interior(level.getBlockState(bottom.relative(across, x).above(height)), axis))
          return null;
    }
    return null;
  }

  public boolean empty(LevelAccessor level) {
    for (int x = 0; x < width; x++)
      for (int y = 0; y < height; y++)
        if (level.getBlockState(cell(x, y)).is(ModWorldBlocks.PORTAL.get())) return false;
    return true;
  }

  public boolean complete(LevelAccessor level) {
    var expected =
        ModWorldBlocks.PORTAL.get().defaultBlockState().setValue(SlimicPortalBlock.AXIS, axis);
    for (int x = 0; x < width; x++)
      for (int y = 0; y < height; y++)
        if (!level.getBlockState(cell(x, y)).equals(expected)) return false;
    return true;
  }

  public boolean sameFrame(LevelAccessor level) {
    return equals(find(level, bottom, axis));
  }
}
