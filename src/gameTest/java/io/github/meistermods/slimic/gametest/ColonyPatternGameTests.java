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
  public static void dashCannotDamageTargetsWhollyOutsideItsWarningLane(GameTestHelper h)
      throws Exception {
    for (var forward :
        java.util.List.of(
            new net.minecraft.world.phys.Vec3(0, 0, 1),
            new net.minecraft.world.phys.Vec3(1, 0, 0),
            new net.minecraft.world.phys.Vec3(1, 0, 1).normalize())) {
      var f = new Fixture(h);
      var side = new net.minecraft.world.phys.Vec3(-forward.z, 0, forward.x);
      var aim = f.target(h, 7.5 + forward.x * 5, 7.5 + forward.z * 5);
      var outside = f.target(h, 7.5 + side.x * 2.5, 7.5 + side.z * 2.5);
      var inside = f.target(h, 7.5 + side.x, 7.5 + side.z);
      f.boss.setTarget(aim);
      begin(f.boss, AimlessColony.Pattern.DASH);
      step(f, AimlessColony.Pattern.DASH.warning);
      step(f, 1);
      h.assertTrue(
          outside.getHealth() == outside.getMaxHealth(),
          "A whole target outside the marked lane stays safe for cardinal and diagonal charges");
      h.assertTrue(
          inside.getHealth() < inside.getMaxHealth(),
          "A target in the body's marked footprint is still hit");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_patterns", timeoutTicks = 200)
  public static void dashDirectionSurvivesShovesTargetChangesAndNativeDataPackets(GameTestHelper h)
      throws Exception {
    var f = new Fixture(h);
    f.boss.setTarget(f.target(h, 12.5, 12.5));
    begin(f.boss, AimlessColony.Pattern.DASH);
    var direction = f.boss.dashDirection();
    var replica = ModEntities.AIMLESS_COLONY.get().create(h.getLevel());
    var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
    try {
      new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(
              f.boss.getId(), f.boss.getEntityData().getNonDefaultValues())
          .write(buffer);
      var decoded = new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(buffer);
      replica.getEntityData().assignValues(decoded.packedItems());
    } finally {
      buffer.release();
    }
    h.assertTrue(
        replica.dashDirection().distanceToSqr(direction) < 1E-12,
        "A native late-tracking data packet preserves the exact charge direction");
    f.boss.moveTo(f.area.point(9.5, 101, 7.5));
    f.boss.setTarget(f.target(h, 2.5, 7.5));
    step(f, AimlessColony.Pattern.DASH.warning);
    step(f, 1);
    var movement = f.boss.getDeltaMovement().multiply(1, 0, 1);
    h.assertTrue(
        f.boss.dashDirection().distanceToSqr(direction) < 1E-12
            && movement.normalize().distanceToSqr(direction) < 1E-12,
        "A shove and new target cannot redirect either the charge or its warning");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_patterns", timeoutTicks = 200)
  public static void dashWarningIncludesBothBodyEndsAndTracksRemainingTravel(GameTestHelper h)
      throws Exception {
    var f = new Fixture(h);
    f.boss.setTarget(f.target(h, 7.5, 13.5));
    var rear = f.target(h, 7.5, 6.0);
    var beyondRear = f.target(h, 7.5, 5.5);
    var front = f.target(h, 7.5, 9.0);
    var beyondFront = f.target(h, 7.5, 9.5);
    begin(f.boss, AimlessColony.Pattern.DASH);
    h.assertTrue(
        Math.abs(f.boss.dashRemainingDistance() - 7.8) < 1E-9,
        "Warning covers the complete twelve-step travel");
    step(f, AimlessColony.Pattern.DASH.warning);
    step(f, 1);
    h.assertTrue(
        rear.getHealth() < rear.getMaxHealth()
            && front.getHealth() < front.getMaxHealth()
            && beyondRear.getHealth() == beyondRear.getMaxHealth()
            && beyondFront.getHealth() == beyondFront.getMaxHealth(),
        "The rear and front contact limits match the body extent shown by the closed warning");
    h.assertTrue(
        Math.abs(f.boss.dashRemainingDistance() - 11 * AimlessColony.DASH_SPEED) < 1E-9,
        "The moving warning shrinks with the remaining duration");
    step(f, 11);
    h.assertTrue(
        f.boss.dashRemainingDistance() == 0,
        "Completed charges leave no stale forward warning distance");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_patterns", timeoutTicks = 200)
  public static void staleDistantOrCrossDimensionTargetsCannotStartOrChainAttacks(GameTestHelper h)
      throws Exception {
    for (boolean otherDimension : new boolean[] {false, true}) {
      var f = new Fixture(h);
      var original = f.target(h, 7.5, 13.5);
      f.boss.setTarget(original);
      begin(f.boss, AimlessColony.Pattern.PULL);
      var followUp = AimlessColony.class.getDeclaredField("followUp");
      followUp.setAccessible(true);
      followUp.set(f.boss, AimlessColony.Pattern.CROSS);
      if (otherDimension) {
        var stale =
            ModEntities.COMBAT_SLIME
                .get()
                .create(h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER));
        stale.setOwnerUUID(UUID.randomUUID());
        stale.setTame(true);
        stale.moveTo(original.position());
        f.boss.setTarget(stale);
      } else original.moveTo(f.area.point(107.5, 101, 7.5));
      step(f, AimlessColony.Pattern.PULL.warning);
      h.assertTrue(
          f.boss.pattern() == AimlessColony.Pattern.IDLE,
          "A distant or cross-dimension reference cannot create an unannounced follow-up");
      var data = AssemblySites.get(h.getLevel());
      var site = data.register(h.getLevel(), f.boss.blockPosition());
      h.assertTrue(site != null && data.awaken(h.getLevel(), site), "Native encounter reserved");
      site.state = AssemblySites.State.ACTIVE;
      f.boss.setUUID(site.boss);
      f.boss.bind(site);
      f.boss.tickCount = 1;
      var delay = AimlessColony.class.getDeclaredField("actionDelay");
      delay.setAccessible(true);
      delay.setInt(f.boss, 0);
      var choose = AimlessColony.class.getDeclaredMethod("customServerAiStep");
      choose.setAccessible(true);
      choose.invoke(f.boss);
      h.assertTrue(
          f.boss.pattern() == AimlessColony.Pattern.IDLE && f.boss.getTarget() == null,
          "Idle attack selection also rejects and clears the stale target");
      data.reset(h.getLevel(), site);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_patterns", timeoutTicks = 200)
  public static void committedCrossingStillFinishesAtItsOriginalMarkAfterTargetLeaves(
      GameTestHelper h) throws Exception {
    var f = new Fixture(h);
    var original = f.target(h, 7.5, 7.5);
    var lane = f.target(h, 12.5, 7.5);
    f.boss.setTarget(original);
    begin(f.boss, AimlessColony.Pattern.CROSS);
    var marked = f.boss.aim();
    original.moveTo(f.area.point(107.5, 101, 7.5));
    step(f, AimlessColony.Pattern.CROSS.warning);
    h.assertTrue(
        f.boss.aim().equals(marked)
            && lane.getHealth() < lane.getMaxHealth()
            && original.getHealth() == original.getMaxHealth(),
        "An already announced crossing stays at its mark rather than following a teleport");
    h.succeed();
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
