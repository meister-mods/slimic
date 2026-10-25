package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.ColonyBondingStationBlockEntity;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.ModBlocks;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.world.ColonyBonding;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LinkedTransportGameTests {
  private static final class Fixture implements AutoCloseable {
    final GameTestHelper helper;
    final LoadedWorkArea area;
    final ServerPlayer owner;
    final BoundTransportSlime colony;
    final List<Entity> entities = new ArrayList<>();

    Fixture(GameTestHelper helper) {
      this.helper = helper;
      area = new LoadedWorkArea(helper, 2400);
      var level = helper.getLevel();
      owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "LinkedTransportStudy"));
      owner.moveTo(area.point(2.5, 101, 2.5));
      var stationPos = BlockPos.containing(area.point(1, 101, 1));
      level.setBlockAndUpdate(
          stationPos, ModBlocks.COLONY_BONDING_STATION.get().defaultBlockState());
      var station = (ColonyBondingStationBlockEntity) level.getBlockEntity(stationPos);
      for (int i = 0; i < 2; i++) {
        var member = ModEntities.TRANSPORT_SLIME.get().create(level);
        member.tame(owner);
        station.setItem(i, FriendlySlimeItem.fromSlime(member));
      }
      helper.assertTrue(
          ColonyBonding.execute(owner, station, false) == ColonyBonding.Problem.COMPLETE,
          "The real bonding station creates an authorized transport colony");
      var item =
          owner.getInventory().items.stream()
              .filter(ColonyBonding::bound)
              .findFirst()
              .orElseThrow()
              .copy();
      UUID id = item.getTag().getCompound("SlimeData").getUUID("UUID");
      owner.getInventory().clearContent();
      owner.setItemInHand(InteractionHand.MAIN_HAND, item);
      var floor = BlockPos.containing(area.point(7, 100, 7));
      var use =
          new UseOnContext(
              owner,
              InteractionHand.MAIN_HAND,
              new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false));
      helper.assertTrue(item.getItem().useOn(use).consumesAction(), "Deploy the stored colony");
      colony = (BoundTransportSlime) level.getEntity(id);
      helper.assertTrue(colony != null && colony.isOrderedToSit(), "Deployment starts in Wait");
      SlimeStat.DETECTION_RANGE.tune(colony, 16);
      entities.add(colony);
      registerCleanup();
    }

    MiningSlime worker(double x, double z, MiningSlime.Status status, Item cargo, int count) {
      var worker = ModEntities.MINING_SLIME.get().create(helper.getLevel());
      worker.tame(owner);
      worker.moveTo(area.point(x, 101, z));
      worker.onReleased();
      worker.setWorking(true);
      // The transport contract is exercised against a stable real worker request.
      // Quarry execution is covered independently by the mining regression suite.
      worker.setNoAi(true);
      try {
        var field = MiningSlime.class.getDeclaredField("status");
        field.setAccessible(true);
        field.set(worker, status);
      } catch (ReflectiveOperationException failure) {
        throw new IllegalStateException(failure);
      }
      if (cargo != null) worker.getBag().setStackInSlot(0, new ItemStack(cargo, count));
      helper.assertTrue(helper.getLevel().addFreshEntity(worker), "Worker joins the native level");
      entities.add(worker);
      return worker;
    }

    void ready() {
      helper.assertTrue(
          colony.getLinkedBody() != null && colony.getLinkedBody().getColony() == colony,
          "The colony creates its second body through the normal lifecycle");
    }

    void begin() {
      colony.getLinkedBody().moveTo(area.point(10.5, 101, 7.5));
      colony.setWorking(true);
    }

    private void registerCleanup() {
      try {
        var field = GameTestHelper.class.getDeclaredField("testInfo");
        field.setAccessible(true);
        ((GameTestInfo) field.get(helper))
            .addListener(
                new GameTestListener() {
                  @Override
                  public void testStructureLoaded(GameTestInfo test) {}

                  @Override
                  public void testPassed(GameTestInfo test) {
                    close();
                  }

                  @Override
                  public void testFailed(GameTestInfo test) {
                    close();
                  }
                });
      } catch (ReflectiveOperationException failure) {
        throw new IllegalStateException(failure);
      }
    }

    @Override
    public void close() {
      entities.forEach(Entity::discard);
      area.close();
    }
  }

  private static int count(IItemHandler inventory, Item item) {
    int total = 0;
    for (int slot : SlimeBag.contentsSlots(inventory)) {
      var stack = inventory.getStackInSlot(slot);
      if (stack.is(item)) total += stack.getCount();
    }
    return total;
  }

  private static String describe(Fixture f) {
    return "primary="
        + f.colony.getPrimaryTransportStatus()
        + " at "
        + f.colony.position()
        + " destination="
        + f.colony.getPrimaryTransportDestination()
        + ", linked="
        + f.colony.getLinkedTransportStatus()
        + " at "
        + (f.colony.getLinkedBody() == null ? "absent" : f.colony.getLinkedBody().position())
        + " destination="
        + f.colony.getLinkedTransportDestination()
        + ", cargo="
        + f.colony.getBag().occupiedSlots().stream()
            .map(slot -> f.colony.getBag().getStackInSlot(slot).toString())
            .toList()
        + ", stamina="
        + f.colony.getStamina();
  }

  private static void separateCollections(GameTestHelper h, Fixture f) {
    var primary = f.colony.getPrimaryTransportDestination();
    var linked = f.colony.getLinkedTransportDestination();
    h.assertTrue(
        f.colony.getPrimaryTransportStatus() == TransportSlime.Status.COLLECTING
            && f.colony.getLinkedTransportStatus() == TransportSlime.Status.COLLECTING
            && primary != null
            && linked != null
            && primary.distanceToSqr(linked) > 16,
        "Both native tasks reserve different workers before either collection completes");
  }

  @GameTest(template = "empty", batch = "linked_transport", timeoutTicks = 1800)
  public static void separateBodiesCollectDifferentWorkersIntoOneCanonicalBag(GameTestHelper h) {
    var f = new Fixture(h);
    var west = f.worker(2.5, 3.5, MiningSlime.Status.FULL, Items.DIAMOND, 41);
    var east = f.worker(13.5, 12.5, MiningSlime.Status.FULL, Items.GOLD_INGOT, 53);
    var chest = f.area.chest(7, 101, 13);
    h.startSequence()
        .thenWaitUntil(f::ready)
        .thenExecute(f::begin)
        .thenWaitUntil(() -> separateCollections(h, f))
        .thenWaitUntil(
            () -> {
              h.assertTrue(
                  count(chest.inventory(), Items.DIAMOND) == 41
                      && count(chest.inventory(), Items.GOLD_INGOT) == 53,
                  "Both independently collected loads arrive exactly once: " + describe(f));
              h.assertTrue(
                  !west.hasCargo() && !east.hasCargo() && !f.colony.hasCargo(),
                  "All cargo leaves the shared bag and the two source bags");
              h.assertTrue(
                  f.colony.members().bags().stream().allMatch(bag -> bag.occupiedSlots().isEmpty()),
                  "The canonical member inventories contain no hidden cargo copy");
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "linked_transport", timeoutTicks = 1800)
  public static void oneSupplyItemCannotEquipTwoRequestingWorkers(GameTestHelper h) {
    var f = new Fixture(h);
    var west = f.worker(2.5, 3.5, MiningSlime.Status.NO_TOOL, null, 0);
    var east = f.worker(13.5, 12.5, MiningSlime.Status.NO_TOOL, null, 0);
    var chest = f.area.chest(7, 101, 13);
    f.colony.getBag().setStackInSlot(0, new ItemStack(Items.IRON_PICKAXE));
    h.onEachTick(
        () ->
            h.assertTrue(
                f.colony.getPrimaryTransportStatus() != TransportSlime.Status.SUPPLYING
                    || f.colony.getLinkedTransportStatus() != TransportSlime.Status.SUPPLYING,
                "Two requests cannot reserve the same last tool in the shared bag"));
    h.startSequence()
        .thenWaitUntil(f::ready)
        .thenExecute(f::begin)
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    west.getTool(0).is(Items.IRON_PICKAXE)
                        || east.getTool(0).is(Items.IRON_PICKAXE),
                    "One requesting worker receives the available tool"))
        .thenIdle(60)
        .thenExecute(
            () -> {
              int equipped =
                  (west.getTool(0).is(Items.IRON_PICKAXE) ? 1 : 0)
                      + (east.getTool(0).is(Items.IRON_PICKAXE) ? 1 : 0);
              h.assertTrue(
                  equipped == 1
                      && count(chest.inventory(), Items.IRON_PICKAXE) == 0
                      && count(f.colony.getBag(), Items.IRON_PICKAXE) == 0,
                  "Competing jobs conserve the single tool without duplication or loss");
              h.assertTrue(
                  west.needsReplacementTool() != east.needsReplacementTool(),
                  "The unsupplied worker keeps its unresolved request");
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "linked_transport", timeoutTicks = 1800)
  public static void unloadingCannotStealTheOtherBodysReservedSupply(GameTestHelper h) {
    var f = new Fixture(h);
    var worker = f.worker(13.5, 13.5, MiningSlime.Status.NO_TOOL, null, 0);
    var chest = f.area.chest(2, 101, 3);
    f.colony.getBag().setStackInSlot(0, new ItemStack(Items.IRON_PICKAXE));
    f.colony.getBag().setStackInSlot(1, new ItemStack(Items.DIAMOND, 23));
    h.startSequence()
        .thenWaitUntil(f::ready)
        .thenExecute(f::begin)
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    f.colony.getPrimaryTransportStatus() == TransportSlime.Status.SUPPLYING
                        || f.colony.getLinkedTransportStatus() == TransportSlime.Status.SUPPLYING,
                    "One body reserves the tool for the requesting worker"))
        .thenExecute(
            () -> {
              var depositing =
                  f.colony.getPrimaryTransportStatus() == TransportSlime.Status.SUPPLYING
                      ? f.colony.getLinkedBody()
                      : f.colony;
              depositing.moveTo(f.area.point(3.5, 101, 3.5));
              depositing.getNavigation().stop();
            })
        .thenWaitUntil(
            () -> {
              h.assertTrue(count(chest.inventory(), Items.DIAMOND) == 23, "Ordinary cargo unloads");
              h.assertTrue(
                  count(chest.inventory(), Items.IRON_PICKAXE) == 0,
                  "The tool assigned to the other body is never deposited into storage");
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    worker.getTool(0).is(Items.IRON_PICKAXE)
                        && count(f.colony.getBag(), Items.IRON_PICKAXE) == 0
                        && count(chest.inventory(), Items.IRON_PICKAXE) == 0,
                    "The reserved tool reaches its worker exactly once"))
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "linked_transport", timeoutTicks = 1800)
  public static void competingCollectionsRecheckTheLastSharedBagSpace(GameTestHelper h) {
    var f = new Fixture(h);
    var west = f.worker(2.5, 3.5, MiningSlime.Status.FULL, Items.COBBLESTONE, 64);
    var east = f.worker(13.5, 12.5, MiningSlime.Status.FULL, Items.COBBLESTONE, 64);
    var chest = f.area.chest(7, 101, 13);
    h.startSequence()
        .thenWaitUntil(f::ready)
        .thenExecute(f::begin)
        .thenWaitUntil(() -> separateCollections(h, f))
        .thenExecute(
            () -> {
              int last = f.colony.getCapacity() - 1;
              for (int slot = 0; slot < last; slot++)
                f.colony.getBag().setStackInSlot(slot, new ItemStack(Items.DIAMOND, 64));
              f.colony.getBag().setStackInSlot(last, new ItemStack(Items.COBBLESTONE, 63));
              var primary = f.colony.getPrimaryTransportDestination();
              boolean firstWest = primary.distanceToSqr(west.position()) < 9;
              f.colony.moveTo((firstWest ? west : east).position().add(0, 0, 1));
              f.colony.getLinkedBody().moveTo((firstWest ? east : west).position().add(0, 0, 1));
              f.colony.getNavigation().stop();
              f.colony.getLinkedBody().getNavigation().stop();
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    count(west.getBag(), Items.COBBLESTONE)
                            + count(east.getBag(), Items.COBBLESTONE)
                        < 128,
                    "At least one reserved collection reaches the shared final space"))
        .thenExecute(
            () -> {
              f.colony.setWaiting(true);
              h.assertTrue(
                  count(west.getBag(), Items.COBBLESTONE) + count(east.getBag(), Items.COBBLESTONE)
                          == 127
                      && count(f.colony.getBag(), Items.COBBLESTONE) == 64
                      && count(chest.inventory(), Items.COBBLESTONE) == 0,
                  "Only one item enters the single remaining space before either job can unload");
              h.assertTrue(
                  count(f.colony.getBag(), Items.DIAMOND) == (f.colony.getCapacity() - 1) * 64,
                  "Competing collections preserve all pre-existing shared cargo");
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "linked_transport", timeoutTicks = 1800)
  public static void waitCancelsBothAssignmentsAndWorkResumesWithoutLostCargo(GameTestHelper h) {
    var f = new Fixture(h);
    var west = f.worker(2.5, 3.5, MiningSlime.Status.FULL, Items.DIAMOND, 31);
    var east = f.worker(13.5, 12.5, MiningSlime.Status.FULL, Items.GOLD_INGOT, 37);
    var chest = f.area.chest(7, 101, 13);
    h.startSequence()
        .thenWaitUntil(f::ready)
        .thenExecute(f::begin)
        .thenWaitUntil(() -> separateCollections(h, f))
        .thenExecute(() -> f.colony.setWaiting(true))
        .thenIdle(60)
        .thenExecute(
            () -> {
              h.assertTrue(
                  f.colony.getPrimaryTransportStatus() == TransportSlime.Status.WAITING
                      && f.colony.getLinkedTransportStatus() == TransportSlime.Status.WAITING
                      && f.colony.getPrimaryTransportDestination() == null
                      && f.colony.getLinkedTransportDestination() == null,
                  "Wait cancels both tasks and pending navigation destinations");
              h.assertTrue(
                  count(west.getBag(), Items.DIAMOND) == 31
                      && count(east.getBag(), Items.GOLD_INGOT) == 37
                      && !f.colony.hasCargo()
                      && count(chest.inventory(), Items.DIAMOND) == 0
                      && count(chest.inventory(), Items.GOLD_INGOT) == 0,
                  "Neither body transfers cargo during the waiting period");
              f.colony.setWorking(true);
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    count(chest.inventory(), Items.DIAMOND) == 31
                        && count(chest.inventory(), Items.GOLD_INGOT) == 37
                        && !west.hasCargo()
                        && !east.hasCargo()
                        && !f.colony.hasCargo(),
                    "Both bodies can resume the same requests without stale reservations: "
                        + describe(f)))
        .thenSucceed();
  }
}
