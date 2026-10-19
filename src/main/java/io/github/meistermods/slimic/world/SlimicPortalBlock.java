package io.github.meistermods.slimic.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.*;

public final class SlimicPortalBlock extends Block {
  public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
  private static final VoxelShape X_SHAPE = box(0, 0, 6, 16, 16, 10);
  private static final VoxelShape Z_SHAPE = box(6, 0, 0, 10, 16, 16);

  public SlimicPortalBlock() {
    super(
        Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_GREEN)
            .strength(-1)
            .noCollission()
            .noOcclusion()
            .noLootTable()
            .lightLevel(s -> 11)
            .sound(SoundType.GLASS)
            .pushReaction(PushReaction.BLOCK));
    registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
  }

  @Override
  public VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return state.getValue(AXIS) == Direction.Axis.X ? X_SHAPE : Z_SHAPE;
  }

  @Override
  public BlockState updateShape(
      BlockState state,
      Direction direction,
      BlockState neighbor,
      LevelAccessor level,
      BlockPos pos,
      BlockPos neighborPos) {
    Direction.Axis axis = state.getValue(AXIS);
    if ((direction.getAxis() == Direction.Axis.Y || direction.getAxis() == axis)
        && (!neighbor.is(this) || neighbor.getValue(AXIS) != axis)) {
      var shape = SlimicPortalShape.find(level, pos, axis);
      if (shape == null || !shape.complete(level)) return Blocks.AIR.defaultBlockState();
    }
    return state;
  }

  @Override
  public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
    if (!level.isClientSide) SlimicPortalTravel.inside(entity, pos, state.getValue(AXIS));
  }

  @Override
  public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
    if (random.nextInt(5) == 0)
      level.addParticle(
          ParticleTypes.SPORE_BLOSSOM_AIR,
          pos.getX() + random.nextDouble(),
          pos.getY() + random.nextDouble(),
          pos.getZ() + random.nextDouble(),
          0,
          .015,
          0);
  }

  @Override
  public BlockState rotate(BlockState state, Rotation rotation) {
    return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90
        ? state.cycle(AXIS)
        : state;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(AXIS);
  }
}
