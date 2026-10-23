package io.github.meistermods.slimic.world;

import io.github.meistermods.slimic.block.RoyalSynthesisStationBlockEntity;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.event.SlimeSynthesisEvent;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraftforge.common.MinecraftForge;

/** Validation, preview and commit all derive their values from the actual stored companions. */
public final class RoyalSynthesis {
  public enum Problem {
    READY,
    MISSING_PARENTS,
    FAMILY,
    OWNER,
    EQUIPMENT,
    CARGO,
    HISTORY,
    CREATIVE,
    MATERIALS,
    CONSUMED,
    ACTIVE_PARENT,
    OUTPUT_FULL,
    CANCELLED,
    CREATION,
    STORAGE,
    COMPLETE,
    ATTACHMENTS
  }

  public record Check(Problem problem, long normalPoints, long ancientPoints, String detail) {
    public long total() {
      return Math.addExact(normalPoints, ancientPoints);
    }
  }

  private RoyalSynthesis() {}

  public static Check check(ServerLevel level, ServerPlayer owner, List<ItemStack> inputs) {
    if (inputs.size() != 4 || inputs.get(0).isEmpty() || inputs.get(1).isEmpty())
      return failed(Problem.MISSING_PARENTS);
    if (SynthesisFamily.match(inputs.get(0), inputs.get(1)) == null) return failed(Problem.FAMILY);
    long[] totals = new long[2];
    Set<UUID> ids = new HashSet<>();
    for (int index = 0; index < 2; index++) {
      ItemStack item = inputs.get(index);
      if (item.getCount() != 1
          || !item.hasTag()
          || !item.getTag().contains("SlimeData", Tag.TAG_COMPOUND))
        return failed(Problem.MISSING_PARENTS);
      CompoundTag data = item.getTag().getCompound("SlimeData");
      if (data.contains("GrowthSchema") && data.getInt("GrowthSchema") != 1)
        return failed(Problem.HISTORY);
      if (!SlimeData.canLoad(data)
          || !data.hasUUID("UUID")
          || !data.hasUUID("Owner")
          || !Float.isFinite(data.getFloat("Health"))
          || data.getFloat("Health") <= 0) return failed(Problem.HISTORY);
      if (!owner.getUUID().equals(data.getUUID("Owner"))) return failed(Problem.OWNER);
      UUID id = data.getUUID("UUID");
      if (!ids.add(id) || RoyalSynthesisData.get(level).consumed(id))
        return failed(Problem.CONSUMED);
      for (var world : level.getServer().getAllLevels())
        if (world.getEntity(id) != null) return failed(Problem.ACTIVE_PARENT);
      var entry = CompanionRegistry.get(level).find(id);
      if (entry != null
          && (entry.location() == CompanionRegistry.Location.WORLD
              || !owner.getUUID().equals(entry.owner()))) return failed(Problem.ACTIVE_PARENT);
      if (!data.getCompound("TunedStats").isEmpty()) return failed(Problem.CREATIVE);
      if (!data.getCompound("ForgeCaps").isEmpty()
          || !item.save(new CompoundTag()).getCompound("ForgeCaps").isEmpty())
        return failed(Problem.ATTACHMENTS);
      if (!ItemStack.of(
              item.getTag()
                  .getCompound(io.github.meistermods.slimic.item.MiningSlimeItem.INITIAL_PICKAXE))
          .isEmpty()) return new Check(Problem.EQUIPMENT, 0, 0, (index + 1) + ":InitialPickaxe:1");
      for (String key : List.of("HandItems", "ArmorItems")) {
        var equipment = data.getList(key, Tag.TAG_COMPOUND);
        for (int slot = 0; slot < equipment.size(); slot++)
          if (!ItemStack.of(equipment.getCompound(slot)).isEmpty())
            return new Check(Problem.EQUIPMENT, 0, 0, (index + 1) + ":" + key + ":" + (slot + 1));
      }
      for (String key :
          List.of(
              "MiningBag", "TransportBag", "FieldBag", "CombatBag", "HealingBag", "CleaningBag"))
        for (var raw : data.getCompound(key).getList("Items", Tag.TAG_COMPOUND)) {
          var stored = (CompoundTag) raw;
          if (!ItemStack.of(stored).isEmpty())
            return new Check(Problem.CARGO, 0, 0, (index + 1) + ":" + (stored.getInt("Slot") + 1L));
        }
      long invested = 0;
      for (SlimeStat stat : SlimeStat.values()) {
        int points = data.getCompound("SlimeStats").getInt(stat.key());
        if (points < 0 || points > stat.maxLevel()) return failed(Problem.HISTORY);
        invested += points;
      }
      long unused = data.getLong("StatPoints"),
          earned =
              data.contains("GrowthSchema")
                  ? data.getLong("GrowthEarned")
                  : data.getLong("EarnedPoints");
      if (unused < 0
          || earned < 0
          || unused > Long.MAX_VALUE - invested
          || invested + unused != earned
          || data.contains("GrowthSchema") && !data.getBoolean("GrowthTrusted"))
        return failed(Problem.HISTORY);
      totals[index] = earned;
    }
    if (totals[0] > Long.MAX_VALUE - totals[1]) return failed(Problem.HISTORY);
    if (!inputs.get(2).is(Items.SLIME_BALL)
        || inputs.get(2).getCount() < 4
        || !inputs.get(3).is(ModItems.OLD_COMMAND_FRAGMENT.get())
        || inputs.get(3).getCount() < 2)
      return new Check(Problem.MATERIALS, totals[0], totals[1], "");
    return new Check(Problem.READY, totals[0], totals[1], "");
  }

