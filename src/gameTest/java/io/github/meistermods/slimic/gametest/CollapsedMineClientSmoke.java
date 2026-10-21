package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.registry.ModWorldBlocks;
import io.github.meistermods.slimic.world.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Native surface and underground views of both abandoned excavation shapes. */
public final class CollapsedMineClientSmoke {
  private static int phase, ticks, camera;
  private static CompletableFuture<Void> task;
  private static final List<ChunkPos> plots = new ArrayList<>();
  private static int bottom, rotation;
  private static final int[] tops = {99, 99};

  private static long seed(boolean quarry) {
    for (long seed = 0; ; seed++) {
      var r = RandomSource.create(seed);
      if (r.nextBoolean() == quarry && (quarry || r.nextInt(4) == 0)) return seed;
    }
  }

  private static void prepare(ServerLevel level, ChunkPos chunk, boolean quarry) {
    for (int x = -1; x <= 1; x++)
      for (int z = -1; z <= 1; z++) {
        level.setChunkForced(chunk.x + x, chunk.z + z, true);
        level.getChunk(chunk.x + x, chunk.z + z);
      }
    for (int x = -16; x < 32; x++)
      for (int z = -16; z < 32; z++)
        for (int y = 73; y < level.getMaxBuildHeight(); y++)
          level.setBlock(
              new BlockPos(chunk.getMinBlockX() + x, y, chunk.getMinBlockZ() + z),
              (y <= 99 ? ModWorldBlocks.block("slimic_stone") : Blocks.AIR).defaultBlockState(),
              2);
    Heightmap.primeHeightmaps(
        level.getChunk(chunk.x, chunk.z), Set.of(Heightmap.Types.WORLD_SURFACE_WG));
    long seed = seed(quarry);
    var r = RandomSource.create(seed);
    r.nextBoolean();
    if (quarry) bottom = 99 - 12 - r.nextInt(7);
    if (!new CollapsedMineFeature()
        .place(
            NoneFeatureConfiguration.INSTANCE,
            level,
            level.getChunkSource().getGenerator(),
            RandomSource.create(seed),
            new BlockPos(chunk.getMinBlockX() + 8, 100, chunk.getMinBlockZ() + 8)))
      throw new IllegalStateException("Visual mine fixture did not place");
  }

  private static void move(Minecraft c) {
    task =
        CompletableFuture.runAsync(
            () -> {
              var owner = c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
              var level = c.getSingleplayerServer().getLevel(SlimicWorld.DIMENSION);
              var chunk = plots.get(camera < 2 ? 0 : 1);
              double x = camera == 0 || camera == 1 ? 5.5 : camera == 2 ? 20 : 5.5;
              double y =
                  camera == 0
                      ? tops[0] + 2
                      : camera == 1 ? tops[0] - 5 : camera == 2 ? tops[1] + 17 : bottom + 1;
              double z = camera == 0 ? -3 : camera == 1 ? 9 : camera == 2 ? -6 : 4;
              float yaw = camera < 2 ? 0 : camera == 2 ? 40 : -25;
              float pitch = camera == 0 ? 28 : camera == 1 ? 5 : camera == 2 ? 55 : 8;
              if (camera < 2) {
                double px = x, pz = z;
                switch (rotation) {
                  case 1 -> {
                    x = 16 - pz;
                    z = px;
                  }
                  case 2 -> {
                    x = 16 - px;
                    z = 16 - pz;
                  }
                  case 3 -> {
                    x = pz;
                    z = 16 - px;
                  }
                  default -> {}
                }
                yaw += 90 * rotation;
              }
              owner.teleportTo(
                  level, chunk.getMinBlockX() + x, y, chunk.getMinBlockZ() + z, yaw, pitch);
            },
            c.getSingleplayerServer());
    ticks = 0;
  }

