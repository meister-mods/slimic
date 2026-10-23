package io.github.meistermods.slimic.world;

import io.github.meistermods.slimic.registry.ModWorldBlocks;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Chunk-clipped castle masonry. A complete plan is checked before any terrain is changed. */
final class SanctumArchitecture {
  static final int HEIGHT = 28;
  private final BlockPos anchor;
  private final Rotation rotation;
  private final int turn, minX, maxX, minZ, maxZ, minY, maxY;
  private final IntUnaryOperator floor;
  private final boolean furnished, expanded;
  private final Map<BlockPos, BlockState> plan = new LinkedHashMap<>();

  SanctumArchitecture(
      BlockPos anchor,
      int turn,
      int zone,
      BoundingBox clip,
      IntUnaryOperator floor,
      int architectureVersion) {
    this.anchor = anchor;
    this.turn = turn;
    rotation = Rotation.values()[turn];
    this.floor = floor;
    furnished = architectureVersion >= 2;
    expanded = architectureVersion >= 3;
    var a =
        FallenSanctumPiece.position(
            BlockPos.ZERO, -turn, clip.minX() - anchor.getX(), 0, clip.minZ() - anchor.getZ());
    var b =
        FallenSanctumPiece.position(
            BlockPos.ZERO, -turn, clip.maxX() - anchor.getX(), 0, clip.maxZ() - anchor.getZ());
    minX = Math.max(-32, Math.min(a.getX(), b.getX()));
    maxX = Math.min(31, Math.max(a.getX(), b.getX()));
    minZ = Math.max(-40 + zone * 16, Math.min(a.getZ(), b.getZ()));
    maxZ = Math.min(-25 + zone * 16, Math.max(a.getZ(), b.getZ()));
    minY = Math.max(-16, clip.minY() - anchor.getY());
    maxY = Math.min(HEIGHT, clip.maxY() - anchor.getY());
  }

  private static BlockState material(String id) {
    return ModWorldBlocks.block(id).defaultBlockState();
  }

  private static BlockState masonry(int x, int y, int z) {
    int weathering = Math.floorMod(x * 31 + y * 17 + z * 13, 29);
    return material(
        weathering < 3
            ? "mossy_slimic_stone_bricks"
            : weathering < 6 ? "cracked_slimic_stone_bricks" : "slimic_stone_bricks");
  }

  private void put(int x, int y, int z, BlockState state) {
    if (x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= minY && y <= maxY)
      plan.put(FallenSanctumPiece.position(anchor, turn, x, y, z), state.rotate(rotation));
  }

