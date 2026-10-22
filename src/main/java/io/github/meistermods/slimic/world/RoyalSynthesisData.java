package io.github.meistermods.slimic.world;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** Authoritative consumption ledger and recoverable result mailbox, shared by every dimension. */
public final class RoyalSynthesisData extends SavedData {
  @Override
  public void save(java.io.File file) {
    if (isDirty()) DurableWorldData.checkpoint(file, this);
  }

  public static final String NAME = "slimic_royal_synthesis";

  public static final class Transaction {
    public final UUID id, owner, normal, ancient, result;
    public final String dimension;
    public final BlockPos position;
    public final long normalPoints, ancientPoints;
    public ItemStack output;
    public final List<ItemStack> before, after;
    public boolean delivered;

    Transaction(
        UUID id,
        UUID owner,
        UUID normal,
        UUID ancient,
        UUID result,
        String dimension,
        BlockPos position,
        long normalPoints,
        long ancientPoints,
        ItemStack output,
        List<ItemStack> before,
        List<ItemStack> after) {
      this.id = id;
      this.owner = owner;
      this.normal = normal;
      this.ancient = ancient;
      this.result = result;
      this.dimension = dimension;
      this.position = position.immutable();
      this.normalPoints = normalPoints;
      this.ancientPoints = ancientPoints;
      this.output = output.copy();
      this.before = copy(before);
      this.after = copy(after);
    }
  }

  private final Map<UUID, Transaction> transactions = new LinkedHashMap<>();

  private record StationKey(String dimension, long position) {}

  private final Map<StationKey, Transaction> latest = new HashMap<>();
  private final Map<UUID, UUID> consumed = new HashMap<>();
  private final Map<UUID, UUID> resultTokens = new HashMap<>();
  private final Map<UUID, ItemStack> releaseRecovery = new HashMap<>();
  private final Set<UUID> releasing = new HashSet<>();
  private final Set<UUID> pending = new HashSet<>();

  public static RoyalSynthesisData get(ServerLevel level) {
    return level
        .getServer()
        .overworld()
        .getDataStorage()
        .computeIfAbsent(RoyalSynthesisData::load, RoyalSynthesisData::new, NAME);
  }

  private static List<ItemStack> copy(List<ItemStack> items) {
    return items.stream().map(ItemStack::copy).toList();
  }

  public boolean consumed(UUID id) {
    return consumed.containsKey(id);
  }

  public boolean unavailable(UUID id) {
    return consumed(id) || pending.contains(id);
  }

  public boolean reserve(UUID first, UUID second) {
    if (first.equals(second)
        || consumed(first)
        || consumed(second)
        || pending.contains(first)
        || pending.contains(second)) return false;
    pending.add(first);
    pending.add(second);
    return true;
  }

  public void release(UUID first, UUID second) {
    pending.remove(first);
    pending.remove(second);
  }

  public Transaction transaction(UUID id) {
    return transactions.get(id);
  }

  public Transaction lastAt(String dimension, BlockPos position) {
    return latest.get(new StationKey(dimension, position.asLong()));
  }

  public boolean staleImage(
      String dimension, BlockPos position, UUID applied, List<ItemStack> inputs) {
    // A stale block image can contain unrelated inputs inserted after an earlier commit.
    // Never roll those unconsumed companions into a different transaction's after-image.
    for (int index = 0; index < Math.min(2, inputs.size()); index++) {
      var item = inputs.get(index);
      if (item.isEmpty()) continue;
      var saved = item.hasTag() ? item.getTag().getCompound("SlimeData") : new CompoundTag();
      if (!saved.hasUUID("UUID") || !consumed(saved.getUUID("UUID"))) return false;
    }
    var prior = applied == null ? null : transactions.get(applied);
    if (prior != null && prior.dimension.equals(dimension) && prior.position.equals(position))
      return true;
    for (var transaction : transactions.values()) {
      if (!transaction.dimension.equals(dimension)
          || !transaction.position.equals(position)
          || transaction.before.size() != inputs.size()) continue;
      boolean matches = true;
      for (int i = 0; i < inputs.size(); i++)
        if (!ItemStack.matches(inputs.get(i), transaction.before.get(i))) matches = false;
      if (matches) return true;
    }
    return false;
  }

  public Transaction pendingFor(UUID owner) {
    return transactions.values().stream()
        .filter(
            tx ->
                tx.owner.equals(owner)
                    && (!tx.delivered
                        || releaseRecovery.containsKey(tx.result)
                            && !releasing.contains(tx.result)))
        .findFirst()
        .orElse(null);
  }