  private static Check failed(Problem reason) {
    return new Check(reason, 0, 0, "");
  }

  public static Check execute(ServerPlayer owner, RoyalSynthesisStationBlockEntity station) {
    if (!(station.getLevel() instanceof ServerLevel level)
        || !level.getServer().isSameThread()
        || station.busy
        || !station.canUse(owner)) return failed(Problem.CANCELLED);
    station.reconcile();
    var inputs = station.contents();
    var check = check(level, owner, inputs);
    if (check.problem != Problem.READY) return check;
    if (owner.getInventory().getFreeSlot() < 0) return failed(Problem.OUTPUT_FULL);
    var first = inputs.get(0).getTag().getCompound("SlimeData");
    var second = inputs.get(1).getTag().getCompound("SlimeData");
    UUID normal = first.getUUID("UUID"), ancient = second.getUUID("UUID");
    var ledger = RoyalSynthesisData.get(level);
    if (!ledger.reserve(normal, ancient)) return failed(Problem.CONSUMED);
    station.busy = true;
    try {
      var family = SynthesisFamily.match(inputs.get(0), inputs.get(1));
      var result = family == null ? null : family.create(level);
      if (result == null) return failed(Problem.CREATION);
      UUID transaction = UUID.randomUUID();
      result.tame(owner);
      result.inheritGrowth(check.total());
      result.setWaiting(true);
      var ancestry = new CompoundTag();
      ancestry.putInt("Generation", 1);
      ancestry.putUUID("NormalParent", normal);
      ancestry.putUUID("AncientParent", ancient);
      ancestry.putString("NormalName", inputs.get(0).getHoverName().getString());
      ancestry.putString("AncientName", inputs.get(1).getHoverName().getString());
      ancestry.putString(
          "NormalNameComponent",
          net.minecraft.network.chat.Component.Serializer.toJson(inputs.get(0).getHoverName()));
      ancestry.putString(
          "AncientNameComponent",
          net.minecraft.network.chat.Component.Serializer.toJson(inputs.get(1).getHoverName()));
      ancestry.putString("Family", family.id());
      ancestry.putLong("SynthesisTime", level.getGameTime());
      if (station.origin() != null) ancestry.putUUID("OriginSanctum", station.origin());
      if (second.getCompound("ForgeData").hasUUID("SlimicAncientTrainingIdentity"))
        ancestry.putUUID(
            "AncientCore",
            second.getCompound("ForgeData").getUUID("SlimicAncientTrainingIdentity"));
      result.getPersistentData().put("NextGenAncestry", ancestry);
      result.getPersistentData().putUUID("SynthesisTransaction", transaction);
      result.getPersistentData().putUUID("SynthesisToken", UUID.randomUUID());
      ItemStack output = FriendlySlimeItem.fromSlime(result);
      if (output.isEmpty()
          || MinecraftForge.EVENT_BUS.post(
              new SlimeSynthesisEvent(owner, inputs.get(0), inputs.get(1), output)))
        return failed(Problem.CANCELLED);
      if (!station.canUse(owner)
          || !station.matches(inputs)
          || check(level, owner, inputs).problem != Problem.READY) return failed(Problem.CANCELLED);
      var after = new ArrayList<ItemStack>();
      after.add(ItemStack.EMPTY);
      after.add(ItemStack.EMPTY);
      after.add(inputs.get(2).copyWithCount(inputs.get(2).getCount() - 4));
      after.add(inputs.get(3).copyWithCount(inputs.get(3).getCount() - 2));
      var tx =
          new RoyalSynthesisData.Transaction(
              transaction,
              owner.getUUID(),
              normal,
              ancient,
              result.getUUID(),
              level.dimension().location().toString(),
              station.getBlockPos(),
              check.normalPoints,
              check.ancientPoints,
              output,
              inputs,
              after);
      if (!ledger.commit(level, tx)) return failed(Problem.STORAGE);
      station.apply(tx);
      CompanionRegistry.get(level).retire(normal);
      CompanionRegistry.get(level).retire(ancient);
      CompanionRegistry.get(level).observeStored(output, level, station.getBlockPos());
      ledger.deliver(owner, tx);
      return new Check(Problem.COMPLETE, check.normalPoints, check.ancientPoints, "");
    } finally {
      station.busy = false;
      ledger.release(normal, ancient);
    }
  }
}
