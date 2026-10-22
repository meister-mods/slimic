package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.registry.ModEntities;
import java.text.Normalizer;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent owner relationships and last-known companion observations. */
public final class CompanionRegistry extends SavedData {
  public enum Location {
    WORLD,
    ITEM,
    BROKEN,
    REMOVED
  }

  public record Entry(
      UUID id,
      UUID owner,
      String type,
      String name,
      String dimension,
      BlockPos position,
      float health,
      float maximumHealth,
      String order,
      String state,
      String reason,
      Location location,
      String tag,
      long seen,
      boolean pinned) {
    public Entry withTag(String value) {
      return new Entry(
          id,
          owner,
          type,
          name,
          dimension,
          position,
          health,
          maximumHealth,
          order,
          state,
          reason,
          location,
          value,
          seen,
          pinned);
    }

    public Entry withLocation(Location value) {
      return new Entry(
          id,
          owner,
          type,
          name,
          dimension,
          position,
          health,
          maximumHealth,
          order,
          state,
          reason,
          value,
          tag,
          seen,
          value == Location.WORLD && pinned);
    }
  }

  // Reservations only span a spawn transaction and never survive a restart.
  private record PendingCommand(UUID identity, Entry previous) {}

  private final Map<UUID, PendingCommand> pendingCommands = new HashMap<>();

  private long relationshipRevision;

  public void retire(UUID id) {
    var entry = companions.get(id);
    if (entry != null) {
      companions.put(id, entry.withLocation(Location.REMOVED));
      setDirty();
    }
  }

  public long relationshipRevision() {
    return relationshipRevision;
  }

  private final Map<UUID, Entry> companions = new HashMap<>();
  private final Map<UUID, Set<UUID>> coOwners = new HashMap<>(), enemies = new HashMap<>();

  public static CompanionRegistry get(ServerLevel level) {
    var overworld = level.getServer().overworld();
    return (overworld == null ? level : overworld)
        .getDataStorage()
        .computeIfAbsent(CompanionRegistry::load, CompanionRegistry::new, "slimic_companions");
  }

  public static CompanionRegistry get(MinecraftServer server) {
    return server
        .overworld()
        .getDataStorage()
        .computeIfAbsent(CompanionRegistry::load, CompanionRegistry::new, "slimic_companions");
  }

