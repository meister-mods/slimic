package io.github.meistermods.slimic.world;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-dimension exit index. Entries are revalidated against real terrain before every trip. */
public final class SlimicPortalIndex extends SavedData {
  private static final int LOAD_LIMIT = 65_536;
  private final Map<Long, Set<Long>> chunks = new HashMap<>();

  public static SlimicPortalIndex get(ServerLevel level) {
    return level
        .getDataStorage()
        .computeIfAbsent(SlimicPortalIndex::load, SlimicPortalIndex::new, "slimic_portals");
  }

  public static SlimicPortalIndex load(CompoundTag tag) {
    var data = new SlimicPortalIndex();
    long[] positions = tag.getLongArray("Portals");
    for (int i = 0; i < Math.min(positions.length, LOAD_LIMIT); i++)
      data.insert(BlockPos.of(positions[i]));
    return data;
  }

  private void insert(BlockPos pos) {
    chunks
        .computeIfAbsent(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4), key -> new HashSet<>())
        .add(pos.asLong());
  }

  public void remember(BlockPos pos) {
    insert(pos);
    setDirty();
  }

  public void forget(BlockPos pos) {
    var values = chunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
    if (values != null && values.remove(pos.asLong())) {
      if (values.isEmpty()) chunks.remove(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
      setDirty();
    }
  }

  public List<BlockPos> near(BlockPos center) {
    List<BlockPos> result = new ArrayList<>();
    for (int x = (center.getX() - 128) >> 4; x <= (center.getX() + 128) >> 4; x++)
      for (int z = (center.getZ() - 128) >> 4; z <= (center.getZ() + 128) >> 4; z++)
        for (long raw : chunks.getOrDefault(ChunkPos.asLong(x, z), Set.of())) {
          var pos = BlockPos.of(raw);
          long dx = (long) pos.getX() - center.getX(), dz = (long) pos.getZ() - center.getZ();
          if (dx * dx + dz * dz <= 128L * 128) result.add(pos);
        }
    result.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
    return result.size() > 16 ? List.copyOf(result.subList(0, 16)) : result;
  }

  @Override
  public CompoundTag save(CompoundTag tag) {
    tag.putLongArray(
        "Portals",
        chunks.values().stream().flatMap(Set::stream).mapToLong(Long::longValue).toArray());
    return tag;
  }
}
