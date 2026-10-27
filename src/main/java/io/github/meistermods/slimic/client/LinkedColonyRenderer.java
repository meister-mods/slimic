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
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Each body is rendered by its own entity; only the representative draws their connecting cord. */
public class LinkedColonyRenderer extends FriendlySlimeRenderer {
  private static final int SEGMENTS = 24;

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
    double sag = Math.min(.35, offset.length() * .06);
    Vec3 side = new Vec3(-offset.z, 0, offset.x);
    side = side.lengthSqr() < .0001 ? new Vec3(.025, 0, 0) : side.normalize().scale(.025);
    var level = slime.level();
    int startBlock = level.getBrightness(LightLayer.BLOCK, slime.blockPosition());
    int endBlock = level.getBrightness(LightLayer.BLOCK, body.blockPosition());
    int startSky = level.getBrightness(LightLayer.SKY, slime.blockPosition());
    int endSky = level.getBrightness(LightLayer.SKY, body.blockPosition());
    var vertices = buffers.getBuffer(RenderType.leash());
    for (int plane = 0; plane < 2; plane++) {
      Vec3 thickness = plane == 0 ? side : new Vec3(0, .025, 0);
      for (int step = 0; step <= SEGMENTS; step++) {
        int segment = plane == 0 ? step : SEGMENTS - step;
        float fraction = segment / (float) SEGMENTS;
        Vec3 point =
            start.add(offset.scale(fraction)).add(0, -4 * sag * fraction * (1 - fraction), 0);
        int packed =
            LightTexture.pack(
                (int) Mth.lerp(fraction, startBlock, endBlock),
                (int) Mth.lerp(fraction, startSky, endSky));
        float shade = segment % 2 == 0 ? .75F : 1;
        vertex(pose, vertices, point.subtract(thickness), packed, shade);
        vertex(pose, vertices, point.add(thickness), packed, shade);
      }
    }
  }

  private static void vertex(
      PoseStack pose, VertexConsumer vertices, Vec3 point, int light, float shade) {
    vertices
        .vertex(pose.last().pose(), (float) point.x, (float) point.y, (float) point.z)
        .color(.5F * shade, .375F * shade, .22F * shade, 1)
        .uv2(light)
        .endVertex();
  }
}
