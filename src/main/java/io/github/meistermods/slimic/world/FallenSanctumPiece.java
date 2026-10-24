package io.github.meistermods.slimic.world;

import io.github.meistermods.slimic.block.SanctumRelayBlockEntity;
import io.github.meistermods.slimic.registry.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** A rotated section of one continuous royal fortress. */
public final class FallenSanctumPiece extends StructurePiece {
  private static final net.minecraft.tags.TagKey<Block> TERRAIN =
      net.minecraft.tags.TagKey.create(
          net.minecraft.core.registries.Registries.BLOCK, SlimicWorld.id("fallen_sanctum_terrain"));
  private final BlockPos anchor;
  private final int rotation, zone, approachOffset, architectureVersion;

  public FallenSanctumPiece(BlockPos anchor, int rotation, int zone) {
    this(anchor, rotation, zone, 0);
  }

  public FallenSanctumPiece(BlockPos anchor, int rotation, int zone, int approachOffset) {
    super(ModWorldgen.SANCTUM_PIECE.get(), zone, box(anchor, rotation, zone));
    this.anchor = anchor.immutable();
    this.rotation = Math.floorMod(rotation, 4);
    this.zone = zone;
    this.approachOffset = zone == 0 ? Math.max(-7, Math.min(7, approachOffset)) : 0;
    architectureVersion = 4;
  }

  public FallenSanctumPiece(StructurePieceSerializationContext context, CompoundTag tag) {
    super(ModWorldgen.SANCTUM_PIECE.get(), tag);
    anchor = NbtUtils.readBlockPos(tag.getCompound("Anchor"));
    rotation = Math.floorMod(tag.getInt("RoyalRotation"), 4);
    zone = Math.max(0, Math.min(4, tag.getInt("Zone")));
    approachOffset = zone == 0 ? Math.max(-7, Math.min(7, tag.getInt("ApproachOffset"))) : 0;
    architectureVersion = Math.max(0, Math.min(4, tag.getInt("ArchitectureVersion")));
  }

  public static BlockPos position(BlockPos anchor, int rotation, int x, int y, int z) {
    return switch (Math.floorMod(rotation, 4)) {
      case 1 -> anchor.offset(-z, y, x);
      case 2 -> anchor.offset(-x, y, -z);
      case 3 -> anchor.offset(z, y, -x);
      default -> anchor.offset(x, y, z);
    };
  }

  private static BoundingBox box(BlockPos anchor, int rotation, int zone) {
    int start = zone == 0 ? SanctumLabyrinthArchitecture.MIN_Z : -40 + zone * 16;
    int end = zone == 4 ? SanctumLabyrinthArchitecture.MAX_Z : -25 + zone * 16;
    BlockPos a = position(anchor, rotation, SanctumLabyrinthArchitecture.MIN_X, -16, start),
        b =
            position(
                anchor,
                rotation,
                SanctumLabyrinthArchitecture.MAX_X,
                SanctumLabyrinthArchitecture.HEIGHT,
                end);
    return new BoundingBox(
        Math.min(a.getX(), b.getX()),
        anchor.getY() - 16,
        Math.min(a.getZ(), b.getZ()),
        Math.max(a.getX(), b.getX()),
        anchor.getY() + SanctumLabyrinthArchitecture.HEIGHT,
        Math.max(a.getZ(), b.getZ()));
  }

  SanctumPlacement.Candidate candidate() {
    return new SanctumPlacement.Candidate(anchor, rotation);
  }

  @Override
  protected void addAdditionalSaveData(
      StructurePieceSerializationContext context, CompoundTag tag) {
    tag.put("Anchor", NbtUtils.writeBlockPos(anchor));
    tag.putInt("RoyalRotation", rotation);
    tag.putInt("Zone", zone);
    tag.putInt("ApproachOffset", approachOffset);
    tag.putInt("ArchitectureVersion", architectureVersion);
  }

  private BlockPos at(int x, int y, int z) {
    return position(anchor, rotation, x, y, z);
  }

  private static BlockState material(String id) {
    return ModWorldBlocks.block(id).defaultBlockState();
  }

  private void put(WorldGenLevel world, BoundingBox clip, int x, int y, int z, BlockState state) {
    BlockPos pos = at(x, y, z);
    if (clip.isInside(pos) && world.getBlockEntity(pos) == null)
      world.setBlock(pos, state.rotate(Rotation.values()[rotation]), 2);
  }

  public int floorLevel(int z) {
    return floor(z);
  }

  private int floor(int z) {
    if (z < -24) {
      int progress =
          architectureVersion >= 4
              ? Math.max(0, (z - SanctumLabyrinthArchitecture.MIN_Z) / 2)
              : (z + 40) / 2;
      return Integer.signum(approachOffset) * Math.max(0, Math.abs(approachOffset) - progress);
    }
    return z < 20 ? 0 : -Math.min(4, (z - 20) / 2);
  }

