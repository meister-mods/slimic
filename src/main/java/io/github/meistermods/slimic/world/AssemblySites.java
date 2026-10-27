package io.github.meistermods.slimic.world;

import io.github.meistermods.slimic.config.SlimicConfig;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.registry.ModItems;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** Site and encounter identities outlive altar blocks, chunks, and individual reward items. */
public final class AssemblySites extends SavedData {
  public static final String NAME = "slimic_assembly_sites";

  public enum State {
    DORMANT,
    AWAKENING,
    ACTIVE,
    DEFEATED
  }

  public enum Claim {
    DELIVERED,
    NONE,
    OUTPUT_FULL,
    STORAGE_ERROR
  }

  public static final class Participant {
    public int ticks;
    public boolean contributed;
  }

  public static final class Delivery {
    public UUID player, encounter, receipt;
    public boolean first, delivered;
  }

  public static final class Site {
    public final UUID id;
    public final BlockPos anchor;
    public String dimension = SlimicWorld.DIMENSION.location().toString();
    public CompoundTag body = new CompoundTag();
    public UUID encounter, boss;
    public State state = State.DORMANT;
    public int awakening, absent;
    public long lastClear;
    public boolean cleared, retryFree = true;
    public UUID paymentOwner, paymentId;
    public boolean paymentPeaceful;
    public ColonyCohesion cohesion = new ColonyCohesion();
    public final Map<UUID, Participant> participants = new LinkedHashMap<>();
    public final Set<UUID> firstReward = new HashSet<>();
    public final List<Delivery> deliveries = new ArrayList<>();

    private Site(UUID id, BlockPos anchor) {
      this.id = id;
      this.anchor = anchor.immutable();
    }

    public boolean active(UUID encounter, UUID boss) {
      return state == State.ACTIVE
          && Objects.equals(this.encounter, encounter)
          && Objects.equals(this.boss, boss);
    }
  }

  private final Map<UUID, Site> sites = new LinkedHashMap<>();

  public static AssemblySites get(ServerLevel level) {
    return level
        .getServer()
        .overworld()
        .getDataStorage()
        .computeIfAbsent(AssemblySites::load, AssemblySites::new, NAME);
  }

  @Override
  public void save(java.io.File file) {
    if (isDirty()) DurableWorldData.checkpoint(file, this);
  }

  public boolean flush(ServerLevel level) {
    setDirty();
    return DurableWorldData.flush(level, NAME, this);
  }

  public static UUID identity(long seed, BlockPos anchor) {
    return UUID.nameUUIDFromBytes(
        (seed + ":assembly:" + anchor.asLong()).getBytes(StandardCharsets.UTF_8));
  }

  public Site register(ServerLevel level, BlockPos anchor) {
    UUID id =
        level.dimension().equals(SlimicWorld.DIMENSION)
            ? identity(level.getSeed(), anchor)
            : UUID.nameUUIDFromBytes(
                (level.getSeed()
                        + ":assembly:"
                        + level.dimension().location()
                        + ":"
                        + anchor.asLong())
                    .getBytes(StandardCharsets.UTF_8));
    Site known = sites.get(id);
    if (known != null) return known;
    Site site = new Site(id, anchor);
    site.dimension = level.dimension().location().toString();
    sites.put(id, site);
    if (!flush(level)) {
      sites.remove(id);
      return null;
    }
    return site;
  }

  public Site find(UUID id) {
    return sites.get(id);
  }

  public Collection<Site> sites() {
    return Collections.unmodifiableCollection(sites.values());
  }

  /** Allocate an encounter before EntityJoinLevel can observe its body or fragments. */
  public boolean awaken(ServerLevel level, Site site) {
    if (site.state == State.ACTIVE || site.state == State.AWAKENING) return false;
    CompoundTag before = saveSite(site);
    site.state = State.AWAKENING;
    site.encounter = UUID.randomUUID();
    site.boss = UUID.randomUUID();
    site.awakening = 40;
    site.absent = 0;
    site.cohesion = new ColonyCohesion();
    site.participants.clear();
    site.body = new CompoundTag();
    site.retryFree = true;
    site.paymentOwner = site.paymentId = null;
    if (flush(level)) return true;
    restore(site, before);
    return false;
  }

