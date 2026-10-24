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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Three inhabited storeys, connected wards and a sunken royal vault, planned per chunk. */
final class SanctumLabyrinthArchitecture {
  static final int MIN_X = -56, MAX_X = 55, MIN_Z = -56, MAX_Z = 55, HEIGHT = 48;
  private final BlockPos anchor;
  private final Rotation rotation;
  private final int turn, minX, maxX, minZ, maxZ, minY, maxY;
  private final IntUnaryOperator approachFloor;
  private final Map<BlockPos, BlockState> plan = new LinkedHashMap<>();

  SanctumLabyrinthArchitecture(
      BlockPos anchor, int turn, int zone, BoundingBox clip, IntUnaryOperator floor) {
    this.anchor = anchor;
    this.turn = Math.floorMod(turn, 4);
    rotation = Rotation.values()[this.turn];
    approachFloor = floor;
    var a =
        FallenSanctumPiece.position(
            BlockPos.ZERO, -turn, clip.minX() - anchor.getX(), 0, clip.minZ() - anchor.getZ());
    var b =
        FallenSanctumPiece.position(
            BlockPos.ZERO, -turn, clip.maxX() - anchor.getX(), 0, clip.maxZ() - anchor.getZ());
    minX = Math.max(MIN_X, Math.min(a.getX(), b.getX()));
    maxX = Math.min(MAX_X, Math.max(a.getX(), b.getX()));
    minZ = Math.max(zone == 0 ? MIN_Z : -40 + zone * 16, Math.min(a.getZ(), b.getZ()));
    maxZ = Math.min(zone == 4 ? MAX_Z : -25 + zone * 16, Math.max(a.getZ(), b.getZ()));
    minY = Math.max(-16, clip.minY() - anchor.getY());
    maxY = Math.min(HEIGHT, clip.maxY() - anchor.getY());
  }

  private static BlockState material(String id) {
    return ModWorldBlocks.block(id).defaultBlockState();
  }

  private static BlockState masonry(int x, int y, int z) {
    int age = Math.floorMod(x * 31 + y * 17 + z * 13, 41);
    return material(
        age < 4
            ? "mossy_slimic_stone_bricks"
            : age < 8 ? "cracked_slimic_stone_bricks" : "slimic_stone_bricks");
  }

