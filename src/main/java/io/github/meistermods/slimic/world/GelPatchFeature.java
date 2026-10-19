package io.github.meistermods.slimic.world;

import io.github.meistermods.slimic.registry.ModWorldBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class GelPatchFeature extends Feature<NoneFeatureConfiguration> {
  public GelPatchFeature() {
    super(NoneFeatureConfiguration.CODEC);
  }

  @Override
  public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
    var level = context.level();
    var random = context.random();
    var origin = context.origin();
    int count = 0;
    for (int dx = -3; dx <= 3; dx++)
      for (int dz = -3; dz <= 3; dz++) {
        if (dx * dx + dz * dz > 10 || random.nextInt(4) == 0) continue;
        int x = origin.getX() + dx, z = origin.getZ() + dz;
        var pos = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1, z);
        var old = level.getBlockState(pos);
        if (!old.is(SlimicPortalTravel.EXIT_TERRAIN)
            || level.getBlockEntity(pos) != null
            || !old.getFluidState().isEmpty()
            || !level.getBlockState(pos.above()).isAir()) continue;
        String id =
            random.nextInt(24) == 0
                ? "luminous_slime"
                : random.nextBoolean() ? "congealed_slime" : "slime_mud";
        setBlock(level, pos, ModWorldBlocks.block(id).defaultBlockState());
        count++;
      }
    return count > 0;
  }
}