  private void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
    for (int x = Math.max(minX, x1); x <= Math.min(maxX, x2); x++)
      for (int z = Math.max(minZ, z1); z <= Math.min(maxZ, z2); z++)
        for (int y = Math.max(minY, y1); y <= Math.min(maxY, y2); y++) put(x, y, z, state);
  }

  private void stone(int x1, int y1, int z1, int x2, int y2, int z2) {
    for (int x = Math.max(minX, x1); x <= Math.min(maxX, x2); x++)
      for (int z = Math.max(minZ, z1); z <= Math.min(maxZ, z2); z++)
        for (int y = Math.max(minY, y1); y <= Math.min(maxY, y2); y++)
          put(x, y, z, masonry(x, y, z));
  }

  private void air(int x1, int y1, int z1, int x2, int y2, int z2) {
    fill(x1, y1, z1, x2, y2, z2, Blocks.AIR.defaultBlockState());
  }

  private static boolean towerColumn(int x, int z, int cx, int cz) {
    int dx = Math.abs(x - cx), dz = Math.abs(z - cz);
    return Math.max(dx, dz) <= 5 && dx + dz <= 8;
  }

  private void foundation() {
    for (int x = minX; x <= maxX; x++)
      for (int z = minZ; z <= maxZ; z++) {
        boolean platform = z >= -28 && z <= 38 && Math.abs(x) <= 28 || Math.abs(x) <= 4;
        for (int cx : new int[] {-25, 25})
          for (int cz : new int[] {-23, 33}) platform |= towerColumn(x, z, cx, cz);
        if (!platform) continue;
        int ground = floor.applyAsInt(z);
        fill(x, ground - 7, z, x, ground - 2, z, material("ancient_foundation"));
        put(
            x,
            ground - 1,
            z,
            material(
                Math.abs(x) <= 2
                    ? "slimic_tiles"
                    : Math.floorMod(x * 3 + z, 11) == 0
                        ? "cracked_slimic_tiles"
                        : "slimic_cobblestone"));
        air(x, ground, z, x, HEIGHT, z);
      }
  }

  private void approach() {
    // A dry defensive ditch and a narrow timber bridge frame the entrance without recurring fluids.
    for (int z = -35; z <= -30; z++) {
      int ground = floor.applyAsInt(z);
      for (int x = -26; x <= 26; x++) {
        if (Math.abs(x) <= 3) continue;
        put(x, ground - 5, z, material("mossy_slimic_cobblestone"));
        air(x, ground - 4, z, x, ground + 4, z);
      }
      fill(-3, ground - 1, z, 3, ground - 1, z, material("slime_infused_planks"));
      for (int x : new int[] {-3, 3})
        if (z % 2 == 0) put(x, ground, z, material("slimic_stone_bricks_wall"));
    }
    for (int x : new int[] {-5, 5}) {
      put(x, floor.applyAsInt(-38), -38, material("royal_waymarker"));
      put(x, floor.applyAsInt(-29), -29, material("old_slime_banner"));
    }
  }

  private void curtain() {
    for (int side : new int[] {-1, 1}) {
      int x = side * 27;
      for (int z = -25; z <= 35; z++) {
        stone(x - 1, floor.applyAsInt(z), z, x + 1, 8, z);
        fill(x - 1, 9, z, x + 1, 9, z, material("ancient_foundation_bricks"));
        // Lower inner parapets leave a usable three-wide wall walk.
        put(x + side, 10, z, masonry(x, 10, z));
        if (Math.floorMod(z, 4) < 2) put(x + side, 11, z, masonry(x, 11, z));
      }
      for (int z = -18; z <= 29; z += 8) {
        stone(x - 2, 0, z, x + 2, 6, z + 1);
        stone(x + side, 10, z, x + side, 11, z);
        put(x + side, 12, z, material("gel_lantern"));
      }
      // Broad, climbable stairs from the ward to the curtain walks.
      int a = side < 0 ? -25 : 23, b = side < 0 ? -23 : 25;
      for (int z = -16; z <= -7; z++) {
        int y = z + 16;
        stone(a, 0, z, b, y - 1, z);
        fill(a, y, z, b, y, z, stairs("slimic_stone_bricks_stairs", Direction.SOUTH));
      }
      fill(a, 9, -6, b, 9, -5, material("slimic_stone_bricks"));
    }
    for (int z : new int[] {-25, 36}) {
      stone(-26, floor.applyAsInt(z), z - 1, 26, 8, z + 1);
      fill(-26, 9, z - 1, 26, 9, z + 1, material("ancient_foundation_bricks"));
      for (int x = -26; x <= 26; x++) {
        put(x, 10, z == -25 ? z - 1 : z + 1, masonry(x, 10, z));
        if (Math.floorMod(x, 4) < 2) put(x, 11, z == -25 ? z - 1 : z + 1, masonry(x, 11, z));
      }
    }
    // A broad breach reads as a collapsed defense, rather than random holes throughout every wall.
    for (int z = 0; z <= 7; z++) {
      int brokenTop = z < 3 ? 1 + z : 2 + (7 - z) / 2;
      air(25, brokenTop, z, 29, 13, z);
      if (z % 2 == 0) put(29, 0, z, material("fallen_masonry_pile"));
    }
  }

  private static BlockState bars(boolean north, boolean east, boolean south, boolean west) {
    return Blocks.IRON_BARS
        .defaultBlockState()
        .setValue(IronBarsBlock.NORTH, north)
        .setValue(IronBarsBlock.EAST, east)
        .setValue(IronBarsBlock.SOUTH, south)
        .setValue(IronBarsBlock.WEST, west);
  }

  private static BlockState stairs(String id, Direction facing) {
    return material(id).setValue(StairBlock.FACING, facing);
  }

  private void tower(int cx, int cz, int height, boolean broken) {
    for (int x = cx - 5; x <= cx + 5; x++)
      for (int z = cz - 5; z <= cz + 5; z++) {
        if (!towerColumn(x, z, cx, cz)) continue;
        int dx = Math.abs(x - cx), dz = Math.abs(z - cz);
        boolean edge = Math.max(dx, dz) == 5 || dx + dz == 8;
        int top = broken ? height - 7 + Math.floorMod(x * 3 + z, 3) : height;
        if (edge) {
          stone(x, floor.applyAsInt(z), z, x, top - 2, z);
          if (!broken && (x + z) % 2 == 0) put(x, top, z, masonry(x, top, z));
          put(x, top - 1, z, material("chiseled_slimic_stone_bricks"));
          for (int band : new int[] {1, 8, 14})
            if (band < top - 1) put(x, band, z, material("ancient_foundation_bricks"));
          if (!broken && (dx == 5 && dz == 0 || dz == 5 && dx == 0))
            fill(x, 4, z, x, 6, z, bars(dx == 5, dx != 5, dx == 5, dx != 5));
        } else {
          // Curtain masonry crosses the tower footprint; hollow the ground room before furnishing.
          if (furnished) air(x, floor.applyAsInt(z), z, x, 8, z);
          put(x, 9, z, material("slime_infused_planks"));
          if (!broken) put(x, height - 2, z, material("slimic_stone_bricks"));
        }
      }
    int inside = cx < 0 ? cx + 5 : cx - 5;
    int base = floor.applyAsInt(cz);
    air(inside - 1, base, cz - 1, inside + 1, base + 3, cz + 1);
    air(inside - 1, 10, cz - 1, inside + 1, 12, cz + 1);
    // The ladder follows a solid west inner wall and pierces both intermediate floors.
    int entranceZ = cz < 0 ? cz + 5 : cz - 5;
    air(cx - 1, base, entranceZ, cx + 1, base + 3, entranceZ);
    air(cx - 1, 10, entranceZ, cx + 1, 12, entranceZ);
    int ladderX = cx - 4, ladderZ = cz + 2;
    int top = broken ? 9 : height - 1;
    air(ladderX, base + 1, ladderZ, ladderX, top, ladderZ);
    fill(
        ladderX,
        base + 1,
        ladderZ,
        ladderX,
        top,
        ladderZ,
        Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST));
    if (!broken) put(cx, height - 1, cz, material("ancient_brazier"));
    else {
      put(cx + 1, 10, cz - 1, material("broken_pillar"));
      put(cx - 1, 8, cz + 2, Blocks.COBWEB.defaultBlockState());
    }
  }

  private void gatehouse() {
    for (int cx : new int[] {-8, 8}) {
      stone(cx - 4, 0, -28, cx + 4, 15, -21);
      air(cx - 2, 0, -26, cx + 2, 13, -23);
      fill(cx - 2, 7, -26, cx + 2, 7, -23, material("slime_infused_planks"));
      fill(cx - 3, 14, -27, cx + 3, 14, -22, material("slimic_stone_bricks"));
      for (int x = cx - 4; x <= cx + 4; x++)
        for (int z = -28; z <= -21; z++)
          if ((x == cx - 4 || x == cx + 4 || z == -28 || z == -21) && Math.floorMod(x + z, 4) < 2)
            put(x, 16, z, masonry(x, 16, z));
      fill(cx, 3, -28, cx, 5, -28, bars(false, true, false, true));
      air(cx - 1, 0, -21, cx + 1, 3, -21);
      // Both gate chambers have an internal ladder and a doorway onto the upper gate bridge.
      fill(
          cx - 2,
          1,
          -25,
          cx - 2,
          15,
          -25,
          Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST));
      air(cx > 0 ? 4 : -4, 8, -25, cx > 0 ? 4 : -4, 10, -23);
      put(cx, 15, -25, material("royal_crest"));
    }
    fill(-4, 7, -27, 4, 7, -22, material("ancient_foundation_bricks"));
    stone(-4, 8, -27, 4, 10, -27);
    fill(-3, 11, -27, 3, 11, -27, material("chiseled_slime_marble"));
    air(-3, 0, -29, 3, 5, -21);
    for (int x = -2; x <= 2; x++)
      for (int z = -28; z <= -27; z++) put(x, 6, z, bars(z == -27, x < 2, true, x > -2));
    for (int x : new int[] {-4, 4}) {
      stone(x, 0, -29, x, 5, -28);
      put(x, 6, -29, stairs("slimic_stone_bricks_stairs", x < 0 ? Direction.EAST : Direction.WEST));
    }
    for (int side : new int[] {-1, 1}) {
      put(
          side * 3,
          5,
          -29,
          stairs("slimic_stone_bricks_stairs", side < 0 ? Direction.EAST : Direction.WEST)
              .setValue(StairBlock.HALF, Half.TOP));
      put(
          side * 2,
          6,
          -29,
          stairs("slimic_stone_bricks_stairs", side < 0 ? Direction.EAST : Direction.WEST)
              .setValue(StairBlock.HALF, Half.TOP));
    }
    fill(-2, 7, -29, 2, 8, -28, material("chiseled_slimic_stone_bricks"));
    put(0, 12, -27, material("royal_crest"));
    for (int x : new int[] {-14, 14}) {
      put(x, 0, -24, material("ancient_brazier"));
      stone(x, 10, -26, x, 11, -26);
      put(x, 12, -26, material("old_slime_banner"));
    }
  }

  private void ward() {
    // An open barracks faces the courtyard, leaving the mineralized miner inside its doorway.
    stone(-22, 0, -20, -14, 4, -10);
    air(-21, 0, -19, -15, 3, -11);
    air(-14, 0, -17, -14, 3, -14);
    fill(-22, 5, -20, -14, 5, -10, material("slime_infused_planks"));
    air(-22, 4, -13, -18, 6, -10);
    for (int z : new int[] {-18, -12})
      fill(-22, 1, z, -22, 2, z + 1, bars(true, false, true, false));
    for (int x = 15; x <= 21; x++) {
      put(x, 4 + (21 - x) / 3, -18, stairs("black_slime_stone_stairs", Direction.EAST));
      put(x, 4 + (21 - x) / 3, -10, stairs("black_slime_stone_stairs", Direction.EAST));
    }
    for (int x : new int[] {15, 21}) for (int z : new int[] {-18, -10}) stone(x, 0, z, x, 3, z);
    for (int x = 15; x <= 21; x++)
      for (int z = -17; z < -10; z++) put(x, 4 + (21 - x) / 3, z, material("black_slime_stone"));
    for (int x = 14; x <= 18; x++)
      for (int z = -16; z <= -12; z++)
        if (x == 14 || x == 18 || z == -16 || z == -12)
          put(x, 0, z, material("mossy_slimic_stone_bricks_slab"));
    fill(15, -2, -15, 17, -2, -13, material("mossy_slimic_cobblestone"));
    air(15, -1, -15, 17, 0, -13);
    put(16, -1, -14, material("broken_pillar"));
    for (int x : new int[] {-5, 5}) put(x, 0, -20, material("gel_lantern"));
  }

  private void keep() {
    for (int side : new int[] {-1, 1}) {
      int x = side * 19;
      stone(x, -4, -8, x, 12, 35);
      fill(x, 0, -8, x, 0, 35, material("ancient_foundation_bricks"));
      fill(x, 10, -8, x, 10, 35, material("chiseled_slimic_stone_bricks"));
      for (int z = -5; z <= 31; z += 7) {
        stone(x - 1, 0, z, x + 1, 9, z + 1);
        fill(x, 3, z + 3, x, 6, z + 4, material("gel_glass"));
        put(
            x + side,
            7,
            z + 3,
            stairs("slimic_stone_bricks_stairs", side < 0 ? Direction.EAST : Direction.WEST));
      }
      // The gallery remains open above the main floor; the three-wide stairs join its inner edge.
      int a = side < 0 ? -17 : 15, b = side < 0 ? -15 : 17;
      for (int z = -5; z <= 2; z++) {
        int y = z + 5;
        stone(a, 0, z, b, y - 1, z);
        fill(a, y, z, b, y, z, stairs("slimic_stone_bricks_stairs", Direction.SOUTH));
      }
      fill(side < 0 ? -18 : 15, 7, 3, side < 0 ? -15 : 18, 7, 32, material("slime_infused_planks"));
      for (int z = 3; z <= 32; z++) put(side * 14, 8, z, material("slimic_stone_bricks_slab"));
    }
    for (int z : new int[] {-8, 35}) {
      stone(-18, -4, z, 18, 12, z);
      for (int x = -18; x <= 18; x++) stone(x, 13, z, x, 22 - Math.abs(x) / 2, z);
      air(-3, 0, z, 3, 5, z);
      fill(-2, 9, z, 2, 11, z, material("gel_glass"));
    }
    for (int x : new int[] {-13, -7, 7, 13}) {
      stone(x, 0, -9, x, 12, -9);
      put(x, 13, -9, material("chiseled_slimic_stone_bricks"));
    }
    for (int side : new int[] {-1, 1}) {
      fill(side * 10, 3, -8, side * 10, 6, -8, material("gel_glass"));
      int a = side < 0 ? -17 : 4, b = side < 0 ? -4 : 17;
      fill(a, 8, -8, b, 8, -8, material("chiseled_slime_marble"));
    }
    // A real pitched slate roof makes the keep visible above the outer defenses.
    for (int x = -20; x <= 20; x++)
      for (int z = -9; z <= 36; z++) {
        int roof = 13 + (20 - Math.abs(x)) / 2;
        boolean breach = x >= 6 && x <= 16 && z >= 10 && z <= 21 && x + z >= 20 && x + z <= 33;
        if (!breach) {
          put(x, roof, z, material("slimic_slate_bricks"));
          if (x != 0)
            put(
                x,
                roof + 1,
                z,
                stairs("black_slime_stone_stairs", x < 0 ? Direction.EAST : Direction.WEST));
          else put(x, roof + 1, z, material("black_slime_stone"));
        }
      }
    put(0, 25, -8, material("royal_crest"));
    for (int z : new int[] {-5, 3, 11, 19, 27, 33}) {
      for (int x : new int[] {-12, 12}) {
        stone(x, Math.max(0, floor.applyAsInt(z)), z, x, 10, z);
        put(x, 11, z, material("chiseled_slime_marble"));
        int hangingX = x + (x < 0 ? 1 : -1);
        fill(hangingX, 7, z, hangingX, 11, z, Blocks.CHAIN.defaultBlockState());
        put(hangingX, 6, z, material("gel_lantern"));
      }
      // Raised ribs keep the central hall tall and leave generous headroom below.
      for (int x = -12; x <= 12; x++)
        put(x, 12 + (12 - Math.abs(x)) / 3, z, material("ancient_foundation_bricks"));
    }
    // The royal command seat sits off the through-route; it is scenery, not a new reward device.
    fill(-11, 0, 4, -8, 0, 6, material("slime_marble"));
    stone(-11, 1, 6, -8, 3, 6);
    put(-10, 1, 5, stairs("slime_marble_stairs", Direction.SOUTH));
    put(-10, 4, 6, material("royal_crest"));
    for (int x : new int[] {8, 11}) stone(x, 0, 4, x, 0, 5);
    fill(
        8,
        1,
        4,
        11,
        1,
        5,
        material("slime_infused_planks_slab").setValue(SlabBlock.TYPE, SlabType.TOP));
    put(9, 2, 4, material("stone_tablet"));
    // The laboratory's collapsed roof has fallen against the wall, away from devices and patrols.
    for (int x = 14; x <= 17; x++)
      for (int z = 10; z <= 12; z++)
        put(x, 0, z, material((x + z) % 2 == 0 ? "fallen_masonry_pile" : "fallen_pillar"));
    for (int z : new int[] {13, 20}) put(18, 4, z, Blocks.COBWEB.defaultBlockState());
    // A lower vault separates the deep chamber from the upper keep without blocking its stairs.
    fill(-18, 5, 27, 18, 5, 34, material("ancient_foundation_bricks"));
    for (int z : new int[] {28, 33}) {
      for (int x : new int[] {-11, 11}) stone(x, -4, z, x, 1, z);
      for (int x = -11; x <= 11; x++)
        put(x, 2 + (11 - Math.abs(x)) / 3, z, material("chiseled_slimic_stone_bricks"));
      for (int x : new int[] {-10, 10}) {
        put(x, 1, z, Blocks.CHAIN.defaultBlockState());
        put(x, 0, z, material("gel_lantern"));
      }
    }
  }

  private static BlockState topSlab(String id) {
    return material(id).setValue(SlabBlock.TYPE, SlabType.TOP);
  }

  private void standard(int cx, int top, int z, boolean torn) {
    // Cloth hangs from a stone lintel fixed into the facade. The pale mark repeats the royal crest.
    fill(cx - 1, top, z, cx + 1, top, z + 1, material("slime_marble_slab"));
    for (int dx = -1; dx <= 1; dx++)
      for (int down = 1; down <= 5; down++) {
        if (down == 5 && (dx != 0 || torn) || torn && dx == 1 && down >= 3) continue;
        boolean mark = down == 2 || down == 3 && dx == 0;
        put(
            cx + dx,
            top - down,
            z,
            (mark ? Blocks.WHITE_WOOL : Blocks.GREEN_WOOL).defaultBlockState());
      }
  }

  private void facadeDetails() {
    for (int side : new int[] {-1, 1}) {
      int cx = side * 8;
      standard(cx, 13, -29, side > 0);
      for (int x : new int[] {cx - 4, cx + 4}) {
        fill(x, 0, -29, x, 1, -29, material("ancient_foundation_bricks"));
        stone(x, 2, -29, x, 12, -29);
        put(x, 7, -29, material("chiseled_slime_marble"));
        put(x, 13, -29, stairs("slime_marble_stairs", Direction.SOUTH));
      }
      fill(cx - 4, 14, -29, cx + 4, 14, -29, topSlab("slime_marble_slab"));
      standard(side * 10, 14, -9, side < 0);
      stone(side * 4, 0, -9, side * 4, 5, -9);
      put(
          side * 3,
          5,
          -9,
          stairs("slime_marble_stairs", side < 0 ? Direction.EAST : Direction.WEST)
              .setValue(StairBlock.HALF, Half.TOP));
      put(
          side * 2,
          6,
          -9,
          stairs("slime_marble_stairs", side < 0 ? Direction.EAST : Direction.WEST)
              .setValue(StairBlock.HALF, Half.TOP));
      put(side * 5, 3, -9, material("chiseled_slime_marble"));
      put(side * 5, 4, -9, material("gel_lantern"));
    }
    fill(-2, 7, -9, 2, 7, -9, material("chiseled_slime_marble"));
    // A diamond-shaped glazed oculus breaks the broad gable and lights the upper hall.
    for (int x = -3; x <= 3; x++)
      for (int dy = -3; dy <= 3; dy++) {
        int distance = Math.abs(x) + Math.abs(dy);
        if (distance > 3) continue;
        var state =
            material(
                distance == 3
                    ? "chiseled_slime_marble"
                    : distance == 0 ? "luminous_slime" : "gel_glass");
        put(x, 17 + dy, -9, state);
        put(x, 17 + dy, -8, state);
      }
    for (int x = -17; x <= 17; x++) put(x, 13, -9, topSlab("slime_marble_slab"));
    for (int cx : new int[] {-25, 25})
      for (int cz : new int[] {-23, 33}) {
        if (cx > 0 && cz > 0) continue;
        int height = cz < 0 ? 18 : 22;
        for (var direction : Direction.Plane.HORIZONTAL) {
          int x = cx + direction.getStepX() * 6, z = cz + direction.getStepZ() * 6;
          put(
              x,
              height - 4,
              z,
              stairs("slimic_stone_bricks_stairs", direction.getOpposite())
                  .setValue(StairBlock.HALF, Half.TOP));
          put(x, height - 3, z, material("slime_marble_slab"));
        }
      }
  }

  private void wardDetails() {
    // Shared meals and supplies sit against the barracks walls, clear of the recovered miner.
    for (int x : new int[] {-20, -18}) put(x, 0, -18, material("ruined_bench"));
    put(-19, 0, -18, material("low_stone_table"));
    put(-21, 0, -14, material("supply_urn"));
    put(-20, 0, -12, material("cracked_stone_tablet"));
    put(-21, 0, -11, material("broken_crate"));
    put(-21, 3, -19, Blocks.COBWEB.defaultBlockState());
    for (int z : new int[] {-19, -15}) fill(-21, 4, z, -15, 4, z, material("slime_infused_planks"));
    put(-20, 3, -17, material("gel_lantern"));
    put(-20, 4, -17, Blocks.CHAIN.defaultBlockState());
    put(20, 0, -17, material("tool_rack"));
    put(19, 0, -11, material("broken_workbench"));
    put(20, 0, -12, material("abandoned_crate"));
    put(20, 0, -13, material("broken_crate"));
    put(14, 0, -18, material("cracked_supply_urn"));
    // The dry well retains its timber windlass, without introducing source fluids or containers.
    for (int x : new int[] {14, 18}) fill(x, 0, -14, x, 2, -14, material("slime_infused_planks"));
    fill(14, 3, -14, 18, 3, -14, material("slime_infused_planks_slab"));
    fill(16, 1, -14, 16, 2, -14, Blocks.CHAIN.defaultBlockState());
    for (int z = -21; z <= -10; z++)
      for (int x = -11; x <= 11; x++) {
        if (Math.abs(x) == 3 || z == -19 && Math.abs(x) <= 10)
          put(x, -1, z, material("polished_slimic_stone"));
        else if (z == -12 && Math.abs(x) <= 2) put(x, -1, z, material("chiseled_slime_marble"));
      }
  }

  private void furnishings() {
    for (int cx : new int[] {-25, 25})
      for (int cz : new int[] {-23, 33}) {
        int base = floor.applyAsInt(cz);
        put(cx + 2, base, cz + 2, material("abandoned_crate"));
        put(cx + 2, base, cz - 2, material("tool_rack"));
        put(cx - 2, base, cz - 2, material("stone_bench"));
        put(cx + 3, base, cz + 2, material("gel_lantern"));
        put(cx + 2, 10, cz - 2, material("cracked_supply_urn"));
      }
    for (int cx : new int[] {-8, 8}) {
      put(cx + 2, 0, -25, material("stone_bench"));
      put(cx + 2, 0, -23, material("supply_urn"));
      put(cx + 1, 8, -26, material("low_stone_table"));
      put(cx + 2, 8, -26, material("gel_lantern"));
    }
    // Records are shelved along the outer gallery edge, leaving a continuous three-wide walk.
    for (int side : new int[] {-1, 1})
      for (int z : new int[] {7, 15, 23}) {
        int x = side * 18;
        fill(x, 8, z, x, 8, z + 2, material("slime_infused_planks"));
        fill(x, 10, z, x, 10, z + 2, topSlab("slime_infused_planks_slab"));
        for (int offset = 0; offset < 3; offset++)
          put(x, 9, z + offset, material(offset == 1 ? "stone_tablet" : "cracked_stone_tablet"));
      }
    for (int z : new int[] {6, 9}) {
      fill(-18, 0, z, -18, 2, z, material("slime_infused_planks"));
      put(-17, 0, z, material("abandoned_crate"));
      put(-17, 1, z, material("stone_tablet"));
    }
    fill(-18, 3, 6, -18, 3, 9, topSlab("slime_infused_planks_slab"));
    for (int z : new int[] {3, 19}) {
      fill(0, 11, z, 0, 15, z, Blocks.CHAIN.defaultBlockState());
      fill(-2, 10, z, 2, 10, z, material("slime_marble_slab"));
      put(0, 10, z, material("chiseled_slime_marble"));
      for (int x : new int[] {-2, 0, 2}) put(x, 9, z, material("gel_lantern"));
    }
    for (int x = 5; x <= 9; x++) {
      put(x, 0, 8, material("low_stone_table"));
      put(x, 0, 7, material("stone_bench"));
      if (x != 8) put(x, 0, 10, material("ruined_bench"));
    }
    put(10, 0, 9, material("cracked_supply_urn"));
    // Laboratory instruments and core alcoves suggest the rooms' former uses.
    fill(-17, 0, 12, -14, 0, 12, topSlab("slime_marble_slab"));
    put(-16, 1, 12, material("gel_basin"));
    put(-14, 1, 12, material("small_gel_crystal_cluster"));
    put(-17, 0, 15, material("broken_crate"));
    put(-16, 0, 15, material("relic_shard_pile"));
    for (int side : new int[] {-1, 1}) {
      // Posterns join the laboratory to the inner curtain lane and the rear tower ground rooms.
      air(side * 19, 0, 12, side * 19, 3, 14);
      for (int z : new int[] {11, 15}) {
        stone(side * 20, 0, z, side * 20, 3, z);
        put(side * 20, 4, z, material("chiseled_slime_marble"));
      }
      fill(side * 20, 4, 12, side * 20, 4, 14, material("slime_marble_slab"));
      fill(side * 16, -4, 29, side * 16, -2, 29, material("chiseled_slimic_stone_bricks"));
      put(side * 16, -1, 29, material("dormant_beacon"));
      put(side * 15, -4, 31, material("cracked_supply_urn"));
      put(side * 14, -4, 34, material("relic_shard_pile"));
    }
    for (int z : new int[] {0, 16, 32}) {
      int y = floor.applyAsInt(z) - 1;
      for (int x = -3; x <= 3; x++)
        for (int dz = -2; dz <= 2; dz++) {
          int distance = Math.abs(x) + Math.abs(dz);
          if (distance <= 2)
            put(x, y, z + dz, material(distance == 2 ? "polished_slimic_stone" : "slimic_tiles"));
        }
    }
  }

  private void roomsAndCrossings() {
    // Separate record rooms and stores retain two doorways each, so patrols cannot trap a visitor
    // in a dead end. Their ceiling is the existing upper gallery, not a second disconnected floor.
    for (int side : new int[] {-1, 1}) {
      int inner = side * 13;
      stone(inner, 0, 4, inner, 6, 20);
      for (int z : new int[] {7, 15}) {
        air(inner, 0, z, inner, 3, z + 2);
        fill(inner, 4, z, inner, 4, z + 2, material("chiseled_slime_marble"));
        put(
            inner,
            3,
            z,
            stairs("slime_marble_stairs", Direction.SOUTH).setValue(StairBlock.HALF, Half.TOP));
        put(
            inner,
            3,
            z + 2,
            stairs("slime_marble_stairs", Direction.NORTH).setValue(StairBlock.HALF, Half.TOP));
      }
      int a = side < 0 ? -18 : 14, b = side < 0 ? -14 : 18;
      for (int z : new int[] {4, 11, 20}) {
        stone(a, 0, z, b, 6, z);
        air(side < 0 ? -17 : 15, 0, z, side < 0 ? -15 : 17, 3, z);
      }
      // The old side posterns join a covered service passage with a clear three-wide lane.
      fill(
          side < 0 ? -24 : 21, 5, -7, side < 0 ? -21 : 24, 5, 25, material("slime_infused_planks"));
      for (int z : new int[] {-7, 1, 9, 17, 25}) {
        stone(side * 24, 0, z, side * 24, 4, z);
        put(side * 24, 4, z, material("chiseled_slimic_stone_bricks"));
        put(side * 23, 4, z, material("gel_lantern"));
      }
      put(side * 17, 0, 18, material(side < 0 ? "stone_tablet" : "tool_rack"));
      put(side * 18, 0, 19, material(side < 0 ? "cracked_stone_tablet" : "broken_workbench"));
      put(side * 17, 0, 5, material(side < 0 ? "relic_shard_pile" : "supply_urn"));
    }
    // Two bridges turn the long upper balconies into a circuit, with views into the hall below.
    for (int z : new int[] {6, 24}) {
      fill(-15, 7, z - 1, 15, 7, z + 1, material("slime_infused_planks"));
      for (int edge : new int[] {z - 2, z + 2}) {
        fill(-14, 7, edge, 14, 7, edge, material("ancient_foundation_bricks"));
        fill(-14, 8, edge, 14, 8, edge, material("slimic_stone_bricks_slab"));
      }
      for (int x : new int[] {-10, -5, 5, 10}) {
        put(
            x,
            6,
            z - 1,
            stairs("slimic_stone_bricks_stairs", Direction.SOUTH)
                .setValue(StairBlock.HALF, Half.TOP));
        put(
            x,
            6,
            z + 1,
            stairs("slimic_stone_bricks_stairs", Direction.NORTH)
                .setValue(StairBlock.HALF, Half.TOP));
      }
      // Existing gallery rails must open at both bridge entrances.
      air(-14, 8, z - 1, 14, 10, z + 1);
    }
  }

  boolean generate(WorldGenLevel world, TagKey<Block> terrain) {
    foundation();
    approach();
    curtain();
    for (int x : new int[] {-25, 25}) {
      tower(x, -23, 18, false);
      tower(x, 33, 22, x > 0);
    }
    gatehouse();
    ward();
    keep();
    if (furnished) {
      facadeDetails();
      wardDetails();
      furnishings();
    }
    if (expanded) roomsAndCrossings();
    // The original route and its floor profile remain continuous across all five saved pieces.
    for (int z = minZ; z <= maxZ; z++)
      air(-2, floor.applyAsInt(z), z, 2, floor.applyAsInt(z) + 3, z);
    for (var entry : plan.entrySet()) {
      var pos = entry.getKey();
      var old = world.getBlockState(pos);
      int localZ =
          FallenSanctumPiece.position(
                  BlockPos.ZERO, -turn, pos.getX() - anchor.getX(), 0, pos.getZ() - anchor.getZ())
              .getZ();
      int ground = anchor.getY() + floor.applyAsInt(localZ);
      if (world.getBlockEntity(pos) != null
          || old.getDestroySpeed(world, pos) < 0
          || !old.getFluidState().isEmpty() && pos.getY() >= ground
          || !old.isAir()
              && !old.canBeReplaced()
              && !old.is(terrain)
              && !(pos.getY() < ground && !old.getFluidState().isEmpty())
              && !old.is(BlockTags.LOGS)
              && !old.is(BlockTags.LEAVES)) return false;
    }
    plan.forEach((pos, state) -> world.setBlock(pos, state, 2));
    return true;
  }
}
