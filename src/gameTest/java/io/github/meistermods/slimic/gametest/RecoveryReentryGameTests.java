package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
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
}
