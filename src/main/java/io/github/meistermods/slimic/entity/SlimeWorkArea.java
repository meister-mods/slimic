package io.github.meistermods.slimic.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A small, read-only snapshot; requesting a preview never surveys or loads terrain. */
public record SlimeWorkArea(boolean planned, List<Shape> shapes) {
  public static final int MAX_SHAPES = 8;

  public record Shape(AABB bounds, boolean radial, boolean next) {
    public Shape {
      for (double value :
          new double[] {
            bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ
          })
        if (!Double.isFinite(value) || Math.abs(value) > 30_100_000)
          throw new IllegalArgumentException("Invalid work-area coordinate");
      if (bounds.getXsize() > 65536 || bounds.getYsize() > 65536 || bounds.getZsize() > 65536)
        throw new IllegalArgumentException("Oversized work area");
    }
  }

  public SlimeWorkArea {
    shapes = List.copyOf(shapes);
    if (shapes.size() > MAX_SHAPES) throw new IllegalArgumentException("Too many work-area shapes");
  }

  private static Shape range(Vec3 center, double radius) {
    return new Shape(new AABB(center, center).inflate(Math.max(0, radius)), true, false);
  }

  private static AABB tunnelPlane(TunnelSlime tunnel, BlockPos front) {
    var side = tunnel.getTunnelDirection().getClockWise();
    var first = front.relative(side, tunnel.getTunnelMinWidth());
    var last =
        front
            .relative(side, tunnel.getTunnelMinWidth() + tunnel.getTunnelSize() - 1)
            .above(tunnel.getTunnelSize() - 1);
    return new AABB(
        Math.min(first.getX(), last.getX()),
        first.getY(),
        Math.min(first.getZ(), last.getZ()),
        Math.max(first.getX(), last.getX()) + 1,
        last.getY() + 1,
        Math.max(first.getZ(), last.getZ()) + 1);
  }

