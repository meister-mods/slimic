package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.SlimeBag;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BagSortingGameTests {
  private BagSortingGameTests() {}

  @GameTest(template = "empty", batch = "bag_sorting")
  public static void menuSortsAllPagesWithoutMovingEquipmentOrPlayerItems(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var miner = h.spawn(ModEntities.MINING_SLIME.get(), new BlockPos(5, 2, 5));
    miner.setNoAi(true);
    miner.tame(owner);
    owner.moveTo(miner.position());
    SlimeStat.CAPACITY.tune(miner, 54);
    var pickaxe = new ItemStack(Items.IRON_PICKAXE);
    pickaxe.setDamageValue(7);
    miner.setTool(0, pickaxe.copy());
    miner.getBag().setStackInSlot(40, new ItemStack(Items.STONE, 20));
    miner.getBag().setStackInSlot(5, new ItemStack(Items.COBBLESTONE, 32));
    miner.getBag().setStackInSlot(30, new ItemStack(Items.COBBLESTONE, 40));
    owner.getInventory().setItem(9, new ItemStack(Items.GOLD_INGOT, 11));
    var menu = TestMenus.create(1, owner.getInventory(), miner);
    menu.setCarried(new ItemStack(Items.DIAMOND, 7));
    h.assertTrue(menu.clickMenuButton(owner, SlimeMenu.NEXT_PAGE), "Open another bag page");
    h.assertTrue(menu.clickMenuButton(owner, SlimeMenu.SORT_BAG), "Owner can sort the entire bag");
    h.assertTrue(
        miner.getBag().occupiedSlots().equals(List.of(0, 1, 2))
            && miner.getBag().getStackInSlot(0).is(Items.COBBLESTONE)
            && miner.getBag().getStackInSlot(0).getCount() == 64
            && miner.getBag().getStackInSlot(1).getCount() == 8
            && miner.getBag().getStackInSlot(2).is(Items.STONE)
            && miner.getBag().getStackInSlot(2).getCount() == 20
            && menu.data(SlimeMenu.BAG_PAGE) == 0,
        "Cargo is merged, ordered and compacted across pages, then shown on the first page");
    h.assertTrue(
        ItemStack.matches(pickaxe, miner.getTool(0))
            && owner.getInventory().getItem(9).getCount() == 11
            && menu.getCarried().is(Items.DIAMOND)
            && menu.getCarried().getCount() == 7,
        "Equipment, player inventory and the carried cursor stack remain unchanged");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "bag_sorting")
  public static void sortingRespectsItemLimitsAndDistinctSavedData(GameTestHelper h) {
    var bag = new SlimeBag(27, () -> 27, () -> {});
    var named = new ItemStack(Items.COBBLESTONE, 12);
    named.setHoverName(Component.literal("Reserved"));
    bag.setStackInSlot(3, named);
    bag.setStackInSlot(17, named.copyWithCount(60));
    bag.setStackInSlot(8, new ItemStack(Items.COBBLESTONE, 5));
    bag.setStackInSlot(20, new ItemStack(Items.BUCKET, 10));
    bag.setStackInSlot(24, new ItemStack(Items.BUCKET, 10));
    var worn = new ItemStack(Items.IRON_PICKAXE);
    worn.setDamageValue(12);
    bag.setStackInSlot(22, worn);
    bag.setStackInSlot(25, new ItemStack(Items.IRON_PICKAXE));
    var before = cargo(bag);
    h.assertTrue(bag.sortContents(), "Normal cargo can be sorted");
    h.assertTrue(before.equals(cargo(bag)), "Names, wear, tags and exact cargo totals survive");
    h.assertTrue(
        bag.occupiedSlots().equals(List.of(0, 1, 2, 3, 4, 5, 6))
            && bag.getStackInSlot(0).is(Items.BUCKET)
            && bag.getStackInSlot(0).getCount() == 16
            && bag.getStackInSlot(1).getCount() == 4,
        "Sixteen-item and single-item limits are respected while matching stacks merge");
    var first = bag.serializeNBT();
    h.assertTrue(bag.sortContents() && first.equals(bag.serializeNBT()), "Sorting is idempotent");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "bag_sorting")
  public static void sparseCreativeCapacityDoesNotRequireScanningEmptySlots(GameTestHelper h) {
    var updates = new AtomicInteger();
    var bag = new SlimeBag(27, () -> Integer.MAX_VALUE, updates::incrementAndGet);
    bag.setStackInSlot(2_000_000_000, new ItemStack(Items.DIRT, 40));
    bag.setStackInSlot(1_000_000_000, new ItemStack(Items.DIRT, 24));
    int beforeUpdates = updates.get();
    h.assertTrue(bag.sortContents(), "Sparse extreme Creative capacity can be sorted");
    h.assertTrue(
        bag.occupiedSlots().equals(List.of(0))
            && bag.getStackInSlot(0).getCount() == 64
            && updates.get() == beforeUpdates + 1,
        "Only actual cargo is visited and observers receive the completed inventory once");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "bag_sorting")
  public static void reducedCapacityRetainsExtractableOverflowWithoutAddingSpace(GameTestHelper h) {
    var capacity = new AtomicInteger(2_000_000_001);
    var bag = new SlimeBag(27, capacity::get, () -> {});
    bag.setStackInSlot(2_000_000_000, new ItemStack(Items.STONE, 64));
    bag.setStackInSlot(9, new ItemStack(Items.DIAMOND, 64));
    bag.setStackInSlot(7, new ItemStack(Items.COAL, 64));
    bag.setStackInSlot(4, new ItemStack(Items.DIRT, 64));
    capacity.set(1);
    var before = cargo(bag);
    h.assertTrue(bag.sortContents(), "Overflow remains sortable after Creative capacity shrinks");
    h.assertTrue(before.equals(cargo(bag)), "All overflow cargo is retained");
    for (int slot : bag.occupiedSlots())
      if (slot > 0)
        h.assertTrue(
            bag.getSlotLimit(slot) == 0
                && !bag.insertItem(slot, new ItemStack(Items.EMERALD), false).isEmpty(),
            "Retained overflow positions never become new insertion space");
    var loaded = new SlimeBag(27, capacity::get, () -> {});
    loaded.deserializeNBT(bag.serializeNBT());
    h.assertTrue(before.equals(cargo(loaded)), "Sorted overflow survives native bag persistence");
    for (int slot : loaded.occupiedSlots()) loaded.extractItem(slot, 64, false);
    h.assertTrue(loaded.occupiedSlots().isEmpty(), "Every retained stack stays extractable");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "bag_sorting")
  public static void colonySortingWritesOriginalMembersAndPreservesIdentityAndEquipment(
      GameTestHelper h) {
    var colony = ModEntities.BOUND_COMBAT_SLIME.get().create(h.getLevel());
    ColonyTestFixtures.appearance(colony);
    var first = colony.members().records().get(0);
    var second = colony.members().records().get(1);
    var firstIdentity = first.getUUID();
    var secondIdentity = second.getUUID();
    var firstEquipment = first.getMainHandItem().copy();
    var secondEquipment = second.getMainHandItem().copy();
    first.getBag().setStackInSlot(8, new ItemStack(Items.COBBLESTONE, 7));
    second.getBag().setStackInSlot(6, new ItemStack(Items.COBBLESTONE, 60));
    var cargo = cargo(colony.getBag());
    h.assertTrue(colony.getBag().sortContents(), "Colony sorts both actual member bags together");
    h.assertTrue(
        first.getBag().occupiedSlots().equals(List.of(0, 1))
            && second.getBag().occupiedSlots().isEmpty()
            && first.getBag().getStackInSlot(0).getCount() == 64
            && first.getBag().getStackInSlot(1).getCount() == 3,
        "The representative view and original member inventories contain the same compact cargo");
    var saved = colony.saveWithoutId(new CompoundTag());
    var restored = ModEntities.BOUND_COMBAT_SLIME.get().create(h.getLevel());
    restored.load(saved);
    h.assertTrue(
        cargo.equals(cargo(restored.getBag()))
            && restored.members().records().get(0).getUUID().equals(firstIdentity)
            && restored.members().records().get(1).getUUID().equals(secondIdentity)
            && ItemStack.matches(
                firstEquipment, restored.members().records().get(0).getMainHandItem())
            && ItemStack.matches(
                secondEquipment, restored.members().records().get(1).getMainHandItem()),
        "Native save/load preserves exact cargo, both original UUIDs and both original weapons");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "bag_sorting")
  public static void unauthorizedAndHiddenMenuSortingCannotChangeCargo(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var fighter = h.spawn(ModEntities.COMBAT_SLIME.get(), new BlockPos(5, 2, 5));
    fighter.setNoAi(true);
    fighter.tame(owner);
    owner.moveTo(fighter.position());
    fighter.getBag().setStackInSlot(8, new ItemStack(Items.DIAMOND, 7));
    var menu = TestMenus.create(1, owner.getInventory(), fighter);
    var before = fighter.getBag().serializeNBT();
    h.assertTrue(menu.clickMenuButton(owner, SlimeMenu.TARGET_PAGE_BUTTON), "Open target settings");
    h.assertTrue(!menu.clickMenuButton(owner, SlimeMenu.SORT_BAG), "Hidden bag rejects sorting");
    h.assertTrue(menu.clickMenuButton(owner, SlimeMenu.TARGET_PAGE_BUTTON), "Return to bag");
    owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    h.assertTrue(!menu.clickMenuButton(owner, SlimeMenu.SORT_BAG), "Missing tuner rejects sorting");
    menu = TestMenus.create(1, owner.getInventory(), fighter);
    fighter.setOwnerUUID(java.util.UUID.randomUUID());
    h.assertTrue(
        !menu.clickMenuButton(owner, SlimeMenu.SORT_BAG), "Changed ownership rejects sorting");
    h.assertTrue(
        before.equals(fighter.getBag().serializeNBT()), "Rejected commands leave cargo intact");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "bag_sorting")
  public static void malformedMemberStacksLeaveEveryOriginalInventoryUntouched(GameTestHelper h) {
    var colony = ModEntities.BOUND_COMBAT_SLIME.get().create(h.getLevel());
    ColonyTestFixtures.appearance(colony);
    colony.members().records().get(0).getBag().setStackInSlot(7, new ItemStack(Items.COAL, 10));
    colony
        .members()
        .records()
        .get(1)
        .getBag()
        .setStackInSlot(8, new ItemStack(Items.IRON_PICKAXE, 2));
    var before = colony.members().save();
    h.assertTrue(
        !colony.getBag().sortContents(), "Invalid oversized cargo is not silently split or lost");
    h.assertTrue(
        before.equals(colony.members().save()), "Planning failure changes neither original record");
    h.succeed();
  }

  private static Map<CompoundTag, Long> cargo(SlimeBag bag) {
    var counts = new HashMap<CompoundTag, Long>();
    for (int slot : bag.occupiedSlots()) {
      var stack = bag.getStackInSlot(slot);
      counts.merge(stack.copyWithCount(1).serializeNBT(), (long) stack.getCount(), Long::sum);
    }
    return counts;
  }
}
