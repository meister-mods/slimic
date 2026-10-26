package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.AimlessColony;
import io.github.meistermods.slimic.entity.CombatSlime;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.world.AssemblySites;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ColonyExpansionGameTests {
  private record Fixture(GameTestHelper h, LoadedWorkArea area, AimlessColony boss) {
    Fixture(GameTestHelper h) {
      this(h, new LoadedWorkArea(h), ModEntities.AIMLESS_COLONY.get().create(h.getLevel()));
      boss.moveTo(area.point(7.5, 101, 7.5));
    }

    CombatSlime target(double x, double y, double z) {
      var target = ModEntities.COMBAT_SLIME.get().create(h.getLevel());
      target.setOwnerUUID(UUID.randomUUID());
      target.setTame(true);
      target.setNoAi(true);
      target.moveTo(area.point(x, y, z));
      h.assertTrue(h.getLevel().addFreshEntity(target), "Combat target joins the native level");
      return target;
    }

    CombatSlime target(double x, double z) {
      return target(x, 101, z);
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

  private static void field(AimlessColony boss, String name, Object value) throws Exception {
    var field = AimlessColony.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(boss, value);
  }

  private static void idleAi(AimlessColony boss) throws Exception {
    var method = AimlessColony.class.getDeclaredMethod("customServerAiStep");
    method.setAccessible(true);
    method.invoke(boss);
  }

  @GameTest(template = "empty", batch = "colony_expansion", timeoutTicks = 200)
  public static void sweepLocksItsSectorAndPreservesSideRearAndVerticalEscape(GameTestHelper h)
      throws Exception {
    var f = new Fixture(h);
    var aim = f.target(7.5, 12.5);
    var forward = f.target(7.5, 11.5);
    var rear = f.target(7.5, 3.5);
    var insideAngle =
        f.target(7.5 + 5 * Math.sin(Math.toRadians(69)), 7.5 + 5 * Math.cos(Math.toRadians(69)));
    var outsideAngle =
        f.target(7.5 + 5 * Math.sin(Math.toRadians(71)), 7.5 + 5 * Math.cos(Math.toRadians(71)));
    var insideRadius = f.target(7.5, 15.4);
    var outsideRadius = f.target(7.5, 15.6);
    var above = f.target(7.5, 103.6, 11.5);
    var victims =
        List.of(forward, rear, insideAngle, outsideAngle, insideRadius, outsideRadius, above);
    f.boss.setTarget(aim);
    begin(f.boss, AimlessColony.Pattern.SWEEP);
    Vec3 origin = f.boss.attackOrigin(), direction = f.boss.dashDirection();
    aim.moveTo(f.area.point(107.5, 101, 7.5));
    f.boss.moveTo(f.area.point(8.5, 101, 7.5));
    step(f.boss, AimlessColony.Pattern.SWEEP.warning - 1);
    h.assertTrue(
        victims.stream().allMatch(v -> v.getHealth() == v.getMaxHealth()),
        "No sweep damage occurs before the complete warning");
    h.assertTrue(
        f.boss.attackOrigin().equals(origin) && f.boss.dashDirection().equals(direction),
        "A shove and departing target do not rotate or relocate the announced sector");
    step(f.boss, 1);
    h.assertTrue(
        victims.stream().allMatch(v -> v.getHealth() == v.getMaxHealth()),
        "The completed warning starts the moving sweep without an instant sector hit");
    step(f.boss, AimlessColony.Pattern.SWEEPING.warning);
    h.assertTrue(
        forward.getHealth() < forward.getMaxHealth()
            && insideAngle.getHealth() < insideAngle.getMaxHealth()
            && insideRadius.getHealth() < insideRadius.getMaxHealth(),
        "The announced front sector reaches the inside of its radial and angular boundaries");
    h.assertTrue(
        rear.getHealth() == rear.getMaxHealth()
            && outsideAngle.getHealth() == outsideAngle.getMaxHealth()
            && outsideRadius.getHealth() == outsideRadius.getMaxHealth()
            && above.getHealth() == above.getMaxHealth(),
        "Rear, outside-angle, outside-radius, and upper-floor escape positions remain safe");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_expansion", timeoutTicks = 200)
  public static void eruptionKeepsFiveFixedMarksAndGapsAfterTargetLeaves(GameTestHelper h)
      throws Exception {
    var f = new Fixture(h);
    var aim = f.target(7.5, 7.5);
    f.boss.setTarget(aim);
    begin(f.boss, AimlessColony.Pattern.ERUPTION);
    var marks = List.copyOf(f.boss.eruptionCenters());
    h.assertTrue(
        marks.size() == 5 && marks.stream().distinct().count() == 5, "Five distinct marks");
    var victims = new ArrayList<CombatSlime>();
    for (var mark : marks) {
      h.assertTrue(
          mark.equals(f.boss.attackOrigin())
              || Math.abs(mark.distanceTo(f.boss.attackOrigin()) - 3.5) < 1E-5,
          "Outer marks stay 3.5 blocks from the announced center");
      var target = f.target(7.5, 7.5);
      target.moveTo(mark);
      victims.add(target);
    }
    var gap = f.target(9.25, 9.25);
    var insideEdge = f.target(7.5, 12.5);
    var outsideEdge = f.target(7.5, 12.7);
    var above = f.target(7.5, 103.6, 7.5);
    aim.moveTo(f.area.point(107.5, 101, 7.5));
    step(f.boss, AimlessColony.Pattern.ERUPTION.warning - 1);
    h.assertTrue(
        victims.stream().allMatch(v -> v.getHealth() == v.getMaxHealth()),
        "All five sites remain harmless throughout the warning");
    step(f.boss, 1);
    h.assertTrue(
        f.boss.eruptionCenters().equals(marks)
            && victims.stream().allMatch(v -> v.getHealth() < v.getMaxHealth())
            && insideEdge.getHealth() < insideEdge.getMaxHealth(),
        "The committed eruption stays on its original marks and reaches each marked disk");
    h.assertTrue(
        gap.getHealth() == gap.getMaxHealth()
            && outsideEdge.getHealth() == outsideEdge.getMaxHealth()
            && above.getHealth() == above.getMaxHealth()
            && aim.getHealth() == aim.getMaxHealth(),
        "Gaps, the outside edge, upper floors, and a departing target remain safe");
    for (var mark : marks)
      h.assertTrue(
          h.getLevel().getBlockState(BlockPos.containing(mark).below()).is(Blocks.BEDROCK)
              && h.getLevel().getBlockState(BlockPos.containing(mark)).isAir(),
          "Eruption leaves floor blocks and air unchanged");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_expansion", timeoutTicks = 200)
  public static void expansionStrikesRespectWallsForgeCancellationAndOneHitPerWarning(
      GameTestHelper h) throws Exception {
    for (var pattern : List.of(AimlessColony.Pattern.SWEEP, AimlessColony.Pattern.ERUPTION)) {
      var f = new Fixture(h);
      boolean sweep = pattern == AimlessColony.Pattern.SWEEP;
      var aim = f.target(7.5, sweep ? 12.5 : 7.5);
      var blocked = f.target(7.5, sweep ? 12.5 : 11);
      var canceled = f.target(sweep ? 11.5 : 11, sweep ? 9.5 : 7.5);
      var hit = f.target(sweep ? 3.5 : 4, sweep ? 9.5 : 7.5);
      for (int x = 6; x <= 8; x++)
        for (int y = 101; y <= 106; y++)
          h.getLevel()
              .setBlockAndUpdate(
                  BlockPos.containing(f.area.point(x, y, sweep ? 10 : 9)),
                  Blocks.STONE.defaultBlockState());
      var attempts = new AtomicInteger();
      Consumer<LivingAttackEvent> listener =
          event -> {
            if (event.getSource().getEntity() != f.boss) return;
            if (event.getEntity() == canceled) event.setCanceled(true);
            if (event.getEntity() == hit) attempts.incrementAndGet();
          };
      MinecraftForge.EVENT_BUS.addListener(listener);
      try {
        f.boss.setTarget(aim);
        begin(f.boss, pattern);
        step(
            f.boss,
            pattern.warning
                + (pattern == AimlessColony.Pattern.SWEEP
                    ? AimlessColony.Pattern.SWEEPING.warning
                    : 0));
        h.assertTrue(
            blocked.getHealth() == blocked.getMaxHealth()
                && canceled.getHealth() == canceled.getMaxHealth()
                && hit.getHealth() < hit.getMaxHealth(),
            "Walls and canceled damage are respected while an unobstructed target is struck: "
                + pattern);
        hit.invulnerableTime = 0;
        step(f.boss, 8);
        h.assertTrue(attempts.get() == 1, "A completed warning cannot repeat damage: " + pattern);
      } finally {
        MinecraftForge.EVENT_BUS.unregister(listener);
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_expansion", timeoutTicks = 200)
  public static void newStrikesRetainPlayerPermissionsAlliancesAndNoncombatExemptions(
      GameTestHelper h) throws Exception {
    for (var pattern : List.of(AimlessColony.Pattern.SWEEP, AimlessColony.Pattern.ERUPTION)) {
      var f = new Fixture(h);
      var target = f.target(7.5, 11);
      var ally = f.target(7.5, 11);
      var worker = ModEntities.MINING_SLIME.get().create(h.getLevel());
      worker.setOwnerUUID(UUID.randomUUID());
      worker.setTame(true);
      worker.setNoAi(true);
      worker.moveTo(target.position());
      h.getLevel().addFreshEntity(worker);
      var wild = EntityType.SLIME.create(h.getLevel());
      wild.setSize(1, true);
      wild.setNoAi(true);
      wild.moveTo(target.position());
      h.getLevel().addFreshEntity(wild);
      var board = h.getLevel().getScoreboard();
      var team = board.addPlayerTeam("colony_" + UUID.randomUUID().toString().substring(0, 8));
      board.addPlayerToTeam(f.boss.getScoreboardName(), team);
      board.addPlayerToTeam(ally.getScoreboardName(), team);
      var players = new ArrayList<ServerPlayer>();
      try {
        for (var mode : List.of(GameType.SURVIVAL, GameType.CREATIVE, GameType.SPECTATOR)) {
          var player =
              new ServerPlayer(
                  h.getLevel().getServer(),
                  h.getLevel(),
                  new GameProfile(UUID.randomUUID(), "Colony" + mode.getName()));
          var connection =
              new net.minecraft.network.Connection(
                  net.minecraft.network.protocol.PacketFlow.SERVERBOUND) {
                @Override
                public void send(net.minecraft.network.protocol.Packet<?> packet) {}
              };
          player.connection =
              new net.minecraft.server.network.ServerGamePacketListenerImpl(
                  h.getLevel().getServer(), connection, player);
          var spawnProtection = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
          spawnProtection.setAccessible(true);
          spawnProtection.setInt(player, 0);
          player.setGameMode(mode);
          player.moveTo(target.position());
          h.getLevel().addNewPlayer(player);
          players.add(player);
          h.assertTrue(
              f.boss.validTarget(player) == (mode == GameType.SURVIVAL),
              "The boss only accepts survival players, not Creative or spectator players");
        }
        f.boss.setTarget(target);
        begin(f.boss, pattern);
        step(
            f.boss,
            pattern.warning
                + (pattern == AimlessColony.Pattern.SWEEP
                    ? AimlessColony.Pattern.SWEEPING.warning
                    : 0));
        h.assertTrue(
            target.getHealth() < target.getMaxHealth()
                && players.get(0).getHealth() < players.get(0).getMaxHealth(),
            "Enemy fighters and survival players remain valid strike targets");
        for (LivingEntity protectedTarget :
            List.of(ally, worker, wild, players.get(1), players.get(2)))
          h.assertTrue(
              protectedTarget.getHealth() == protectedTarget.getMaxHealth(),
              "Allied fighters, noncombat companions, wild slimes, and exempt players remain safe");
      } finally {
        for (var player : players) player.discard();
        board.removePlayerTeam(team);
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_expansion", timeoutTicks = 200)
  public static void newPatternMarksAndDirectionsSurviveLateTrackingPackets(GameTestHelper h)
      throws Exception {
    for (var pattern : List.of(AimlessColony.Pattern.SWEEP, AimlessColony.Pattern.ERUPTION)) {
      var f = new Fixture(h);
      f.boss.setTarget(f.target(12.5, 12.5));
      begin(f.boss, pattern);
      step(f.boss, 3);
      var replica = ModEntities.AIMLESS_COLONY.get().create(h.getLevel());
      var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
      try {
        new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(
                f.boss.getId(), f.boss.getEntityData().getNonDefaultValues())
            .write(buffer);
        var packet = new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(buffer);
        replica.getEntityData().assignValues(packet.packedItems());
      } finally {
        buffer.release();
      }
      h.assertTrue(
          replica.pattern() == pattern
              && replica.patternTicks() == pattern.warning - 3
              && replica.attackOrigin().equals(f.boss.attackOrigin())
              && replica.dashDirection().distanceToSqr(f.boss.dashDirection()) < 1E-12
              && replica.eruptionCenters().equals(f.boss.eruptionCenters()),
          "Late tracking reproduces the same committed warning geometry and remaining time");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_expansion", timeoutTicks = 200)
  public static void reloadCancelsNewStrikesAndMovementWithoutRemovingTheRecoveryDelay(
      GameTestHelper h) throws Exception {
    for (var pattern : List.of(AimlessColony.Pattern.SWEEP, AimlessColony.Pattern.ERUPTION)) {
      var f = new Fixture(h);
      var target = f.target(7.5, 11);
      f.boss.setTarget(target);
      begin(f.boss, pattern);
      field(f.boss, "followUp", AimlessColony.Pattern.SWEEP);
      step(f.boss, pattern.warning - 1);
      f.boss.setDeltaMovement(.3, .1, -.4);
      var saved = f.boss.saveWithoutId(new CompoundTag());
      saved.putInt("ActionDelay", 1);
      var restored = ModEntities.AIMLESS_COLONY.get().create(h.getLevel());
      restored.load(saved);
      var followUp = AimlessColony.class.getDeclaredField("followUp");
      followUp.setAccessible(true);
      h.assertTrue(
          restored.pattern() == AimlessColony.Pattern.IDLE
              && restored.patternTicks() == 0
              && followUp.get(restored) == AimlessColony.Pattern.IDLE
              && restored.getDeltaMovement().horizontalDistanceSqr() == 0,
          "Reload clears a nearly finished strike, combo, and unannounced horizontal movement");
      var data = AssemblySites.get(h.getLevel());
      var site = data.register(h.getLevel(), restored.blockPosition());
      h.assertTrue(site != null && data.awaken(h.getLevel(), site), "Restored encounter reserved");
      site.state = AssemblySites.State.ACTIVE;
      restored.setUUID(site.boss);
      restored.bind(site);
      restored.setTarget(target);
      try {
        for (int tick = 1; tick < 20; tick++) {
          restored.tickCount = tick;
          idleAi(restored);
          h.assertTrue(
              restored.pattern() == AimlessColony.Pattern.IDLE
                  && target.getHealth() == target.getMaxHealth(),
              "Reload preserves at least twenty ticks before any new attack warning");
        }
      } finally {
        data.reset(h.getLevel(), site);
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_expansion", timeoutTicks = 200)
  public static void largeBodyChargesStopBeforeWallsCliffsFluidsAndLowCeilings(GameTestHelper h)
      throws Exception {
    for (String obstacle : List.of("wall", "cliff", "fluid", "ceiling", "clear")) {
      var f = new Fixture(h);
      f.boss.moveTo(f.area.point(7.5, 101, 7.1));
      f.boss.setTarget(f.target(7.5, 13));
      switch (obstacle) {
        case "wall" ->
            h.getLevel()
                .setBlockAndUpdate(
                    BlockPos.containing(f.area.point(7, 101, 9)), Blocks.STONE.defaultBlockState());
        case "cliff" ->
            h.getLevel()
                .setBlockAndUpdate(
                    BlockPos.containing(f.area.point(9, 100, 9)), Blocks.AIR.defaultBlockState());
        case "fluid" ->
            h.getLevel()
                .setBlockAndUpdate(
                    BlockPos.containing(f.area.point(9, 101, 9)), Blocks.LAVA.defaultBlockState());
        case "ceiling" ->
            h.getLevel()
                .setBlockAndUpdate(
                    BlockPos.containing(f.area.point(7, 103, 9)), Blocks.STONE.defaultBlockState());
        default -> {}
      }
      begin(f.boss, AimlessColony.Pattern.DASH);
      step(f.boss, AimlessColony.Pattern.DASH.warning);
      var before = f.boss.position();
      step(f.boss, 1);
      h.assertTrue(
          (f.boss.position().distanceToSqr(before) > .01) == obstacle.equals("clear"),
          "The full body may advance only onto supported, dry, unobstructed terrain: " + obstacle);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_expansion", timeoutTicks = 200)
  public static void attackSelectionVariesWithoutRepeatingAndPreservesItsLastChoiceOnReload(
      GameTestHelper h) throws Exception {
    var choose = AimlessColony.class.getDeclaredMethod("chooseAttack");
    choose.setAccessible(true);
    var f = new Fixture(h);
    var target = f.target(7.5, 11);
    for (float health : new float[] {1, .6F, .2F}) {
      for (double distance : new double[] {3, 8, 12}) {
        f.boss.setHealth(f.boss.getMaxHealth() * health);
        target.moveTo(f.boss.position().add(0, 0, distance));
        f.boss.setTarget(target);
        f.boss.getRandom().setSeed(113);
        var observed = new java.util.HashSet<AimlessColony.Pattern>();
        var previous = AimlessColony.Pattern.IDLE;
        for (int attempt = 0; attempt < 20; attempt++) {
          var next = (AimlessColony.Pattern) choose.invoke(f.boss);
          h.assertTrue(next != previous, "Distance-aware selection never repeats the last attack");
          begin(f.boss, next);
          observed.add(next);
          previous = next;
        }
        h.assertTrue(observed.size() >= 2, "Each health and distance band offers multiple attacks");
        var saved = f.boss.saveWithoutId(new CompoundTag());
        h.assertTrue(
            saved.getString("LastAttack").equals(previous.name()),
            "Last attack is persisted by name");
        var restored = ModEntities.AIMLESS_COLONY.get().create(h.getLevel());
        restored.load(saved);
        restored.setTarget(target);
        h.assertTrue(
            choose.invoke(restored) != previous, "Reload cannot repeat the last announced attack");
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_expansion", timeoutTicks = 200)
  public static void pullCannotMoveTargetsThroughAnOpaqueWall(GameTestHelper h) throws Exception {
    var f = new Fixture(h);
    var blocked = f.target(12.5, 7.5);
    var visible = f.target(7.5, 12.5);
    for (int y = 101; y <= 106; y++)
      for (int z = 6; z <= 8; z++)
        h.getLevel()
            .setBlockAndUpdate(
                BlockPos.containing(f.area.point(10, y, z)), Blocks.STONE.defaultBlockState());
    begin(f.boss, AimlessColony.Pattern.PULL);
    step(f.boss, AimlessColony.Pattern.PULL.warning);
    h.assertTrue(
        blocked.getDeltaMovement().horizontalDistanceSqr() == 0
            && visible.getDeltaMovement().horizontalDistanceSqr() > 0,
        "Opaque walls block the pull while visible targets still move");
    h.succeed();
  }
}
