package io.github.meistermods.slimic.block;

import io.github.meistermods.slimic.registry.ModBlocks;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Expiring light leases are stored with their chunk, including temporary firefly trails. */
public final class SlimeLightBlockEntity extends BlockEntity {
  private final Map<UUID, Long> sources = new HashMap<>();
  private long fireflyUntil;

  public SlimeLightBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlocks.SLIME_LIGHT_ENTITY.get(), pos, state);
  }

  public void lease(UUID source, long until, boolean firefly) {
    if (firefly) fireflyUntil = Math.max(fireflyUntil, until);
    else if (sources.size() < 128 || sources.containsKey(source)) sources.put(source, until);
    setChanged();
  }

  public boolean hasFireflies(long time) {
    return fireflyUntil > time;
  }

  public boolean active(long time) {
    sources.values().removeIf(until -> until <= time);
    return fireflyUntil > time || !sources.isEmpty();
  }

  public void release(UUID source) {
    if (sources.remove(source) != null) setChanged();
    if (level != null
        && !level.isClientSide
        && !active(level.getGameTime())
        && level.getBlockEntity(worldPosition) == this) level.removeBlock(worldPosition, false);
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    tag.putLong("FireflyUntil", fireflyUntil);
    var list = new ListTag();
    sources.forEach(
        (id, until) -> {
          var t = new CompoundTag();
          t.putUUID("Source", id);
          t.putLong("Until", until);
          list.add(t);
        });
    tag.put("Sources", list);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    sources.clear();
    fireflyUntil = tag.getLong("FireflyUntil");
    var list = tag.getList("Sources", Tag.TAG_COMPOUND);
    for (int i = 0; i < Math.min(128, list.size()); i++) {
      var t = list.getCompound(i);
      if (t.hasUUID("Source")) sources.put(t.getUUID("Source"), t.getLong("Until"));
    }
  }
}
