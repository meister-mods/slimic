package io.github.meistermods.slimic.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/** Missing chunks are solid boundaries; construction loads only the known origin. */
final class LoadedPathRegion extends PathNavigationRegion {
  private final ServerLevel world;
  private final BlockPos origin;
  private final int radius;
  private final LoadedBlockView blocks;

  LoadedPathRegion(ServerLevel world, BlockPos origin, int radius) {
    super(world, origin, origin);
    this.world = world;
    this.origin = origin;
    this.radius = radius;
    this.blocks = new LoadedBlockView(world);
  }

  private boolean contains(BlockPos pos) {
    return Math.abs((long) pos.getX() - origin.getX()) <= radius
        && Math.abs((long) pos.getY() - origin.getY()) <= radius
        && Math.abs((long) pos.getZ() - origin.getZ()) <= radius;
  }

  @Override
  public BlockState getBlockState(BlockPos pos) {
    if (world.isOutsideBuildHeight(pos)) return Blocks.AIR.defaultBlockState();
    if (!contains(pos)) return Blocks.BEDROCK.defaultBlockState();
    return blocks.getBlockState(pos);
  }

  @Override
  public FluidState getFluidState(BlockPos pos) {
    if (!contains(pos) || world.isOutsideBuildHeight(pos)) return Fluids.EMPTY.defaultFluidState();
    return blocks.getFluidState(pos);
  }

  @Override
  public BlockEntity getBlockEntity(BlockPos pos) {
    if (!contains(pos) || world.isOutsideBuildHeight(pos)) return null;
    return blocks.getBlockEntity(pos);
  }

  @Override
  public BlockGetter getChunkForCollisions(int x, int z) {
    return blocks.chunk(x, z);
  }
}
