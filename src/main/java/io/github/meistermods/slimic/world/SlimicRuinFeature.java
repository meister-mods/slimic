package io.github.meistermods.slimic.world;

import io.github.meistermods.slimic.block.SlimeChestBlockEntity;
import io.github.meistermods.slimic.entity.AncientSlime;
import io.github.meistermods.slimic.registry.ModBlocks;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModWorldBlocks;
import net.minecraft.core.*;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Chunk-contained remnants of the kingdom, including occupied depots, archives and royal wards. */
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
    if (!level.getLevel().dimension().equals(SlimicWorld.DIMENSION)) return false;
    if (SanctumPlacement.reserved(
            level.getLevel(), new net.minecraft.world.level.ChunkPos(context.origin()))
        || AssemblyPlacement.reserved(
            level.getLevel(), new net.minecraft.world.level.ChunkPos(context.origin())))
      return false;
    int x = (context.origin().getX() & ~15) + 8, z = (context.origin().getZ() & ~15) + 8;
    int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
    if (y < level.getSeaLevel() + 1 || y + 5 >= level.getMaxBuildHeight()) return false;
    var center = new BlockPos(x, y, z);
    int choice = random.nextInt(12);
    int type = choice < 4 ? 0 : choice < 8 ? 1 : choice == 8 ? 2 : choice < 11 ? 3 : 4;
    int width = type >= 3 ? 7 : 5, depth = type >= 3 ? 7 : 4;
    for (int dx = -width; dx <= width; dx++)
      for (int dz = -depth; dz <= depth; dz++) {
        if (Math.abs(level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x + dx, z + dz) - y) > 2)
          return false;
        for (int dy = -1; dy <= 4; dy++)
          if (level.getBlockEntity(center.offset(dx, dy, dz)) != null) return false;
      }
    for (int dx = -width; dx <= width; dx++)
      for (int dz = -depth; dz <= depth; dz++) {
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
        if (type == 2
            || Math.abs(dx) != width && Math.abs(dz) != depth
            || dz == -depth && Math.abs(dx) <= 1) continue;
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
      decoration(level, center.offset(0, 0, -2), "command_pedestal");
      var source = center.offset(1, 0, -2);
      var destination = center.offset(-1, 0, 2);
      setBlock(level, destination, ModBlocks.SLIME_CHEST.get().defaultBlockState());
      var ruin = new java.util.UUID(random.nextLong(), random.nextLong());
      for (var depot : new BlockPos[] {source, destination}) {
        if (level.getBlockEntity(depot) instanceof SlimeChestBlockEntity box) {
          box.getPersistentData().putUUID(AncientSlime.DEPOT_KEY, ruin);
          box.setChanged();
        }
      }
      if (level.getBlockEntity(source) instanceof SlimeChestBlockEntity box)
        box.setItem(
            0,
            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 8));
      if (type == 3) {
        for (int dx = -4; dx <= 4; dx += 2) {
          setBlock(level, center.offset(dx, 0, 5), Blocks.BOOKSHELF.defaultBlockState());
          decoration(level, center.offset(dx, 1, 5), "stone_tablet");
        }
        decoration(level, center.offset(0, 0, 4), "low_stone_table");
      } else if (type == 4) {
        decoration(level, center.offset(0, 0, 5), "royal_crest");
        for (int dx : new int[] {-5, 5}) {
          decoration(level, center.offset(dx, 0, -4), "royal_waymarker");
          decoration(level, center.offset(dx, 0, 4), "broken_pillar");
          decoration(level, center.offset(dx, 0, 0), "old_slime_banner");
        }
      }
      spawn(
          level,
          center.offset(-2, 0, 0),
          center,
          ruin,
          source,
          destination,
          ModEntities.ANCIENT_GUARD_SLIME.get(),
          type >= 3 ? 2 : 1);
      spawn(
          level,
          center.offset(2, 0, 0),
          center,
          ruin,
          source,
          destination,
          ModEntities.FORGOTTEN_TRANSPORT_SLIME.get(),
          1);
      if (type >= 3)
        spawn(
            level,
            center.offset(0, 0, 2),
            center,
            ruin,
            source,
            destination,
            ModEntities.ANCIENT_COMMAND_SLIME.get(),
            3);
      if (type == 4)
        spawn(
            level,
            center.offset(2, 0, 3),
            center,
            ruin,
            source,
            destination,
            ModEntities.ANCIENT_GUARD_SLIME.get(),
            3);
      if (type >= 3
          && level.getBlockEntity(chest)
              instanceof
              net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity container)
        container.setLootTable(
            SlimicWorld.id(type == 3 ? "chests/slimic_archive" : "chests/slimic_royal_ward"),
            random.nextLong());
    }
    return true;
  }

  private void spawn(
      WorldGenLevel level,
      BlockPos position,
      BlockPos home,
      java.util.UUID ruin,
      BlockPos source,
      BlockPos destination,
      net.minecraft.world.entity.EntityType<AncientSlime> type,
      int rank) {
    var slime = type.create(level.getLevel());
    if (slime == null) return;
    slime.moveTo(position.getX() + .5, position.getY(), position.getZ() + .5, 0, 0);
    slime.bindToRuin(home, ruin, source, destination, rank);
    net.minecraftforge.event.ForgeEventFactory.onFinalizeSpawn(
        slime,
        level,
        level.getCurrentDifficultyAt(position),
        net.minecraft.world.entity.MobSpawnType.STRUCTURE,
        null,
        null);
    if (!slime.isSpawnCancelled()
        && slime.isAlive()
        && slime.level() == level.getLevel()
        && slime.blockPosition().equals(position)
        && level.noCollision(slime)) level.addFreshEntity(slime);
  }

  private void decoration(WorldGenLevel level, BlockPos pos, String id) {
    var block = state(id);
    if (level.getBlockState(pos).isAir() && block.canSurvive(level, pos))
      setBlock(level, pos, block);
  }
}
