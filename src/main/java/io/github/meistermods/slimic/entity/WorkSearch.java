package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.SlimeChestBlockEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Shared, short-lived candidate lists; callers revalidate every request and transfer. */
@Mod.EventBusSubscriber(modid = Slimic.MOD_ID)
public final class WorkSearch {
  public static final int REFRESH_TICKS = 20;
  private static final Map<ServerLevel, Map<Long, Snapshot>> CACHE = new HashMap<>();

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

  @SubscribeEvent
  public static void unload(LevelEvent.Unload event) {
    if (event.getLevel() instanceof ServerLevel level) CACHE.remove(level);
  }
}
