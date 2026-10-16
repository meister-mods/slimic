package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.*;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.recipe.*;
import io.github.meistermods.slimic.registry.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;
import net.minecraftforge.items.IItemHandler;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OrderTenGameTests {
  private record Site(
      GameTestHelper h,
      LoadedWorkArea area,
      Player owner,
      HealingSlime healer,
      FriendlySlime patient) {
    GameTestSequence start() {
      return h.startSequence()
          .thenWaitUntil(
              () ->
                  h.assertTrue(
                      h.getLevel().isPositionEntityTicking(healer.blockPosition()), "Arena ticks"))
          .thenExecute(
              () -> {
                h.getLevel().addFreshEntity(patient);
                h.getLevel().addFreshEntity(healer);
              });
    }

    void close() {
      healer.discard();
      patient.discard();
      area.close();
    }
  }

  private static Site site(GameTestHelper h, int balls) {
    var area = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var healer = ModEntities.HEALING_SLIME.get().create(h.getLevel());
    healer.tame(owner);
    healer.moveTo(area.point(6.5, 101, 8.5));
    healer.onReleased();
    healer.setWorking(true);
    if (balls > 0) healer.getBag().setStackInSlot(0, new ItemStack(Items.SLIME_BALL, balls));
    var patient = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    patient.tame(owner);
    patient.moveTo(area.point(8.5, 101, 8.5));
    patient.setNoAi(true);
    patient.setHealth(5);
    return new Site(h, area, owner, healer, patient);
  }

  private static TransientCraftingContainer grid() {
    return new TransientCraftingContainer(
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
        3,
        3);
  }

  private static int count(IItemHandler inv, Item item) {
    int n = 0;
    for (int i = 0; i < inv.getSlots(); i++)
      if (inv.getStackInSlot(i).is(item)) n += inv.getStackInSlot(i).getCount();
    return n;
  }

  private static void fill(BagSlime slime) {
    for (int i = 0; i < slime.getCapacity(); i++)
      slime.getBag().setStackInSlot(i, new ItemStack(Items.DIRT, 64));
  }

  private static WarehouseSlime warehouse(
      GameTestHelper h, LoadedWorkArea a, Player owner, double x, double z) {
    var w = ModEntities.WAREHOUSE_SLIME.get().create(h.getLevel());
    w.tame(owner);
    w.moveTo(a.point(x, 101, z));
    w.onReleased();
    w.setNoAi(true);
    h.getLevel().addFreshEntity(w);
    return w;
  }

  private static TransportSlime carrier(GameTestHelper h, LoadedWorkArea a, Player owner) {
    var c = ModEntities.TRANSPORT_SLIME.get().create(h.getLevel());
    c.tame(owner);
    c.moveTo(a.point(7.5, 101, 8.5));
    c.onReleased();
    c.setWorking(true);
    h.getLevel().addFreshEntity(c);
    return c;
  }

  @GameTest(template = "empty", batch = "order_ten")
  public static void healingRecipePreservesStoredTraitsAndUsesGoldenCarrot(GameTestHelper h) {
    var p = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    p.tame(h.makeMockSurvivalPlayer());
    p.setTunedVitals(7, 31, 5);
    var input = FriendlySlimeItem.fromSlime(p);
    var g = grid();
    g.setItem(0, input);
    g.setItem(8, new ItemStack(Items.GOLDEN_CARROT));
    var recipe =
        h.getLevel()
            .getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, g, h.getLevel())
            .orElseThrow();
    var result = recipe.assemble(g, h.getLevel().registryAccess());
    h.assertTrue(
        result.is(ModItems.HEALING_SLIME.get()) && result.getTag().equals(input.getTag()),
        "Native recipe preserves all companion data");
    g.setItem(8, new ItemStack(Items.CARROT));
    h.assertTrue(!recipe.matches(g, h.getLevel()), "Ordinary carrots rejected");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_ten")
  public static void warehouseRecipeNeedsEightSeparateChestsAndPreservesCargo(GameTestHelper h) {
    var c = ModEntities.TRANSPORT_SLIME.get().create(h.getLevel());
    c.tame(h.makeMockSurvivalPlayer());
    c.setWorking(true);
    c.setTunedVitals(7, 31, 5);
    c.getBag().setStackInSlot(17, new ItemStack(Items.DIAMOND, 3));
    var input = FriendlySlimeItem.fromSlime(c);
    var g = grid();
    for (int i = 0; i < 9; i++) g.setItem(i, i == 4 ? input : new ItemStack(Items.CHEST));
    var recipe =
        h.getLevel()
            .getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, g, h.getLevel())
            .orElseThrow();
    var result = recipe.assemble(g, h.getLevel().registryAccess());
    var w = ModEntities.WAREHOUSE_SLIME.get().create(h.getLevel());
    w.load(result.getTag().getCompound("SlimeData"));
    h.assertTrue(
        result.is(ModItems.WAREHOUSE_SLIME.get())
            && w.getUUID().equals(c.getUUID())
            && w.getHealth() == 7
            && w.getStamina() == 31
            && count(w.getBag(), Items.DIAMOND) == 3
            && w.isOrderedToSit()
            && !w.isWorking(),
        "New specialization waits while preserving identity, vitals and cargo");
    h.assertTrue(
        c.isWorking() && input.getTag().getCompound("SlimeData").getBoolean("TransportWorking"),
        "Recipe leaves source state intact");
    g.setItem(8, ItemStack.EMPTY);
    h.assertTrue(!recipe.matches(g, h.getLevel()), "Seven chests rejected");
    g.setItem(8, new ItemStack(Items.TRAPPED_CHEST));
    h.assertTrue(!recipe.matches(g, h.getLevel()), "Only regular chests");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_ten")
  public static void warehouseCapacityStaysFourTimesTransportThroughUpgradesAndTuning(
      GameTestHelper h) {
    var c = ModEntities.TRANSPORT_SLIME.get().create(h.getLevel());
    var w = ModEntities.WAREHOUSE_SLIME.get().create(h.getLevel());
    c.setTunedVitals(16, 100, 6);
    w.setTunedVitals(16, 100, 6);
    for (int i = 0; i <= 6; i++) {
      h.assertTrue(w.getCapacity() == c.getCapacity() * 4, "Fourfold at upgrade " + i);
      if (i < 6) {
        c.upgradeStat(SlimeStat.CAPACITY);
        w.upgradeStat(SlimeStat.CAPACITY);
      }
    }
    h.assertTrue(w.getCapacity() == 216 && w.getBag().getSlots() == 216, "Full backing inventory");
    for (int n : new int[] {1, 5, 27}) {
      SlimeStat.CAPACITY.tune(c, n);
      SlimeStat.CAPACITY.tune(w, n);
      h.assertTrue(w.getCapacity() == c.getCapacity() * 4, "Fourfold tuned capacity");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_ten")
  public static void warehouseLastPageAndRecoveryRetainFinalSlot(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var w = ModEntities.WAREHOUSE_SLIME.get().create(h.getLevel());
    w.tame(owner);
    owner.moveTo(w.position());
    SlimeStat.CAPACITY.tune(w, 27);
    w.setWorking(true);
    w.getBag().setStackInSlot(215, new ItemStack(Items.EMERALD, 17));
    var menu = TestMenus.create(1, owner.getInventory(), w);
    h.assertTrue(
        menu.bagPages() == 8 && menu.slots.size() == 252 && !menu.getSlot(215).isActive(),
        "Eight pages, no equipment");
    for (int i = 0; i < 7; i++)
      h.assertTrue(menu.clickMenuButton(owner, SlimeMenu.NEXT_PAGE), "Advance page");
    h.assertTrue(
        menu.getSlot(215).isActive() && !menu.clickMenuButton(owner, SlimeMenu.NEXT_PAGE),
        "Last page bounded");
    var stack = FriendlySlimeItem.fromSlime(w);
    h.assertTrue(stack.is(ModItems.WAREHOUSE_SLIME.get()), "Correct recovery item");
    var restored = ModEntities.WAREHOUSE_SLIME.get().create(h.getLevel());
    restored.load(stack.getTag().getCompound("SlimeData"));
    h.assertTrue(
        restored.isWorking() && count(restored.getBag(), Items.EMERALD) == 17,
        "Order and last slot persist");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_ten", timeoutTicks = 150)
  public static void oneBallHealsFourAndAwardsActivity(GameTestHelper h) {
    var s = site(h, 1);
    s.start()
        .thenWaitUntil(() -> h.assertTrue(s.patient.getHealth() > 5, "Treatment occurs"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  s.patient.getHealth() == 9
                      && count(s.healer.getBag(), Items.SLIME_BALL) == 0
                      && s.healer.getActivityProgress() == 1,
                  "One ball heals four and earns activity");
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_ten", timeoutTicks = 150)
  public static void foreignAndHealthyCompanionsDoNotConsumeSupplies(GameTestHelper h) {
    var s = site(h, 3);
    s.patient.setOwnerUUID(UUID.randomUUID());
    s.start()
        .thenIdle(65)
        .thenExecute(
            () -> {
              h.assertTrue(
                  s.patient.getHealth() == 5 && count(s.healer.getBag(), Items.SLIME_BALL) == 3,
                  "Foreign injured companion ignored");
              s.patient.setOwnerUUID(s.owner.getUUID());
              s.patient.setHealth(s.patient.getMaxHealth());
            })
        .thenIdle(35)
        .thenExecute(
            () -> {
              h.assertTrue(
                  count(s.healer.getBag(), Items.SLIME_BALL) == 3, "Healthy companion ignored");
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_ten", timeoutTicks = 120)
  public static void missingBallsRequestsSupplyAndWaitWithdrawsIt(GameTestHelper h) {
    var s = site(h, 0);
    s.start()
        .thenWaitUntil(() -> h.assertTrue(s.healer.needsWorkSupply(), "Request visible"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  s.patient.getHealth() == 5
                      && s.healer.getWorkStatus().requests(WorkStatus.Request.SLIMEBALL_SUPPLY),
                  "No free healing");
              s.healer.setWaiting(true);
              h.assertTrue(!s.healer.needsWorkSupply(), "Wait withdraws request");
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_ten", timeoutTicks = 150)
  public static void cancelledHealingPreservesBallAndActivity(GameTestHelper h) {
    var s = site(h, 1);
    Consumer<LivingHealEvent> listener =
        e -> {
          if (e.getEntity() == s.patient) e.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(listener);
    h.runAfterDelay(149, () -> MinecraftForge.EVENT_BUS.unregister(listener));
    s.start()
        .thenIdle(75)
        .thenExecute(
            () -> {
              h.assertTrue(
                  s.patient.getHealth() == 5
                      && count(s.healer.getBag(), Items.SLIME_BALL) == 1
                      && s.healer.getActivityProgress() == 0,
                  "Cancelled heal has no cost");
              MinecraftForge.EVENT_BUS.unregister(listener);
              s.close();
            })
        .thenSucceed();
  }

  private static BlockPos broken(Site s) {
    BlockPos pos = BlockPos.containing(s.area.point(8.5, 101, 8.5));
    s.patient.getPersistentData().putString("Identity", "kept");
    ItemStack stored = FriendlySlimeItem.fromSlime(s.patient);
    s.patient.discard();
    s.h.getLevel().setBlockAndUpdate(pos, ModBlocks.BROKEN_SLIME.get().defaultBlockState());
    ((BrokenSlimeBlockEntity) s.h.getLevel().getBlockEntity(pos)).setSlime(stored);
    return pos;
  }

  private static GameTestSequence startBroken(Site s) {
    return s.h
        .startSequence()
        .thenWaitUntil(
            () ->
                s.h.assertTrue(
                    s.h.getLevel().isPositionEntityTicking(s.healer.blockPosition()),
                    "Arena ticks"))
        .thenExecute(() -> s.h.getLevel().addFreshEntity(s.healer));
  }

  @GameTest(template = "empty", batch = "order_ten", timeoutTicks = 150)
  public static void revivalUsesOneBallAndRestoresAtOneHealth(GameTestHelper h) {
    var s = site(h, 1);
    var pos = broken(s);
    startBroken(s)
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getEntity(s.patient.getUUID()) instanceof FriendlySlime,
                    "Companion revived"))
        .thenExecute(
            () -> {
              var p = (FriendlySlime) h.getLevel().getEntity(s.patient.getUUID());
              h.assertTrue(
                  p.getHealth() == 1
                      && p.getOwnerUUID().equals(s.owner.getUUID())
                      && p.getPersistentData().getString("Identity").equals("kept")
                      && count(s.healer.getBag(), Items.SLIME_BALL) == 0
                      && h.getLevel().getBlockState(pos).isAir(),
                  "Revival preserves identity, costs one ball and starts at one HP");
              p.discard();
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_ten", timeoutTicks = 160)
  public static void protectedRevivalPreservesBlockAndSupplies(GameTestHelper h) {
    var s = site(h, 1);
    var pos = broken(s);
    Consumer<BlockEvent.BreakEvent> listener =
        e -> {
          if (e.getPos().equals(pos)) e.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(listener);
    h.runAfterDelay(159, () -> MinecraftForge.EVENT_BUS.unregister(listener));
    startBroken(s)
        .thenIdle(70)
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockEntity(pos) instanceof BrokenSlimeBlockEntity
                      && count(s.healer.getBag(), Items.SLIME_BALL) == 1
                      && s.healer.getActivityProgress() == 0,
                  "Protected revival has no cost");
              MinecraftForge.EVENT_BUS.unregister(listener);
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_ten", timeoutTicks = 160)
  public static void obstructedRevivalRollsBackOriginalData(GameTestHelper h) {
    var s = site(h, 1);
    var pos = broken(s);
    h.getLevel().setBlockAndUpdate(pos.above(), Blocks.BEDROCK.defaultBlockState());
    var before = ((BrokenSlimeBlockEntity) h.getLevel().getBlockEntity(pos)).getSlime();
    startBroken(s)
        .thenIdle(70)
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockEntity(pos) instanceof BrokenSlimeBlockEntity b
                      && ItemStack.matches(b.getSlime(), before)
                      && count(s.healer.getBag(), Items.SLIME_BALL) == 1,
                  "Failed placement restores exact stored block and ball");
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_ten", timeoutTicks = 160)
  public static void duplicateUuidCannotBeRevived(GameTestHelper h) {
    var s = site(h, 1);
    var pos = broken(s);
    var duplicate = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    duplicate.setUUID(s.patient.getUUID());
    duplicate.tame(s.owner);
    duplicate.setNoAi(true);
    duplicate.moveTo(s.area.point(12, 101, 12));
    h.getLevel().addFreshEntity(duplicate);
    startBroken(s)
        .thenIdle(70)
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockEntity(pos) instanceof BrokenSlimeBlockEntity
                      && count(s.healer.getBag(), Items.SLIME_BALL) == 1,
                  "Duplicate UUID retains recovery block and resources");
              duplicate.discard();
              s.close();
            })
        .thenSucceed();
  }

  private static void deposits(GameTestHelper h, int mode) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var w = warehouse(h, a, owner, 10.5, 8.5);
    var chest = a.chest(6, 101, 10);
    if (mode == 1 || mode == 3) fill(w);
    if (mode == 3) w.getBag().setStackInSlot(0, new ItemStack(Items.EMERALD, 63));
    if (mode == 4) w.setOwnerUUID(UUID.randomUUID());
    var c = carrier(h, a, owner);
    c.getBag().setStackInSlot(0, new ItemStack(Items.EMERALD, 5));
    h.startSequence()
        .thenWaitUntil(
            () -> h.assertTrue(!c.hasCargo(), "Carrier deposits cargo: " + c.getTransportStatus()))
        .thenExecute(
            () -> {
              int stored = count(w.getBag(), Items.EMERALD),
                  boxed = count(chest.inventory(), Items.EMERALD);
              h.assertTrue(
                  mode == 0
                      ? stored == 5 && boxed == 0
                      : mode == 3 ? stored == 64 && boxed == 4 : stored == 0 && boxed == 5,
                  "Priority and fallback preserve exact counts");
              c.discard();
              w.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_ten", timeoutTicks = 500)
  public static void warehousePreferredOverNearerChest(GameTestHelper h) {
    deposits(h, 0);
  }

  @GameTest(template = "empty", batch = "order_ten", timeoutTicks = 500)
  public static void fullWarehouseFallsBackToChest(GameTestHelper h) {
    deposits(h, 1);
  }

  @GameTest(template = "empty", batch = "order_ten", timeoutTicks = 500)
  public static void partialWarehouseTransferContinuesToChest(GameTestHelper h) {
    deposits(h, 3);
  }

  @GameTest(template = "empty", batch = "order_ten", timeoutTicks = 500)
  public static void foreignWarehouseIsExcluded(GameTestHelper h) {
    deposits(h, 4);
  }

  @GameTest(template = "empty", batch = "order_ten", timeoutTicks = 500)
  public static void fullChestIsSkippedForAnotherChest(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var first = a.chest(7, 101, 10);
    var other = a.chest(10, 101, 9);
    for (int i = 0; i < first.inventory().getSlots(); i++)
      first.inventory().insertItem(i, new ItemStack(Items.DIRT, 64), false);
    var c = carrier(h, a, owner);
    c.getBag().setStackInSlot(0, new ItemStack(Items.EMERALD, 5));
    h.startSequence()
        .thenWaitUntil(() -> h.assertTrue(!c.hasCargo(), "Alternative chest receives cargo"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  count(first.inventory(), Items.EMERALD) == 0
                      && count(other.inventory(), Items.EMERALD) == 5,
                  "Full first chest skipped");
              c.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_ten", timeoutTicks = 170)
  public static void warehouseKeepsCargoAndFullStorageDoesNotRequestUnloading(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var w = warehouse(h, a, owner, 8.5, 8.5);
    var chest = a.chest(10, 101, 8);
    w.setNoAi(false);
    w.setWorking(true);
    fill(w);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    w.getWorkStatus().reason() == WorkStatus.Reason.STORAGE_FULL,
                    "Full warehouse pauses"))
        .thenIdle(35)
        .thenExecute(
            () -> {
              h.assertTrue(
                  count(w.getBag(), Items.DIRT) == 72 * 64
                      && count(chest.inventory(), Items.DIRT) == 0
                      && !w.needsUnloading(),
                  "No automatic offload or cargo loop");
              w.getBag().setStackInSlot(0, ItemStack.EMPTY);
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    w.getWorkStatus().reason() == WorkStatus.Reason.NONE,
                    "Making space resumes warehouse"))
        .thenExecute(
            () -> {
              w.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_ten", timeoutTicks = 300)
  public static void warehouseCollectsBlockedWorkerWithoutAnyChest(GameTestHelper h) {
    var s = site(h, 0);
    fill(s.healer);
    s.patient.setHealth(s.patient.getMaxHealth());
    var w = warehouse(h, s.area, s.owner, 8.5, 8.5);
    w.setNoAi(false);
    w.setWorking(true);
    s.start()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    count(w.getBag(), Items.DIRT) > 0,
                    "Warehouse collects blocked worker cargo without chest"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  w.getActivityProgress() > 0
                      && count(s.healer.getBag(), Items.DIRT) + count(w.getBag(), Items.DIRT)
                          == 9 * 64,
                  "Collection grows warehouse and preserves cargo");
              w.discard();
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_ten", timeoutTicks = 650)
  public static void transportSuppliesHealerFromWaitingWarehouse(GameTestHelper h) {
    var s = site(h, 0);
    s.patient.setHealth(s.patient.getMaxHealth());
    var w = warehouse(h, s.area, s.owner, 10.5, 8.5);
    w.getBag().setStackInSlot(0, new ItemStack(Items.SLIME_BALL, 37));
    var c = carrier(h, s.area, s.owner);
    s.start()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    count(s.healer.getBag(), Items.SLIME_BALL) == 37,
                    "Healer supplied from warehouse; carrier="
                        + c.getTransportStatus()
                        + " @"
                        + c.position()
                        + " carried="
                        + count(c.getBag(), Items.SLIME_BALL)
                        + " source="
                        + count(w.getBag(), Items.SLIME_BALL)
                        + " healer="
                        + s.healer.getHealingStatus()
                        + " bag="
                        + count(s.healer.getBag(), Items.SLIME_BALL)
                        + " @"
                        + s.healer.position()))
        .thenExecute(
            () -> {
              h.assertTrue(
                  count(w.getBag(), Items.SLIME_BALL) == 0 && w.isOrderedToSit() && !c.hasCargo(),
                  "Partial stock delivered unchanged; waiting warehouse available");
              w.discard();
              c.discard();
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_ten", timeoutTicks = 180)
  public static void waitAndFollowSuspendTreatment(GameTestHelper h) {
    var s = site(h, 2);
    s.healer.setWaiting(true);
    s.start()
        .thenIdle(40)
        .thenExecute(
            () -> {
              h.assertTrue(s.patient.getHealth() == 5, "Wait does not heal");
              s.healer.setWorking(false);
            })
        .thenIdle(40)
        .thenExecute(
            () -> {
              h.assertTrue(
                  s.patient.getHealth() == 5 && count(s.healer.getBag(), Items.SLIME_BALL) == 2,
                  "Follow does not heal");
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_ten", timeoutTicks = 160)
  public static void healingClampsAtMaximumHealth(GameTestHelper h) {
    var s = site(h, 2);
    s.patient.setHealth(14);
    s.start()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    s.patient.getHealth() == s.patient.getMaxHealth(), "Health reaches maximum"))
        .thenIdle(30)
        .thenExecute(
            () -> {
              h.assertTrue(
                  count(s.healer.getBag(), Items.SLIME_BALL) == 1,
                  "One successful clamped treatment; no cost at full health");
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_ten", timeoutTicks = 450)
  public static void exhaustedHealerRestsUntilQuarterStaminaBeforeTreatment(GameTestHelper h) {
    var s = site(h, 1);
    s.healer.setTunedVitals(16, 0, 0);
    s.start()
        .thenIdle(80)
        .thenExecute(
            () ->
                h.assertTrue(
                    s.patient.getHealth() == 5
                        && count(s.healer.getBag(), Items.SLIME_BALL) == 1
                        && s.healer.getHealingStatus() == HealingSlime.Status.RESTING,
                    "Exhaustion pauses treatment without spending balls"))
        .thenWaitUntil(
            () -> h.assertTrue(s.patient.getHealth() == 9, "Recovered healer resumes treatment"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  s.healer.getStamina() >= 24 && count(s.healer.getBag(), Items.SLIME_BALL) == 0,
                  "Quarter-stamina threshold and one-ball cost");
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_ten", timeoutTicks = 500)
  public static void fullWarehouseIsSkippedForAnotherWarehouseBeforeChest(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var first = warehouse(h, a, owner, 8.5, 10.5);
    fill(first);
    var second = warehouse(h, a, owner, 10.5, 8.5);
    var chest = a.chest(6, 101, 10);
    var c = carrier(h, a, owner);
    c.getBag().setStackInSlot(0, new ItemStack(Items.EMERALD, 5));
    h.startSequence()
        .thenWaitUntil(() -> h.assertTrue(!c.hasCargo(), "Second warehouse accepts cargo"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  count(second.getBag(), Items.EMERALD) == 5
                      && count(chest.inventory(), Items.EMERALD) == 0,
                  "Available warehouse remains preferred over chest");
              first.discard();
              second.discard();
              c.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_ten", timeoutTicks = 500)
  public static void ownershipChangeInvalidatesWarehouseBeforeTransfer(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var w = warehouse(h, a, owner, 13.5, 8.5);
    var chest = a.chest(6, 101, 10);
    var c = carrier(h, a, owner);
    c.getBag().setStackInSlot(0, new ItemStack(Items.EMERALD, 5));
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    c.getTransportStatus() == TransportSlime.Status.DELIVERING,
                    "Warehouse selected"))
        .thenExecute(() -> w.setOwnerUUID(UUID.randomUUID()))
        .thenWaitUntil(() -> h.assertTrue(!c.hasCargo(), "Carrier retargets accessible chest"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  count(w.getBag(), Items.EMERALD) == 0
                      && count(chest.inventory(), Items.EMERALD) == 5,
                  "Changed owner excludes warehouse before transfer");
              w.discard();
              c.discard();
              a.close();
            })
        .thenSucceed();
  }
}
