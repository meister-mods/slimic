package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.ColonyBondingStationBlockEntity;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.ModBlocks;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.world.ColonyBonding;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ColonyGrowthCostGameTests {
  private record Fixture(FriendlySlime root, ColonyMembers members, FakePlayer owner) {}

  private static CompoundTag member(
      GameTestHelper h, FakePlayer owner, boolean combat, long points, int earned, int halfUnits) {
    BagSlime member =
        combat
            ? ModEntities.COMBAT_SLIME.get().create(h.getLevel())
            : ModEntities.TRANSPORT_SLIME.get().create(h.getLevel());
    member.tame(owner);
    member.inheritGrowth(points);
    var data = new CompoundTag();
    member.save(data);
    data.putInt("EarnedPoints", earned);
    data.putInt("ActivityProgress", halfUnits / 2);
    data.putInt("ActivityHalf", halfUnits % 2);
    return data;
  }

  private static Fixture fixture(
      GameTestHelper h,
      boolean combat,
      long firstPoints,
      long secondPoints,
      int firstEarned,
      int secondEarned,
      int firstHalf,
      int secondHalf) {
    var owner = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "ColonyCostStudy"));
    var list = new ListTag();
    list.add(member(h, owner, combat, firstPoints, firstEarned, firstHalf));
    list.add(member(h, owner, combat, secondPoints, secondEarned, secondHalf));
    var data = new CompoundTag();
    data.putInt("Schema", 1);
    data.put("Members", list);
    FriendlySlime root =
        combat
            ? ModEntities.BOUND_COMBAT_SLIME.get().create(h.getLevel())
            : ModEntities.BOUND_TRANSPORT_SLIME.get().create(h.getLevel());
    root.tame(owner);
    var members = ((BoundColony) root).members();
    members.load(data);
    h.assertTrue(members.ready(), "Complete original native records load");
    return new Fixture(root, members, owner);
  }

  private static Fixture reload(GameTestHelper h, Fixture f) {
    FriendlySlime root =
        f.root instanceof BoundCombatSlime
            ? ModEntities.BOUND_COMBAT_SLIME.get().create(h.getLevel())
            : ModEntities.BOUND_TRANSPORT_SLIME.get().create(h.getLevel());
    root.load(f.root.saveWithoutId(new CompoundTag()));
    var members = ((BoundColony) root).members();
    h.assertTrue(members.ready(), "Native colony save restores complete records");
    return new Fixture(root, members, f.owner);
  }

  private static int earned(FriendlySlime slime) {
    return slime.saveWithoutId(new CompoundTag()).getInt("EarnedPoints");
  }

  private static int halfUnits(FriendlySlime slime) {
    var data = slime.saveWithoutId(new CompoundTag());
    return data.getInt("ActivityProgress") * 2 + data.getInt("ActivityHalf");
  }

  private static int sharedHalf(ColonyMembers members) {
    return halfUnits(members.records().get(0));
  }

  private static void levels(GameTestHelper h, ColonyMembers members, int first, int second) {
    var saved = members.save();
    h.assertTrue(
        saved.getInt("Schema") == 3
            && Arrays.equals(
                saved.getIntArray("IndividualGrowthLevels"), new int[] {first, second}),
        "The canonical schema stores the exact individual cost histories");
  }

  private static void activity(ColonyMembers members, int units) {
    while (units > 0) {
      int batch = Math.min(units, 64);
      members.activity(batch);
      units -= batch;
    }
  }

  private static void individual(
      GameTestHelper h, List<BagSlime> released, long points, int firstEarned, int secondEarned) {
    h.assertTrue(
        released.size() == 2
            && earned(released.get(0)) == firstEarned
            && earned(released.get(1)) == secondEarned,
        "Released individuals keep their own original counters plus actual shared awards");
    for (var member : released) {
      h.assertTrue(
          member.getStatPoints() == points && member.isOrderedToSit(),
          "The common balance is copied to each waiting individual");
    }
  }

  @GameTest(template = "empty", batch = "colony_growth_cost")
  public static void unequalHistoriesSurviveNoWorkReloadAndDetachedReleaseCopies(GameTestHelper h) {
    for (boolean combat : new boolean[] {false, true}) {
      var f = fixture(h, combat, 4, 8, 0, 5, 0, 0);
      for (int cycle = 0; cycle < 4; cycle++) {
        levels(h, f.members, 0, 5);
        h.assertTrue(
            f.root.getStatPoints() == 6 && f.root.getNextPointCost() == 352,
            "The bound colony uses the mean balance and larger next cost");
        var before = f.members.save();
        var released = f.members.prepareUnbind();
        individual(h, released, 6, 0, 5);
        h.assertTrue(
            released.get(0).getNextPointCost() == 32 && released.get(1).getNextPointCost() == 352,
            "Unbinding restores the two distinct original costs");
        h.assertTrue(
            released.get(0) != f.members.records().get(0)
                && released.get(0).getUUID().equals(f.members.records().get(0).getUUID())
                && released.get(1).getUUID().equals(f.members.records().get(1).getUUID()),
            "Release copies preserve identities without aliasing canonical records");
        h.assertTrue(
            released.get(0).upgradeStat(SlimeStat.REACH),
            "A detached result can train independently");
        h.assertTrue(
            before.equals(f.members.save()),
            "Preparing or modifying release copies cannot change bound records");
        individual(h, f.members.prepareUnbind(), 6, 0, 5);
        f = reload(h, f);
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_growth_cost")
  public static void onlyActualSharedPointAwardsAdvanceBothIndividualCounters(GameTestHelper h) {
    for (boolean combat : new boolean[] {false, true}) {
      var f = fixture(h, combat, 0, 0, 0, 5, 0, 0);
      activity(f.members, 703);
      h.assertTrue(
          f.root.getStatPoints() == 0 && sharedHalf(f.members) == 703,
          "Half-speed work stops exactly half a unit before the shared 352-unit cost");
      levels(h, f.members, 0, 5);
      f = reload(h, f);
      f.members.activity(1);
      h.assertTrue(
          f.root.getStatPoints() == 1
              && sharedHalf(f.members) == 0
              && f.root.getNextPointCost() == 416,
          "The completing half-unit awards one point at the next shared cost");
      levels(h, f.members, 1, 6);
      individual(h, f.members.prepareUnbind(), 1, 1, 6);
      h.assertTrue(f.root.upgradeStat(SlimeStat.REACH), "One common point trains both members");
      levels(h, f.members, 1, 6);
      f.root.setTunedVitals(f.root.getHealth(), f.root.getStamina(), 19);
      f = reload(h, f);
      levels(h, f.members, 1, 6);
      var released = f.members.prepareUnbind();
      individual(h, released, 19, 1, 6);
      h.assertTrue(
          released.get(0).getNextPointCost() == 96 && released.get(1).getNextPointCost() == 416,
          "Training and Creative balance changes do not count as earned work points");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_growth_cost")
  public static void relativeProgressAvoidsEarlyPayoutAndSurvivesRepeatedNativeLoads(
      GameTestHelper h) {
    for (boolean combat : new boolean[] {false, true}) {
      var f = fixture(h, combat, 5, 5, 0, 5, 37, 395);
      h.assertTrue(
          sharedHalf(f.members) == 401 && f.root.getActivityProgress() == 200,
          "18.5/32 and 197.5/352 average to 200.5/352 after half-unit rounding");
      for (int cycle = 0; cycle < 5; cycle++) {
        levels(h, f.members, 0, 5);
        h.assertTrue(
            f.root.getStatPoints() == 5 && sharedHalf(f.members) == 401,
            "Saved shared progress above the lower individual cost cannot pay early");
        for (var member : f.members.records())
          h.assertTrue(
              earned(member) == 5 && member.getNextPointCost() == 352,
              "Canonical native records use the shared threshold while bound");
        var before = f.members.save();
        var released = f.members.prepareUnbind();
        individual(h, released, 5, 0, 5);
        h.assertTrue(
            halfUnits(released.get(0)) == 36 && halfUnits(released.get(1)) == 401,
            "Unbinding converts the common fraction to 18/32 and 200.5/352 without a payout");
        for (var member : released) {
          BagSlime restored =
              combat
                  ? ModEntities.COMBAT_SLIME.get().create(h.getLevel())
                  : ModEntities.TRANSPORT_SLIME.get().create(h.getLevel());
          restored.load(FriendlySlimeItem.fromSlime(member).getTag().getCompound("SlimeData"));
          h.assertTrue(
              restored.getStatPoints() == 5
                  && earned(restored) == earned(member)
                  && halfUnits(restored) == halfUnits(member),
              "Recovered individual item loading never settles a spurious point");
        }
        h.assertTrue(
            before.equals(f.members.save()),
            "Previewing the release leaves canonical progress unchanged");
        f = reload(h, f);
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_growth_cost")
  public static void legacyColonyCostsMigrateFromExistingEvidenceAndOrdinaryGrowthStillSettles(
      GameTestHelper h) {
    var f = fixture(h, false, 7, 7, 5, 5, 201, 201);
    var legacy = f.members.save();
    legacy.putInt("Schema", 2);
    legacy.remove("IndividualGrowthLevels");
    f.members.load(legacy);
    levels(h, f.members, 5, 5);
    var before = f.members.save();
    individual(h, f.members.prepareUnbind(), 7, 5, 5);
    h.assertTrue(
        sharedHalf(f.members) == 201 && before.equals(f.members.save()),
        "Already normalized old histories stay normalized instead of guessing lost counters");
    var nativeData = member(h, f.owner, false, 0, 0, 200);
    var ordinary = ModEntities.TRANSPORT_SLIME.get().create(h.getLevel());
    ordinary.load(nativeData);
    h.assertTrue(
        ordinary.getStatPoints() == 1
            && earned(ordinary) == 1
            && ordinary.getActivityProgress() == 68
            && ordinary.getNextPointCost() == 96,
        "Existing ordinary legacy progress still settles against its own native threshold");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_growth_cost")
  public static void counterLimitsAndBlockedPayoutsCountOnlyActualAwards(GameTestHelper h) {
    var capped = fixture(h, false, 0, 0, 999999, 1000000, 8191, 8191);
    capped.members.activity(1);
    levels(h, capped.members, 1000000, 1000000);
    individual(h, capped.members.prepareUnbind(), 1, 1000000, 1000000);
    h.assertTrue(
        capped.root.getNextPointCost() == 4096,
        "Individual counter saturation leaves the established maximum cost unchanged");
    var full = fixture(h, false, Long.MAX_VALUE, Long.MAX_VALUE, 0, 5, 0, 0);
    activity(full.members, 704);
    h.assertTrue(
        full.root.getStatPoints() == Long.MAX_VALUE,
        "A full common balance cannot receive another point");
    levels(h, full.members, 0, 5);
    full = reload(h, full);
    levels(h, full.members, 0, 5);
    h.assertTrue(
        full.root.getStatPoints() == Long.MAX_VALUE,
        "Reload cannot invent an award for a blocked balance");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_growth_cost", timeoutTicks = 600)
  public static void stationFailureRetryRecoveryAndRebindingPreserveIndividualGrowthHistory(
      GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var level = h.getLevel();
    var owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ColonyCostStation"));
    var center = BlockPos.containing(area.point(7, 101, 7));
    owner.moveTo(area.point(7.5, 101, 5.5));
    level.setBlockAndUpdate(center, ModBlocks.COLONY_BONDING_STATION.get().defaultBlockState());
    var station = (ColonyBondingStationBlockEntity) level.getBlockEntity(center);
    UUID[] identities = new UUID[2];
    try {
      for (int index = 0; index < 2; index++) {
        var original = ModEntities.TRANSPORT_SLIME.get().create(level);
        original.load(member(h, owner, false, 0, index == 0 ? 0 : 5, 0));
        original.getBag().setStackInSlot(0, new ItemStack(Items.EMERALD, 5 + index));
        identities[index] = original.getUUID();
        station.setItem(index, FriendlySlimeItem.fromSlime(original));
      }
      h.assertTrue(
          ColonyBonding.execute(owner, station, false) == ColonyBonding.Problem.COMPLETE,
          "The actual station binds individual cost histories zero and five");
      var stored =
          owner.getInventory().items.stream()
              .filter(ColonyBonding::bound)
              .findFirst()
              .orElseThrow()
              .copy();
      owner.getInventory().clearContent();
      var root = ModEntities.BOUND_TRANSPORT_SLIME.get().create(level);
      root.load(stored.getTag().getCompound("SlimeData"));
      activity(root.members(), 704);
      h.assertTrue(root.upgradeStat(SlimeStat.REACH), "Actual bound work funds a shared upgrade");
      activity(root.members(), 100);
      levels(h, root.members(), 1, 6);
      station.setItem(0, FriendlySlimeItem.fromSlime(root));
      var beforeFailedRelease = station.getItem(0).copy();
      for (int dx = -5; dx <= 5; dx++)
        for (int dz = -5; dz <= 5; dz++) {
          if (Math.max(Math.abs(dx), Math.abs(dz)) < 2) continue;
          for (int y = 0; y <= 2; y++)
            level.setBlock(center.offset(dx, y, dz), Blocks.STONE.defaultBlockState(), 2);
        }
      h.assertTrue(
          ColonyBonding.execute(owner, station, true) == ColonyBonding.Problem.SPACE
              && ItemStack.matches(beforeFailedRelease, station.getItem(0)),
          "A blocked release consumes neither the item nor any individual growth history");
      for (int dx = -5; dx <= 5; dx++)
        for (int dz = -5; dz <= 5; dz++) {
          if (Math.max(Math.abs(dx), Math.abs(dz)) < 2) continue;
          for (int y = 0; y <= 2; y++)
            level.setBlock(center.offset(dx, y, dz), Blocks.AIR.defaultBlockState(), 2);
        }
      h.assertTrue(
          ColonyBonding.execute(owner, station, true) == ColonyBonding.Problem.COMPLETE,
          "The same untouched item releases successfully after space is cleared");
      var first = (TransportSlime) level.getEntity(identities[0]);
      var second = (TransportSlime) level.getEntity(identities[1]);
      h.assertTrue(first != null && second != null, "Both original identities return to the world");
      individual(h, List.of(first, second), 0, 1, 6);
      h.assertTrue(
          first.getNextPointCost() == 96
              && second.getNextPointCost() == 416
              && halfUnits(first) == 23
              && halfUnits(second) == 100
              && first.getBag().getStackInSlot(0).getCount() == 5
              && second.getBag().getStackInSlot(0).getCount() == 6
              && first.getStatLevel(SlimeStat.REACH) == 1
              && second.getStatLevel(SlimeStat.REACH) == 1,
          "Spawned individuals keep distinct costs, proportional progress, shared training and"
              + " original cargo");
      h.assertTrue(
          first.capture(owner).consumesAction() && second.capture(owner).consumesAction(),
          "Both individuals are captured through the native interaction");
      var recovered =
          owner.getInventory().items.stream()
              .filter(stack -> stack.getItem() instanceof FriendlySlimeItem)
              .map(ItemStack::copy)
              .toList();
      h.assertTrue(recovered.size() == 2, "Recovery returns exactly two original items");
      station.setItem(0, recovered.get(0));
      station.setItem(1, recovered.get(1));
      owner.getInventory().clearContent();
      h.assertTrue(
          ColonyBonding.execute(owner, station, false) == ColonyBonding.Problem.COMPLETE,
          "The recovered individuals bind again without another earned point");
      var rebound =
          owner.getInventory().items.stream()
              .filter(ColonyBonding::bound)
              .findFirst()
              .orElseThrow();
      var restored = ModEntities.BOUND_TRANSPORT_SLIME.get().create(level);
      restored.load(rebound.getTag().getCompound("SlimeData"));
      levels(h, restored.members(), 1, 6);
      h.assertTrue(
          restored.getStatPoints() == 0
              && restored.getNextPointCost() == 416
              && sharedHalf(restored.members()) == 99,
          "Rebinding keeps the earned histories and rounds only the sub-half-unit remainder down");
      activity(restored.members(), 733);
      levels(h, restored.members(), 2, 7);
      individual(h, restored.members().prepareUnbind(), 1, 2, 7);
    } finally {
      for (var identity : identities)
        if (identity != null && level.getEntity(identity) != null)
          level.getEntity(identity).discard();
      area.close();
    }
    h.succeed();
  }
}
