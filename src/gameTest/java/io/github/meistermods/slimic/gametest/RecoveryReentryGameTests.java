package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.config.SlimicConfig;
import io.github.meistermods.slimic.entity.CompanionRegistry;
import io.github.meistermods.slimic.entity.EnderSlime;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.HealingSlime;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.ModBlocks;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecoveryReentryGameTests {
  @GameTest(template = "empty", batch = "recovery_reentry", timeoutTicks = 400)
  public static void spawnHookCannotRestoreOneIdentityInTwoDimensions(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var level = h.getLevel();
    var other = level.getServer().getLevel(Level.NETHER);
    var pos = BlockPos.containing(a.point(8, 201, 8));
    int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
    for (int dx = -1; dx <= 1; dx++)
      for (int dz = -1; dz <= 1; dz++) {
        other.setChunkForced(cx + dx, cz + dz, true);
        other.getChunk(cx + dx, cz + dz);
      }
    long readyBy = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
    level
        .getServer()
        .managedBlock(
            () -> {
              level.getChunkSource().pollTask();
              other.getChunkSource().pollTask();
              return other.isPositionEntityTicking(pos) || System.nanoTime() >= readyBy;
            });
    h.assertTrue(
        other.isPositionEntityTicking(pos),
        "The second world is ready before restoration callbacks");
    h.runAfterDelay(
        399,
        () -> {
          for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) other.setChunkForced(cx + dx, cz + dz, false);
        });
    for (int x = -2; x <= 2; x++)
      for (int z = -2; z <= 2; z++) {
        other.setBlockAndUpdate(pos.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState());
        for (int y = 0; y < 4; y++)
          other.setBlockAndUpdate(pos.offset(x, y, z), Blocks.AIR.defaultBlockState());
      }
    var owner = h.makeMockSurvivalPlayer();
    var saved = ModEntities.HEALING_SLIME.get().create(level);
    saved.tame(owner);
    saved.setHealth(7);
    saved.setNoAi(true);
    var original = FriendlySlimeItem.fromSlime(saved);
    var duplicate = original.copy();
    var item = (FriendlySlimeItem) original.getItem();
    boolean[] attempted = {false}, nestedSucceeded = {false};
    Consumer<EntityJoinLevelEvent> nested =
        event -> {
          if (event.getLevel() == level
              && event.getEntity().getUUID().equals(saved.getUUID())
              && !attempted[0]) {
            attempted[0] = true;
            var otherOwner =
                FakePlayerFactory.get(other, new GameProfile(owner.getUUID(), "RevivalOwner"));
            nestedSucceeded[0] = item.revive(other, otherOwner, duplicate, pos);
          }
        };
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    level.isPositionEntityTicking(BlockPos.containing(a.point(8, 101, 8)))
                        && other.isPositionEntityTicking(pos),
                    "Both dimensions tick"))
        .thenExecute(
            () -> {
              MinecraftForge.EVENT_BUS.addListener(nested);
              try {
                h.assertTrue(
                    item.revive(level, owner, original, BlockPos.containing(a.point(8, 101, 8))),
                    "Original restoration succeeds");
                h.assertTrue(
                    attempted[0]
                        && !nestedSucceeded[0]
                        && duplicate.getCount() == 1
                        && original.isEmpty(),
                    "An in-progress identity must be reserved across dimension join callbacks");
                h.assertTrue(
                    level.getEntity(saved.getUUID()) instanceof FriendlySlime
                        && other.getEntity(saved.getUUID()) == null,
                    "Exactly one restored identity exists across all dimensions");
              } finally {
                MinecraftForge.EVENT_BUS.unregister(nested);
                for (var world : level.getServer().getAllLevels()) {
                  var restored = world.getEntity(saved.getUUID());
                  if (restored != null) restored.discard();
                }
                for (int dx = -1; dx <= 1; dx++)
                  for (int dz = -1; dz <= 1; dz++) other.setChunkForced(cx + dx, cz + dz, false);
                a.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "recovery_reentry", timeoutTicks = 400)
  public static void cancelledRestorationAllowsImmediateRetry(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var old = ModEntities.HEALING_SLIME.get().create(h.getLevel());
    old.tame(owner);
    old.setNoAi(true);
    var stored = FriendlySlimeItem.fromSlime(old);
    var item = (FriendlySlimeItem) stored.getItem();
    var pos = BlockPos.containing(a.point(8, 101, 8));
    Consumer<EntityJoinLevelEvent> cancel =
        event -> {
          if (event.getEntity().getUUID().equals(old.getUUID())) event.setCanceled(true);
        };
    h.startSequence()
        .thenWaitUntil(() -> h.assertTrue(h.getLevel().isPositionEntityTicking(pos), "Arena ticks"))
        .thenExecute(
            () -> {
              MinecraftForge.EVENT_BUS.addListener(cancel);
              try {
                h.assertTrue(
                    !item.revive(h.getLevel(), owner, stored, pos) && stored.getCount() == 1,
                    "Cancellation preserves the stored companion");
              } finally {
                MinecraftForge.EVENT_BUS.unregister(cancel);
              }
              try {
                h.assertTrue(
                    item.revive(h.getLevel(), owner, stored, pos) && stored.isEmpty(),
                    "A rejected restoration releases any pending identity reservation");
              } finally {
                var restored = h.getLevel().getEntity(old.getUUID());
                if (restored != null) restored.discard();
                a.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "recovery_reentry", timeoutTicks = 400)
  public static void rejectedRestorationRetainsTraitsAndAllowsImmediateRetry(GameTestHelper h) {
    rejectedAdmission(h, true);
  }

  @GameTest(template = "empty", batch = "recovery_reentry", timeoutTicks = 400)
  public static void rejectedFreshSummonRetainsItsStackAndAllowsImmediateRetry(GameTestHelper h) {
    rejectedAdmission(h, false);
  }

  private static void rejectedAdmission(GameTestHelper h, boolean restoring) {
    var area = new LoadedWorkArea(h);
    var pos = BlockPos.containing(area.point(8, 101, 8));
    var owner = h.makeMockSurvivalPlayer();
    owner.moveTo(Vec3.atBottomCenterOf(pos).add(0, 0, -2));
    h.startSequence()
        .thenWaitUntil(() -> h.assertTrue(h.getLevel().isPositionEntityTicking(pos), "Arena ticks"))
        .thenExecute(
            () -> {
              try {
                for (int mode = 0; mode < 5; mode++) {
                  var original = ModEntities.HEALING_SLIME.get().create(h.getLevel());
                  original.tame(owner);
                  original.setNoAi(true);
                  original.setHealth(7);
                  original.getBag().setStackInSlot(8, new ItemStack(Items.EMERALD, 17));
                  var stack =
                      restoring
                          ? FriendlySlimeItem.fromSlime(original)
                          : new ItemStack(ModItems.HEALING_SLIME.get(), 3);
                  var before = stack.copy();
                  var registry = CompanionRegistry.get(h.getLevel());
                  if (restoring) registry.observeStored(stack, h.getLevel(), pos);
                  var previous = restoring ? registry.find(original.getUUID()) : null;
                  var item = (FriendlySlimeItem) stack.getItem();
                  var floor = pos.below();
                  var hit = new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false);
                  owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
                  FriendlySlime[] rejected = {null};
                  java.util.UUID[] attemptedIdentity = {null};
                  int action = mode;
                  Consumer<EntityJoinLevelEvent> callback =
                      event -> {
                        if (event.getLevel() != h.getLevel()
                            || !(event.getEntity() instanceof HealingSlime candidate)
                            || !owner.getUUID().equals(candidate.getOwnerUUID())) return;
                        rejected[0] = candidate;
                        attemptedIdentity[0] = candidate.getUUID();
                        switch (action) {
                          case 0 -> candidate.discard();
                          case 1 ->
                              candidate.hurt(
                                  candidate.damageSources().genericKill(), Float.MAX_VALUE);
                          case 2 -> candidate.setOwnerUUID(java.util.UUID.randomUUID());
                          case 3 -> candidate.setUUID(java.util.UUID.randomUUID());
                          default -> {
                            candidate.hurt(
                                candidate.damageSources().genericKill(), Float.MAX_VALUE);
                            event.setCanceled(true);
                          }
                        }
                      };
                  MinecraftForge.EVENT_BUS.addListener(callback);
                  try {
                    boolean admitted =
                        restoring
                            ? item.revive(h.getLevel(), owner, stack, pos)
                            : item.useOn(
                                    new UseOnContext(
                                        h.getLevel(), owner, InteractionHand.MAIN_HAND, stack, hit))
                                .consumesAction();
                    h.assertTrue(
                        rejected[0] != null && !admitted && ItemStack.matches(stack, before),
                        "Rejected admission retains the exact summon item and stored traits: "
                            + mode);
                    h.assertTrue(
                        h.getLevel().getEntity(rejected[0].getUUID()) == null
                            && h.getLevel().getEntity(original.getUUID()) == null
                            && !h.getLevel().getBlockState(pos).is(ModBlocks.BROKEN_SLIME.get())
                            && h.getLevel()
                                .getEntitiesOfClass(
                                    ItemEntity.class,
                                    new AABB(pos).inflate(4),
                                    e -> e.getItem().is(ModItems.BROKEN_SLIME.get()))
                                .isEmpty(),
                        "Rejected admission leaves no ghost or duplicate recovery data: " + mode);
                    h.assertTrue(
                        java.util.Objects.equals(registry.find(attemptedIdentity[0]), previous)
                            && (attemptedIdentity[0].equals(rejected[0].getUUID())
                                || registry.find(rejected[0].getUUID()) == null),
                        "Rejected admission preserves its original registry record: " + mode);
                  } finally {
                    MinecraftForge.EVENT_BUS.unregister(callback);
                  }
                  h.assertTrue(
                      restoring
                          ? item.revive(h.getLevel(), owner, stack, pos)
                          : item.useOn(
                                  new UseOnContext(
                                      h.getLevel(), owner, InteractionHand.MAIN_HAND, stack, hit))
                              .consumesAction(),
                      "Removing the admission restriction allows immediate retry: " + mode);
                  var live =
                      h.getLevel()
                          .getEntitiesOfClass(
                              HealingSlime.class,
                              new AABB(pos).inflate(4),
                              e -> e.isAlive() && owner.getUUID().equals(e.getOwnerUUID()));
                  h.assertTrue(
                      live.size() == 1
                          && live.get(0).isOrderedToSit()
                          && registry.find(live.get(0).getUUID()).location()
                              == CompanionRegistry.Location.WORLD
                          && stack.getCount() == (restoring ? 0 : 2),
                      "A successful retry creates one waiting companion and consumes one item");
                  if (restoring)
                    h.assertTrue(
                        live.get(0).getUUID().equals(original.getUUID())
                            && live.get(0).getHealth() == 1
                            && live.get(0).getBag().getStackInSlot(8).getCount() == 17,
                        "A restoration retry preserves identity, cargo and one-health revival");
                  live.forEach(FriendlySlime::discard);
                }
              } finally {
                area.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "recovery_reentry", timeoutTicks = 100)
  public static void nestedPinnedRestorationsCannotExceedTheOwnerLimit(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var outer = ModEntities.ENDER_SLIME.get().create(h.getLevel());
    var inner = ModEntities.ENDER_SLIME.get().create(h.getLevel());
    ItemStack[] stacks = new ItemStack[2];
    int n = 0;
    for (var slime : new EnderSlime[] {outer, inner}) {
      slime.tame(owner);
      slime.setNoAi(true);
      var data = slime.saveWithoutId(new CompoundTag());
      data.putBoolean("EnderPinned", true);
      slime.load(data);
      stacks[n++] = FriendlySlimeItem.fromSlime(slime);
    }
    int previous = SlimicConfig.ENDER_ANCHORS.get();
    boolean[] nested = {false}, attempted = {false};
    Consumer<EntityJoinLevelEvent> callback =
        e -> {
          if (e.getEntity().getUUID().equals(outer.getUUID()) && !attempted[0]) {
            attempted[0] = true;
            nested[0] =
                ((FriendlySlimeItem) stacks[1].getItem())
                    .revive(h.getLevel(), owner, stacks[1], h.absolutePos(new BlockPos(10, 2, 10)));
          }
        };
    SlimicConfig.ENDER_ANCHORS.set(1);
    MinecraftForge.EVENT_BUS.addListener(callback);
    try {
      boolean accepted =
          ((FriendlySlimeItem) stacks[0].getItem())
              .revive(h.getLevel(), owner, stacks[0], h.absolutePos(new BlockPos(6, 2, 6)));
      h.assertTrue(
          attempted[0] && (accepted != nested[0]), "Exactly one competing restoration succeeds");
      h.assertTrue(
          CompanionRegistry.get(h.getLevel()).pinnedCount(owner.getUUID(), null) == 1,
          "Native admission never exceeds the pinned Ender limit");
      h.assertTrue(
          stacks[0].getCount() + stacks[1].getCount() == 1,
          "Only the admitted companion consumes its item");
      h.succeed();
    } finally {
      MinecraftForge.EVENT_BUS.unregister(callback);
      SlimicConfig.ENDER_ANCHORS.set(previous);
      for (var slime : new EnderSlime[] {outer, inner}) {
        var restored = h.getLevel().getEntity(slime.getUUID());
        if (restored != null) restored.discard();
      }
    }
  }
}
