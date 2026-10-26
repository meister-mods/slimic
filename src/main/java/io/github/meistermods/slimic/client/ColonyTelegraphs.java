package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.*;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.AimlessColony;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** World outlines and action-bar warnings stay visible when particles are reduced. */
@Mod.EventBusSubscriber(modid = Slimic.MOD_ID, value = Dist.CLIENT)
public final class ColonyTelegraphs {
  private ColonyTelegraphs() {}

  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null) return;
    PoseStack pose = event.getPoseStack();
    Vec3 camera = event.getCamera().getPosition();
    pose.pushPose();
    pose.translate(-camera.x, -camera.y, -camera.z);
    var buffer = mc.renderBuffers().bufferSource();
    VertexConsumer sink = buffer.getBuffer(RenderType.lines());
    for (var boss :
        mc.level.getEntitiesOfClass(AimlessColony.class, mc.player.getBoundingBox().inflate(64))) {
      var pattern = boss.pattern();
      if (pattern == AimlessColony.Pattern.IDLE || pattern == AimlessColony.Pattern.RECOVERY)
        continue;
      double radius =
          pattern == AimlessColony.Pattern.SLAM
              ? 5.5
              : pattern == AimlessColony.Pattern.PULL
                  ? 4
                  : pattern == AimlessColony.Pattern.RING ? 8 : 7;
      Vec3 origin = boss.position().add(0, .12, 0);
      if (pattern == AimlessColony.Pattern.DASH || pattern == AimlessColony.Pattern.DASHING) {
        Vec3 direction = boss.dashDirection();
        double halfWidth = boss.dashHalfWidth();
        Vec3 side = new Vec3(-direction.z, 0, direction.x).scale(halfWidth);
        Vec3 rear = origin.subtract(direction.scale(halfWidth));
        Vec3 front = origin.add(direction.scale(boss.dashRemainingDistance() + halfWidth));
        for (int sign : new int[] {-1, 1})
          line(pose, sink, rear.add(side.scale(sign)), front.add(side.scale(sign)), 1, .65F, .15F);
        line(pose, sink, rear.subtract(side), rear.add(side), 1, .65F, .15F);
        line(pose, sink, front.subtract(side), front.add(side), 1, .65F, .15F);
      } else if (pattern == AimlessColony.Pattern.SWEEP
          || pattern == AimlessColony.Pattern.SWEEPING) {
        Vec3 center = boss.attackOrigin().add(0, .12, 0);
        Vec3 direction = boss.dashDirection();
        double angle = Math.atan2(direction.z, direction.x);
        double progress = boss.sweepProgress(event.getPartialTick());
        double from = angle - boss.sweepAngle(progress);
        double to = angle - boss.sweepAngle(1);
        double reach = AimlessColony.SWEEP_RADIUS;
        arc(pose, sink, center, reach, from, to, 40, 1, .5F, .18F);
        line(pose, sink, center, radial(center, reach, to), 1, .5F, .18F);
        line(pose, sink, center, radial(center, reach, from), 1, .9F, .5F);
      } else if (pattern == AimlessColony.Pattern.ERUPTION) {
        float progress =
            1
                - net.minecraft.util.Mth.clamp(
                    (boss.patternTicks() - event.getPartialTick()) / pattern.warning, 0, 1);
        for (Vec3 point : boss.eruptionCenters()) {
          Vec3 center = point.add(0, .12, 0);
          circle(pose, sink, center, AimlessColony.ERUPTION_RADIUS, .9F, .65F, .2F);
          for (int side = 0; side < 4; side++) {
            Vec3 edge = radial(center, AimlessColony.ERUPTION_RADIUS, side * Math.PI / 2);
            line(
                pose,
                sink,
                edge,
                edge.add(0, .3 + progress * (AimlessColony.ERUPTION_VERTICAL_REACH - .3), 0),
                .9F,
                .65F,
                .2F);
          }
        }
      } else if (pattern == AimlessColony.Pattern.CROSS) {
        Vec3 center = Vec3.atBottomCenterOf(boss.aim()).add(0, .12, 0);
        for (int axis = 0; axis < 2; axis++)
          for (int sign : new int[] {-1, 1}) {
            Vec3 a = axis == 0 ? new Vec3(-8, 0, sign * 1.25) : new Vec3(sign * 1.25, 0, -8);
            Vec3 b = axis == 0 ? new Vec3(8, 0, sign * 1.25) : new Vec3(sign * 1.25, 0, 8);
            line(pose, sink, center.add(a), center.add(b), 1, .4F, .2F);
            Vec3 endA = axis == 0 ? new Vec3(sign * 8, 0, -1.25) : new Vec3(-1.25, 0, sign * 8);
            Vec3 endB = axis == 0 ? new Vec3(sign * 8, 0, 1.25) : new Vec3(1.25, 0, sign * 8);
            line(pose, sink, center.add(endA), center.add(endB), 1, .4F, .2F);
          }
      } else {
        if (pattern == AimlessColony.Pattern.RING) circle(pose, sink, origin, 3.5, .4F, 1, .5F);
        circle(
            pose, sink, origin, radius, pattern == AimlessColony.Pattern.RALLY ? .7F : 1, .7F, .2F);
      }
    }
    buffer.endBatch(RenderType.lines());
    pose.popPose();
  }

  private static void circle(
      PoseStack pose,
      VertexConsumer sink,
      Vec3 origin,
      double radius,
      float r,
      float g,
      float blue) {
    arc(pose, sink, origin, radius, 0, Math.PI * 2, 64, r, g, blue);
  }

  private static Vec3 radial(Vec3 origin, double radius, double angle) {
    return origin.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
  }

  private static void arc(
      PoseStack pose,
      VertexConsumer sink,
      Vec3 origin,
      double radius,
      double from,
      double to,
      int segments,
      float r,
      float g,
      float blue) {
    for (int segment = 0; segment < segments; segment++) {
      double a = from + (to - from) * segment / segments;
      double b = from + (to - from) * (segment + 1) / segments;
      line(pose, sink, radial(origin, radius, a), radial(origin, radius, b), r, g, blue);
    }
  }

  private static void line(
      PoseStack pose, VertexConsumer sink, Vec3 a, Vec3 b, float r, float g, float blue) {
    Vec3 normal = b.subtract(a).normalize();
    var p = pose.last();
    sink.vertex(p.pose(), (float) a.x, (float) a.y, (float) a.z)
        .color(r, g, blue, 1)
        .normal(p.normal(), (float) normal.x, (float) normal.y, (float) normal.z)
        .endVertex();
    sink.vertex(p.pose(), (float) b.x, (float) b.y, (float) b.z)
        .color(r, g, blue, 1)
        .normal(p.normal(), (float) normal.x, (float) normal.y, (float) normal.z)
        .endVertex();
  }
}
