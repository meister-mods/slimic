package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkSightGameTests {
  private static int nextArea;

  private record Site(BagSlime actor, FriendlySlime target, BlockPos gap, int chunkX, int chunkZ) {}

  private static Site site(GameTestHelper h, EntityType<? extends BagSlime> type) {
    int x = (6_000_000 + nextArea++ * 256) >> 4, z = -(6_000_000 >> 4);
    var level = h.getLevel();
    level.getChunk(x, z);
    level.getChunk(x + 3, z);
    var start = new BlockPos((x << 4) + 8, 310, (z << 4) + 8);
    var end = start.offset(48, 0, 0);
    for (var center : java.util.List.of(start, end))
      for (int dx = -2; dx <= 2; dx++)
        for (int dz = -2; dz <= 2; dz++) {
          level.setBlock(center.offset(dx, -1, dz), Blocks.BEDROCK.defaultBlockState(), 2);
          for (int dy = 0; dy < 4; dy++)
            level.setBlock(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
        }
    var owner = h.makeMockSurvivalPlayer();
    var actor = type.create(level);
    actor.tame(owner);
    actor.moveTo(start.getX() + .5, 310, start.getZ() + .5);
    actor.setOnGround(true);
    actor.onReleased();
    actor.setWorking(true);
    var target = ModEntities.FRIENDLY_SLIME.get().create(level);
    target.tame(owner);
    target.moveTo(end.getX() + .5, 310, end.getZ() + .5);
    var gap = start.offset(16, 0, 0);
    h.assertTrue(!level.hasChunkAt(gap), "Sight gap begins unloaded");
    return new Site(actor, target, gap, x, z);
  }

  private static void blockedSight(GameTestHelper h, EntityType<? extends BagSlime> type) {
    var s = site(h, type);
    boolean visible = s.actor.hasLineOfSight(s.target);
    h.assertTrue(
        !h.getLevel().hasChunkAt(s.gap) && !visible,
        "Work sight treats an unavailable gap as opaque without loading it");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "work_sight")
  public static void transportSightDoesNotLoadAnUnknownGap(GameTestHelper h) {
    blockedSight(h, ModEntities.TRANSPORT_SLIME.get());
  }

  @GameTest(template = "empty", batch = "work_sight")
  public static void healingSightDoesNotLoadAnUnknownGap(GameTestHelper h) {
    blockedSight(h, ModEntities.HEALING_SLIME.get());
  }

  @GameTest(template = "empty", batch = "work_sight")
  public static void combatSightDoesNotLoadAnUnknownGap(GameTestHelper h) {
    blockedSight(h, ModEntities.COMBAT_SLIME.get());
  }

  @GameTest(template = "empty", batch = "work_sight")
  public static void loadedSightRecoversAfterTerrainBecomesAvailable(GameTestHelper h) {
    var s = site(h, ModEntities.HEALING_SLIME.get());
    h.assertTrue(
        !s.actor.hasLineOfSight(s.target) && !h.getLevel().hasChunkAt(s.gap),
        "Initial unavailable terrain blocks sight");
    h.getLevel().getChunk(s.chunkX + 1, s.chunkZ);
    h.getLevel().getChunk(s.chunkX + 2, s.chunkZ);
    h.assertTrue(
        s.actor.hasLineOfSight(s.target), "A new query sees an entirely loaded clear route");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "work_sight")
  public static void ordinaryFollowingRetainsNativeSightBehavior(GameTestHelper h) {
    var s = site(h, ModEntities.TRANSPORT_SLIME.get());
    s.actor.setWorking(false);
    h.assertTrue(
        s.actor.hasLineOfSight(s.target) && h.getLevel().hasChunkAt(s.gap),
        "Native following demonstrates why work rays need their own loaded view");
    h.succeed();
  }

  private static void fieldApproach(GameTestHelper h, EntityType<? extends FieldSlime> type) {
    var s = site(h, type);
    SlimeStat.DETECTION_RANGE.tune(s.actor, 32);
    try {
      var approach = FieldSlime.class.getDeclaredMethod("approach", BlockPos.class);
      approach.setAccessible(true);
      approach.invoke(s.actor, s.target.blockPosition());
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    h.assertTrue(
        !h.getLevel().hasChunkAt(s.gap),
        "A distant field target cannot load terrain during its pre-range sight check");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "work_sight")
  public static void farmingApproachDoesNotLoadAColdSightGap(GameTestHelper h) {
    fieldApproach(h, ModEntities.FARMING_SLIME.get());
  }

  @GameTest(template = "empty", batch = "work_sight")
  public static void loggingApproachDoesNotLoadAColdSightGap(GameTestHelper h) {
    fieldApproach(h, ModEntities.LOGGING_SLIME.get());
  }

  private static void cleaningSight(
      GameTestHelper h, CleaningSlime actor, ItemEntity item, BlockPos gap) {
    double reach = SlimeStat.REACH.value(actor);
    h.assertTrue(
        actor.getEyePosition().distanceToSqr(item.position().add(0, .1, 0)) <= reach * reach,
        "The actual collection sight check is within equipped reach");
    h.assertTrue(!h.getLevel().hasChunkAt(gap), "Collection gap begins unloaded");
    boolean visible;
    try {
      var method = CleaningSlime.class.getDeclaredMethod("visible", ItemEntity.class);
      method.setAccessible(true);
      visible = (boolean) method.invoke(actor, item);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    h.assertTrue(
        !visible && !h.getLevel().hasChunkAt(gap),
        "Collection sight cannot acquire an unavailable chunk, even with a reachable item");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "work_sight")
  public static void cleaningReachUpgradeDoesNotLoadAColdSightGap(GameTestHelper h) {
    var s = site(h, ModEntities.CLEANING_SLIME.get());
    SlimeStat.REACH.tune(s.actor, 32);
    h.getLevel().getChunk(s.chunkX + 2, s.chunkZ);
    var item =
        new ItemEntity(
            h.getLevel(), s.actor.getX() + 30, 310, s.actor.getZ(), new ItemStack(Items.DIAMOND));
    cleaningSight(h, (CleaningSlime) s.actor, item, s.gap);
  }

  @GameTest(template = "empty", batch = "work_sight")
  public static void cleaningDefaultReachDoesNotLoadAnUnknownDiagonalCorner(GameTestHelper h) {
    var s = site(h, ModEntities.CLEANING_SLIME.get());
    h.getLevel().getChunk(s.chunkX + 1, s.chunkZ + 1);
    s.actor.moveTo((s.chunkX << 4) + 15.5, 310, (s.chunkZ << 4) + 15.9);
    var item =
        new ItemEntity(
            h.getLevel(),
            (s.chunkX << 4) + 16.7,
            310,
            (s.chunkZ << 4) + 17.1,
            new ItemStack(Items.DIAMOND));
    cleaningSight(
        h,
        (CleaningSlime) s.actor,
        item,
        new BlockPos((s.chunkX << 4) + 15, 310, (s.chunkZ << 4) + 16));
  }

  @GameTest(template = "empty", batch = "work_sight")
  public static void miningSightFromOutsideQuarryDoesNotLoadADiagonalCorner(GameTestHelper h) {
    var s = site(h, ModEntities.MINING_SLIME.get());
    h.getLevel().getChunk(s.chunkX + 1, s.chunkZ + 1);
    s.actor.moveTo((s.chunkX << 4) + 16.7, 310, (s.chunkZ << 4) + 17.1);
    var pos = new BlockPos((s.chunkX << 4) + 15, 310, (s.chunkZ << 4) + 15);
    // Known-shape placement avoids neighbor-shape reads before the ray under test.
    h.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 18);
    var gap = new BlockPos((s.chunkX << 4) + 15, 310, (s.chunkZ << 4) + 16);
    h.assertTrue(!h.getLevel().hasChunkAt(gap), "Quarry corner begins unavailable");
    h.assertTrue(
        ((MiningSlime) s.actor)
            .getMiningChunk()
            .equals(new net.minecraft.world.level.ChunkPos(pos)),
        "The target remains inside the original quarry while the miner stands just outside");
    try {
      var method = MiningSlime.class.getDeclaredMethod("canSee", BlockPos.class);
      method.setAccessible(true);
      h.assertTrue(
          !(boolean) method.invoke(s.actor, pos) && !h.getLevel().hasChunkAt(gap),
          "A quarry sight check cannot load the diagonal gap between two loaded chunks");
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    h.succeed();
  }
}
