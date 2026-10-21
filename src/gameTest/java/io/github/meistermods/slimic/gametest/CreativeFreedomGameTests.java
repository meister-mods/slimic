package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.menu.*;
import io.github.meistermods.slimic.registry.*;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreativeFreedomGameTests {
  private static Player editor(GameTestHelper h, FriendlySlime slime) {
    var player = h.makeMockSurvivalPlayer();
    player.getAbilities().instabuild = true;
    player.moveTo(slime.position());
    player.setItemInHand(
        net.minecraft.world.InteractionHand.MAIN_HAND,
        new ItemStack(ModItems.CREATIVE_SLIME_TUNER.get()));
    return player;
  }

  private static double[] values(FriendlySlime slime) {
    return Arrays.stream(SlimeStat.values()).mapToDouble(stat -> stat.tunerValue(slime)).toArray();
  }

  @GameTest(template = "empty", batch = "creative_freedom")
  public static void bothProfessionsKeepFourfoldHealthThroughAllNormalUpgrades(GameTestHelper h) {
    for (var type :
        List.of(ModEntities.GUARDIAN_ANGEL_SLIME.get(), ModEntities.ROYAL_GUARD_SLIME.get())) {
      var slime = (FriendlySlime) type.create(h.getLevel());
      h.assertTrue(
          slime.getMaxHealth() == 64 && slime.getHealth() == 64, "Initial fourfold health");
      slime.setTunedVitals(7, 100, 20);
      for (int level = 1; level <= 20; level++) {
        h.assertTrue(
            slime.upgradeStat(SlimeStat.HEALTH), "Every normal Health level remains available");
        h.assertTrue(
            slime.getMaxHealth() == 64 + 16 * level && slime.getHealth() == 7,
            "Fourfold training never refills health");
      }
      h.assertTrue(!slime.upgradeStat(SlimeStat.HEALTH), "Normal level limit remains twenty");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "creative_freedom")
  public static void legacyHealthModifiersMigrateOnceWithoutMutatingTheInput(GameTestHelper h) {
    for (boolean royal : new boolean[] {false, true}) {
      var slime =
          royal
              ? ModEntities.ROYAL_GUARD_SLIME.get().create(h.getLevel())
              : ModEntities.GUARDIAN_ANGEL_SLIME.get().create(h.getLevel());
      var saved = slime.saveWithoutId(new CompoundTag());
      var identity = royal ? ProfessionHealth.ROYAL_GUARD : ProfessionHealth.GUARDIAN;
      for (var entry : saved.getList("Attributes", 10)) {
        var attribute = (CompoundTag) entry;
        for (var value : attribute.getList("Modifiers", 10)) {
          var modifier = (CompoundTag) value;
          if (modifier.hasUUID("UUID") && modifier.getUUID("UUID").equals(identity)) {
            modifier.putDouble("Amount", royal ? 144 : 9);
            modifier.putInt("Operation", royal ? 0 : 2);
          }
        }
      }
      saved.putFloat("Health", 100);
      var original = saved.copy();
      slime.load(saved);
      h.assertTrue(saved.equals(original), "Migration must not alter a stored input stack");
      for (int i = 0; i < 5; i++) {
        h.assertTrue(
            slime.getMaxHealth() == 64 && slime.getHealth() == 64, "Legacy max shrinks safely");
        slime.setHealth(7);
        slime.load(slime.saveWithoutId(new CompoundTag()));
        h.assertTrue(slime.getHealth() == 7, "Reload never heals or stacks the multiplier");
        slime.setHealth(64);
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "creative_freedom")
  public static void valuesAboveEveryRecommendationApplyAndSurviveRecovery(GameTestHelper h) {
    var slime = ModEntities.MINING_SLIME.get().create(h.getLevel());
    var owner = editor(h, slime);
    slime.tame(owner);
    var menu = new CreativeSlimeTunerMenu(1, owner.getInventory(), slime);
    double[] high = {1000000, 1000000, 1000, 20, 100, 100, 1000000, 100, 128};
    h.assertTrue(
        menu.apply(owner, high, 5000, 900000, 1000000000) && menu.result() == 2,
        "Out-of-recommendation input applies with a nonblocking warning");
    h.assertTrue(
        slime.getMaxHealth() == 1000000
            && slime.getHealth() == 5000
            && slime.getAttributeValue(Attributes.ATTACK_DAMAGE) == 1000000
            && slime.getAttributeValue(Attributes.ARMOR) == 1000,
        "Creative native attributes exceed vanilla ceilings on this companion");
    slime.getBag().setStackInSlot(99, new ItemStack(Items.DIAMOND, 7));
    var item = FriendlySlimeItem.fromSlime(slime);
    var loaded = ModEntities.MINING_SLIME.get().create(h.getLevel());
    loaded.load(item.getTag().getCompound("SlimeData"));
    for (var stat : SlimeStat.values())
      h.assertTrue(
          stat.tunerValue(loaded) == high[stat.ordinal()], "Exact tuned stat survives: " + stat);
    h.assertTrue(
        loaded.getHealth() == 5000
            && loaded.getStamina() == 900000
            && loaded.getStatPoints() == 1000000000
            && loaded.getBag().getStackInSlot(99).getCount() == 7,
        "High vitals, points and expanded cargo survive");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "creative_freedom")
  public static void creativeProfessionHealthAndRosterAreNotClampedToVanillaLimits(
      GameTestHelper h) {
    for (var type :
        List.of(ModEntities.GUARDIAN_ANGEL_SLIME.get(), ModEntities.ROYAL_GUARD_SLIME.get())) {
      var slime = (FriendlySlime) type.create(h.getLevel());
      var owner = editor(h, slime);
      slime.tame(owner);
      var stats = values(slime);
      stats[SlimeStat.HEALTH.ordinal()] = 2000;
      var menu = new CreativeSlimeTunerMenu(1, owner.getInventory(), slime);
      h.assertTrue(
          menu.apply(owner, stats, 6000, 50, 40000)
              && slime.getMaxHealth() == 8000
              && slime.getHealth() == 6000,
          "Fourfold Creative health");
      var item = FriendlySlimeItem.fromSlime(slime);
      CompanionRegistry.get(h.getLevel()).observeStored(item, h.getLevel(), BlockPos.ZERO);
      var entry = CompanionRegistry.get(h.getLevel()).find(slime.getUUID());
      h.assertTrue(
          entry.health() == 6000 && entry.maximumHealth() == 8000, "Roster retains large vitals");
      slime.load(item.getTag().getCompound("SlimeData"));
      h.assertTrue(
          slime.getMaxHealth() == 8000 && slime.getHealth() == 6000, "No load-time truncation");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "creative_freedom")
  public static void billionSlotBagsStoreOnlyActualItemsAndSimulateWithoutDenseAllocation(
      GameTestHelper h) {
    var slime = ModEntities.MINING_SLIME.get().create(h.getLevel());
    SlimeStat.CAPACITY.tune(slime, 1000000000);
    slime.getBag().setStackInSlot(999999999, new ItemStack(Items.DIAMOND, 7));
    h.assertTrue(
        SlimeBag.insertItemStacked(slime.getBag(), new ItemStack(Items.COBBLESTONE, 64), true)
                .isEmpty()
            && slime.getBag().occupiedSlots().size() == 1,
        "Sparse simulation is nonmutating");
    SlimeBag.insertItemStacked(slime.getBag(), new ItemStack(Items.COBBLESTONE, 64), false);
    var saved = slime.saveWithoutId(new CompoundTag());
    var loaded = ModEntities.MINING_SLIME.get().create(h.getLevel());
    loaded.load(saved);
    h.assertTrue(
        loaded.getBag().getSlots() == 1000000000
            && loaded.getBag().occupiedSlots().size() == 2
            && loaded.getBag().getStackInSlot(999999999).getCount() == 7,
        "High slot survives sparse NBT");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "creative_freedom")
  public static void overflowPagesTransferToTheCorrectSlotsWithoutAddingMenuSlots(
      GameTestHelper h) {
    var slime = ModEntities.MINING_SLIME.get().create(h.getLevel());
    var owner = h.makeMockSurvivalPlayer();
    slime.tame(owner);
    owner.moveTo(slime.position());
    owner.setItemInHand(
        net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
    SlimeStat.CAPACITY.tune(slime, 60);
    var menu = TestMenus.create(1, owner.getInventory(), slime);
    h.assertTrue(menu.slots.size() == 65 && menu.bagPages() == 3, "Fixed menu with expanded pages");
    h.assertTrue(menu.clickMenuButton(owner, SlimeMenu.NEXT_PAGE), "Second page available");
    menu.broadcastChanges();
    owner.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 7));
    menu.quickMoveStack(owner, 29);
    h.assertTrue(
        slime.getBag().getStackInSlot(27).getCount() == 7
            && slime.getBag().getStackInSlot(0).isEmpty(),
        "Shift deposit targets overflow page");
    menu.quickMoveStack(owner, 2);
    h.assertTrue(
        slime.getBag().getStackInSlot(27).isEmpty(), "Shift withdrawal targets overflow page");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "creative_freedom")
  public static void capacityShrinkStillProtectsCargoAndInvalidNumbersRemainAtomic(
      GameTestHelper h) {
    var slime = ModEntities.MINING_SLIME.get().create(h.getLevel());
    var owner = editor(h, slime);
    var stats = values(slime);
    stats[SlimeStat.CAPACITY.ordinal()] = 100;
    var menu = new CreativeSlimeTunerMenu(1, owner.getInventory(), slime);
    h.assertTrue(menu.apply(owner, stats, 7, 50, 40000), "Expanded capacity applies");
    slime.getBag().setStackInSlot(99, new ItemStack(Items.DIAMOND, 7));
    stats[0] = 1000;
    stats[5] = 9;
    h.assertTrue(
        !menu.apply(owner, stats, 7, 50, 0)
            && menu.result() == 3
            && slime.getMaxHealth() == 16
            && slime.getBag().getStackInSlot(99).getCount() == 7,
        "Occupied shrink cannot partly change other stats");
    stats = values(slime);
    for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1}) {
      stats[0] = invalid;
      h.assertTrue(
          !menu.apply(owner, stats, 7, 50, 0) && slime.getHealth() == 7,
          "Invalid numeric input rejected");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "creative_freedom", timeoutTicks = 200)
  public static void workerRestocksFromSparseFinalSlotAndUsesItsBucket(GameTestHelper h) {
    var slime = h.spawn(ModEntities.MINING_SLIME.get(), new BlockPos(3, 2, 3));
    slime.tame(h.makeMockSurvivalPlayer());
    SlimeStat.CAPACITY.tune(slime, 1000000000);
    var pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
    pickaxe.setHoverName(net.minecraft.network.chat.Component.literal("Sparse spare"));
    pickaxe.setDamageValue(19);
    slime.getBag().setStackInSlot(999999999, pickaxe.copy());
    slime.getBag().setStackInSlot(999999998, new ItemStack(Items.BUCKET));
    slime.onReleased();
    slime.setWorking(true);
    h.succeedWhen(
        () -> {
          h.assertTrue(
              ItemStack.matches(slime.getTool(0), pickaxe),
              "Final-slot exact tool restocks on a native work tick");
          h.assertTrue(
              slime.hasBucket(), "Final-slot bucket enables fluid work without a dense scan");
          h.assertTrue(
              slime.getBag().getStackInSlot(999999999).isEmpty()
                  && slime.getBag().getStackInSlot(999999998).is(Items.BUCKET),
              "Restock moves the tool once and retains the bucket");
        });
  }

  @GameTest(template = "empty", batch = "creative_freedom", timeoutTicks = 200)
  public static void extremeDetectionSurveysOnlyLoadedTerrainAndKeepsCandidates(GameTestHelper h) {
    var farmer = h.spawn(ModEntities.FARMER_SLIME.get(), new BlockPos(3, 2, 3));
    farmer.tame(h.makeMockSurvivalPlayer());
    farmer.setTool(new ItemStack(Items.DIAMOND_HOE));
    h.setBlock(new BlockPos(3, 1, 4), net.minecraft.world.level.block.Blocks.FARMLAND);
    SlimeStat.DETECTION_RANGE.tune(farmer, Double.MAX_VALUE);
    var origin = farmer.blockPosition();
    int chunks = h.getLevel().getChunkSource().getLoadedChunksCount();
    var selected = WorkSearch.loadedChunks(h.getLevel(), origin, Double.MAX_VALUE);
    h.assertTrue(
        selected.stream().anyMatch(chunk -> chunk.getPos().equals(farmer.chunkPosition())),
        "Extreme range retains loaded origin chunk");
    h.assertTrue(
        selected.size() <= chunks, "Search does not create coordinate-sized candidate arrays");
    h.assertTrue(
        WorkSearch.entities(
                h.getLevel(),
                FriendlySlime.class,
                new net.minecraft.world.phys.AABB(origin).inflate(Double.MAX_VALUE))
            .contains(farmer),
        "Extreme entity query retains loaded candidates");
    farmer.setWorking(true);
    h.succeedWhen(
        () -> {
          h.assertTrue(
              farmer.getFarmPlots().contains(h.absolutePos(new BlockPos(3, 1, 4))),
              "Extreme survey preserves real farmland");
          h.assertTrue(
              SlimeStat.DETECTION_RANGE.tunerValue(farmer) == Double.MAX_VALUE,
              "Search optimization never caps the stored stat");
        });
  }
}
