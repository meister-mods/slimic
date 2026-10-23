package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.RoyalSynthesisStationBlockEntity;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.event.SlimeSynthesisEvent;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.menu.*;
import io.github.meistermods.slimic.registry.*;
import io.github.meistermods.slimic.world.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RoyalSynthesisGameTests {
  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void clientMenuUsesReceivedStatusEvenWhenALocalSourceIsPresent(GameTestHelper h) {
    var server =
        new NumericContainerData(4, false, i -> i == 0 ? 4 : 1, Map.of(1, () -> 25L, 2, () -> 18L));
    var client = new NumericContainerData(4, true, i -> 0, Map.of(1, () -> 0L, 2, () -> 0L));
    for (int i = 0; i < server.getCount(); i++) client.set(i, (short) server.get(i));
    h.assertTrue(
        client.isReady()
            && client.value(0) == 4
            && client.value(3) == 1
            && client.longValue(1) == 25
            && client.longValue(2) == 18,
        "The native client status and pending flag come from server packets");
    h.succeed();
  }

  private record Fixture(
      GameTestHelper helper, ServerPlayer owner, RoyalSynthesisStationBlockEntity station) {
    RoyalSynthesis.Check check() {
      return RoyalSynthesis.check(helper.getLevel(), owner, station.contents());
    }

    RoyalSynthesis.Check execute() {
      return RoyalSynthesis.execute(owner, station);
    }

    ItemStack result() {
      return owner.getInventory().items.stream()
          .filter(item -> item.is(ModItems.NEXT_GENERATION_MINING_SLIME.get()))
          .findFirst()
          .orElse(ItemStack.EMPTY);
    }
  }

  private static ServerPlayer owner(GameTestHelper h) {
    return new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "RoyalResearch"));
  }

  private static ItemStack parent(
      GameTestHelper h,
      ServerPlayer owner,
      boolean ancient,
      long points,
      int healthLevels,
      int speedLevels) {
    var pet =
        (FriendlySlime)
            (ancient ? ModEntities.ANCIENT_MINING_SLIME.get() : ModEntities.MINING_SLIME.get())
                .create(h.getLevel());
    pet.tame(owner);
    pet.inheritGrowth(points);
    for (int i = 0; i < healthLevels; i++)
      h.assertTrue(pet.upgradeStat(SlimeStat.HEALTH), "Legal health training");
    for (int i = 0; i < speedLevels; i++)
      h.assertTrue(pet.upgradeStat(SlimeStat.MOVEMENT_SPEED), "Legal speed training");
    return FriendlySlimeItem.fromSlime(pet);
  }

  private static Fixture fixture(GameTestHelper h) {
    var player = owner(h);
    var pos = h.absolutePos(new BlockPos(2, 2, 2));
    player.moveTo(Vec3.atCenterOf(pos));
    h.getLevel()
        .setBlockAndUpdate(pos, ModBlocks.ROYAL_SYNTHESIS_STATION.get().defaultBlockState());
    var station = (RoyalSynthesisStationBlockEntity) h.getLevel().getBlockEntity(pos);
    station.setItem(0, parent(h, player, false, 25, 20, 1));
    station.setItem(1, parent(h, player, true, 18, 16, 0));
    station.setItem(2, new ItemStack(Items.SLIME_BALL, 4));
    station.setItem(3, new ItemStack(ModItems.OLD_COMMAND_FRAGMENT.get(), 2));
    return new Fixture(h, player, station);
  }

  private static CompoundTag data(ItemStack item) {
    return item.getTag().getCompound("SlimeData");
  }

  private static void unchanged(Fixture f, List<ItemStack> before) {
    var after = f.station.contents();
    for (int i = 0; i < 4; i++)
      f.helper.assertTrue(
          ItemStack.matches(before.get(i), after.get(i)), "Input " + i + " preserved");
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void fullRefundCreatesOneNewWaitingMinerWithZeroInvestment(GameTestHelper h) {
    var f = fixture(h);
    var firstName =
        net.minecraft.network.chat.Component.Serializer.toJson(f.station.getItem(0).getHoverName());
    f.station.getItem(1).setHoverName(net.minecraft.network.chat.Component.literal("Custom XXX"));
    var first = data(f.station.getItem(0)).getUUID("UUID");
    var second = data(f.station.getItem(1)).getUUID("UUID");
    h.assertTrue(
        f.check().problem() == RoyalSynthesis.Problem.READY && f.check().total() == 43,
        "25 plus 18 preview");
    h.assertTrue(f.execute().problem() == RoyalSynthesis.Problem.COMPLETE, "Commit succeeds");
    var item = f.result();
    var child = ModEntities.NEXT_GENERATION_MINING_SLIME.get().create(h.getLevel());
    child.load(data(item));
    h.assertTrue(
        child.getStatPoints() == 43
            && child.getGrowthEarned() == 43
            && child.getNextPointCost() == 2784,
        "Full refund and subsequent cost");
    for (var stat : SlimeStat.values())
      h.assertTrue(child.getStatLevel(stat) == 0, "No investment inherited");
    h.assertTrue(
        child.isOrderedToSit()
            && !child.isWorking()
            && child.getMaxHealth() == 16
            && child.getHealth() == 16,
        "Ordinary defaults and Wait");
    h.assertTrue(
        !child.getUUID().equals(first)
            && !child.getUUID().equals(second)
            && f.owner.getUUID().equals(child.getOwnerUUID()),
        "Fresh owned identity");
    var ancestry = child.getPersistentData().getCompound("NextGenAncestry");
    h.assertTrue(
        ancestry.getUUID("NormalParent").equals(first)
            && ancestry.getUUID("AncientParent").equals(second),
        "Ancestry retains both identities");
    h.assertTrue(
        ancestry.getString("NormalNameComponent").equals(firstName)
            && net.minecraft.network.chat.Component.Serializer.fromJson(
                    ancestry.getString("AncientNameComponent"))
                .getString()
                .equals("Custom XXX"),
        "Translated defaults and custom names retain component structure");
    var ledger = RoyalSynthesisData.get(h.getLevel());
    h.assertTrue(ledger.consumed(first) && ledger.consumed(second), "Both parents retired");
    h.assertTrue(f.station.isEmpty(), "Exact reagents consumed");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void previewAndRepeatedConfirmationNeverConsumeAnAdditionalPair(GameTestHelper h) {
    var f = fixture(h);
    var before = f.station.contents();
    for (int i = 0; i < 10; i++) f.check();
    unchanged(f, before);
    f.execute();
    var result = f.result().copy();
    f.execute();
    f.execute();
    h.assertTrue(
        ItemStack.matches(result, f.result())
            && f.owner.getInventory().items.stream()
                    .filter(s -> s.is(ModItems.NEXT_GENERATION_MINING_SLIME.get()))
                    .count()
                == 1,
        "Single result for repeated confirm");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void allHandAndArmorSlotsMustBeEmpty(GameTestHelper h) {
    var f = fixture(h);
    for (int parent = 0; parent < 2; parent++)
      for (var slot : EquipmentSlot.values()) {
        var before = f.station.contents();
        var item = before.get(parent).copy();
        var pet =
            (FriendlySlime)
                (parent == 0
                        ? ModEntities.MINING_SLIME.get()
                        : ModEntities.ANCIENT_MINING_SLIME.get())
                    .create(h.getLevel());
        pet.load(data(item));
        pet.setItemSlot(
            slot,
            new ItemStack(
                slot.getType() == EquipmentSlot.Type.HAND
                    ? Items.IRON_PICKAXE
                    : Items.DIAMOND_CHESTPLATE));
        f.station.setItem(parent, FriendlySlimeItem.fromSlime(pet));
        var equipped = f.station.contents();
        h.assertTrue(
            f.check().problem() == RoyalSynthesis.Problem.EQUIPMENT
                && !f.check().detail().isEmpty(),
            "Detailed equipment refusal for " + slot);
        f.execute();
        unchanged(f, equipped);
        f.station.setItem(parent, before.get(parent));
      }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void cargoIsPreservedWithASpecificSlotReason(GameTestHelper h) {
    var f = fixture(h);
    var pet = ModEntities.ANCIENT_MINING_SLIME.get().create(h.getLevel());
    pet.load(data(f.station.getItem(1)));
    var cargo = new ItemStack(Items.DIAMOND, 7);
    cargo.getOrCreateTag().putString("Lot", "seven");
    pet.getBag().setStackInSlot(8, cargo);
    f.station.setItem(1, FriendlySlimeItem.fromSlime(pet));
    var before = f.station.contents();
    h.assertTrue(
        f.check().problem() == RoyalSynthesis.Problem.CARGO && f.check().detail().equals("2:9"),
        "Parent two slot nine identified");
    f.execute();
    unchanged(f, before);
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void sharedManagementDoesNotReplacePrimaryOwnership(GameTestHelper h) {
    var f = fixture(h);
    var foreign = owner(h);
    var pet = ModEntities.MINING_SLIME.get().create(h.getLevel());
    pet.tame(foreign);
    CompanionRegistry.get(h.getLevel())
        .setRelation(foreign.getUUID(), f.owner.getUUID(), true, true);
    f.station.setItem(0, FriendlySlimeItem.fromSlime(pet));
    var before = f.station.contents();
    h.assertTrue(f.check().problem() == RoyalSynthesis.Problem.OWNER, "Primary owner required");
    f.execute();
    unchanged(f, before);
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void loadedParentCannotBeConsumedByACopiedItem(GameTestHelper h) {
    var f = fixture(h);
    var pet = ModEntities.MINING_SLIME.get().create(h.getLevel());
    pet.load(data(f.station.getItem(0)));
    pet.moveTo(f.owner.position());
    h.getLevel().addFreshEntity(pet);
    try {
      var before = f.station.contents();
      h.assertTrue(
          f.check().problem() == RoyalSynthesis.Problem.ACTIVE_PARENT, "Live UUID rejected");
      f.execute();
      unchanged(f, before);
    } finally {
      pet.discard();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void identicalParentsAndNextGenerationChainsAreRejected(GameTestHelper h) {
    var f = fixture(h);
    data(f.station.getItem(1)).putUUID("UUID", data(f.station.getItem(0)).getUUID("UUID"));
    h.assertTrue(f.check().problem() == RoyalSynthesis.Problem.CONSUMED, "Duplicate UUID rejected");
    f.station.setItem(0, new ItemStack(ModItems.NEXT_GENERATION_MINING_SLIME.get()));
    h.assertTrue(f.check().problem() == RoyalSynthesis.Problem.FAMILY, "No chain synthesis");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void creativeValuesAreNeverConvertedIntoGrowth(GameTestHelper h) {
    var f = fixture(h);
    data(f.station.getItem(0)).getCompound("TunedStats").putDouble("health", 123456789);
    var before = f.station.contents();
    h.assertTrue(
        f.check().problem() == RoyalSynthesis.Problem.CREATIVE, "Creative provenance rejected");
    f.execute();
    unchanged(f, before);
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void provenLegacyGrowthMigratesAndUnknownHistoryKeepsOriginals(GameTestHelper h) {
    var f = fixture(h);
    for (int i = 0; i < 2; i++) {
      var d = data(f.station.getItem(i));
      d.remove("GrowthSchema");
      d.remove("GrowthEarned");
      d.remove("GrowthTrusted");
    }
    h.assertTrue(
        f.check().problem() == RoyalSynthesis.Problem.READY && f.check().total() == 43,
        "Old invested plus unused equals legacy earned");
    data(f.station.getItem(0)).putInt("EarnedPoints", 24);
    var before = f.station.contents();
    h.assertTrue(
        f.check().problem() == RoyalSynthesis.Problem.HISTORY, "Unverifiable source rejected");
    f.execute();
    unchanged(f, before);
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void largePointBalancesRemainExactThroughWireAndSynthesis(GameTestHelper h) {
    var f = fixture(h);
    long large = (1L << 54) + 3;
    f.station.setItem(0, parent(h, f.owner, false, large, 0, 0));
    f.station.setItem(1, parent(h, f.owner, true, 18, 0, 0));
    h.assertTrue(f.check().total() == large + 18, "Long total exact");
    var server = new NumericContainerData(1, false, i -> 0, 0, () -> large);
    var client = new NumericContainerData(1, true, null, 0, () -> 0);
    for (int i = 0; i < server.getCount(); i++) client.set(i, (short) server.get(i));
    h.assertTrue(
        client.longValue(0) == large,
        "Signed-short wire preserves low bits above double precision");
    h.assertTrue(
        f.execute().problem() == RoyalSynthesis.Problem.COMPLETE
            && data(f.result()).getLong("StatPoints") == large + 18,
        "No hidden point cap");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void overflowAndMissingReagentsPreserveEveryInput(GameTestHelper h) {
    var f = fixture(h);
    f.station.setItem(0, parent(h, f.owner, false, Long.MAX_VALUE, 0, 0));
    var before = f.station.contents();
    h.assertTrue(f.check().problem() == RoyalSynthesis.Problem.HISTORY, "Overflow refused");
    f.execute();
    unchanged(f, before);
    f.station.setItem(0, parent(h, f.owner, false, 25, 0, 0));
    f.station.setItem(2, new ItemStack(Items.SLIME_BALL, 3));
    before = f.station.contents();
    h.assertTrue(f.check().problem() == RoyalSynthesis.Problem.MATERIALS, "Insufficient reagents");
    f.execute();
    unchanged(f, before);
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void externalCancellationLeavesParentsAndMaterialsUntouched(GameTestHelper h) {
    var f = fixture(h);
    var before = f.station.contents();
    Consumer<SlimeSynthesisEvent> cancel =
        e -> {
          if (e.getEntity() == f.owner) e.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(cancel);
    try {
      h.assertTrue(f.execute().problem() == RoyalSynthesis.Problem.CANCELLED, "External veto");
      unchanged(f, before);
      h.assertTrue(
          !RoyalSynthesisData.get(h.getLevel()).consumed(data(before.get(0)).getUUID("UUID")),
          "No committed consumption");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(cancel);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void reentrantConfirmationIsRejectedWhileTheOriginalCommitSucceeds(
      GameTestHelper h) {
    var f = fixture(h);
    int[] callbacks = {0};
    Consumer<SlimeSynthesisEvent> callback =
        e -> {
          if (e.getEntity() == f.owner) {
            callbacks[0]++;
            h.assertTrue(
                f.execute().problem() == RoyalSynthesis.Problem.CANCELLED, "Reentry blocked");
          }
        };
    MinecraftForge.EVENT_BUS.addListener(callback);
    try {
      h.assertTrue(
          f.execute().problem() == RoyalSynthesis.Problem.COMPLETE && callbacks[0] == 1,
          "Exactly one commit");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(callback);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void staleBlockImageReconcilesAgainstThePersistedTransaction(GameTestHelper h) {
    var f = fixture(h);
    var stale = f.station.saveWithFullMetadata();
    var first = data(f.station.getItem(0)).getUUID("UUID");
    f.execute();
    var ledger =
        RoyalSynthesisData.load(RoyalSynthesisData.get(h.getLevel()).save(new CompoundTag()));
    h.assertTrue(ledger.consumed(first), "Consumption survives SavedData reload");
    f.station.load(stale);
    f.station.reconcile();
    h.assertTrue(f.station.isEmpty(), "Stale chunk inputs become the committed after-image");
    h.assertTrue(
        !ledger.mayRestore(
            data(ItemStack.of(stale.getList("Items", Tag.TAG_COMPOUND).getCompound(0)))),
        "Copied parent permanently rejected");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void aFullInventoryDoesNotConsumeTheParents(GameTestHelper h) {
    var f = fixture(h);
    for (int i = 0; i < 36; i++) f.owner.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
    var before = f.station.contents();
    h.assertTrue(
        f.execute().problem() == RoyalSynthesis.Problem.OUTPUT_FULL, "Require result space");
    unchanged(f, before);
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void nextGenerationPolicyAndOreMaskSurviveStorage(GameTestHelper h) {
    var next = ModEntities.NEXT_GENERATION_MINING_SLIME.get().create(h.getLevel());
    next.tame(owner(h));
    h.assertTrue(
        next.policy() == NextGenerationMiningSlime.Policy.NORMAL && next.isOrderedToSit(),
        "Fresh NORMAL and Wait");
    next.setPolicy(NextGenerationMiningSlime.Policy.PRECISION_PROSPECTING);
    next.toggleOre(0);
    var data = FriendlySlimeItem.fromSlime(next);
    var restored = ModEntities.NEXT_GENERATION_MINING_SLIME.get().create(h.getLevel());
    restored.load(data(data));
    h.assertTrue(
        restored.policy() == NextGenerationMiningSlime.Policy.PRECISION_PROSPECTING
            && restored.oreMask() == next.oreMask(),
        "Policy and mask persist");
    h.assertTrue(
        !restored.allowsOre(Blocks.STONE.defaultBlockState())
            && !restored.allowsOre(Blocks.COAL_ORE.defaultBlockState())
            && restored.allowsOre(Blocks.IRON_ORE.defaultBlockState()),
        "Forge ore tags govern selection");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void failedJournalCheckpointPreservesEveryInput(GameTestHelper h) throws Exception {
    var f = fixture(h);
    var before = f.station.contents();
    var probe =
        h.getLevel()
            .getServer()
            .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("data")
            .resolve(RoyalSynthesisData.NAME + ".pending");
    h.assertTrue(
        !java.nio.file.Files.exists(probe), "Fault path belongs only to this disposable fixture");
    java.nio.file.Files.createDirectories(probe);
    try {
      h.assertTrue(
          f.execute().problem() == RoyalSynthesis.Problem.STORAGE, "Disk checkpoint refusal");
      unchanged(f, before);
      h.assertTrue(
          !RoyalSynthesisData.get(h.getLevel()).consumed(data(before.get(0)).getUUID("UUID")),
          "No spent UUID on failed commit");
    } finally {
      java.nio.file.Files.delete(probe);
    } // This empty directory was created by this test.
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void failedPlayerCheckpointKeepsTheResultReservedWithoutChangingExistingInventory(
      GameTestHelper h) throws Exception {
    var f = fixture(h);
    f.owner.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 7));
    f.owner.getInventory().setItem(40, new ItemStack(Items.IRON_AXE));
    var original =
        java.util.stream.IntStream.range(0, f.owner.getInventory().getContainerSize())
            .mapToObj(i -> f.owner.getInventory().getItem(i).copy())
            .toList();
    var folder =
        h.getLevel()
            .getServer()
            .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("playerdata");
    java.nio.file.Files.createDirectories(folder);
    var probe = folder.resolve(f.owner.getStringUUID() + ".pending");
    h.assertTrue(!java.nio.file.Files.exists(probe), "Unique fixture fault path");
    java.nio.file.Files.createDirectory(probe);
    try {
      h.assertTrue(
          f.execute().problem() == RoyalSynthesis.Problem.COMPLETE && f.result().isEmpty(),
          "Uncheckpointed result stays in the recoverable mailbox");
      h.assertTrue(
          RoyalSynthesisData.get(h.getLevel()).pendingFor(f.owner.getUUID()) != null,
          "Delivery acknowledgement remains pending");
      for (int i = 0; i < original.size(); i++)
        h.assertTrue(
            ItemStack.matches(original.get(i), f.owner.getInventory().getItem(i)),
            "Every existing inventory slot remains intact: " + i);
    } finally {
      java.nio.file.Files.delete(probe);
    } // Remove only this test's empty marker directory.
    var ledger = RoyalSynthesisData.get(h.getLevel());
    var pending = ledger.pendingFor(f.owner.getUUID());
    h.assertTrue(ledger.deliver(f.owner, pending), "Reserved result checkpointed on retry");
    h.assertTrue(
        f.owner.getInventory().items.stream()
                    .filter(s -> s.is(ModItems.NEXT_GENERATION_MINING_SLIME.get()))
                    .count()
                == 1
            && ledger.pendingFor(f.owner.getUUID()) == null,
        "One result and no repeated delivery");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis", timeoutTicks = 400)
  public static void nativeReleaseRotatesEpochAndRecoveryRejectsTheOldCopy(GameTestHelper h) {
    var f = fixture(h);
    f.execute();
    var original = f.result();
    var copied = original.copy();
    UUID id = data(original).getUUID("UUID");
    var pos = f.station.getBlockPos().offset(0, 0, 3);
    h.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
    h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    h.getLevel().setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
    f.owner.setItemInHand(InteractionHand.MAIN_HAND, original);
    var hit =
        new net.minecraft.world.phys.BlockHitResult(
            Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false);
    var result =
        original.useOn(
            new net.minecraft.world.item.context.UseOnContext(
                f.owner, InteractionHand.MAIN_HAND, hit));
    h.assertTrue(
        result.consumesAction() && h.getLevel().getEntity(id) instanceof NextGenerationMiningSlime,
        "Actual item release admits the new child");
    var ledger = RoyalSynthesisData.get(h.getLevel());
    h.assertTrue(!ledger.mayRestore(data(copied)), "Old copied epoch invalid after release");
    var pet = (NextGenerationMiningSlime) h.getLevel().getEntity(id);
    h.assertTrue(pet.isOrderedToSit() && !pet.isWorking(), "Redeployed child waits");
    h.assertTrue(pet.capture(f.owner).consumesAction(), "Capture preserves current epoch");
    var captured = f.result();
    h.assertTrue(
        !captured.isEmpty()
            && ledger.mayRestore(data(captured))
            && !ledger.mayRestore(data(copied)),
        "Only the latest recovered item remains valid");
    var stale = ModEntities.NEXT_GENERATION_MINING_SLIME.get().create(h.getLevel());
    stale.load(data(copied));
    stale.moveTo(Vec3.atCenterOf(pos));
    h.assertTrue(
        !h.getLevel().addFreshEntity(stale),
        "Stale saved entity cannot rejoin with an obsolete epoch");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void interruptedReleaseSnapshotSurvivesJournalReload(GameTestHelper h) {
    var f = fixture(h);
    f.execute();
    var child = f.result().copy();
    UUID id = data(child).getUUID("UUID");
    var snapshot = RoyalSynthesisData.get(h.getLevel()).save(new CompoundTag());
    var recovery = new CompoundTag();
    recovery.putUUID("Id", id);
    recovery.put("Item", child.save(new CompoundTag()));
    var recoveries = new ListTag();
    recoveries.add(recovery);
    snapshot.put("ReleaseRecovery", recoveries);
    var restored = RoyalSynthesisData.load(snapshot);
    var pending = restored.pendingFor(f.owner.getUUID());
    h.assertTrue(
        pending != null
            && pending.result.equals(id)
            && data(pending.output).getLong("StatPoints") == 43,
        "Interrupted handoff has a recoverable exact companion after restart");
    var roundtrip = restored.save(new CompoundTag());
    h.assertTrue(
        roundtrip.getList("ReleaseRecovery", Tag.TAG_COMPOUND).size() == 1,
        "Recovery image survives repeated serialization");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void multipleTransactionsAtOneStationReconcileTheLatestAfterImage(
      GameTestHelper h) {
    var f = fixture(h);
    var stale = f.station.saveWithFullMetadata();
    f.execute();
    f.station.setItem(0, parent(h, f.owner, false, 9, 0, 0));
    f.station.setItem(1, parent(h, f.owner, true, 12, 0, 0));
    f.station.setItem(2, new ItemStack(Items.SLIME_BALL, 9));
    f.station.setItem(3, new ItemStack(ModItems.OLD_COMMAND_FRAGMENT.get(), 7));
    f.execute();
    f.station.load(stale);
    f.station.reconcile();
    h.assertTrue(
        f.station.getItem(0).isEmpty()
            && f.station.getItem(1).isEmpty()
            && f.station.getItem(2).getCount() == 5
            && f.station.getItem(3).getCount() == 5,
        "Old block snapshot rolls forward to the latest committed contents");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void futureGrowthSchemasAreRefusedWithoutRewritingTheirPayload(GameTestHelper h) {
    var f = fixture(h);
    data(f.station.getItem(0)).putInt("GrowthSchema", 99);
    var before = f.station.contents();
    h.assertTrue(
        f.execute().problem() == RoyalSynthesis.Problem.HISTORY,
        "Unknown future growth is not guessed");
    unchanged(f, before);
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void stationOriginSurvivesDropsAndBecomesChildAncestry(GameTestHelper h) {
    var f = fixture(h);
    UUID origin = UUID.randomUUID();
    var tag = f.station.saveWithFullMetadata();
    tag.putUUID("OriginSanctum", origin);
    f.station.load(tag);
    var drops =
        net.minecraft.world.level.block.Block.getDrops(
            f.station.getBlockState(), h.getLevel(), f.station.getBlockPos(), f.station);
    h.assertTrue(
        drops.stream()
            .anyMatch(
                s ->
                    s.is(ModItems.ROYAL_SYNTHESIS_STATION.get())
                        && s.hasTag()
                        && origin.equals(
                            s.getTag().getCompound("BlockEntityTag").getUUID("OriginSanctum"))),
        "Moving a crafted station preserves its core origin");
    f.execute();
    h.assertTrue(
        origin.equals(
            data(f.result())
                .getCompound("ForgeData")
                .getCompound("NextGenAncestry")
                .getUUID("OriginSanctum")),
        "Result ancestry retains original sanctum");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void automationCannotInsertOrExtractDuringTheCommitCallback(GameTestHelper h) {
    var f = fixture(h);
    Consumer<SlimeSynthesisEvent> callback =
        event -> {
          if (event.getEntity() != f.owner) return;
          var handler =
              f.station
                  .getCapability(
                      net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER)
                  .orElseThrow(IllegalStateException::new);
          var incoming = new ItemStack(Items.SLIME_BALL, 7);
          h.assertTrue(
              handler.insertItem(2, incoming, false).getCount() == 7,
              "Busy insertion returns the complete incoming stack");
          h.assertTrue(
              handler.extractItem(0, 1, false).isEmpty(), "Busy parent extraction is denied");
          h.assertTrue(
              f.station.getItem(2).getCount() == 4 && !f.station.getItem(0).isEmpty(),
              "No hidden mutation");
        };
    MinecraftForge.EVENT_BUS.addListener(callback);
    try {
      h.assertTrue(
          f.execute().problem() == RoyalSynthesis.Problem.COMPLETE, "Original inputs commit once");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(callback);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void formalAncientRestorationTrainingIsFullyRefunded(GameTestHelper h) {
    var f = fixture(h);
    var parent = ModEntities.ANCIENT_MINING_SLIME.get().create(h.getLevel());
    parent.tame(f.owner);
    UUID core = UUID.randomUUID();
    AncientTraining.applyOnce(parent, core, 2);
    long earned = parent.getGrowthEarned();
    h.assertTrue(
        earned >= 12 && earned <= 28 && parent.hasTrustedGrowth(),
        "Normal restoration training has a verifiable ledger");
    f.station.setItem(1, FriendlySlimeItem.fromSlime(parent));
    h.assertTrue(f.check().total() == 25 + earned, "All veteran investment counts");
    f.execute();
    h.assertTrue(
        data(f.result()).getLong("StatPoints") == 25 + earned
            && data(f.result())
                .getCompound("ForgeData")
                .getCompound("NextGenAncestry")
                .getUUID("AncientCore")
                .equals(core),
        "Refund and core ancestry preserve the actual restoration grant");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void aNewlySpecializedStoredMinerMustFirstRemoveItsInitialPickaxe(
      GameTestHelper h) {
    var f = fixture(h);
    var tool = new ItemStack(Items.DIAMOND_PICKAXE);
    tool.setDamageValue(71);
    f.station
        .getItem(0)
        .getOrCreateTag()
        .put(
            io.github.meistermods.slimic.item.MiningSlimeItem.INITIAL_PICKAXE,
            tool.save(new CompoundTag()));
    var before = f.station.contents();
    h.assertTrue(
        f.execute().problem() == RoyalSynthesis.Problem.EQUIPMENT,
        "A not-yet-released specialization still carries its physical crafting tool");
    unchanged(f, before);
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void anOldBlockImageNeverDeletesUnrelatedUnconsumedParents(GameTestHelper h) {
    var f = fixture(h);
    f.execute();
    var unrelated = parent(h, f.owner, false, 19, 0, 0);
    f.station.setItem(0, unrelated);
    var stale = f.station.saveWithFullMetadata();
    f.station.setItem(0, parent(h, f.owner, false, 8, 0, 0));
    f.station.setItem(1, parent(h, f.owner, true, 11, 0, 0));
    f.station.setItem(2, new ItemStack(Items.SLIME_BALL, 4));
    f.station.setItem(3, new ItemStack(ModItems.OLD_COMMAND_FRAGMENT.get(), 2));
    f.execute();
    f.station.load(stale);
    f.station.reconcile();
    h.assertTrue(
        ItemStack.matches(unrelated, f.station.getItem(0)),
        "An unrelated saved input remains recoverable instead of becoming another transaction's"
            + " after-image");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void unverifiedAttachedInventoryCannotBeSilentlyConsumed(GameTestHelper h) {
    var f = fixture(h);
    var attachments = new CompoundTag();
    var contents = new ListTag();
    contents.add(new ItemStack(Items.DIAMOND, 9).save(new CompoundTag()));
    attachments.put("Items", contents);
    var caps = new CompoundTag();
    caps.put("example:additional_inventory", attachments);
    data(f.station.getItem(0)).put("ForgeCaps", caps);
    var before = f.station.contents();
    h.assertTrue(
        f.execute().problem() == RoyalSynthesis.Problem.ATTACHMENTS,
        "Unverified attached cargo must stay recoverable");
    unchanged(f, before);
    h.succeed();
  }

  @GameTest(template = "empty", batch = "royal_synthesis")
  public static void savedReceiptPreventsDuplicateDeliveryAfterAnInterruptedAcknowledgement(
      GameTestHelper h) {
    var f = fixture(h);
    f.execute();
    var child = f.result().copy();
    var snapshot = RoyalSynthesisData.get(h.getLevel()).save(new CompoundTag());
    var id = data(child).getCompound("ForgeData").getUUID("SynthesisTransaction");
    for (var raw : snapshot.getList("Transactions", Tag.TAG_COMPOUND)) {
      var tx = (CompoundTag) raw;
      if (tx.getUUID("Id").equals(id)) tx.putBoolean("Delivered", false);
    }
    var restored = RoyalSynthesisData.load(snapshot);
    var tx = restored.transaction(id);
    h.assertTrue(tx != null && !tx.delivered, "Interrupted acknowledgement survives reload");
    h.assertTrue(restored.deliver(f.owner, tx), "The saved receipt is acknowledged");
    h.assertTrue(
        f.owner.getInventory().items.stream()
                    .filter(i -> i.is(ModItems.NEXT_GENERATION_MINING_SLIME.get()))
                    .count()
                == 1
            && ItemStack.matches(child, f.result())
            && restored.pendingFor(f.owner.getUUID()) == null,
        "Existing result remains exact and singular");
    h.succeed();
  }
}
