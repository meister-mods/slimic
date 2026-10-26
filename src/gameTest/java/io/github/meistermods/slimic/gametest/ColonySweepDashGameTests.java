package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.AimlessColony;
import io.github.meistermods.slimic.entity.CombatSlime;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.world.AssemblySites;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ColonySweepDashGameTests {
  private record Fixture(GameTestHelper helper, LoadedWorkArea area, AimlessColony boss) {
    Fixture(GameTestHelper h) {
      this(h, new LoadedWorkArea(h), ModEntities.AIMLESS_COLONY.get().create(h.getLevel()));
      boss.moveTo(area.point(7.5, 101, 7.5));
    }

    CombatSlime target(Vec3 point) {
      var target = ModEntities.COMBAT_SLIME.get().create(helper.getLevel());
      target.setOwnerUUID(UUID.randomUUID());
      target.setTame(true);
      target.setNoAi(true);
      target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
      target.setHealth(100);
      target.moveTo(point);
      helper.assertTrue(helper.getLevel().addFreshEntity(target), "Native target is present");
      return target;
    }
  }

  private static void begin(AimlessColony boss, AimlessColony.Pattern pattern) throws Exception {
    var method = AimlessColony.class.getDeclaredMethod("begin", AimlessColony.Pattern.class);
    method.setAccessible(true);
    method.invoke(boss, pattern);
  }

  private static void step(AimlessColony boss, int count) throws Exception {
    var method =
        AimlessColony.class.getDeclaredMethod(
            "patternStep", ServerLevel.class, AssemblySites.Site.class);
    method.setAccessible(true);
    for (int i = 0; i < count; i++) method.invoke(boss, boss.level(), null);
  }

  @SuppressWarnings("unchecked")
  private static void sign(AimlessColony boss, int sign) throws Exception {
    var field = AimlessColony.class.getDeclaredField("SWEEP_SIGN");
    field.setAccessible(true);
    boss.getEntityData().set((EntityDataAccessor<Integer>) field.get(null), sign);
  }

  @GameTest(template = "empty", batch = "colony_sweep_dash", timeoutTicks = 200)
  public static void bothSweepDirectionsTravelFromFirstEdgeToLastOnlyAfterWarning(GameTestHelper h)
      throws Exception {
    for (int sign : new int[] {-1, 1}) {
      var f = new Fixture(h);
      var origin = f.boss.position();
      var early = f.target(origin.add(new Vec3(0, 0, 5).yRot((float) Math.toRadians(-60 * sign))));
      var middle = f.target(origin.add(0, 0, 5));
      var late = f.target(origin.add(new Vec3(0, 0, 5).yRot((float) Math.toRadians(60 * sign))));
      f.boss.setTarget(middle);
      begin(f.boss, AimlessColony.Pattern.SWEEP);
      sign(f.boss, sign);
      step(f.boss, AimlessColony.Pattern.SWEEP.warning);
      h.assertTrue(
          early.getHealth() == 100 && middle.getHealth() == 100 && late.getHealth() == 100,
          "All of the warning is harmless");
      step(f.boss, 1);
      h.assertTrue(
          early.getHealth() < 100 && middle.getHealth() == 100 && late.getHealth() == 100,
          "The first tick hits the indicated starting edge only");
      step(f.boss, 4);
      h.assertTrue(
          middle.getHealth() < 100 && late.getHealth() == 100,
          "The swing reaches the middle before the far edge");
      step(f.boss, 5);
      h.assertTrue(
          late.getHealth() < 100 && f.boss.pattern() == AimlessColony.Pattern.RECOVERY,
          "The far edge is reached before a harmless recovery");
      float health = middle.getHealth();
      middle.invulnerableTime = 0;
      step(f.boss, AimlessColony.Pattern.RECOVERY.warning);
      h.assertTrue(
          middle.getHealth() == health && f.boss.pattern() == AimlessColony.Pattern.IDLE,
          "Recovery cannot repeat the sweep damage");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_sweep_dash", timeoutTicks = 200)
  public static void enteringAnAlreadySweptSectorIsSafeAndCanceledHitsAreNotRetried(
      GameTestHelper h) throws Exception {
    var f = new Fixture(h);
    var center = f.target(f.boss.position().add(0, 0, 5));
    var lateArrival = f.target(f.boss.position().add(0, 0, -5));
    f.boss.setTarget(center);
    begin(f.boss, AimlessColony.Pattern.SWEEP);
    sign(f.boss, 1);
    var attempts = new AtomicInteger();
    Consumer<LivingAttackEvent> listener =
        event -> {
          if (event.getEntity() == center && event.getSource().getEntity() == f.boss) {
            attempts.incrementAndGet();
            event.setCanceled(true);
          }
        };
    MinecraftForge.EVENT_BUS.addListener(listener);
    try {
      step(f.boss, AimlessColony.Pattern.SWEEP.warning + 6);
      lateArrival.moveTo(f.boss.position().add(0, 0, 5));
      step(f.boss, 4 + AimlessColony.Pattern.RECOVERY.warning);
      h.assertTrue(
          center.getHealth() == 100 && attempts.get() == 1 && lateArrival.getHealth() == 100,
          "Canceled hits stay canceled and passed ground does not remain hazardous");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(listener);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_sweep_dash", timeoutTicks = 200)
  public static void finalChargeStepHitsExactlyItsAdvertisedEndWithoutExtraTravel(GameTestHelper h)
      throws Exception {
    var f = new Fixture(h);
    f.boss.moveTo(f.area.point(7.5, 101, 3.5));
    var start = f.boss.position();
    double extent =
        AimlessColony.DASH_SPEED * AimlessColony.Pattern.DASHING.warning + f.boss.dashHalfWidth();
    var last = f.target(start.add(0, 0, extent - .05));
    var beyond = f.target(start.add(0, 0, extent + .05));
    f.boss.setTarget(last);
    begin(f.boss, AimlessColony.Pattern.DASH);
    step(f.boss, AimlessColony.Pattern.DASH.warning);
    step(f.boss, 11);
    h.assertTrue(last.getHealth() == 100, "The last-step contact is not struck early");
    step(f.boss, 1);
    h.assertTrue(
        last.getHealth() < 100 && beyond.getHealth() == 100,
        "The final movement interval hits its front limit without going beyond the warning");
    var end = f.boss.position();
    h.assertTrue(
        Math.abs(end.z - start.z - 7.8) < 1E-7, "All twelve physical steps occur exactly once");
    f.boss.travel(Vec3.ZERO);
    h.assertTrue(
        f.boss.position().subtract(end).horizontalDistanceSqr() < 1E-9,
        "Ordinary travel cannot apply the last horizontal step twice");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_sweep_dash", timeoutTicks = 200)
  public static void diagonalChargeIncludesRotatedCornersButNotTheOutsideOrUpperFloor(
      GameTestHelper h) throws Exception {
    var f = new Fixture(h);
    f.boss.moveTo(f.area.point(4.5, 101, 4.5));
    Vec3 forward = new Vec3(1, 0, 1).normalize(), side = new Vec3(1, 0, -1).normalize();
    var aiming = f.target(f.boss.position().add(forward.scale(7)));
    double half = f.boss.dashHalfWidth();
    Vec3 edge = f.boss.position().add(forward.scale(half - .05)).add(side.scale(half - .05));
    var inside = f.target(edge);
    var outside = f.target(edge.add(side.scale(.1)));
    var above = f.target(edge.add(0, 5, 0));
    f.boss.setTarget(aiming);
    begin(f.boss, AimlessColony.Pattern.DASH);
    step(f.boss, AimlessColony.Pattern.DASH.warning + 1);
    h.assertTrue(
        inside.getHealth() < 100 && outside.getHealth() == 100 && above.getHealth() == 100,
        "Diagonal corners match the oriented warning without widening damage or reaching another"
            + " floor");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_sweep_dash", timeoutTicks = 200)
  public static void movingSweepAndRecoverySynchronizeAndReloadWithoutAttacking(GameTestHelper h)
      throws Exception {
    var f = new Fixture(h);
    f.boss.setTarget(f.target(f.boss.position().add(0, 0, 5)));
    begin(f.boss, AimlessColony.Pattern.SWEEP);
    sign(f.boss, -1);
    step(f.boss, AimlessColony.Pattern.SWEEP.warning + 3);
    for (int i = 0; i < 2; i++) {
      var replica = ModEntities.AIMLESS_COLONY.get().create(h.getLevel());
      var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
      try {
        new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(
                f.boss.getId(), f.boss.getEntityData().getNonDefaultValues())
            .write(buffer);
        replica
            .getEntityData()
            .assignValues(
                new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(buffer)
                    .packedItems());
      } finally {
        buffer.release();
      }
      h.assertTrue(
          replica.pattern() == f.boss.pattern()
              && replica.patternTicks() == f.boss.patternTicks()
              && replica.sweepSign() == -1
              && replica.recoveryMotion() == f.boss.recoveryMotion(),
          "Late tracking receives active motion, direction, and recovery provenance");
      var restored = ModEntities.AIMLESS_COLONY.get().create(h.getLevel());
      restored.load(f.boss.saveWithoutId(new CompoundTag()));
      h.assertTrue(
          restored.pattern() == AimlessColony.Pattern.IDLE
              && restored.patternTicks() == 0
              && restored.recoveryMotion() == AimlessColony.Pattern.IDLE
              && restored.getDeltaMovement().equals(Vec3.ZERO),
          "Save reload cancels active strikes and leftover motion");
      if (i == 0) step(f.boss, 7);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_sweep_dash", timeoutTicks = 160)
  public static void liveServerTicksMoveOncePerChargeStepAndThenSettle(GameTestHelper h)
      throws Exception {
    var f = new Fixture(h);
    f.boss.moveTo(f.area.point(7.5, 101, 3.5));
    var start = f.boss.position();
    var target = f.target(start.add(0, 0, 7));
    var data = AssemblySites.get(h.getLevel());
    var site = data.register(h.getLevel(), f.boss.blockPosition());
    h.assertTrue(site != null && data.awaken(h.getLevel(), site), "Live encounter is reserved");
    site.state = AssemblySites.State.ACTIVE;
    f.boss.setUUID(site.boss);
    f.boss.bind(site);
    f.boss.setNoGravity(true);
    f.boss.setTarget(target);
    h.assertTrue(
        h.getLevel().addFreshEntity(f.boss), "Boss participates in native AI and physics ticks");
    begin(f.boss, AimlessColony.Pattern.DASH);
    Vec3[] previous = {start};
    int[] moving = {0}, recovery = {0};
    h.onEachTick(
        () -> {
          Vec3 current = f.boss.position();
          double distance = current.subtract(previous[0]).horizontalDistance();
          h.assertTrue(
              distance <= AimlessColony.DASH_SPEED + 1E-6,
              "Native travel cannot double the server's charge displacement");
          if (distance > .1) moving[0]++;
          previous[0] = current;
          if (f.boss.pattern() == AimlessColony.Pattern.RECOVERY) recovery[0]++;
          if (recovery[0] > 0 && f.boss.pattern() == AimlessColony.Pattern.IDLE) {
            h.assertTrue(
                moving[0] == 12
                    && recovery[0] == AimlessColony.Pattern.RECOVERY.warning
                    && Math.abs(current.z - start.z - 7.8) < 1E-5
                    && target.getHealth() < 100,
                "Live AI preserves twelve actual steps, contact damage and the full harmless"
                    + " recovery");
            data.reset(h.getLevel(), site);
            f.boss.discard();
            h.succeed();
          }
        });
  }
}
