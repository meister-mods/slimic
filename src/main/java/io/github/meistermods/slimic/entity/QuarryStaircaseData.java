package io.github.meistermods.slimic.entity;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent, dimension-local staircase profiles, independent of their builders' lifetimes. */
public final class QuarryStaircaseData extends SavedData {
  private static final String NAME = "slimic_quarry_staircases";
  private static final int PERIOD = 28;
  private final int minY, maxY;
  private final Map<Long, Staircase> chunks = new HashMap<>();

  private static final class Staircase {
    final int origin;
    final int[] tops;

    Staircase(int origin, int[] tops) {
      this.origin = origin;
      this.tops = tops;
    }
  }

  private QuarryStaircaseData(int minY, int maxY) {
    this.minY = minY;
    this.maxY = maxY;
  }

  public static QuarryStaircaseData get(ServerLevel level) {
    return level
        .getDataStorage()
        .computeIfAbsent(
            tag -> load(tag, level.getMinBuildHeight(), level.getMaxBuildHeight()),
            () -> new QuarryStaircaseData(level.getMinBuildHeight(), level.getMaxBuildHeight()),
            NAME);
  }

  public static QuarryStaircaseData load(CompoundTag tag, int minY, int maxY) {
    var data = new QuarryStaircaseData(minY, maxY);
    for (var entry : tag.getList("Chunks", Tag.TAG_COMPOUND)) {
      var saved = (CompoundTag) entry;
      int[] tops = saved.getIntArray("Tops").clone();
      if (tops.length == PERIOD && saved.contains("Chunk", Tag.TAG_LONG)) {
        for (int i = 0; i < PERIOD; i++) tops[i] = Math.max(minY - 1, Math.min(maxY - 3, tops[i]));
        data.chunks.put(
            saved.getLong("Chunk"),
            new Staircase(Math.floorMod(saved.getInt("Origin"), PERIOD), tops));
      }
    }
    return data;
  }

  public void register(ChunkPos chunk, QuarryPlan plan) {
    long key = chunk.toLong();
    var staircase = chunks.get(key);
    if (staircase == null) {
      int[] tops = new int[PERIOD];
      Arrays.fill(tops, minY - 1);
      staircase = new Staircase(plan.rampOrigin(), tops);
      chunks.put(key, staircase);
      setDirty();
    }
    // A plan that has not reached its first floor can safely join the existing ramp.
    if (plan.layer() > plan.topFloor()) plan.alignRamp(staircase.origin);
    int phase = plan.rampOrigin();
    int top = Math.max(minY - 1, Math.min(maxY - 3, plan.topFloor()));
    if (top > staircase.tops[phase]) {
      staircase.tops[phase] = top;
      setDirty();
    }
  }

  public boolean protects(BlockPos pos) {
    var staircase = chunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
    if (staircase == null || pos.getY() < minY || pos.getY() >= maxY) return false;
    int phase = QuarryPlan.rampPhase(pos.getX() & 15, pos.getZ() & 15);
    return phase >= 0
        && staircase.tops[Math.floorMod(pos.getY() + phase / 2, PERIOD)] >= pos.getY();
  }

  @Override
  public CompoundTag save(CompoundTag tag) {
    tag.putInt("Version", 1);
    var entries = new ListTag();
    chunks.forEach(
        (chunk, staircase) -> {
          var entry = new CompoundTag();
          entry.putLong("Chunk", chunk);
          entry.putInt("Origin", staircase.origin);
          entry.putIntArray("Tops", staircase.tops.clone());
          entries.add(entry);
        });
    tag.put("Chunks", entries);
    return tag;
  }
}
