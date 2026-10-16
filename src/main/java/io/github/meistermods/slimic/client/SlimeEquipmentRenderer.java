package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.meistermods.slimic.entity.FriendlySlime;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolActions;

public final class SlimeEquipmentRenderer {
  private SlimeEquipmentRenderer() {}

  public static void held(
      EntityRendererProvider.Context context,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      FriendlySlime slime,
      ItemStack stack,
      boolean left) {
    if (stack.isEmpty()) return;
    boolean shield = stack.canPerformAction(ToolActions.SHIELD_BLOCK);
    pose.pushPose();
    pose.translate(left ? -.34 : .34, shield ? 1.18 : 1.14, shield ? -.34 : -.28);
    if (shield) {
      if (slime.isBlocking()) pose.translate(.10, -.08, -.16);
      // Render the plate facing forward, with its grip extending inward toward the body.
      pose.mulPose(Axis.YP.rotationDegrees(180));
      pose.scale(.45F, .45F, .45F);
      pose.translate(.5, .5, .5);
    } else {
      // Slime model Y points down. Account for the tool model's built-in diagonal grip.
      pose.mulPose(Axis.YP.rotationDegrees(left ? -25 : 25));
      pose.mulPose(Axis.XP.rotationDegrees(180));
      pose.mulPose(Axis.YP.rotationDegrees(180));
      pose.scale(.55F, .55F, .55F);
    }
    context
        .getItemRenderer()
        .renderStatic(
            slime,
            stack,
            shield
                ? ItemDisplayContext.NONE
                : left
                    ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
            !shield && left,
            pose,
            buffers,
            slime.level(),
            light,
            OverlayTexture.NO_OVERLAY,
            slime.getId());
    pose.popPose();
  }
}