  public static String normalizeTag(String value) {
    if (value == null) return "";
    var text = Normalizer.normalize(value.strip(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    if (text.length() > 24 || text.codePoints().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Invalid companion tag");
    return text;
  }

  public Entry find(UUID id) {
    return companions.get(id);
  }

  public List<Entry> owned(UUID owner) {
    return companions.values().stream()
        .filter(e -> e.owner.equals(owner))
        .sorted(Comparator.comparing(Entry::type).thenComparing(e -> e.id.toString()))
        .toList();
  }

  public Set<UUID> coOwners(UUID owner) {
    return Set.copyOf(coOwners.getOrDefault(owner, Set.of()));
  }

  public Set<UUID> enemies(UUID owner) {
    return Set.copyOf(enemies.getOrDefault(owner, Set.of()));
  }

  public boolean owns(UUID owner, UUID player) {
    return owner != null
        && player != null
        && (owner.equals(player) || coOwners.getOrDefault(owner, Set.of()).contains(player));
  }

  public boolean shares(UUID first, UUID second) {
    if (first == null || second == null) return false;
    if (owns(first, second) || owns(second, first)) return true;
    for (var id : coOwners.getOrDefault(first, Set.of())) if (owns(second, id)) return true;
    return false;
  }

  public boolean enemy(UUID owner, UUID player) {
    return !owns(owner, player) && enemies.getOrDefault(owner, Set.of()).contains(player);
  }

  /** Shared-access edges form an undirected group only for the command population limit. */
  private Set<UUID> commandGroup(UUID owner) {
    var adjacency = new HashMap<UUID, Set<UUID>>();
    coOwners.forEach(
        (primary, shared) -> {
          for (var other : shared) {
            adjacency.computeIfAbsent(primary, ignored -> new HashSet<>()).add(other);
            adjacency.computeIfAbsent(other, ignored -> new HashSet<>()).add(primary);
          }
        });
    var group = new HashSet<UUID>();
    var queue = new ArrayDeque<UUID>();
    group.add(owner);
    queue.add(owner);
    while (!queue.isEmpty())
      for (var other : adjacency.getOrDefault(queue.remove(), Set.of()))
        if (group.add(other)) queue.add(other);
    return group;
  }

  private static boolean worldCommand(Entry entry) {
    return entry != null
        && entry.location == Location.WORLD
        && entry.health > 0
        && entry.type.equals(ModEntities.COMMAND_SLIME.getId().toString());
  }

  private long commandCount(Set<UUID> group, UUID exceptId, UUID exceptReservationOwner) {
    long count =
        companions.values().stream()
            .filter(e -> group.contains(e.owner) && !e.id.equals(exceptId) && worldCommand(e))
            .count();
    for (var pending : pendingCommands.entrySet())
      if (group.contains(pending.getKey())
          && !pending.getKey().equals(exceptReservationOwner)
          && (pending.getValue() == null
              || !worldCommand(companions.get(pending.getValue().identity())))) count++;
    return count;
  }

  /** Reserves capacity before spawn callbacks can recursively release another command. */
  public boolean reserveCommand(UUID owner) {
    if (owner == null || commandCount(commandGroup(owner), null, null) > 0) return false;
    pendingCommands.put(owner, null);
    return true;
  }

  public boolean prepareCommandSpawn(CommandSlime slime) {
    var owner = slime.getOwnerUUID();
    if (owner == null || !pendingCommands.containsKey(owner)) return false;
    pendingCommands.put(owner, new PendingCommand(slime.getUUID(), find(slime.getUUID())));
    return commandCount(commandGroup(owner), slime.getUUID(), owner) == 0;
  }

  public void releaseCommandReservation(UUID owner, boolean spawned) {
    var pending = pendingCommands.remove(owner);
    if (!spawned && pending != null) {
      // Join listeners may observe the candidate before another listener cancels insertion.
      if (pending.previous() == null) companions.remove(pending.identity());
      else companions.put(pending.identity(), pending.previous());
      setDirty();
    }
  }

  public boolean commandMergeConflict(UUID owner, UUID target) {
    if (owner == null || target == null) return false;
    var group = commandGroup(owner);
    if (group.contains(target)) return false;
    group.addAll(commandGroup(target));
    return commandCount(group, null, null) > 1;
  }

  public boolean setRelation(UUID owner, UUID target, boolean shared, boolean enabled) {
    if (owner == null || target == null || owner.equals(target)) return false;
    var map = shared ? coOwners : enemies;
    if (enabled && !shared && owns(owner, target)) return false;
    if (enabled && shared && commandMergeConflict(owner, target)) return false;
    var values = map.computeIfAbsent(owner, ignored -> new HashSet<>());
    if (enabled && values.size() >= (shared ? 64 : 256) && !values.contains(target)) return false;
    boolean changed = enabled ? values.add(target) : values.remove(target);
    if (enabled && shared) {
      var hostile = enemies.get(owner);
      if (hostile != null) changed |= hostile.remove(target);
    }
    if (changed) {
      relationshipRevision++;
      setDirty();
    }
    return changed;
  }

  public void observe(FriendlySlime slime) {
    if (!(slime.level() instanceof ServerLevel level)
        || slime.getOwnerUUID() == null
        || slime.isPendingSpawn()) return;
    var previous = companions.get(slime.getUUID());
    var status = slime.getWorkStatus();
    String tag =
        previous != null && previous.owner.equals(slime.getOwnerUUID())
            ? previous.tag
            : slime.getLocalCompanionTag();
    String name = slime.hasCustomName() ? slime.getCustomName().getString() : "";
    if (name.length() > 64) name = name.substring(0, 64);
    var entry =
        new Entry(
            slime.getUUID(),
            slime.getOwnerUUID(),
            net.minecraft.world.entity.EntityType.getKey(slime.getType()).toString(),
            name,
            level.dimension().location().toString(),
            slime.blockPosition().immutable(),
            slime.getHealth(),
            slime.getMaxHealth(),
            status.order().name(),
            status.state().name(),
            status.reason().name(),
            Location.WORLD,
            tag,
            level.getGameTime(),
            slime instanceof EnderSlime ender && ender.isPinned());
    if (!entry.equals(previous)) {
      companions.put(entry.id, entry);
      setDirty();
    }
  }

  public void observeStored(
      net.minecraft.world.item.ItemStack item, ServerLevel level, BlockPos pos) {
    if (!(item.getItem() instanceof io.github.meistermods.slimic.item.FriendlySlimeItem)
        || !item.hasTag()) return;
    var data = SlimeData.migrate(item.getTag().getCompound("SlimeData"));
    if (!data.hasUUID("UUID")
        || !data.hasUUID("Owner")
        || !data.contains("Health", Tag.TAG_ANY_NUMERIC)
        || !SlimeData.canLoad(data)
        || !Float.isFinite(data.getFloat("Health"))) return;
    var previous = find(data.getUUID("UUID"));
    if (previous != null && previous.location == Location.WORLD) return;
    var type = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem());
    if (type == null
        || !net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.containsKey(type)) return;
    String label = item.hasCustomHoverName() ? item.getHoverName().getString() : "";
    if (label.length() > 64) label = label.substring(0, 64);
    String group;
    try {
      group = previous == null ? normalizeTag(data.getString("CompanionTag")) : previous.tag;
    } catch (IllegalArgumentException invalid) {
      group = "";
    }
    float health = finiteHealth(data.getFloat("Health")), maximum = Math.max(16, health);
    for (var element : data.getList("Attributes", Tag.TAG_COMPOUND)) {
      var attribute = (CompoundTag) element;
      if (attribute.getString("Name").equals("minecraft:generic.max_health")) {
        var instance =
            new net.minecraft.world.entity.ai.attributes.AttributeInstance(
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, ignored -> {});
        instance.load(attribute);
        maximum =
            finiteHealth(
                CreativeStats.asFloat(
                    data.getCompound("TunedStats").contains("health")
                        ? CreativeStats.attributeValue(instance)
                        : instance.getValue()));
      }
    }
    health = Math.min(health, maximum);
    var entry =
        new Entry(
            data.getUUID("UUID"),
            data.getUUID("Owner"),
            type.toString(),
            label,
            level.dimension().location().toString(),
            pos.immutable(),
            health,
            maximum,
            "WAIT",
            "IDLE",
            "NONE",
            health <= 0 ? Location.BROKEN : Location.ITEM,
            group,
            level.getGameTime(),
            false);
    if (!entry.equals(previous)) {
      companions.put(entry.id, entry);
      setDirty();
    }
  }

  public void mark(FriendlySlime slime, Location location) {
    observe(slime);
    var entry = find(slime.getUUID());
    if (entry != null) {
      companions.put(entry.id, entry.withLocation(location));
      setDirty();
    }
  }

  public boolean setTag(UUID owner, UUID id, String value) {
    var entry = find(id);
    if (entry == null || !entry.owner.equals(owner)) return false;
    String tag;
    try {
      tag = normalizeTag(value);
    } catch (IllegalArgumentException invalid) {
      return false;
    }
    if (!tag.equals(entry.tag)) {
      companions.put(id, entry.withTag(tag));
      setDirty();
    }
    return true;
  }

  public long pinnedCount(UUID owner, UUID except) {
    return companions.values().stream()
        .filter(
            e ->
                e.owner.equals(owner)
                    && !e.id.equals(except)
                    && e.location == Location.WORLD
                    && e.pinned)
        .count();
  }

  public static CompanionRegistry load(CompoundTag tag) {
    var result = new CompanionRegistry();
    var entries = tag.getList("Companions", Tag.TAG_COMPOUND);
    for (int i = 0; i < Math.min(entries.size(), 100000); i++) {
      var e = entries.getCompound(i);
      if (!e.hasUUID("Id")
          || !e.hasUUID("Owner")
          || ResourceLocation.tryParse(e.getString("Type")) == null
          || ResourceLocation.tryParse(e.getString("Dimension")) == null) continue;
      try {
        var entry =
            new Entry(
                e.getUUID("Id"),
                e.getUUID("Owner"),
                e.getString("Type"),
                e.getString("Name").substring(0, Math.min(64, e.getString("Name").length())),
                e.getString("Dimension"),
                BlockPos.of(e.getLong("Position")),
                finiteHealth(e.getFloat("Health")),
                finiteHealth(e.getFloat("MaximumHealth")),
                WorkStatus.Order.valueOf(e.getString("Order")).name(),
                WorkStatus.State.valueOf(e.getString("State")).name(),
                WorkStatus.Reason.valueOf(e.getString("Reason")).name(),
                Location.valueOf(e.getString("Location")),
                normalizeTag(e.getString("Tag")),
                e.getLong("Seen"),
                e.getBoolean("Pinned"));
        result.companions.put(entry.id, entry);
      } catch (IllegalArgumentException ignored) {
      }
    }
    loadRelations(tag.getList("CoOwners", Tag.TAG_COMPOUND), result.coOwners, 64);
    loadRelations(tag.getList("Enemies", Tag.TAG_COMPOUND), result.enemies, 256);
    return result;
  }

  private static float finiteHealth(float value) {
    return Float.isFinite(value) ? Math.max(0, value) : 0;
  }

  private static void loadRelations(ListTag list, Map<UUID, Set<UUID>> target, int limit) {
    for (int i = 0; i < Math.min(list.size(), 10000); i++) {
      var tag = list.getCompound(i);
      if (!tag.hasUUID("Owner")) continue;
      var values = new HashSet<UUID>();
      for (var key : tag.getCompound("Players").getAllKeys()) {
        try {
          if (values.size() < limit) values.add(UUID.fromString(key));
        } catch (IllegalArgumentException ignored) {
        }
      }
      values.remove(tag.getUUID("Owner"));
      target.put(tag.getUUID("Owner"), values);
    }
  }

  private static ListTag saveRelations(Map<UUID, Set<UUID>> map) {
    var list = new ListTag();
    map.forEach(
        (owner, players) -> {
          var tag = new CompoundTag();
          tag.putUUID("Owner", owner);
          var values = new CompoundTag();
          players.forEach(id -> values.putBoolean(id.toString(), true));
          tag.put("Players", values);
          list.add(tag);
        });
    return list;
  }

  @Override
  public CompoundTag save(CompoundTag tag) {
    var list = new ListTag();
    for (var e : companions.values()) {
      var t = new CompoundTag();
      t.putUUID("Id", e.id);
      t.putUUID("Owner", e.owner);
      t.putString("Type", e.type);
      t.putString("Name", e.name);
      t.putString("Dimension", e.dimension);
      t.putLong("Position", e.position.asLong());
      t.putFloat("Health", e.health);
      t.putFloat("MaximumHealth", e.maximumHealth);
      t.putString("Order", e.order);
      t.putString("State", e.state);
      t.putString("Reason", e.reason);
      t.putString("Location", e.location.name());
      t.putString("Tag", e.tag);
      t.putLong("Seen", e.seen);
      t.putBoolean("Pinned", e.pinned);
      list.add(t);
    }
    tag.put("Companions", list);
    tag.put("CoOwners", saveRelations(coOwners));
    tag.put("Enemies", saveRelations(enemies));
    return tag;
  }
}
