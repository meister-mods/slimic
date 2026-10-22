package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.SanctumRelayBlockEntity;
import io.github.meistermods.slimic.registry.*;
import io.github.meistermods.slimic.world.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SanctumArchitectureGameTests {
  private static int next;

  private static BlockPos anchor() {
    return new BlockPos(420008 + next++ * 192, 220, -420008);
  }

  private static BlockPos at(BlockPos anchor, int rotation, int x, int y, int z) {
    return FallenSanctumPiece.position(anchor, rotation, x, y, z);
  }

  private static FallenSanctumPiece piece(
      ServerLevel level, BlockPos anchor, int rotation, int zone) {
    var piece = new FallenSanctumPiece(anchor, rotation, zone);
    var context = StructurePieceSerializationContext.fromLevel(level);
    return new FallenSanctumPiece(context, piece.createTag(context));
  }

  static void generate(ServerLevel level, BlockPos anchor, int rotation, boolean reverse) {
    var first = at(anchor, rotation, -32, 0, -40);
    var last = at(anchor, rotation, 31, 0, 39);
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
        int zone = reverse ? 4 - i : i;
        var piece = piece(level, anchor, rotation, zone);
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

  private static Set<BlockPos> walkable(ServerLevel level, BlockPos anchor, int rotation) {
    // Conservative two-block-tall player walking and one-block steps, without flying or block
    // edits.
    var start = at(anchor, rotation, 0, 0, -39);
    var seen = new HashSet<BlockPos>();
    var queue = new ArrayDeque<BlockPos>();
    seen.add(start);
    queue.add(start);
    while (!queue.isEmpty()) {
      var pos = queue.remove();
      for (var direction : Direction.Plane.HORIZONTAL)
        for (int step : new int[] {0, 1, -1}) {
          var target = pos.relative(direction).offset(0, step, 0);
          var local =
              FallenSanctumPiece.position(
                  BlockPos.ZERO,
                  -rotation,
                  target.getX() - anchor.getX(),
                  target.getY() - anchor.getY(),
                  target.getZ() - anchor.getZ());
          if (Math.abs(local.getX()) > 30
              || local.getZ() < -39
              || local.getZ() > 38
              || local.getY() < -4
              || local.getY() > 11
              || seen.contains(target)) continue;
          var body = new AABB(target).deflate(.2, 0, .2).expandTowards(0, .8, 0);
          var below = level.getBlockState(target.below());
          boolean footing =
              below.isFaceSturdy(level, target.below(), Direction.UP)
                  || below.getBlock() instanceof StairBlock
                  || below.getBlock() instanceof SlabBlock;
          if (!level.noCollision(body) || !footing) continue;
          // A one-block rise must also leave space above the preceding position.
          if (step > 0 && !level.noCollision(body.move(0, 1, 0))) continue;
          seen.add(target);
          queue.add(target);
        }
    }
    return seen;
  }

  @GameTest(template = "empty", batch = "sanctum_architecture", timeoutTicks = 2400)
  public static void everyRotationHasReachableDevicesWallsGalleriesAndPreservedEncounters(
      GameTestHelper h) {
    var level = h.getLevel();
    for (int rotation = 0; rotation < 4; rotation++) {
      var anchor = anchor();
      generate(level, anchor, rotation, rotation % 2 != 0);
      var seen = walkable(level, anchor, rotation);
      for (int z = -40; z < 40; z++) {
        int y = z < 20 ? 0 : -Math.min(4, (z - 20) / 2);
        var floor = at(anchor, rotation, 0, y - 1, z);
        h.assertTrue(
            level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP),
            "Continuous main footing " + rotation + "/" + z);
        for (int x = -2; x <= 2; x++)
          for (int head = 0; head < 4; head++)
            h.assertTrue(
                level.getBlockState(at(anchor, rotation, x, y + head, z)).isAir(),
                "Five-wide main route " + rotation + "/" + z);
      }
      for (int zone = 1; zone <= 4; zone++) {
        int z = -32 + zone * 16, y = zone == 4 ? -4 : 0;
        var relay = at(anchor, rotation, 6, y, z);
        var chest = at(anchor, rotation, -8, y, z - 2);
        h.assertTrue(
            level.getBlockEntity(relay) instanceof SanctumRelayBlockEntity,
            "Original relay socket " + zone);
        h.assertTrue(
            level.getBlockEntity(chest) instanceof ChestBlockEntity, "Original chest " + zone);
        for (var target : List.of(relay, chest))
          h.assertTrue(
              Direction.Plane.HORIZONTAL.stream().anyMatch(d -> seen.contains(target.relative(d))),
              "Walk to device " + rotation + "/" + zone + "/" + target);
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
              "Original remnant home remains clear " + zone + "/" + x);
        }
      }
      for (var local :
          List.of(
              new BlockPos(-24, 10, -6),
              new BlockPos(24, 10, -6),
              new BlockPos(-16, 8, 6),
              new BlockPos(16, 8, 6),
              new BlockPos(-16, 8, 24),
              new BlockPos(16, 8, 24),
              new BlockPos(-19, 0, -16),
              new BlockPos(19, 0, -14),
              new BlockPos(-25, 0, -21),
              new BlockPos(25, 0, -21),
              new BlockPos(-25, -4, 31),
              new BlockPos(25, -4, 31),
              new BlockPos(-16, 0, -14)))
        h.assertTrue(
            seen.contains(at(anchor, rotation, local.getX(), local.getY(), local.getZ())),
            "Walk to wall, upper gallery or courtyard remains " + rotation + "/" + local);
      h.assertTrue(
          level.getBlockState(at(anchor, rotation, -29, -3, 35)).is(Blocks.LADDER),
          "Rear tower ladder reaches its lowered ground floor");
      var roof = at(anchor, rotation, 0, 24, 0);
      h.assertTrue(!level.getBlockState(roof).isAir(), "Keep ridge rises above the curtain walls");
      var ladder = level.getBlockState(at(anchor, rotation, -29, 5, -21));
      h.assertTrue(
          ladder.is(Blocks.LADDER)
              && ladder.getValue(LadderBlock.FACING)
                  == Rotation.values()[rotation].rotate(Direction.EAST),
          "Rotated supported tower ladder");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "sanctum_architecture", timeoutTicks = 1600)
  public static void clippingOrderAndSavedPieceReloadProduceTheSameBlocks(GameTestHelper h) {
    var level = h.getLevel();
    var a = anchor();
    var b = anchor();
    generate(level, a, 0, false);
    generate(level, b, 0, true);
    for (int x = -32; x <= 31; x++)
      for (int z = -40; z <= 39; z++)
        for (int y = -11; y <= 28; y++) {
          var first = level.getBlockState(at(a, 0, x, y, z));
          var second = level.getBlockState(at(b, 0, x, y, z));
          h.assertTrue(
              first == second,
              "Chunk and piece order cannot alter geometry at "
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
    var context = StructurePieceSerializationContext.fromLevel(level);
    for (int zone = 0; zone < 5; zone++) {
      var saved = piece(level, a, 0, zone).createTag(context);
      h.assertTrue(
          saved.getInt("ArchitectureVersion") == 2
              && saved.equals(new FallenSanctumPiece(context, saved).createTag(context)),
          "Architectural version and bounds round trip");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "sanctum_architecture", timeoutTicks = 1200)
  public static void companionNavigationCanFollowTheExpeditionRoute(GameTestHelper h) {
    var level = h.getLevel();
    var anchor = anchor();
    generate(level, anchor, 1, true);
    var slime = ModEntities.MINING_SLIME.get().create(level);
    slime
        .getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)
        .setBaseValue(32);
    for (int z = -38; z < 32; z += 7) {
      int end = Math.min(32, z + 7);
      int y = z < 20 ? 0 : -Math.min(4, (z - 20) / 2);
      int nextY = end < 20 ? 0 : -Math.min(4, (end - 20) / 2);
      var from = at(anchor, 1, 0, y, z);
      var target = at(anchor, 1, 0, nextY, end);
      slime.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(from));
      slime.setOnGround(true);
      var path = slime.getNavigation().createPath(target, 0);
      h.assertTrue(
          path != null && path.canReach(),
          "Native companion navigation reaches the next section at " + z);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "sanctum_architecture", timeoutTicks = 400)
  public static void foreignConstructionAndContainersAtRoofHeightCancelWrites(GameTestHelper h) {
    var level = h.getLevel();
    var anchor = anchor();
    var generator = level.getChunkSource().getGenerator();
    for (boolean container : new boolean[] {false, true}) {
      var piece = piece(level, anchor, 0, 2);
      var protectedPos = at(anchor, 0, 0, 22, 0);
      var clip = piece.getBoundingBox();
      level.setBlockAndUpdate(
          protectedPos,
          container ? Blocks.CHEST.defaultBlockState() : Blocks.GOLD_BLOCK.defaultBlockState());
      if (container)
        ((ChestBlockEntity) level.getBlockEntity(protectedPos))
            .setItem(
                0,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
      var original = level.getBlockState(protectedPos);
      var untouched = at(anchor, 0, 1, 0, 0);
      h.assertTrue(level.getBlockState(untouched).isAir(), "Empty safety fixture");
      piece.postProcess(
          level,
          level.structureManager(),
          generator,
          RandomSource.create(),
          clip,
          new ChunkPos(anchor),
          anchor);
      h.assertTrue(
          level.getBlockState(protectedPos) == original && level.getBlockState(untouched).isAir(),
          "No writes occur before the whole clipped plan passes validation");
      if (container)
        h.assertTrue(
            ((ChestBlockEntity) level.getBlockEntity(protectedPos)).getItem(0).getCount() == 7,
            "Existing cargo remains intact");
      level.setBlockAndUpdate(protectedPos, Blocks.AIR.defaultBlockState());
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "sanctum_architecture", timeoutTicks = 400)
  public static void oldSavedPiecesKeepTheirOriginalLayoutAndBounds(GameTestHelper h) {
    var level = h.getLevel();
    var anchor = anchor();
    var context = StructurePieceSerializationContext.fromLevel(level);
    var tag = new FallenSanctumPiece(anchor, 0, 2).createTag(context);
    tag.remove("ArchitectureVersion");
    var bounds = tag.getIntArray("BB");
    bounds[4] = anchor.getY() + 15;
    tag.putIntArray("BB", bounds);
    var old = new FallenSanctumPiece(context, tag);
    old.postProcess(
        level,
        level.structureManager(),
        level.getChunkSource().getGenerator(),
        RandomSource.create(),
        old.getBoundingBox(),
        new ChunkPos(anchor),
        anchor);
    h.assertTrue(
        old.getBoundingBox().maxY() == anchor.getY() + 15
            && old.createTag(context).getInt("ArchitectureVersion") == 0,
        "Old pieces retain their saved bounds and legacy generator");
    h.assertTrue(
        level.getBlockState(at(anchor, 0, 0, 24, 0)).isAir()
            && level.getBlockEntity(at(anchor, 0, 6, 0, 0)) instanceof SanctumRelayBlockEntity,
        "Old unfinished chamber has its original relay and no new roof");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "sanctum_architecture", timeoutTicks = 600)
  public static void savedCastlePiecesKeepTheirUnfurnishedLayout(GameTestHelper h) {
    var level = h.getLevel();
    var context = StructurePieceSerializationContext.fromLevel(level);
    for (int version : new int[] {1, 2}) {
      var anchor = anchor();
      var tag = new FallenSanctumPiece(anchor, 0, 1).createTag(context);
      tag.putInt("ArchitectureVersion", version);
      var piece = new FallenSanctumPiece(context, tag);
      piece.postProcess(
          level,
          level.structureManager(),
          level.getChunkSource().getGenerator(),
          RandomSource.create(),
          piece.getBoundingBox(),
          new ChunkPos(anchor),
          anchor);
      h.assertTrue(
          piece.createTag(context).getInt("ArchitectureVersion") == version,
          "Saved castle version survives reload");
      h.assertTrue(
          level
              .getBlockState(at(anchor, 0, -19, 0, -18))
              .is(version == 1 ? Blocks.AIR : ModWorldBlocks.block("low_stone_table")),
          "Only newly planned castles receive furnishings");
      h.assertTrue(
          level.noCollision(new AABB(at(anchor, 0, -16, 0, -14)))
              && level.getBlockEntity(at(anchor, 0, 6, 0, -16)) instanceof SanctumRelayBlockEntity,
          "Saved courtyard remains and relay keep their access");
    }
    h.succeed();
  }
}
