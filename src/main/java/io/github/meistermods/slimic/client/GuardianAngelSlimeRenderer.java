package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.*;
import io.github.meistermods.slimic.entity.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

public final class GuardianAngelSlimeRenderer extends FriendlySlimeRenderer {
  public GuardianAngelSlimeRenderer(EntityRendererProvider.Context context) {
    super(context);
  }

  @Override
  public boolean shouldRender(
      FriendlySlime slime, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
    if (super.shouldRender(slime, frustum, cameraX, cameraY, cameraZ)) return true;
    var guardian = (GuardianAngelSlime) slime;
    return slime.level().getEntity(guardian.getBeamTargetId()) instanceof LivingEntity target
        && target.isAlive()
        && frustum.isVisible(
            new AABB(slime.getEyePosition(), target.getBoundingBox().getCenter()).inflate(.1));
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
    var guardian = (GuardianAngelSlime) slime;
    if (guardian.getBeamTargetId() < 0) return;
    if (slime.level().getEntity(guardian.getBeamTargetId()) instanceof LivingEntity target
        && target.isAlive()) {
      var end =
          target
              .getPosition(partial)
              .add(0, target.getBbHeight() * .5, 0)
              .subtract(slime.getPosition(partial));
      float eye = slime.getEyeHeight();
      var consumer = buffers.getBuffer(RenderType.lightning());
      var matrix = pose.last().pose();
      for (int plane = 0; plane < 2; plane++) {
        // Lightning culls back faces. Emit both windings for each crossed ribbon.
        for (int side = -1; side <= 1; side += 2) {
          float x = plane == 0 ? .06F * side : 0, y = plane == 1 ? .06F * side : 0;
          consumer.vertex(matrix, -x, eye - y, 0).color(255, 245, 150, 220).endVertex();
          consumer
              .vertex(matrix, (float) end.x - x, (float) end.y - y, (float) end.z)
              .color(255, 245, 150, 220)
              .endVertex();
          consumer
              .vertex(matrix, (float) end.x + x, (float) end.y + y, (float) end.z)
              .color(255, 245, 150, 220)
              .endVertex();
          consumer.vertex(matrix, x, eye + y, 0).color(255, 245, 150, 220).endVertex();
        }
      }
    }
  }
}
