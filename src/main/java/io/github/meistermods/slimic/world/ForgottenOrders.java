package io.github.meistermods.slimic.world;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Redeemed ancient identities are world-wide, independent of companion UUIDs. */
public final class ForgottenOrders extends SavedData {
  private final Set<UUID> redeemed = new HashSet<>();
  private final Set<UUID> pending = new HashSet<>();

  public static ForgottenOrders get(ServerLevel level) {
    return level
        .getServer()
        .overworld()
        .getDataStorage()
        .computeIfAbsent(ForgottenOrders::load, ForgottenOrders::new, "slimic_forgotten_orders");
  }

  public boolean reserve(UUID core) {
    return !redeemed.contains(core) && pending.add(core);
  }

  public void release(UUID core) {
    pending.remove(core);
  }

  public boolean isRedeemed(UUID core) {
    return redeemed.contains(core);
  }

  public void redeem(UUID core) {
    redeemed.add(core);
    setDirty();
  }

  public static ForgottenOrders load(CompoundTag tag) {
    var result = new ForgottenOrders();
    for (var value : tag.getList("Redeemed", 11)) {
      try {
        result.redeemed.add(NbtUtils.loadUUID(value));
      } catch (IllegalArgumentException invalid) {
        /* Ignore malformed legacy records. */
      }
    }
    return result;
  }

  @Override
  public CompoundTag save(CompoundTag tag) {
    var values = new ListTag();
    redeemed.forEach(id -> values.add(NbtUtils.createUUID(id)));
    tag.put("Redeemed", values);
    return tag;
  }
}
