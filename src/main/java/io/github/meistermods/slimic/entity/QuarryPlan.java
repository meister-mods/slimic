package io.github.meistermods.slimic.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

public final class QuarryPlan {
  private final ChunkPos chunk;
  private final int topFloor;
  private int layer;
  private int rampOrigin;

  public QuarryPlan(ChunkPos chunk, int topFloor, int layer) {
    this.chunk = chunk;
    this.topFloor = topFloor;
    this.layer = layer;
    rampOrigin = Math.floorMod(topFloor, 28);
  }

  public int topFloor() {
    return topFloor;
  }

  public int rampOrigin() {
    return rampOrigin;
  }

  void alignRamp(int origin) {
    rampOrigin = Math.floorMod(origin, 28);
  }

  public int layer() {
    return layer;
  }

  public void descend() {
    layer--;
  }

  public boolean contains(BlockPos pos) {
    return (pos.getX() >> 4) == chunk.x && (pos.getZ() >> 4) == chunk.z;
  }

  // Each corner is a flat 2x2 landing. A straight tread is two blocks long and wide.
  public static int rampPhase(int x, int z) {
    if (x < 0 || x > 15 || z < 0 || z > 15) return -1;
    if (x <= 1 && z <= 1) return 0;
    if (x >= 14 && z <= 1) return 14;
    if (x >= 14 && z >= 14) return 28;
    if (x <= 1 && z >= 14) return 42;
    if (z <= 1) return x - 1;
    if (x >= 14) return 14 + z - 1;
    if (z >= 14) return 28 + 14 - x;
    if (x <= 1) return 42 + 14 - z;
    return -1;
  }

  public boolean isRampFloor(BlockPos pos) {
    if (!contains(pos) || pos.getY() > topFloor) return false;
    int phase = rampPhase(pos.getX() - chunk.getMinBlockX(), pos.getZ() - chunk.getMinBlockZ());
    return phase >= 0 && Math.floorMod(rampOrigin - pos.getY(), 28) == phase / 2;
  }

  public List<BlockPos> floorsAt(int y) {
    List<BlockPos> result = new ArrayList<>();
    if (y > topFloor) return result;
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) {
        BlockPos pos = new BlockPos(chunk.getMinBlockX() + x, y, chunk.getMinBlockZ() + z);
        if (isRampFloor(pos)) result.add(pos);
      }
    return result;
  }

  public CompoundTag save() {
    CompoundTag tag = new CompoundTag();
    tag.putInt("TopFloor", topFloor);
    tag.putInt("Layer", layer);
    tag.putInt("RampOrigin", rampOrigin);
    return tag;
  }

  public static QuarryPlan load(ChunkPos chunk, CompoundTag tag, int minY, int maxY) {
    int top = Math.max(minY, Math.min(maxY - 3, tag.getInt("TopFloor")));
    int layer = Math.max(minY - 1, Math.min(top + 2, tag.getInt("Layer")));
    var plan = new QuarryPlan(chunk, top, layer);
    if (tag.contains("RampOrigin", net.minecraft.nbt.Tag.TAG_INT))
      plan.alignRamp(tag.getInt("RampOrigin"));
    return plan;
  }
}
