package io.github.meistermods.slimic.world;

import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** World-wide progress belongs to a site, never to a copied map or replaceable block. */
public final class SanctumSites extends SavedData {
  @Override
  public void save(java.io.File file) {
    if (isDirty()) DurableWorldData.checkpoint(file, this);
  }

  public static final int COURTYARD = 1, GALLERY = 2;
  private final Map<UUID, Site> sites = new LinkedHashMap<>();

  public enum GrantResult {
    DELIVERED,
    UNAVAILABLE,
    OUTPUT_FULL,
    STORAGE_ERROR
  }

  public static final class Site {
    public final UUID id;
    public final BlockPos anchor;
    public final int rotation;
    public final Set<UUID> visitors = new HashSet<>();
    public final Set<UUID> defeated = new HashSet<>();
    public final Set<UUID> spawned = new HashSet<>();
    public final Set<UUID> recoverableEncounters = new HashSet<>();
    public final Map<UUID, Long> recoverySince = new HashMap<>();
    public int seals;
    public boolean claimed, cleared, reinforcement;
    public UUID claimant;
    public long activatedAt;
    public final Map<Integer, Delivery> deliveries = new HashMap<>();

    private Site(UUID id, BlockPos anchor, int rotation) {
      this.id = id;
      this.anchor = anchor.immutable();
      this.rotation = Math.floorMod(rotation, 4);
    }

    public BlockPos at(int x, int y, int z) {
      return switch (rotation) {
        case 1 -> anchor.offset(-z, y, x);
        case 2 -> anchor.offset(-x, y, -z);
        case 3 -> anchor.offset(z, y, -x);
        default -> anchor.offset(x, y, z);
      };
    }

    public UUID encounter(int zone, int index) {
      return UUID.nameUUIDFromBytes(
          (id + ":" + zone + ":" + index).getBytes(StandardCharsets.UTF_8));
    }
  }

  public static final class Delivery {
    public final UUID owner, receipt;
    public final List<ItemStack> items;
    public boolean delivered;

    private Delivery(UUID owner, UUID receipt, List<ItemStack> items) {
      this.owner = owner;
      this.receipt = receipt;
      this.items = items.stream().map(ItemStack::copy).toList();
    }
  }

  public boolean grant(ServerPlayer player, Site site, int zone, List<ItemStack> rewards) {
    return grantResult(player, site, zone, rewards) == GrantResult.DELIVERED;
  }

