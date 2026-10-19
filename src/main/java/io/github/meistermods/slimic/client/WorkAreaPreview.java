package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.item.SlimeScannerItem;
import io.github.meistermods.slimic.network.WorkAreaPacket;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Slimic.MOD_ID, value = Dist.CLIENT)
public final class WorkAreaPreview {
  private static WorkAreaPacket current;
  private static int remainingTicks;

  private WorkAreaPreview() {}

  public static void receive(WorkAreaPacket packet) {
    current = packet.area().shapes().isEmpty() ? null : packet;
    remainingTicks = 300;
  }

  @SubscribeEvent
  public static void tick(TickEvent.ClientTickEvent event) {
    if (event.phase != TickEvent.Phase.END || current == null) return;
    var client = Minecraft.getInstance();
    if (client.level == null
        || client.player == null
        || !client.level.dimension().location().equals(current.dimension())
        || !SlimeScannerItem.isHolding(client.player)
        || !(client.level.getEntity(current.entityId()) instanceof FriendlySlime slime)
        || !slime.getUUID().equals(current.companion())
        || !slime.isAlive()
        || !slime.canManage(client.player)
        || (!client.isPaused() && --remainingTicks <= 0)) current = null;
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || current == null) return;
    var client = Minecraft.getInstance();
    if (client.level == null
        || client.options.hideGui
        || !client.level.dimension().location().equals(current.dimension())) return;
    var pose = event.getPoseStack();
    var camera = event.getCamera().getPosition();
    var buffers = client.renderBuffers().bufferSource();
    var vertices = buffers.getBuffer(PreviewLines.TYPE);
    pose.pushPose();
    for (var shape : current.area().shapes()) {
      float r = shape.next() ? 1 : .15F;
      float g = shape.next() ? .75F : .9F;
      float b = shape.next() ? .15F : 1;
      var bounds = shape.bounds().move(-camera.x, -camera.y, -camera.z);
      if (shape.radial()) rings(pose, vertices, bounds, r, g, b);
      else LevelRenderer.renderLineBox(pose, vertices, bounds, r, g, b, .8F);
    }
    pose.popPose();
    buffers.endBatch(PreviewLines.TYPE);
  }

  private static void rings(
      PoseStack pose, VertexConsumer vertices, AABB box, float r, float g, float b) {
    Vec3 center = box.getCenter();
    double radius = box.getXsize() / 2;
    for (int plane = 0; plane < 3; plane++)
      for (int step = 0; step < 64; step++) {
        double a = step * Math.PI / 32, z = (step + 1) * Math.PI / 32;
        double x1 = Math.cos(a) * radius, y1 = Math.sin(a) * radius;
        double x2 = Math.cos(z) * radius, y2 = Math.sin(z) * radius;
        Vec3 from =
            center.add(
                plane == 2 ? 0 : x1,
                plane == 0 ? 0 : plane == 1 ? y1 : x1,
                plane == 0 ? y1 : plane == 1 ? 0 : y1);
        Vec3 to =
            center.add(
                plane == 2 ? 0 : x2,
                plane == 0 ? 0 : plane == 1 ? y2 : x2,
                plane == 0 ? y2 : plane == 1 ? 0 : y2);
        Vec3 normal = to.subtract(from).normalize();
        for (Vec3 point : new Vec3[] {from, to})
          vertices
              .vertex(pose.last().pose(), (float) point.x, (float) point.y, (float) point.z)
              .color(r, g, b, .65F)
              .normal(pose.last().normal(), (float) normal.x, (float) normal.y, (float) normal.z)
              .endVertex();
      }
  }

  private static final class PreviewLines extends RenderType {
    private static final RenderType TYPE =
        create(
            "slimic_work_area",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            4096,
            false,
            false,
            CompositeState.builder()
                .setShaderState(RENDERTYPE_LINES_SHADER)
                .setLineState(new LineStateShard(OptionalDouble.of(2)))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(NO_DEPTH_TEST)
                .setWriteMaskState(COLOR_WRITE)
                .setCullState(NO_CULL)
                .createCompositeState(false));

    private PreviewLines(
        String name,
        VertexFormat format,
        VertexFormat.Mode mode,
        int size,
        boolean crumbling,
        boolean sort,
        Runnable setup,
        Runnable clear) {
      super(name, format, mode, size, crumbling, sort, setup, clear);
    }
  }
}