  private static BlockState stairs(Direction direction) {
    return material("slimic_stone_bricks_stairs").setValue(StairBlock.FACING, direction);
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

  private int ground(int x, int z) {
    if (Math.abs(x) <= 3 && z <= -40) return approachFloor.applyAsInt(z);
    return Math.abs(x) <= 29 && z >= 24 && z <= 44 ? -Math.min(4, z - 24) : 0;
  }

  private void foundation() {
    for (int x = minX; x <= maxX; x++)
      for (int z = minZ; z <= maxZ; z++) {
        boolean castle = x >= -54 && x <= 53 && z >= -54 && z <= 53;
        if (!castle && (Math.abs(x) > 5 || z > -40)) continue;
        int y = ground(x, z);
        fill(x, y - 7, z, x, y - 2, z, material("ancient_foundation"));
        put(
            x,
            y - 1,
            z,
            material(Math.floorMod(x * 3 + z, 17) == 0 ? "cracked_slimic_tiles" : "slimic_tiles"));
        air(x, y, z, x, HEIGHT, z);
      }
  }

  private void curtain() {
    for (int side : new int[] {-1, 1}) {
      int lo = side < 0 ? -54 : 50, hi = lo + 3, outer = side < 0 ? lo : hi;
      stone(lo, 0, -53, hi, 8, 52);
      fill(lo, 9, -53, hi, 9, 52, material("ancient_foundation_bricks"));
      stone(outer, 10, -53, outer, 10, 52);
      for (int z = -53; z <= 52; z++)
        if (Math.floorMod(z, 4) < 2) put(outer, 11, z, masonry(outer, 11, z));
      for (int z = -36; z <= 36; z += 12) {
        stone(outer + side, 0, z, outer + side, 7, z + 1);
        put(outer + side, 8, z, material("chiseled_slime_marble"));
      }
    }
    for (int z : new int[] {-54, 50}) {
      stone(-54, 0, z, 53, 8, z + 3);
      fill(-54, 9, z, 53, 9, z + 3, material("ancient_foundation_bricks"));
      int outer = z < 0 ? z : z + 3;
      stone(-54, 10, outer, 53, 10, outer);
      for (int x = -54; x <= 53; x++)
        if (Math.floorMod(x, 4) < 2) put(x, 11, outer, masonry(x, 11, outer));
    }
  }

  private void curtainAccess() {
    // Applied after the ward walls, so their stone cannot fill a staircase's headroom.
    for (int x : new int[] {-48, 48}) {
      fill(
          x < 0 ? -51 : 46,
          9,
          -25,
          x < 0 ? -46 : 51,
          9,
          -23,
          material("ancient_foundation_bricks"));
      fill(x < 0 ? -51 : 46, 9, 23, x < 0 ? -46 : 51, 9, 25, material("ancient_foundation_bricks"));
      stairFlight(x, -35, 0, 1);
      stairFlight(x, 35, 0, -1);
    }
  }

  private static boolean towerColumn(int x, int z, int cx, int cz) {
    int dx = Math.abs(x - cx), dz = Math.abs(z - cz);
    return Math.max(dx, dz) <= 5 && dx + dz <= 8;
  }

  private void tower(int cx, int cz, int height, boolean broken) {
    for (int x = cx - 5; x <= cx + 5; x++)
      for (int z = cz - 5; z <= cz + 5; z++) {
        if (!towerColumn(x, z, cx, cz)) continue;
        int dx = Math.abs(x - cx), dz = Math.abs(z - cz);
        boolean edge = Math.max(dx, dz) == 5 || dx + dz == 8;
        if (edge) {
          stone(x, 0, z, x, height - 6, z);
          for (int y : new int[] {1, 9, 19, height - 7})
            put(x, y, z, material("chiseled_slime_marble"));
          if (dx == 5 && dz == 0 || dz == 5 && dx == 0)
            for (int y : new int[] {3, 13, 23})
              if (y + 2 < height - 6) fill(x, y, z, x, y + 2, z, material("gel_glass"));
        } else {
          air(x, 0, z, x, height - 7, z);
          for (int y : new int[] {9, 19}) put(x, y, z, material("slime_infused_planks"));
        }
        int roof = height - 5 + (5 - Math.max(dx, dz));
        boolean hole = broken && x > cx && z > cz;
        if (!hole) put(x, roof, z, material("slimic_slate_bricks"));
      }
    // Inner doorways and wall-walk portals preserve access across piece boundaries.
    for (int y : new int[] {0, 10}) {
      for (int dx : new int[] {-5, 5}) air(cx + dx, y, cz - 1, cx + dx, y + 3, cz + 1);
      for (int dz : new int[] {-5, 5}) air(cx - 1, y, cz + dz, cx + 1, y + 3, cz + dz);
    }
    // The ladder rests against the west inner wall and cuts both timber floors.
    air(cx - 4, 1, cz + 2, cx - 4, 21, cz + 2);
    fill(
        cx - 4,
        1,
        cz + 2,
        cx - 4,
        21,
        cz + 2,
        Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST));
    put(cx + 2, 0, cz - 2, material("tool_rack"));
    put(cx + 2, 10, cz + 2, material("stone_bench"));
    put(cx + 2, 20, cz - 2, material("cracked_supply_urn"));
    put(cx, height, cz, Blocks.GOLD_BLOCK.defaultBlockState());
    put(cx, height + 1, cz, material("royal_crest"));
    if (broken) put(cx + 2, 23, cz + 2, Blocks.COBWEB.defaultBlockState());
  }