  public GrantResult grantResult(
      ServerPlayer player, Site site, int zone, List<ItemStack> rewards) {
    var delivery = site.deliveries.get(zone);
    if (delivery == null) {
      if (zone == 4 && site.claimed) return GrantResult.UNAVAILABLE;
      if (player.getInventory().items.stream().filter(ItemStack::isEmpty).count() < rewards.size())
        return GrantResult.OUTPUT_FULL;
      UUID receipt = UUID.randomUUID();
      var tagged = rewards.stream().map(ItemStack::copy).toList();
      for (int i = 0; i < tagged.size(); i++) {
        tagged.get(i).getOrCreateTag().putUUID("SanctumReward", receipt);
        tagged.get(i).getTag().putInt("RewardPart", i);
        tagged.get(i).getTag().putUUID("OriginSanctum", site.id);
      }
      delivery = new Delivery(player.getUUID(), receipt, tagged);
      int previousSeals = site.seals;
      boolean previousClaimed = site.claimed, previousCleared = site.cleared;
      UUID previousClaimant = site.claimant;
      site.deliveries.put(zone, delivery);
      if (zone == 1) site.seals |= COURTYARD;
      if (zone == 2) site.seals |= GALLERY;
      if (zone == 4) {
        site.claimed = true;
        site.cleared = true;
        site.claimant = player.getUUID();
      }
      setDirty();
      if (!DurableWorldData.flush(player.serverLevel(), "slimic_sanctum_sites", this)) {
        site.deliveries.remove(zone);
        site.seals = previousSeals;
        site.claimed = previousClaimed;
        site.cleared = previousCleared;
        site.claimant = previousClaimant;
        return GrantResult.STORAGE_ERROR;
      }
    }
    if (!delivery.owner.equals(player.getUUID()) || delivery.delivered)
      return GrantResult.UNAVAILABLE;
    String received = "SanctumReward_" + delivery.receipt;
    if (player.getPersistentData().getBoolean(received)) {
      delivery.delivered = true;
      setDirty();
      DurableWorldData.flush(player.serverLevel(), "slimic_sanctum_sites", this);
      return GrantResult.DELIVERED;
    }
    var missing = new ArrayList<ItemStack>();
    for (int i = 0; i < delivery.items.size(); i++) {
      boolean present = false;
      for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
        var item = player.getInventory().getItem(slot);
        if (item.hasTag()
            && item.getTag().hasUUID("SanctumReward")
            && delivery.receipt.equals(item.getTag().getUUID("SanctumReward"))
            && item.getTag().getInt("RewardPart") == i) {
          present = true;
          break;
        }
      }
      if (!present) missing.add(delivery.items.get(i).copy());
    }
    long free = player.getInventory().items.stream().filter(ItemStack::isEmpty).count();
    if (free < missing.size()) return GrantResult.OUTPUT_FULL;
    // Save the receipt with the items so using a reward cannot erase proof of delivery when
    // the final world acknowledgement is interrupted.
    player.getPersistentData().putBoolean(received, true);
    if (!DurableWorldData.givePlayerItems(player, missing)) {
      player.getPersistentData().remove(received);
      return GrantResult.STORAGE_ERROR;
    }
    delivery.delivered = true;
    setDirty();
    DurableWorldData.flush(player.serverLevel(), "slimic_sanctum_sites", this);
    player.inventoryMenu.broadcastChanges();
    return GrantResult.DELIVERED;
  }

  public static SanctumSites get(ServerLevel level) {
    return level
        .getServer()
        .overworld()
        .getDataStorage()
        .computeIfAbsent(SanctumSites::load, SanctumSites::new, "slimic_sanctum_sites");
  }

  public static UUID identity(long seed, BlockPos anchor) {
    return UUID.nameUUIDFromBytes(
        (seed + ":fallen_sanctum:" + anchor.asLong()).getBytes(StandardCharsets.UTF_8));
  }

  public Site register(ServerLevel level, BlockPos anchor, int rotation) {
    UUID id = identity(level.getSeed(), anchor);
    Site site = sites.get(id);
    if (site == null) {
      site = new Site(id, anchor, rotation);
      sites.put(id, site);
      setDirty();
      DurableWorldData.flush(level, "slimic_sanctum_sites", this);
    }
    return site;
  }

  public Site find(UUID id) {
    return sites.get(id);
  }

  public Site near(BlockPos pos, int range) {
    return sites.values().stream()
        .filter(s -> s.anchor.distSqr(pos) <= (double) range * range)
        .min(Comparator.comparingDouble(s -> s.anchor.distSqr(pos)))
        .orElse(null);
  }

  public void visit(Site site, UUID player) {
    if (site.visitors.add(player)) setDirty();
  }

  public void defeated(ServerLevel level, UUID siteId, UUID encounter) {
    Site site = sites.get(siteId);
    if (site != null && site.defeated.add(encounter)) {
      site.spawned.add(encounter);
      setDirty();
      DurableWorldData.flush(level, "slimic_sanctum_sites", this);
    }
  }

  public static SanctumSites load(CompoundTag tag) {
    var result = new SanctumSites();
    for (Tag raw : tag.getList("Sites", Tag.TAG_COMPOUND)) {
      CompoundTag data = (CompoundTag) raw;
      if (!data.hasUUID("Id") || !data.contains("Anchor", Tag.TAG_COMPOUND)) continue;
      var site =
          new Site(
              data.getUUID("Id"),
              NbtUtils.readBlockPos(data.getCompound("Anchor")),
              data.getInt("Rotation"));
      site.seals = data.getInt("Seals") & 3;
      site.claimed = data.getBoolean("Claimed");
      site.cleared = data.getBoolean("Cleared");
      site.reinforcement = data.getBoolean("Reinforcement");
      site.activatedAt = data.getLong("ActivatedAt");
      if (data.hasUUID("Claimant")) site.claimant = data.getUUID("Claimant");
      readIds(data, "Visitors", site.visitors);
      readIds(data, "Spawned", site.spawned);
      readIds(data, "Defeated", site.defeated);
      site.recoverableEncounters.addAll(site.spawned);
      site.recoverableEncounters.removeAll(site.defeated);
      for (var rawDelivery : data.getList("Deliveries", Tag.TAG_COMPOUND)) {
        var d = (CompoundTag) rawDelivery;
        if (!d.hasUUID("Owner") || !d.hasUUID("Receipt")) continue;
        var delivery =
            new Delivery(
                d.getUUID("Owner"),
                d.getUUID("Receipt"),
                d.getList("Items", Tag.TAG_COMPOUND).stream()
                    .map(rawItem -> ItemStack.of((CompoundTag) rawItem))
                    .toList());
        delivery.delivered = d.getBoolean("Delivered");
        site.deliveries.put(d.getInt("Zone"), delivery);
      }
      result.sites.put(site.id, site);
    }
    return result;
  }

  private static void readIds(CompoundTag data, String key, Set<UUID> target) {
    for (Tag raw : data.getList(key, Tag.TAG_INT_ARRAY)) {
      try {
        target.add(NbtUtils.loadUUID(raw));
      } catch (IllegalArgumentException ignored) {
      }
    }
  }

  private static ListTag ids(Set<UUID> values) {
    var result = new ListTag();
    values.forEach(id -> result.add(NbtUtils.createUUID(id)));
    return result;
  }

  @Override
  public CompoundTag save(CompoundTag tag) {
    var list = new ListTag();
    for (Site site : sites.values()) {
      var data = new CompoundTag();
      data.putUUID("Id", site.id);
      data.put("Anchor", NbtUtils.writeBlockPos(site.anchor));
      data.putInt("Rotation", site.rotation);
      data.putInt("Seals", site.seals);
      data.putBoolean("Claimed", site.claimed);
      data.putBoolean("Cleared", site.cleared);
      data.putBoolean("Reinforcement", site.reinforcement);
      data.putLong("ActivatedAt", site.activatedAt);
      if (site.claimant != null) data.putUUID("Claimant", site.claimant);
      data.put("Visitors", ids(site.visitors));
      data.put("Spawned", ids(site.spawned));
      data.put("Defeated", ids(site.defeated));
      list.add(data);
      var deliveries = new ListTag();
      site.deliveries.forEach(
          (zone, delivery) -> {
            var d = new CompoundTag();
            d.putInt("Zone", zone);
            d.putUUID("Owner", delivery.owner);
            d.putUUID("Receipt", delivery.receipt);
            d.putBoolean("Delivered", delivery.delivered);
            var items = new ListTag();
            delivery.items.forEach(item -> items.add(item.save(new CompoundTag())));
            d.put("Items", items);
            deliveries.add(d);
          });
      data.put("Deliveries", deliveries);
    }
    tag.putInt("Schema", 1);
    tag.put("Sites", list);
    return tag;
  }
}
