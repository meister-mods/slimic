package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.AimlessColony;
import io.github.meistermods.slimic.entity.CombatSlime;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.world.AssemblySites;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ColonyMovementGameTests {
  private static CombatSlime target(GameTestHelper h, Vec3 position) {
    var target = ModEntities.COMBAT_SLIME.get().create(h.getLevel());
    target.setOwnerUUID(UUID.randomUUID());
    target.setTame(true);
    target.setNoAi(true);
    target.moveTo(position);
    h.assertTrue(h.getLevel().addFreshEntity(target), "Movement target joins the native level");
    return target;
  }

  private static void field(AimlessColony boss, String name, int value) throws Exception {
    var field = AimlessColony.class.getDeclaredField(name);
    field.setAccessible(true);
    field.setInt(boss, value);
  }

  private static void advance(GameTestHelper h, AimlessColony boss, AssemblySites.Site site)
      throws Exception {
    var method =
        AimlessColony.class.getDeclaredMethod("moveBetweenAttacks", AssemblySites.Site.class);
    method.setAccessible(true);
    method.invoke(boss, site);
    Vec3 before = boss.position();
    boss.move(MoverType.SELF, boss.getDeltaMovement());
    h.assertTrue(
        boss.position().distanceToSqr(before) > 1E-6,
        "Native entity movement advances the body rather than merely requesting a direction");
    h.assertTrue(
        Math.abs(boss.getY() - 101) < 1E-6 && h.getLevel().noCollision(boss),
        "The moved body remains on its supported floor without intersecting terrain");
    var box = boss.getBoundingBox().deflate(1E-5);
    for (int x = (int) Math.floor(box.minX); x <= Math.floor(box.maxX); x++)
      for (int z = (int) Math.floor(box.minZ); z <= Math.floor(box.maxZ); z++) {
        var floor = new BlockPos(x, 100, z);
        h.assertTrue(
            h.getLevel().getBlockState(floor).isFaceSturdy(h.getLevel(), floor, Direction.UP)
                && h.getLevel().getFluidState(floor).isEmpty(),
            "Every part of the moved body's footprint has solid, dry support");
      }
  }

  private static void idleAi(AimlessColony boss) throws Exception {
    boss.tickCount = 1;
    field(boss, "actionDelay", 0);
    field(boss, "splitDelay", 1000);
    field(boss, "rallyDelay", 1000);
    var method = AimlessColony.class.getDeclaredMethod("customServerAiStep");
    method.setAccessible(true);
    method.invoke(boss);
  }

  @GameTest(template = "empty", batch = "colony_movement", timeoutTicks = 400)
  public static void idleMovementActuallyApproachesRetreatsCirclesAndReturnsSafely(GameTestHelper h)
      throws Exception {
    var data = AssemblySites.get(h.getLevel());
    for (double distance : new double[] {3, 6.5, 12}) {
      var area = new LoadedWorkArea(h);
      var boss = ModEntities.AIMLESS_COLONY.get().create(h.getLevel());
      boss.moveTo(area.point(7.5, 101, 7.5));
      boss.getRandom().setSeed(191);
      var target = target(h, boss.position().add(0, 0, distance));
      boss.setTarget(target);
      var site = data.register(h.getLevel(), boss.blockPosition());
      h.assertTrue(site != null, "Movement origin is persisted");
      Vec3 start = boss.position();
      double before = boss.distanceToSqr(target);
      for (int i = 0; i < 12; i++) advance(h, boss, site);
      if (distance == 3)
        h.assertTrue(
            boss.distanceToSqr(target) > before + 1, "Close pressure causes actual retreat");
      else if (distance == 12)
        h.assertTrue(boss.distanceToSqr(target) < before - 5, "A distant opponent is approached");
      else
        h.assertTrue(
            Math.abs(boss.getX() - start.x) > 1,
            "At middle distance the body actually circles sideways instead of standing still");
    }

    var area = new LoadedWorkArea(h);
    var level = h.getLevel();
    var firstExtra = BlockPos.containing(area.point(32, 100, 7));
    var secondExtra = BlockPos.containing(area.point(48, 100, 7));
    for (var pos : new BlockPos[] {firstExtra, secondExtra}) {
      level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
      level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }
    AssemblySites.Site site = null;
    try {
      // A continuous loaded runway exercises real travel across the encounter's 44-block boundary.
      for (int x = 0; x < 56; x++)
        for (int z = 3; z <= 12; z++) {
          var floor = BlockPos.containing(area.point(x, 100, z));
          int ceiling =
              Math.max(
                  107, level.getHeight(Heightmap.Types.WORLD_SURFACE, floor.getX(), floor.getZ()));
          level.setBlock(floor, Blocks.BEDROCK.defaultBlockState(), 2);
          for (int y = 101; y <= ceiling; y++)
            level.setBlock(
                new BlockPos(floor.getX(), y, floor.getZ()), Blocks.AIR.defaultBlockState(), 2);
        }
      var home = area.point(7.5, 101, 7.5);
      site = data.register(level, BlockPos.containing(home));
      h.assertTrue(site != null && data.awaken(level, site), "Return encounter is reserved");
      site.state = AssemblySites.State.ACTIVE;
      var boss = ModEntities.AIMLESS_COLONY.get().create(level);
      boss.setUUID(site.boss);
      boss.bind(site);
      boss.moveTo(home.add(45, 0, 0));
      var opponent = target(h, boss.position().add(0, 0, 3.5));
      boss.setTarget(opponent);
      idleAi(boss);
      h.assertTrue(
          boss.pattern() == AimlessColony.Pattern.IDLE,
          "A nearby target cannot start an attack during return");
      for (int i = 0; i < 12; i++) advance(h, boss, site);
      h.assertTrue(
          boss.position().distanceTo(home) < 44,
          "The body crosses back inside the encounter boundary");
      int steps = 0;
      while (boss.position().distanceTo(home) > 20 && steps++ < 200) advance(h, boss, site);
      h.assertTrue(steps < 200, "Returning continues beyond the old 38-block threshold");
      var saved = boss.saveWithoutId(new CompoundTag());
      h.assertTrue(
          saved.getBoolean("ReturningHome"), "Return intent persists inside the intermediate band");
      var restored = ModEntities.AIMLESS_COLONY.get().create(level);
      restored.load(saved);
      restored.setTarget(opponent);
      idleAi(restored);
      h.assertTrue(
          restored.pattern() == AimlessColony.Pattern.IDLE,
          "Reload at twenty blocks resumes return without attacking");
      steps = 0;
      while (restored.position().distanceTo(home) > 8 && steps++ < 140) advance(h, restored, site);
      h.assertTrue(steps < 140, "Native movement reaches the inner return threshold");
      restored.moveTo(home.add(7, 0, 0));
      opponent.moveTo(restored.position().add(0, 0, 5));
      restored.setTarget(opponent);
      idleAi(restored);
      h.assertTrue(
          !restored.saveWithoutId(new CompoundTag()).getBoolean("ReturningHome")
              && restored.pattern() != AimlessColony.Pattern.IDLE,
          "Inside seven blocks, return ends and a fresh full-warning attack may begin");
    } finally {
      if (site != null) data.reset(level, site);
      for (var pos : new BlockPos[] {firstExtra, secondExtra})
        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false);
    }
    h.succeed();
  }
}
