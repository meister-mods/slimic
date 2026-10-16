package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.config.SlimicConfig;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.recipe.MiningSlimeRecipe;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkSystemGameTests {
  private WorkSystemGameTests() {}

  @GameTest(template = "empty", batch = "work_system", timeoutTicks = 300)
  public static void newMinersWaitUntilTheOwnerExplicitlyStartsMining(GameTestHelper h) {
    LoadedWorkArea arena = new LoadedWorkArea(h);
    Player owner = h.makeMockSurvivalPlayer();
    var original = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    original.tame(owner);
    original.setHealth(7);
    ItemStack[] items = {
      MiningSlimeRecipe.specialize(
          new ItemStack(ModItems.FRIENDLY_SLIME.get()), new ItemStack(Items.IRON_PICKAXE)),
      new ItemStack(ModItems.MINING_SLIME.get()),
      MiningSlimeRecipe.specialize(
          FriendlySlimeItem.fromSlime(original), new ItemStack(Items.DIAMOND_PICKAXE))
    };
    var miners = new ArrayList<MiningSlime>();
    for (int index = 0; index < items.length; index++) {
      owner.getAbilities().instabuild = index == 1;
      BlockPos floor = BlockPos.containing(arena.point(4 + index * 4, 100, 8));
      h.assertTrue(
          use(owner, items[index], floor).consumesAction(), "Summon each initial miner variant");
    }
    owner.getAbilities().instabuild = false;
    BlockPos stone = BlockPos.containing(arena.point(3, 101, 8));
    h.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
    h.startSequence()
        .thenIdle(80)
        .thenExecute(
            () -> {
              miners.addAll(
                  h.getLevel()
                      .getEntitiesOfClass(
                          MiningSlime.class,
                          new AABB(arena.point(0, 101, 0), arena.point(16, 106, 16))));
              miners.sort(java.util.Comparator.comparingDouble(MiningSlime::getX));
              h.assertTrue(miners.size() == 3, "All three native item uses must create one miner");
              h.assertTrue(
                  miners.stream()
                      .allMatch(
                          miner ->
                              miner.isOrderedToSit()
                                  && !miner.isWorking()
                                  && miner.getWorkStatus().order() == WorkStatus.Order.WAIT),
                  "Crafted, Creative, and newly specialized stored companions must wait");
              h.assertTrue(
                  miners.get(2).getHealth() == 7, "Specialization must retain saved health");
              h.assertTrue(
                  h.getLevel().getBlockState(stone).is(Blocks.STONE),
                  "Initial summons must leave nearby terrain intact");
              MiningSlime miner = miners.get(0);
              owner.moveTo(miner.position());
              h.assertTrue(
                  new SlimeMenu(1, owner.getInventory(), miner)
                      .clickMenuButton(owner, SlimeMenu.WORK),
                  "The owner can explicitly start mining");
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getBlockState(stone).isAir(),
                    "Mine must start the quarry: "
                        + miners.get(0).getMiningStatus()
                        + ", at="
                        + miners.get(0).position()
                        + ", tool="
                        + miners.get(0).getPickaxe()
                        + ", work="
                        + miners.get(0).isWorking()
                        + ", layer="
                        + miners.get(0).getQuarryPlan().layer()
                        + ", top="
                        + miners.get(0).getQuarryPlan().topFloor()))
        .thenExecute(
            () -> {
              miners.forEach(FriendlySlime::discard);
              arena.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "work_system")
  public static void commonWorkStatusSeparatesCommandsReasonsAndRequests(GameTestHelper h) {
    var miner = ModEntities.MINING_SLIME.get().create(h.getLevel());
    CompoundTag saved = miner.saveWithoutId(new CompoundTag());
    saved.putInt("MiningStatus", MiningSlime.Status.FULL.ordinal());
    miner.load(saved);
    WorkStatus state = miner.getWorkStatus();
    h.assertTrue(
        state.order() == WorkStatus.Order.MINE
            && state.state() == WorkStatus.State.BLOCKED
            && state.reason() == WorkStatus.Reason.INVENTORY_FULL
            && state.requests(WorkStatus.Request.CARGO_PICKUP)
            && miner.needsUnloading(),
        "A full worker exposes an actionable cargo request independently of its order");
    miner.setWaiting(true);
    h.assertTrue(
        miner.getWorkStatus().order() == WorkStatus.Order.WAIT
            && !miner.isWorkBlocked()
            && !miner.needsUnloading(),
        "Manual waiting suppresses work blockage and support requests");
    saved.putInt("MiningStatus", MiningSlime.Status.NO_TOOL.ordinal());
    miner.load(saved);
    h.assertTrue(
        miner.needsReplacementTool()
            && miner.getWorkStatus().reason() == WorkStatus.Reason.TOOL_MISSING,
        "A missing tool produces a tool request");
    miner.setTool(0, new ItemStack(Items.IRON_PICKAXE));
    h.assertTrue(
        !miner.needsReplacementTool(),
        "Supply revalidation must see equipment changes immediately");
    saved.putInt("MiningStatus", MiningSlime.Status.NEEDS_TIER.ordinal());
    miner.load(saved);
    h.assertTrue(
        miner.isWorkBlocked()
            && !miner.needsReplacementTool()
            && miner.getWorkStatus().reason() == WorkStatus.Reason.TOOL_TIER,
        "An unsuitable tool tier is distinct from an empty equipment slot");
    saved.putInt("MiningStatus", MiningSlime.Status.COMPLETE.ordinal());
    miner.load(saved);
    h.assertTrue(
        !miner.isWorkBlocked() && miner.getWorkStatus().state() == WorkStatus.State.COMPLETE,
        "Completed work is not a blockage");
    var fighter = ModEntities.COMBAT_SLIME.get().create(h.getLevel());
    h.assertTrue(
        fighter.needsReplacementTool()
            && fighter.getWorkStatus().requests(WorkStatus.Request.TOOL_SUPPLY),
        "Combat uses the same tool-supply request");
    fighter.setWorking(false);
    h.assertTrue(
        fighter.getWorkStatus().order() == WorkStatus.Order.FOLLOW
            && !fighter.needsReplacementTool(),
        "Following has no work request");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "work_system")
  public static void recoveryScalesWithMaximumStaminaForEveryCompanion(GameTestHelper h) {
    for (EntityType<?> type :
        new EntityType<?>[] {
          ModEntities.FRIENDLY_SLIME.get(), ModEntities.MINING_SLIME.get(),
              ModEntities.TRANSPORT_SLIME.get(),
          ModEntities.HERALD_SLIME.get(), ModEntities.COMBAT_SLIME.get(),
              ModEntities.CLEANING_SLIME.get()
        }) {
      for (int maximum : new int[] {100, 200, 500}) {
        FriendlySlime slime = (FriendlySlime) type.create(h.getLevel());
        slime.setNoAi(true);
        slime.setNoGravity(true);
        slime.setWaiting(true);
        SlimeStat.STAMINA.tune(slime, maximum);
        slime.setTunedVitals(16, 0, 0);
        for (int tick = 0; tick < 20; tick++) {
          slime.tickCount++;
          slime.tick();
        }
        h.assertTrue(
            Math.abs(slime.getStamina() - maximum * .02F) < .001,
            "Recovery: " + type + ", maximum=" + maximum + ", actual=" + slime.getStamina());
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "work_system", timeoutTicks = 400)
  public static void upgradedStaminaDoesNotExtendExhaustionRecovery(GameTestHelper h) {
    LoadedWorkArea arena = new LoadedWorkArea(h);
    var chest = arena.chest(8, 101, 8);
    var carriers = new ArrayList<TransportSlime>();
    for (int index = 0; index < 2; index++) {
      var carrier = arena.carrier(new Vec3(5, 101, 6 + index * 4));
      SlimeStat.REACH.tune(carrier, 8);
      SlimeStat.STAMINA.tune(carrier, index == 0 ? 100 : 500);
      carrier.setTunedVitals(16, 0, 0);
      carrier.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 1));
      carriers.add(carrier);
    }
    h.startSequence()
        .thenIdle(200)
        .thenExecute(
            () -> {
              h.assertTrue(
                  chest.isEmpty()
                      && carriers.stream()
                          .allMatch(c -> c.getTransportStatus() == TransportSlime.Status.RESTING),
                  "Both stamina levels must still be resting below 25%");
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    chest.getItem(0).getCount() == 2,
                    "Both carriers must resume in the same recovery window"))
        .thenExecute(
            () -> {
              carriers.forEach(FriendlySlime::discard);
              arena.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "work_system", timeoutTicks = 400)
  public static void realOwnerFollowingEarnsGrowthAndWaitingDoesNot(GameTestHelper h) {
    LoadedWorkArea arena = new LoadedWorkArea(h);
    FakePlayer owner =
        new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "GrowthOwner"));
    owner.setGameMode(GameType.SURVIVAL);
    owner.moveTo(arena.point(11, 101, 8));
    h.getLevel().addNewPlayer(owner);
    var pet = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    pet.moveTo(arena.point(4, 101, 8));
    pet.tame(owner);
    CompoundTag saved = pet.saveWithoutId(new CompoundTag());
    saved.putInt("FollowingActivityTicks", SlimicConfig.FOLLOW_ACTIVITY_INTERVAL.get() - 1);
    pet.load(saved);
    h.assertTrue(
        FriendlySlimeItem.fromSlime(pet)
                .getTag()
                .getCompound("SlimeData")
                .getInt("FollowingActivityTicks")
            == SlimicConfig.FOLLOW_ACTIVITY_INTERVAL.get() - 1,
        "Partial following growth must survive recovery");
    h.getLevel().addFreshEntity(pet);
    AtomicInteger earned = new AtomicInteger();
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    pet.getActivityProgress() > 0, "Actual following movement must earn growth"))
        .thenExecute(
            () -> {
              earned.set(pet.getActivityProgress());
              pet.setWaiting(true);
            })
        .thenIdle(60)
        .thenExecute(
            () -> {
              h.assertTrue(
                  pet.getActivityProgress() == earned.get(), "Waiting must grant no growth");
              pet.discard();
              owner.discard();
              arena.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "work_system", timeoutTicks = 160)
  public static void heraldGrowthRequiresAnActiveSameOwnerWorker(GameTestHelper h) {
    LoadedWorkArea arena = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var miner = ModEntities.MINING_SLIME.get().create(h.getLevel());
    miner.moveTo(arena.point(5, 101, 8));
    miner.tame(owner);
    miner.setNoAi(true);
    CompoundTag blocked = miner.saveWithoutId(new CompoundTag());
    blocked.putInt("MiningStatus", MiningSlime.Status.NO_TOOL.ordinal());
    miner.load(blocked);
    miner.onReleased();
    h.getLevel().addFreshEntity(miner);
    var herald = ModEntities.HERALD_SLIME.get().create(h.getLevel());
    herald.moveTo(arena.point(8, 101, 8));
    herald.tame(owner);
    CompoundTag saved = herald.saveWithoutId(new CompoundTag());
    saved.putInt(
        "HeraldMonitoringActivityTicks", SlimicConfig.MONITOR_ACTIVITY_INTERVAL.get() - 20);
    herald.load(saved);
    var restored = ModEntities.HERALD_SLIME.get().create(h.getLevel());
    restored.load(FriendlySlimeItem.fromSlime(herald).getTag().getCompound("SlimeData"));
    h.assertTrue(
        restored.saveWithoutId(new CompoundTag()).getInt("HeraldMonitoringActivityTicks")
            == SlimicConfig.MONITOR_ACTIVITY_INTERVAL.get() - 20,
        "Monitoring growth fractions must survive recovery");
    h.getLevel().addFreshEntity(herald);
    AtomicInteger earned = new AtomicInteger();
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    herald.getActivityProgress() == 1,
                    "Monitoring an active worker must earn growth"))
        .thenExecute(
            () -> {
              earned.set(herald.getActivityProgress());
              miner.setWaiting(true);
            })
        .thenIdle(60)
        .thenExecute(
            () -> {
              h.assertTrue(
                  herald.getActivityProgress() == earned.get(),
                  "Waiting workers do not generate monitoring growth");
              herald.discard();
              miner.discard();
              arena.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "work_system", timeoutTicks = 60)
  public static void searchesShareOneLoadedChunkSnapshotAndExpireIt(GameTestHelper h) {
    LoadedWorkArea arena = new LoadedWorkArea(h);
    var chest = arena.chest(8, 101, 8);
    var chunk = new ChunkPos(chest.getBlockPos());
    var snapshot = WorkSearch.inChunk(h.getLevel(), chunk);
    h.assertTrue(snapshot.chests().contains(chest.getBlockPos()), "Loaded storage is indexed");
    for (int query = 0; query < 100; query++)
      h.assertTrue(
          WorkSearch.inChunk(h.getLevel(), chunk) == snapshot,
          "100 overlapping queries must reuse one snapshot");
    ChunkPos unloaded = new ChunkPos(chunk.x + 100, chunk.z + 100);
    h.assertTrue(
        h.getLevel().getChunkSource().getChunkNow(unloaded.x, unloaded.z) == null,
        "Distant fixture chunk starts unloaded");
    var empty = WorkSearch.inChunk(h.getLevel(), unloaded);
    h.assertTrue(
        empty.chests().isEmpty()
            && empty.workers().isEmpty()
            && h.getLevel().getChunkSource().getChunkNow(unloaded.x, unloaded.z) == null,
        "Candidate searches must not load absent chunks");
    h.getLevel().removeBlock(chest.getBlockPos(), false);
    h.runAfterDelay(
        21,
        () -> {
          var refreshed = WorkSearch.inChunk(h.getLevel(), chunk);
          h.assertTrue(
              refreshed != snapshot && !refreshed.chests().contains(chest.getBlockPos()),
              "Expired snapshots must discover removed storage");
          arena.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "work_system", timeoutTicks = 200)
  public static void thirtyTwoCarriersShareStorageWithoutLosingOrDuplicatingCargo(
      GameTestHelper h) {
    LoadedWorkArea arena = new LoadedWorkArea(h);
    var chest = arena.chest(8, 101, 8);
    var carriers = new ArrayList<TransportSlime>();
    for (int index = 0; index < 32; index++) {
      var carrier = arena.carrier(new Vec3(1.5 + index % 8 * 1.8, 101, 1.5 + index / 8 * 1.8));
      SlimeStat.REACH.tune(carrier, 16);
      SlimeStat.DETECTION_RANGE.tune(carrier, 16);
      SlimeStat.MOVEMENT_SPEED.tune(carrier, 0);
      carrier.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 1));
      carriers.add(carrier);
    }
    h.succeedWhen(
        () -> {
          h.assertTrue(
              chest.getItem(0).getCount() == 32 && carriers.stream().noneMatch(BagSlime::hasCargo),
              "All 32 diamonds must arrive exactly once in shared storage");
          h.assertTrue(
              carriers.stream()
                  .allMatch(c -> c.getActivityProgress() == SlimicConfig.TRANSPORT_ACTIVITY.get()),
              "Each successful delivery earns the configured growth reward once");
          carriers.forEach(FriendlySlime::discard);
          arena.close();
        });
  }

  @GameTest(template = "empty", batch = "work_system")
  public static void legacyStoredDataMigratesAndFutureVersionsRemainIntact(GameTestHelper h) {
    LoadedWorkArea arena = new LoadedWorkArea(h);
    Player owner = h.makeMockSurvivalPlayer();
    var pet = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    pet.tame(owner);
    pet.setHealth(7);
    pet.setWaiting(true);
    ItemStack stored = FriendlySlimeItem.fromSlime(pet);
    CompoundTag saved = stored.getTag().getCompound("SlimeData");
    h.assertTrue(
        saved.getInt(SlimeData.VERSION_KEY) == SlimeData.VERSION,
        "New saves carry a schema version");
    saved.putInt(SlimeData.VERSION_KEY, SlimeData.VERSION + 1);
    CompoundTag before = stored.save(new CompoundTag());
    BlockPos floor = BlockPos.containing(arena.point(8, 100, 8));
    h.assertTrue(
        !use(owner, stored, floor).consumesAction()
            && before.equals(stored.save(new CompoundTag())),
        "An unknown future schema must not consume or alter the stored companion");
    saved.putInt(SlimeData.VERSION_KEY, -1);
    before = stored.save(new CompoundTag());
    h.assertTrue(
        !use(owner, stored, floor).consumesAction()
            && before.equals(stored.save(new CompoundTag())),
        "Negative schema versions must remain intact");
    saved.putString(SlimeData.VERSION_KEY, "1");
    before = stored.save(new CompoundTag());
    h.assertTrue(
        !use(owner, stored, floor).consumesAction()
            && before.equals(stored.save(new CompoundTag())),
        "Malformed schema versions must remain intact");
    saved.remove(SlimeData.VERSION_KEY);
    h.assertTrue(
        use(owner, stored, floor).consumesAction(),
        "Unversioned stored companions must remain usable");
    h.succeedWhen(
        () -> {
          var restored = (FriendlySlime) h.getLevel().getEntity(pet.getUUID());
          h.assertTrue(
              restored != null
                  && restored.getHealth() == 7
                  && restored.isOrderedToSit()
                  && restored.saveWithoutId(new CompoundTag()).getInt(SlimeData.VERSION_KEY)
                      == SlimeData.VERSION,
              "Migration must retain identity, health, and orders and write the current schema: "
                  + restored
                  + (restored == null
                      ? ""
                      : ", health="
                          + restored.getHealth()
                          + ", waiting="
                          + restored.isOrderedToSit()));
          restored.discard();
          arena.close();
        });
  }

  private static net.minecraft.world.InteractionResult use(
      Player owner, ItemStack stack, BlockPos floor) {
    owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
    return stack
        .getItem()
        .useOn(
            new UseOnContext(
                owner,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                    Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false)));
  }
}