  private boolean modifies(int x, int z, int start) {
    return zone > 0 && Math.abs(x) <= 25
        || Math.abs(x) <= 4
        || Math.abs(x) <= 28 && (x * 31 + z * 7) % 9 == 0
        || Math.abs(x) == 12 && (z == start + 3 || z == start + 11)
        || zone == 0
            && (Math.abs(x) == 5 && (z == -37 || z == -28) || Math.abs(x) == 18 && z == -25);
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
    int start = -40 + zone * 16;
    if (architectureVersion >= 4) {
      if (!new SanctumLabyrinthArchitecture(anchor, rotation, zone, clip, this::floorLevel)
          .generate(world, TERRAIN)) return;
    } else if (architectureVersion > 0) {
      if (!new SanctumArchitecture(
              anchor, rotation, zone, clip, this::floorLevel, architectureVersion)
          .generate(world, TERRAIN)) return;
    } else {
      // A partial upgrade may bring old chunks next to new ones. Preserve any foreign block entity
      // or construction encountered in this generating section instead of replacing it.
      for (int x = -32; x <= 31; x++)
        for (int z = start; z < start + 16; z++) {
          if (!modifies(x, z, start) || !clip.isInside(at(x, 0, z))) continue;
          int floor = floor(z);
          for (int y = floor - 7; y <= 11; y++) {
            var pos = at(x, y, z);
            var old = world.getBlockState(pos);
            if (world.getBlockEntity(pos) != null
                || old.getDestroySpeed(world, pos) < 0
                || !old.getFluidState().isEmpty() && y >= floor
                || !old.isAir()
                    && !old.canBeReplaced()
                    && !old.is(TERRAIN)
                    && !(y < floor && !old.getFluidState().isEmpty())
                    && !old.is(net.minecraft.tags.BlockTags.LOGS)
                    && !old.is(net.minecraft.tags.BlockTags.LEAVES)) return;
          }
        }
      for (int x = -32; x <= 31; x++)
        for (int z = start; z < start + 16; z++) {
          int floor = floor(z);
          boolean road = Math.abs(x) <= 4;
          boolean room = zone > 0 && Math.abs(x) <= 25;
          if (!room && !road && (Math.abs(x) > 28 || (x * 31 + z * 7) % 9 != 0)) continue;
          for (int y = floor - 7; y < floor; y++)
            put(
                world,
                clip,
                x,
                y,
                z,
                material(
                    y < floor - 1
                        ? "ancient_foundation"
                        : (x * 3 + z) % 7 == 0 ? "cracked_slimic_tiles" : "slimic_tiles"));
          for (int y = floor; y <= 11; y++)
            put(world, clip, x, y, z, Blocks.AIR.defaultBlockState());
          boolean side = room && Math.abs(x) == 25;
          boolean divider = room && (z == start || z == start + 15) && Math.abs(x) > 3;
          int h = side || divider ? 5 + Math.floorMod(x * 17 + z * 13, 4) : 0;
          for (int y = 0; y < h; y++)
            if (Math.floorMod(x * 11 + y * 19 + z, 17) != 0)
              put(
                  world,
                  clip,
                  x,
                  floor + y,
                  z,
                  material(
                      Math.floorMod(x + z + y, 5) == 0 ? "ruined_bricks" : "slimic_stone_bricks"));
          if (room && Math.abs(x) > 4 && Math.floorMod(x * 37 + z * 13, 131) == 0)
            put(world, clip, x, floor, z, Blocks.COBWEB.defaultBlockState());
          if (room && Math.abs(x) > 4 && Math.floorMod(x * 7 + z * 11, 89) == 0)
            put(world, clip, x, floor, z, material("fallen_masonry_pile"));
        }
      // The central five-wide route stays open across all piece seams and descends into the core.
      for (int z = start; z < start + 16; z++)
        for (int x = -2; x <= 2; x++)
          for (int y = floor(z); y < floor(z) + 4; y++)
            put(world, clip, x, y, z, Blocks.AIR.defaultBlockState());
      for (int x : new int[] {-12, 12})
        for (int z = start + 3; z < start + 15; z += 8) {
          for (int y = 0; y < (zone == 0 ? 3 : 7); y++)
            put(
                world,
                clip,
                x,
                floor(z) + y,
                z,
                material(y == 0 ? "chiseled_slimic_stone_bricks" : "ancient_foundation_bricks"));
          put(world, clip, x, floor(z) + (zone == 0 ? 3 : 7), z, material("gel_lantern"));
        }
    }
    if (zone == 0) {
      if (architectureVersion > 0) return;
      for (int z = -36; z <= -33; z++) {
        for (int x : new int[] {-4, -3, 3, 4})
          for (int y = floor(z) - 5; y < floor(z); y++)
            put(world, clip, x, y, z, Blocks.AIR.defaultBlockState());
        for (int x = -2; x <= 2; x++)
          put(world, clip, x, floor(z) - 1, z, material("slime_infused_planks"));
      }
      for (int x : new int[] {-5, 5}) {
        put(world, clip, x, floor(-37), -37, material("royal_waymarker"));
        put(world, clip, x, floor(-28), -28, material("old_slime_banner"));
      }
      for (int x : new int[] {-18, 18})
        for (int y = 0; y < 11; y++)
          put(world, clip, x, y, -25, material(y % 4 == 0 ? "royal_crest" : "slimic_stone_bricks"));
    } else {
      int centerZ = start + 8, ground = floor(centerZ);
      if (zone == 1 && architectureVersion == 0)
        for (int x = -20; x <= 20; x += 4)
          if (Math.abs(x) > 4) {
            put(world, clip, x, ground, centerZ - 4, material("fallen_masonry_pile"));
            put(world, clip, x, ground, centerZ + 4, material("fallen_pillar"));
          }
      if (zone == 2 && architectureVersion == 0) {
        for (int x : new int[] {-16, -8, 8, 16})
          put(world, clip, x, ground, centerZ + 3, material("cracked_command_pedestal"));
        for (int x = -22; x <= 22; x++)
          if (Math.floorMod(x, 7) != 0) {
            put(world, clip, x, ground + 7, centerZ - 4, material("ancient_foundation_bricks"));
            put(world, clip, x, ground + 7, centerZ + 4, material("cracked_slimic_tiles"));
          }
      }
      if (zone == 1) {
        var pos = at(-16, ground, centerZ + 3);
        put(
            world,
            clip,
            -16,
            ground,
            centerZ + 3,
            ModBlocks.BROKEN_SLIME
                .get()
                .defaultBlockState()
                .setValue(io.github.meistermods.slimic.block.BrokenSlimeBlock.MINERALIZED, true));
        if (clip.isInside(pos)
            && world.getBlockEntity(pos)
                instanceof io.github.meistermods.slimic.block.BrokenSlimeBlockEntity corpse)
          corpse.setAncientCore(
              io.github.meistermods.slimic.item.AncientSlimeCoreItem.fromRemains(
                  io.github.meistermods.slimic.entity.AncientSlime.Role.MINING,
                  java.util.UUID.nameUUIDFromBytes(
                      (anchor.asLong() + ":courtyard_miner")
                          .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                  2));
      }
      put(world, clip, -20, ground, centerZ, material("old_slime_banner"));
      put(world, clip, -18, ground, centerZ + 2, material("broken_workbench"));
      put(world, clip, 18, ground, centerZ - 2, material("tool_rack"));
      if (zone == 3) {
        for (int x : new int[] {-17, -14})
          put(
              world,
              clip,
              x,
              ground,
              centerZ + 4,
              ModBlocks.BROKEN_SLIME
                  .get()
                  .defaultBlockState()
                  .setValue(io.github.meistermods.slimic.block.BrokenSlimeBlock.MINERALIZED, true));
        for (int x : new int[] {-8, 8}) {
          int crystalX = architectureVersion > 0 ? x + Integer.signum(x) * 2 : x;
          put(world, clip, crystalX, ground, centerZ, material("gel_crystal_cluster"));
          put(world, clip, x, ground, centerZ + 3, material("gel_basin"));
        }
        put(
            world,
            clip,
            12,
            ground,
            centerZ,
            ModBlocks.ROYAL_SYNTHESIS_STATION
                .get()
                .defaultBlockState()
                .setValue(
                    io.github.meistermods.slimic.block.RoyalSynthesisStationBlock.RUINED, true));
      }
      device(world, clip, zone, 6, ground, centerZ);
      chest(world, clip, -8, ground, centerZ - 2, zone);
    }
  }

  private void device(WorldGenLevel world, BoundingBox clip, int zone, int x, int y, int z) {
    var pos = at(x, y, z);
    if (!clip.isInside(pos)) return;
    put(
        world,
        clip,
        x,
        y,
        z,
        ModBlocks.SANCTUM_RELAY
            .get()
            .defaultBlockState()
            .setValue(io.github.meistermods.slimic.block.SanctumRelayBlock.ZONE, zone));
    if (world.getBlockEntity(pos) instanceof SanctumRelayBlockEntity relay)
      relay.bind(anchor, rotation, zone);
  }

  private void chest(WorldGenLevel world, BoundingBox clip, int x, int y, int z, int zone) {
    var pos = at(x, y, z);
    if (!clip.isInside(pos)) return;
    put(
        world,
        clip,
        x,
        y,
        z,
        Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
    if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest)
      chest.setLootTable(
          SlimicWorld.id(
              "chests/sanctum_"
                  + switch (zone) {
                    case 1 -> "courtyard";
                    case 2 -> "gallery";
                    case 3 -> "laboratory";
                    default -> "core";
                  }),
          pos.asLong() ^ anchor.asLong());
  }
}
