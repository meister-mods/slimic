package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.ColonyBondingStationBlockEntity;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.ModBlocks;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.world.ColonyBonding;
import java.math.BigInteger;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ColonyGrowthGameTests {
  private record Fixture(FriendlySlime root, ColonyMembers members, FakePlayer owner) {}

  private static CompoundTag member(
      GameTestHelper h, FakePlayer owner, boolean combat, long points) {
    BagSlime member =
        combat
            ? ModEntities.COMBAT_SLIME.get().create(h.getLevel())
            : ModEntities.TRANSPORT_SLIME.get().create(h.getLevel());
    member.tame(owner);
    member.inheritGrowth(points);
    var saved = new CompoundTag();
    member.save(saved);
    return saved;
  }

  private static Fixture fixture(GameTestHelper h, boolean combat, long first, long second) {
    var owner =
        new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "ColonyGrowthStudy"));
    return fixture(
        h, combat, owner, member(h, owner, combat, first), member(h, owner, combat, second));
  }

  private static Fixture fixture(
      GameTestHelper h, boolean combat, FakePlayer owner, CompoundTag first, CompoundTag second) {
    FriendlySlime root =
        combat
            ? ModEntities.BOUND_COMBAT_SLIME.get().create(h.getLevel())
            : ModEntities.BOUND_TRANSPORT_SLIME.get().create(h.getLevel());
    root.tame(owner);
    var list = new ListTag();
    list.add(first.copy());
    list.add(second.copy());
    var data = new CompoundTag();
    data.putInt("Schema", 1);
    data.put("Members", list);
    var members = ((BoundColony) root).members();
    members.load(data);
    h.assertTrue(members.ready(), "Two complete member records load");
    return new Fixture(root, members, owner);
  }

  private static Fixture reload(GameTestHelper h, Fixture original) {
    FriendlySlime root =
        original.root instanceof BoundCombatSlime
            ? ModEntities.BOUND_COMBAT_SLIME.get().create(h.getLevel())
            : ModEntities.BOUND_TRANSPORT_SLIME.get().create(h.getLevel());
    root.load(original.root.saveWithoutId(new CompoundTag()));
    var members = ((BoundColony) root).members();
    h.assertTrue(members.ready(), "Native root save restores both members");
    return new Fixture(root, members, original.owner);
  }

  private static BigInteger balance(ColonyMembers members) {
    return members.records().stream()
        .map(member -> BigInteger.valueOf(member.getStatPoints()))
        .reduce(BigInteger.ZERO, BigInteger::add);
  }

  private static int sharedHalfUnits(ColonyMembers members) {
    int total = 0;
    for (var member : members.records()) {
      var saved = member.saveWithoutId(new CompoundTag());
      total += member.getActivityProgress() * 2 + saved.getInt("ActivityHalf");
    }
    return total / 2;
  }

  private static void activity(ColonyMembers members, int total) {
    while (total > 0) {
      int units = Math.min(64, total);
      members.activity(units);
      total -= units;
    }
  }

  private static void trusted(GameTestHelper h, ColonyMembers members) {
    for (var member : members.records()) {
      long invested = 0;
      for (var stat : SlimeStat.values()) invested += member.getStatLevel(stat);
      h.assertTrue(
          member.hasTrustedGrowth()
              && member.getGrowthEarned() == invested + member.getStatPoints(),
          "Ordinary training preserves exact trusted native growth history");
    }
  }

  @GameTest(template = "empty", batch = "colony_growth")
  public static void onePooledPointUpgradesBothMembersEvenWhenTheFirstHasNone(GameTestHelper h) {
    for (boolean combat : new boolean[] {false, true}) {
      var f = fixture(h, combat, 0, 6);
      h.assertTrue(
          f.root.getStatPoints() == 3,
          "The arithmetic mean of both balances forms one shared pool");
      h.assertTrue(
          f.root.upgradeStat(SlimeStat.REACH), "The second member can fund shared training");
      h.assertTrue(
          f.root.getStatPoints() == 2
              && balance(f.members).equals(BigInteger.valueOf(4))
              && f.members.records().stream()
                  .allMatch(member -> member.getStatLevel(SlimeStat.REACH) == 1)
              && f.root.getStatLevel(SlimeStat.REACH) == 1,
          "One pooled point buys exactly one level for each member");
      var restored = reload(h, f);
      h.assertTrue(
          restored.root.getStatPoints() == 2
              && restored.members.records().stream()
                  .allMatch(member -> member.getStatLevel(SlimeStat.REACH) == 1),
          "Both levels and the single debit survive native reload");
      trusted(h, restored.members);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_growth")
  public static void oneCappedMemberTrainsItsPartnerAndBothCapsRejectWithoutSpending(
      GameTestHelper h) {
    for (boolean combat : new boolean[] {false, true}) {
      var f = fixture(h, combat, 9, 9);
      var data = f.members.save();
      var second = data.getList("Members", Tag.TAG_COMPOUND).getCompound(1);
      second
          .getCompound("SlimeStats")
          .putInt(SlimeStat.CAPACITY.key(), SlimeStat.CAPACITY.maxLevel());
      second.putLong("GrowthEarned", second.getLong("StatPoints") + SlimeStat.CAPACITY.maxLevel());
      f.members.load(data);
      h.assertTrue(
          f.root.getStatLevel(SlimeStat.CAPACITY) == 0 && f.root.upgradeStat(SlimeStat.CAPACITY),
          "The lower member level keeps a one-sided upgrade available");
      h.assertTrue(
          f.root.getStatPoints() == 8
              && f.members.records().get(0).getStatLevel(SlimeStat.CAPACITY) == 1
              && f.members.records().get(1).getStatLevel(SlimeStat.CAPACITY)
                  == SlimeStat.CAPACITY.maxLevel(),
          "One common point trains only the eligible member without exceeding its partner's cap");
      for (int i = 1; i < SlimeStat.CAPACITY.maxLevel(); i++) {
        h.assertTrue(
            f.root.upgradeStat(SlimeStat.CAPACITY), "Remaining one-sided levels stay available");
      }
      var before = f.members.save();
      h.assertTrue(
          f.root.getStatLevel(SlimeStat.CAPACITY) == SlimeStat.CAPACITY.maxLevel()
              && !f.root.upgradeStat(SlimeStat.CAPACITY)
              && before.equals(f.members.save()),
          "Both caps reject training atomically without another debit");
      trusted(h, f.members);
    }
    var empty = fixture(h, false, 0, 0);
    h.assertTrue(
        !empty.root.upgradeStat(SlimeStat.REACH), "An empty pool cannot train either member");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_growth")
  public static void growthReturnsToFullSpeedOnlyWhenNoUsedStatCanTrainBothMembers(
      GameTestHelper h) {
    for (boolean combat : new boolean[] {false, true}) {
      for (int scenario = 0; scenario < 4; scenario++) {
        var f = fixture(h, combat, 9, 9);
        var data = f.members.save();
        var records = data.getList("Members", Tag.TAG_COMPOUND);
        for (int index = 0; index < 2; index++) {
          var record = records.getCompound(index);
          long invested = 0;
          for (var stat : SlimeStat.values()) {
            int level = 0;
            if (stat.isUsedBy(f.root)) {
              if (scenario == 0)
                level = index == 1 && stat == SlimeStat.CAPACITY ? stat.maxLevel() : 0;
              else if (scenario == 1) level = index == 0 ? stat.maxLevel() : 0;
              else if (scenario == 2) level = stat.ordinal() % 2 == index ? stat.maxLevel() : 0;
              else if (stat == SlimeStat.REACH) level = index == 0 ? stat.maxLevel() - 1 : 0;
              else level = stat.maxLevel();
            }
            record.getCompound("SlimeStats").putInt(stat.key(), level);
            invested += level;
          }
          record.putLong("GrowthEarned", invested + record.getLong("StatPoints"));
        }
        f.members.load(data);
        int originalCost = f.root.getNextPointCost();
        if (scenario < 3) {
          f.members.activity(3);
          h.assertTrue(
              sharedHalfUnits(f.members) == (scenario == 0 ? 3 : 6),
              "Only a shared upgrade among the job's used stats keeps growth at half speed: "
                  + scenario);
          h.assertTrue(
              f.root.getNextPointCost() == originalCost && f.root.getStatPoints() == 9,
              "Changing the growth rate never changes costs or creates an unearned point");
        } else {
          f.members.activity(1);
          h.assertTrue(
              sharedHalfUnits(f.members) == 1,
              "The final jointly trainable stat still halves growth");
          h.assertTrue(
              f.root.upgradeStat(SlimeStat.REACH), "The final common level trains both members");
          f.members.activity(1);
          h.assertTrue(
              sharedHalfUnits(f.members) == 3 && f.root.getStatPoints() == 8,
              "Full-speed growth retains the prior half-unit exactly when shared eligibility ends");
        }
        int progress = sharedHalfUnits(f.members);
        f = reload(h, f);
        h.assertTrue(
            sharedHalfUnits(f.members) == progress,
            "Native reload retains either growth rate's fraction");
        trusted(h, f.members);
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_growth")
  public static void halfActivitySurvivesReloadAndUsesTheExistingIncreasingCosts(GameTestHelper h) {
    for (boolean combat : new boolean[] {false, true}) {
      var f = fixture(h, combat, 0, 0);
      f.members.activity(-1);
      f.members.activity(0);
      activity(f.members, 63);
      h.assertTrue(
          f.root.getStatPoints() == 0
              && f.root.getActivityProgress() == 31
              && sharedHalfUnits(f.members) == 63
              && f.root.getNextPointCost() == 32,
          "Sixty-three raw units yield thirty-one and a half credited units");
      f = reload(h, f);
      h.assertTrue(sharedHalfUnits(f.members) == 63, "The saved half unit is not rounded away");
      f.members.activity(1);
      h.assertTrue(
          f.root.getStatPoints() == 1
              && f.root.getActivityProgress() == 0
              && sharedHalfUnits(f.members) == 0
              && f.root.getNextPointCost() == 96,
          "The sixty-fourth raw unit crosses the ordinary first cost exactly once");
      activity(f.members, 190);
      h.assertTrue(
          f.root.getStatPoints() == 1 && f.root.getActivityProgress() == 95,
          "The second point still costs ninety-six credited units");
      f.members.activity(2);
      h.assertTrue(
          f.root.getStatPoints() == 2
              && f.root.getActivityProgress() == 0
              && f.root.getNextPointCost() == 160,
          "The next cost rises by the unchanged sixty-four units");
      trusted(h, f.members);
    }
    var clamped = fixture(h, false, 0, 0);
    clamped.members.activity(Integer.MAX_VALUE);
    h.assertTrue(
        clamped.root.getStatPoints() == 1 && sharedHalfUnits(clamped.members) == 0,
        "One activity call is clamped to sixty-four raw units before halving");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_growth")
  public static void legacyBalancesAndProgressCombineOnceUsingTheHigherCostHistory(
      GameTestHelper h) {
    var f = fixture(h, false, 0, 0);
    var entries = new ListTag();
    for (int i = 0; i < 2; i++) {
      var original = member(h, f.owner, false, 3 + i);
      original.remove("ActivityHalf");
      original.putInt("ActivityProgress", i == 0 ? 11 : 14);
      entries.add(original);
    }
    var legacy = new CompoundTag();
    legacy.putInt("Schema", 1);
    legacy.put("Members", entries);
    legacy.putInt("NextWorker", 1);
    f.members.load(legacy);
    h.assertTrue(
        f.root.getStatPoints() == 3
            && f.root.getActivityProgress() == 14
            && sharedHalfUnits(f.members) == 28
            && f.root.getNextPointCost() == 288,
        "Legacy balances average to three; completion fractions average to fourteen common units");
    for (int i = 0; i < 5; i++) {
      f.members.prepareUnbind();
      f = reload(h, f);
      h.assertTrue(
          f.root.getStatPoints() == 3
              && sharedHalfUnits(f.members) == 28
              && f.root.getNextPointCost() == 288,
          "Repeated save, unbind and rebind cycles create no zero-work points or progress");
    }
    f.members.activity(1);
    f.members.prepareUnbind();
    h.assertTrue(
        balance(f.members).equals(BigInteger.valueOf(6)) && sharedHalfUnits(f.members) == 29,
        "One raw activity unit completes the retained half without duplicating progress");
    for (var member : f.members.records())
      h.assertTrue(
          member.getNextPointCost() == 288 && member.getActivityProgress() == 14,
          "Canonical bound records retain the same credited progress and shared growth cost");
    trusted(h, f.members);
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_growth")
  public static void bindingRoundsTheMeanDownAndUnbindingCopiesTheSharedBalance(GameTestHelper h) {
    for (boolean combat : new boolean[] {false, true})
      for (long total : new long[] {0, 1, 2, 7}) {
        var f = fixture(h, combat, 0, total);
        UUID first = f.members.records().get(0).getUUID();
        UUID second = f.members.records().get(1).getUUID();
        f.members.prepareUnbind();
        f.members.prepareUnbind();
        h.assertTrue(
            f.members.records().get(0).getStatPoints() == total / 2
                && f.members.records().get(1).getStatPoints() == total / 2,
            "Binding rounds down the mean; unbinding copies that same balance to both members");
        var restored = reload(h, f);
        h.assertTrue(
            restored.root.getStatPoints() == total / 2
                && restored.members.records().get(0).getUUID().equals(first)
                && restored.members.records().get(1).getUUID().equals(second),
            "Rebinding the canonical records preserves identities and exact pooled balance");
        trusted(h, restored.members);
      }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_growth")
  public static void mixingUntrustedGrowthNeverLaundersEitherMembersHistory(GameTestHelper h) {
    var f = fixture(h, false, 2, 4);
    var data = f.members.save();
    data.getList("Members", Tag.TAG_COMPOUND).getCompound(0).putBoolean("GrowthTrusted", false);
    f.members.load(data);
    h.assertTrue(f.root.upgradeStat(SlimeStat.REACH), "Creative-derived balances remain usable");
    f.members.prepareUnbind();
    for (var member : f.members.records())
      h.assertTrue(
          !member.hasTrustedGrowth(), "Shared points never restore a trusted synthesis history");
    f = reload(h, f);
    h.assertTrue(
        f.members.records().stream().noneMatch(FriendlySlime::hasTrustedGrowth),
        "The untrusted status persists after another native load");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_growth")
  public static void maximumBalancesAverageWithoutOverflowAndCapacityPreflightIsAtomic(
      GameTestHelper h) {
    var f = fixture(h, false, Long.MAX_VALUE, Long.MAX_VALUE);
    var expected = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO);
    h.assertTrue(
        f.root.getStatPoints() == Long.MAX_VALUE && balance(f.members).equals(expected),
        "Averaging two Long.MAX_VALUE balances neither wraps nor truncates either record");
    f = reload(h, f);
    f.members.prepareUnbind();
    h.assertTrue(
        balance(f.members).equals(expected)
            && f.members.records().stream()
                .allMatch(member -> member.getStatPoints() == Long.MAX_VALUE),
        "Save and unbind preserve the full common balance in both members");
    h.assertTrue(
        f.root.upgradeStat(SlimeStat.REACH)
            && f.root.getStatPoints() == Long.MAX_VALUE - 1
            && f.members.records().stream()
                .allMatch(
                    member ->
                        member.getStatPoints() == Long.MAX_VALUE - 1
                            && member.getStatLevel(SlimeStat.REACH) == 1),
        "One shared debit makes room for both invested levels at the long boundary");
    trusted(h, f.members);
    var capacity = fixture(h, false, 1, 1);
    for (var member : capacity.members.records()) SlimeStat.CAPACITY.tune(member, 536870910);
    capacity.members.project();
    h.assertTrue(
        ((BagSlime) capacity.root).getBag().getSlots() == 2147483640,
        "The sparse bag starts seven slots below the supported combined limit");
    var before = capacity.members.save();
    h.assertTrue(
        !capacity.root.upgradeStat(SlimeStat.CAPACITY) && before.equals(capacity.members.save()),
        "Twelve added slots are rejected before either native bag or pooled point changes");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_growth")
  public static void creativePointOnlyEditPreservesUnequalMemberStatsOddCapacityAndBoundaryCargo(
      GameTestHelper h) {
    var f = fixture(h, true, 30, 30);
    for (int index = 0; index < 2; index++) {
      var member = f.members.records().get(index);
      int[] levels = {index == 0 ? 6 : 1, index == 0 ? 4 : 1, index == 0 ? 2 : 5};
      SlimeStat[] stats = {SlimeStat.CAPACITY, SlimeStat.ATTACK, SlimeStat.REACH};
      for (int stat = 0; stat < stats.length; stat++)
        for (int level = 0; level < levels[stat]; level++) {
          h.assertTrue(
              member.upgradeStat(stats[stat]), "Native individual training builds unequal members");
        }
      int last = member.getCapacity() - 1;
      member
          .getBag()
          .setStackInSlot(
              last, new ItemStack(index == 0 ? Items.EMERALD : Items.DIAMOND, index == 0 ? 5 : 7));
    }
    f.members.project();
    var bag = ((BagSlime) f.root).getBag();
    h.assertTrue(
        f.members.records().get(0).getCapacity() == 27
            && f.members.records().get(1).getCapacity() == 12
            && f.root instanceof BagSlime
            && ((BagSlime) f.root).getCapacity() == 39
            && bag.getSlots() == 39
            && bag.getStackInSlot(38).is(Items.DIAMOND),
        "The odd 39-slot bag includes occupied native member and combined boundaries");
    var beforeBags =
        f.members.records().stream().map(member -> member.getBag().serializeNBT()).toList();
    var before =
        f.members.records().stream()
            .map(member -> member.saveWithoutId(new CompoundTag()))
            .toList();
    double[][] values =
        f.members.records().stream()
            .map(
                member ->
                    java.util.Arrays.stream(SlimeStat.values())
                        .mapToDouble(stat -> stat.value(member))
                        .toArray())
            .toArray(double[][]::new);
    var editor = f.owner;
    editor.getAbilities().instabuild = true;
    editor.moveTo(f.root.position());
    editor.setItemInHand(
        net.minecraft.world.InteractionHand.MAIN_HAND,
        new ItemStack(io.github.meistermods.slimic.registry.ModItems.CREATIVE_SLIME_TUNER.get()));
    var menu =
        new io.github.meistermods.slimic.menu.CreativeSlimeTunerMenu(
            1, editor.getInventory(), f.root);
    double[] unchanged =
        java.util.Arrays.stream(SlimeStat.values())
            .mapToDouble(stat -> stat.tunerValue(f.root))
            .toArray();
    h.assertTrue(
        menu.stillValid(editor)
            && menu.apply(editor, unchanged, f.root.getHealth(), f.root.getStamina(), 19),
        "An authorized Creative editor applies a point-only change through the actual menu");
    h.assertTrue(
        f.root.getStatPoints() == 19
            && ((BagSlime) f.root).getCapacity() == 39
            && bag.getSlots() == 39
            && bag.getStackInSlot(38).is(Items.DIAMOND)
            && bag.getStackInSlot(38).getCount() == 7,
        "Only common points change; odd capacity and every occupied boundary stack are preserved");
    for (int index = 0; index < 2; index++) {
      var member = f.members.records().get(index);
      var after = member.saveWithoutId(new CompoundTag());
      h.assertTrue(
          member.getStatPoints() == 19
              && beforeBags.get(index).equals(member.getBag().serializeNBT())
              && before.get(index).getCompound("SlimeStats").equals(after.getCompound("SlimeStats"))
              && before
                  .get(index)
                  .getCompound("TunedStats")
                  .equals(after.getCompound("TunedStats")),
          "Each member receives the edited balance without rewritten levels or direct stat edits");
      for (var stat : SlimeStat.values()) {
        h.assertTrue(
            Double.compare(values[index][stat.ordinal()], stat.value(member)) == 0,
            "Unequal native member stat remains unchanged: " + index + "/" + stat);
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_growth")
  public static void creativeUnrepresentableCapacityRejectsAtomicallyAndOddCapacityKeepsItsTotal(
      GameTestHelper h) {
    for (boolean combat : new boolean[] {false, true}) {
      var f = fixture(h, combat, 4, 6);
      f.members.records().get(0).getBag().setStackInSlot(8, new ItemStack(Items.EMERALD, 5));
      f.members.records().get(1).getBag().setStackInSlot(8, new ItemStack(Items.DIAMOND, 7));
      f.members.project();
      var editor = f.owner;
      editor.getAbilities().instabuild = true;
      editor.moveTo(f.root.position());
      editor.setItemInHand(
          net.minecraft.world.InteractionHand.MAIN_HAND,
          new ItemStack(io.github.meistermods.slimic.registry.ModItems.CREATIVE_SLIME_TUNER.get()));
      var menu =
          new io.github.meistermods.slimic.menu.CreativeSlimeTunerMenu(
              1, editor.getInventory(), f.root);
      var before = f.root.saveWithoutId(new CompoundTag());
      double[] requested =
          java.util.Arrays.stream(SlimeStat.values())
              .mapToDouble(stat -> stat.tunerValue(f.root))
              .toArray();
      requested[SlimeStat.HEALTH.ordinal()] = 128;
      requested[SlimeStat.ATTACK.ordinal()] = 12;
      requested[SlimeStat.CAPACITY.ordinal()] = Double.MAX_VALUE;
      h.assertTrue(
          menu.stillValid(editor) && !menu.apply(editor, requested, 1, 1, 19) && menu.result() == 4,
          "An unrepresentable combined bag is rejected through the authorized menu before any field"
              + " applies");
      h.assertTrue(
          before.equals(f.root.saveWithoutId(new CompoundTag())),
          "Rejected capacity leaves both members, health, all other stats, points and cargo"
              + " untouched");
    }
    var odd = fixture(h, true, 4, 6);
    odd.members.tune(SlimeStat.CAPACITY, 41);
    h.assertTrue(
        odd.members.records().get(0).getCapacity() == 21
            && odd.members.records().get(1).getCapacity() == 20
            && odd.root.getCapacity() == 41
            && ((BagSlime) odd.root).getBag().getSlots() == 41,
        "Explicit odd combat capacity preserves its full total with the extra slot on the first"
            + " member");
    odd = reload(h, odd);
    h.assertTrue(
        odd.root.getCapacity() == 41
            && odd.members.records().get(0).getCapacity() == 21
            && odd.members.records().get(1).getCapacity() == 20,
        "Native reload keeps the entire explicitly edited odd capacity");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_growth", timeoutTicks = 600)
  public static void actualStationUnbindAndRebindPreserveBothUpgradesAndFractionalGrowth(
      GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var level = h.getLevel();
    var owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ColonyGrowthStation"));
    var pos = BlockPos.containing(area.point(7, 101, 7));
    owner.moveTo(area.point(7.5, 101, 5.5));
    level.setBlockAndUpdate(pos, ModBlocks.COLONY_BONDING_STATION.get().defaultBlockState());
    var station = (ColonyBondingStationBlockEntity) level.getBlockEntity(pos);
    UUID[] identities = new UUID[2];
    try {
      for (int i = 0; i < 2; i++) {
        var member = ModEntities.TRANSPORT_SLIME.get().create(level);
        member.tame(owner);
        member.inheritGrowth(3 + i);
        member.getBag().setStackInSlot(0, new ItemStack(Items.EMERALD, 5 + i));
        identities[i] = member.getUUID();
        station.setItem(i, FriendlySlimeItem.fromSlime(member));
      }
      h.assertTrue(
          ColonyBonding.execute(owner, station, false) == ColonyBonding.Problem.COMPLETE,
          "The native station averages three and four into three shared points");
      var stored =
          owner.getInventory().items.stream()
              .filter(ColonyBonding::bound)
              .findFirst()
              .orElseThrow()
              .copy();
      owner.getInventory().clearContent();
      var root = ModEntities.BOUND_TRANSPORT_SLIME.get().create(level);
      root.load(stored.getTag().getCompound("SlimeData"));
      h.assertTrue(
          root.upgradeStat(SlimeStat.REACH)
              && root.upgradeStat(SlimeStat.REACH)
              && root.getStatPoints() == 1,
          "Two shared points train both originals twice");
      root.members().activity(2);
      station.setItem(0, FriendlySlimeItem.fromSlime(root));
      h.assertTrue(
          ColonyBonding.execute(owner, station, true) == ColonyBonding.Problem.COMPLETE,
          "The trained colony unbinds through the real station transaction");
      var first = (TransportSlime) level.getEntity(identities[0]);
      var second = (TransportSlime) level.getEntity(identities[1]);
      h.assertTrue(
          first != null
              && second != null
              && first.isOrderedToSit()
              && second.isOrderedToSit()
              && first.getStatPoints() == 1
              && second.getStatPoints() == 1
              && first.getStatLevel(SlimeStat.REACH) == 2
              && second.getStatLevel(SlimeStat.REACH) == 2
              && first.getBag().getStackInSlot(0).getCount() == 5
              && second.getBag().getStackInSlot(0).getCount() == 6
              && first.hasTrustedGrowth()
              && second.hasTrustedGrowth(),
          "Original identities return waiting with matching points, both upgrades, cargo and"
              + " trusted history");
      h.assertTrue(
          first.capture(owner).consumesAction() && second.capture(owner).consumesAction(),
          "Both unbound originals can be recovered normally");
      var recovered =
          owner.getInventory().items.stream()
              .filter(stack -> stack.getItem() instanceof FriendlySlimeItem)
              .map(ItemStack::copy)
              .toList();
      h.assertTrue(recovered.size() == 2, "Recovery produces exactly the original pair");
      station.setItem(0, recovered.get(0));
      station.setItem(1, recovered.get(1));
      owner.getInventory().clearContent();
      h.assertTrue(
          ColonyBonding.execute(owner, station, false) == ColonyBonding.Problem.COMPLETE,
          "The same originals can be bound again without spending another point");
      var rebound =
          owner.getInventory().items.stream()
              .filter(ColonyBonding::bound)
              .findFirst()
              .orElseThrow();
      var result = ModEntities.BOUND_TRANSPORT_SLIME.get().create(level);
      result.load(rebound.getTag().getCompound("SlimeData"));
      h.assertTrue(
          result.getStatPoints() == 1
              && sharedHalfUnits(result.members()) == 1
              && result.members().records().stream()
                  .allMatch(member -> member.getStatLevel(SlimeStat.REACH) == 2),
          "Recovery and rebinding preserve the balance and round only the sub-half-unit progress"
              + " down");
    } finally {
      for (var id : identities)
        if (id != null && level.getEntity(id) != null) level.getEntity(id).discard();
      area.close();
    }
    h.succeed();
  }
}
