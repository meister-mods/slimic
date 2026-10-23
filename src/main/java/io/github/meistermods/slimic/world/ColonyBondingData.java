package io.github.meistermods.slimic.world;

import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** Reversible membership and release epochs are intentionally independent of royal synthesis. */
public final class ColonyBondingData extends SavedData {
  public static final String NAME = "slimic_colony_bonding";

  public enum State {
    BOUND,
    UNBIND_RESERVED,
    RESTORED
  }

  public static final class Binding {
    public UUID id, owner, representative;
    public UUID first, second;
    public State state = State.BOUND;
    public ItemStack output = ItemStack.EMPTY;
    public boolean delivered;
    public CompoundTag release = new CompoundTag();
  }

  private final Map<UUID, Binding> bindings = new LinkedHashMap<>();
  private final Map<UUID, UUID> tokens = new HashMap<>();
  private final Set<UUID> stored = new HashSet<>();
  private final Map<UUID, ItemStack> recovery = new LinkedHashMap<>();
  private final Set<UUID> admitting = new HashSet<>();
  private final List<java.lang.ref.WeakReference<FriendlySlime>> diskAdmissions = new ArrayList<>();

  public static ColonyBondingData get(ServerLevel level) {
    return level
        .getServer()
        .overworld()
        .getDataStorage()
        .computeIfAbsent(ColonyBondingData::load, ColonyBondingData::new, NAME);
  }

  @Override
  public void save(java.io.File file) {
    if (isDirty()) DurableWorldData.checkpoint(file, this);
  }

  public boolean flush(ServerLevel level) {
    setDirty();
    return DurableWorldData.flush(level, NAME, this);
  }

  public Binding binding(UUID representative) {
    return bindings.values().stream()
        .filter(b -> b.representative.equals(representative))
        .findFirst()
        .orElse(null);
  }

  public boolean memberBound(UUID id) {
    return bindings.values().stream()
        .anyMatch(b -> b.state != State.RESTORED && (b.first.equals(id) || b.second.equals(id)));
  }

  public boolean controlled(UUID id) {
    return tokens.containsKey(id);
  }

  private boolean matchingMembers(CompoundTag saved, Binding binding) {
    if (binding == null
        || binding.state != State.BOUND
        || !saved.hasUUID("Owner")
        || !binding.owner.equals(saved.getUUID("Owner"))) return false;
    CompoundTag forge = saved.getCompound("ForgeData");
    if (!forge.hasUUID("ColonyBinding") || !binding.id.equals(forge.getUUID("ColonyBinding")))
      return false;
    ListTag members = saved.getCompound("ColonyMembers").getList("Members", Tag.TAG_COMPOUND);
    if (members.size() != 2) return false;
    UUID[] expected = {binding.first, binding.second};
    for (int i = 0; i < 2; i++) {
      CompoundTag member = members.getCompound(i);
      if (!member.hasUUID("UUID")
          || !expected[i].equals(member.getUUID("UUID"))
          || !member.hasUUID("Owner")
          || !binding.owner.equals(member.getUUID("Owner"))) return false;
    }
    return true;
  }

  public boolean mayRestore(CompoundTag saved) {
    if (!saved.hasUUID("UUID")) return false;
    UUID id = saved.getUUID("UUID");
    if (memberBound(id)) return false;
    Binding binding = binding(id);
    if (binding != null && binding.state != State.BOUND) return false;
    if (saved.contains("ColonyMembers") && !matchingMembers(saved, binding)) return false;
    UUID token = tokens.get(id);
    if (token == null) return !saved.getCompound("ForgeData").hasUUID("ColonyToken");
    return stored.contains(id)
        && token.equals(
            saved.getCompound("ForgeData").hasUUID("ColonyToken")
                ? saved.getCompound("ForgeData").getUUID("ColonyToken")
                : null);
  }