  public boolean commit(ServerLevel level, Transaction tx) {
    if (consumed(tx.normal) || consumed(tx.ancient) || transactions.containsKey(tx.id))
      return false;
    transactions.put(tx.id, tx);
    var key = new StationKey(tx.dimension, tx.position.asLong());
    var prior = latest.put(key, tx);
    consumed.put(tx.normal, tx.id);
    consumed.put(tx.ancient, tx.id);
    UUID token =
        tx.output
            .getTag()
            .getCompound("SlimeData")
            .getCompound("ForgeData")
            .getUUID("SynthesisToken");
    resultTokens.put(tx.result, token);
    setDirty();
    if (DurableWorldData.flush(level, NAME, this)) return true;
    transactions.remove(tx.id);
    if (prior == null) latest.remove(key);
    else latest.put(key, prior);
    consumed.remove(tx.normal);
    consumed.remove(tx.ancient);
    resultTokens.remove(tx.result);
    setDirty();
    return false;
  }

  /**
   * A copied old summon item cannot become valid again after a result is released and recovered.
   */
  public boolean mayRestore(CompoundTag entity) {
    if (!entity.hasUUID("UUID")) return false;
    UUID id = entity.getUUID("UUID");
    if (unavailable(id)) return false;
    UUID token = resultTokens.get(id);
    if (token == null) return !entity.getCompound("ForgeData").hasUUID("SynthesisTransaction");
    var data = entity.getCompound("ForgeData");
    return data.hasUUID("SynthesisToken") && token.equals(data.getUUID("SynthesisToken"));
  }

  public boolean released(
      ServerLevel level, io.github.meistermods.slimic.entity.FriendlySlime slime) {
    UUID previous = resultTokens.get(slime.getUUID());
    if (previous == null) return !consumed(slime.getUUID());
    var persistent = slime.getPersistentData();
    var tx =
        persistent.hasUUID("SynthesisTransaction")
            ? transactions.get(persistent.getUUID("SynthesisTransaction"))
            : null;
    if (tx == null || level.noSave) return false;
    UUID next = UUID.randomUUID();
    var snapshot = io.github.meistermods.slimic.item.FriendlySlimeItem.fromSlime(slime);
    snapshot
        .getTag()
        .getCompound("SlimeData")
        .getCompound("ForgeData")
        .putUUID("SynthesisToken", next);
    releaseRecovery.put(slime.getUUID(), snapshot);
    releasing.add(slime.getUUID());
    resultTokens.put(slime.getUUID(), next);
    setDirty();
    if (!DurableWorldData.flush(level, NAME, this)) {
      resultTokens.put(slime.getUUID(), previous);
      releaseRecovery.remove(slime.getUUID());
      releasing.remove(slime.getUUID());
      return false;
    }
    persistent.putUUID("SynthesisToken", next);
    try {
      // Keep a recoverable new-epoch snapshot until native entity storage has been flushed.
      level.save(null, true, false);
    } catch (RuntimeException failure) {
      releasing.remove(slime.getUUID());
      com.mojang.logging.LogUtils.getLogger()
          .error("Could not checkpoint released synthesis companion {}", slime.getUUID(), failure);
      return false;
    }
    releaseRecovery.remove(slime.getUUID());
    releasing.remove(slime.getUUID());
    setDirty();
    DurableWorldData.flush(level, NAME, this);
    return true;
  }

