package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.MiningSlime;
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

public final class MiningSlimeLayer extends RenderLayer<FriendlySlime, SlimeModel<FriendlySlime>> {
  public static final ModelLayerLocation HELMET =
      new ModelLayerLocation(
          ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "mining_helmet"), "main");
  private static final ResourceLocation TEXTURE = SlimeTextures.accessory("mining");
  private final ModelPart helmet;
  private final ModelPart lamp;
  private final EntityRendererProvider.Context context;

  public MiningSlimeLayer(
      RenderLayerParent<FriendlySlime, SlimeModel<FriendlySlime>> parent,
      EntityRendererProvider.Context context) {
    super(parent);
    this.context = context;
    ModelPart root = context.bakeLayer(HELMET);
    helmet = root.getChild("helmet");
    lamp = root.getChild("lamp");
  }

  public static LayerDefinition createLayer() {
    MeshDefinition mesh = new MeshDefinition();
    mesh.getRoot()
        .addOrReplaceChild(
            "helmet",
            CubeListBuilder.create()
                .texOffs(1, 1)
                .addBox(-4.5F, 15.0F, -4.5F, 9.0F, 1.0F, 9.0F)
                .texOffs(1, 13)
                .addBox(-3.5F, 12.0F, -3.5F, 7.0F, 3.0F, 7.0F),
            PartPose.ZERO);
    mesh.getRoot()
        .addOrReplaceChild(
            "lamp",
            CubeListBuilder.create().texOffs(31, 13).addBox(-1.0F, 12.5F, -4.2F, 2.0F, 2.0F, 1.0F),
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
    if (!(slime instanceof MiningSlime miner) || slime.isInvisible()) return;
    helmet.render(
        pose,
        buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
        light,
        OverlayTexture.NO_OVERLAY);
    lamp.render(
        pose,
        buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
        15728880,
        OverlayTexture.NO_OVERLAY);
    SlimeEquipmentRenderer.held(context, pose, buffers, light, miner, miner.getPickaxe(), false);
    SlimeEquipmentRenderer.held(context, pose, buffers, light, miner, miner.getShovel(), true);
  }
}
