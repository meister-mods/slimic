package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.HeraldSlime;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class HeraldSlimeLayer extends RenderLayer<FriendlySlime, SlimeModel<FriendlySlime>> {
  private static final ResourceLocation TEXTURE =
      ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "textures/entity/herald_hat.png");
  private static final float RADIUS = 0.27F;
  private static final float BASE = 1.0F;
  private static final float TIP = 0.62F;

  public HeraldSlimeLayer(RenderLayerParent<FriendlySlime, SlimeModel<FriendlySlime>> parent) {
    super(parent);
  }

  @Override
  public void render(
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      FriendlySlime slime,
      float limbSwing,
      float limbAmount,
      float partialTick,
      float age,
      float headYaw,
      float headPitch) {
    if (!(slime instanceof HeraldSlime) || slime.isInvisible()) return;
    VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
    for (int side = 0; side < 8; side++) {
      double a = side * Math.PI / 4, b = (side + 1) * Math.PI / 4;
      float nx = (float) Math.cos((a + b) / 2);
      float nz = (float) Math.sin((a + b) / 2);
      float ny = -RADIUS / (BASE - TIP);
      float length = (float) Math.sqrt(1 + ny * ny);
      nx /= length;
      ny /= length;
      nz /= length;
      float u0 = side / 8F, u1 = (side + 1) / 8F;
      vertex(
          vertices,
          pose.last(),
          light,
          (float) Math.cos(a) * RADIUS,
          BASE,
          (float) Math.sin(a) * RADIUS,
          u0,
          1,
          nx,
          ny,
          nz);
      vertex(vertices, pose.last(), light, 0, TIP, 0, (u0 + u1) / 2, 0, nx, ny, nz);
      vertex(vertices, pose.last(), light, 0, TIP, 0, (u0 + u1) / 2, 0, nx, ny, nz);
      vertex(
          vertices,
          pose.last(),
          light,
          (float) Math.cos(b) * RADIUS,
          BASE,
          (float) Math.sin(b) * RADIUS,
          u1,
          1,
          nx,
          ny,
          nz);
    }
  }

  private static void vertex(
      VertexConsumer vertices,
      PoseStack.Pose pose,
      int light,
      float x,
      float y,
      float z,
      float u,
      float v,
      float nx,
      float ny,
      float nz) {
    vertices
        .vertex(pose.pose(), x, y, z)
        .color(255, 255, 255, 255)
        .uv(u, v)
        .overlayCoords(OverlayTexture.NO_OVERLAY)
        .uv2(light)
        .normal(pose.normal(), nx, ny, nz)
        .endVertex();
  }
}
