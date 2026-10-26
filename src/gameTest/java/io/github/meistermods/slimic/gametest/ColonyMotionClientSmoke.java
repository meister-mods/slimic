package io.github.meistermods.slimic.gametest;

import com.google.gson.GsonBuilder;
import io.github.meistermods.slimic.client.AimlessColonyModel;
import io.github.meistermods.slimic.entity.AimlessColony;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.world.AssemblySites;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Native tracking and renderer checks for the full wind-up, strike and settling poses. */
public final class ColonyMotionClientSmoke {
  private record Pose(String name, AimlessColony.Pattern pattern, int steps, int sign) {}

  private static final List<Pose> POSES =
      List.of(
          new Pose("idle", AimlessColony.Pattern.IDLE, 0, 1),
          new Pose("charge-coil", AimlessColony.Pattern.DASH, 16, 1),
          new Pose("charge-launch", AimlessColony.Pattern.DASH, 21, 1),
          new Pose("charge-stretch", AimlessColony.Pattern.DASH, 26, 1),
          new Pose("charge-brake", AimlessColony.Pattern.DASH, 32, 1),
          new Pose("charge-settle", AimlessColony.Pattern.DASH, 38, 1),
          new Pose("sweep-right-coil", AimlessColony.Pattern.SWEEP, 28, 1),
          new Pose("sweep-right-start", AimlessColony.Pattern.SWEEP, 34, 1),
          new Pose("sweep-right-middle", AimlessColony.Pattern.SWEEP, 37, 1),
          new Pose("sweep-right-end", AimlessColony.Pattern.SWEEP, 41, 1),
          new Pose("sweep-right-settle", AimlessColony.Pattern.SWEEP, 48, 1),
          new Pose("sweep-left-coil", AimlessColony.Pattern.SWEEP, 28, -1),
          new Pose("sweep-left-start", AimlessColony.Pattern.SWEEP, 34, -1),
          new Pose("sweep-left-middle", AimlessColony.Pattern.SWEEP, 37, -1),
          new Pose("sweep-left-end", AimlessColony.Pattern.SWEEP, 41, -1),
          new Pose("slam-rise", AimlessColony.Pattern.SLAM, 18, 1),
          new Pose("pull-contract", AimlessColony.Pattern.PULL, 18, 1),
          new Pose("split-spread", AimlessColony.Pattern.SPLIT, 14, 1),
          new Pose("rally-pulse", AimlessColony.Pattern.RALLY, 35, 1),
          new Pose("ring-expand", AimlessColony.Pattern.RING, 23, 1),
          new Pose("cross-tense", AimlessColony.Pattern.CROSS, 25, 1),
          new Pose("eruption-shiver", AimlessColony.Pattern.ERUPTION, 31, 1));
  private static final List<Map<String, Object>> measurements = new ArrayList<>();
  private static CompletableFuture<?> action;
  private static int phase, sample, ticks, bossId;
  private static AimlessColony serverBoss;
  private static volatile AimlessColony.Pattern expected;
  private static volatile int expectedTicks;
  private static final Vec3 HOME = new Vec3(24.5, 101, 24.5);

  private ColonyMotionClientSmoke() {}

  private static void require(boolean valid, String message) {
    if (!valid) throw new IllegalStateException(message);
  }

