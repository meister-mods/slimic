package io.github.meistermods.slimic.block;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Freestanding, waterloggable ruin props; each model uses matching collision cuboids. */
public final class RuinDecorationBlock extends Block implements SimpleWaterloggedBlock {
  public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
  public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
  private final VoxelShape[] shapes = new VoxelShape[4];

  public RuinDecorationBlock(Properties properties, List<double[]> boxes) {
    super(properties);
    for (int rotation = 0; rotation < 4; rotation++) {
      VoxelShape shape = Shapes.empty();
      for (double[] original : boxes) {
        double[] b = original.clone();
        for (int i = 0; i < rotation; i++)
          b = new double[] {16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]};
        shape = Shapes.or(shape, box(b[0], b[1], b[2], b[3], b[4], b[5]));
      }
      shapes[rotation] = shape.optimize();
    }
    registerDefaultState(
        stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    return defaultBlockState()
        .setValue(FACING, context.getHorizontalDirection().getOpposite())
        .setValue(
            WATERLOGGED,
            context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER));
  }

  @Override
  public VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return shapes[
        switch (state.getValue(FACING)) {
          case EAST -> 1;
          case SOUTH -> 2;
          case WEST -> 3;
          default -> 0;
        }];
  }

  @Override
  public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
    return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
  }

  @Override
  public BlockState updateShape(
      BlockState state,
      Direction direction,
      BlockState neighbor,
      LevelAccessor level,
      BlockPos pos,
      BlockPos neighborPos) {
    if (state.getValue(WATERLOGGED))
      level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
    if (direction == Direction.DOWN && !canSurvive(state, level, pos))
      // Native destruction drops the prop and restores its fluid state.
      return Blocks.AIR.defaultBlockState();
    return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
  }

  @Override
  public FluidState getFluidState(BlockState state) {
    return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
  }

  @Override
  public BlockState rotate(BlockState state, Rotation rotation) {
    return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
  }

  @Override
  public BlockState mirror(BlockState state, Mirror mirror) {
    return rotate(state, mirror.getRotation(state.getValue(FACING)));
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FACING, WATERLOGGED);
  }
}