  public boolean mayJoin(FriendlySlime slime) {
    if (slime instanceof BoundColony colony
        && (!colony.members().ready()
            || !matchingMembers(slime.saveWithoutId(new CompoundTag()), binding(slime.getUUID()))))
      return false;
    if (admitting.contains(slime.getUUID())) return true;
    UUID id = slime.getUUID();
    if (memberBound(id)) return false;
    Binding binding = binding(id);
    if (binding != null && binding.state != State.BOUND) return false;
    UUID token = tokens.get(id);
    if (token == null)
      return !(slime instanceof BoundColony) && !slime.getPersistentData().hasUUID("ColonyToken");
    return !stored.contains(id)
        && token.equals(
            slime.getPersistentData().hasUUID("ColonyToken")
                ? slime.getPersistentData().getUUID("ColonyToken")
                : null);
  }

  public void admitting(UUID id, boolean value) {
    if (value) admitting.add(id);
    else admitting.remove(id);
  }

  public boolean commit(ServerLevel level, Binding binding, FriendlySlime representative) {
    if (memberBound(binding.first)
        || memberBound(binding.second)
        || bindings.containsKey(binding.id)) return false;
    UUID token = UUID.randomUUID();
    representative.getPersistentData().putUUID("ColonyToken", token);
    representative.getPersistentData().putUUID("ColonyBinding", binding.id);
    binding.output = FriendlySlimeItem.fromSlime(representative);
    bindings.put(binding.id, binding);
    tokens.put(binding.representative, token);
    stored.add(binding.representative);
    if (flush(level)) return true;
    bindings.remove(binding.id);
    tokens.remove(binding.representative);
    stored.remove(binding.representative);
    return false;
  }

  public boolean released(ServerLevel level, FriendlySlime slime) {
    if (level.noSave) return false;
    if (slime instanceof BoundColony
        && !matchingMembers(slime.saveWithoutId(new CompoundTag()), binding(slime.getUUID())))
      return false;
    UUID id = slime.getUUID(), previous = tokens.get(id);
    if (previous == null) return !(slime instanceof BoundColony);
    if (!stored.contains(id) || !previous.equals(slime.getPersistentData().getUUID("ColonyToken")))
      return false;
    UUID next = UUID.randomUUID();
    slime.getPersistentData().putUUID("ColonyToken", next);
    tokens.put(id, next);
    stored.remove(id);
    recovery.put(id, snapshot(level, slime));
    if (!flush(level)) {
      slime.getPersistentData().putUUID("ColonyToken", previous);
      tokens.put(id, previous);
      stored.add(id);
      recovery.remove(id);
      return false;
    }
    try {
      level.save(null, true, false);
    } catch (RuntimeException failure) {
      com.mojang.logging.LogUtils.getLogger()
          .error("Could not checkpoint released colony member {}", id, failure);
      return false;
    }
    // Native entity storage can log an I/O error without throwing from Level.save. Keep the
    // fallback until this exact epoch is observed in a later disk admission.
    return true;
  }

  /** A new epoch invalidates copied world records before the stored item becomes authoritative. */
  public boolean store(ServerLevel level, FriendlySlime slime) {
    return store(level, slime, null, null);
  }

  private boolean store(ServerLevel level, FriendlySlime slime, UUID recipient, UUID receipt) {
    UUID id = slime.getUUID(), old = tokens.get(id);
    if (old == null) return true;
    if (level.noSave) return false;
    boolean wasStored = stored.contains(id);
    UUID next = UUID.randomUUID();
    tokens.put(id, next);
    stored.add(id);
    slime.getPersistentData().putUUID("ColonyToken", next);
    ItemStack previousRecovery = recovery.get(id);
    ItemStack fallback = snapshot(level, slime);
    if (recipient != null && receipt != null) {
      fallback.getOrCreateTag().putUUID("ColonyRecipient", recipient);
      fallback.getTag().putUUID("ColonyRecoveryReceipt", receipt);
    }
    recovery.put(id, fallback);
    if (flush(level)) return true;
    tokens.put(id, old);
    if (!wasStored) stored.remove(id);
    slime.getPersistentData().putUUID("ColonyToken", old);
    if (previousRecovery == null) recovery.remove(id);
    else recovery.put(id, previousRecovery);
    return false;
  }

