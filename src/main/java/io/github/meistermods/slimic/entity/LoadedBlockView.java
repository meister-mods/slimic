package io.github.meistermods.slimic.entity;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

/** A single-query terrain view; missing chunks are opaque and never acquired. */
final class LoadedBlockView implements BlockGetter {
  private final ServerLevel level;
  private final Long2ObjectMap<LevelChunk> chunks = new Long2ObjectOpenHashMap<>();

  LoadedBlockView(ServerLevel level) {
    this.level = level;
  }

  static BlockHitResult clip(Level level, ClipContext context) {
    return level instanceof ServerLevel server
        ? new LoadedBlockView(server).clip(context)
        : level.clip(context);
  }

  LevelChunk chunk(int x, int z) {
    long key = ChunkPos.asLong(x, z);
    if (!chunks.containsKey(key)) chunks.put(key, level.getChunkSource().getChunkNow(x, z));
    return chunks.get(key);
  }

  @Override
  public BlockState getBlockState(BlockPos pos) {
    if (level.isOutsideBuildHeight(pos)) return Blocks.AIR.defaultBlockState();
    var chunk = chunk(pos.getX() >> 4, pos.getZ() >> 4);
    return chunk == null ? Blocks.BEDROCK.defaultBlockState() : chunk.getBlockState(pos);
  }

  @Override
  public FluidState getFluidState(BlockPos pos) {
    if (level.isOutsideBuildHeight(pos)) return Fluids.EMPTY.defaultFluidState();
    var chunk = chunk(pos.getX() >> 4, pos.getZ() >> 4);
    return chunk == null ? Fluids.EMPTY.defaultFluidState() : chunk.getFluidState(pos);
  }

  @Override
  public BlockEntity getBlockEntity(BlockPos pos) {
    if (level.isOutsideBuildHeight(pos)) return null;
    var chunk = chunk(pos.getX() >> 4, pos.getZ() >> 4);
    return chunk == null ? null : chunk.getBlockEntity(pos);
  }

  @Override
  public int getHeight() {
    return level.getHeight();
  }

  @Override
  public int getMinBuildHeight() {
    return level.getMinBuildHeight();
  }
}
