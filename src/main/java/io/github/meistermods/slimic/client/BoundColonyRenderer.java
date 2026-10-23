package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.meistermods.slimic.entity.FriendlySlime;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;

/**
 * Overlapping gel bodies share one position and order, while retaining each member's appearance.
 */
public final class BoundColonyRenderer extends FriendlySlimeRenderer {
  public BoundColonyRenderer(EntityRendererProvider.Context context) {
    super(context);
    shadowRadius = .8F;
  }

  @Override
  public void render(
      FriendlySlime slime,
      float yaw,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light) {
    for (int member = 0; member < 2; member++) {
      pose.pushPose();
      pose.translate(member == 0 ? -.3 : .3, 0, 0);
      slime.setRenderingSecondMember(member == 1);
      try {
        super.render(slime, yaw, partial, pose, buffers, light);
      } finally {
        slime.setRenderingSecondMember(false);
        pose.popPose();
      }
    }
  }

  @Override
  protected void renderNameTag(
      FriendlySlime slime, Component name, PoseStack pose, MultiBufferSource buffers, int light) {
    if (slime.isRenderingSecondMember()) return;
    pose.pushPose();
    pose.translate(.3, 0, 0);
    super.renderNameTag(slime, name, pose, buffers, light);
    pose.popPose();
  }
}