  @SuppressWarnings("unchecked")
  private static void stage(Minecraft client) {
    action =
        client
            .getSingleplayerServer()
            .submit(
                () -> {
                  try {
                    var pose = POSES.get(sample);
                    serverBoss.moveTo(HOME);
                    serverBoss.setDeltaMovement(Vec3.ZERO);
                    var target = ModEntities.COMBAT_SLIME.get().create(serverBoss.level());
                    target.moveTo(HOME.add(0, 0, 7));
                    serverBoss.setTarget(target);
                    var begin =
                        AimlessColony.class.getDeclaredMethod("begin", AimlessColony.Pattern.class);
                    begin.setAccessible(true);
                    begin.invoke(serverBoss, pose.pattern);
                    var sign = AimlessColony.class.getDeclaredField("SWEEP_SIGN");
                    sign.setAccessible(true);
                    serverBoss
                        .getEntityData()
                        .set((EntityDataAccessor<Integer>) sign.get(null), pose.sign);
                    var step =
                        AimlessColony.class.getDeclaredMethod(
                            "patternStep", ServerLevel.class, AssemblySites.Site.class);
                    step.setAccessible(true);
                    for (int i = 0; i < pose.steps; i++)
                      step.invoke(serverBoss, serverBoss.level(), null);
                    serverBoss.hasImpulse = true;
                    expected = serverBoss.pattern();
                    expectedTicks = serverBoss.patternTicks();
                  } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException(failure);
                  }
                });
    ticks = 0;
    phase = 2;
  }

  private static List<Float> transform(ModelPart part) {
    return List.of(
        part.x,
        part.y,
        part.z,
        part.xRot,
        part.yRot,
        part.zRot,
        part.xScale,
        part.yScale,
        part.zScale);
  }

  public static boolean tick(Minecraft client) {
    if (phase == 5) return true;
    if (action != null && !action.isDone()) return false;
    if (action != null) {
      action.join();
      action = null;
    }
    if (phase == 0) {
      client.setScreen(null);
      client.options.particles().set(ParticleStatus.MINIMAL);
      client.options.hideGui = true;
      action =
          client
              .getSingleplayerServer()
              .submit(
                  () -> {
                    var player =
                        client
                            .getSingleplayerServer()
                            .getPlayerList()
                            .getPlayer(client.player.getUUID());
                    var level = player.serverLevel();
                    player.setGameMode(GameType.CREATIVE);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    for (int x = 8; x < 48; x++)
                      for (int z = 8; z < 48; z++) {
                        level.setBlock(
                            new BlockPos(x, 100, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                        for (int y = 101; y < 112; y++)
                          level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                      }
                    level.setDayTime(6000);
                    level.setWeatherParameters(10000, 0, false, false);
                    player.teleportTo(level, 38.5, 106, 39.5, 135, 15);
                    serverBoss = ModEntities.AIMLESS_COLONY.get().create(level);
                    serverBoss.setNoAi(true);
                    serverBoss.setNoGravity(true);
                    serverBoss.moveTo(HOME);
                    require(
                        level.addFreshEntity(serverBoss), "A real boss is tracked by the client");
                    bossId = serverBoss.getId();
                  });
      phase = 1;
      return false;
    }
    if (phase == 1) {
      if (!(client.level.getEntity(bossId) instanceof AimlessColony)) return false;
      stage(client);
      return false;
    }
    if (phase == 2) {
      if (++ticks < 12) return false;
      var boss = (AimlessColony) client.level.getEntity(bossId);
      require(
          boss != null && boss.pattern() == expected && boss.patternTicks() == expectedTicks,
          "Native packets carry the staged motion and remaining time");
      if (POSES.get(sample).pattern == AimlessColony.Pattern.SWEEP)
        require(
            boss.sweepSign() == POSES.get(sample).sign, "Both swing directions are synchronized");
      Vec3 look = boss.position().add(0, 1.1, 0).subtract(client.player.getEyePosition());
      client.player.setYRot((float) (Math.toDegrees(Math.atan2(look.z, look.x)) - 90));
      client.player.setXRot((float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance())));
      phase = 3;
      ticks = 0;
      return false;
    }
    if (phase == 3) {
      if (++ticks < 3) return false;
      try {
        var boss = (AimlessColony) client.level.getEntity(bossId);
        var root = client.getEntityModels().bakeLayer(AimlessColonyModel.BODY);
        var model = new AimlessColonyModel(root);
        model.setupAnim(boss, 0, 0, boss.tickCount + .5F, 0, 0);
        var poses = new LinkedHashMap<String, List<Float>>();
        for (String name :
            List.of(
                "middle",
                "head",
                "crown",
                "left",
                "right",
                "tail",
                "front_sprawl",
                "rear_sprawl")) {
          var shell = root.getChild("shell").getChild(name);
          var features = root.getChild("features").getChild(name);
          require(
              transform(shell).equals(transform(features)),
              "Eyes and cores follow the deforming shell: " + name);
          require(
              transform(shell).stream().allMatch(Float::isFinite)
                  && shell.xScale > .5
                  && shell.yScale > .5
                  && shell.zScale > .5,
              "Every staged lobe has a finite, noncollapsed transform");
          poses.put(name, transform(shell));
        }
        model.setupAnim(boss, 0, 0, boss.tickCount + .5F, 0, 0);
        require(
            poses.get("head").equals(transform(root.getChild("shell").getChild("head"))),
            "Repeated frames reset their transforms rather than accumulating motion");
        var directory = client.gameDirectory.toPath().resolve("colony-motion-audit");
        Files.createDirectories(directory);
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
          image.writeToFile(directory.resolve(POSES.get(sample).name + ".png"));
        }
        measurements.add(
            Map.of(
                "pose",
                POSES.get(sample).name,
                "pattern",
                boss.pattern().name(),
                "timer",
                boss.patternTicks(),
                "lobes",
                poses));
        if (++sample < POSES.size()) {
          stage(client);
          return false;
        }
        Files.writeString(
            directory.resolve("poses.json"),
            new GsonBuilder().setPrettyPrinting().create().toJson(measurements));
        client.options.hideGui = false;
        action = client.getSingleplayerServer().submit(() -> serverBoss.discard());
        phase = 4;
        return false;
      } catch (java.io.IOException failure) {
        throw new IllegalStateException(failure);
      }
    }
    phase = 5;
    System.out.println(
        "SLIMIC COLONY MOTION CLIENT SMOKE PASSED: "
            + measurements.size()
            + " native poses, both sweep directions, minimal-particle warnings and shell/feature"
            + " alignment");
    return true;
  }
}