  public net.minecraft.world.InteractionResult capture(ServerPlayer player, FriendlySlime slime) {
    int slot = player.getInventory().getFreeSlot();
    if (slot < 0) return net.minecraft.world.InteractionResult.FAIL;
    UUID receipt = UUID.randomUUID();
    if (!store(player.serverLevel(), slime, player.getUUID(), receipt))
      return net.minecraft.world.InteractionResult.FAIL;
    ItemStack item = FriendlySlimeItem.fromSlime(slime);
    player.getInventory().setItem(slot, item);
    player.getInventory().setChanged();
    String received = "ColonyRecovery_" + receipt;
    player.getPersistentData().putBoolean(received, true);
    if (!DurableWorldData.savePlayer(player)) {
      player.getInventory().setItem(slot, ItemStack.EMPTY);
      player.getPersistentData().remove(received);
      // The committed recovery snapshot remains available; the old world epoch is invalid.
      slime.discard();
      return net.minecraft.world.InteractionResult.FAIL;
    }
    acknowledgeStored(player.serverLevel(), slime.getUUID());
    CompanionRegistry.get(player.serverLevel()).mark(slime, CompanionRegistry.Location.ITEM);
    slime.dropLeash(true, true);
    slime.discard();
    player.inventoryMenu.broadcastChanges();
    return net.minecraft.world.InteractionResult.CONSUME;
  }

  public void acknowledgeStored(ServerLevel level, UUID id) {
    recovery.remove(id);
    flush(level);
  }

  public Binding pending(UUID owner) {
    return bindings.values().stream()
        .filter(
            b ->
                b.owner.equals(owner)
                    && b.state == State.BOUND
                    && !b.delivered
                    && b.output.hasTag()
                    && mayRestore(b.output.getTag().getCompound("SlimeData")))
        .findFirst()
        .orElse(null);
  }

