package io.github.meistermods.slimic.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

public final class ColonyBondingStationBlock extends BaseEntityBlock {
  public ColonyBondingStationBlock(Properties properties) {
    super(properties);
  }

  @Override
  public RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new ColonyBondingStationBlockEntity(pos, state);
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
        && level.getBlockEntity(pos) instanceof ColonyBondingStationBlockEntity station)
      NetworkHooks.openScreen(server, station, pos);
    return InteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  public void onRemove(
      BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
    if (!state.is(next.getBlock())
        && level.getBlockEntity(pos) instanceof ColonyBondingStationBlockEntity station) {
      station.reconcile();
      Containers.dropContents(level, pos, station);
    }
    super.onRemove(state, level, pos, next, moving);
  }
}
