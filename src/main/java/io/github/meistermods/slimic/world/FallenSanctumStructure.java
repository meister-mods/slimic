package io.github.meistermods.slimic.world;

import com.mojang.serialization.Codec;
import io.github.meistermods.slimic.registry.ModWorldgen;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;

/** Five native pieces let ordinary structure generation handle chunk boundaries and persistence. */
public final class FallenSanctumStructure extends Structure {
  public static final Codec<FallenSanctumStructure> CODEC =
      simpleCodec(FallenSanctumStructure::new);

  public FallenSanctumStructure(StructureSettings settings) {
    super(settings);
  }

  public static int floor(
      ChunkGenerator generator, RandomState random, LevelHeightAccessor height, int x, int z) {
    int minimum = Integer.MAX_VALUE, maximum = Integer.MIN_VALUE;
    for (int dx : new int[] {-40, -20, 0, 20, 40})
      for (int dz : new int[] {-40, -20, 0, 20, 40}) {
        int y =
            generator.getBaseHeight(
                x + dx, z + dz, Heightmap.Types.WORLD_SURFACE_WG, height, random);
        if (y <= generator.getSeaLevel() + 2
            || y + SanctumArchitecture.HEIGHT >= height.getMaxBuildHeight())
          return Integer.MIN_VALUE;
        var column = generator.getBaseColumn(x + dx, z + dz, height, random);
        if (!column.getBlock(y - 1).getFluidState().isEmpty()) return Integer.MIN_VALUE;
        minimum = Math.min(minimum, y);
        maximum = Math.max(maximum, y);
      }
    return maximum - minimum > 14 ? Integer.MIN_VALUE : (minimum + maximum) / 2;
  }

  @Override
  protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
    int x = context.chunkPos().getMiddleBlockX(), z = context.chunkPos().getMiddleBlockZ();
    int y = floor(context.chunkGenerator(), context.randomState(), context.heightAccessor(), x, z);
    if (y == Integer.MIN_VALUE) return Optional.empty();
    var level = SanctumPlacement.level(context.structureTemplateManager());
    if (level != null && !SanctumPlacement.existingTerrainAllows(level, context.chunkPos()))
      return Optional.empty();
    BlockPos anchor = new BlockPos(x, y, z);
    int rotation = rotation(context.seed(), context.chunkPos().toLong());
    var entrance = FallenSanctumPiece.position(anchor, rotation, 0, 0, -40);
    int approachOffset =
        context
                .chunkGenerator()
                .getBaseHeight(
                    entrance.getX(),
                    entrance.getZ(),
                    Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor(),
                    context.randomState())
            - y;
    return Optional.of(
        new GenerationStub(
            anchor,
            builder -> {
              for (int zone = 0; zone < 5; zone++)
                builder.addPiece(new FallenSanctumPiece(anchor, rotation, zone, approachOffset));
            }));
  }

  public static int rotation(long seed, long chunk) {
    return (int) ((seed ^ Long.rotateLeft(chunk, 13)) & 3);
  }

  @Override
  public StructureType<?> type() {
    return ModWorldgen.FALLEN_SANCTUM.get();
  }
}