  public boolean deliver(ServerPlayer player, Binding binding) {
    if (binding == null
        || binding.delivered
        || !binding.owner.equals(player.getUUID())
        || binding.state != State.BOUND) return false;
    String receipt = "ColonyDelivery_" + binding.id;
    if (player.getPersistentData().getBoolean(receipt)) {
      binding.delivered = true;
      if (!flush(player.serverLevel())) binding.delivered = false;
      return true;
    }
    if (!mayRestore(binding.output.getTag().getCompound("SlimeData"))) return false;
    boolean present = false;
    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
      ItemStack item = player.getInventory().getItem(slot);
      if (item.hasTag()
          && item.getTag().getCompound("SlimeData").hasUUID("UUID")
          && binding.representative.equals(item.getTag().getCompound("SlimeData").getUUID("UUID"))
          && mayRestore(item.getTag().getCompound("SlimeData"))) {
        present = true;
        break;
      }
    }
    player.getPersistentData().putBoolean(receipt, true);
    if (present
        ? !DurableWorldData.savePlayer(player)
        : !DurableWorldData.givePlayerItems(player, List.of(binding.output))) {
      player.getPersistentData().remove(receipt);
      return false;
    }
    binding.delivered = true;
    if (!flush(player.serverLevel())) binding.delivered = false;
    return true;
  }

  public boolean reserveUnbind(ServerLevel level, Binding binding, CompoundTag release) {
    if (binding.state != State.BOUND) return false;
    binding.state = State.UNBIND_RESERVED;
    binding.release = release.copy();
    if (flush(level)) return true;
    binding.state = State.BOUND;
    binding.release = new CompoundTag();
    return false;
  }

  public boolean rollbackUnbind(ServerLevel level, Binding binding) {
    binding.state = State.BOUND;
    binding.release = new CompoundTag();
    return flush(level);
  }

  public boolean commitUnbind(ServerLevel level, Binding binding, List<BagSlime> originals) {
    if (binding.state != State.UNBIND_RESERVED || originals.size() != 2) return false;
    Map<UUID, UUID> before = new HashMap<>(tokens);
    for (BagSlime original : originals) {
      UUID token = UUID.randomUUID();
      tokens.put(original.getUUID(), token);
      stored.remove(original.getUUID());
      original.getPersistentData().putUUID("ColonyToken", token);
      recovery.put(original.getUUID(), snapshot(level, original));
    }
    binding.state = State.RESTORED;
    if (flush(level)) return true;
    tokens.clear();
    tokens.putAll(before);
    binding.state = State.UNBIND_RESERVED;
    for (BagSlime original : originals) recovery.remove(original.getUUID());
    return false;
  }

  public void checkpointUnbound(ServerLevel level, Binding binding) {
    try {
      level.save(null, true, false);
      binding.release = new CompoundTag();
      flush(level);
    } catch (RuntimeException failure) {
      com.mojang.logging.LogUtils.getLogger()
          .error("Could not checkpoint unbound colony {}", binding.id, failure);
    }
  }

  public Collection<Binding> bindings() {
    return Collections.unmodifiableCollection(bindings.values());
  }

  public Map<UUID, ItemStack> recovery() {
    return Collections.unmodifiableMap(recovery);
  }

  public void diskAdmission(FriendlySlime slime) {
    if (controlled(slime.getUUID())) diskAdmissions.add(new java.lang.ref.WeakReference<>(slime));
  }

  public void storedDiskProof(ServerLevel level, ItemStack item) {
    if (!item.hasTag()) return;
    var data = item.getTag().getCompound("SlimeData");
    if (data.hasUUID("UUID") && controlled(data.getUUID("UUID")) && mayRestore(data))
      acknowledgeStored(level, data.getUUID("UUID"));
  }

  public void leaving(ServerLevel level, FriendlySlime slime) {
    UUID id = slime.getUUID();
    if (!controlled(id) || stored.contains(id) || !mayJoin(slime)) return;
    recovery.put(id, snapshot(level, slime));
    flush(level);
  }

  private boolean received(net.minecraft.server.MinecraftServer server, ItemStack item) {
    if (!item.hasTag()
        || !item.getTag().hasUUID("ColonyRecoveryReceipt")
        || !item.getTag().hasUUID("ColonyRecipient")) return false;
    UUID actor = item.getTag().getUUID("ColonyRecipient");
    String key = "ColonyRecovery_" + item.getTag().getUUID("ColonyRecoveryReceipt");
    try {
      var path =
          server
              .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
              .resolve("playerdata")
              .resolve(actor + ".dat");
      return java.nio.file.Files.isRegularFile(path)
          && NbtIo.readCompressed(path.toFile()).getCompound("ForgeData").getBoolean(key);
    } catch (java.io.IOException unreadable) {
      return false;
    }
  }

  private static ItemStack snapshot(ServerLevel level, FriendlySlime slime) {
    ItemStack item = FriendlySlimeItem.fromSlime(slime);
    item.getOrCreateTag()
        .putString("ColonyRecoveryDimension", level.dimension().location().toString());
    return item;
  }

  /** Resolve an interrupted release only after its original chunk is naturally loaded. */
  public void recoverInterrupted(net.minecraft.server.MinecraftServer server) {
    for (var reference : new ArrayList<>(diskAdmissions)) {
      var slime = reference.get();
      if (slime != null
          && slime.isAddedToWorld()
          && !slime.isRemoved()
          && slime.level() instanceof ServerLevel level
          && level.getEntity(slime.getUUID()) == slime
          && mayJoin(slime)) acknowledgeStored(level, slime.getUUID());
    }
    diskAdmissions.clear();
    for (Binding binding : bindings.values()) {
      if (binding.state != State.UNBIND_RESERVED) continue;
      var dimension =
          net.minecraft.resources.ResourceLocation.tryParse(binding.release.getString("Dimension"));
      if (dimension == null) continue;
      ServerLevel level =
          server.getLevel(
              net.minecraft.resources.ResourceKey.create(
                  net.minecraft.core.registries.Registries.DIMENSION, dimension));
      BlockPos station = NbtUtils.readBlockPos(binding.release.getCompound("Station"));
      if (level == null || !level.hasChunkAt(station)) continue;
      ItemStack original = ItemStack.of(binding.release.getCompound("ColonyItem"));
      CompoundTag previousRelease = binding.release.copy();
      binding.state = State.BOUND;
      if (!original.isEmpty()) {
        original.getOrCreateTag().putString("ColonyRecoveryDimension", dimension.toString());
        recovery.put(binding.representative, original);
      }
      binding.release = new CompoundTag();
      if (!flush(level)) {
        binding.state = State.UNBIND_RESERVED;
        binding.release = previousRelease;
        recovery.remove(binding.representative);
      }
    }
    for (var entry : new ArrayList<>(recovery.entrySet())) {
      UUID id = entry.getKey();
      ItemStack item = entry.getValue();
      if (!item.hasTag()) continue;
      if (received(server, item)) {
        recovery.remove(id);
        flush(server.overworld());
        continue;
      }
      CompoundTag nativeData = item.getTag().getCompound("SlimeData");
      boolean found = false;
      for (ServerLevel world : server.getAllLevels()) {
        var entity = world.getEntity(id);
        if (entity instanceof FriendlySlime slime && mayJoin(slime)) {
          found = true;
          break;
        }
      }
      if (found) continue;
      var dimension =
          net.minecraft.resources.ResourceLocation.tryParse(
              item.getTag().getString("ColonyRecoveryDimension"));
      if (dimension == null) continue;
      ServerLevel level =
          server.getLevel(
              net.minecraft.resources.ResourceKey.create(
                  net.minecraft.core.registries.Registries.DIMENSION, dimension));
      ListTag position = nativeData.getList("Pos", Tag.TAG_DOUBLE);
      if (level == null || position.size() != 3) continue;
      BlockPos pos =
          BlockPos.containing(position.getDouble(0), position.getDouble(1), position.getDouble(2));
      if (!level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(pos).toLong())) continue;
      // The original world epoch has no loaded entity. Move authority to a recoverable item;
      // rotating its token makes any older, unloaded record harmless on its eventual admission.
      if (!stored.contains(id)) {
        UUID old = tokens.get(id);
        CompoundTag oldNativeData = nativeData.copy();
        UUID next = UUID.randomUUID();
        tokens.put(id, next);
        stored.add(id);
        nativeData.getCompound("ForgeData").putUUID("ColonyToken", next);
        if (!flush(level)) {
          tokens.put(id, old);
          stored.remove(id);
          item.getTag().put("SlimeData", oldNativeData);
          continue;
        }
      }
    }
  }

  public boolean hasRecovery(UUID owner) {
    return recovery.entrySet().stream()
        .filter(entry -> stored.contains(entry.getKey()))
        .map(Map.Entry::getValue)
        .anyMatch(
            item ->
                item.hasTag()
                    && item.getTag().getCompound("SlimeData").hasUUID("Owner")
                    && owner.equals(item.getTag().getCompound("SlimeData").getUUID("Owner")));
  }

  public boolean recover(ServerPlayer player) {
    recoverInterrupted(player.server);
    for (var entry : new ArrayList<>(recovery.entrySet())) {
      ItemStack item = entry.getValue();
      if (!item.hasTag()) continue;
      var nativeData = item.getTag().getCompound("SlimeData");
      if (!nativeData.hasUUID("Owner")
          || !player.getUUID().equals(nativeData.getUUID("Owner"))
          || !mayRestore(nativeData)) continue;
      if (received(player.server, item)) {
        recovery.remove(entry.getKey());
        flush(player.serverLevel());
        return true;
      }
      boolean present = false;
      for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
        ItemStack held = player.getInventory().getItem(slot);
        if (held.hasTag()
            && held.getTag().getCompound("SlimeData").hasUUID("UUID")
            && entry.getKey().equals(held.getTag().getCompound("SlimeData").getUUID("UUID"))
            && mayRestore(held.getTag().getCompound("SlimeData"))) {
          present = true;
          break;
        }
      }
      ItemStack before = item.copy();
      UUID previousToken = tokens.get(entry.getKey());
      if (!present
          && (!item.getTag().hasUUID("ColonyRecoveryReceipt")
              || !item.getTag().hasUUID("ColonyRecipient")
              || !player.getUUID().equals(item.getTag().getUUID("ColonyRecipient")))) {
        UUID token = UUID.randomUUID();
        tokens.put(entry.getKey(), token);
        nativeData.getCompound("ForgeData").putUUID("ColonyToken", token);
        item.getTag().putUUID("ColonyRecoveryReceipt", UUID.randomUUID());
        item.getTag().putUUID("ColonyRecipient", player.getUUID());
        if (!flush(player.serverLevel())) {
          tokens.put(entry.getKey(), previousToken);
          recovery.put(entry.getKey(), before);
          return false;
        }
      }
      if (!item.getTag().hasUUID("ColonyRecoveryReceipt")) {
        item.getTag().putUUID("ColonyRecoveryReceipt", UUID.randomUUID());
        item.getTag().putUUID("ColonyRecipient", player.getUUID());
        if (!flush(player.serverLevel())) {
          recovery.put(entry.getKey(), before);
          return false;
        }
      }
      String receipt = "ColonyRecovery_" + item.getTag().getUUID("ColonyRecoveryReceipt");
      player.getPersistentData().putBoolean(receipt, true);
      ItemStack deliverable =
          nativeData.getFloat("Health") <= 0
              ? io.github.meistermods.slimic.block.BrokenSlimeBlock.recoveryItem(item)
              : item;
      if (present
          ? !DurableWorldData.savePlayer(player)
          : !DurableWorldData.givePlayerItems(player, List.of(deliverable))) {
        player.getPersistentData().remove(receipt);
        return false;
      }
      recovery.remove(entry.getKey());
      flush(player.serverLevel());
      return true;
    }
    return deliver(player, pending(player.getUUID()));
  }

  public static ColonyBondingData load(CompoundTag tag) {
    var result = new ColonyBondingData();
    for (Tag raw : tag.getList("Bindings", Tag.TAG_COMPOUND)) {
      CompoundTag entry = (CompoundTag) raw;
      if (!entry.hasUUID("Id")
          || !entry.hasUUID("Owner")
          || !entry.hasUUID("Representative")
          || !entry.hasUUID("First")
          || !entry.hasUUID("Second")) continue;
      Binding binding = new Binding();
      binding.id = entry.getUUID("Id");
      binding.owner = entry.getUUID("Owner");
      binding.representative = entry.getUUID("Representative");
      binding.first = entry.getUUID("First");
      binding.second = entry.getUUID("Second");
      try {
        binding.state = State.valueOf(entry.getString("State"));
      } catch (IllegalArgumentException ex) {
        continue;
      }
      binding.output = ItemStack.of(entry.getCompound("Output"));
      binding.delivered = entry.getBoolean("Delivered");
      binding.release = entry.getCompound("Release").copy();
      result.bindings.put(binding.id, binding);
    }
    for (Tag raw : tag.getList("Tokens", Tag.TAG_COMPOUND)) {
      CompoundTag entry = (CompoundTag) raw;
      if (!entry.hasUUID("Id") || !entry.hasUUID("Token")) continue;
      UUID id = entry.getUUID("Id");
      result.tokens.put(id, entry.getUUID("Token"));
      if (entry.getBoolean("Stored")) result.stored.add(id);
      if (entry.contains("Recovery", Tag.TAG_COMPOUND))
        result.recovery.put(id, ItemStack.of(entry.getCompound("Recovery")));
    }
    return result;
  }

  @Override
  public CompoundTag save(CompoundTag tag) {
    ListTag list = new ListTag();
    for (Binding binding : bindings.values()) {
      CompoundTag entry = new CompoundTag();
      entry.putUUID("Id", binding.id);
      entry.putUUID("Owner", binding.owner);
      entry.putUUID("Representative", binding.representative);
      entry.putUUID("First", binding.first);
      entry.putUUID("Second", binding.second);
      entry.putString("State", binding.state.name());
      entry.put("Output", binding.output.save(new CompoundTag()));
      entry.putBoolean("Delivered", binding.delivered);
      entry.put("Release", binding.release.copy());
      list.add(entry);
    }
    tag.put("Bindings", list);
    ListTag epochs = new ListTag();
    tokens.forEach(
        (id, token) -> {
          CompoundTag entry = new CompoundTag();
          entry.putUUID("Id", id);
          entry.putUUID("Token", token);
          entry.putBoolean("Stored", stored.contains(id));
          if (recovery.containsKey(id))
            entry.put("Recovery", recovery.get(id).save(new CompoundTag()));
          epochs.add(entry);
        });
    tag.put("Tokens", epochs);
    return tag;
  }
}
