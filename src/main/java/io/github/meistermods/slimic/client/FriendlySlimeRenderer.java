package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.FriendlySlime;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class FriendlySlimeRenderer
    extends MobRenderer<FriendlySlime, SlimeModel<FriendlySlime>> {
  private static final ResourceLocation TEXTURE =
      ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "textures/entity/friendly_slime.png");

  public FriendlySlimeRenderer(EntityRendererProvider.Context context) {
    super(context, new SlimeModel<>(context.bakeLayer(ModelLayers.SLIME)), 0.5F);
    addLayer(new SlimeOuterLayer<>(this, context.getModelSet()));
  }

  @Override
  protected void scale(FriendlySlime slime, PoseStack pose, float partialTick) {
    pose.scale(0.999F, 0.999F, 0.999F);
    pose.translate(0.0F, 0.001F, 0.0F);
    float squish = Mth.lerp(partialTick, slime.oldSquish, slime.squish) / 2.0F;
    float stretch = 1.0F / (squish + 1.0F);
    pose.scale(stretch * 2.0F, 2.0F / stretch, stretch * 2.0F);
  }

  @Override
  public ResourceLocation getTextureLocation(FriendlySlime slime) {
    return TEXTURE;
  }
}
