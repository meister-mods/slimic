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

  @GameTest(template = "empty", batch = "bag_sorting")
  public static void explicitSortingCompactsRetiredColonySpansWithoutChangingVitals(
      GameTestHelper h) {
    for (var type :
        List.of(ModEntities.BOUND_COMBAT_SLIME.get(), ModEntities.BOUND_TRANSPORT_SLIME.get())) {
      var owner = h.makeMockSurvivalPlayer();
      var colony = (io.github.meistermods.slimic.entity.BagSlime) type.create(h.getLevel());
      colony.tame(owner);
      ColonyTestFixtures.appearance(colony);
      var members = ((io.github.meistermods.slimic.entity.BoundColony) colony).members();
      var first = members.records().get(0);
      var second = members.records().get(1);
      SlimeStat.CAPACITY.tune(first, 128);
      first.getBag().setStackInSlot(100, new ItemStack(Items.DIAMOND));
      first.getBag().setStackInSlot(101, new ItemStack(Items.EMERALD));
      second.getBag().setStackInSlot(0, new ItemStack(Items.COAL));
      members.project();
      SlimeStat.CAPACITY.tune(colony, 4);
      colony.setHealth(11.5F);
      float stamina = colony.getStamina();
      owner.moveTo(colony.position());
      var menu = TestMenus.create(1, owner.getInventory(), colony);
      var before = cargo(colony.getBag());
      h.assertTrue(menu.bagPages() > 1, "Shrunken member retains overflow spans before sorting");
      h.assertTrue(
          menu.clickMenuButton(owner, SlimeMenu.SORT_BAG), "Sort completes through management");
      h.assertTrue(
          colony.getBag().occupiedSlots().equals(List.of(0, 1, 2))
              && colony.getBag().storageSlots() == 4
              && menu.bagPages() == 1
              && colony.getHealth() == 11.5F
              && colony.getStamina() == stamina,
          "Explicit sorting retires empty span pages without healing or changing stamina");
      var restored = (io.github.meistermods.slimic.entity.BagSlime) type.create(h.getLevel());
      restored.load(colony.saveWithoutId(new CompoundTag()));
      h.assertTrue(
          before.equals(cargo(restored.getBag()))
              && restored.getBag().storageSlots() == 4
              && restored.getHealth() == 11.5F,
          "Compacted spans and exact cargo persist through native save/load");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "bag_sorting")
  public static void simulatedColonySortDoesNotMoveLiveMemberCargoOrSpans(GameTestHelper h) {
    var colony = ModEntities.BOUND_COMBAT_SLIME.get().create(h.getLevel());
    ColonyTestFixtures.appearance(colony);
    var first = colony.members().records().get(0);
    SlimeStat.CAPACITY.tune(first, 128);
    first.getBag().setStackInSlot(100, new ItemStack(Items.DIAMOND));
    colony.members().records().get(1).getBag().setStackInSlot(0, new ItemStack(Items.COAL));
    colony.members().project();
    SlimeStat.CAPACITY.tune(colony, 2);
    var before = colony.members().save();
    var simulated = colony.getBag().copyForSimulation();
    h.assertTrue(
        simulated.sortContents() && simulated.storageSlots() == 2,
        "Simulation has its own compacted member spans");
    h.assertTrue(
        before.equals(colony.members().save()) && colony.getBag().storageSlots() == 102,
        "Simulation changes neither live cargo nor its frozen spans");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "bag_sorting")
  public static void sortingRetainsNativeForgeCapabilitiesAndSeparatesDifferentCharge(
      GameTestHelper h) {
    java.util.function.Consumer<net.minecraftforge.event.AttachCapabilitiesEvent<ItemStack>>
        listener =
            event -> {
              if (event.getObject().is(Items.DIAMOND))
                event.addCapability(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        "slimic_tests", "bag_charge"),
                    new Charge());
            };
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.addGenericListener(
        ItemStack.class, listener);
    try {
      var bag = new SlimeBag(27, () -> 27, () -> {});
      for (int i = 0; i < 3; i++) {
        var stack = new ItemStack(Items.DIAMOND, i == 0 ? 10 : i == 1 ? 20 : 5);
        stack
            .getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY)
            .orElseThrow(() -> new IllegalStateException("Test energy capability missing"))
            .receiveEnergy(i < 2 ? 7 : 11, false);
        bag.setStackInSlot(i * 5 + 3, stack);
      }
      var before = cargo(bag);
      h.assertTrue(bag.sortContents(), "Forge capability-bearing cargo sorts normally");
      h.assertTrue(
          before.equals(cargo(bag)) && bag.occupiedSlots().size() == 2,
          "Equal capability states merge; different charge remains separate with exact serialized"
              + " data");
      var reloaded = new SlimeBag(27, () -> 27, () -> {});
      reloaded.deserializeNBT(bag.serializeNBT());
      h.assertTrue(
          before.equals(cargo(reloaded)), "Capabilities survive sorted native bag persistence");
    } finally {
      net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);
    }
    h.succeed();
  }

  private static final class Charge
      implements net.minecraftforge.common.capabilities.ICapabilitySerializable<CompoundTag> {
    private net.minecraftforge.energy.EnergyStorage storage =
        new net.minecraftforge.energy.EnergyStorage(100);
    private final net.minecraftforge.common.util.LazyOptional<
            net.minecraftforge.energy.IEnergyStorage>
        capability = net.minecraftforge.common.util.LazyOptional.of(() -> storage);

    @Override
    public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
        net.minecraftforge.common.capabilities.Capability<T> cap,
        net.minecraft.core.Direction side) {
      return cap == net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY
          ? capability.cast()
          : net.minecraftforge.common.util.LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
      var tag = new CompoundTag();
      tag.putInt("Charge", storage.getEnergyStored());
      return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
      storage = new net.minecraftforge.energy.EnergyStorage(100, 100, 100, tag.getInt("Charge"));
    }
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
