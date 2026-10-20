package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.AncientSlime;
import io.github.meistermods.slimic.registry.ModWorldBlocks;
import io.github.meistermods.slimic.world.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForgottenRuinGameTests {
  private static int index;

  private record Plot(ServerLevel level, BlockPos center) implements AutoCloseable {
    @Override
    public void close() {
      level
          .getEntitiesOfClass(AncientSlime.class, new AABB(center).inflate(24))
          .forEach(AncientSlime::discard);
      for (int dx = -1; dx <= 1; dx++)
        for (int dz = -1; dz <= 1; dz++)
          level.setChunkForced((center.getX() >> 4) + dx, (center.getZ() >> 4) + dz, false);
    }
  }

  private static Plot plot(GameTestHelper h) {
    var level = h.getLevel().getServer().getLevel(SlimicWorld.DIMENSION);
    int x = 1_600_000 + index++ * 64, z = -1_600_000;
    var center = new BlockPos(x + 8, 300, z + 8);
    for (int dx = -1; dx <= 1; dx++)
      for (int dz = -1; dz <= 1; dz++) {
        level.setChunkForced((x >> 4) + dx, (z >> 4) + dz, true);
        level.getChunk((x >> 4) + dx, (z >> 4) + dz);
      }
    long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
    level
        .getServer()
        .managedBlock(
            () -> {
              level.getChunkSource().pollTask();
              return level.isPositionEntityTicking(center) || System.nanoTime() >= end;
            });
    h.assertTrue(
        level.isPositionEntityTicking(center), "Native ruin plot supports entity admission");
    for (int dx = 0; dx < 16; dx++)
      for (int dz = 0; dz < 16; dz++) {
        level.setBlock(new BlockPos(x + dx, 299, z + dz), Blocks.STONE.defaultBlockState(), 2);
        for (int y = 300; y < level.getMaxBuildHeight(); y++)
          level.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.AIR.defaultBlockState(), 2);
      }
    Heightmap.primeHeightmaps(
        level.getChunk(x >> 4, z >> 4), Set.of(Heightmap.Types.WORLD_SURFACE_WG));
    return new Plot(level, center);
  }

  private static RandomSource random(int choice) {
    for (long seed = 0; ; seed++) {
      var random = RandomSource.create(seed);
      if (random.nextInt(12) == choice) return RandomSource.create(seed);
    }
  }

  private static boolean place(Plot plot, int choice) {
    return new SlimicRuinFeature()
        .place(
            NoneFeatureConfiguration.INSTANCE,
            plot.level,
            plot.level.getChunkSource().getGenerator(),
            random(choice),
            plot.center);
  }

  @GameTest(template = "empty", batch = "forgotten_ruins", timeoutTicks = 400)
  public static void allFiveRuinVariantsPlaceOnlyBoundedExpectedNativeRemnants(GameTestHelper h) {
    int[] choices = {0, 4, 8, 9, 11}, counts = {2, 2, 0, 3, 4};
    for (int i = 0; i < choices.length; i++)
      try (var plot = plot(h)) {
        h.assertTrue(place(plot, choices[i]), "Native ruin variant places: " + choices[i]);
        var mobs =
            plot.level.getEntitiesOfClass(AncientSlime.class, new AABB(plot.center).inflate(20));
        h.assertTrue(
            mobs.size() == counts[i],
            "Expected number of actual admitted NPCs for " + choices[i] + ": " + mobs.size());
        for (var mob : mobs)
          h.assertTrue(
              mob.getRuinHome().equals(plot.center)
                  && mob.getRuinId() != null
                  && mob.chunkPosition()
                      .equals(new net.minecraft.world.level.ChunkPos(plot.center)),
              "Every remnant is bound inside the feature chunk");
        if (choices[i] != 8)
          h.assertTrue(
              plot.level
                  .getBlockState(plot.center.offset(0, 0, -2))
                  .is(ModWorldBlocks.block("command_pedestal")),
              "Occupied ruins contain an intact restoration pedestal");
        if (choices[i] == 9)
          h.assertTrue(
              plot.level.getBlockState(plot.center.offset(0, 0, 5)).is(Blocks.BOOKSHELF),
              "Archive has actual bookshelves");
        if (choices[i] == 11)
          h.assertTrue(
              plot.level
                  .getBlockState(plot.center.offset(0, 0, 5))
                  .is(ModWorldBlocks.block("royal_crest")),
              "Royal ward has its royal insignia");
      }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_ruins", timeoutTicks = 400)
  public static void spawnVetoAndExistingContainersAreRespectedByRuinGeneration(GameTestHelper h) {
    try (var plot = plot(h)) {
      Consumer<MobSpawnEvent.FinalizeSpawn> veto =
          event -> {
            if (event.getEntity() instanceof AncientSlime) event.setSpawnCancelled(true);
          };
      MinecraftForge.EVENT_BUS.addListener(veto);
      try {
        h.assertTrue(place(plot, 0), "A protected entity spawn does not discard the structure");
      } finally {
        MinecraftForge.EVENT_BUS.unregister(veto);
      }
      h.assertTrue(
          plot.level
              .getEntitiesOfClass(AncientSlime.class, new AABB(plot.center).inflate(20))
              .isEmpty(),
          "Native finalize-spawn veto prevents each remnant");
    }
    try (var plot = plot(h)) {
      plot.level.setBlockAndUpdate(plot.center, Blocks.CHEST.defaultBlockState());
      var original = plot.level.getBlockEntity(plot.center);
      h.assertTrue(
          !place(plot, 0) && plot.level.getBlockEntity(plot.center) == original,
          "An existing container prevents construction before any replacement");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_ruins")
  public static void ancientRuinFeatureRejectsOtherDimensionsBeforeChangingTerrain(
      GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(3, 1, 3));
    var original = h.getLevel().getBlockState(pos);
    boolean placed =
        new SlimicRuinFeature()
            .place(
                NoneFeatureConfiguration.INSTANCE,
                h.getLevel(),
                h.getLevel().getChunkSource().getGenerator(),
                RandomSource.create(0),
                pos);
    h.assertTrue(
        !placed && h.getLevel().getBlockState(pos).equals(original),
        "Occupied ancient ruins are restricted to The Slimic");
    h.succeed();
  }
}