  private static void prepareNatural(Minecraft c, ServerLevel level) {
    try {
      var data =
          com.google.gson.JsonParser.parseString(
                  Files.readString(c.gameDirectory.toPath().resolve("natural-sites.json")))
              .getAsJsonObject();
      var sites = data.getAsJsonArray("sites");
      for (String kind : new String[] {"tunnel", "quarry"}) {
        com.google.gson.JsonObject selected = null;
        for (var value : sites)
          if (value.getAsJsonObject().get("kind").getAsString().equals(kind)) {
            selected = value.getAsJsonObject();
            break;
          }
        if (selected == null) throw new IllegalStateException("Missing natural mine " + kind);
        var coords = selected.getAsJsonArray("chunk");
        var chunk = new ChunkPos(coords.get(0).getAsInt(), coords.get(1).getAsInt());
        plots.add(chunk);
        for (int dx = -1; dx <= 1; dx++)
          for (int dz = -1; dz <= 1; dz++) {
            level.setChunkForced(chunk.x + dx, chunk.z + dz, true);
            level.getChunk(chunk.x + dx, chunk.z + dz);
          }
        var remains = selected.getAsJsonArray("remains");
        if (kind.equals("tunnel")) {
          int floor = Integer.MAX_VALUE;
          for (var value : remains)
            floor = Math.min(floor, value.getAsJsonArray().get(1).getAsInt());
          tops[0] = floor + 5;
          var positions = new java.util.HashSet<String>();
          for (var value : remains) {
            var xyz = value.getAsJsonArray();
            positions.add((xyz.get(0).getAsInt() & 15) + ":" + (xyz.get(2).getAsInt() & 15));
          }
          rotation =
              positions.contains("4:11")
                  ? 0
                  : positions.contains("4:4") ? 1 : positions.contains("11:4") ? 2 : 3;
        } else {
          int high = 0;
          for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++)
              high =
                  Math.max(
                      high,
                      level.getHeight(
                          Heightmap.Types.WORLD_SURFACE,
                          chunk.getMinBlockX() + dx,
                          chunk.getMinBlockZ() + dz));
          tops[1] = high - 5;
          bottom =
              Math.min(
                      remains.get(0).getAsJsonArray().get(1).getAsInt(),
                      remains.get(1).getAsJsonArray().get(1).getAsInt())
                  - 1;
        }
      }
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static boolean tick(Minecraft c) {
    if (phase == 0) {
      c.options.hideGui = true;
      c.options.renderDistance().set(4);
      c.options.fov().set(70);
      c.setScreen(null);
      task =
          CompletableFuture.runAsync(
              () -> {
                var owner = c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
                owner.setGameMode(GameType.SPECTATOR);
                var level = c.getSingleplayerServer().getLevel(SlimicWorld.DIMENSION);
                level.setDayTime(6000);
                level.setWeatherParameters(6000, 0, false, false);
                if (Boolean.getBoolean("slimic.mineNaturalView")) prepareNatural(c, level);
                else {
                  for (int i = 0; i < 2; i++) {
                    var chunk = new ChunkPos(10000 + i * 4, 10000);
                    plots.add(chunk);
                    prepare(level, chunk, i == 1);
                  }
                }
              },
              c.getSingleplayerServer());
      phase = 1;
      return false;
    }
    if (task != null) {
      if (!task.isDone()) return false;
      task.join();
      task = null;
    }
    if (phase == 1) {
      move(c);
      phase = 2;
      return false;
    }
    if (phase == 2) {
      if (!c.level.dimension().equals(SlimicWorld.DIMENSION) || ++ticks < 50) return false;
      try {
        var folder = c.gameDirectory.toPath().resolve("collapsed-mine-audit");
        Files.createDirectories(folder);
        String[] names = {"tunnel-entrance", "tunnel-interior", "quarry-overview", "quarry-floor"};
        try (var image = Screenshot.takeScreenshot(c.getMainRenderTarget())) {
          image.writeToFile(folder.resolve(names[camera] + ".png"));
        }
      } catch (java.io.IOException error) {
        throw new IllegalStateException(error);
      }
      if (++camera < 4) {
        move(c);
        return false;
      }
      task =
          CompletableFuture.runAsync(
              () -> {
                var level = c.getSingleplayerServer().getLevel(SlimicWorld.DIMENSION);
                for (var chunk : plots)
                  for (int x = -1; x <= 1; x++)
                    for (int z = -1; z <= 1; z++)
                      level.setChunkForced(chunk.x + x, chunk.z + z, false);
              },
              c.getSingleplayerServer());
      phase = 3;
      return false;
    }
    System.out.println(
        "SLIMIC COLLAPSED MINE CLIENT SMOKE PASSED: surface entrances, 3x3 tunnel, quarry stairs"
            + " and empty remains");
    return true;
  }
}
