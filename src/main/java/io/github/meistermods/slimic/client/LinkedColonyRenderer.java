package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.LinkedColony;
import io.github.meistermods.slimic.entity.LinkedColonyBody;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Each body is rendered by its own entity; only the representative draws their connecting cord. */
public class LinkedColonyRenderer extends FriendlySlimeRenderer {
  private static final int SEGMENTS = 24;
  private static final ResourceLocation CORD =
      ResourceLocation.fromNamespaceAndPath("slimic", "textures/entity/slime_cord.png");

  public LinkedColonyRenderer(EntityRendererProvider.Context context) {
    super(context);
  }

  @Nullable
  private static LinkedColonyBody<?> linked(FriendlySlime slime) {
    var colony = (LinkedColony) slime;
    var body = colony.getLinkedBody();
    return body != null
            && body.isAlive()
            && !body.isRemoved()
            && body.level() == slime.level()
            && body.getColony() == slime
            && !slime.isInvisible()
            && !body.isInvisible()
            && slime.distanceToSqr(body) <= Math.pow(colony.getLinkRange() + 2, 2)
            && slime.level().hasChunkAt(slime.blockPosition())
            && slime.level().hasChunkAt(body.blockPosition())
        ? body
        : null;
  }

  @Override
  public boolean shouldRender(
      FriendlySlime slime, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
    if (super.shouldRender(slime, frustum, cameraX, cameraY, cameraZ)) return true;
    var body = linked(slime);
    return body != null
        && frustum.isVisible(new AABB(slime.position(), body.position()).inflate(1));
  }

  @Override
  public void render(
      FriendlySlime slime,
      float yaw,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light) {
    super.render(slime, yaw, partial, pose, buffers, light);
    var body = linked(slime);
    if (body == null) return;
    Vec3 start = new Vec3(0, slime.getBbHeight() * .65, 0);
    Vec3 end =
        body.getPosition(partial)
            .subtract(slime.getPosition(partial))
            .add(0, body.getBbHeight() * .65, 0);
    Vec3 offset = end.subtract(start);
    double sag =
        Math.min(
            Math.min(.65, offset.length() * .14),
            Math.min(slime.getBbHeight(), body.getBbHeight()) * .48);
    Vec3 side = new Vec3(-offset.z, 0, offset.x);
    side = side.lengthSqr() < .0001 ? new Vec3(1, 0, 0) : side.normalize();
    Vec3 up = offset.normalize().cross(side).normalize();
    if (up.lengthSqr() < .0001) up = new Vec3(0, 0, 1);
    var level = slime.level();
    int startBlock = level.getBrightness(LightLayer.BLOCK, slime.blockPosition());
    int endBlock = level.getBrightness(LightLayer.BLOCK, body.blockPosition());
    int startSky = level.getBrightness(LightLayer.SKY, slime.blockPosition());
    int endSky = level.getBrightness(LightLayer.SKY, body.blockPosition());
    var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(CORD));
    Vec3[] rim = {side, up, side.scale(-1), up.scale(-1)};
    for (int segment = 0; segment < SEGMENTS; segment++) {
      for (int face = 0; face < 4; face++) {
        // Four faces make a soft, substantial strand visible from every viewing angle.
        for (int corner = 0; corner < 4; corner++) {
          float fraction = (segment + (corner == 1 || corner == 2 ? 1 : 0)) / (float) SEGMENTS;
          int edge = (face + (corner >= 2 ? 1 : 0)) % 4;
          double radius = .1 * (1 + .12 * Math.sin(fraction * Math.PI * 6));
          Vec3 point =
              start
                  .add(offset.scale(fraction))
                  .add(0, -4 * sag * fraction * (1 - fraction), 0)
                  .add(rim[edge].scale(radius));
          int packed =
              LightTexture.pack(
                  (int) Mth.lerp(fraction, startBlock, endBlock),
                  (int) Mth.lerp(fraction, startSky, endSky));
          Vec3 normal = rim[face].add(rim[(face + 1) % 4]).normalize();
          vertex(
              pose,
              vertices,
              point,
              packed,
              fraction * (float) offset.length(),
              corner >= 2 ? 1 : 0,
              normal);
        }
      }
    }
  }

  private static void vertex(
      PoseStack pose,
      VertexConsumer vertices,
      Vec3 point,
      int light,
      float u,
      float v,
      Vec3 normal) {
    vertices
        .vertex(pose.last().pose(), (float) point.x, (float) point.y, (float) point.z)
        .color(1F, 1F, 1F, 1F)
        .uv(u, v)
        .overlayCoords(OverlayTexture.NO_OVERLAY)
        .uv2(light)
        .normal(pose.last().normal(), (float) normal.x, (float) normal.y, (float) normal.z)
        .endVertex();
  }
}
