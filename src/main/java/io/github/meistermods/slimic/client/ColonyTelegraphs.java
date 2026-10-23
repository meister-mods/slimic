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
      if (pattern == AimlessColony.Pattern.IDLE) continue;
      double radius =
          pattern == AimlessColony.Pattern.SLAM
              ? 5.5
              : pattern == AimlessColony.Pattern.PULL ? 4 : 7;
      Vec3 origin = boss.position().add(0, .12, 0);
      if (pattern == AimlessColony.Pattern.DASH || pattern == AimlessColony.Pattern.DASHING) {
        Vec3 direction =
            Vec3.atCenterOf(boss.aim()).subtract(boss.position()).multiply(1, 0, 1).normalize();
        Vec3 side = new Vec3(-direction.z, 0, direction.x).scale(1.6);
        for (int sign : new int[] {-1, 1})
          line(
              pose,
              sink,
              origin.add(side.scale(sign)),
              origin.add(direction.scale(10)).add(side.scale(sign)),
              1,
              .65F,
              .15F);
      } else
        for (int segment = 0; segment < 64; segment++) {
          double a = segment * Math.PI / 32, b = (segment + 1) * Math.PI / 32;
          line(
              pose,
              sink,
              origin.add(Math.cos(a) * radius, 0, Math.sin(a) * radius),
              origin.add(Math.cos(b) * radius, 0, Math.sin(b) * radius),
              pattern == AimlessColony.Pattern.RALLY ? .7F : 1,
              .7F,
              .2F);
        }
    }
    buffer.endBatch(RenderType.lines());
    pose.popPose();
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
