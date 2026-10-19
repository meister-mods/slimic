package io.github.meistermods.slimic.world;

import io.github.meistermods.slimic.registry.ModBlocks;
import io.github.meistermods.slimic.registry.ModWorldBlocks;
import net.minecraft.core.*;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Chunk-contained workshops, depots and broken roads; no independent slime professions. */
public final class SlimicRuinFeature extends Feature<NoneFeatureConfiguration> {
  public SlimicRuinFeature() {
    super(NoneFeatureConfiguration.CODEC);
  }

  private static BlockState state(String id) {
    return ModWorldBlocks.block(id).defaultBlockState();
  }

  @Override
  public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
    var level = context.level();
    var random = context.random();
    int x = (context.origin().getX() & ~15) + 8, z = (context.origin().getZ() & ~15) + 8;
    int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
    if (y < level.getSeaLevel() + 1 || y + 5 >= level.getMaxBuildHeight()) return false;
    var center = new BlockPos(x, y, z);
    for (int dx = -5; dx <= 5; dx++)
      for (int dz = -4; dz <= 4; dz++) {
        if (Math.abs(level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x + dx, z + dz) - y) > 2)
          return false;
        for (int dy = -1; dy <= 4; dy++)
          if (level.getBlockEntity(center.offset(dx, dy, dz)) != null) return false;
      }
    int type = random.nextInt(3);
    for (int dx = -5; dx <= 5; dx++)
      for (int dz = -4; dz <= 4; dz++) {
        var pos = center.offset(dx, -1, dz);
        if (type == 2 && Math.abs(dz) > 1) continue;
        setBlock(
            level,
            pos,
            state(random.nextInt(5) == 0 ? "mossy_slimic_cobblestone" : "slimic_tiles"));
        for (int dy = 0; dy <= 3; dy++) {
          var above = pos.above(dy + 1);
          var old = level.getBlockState(above);
          if (old.is(SlimicPortalTravel.EXIT_TERRAIN) || old.canBeReplaced())
            setBlock(level, above, Blocks.AIR.defaultBlockState());
        }
        if (type == 2 || Math.abs(dx) != 5 && Math.abs(dz) != 4 || dz == -4 && Math.abs(dx) <= 1)
          continue;
        int height = random.nextInt(4);
        for (int dy = 0; dy < height; dy++)
          if (random.nextInt(5) != 0)
            setBlock(
                level,
                center.offset(dx, dy, dz),
                state(
                    random.nextInt(3) == 0
                        ? "cracked_slimic_stone_bricks"
                        : "slimic_stone_bricks"));
      }
    if (type == 2) {
      decoration(level, center.offset(-4, 0, 1), "weathered_waymarker");
      decoration(level, center.offset(4, 0, -1), "fallen_masonry_pile");
    } else {
      decoration(
          level, center.offset(-3, 0, 2), type == 0 ? "broken_workbench" : "abandoned_crate");
      decoration(
          level,
          center.offset(0, 0, 1),
          type == 0 ? "cracked_command_pedestal" : "cracked_supply_urn");
      decoration(level, center.offset(3, 0, -2), "gel_lantern");
      decoration(level, center.offset(-3, 0, -2), "broken_pillar");
      BlockPos chest = center.offset(3, 0, 2);
      setBlock(level, center.offset(1, 0, -2), ModBlocks.SLIME_CHEST.get().defaultBlockState());
      setBlock(level, chest, Blocks.CHEST.defaultBlockState());
      if (level.getBlockEntity(chest)
          instanceof
          net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity container)
        container.setLootTable(SlimicWorld.id("chests/slimic_ruins"), random.nextLong());
    }
    return true;
  }

  private void decoration(WorldGenLevel level, BlockPos pos, String id) {
    var block = state(id);
    if (level.getBlockState(pos).isAir() && block.canSurvive(level, pos))
      setBlock(level, pos, block);
  }
}
