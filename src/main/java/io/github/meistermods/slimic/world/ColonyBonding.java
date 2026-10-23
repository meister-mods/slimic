package io.github.meistermods.slimic.world;

import io.github.meistermods.slimic.block.ColonyBondingStationBlockEntity;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Two stored originals become one representative; unbinding restores those exact identities. */
public final class ColonyBonding {
  public enum Problem {
    READY,
    MISSING,
    UNSUPPORTED,
    OWNER,
    INVALID,
    OCCUPIED,
    ATTACHMENTS,
    CAPACITY,
    SPACE,
    CANCELLED,
    STORAGE,
    OUTPUT_FULL,
    COMPLETE,
    READY_UNBIND,
    EFFECTS,
    RANGE
  }

  public record Check(Problem problem, FriendlySlime representative) {}

  private ColonyBonding() {}

  private static Check failed(Problem problem) {
    return new Check(problem, null);
  }

  public static boolean supported(ItemStack item) {
    return item.is(ModItems.TRANSPORT_SLIME.get()) || item.is(ModItems.COMBAT_SLIME.get());
  }

  public static boolean bound(ItemStack item) {
    return item.is(ModItems.BOUND_TRANSPORT_SLIME.get())
        || item.is(ModItems.BOUND_COMBAT_SLIME.get());
  }

  public static Check check(ServerPlayer player, List<ItemStack> inputs) {
    if (inputs.size() != 2 || inputs.get(0).isEmpty()) return failed(Problem.MISSING);
    ServerLevel level = player.serverLevel();
    var ledger = ColonyBondingData.get(level);
    ItemStack first = inputs.get(0), second = inputs.get(1);
    if (bound(first) && second.isEmpty()) {
      if (!validStored(first, player)
          || !ledger.mayRestore(first.getTag().getCompound("SlimeData")))
        return failed(Problem.INVALID);
      var payload = first.getTag().getCompound("SlimeData");
      if (!payload.getCompound("ForgeCaps").isEmpty()
          || !first.save(new CompoundTag()).getCompound("ForgeCaps").isEmpty()
          || payload.getCompound("ForgeData").getAllKeys().stream()
              .anyMatch(key -> !key.equals("ColonyToken") && !key.equals("ColonyBinding")))
        return failed(Problem.ATTACHMENTS);
      var type =
          first.is(ModItems.BOUND_TRANSPORT_SLIME.get())
              ? ModEntities.BOUND_TRANSPORT_SLIME.get()
              : ModEntities.BOUND_COMBAT_SLIME.get();
      FriendlySlime representative = type.create(level);
      if (representative == null) return failed(Problem.INVALID);
      representative.load(first.getTag().getCompound("SlimeData").copy());
      if (!(representative instanceof BoundColony colony)
          || !colony.members().ready()
          || !player.getUUID().equals(representative.getOwnerUUID()))
        return failed(Problem.INVALID);
      if (loaded(level, representative.getUUID())) return failed(Problem.OCCUPIED);
      return new Check(Problem.READY_UNBIND, representative);
    }
    if (second.isEmpty()) return failed(Problem.MISSING);
    if (!supported(first) || !ItemStack.isSameItem(first, second))
      return failed(Problem.UNSUPPORTED);
    ListTag members = new ListTag();
    Set<UUID> identities = new HashSet<>();
    long capacity = 0;
    double maximumHealth = 0, maximumEnergy = 0;
    for (ItemStack item : inputs) {
      if (!validStored(item, player)) {
        boolean otherOwner =
            item.hasTag()
                && item.getTag().getCompound("SlimeData").hasUUID("Owner")
                && !player
                    .getUUID()
                    .equals(item.getTag().getCompound("SlimeData").getUUID("Owner"));
        return failed(otherOwner ? Problem.OWNER : Problem.INVALID);
      }
      CompoundTag data = item.getTag().getCompound("SlimeData").copy();
      UUID id = data.getUUID("UUID");
      if (!identities.add(id)
          || !ledger.mayRestore(data)
          || !RoyalSynthesisData.get(level).mayRestore(data)
          || loaded(level, id)) return failed(Problem.OCCUPIED);
      var entry = CompanionRegistry.get(level).find(id);
      if (entry != null
          && (entry.location() == CompanionRegistry.Location.WORLD
              || entry.location() == CompanionRegistry.Location.BOUND))
        return failed(Problem.OCCUPIED);
      if (!data.getCompound("ForgeCaps").isEmpty()
          || !item.save(new CompoundTag()).getCompound("ForgeCaps").isEmpty()
          || data.contains("Passengers")
          || data.contains("Leash")) return failed(Problem.ATTACHMENTS);
      var type =
          item.is(ModItems.TRANSPORT_SLIME.get())
              ? ModEntities.TRANSPORT_SLIME.get()
              : ModEntities.COMBAT_SLIME.get();
      data.putString("id", EntityType.getKey(type).toString());
      var created = EntityType.loadEntityRecursive(data, level, e -> e);
      if (!(created instanceof BagSlime member) || member.getType() != type)
        return failed(Problem.INVALID);
      if (!member.getActiveEffects().isEmpty()
          || member.getAbsorptionAmount() > 0
          || member.isOnFire()
          || member.getTicksFrozen() > 0
          || member.getAirSupply() < member.getMaxAirSupply()) return failed(Problem.EFFECTS);
      maximumHealth += member.getMaxHealth();
      maximumEnergy += member.getMaxStamina();
      if (maximumHealth > Float.MAX_VALUE || maximumEnergy > Float.MAX_VALUE)
        return failed(Problem.RANGE);
      capacity += member.getBag().storageSlots();
      if (capacity > Integer.MAX_VALUE) return failed(Problem.CAPACITY);
      members.add(data);
    }
    var type =
        first.is(ModItems.TRANSPORT_SLIME.get())
            ? ModEntities.BOUND_TRANSPORT_SLIME.get()
            : ModEntities.BOUND_COMBAT_SLIME.get();
    FriendlySlime representative = type.create(level);
    if (!(representative instanceof BoundColony colony)) return failed(Problem.INVALID);
    representative.tame(player);
    representative.setWaiting(true);
    CompoundTag records = new CompoundTag();
    records.put("Members", members);
    colony.members().load(records);
    if (!colony.members().ready()) return failed(Problem.INVALID);
    if (representative instanceof CombatSlime combat
        && colony.members().records().get(0) instanceof CombatSlime original)
      for (CombatTarget target : CombatTarget.values())
        combat.setAllowed(target, original.allows(target));
    representative.setDyeColorId(colony.members().records().get(0).getDyeColorId());
    CompoundTag representativeData = representative.saveWithoutId(new CompoundTag());
    representativeData.putString(
        "CompanionTag", colony.members().records().get(0).getCompanionTag());
    representative.load(representativeData);
    return new Check(Problem.READY, representative);
  }