  private void gatehouse() {
    for (int cx : new int[] {-11, 11}) {
      stone(cx - 5, 0, -54, cx + 5, 23, -43);
      air(cx - 3, 0, -52, cx + 3, 22, -45);
      for (int y : new int[] {9, 19})
        fill(cx - 3, y, -52, cx + 3, y, -45, material("slime_infused_planks"));
      for (int x = cx - 6; x <= cx + 6; x++)
        for (int z = -55; z <= -42; z++)
          put(x, 24 + (6 - Math.abs(x - cx)) / 2, z, material("slimic_slate_bricks"));
      for (int y : new int[] {0, 10, 20}) air(cx - 2, y, -44, cx + 2, y + 3, -43);
      air(cx < 0 ? -6 : 6, 10, -50, cx < 0 ? -6 : 6, 13, -46);
      fill(
          cx - 3,
          1,
          -50,
          cx - 3,
          21,
          -50,
          Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST));
      standard(cx, 23, -55, cx > 0);
      put(cx, 28, -48, Blocks.GOLD_BLOCK.defaultBlockState());
      put(cx, 29, -48, material("royal_crest"));
    }
    fill(-6, 9, -52, 6, 9, -45, material("ancient_foundation_bricks"));
    stone(-6, 10, -52, 6, 12, -52);
    stone(-6, 10, -45, 6, 10, -45);
    air(-3, -7, -55, 3, 6, -43);
    for (int side : new int[] {-1, 1}) {
      stone(side * 4, 0, -55, side * 4, 5, -54);
      put(
          side * 3,
          6,
          -55,
          stairs(side < 0 ? Direction.EAST : Direction.WEST).setValue(StairBlock.HALF, Half.TOP));
    }
    fill(-2, 7, -55, 2, 7, -54, material("chiseled_slime_marble"));
    put(0, 12, -53, material("royal_crest"));
  }

  private void standard(int cx, int top, int z, boolean torn) {
    fill(cx - 1, top, z, cx + 1, top, z, material("chiseled_slime_marble"));
    for (int dx = -1; dx <= 1; dx++)
      for (int down = 1; down <= 6; down++) {
        if (down == 6 && dx != 0 || torn && dx == 1 && down >= 4) continue;
        put(
            cx + dx,
            top - down,
            z,
            (down == 2 || down == 3 && dx == 0 ? Blocks.YELLOW_WOOL : Blocks.GREEN_WOOL)
                .defaultBlockState());
      }
  }

  private void ward() {
    // An offset gateway makes the first ward a real dogleg, without a central through-road.
    stone(-51, 0, -29, 50, 7, -28);
    fill(-51, 8, -29, 50, 8, -28, material("chiseled_slime_marble"));
    doorwayZ(-36, 0, -29, -28, Blocks.YELLOW_TERRACOTTA.defaultBlockState());
    for (int x = -49; x <= 49; x += 7) {
      put(x, 9, -29, masonry(x, 9, -29));
      if (x % 2 == 0) put(x, 10, -29, material("gel_lantern"));
    }
    for (int side : new int[] {-1, 1}) {
      int x1 = side < 0 ? -31 : 17, x2 = x1 + 14;
      shell(x1, -41, x2, -32, 0, 6);
      doorwayZ(side * 23, 0, -32, -32, Blocks.YELLOW_TERRACOTTA.defaultBlockState());
      fill(x1 - 1, 7, -42, x2 + 1, 7, -31, material("slimic_slate_bricks"));
      for (int z : new int[] {-39, -36}) {
        put(x1 + 2, 0, z, material("stone_bench"));
        put(x2 - 2, 0, z, material(side < 0 ? "tool_rack" : "supply_urn"));
      }
      put(x1 + 3, 0, -34, material("low_stone_table"));
      put(x1 + 3, 1, -34, material("cracked_stone_tablet"));
      put(x2 - 2, 4, -39, material("gel_lantern"));
      // Arcaded shelters frame the larger inner courtyard.
      int lo = side < 0 ? -31 : 24, hi = lo + 7;
      fill(lo, 7, -24, hi, 7, -10, material("slime_infused_planks"));
      for (int z : new int[] {-24, -17, -10}) {
        stone(side * 24, 0, z, side * 24, 6, z);
        put(side * 24, 6, z, material("chiseled_slime_marble"));
        put(side * 25, 6, z, material("gel_lantern"));
      }
    }
    // Dry basins retain their windlasses; no fluid can escape into the maze.
    for (int cx : new int[] {-27, 27}) {
      fill(cx - 2, -2, -20, cx + 2, -2, -16, material("mossy_slimic_cobblestone"));
      air(cx - 1, -1, -19, cx + 1, 0, -17);
      for (int x = cx - 2; x <= cx + 2; x++)
        for (int z = -20; z <= -16; z++)
          if (Math.abs(x - cx) == 2 || z == -20 || z == -16)
            put(x, 0, z, material("mossy_slimic_stone_bricks_slab"));
      for (int x : new int[] {cx - 2, cx + 2})
        fill(x, 1, -18, x, 3, -18, material("slime_infused_planks"));
      fill(cx - 2, 4, -18, cx + 2, 4, -18, material("slime_infused_planks"));
      fill(cx, 1, -18, cx, 3, -18, Blocks.CHAIN.defaultBlockState());
    }
    for (int x : new int[] {-12, 12}) {
      stone(x, 0, -22, x, 1, -22);
      put(x, 2, -22, material("ancient_brazier"));
    }
    for (int z : new int[] {-22, -11})
      fill(-13, -1, z, 13, -1, z, material("chiseled_slime_marble"));
  }

  private void shell(int x1, int z1, int x2, int z2, int bottom, int top) {
    stone(x1, bottom, z1, x2, top, z1);
    stone(x1, bottom, z2, x2, top, z2);
    stone(x1, bottom, z1, x1, top, z2);
    stone(x2, bottom, z1, x2, top, z2);
  }

  private void doorwayZ(int cx, int y, int z1, int z2, BlockState clue) {
    air(cx - 2, y, z1, cx + 2, y + 3, z2);
    fill(cx - 2, y + 4, z1, cx + 2, y + 4, z2, material("chiseled_slime_marble"));
    put(cx, y + 4, z1, clue);
    put(cx - 3, y + 2, z1, material("gel_lantern"));
  }

  private void doorwayX(int x, int y, int cz, BlockState clue) {
    air(x, y, cz - 2, x, y + 3, cz + 2);
    fill(x, y + 4, cz - 2, x, y + 4, cz + 2, material("chiseled_slime_marble"));
    put(x, y + 4, cz, clue);
  }

  private void keep() {
    shell(-29, -8, 29, 44, -4, 29);
    for (int y : new int[] {9, 19, 29}) {
      fill(-29, y, -8, 29, y, -8, material("chiseled_slime_marble"));
      fill(-29, y, 44, 29, y, 44, material("chiseled_slime_marble"));
      fill(-29, y, -8, -29, y, 44, material("chiseled_slime_marble"));
      fill(29, y, -8, 29, y, 44, material("chiseled_slime_marble"));
    }
    for (int side : new int[] {-1, 1}) {
      for (int z = -4; z <= 40; z += 8) {
        stone(side * 30, 0, z, side * 30, 27, z + 1);
        for (int y : new int[] {3, 13, 23})
          fill(side * 29, y, z + 3, side * 29, y + 3, z + 4, material("gel_glass"));
        put(side * 30, 28, z, material("slime_marble_slab"));
      }
      for (int x : new int[] {side * 8, side * 16, side * 26}) {
        stone(x, 0, -9, x, 27, -9);
        for (int y : new int[] {6, 16, 26}) put(x, y, -9, material("royal_crest"));
      }
      standard(side * 8, 27, -10, side < 0);
      standard(side * 16, 17, -10, side > 0);
    }
    // Three full floors retain openings into the entrance, laboratory and deep atria.
    for (int y : new int[] {9, 19}) {
      fill(-28, y, -7, 28, y, 43, material("slime_infused_planks"));
      atrium(-10, -5, 10, 5, y);
      atrium(-10, 11, 10, 21, y);
      atrium(-10, 29, 10, 39, y);
    }
    bridge(-12, -1, 12, 1, 9);
    bridge(-12, 15, 12, 17, 9);
    bridge(-12, 31, 12, 33, 9);
    bridge(-1, -7, 1, 7, 19);
    bridge(-1, 9, 1, 23, 19);
    bridge(-12, 35, 12, 37, 19);
    // Solid crosswalls and alternating gateways replace the old straight central corridor.
    for (int level = 0; level < 3; level++) {
      int y = level * 10;
      for (int z : new int[] {8, 24}) {
        stone(-28, y, z, 28, y + 8, z);
        int gate = z == 8 ? (level == 1 ? 20 : -22) : (level == 1 ? -22 : 22);
        doorwayZ(
            gate,
            y,
            z,
            z,
            (z == 8 ? Blocks.CYAN_TERRACOTTA : Blocks.PURPLE_TERRACOTTA).defaultBlockState());
      }
      for (int side : new int[] {-1, 1})
        for (int section = 0; section < 3; section++) {
          int z1 = -7 + section * 16, z2 = section == 2 ? 43 : z1 + 14;
          int bottom = level == 0 && section == 2 ? -4 : y;
          stone(side * 14, bottom, z1, side * 14, y + 8, z2);
          int door = section * 16 + (side < 0 ? -1 : 1);
          doorwayX(
              side * 14,
              bottom,
              door,
              (section == 0
                      ? Blocks.YELLOW_TERRACOTTA
                      : section == 1 ? Blocks.CYAN_TERRACOTTA : Blocks.PURPLE_TERRACOTTA)
                  .defaultBlockState());
          // A second opening creates a genuine room loop, separate from the crosswall gate.
          if (section > 0)
            doorwayX(
                side * 14,
                bottom,
                section * 16 + 6,
                Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState());
        }
    }
    doorwayZ(20, 0, -9, -8, Blocks.YELLOW_TERRACOTTA.defaultBlockState());
    // Roof slopes span the whole keep, with a narrow stepped ridge and two weathered holes.
    for (int x = -31; x <= 31; x++)
      for (int z = -10; z <= 46; z++) {
        int roof = 30 + (31 - Math.abs(x)) / 2;
        boolean hole =
            x >= 18 && x <= 24 && z >= 12 && z <= 18 && x + z > 33
                || x <= -20 && x >= -24 && z >= 34 && z <= 37;
        if (hole) continue;
        put(x, roof, z, material("slimic_slate_bricks"));
        if (Math.abs(x) > 1)
          put(
              x,
              roof + 1,
              z,
              material("black_slime_stone_stairs")
                  .setValue(StairBlock.FACING, x < 0 ? Direction.EAST : Direction.WEST));
        else put(x, roof + 1, z, material("black_slime_stone"));
      }
    for (int z : new int[] {-8, 44}) {
      for (int x = -29; x <= 29; x++) stone(x, 30, z, x, 30 + (29 - Math.abs(x)) / 2, z);
      for (int x = -4; x <= 4; x++)
        for (int dy = -4; dy <= 4; dy++) {
          int distance = Math.abs(x) + Math.abs(dy);
          if (distance <= 4)
            put(x, 36 + dy, z, material(distance == 4 ? "chiseled_slime_marble" : "gel_glass"));
        }
      put(0, 47, z, Blocks.GOLD_BLOCK.defaultBlockState());
      put(0, 48, z, material("royal_crest"));
    }
    // Chandelier chains stay high above every bridge and the remnant spawn spaces.
    for (int z : new int[] {0, 16, 34}) {
      fill(5, 25, z, 5, 37, z, Blocks.CHAIN.defaultBlockState());
      fill(3, 24, z, 7, 24, z, material("slime_marble_slab"));
      for (int x : new int[] {3, 5, 7}) put(x, 23, z, material("gel_lantern"));
    }
  }

  private void atrium(int x1, int z1, int x2, int z2, int y) {
    air(x1, y, z1, x2, y, z2);
    for (int x : new int[] {x1 - 1, x2 + 1}) fill(x, y + 1, z1, x, y + 1, z2, rail(false));
    for (int z : new int[] {z1 - 1, z2 + 1}) fill(x1 - 1, y + 1, z, x2 + 1, y + 1, z, rail(true));
  }

  private static BlockState rail(boolean eastWest) {
    return material("slime_marble_bricks_wall")
        .setValue(
            eastWest ? BlockStateProperties.EAST_WALL : BlockStateProperties.NORTH_WALL,
            WallSide.LOW)
        .setValue(
            eastWest ? BlockStateProperties.WEST_WALL : BlockStateProperties.SOUTH_WALL,
            WallSide.LOW);
  }

  private void bridge(int x1, int z1, int x2, int z2, int y) {
    fill(x1, y, z1, x2, y, z2, material("ancient_foundation_bricks"));
    air(x1, y + 1, z1, x2, y + 4, z2);
    if (x2 - x1 > z2 - z1) {
      for (int z : new int[] {z1 - 1, z2 + 1}) {
        fill(x1, y, z, x2, y, z, material("slime_marble"));
        fill(x1 + 1, y + 1, z, x2 - 1, y + 1, z, rail(true));
      }
    } else {
      for (int x : new int[] {x1 - 1, x2 + 1}) {
        fill(x, y, z1, x, y, z2, material("slime_marble"));
        fill(x, y + 1, z1 + 1, x, y + 1, z2 - 1, rail(false));
      }
    }
  }

  private void wings() {
    for (int side : new int[] {-1, 1}) {
      int x1 = side < 0 ? -46 : 33, x2 = x1 + 13, cx = side * 39;
      shell(x1, -24, x2, 40, 0, 29);
      for (int y : new int[] {9, 19})
        fill(x1 + 1, y, -23, x2 - 1, y, 39, material("slime_infused_planks"));
      for (int y : new int[] {0, 10, 20}) {
        for (int z : new int[] {-8, 8, 24}) {
          stone(x1 + 1, y, z, x2 - 1, y + 8, z);
          int gate = cx + (z == 8 ? -side * 2 : side * 2);
          doorwayZ(gate, y, z, z, Blocks.LIGHT_BLUE_TERRACOTTA.defaultBlockState());
        }
        // The two wings differ: a shelved archive to the west, workshops to the east.
        for (int z : new int[] {-17, -2, 14, 35}) {
          int wallX = side < 0 ? x1 + 1 : x2 - 1;
          fill(wallX, y, z - 2, wallX, y, z + 2, material("slime_infused_planks"));
          fill(wallX, y + 3, z - 2, wallX, y + 3, z + 2, material("slime_infused_planks_slab"));
          for (int dz : new int[] {-2, 0, 2})
            put(
                wallX,
                y + 1,
                z + dz,
                material(side < 0 ? "stone_tablet" : dz == 0 ? "tool_rack" : "supply_urn"));
          put(wallX, y + 5, z, material("gel_lantern"));
        }
      }
      doorwayZ(cx, 0, -24, -24, Blocks.LIGHT_BLUE_TERRACOTTA.defaultBlockState());
      // Two-storey bridges cross the covered service lanes and enter the keep side rooms.
      for (int y : new int[] {9, 19})
        for (int z : new int[] {0, 16, 36}) {
          bridge(side < 0 ? -34 : 28, z - 2, side < 0 ? -28 : 34, z + 2, y);
          doorwayX(side * 33, y + 1, z, Blocks.LIGHT_BLUE_TERRACOTTA.defaultBlockState());
          doorwayX(side * 29, y + 1, z, Blocks.LIGHT_BLUE_TERRACOTTA.defaultBlockState());
        }
      for (int z : new int[] {-2, 16}) {
        doorwayX(side * 33, 0, z, Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState());
        doorwayX(side * 29, 0, z, Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState());
      }
      for (int x = x1 - 1; x <= x2 + 1; x++)
        for (int z = -25; z <= 41; z++) {
          int roof = 30 + (7 - Math.abs(x - cx)) / 2;
          put(x, roof, z, material("slimic_slate_bricks"));
        }
      for (int z = -20; z <= 36; z += 8)
        for (int y : new int[] {3, 13, 23}) {
          fill(x1, y, z, x1, y + 3, z + 1, material("gel_glass"));
          fill(x2, y, z, x2, y + 3, z + 1, material("gel_glass"));
        }
      standard(cx, 28, -25, side < 0);
      stairFlight(cx, -20, 0, 1);
      stairFlight(cx, 20, 10, 1);
    }
  }

  /** Ten single-block rises, with five-wide footing and four blocks of overhead clearance. */
  private void stairFlight(int cx, int zStart, int yStart, int direction) {
    for (int step = 0; step < 10; step++) {
      int z = zStart + direction * step, y = yStart + step;
      fill(cx - 2, yStart, z, cx + 2, y - 1, z, material("ancient_foundation_bricks"));
      fill(cx - 2, y, z, cx + 2, y, z, stairs(direction > 0 ? Direction.SOUTH : Direction.NORTH));
      air(cx - 2, y + 1, z, cx + 2, y + 4, z);
    }
    int lastZ = zStart + direction * 10;
    fill(
        cx - 2,
        yStart + 9,
        Math.min(lastZ, lastZ + direction),
        cx + 2,
        yStart + 9,
        Math.max(lastZ, lastZ + direction),
        material("slime_marble"));
    air(
        cx - 2,
        yStart + 10,
        Math.min(lastZ, lastZ + direction),
        cx + 2,
        yStart + 13,
        Math.max(lastZ, lastZ + direction));
  }

  private void staircasesAndVault() {
    for (int cx : new int[] {-22, 22}) {
      stairFlight(cx, -6, 0, 1);
      stairFlight(cx, 24, 10, 1);
    }
    // The laboratory reaches the vault through the eastern gate and a short descending ramp.
    for (int z = 25; z <= 28; z++) {
      int y = 24 - z;
      fill(20, y - 1, z, 24, y - 1, z, material("ancient_foundation_bricks"));
      air(20, y, z, 24, y + 4, z);
    }
    // Rear stairs return to a postern, providing a loop instead of a single-exit deep chamber.
    for (int z = 39; z <= 43; z++) {
      int y = z - 43;
      fill(-24, -5, z, -20, y - 1, z, material("ancient_foundation_bricks"));
      air(-24, y, z, -20, y + 4, z);
    }
    doorwayZ(-22, 0, 44, 44, Blocks.PURPLE_TERRACOTTA.defaultBlockState());
    fill(-24, -1, 44, -20, -1, 45, material("ancient_foundation_bricks"));
    for (int side : new int[] {-1, 1}) {
      for (int z : new int[] {29, 40}) {
        stone(side * 12, -4, z, side * 12, 4, z);
        put(side * 12, 5, z, material("chiseled_slime_marble"));
        put(side * 11, 4, z, material("gel_lantern"));
      }
      fill(side * 12, -4, 35, side * 12, -2, 35, material("chiseled_slimic_stone_bricks"));
      put(side * 12, -1, 35, material("dormant_beacon"));
      put(side * 11, -4, 38, material("cracked_supply_urn"));
    }
  }

  private void roomDetails() {
    for (int level = 0; level < 3; level++) {
      int y = level * 10;
      for (int section = 0; section < 3; section++) {
        int cz = section * 16, base = level == 0 && section == 2 ? -4 : y;
        for (int side : new int[] {-1, 1}) {
          int x = side * 27;
          // Recessed cabinets and return walls form side alcoves with an optional dead end.
          stone(side < 0 ? -28 : 18, base, cz + 5, side < 0 ? -18 : 28, y + 5, cz + 5);
          air(side < 0 ? -20 : 18, base, cz + 5, side < 0 ? -18 : 20, base + 3, cz + 5);
          put(
              x,
              base,
              cz + 3,
              material(
                  section == 0
                      ? "stone_bench"
                      : section == 1 ? "broken_workbench" : "relic_shard_pile"));
          put(x, base, cz + 4, material(side < 0 ? "cracked_stone_tablet" : "abandoned_crate"));
          put(x, base + 4, cz + 3, material("gel_lantern"));
          if (level == 2 && section == 1) put(x, y + 6, cz + 4, Blocks.COBWEB.defaultBlockState());
        }
        if (level == 0) continue;
        for (int side : new int[] {-1, 1}) {
          int x = side * 16;
          put(x, y, cz + 2, material(section == 1 ? "gel_basin" : "low_stone_table"));
          put(x, y, cz + 3, material(section == 1 ? "small_gel_crystal_cluster" : "stone_bench"));
        }
      }
    }
    // A small royal seat faces the entrance hall without obstructing its relay or guard homes.
    fill(-11, 0, 4, -7, 0, 5, material("slime_marble"));
    stone(-11, 1, 5, -7, 3, 5);
    put(-9, 1, 4, material("slime_marble_stairs").setValue(StairBlock.FACING, Direction.SOUTH));
    put(-9, 4, 5, material("royal_crest"));
    for (int z : new int[] {11, 21}) {
      fill(15, 0, z, 18, 0, z, material("slime_marble_slab"));
      put(16, 1, z, material("gel_basin"));
      put(18, 1, z, material("small_gel_crystal_cluster"));
    }
    // Color at a room's threshold identifies its district without drawing a continuous route.
    for (int z : new int[] {-16, 0, 16, 32}) {
      int y = z == 32 ? -5 : -1;
      for (int x = -10; x <= 10; x++)
        put(
            x,
            y,
            z + 1,
            (z < 0
                    ? Blocks.YELLOW_TERRACOTTA
                    : z == 0
                        ? Blocks.LIGHT_GRAY_TERRACOTTA
                        : z == 16 ? Blocks.CYAN_TERRACOTTA : Blocks.PURPLE_TERRACOTTA)
                .defaultBlockState());
    }
  }

  private void approach() {
    // Only the entrance ramp follows surface relief. Its height changes by at most one block.
    for (int z = MIN_Z; z <= -40; z++) {
      int y = approachFloor.applyAsInt(z);
      fill(-3, y - 7, z, 3, y - 2, z, material("ancient_foundation"));
      fill(-3, y - 1, z, 3, y - 1, z, material("slime_infused_planks"));
      air(-3, y, z, 3, y + 2, z);
    }
    for (int x : new int[] {-5, 5}) {
      put(x, 0, -40, material("royal_waymarker"));
      put(x, 1, -55, material("old_slime_banner"));
    }
  }

  private void dryDitch() {
    // The gate opens onto a dry inner defense, divided by the barracks' stone abutments.
    for (int x = -45; x <= 45; x++) {
      if (Math.abs(x) >= 17 && Math.abs(x) <= 31) continue;
      fill(x, -5, -42, x, -5, -39, material("mossy_slimic_cobblestone"));
      air(x, -4, -42, x, 0, -39);
      if (Math.abs(x) <= 3) fill(x, -1, -42, x, -1, -39, material("slime_infused_planks"));
    }
    for (int x : new int[] {-4, 4}) fill(x, 0, -42, x, 0, -39, rail(false));
  }

  boolean generate(WorldGenLevel world, TagKey<Block> terrain) {
    foundation();
    curtain();
    for (int x : new int[] {-48, 48}) {
      tower(x, -48, 34, false);
      tower(x, 0, 32, x > 0);
      tower(x, 48, 38, false);
    }
    for (int x : new int[] {-16, 16}) tower(x, 48, 40, x < 0);
    gatehouse();
    ward();
    keep();
    wings();
    roomDetails();
    staircasesAndVault();
    curtainAccess();
    approach();
    dryDitch();
    // Validate the complete clipped plan before touching a block, including the air volumes.
    for (var entry : plan.entrySet()) {
      var pos = entry.getKey();
      if (!world.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return false;
      var old = world.getBlockState(pos);
      var local =
          FallenSanctumPiece.position(
              BlockPos.ZERO, -turn, pos.getX() - anchor.getX(), 0, pos.getZ() - anchor.getZ());
      int ground = anchor.getY() + ground(local.getX(), local.getZ());
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
    // Stair directions and connected rails are part of the plan. Updating neighbour shapes here
    // would let a later chunk rewrite an earlier chunk's railing, depending on generation order.
    plan.forEach(
        (pos, state) ->
            world.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
    return true;
  }
}
