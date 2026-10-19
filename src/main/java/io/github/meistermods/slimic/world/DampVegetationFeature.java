package io.github.meistermods.slimic.world;

import io.github.meistermods.slimic.registry.ModWorldBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class DampVegetationFeature extends Feature<NoneFeatureConfiguration> {
  private static final String[] PLANTS = {
    "slime_tuft", "slime_tuft", "slime_roots", "gel_cap", "glowing_bloom"
  };

  public DampVegetationFeature() {
    super(NoneFeatureConfiguration.CODEC);
  }

  @Override
  public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
    var level = context.level();
    var random = context.random();
    int placed = 0;
    int centerX = (context.origin().getX() & ~15) + 8,
        centerZ = (context.origin().getZ() & ~15) + 8;
    for (int i = 0; i < 14; i++) {
      int x = centerX + random.nextInt(13) - 6, z = centerZ + random.nextInt(13) - 6;
      var pos = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z);
      var plant = ModWorldBlocks.block(PLANTS[random.nextInt(PLANTS.length)]).defaultBlockState();
      if (level.getBlockState(pos).isAir() && plant.canSurvive(level, pos)) {
        setBlock(level, pos, plant);
        placed++;
      }
    }
    return placed > 0;
  }
}