  public boolean deliver(ServerPlayer owner, Transaction tx) {
    if (!tx.owner.equals(owner.getUUID()) || releasing.contains(tx.result)) return false;
    var recovery = releaseRecovery.get(tx.result);
    if (recovery != null) {
      for (var world : owner.server.getAllLevels()) {
        var entity = world.getEntity(tx.result);
        if (entity instanceof io.github.meistermods.slimic.entity.FriendlySlime slime
            && mayRestore(slime.saveWithoutId(new CompoundTag()))) {
          releaseRecovery.remove(tx.result);
          setDirty();
          DurableWorldData.flush(owner.serverLevel(), NAME, this);
          return false;
        }
      }
      if (owner.getInventory().getFreeSlot() < 0) return false;
      UUID previous = resultTokens.get(tx.result), next = UUID.randomUUID();
      var before = tx.output;
      boolean deliveredBefore = tx.delivered;
      tx.output = recovery.copy();
      tx.output
          .getTag()
          .getCompound("SlimeData")
          .getCompound("ForgeData")
          .putUUID("SynthesisToken", next);
      tx.delivered = false;
      resultTokens.put(tx.result, next);
      releaseRecovery.remove(tx.result);
      setDirty();
      if (!DurableWorldData.flush(owner.serverLevel(), NAME, this)) {
        tx.output = before;
        tx.delivered = deliveredBefore;
        resultTokens.put(tx.result, previous);
        releaseRecovery.put(tx.result, recovery);
        return false;
      }
    }
    if (tx.delivered) return false;
    // The result is saved in the player's inventory before acknowledging delivery. A crash
    // between these writes is recovered by the transaction id already present in that inventory.
    for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
      var item = owner.getInventory().getItem(slot);
      if (isResult(item, tx)) {
        if (!DurableWorldData.savePlayer(owner)) return false;
        tx.delivered = true;
        setDirty();
        DurableWorldData.flush(owner.serverLevel(), NAME, this);
        return true;
      }
    }
    int slot = owner.getInventory().getFreeSlot();
    if (slot < 0) return false;
    if (!DurableWorldData.givePlayerItems(owner, List.of(tx.output))) return false;
    tx.delivered = true;
    setDirty();
    DurableWorldData.flush(owner.serverLevel(), NAME, this);
    owner.inventoryMenu.broadcastChanges();
    return true;
  }

  private static boolean isResult(ItemStack item, Transaction tx) {
    return item.hasTag()
        && item.getTag()
            .getCompound("SlimeData")
            .getCompound("ForgeData")
            .hasUUID("SynthesisTransaction")
        && tx.id.equals(
            item.getTag()
                .getCompound("SlimeData")
                .getCompound("ForgeData")
                .getUUID("SynthesisTransaction"))
        && item.getTag().getCompound("SlimeData").getCompound("ForgeData").hasUUID("SynthesisToken")
        && tx.output
            .getTag()
            .getCompound("SlimeData")
            .getCompound("ForgeData")
            .getUUID("SynthesisToken")
            .equals(
                item.getTag()
                    .getCompound("SlimeData")
                    .getCompound("ForgeData")
                    .getUUID("SynthesisToken"));
  }

  private static ListTag stacks(List<ItemStack> items) {
    var list = new ListTag();
    items.forEach(item -> list.add(item.save(new CompoundTag())));
    return list;
  }

  private static List<ItemStack> stacks(CompoundTag tag, String name) {
    return tag.getList(name, Tag.TAG_COMPOUND).stream()
        .map(raw -> ItemStack.of((CompoundTag) raw))
        .toList();
  }

  @Override
  public CompoundTag save(CompoundTag tag) {
    var list = new ListTag();
    for (var tx : transactions.values()) {
      var data = new CompoundTag();
      data.putUUID("Id", tx.id);
      data.putUUID("Owner", tx.owner);
      data.putUUID("Normal", tx.normal);
      data.putUUID("Ancient", tx.ancient);
      data.putUUID("Result", tx.result);
      data.putString("Dimension", tx.dimension);
      data.put("Position", NbtUtils.writeBlockPos(tx.position));
      data.putLong("NormalPoints", tx.normalPoints);
      data.putLong("AncientPoints", tx.ancientPoints);
      data.put("Output", tx.output.save(new CompoundTag()));
      data.put("Before", stacks(tx.before));
      data.put("After", stacks(tx.after));
      data.putBoolean("Delivered", tx.delivered);
      list.add(data);
    }
    tag.putInt("Schema", 1);
    tag.put("Transactions", list);
    var tokens = new ListTag();
    resultTokens.forEach(
        (id, token) -> {
          var data = new CompoundTag();
          data.putUUID("Id", id);
          data.putUUID("Token", token);
          tokens.add(data);
        });
    tag.put("ResultTokens", tokens);
    var recoveries = new ListTag();
    releaseRecovery.forEach(
        (id, item) -> {
          var entry = new CompoundTag();
          entry.putUUID("Id", id);
          entry.put("Item", item.save(new CompoundTag()));
          recoveries.add(entry);
        });
    tag.put("ReleaseRecovery", recoveries);
    return tag;
  }

  public static RoyalSynthesisData load(CompoundTag tag) {
    var result = new RoyalSynthesisData();
    for (var raw : tag.getList("Transactions", Tag.TAG_COMPOUND)) {
      var d = (CompoundTag) raw;
      if (!d.hasUUID("Id")
          || !d.hasUUID("Owner")
          || !d.hasUUID("Normal")
          || !d.hasUUID("Ancient")
          || !d.hasUUID("Result")) continue;
      var output = ItemStack.of(d.getCompound("Output"));
      if (output.isEmpty()) continue;
      var tx =
          new Transaction(
              d.getUUID("Id"),
              d.getUUID("Owner"),
              d.getUUID("Normal"),
              d.getUUID("Ancient"),
              d.getUUID("Result"),
              d.getString("Dimension"),
              NbtUtils.readBlockPos(d.getCompound("Position")),
              d.getLong("NormalPoints"),
              d.getLong("AncientPoints"),
              output,
              stacks(d, "Before"),
              stacks(d, "After"));
      tx.delivered = d.getBoolean("Delivered");
      result.transactions.put(tx.id, tx);
      result.latest.put(new StationKey(tx.dimension, tx.position.asLong()), tx);
      result.consumed.put(tx.normal, tx.id);
      result.consumed.put(tx.ancient, tx.id);
    }
    for (var raw : tag.getList("ResultTokens", Tag.TAG_COMPOUND)) {
      var d = (CompoundTag) raw;
      if (d.hasUUID("Id") && d.hasUUID("Token"))
        result.resultTokens.put(d.getUUID("Id"), d.getUUID("Token"));
    }
    for (var raw : tag.getList("ReleaseRecovery", Tag.TAG_COMPOUND)) {
      var entry = (CompoundTag) raw;
      var item = ItemStack.of(entry.getCompound("Item"));
      if (entry.hasUUID("Id") && !item.isEmpty())
        result.releaseRecovery.put(entry.getUUID("Id"), item);
    }
    return result;
  }
}