  public boolean changeMass(ServerLevel level, Site site, Consumer<ColonyCohesion> change) {
    CompoundTag before = site.cohesion.save();
    change.accept(site.cohesion);
    if (flush(level)) return true;
    site.cohesion = ColonyCohesion.load(before);
    return false;
  }

  public boolean reset(ServerLevel level, Site site) {
    CompoundTag before = saveSite(site);
    site.state = State.DORMANT;
    site.awakening = site.absent = 0;
    site.retryFree = true;
    if (flush(level)) return true;
    restore(site, before);
    return false;
  }

  public void presence(ServerLevel level, Site site) {
    boolean present = false;
    for (ServerPlayer player : level.players()) {
      if (!eligiblePosition(player, site)) continue;
      present = true;
      Participant participant =
          site.participants.computeIfAbsent(player.getUUID(), id -> new Participant());
      participant.ticks = Math.min(72000, participant.ticks + 20);
    }
    site.absent = present ? 0 : site.absent + 20;
    setDirty();
  }

  public static boolean eligiblePosition(ServerPlayer player, Site site) {
    return player.isAlive()
        && !player.isSpectator()
        && !player.isCreative()
        && player.level().dimension().location().toString().equals(site.dimension)
        && player.blockPosition().distSqr(site.anchor) <= 48D * 48;
  }

  /** Call only after actual damage/healing/protection/command effects have succeeded. */
  public static void contribute(Entity source) {
    if (!(source.level() instanceof ServerLevel level)) return;
    UUID player =
        source instanceof ServerPlayer p
            ? p.getUUID()
            : source instanceof FriendlySlime slime
                ? slime.getOwnerUUID()
                : source instanceof io.github.meistermods.slimic.entity.CombatColonyBody body
                        && body.getColony() != null
                    ? body.getColony().getOwnerUUID()
                    : null;
    if (player == null) return;
    var data = get(level);
    for (Site site : data.sites.values())
      if (site.state == State.ACTIVE
          && site.dimension.equals(level.dimension().location().toString())
          && source.blockPosition().distSqr(site.anchor) <= 48D * 48) {
        var participant = site.participants.computeIfAbsent(player, id -> new Participant());
        participant.contributed = true;
        data.setDirty();
      }
  }

  /** One durable defeat produces all personal entitlements, before anybody receives an item. */
  public boolean defeated(ServerLevel level, Site site) {
    if (site.state != State.ACTIVE) return false;
    CompoundTag before = saveSite(site);
    for (ServerPlayer player : level.players()) {
      Participant p = site.participants.get(player.getUUID());
      if (p != null
          && p.contributed
          && p.ticks >= SlimicConfig.ASSEMBLY_PARTICIPATION.get()
          && eligiblePosition(player, site)) entitle(site, player.getUUID(), site.encounter);
    }
    site.state = State.DEFEATED;
    site.cleared = true;
    site.retryFree = false;
    site.lastClear = level.getGameTime();
    if (flush(level)) return true;
    restore(site, before);
    return false;
  }

  public boolean peacefulReward(ServerLevel level, Site site, UUID player) {
    if (site.firstReward.contains(player)) return false;
    CompoundTag before = saveSite(site);
    entitle(site, player, UUID.randomUUID());
    site.paymentOwner = site.paymentId = null;
    if (flush(level)) return true;
    restore(site, before);
    return false;
  }

  private void entitle(Site site, UUID player, UUID encounter) {
    if (site.deliveries.stream()
        .anyMatch(d -> d.player.equals(player) && d.encounter.equals(encounter))) return;
    Delivery delivery = new Delivery();
    delivery.player = player;
    delivery.encounter = encounter;
    delivery.receipt = UUID.randomUUID();
    delivery.first = site.firstReward.add(player);
    site.deliveries.add(delivery);
  }

