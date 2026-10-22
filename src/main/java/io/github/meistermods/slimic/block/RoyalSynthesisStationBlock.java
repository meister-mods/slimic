package io.github.meistermods.slimic.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

public final class RoyalSynthesisStationBlock extends BaseEntityBlock {
  public static final BooleanProperty RUINED = BooleanProperty.create("ruined");

  public RoyalSynthesisStationBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(RUINED, false));
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(RUINED);
  }

  @Override
  public RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new RoyalSynthesisStationBlockEntity(pos, state);
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
    if (player instanceof ServerPlayer server) {
      if (state.getValue(RUINED))
        server.displayClientMessage(
            Component.translatable("message.slimic.synthesis_ruined"), true);
      else if (level.getBlockEntity(pos) instanceof RoyalSynthesisStationBlockEntity station)
        NetworkHooks.openScreen(server, station, pos);
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  public void onRemove(
      BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
    if (!state.is(next.getBlock())
        && level.getBlockEntity(pos) instanceof RoyalSynthesisStationBlockEntity station) {
      station.reconcile();
      Containers.dropContents(level, pos, station);
      level.updateNeighbourForOutputSignal(pos, this);
    }
    super.onRemove(state, level, pos, next, moving);
  }
}
