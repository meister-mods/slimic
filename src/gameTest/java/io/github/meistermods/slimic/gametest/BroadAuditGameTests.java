package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.menu.*;
import io.github.meistermods.slimic.recipe.CompanionRecipe;
import io.github.meistermods.slimic.registry.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.gametest.*;
import net.minecraftforge.items.IItemHandler;

/** Independent boundary cases complementing the profession integration suites. */
@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BroadAuditGameTests {
  private static FriendlySlime pet(GameTestHelper h, Player owner) {
    var pet = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    pet.tame(owner);
    return pet;
  }

  private static TransientCraftingContainer grid() {
    return new TransientCraftingContainer(
        new AbstractContainerMenu(null, 0) {
          public boolean stillValid(Player p) {
            return true;
          }

          public ItemStack quickMoveStack(Player p, int i) {
            return ItemStack.EMPTY;
          }
        },
        2,
        2);
  }

  private static int count(IItemHandler bag, Item item) {
    int count = 0;
    for (int i = 0; i < bag.getSlots(); i++)
      if (bag.getStackInSlot(i).is(item)) count += bag.getStackInSlot(i).getCount();
    return count;
  }

  @GameTest(template = "empty", batch = "broad_audit")
  public static void malformedStatsAndEnergyAreSanitizedOnLoad(GameTestHelper h) {
    var pet = pet(h, h.makeMockSurvivalPlayer());
    var data = pet.saveWithoutId(new CompoundTag());
    var levels = new CompoundTag();
    var tuned = new CompoundTag();
    for (var stat : SlimeStat.values()) {
      levels.putInt(stat.key(), Integer.MAX_VALUE);
      tuned.putDouble(stat.key(), Double.NaN);
    }
    data.put("SlimeStats", levels);
    data.put("TunedStats", tuned);
    data.putFloat("Stamina", Float.NaN);
    data.putInt("EarnedPoints", Integer.MAX_VALUE);
    data.putInt("ActivityProgress", Integer.MAX_VALUE);
    pet.load(data);
    for (var stat : SlimeStat.values())
      h.assertTrue(
          pet.getStatLevel(stat) == stat.maxLevel() && !pet.hasTunedStat(stat),
          "Finite bounded stats: " + stat);
    h.assertTrue(
        Float.isFinite(pet.getStamina()) && pet.getStamina() == pet.getMaxStamina(),
        "Malformed energy restored safely");
    h.assertTrue(
        pet.getNextPointCost() <= 4096 && pet.getActivityProgress() < pet.getNextPointCost(),
        "Growth bounded");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "broad_audit")
  public static void unsupportedRecoverySchemaFailsWithoutMutatingPayload(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var item = FriendlySlimeItem.fromSlime(pet(h, owner));
    item.getTag().getCompound("SlimeData").putInt(SlimeData.VERSION_KEY, SlimeData.VERSION + 1);
    var before = item.copy();
    h.assertTrue(
        !((FriendlySlimeItem) item.getItem())
            .revive(h.getLevel(), owner, item, h.absolutePos(new BlockPos(3, 2, 3))),
        "Future data rejected");
    h.assertTrue(ItemStack.matches(before, item), "Failed placement preserves the complete input");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "broad_audit")
  public static void specializationAssemblyDoesNotMutateNamedWornInputs(GameTestHelper h) {
    var kinds =
        new CompanionRecipe.Kind[] {
          CompanionRecipe.Kind.COMBAT,
          CompanionRecipe.Kind.CLEANING,
          CompanionRecipe.Kind.SADDLE,
          CompanionRecipe.Kind.FARMER,
          CompanionRecipe.Kind.LUMBERJACK
        };
    var items =
        new Item[] {
          Items.IRON_SWORD, Items.ROTTEN_FLESH, Items.SADDLE, Items.IRON_HOE, Items.IRON_AXE
        };
    for (int i = 0; i < kinds.length; i++) {
      var friendly = FriendlySlimeItem.fromSlime(pet(h, h.makeMockSurvivalPlayer()));
      var tool = new ItemStack(items[i]);
      tool.setHoverName(net.minecraft.network.chat.Component.literal("Exact ingredient"));
      if (tool.isDamageableItem()) tool.setDamageValue(7);
      tool.getOrCreateTag().putString("AuditMark", "preserved");
      var before = tool.copy();
      var original = friendly.copy();
      var grid = grid();
      grid.setItem(0, friendly);
      grid.setItem(3, tool);
      var recipe =
          new CompanionRecipe(
              ResourceLocation.fromNamespaceAndPath("slimic", "audit"),
              CraftingBookCategory.MISC,
              kinds[i]);
      h.assertTrue(recipe.matches(grid, h.getLevel()), "Valid ingredients " + kinds[i]);
      var result = recipe.assemble(grid, h.getLevel().registryAccess());
      h.assertTrue(
          result
              .getTag()
              .getCompound("SlimeData")
              .equals(friendly.getTag().getCompound("SlimeData")),
          "Complete companion saved state");
      h.assertTrue(
          ItemStack.matches(before, tool) && ItemStack.matches(original, friendly),
          "Assembly never consumes or edits inputs");
      grid.setItem(2, new ItemStack(Items.DIRT));
      h.assertTrue(!recipe.matches(grid, h.getLevel()), "Unexpected ingredient rejected");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "broad_audit")
  public static void canceledAndZeroHealingDoNotConsumeSlimeballs(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var pet = pet(h, owner);
    pet.setHealth(5);
    owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SLIME_BALL, 3));
    Consumer<LivingHealEvent> cancel =
        event -> {
          if (event.getEntity() == pet) event.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(cancel);
    try {
      pet.mobInteract(owner, InteractionHand.MAIN_HAND);
    } finally {
      MinecraftForge.EVENT_BUS.unregister(cancel);
    }
    h.assertTrue(
        pet.getHealth() == 5 && owner.getMainHandItem().getCount() == 3,
        "Canceled healing keeps the ball");
    Consumer<LivingHealEvent> zero =
        event -> {
          if (event.getEntity() == pet) event.setAmount(0);
        };
    MinecraftForge.EVENT_BUS.addListener(zero);
    try {
      pet.mobInteract(owner, InteractionHand.MAIN_HAND);
    } finally {
      MinecraftForge.EVENT_BUS.unregister(zero);
    }
    h.assertTrue(
        pet.getHealth() == 5 && owner.getMainHandItem().getCount() == 3,
        "Zero healing keeps the ball");
    pet.mobInteract(owner, InteractionHand.MAIN_HAND);
    h.assertTrue(
        pet.getHealth() == 9 && owner.getMainHandItem().getCount() == 2,
        "Ordinary healing consumes exactly one");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "broad_audit")
  public static void spectatorTransitionInvalidatesAlreadyOpenManagement(GameTestHelper h) {
    boolean[] spectator = {false};
    var owner =
        new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "AuditOwner")) {
          @Override
          public boolean isSpectator() {
            return spectator[0];
          }
        };
    var pet = pet(h, owner);
    owner.moveTo(pet.position());
    var menu = TestMenus.create(1, owner.getInventory(), pet);
    h.assertTrue(menu.stillValid(owner), "Initial management available");
    spectator[0] = true;
    h.assertTrue(
        !menu.stillValid(owner) && !menu.clickMenuButton(owner, SlimeMenu.WAIT),
        "Spectator cannot keep using an already open menu");
    h.assertTrue(!pet.isOrderedToSit(), "Rejected command has no effect");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "broad_audit")
  public static void invalidCreativePayloadsAreRejectedAtomically(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    owner.getAbilities().instabuild = true;
    var pet = pet(h, owner);
    owner.moveTo(pet.position());
    owner.setItemInHand(
        InteractionHand.MAIN_HAND, new ItemStack(ModItems.CREATIVE_SLIME_TUNER.get()));
    var menu = new CreativeSlimeTunerMenu(1, owner.getInventory(), pet);
    double[] valid =
        Arrays.stream(SlimeStat.values()).mapToDouble(s -> s.tunerValue(pet)).toArray();
    for (int i = 0; i < valid.length; i++) {
      for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1}) {
        var values = valid.clone();
        values[i] = bad;
        h.assertTrue(!menu.apply(owner, values, 10, 50, 0), "Invalid payload rejected at " + i);
        for (var stat : SlimeStat.values())
          h.assertTrue(stat.tunerValue(pet) == valid[stat.ordinal()], "Atomic rejection");
      }
    }
    h.assertTrue(!menu.apply(owner, new double[8], 10, 50, 0), "Wrong payload size rejected");
    h.assertTrue(!menu.apply(owner, valid, Float.NaN, 50, 0), "Non-finite health rejected");
    h.assertTrue(
        !menu.apply(owner, valid, 10, Float.POSITIVE_INFINITY, 0), "Non-finite stamina rejected");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "broad_audit")
  public static void guardianQuickMoveRespectsPlayerSlotLimitsAndTags(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    owner.getInventory().clearContent();
    var guard = ModEntities.GUARDIAN_ANGEL_SLIME.get().create(h.getLevel());
    guard.setWaiting(false);
    guard.tame(owner);
    owner.moveTo(guard.position());
    var stack = new ItemStack(Items.TOTEM_OF_UNDYING, 64);
    stack.getOrCreateTag().putString("Mark", "original");
    guard.setTotems(stack);
    var menu = TestMenus.create(1, owner.getInventory(), guard);
    menu.quickMoveStack(owner, 0);
    int moved = 0;
    for (int slot = 0; slot < 36; slot++) {
      var item = owner.getInventory().getItem(slot);
      if (item.is(Items.TOTEM_OF_UNDYING)) {
        h.assertTrue(
            item.getCount() == 1 && item.getTag().getString("Mark").equals("original"),
            "Native max stack and exact tags");
        moved++;
      }
    }
    h.assertTrue(moved == 36 && guard.getTotems().getCount() == 28, "Overflow remains in reserve");
    menu.quickMoveStack(owner, 0);
    h.assertTrue(guard.getTotems().getCount() == 28, "Full player inventory cannot lose reserve");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "broad_audit")
  public static void capacityAndRecoveryScaleAtEveryCommonLevel(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var carrier = ModEntities.TRANSPORT_SLIME.get().create(h.getLevel());
    carrier.setWorking(true);
    carrier.tame(owner);
    var cleaner = ModEntities.CLEANING_SLIME.get().create(h.getLevel());
    cleaner.setWorking(true);
    cleaner.tame(owner);
    for (int level = 0; level <= SlimeStat.CAPACITY.maxLevel(); level++) {
      for (var pet : List.of(carrier, cleaner)) {
        var data = pet.saveWithoutId(new CompoundTag());
        var stats = data.getCompound("SlimeStats");
        stats.putInt("capacity", level);
        stats.putInt("stamina", level);
        data.put("SlimeStats", stats);
        pet.load(data);
        h.assertTrue(
            Math.abs(pet.getStaminaRecoveryPerSecond() / pet.getMaxStamina() - .02) < .00001,
            "Proportional recovery");
      }
      h.assertTrue(
          carrier.getCapacity() == cleaner.getCapacity() * 2
              && cleaner.getCapacity() == 9 + 3 * level,
          "Carrier multiplier at " + level);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "broad_audit", timeoutTicks = 50)
  public static void freshMinerNeverEditsTerrainWithoutAnExplicitOrder(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    owner.moveTo(a.point(8, 101, 8));
    var summon = new ItemStack(ModItems.MINING_SLIME.get());
    summon
        .getOrCreateTag()
        .put("InitialPickaxe", new ItemStack(Items.IRON_PICKAXE).save(new CompoundTag()));
    owner.setItemInHand(InteractionHand.MAIN_HAND, summon);
    var floor = BlockPos.containing(a.point(8, 100, 8));
    h.assertTrue(
        summon
            .useOn(
                new net.minecraft.world.item.context.UseOnContext(
                    owner,
                    InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(
                        Vec3.atCenterOf(floor), Direction.UP, floor, false)))
            .consumesAction(),
        "Actual summon item succeeds");
    var stone = BlockPos.containing(a.point(10, 101, 8));
    h.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
    h.runAfterDelay(
        30,
        () -> {
          var miners =
              h.getLevel()
                  .getEntitiesOfClass(
                      MiningSlime.class, new net.minecraft.world.phys.AABB(floor).inflate(8));
          h.assertTrue(
              miners.size() == 1,
              "One summoned miner after entity registration; count=" + miners.size());
          var miner = miners.get(0);
          h.assertTrue(
              !miner.isWorking()
                  && miner.isOrderedToSit()
                  && h.getLevel().getBlockState(stone).is(Blocks.STONE)
                  && miner.getTool(0).getDamageValue() == 0,
              "No implicit digging or wear");
          miner.discard();
          a.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "broad_audit")
  public static void workSearchNeverLoadsUnloadedChunksOrChangesForceFlags(GameTestHelper h) {
    var forced = new HashSet<>(h.getLevel().getForcedChunks());
    for (int i = 0; i < 64; i++) {
      var pos = new ChunkPos(700000 + i, -700000);
      h.assertTrue(
          h.getLevel().getChunkSource().getChunkNow(pos.x, pos.z) == null, "Initially unloaded");
      var result = WorkSearch.inChunk(h.getLevel(), pos);
      h.assertTrue(
          result.chests().isEmpty()
              && result.workers().isEmpty()
              && h.getLevel().getChunkSource().getChunkNow(pos.x, pos.z) == null,
          "Search stays loaded-only");
    }
    h.assertTrue(forced.equals(h.getLevel().getForcedChunks()), "No chunk force flags changed");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "broad_audit")
  public static void movingFarmersRequiresANewFarmlandSnapshot(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var farmer = ModEntities.FARMER_SLIME.get().create(h.getLevel());
    farmer.tame(h.makeMockSurvivalPlayer());
    farmer.moveTo(a.point(4, 101, 4));
    farmer.onReleased();
    h.getLevel()
        .setBlockAndUpdate(
            BlockPos.containing(a.point(6, 100, 4)), Blocks.FARMLAND.defaultBlockState());
    farmer.setWorking(true);
    var data = farmer.saveWithoutId(new CompoundTag());
    h.assertTrue(data.getLongArray("FarmPlots").length == 1, "Original plot snapshot saved");
    farmer.moveTo(a.point(12, 101, 12));
    farmer.onReleased();
    h.assertTrue(
        !farmer.isWorking() && farmer.isOrderedToSit() && farmer.getFarmPlots().isEmpty(),
        "Relocation clears old plot edits and waits");
    a.close();
    h.succeed();
  }

  private static LumberjackSlime logger(GameTestHelper h, LoadedWorkArea a) {
    a.clearSurroundingTrees();
    var pet = ModEntities.LUMBERJACK_SLIME.get().create(h.getLevel());
    pet.setWorking(true);
    pet.tame(h.makeMockSurvivalPlayer());
    pet.moveTo(a.point(4, 101, 5));
    pet.setTool(new ItemStack(Items.IRON_AXE));
    pet.onReleased();
    h.getLevel().addFreshEntity(pet);
    return pet;
  }

  private static BlockPos tree(GameTestHelper h, LoadedWorkArea a) {
    var pos = BlockPos.containing(a.point(6, 101, 5));
    h.getLevel().setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState());
    h.getLevel().setBlockAndUpdate(pos, Blocks.OAK_LOG.defaultBlockState());
    h.getLevel()
        .setBlockAndUpdate(
            pos.above(),
            Blocks.OAK_LEAVES
                .defaultBlockState()
                .setValue(LeavesBlock.PERSISTENT, false)
                .setValue(LeavesBlock.DISTANCE, 1));
    return pos;
  }

  @GameTest(template = "empty", batch = "broad_audit", timeoutTicks = 260)
  public static void removedTreeClearsStaleSaplingRequirements(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var pet = logger(h, a);
    // Keep other naturally generated trees outside this fixture's work radius.
    SlimeStat.DETECTION_RANGE.tune(pet, 2);
    var root = tree(h, a);
    h.startSequence()
        .thenWaitUntil(
            () -> h.assertTrue(pet.getRequiredSaplingCount() == 1, "Missing seed detected"))
        .thenExecute(
            () -> {
              h.getLevel().setBlockAndUpdate(root, Blocks.AIR.defaultBlockState());
              h.getLevel().setBlockAndUpdate(root.above(), Blocks.AIR.defaultBlockState());
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    pet.getRequiredSapling().isEmpty()
                        && pet.getRequiredSaplingCount() == 0
                        && pet.getFieldStatus() != FieldSlime.Status.NO_SEEDS,
                    "Removed tree cannot keep requesting saplings"))
        .thenExecute(
            () -> {
              pet.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "broad_audit", timeoutTicks = 350)
  public static void unreachableDroppedMaterialCannotStarveReachableForestry(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var pet = logger(h, a);
    var root = BlockPos.containing(a.point(6, 101, 5));
    pet.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
    var cell = BlockPos.containing(a.point(11, 101, 5));
    for (int x = -1; x <= 1; x++)
      for (int y = 0; y <= 2; y++)
        for (int z = -1; z <= 1; z++)
          if (x != 0 || y != 0 || z != 0)
            h.getLevel()
                .setBlockAndUpdate(cell.offset(x, y, z), Blocks.BEDROCK.defaultBlockState());
    var item =
        new ItemEntity(
            h.getLevel(),
            cell.getX() + .5,
            cell.getY(),
            cell.getZ() + .5,
            new ItemStack(Items.OAK_LOG));
    item.setNoGravity(true);
    item.setDeltaMovement(Vec3.ZERO);
    item.setNoPickUpDelay();
    h.getLevel().addFreshEntity(item);
    var protectedItem =
        new ItemEntity(h.getLevel(), pet.getX() + 1, 101, pet.getZ(), new ItemStack(Items.OAK_LOG));
    protectedItem.setNoPickUpDelay();
    h.getLevel().addFreshEntity(protectedItem);
    int[] attempts = {0};
    Consumer<EntityItemPickupEvent> cancel =
        event -> {
          if (event.getItem() == protectedItem) {
            attempts[0]++;
            event.setCanceled(true);
          }
        };
    MinecraftForge.EVENT_BUS.addListener(cancel);
    h.runAfterDelay(349, () -> MinecraftForge.EVENT_BUS.unregister(cancel));
    h.runAfterDelay(
        40,
        () -> {
          tree(h, a);
          pet.getBag().setStackInSlot(0, new ItemStack(Items.OAK_SAPLING));
        });
    h.succeedWhen(
        () -> {
          h.assertTrue(
              h.getLevel().getBlockState(root).is(Blocks.OAK_SAPLING),
              "Unreachable and protected drops must not prevent reachable tree work; status="
                  + pet.getFieldStatus());
          h.assertTrue(
              item.isAlive()
                  && item.getItem().getCount() == 1
                  && protectedItem.isAlive()
                  && protectedItem.getItem().getCount() == 1
                  && attempts[0] > 0,
              "Unreachable and protected drops untouched");
          MinecraftForge.EVENT_BUS.unregister(cancel);
          protectedItem.discard();
          item.discard();
          pet.discard();
          a.close();
        });
  }

  @GameTest(template = "empty", batch = "broad_audit", timeoutTicks = 240)
  public static void concurrentCarriersConserveExactCargoAtOneChest(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var chest = a.chest(8, 101, 8);
    var first = a.carrier(new Vec3(6, 101, 8));
    var second = a.carrier(new Vec3(10, 101, 8));
    var named = new ItemStack(Items.COBBLESTONE, 32);
    named.getOrCreateTag().putString("Mark", "cargo");
    first.getBag().setStackInSlot(0, named.copy());
    second.getBag().setStackInSlot(0, named.copy());
    h.succeedWhen(
        () -> {
          h.assertTrue(
              count(chest.inventory(), Items.COBBLESTONE) == 64
                  && !first.hasCargo()
                  && !second.hasCargo(),
              "Both cargos delivered exactly once");
          h.assertTrue(
              chest.getItem(0).getTag().getString("Mark").equals("cargo"), "Exact cargo tags");
          first.discard();
          second.discard();
          a.close();
        });
  }

  @GameTest(template = "empty", batch = "broad_audit", timeoutTicks = 100)
  public static void canceledCleaningPickupPreservesItemsUntilPermissionReturns(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var pet = ModEntities.CLEANING_SLIME.get().create(h.getLevel());
    pet.setWorking(true);
    pet.tame(h.makeMockSurvivalPlayer());
    pet.moveTo(a.point(6, 101, 6));
    pet.onReleased();
    h.getLevel().addFreshEntity(pet);
    var item =
        new ItemEntity(
            h.getLevel(), pet.getX() + 1, 101, pet.getZ(), new ItemStack(Items.DIAMOND, 7));
    item.setNoPickUpDelay();
    h.getLevel().addFreshEntity(item);
    Consumer<EntityItemPickupEvent> cancel =
        event -> {
          if (event.getItem() == item) event.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(cancel);
    h.runAfterDelay(90, () -> MinecraftForge.EVENT_BUS.unregister(cancel));
    h.runAfterDelay(
        35,
        () -> {
          MinecraftForge.EVENT_BUS.unregister(cancel);
          h.assertTrue(
              item.isAlive() && item.getItem().getCount() == 7 && !pet.hasCargo(),
              "Canceled pickup preserves all items");
        });
    h.succeedWhen(
        () -> {
          h.assertTrue(
              count(pet.getBag(), Items.DIAMOND) == 7 && !item.isAlive(),
              "Pickup resumes when permitted");
          pet.discard();
          a.close();
        });
  }

  @GameTest(template = "empty", batch = "broad_audit")
  public static void canceledSignalSpawnDoesNotConsumeManualCooldown(GameTestHelper h) {
    var herald = ModEntities.HERALD_SLIME.get().create(h.getLevel());
    herald.setMonitoring(true);
    herald.tame(h.makeMockSurvivalPlayer());
    Consumer<EntityJoinLevelEvent> cancel =
        event -> {
          if (event.getEntity() instanceof FireworkRocketEntity rocket
              && rocket.getOwner() == herald) event.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(cancel);
    try {
      h.assertTrue(!herald.launchManually(), "Canceled signal reports failure");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(cancel);
    }
    h.assertTrue(
        herald.launchManually() && !herald.launchManually(),
        "Retry works immediately then cooldown applies");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "broad_audit")
  public static void combatRevalidatesOrdersAndTargetSwitchesBeforeDamage(GameTestHelper h) {
    var fighter = ModEntities.COMBAT_SLIME.get().create(h.getLevel());
    fighter.setWorking(true);
    fighter.tame(h.makeMockSurvivalPlayer());
    fighter.setEquipment(0, new ItemStack(Items.IRON_SWORD));
    var cow = EntityType.COW.create(h.getLevel());
    float health = cow.getHealth();
    fighter.setAllowed(CombatTarget.PASSIVE, true);
    fighter.setWorking(false);
    h.assertTrue(!fighter.doHurtTarget(cow), "Follow order cannot attack");
    fighter.setWorking(true);
    fighter.setWaiting(true);
    h.assertTrue(!fighter.doHurtTarget(cow), "Wait order cannot attack");
    fighter.setWaiting(false);
    fighter.setAllowed(CombatTarget.PASSIVE, false);
    h.assertTrue(
        !fighter.doHurtTarget(cow)
            && cow.getHealth() == health
            && fighter.getEquipment(0).getDamageValue() == 0
            && fighter.getStamina() == 100,
        "Disabled target incurs no damage, wear or energy cost");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "broad_audit")
  public static void guardianReserveRejectsMismatchedTagsAndSurvivesReloadAtLimit(
      GameTestHelper h) {
    var guard = ModEntities.GUARDIAN_ANGEL_SLIME.get().create(h.getLevel());
    guard.setWaiting(false);
    guard.tame(h.makeMockSurvivalPlayer());
    var stack = new ItemStack(Items.TOTEM_OF_UNDYING, 64);
    stack.getOrCreateTag().putString("Mark", "kept");
    guard.setTotems(stack);
    h.assertTrue(
        guard.addTotems(stack) == 0 && guard.getTotems().getCount() == 64,
        "Full reserve never overflows");
    guard.setTotems(stack.copyWithCount(3));
    var different = stack.copyWithCount(1);
    different.getTag().putString("Mark", "other");
    h.assertTrue(
        guard.addTotems(different) == 0 && guard.getTotems().getCount() == 3,
        "Different NBT cannot merge");
    var data = guard.saveWithoutId(new CompoundTag());
    var copy = ModEntities.GUARDIAN_ANGEL_SLIME.get().create(h.getLevel());
    copy.setWaiting(false);
    copy.load(data);
    h.assertTrue(
        ItemStack.matches(copy.getTotems(), guard.getTotems())
            && copy.getMaxHealth() == guard.getMaxHealth(),
        "Reserve and multiplier survive load");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "broad_audit")
  public static void removingMountEquipmentRevokesFlightAndRiderControl(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var mount = ModEntities.SADDLE_SLIME.get().create(h.getLevel());
    mount.tame(owner);
    owner.moveTo(mount.position());
    mount.setEquipment(0, new ItemStack(Items.SADDLE));
    mount.setEquipment(1, new ItemStack(Items.ELYTRA));
    h.assertTrue(
        mount.mount(owner) && mount.isFlyingMount() && mount.setRiderInput(owner, true, true),
        "Authorized flight input");
    h.assertTrue(
        !mount.setRiderInput(h.makeMockSurvivalPlayer(), true, true), "Foreign input rejected");
    mount.setEquipment(1, ItemStack.EMPTY);
    h.assertTrue(!mount.isFlyingMount(), "Removing wings revokes flight immediately");
    mount.setEquipment(0, ItemStack.EMPTY);
    h.assertTrue(
        owner.getVehicle() == null
            && !mount.setRiderInput(owner, true, true)
            && !mount.isBoosting(),
        "Removing saddle ejects rider and clears controls");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "broad_audit")
  public static void chestMultipleViewersAndAutomationConserveInventory(GameTestHelper h)
      throws Exception {
    var a = new LoadedWorkArea(h);
    var chest = a.chest(8, 101, 8);
    var first = h.makeMockSurvivalPlayer();
    var second = h.makeMockSurvivalPlayer();
    chest.startOpen(first);
    chest.startOpen(second);
    var field = chest.getClass().getDeclaredField("openers");
    field.setAccessible(true);
    var counter = (ContainerOpenersCounter) field.get(chest);
    h.assertTrue(counter.getOpenerCount() == 2, "Two independent openers");
    chest.stopOpen(first);
    h.assertTrue(counter.getOpenerCount() == 1, "First close retains second opener");
    var stack = new ItemStack(Items.DIAMOND, 64);
    stack.getOrCreateTag().putString("Mark", "chest");
    h.assertTrue(
        chest.inventory().insertItem(0, stack, false).isEmpty(), "Automation works with open lid");
    var saved = chest.saveWithFullMetadata();
    chest.load(saved);
    h.assertTrue(ItemStack.matches(chest.getItem(0), stack), "Inventory survives save and reload");
    chest.stopOpen(second);
    h.assertTrue(
        counter.getOpenerCount() == 0 && chest.getItem(0).getCount() == 64,
        "Final close preserves inventory");
    a.close();
    h.succeed();
  }
}
