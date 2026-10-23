package io.github.meistermods.slimic.world;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/** Uses the same bounded placement prediction and disk-only metadata checks as kingdom maps. */
public final class AssemblyPlacement {
  private static final java.util.Map<ServerLevel, java.util.Map<Long, java.util.Set<Long>>> LOADED =
      new java.util.concurrent.ConcurrentHashMap<>();
  public static final ResourceKey<StructureSet> SET =
      ResourceKey.create(Registries.STRUCTURE_SET, SlimicWorld.id("abandoned_assembly_chamber"));

  public record Candidate(BlockPos anchor, int entranceY) {}

  private AssemblyPlacement() {}

  public static void forget(ServerLevel level) {
    LOADED.remove(level);
  }

  public static void chunk(net.minecraft.world.level.chunk.LevelChunk chunk, boolean loaded) {
    if (!(chunk.getLevel() instanceof ServerLevel level)
        || !level.dimension().equals(SlimicWorld.DIMENSION)) return;
    var known =
        LOADED.computeIfAbsent(level, unused -> new java.util.concurrent.ConcurrentHashMap<>());
    if (!loaded) {
      known.remove(chunk.getPos().toLong());
      return;
    }
    var structure =
        level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(SET.location());
    Set<Long> references = new HashSet<>();
    var start = structure == null ? null : chunk.getStartForStructure(structure);
    if (start != null && start.isValid()) references.add(chunk.getPos().toLong());
    if (structure != null) references.addAll(chunk.getReferencesForStructure(structure));
    known.put(chunk.getPos().toLong(), Set.copyOf(references));
  }

  private static RandomSpreadStructurePlacement placement(ServerLevel level) {
    var set = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(SET.location());
    return set != null && set.placement() instanceof RandomSpreadStructurePlacement spread
        ? spread
        : null;
  }

  public static boolean reserved(ServerLevel level, ChunkPos chunk) {
    if (!level.dimension().equals(SlimicWorld.DIMENSION)) return false;
    var placement = placement(level);
    if (placement == null) return false;
    int spacing = placement.spacing(),
        rx = Math.floorDiv(chunk.x, spacing),
        rz = Math.floorDiv(chunk.z, spacing);
    for (int x = rx - 1; x <= rx + 1; x++)
      for (int z = rz - 1; z <= rz + 1; z++) {
        var start = placement.getPotentialStructureChunk(level.getSeed(), x * spacing, z * spacing);
        if (Math.abs((long) start.x - chunk.x) <= 5 && Math.abs((long) start.z - chunk.z) <= 6)
          return true;
      }
    return false;
  }

  public static boolean allowed(ServerLevel level, ChunkPos start) {
    if (!level.dimension().equals(SlimicWorld.DIMENSION)) return false;
    for (int x = start.x - 1; x <= start.x + 1; x++)
      for (int z = start.z - 4; z <= start.z + 1; z++) {
        ChunkPos position = new ChunkPos(x, z);
        if (SanctumPlacement.reserved(level, position)) return false;
        var loaded = LOADED.getOrDefault(level, Map.of()).get(position.toLong());
        if (loaded != null) {
          if (loaded.contains(start.toLong())) continue;
          return false;
        }
        try {
          var data = level.getChunkSource().chunkMap.read(position).join();
          if (data.isEmpty()) continue;
          var tag = data.get();
          if (!tag.getString("Status").equals("minecraft:full")
              && !tag.getString("Status").equals("full")) continue;
          var structures = tag.getCompound("structures");
          var actual = structures.getCompound("starts").getCompound(SET.location().toString());
          boolean own =
              actual.getString("id").equals(SET.location().toString())
                  && actual.getInt("ChunkX") == start.x
                  && actual.getInt("ChunkZ") == start.z;
          boolean referenced =
              Arrays.stream(
                      structures.getCompound("References").getLongArray(SET.location().toString()))
                  .anyMatch(value -> value == start.toLong());
          if (!own && !referenced) return false;
        } catch (RuntimeException unreadable) {
          return false;
        }
      }
    return true;
  }

  public static Candidate find(ServerLevel level, BlockPos origin, int radius) {
    if (!level.dimension().equals(SlimicWorld.DIMENSION)) return null;
    var placement = placement(level);
    if (placement == null) return null;
    radius = Math.max(1, Math.min(512, radius));
    int spacing = placement.spacing(), cx = origin.getX() >> 4, cz = origin.getZ() >> 4;
    int rx = Math.floorDiv(cx, spacing),
        rz = Math.floorDiv(cz, spacing),
        rings = radius / spacing + 2;
    List<ChunkPos> candidates = new ArrayList<>();
    for (int dx = -rings; dx <= rings; dx++)
      for (int dz = -rings; dz <= rings; dz++) {
        var chunk =
            placement.getPotentialStructureChunk(
                level.getSeed(), (rx + dx) * spacing, (rz + dz) * spacing);
        if (Math.abs((long) chunk.x - cx) <= radius && Math.abs((long) chunk.z - cz) <= radius)
          candidates.add(chunk);
      }
    candidates.sort(
        Comparator.comparingDouble(
            chunk -> origin.distSqr(chunk.getMiddleBlockPosition(origin.getY()))));
    var generator = level.getChunkSource().getGenerator();
    var random = level.getChunkSource().randomState();
    for (ChunkPos chunk : candidates.subList(0, Math.min(128, candidates.size()))) {
      int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
      if (!level.getWorldBorder().isWithinBounds(new BlockPos(x - 24, 64, z - 65))
          || !level.getWorldBorder().isWithinBounds(new BlockPos(x + 24, 64, z + 24))) continue;
      int y = AssemblyChamberStructure.floor(generator, random, level, x, z);
      if (y == Integer.MIN_VALUE || !allowed(level, chunk)) continue;
      if (!generator
          .getBiomeSource()
          .getNoiseBiome(
              QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), random.sampler())
          .is(SlimicWorld.DAMP_PLAINS)) continue;
      int entrance =
          generator.getBaseHeight(
              x,
              z - 62,
              net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
              level,
              random);
      if (entrance - y < 12 || entrance - y > 36) continue;
      return new Candidate(new BlockPos(x, y, z), entrance);
    }
    return null;
  }
}
