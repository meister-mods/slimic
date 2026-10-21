package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MiningDrainageGameTests {
  private record Basin(
      LoadedWorkArea area, MiningSlime miner, List<BlockPos> water, ItemStack bucket) {}

  private static Basin basin(GameTestHelper h, int width, double reach, float stamina) {
    var a = new LoadedWorkArea(h);
    // This is a finite basin; exterior terrain must not feed water into the fixture.
    a.sealSurroundingFluids();
    var water = new ArrayList<BlockPos>();
    int x = 4, z = 4;
    for (int dx = -1; dx <= width; dx++)
      for (int dz = -1; dz <= width; dz++) {
        var pos = BlockPos.containing(a.point(x + dx, 100, z + dz));
        h.getLevel().setBlockAndUpdate(pos.below(), Blocks.BEDROCK.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState());
        if (dx >= 0 && dx < width && dz >= 0 && dz < width) water.add(pos);
      }
    for (var pos : water) h.getLevel().setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
    var miner = ModEntities.MINING_SLIME.get().create(h.getLevel());
    miner.moveTo(a.point(3.5, 101, 4.5));
    miner.tame(h.makeMockSurvivalPlayer());
    miner.setTool(0, new ItemStack(Items.IRON_PICKAXE));
    miner.onReleased();
    miner.setWorking(true);
    SlimeStat.REACH.tune(miner, reach);
    SlimeStat.DETECTION_RANGE.tune(miner, Math.max(8, reach));
    miner.setTunedVitals(16, stamina, 0);
    var bucket = new ItemStack(Items.BUCKET, 3);
    bucket.getOrCreateTag().putString("Drainage", "preserved");
    miner.getBag().setStackInSlot(0, bucket.copy());
    return new Basin(a, miner, water, bucket);
  }

  private static GameTestSequence start(GameTestHelper h, Basin b, BlockPos... otherChunks) {
    return h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(b.miner.blockPosition())
                        && Arrays.stream(otherChunks)
                            .allMatch(h.getLevel()::isPositionEntityTicking),
                    "Basin chunks are entity-ticking"))
        .thenExecute(() -> h.getLevel().addFreshEntity(b.miner));
  }

  private static void drain(GameTestHelper h, int width) {
    var b = basin(h, width, 3, 100);
    start(h, b)
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    b.water.stream().allMatch(p -> h.getLevel().getFluidState(p).isEmpty()),
                    "Finite infinite-water basin must drain; size="
                        + width
                        + " status="
                        + b.miner.getMiningStatus()
                        + " progress="
                        + b.miner.getActivityProgress()
                        + " alive="
                        + b.miner.isAlive()
                        + " health="
                        + b.miner.getHealth()
                        + " stamina="
                        + b.miner.getStamina()
                        + " pos="
                        + b.miner.position()
                        + " sources="
                        + b.water.stream()
                            .filter(p -> h.getLevel().getFluidState(p).isSource())
                            .count()
                        + " remaining="
                        + b.water.stream()
                            .filter(p -> !h.getLevel().getFluidState(p).isEmpty())
                            .count()))
        .thenExecute(() -> b.miner.setWorking(false))
        .thenIdle(60)
        .thenExecute(
            () -> {
              h.assertTrue(
                  b.water.stream().allMatch(p -> h.getLevel().getFluidState(p).isEmpty()),
                  "Water remains absent after source protection is released");
              h.assertTrue(
                  ItemStack.matches(b.bucket, b.miner.getBag().getStackInSlot(0)),
                  "Bucket count and data remain unchanged");
              b.miner.discard();
              b.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "drainage", timeoutTicks = 400)
  public static void drainsTwoByTwoInfiniteWaterWithoutRefilling(GameTestHelper h) {
    drain(h, 2);
  }

  @GameTest(template = "empty", batch = "drainage", timeoutTicks = 600)
  public static void drainsThreeByThreeInfiniteWaterWithoutRefilling(GameTestHelper h) {
    drain(h, 3);
  }

  @GameTest(template = "empty", batch = "drainage", timeoutTicks = 4800)
  public static void drainsSevenBySevenInfiniteWaterWithDefaultReach(GameTestHelper h) {
    drain(h, 7);
  }

  @GameTest(template = "empty", batch = "drainage", timeoutTicks = 120)
  public static void oneActionDrainsAtMostThreeByThreeAndPaysPerCell(GameTestHelper h) {
    var b = basin(h, 3, 5, 100);
    start(h, b)
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    b.miner.getActivityProgress() >= 9, "One batch removes the nine source cells"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  b.miner.getActivityProgress() == 9
                      && b.miner.getStamina() == 91
                      && b.water.stream().allMatch(p -> h.getLevel().getFluidState(p).isEmpty()),
                  "Nine successful removals cost nine stamina and retain per-cell growth");
              h.assertTrue(
                  ItemStack.matches(b.bucket, b.miner.getBag().getStackInSlot(0))
                      && b.miner.getPickaxe().getDamageValue() == 0,
                  "Batch uses neither bucket contents nor tool durability");
              b.miner.discard();
              b.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "drainage", timeoutTicks = 120)
  public static void largerPoolStartsAtAnExposedCornerWithOneThreeByThreePatch(GameTestHelper h) {
    var b = basin(h, 7, 32, 100);
    start(h, b)
        .thenWaitUntil(() -> h.assertTrue(b.miner.getActivityProgress() > 0, "First fluid action"))
        .thenExecute(
            () -> {
              var dry =
                  b.water.stream().filter(p -> h.getLevel().getFluidState(p).isEmpty()).toList();
              h.assertTrue(
                  dry.size() == 9 && b.miner.getActivityProgress() == 9,
                  "One action has nine cells, not the whole pool");
              var corner = b.water.get(0);
              h.assertTrue(
                  dry.stream()
                      .allMatch(
                          p ->
                              p.getX() >= corner.getX()
                                  && p.getX() < corner.getX() + 3
                                  && p.getZ() >= corner.getZ()
                                  && p.getZ() < corner.getZ() + 3),
                  "First patch starts at the exposed nearest corner");
              b.miner.discard();
              b.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "drainage", timeoutTicks = 120)
  public static void batchCannotSpendMoreStaminaThanAvailable(GameTestHelper h) {
    var b = basin(h, 3, 5, 3);
    start(h, b)
        .thenWaitUntil(
            () -> h.assertTrue(b.miner.getActivityProgress() > 0, "Limited-stamina batch"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  b.miner.getStamina() == 0
                      && b.miner.getActivityProgress() == 3
                      && b.water.stream()
                              .filter(p -> h.getLevel().getFluidState(p).isEmpty())
                              .count()
                          == 3,
                  "Exactly three cells removed with three stamina");
              b.miner.discard();
              b.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "drainage", timeoutTicks = 120)
  public static void protectedCellSurvivesWhileOtherBatchCellsDrain(GameTestHelper h) {
    var b = basin(h, 3, 5, 100);
    var protectedPos = b.water.get(4);
    int[] calls = {0};
    Consumer<BlockEvent.BreakEvent> cancel =
        e -> {
          if (e.getPos().equals(protectedPos)) {
            calls[0]++;
            e.setCanceled(true);
          }
        };
    MinecraftForge.EVENT_BUS.addListener(cancel);
    h.runAfterDelay(119, () -> MinecraftForge.EVENT_BUS.unregister(cancel));
    start(h, b)
        .thenWaitUntil(
            () -> h.assertTrue(b.miner.getActivityProgress() > 0, "Permitted cells drained"))
        .thenExecute(
            () -> {
              MinecraftForge.EVENT_BUS.unregister(cancel);
              h.assertTrue(
                  calls[0] == 1
                      && h.getLevel().getFluidState(protectedPos).isSource()
                      && b.miner.getActivityProgress() == 8
                      && b.miner.getStamina() == 92,
                  "Protected cell untouched, eight permitted removals paid individually");
              b.miner.discard();
              b.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "drainage", timeoutTicks = 1000)
  public static void sourceGuardSurvivesRecoveryAndDoesNotChangeNeighbouringChunks(
      GameTestHelper h) {
    var b = basin(h, 3, 5, 1);
    var outside = new ArrayList<BlockPos>();
    for (int x = 16; x < 20; x++)
      for (int z = 4; z < 8; z++) {
        var pos = BlockPos.containing(b.area.point(x, 100, z));
        h.getLevel().setBlockAndUpdate(pos.below(), Blocks.BEDROCK.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState());
        if (x >= 17 && x < 19 && z >= 5 && z < 7) outside.add(pos);
      }
    for (var pos : outside) h.getLevel().setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
    var copy = ModEntities.MINING_SLIME.get().create(h.getLevel());
    var drained = new BlockPos[1];
    start(h, b, outside.get(0))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    b.miner.getActivityProgress() == 1, "One drained cell and active source guard"))
        .thenExecute(
            () -> {
              drained[0] =
                  b.water.stream()
                      .filter(p -> h.getLevel().getFluidState(p).isEmpty())
                      .findFirst()
                      .orElseThrow();
              var saved = FriendlySlimeItem.fromSlime(b.miner).getTag().getCompound("SlimeData");
              h.assertTrue(
                  saved.getInt("MiningDrainedWaterLayer") == 100, "Current draining layer saved");
              copy.load(saved);
              b.miner.discard();
              h.getLevel().addFreshEntity(copy);
              h.getLevel().setBlockAndUpdate(outside.get(0), Blocks.AIR.defaultBlockState());
            })
        .thenIdle(35)
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getFluidState(outside.get(0)).isSource(),
                    "Native infinite-water regeneration outside quarry: "
                        + h.getLevel().getFluidState(outside.get(0))))
        .thenExecute(
            () -> {
              h.assertTrue(
                  !h.getLevel().getFluidState(drained[0]).isSource(),
                  "Recovered active quarry does not regenerate its removed source");
              h.assertTrue(
                  h.getLevel().getFluidState(outside.get(0)).isSource(),
                  "Native infinite-water regeneration outside quarry remains unchanged");
              h.assertTrue(
                  ItemStack.matches(b.bucket, copy.getBag().getStackInSlot(0)),
                  "Exact bucket retained through source-guard recovery");
              copy.discard();
              b.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "drainage", timeoutTicks = 140)
  public static void waitingReleasesTheSourceRegenerationGuard(GameTestHelper h) {
    var b = basin(h, 3, 5, 1);
    var drained = new BlockPos[1];
    start(h, b)
        .thenWaitUntil(
            () -> h.assertTrue(b.miner.getActivityProgress() == 1, "One water cell removed"))
        .thenExecute(
            () -> {
              drained[0] =
                  b.water.stream()
                      .filter(p -> h.getLevel().getFluidState(p).isEmpty())
                      .findFirst()
                      .orElseThrow();
              b.miner.setWaiting(true);
            })
        .thenIdle(35)
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getFluidState(drained[0]).isSource(),
                  "Waiting restores native source regeneration");
              h.assertTrue(
                  ItemStack.matches(b.bucket, b.miner.getBag().getStackInSlot(0)),
                  "Waiting preserves the original bucket");
              b.miner.discard();
              b.area.close();
            })
        .thenSucceed();
  }
}
