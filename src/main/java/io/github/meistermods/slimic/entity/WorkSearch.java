package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.SlimeChestBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Shared, short-lived candidate lists; callers revalidate every request and transfer. */
@Mod.EventBusSubscriber(modid = Slimic.MOD_ID)
public final class WorkSearch {
  public static final int REFRESH_TICKS = 20;
  private static final Map<ServerLevel, Map<Long, Snapshot>> CACHE = new HashMap<>();

  private static final Map<ServerLevel, Map<Long, ChunkPos>> LOADED = new ConcurrentHashMap<>();

  public record Snapshot(long sampledAt, List<BlockPos> chests, List<BagSlime> workers) {
    public Snapshot {
      chests = List.copyOf(chests);
      workers = List.copyOf(workers);
    }
  }

  private WorkSearch() {}

  public static Snapshot inChunk(ServerLevel level, ChunkPos pos) {
    var chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
    long now = level.getGameTime();
    if (chunk == null) return new Snapshot(now, List.of(), List.of());
    Map<Long, Snapshot> cache = CACHE.computeIfAbsent(level, ignored -> new HashMap<>());
    Snapshot saved = cache.get(pos.toLong());
    if (saved != null && now >= saved.sampledAt() && now - saved.sampledAt() < REFRESH_TICKS)
      return saved;
    // Expire regions no longer queried so exploring a world cannot retain old entity references.
    cache
        .values()
        .removeIf(value -> now - value.sampledAt() >= REFRESH_TICKS || now < value.sampledAt());
    Snapshot result =
        new Snapshot(
            now,
            chunk.getBlockEntities().values().stream()
                .filter(entity -> entity instanceof SlimeChestBlockEntity && !entity.isRemoved())
                .map(entity -> entity.getBlockPos().immutable())
                .toList(),
            level.getEntitiesOfClass(
                BagSlime.class,
                new AABB(
                    pos.getMinBlockX(),
                    level.getMinBuildHeight(),
                    pos.getMinBlockZ(),
                    pos.getMaxBlockX() + 1,
                    level.getMaxBuildHeight(),
                    pos.getMaxBlockZ() + 1)));
    cache.put(pos.toLong(), result);
    return result;
  }

  /** Chooses between a small coordinate window and the actual loaded-chunk set. */
  public static List<LevelChunk> loadedChunks(ServerLevel level, BlockPos origin, double radius) {
    double r = Math.ceil(radius);
    double minX = Math.floor((origin.getX() - r) / 16), maxX = Math.floor((origin.getX() + r) / 16);
    double minZ = Math.floor((origin.getZ() - r) / 16), maxZ = Math.floor((origin.getZ() + r) / 16);
    var result = new ArrayList<LevelChunk>();
    if ((maxX - minX + 1) * (maxZ - minZ + 1) <= 256) {
      for (int x = (int) minX; x <= maxX; x++)
        for (int z = (int) minZ; z <= maxZ; z++) {
          var chunk = level.getChunkSource().getChunkNow(x, z);
          if (chunk != null) result.add(chunk);
        }
    } else {
      for (var pos : LOADED.getOrDefault(level, Map.of()).values()) {
        if (pos.x < minX || pos.x > maxX || pos.z < minZ || pos.z > maxZ) continue;
        var chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk != null) result.add(chunk);
      }
      result.sort(
          Comparator.comparingInt((LevelChunk chunk) -> chunk.getPos().x)
              .thenComparingInt(chunk -> chunk.getPos().z));
    }
    return result;
  }

  /** Large Creative boxes iterate accessible entities rather than millions of empty columns. */
  public static <T extends Entity> List<T> entities(Level level, Class<T> type, AABB box) {
    if (!(level instanceof ServerLevel server) || box.getXsize() <= 4096)
      return level.getEntitiesOfClass(type, box);
    var result = new ArrayList<T>();
    for (var entity : server.getAllEntities()) {
      if (type.isInstance(entity)
          && !entity.isRemoved()
          && EntitySelector.NO_SPECTATORS.test(entity)
          && entity.getBoundingBox().intersects(box)) result.add(type.cast(entity));
    }
    return result;
  }

  @SubscribeEvent
  public static void chunkLoaded(ChunkEvent.Load event) {
    if (event.getLevel() instanceof ServerLevel level
        && event.getChunk() instanceof LevelChunk chunk)
      LOADED
          .computeIfAbsent(level, ignored -> new ConcurrentHashMap<>())
          .put(chunk.getPos().toLong(), chunk.getPos());
  }

  @SubscribeEvent
  public static void chunkUnloaded(ChunkEvent.Unload event) {
    if (event.getLevel() instanceof ServerLevel level) {
      var chunks = LOADED.get(level);
      if (chunks != null) chunks.remove(event.getChunk().getPos().toLong());
      var snapshots = CACHE.get(level);
      if (snapshots != null) snapshots.remove(event.getChunk().getPos().toLong());
    }
  }

  @SubscribeEvent
  public static void unload(LevelEvent.Unload event) {
    if (event.getLevel() instanceof ServerLevel level) {
      CACHE.remove(level);
      LOADED.remove(level);
    }
  }
}