  private static boolean validStored(ItemStack item, ServerPlayer player) {
    if (item.getCount() != 1
        || !item.hasTag()
        || !item.getTag().contains("SlimeData", Tag.TAG_COMPOUND)) return false;
    CompoundTag data = item.getTag().getCompound("SlimeData");
    if (!SlimeData.canLoad(data)
        || !data.hasUUID("UUID")
        || !data.hasUUID("Owner")
        || !player.getUUID().equals(data.getUUID("Owner"))
        || !Float.isFinite(data.getFloat("Health"))
        || data.getFloat("Health") <= 0
        || !Float.isFinite(data.getFloat("Stamina"))
        || data.getFloat("Stamina") < 0) return false;
    for (SlimeStat stat : SlimeStat.values()) {
      int level = data.getCompound("SlimeStats").getInt(stat.key());
      if (level < 0 || level > stat.maxLevel()) return false;
    }
    return true;
  }

  private static boolean loaded(ServerLevel level, UUID id) {
    for (ServerLevel world : level.getServer().getAllLevels())
      if (world.getEntity(id) != null) return true;
    return false;
  }

  public static Problem execute(
      ServerPlayer player, ColonyBondingStationBlockEntity station, boolean unbind) {
    if (player.serverLevel().noSave) return Problem.STORAGE;
    if (station.busy || !station.canUse(player)) return Problem.CANCELLED;
    station.reconcile();
    var inputs = station.contents();
    var check = check(player, inputs);
    if (unbind ? check.problem != Problem.READY_UNBIND : check.problem != Problem.READY)
      return check.problem;
    station.busy = true;
    try {
      if (unbind) return unbind(player, station, check.representative, inputs);
      if (player.getInventory().getFreeSlot() < 0) return Problem.OUTPUT_FULL;
      var binding = new ColonyBondingData.Binding();
      binding.id = UUID.randomUUID();
      binding.owner = player.getUUID();
      binding.representative = check.representative.getUUID();
      binding.first = inputs.get(0).getTag().getCompound("SlimeData").getUUID("UUID");
      binding.second = inputs.get(1).getTag().getCompound("SlimeData").getUUID("UUID");
      var ledger = ColonyBondingData.get(player.serverLevel());
      if (!station.matches(inputs)
          || !station.canUse(player)
          || !ledger.commit(player.serverLevel(), binding, check.representative))
        return Problem.STORAGE;
      station.clearCommitted();
      for (BagSlime member : ((BoundColony) check.representative).members().records()) {
        var registry = CompanionRegistry.get(player.serverLevel());
        registry.observe(member);
        registry.mark(member, CompanionRegistry.Location.BOUND);
      }
      // If a chunk checkpoint fails, the durable mailbox still owns the sole valid output.
      try {
        player.serverLevel().save(null, true, false);
      } catch (RuntimeException failure) {
        com.mojang.logging.LogUtils.getLogger()
            .error("Could not checkpoint colony station {}", station.getBlockPos(), failure);
      }
      return ledger.deliver(player, binding) ? Problem.COMPLETE : Problem.STORAGE;
    } finally {
      station.busy = false;
    }
  }

