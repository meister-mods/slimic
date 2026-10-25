package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.meistermods.slimic.entity.AimlessColony;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

public final class AimlessColonyRenderer extends MobRenderer<AimlessColony, AimlessColonyModel> {
  private static final ResourceLocation TEXTURE =
      ResourceLocation.fromNamespaceAndPath("slimic", "textures/entity/aimless_colony.png");

  public AimlessColonyRenderer(EntityRendererProvider.Context context) {
    super(context, new AimlessColonyModel(context.bakeLayer(AimlessColonyModel.BODY)), 1.7F);
    addLayer(
        new net.minecraft.client.renderer.entity.layers.RenderLayer<>(this) {
          @Override
          public void render(
              PoseStack pose,
              MultiBufferSource buffers,
              int light,
              AimlessColony entity,
              float swing,
              float amount,
              float partial,
              float age,
              float yaw,
              float pitch) {
            if (!entity.isInvisible())
              getParentModel()
                  .renderShell(
                      pose,
                      buffers.getBuffer(RenderType.entityTranslucentCull(TEXTURE)),
                      light,
                      LivingEntityRenderer.getOverlayCoords(entity, 0));
          }
        });
  }

  @Override
  protected void scale(AimlessColony entity, PoseStack pose, float partial) {
    pose.scale(3.6F, 3.6F, 3.6F);
  }

  @Override
  protected RenderType getRenderType(
      AimlessColony entity, boolean visible, boolean translucent, boolean glowing) {
    return glowing
        ? RenderType.outline(TEXTURE)
        : visible
            ? RenderType.entityCutoutNoCull(TEXTURE)
            : translucent ? RenderType.entityTranslucent(TEXTURE) : null;
  }

  @Override
  public ResourceLocation getTextureLocation(AimlessColony entity) {
    return TEXTURE;
  }
}
