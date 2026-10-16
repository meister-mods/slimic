package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;
import net.minecraftforge.items.IItemHandler;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LogisticsStressGameTests {
  private static int count(IItemHandler bag, Item item) {
    int total = 0;
    for (int slot = 0; slot < bag.getSlots(); slot++) {
      var stack = bag.getStackInSlot(slot);
      if (stack.is(item)) total += stack.getCount();
    }
    return total;
  }

  private static int marked(List<BagSlime> slimes, int source) {
    int total = 0;
    for (var slime : slimes)
      for (int slot = 0; slot < slime.getBag().getSlots(); slot++) {
        var stack = slime.getBag().getStackInSlot(slot);
        if (stack.is(Items.DIRT)
            && stack.hasTag()
            && stack.getTag().getInt("CargoSource") == source) total += stack.getCount();
      }
    return total;
  }

  @GameTest(template = "empty", batch = "logistics_stress", timeoutTicks = 500)
  public static void fortyCompanionsPreserveEveryCargoSource(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var all = new ArrayList<BagSlime>();
    var workers = new ArrayList<HealingSlime>();
    var warehouses = new ArrayList<WarehouseSlime>();
    var carriers = new ArrayList<TransportSlime>();
    for (int i = 0; i < 8; i++) {
      var worker = ModEntities.HEALING_SLIME.get().create(h.getLevel());
      worker.tame(owner);
      worker.moveTo(a.point(3.5 + i % 4 * 3, 101, 3.5 + i / 4 * 3));
      worker.onReleased();
      worker.setWorking(true);
      for (int slot = 0; slot < worker.getCapacity(); slot++) {
        var cargo = new ItemStack(Items.DIRT, 64);
        cargo.getOrCreateTag().putInt("CargoSource", i);
        worker.getBag().setStackInSlot(slot, cargo);
      }
      workers.add(worker);
      all.add(worker);
      var warehouse = ModEntities.WAREHOUSE_SLIME.get().create(h.getLevel());
      warehouse.tame(owner);
      warehouse.moveTo(a.point(3.5 + i % 4 * 3, 101, 9.5 + i / 4 * 3));
      warehouse.onReleased();
      warehouse.setNoAi(true);
      warehouses.add(warehouse);
      all.add(warehouse);
    }
    for (int i = 0; i < 24; i++) {
      var carrier = ModEntities.TRANSPORT_SLIME.get().create(h.getLevel());
      carrier.tame(owner);
      carrier.moveTo(a.point(3.5 + i % 6 * 1.5, 101, 3.5 + i / 6 * 2.5));
      carrier.onReleased();
      carrier.setWorking(true);
      SlimeStat.REACH.tune(carrier, 32);
      SlimeStat.DETECTION_RANGE.tune(carrier, 32);
      carriers.add(carrier);
      all.add(carrier);
    }
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(all.get(0).blockPosition()),
                    "Arena ticks"))
        .thenExecute(() -> all.forEach(h.getLevel()::addFreshEntity))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    warehouses.stream().mapToInt(w -> count(w.getBag(), Items.DIRT)).sum() == 4608,
                    "All cargo reaches storage across competing carriers"))
        .thenIdle(30)
        .thenExecute(
            () -> {
              for (int i = 0; i < 8; i++)
                h.assertTrue(
                    marked(all, i) == 576, "Cargo source " + i + " remains exact with metadata");
              h.assertTrue(
                  workers.stream().allMatch(w -> count(w.getBag(), Items.DIRT) == 0)
                      && carriers.stream().allMatch(c -> !c.hasCargo()),
                  "Delivery leaves no stranded cargo");
              h.assertTrue(
                  h.getLevel()
                      .getEntitiesOfClass(
                          ItemEntity.class,
                          new AABB(all.get(0).blockPosition()).inflate(32),
                          e ->
                              e.getItem().is(Items.DIRT)
                                  && e.getItem().hasTag()
                                  && e.getItem().getTag().contains("CargoSource"))
                      .isEmpty(),
                  "No overflow is dropped into the world");
              all.forEach(BagSlime::discard);
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "logistics_stress", timeoutTicks = 240)
  public static void concurrentCarriersSupplyOnlyTheRequestedStack(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var worker = ModEntities.HEALING_SLIME.get().create(h.getLevel());
    worker.tame(owner);
    worker.moveTo(a.point(6.5, 101, 6.5));
    worker.onReleased();
    worker.setWorking(true);
    var source = ModEntities.WAREHOUSE_SLIME.get().create(h.getLevel());
    source.tame(owner);
    source.moveTo(a.point(8.5, 101, 8.5));
    source.onReleased();
    source.setNoAi(true);
    var balls = new ItemStack(Items.SLIME_BALL, 37);
    balls.getOrCreateTag().putString("SupplyLot", "warehouse37");
    source.getBag().setStackInSlot(0, balls);
    var carriers = new ArrayList<TransportSlime>();
    for (int i = 0; i < 12; i++) {
      var carrier = ModEntities.TRANSPORT_SLIME.get().create(h.getLevel());
      carrier.tame(owner);
      carrier.moveTo(a.point(4.5 + i % 4 * 2, 101, 4.5 + i / 4 * 3));
      carrier.onReleased();
      carrier.setWorking(true);
      SlimeStat.REACH.tune(carrier, 32);
      carriers.add(carrier);
    }
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(worker.blockPosition()), "Arena ticks"))
        .thenExecute(
            () -> {
              h.getLevel().addFreshEntity(worker);
              h.getLevel().addFreshEntity(source);
              carriers.forEach(h.getLevel()::addFreshEntity);
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    count(worker.getBag(), Items.SLIME_BALL) == 37,
                    "Competing carriers complete one supply request"))
        .thenIdle(35)
        .thenExecute(
            () -> {
              h.assertTrue(
                  !source.hasCargo()
                      && carriers.stream().allMatch(c -> !c.hasCargo())
                      && !worker.needsWorkSupply(),
                  "No stale request causes a second transfer");
              h.assertTrue(
                  worker.getBag().getStackInSlot(0).hasTag()
                      && "warehouse37"
                          .equals(
                              worker.getBag().getStackInSlot(0).getTag().getString("SupplyLot")),
                  "Supply metadata survives both handoffs");
              worker.discard();
              source.discard();
              carriers.forEach(TransportSlime::discard);
              a.close();
            })
        .thenSucceed();
  }
}
