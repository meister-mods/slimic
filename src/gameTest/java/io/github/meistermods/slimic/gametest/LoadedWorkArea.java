package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.block.SlimeChestBlockEntity;
import io.github.meistermods.slimic.entity.TransportSlime;
import io.github.meistermods.slimic.registry.ModBlocks;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A loaded, chunk-aligned work area isolated from other test structures and natural terrain. */
final class LoadedWorkArea {
  private static final int OFFSET = 65536 + (int) Math.floorMod(System.nanoTime(), 100000L) * 16;
  private static int nextArea;
  private final GameTestHelper helper;
  private final int x, z;

  LoadedWorkArea(GameTestHelper helper) {
    this.helper = helper;
    x = OFFSET + nextArea++ * 64;
    z = -OFFSET;
    force(true);
    helper.runAfterDelay(4990, this::close);
    helper
        .getLevel()
        .getEntitiesOfClass(
            net.minecraft.world.entity.Entity.class,
            new AABB(x, 101, z, x + 16, helper.getLevel().getMaxBuildHeight(), z + 16),
            entity -> !(entity instanceof net.minecraft.world.entity.player.Player))
        .forEach(net.minecraft.world.entity.Entity::discard);
    for (int dx = 0; dx < 16; dx++)
      for (int dz = 0; dz < 16; dz++) {
        // Higher terrain can fall into the arena after its lower blocks are cleared.
        int ceiling =
            Math.max(
                107, helper.getLevel().getHeight(Heightmap.Types.WORLD_SURFACE, x + dx, z + dz));
        helper
            .getLevel()
            .setBlock(new BlockPos(x + dx, 100, z + dz), Blocks.BEDROCK.defaultBlockState(), 2);
        for (int y = 101; y < ceiling; y++)
          helper
              .getLevel()
              .setBlock(new BlockPos(x + dx, y, z + dz), Blocks.AIR.defaultBlockState(), 2);
        helper.assertTrue(
            helper.getLevel().getHeight(Heightmap.Types.WORLD_SURFACE, x + dx, z + dz) == 101,
            "Work arena must have no natural ceiling or falling material above its floor");
      }
  }

  Vec3 point(double dx, double y, double dz) {
    return new Vec3(x + dx, y, z + dz);
  }

  TransportSlime carrier(Vec3 offset) {
    TransportSlime carrier = ModEntities.TRANSPORT_SLIME.get().create(helper.getLevel());
    carrier.setWorking(true);
    carrier.moveTo(point(offset.x, offset.y, offset.z));
    carrier.tame(helper.makeMockSurvivalPlayer());
    carrier.onReleased();
    helper.getLevel().addFreshEntity(carrier);
    return carrier;
  }

  SlimeChestBlockEntity chest(int dx, int y, int dz) {
    BlockPos pos = new BlockPos(x + dx, y, z + dz);
    helper.getLevel().setBlockAndUpdate(pos, ModBlocks.SLIME_CHEST.get().defaultBlockState());
    return (SlimeChestBlockEntity) helper.getLevel().getBlockEntity(pos);
  }

  private void force(boolean value) {
    for (int dx = -1; dx <= 1; dx++)
      for (int dz = -1; dz <= 1; dz++)
        helper.getLevel().setChunkForced((x >> 4) + dx, (z >> 4) + dz, value);
    if (value)
      for (int dx = -1; dx <= 1; dx++)
        for (int dz = -1; dz <= 1; dz++) helper.getLevel().getChunk((x >> 4) + dx, (z >> 4) + dz);
  }

  void close() {
    force(false);
  }
}
