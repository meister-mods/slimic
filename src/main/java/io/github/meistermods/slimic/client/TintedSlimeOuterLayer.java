package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.meistermods.slimic.entity.FriendlySlime;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

public final class TintedSlimeOuterLayer
    extends RenderLayer<FriendlySlime, SlimeModel<FriendlySlime>> {
  private final TintedSlimeModel model;

  public TintedSlimeOuterLayer(
      RenderLayerParent<FriendlySlime, SlimeModel<FriendlySlime>> parent,
      EntityRendererProvider.Context context) {
    super(parent);
    model = new TintedSlimeModel(context.bakeLayer(ModelLayers.SLIME_OUTER));
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
    if (slime.isInvisible()) return;
    getParentModel().copyPropertiesTo(model);
    model.setupAnim(slime, limbSwing, limbAmount, age, headYaw, headPitch);
    RenderType type =
        slime.getDyeColorId() < 0
            ? RenderType.entityTranslucent(getTextureLocation(slime))
            : DyedSlimeRenderType.forTexture(getTextureLocation(slime));
    model.renderToBuffer(
        pose,
        buffers.getBuffer(type),
        light,
        LivingEntityRenderer.getOverlayCoords(slime, 0),
        1,
        1,
        1,
        1);
  }
}
