package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.SanctumRelayBlockEntity;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.world.FallenSanctumPiece;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

/**
 * Version-four expedition routes are checked against generated collision shapes, not plan nodes.
 */
@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SanctumLabyrinthGameTests {
  private static int next;

  private static BlockPos anchor() {
    return new BlockPos(460008 + next++ * 192, 220, -460008);
  }

  private static BlockPos at(BlockPos anchor, int rotation, int x, int y, int z) {
    return FallenSanctumPiece.position(anchor, rotation, x, y, z);
  }

  private static BlockPos local(BlockPos anchor, int rotation, BlockPos pos) {
    return at(
        BlockPos.ZERO,
        -rotation,
        pos.getX() - anchor.getX(),
        pos.getY() - anchor.getY(),
        pos.getZ() - anchor.getZ());
  }

  private static FallenSanctumPiece piece(
      ServerLevel level, BlockPos anchor, int rotation, int zone) {
    var context = StructurePieceSerializationContext.fromLevel(level);
    return new FallenSanctumPiece(
        context, new FallenSanctumPiece(anchor, rotation, zone).createTag(context));
  }

  private static void generate(ServerLevel level, BlockPos anchor, int rotation, boolean reverse) {
    var first = at(anchor, rotation, -56, 0, -56);
    var last = at(anchor, rotation, 55, 0, 55);
    var chunks = new ArrayList<ChunkPos>();
    for (int x = Math.min(first.getX(), last.getX()) >> 4;
        x <= Math.max(first.getX(), last.getX()) >> 4;
        x++)
      for (int z = Math.min(first.getZ(), last.getZ()) >> 4;
          z <= Math.max(first.getZ(), last.getZ()) >> 4;
          z++) chunks.add(new ChunkPos(x, z));
    if (reverse) Collections.reverse(chunks);
    for (var chunk : chunks) {
      level.getChunk(chunk.x, chunk.z);
      var clip =
          new BoundingBox(
              chunk.getMinBlockX(),
              level.getMinBuildHeight(),
              chunk.getMinBlockZ(),
              chunk.getMaxBlockX(),
              level.getMaxBuildHeight() - 1,
              chunk.getMaxBlockZ());
      for (int i = 0; i < 5; i++) {
        var piece = piece(level, anchor, rotation, reverse ? 4 - i : i);
        if (piece.getBoundingBox().intersects(clip))
          piece.postProcess(
              level,
              level.structureManager(),
              level.getChunkSource().getGenerator(),
              RandomSource.create(1234),
              clip,
              chunk,
              anchor);
      }
    }
  }

  private static AABB body(BlockPos pos) {
    return new AABB(
        pos.getX() + .2,
        pos.getY(),
        pos.getZ() + .2,
        pos.getX() + .8,
        pos.getY() + 1.8,
        pos.getZ() + .8);
  }

  private static boolean standing(ServerLevel level, BlockPos pos) {
    if (!level.getFluidState(pos).isEmpty() || !level.noCollision(body(pos))) return false;
    var below = level.getBlockState(pos.below());
    return below.isFaceSturdy(level, pos.below(), Direction.UP)
        || below.getBlock() instanceof StairBlock
        || below.getBlock() instanceof SlabBlock;
  }

  static Map<BlockPos, Integer> walkable(
      ServerLevel level, BlockPos anchor, int rotation, BlockPos start) {
    return walkable(level, anchor, rotation, start, p -> false);
  }

  private static Map<BlockPos, Integer> walkable(
      ServerLevel level,
      BlockPos anchor,
      int rotation,
      BlockPos start,
      Predicate<BlockPos> excludedLocal) {
    var distances = new HashMap<BlockPos, Integer>();
    var standing = new HashMap<BlockPos, Boolean>();
    var queue = new ArrayDeque<BlockPos>();
    if (!standing(level, start)) return distances;
    distances.put(start, 0);
    queue.add(start);
    while (!queue.isEmpty()) {
      var pos = queue.remove();
      for (var direction : Direction.Plane.HORIZONTAL)
        for (int rise : new int[] {0, 1, -1}) {
          var target = pos.relative(direction).offset(0, rise, 0);
          if (distances.containsKey(target)) continue;
          var local = local(anchor, rotation, target);
          if (local.getX() < -55
              || local.getX() > 54
              || local.getZ() < -56
              || local.getZ() > 54
              || local.getY() < -7
              || local.getY() > 35
              || excludedLocal.test(local)) continue;
          if (!standing.computeIfAbsent(target, p -> standing(level, p))) continue;
          // Check the departure headroom on a rise, and the arrival headroom before a descent.
          // This prevents the graph from jumping through a ceiling or a diagonal wall corner.
          if (rise > 0 && !level.noCollision(body(pos.above()))) continue;
          if (rise < 0 && !level.noCollision(body(target.above()))) continue;
          distances.put(target, distances.get(pos) + 1);
          queue.add(target);
        }
    }
    return distances;
  }

  static void assertDevicesReachable(
      GameTestHelper h,
      ServerLevel level,
      BlockPos anchor,
      int rotation,
      Map<BlockPos, Integer> seen) {
    for (int zone = 1; zone <= 4; zone++) {
      int z = -32 + zone * 16, y = zone == 4 ? -4 : 0;
      var relay = at(anchor, rotation, 6, y, z);
      var chest = at(anchor, rotation, -8, y, z - 2);
      h.assertTrue(
          level.getBlockEntity(relay) instanceof SanctumRelayBlockEntity,
          "Original relay socket " + rotation + "/" + zone);
      h.assertTrue(
          level.getBlockEntity(chest) instanceof ChestBlockEntity,
          "Original loot chest " + rotation + "/" + zone);
      for (var device : List.of(relay, chest))
        h.assertTrue(
            Direction.Plane.HORIZONTAL.stream().anyMatch(d -> seen.containsKey(device.relative(d))),
            "Walking route reaches device " + rotation + "/" + zone + "/" + device);
      for (int x : new int[] {-8, 0, 8}) {
        var home = at(anchor, rotation, x, y, z);
        h.assertTrue(
            level.noCollision(
                new AABB(
                    home.getX() - .02,
                    home.getY(),
                    home.getZ() - .02,
                    home.getX() + 1.02,
                    home.getY() + 1.04,
                    home.getZ() + 1.02)),
            "Preserved encounter spawn is unobstructed " + rotation + "/" + zone + "/" + x);
      }
    }
    var remains = at(anchor, rotation, -16, 0, -13);
    h.assertTrue(
        Direction.Plane.HORIZONTAL.stream().anyMatch(d -> seen.containsKey(remains.relative(d))),
        "The guaranteed mining core remains are accessible on foot");
  }

  @GameTest(template = "empty", batch = "sanctum_labyrinth", timeoutTicks = 6000)
  public static void everyRotationHasConnectedThreeStoreyExpeditionRoutes(GameTestHelper h) {
    var level = h.getLevel();
    for (int rotation = 0; rotation < 4; rotation++) {
      var anchor = anchor();
      generate(level, anchor, rotation, rotation % 2 != 0);
      var start = at(anchor, rotation, 0, 0, -55);
      var seen = walkable(level, anchor, rotation, start);
      assertDevicesReachable(h, level, anchor, rotation, seen);
      for (var node :
          List.of(
              new BlockPos(-36, 0, -28), new BlockPos(20, 0, -8),
              new BlockPos(-22, 0, 8), new BlockPos(22, -4, 28),
              new BlockPos(-22, 10, 4), new BlockPos(22, 10, 4),
              new BlockPos(-22, 20, 34), new BlockPos(22, 20, 34),
              new BlockPos(-39, 10, -10), new BlockPos(39, 10, -10),
              new BlockPos(-39, 20, 30), new BlockPos(39, 20, 30)))
        h.assertTrue(
            seen.containsKey(at(anchor, rotation, node.getX(), node.getY(), node.getZ())),
            "No ladder, flight or block breaking needed at " + rotation + "/" + node);
      for (int floor : new int[] {0, 10, 20})
        for (int side : new int[] {-1, 1}) {
          for (int section = 0; section < 3; section++) {
            int y = floor == 0 && section == 2 ? -4 : floor;
            var alcove = at(anchor, rotation, side * 25, y, section * 16 + 6);
            h.assertTrue(
                seen.containsKey(alcove),
                "Furnished side alcove remains accessible "
                    + rotation
                    + "/"
                    + side
                    + "/"
                    + floor
                    + "/"
                    + section);
          }
          for (int z : new int[] {-16, -2, 14, 34}) {
            var wingRoom = at(anchor, rotation, side * 36, floor, z);
            h.assertTrue(
                seen.containsKey(wingRoom),
                "Archive or workshop room remains accessible "
                    + rotation
                    + "/"
                    + side
                    + "/"
                    + floor
                    + "/"
                    + z);
          }
        }
      var coreAccess = at(anchor, rotation, 5, -4, 32);
      h.assertTrue(
          seen.getOrDefault(coreAccess, 0) >= 120,
          "Deep core route remains an expedition rather than the old straight corridor: "
              + seen.get(coreAccess));
      var relayDistances = new ArrayList<Integer>();
      for (int zone = 1; zone <= 4; zone++) {
        var relay = at(anchor, rotation, 6, zone == 4 ? -4 : 0, -32 + zone * 16);
        relayDistances.add(
            Direction.Plane.HORIZONTAL.stream()
                .mapToInt(d -> seen.getOrDefault(relay.relative(d), Integer.MAX_VALUE))
                .min()
                .orElseThrow());
      }
      System.out.println(
          "SLIMIC LABYRINTH WALK: rotation="
              + rotation
              + " nodes="
              + seen.size()
              + " relay_distances="
              + relayDistances
              + " core_distance="
              + seen.get(coreAccess));
      if (rotation == 0) {
        assertAlternateStairs(h, level, anchor, start);
        assertCompanionStairs(h, level, anchor);
      }
    }
    h.succeed();
  }

  private static void assertAlternateStairs(
      GameTestHelper h, ServerLevel level, BlockPos anchor, BlockPos start) {
    var withoutWestMainStair =
        walkable(
            level,
            anchor,
            0,
            start,
            p ->
                p.getX() >= -24
                    && p.getX() <= -20
                    && p.getZ() >= -6
                    && p.getZ() <= 3
                    && p.getY() < 10);
    var firstLanding = at(anchor, 0, -22, 10, 4);
    h.assertTrue(
        withoutWestMainStair.containsKey(firstLanding),
        "A separate stair and upper corridor complete the first-storey walking loop");
    var withoutWestUpperStairs =
        walkable(
            level,
            anchor,
            0,
            start,
            p ->
                p.getX() <= -20
                    && p.getX() >= -41
                    && p.getZ() >= 20
                    && p.getZ() <= 33
                    && p.getY() >= 10
                    && p.getY() < 20);
    var secondLanding = at(anchor, 0, -22, 20, 34);
    h.assertTrue(
        withoutWestUpperStairs.containsKey(secondLanding),
        "The third storey has another route when the western stair flights are unavailable");
    System.out.println(
        "SLIMIC LABYRINTH LOOPS: first_storey_detour="
            + withoutWestMainStair.get(firstLanding)
            + " third_storey_detour="
            + withoutWestUpperStairs.get(secondLanding));
  }

  private static void assertCompanionStairs(GameTestHelper h, ServerLevel level, BlockPos anchor) {
    var slime = ModEntities.MINING_SLIME.get().create(level);
    slime
        .getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)
        .setBaseValue(48);
    for (int x : new int[] {-22, 22})
      for (int floor : new int[] {0, 10}) {
        int z = floor == 0 ? -7 : 23;
        var from = at(anchor, 0, x, floor, z);
        var to = at(anchor, 0, x, floor + 10, z + 11);
        slime.moveTo(Vec3.atBottomCenterOf(from));
        slime.setOnGround(true);
        var path = slime.getNavigation().createPath(to, 0);
        h.assertTrue(
            path != null && path.canReach(),
            "Native companion navigation climbs the furnished three-wide stair " + x + "/" + floor);
      }
  }

  @GameTest(template = "empty", batch = "sanctum_labyrinth", timeoutTicks = 6000)
  public static void chunkOrderAndSavedPieceReloadDoNotChangeAnyBlock(GameTestHelper h) {
    var level = h.getLevel();
    var a = anchor();
    var b = anchor();
    generate(level, a, 0, false);
    generate(level, b, 0, true);
    for (int x = -56; x <= 55; x++)
      for (int z = -56; z <= 55; z++)
        for (int y = -16; y <= 48; y++) {
          BlockState first = level.getBlockState(at(a, 0, x, y, z));
          BlockState second = level.getBlockState(at(b, 0, x, y, z));
          h.assertTrue(
              first == second,
              "Chunk and piece order preserve geometry at "
                  + x
                  + "/"
                  + y
                  + "/"
                  + z
                  + ": "
                  + first
                  + " / "
                  + second);
        }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "sanctum_labyrinth", timeoutTicks = 400)
  public static void newBoundsAndEverySavedArchitectureVersionRoundTrip(GameTestHelper h) {
    var context = StructurePieceSerializationContext.fromLevel(h.getLevel());
    var anchor = anchor();
    int[] minZ = {-56, -24, -8, 8, 24}, maxZ = {-25, -9, 7, 23, 55};
    for (int rotation = 0; rotation < 4; rotation++)
      for (int zone = 0; zone < 5; zone++) {
        var saved = new FallenSanctumPiece(anchor, rotation, zone).createTag(context);
        var a = at(anchor, rotation, -56, -16, minZ[zone]);
        var b = at(anchor, rotation, 55, 48, maxZ[zone]);
        int[] expected = {
          Math.min(a.getX(), b.getX()),
          a.getY(),
          Math.min(a.getZ(), b.getZ()),
          Math.max(a.getX(), b.getX()),
          b.getY(),
          Math.max(a.getZ(), b.getZ())
        };
        h.assertTrue(
            saved.getInt("ArchitectureVersion") == 4
                && Arrays.equals(expected, saved.getIntArray("BB"))
                && saved.equals(new FallenSanctumPiece(context, saved).createTag(context)),
            "New version-four bounds survive save/reload " + rotation + "/" + zone);
        for (int version = 0; version <= 3; version++) {
          var old = saved.copy();
          old.putInt("ArchitectureVersion", version);
          var oldA = at(anchor, rotation, -32, -16, -40 + zone * 16);
          var oldB = at(anchor, rotation, 31, version == 0 ? 15 : 28, -25 + zone * 16);
          old.putIntArray(
              "BB",
              new int[] {
                Math.min(oldA.getX(), oldB.getX()),
                oldA.getY(),
                Math.min(oldA.getZ(), oldB.getZ()),
                Math.max(oldA.getX(), oldB.getX()),
                oldB.getY(),
                Math.max(oldA.getZ(), oldB.getZ())
              });
          h.assertTrue(
              old.equals(new FallenSanctumPiece(context, old).createTag(context)),
              "Saved layout and exact bounds stay unchanged "
                  + version
                  + "/"
                  + rotation
                  + "/"
                  + zone);
        }
      }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "sanctum_labyrinth", timeoutTicks = 2400)
  public static void extremeApproachOffsetsStillReachTheGroundFloorOnFoot(GameTestHelper h) {
    var level = h.getLevel();
    var context = StructurePieceSerializationContext.fromLevel(level);
    for (int offset : new int[] {-7, 7}) {
      var anchor = anchor();
      var piece =
          new FallenSanctumPiece(
              context, new FallenSanctumPiece(anchor, 0, 0, offset).createTag(context));
      var bounds = piece.getBoundingBox();
      for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++)
        for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) {
          var chunk = new ChunkPos(x, z);
          level.getChunk(x, z);
          var clip =
              new BoundingBox(
                  chunk.getMinBlockX(),
                  bounds.minY(),
                  chunk.getMinBlockZ(),
                  chunk.getMaxBlockX(),
                  bounds.maxY(),
                  chunk.getMaxBlockZ());
          piece.postProcess(
              level,
              level.structureManager(),
              level.getChunkSource().getGenerator(),
              RandomSource.create(),
              clip,
              chunk,
              anchor);
        }
      var start = at(anchor, 0, 0, offset, -56);
      var end = at(anchor, 0, 0, 0, -40);
      var route = walkable(level, anchor, 0, start);
      h.assertTrue(
          piece.floorLevel(-56) == offset && piece.floorLevel(-40) == 0 && route.containsKey(end),
          "The " + offset + " entrance ramp reaches ground level without a cliff or low ceiling");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "sanctum_labyrinth", timeoutTicks = 1200)
  public static void protectedConstructionInTheExpandedWingCancelsTheWholeClip(GameTestHelper h) {
    var level = h.getLevel();
    var controlAnchor = anchor();
    var control = piece(level, controlAnchor, 0, 2);
    var controlTarget = at(controlAnchor, 0, 41, 19, 0);
    var controlChunk = new ChunkPos(controlTarget);
    var controlClip =
        new BoundingBox(
            controlChunk.getMinBlockX(),
            controlAnchor.getY() - 16,
            controlChunk.getMinBlockZ(),
            controlChunk.getMaxBlockX(),
            controlAnchor.getY() + 48,
            controlChunk.getMaxBlockZ());
    level.getChunk(controlChunk.x, controlChunk.z);
    control.postProcess(
        level,
        level.structureManager(),
        level.getChunkSource().getGenerator(),
        RandomSource.create(),
        controlClip,
        controlChunk,
        controlAnchor);
    h.assertTrue(
        !level.getBlockState(controlTarget).isAir(),
        "The unprotected control actually builds the expanded wing floor");
    for (boolean container : new boolean[] {false, true}) {
      var anchor = anchor();
      var piece = piece(level, anchor, 0, 2);
      var protectedPos = at(anchor, 0, 40, 20, 0);
      var untouched = at(anchor, 0, 41, 19, 0);
      level.setBlockAndUpdate(
          protectedPos,
          container ? Blocks.CHEST.defaultBlockState() : Blocks.GOLD_BLOCK.defaultBlockState());
      if (container)
        ((ChestBlockEntity) level.getBlockEntity(protectedPos))
            .setItem(
                0,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
      var original = level.getBlockState(protectedPos);
      h.assertTrue(level.getBlockState(untouched).isAir(), "Empty expanded-wing safety fixture");
      var chunk = new ChunkPos(protectedPos);
      var clip =
          new BoundingBox(
              chunk.getMinBlockX(),
              anchor.getY() - 16,
              chunk.getMinBlockZ(),
              chunk.getMaxBlockX(),
              anchor.getY() + 48,
              chunk.getMaxBlockZ());
      piece.postProcess(
          level,
          level.structureManager(),
          level.getChunkSource().getGenerator(),
          RandomSource.create(),
          clip,
          chunk,
          anchor);
      h.assertTrue(
          level.getBlockState(protectedPos) == original && level.getBlockState(untouched).isAir(),
          "The complete clipped plan is validated before changing the expanded wing");
      if (container)
        h.assertTrue(
            ((ChestBlockEntity) level.getBlockEntity(protectedPos)).getItem(0).getCount() == 7,
            "Foreign chest and cargo survive intact");
    }
    h.succeed();
  }
}
