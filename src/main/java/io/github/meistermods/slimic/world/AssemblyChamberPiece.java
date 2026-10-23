package io.github.meistermods.slimic.world;

import io.github.meistermods.slimic.block.AssemblyCoreBlockEntity;
import io.github.meistermods.slimic.registry.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** A forty-block clear arena, high vault, and permanent three-wide stair approach. */
public final class AssemblyChamberPiece extends StructurePiece {
  private static final TagKey<Block> TERRAIN =
      TagKey.create(
          net.minecraft.core.registries.Registries.BLOCK, SlimicWorld.id("fallen_sanctum_terrain"));
  private final BlockPos anchor;
  private final int entrance;

  public AssemblyChamberPiece(BlockPos anchor, int entrance) {
    super(
        ModWorldgen.ASSEMBLY_PIECE.get(),
        0,
        new BoundingBox(
            anchor.getX() - 23,
            anchor.getY() - 4,
            anchor.getZ() - 64,
            anchor.getX() + 23,
            entrance + 7,
            anchor.getZ() + 23));
    this.anchor = anchor.immutable();
    this.entrance = entrance;
  }

  public AssemblyChamberPiece(StructurePieceSerializationContext context, CompoundTag tag) {
    super(ModWorldgen.ASSEMBLY_PIECE.get(), tag);
    anchor = NbtUtils.readBlockPos(tag.getCompound("Anchor"));
    entrance = tag.getInt("Entrance");
  }

  @Override
  protected void addAdditionalSaveData(
      StructurePieceSerializationContext context, CompoundTag tag) {
    tag.put("Anchor", NbtUtils.writeBlockPos(anchor));
    tag.putInt("Entrance", entrance);
  }

  private BlockPos at(int x, int y, int z) {
    return anchor.offset(x, y, z);
  }

  private static BlockState material(String name) {
    return ModWorldBlocks.block(name).defaultBlockState();
  }

  private void put(WorldGenLevel world, BoundingBox clip, int x, int y, int z, BlockState state) {
    BlockPos pos = at(x, y, z);
    if (clip.isInside(pos) && world.getBlockEntity(pos) == null) world.setBlock(pos, state, 2);
  }

  private int pathFloor(int z) {
    return Math.max(
        0, Math.min(entrance - anchor.getY(), (-z - 22) * (entrance - anchor.getY()) / 40));
  }

  @Override
  public void postProcess(
      WorldGenLevel world,
      StructureManager manager,
      ChunkGenerator generator,
      RandomSource random,
      BoundingBox clip,
      ChunkPos chunk,
      BlockPos pivot) {
    // A partial structure bordering an upgraded world must yield to existing construction.
    for (int x = -23; x <= 23; x++)
      for (int z = -64; z <= 23; z++) {
        if (z < -23 && Math.abs(x) > 3 || !clip.isInside(at(x, 0, z))) continue;
        int floor = z < -22 ? pathFloor(z) : 0;
        int roof = z < -22 ? floor + 6 : 12;
        for (int y = floor - 4; y <= roof; y++) {
          BlockPos pos = at(x, y, z);
          BlockState old = world.getBlockState(pos);
          if (world.getBlockEntity(pos) != null
              || old.getDestroySpeed(world, pos) < 0
              || !old.isAir()
                  && !old.canBeReplaced()
                  && !old.is(TERRAIN)
                  && !old.is(net.minecraft.tags.BlockTags.LOGS)
                  && !old.is(net.minecraft.tags.BlockTags.LEAVES)) return;
        }
      }
    for (int x = -23; x <= 23; x++)
      for (int z = -64; z <= 23; z++) {
        if (z < -23 && Math.abs(x) > 3) continue;
        int floor = z < -22 ? pathFloor(z) : 0;
        int roof = z < -22 ? floor + 5 : 10 + (Math.abs(x) < 12 ? 2 : 0);
        for (int y = floor - 3; y <= roof; y++) {
          boolean shell =
              y < floor
                  || y == roof
                  || z >= -23 && (Math.abs(x) >= 21 || z >= 21 || z <= -21 && Math.abs(x) > 2)
                  || z < -23 && Math.abs(x) >= 2;
          BlockState state =
              shell
                  ? material(
                      y == floor - 1
                          ? Math.floorMod(x * 13 + z * 7, 11) < 2
                              ? "cracked_slimic_tiles"
                              : "slimic_tiles"
                          : "slimic_stone_bricks")
                  : Blocks.AIR.defaultBlockState();
          put(world, clip, x, y, z, state);
        }
        // Side colonnades stay outside the forty-block maneuvering floor.
        if (z >= -18 && z <= 18 && z % 9 == 0 && Math.abs(x) == 20)
          for (int y = 0; y < 10; y++) put(world, clip, x, y, z, material("ancient_foundation"));
        if (z >= -18 && z <= 18 && z % 9 == 0 && Math.abs(x) == 19)
          put(world, clip, x, 5, z, ModWorldBlocks.block("gel_lantern").defaultBlockState());
        if (Math.abs(x) <= 1 && z == -64) // Open end at the actual surface; never seal a retreat.
        for (int y = floor; y < floor + 6; y++)
            put(world, clip, x, y, z, Blocks.AIR.defaultBlockState());
      }
    // Surface markers make the stair mouth visible without a mandatory map.
    for (int x : new int[] {-3, 3})
      for (int y = entrance - anchor.getY(); y <= entrance - anchor.getY() + 4; y++)
        put(
            world,
            clip,
            x,
            y,
            -62,
            material(y == entrance - anchor.getY() + 4 ? "gel_lantern" : "ancient_foundation"));
    BlockPos altar = at(0, 0, -15);
    if (clip.isInside(altar)) {
      world.setBlock(altar, ModBlocks.ASSEMBLY_CORE.get().defaultBlockState(), 2);
      if (world.getBlockEntity(altar) instanceof AssemblyCoreBlockEntity core) core.bind(anchor);
    }
    BlockPos chest = at(18, 0, -17);
    if (clip.isInside(chest)) {
      world.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
      if (world.getBlockEntity(chest) instanceof ChestBlockEntity entity)
        entity.setLootTable(SlimicWorld.id("chests/assembly_chamber"), random.nextLong());
    }
  }
}
