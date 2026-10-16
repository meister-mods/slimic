package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.TransportSlime;
import io.github.meistermods.slimic.entity.WarehouseSlime;
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
    // Inner and outer slime faces end at z=3 and z=4; keep the pack beyond both.
    MeshDefinition mesh = new MeshDefinition();
    mesh.getRoot()
        .addOrReplaceChild(
            "pack",
            CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, 17, 4.4F, 7, 6, 4),
            PartPose.ZERO);
    mesh.getRoot()
        .addOrReplaceChild(
            "straps",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-4.65F, 15.8F, -4.2F, 0.5F, 7.2F, 8.8F)
                .addBox(4.15F, 15.8F, -4.2F, 0.5F, 7.2F, 8.8F)
                .addBox(-4.65F, 17.2F, 4.15F, 1.3F, 5.6F, 0.5F)
                .addBox(3.35F, 17.2F, 4.15F, 1.3F, 5.6F, 0.5F),
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
    pose.pushPose();
    if (slime instanceof WarehouseSlime) {
      pose.translate(0, -.25, -.1);
      pose.scale(1.35F, 1.2F, 1.5F);
    }
    pack.render(
        pose,
        buffers.getBuffer(RenderType.entityCutoutNoCull(WOOD)),
        light,
        OverlayTexture.NO_OVERLAY);
    pose.popPose();
    straps.render(
        pose,
        buffers.getBuffer(RenderType.entityCutoutNoCull(STRAPS)),
        light,
        OverlayTexture.NO_OVERLAY);
  }
}
