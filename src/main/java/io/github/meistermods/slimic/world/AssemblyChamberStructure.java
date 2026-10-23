package io.github.meistermods.slimic.world;

import com.mojang.serialization.Codec;
import io.github.meistermods.slimic.registry.ModWorldgen;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;

public final class AssemblyChamberStructure extends Structure {
  public static final Codec<AssemblyChamberStructure> CODEC =
      simpleCodec(AssemblyChamberStructure::new);

  public AssemblyChamberStructure(StructureSettings settings) {
    super(settings);
  }

  public static int floor(
      ChunkGenerator generator, RandomState random, LevelHeightAccessor height, int x, int z) {
    int minimum = Integer.MAX_VALUE;
    for (int dx : new int[] {-22, 0, 22})
      for (int dz : new int[] {-22, 0, 22}) {
        int y =
            generator.getBaseHeight(
                x + dx, z + dz, Heightmap.Types.WORLD_SURFACE_WG, height, random);
        if (y < generator.getSeaLevel() + 2) return Integer.MIN_VALUE;
        if (!generator
            .getBaseColumn(x + dx, z + dz, height, random)
            .getBlock(y - 1)
            .getFluidState()
            .isEmpty()) return Integer.MIN_VALUE;
        minimum = Math.min(minimum, y);
      }
    return minimum - 18 > height.getMinBuildHeight() + 6 ? minimum - 18 : Integer.MIN_VALUE;
  }

  @Override
  protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
    var level = SanctumPlacement.level(context.structureTemplateManager());
    if (level == null || !level.dimension().equals(SlimicWorld.DIMENSION)) return Optional.empty();
    int x = context.chunkPos().getMiddleBlockX(), z = context.chunkPos().getMiddleBlockZ();
    if (!level.getWorldBorder().isWithinBounds(new BlockPos(x - 23, 64, z - 64))
        || !level.getWorldBorder().isWithinBounds(new BlockPos(x + 23, 64, z + 23)))
      return Optional.empty();
    int y = floor(context.chunkGenerator(), context.randomState(), context.heightAccessor(), x, z);
    if (y == Integer.MIN_VALUE || !AssemblyPlacement.allowed(level, context.chunkPos()))
      return Optional.empty();
    int entrance =
        context
            .chunkGenerator()
            .getBaseHeight(
                x,
                z - 62,
                Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(),
                context.randomState());
    if (entrance - y < 12 || entrance - y > 36) return Optional.empty();
    BlockPos anchor = new BlockPos(x, y, z);
    return Optional.of(
        new GenerationStub(
            anchor, builder -> builder.addPiece(new AssemblyChamberPiece(anchor, entrance))));
  }

  @Override
  public StructureType<?> type() {
    return ModWorldgen.ASSEMBLY_CHAMBER.get();
  }
}
