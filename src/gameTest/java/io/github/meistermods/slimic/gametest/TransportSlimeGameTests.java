package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.SlimeChestBlockEntity;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.entity.TransportSlime;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.ModBlocks;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TransportSlimeGameTests {
  private static final BlockPos MINER = new BlockPos(3, 2, 5);
  private static final BlockPos CARRIER = new BlockPos(5, 2, 5);
  private static final BlockPos CHEST = new BlockPos(8, 2, 5);

  private TransportSlimeGameTests() {}

  @GameTest(template = "empty")
  public static void recipesUsePlanksAndPlainChestAndPreserveTraits(GameTestHelper helper) {
    var grid = grid();
    for (int slot = 0; slot < 9; slot++)
      grid.setItem(slot, new ItemStack(slot == 4 ? Items.SLIME_BALL : Items.MANGROVE_PLANKS));
    var recipe =
        helper
            .getLevel()
            .getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel())
            .orElseThrow();
    helper.assertTrue(
        recipe.assemble(grid, helper.getLevel().registryAccess()).is(ModItems.SLIME_CHEST.get()),
        "Eight planks and a slime ball must craft a Slime Chest");
    FriendlySlime friendly = helper.spawn(ModEntities.FRIENDLY_SLIME.get(), MINER);
    friendly.tame(helper.makeMockSurvivalPlayer());
    friendly.setHealth(5);
    friendly.setCustomName(Component.literal("Porter"));
    ItemStack stored = FriendlySlimeItem.fromSlime(friendly);
    grid.clearContent();
    grid.setItem(0, stored);
    grid.setItem(8, new ItemStack(Items.CHEST));
    recipe =
        helper
            .getLevel()
            .getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel())
            .orElseThrow();
    ItemStack result = recipe.assemble(grid, helper.getLevel().registryAccess());
    helper.assertTrue(
        result.is(ModItems.TRANSPORT_SLIME.get()) && result.getTag().equals(stored.getTag()),
        "Specialization must preserve all saved companion data");
    helper.assertTrue(
        recipe.getRemainingItems(grid).stream().allMatch(ItemStack::isEmpty),
        "The chest must be consumed without duplication");
    grid.setItem(8, new ItemStack(ModItems.SLIME_CHEST.get()));
    helper.assertTrue(
        helper
            .getLevel()
            .getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel())
            .isEmpty(),
        "Specialization requires a regular chest");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void capacityIsExactlyDoubleAtEveryUpgrade(GameTestHelper helper) {
    MiningSlime miner = helper.spawn(ModEntities.MINING_SLIME.get(), MINER);
    TransportSlime carrier = helper.spawn(ModEntities.TRANSPORT_SLIME.get(), CARRIER);
    seed(miner, 6, 0, 100);
    seed(carrier, 6, 0, 100);
    for (int level = 0; level <= 6; level++) {
      helper.assertTrue(
          carrier.getCapacity() == miner.getCapacity() * 2
              && SlimeStat.CAPACITY.value(carrier) == SlimeStat.CAPACITY.value(miner) * 2,
          "Transport capacity must stay exactly double at level " + level);
      if (level < 6) {
        helper.assertTrue(
            carrier.upgradeStat(SlimeStat.CAPACITY) && miner.upgradeStat(SlimeStat.CAPACITY),
            "Both slimes must accept each Capacity upgrade");
      }
    }
    helper.assertTrue(
        carrier.getCapacity() == 54 && carrier.getBag().getSlots() == 54,
        "All 54 cargo slots must be backed by storage");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void pagedBagHasNoEquipmentSlotAndEnforcesAccess(GameTestHelper helper) {
    TransportSlime carrier = carrier(helper);
    carrier.setWorking(false);
    seed(carrier, 6, 0, 100);
    for (int level = 0; level < 6; level++) carrier.upgradeStat(SlimeStat.CAPACITY);
    Player owner = helper.makeMockSurvivalPlayer();
    carrier.tame(owner);
    owner.moveTo(carrier.position().add(1, 0, 0));
    SlimeMenu menu = new SlimeMenu(1, owner.getInventory(), carrier);
    carrier.getBag().setStackInSlot(53, new ItemStack(Items.EMERALD, 17));
    helper.assertTrue(
        menu.slots.size() == 90
            && !menu.getSlot(53).isActive()
            && !menu.getSlot(53).mayPickup(owner),
        "The carrier has 54 bag slots, 36 player slots, and no equipment slot");
    helper.assertTrue(
        menu.quickMoveStack(owner, 53).isEmpty(), "Hidden pages must reject direct transfers");
    helper.assertTrue(
        menu.clickMenuButton(owner, SlimeMenu.NEXT_PAGE) && menu.getSlot(53).isActive(),
        "The second page must expose the expanded bag");
    helper.assertTrue(
        !menu.quickMoveStack(owner, 53).isEmpty() && carrier.getBag().getStackInSlot(53).isEmpty(),
        "The final slot must support withdrawal");
    owner.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
    helper.assertTrue(
        !menu.quickMoveStack(owner, 81).isEmpty()
            && carrier.getMainHandItem().isEmpty()
            && count(carrier.getBag(), Items.IRON_PICKAXE) == 1,
        "Shift-clicked tools belong in cargo rather than equipment");
    Player stranger = helper.makeMockSurvivalPlayer();
    stranger.moveTo(owner.position());
    helper.assertTrue(
        !menu.clickMenuButton(stranger, SlimeMenu.PREVIOUS_PAGE)
            && menu.quickMoveStack(stranger, 27).isEmpty(),
        "Other players cannot manipulate the bag or page");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 400)
  public static void unloadsFullMinerAndResumesMining(GameTestHelper helper) {
    LoadedWorkArea arena = new LoadedWorkArea(helper);
    TransportSlime carrier = arena.carrier(new Vec3(8, 101, 8));
    MiningSlime miner = ModEntities.MINING_SLIME.get().create(helper.getLevel());
    miner.moveTo(arena.point(6, 101, 8));
    miner.setOwnerUUID(carrier.getOwnerUUID());
    miner.setTame(true);
    miner.setTool(0, new ItemStack(Items.IRON_PICKAXE));
    miner.onReleased();
    helper.getLevel().addFreshEntity(miner);
    for (int slot = 0; slot < 9; slot++)
      miner.getBag().setStackInSlot(slot, new ItemStack(Items.DIAMOND, 64));
    SlimeChestBlockEntity chest = arena.chest(11, 101, 8);
    BlockPos ore = BlockPos.containing(arena.point(5, 101, 8));
    helper.getLevel().setBlockAndUpdate(ore, Blocks.STONE.defaultBlockState());
    helper.succeedWhen(
        () -> {
          helper.assertTrue(
              count(chest.inventory(), Items.DIAMOND) == 576
                  && count(carrier.getBag(), Items.DIAMOND) == 0
                  && count(miner.getBag(), Items.DIAMOND) == 0,
              "All mined cargo must arrive exactly once in the chest: chest="
                  + count(chest.inventory(), Items.DIAMOND)
                  + ", carrier="
                  + count(carrier.getBag(), Items.DIAMOND)
                  + ", miner="
                  + count(miner.getBag(), Items.DIAMOND)
                  + ", hauling="
                  + carrier.getTransportStatus()
                  + " at "
                  + carrier.position()
                  + ", mining="
                  + miner.getMiningStatus()
                  + " at "
                  + miner.position());
          helper.assertTrue(
              !helper.getLevel().getBlockState(ore).is(Blocks.STONE),
              "Emptying the bag must allow the target block to be mined");
          helper.assertTrue(
              count(miner.getBag(), Items.COBBLESTONE)
                      + count(carrier.getBag(), Items.COBBLESTONE)
                      + count(chest.inventory(), Items.COBBLESTONE)
                  == 1,
              "Emptying the bag must let mining resume");
          helper.assertTrue(
              carrier.getActivityProgress()
                      >= 9
                          * io.github.meistermods.slimic.config.SlimicConfig.TRANSPORT_ACTIVITY
                              .get()
                  && carrier.getStatPoints() == 0,
              "Deliveries must grant slow activity progress");
          carrier.discard();
          miner.discard();
          arena.close();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 300)
  public static void fullChestKeepsCargoUntilSpaceAppears(GameTestHelper helper) {
    TransportSlime carrier = carrier(helper);
    carrier.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 10));
    SlimeChestBlockEntity chest = chest(helper, CHEST);
    fill(chest, Items.COBBLESTONE);
    helper.runAfterDelay(
        60,
        () -> {
          helper.assertTrue(
              carrier.getTransportStatus() == TransportSlime.Status.CHESTS_FULL
                  && count(carrier.getBag(), Items.DIAMOND) == 10
                  && carrier.getActivityProgress() == 0,
              "A full destination must preserve cargo and grant no activity");
          chest.setItem(0, ItemStack.EMPTY);
        });
    helper.succeedWhen(
        () ->
            helper.assertTrue(
                count(chest.inventory(), Items.DIAMOND) == 10 && !carrier.hasCargo(),
                "Hauling must resume after the chest has room"));
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void ordinaryChestsAreNotLogisticsEndpoints(GameTestHelper helper) {
    TransportSlime carrier = carrier(helper);
    carrier.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 10));
    helper.setBlock(CHEST, Blocks.CHEST);
    helper.runAfterDelay(
        80,
        () -> {
          helper.assertTrue(
              carrier.getTransportStatus() == TransportSlime.Status.NO_CHEST
                  && count(carrier.getBag(), Items.DIAMOND) == 10
                  && carrier.getActivityProgress() == 0,
              "Only marked Slime Chests may receive cargo");
          helper.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 300)
  public static void replacementToolRetainsDamageNameAndEnchantments(GameTestHelper helper) {
    TransportSlime carrier = carrier(helper);
    MiningSlime miner = miner(helper, carrier, ItemStack.EMPTY);
    SlimeChestBlockEntity chest = chest(helper, CHEST);
    ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
    tool.setDamageValue(99);
    tool.enchant(Enchantments.BLOCK_FORTUNE, 3);
    tool.setHoverName(Component.literal("Spare"));
    chest.setItem(0, new ItemStack(Items.STICK));
    chest.setItem(4, tool.copy());
    helper.succeedWhen(
        () -> {
          helper.assertTrue(
              ItemStack.matches(miner.getPickaxe(), tool),
              "The exact replacement tool must reach the miner");
          helper.assertTrue(
              chest.getItem(4).isEmpty()
                  && chest.getItem(0).is(Items.STICK)
                  && !carrier.hasCargo()
                  && carrier.getMainHandItem().isEmpty(),
              "Only the requested tool may leave stock, using cargo instead of equipment");
          helper.assertTrue(
              carrier.getActivityProgress()
                      == io.github.meistermods.slimic.config.SlimicConfig.TRANSPORT_ACTIVITY.get()
                  && carrier.getStatPoints() == 0,
              "One supplied tool earns the configured delivery activity");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 400)
  public static void brokenToolIsAutomaticallyReplaced(GameTestHelper helper) {
    TransportSlime carrier = carrier(helper);
    ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
    worn.setDamageValue(worn.getMaxDamage() - 1);
    MiningSlime miner = miner(helper, carrier, worn);
    SlimeChestBlockEntity chest = chest(helper, CHEST);
    chest.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
    BlockPos ore = ore(helper, miner);
    helper.setBlock(ore, Blocks.STONE);
    helper.succeedWhen(
        () -> {
          helper.assertBlockNotPresent(Blocks.STONE, ore);
          helper.assertTrue(
              miner.getPickaxe().is(Items.DIAMOND_PICKAXE) && chest.getItem(0).isEmpty(),
              "A miner whose last durability was consumed must receive a new tool");
          helper.assertTrue(
              count(miner.getBag(), Items.COBBLESTONE) == 1,
              "Replacing a tool must preserve mined resources");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void supportsOnlyWorkersWithTheSameOwner(GameTestHelper helper) {
    TransportSlime carrier = carrier(helper);
    MiningSlime stranger = miner(helper, carrier, ItemStack.EMPTY);
    stranger.tame(helper.makeMockSurvivalPlayer());
    SlimeChestBlockEntity chest = chest(helper, CHEST);
    chest.setItem(0, new ItemStack(Items.IRON_PICKAXE));
    helper.runAfterDelay(
        100,
        () -> {
          helper.assertTrue(
              stranger.getPickaxe().isEmpty()
                  && chest.getItem(0).is(Items.IRON_PICKAXE)
                  && !carrier.hasCargo(),
              "The carrier must not alter another player's worker or consume its spare tool");
          helper.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void waitingWorkersDoNotReceiveUnrequestedTools(GameTestHelper helper) {
    TransportSlime carrier = carrier(helper);
    MiningSlime miner = miner(helper, carrier, ItemStack.EMPTY);
    miner.setWaiting(true);
    SlimeChestBlockEntity chest = chest(helper, CHEST);
    chest.setItem(0, new ItemStack(Items.IRON_PICKAXE));
    helper.runAfterDelay(
        100,
        () -> {
          helper.assertTrue(
              miner.getPickaxe().isEmpty() && chest.getItem(0).is(Items.IRON_PICKAXE),
              "Waiting is a manual order, not a tool failure to repair");
          helper.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 300)
  public static void partialStacksUseAnotherChestWithoutLoss(GameTestHelper helper) {
    TransportSlime carrier = carrier(helper);
    carrier.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 64));
    carrier.getBag().setStackInSlot(1, new ItemStack(Items.DIAMOND, 6));
    SlimeChestBlockEntity first = chest(helper, new BlockPos(6, 2, 7));
    fill(first, Items.COBBLESTONE);
    first.setItem(0, new ItemStack(Items.DIAMOND, 63));
    SlimeChestBlockEntity second = chest(helper, CHEST);
    helper.succeedWhen(
        () ->
            helper.assertTrue(
                !carrier.hasCargo()
                    && count(first.inventory(), Items.DIAMOND)
                            + count(second.inventory(), Items.DIAMOND)
                        == 133,
                "Partial insertion must preserve every item and continue to another chest"));
  }

  @GameTest(template = "empty", timeoutTicks = 300)
  public static void twoCarriersDoNotDuplicateSpareTools(GameTestHelper helper) {
    TransportSlime first = carrier(helper);
    TransportSlime second = helper.spawn(ModEntities.TRANSPORT_SLIME.get(), CARRIER.south(2));
    second.setOwnerUUID(first.getOwnerUUID());
    second.setTame(true);
    second.onReleased();
    MiningSlime miner = miner(helper, first, ItemStack.EMPTY);
    SlimeChestBlockEntity chest = chest(helper, CHEST);
    chest.setItem(0, new ItemStack(Items.IRON_PICKAXE));
    chest.setItem(1, new ItemStack(Items.IRON_PICKAXE));
    helper.runAfterDelay(
        180,
        () -> {
          helper.assertTrue(
              miner.getPickaxe().is(Items.IRON_PICKAXE), "A carrier must supply the stopped miner");
          helper.assertTrue(
              count(chest.inventory(), Items.IRON_PICKAXE)
                      + count(first.getBag(), Items.IRON_PICKAXE)
                      + count(second.getBag(), Items.IRON_PICKAXE)
                      + miner.getPickaxe().getCount()
                  == 2,
              "Concurrent supply jobs must preserve both tools exactly");
          helper.succeed();
        });
  }

  @GameTest(template = "empty")
  public static void recoveryPreservesExpandedBagAndAllTraits(GameTestHelper helper) {
    TransportSlime carrier = carrier(helper);
    Player owner = helper.makeMockSurvivalPlayer();
    carrier.tame(owner);
    seed(carrier, 7, 37, 13);
    for (int i = 0; i < 6; i++) carrier.upgradeStat(SlimeStat.CAPACITY);
    carrier.getBag().setStackInSlot(53, new ItemStack(Items.DIAMOND, 17));
    ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
    tool.setDamageValue(31);
    carrier.getBag().setStackInSlot(27, tool.copy());
    carrier.setHealth(7);
    carrier.setCustomName(Component.literal("Porter"));
    carrier.setCustomNameVisible(true);
    carrier.setWorking(false);
    carrier.setWaiting(true);
    UUID identity = carrier.getUUID();
    carrier.capture(owner);
    ItemStack stored = owner.getInventory().getItem(0);
    helper.assertTrue(
        stored.is(ModItems.TRANSPORT_SLIME.get()) && stored.getTag().contains("SlimeData"),
        "Recovery must return the transport item type");
    owner.setItemInHand(InteractionHand.MAIN_HAND, stored);
    BlockPos floor = helper.absolutePos(CARRIER.east().below());
    var hit = new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false);
    stored
        .getItem()
        .useOn(new UseOnContext(helper.getLevel(), owner, InteractionHand.MAIN_HAND, stored, hit));
    TransportSlime restored =
        helper.getEntities(ModEntities.TRANSPORT_SLIME.get(), CARRIER, 7).get(0);
    helper.assertTrue(
        identity.equals(restored.getUUID())
            && restored.getHealth() == 7
            && restored.isOrderedToSit()
            && restored.hasCustomName(),
        "Identity, health, name, and manual order must survive");
    helper.assertTrue(
        restored.getCapacity() == 54
            && restored.getBag().getStackInSlot(53).getCount() == 17
            && ItemStack.matches(restored.getBag().getStackInSlot(27), tool),
        "Both bag pages and worn tools must survive recovery");
    helper.assertTrue(
        restored.getStatPoints() == 1
            && restored.getActivityProgress() == 37
            && restored.getStamina() == 13
            && restored.getWorkOrigin().equals(restored.blockPosition()),
        "Growth, stamina, and the new work-area anchor must be correct");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void chestPersistsInventoryAndExposesAutomation(GameTestHelper helper) {
    SlimeChestBlockEntity chest = chest(helper, CHEST);
    chest.setCustomName(Component.literal("Supply"));
    var handler =
        chest
            .getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN)
            .orElseThrow(() -> new IllegalStateException("Missing chest item capability"));
    helper.assertTrue(
        handler.insertItem(26, new ItemStack(Items.DIAMOND, 17), false).isEmpty(),
        "The chest must accept items through the Forge inventory capability");
    CompoundTag saved = chest.saveWithFullMetadata();
    var restored =
        (SlimeChestBlockEntity)
            BlockEntity.loadStatic(chest.getBlockPos(), chest.getBlockState(), saved);
    helper.assertTrue(
        restored != null
            && restored.getContainerSize() == 27
            && restored.getItem(26).getCount() == 17
            && restored.getDisplayName().getString().equals("Supply"),
        "World saves must preserve the full chest inventory and custom name");
    Player viewer = helper.makeMockSurvivalPlayer();
    helper.assertTrue(
        chest.createMenu(1, viewer.getInventory(), viewer).slots.size() == 63,
        "The chest must expose a standard 27-slot menu");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void breakingChestDropsCargoExactlyOnce(GameTestHelper helper) {
    SlimeChestBlockEntity chest = chest(helper, CHEST);
    chest.setItem(0, new ItemStack(Items.DIAMOND, 17));
    helper.getLevel().destroyBlock(helper.absolutePos(CHEST), true);
    helper.runAfterDelay(
        4,
        () -> {
          int diamonds = 0, boxes = 0;
          for (ItemEntity drop :
              helper.getEntities(net.minecraft.world.entity.EntityType.ITEM, CHEST, 4)) {
            if (drop.getItem().is(Items.DIAMOND)) diamonds += drop.getItem().getCount();
            if (drop.getItem().is(ModItems.SLIME_CHEST.get())) boxes += drop.getItem().getCount();
          }
          helper.assertTrue(
              diamonds == 17 && boxes == 1,
              "Breaking a Slime Chest must drop its block and contents once");
          helper.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 400)
  public static void exhaustedCarrierRestsBeforeEarningDeliveryPoint(GameTestHelper helper) {
    TransportSlime carrier = carrier(helper);
    seed(carrier, 0, 255, 0);
    carrier.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 10));
    SlimeChestBlockEntity chest = chest(helper, CHEST);
    helper.runAfterDelay(
        60,
        () ->
            helper.assertTrue(
                carrier.getTransportStatus() == TransportSlime.Status.RESTING
                    && count(carrier.getBag(), Items.DIAMOND) == 10
                    && carrier.getActivityProgress() == 255,
                "Resting must retain cargo without earning work progress"));
    helper.succeedWhen(
        () ->
            helper.assertTrue(
                count(chest.inventory(), Items.DIAMOND) == 10
                    && !carrier.hasCargo()
                    && carrier.getStatPoints() == 1
                    && carrier.getNextPointCost() == 320,
                "Recovered stamina must permit delivery and earn exactly one point at the shared"
                    + " threshold"));
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void activeWorkersKeepTheirPartiallyFilledBags(GameTestHelper helper) {
    TransportSlime carrier = carrier(helper);
    MiningSlime miner = miner(helper, carrier, new ItemStack(Items.IRON_PICKAXE));
    miner.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 11));
    SlimeChestBlockEntity chest = chest(helper, CHEST);
    helper.runAfterDelay(
        100,
        () -> {
          helper.assertTrue(
              count(miner.getBag(), Items.DIAMOND) == 11 && chest.isEmpty() && !carrier.hasCargo(),
              "Only full-bag work stoppages may request unloading");
          helper.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void noChestLeavesFullWorkerCargoInPlace(GameTestHelper helper) {
    TransportSlime carrier = carrier(helper);
    MiningSlime miner = miner(helper, carrier, new ItemStack(Items.IRON_PICKAXE));
    for (int slot = 0; slot < 9; slot++)
      miner.getBag().setStackInSlot(slot, new ItemStack(Items.DIAMOND, 64));
    helper.setBlock(ore(helper, miner).above(), Blocks.STONE);
    helper.runAfterDelay(
        100,
        () -> {
          helper.assertTrue(
              miner.needsUnloading()
                  && count(miner.getBag(), Items.DIAMOND) == 576
                  && !carrier.hasCargo()
                  && carrier.getTransportStatus() == TransportSlime.Status.NO_CHEST,
              "Without a destination the carrier must leave the worker's cargo intact");
          helper.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void lockedChestIsNotUsedForLogistics(GameTestHelper helper) {
    TransportSlime carrier = carrier(helper);
    carrier.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 10));
    SlimeChestBlockEntity chest = chest(helper, CHEST);
    CompoundTag state = chest.saveWithFullMetadata();
    state.putString("Lock", "Private supply");
    chest.load(state);
    helper.runAfterDelay(
        80,
        () -> {
          helper.assertTrue(
              chest.isEmpty()
                  && count(carrier.getBag(), Items.DIAMOND) == 10
                  && carrier.getTransportStatus() == TransportSlime.Status.NO_CHEST,
              "A locked container must not accept automatic transfers");
          helper.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void workSpeedScalesTheIntervalBetweenTransferActions(GameTestHelper helper) {
    TransportSlime slow = carrier(helper);
    TransportSlime fast = helper.spawn(ModEntities.TRANSPORT_SLIME.get(), CARRIER.south(2));
    fast.tame(helper.makeMockSurvivalPlayer());
    fast.onReleased();
    seed(fast, 20, 0, 100);
    for (int point = 0; point < 20; point++) fast.upgradeStat(SlimeStat.WORK_SPEED);
    MiningSlime slowWorker = miner(helper, slow, ItemStack.EMPTY);
    MiningSlime fastWorker = helper.spawn(ModEntities.MINING_SLIME.get(), MINER.south(2));
    fastWorker.setOwnerUUID(fast.getOwnerUUID());
    fastWorker.setTame(true);
    fastWorker.onReleased();
    SlimeChestBlockEntity chest = chest(helper, new BlockPos(6, 2, 6));
    chest.setItem(0, new ItemStack(Items.IRON_PICKAXE));
    chest.setItem(1, new ItemStack(Items.IRON_PICKAXE));
    long[] times = new long[4];
    helper.succeedWhen(
        () -> {
          long now = helper.getLevel().getGameTime();
          if (times[0] == 0 && slow.hasCargo()) times[0] = now;
          if (times[1] == 0 && fast.hasCargo()) times[1] = now;
          if (times[2] == 0 && slowWorker.getPickaxe().is(Items.IRON_PICKAXE)) times[2] = now;
          if (times[3] == 0 && fastWorker.getPickaxe().is(Items.IRON_PICKAXE)) times[3] = now;
          helper.assertTrue(
              times[0] != 0 && times[1] != 0 && times[2] != 0 && times[3] != 0,
              "Both carriers must fetch and supply a tool");
          helper.assertTrue(
              times[2] - times[0] == 20 && times[3] - times[1] == 7,
              "Transfers must respect 20 ticks at 1x speed and ceil(20/3) ticks at 3x speed");
        });
  }

  private static TransportSlime carrier(GameTestHelper helper) {
    for (int x = 0; x < 12; x++)
      for (int z = 0; z < 12; z++) helper.setBlock(x, 1, z, Blocks.BEDROCK);
    TransportSlime carrier = helper.spawn(ModEntities.TRANSPORT_SLIME.get(), CARRIER);
    carrier.tame(helper.makeMockSurvivalPlayer());
    carrier.onReleased();
    return carrier;
  }

  private static MiningSlime miner(GameTestHelper helper, TransportSlime carrier, ItemStack tool) {
    MiningSlime miner = helper.spawn(ModEntities.MINING_SLIME.get(), MINER);
    miner.setOwnerUUID(carrier.getOwnerUUID());
    miner.setTame(true);
    miner.setItemSlot(EquipmentSlot.MAINHAND, tool);
    miner.onReleased();
    return miner;
  }

  private static BlockPos ore(GameTestHelper helper, MiningSlime miner) {
    BlockPos relative = MINER.west();
    if (!new ChunkPos(helper.absolutePos(relative)).equals(miner.getMiningChunk()))
      relative = MINER.east();
    return relative;
  }

  private static SlimeChestBlockEntity chest(GameTestHelper helper, BlockPos pos) {
    helper.setBlock(pos, ModBlocks.SLIME_CHEST.get());
    return (SlimeChestBlockEntity) helper.getBlockEntity(pos);
  }

  private static void fill(SlimeChestBlockEntity chest, Item item) {
    for (int slot = 0; slot < chest.getContainerSize(); slot++)
      chest.setItem(slot, new ItemStack(item, 64));
  }

  private static void seed(FriendlySlime slime, int points, int activity, float stamina) {
    CompoundTag state = slime.saveWithoutId(new CompoundTag());
    state.putInt("StatPoints", points);
    state.putInt("ActivityProgress", activity);
    state.putFloat("Stamina", stamina);
    slime.load(state);
  }

  private static int count(IItemHandler inventory, Item item) {
    int result = 0;
    for (int slot = 0; slot < inventory.getSlots(); slot++)
      if (inventory.getStackInSlot(slot).is(item))
        result += inventory.getStackInSlot(slot).getCount();
    return result;
  }

  private static TransientCraftingContainer grid() {
    return new TransientCraftingContainer(
        new AbstractContainerMenu(null, -1) {
          @Override
          public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
          }

          @Override
          public boolean stillValid(Player player) {
            return true;
          }
        },
        3,
        3);
  }
}
