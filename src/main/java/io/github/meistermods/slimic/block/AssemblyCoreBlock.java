package io.github.meistermods.slimic.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class AssemblyCoreBlock extends BaseEntityBlock {
  public AssemblyCoreBlock(Properties properties) {
    super(properties);
  }

  @Override
  public RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new AssemblyCoreBlockEntity(pos, state);
  }

  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> type) {
    // The site keeps awakening even if somebody breaks its replaceable altar.
    return null;
  }

  @Override
  public InteractionResult use(
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    if (player.isSpectator() || !level.mayInteract(player, pos)) return InteractionResult.FAIL;
    if (player instanceof ServerPlayer server
        && level.getBlockEntity(pos) instanceof AssemblyCoreBlockEntity core) core.use(server);
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
