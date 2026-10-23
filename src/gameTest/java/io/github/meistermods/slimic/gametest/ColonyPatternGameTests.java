package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.AimlessColony;
import io.github.meistermods.slimic.entity.CombatSlime;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.world.AssemblySites;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ColonyPatternGameTests {
  private record Fixture(LoadedWorkArea area, AimlessColony boss, AssemblySites.Site site) {
    Fixture(GameTestHelper h) {
      this(new LoadedWorkArea(h), ModEntities.AIMLESS_COLONY.get().create(h.getLevel()), null);
      boss.moveTo(area.point(7.5, 101, 7.5));
    }

    CombatSlime target(GameTestHelper h, double x, double z) {
      var slime = ModEntities.COMBAT_SLIME.get().create(h.getLevel());
      slime.setOwnerUUID(UUID.randomUUID());
      slime.setTame(true);
      slime.setNoAi(true);
      slime.moveTo(area.point(x, 101, z));
      h.assertTrue(h.getLevel().addFreshEntity(slime), "Target joins the native level");
      return slime;
    }
  }

  private static void begin(AimlessColony boss, AimlessColony.Pattern pattern) throws Exception {
    var method = AimlessColony.class.getDeclaredMethod("begin", AimlessColony.Pattern.class);
    method.setAccessible(true);
    method.invoke(boss, pattern);
  }

  private static void step(Fixture f, int count) throws Exception {
    var method =
        AimlessColony.class.getDeclaredMethod(
            "patternStep", ServerLevel.class, AssemblySites.Site.class);
    method.setAccessible(true);
    for (int i = 0; i < count; i++) method.invoke(f.boss, f.boss.level(), f.site);
  }

  @GameTest(template = "empty", batch = "colony_patterns", timeoutTicks = 200)
  public static void ringLeavesCenterSafeAndOnlyDamagesAfterTheFullWarning(GameTestHelper h)
      throws Exception {
    var f = new Fixture(h);
    var inside = f.target(h, 8.5, 7.5);
    var ring = f.target(h, 12.5, 7.5);
    float before = ring.getHealth();
    begin(f.boss, AimlessColony.Pattern.RING);
    step(f, AimlessColony.Pattern.RING.warning - 1);
    h.assertTrue(ring.getHealth() == before, "No damage during the warning");
    step(f, 1);
    h.assertTrue(
        ring.getHealth() < before && inside.getHealth() == inside.getMaxHealth(),
        "The ring hits its annulus and leaves an inner refuge");
    h.assertTrue(
        f.boss.pattern() == AimlessColony.Pattern.IDLE,
        "The attack ends without persistent terrain hazards");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_patterns", timeoutTicks = 200)
  public static void crossingLocksItsAimAndLeavesDiagonalEscape(GameTestHelper h) throws Exception {
    var f = new Fixture(h);
    var target = f.target(h, 7.5, 7.5);
    var lane = f.target(h, 12.5, 7.5);
    var diagonal = f.target(h, 12.5, 12.5);
    f.boss.setTarget(target);
    begin(f.boss, AimlessColony.Pattern.CROSS);
    var marked = f.boss.aim();
    target.moveTo(f.area.point(3.5, 101, 3.5));
    step(f, AimlessColony.Pattern.CROSS.warning);
    h.assertTrue(
        f.boss.aim().equals(marked) && target.getHealth() == target.getMaxHealth(),
        "A moving target escapes the originally marked lanes");
    h.assertTrue(
        lane.getHealth() < lane.getMaxHealth() && diagonal.getHealth() == diagonal.getMaxHealth(),
        "Cross lanes hit while diagonal sectors remain safe");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_patterns", timeoutTicks = 200)
  public static void newPatternsRespectLineOfSightAndNativeDamageCancellation(GameTestHelper h)
      throws Exception {
    var f = new Fixture(h);
    var blocked = f.target(h, 12.5, 7.5);
    var canceled = f.target(h, 7.5, 12.5);
    for (int y = 101; y <= 105; y++)
      h.getLevel()
          .setBlockAndUpdate(
              net.minecraft.core.BlockPos.containing(f.area.point(10, y, 7)),
              Blocks.STONE.defaultBlockState());
    java.util.function.Consumer<LivingAttackEvent> listener =
        e -> {
          if (e.getEntity() == canceled) e.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(listener);
    try {
      begin(f.boss, AimlessColony.Pattern.RING);
      step(f, AimlessColony.Pattern.RING.warning);
      h.assertTrue(
          blocked.getHealth() == blocked.getMaxHealth()
              && canceled.getHealth() == canceled.getMaxHealth(),
          "Walls and Forge damage cancellation protect targets");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(listener);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_patterns", timeoutTicks = 200)
  public static void chainedAttackHasItsOwnWarningAndReloadCancelsUnfinishedStrike(GameTestHelper h)
      throws Exception {
    var f = new Fixture(h);
    var target = f.target(h, 12.5, 7.5);
    f.boss.setTarget(target);
    begin(f.boss, AimlessColony.Pattern.DASHING);
    var followUp = AimlessColony.class.getDeclaredField("followUp");
    followUp.setAccessible(true);
    followUp.set(f.boss, AimlessColony.Pattern.RING);
    step(f, AimlessColony.Pattern.DASHING.warning);
    h.assertTrue(
        f.boss.pattern() == AimlessColony.Pattern.RING
            && f.boss.patternTicks() == AimlessColony.Pattern.RING.warning,
        "Follow-up starts with a complete independent telegraph");
    var saved = f.boss.saveWithoutId(new CompoundTag());
    var restored = ModEntities.AIMLESS_COLONY.get().create(h.getLevel());
    restored.load(saved);
    h.assertTrue(
        restored.pattern() == AimlessColony.Pattern.IDLE
            && restored.patternTicks() == 0
            && followUp.get(restored) == AimlessColony.Pattern.IDLE,
        "Reload never resumes an invisible strike or stale combo");
    h.succeed();
  }
}
