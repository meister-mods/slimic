package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.ModEntities;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CooperativeQuarryGameTests {
  private static MiningSlime miner(
      GameTestHelper h, LoadedWorkArea area, Player owner, double x, double y, double z) {
    var miner = ModEntities.MINING_SLIME.get().create(h.getLevel());
    miner.tame(owner);
    miner.moveTo(area.point(x, y, z));
    miner.setNoAi(true);
    miner.onReleased();
    miner.setWorking(true);
    miner.setTool(0, new ItemStack(Items.NETHERITE_PICKAXE));
    miner.setTool(1, new ItemStack(Items.NETHERITE_SHOVEL));
    return miner;
  }

  private static Object call(MiningSlime miner, String name, Class<?>[] signature, Object... args) {
    try {
      var method = MiningSlime.class.getDeclaredMethod(name, signature);
      method.setAccessible(true);
      return method.invoke(miner, args);
    } catch (ReflectiveOperationException e) {
      if (e instanceof InvocationTargetException invoked
          && invoked.getCause() instanceof RuntimeException cause) throw cause;
      throw new IllegalStateException(e);
    }
  }

  private static void target(MiningSlime miner, BlockPos target) {
    try {
      var field = MiningSlime.class.getDeclaredField("target");
      field.setAccessible(true);
      field.set(miner, target);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static boolean terrain(MiningSlime miner, BlockPos pos) {
    return (boolean)
        call(
            miner,
            "terrain",
            new Class<?>[] {BlockPos.class, BlockState.class},
            pos,
            miner.level().getBlockState(pos));
  }

  private static int count(MiningSlime miner, net.minecraft.world.item.Item item) {
    int result = 0;
    for (int i = 0; i < miner.getBag().getSlots(); i++)
      if (miner.getBag().getStackInSlot(i).is(item))
        result += miner.getBag().getStackInSlot(i).getCount();
    return result;
  }

  @GameTest(template = "empty", batch = "cooperative_quarry")
  public static void differentStartingHeightsShareOneStaircase(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var high = miner(h, area, owner, 8.5, 104, 8.5);
    var low = miner(h, area, owner, 8.5, 100, 8.5);
    h.assertTrue(
        high.getQuarryPlan().topFloor() == 103
            && low.getQuarryPlan().topFloor() == 99
            && high.getQuarryPlan().rampOrigin() == low.getQuarryPlan().rampOrigin(),
        "Different excavation starts share a staircase without changing their own starting layers");
    for (int y = 95; y <= 99; y++)
      h.assertTrue(
          high.getQuarryPlan().floorsAt(y).equals(low.getQuarryPlan().floorsAt(y)),
          "Both miners reserve identical treads below their starting floors");
    area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "cooperative_quarry")
  public static void upperCompanionLandingIsProtectedAboveTheLowerMinersOwnTop(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var high = miner(h, area, owner, 8.5, 102, 8.5);
    var low = miner(h, area, owner, 8.5, 100, 8.5);
    var floor = high.getQuarryPlan().floorsAt(101).get(0);
    h.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
    h.assertTrue(
        !low.getQuarryPlan().isRampFloor(floor) && !terrain(low, floor),
        "The lower miner preserves another miner's landing even above its own ramp limit");
    area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "cooperative_quarry")
  public static void staircaseSurvivesBuilderRecoveryAndDataReload(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var high = miner(h, area, owner, 8.5, 104, 8.5);
    var chunk = high.getMiningChunk();
    var floor = high.getQuarryPlan().floorsAt(101).get(0);
    h.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
    var stored = FriendlySlimeItem.fromSlime(high);
    high.discard();
    var data = QuarryStaircaseData.get(h.getLevel());
    var saved = data.save(new CompoundTag());
    var loaded =
        QuarryStaircaseData.load(
            saved, h.getLevel().getMinBuildHeight(), h.getLevel().getMaxBuildHeight());
    h.assertTrue(
        loaded.protects(floor), "A reloaded world record preserves stairs without a live builder");
    var low = miner(h, area, h.makeMockSurvivalPlayer(), 8.5, 100, 8.5);
    h.assertTrue(
        !terrain(low, floor), "A different owner still preserves physical quarry infrastructure");
    var restored = ModEntities.MINING_SLIME.get().create(h.getLevel());
    restored.load(stored.getTag().getCompound("SlimeData"));
    restored.onReleased();
    h.assertTrue(
        restored.getMiningChunk().equals(chunk)
            && restored.getQuarryPlan().rampOrigin() == high.getQuarryPlan().rampOrigin(),
        "Recovery retains the selected staircase origin");
    area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "cooperative_quarry")
  public static void freshWaitingMinerDoesNotReserveAStaircase(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var data = QuarryStaircaseData.get(h.getLevel());
    var before = data.save(new CompoundTag()).copy();
    var idle = ModEntities.MINING_SLIME.get().create(h.getLevel());
    idle.tame(h.makeMockSurvivalPlayer());
    idle.moveTo(area.point(8.5, 108, 8.5));
    idle.onReleased();
    idle.setWaiting(true);
    idle.shareStaircase();
    h.assertTrue(
        data.save(new CompoundTag()).equals(before),
        "A fresh waiting summon has no worksite reservation");
    var active = miner(h, area, h.makeMockSurvivalPlayer(), 8.5, 101, 8.5);
    idle.setWorking(true);
    h.assertTrue(
        idle.getQuarryPlan().rampOrigin() == active.getQuarryPlan().rampOrigin(),
        "Explicit Mine joins the existing worksite");
    area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "cooperative_quarry")
  public static void legacyDifferentStaircasesRemainProtectedTogether(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var first = miner(h, area, owner, 8.5, 101, 8.5);
    var chunk = first.getMiningChunk();
    var old = new CompoundTag();
    old.putInt("TopFloor", 101);
    old.putInt("Layer", 100);
    var legacy = QuarryPlan.load(chunk, old, -64, 320);
    var data = QuarryStaircaseData.get(h.getLevel());
    data.register(chunk, legacy);
    var oldFloor = legacy.floorsAt(100).get(0);
    h.getLevel().setBlockAndUpdate(oldFloor, Blocks.STONE.defaultBlockState());
    h.assertTrue(
        !first.getQuarryPlan().isRampFloor(oldFloor) && !terrain(first, oldFloor),
        "Legacy stair profiles retain protection alongside the canonical profile");
    for (var floor : first.getQuarryPlan().floorsAt(100))
      h.assertTrue(
          data.protects(floor),
          "Registering legacy progress never replaces the original staircase");
    area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "cooperative_quarry")
  public static void worksiteLookupDoesNotLoadChunksOrProtectInteriors(GameTestHelper h) {
    var chunk = new ChunkPos(1000001, -1000001);
    var level = h.getLevel();
    h.assertTrue(!level.hasChunk(chunk.x, chunk.z), "Remote worksite starts unavailable");
    var data = QuarryStaircaseData.get(level);
    var plan = new QuarryPlan(chunk, 100, 99);
    data.register(chunk, plan);
    var floor = plan.floorsAt(99).get(0);
    h.assertTrue(
        data.protects(floor)
            && !data.protects(new BlockPos(chunk.getMinBlockX() + 8, 99, chunk.getMinBlockZ() + 8))
            && !data.protects(floor.east(16))
            && !level.hasChunk(chunk.x, chunk.z),
        "Only this chunk's perimeter is reserved, without chunk acquisition");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "cooperative_quarry")
  public static void sharedRepairUsesExactlyOneBlockAcrossTwoBuilders(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var first = miner(h, area, owner, 2.5, 101, 1.5);
    var second = miner(h, area, owner, 2.5, 101, 1.5);
    for (var miner : List.of(first, second)) {
      miner.getQuarryPlan().descend();
      miner.getQuarryPlan().descend();
      miner.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
    }
    var floor = first.getQuarryPlan().floorsAt(100).get(0);
    h.getLevel().setBlockAndUpdate(floor, Blocks.AIR.defaultBlockState());
    call(
        first,
        "ensureRamp",
        new Class<?>[] {net.minecraft.server.level.ServerLevel.class},
        h.getLevel());
    call(
        second,
        "ensureRamp",
        new Class<?>[] {net.minecraft.server.level.ServerLevel.class},
        h.getLevel());
    h.assertTrue(
        h.getLevel().getBlockState(floor).is(Blocks.COBBLESTONE)
            && count(first, Items.COBBLESTONE) + count(second, Items.COBBLESTONE) == 127,
        "Shared repair neither duplicates placement nor charges the second builder");
    area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "cooperative_quarry")
  public static void legacyRepairCannotFillAnotherStaircasesHeadroom(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    miner(h, area, owner, 8.5, 101, 8.5);
    var old = new CompoundTag();
    old.putInt("TopFloor", 101);
    old.putInt("Layer", 101);
    var legacy = miner(h, area, owner, 2.5, 102, 1.5);
    var saved = legacy.saveWithoutId(new CompoundTag());
    saved.put("MiningQuarry", old);
    legacy.load(saved);
    legacy.shareStaircase();
    legacy.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 8));
    var floor = legacy.getQuarryPlan().floorsAt(101).get(0);
    h.getLevel().setBlockAndUpdate(floor, Blocks.AIR.defaultBlockState());
    call(
        legacy,
        "ensureRamp",
        new Class<?>[] {net.minecraft.server.level.ServerLevel.class},
        h.getLevel());
    h.assertTrue(
        h.getLevel().getBlockState(floor).isAir()
            && count(legacy, Items.COBBLESTONE) == 8
            && legacy.getMiningStatus() == MiningSlime.Status.UNREACHABLE,
        "Conflicting legacy repair keeps the lower landing usable and consumes nothing");
    area.close();
    h.succeed();
  }

  public static final class ClaimDuringBreak {
    final BlockPos pos;
    final ChunkPos chunk;
    boolean fired;

    ClaimDuringBreak(BlockPos pos, ChunkPos chunk) {
      this.pos = pos;
      this.chunk = chunk;
    }

    @SubscribeEvent
    public void breaking(BlockEvent.BreakEvent event) {
      if (!event.getPos().equals(pos)) return;
      fired = true;
      var plan =
          new QuarryPlan(
              chunk,
              pos.getY() + QuarryPlan.rampPhase(pos.getX() & 15, pos.getZ() & 15) / 2,
              pos.getY());
      QuarryStaircaseData.get((net.minecraft.server.level.ServerLevel) event.getLevel())
          .register(chunk, plan);
    }
  }

  @GameTest(template = "empty", batch = "cooperative_quarry")
  public static void aStaircaseClaimDuringTheBreakEventCancelsTheQueuedBreak(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var miner = miner(h, area, h.makeMockSurvivalPlayer(), 2.5, 101, 3.5);
    var pos = BlockPos.containing(area.point(0, 101, 3));
    h.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
    miner.getPickaxe().enchant(Enchantments.BLOCK_EFFICIENCY, 5);
    SlimeStat.WORK_SPEED.tune(miner, 32);
    var hook = new ClaimDuringBreak(pos, miner.getMiningChunk());
    MinecraftForge.EVENT_BUS.register(hook);
    try {
      target(miner, pos);
      call(
          miner,
          "mine",
          new Class<?>[] {net.minecraft.server.level.ServerLevel.class},
          h.getLevel());
    } finally {
      MinecraftForge.EVENT_BUS.unregister(hook);
    }
    h.assertTrue(
        hook.fired
            && h.getLevel().getBlockState(pos).is(Blocks.STONE)
            && miner.getPickaxe().getDamageValue() == 0
            && miner.getStamina() == 100
            && !miner.hasCargo(),
        "A newly shared floor survives the final action check with no drops, wear or stamina"
            + " charge");
    area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "cooperative_quarry")
  public static void twoQueuedMinersProduceOnlyOneDropAndOneToolUse(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var first = miner(h, area, owner, 7.5, 101, 8.5);
    var second = miner(h, area, owner, 7.5, 101, 8.5);
    var pos = BlockPos.containing(area.point(9, 101, 8));
    h.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
    for (var miner : List.of(first, second)) {
      miner.getPickaxe().enchant(Enchantments.BLOCK_EFFICIENCY, 5);
      SlimeStat.WORK_SPEED.tune(miner, 32);
      target(miner, pos);
      call(
          miner,
          "mine",
          new Class<?>[] {net.minecraft.server.level.ServerLevel.class},
          h.getLevel());
    }
    h.assertTrue(
        h.getLevel().getBlockState(pos).isAir()
            && count(first, Items.COBBLESTONE) + count(second, Items.COBBLESTONE) == 1
            && first.getPickaxe().getDamageValue() + second.getPickaxe().getDamageValue() == 1,
        "Concurrent targets preserve exact loot and charge only the successful worker");
    area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "cooperative_quarry")
  public static void staircaseRecordPersistsToTheNativeWorldDataFile(GameTestHelper h)
      throws java.io.IOException {
    var area = new LoadedWorkArea(h);
    var builder = miner(h, area, h.makeMockSurvivalPlayer(), 8.5, 104, 8.5);
    var floor = builder.getQuarryPlan().floorsAt(100).get(0);
    builder.discard();
    h.getLevel().getDataStorage().save();
    var disk =
        h.getLevel()
            .getDataStorage()
            .readTagFromDisk(
                "slimic_quarry_staircases",
                net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
    var loaded =
        QuarryStaircaseData.load(
            disk.getCompound("data"),
            h.getLevel().getMinBuildHeight(),
            h.getLevel().getMaxBuildHeight());
    h.assertTrue(
        loaded.protects(floor),
        "Native compressed world data retains the staircase after its builder is gone");
    area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "cooperative_quarry")
  public static void staircaseRecordsAreIndependentBetweenDimensions(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var builder = miner(h, area, h.makeMockSurvivalPlayer(), 8.5, 101, 8.5);
    var floor = builder.getQuarryPlan().floorsAt(100).get(0);
    var nether = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
    h.assertTrue(
        nether != null
            && QuarryStaircaseData.get(h.getLevel()).protects(floor)
            && !QuarryStaircaseData.get(nether).protects(floor),
        "The same coordinates in another dimension have a separate worksite");
    area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "cooperative_quarry")
  public static void releaseJoinCannotImportAnotherDimensionsStaircase(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var builder = miner(h, area, h.makeMockSurvivalPlayer(), 8.5, 101, 8.5);
    var floor = builder.getQuarryPlan().floorsAt(100).get(0);
    var nether = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
    var data = QuarryStaircaseData.get(nether);
    var before = data.save(new CompoundTag()).copy();
    var moved = ModEntities.MINING_SLIME.get().create(nether);
    moved.load(FriendlySlimeItem.fromSlime(builder).getTag().getCompound("SlimeData"));
    moved.moveTo(floor.getX() + 32.5, 101, floor.getZ() + .5);
    // Restored items join the destination before onReleased updates their work anchor.
    FriendlySlimeInteractions.trackQuarryDrainage(
        new net.minecraftforge.event.entity.EntityJoinLevelEvent(moved, nether));
    h.assertTrue(
        data.save(new CompoundTag()).equals(before),
        "The join callback cannot import the previous dimension's worksite");
    moved.onReleased();
    h.assertTrue(
        data.protects(moved.getQuarryPlan().floorsAt(100).get(0))
            && !data.protects(floor)
            && QuarryStaircaseData.get(h.getLevel()).protects(floor),
        "Release registers only the new destination while the original staircase stays protected");
    area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "cooperative_quarry_integration", timeoutTicks = 20000)
  public static void threeMinersAtDifferentHeightsExcavateOneChunkWithoutBreakingTheStairs(
      GameTestHelper h) {
    var area = new LoadedWorkArea(h, 21000);
    var level = h.getLevel();
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++)
        for (int y = 96; y <= 104; y++)
          level.setBlockAndUpdate(
              BlockPos.containing(area.point(x, y, z)),
              (y == 96
                      ? Blocks.BEDROCK
                      : y <= 101 ? (y % 2 == 0 ? Blocks.STONE : Blocks.DIRT) : Blocks.AIR)
                  .defaultBlockState());
    for (int i = 0; i < 16; i++)
      for (int y = 96; y <= 104; y++)
        for (var pos :
            List.of(
                BlockPos.containing(area.point(-1, y, i)),
                BlockPos.containing(area.point(16, y, i)),
                BlockPos.containing(area.point(i, y, -1)),
                BlockPos.containing(area.point(i, y, 16))))
          level.setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState());
    var owner = h.makeMockSurvivalPlayer();
    var high = miner(h, area, owner, 8.5, 102, 8.5);
    var middle = miner(h, area, owner, 5.5, 100, 8.5);
    var low = miner(h, area, h.makeMockSurvivalPlayer(), 10.5, 98, 8.5);
    var workers = List.of(high, middle, low);
    for (var worker : List.of(middle, low))
      for (int x = worker.getBlockX() - 1; x <= worker.getBlockX() + 1; x++)
        for (int z = worker.getBlockZ() - 1; z <= worker.getBlockZ() + 1; z++)
          for (int y = worker.getBlockY(); y <= 103; y++)
            level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
    var gap = high.getQuarryPlan().floorsAt(100).get(0);
    level.setBlockAndUpdate(gap, Blocks.AIR.defaultBlockState());
    int initialTerrain = 0;
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++)
        for (int y = 97; y <= 101; y++) {
          var pos = BlockPos.containing(area.point(x, y, z));
          if (!high.getQuarryPlan().isRampFloor(pos) && !level.getBlockState(pos).isAir())
            initialTerrain++;
        }
    final int expectedCargo = initialTerrain + 192 - 1;
    for (var worker : workers) {
      SlimeStat.CAPACITY.tune(worker, 27);
      SlimeStat.STAMINA.tune(worker, 2000);
      SlimeStat.WORK_SPEED.tune(worker, 32);
      SlimeStat.REACH.tune(worker, 8);
      SlimeStat.DETECTION_RANGE.tune(worker, 28);
      worker.setTunedVitals(16, 2000, 0);
      worker.getPickaxe().enchant(Enchantments.BLOCK_EFFICIENCY, 5);
      worker.getShovel().enchant(Enchantments.BLOCK_EFFICIENCY, 5);
      worker.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
      worker.setNoAi(false);
    }
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    workers.stream()
                        .allMatch(w -> level.isPositionEntityTicking(w.blockPosition())),
                    "All three mining bodies tick"))
        .thenExecute(() -> workers.forEach(level::addFreshEntity))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    workers.stream()
                        .allMatch(w -> w.getMiningStatus() == MiningSlime.Status.COMPLETE),
                    "All three quarries complete: "
                        + workers.stream()
                            .map(
                                w ->
                                    w.getMiningStatus()
                                        + "/"
                                        + w.blockPosition()
                                        + "/"
                                        + w.getQuarryPlan().layer())
                            .toList()))
        .thenExecute(
            () -> {
              for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++)
                  for (int y = 97; y <= 101; y++) {
                    var pos = BlockPos.containing(area.point(x, y, z));
                    h.assertTrue(
                        high.getQuarryPlan().isRampFloor(pos)
                            ? level.getBlockState(pos).isCollisionShapeFullBlock(level, pos)
                            : level.getBlockState(pos).isAir(),
                        "Shared stairs survive while the remaining chunk is excavated: " + pos);
                  }
              int cargo =
                  workers.stream()
                      .mapToInt(w -> count(w, Items.COBBLESTONE) + count(w, Items.DIRT))
                      .sum();
              h.assertTrue(
                  cargo == expectedCargo
                      && level.getBlockState(gap).is(Blocks.COBBLESTONE)
                      && workers.stream().allMatch(MiningSlime::isAlive),
                  "Three workers preserve exact drops, one repair cost and every companion");
              workers.forEach(MiningSlime::discard);
              area.close();
            })
        .thenSucceed();
  }
}
