package io.github.meistermods.slimic.block;

import io.github.meistermods.slimic.registry.ModBlocks;
import io.github.meistermods.slimic.world.SanctumSites;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class SanctumRelayBlock extends BaseEntityBlock {
  public static final IntegerProperty ZONE = IntegerProperty.create("zone", 1, 4);

  public SanctumRelayBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(ZONE, 1));
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(ZONE);
  }

  @Override
  public RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new SanctumRelayBlockEntity(pos, state);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    if (context.getLevel() instanceof ServerLevel level) {
      var site = SanctumSites.get(level).near(context.getClickedPos(), 96);
      if (site != null)
        for (int zone = 1; zone <= 4; zone++) {
          int z = -40 + zone * 16 + 8, y = z < 20 ? 0 : -Math.min(4, (z - 20) / 2);
          if (context.getClickedPos().equals(site.at(6, y, z)))
            return defaultBlockState().setValue(ZONE, zone);
        }
    }
    return defaultBlockState();
  }

  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> type) {
    return level.isClientSide
        ? null
        : createTickerHelper(
            type, ModBlocks.SANCTUM_RELAY_ENTITY.get(), SanctumRelayBlockEntity::tick);
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
        && level.getBlockEntity(pos) instanceof SanctumRelayBlockEntity relay) relay.use(server);
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
