package io.github.meistermods.slimic.world;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Placement prediction and bounded disk metadata reads never request a chunk or a ticket. */
public final class SanctumPlacement {
  public static final ResourceKey<StructureSet> SET =
      ResourceKey.create(Registries.STRUCTURE_SET, SlimicWorld.id("fallen_sanctum"));
  private static final Map<StructureTemplateManager, java.lang.ref.WeakReference<ServerLevel>>
      LEVELS = new ConcurrentHashMap<>();
  private static final Map<ServerLevel, Map<Long, Set<Long>>> LOADED_SITES =
      new ConcurrentHashMap<>();

  public record Candidate(BlockPos anchor, int rotation) {}

  private SanctumPlacement() {}

  public static void track(ServerLevel level) {
    if (level.dimension().equals(SlimicWorld.DIMENSION))
      LEVELS.put(level.getStructureManager(), new java.lang.ref.WeakReference<>(level));
  }

  public static void forget(ServerLevel level) {
    LEVELS.remove(level.getStructureManager());
    LOADED_SITES.remove(level);
  }

  public static void chunk(net.minecraft.world.level.chunk.LevelChunk chunk, boolean loaded) {
    if (!(chunk.getLevel() instanceof ServerLevel level)
        || !level.dimension().equals(SlimicWorld.DIMENSION)) return;
    long position = chunk.getPos().toLong();
    if (!loaded) {
      var known = LOADED_SITES.get(level);
      if (known != null) known.remove(position);
      return;
    }
    var known = LOADED_SITES.computeIfAbsent(level, unused -> new ConcurrentHashMap<>());
    var structure =
        level
            .registryAccess()
            .registryOrThrow(Registries.STRUCTURE)
            .get(SlimicWorld.id("fallen_sanctum"));
    var start = structure == null ? null : chunk.getStartForStructure(structure);
    Set<Long> references = new HashSet<>();
    if (start != null && start.isValid()) references.add(position);
    if (structure != null) references.addAll(chunk.getReferencesForStructure(structure));
    known.put(position, Set.copyOf(references));
  }

  public static ServerLevel level(StructureTemplateManager manager) {
    var reference = LEVELS.get(manager);
    return reference == null ? null : reference.get();
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
    int spacing = placement.spacing();
    int rx = Math.floorDiv(chunk.x, spacing), rz = Math.floorDiv(chunk.z, spacing);
    for (int x = rx - 1; x <= rx + 1; x++)
      for (int z = rz - 1; z <= rz + 1; z++) {
        var start = placement.getPotentialStructureChunk(level.getSeed(), x * spacing, z * spacing);
        if (Math.abs((long) start.x - chunk.x) <= 4 && Math.abs((long) start.z - chunk.z) <= 4)
          return true;
      }
    return false;
  }

  public static boolean existingTerrainAllows(ServerLevel level, ChunkPos start) {
    int rotation = FallenSanctumStructure.rotation(level.getSeed(), start.toLong());
    var anchor = start.getMiddleBlockPosition(0);
    var first = FallenSanctumPiece.position(anchor, rotation, -32, 0, -40);
    var last = FallenSanctumPiece.position(anchor, rotation, 31, 0, 39);
    for (int x = Math.min(first.getX(), last.getX()) >> 4;
        x <= Math.max(first.getX(), last.getX()) >> 4;
        x++)
      for (int z = Math.min(first.getZ(), last.getZ()) >> 4;
          z <= Math.max(first.getZ(), last.getZ()) >> 4;
          z++) {
        var position = new ChunkPos(x, z);
        var references = LOADED_SITES.getOrDefault(level, Map.of()).get(position.toLong());
        if (references != null) {
          if (!references.contains(start.toLong())) return false;
          continue;
        }
        try {
          var data = level.getChunkSource().chunkMap.read(position).join();
          if (data.isEmpty()) continue;
          CompoundTag tag = data.get();
          if (!tag.getString("Status").equals("minecraft:full")
              && !tag.getString("Status").equals("full")) continue;
          var structures = tag.getCompound("structures");
          var actualStart = structures.getCompound("starts").getCompound("slimic:fallen_sanctum");
          boolean own =
              actualStart.getString("id").equals("slimic:fallen_sanctum")
                  && actualStart.getInt("ChunkX") == start.x
                  && actualStart.getInt("ChunkZ") == start.z;
          boolean referenced =
              java.util.Arrays.stream(
                      structures.getCompound("References").getLongArray("slimic:fallen_sanctum"))
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
    List<ChunkPos> candidates = new ArrayList<>();
    int centerX = Math.floorDiv(cx, spacing), centerZ = Math.floorDiv(cz, spacing);
    int rings = radius / spacing + 2, limit = radius;
    int[] visited = {0};
    java.util.function.BiConsumer<Integer, Integer> consider =
        (rx, rz) -> {
          if (visited[0]++ >= 4096) return;
          var pos =
              placement.getPotentialStructureChunk(level.getSeed(), rx * spacing, rz * spacing);
          if (Math.abs((long) pos.x - cx) <= limit && Math.abs((long) pos.z - cz) <= limit)
            candidates.add(pos);
        };
    consider.accept(centerX, centerZ);
    for (int ring = 1; ring <= rings && visited[0] < 4096; ring++) {
      for (int dx = -ring; dx <= ring && visited[0] < 4096; dx++) {
        consider.accept(centerX + dx, centerZ - ring);
        consider.accept(centerX + dx, centerZ + ring);
      }
      for (int dz = -ring + 1; dz < ring && visited[0] < 4096; dz++) {
        consider.accept(centerX - ring, centerZ + dz);
        consider.accept(centerX + ring, centerZ + dz);
      }
    }
    candidates.sort(
        Comparator.comparingDouble(
            pos -> origin.distSqr(pos.getMiddleBlockPosition(origin.getY()))));
    var generator = level.getChunkSource().getGenerator();
    var random = level.getChunkSource().randomState();
    for (var pos : candidates.subList(0, Math.min(128, candidates.size()))) {
      int x = pos.getMiddleBlockX(), z = pos.getMiddleBlockZ();
      if (!level.getWorldBorder().isWithinBounds(new BlockPos(x - 48, 64, z - 48))
          || !level.getWorldBorder().isWithinBounds(new BlockPos(x + 48, 64, z + 48))) continue;
      int y = FallenSanctumStructure.floor(generator, random, level, x, z);
      if (y == Integer.MIN_VALUE) continue;
      if (!generator
          .getBiomeSource()
          .getNoiseBiome(
              QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), random.sampler())
          .is(SlimicWorld.DAMP_PLAINS)) continue;
      if (!existingTerrainAllows(level, pos)) continue;
      return new Candidate(
          new BlockPos(x, y, z), FallenSanctumStructure.rotation(level.getSeed(), pos.toLong()));
    }
    return null;
  }
}
