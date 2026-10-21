package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.BrokenSlimeBlock;
import io.github.meistermods.slimic.block.BrokenSlimeBlockEntity;
import io.github.meistermods.slimic.entity.QuarryPlan;
import io.github.meistermods.slimic.entity.QuarryStaircaseData;
import io.github.meistermods.slimic.registry.ModBlocks;
import io.github.meistermods.slimic.registry.ModWorldBlocks;
import io.github.meistermods.slimic.world.CollapsedMineFeature;
import io.github.meistermods.slimic.world.SlimicWorld;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CollapsedMineGameTests {
  private static int index;

  private record Plot(ServerLevel level, ChunkPos chunk) implements AutoCloseable {
    BlockPos at(int x, int y, int z) {
      return new BlockPos(chunk.getMinBlockX() + x, y, chunk.getMinBlockZ() + z);
    }

    void heightmap() {
      Heightmap.primeHeightmaps(
          level.getChunk(chunk.x, chunk.z), Set.of(Heightmap.Types.WORLD_SURFACE_WG));
    }

    @Override
    public void close() {
      for (int x = -1; x <= 1; x++)
        for (int z = -1; z <= 1; z++) level.setChunkForced(chunk.x + x, chunk.z + z, false);
    }
  }

  private static Plot plot(GameTestHelper h) {
    var level = h.getLevel().getServer().getLevel(SlimicWorld.DIMENSION);
    var plot = new Plot(level, new ChunkPos(125000 + 4 * index++, -125000));
    for (int x = -1; x <= 1; x++)
      for (int z = -1; z <= 1; z++) {
        level.setChunkForced(plot.chunk.x + x, plot.chunk.z + z, true);
        level.getChunk(plot.chunk.x + x, plot.chunk.z + z);
      }
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++)
        for (int y = 273; y < level.getMaxBuildHeight(); y++)
          level.setBlock(
              plot.at(x, y, z),
              (y <= 299 ? ModWorldBlocks.block("slimic_stone") : Blocks.AIR).defaultBlockState(),
              2);
    plot.heightmap();
    return plot;
  }

  private static long seed(boolean quarry, int rotation) {
    for (long seed = 0; ; seed++) {
      var r = RandomSource.create(seed);
      if (r.nextBoolean() == quarry && (quarry || r.nextInt(4) == rotation)) return seed;
    }
  }

  private static boolean place(Plot p, long seed) {
    return new CollapsedMineFeature()
        .place(
            NoneFeatureConfiguration.INSTANCE,
            p.level,
            p.level.getChunkSource().getGenerator(),
            RandomSource.create(seed),
            p.at(8, 300, 8));
  }

  private static List<BlockPos> remains(Plot p) {
    var found = new ArrayList<BlockPos>();
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++)
        for (int y = 273; y <= 303; y++)
          if (p.level.getBlockState(p.at(x, y, z)).is(ModBlocks.BROKEN_SLIME.get()))
            found.add(p.at(x, y, z));
    return found;
  }

  @GameTest(template = "empty", batch = "collapsed_mine", timeoutTicks = 400)
  public static void allTunnelOrientationsHaveSurfaceEntriesAndStoppedThreeByThreeFaces(
      GameTestHelper h) {
    for (int rotation = 0; rotation < 4; rotation++)
      try (var p = plot(h)) {
        h.assertTrue(place(p, seed(false, rotation)), "Tunnel orientation places: " + rotation);
        h.assertTrue(remains(p).size() == 3, "Fallen workers remain below and beside the entrance");
        int webs = 0, surfaceTimber = 0, face = 0;
        for (int x = 0; x < 16; x++)
          for (int z = 0; z < 16; z++) {
            if (p.level
                .getBlockState(p.at(x, 303, z))
                .is(ModWorldBlocks.block("slime_infused_planks"))) surfaceTimber++;
            for (int y = 293; y <= 302; y++) {
              if (p.level.getBlockState(p.at(x, y, z)).is(Blocks.COBWEB)) webs++;
            }
          }
        for (int x = 4; x <= 6; x++)
          for (int y = 294; y <= 296; y++) {
            int rx = x, rz = 14;
            switch (rotation) {
              case 1 -> {
                rx = 1;
                rz = x;
              }
              case 2 -> {
                rx = 15 - x;
                rz = 1;
              }
              case 3 -> {
                rx = 14;
                rz = 15 - x;
              }
              default -> {}
            }
            if (p.level.getBlockState(p.at(rx, y, rz)).is(ModWorldBlocks.block("slimic_stone")))
              face++;
          }
        h.assertTrue(
            surfaceTimber >= 5 && webs >= 2 && face == 9,
            "Visible surface frame, cobwebs and a solid 3x3 unfinished face");
      }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "collapsed_mine", timeoutTicks = 400)
  public static void tunnelEntrancesMeetTheSurfaceInsteadOfTheHighestInteriorColumn(
      GameTestHelper h) {
    for (int rotation = 0; rotation < 4; rotation++)
      try (var p = plot(h)) {
        p.level.setBlock(
            p.at(8, 300, 8), ModWorldBlocks.block("slimic_stone").defaultBlockState(), 2);
        p.level.setBlock(
            p.at(8, 301, 8), ModWorldBlocks.block("slimic_stone").defaultBlockState(), 2);
        p.heightmap();
        h.assertTrue(place(p, seed(false, rotation)), "Gentle surface variation permits the mine");
        int x = rotation == 0 ? 5 : rotation == 1 ? 15 : rotation == 2 ? 10 : 0;
        int z = rotation == 0 ? 0 : rotation == 1 ? 5 : rotation == 2 ? 15 : 10;
        h.assertTrue(
            p.level.getBlockState(p.at(x, 299, z)).is(ModWorldBlocks.block("slimic_cobblestone"))
                && p.level.getBlockState(p.at(x, 300, z)).isAir(),
            "Every rotated entrance joins its existing surface without raising a two-block ledge");
      }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "collapsed_mine", timeoutTicks = 400)
  public static void quarryUsesTheRealTwoWideRampAndPersistsItsProtection(GameTestHelper h) {
    try (var p = plot(h)) {
      long seed = seed(true, 0);
      var r = RandomSource.create(seed);
      r.nextBoolean();
      int bottom = 299 - 12 - r.nextInt(7);
      h.assertTrue(place(p, seed), "Chunk quarry places");
      var plan = new QuarryPlan(p.chunk, 299, bottom + 2);
      var saved = QuarryStaircaseData.get(p.level).save(new CompoundTag());
      var loaded =
          QuarryStaircaseData.load(saved, p.level.getMinBuildHeight(), p.level.getMaxBuildHeight());
      for (int y = bottom + 1; y <= 299; y++)
        for (var pos : plan.floorsAt(y))
          h.assertTrue(
              p.level.getBlockState(pos).isCollisionShapeFullBlock(p.level, pos)
                  && loaded.protects(pos),
              "Every generated stair floor is solid and survives saved-profile reload");
      h.assertTrue(
          remains(p).size() == 2
              && p.level.getBlockState(p.at(7, 300, 7)).isAir()
              && p.level
                  .getBlockState(p.at(12, bottom + 1, 8))
                  .is(ModWorldBlocks.block("slimic_stone")),
          "Open surface shaft, fallen workers and an unfinished lower shelf");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "collapsed_mine", timeoutTicks = 400)
  public static void generatedStairsStayStableAcrossFallingTerrainUpdates(GameTestHelper h) {
    var p = plot(h);
    long seed = seed(true, 0);
    var r = RandomSource.create(seed);
    r.nextBoolean();
    int bottom = 299 - 12 - r.nextInt(7);
    var plan = new QuarryPlan(p.chunk, 299, bottom + 2);
    var floors = new ArrayList<BlockPos>();
    for (int y = bottom + 1; y <= 299; y++) floors.addAll(plan.floorsAt(y));
    for (var pos : floors) p.level.setBlock(pos, Blocks.GRAVEL.defaultBlockState(), 2);
    h.assertTrue(place(p, seed), "Mine can generate in natural gravel strata");
    h.runAfterDelay(
        20,
        () -> {
          for (var pos : floors)
            h.assertTrue(
                p.level.getBlockState(pos).is(ModWorldBlocks.block("slimic_cobblestone")),
                "Every two-wide tread remains stable after scheduled falling-block ticks");
          p.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "collapsed_mine", timeoutTicks = 400)
  public static void ancientBrokenWorkersNeverReviveAndTheirDropsStayEmpty(GameTestHelper h) {
    for (boolean quarry : new boolean[] {false, true})
      try (var p = plot(h)) {
        h.assertTrue(place(p, seed(quarry, 0)), "Mine places");
        var player = h.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SLIME_BALL, 3));
        for (var pos : remains(p)) {
          var broken = (BrokenSlimeBlockEntity) p.level.getBlockEntity(pos);
          h.assertTrue(
              broken != null && broken.getSlime().isEmpty(),
              "No companion identity is stored in a ruin");
          var data = broken.saveWithFullMetadata();
          broken.load(data);
          h.assertTrue(
              broken.getSlime().isEmpty()
                  && !BrokenSlimeBlock.revive(p.level, pos, player)
                  && player.getMainHandItem().getCount() == 3,
              "Reload and revival attempts cannot create a companion or spend slimeballs");
          var drops =
              net.minecraft.world.level.block.Block.getDrops(
                  p.level.getBlockState(pos), p.level, pos, broken);
          h.assertTrue(
              drops.size() == 1
                  && drops.get(0).is(ModBlocks.BROKEN_SLIME.get().asItem())
                  && (net.minecraft.world.item.BlockItem.getBlockEntityData(drops.get(0)) == null
                      || !net.minecraft.world.item.BlockItem.getBlockEntityData(drops.get(0))
                          .contains("Slime")),
              "Broken remnants drop no recoverable companion snapshot");
        }
      }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "collapsed_mine", timeoutTicks = 400)
  public static void fluidContainersAndUnbreakableBlocksRejectBeforeAnyCarving(GameTestHelper h) {
    for (var obstacle :
        new net.minecraft.world.level.block.Block[] {
          Blocks.CHEST, Blocks.WATER, Blocks.LAVA, Blocks.BEDROCK, Blocks.DIAMOND_BLOCK
        })
      try (var p = plot(h)) {
        var pos = p.at(8, 290, 8);
        p.level.setBlock(pos, obstacle.defaultBlockState(), 2);
        var before = p.level.getBlockState(p.at(8, 299, 8));
        var entity = p.level.getBlockEntity(pos);
        h.assertTrue(
            !place(p, seed(true, 0))
                && p.level.getBlockState(pos).is(obstacle)
                && p.level.getBlockState(p.at(8, 299, 8)).equals(before)
                && p.level.getBlockEntity(pos) == entity,
            "Preflight preserves " + obstacle);
      }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "collapsed_mine", timeoutTicks = 400)
  public static void repeatPlacementPreservesExistingRemains(GameTestHelper h) {
    try (var p = plot(h)) {
      h.assertTrue(place(p, seed(false, 0)), "Initial tunnel places");
      var pos = remains(p).get(0);
      var block = p.level.getBlockEntity(pos);
      p.heightmap();
      h.assertTrue(
          !place(p, seed(false, 0)) && p.level.getBlockEntity(pos) == block,
          "A second feature cannot overwrite the previous site");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "collapsed_mine", timeoutTicks = 400)
  public static void surfaceRuinsNeverPaveOverTheMineEntrance(GameTestHelper h) {
    for (int rotation = 0; rotation < 4; rotation++)
      try (var p = plot(h)) {
        for (int y = 300; y <= 301; y++)
          p.level.setBlock(
              rotated(p, 5, y, 0, rotation),
              ModWorldBlocks.block("slimic_stone").defaultBlockState(),
              2);
        for (int x = 4; x <= 6; x++)
          p.level.setBlock(
              rotated(p, x, 300, 7, rotation),
              ModWorldBlocks.block("slimic_stone").defaultBlockState(),
              2);
        p.heightmap();
        h.assertTrue(
            place(p, seed(false, rotation)), "The tunnel enters at the higher surface edge");
        p.heightmap();
        for (int choice : new int[] {0, 4, 8, 9, 11}) {
          long seed = 0;
          while (RandomSource.create(seed).nextInt(12) != choice) seed++;
          boolean ruin =
              new io.github.meistermods.slimic.world.SlimicRuinFeature()
                  .place(
                      NoneFeatureConfiguration.INSTANCE,
                      p.level,
                      p.level.getChunkSource().getGenerator(),
                      RandomSource.create(seed),
                      p.at(8, 300, 8));
          h.assertTrue(!ruin, "Every surface ruin preserves all rotated mine entrances");
        }
      }
    h.succeed();
  }

  private static BlockPos rotated(Plot p, int x, int y, int z, int rotation) {
    return switch (rotation) {
      case 1 -> p.at(15 - z, y, x);
      case 2 -> p.at(15 - x, y, 15 - z);
      case 3 -> p.at(z, y, 15 - x);
      default -> p.at(x, y, z);
    };
  }

  @GameTest(template = "empty", batch = "collapsed_mine")
  public static void collapsedMinesNeverModifyOtherDimensions(GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(2, 1, 2));
    var before = h.getLevel().getBlockState(pos);
    boolean placed =
        new CollapsedMineFeature()
            .place(
                NoneFeatureConfiguration.INSTANCE,
                h.getLevel(),
                h.getLevel().getChunkSource().getGenerator(),
                RandomSource.create(0),
                pos);
    h.assertTrue(
        !placed && h.getLevel().getBlockState(pos).equals(before),
        "Only The Slimic generates these mines");
    h.succeed();
  }
}
