package io.github.meistermods.slimic.world;

import java.util.*;
import net.minecraft.nbt.*;

/** The encounter owns mass, even while a fragment's chunk is unloaded. */
public final class ColonyCohesion {
  public static final int TOTAL = 100, FRAGMENT = 12, MAX_FRAGMENTS = 6;
  private final Map<UUID, Integer> active = new LinkedHashMap<>();
  private int lost, sequence, healSpent;

  public int free() {
    return TOTAL - lost - active.size() * FRAGMENT;
  }

  public int lost() {
    return lost;
  }

  public int remaining() {
    return TOTAL - lost;
  }

  public int count() {
    return active.size();
  }

  public int healSpent() {
    return healSpent;
  }

  public Set<UUID> ids() {
    return Set.copyOf(active.keySet());
  }

  public boolean owns(UUID id, int sequence) {
    return Objects.equals(active.get(id), sequence);
  }

  /** Reserve before spawning. A rejected spawn rolls back this reservation without healing. */
  public int reserve(UUID id, int cap) {
    if (active.containsKey(id)
        || active.size() >= Math.min(MAX_FRAGMENTS, cap)
        || free() < FRAGMENT) return -1;
    int next = ++sequence;
    active.put(id, next);
    return next;
  }

  public boolean cancel(UUID id, int sequence) {
    if (!owns(id, sequence)) return false;
    active.remove(id);
    return true;
  }

  public boolean killed(UUID id, int sequence) {
    if (!cancel(id, sequence)) return false;
    lost += FRAGMENT;
    return true;
  }

  /** Only real surviving fragments may return. Healing caps never manufacture mass. */
  public int absorb(Collection<UUID> survivors) {
    int returned = 0;
    for (UUID id : new LinkedHashSet<>(survivors)) if (active.remove(id) != null) returned++;
    int heal = Math.min(Math.min(returned * 3, 9), 48 - healSpent);
    healSpent += heal;
    return heal;
  }

  public CompoundTag save() {
    CompoundTag tag = new CompoundTag();
    tag.putInt("Lost", lost);
    tag.putInt("Sequence", sequence);
    tag.putInt("HealSpent", healSpent);
    ListTag fragments = new ListTag();
    active.forEach(
        (id, seq) -> {
          CompoundTag entry = new CompoundTag();
          entry.putUUID("Id", id);
          entry.putInt("Sequence", seq);
          fragments.add(entry);
        });
    tag.put("Active", fragments);
    return tag;
  }

  public static ColonyCohesion load(CompoundTag tag) {
    ColonyCohesion pool = new ColonyCohesion();
    pool.lost = Math.max(0, Math.min(96, tag.getInt("Lost") / FRAGMENT * FRAGMENT));
    pool.sequence = Math.max(0, tag.getInt("Sequence"));
    pool.healSpent = Math.max(0, Math.min(48, tag.getInt("HealSpent")));
    for (Tag raw : tag.getList("Active", Tag.TAG_COMPOUND)) {
      CompoundTag entry = (CompoundTag) raw;
      int seq = entry.getInt("Sequence");
      if (entry.hasUUID("Id")
          && seq > 0
          && pool.count() < MAX_FRAGMENTS
          && pool.free() >= FRAGMENT) {
        pool.active.putIfAbsent(entry.getUUID("Id"), seq);
        pool.sequence = Math.max(pool.sequence, seq);
      }
    }
    return pool;
  }
}