  public static SlimeWorkArea of(FriendlySlime slime) {
    List<Shape> shapes = new ArrayList<>();
    double radius = SlimeStat.DETECTION_RANGE.value(slime);
    if (slime instanceof TunnelSlime tunnel) {
      var front = tunnel.getTunnelFront();
      var direction = tunnel.getTunnelDirection();
      var plane = tunnelPlane(tunnel, front);
      shapes.add(
          new Shape(plane.minmax(tunnelPlane(tunnel, front.relative(direction, 7))), false, false));
      shapes.add(new Shape(plane, false, true));
    } else if (slime instanceof MiningSlime miner) {
      var plan = miner.getQuarryPlan();
      boolean existingPlan =
          plan != null
              && (miner.getMiningChunk().equals(slime.chunkPosition()) || slime.isWorking());
      ChunkPos chunk = existingPlan ? miner.getMiningChunk() : slime.chunkPosition();
      if (miner instanceof NextGenerationMiningSlime next
          && next.policy() == NextGenerationMiningSlime.Policy.PRECISION_PROSPECTING) {
        int boundedRange = PrecisionProspecting.radius(slime);
        var shaft = next.prospecting().shaftFront();
        if (shaft == null)
          shaft = BlockPos.containing(slime.getX() - .5, slime.getY(), slime.getZ() - .5);
        int feet =
            Math.max(
                slime.level().getMinBuildHeight() + 1,
                Math.min(slime.level().getMaxBuildHeight() - 3, shaft.getY()));
        shapes.add(
            new Shape(
                new AABB(
                    shaft.getX(),
                    slime.level().getMinBuildHeight(),
                    shaft.getZ(),
                    shaft.getX() + 2,
                    feet + 2,
                    shaft.getZ() + 2),
                false,
                false));
        shapes.add(
            new Shape(
                new AABB(
                    slime.getX() - boundedRange,
                    Math.max(slime.level().getMinBuildHeight(), slime.getY() - boundedRange),
                    slime.getZ() - boundedRange,
                    slime.getX() + boundedRange,
                    Math.min(slime.level().getMaxBuildHeight(), slime.getY() + boundedRange + 1),
                    slime.getZ() + boundedRange),
                false,
                false));
        var front = next.prospecting().nextCell();
        if (front != null)
          shapes.add(new Shape(new AABB(front, front.offset(2, 2, 2)), false, true));
        return new SlimeWorkArea(!slime.isWorking(), shapes);
      }
      int topFloor = existingPlan ? plan.topFloor() : miner.getQuarryStartFloor();
      int nextLayer = existingPlan ? plan.layer() : topFloor + 2;
      int layer = nextLayer;
      layer =
          Math.max(
              slime.level().getMinBuildHeight(),
              Math.min(slime.level().getMaxBuildHeight() - 1, layer));
      int top = Math.max(layer + 1, topFloor + 3);
      shapes.add(
          new Shape(
              new AABB(
                  chunk.getMinBlockX(),
                  slime.level().getMinBuildHeight(),
                  chunk.getMinBlockZ(),
                  chunk.getMinBlockX() + 16,
                  top,
                  chunk.getMinBlockZ() + 16),
              false,
              false));
      if (nextLayer >= slime.level().getMinBuildHeight())
        shapes.add(
            new Shape(
                new AABB(
                    chunk.getMinBlockX(),
                    layer,
                    chunk.getMinBlockZ(),
                    chunk.getMinBlockX() + 16,
                    layer + 1,
                    chunk.getMinBlockZ() + 16),
                false,
                true));
    } else if (slime instanceof FieldSlime field) {
      BlockPos center = field.getPreviewWorkHome();
      if (slime instanceof FarmingSlime && !slime.isWorking()) center = slime.blockPosition();
      shapes.add(range(Vec3.atCenterOf(center), radius));
      if (slime instanceof FarmingSlime farming && slime.isWorking()) {
        AABB plots = null;
        for (var plot : farming.getFarmPlots())
          plots = plots == null ? new AABB(plot) : plots.minmax(new AABB(plot));
        if (plots != null) shapes.add(new Shape(plots, false, true));
      }
      if (slime.isWorking() && field.getWorkTarget() != null)
        shapes.add(new Shape(new AABB(field.getWorkTarget()), false, true));
    } else if (slime instanceof CleaningSlime cleaner) {
      shapes.add(range(cleaner.getCleaningHome(), radius));
    } else if (slime instanceof TransportSlime transport) {
      shapes.add(range(Vec3.atCenterOf(transport.getWorkOrigin()), radius));
    } else if (slime instanceof HealingSlime healer) {
      shapes.add(range(Vec3.atCenterOf(healer.getWorkOrigin()), radius));
    } else if (slime instanceof FishingSlime fishing) {
      shapes.add(range(Vec3.atCenterOf(fishing.getWorkOrigin()), radius));
      if (slime.isWorking() && fishing.getFishingWater() != null)
        shapes.add(new Shape(new AABB(fishing.getFishingWater()), false, true));
    } else if (slime instanceof GuardianAngelSlime) {
      shapes.add(range(slime.position(), Math.min(radius, 4 * SlimeStat.REACH.value(slime))));
    } else if (slime instanceof RoyalGuardSlime guard) {
      shapes.add(
          range(
              Vec3.atCenterOf(slime.isWorking() ? guard.getGuardPost() : slime.blockPosition()),
              radius));
      shapes.add(range(slime.position(), 4));
    } else if (slime instanceof SignalingSlime || slime instanceof CombatSlime) {
      shapes.add(range(slime.position(), radius));
    } else if (slime instanceof SoggySlime soggy) {
      shapes.add(
          range(
              Vec3.atCenterOf(soggy.isRoaming() ? soggy.getRoamHome() : slime.blockPosition()),
              radius));
    }
    return new SlimeWorkArea(!slime.isWorking(), shapes);
  }
}
