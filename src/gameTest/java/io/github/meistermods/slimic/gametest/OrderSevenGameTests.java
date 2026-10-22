package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.recipe.CompanionRecipe;
import io.github.meistermods.slimic.registry.*;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OrderSevenGameTests {
  private record Farm(LoadedWorkArea area, FarmingSlime pet, Player owner, BlockPos plot) {}

  private static Farm farm(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var pet = ModEntities.FARMING_SLIME.get().create(h.getLevel());
    pet.moveTo(area.point(6.5, 101, 5.5));
    pet.tame(owner);
    pet.setTool(new ItemStack(Items.IRON_HOE));
    h.getLevel().addFreshEntity(pet);
    BlockPos plot = BlockPos.containing(area.point(6, 100, 7));
    h.getLevel()
        .setBlockAndUpdate(
            plot, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
    h.getLevel().setBlockAndUpdate(plot.offset(1, 0, 0), Blocks.WATER.defaultBlockState());
    return new Farm(area, pet, owner, plot);
  }

  private static int count(BagSlime pet, Item item) {
    int n = 0;
    for (int i = 0; i < pet.getBag().getSlots(); i++)
      if (pet.getBag().getStackInSlot(i).is(item)) n += pet.getBag().getStackInSlot(i).getCount();
    return n;
  }

  private static LoggingSlime logger(GameTestHelper h, LoadedWorkArea area) {
    area.clearSurroundingTrees();
    var pet = ModEntities.LOGGING_SLIME.get().create(h.getLevel());
    pet.setWorking(true);
    pet.moveTo(area.point(6.5, 101, 5.5));
    pet.tame(h.makeMockSurvivalPlayer());
    pet.setTool(new ItemStack(Items.DIAMOND_AXE));
    pet.onReleased();
    h.getLevel().addFreshEntity(pet);
    return pet;
  }

  private static BlockPos tree(
      GameTestHelper h, LoadedWorkArea area, Block log, Block leaves, int height) {
    BlockPos base = BlockPos.containing(area.point(6, 101, 7));
    h.getLevel().setBlockAndUpdate(base.below(), Blocks.DIRT.defaultBlockState());
    for (int y = 0; y < height; y++)
      h.getLevel().setBlockAndUpdate(base.above(y), log.defaultBlockState());
    h.getLevel()
        .setBlockAndUpdate(
            base.above(height),
            leaves
                .defaultBlockState()
                .setValue(LeavesBlock.PERSISTENT, false)
                .setValue(LeavesBlock.DISTANCE, 1));
    return base;
  }

  @GameTest(template = "empty", batch = "order_seven", timeoutTicks = 80)
  public static void everyHoeAndAxeRecipePreservesOriginalToolAndIdentity(GameTestHelper h) {
    for (boolean farming : List.of(true, false)) {
      var kind = farming ? CompanionRecipe.Kind.FARMING : CompanionRecipe.Kind.LOGGING;
      var recipe =
          new CompanionRecipe(
              ResourceLocation.fromNamespaceAndPath("slimic", "recipe_audit"),
              CraftingBookCategory.MISC,
              kind);
      for (Item item :
          farming
              ? List.of(
                  Items.WOODEN_HOE,
                  Items.STONE_HOE,
                  Items.IRON_HOE,
                  Items.GOLDEN_HOE,
                  Items.DIAMOND_HOE,
                  Items.NETHERITE_HOE)
              : List.of(
                  Items.WOODEN_AXE,
                  Items.STONE_AXE,
                  Items.IRON_AXE,
                  Items.GOLDEN_AXE,
                  Items.DIAMOND_AXE,
                  Items.NETHERITE_AXE)) {
        var friendly = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
        friendly.tame(h.makeMockSurvivalPlayer());
        friendly.setTunedVitals(7, 100, 4);
        var input = FriendlySlimeItem.fromSlime(friendly);
        var tool = new ItemStack(item);
        tool.setDamageValue(7);
        tool.setHoverName(Component.literal("Kept tool"));
        tool.enchant(Enchantments.UNBREAKING, 2);
        var grid =
            new TransientCraftingContainer(
                new AbstractContainerMenu(null, 0) {
                  @Override
                  public boolean stillValid(Player p) {
                    return true;
                  }

                  @Override
                  public ItemStack quickMoveStack(Player p, int i) {
                    return ItemStack.EMPTY;
                  }
                },
                2,
                2);
        grid.setItem(0, input);
        grid.setItem(3, tool);
        h.assertTrue(recipe.matches(grid, h.getLevel()), "Every native hoe/axe specializes");
        var output = recipe.assemble(grid, h.getLevel().registryAccess());
        h.assertTrue(
            ItemStack.matches(ItemStack.of(output.getTag().getCompound("InitialWorkTool")), tool),
            "Original damage, name and enchantments preserved");
        h.assertTrue(
            output.getTag().getCompound("SlimeData").getUUID("UUID").equals(friendly.getUUID())
                && output.getTag().getCompound("SlimeData").getFloat("Health") == 7,
            "Identity and health preserved");
        h.assertTrue(
            recipe.getRemainingItems(grid).stream().allMatch(ItemStack::isEmpty),
            "Consumed tool has no duplicate remainder");
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_seven", timeoutTicks = 160)
  public static void farmSnapshotsOnlyFarmlandPresentAtActivation(GameTestHelper h) {
    var f = farm(h);
    f.pet.getBag().setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS, 12));
    f.pet.setWaiting(true);
    h.runAfterDelay(
        20,
        () -> {
          h.assertTrue(h.getLevel().getBlockState(f.plot.above()).isAir(), "Wait makes no edits");
          f.pet.setWorking(true);
          var later = f.plot.offset(-2, 0, 0);
          h.getLevel()
              .setBlockAndUpdate(
                  later, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
          h.runAfterDelay(
              60,
              () -> {
                h.assertTrue(
                    f.pet.getFarmPlots().contains(f.plot) && !f.pet.getFarmPlots().contains(later),
                    "Frozen activation survey excludes later farmland");
                h.assertTrue(
                    h.getLevel().getBlockState(f.plot.above()).is(Blocks.WHEAT)
                        && h.getLevel().getBlockState(later.above()).isAir(),
                    "Only registered plot is planted");
                h.assertTrue(f.pet.getActivityProgress() > 0, "Planting grants actual activity");
                f.area.close();
                h.succeed();
              });
        });
  }

  @GameTest(template = "empty", batch = "order_seven", timeoutTicks = 180)
  public static void harvestReplantsSameCropAndStoresProduce(GameTestHelper h) {
    var f = farm(h);
    f.pet.getBag().setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS, 4));
    h.getLevel()
        .setBlockAndUpdate(
            f.plot.above(), Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, 7));
    f.pet.setWorking(true);
    h.runAfterDelay(
        110,
        () -> {
          var state = h.getLevel().getBlockState(f.plot.above());
          h.assertTrue(
              state.is(Blocks.CARROTS) && !((CropBlock) state.getBlock()).isMaxAge(state),
              "Carrots are replanted rather than substituted with wheat");
          h.assertTrue(
              count(f.pet, Items.CARROT) > 0 && count(f.pet, Items.WHEAT_SEEDS) == 4,
              "Harvest stored and unrelated seeds retained");
          h.assertTrue(
              f.pet.getActivityProgress() >= 2, "Harvest and planting both contribute activity");
          f.area.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_seven", timeoutTicks = 220)
  public static void repairsRegisteredDirtAndForgetsNonSoil(GameTestHelper h) {
    var f = farm(h);
    f.pet.setWorking(true);
    h.runAfterDelay(
        20,
        () -> {
          h.getLevel().setBlockAndUpdate(f.plot, Blocks.DIRT.defaultBlockState());
        });
    h.runAfterDelay(
        75,
        () -> {
          h.assertTrue(
              h.getLevel().getBlockState(f.plot).is(Blocks.FARMLAND),
              "Registered trampled dirt is repaired");
          h.getLevel().setBlockAndUpdate(f.plot, Blocks.STONE.defaultBlockState());
        });
    h.runAfterDelay(
        110,
        () -> {
          h.assertTrue(
              !f.pet.getFarmPlots().contains(f.plot), "Non-soil plot permanently excluded");
          h.getLevel().setBlockAndUpdate(f.plot, Blocks.DIRT.defaultBlockState());
        });
    h.runAfterDelay(
        150,
        () -> {
          h.assertTrue(
              h.getLevel().getBlockState(f.plot).is(Blocks.DIRT),
              "Forgotten plot is not tilled again");
          f.area.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_seven", timeoutTicks = 40)
  public static void farmingHopsNeverTrampleFarmland(GameTestHelper h) {
    var f = farm(h);
    f.pet.setNoAi(true);
    for (int i = 0; i < 40; i++)
      Blocks.FARMLAND.fallOn(h.getLevel(), h.getLevel().getBlockState(f.plot), f.plot, f.pet, 10);
    h.assertTrue(
        h.getLevel().getBlockState(f.plot).is(Blocks.FARMLAND),
        "Farmland survives native repeated falling");
    f.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_seven", timeoutTicks = 40)
  public static void fieldDetectionAlwaysDoublesUnderlyingStatAndMenusExposeTool(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    for (var type : List.of(ModEntities.FARMING_SLIME.get(), ModEntities.LOGGING_SLIME.get())) {
      var pet = (FieldSlime) type.create(h.getLevel());
      pet.tame(owner);
      pet.moveTo(area.point(5.5, 101, 5.5));
      owner.moveTo(pet.position());
      for (double base : List.of(8D, 17D, 64D)) {
        SlimeStat.DETECTION_RANGE.tune(pet, base);
        h.assertTrue(
            SlimeStat.DETECTION_RANGE.value(pet) == 2 * base,
            "Detection stays exactly double including tuner");
      }
      var menu = TestMenus.create(1, owner.getInventory(), pet);
      h.assertTrue(
          menu.slots.size() == 64
              && menu.getSlot(0)
                  .mayPlace(
                      new ItemStack(pet instanceof FarmingSlime ? Items.IRON_HOE : Items.IRON_AXE))
              && !menu.getSlot(0).mayPlace(new ItemStack(Items.IRON_PICKAXE)),
          "One filtered equipment slot plus ordinary bag");
    }
    area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_seven", timeoutTicks = 1000)
  public static void loggingFellsTallTreeAndReplantsAtOriginalStump(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var pet = logger(h, area);
    var root = tree(h, area, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 6);
    pet.getBag().setStackInSlot(0, new ItemStack(Items.OAK_SAPLING));
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getBlockState(root).is(Blocks.OAK_SAPLING),
                    "Original stump replanted: status="
                        + pet.getFieldStatus()
                        + ", progress="
                        + pet.getActivityProgress()
                        + ", pending="
                        + pet.getPendingPlantings()))
        .thenExecute(
            () -> {
              h.assertTrue(
                  count(pet, Items.OAK_LOG) == 6
                      && count(pet, Items.OAK_SAPLING) == 0
                      && pet.getPendingPlantings() == 0,
                  "Full connected crown collected and exactly one sapling consumed");
              h.assertTrue(
                  pet.getActivityProgress() >= 7 && pet.getTool().getDamageValue() > 0,
                  "Logging and replanting award activity and wear axe");
              pet.discard();
              area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_seven", timeoutTicks = 180)
  public static void loggingRequiresMatchingSaplingsBeforeAnyCut(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var pet = logger(h, area);
    var root = tree(h, area, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES, 3);
    pet.getBag().setStackInSlot(0, new ItemStack(Items.OAK_SAPLING));
    h.runAfterDelay(
        100,
        () -> {
          h.assertTrue(
              h.getLevel().getBlockState(root).is(Blocks.BIRCH_LOG)
                  && pet.getWorkStatus().reason() == WorkStatus.Reason.SEEDS_MISSING,
              "Wrong or absent saplings cannot start destructive work: "
                  + pet.getWorkStatus()
                  + ", home="
                  + pet.getWorkHome()
                  + ", root="
                  + root
                  + ", leaves="
                  + h.getLevel().getBlockState(root.above(3)));
          h.assertTrue(
              count(pet, Items.BIRCH_LOG) == 0 && pet.getActivityProgress() == 0,
              "No fake growth for blocked work");
          area.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_seven", timeoutTicks = 180)
  public static void loggingLeavesBuildingsAndPersistentDecorativeLeavesAlone(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var pet = logger(h, area);
    var root = tree(h, area, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 3);
    pet.getBag().setStackInSlot(0, new ItemStack(Items.OAK_SAPLING, 4));
    h.getLevel()
        .setBlockAndUpdate(
            root.above(3),
            Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
    h.runAfterDelay(
        100,
        () -> {
          h.assertTrue(
              count(pet, Items.OAK_LOG) == 0 && h.getLevel().getBlockState(root).is(Blocks.OAK_LOG),
              "Decorative logs with persistent leaves are preserved");
          area.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_seven", timeoutTicks = 180)
  public static void fullBagStopsHarvestWithoutDestroyingCrop(GameTestHelper h) {
    var f = farm(h);
    h.getLevel()
        .setBlockAndUpdate(
            f.plot.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
    for (int i = 0; i < f.pet.getCapacity(); i++)
      f.pet.getBag().setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
    f.pet.setWorking(true);
    h.runAfterDelay(
        60,
        () -> {
          h.assertTrue(
              h.getLevel().getBlockState(f.plot.above()).getValue(CropBlock.AGE) == 7
                  && f.pet.needsUnloading(),
              "Full inventory raises cargo request while preserving crop");
          f.pet.getBag().setStackInSlot(0, ItemStack.EMPTY);
          f.pet.getBag().setStackInSlot(1, ItemStack.EMPTY);
        });
    h.runAfterDelay(
        120,
        () -> {
          h.assertTrue(count(f.pet, Items.WHEAT) > 0, "Removing cargo resumes work");
          f.area.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_seven", timeoutTicks = 180)
  public static void cancelledHarvestKeepsCropEquipmentAndGrowthUnchanged(GameTestHelper h) {
    var f = farm(h);
    h.getLevel()
        .setBlockAndUpdate(
            f.plot.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
    Consumer<BlockEvent.BreakEvent> cancel =
        e -> {
          if (e.getPos().equals(f.plot.above())) e.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(cancel);
    f.pet.setWorking(true);
    h.runAfterDelay(
        70,
        () -> {
          MinecraftForge.EVENT_BUS.unregister(cancel);
          h.assertTrue(
              h.getLevel().getBlockState(f.plot.above()).getValue(CropBlock.AGE) == 7
                  && f.pet.getTool().getDamageValue() == 0
                  && f.pet.getActivityProgress() == 0
                  && !f.pet.hasCargo(),
              "Protection cancellation preserves terrain and awards nothing");
          f.area.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_seven", timeoutTicks = 160)
  public static void cancelledPlantRollsBackAndRetainsSeed(GameTestHelper h) {
    var f = farm(h);
    f.pet.getBag().setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS, 3));
    Consumer<BlockEvent.EntityPlaceEvent> cancel =
        e -> {
          if (e.getPos().equals(f.plot.above())) e.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(cancel);
    f.pet.setWorking(true);
    h.runAfterDelay(
        70,
        () -> {
          MinecraftForge.EVENT_BUS.unregister(cancel);
          h.assertTrue(
              h.getLevel().getBlockState(f.plot.above()).isAir()
                  && count(f.pet, Items.WHEAT_SEEDS) == 3
                  && f.pet.getActivityProgress() == 0,
              "Cancelled placement restores air and retains seeds");
          f.area.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_seven", timeoutTicks = 140)
  public static void replacementToolRequestsUseExactGearForBothWorkers(GameTestHelper h) {
    var f = farm(h);
    var log = logger(h, f.area);
    f.pet.setTool(ItemStack.EMPTY);
    log.setTool(ItemStack.EMPTY);
    f.pet.setWorking(true);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    f.pet.needsReplacementTool() && log.needsReplacementTool(),
                    "Both workers request their profession tools"))
        .thenExecute(
            () -> {
              for (FieldSlime pet : List.of(f.pet, log)) {
                var tool =
                    new ItemStack(
                        pet instanceof FarmingSlime ? Items.DIAMOND_HOE : Items.DIAMOND_AXE);
                tool.setDamageValue(11);
                tool.setHoverName(Component.literal("Supplied"));
                h.assertTrue(
                    pet.needsReplacementTool()
                        && !pet.acceptsWorkTool(new ItemStack(Items.IRON_PICKAXE))
                        && pet.receiveWorkTool(tool),
                    "Shared tool-supply request accepts correct profession tool");
                h.assertTrue(
                    ItemStack.matches(pet.getTool(), tool)
                        && !pet.receiveWorkTool(new ItemStack(tool.getItem())),
                    "Received tool stays exact and duplicate supply is rejected");
              }
              f.pet.discard();
              log.discard();
              f.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_seven", timeoutTicks = 100)
  public static void farmingSnapshotAndGearSurviveRecoverySerialization(GameTestHelper h) {
    var f = farm(h);
    f.pet.setTunedVitals(9, 37, 0);
    f.pet.setWorking(true);
    var stack = FriendlySlimeItem.fromSlime(f.pet);
    var restored = ModEntities.FARMING_SLIME.get().create(h.getLevel());
    restored.load(stack.getTag().getCompound("SlimeData"));
    h.assertTrue(
        stack.is(ModItems.FARMING_SLIME.get())
            && restored.getFarmPlots().contains(f.plot)
            && restored.getUUID().equals(f.pet.getUUID())
            && restored.getHealth() == 9
            && restored.getStamina() == 37
            && ItemStack.matches(restored.getTool(), f.pet.getTool()),
        "In-flight survey, gear, identity and current resources round-trip");
    f.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_seven", timeoutTicks = 1200)
  public static void fourStumpTreeRetainsReservedSaplingsAcrossReload(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var pet = logger(h, area);
    BlockPos base = BlockPos.containing(area.point(6, 101, 7));
    for (int x = 0; x < 2; x++)
      for (int z = 0; z < 2; z++) {
        var p = base.offset(x, 0, z);
        h.getLevel().setBlockAndUpdate(p.below(), Blocks.DIRT.defaultBlockState());
        for (int y = 0; y < 3; y++)
          h.getLevel().setBlockAndUpdate(p.above(y), Blocks.DARK_OAK_LOG.defaultBlockState());
        h.getLevel()
            .setBlockAndUpdate(
                p.above(3),
                Blocks.DARK_OAK_LEAVES
                    .defaultBlockState()
                    .setValue(LeavesBlock.PERSISTENT, false)
                    .setValue(LeavesBlock.DISTANCE, 1));
      }
    pet.getBag().setStackInSlot(0, new ItemStack(Items.DARK_OAK_SAPLING, 4));
    final LoggingSlime[] current = {pet};
    h.startSequence()
        .thenWaitUntil(
            () -> h.assertTrue(pet.getPendingPlantings() == 4, "Wait for four reserved saplings"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  pet.getPendingPlantings() == 4,
                  "All four saplings reserved before cutting; pending="
                      + pet.getPendingPlantings()
                      + " status="
                      + pet.getFieldStatus()
                      + " seeds="
                      + count(pet, Items.DARK_OAK_SAPLING));
              CompoundTag saved = pet.saveWithoutId(new CompoundTag());
              pet.discard();
              h.runAfterDelay(
                  1,
                  () -> {
                    var loaded = ModEntities.LOGGING_SLIME.get().create(h.getLevel());
                    loaded.setWorking(true);
                    loaded.load(saved);
                    h.getLevel().addFreshEntity(loaded);
                    current[0] = loaded;
                  });
            });
    h.startSequence()
        .thenWaitUntil(
            () -> {
              for (int x = 0; x < 2; x++)
                for (int z = 0; z < 2; z++)
                  h.assertTrue(
                      h.getLevel().getBlockState(base.offset(x, 0, z)).is(Blocks.DARK_OAK_SAPLING),
                      "Wait for all original stumps to be replanted; status="
                          + current[0].getFieldStatus()
                          + " logs="
                          + count(current[0], Items.DARK_OAK_LOG));
            })
        .thenExecute(
            () -> {
              var loaded = current[0];
              for (int x = 0; x < 2; x++)
                for (int z = 0; z < 2; z++)
                  h.assertTrue(
                      h.getLevel().getBlockState(base.offset(x, 0, z)).is(Blocks.DARK_OAK_SAPLING),
                      "Each original stump replanted after reload");
              h.assertTrue(
                  count(loaded, Items.DARK_OAK_LOG) == 12
                      && loaded.getPendingPlantings() == 0
                      && count(loaded, Items.DARK_OAK_SAPLING) == 0,
                  "No lost logs or duplicated reserved saplings");
              loaded.discard();
              area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_seven", timeoutTicks = 180)
  public static void loggingCollectsDroppedLogsAndSaplingsWithPickupDelay(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var pet = logger(h, area);
    var log =
        new net.minecraft.world.entity.item.ItemEntity(
            h.getLevel(), pet.getX(), 101, pet.getZ() + 1, new ItemStack(Items.OAK_LOG, 3));
    log.setPickUpDelay(40);
    h.getLevel().addFreshEntity(log);
    var seed =
        new net.minecraft.world.entity.item.ItemEntity(
            h.getLevel(), pet.getX() + 1, 101, pet.getZ(), new ItemStack(Items.OAK_SAPLING, 2));
    seed.setPickUpDelay(40);
    h.getLevel().addFreshEntity(seed);
    h.runAfterDelay(
        20,
        () ->
            h.assertTrue(
                count(pet, Items.OAK_LOG) == 0 && count(pet, Items.OAK_SAPLING) == 0,
                "Delayed items stay in the world"));
    h.runAfterDelay(
        110,
        () -> {
          h.assertTrue(
              count(pet, Items.OAK_LOG) == 3
                  && count(pet, Items.OAK_SAPLING) == 2
                  && pet.getActivityProgress() >= 2,
              "Wood and saplings collected after delay with real activity");
          area.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_seven", timeoutTicks = 200)
  public static void changedTreeIsRevalidatedWhenMissingSaplingsArrive(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var pet = logger(h, area);
    var base = tree(h, area, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 3);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    pet.getWorkStatus().reason() == WorkStatus.Reason.SEEDS_MISSING,
                    "Initial tree waits for saplings; status="
                        + pet.getFieldStatus()
                        + " pos="
                        + pet.position()
                        + " ticking="
                        + h.getLevel().isPositionEntityTicking(pet.blockPosition())))
        .thenExecute(
            () -> {
              h.getLevel().setBlockAndUpdate(base.above(2), Blocks.STONE.defaultBlockState());
              pet.getBag().setStackInSlot(0, new ItemStack(Items.OAK_SAPLING));
            })
        .thenIdle(85)
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockState(base).is(Blocks.OAK_LOG)
                      && h.getLevel().getBlockState(base.above(2)).is(Blocks.STONE)
                      && count(pet, Items.OAK_LOG) == 0
                      && count(pet, Items.OAK_SAPLING) == 1,
                  "Stale tree plan is rejected without cutting or reserving saplings");
              area.close();
            })
        .thenSucceed();
  }
}
