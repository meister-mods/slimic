package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.HealingSlime;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class HealingSlimeLayer extends RenderLayer<FriendlySlime, SlimeModel<FriendlySlime>> {
  public static final ModelLayerLocation BAND =
      new ModelLayerLocation(
          ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "healing_band"), "main");
  private static final ResourceLocation TEXTURE = SlimeTextures.accessory("healing");
  private final ModelPart band, emblem;

  public HealingSlimeLayer(
      RenderLayerParent<FriendlySlime, SlimeModel<FriendlySlime>> parent,
      EntityRendererProvider.Context context) {
    super(parent);
    ModelPart root = context.bakeLayer(BAND);
    band = root.getChild("band");
    emblem = root.getChild("emblem");
  }

  public static LayerDefinition createLayer() {
    MeshDefinition mesh = new MeshDefinition();
    mesh.getRoot()
        .addOrReplaceChild(
            "band",
            CubeListBuilder.create()
                .texOffs(1, 1)
                .addBox(-4.4F, 15.5F, -4.4F, 8.8F, 1.8F, .5F)
                .texOffs(22, 1)
                .addBox(-4.4F, 15.5F, 3.9F, 8.8F, 1.8F, .5F)
                .texOffs(43, 1)
                .addBox(-4.4F, 15.5F, -3.9F, .5F, 1.8F, 7.8F)
                .texOffs(1, 13)
                .addBox(3.9F, 15.5F, -3.9F, .5F, 1.8F, 7.8F),
            PartPose.ZERO);
    mesh.getRoot()
        .addOrReplaceChild(
            "emblem",
            CubeListBuilder.create()
                .texOffs(20, 13)
                .addBox(-.5F, 15, -4.65F, 1, 2.8F, .3F)
                .texOffs(25, 13)
                .addBox(-1.4F, 15.9F, -4.65F, .9F, 1, .3F)
                .texOffs(30, 13)
                .addBox(.5F, 15.9F, -4.65F, .9F, 1, .3F),
            PartPose.ZERO);
    return LayerDefinition.create(mesh, 64, 64);
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
    if (!(slime instanceof HealingSlime) || slime.isInvisible()) return;
    band.render(
        pose,
        buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
        light,
        OverlayTexture.NO_OVERLAY);
    emblem.render(
        pose,
        buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
        light,
        OverlayTexture.NO_OVERLAY);
  }
}
