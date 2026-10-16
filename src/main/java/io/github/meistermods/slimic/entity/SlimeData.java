package io.github.meistermods.slimic.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Versioned companion save data shared by world saves and stored summon items. */
public final class SlimeData {
  public static final int VERSION = 1;
  public static final String VERSION_KEY = "SlimeDataVersion";

  private SlimeData() {}

  public static boolean canLoad(CompoundTag tag) {
    if (!tag.contains(VERSION_KEY)) return true;
    return tag.contains(VERSION_KEY, Tag.TAG_INT)
        && tag.getInt(VERSION_KEY) >= 0
        && tag.getInt(VERSION_KEY) <= VERSION;
  }

  public static CompoundTag migrate(CompoundTag tag) {
    CompoundTag migrated = tag.copy();
    if (canLoad(migrated)) migrated.putInt(VERSION_KEY, VERSION);
    return migrated;
  }
}
