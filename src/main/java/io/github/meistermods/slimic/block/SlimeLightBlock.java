package io.github.meistermods.slimic.block;

import io.github.meistermods.slimic.registry.ModBlocks;
import io.github.meistermods.slimic.registry.ModParticles;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class SlimeLightBlock extends BaseEntityBlock {
  public static final BooleanProperty FIREFLY = BooleanProperty.create("firefly");

  public SlimeLightBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(FIREFLY, false));
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FIREFLY);
  }

  @Override
  public RenderShape getRenderShape(BlockState state) {
    return RenderShape.INVISIBLE;
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new SlimeLightBlockEntity(pos, state);
  }

  @Override
  public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
    super.onPlace(state, level, pos, old, moving);
    level.scheduleTick(pos, this, 10);
  }

  @Override
  public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    if (!(level.getBlockEntity(pos) instanceof SlimeLightBlockEntity light)
        || !light.active(level.getGameTime())) level.removeBlock(pos, false);
    else {
      if (state.getValue(FIREFLY) && !light.hasFireflies(level.getGameTime()))
        level.setBlock(pos, state.setValue(FIREFLY, false), 3);
      level.scheduleTick(pos, this, 10);
    }
  }

  @Override
  public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
    if (state.getValue(FIREFLY) && random.nextInt(3) == 0)
      level.addParticle(
          ModParticles.FIREFLY.get(), pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 0, 0, 0);
  }

  public static boolean illuminate(
      ServerLevel level, BlockPos pos, UUID source, int duration, boolean firefly) {
    if (!level.hasChunkAt(pos)
        || !level.isInWorldBounds(pos)
        || !level.getWorldBorder().isWithinBounds(pos)) return false;
    var state = level.getBlockState(pos);
    if (!state.is(ModBlocks.SLIME_LIGHT.get())) {
      if (!state.isAir() || !level.getFluidState(pos).isEmpty()) return false;
      if (!level.setBlock(
          pos, ModBlocks.SLIME_LIGHT.get().defaultBlockState().setValue(FIREFLY, firefly), 3))
        return false;
    } else if (firefly && !state.getValue(FIREFLY)) {
      level.setBlock(pos, state.setValue(FIREFLY, true), 3);
    }
    if (!(level.getBlockEntity(pos) instanceof SlimeLightBlockEntity light)) return false;
    light.lease(source, level.getGameTime() + Math.max(1, Math.min(400, duration)), firefly);
    level.scheduleTick(pos, ModBlocks.SLIME_LIGHT.get(), 10);
    return true;
  }
}