  private static Problem unbind(
      ServerPlayer player,
      ColonyBondingStationBlockEntity station,
      FriendlySlime representative,
      List<ItemStack> inputs) {
    ServerLevel level = player.serverLevel();
    var ledger = ColonyBondingData.get(level);
    var binding = ledger.binding(representative.getUUID());
    if (binding == null
        || !binding.owner.equals(player.getUUID())
        || !(representative instanceof BoundColony colony)) return Problem.INVALID;
    colony.members().prepareUnbind();
    List<BagSlime> originals = colony.members().records();
    List<Vec3> landings = new ArrayList<>();
    for (BagSlime member : originals) {
      Vec3 landing = landing(level, member, station.getBlockPos(), landings);
      if (landing == null) return Problem.SPACE;
      landings.add(landing);
    }
    CompoundTag release = new CompoundTag();
    release.putString("Dimension", level.dimension().location().toString());
    release.put("Station", NbtUtils.writeBlockPos(station.getBlockPos()));
    release.put("ColonyItem", inputs.get(0).save(new CompoundTag()));
    ListTag snapshots = new ListTag();
    for (int i = 0; i < 2; i++) {
      BagSlime member = originals.get(i);
      Vec3 pos = landings.get(i);
      member.moveTo(pos.x, pos.y, pos.z, 0, 0);
      member.setWaiting(true);
      CompoundTag snapshot = new CompoundTag();
      member.save(snapshot);
      snapshots.add(snapshot);
    }
    release.put("Members", snapshots);
    if (!ledger.reserveUnbind(level, binding, release)) return Problem.STORAGE;
    List<BagSlime> admitted = new ArrayList<>();
    boolean committed = false;
    try {
      for (BagSlime member : originals) {
        UUID identity = member.getUUID();
        UUID owner = member.getOwnerUUID();
        member.setPendingSpawn(true);
        ledger.admitting(identity, true);
        boolean added;
        try {
          added = level.addFreshEntity(member);
        } finally {
          ledger.admitting(identity, false);
        }
        admitted.add(member);
        if (!added
            || !member.isAddedToWorld()
            || !member.isAlive()
            || member.isRemoved()
            || member.level() != level
            || !identity.equals(member.getUUID())
            || !Objects.equals(owner, member.getOwnerUUID())
            || !station.canUse(player)
            || !station.matches(inputs)) return Problem.CANCELLED;
      }
      for (int i = 0; i < originals.size(); i++) {
        BagSlime member = originals.get(i);
        if (!member.getUUID().equals(i == 0 ? binding.first : binding.second)
            || !player.getUUID().equals(member.getOwnerUUID())
            || !member.isAlive()
            || member.isRemoved()
            || !member.isAddedToWorld()
            || member.level() != level
            || member.position().distanceToSqr(landings.get(i)) > .01
            || !level.mayInteract(player, member.blockPosition())
            || !level.noCollision(member)
            || !level
                .getBlockState(member.blockPosition().below())
                .isFaceSturdy(level, member.blockPosition().below(), Direction.UP)
            || !level.getFluidState(member.blockPosition()).isEmpty()) return Problem.CANCELLED;
      }
      if (!ledger.commitUnbind(level, binding, originals)) return Problem.STORAGE;
      committed = true;
      station.clearCommitted();
      for (BagSlime member : originals) {
        member.setPendingSpawn(false);
        member.setWaiting(true);
        member.onReleased();
      }
      CompanionRegistry.get(level).retire(representative.getUUID());
      ledger.checkpointUnbound(level, binding);
      return Problem.COMPLETE;
    } finally {
      if (!committed) {
        for (BagSlime member : admitted) {
          if (member.isAddedToWorld() && member.isRemoved()) member.revive();
          member.discard();
          member.setPendingSpawn(false);
        }
        ledger.rollbackUnbind(level, binding);
      }
    }
  }

  private static Vec3 landing(
      ServerLevel level, BagSlime member, BlockPos center, List<Vec3> previous) {
    for (int radius = 2; radius <= 5; radius++)
      for (int dx = -radius; dx <= radius; dx++)
        for (int dz = -radius; dz <= radius; dz++) {
          if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
          for (int dy : new int[] {0, 1, -1}) {
            BlockPos feet = center.offset(dx, dy, dz);
            if (!level.hasChunkAt(feet)
                || !level.getWorldBorder().isWithinBounds(feet)
                || !level
                    .getBlockState(feet.below())
                    .isFaceSturdy(level, feet.below(), Direction.UP)
                || !level.getFluidState(feet).isEmpty()
                || !level.getFluidState(feet.above()).isEmpty()) continue;
            Vec3 pos = Vec3.atBottomCenterOf(feet);
            if (previous.stream().anyMatch(other -> other.distanceToSqr(pos) < 2)) continue;
            member.moveTo(pos.x, pos.y, pos.z, 0, 0);
            if (level.noCollision(member)
                && level.getEntities(member, member.getBoundingBox()).isEmpty()) return pos;
          }
        }
    return null;
  }
}
