package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.TransportSlime;
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

public final class TransportSlimeLayer
    extends RenderLayer<FriendlySlime, SlimeModel<FriendlySlime>> {
  public static final ModelLayerLocation PACK =
      new ModelLayerLocation(
          ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "transport_pack"), "main");
  private static final ResourceLocation WOOD =
      ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/oak_planks.png");
  private static final ResourceLocation STRAPS =
      ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/brown_concrete.png");
  private final ModelPart pack;
  private final ModelPart straps;

  public TransportSlimeLayer(
      RenderLayerParent<FriendlySlime, SlimeModel<FriendlySlime>> parent,
      EntityRendererProvider.Context context) {
    super(parent);
    ModelPart root = context.bakeLayer(PACK);
    pack = root.getChild("pack");
    straps = root.getChild("straps");
  }

  public static LayerDefinition createLayer() {
    MeshDefinition mesh = new MeshDefinition();
    mesh.getRoot()
        .addOrReplaceChild(
            "pack",
            CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, 17, 3, 7, 6, 4),
            PartPose.ZERO);
    mesh.getRoot()
        .addOrReplaceChild(
            "straps",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-4.15F, 16, -4.2F, 0.7F, 7, 8.5F)
                .addBox(3.45F, 16, -4.2F, 0.7F, 7, 8.5F),
            PartPose.ZERO);
    return LayerDefinition.create(mesh, 16, 16);
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
    if (!(slime instanceof TransportSlime) || slime.isInvisible()) return;
    pack.render(
        pose,
        buffers.getBuffer(RenderType.entityCutoutNoCull(WOOD)),
        light,
        OverlayTexture.NO_OVERLAY);
    straps.render(
        pose,
        buffers.getBuffer(RenderType.entityCutoutNoCull(STRAPS)),
        light,
        OverlayTexture.NO_OVERLAY);
  }
}
