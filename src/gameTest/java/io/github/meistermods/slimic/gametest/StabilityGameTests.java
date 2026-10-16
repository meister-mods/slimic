package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.SlimeChestBlockEntity;
import io.github.meistermods.slimic.entity.CleaningSlime;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.entity.TransportSlime;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StabilityGameTests {
  private StabilityGameTests() {}

  @GameTest(template = "empty", batch = "stability")
  public static void sameCoordinatesInAnotherDimensionStartANewQuarry(GameTestHelper h) {
    MiningSlime miner = ModEntities.MINING_SLIME.get().create(h.getLevel());
    BlockPos origin = h.absolutePos(new BlockPos(5, 2, 5));
    miner.moveTo(Vec3.atBottomCenterOf(origin));
    miner.tame(h.makeMockSurvivalPlayer());
    miner.onReleased();
    miner.getQuarryPlan().descend();
    miner.setWorking(false);
    miner.setWaiting(true);
    miner.setHealth(7);
    miner.setTunedVitals(7, 39, 4);
    ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
    pickaxe.setDamageValue(31);
    miner.setTool(0, pickaxe);
    miner.getBag().setStackInSlot(0, new ItemStack(Items.EMERALD, 5));
    ItemStack stored = FriendlySlimeItem.fromSlime(miner);
    ServerLevel destination = h.getLevel().getServer().getLevel(Level.NETHER);
    h.assertTrue(destination != null, "The Nether must be available for dimension restoration");
    MiningSlime restored = ModEntities.MINING_SLIME.get().create(destination);
    restored.load(stored.getTag().getCompound("SlimeData"));
    restored.moveTo(origin.getX() + .5, 75, origin.getZ() + .5);
    restored.onReleased();
    h.assertTrue(
        restored.isWorking()
            && !restored.isOrderedToSit()
            && restored.getQuarryPlan().topFloor() == 74
            && restored.getQuarryPlan().layer() == 76,
        "A different dimension is a different quarry even at the same X/Z chunk coordinates");
    h.assertTrue(
        restored.getUUID().equals(miner.getUUID())
            && restored.getOwnerUUID().equals(miner.getOwnerUUID())
            && restored.getHealth() == 7
            && restored.getStamina() == 39
            && restored.getStatPoints() == 4
            && ItemStack.matches(restored.getPickaxe(), pickaxe)
            && restored.getBag().getStackInSlot(0).getCount() == 5,
        "Changing dimension must retain identity, owner, vitals, upgrades, equipment and cargo");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "stability")
  public static void olderQuarrySavesAdoptTheirExistingDimension(GameTestHelper h) {
    MiningSlime miner = ModEntities.MINING_SLIME.get().create(h.getLevel());
    miner.moveTo(h.absoluteVec(new Vec3(5.5, 2, 5.5)));
    miner.tame(h.makeMockSurvivalPlayer());
    miner.onReleased();
    miner.getQuarryPlan().descend();
    miner.setWaiting(true);
    int layer = miner.getQuarryPlan().layer();
    CompoundTag saved = miner.saveWithoutId(new CompoundTag());
    saved.remove("WorkDimension");
    MiningSlime restored = ModEntities.MINING_SLIME.get().create(h.getLevel());
    restored.load(saved);
    restored.onReleased();
    h.assertTrue(
        restored.getQuarryPlan().layer() == layer && restored.isOrderedToSit(),
        "Legacy saves in their current world must retain progress and orders");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "stability", timeoutTicks = 80)
  public static void dimensionTravelRecentersTransportAndCleaningWithoutLosingCargo(
      GameTestHelper h) {
    LoadedWorkArea arena = new LoadedWorkArea(h);
    var destination = h.getLevel().getServer().getLevel(Level.NETHER);
    h.assertTrue(destination != null, "The Nether must be available");
    var carrier = arena.carrier(new Vec3(5, 101, 5));
    var cleaner = ModEntities.CLEANING_SLIME.get().create(h.getLevel());
    cleaner.moveTo(arena.point(8, 101, 5));
    cleaner.setOwnerUUID(carrier.getOwnerUUID());
    cleaner.setTame(true);
    cleaner.setNoAi(true);
    cleaner.onReleased();
    cleaner.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
    carrier.getBag().setStackInSlot(0, new ItemStack(Items.EMERALD, 7));
    carrier.setNoAi(true);
    carrier.setWorking(false);
    cleaner.setWaiting(true);
    h.getLevel().addFreshEntity(cleaner);
    Vec3 arrival = arena.point(5, 80, 5);
    Vec3 cleaningArrival = arrival.add(3, 0, 0);
    h.runAfterDelay(
        5,
        () -> {
          TransportSlime moved =
              (TransportSlime) carrier.changeDimension(destination, teleporter(arrival));
          CleaningSlime swept =
              (CleaningSlime) cleaner.changeDimension(destination, teleporter(cleaningArrival));
          try {
            h.assertTrue(
                moved != null
                    && swept != null
                    && moved.getWorkOrigin().equals(BlockPos.containing(arrival))
                    && swept.getCleaningHome().distanceToSqr(cleaningArrival) < .001,
                "World-visible dimension transfers must rebase fixed work areas at the"
                    + " destination");
            h.assertTrue(
                !moved.isWorking()
                    && swept.isOrderedToSit()
                    && moved.getBag().getStackInSlot(0).getCount() == 7
                    && swept.getBag().getStackInSlot(0).getCount() == 3,
                "Portal travel must retain work orders and exact cargo");
            h.succeed();
          } finally {
            if (moved != null) moved.discard();
            if (swept != null) swept.discard();
            arena.close();
          }
        });
  }

  private static ITeleporter teleporter(Vec3 position) {
    return new ITeleporter() {
      @Override
      public PortalInfo getPortalInfo(
          Entity entity, ServerLevel destination, Function<ServerLevel, PortalInfo> fallback) {
        return new PortalInfo(position, Vec3.ZERO, 0, 0);
      }
    };
  }

  @GameTest(template = "empty", batch = "stability")
  public static void commandStyleDimensionRestorationRebasesBeforeTheFirstAITick(GameTestHelper h) {
    LoadedWorkArea arena = new LoadedWorkArea(h);
    TransportSlime carrier = arena.carrier(new Vec3(5, 101, 5));
    carrier.setNoAi(true);
    carrier.setWorking(false);
    carrier.getBag().setStackInSlot(0, new ItemStack(Items.EMERALD, 7));
    TransportSlime restored =
        ModEntities.TRANSPORT_SLIME.get().create(h.getLevel().getServer().getLevel(Level.NETHER));
    restored.load(carrier.saveWithoutId(new CompoundTag()));
    restored.moveTo(arena.point(12, 80, 12));
    restored.tick();
    h.assertTrue(
        restored.getWorkOrigin().equals(restored.blockPosition())
            && !restored.isWorking()
            && restored.getBag().getStackInSlot(0).getCount() == 7,
        "A dimension-restored copy must rebase before AI while preserving orders and cargo");
    carrier.discard();
    arena.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "stability")
  public static void sameDimensionReloadRetainsTheOriginalWorkArea(GameTestHelper h) {
    LoadedWorkArea arena = new LoadedWorkArea(h);
    TransportSlime carrier = arena.carrier(new Vec3(5, 101, 5));
    carrier.setNoAi(true);
    BlockPos home = carrier.getWorkOrigin();
    carrier.moveTo(arena.point(11, 101, 5));
    TransportSlime restored = ModEntities.TRANSPORT_SLIME.get().create(h.getLevel());
    restored.load(carrier.saveWithoutId(new CompoundTag()));
    restored.tick();
    h.assertTrue(
        restored.getWorkOrigin().equals(home) && !home.equals(restored.blockPosition()),
        "Normal world reloads must not shift an active worker's fixed origin to its current"
            + " position");
    carrier.discard();
    arena.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "stability")
  public static void cancelledDimensionTravelRetainsTheSourceWorkArea(GameTestHelper h) {
    LoadedWorkArea arena = new LoadedWorkArea(h);
    TransportSlime carrier = arena.carrier(new Vec3(5, 101, 5));
    carrier.setNoAi(true);
    BlockPos home = carrier.getWorkOrigin();
    Entity result =
        carrier.changeDimension(
            h.getLevel().getServer().getLevel(Level.NETHER),
            new ITeleporter() {
              @Override
              public PortalInfo getPortalInfo(
                  Entity entity,
                  ServerLevel destination,
                  Function<ServerLevel, PortalInfo> fallback) {
                return null;
              }
            });
    h.assertTrue(
        result == null
            && carrier.isAlive()
            && carrier.level() == h.getLevel()
            && carrier.getWorkOrigin().equals(home),
        "A cancelled portal must not rebase or remove the original worker");
    carrier.discard();
    arena.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "stability", timeoutTicks = 5000)
  public static void slowCarrierKeepsMakingProgressPastFourHundredTicks(GameTestHelper h) {
    LoadedWorkArea arena = new LoadedWorkArea(h);
    TransportSlime carrier = arena.carrier(new Vec3(8, 101, 8));
    SlimeStat.DETECTION_RANGE.tune(carrier, 16);
    SlimeStat.MOVEMENT_SPEED.tune(carrier, .2);
    carrier.moveTo(arena.point(1, 101, 8));
    carrier.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 11));
    SlimeChestBlockEntity chest = arena.chest(14, 101, 8);
    boolean[] checked = {false};
    h.onEachTick(
        () -> {
          if (!checked[0] && carrier.tickCount >= 450) {
            h.assertTrue(
                !carrier.isWorkBlocked()
                    && carrier.position().distanceToSqr(arena.point(1, 101, 8)) > .01,
                "Slow but advancing delivery must not be declared blocked at 400 elapsed ticks: "
                    + carrier.getTransportStatus()
                    + " position="
                    + carrier.position());
            checked[0] = true;
          }
        });
    h.succeedWhen(
        () -> {
          h.assertTrue(
              checked[0]
                  && chest.getItem(0).is(Items.DIAMOND)
                  && chest.getItem(0).getCount() == 11
                  && !carrier.hasCargo(),
              "The slow carrier must finish one exact delivery after its progress check");
          carrier.discard();
          arena.close();
        });
  }

  @GameTest(template = "empty", batch = "stability", timeoutTicks = 500)
  public static void transportTransferCooldownSurvivesRecovery(GameTestHelper h) {
    LoadedWorkArea arena = new LoadedWorkArea(h);
    TransportSlime carrier = arena.carrier(new Vec3(5, 101, 5));
    SlimeStat.WORK_SPEED.tune(carrier, .1);
    carrier.setTunedVitals(16, 1, 0);
    carrier.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND));
    carrier.getBag().setStackInSlot(1, new ItemStack(Items.EMERALD));
    SlimeChestBlockEntity chest = arena.chest(6, 101, 5);
    boolean[] restored = {false};
    h.onEachTick(
        () -> {
          if (restored[0] || !chest.getItem(0).is(Items.DIAMOND)) return;
          restored[0] = true;
          ItemStack stored = FriendlySlimeItem.fromSlime(carrier);
          carrier.discard();
          TransportSlime copy = ModEntities.TRANSPORT_SLIME.get().create(h.getLevel());
          copy.load(stored.getTag().getCompound("SlimeData"));
          copy.onReleased();
          copy.setTunedVitals(16, 100, 0);
          h.getLevel().addFreshEntity(copy);
          boolean[] waited = {false};
          h.runAfterDelay(
              40,
              () -> {
                h.assertTrue(
                    copy.hasCargo() && chest.getItem(1).isEmpty(),
                    "Recovery must preserve the remaining transfer interval even with enough"
                        + " stamina");
                waited[0] = true;
              });
          h.succeedWhen(
              () -> {
                h.assertTrue(
                    waited[0] && chest.getItem(1).is(Items.EMERALD) && !copy.hasCargo(),
                    "The retained cooldown must expire and allow exactly one subsequent transfer");
                copy.discard();
                arena.close();
              });
        });
  }

  @GameTest(template = "empty", batch = "stability", timeoutTicks = 1000)
  public static void verySlowMovementStillCountsAsDeliveryProgress(GameTestHelper h) {
    LoadedWorkArea arena = new LoadedWorkArea(h);
    TransportSlime carrier = arena.carrier(new Vec3(8, 101, 8));
    SlimeStat.DETECTION_RANGE.tune(carrier, 16);
    SlimeStat.MOVEMENT_SPEED.tune(carrier, .05);
    carrier.moveTo(arena.point(1, 101, 8));
    carrier.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 11));
    arena.chest(14, 101, 8);
    h.succeedWhen(
        () -> {
          h.assertTrue(
              carrier.tickCount >= 450
                  && !carrier.isWorkBlocked()
                  && carrier.position().distanceToSqr(arena.point(1, 101, 8)) > .01,
              "Sub-block movement at a very low speed must count as advancing delivery progress");
          carrier.discard();
          arena.close();
        });
  }

  @GameTest(template = "empty", batch = "stability", timeoutTicks = 1000)
  public static void stationaryCarrierStillReportsAStalledDelivery(GameTestHelper h) {
    LoadedWorkArea arena = new LoadedWorkArea(h);
    TransportSlime carrier = arena.carrier(new Vec3(8, 101, 8));
    SlimeStat.DETECTION_RANGE.tune(carrier, 16);
    SlimeStat.MOVEMENT_SPEED.tune(carrier, 0);
    carrier.moveTo(arena.point(1, 101, 8));
    carrier.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 11));
    SlimeChestBlockEntity chest = arena.chest(14, 101, 8);
    h.succeedWhen(
        () -> {
          h.assertTrue(
              carrier.tickCount >= 450
                  && carrier.isWorkBlocked()
                  && carrier.getBag().getStackInSlot(0).getCount() == 11
                  && chest.isEmpty(),
              "A carrier making no horizontal progress must still report blocked without losing"
                  + " cargo");
          carrier.discard();
          arena.close();
        });
  }
}