  /** Any assembly altar can recover a pending personal reward if its original altar is broken. */
  public Claim claim(ServerPlayer player) {
    for (Site site : sites.values())
      for (Delivery delivery : site.deliveries) {
        if (!delivery.player.equals(player.getUUID()) || delivery.delivered) continue;
        String received = "AssemblyReward_" + delivery.receipt;
        if (player.getPersistentData().getBoolean(received)) {
          delivery.delivered = true;
          if (!flush(player.serverLevel())) delivery.delivered = false;
          return Claim.DELIVERED;
        }
        List<ItemStack> rewards = new ArrayList<>();
        rewards.add(
            new ItemStack(
                delivery.first ? ModItems.COLONY_HEART.get() : ModItems.COHESION_SHARD.get(),
                delivery.first ? 1 : 2));
        if (delivery.first) rewards.add(new ItemStack(ModItems.LAST_RALLY_RECORD.get()));
        List<ItemStack> missing = new ArrayList<>();
        for (int index = 0; index < rewards.size(); index++) {
          int part = index;
          boolean exists = false;
          for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item.hasTag()
                && item.getTag().hasUUID("AssemblyReceipt")
                && delivery.receipt.equals(item.getTag().getUUID("AssemblyReceipt"))
                && item.getTag().getInt("RewardPart") == part) {
              exists = true;
              break;
            }
          }
          if (!exists) {
            ItemStack item = rewards.get(index);
            item.getOrCreateTag().putUUID("AssemblyReceipt", delivery.receipt);
            item.getTag().putUUID("OriginAssembly", site.id);
            item.getTag().putUUID("AssemblyEncounter", delivery.encounter);
            item.getTag().putInt("RewardPart", index);
            missing.add(item);
          }
        }
        if (player.getInventory().items.stream().filter(ItemStack::isEmpty).count()
            < missing.size()) return Claim.OUTPUT_FULL;
        // The player's receipt survives using, moving or dropping the reward before a delayed
        // world-ledger acknowledgement. It is checkpointed in the same player write as the items.
        player.getPersistentData().putBoolean(received, true);
        if (!DurableWorldData.givePlayerItems(player, missing)) {
          player.getPersistentData().remove(received);
          return Claim.STORAGE_ERROR;
        }
        delivery.delivered = true;
        // The player's receipt was checkpointed first. A failed final checkpoint is retry-safe.
        if (!flush(player.serverLevel())) delivery.delivered = false;
        player.inventoryMenu.broadcastChanges();
        return Claim.DELIVERED;
      }
    return Claim.NONE;
  }

  private static CompoundTag saveSite(Site site) {
    CompoundTag tag = new CompoundTag();
    tag.putUUID("Id", site.id);
    tag.put("Anchor", NbtUtils.writeBlockPos(site.anchor));
    tag.putString("Dimension", site.dimension);
    tag.put("Body", site.body.copy());
    tag.putString("State", site.state.name());
    if (site.encounter != null) tag.putUUID("Encounter", site.encounter);
    if (site.boss != null) tag.putUUID("Boss", site.boss);
    tag.putInt("Awakening", site.awakening);
    tag.putInt("Absent", site.absent);
    tag.putLong("LastClear", site.lastClear);
    tag.putBoolean("Cleared", site.cleared);
    tag.putBoolean("RetryFree", site.retryFree);
    if (site.paymentOwner != null) tag.putUUID("PaymentOwner", site.paymentOwner);
    if (site.paymentId != null) tag.putUUID("PaymentId", site.paymentId);
    tag.putBoolean("PaymentPeaceful", site.paymentPeaceful);
    tag.put("Cohesion", site.cohesion.save());
    ListTag participants = new ListTag();
    site.participants.forEach(
        (id, p) -> {
          CompoundTag entry = new CompoundTag();
          entry.putUUID("Id", id);
          entry.putInt("Ticks", p.ticks);
          entry.putBoolean("Contributed", p.contributed);
          participants.add(entry);
        });
    tag.put("Participants", participants);
    ListTag first = new ListTag();
    site.firstReward.forEach(id -> first.add(NbtUtils.createUUID(id)));
    tag.put("FirstReward", first);
    ListTag deliveries = new ListTag();
    for (Delivery delivery : site.deliveries) {
      CompoundTag entry = new CompoundTag();
      entry.putUUID("Player", delivery.player);
      entry.putUUID("Encounter", delivery.encounter);
      entry.putUUID("Receipt", delivery.receipt);
      entry.putBoolean("First", delivery.first);
      entry.putBoolean("Delivered", delivery.delivered);
      deliveries.add(entry);
    }
    tag.put("Deliveries", deliveries);
    return tag;
  }

  private static void restore(Site site, CompoundTag tag) {
    site.dimension =
        tag.contains("Dimension")
            ? tag.getString("Dimension")
            : SlimicWorld.DIMENSION.location().toString();
    site.body = tag.getCompound("Body").copy();
    try {
      site.state = State.valueOf(tag.getString("State"));
    } catch (IllegalArgumentException ex) {
      site.state = State.DORMANT;
    }
    site.encounter = tag.hasUUID("Encounter") ? tag.getUUID("Encounter") : null;
    site.boss = tag.hasUUID("Boss") ? tag.getUUID("Boss") : null;
    site.awakening = Math.max(0, Math.min(40, tag.getInt("Awakening")));
    site.absent = Math.max(0, tag.getInt("Absent"));
    site.lastClear = tag.getLong("LastClear");
    site.cleared = tag.getBoolean("Cleared");
    site.retryFree = !tag.contains("RetryFree") || tag.getBoolean("RetryFree");
    site.paymentOwner = tag.hasUUID("PaymentOwner") ? tag.getUUID("PaymentOwner") : null;
    site.paymentId = tag.hasUUID("PaymentId") ? tag.getUUID("PaymentId") : null;
    site.paymentPeaceful = tag.getBoolean("PaymentPeaceful");
    site.cohesion = ColonyCohesion.load(tag.getCompound("Cohesion"));
    site.participants.clear();
    site.firstReward.clear();
    site.deliveries.clear();
    for (Tag raw : tag.getList("Participants", Tag.TAG_COMPOUND)) {
      CompoundTag entry = (CompoundTag) raw;
      if (!entry.hasUUID("Id")) continue;
      Participant p = new Participant();
      p.ticks = Math.max(0, entry.getInt("Ticks"));
      p.contributed = entry.getBoolean("Contributed");
      site.participants.put(entry.getUUID("Id"), p);
    }
    for (Tag raw : tag.getList("FirstReward", Tag.TAG_INT_ARRAY))
      try {
        site.firstReward.add(NbtUtils.loadUUID(raw));
      } catch (IllegalArgumentException ignored) {
      }
    for (Tag raw : tag.getList("Deliveries", Tag.TAG_COMPOUND)) {
      CompoundTag entry = (CompoundTag) raw;
      if (!entry.hasUUID("Player") || !entry.hasUUID("Encounter") || !entry.hasUUID("Receipt"))
        continue;
      Delivery d = new Delivery();
      d.player = entry.getUUID("Player");
      d.encounter = entry.getUUID("Encounter");
      d.receipt = entry.getUUID("Receipt");
      d.first = entry.getBoolean("First");
      d.delivered = entry.getBoolean("Delivered");
      site.deliveries.add(d);
    }
  }

  public static AssemblySites load(CompoundTag tag) {
    AssemblySites result = new AssemblySites();
    for (Tag raw : tag.getList("Sites", Tag.TAG_COMPOUND)) {
      CompoundTag entry = (CompoundTag) raw;
      if (!entry.hasUUID("Id") || !entry.contains("Anchor", Tag.TAG_COMPOUND)) continue;
      Site site = new Site(entry.getUUID("Id"), NbtUtils.readBlockPos(entry.getCompound("Anchor")));
      restore(site, entry);
      result.sites.put(site.id, site);
    }
    return result;
  }

  @Override
  public CompoundTag save(CompoundTag tag) {
    ListTag entries = new ListTag();
    sites.values().forEach(site -> entries.add(saveSite(site)));
    tag.put("Sites", entries);
    return tag;
  }
}
