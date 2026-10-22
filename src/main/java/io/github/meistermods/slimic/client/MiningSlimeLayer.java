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
  private static final ResourceLocation GOGGLES = SlimeTextures.accessory("prospecting_goggles");
  private final ModelPart helmet;
  private final ModelPart lamp;
  private final ModelPart goggles;
  private final ModelPart lenses;
  private final EntityRendererProvider.Context context;

  public MiningSlimeLayer(
      RenderLayerParent<FriendlySlime, SlimeModel<FriendlySlime>> parent,
      EntityRendererProvider.Context context) {
    super(parent);
    this.context = context;
    ModelPart root = context.bakeLayer(HELMET);
    helmet = root.getChild("helmet");
    lamp = root.getChild("lamp");
    goggles = root.getChild("goggles");
    lenses = root.getChild("lenses");
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
    mesh.getRoot()
        .addOrReplaceChild(
            "goggles",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-4.1F, 17.5F, -4.6F, 3.8F, .4F, .4F)
                .texOffs(0, 0)
                .addBox(.3F, 17.5F, -4.6F, 3.8F, .4F, .4F)
                .texOffs(0, 0)
                .addBox(-4.1F, 20.1F, -4.6F, 3.8F, .4F, .4F)
                .texOffs(0, 0)
                .addBox(.3F, 20.1F, -4.6F, 3.8F, .4F, .4F)
                .texOffs(0, 0)
                .addBox(-4.1F, 17.9F, -4.6F, .4F, 2.2F, .4F)
                .texOffs(0, 0)
                .addBox(-.7F, 17.9F, -4.6F, .4F, 2.2F, .4F)
                .texOffs(0, 0)
                .addBox(.3F, 17.9F, -4.6F, .4F, 2.2F, .4F)
                .texOffs(0, 0)
                .addBox(3.7F, 17.9F, -4.6F, .4F, 2.2F, .4F)
                .texOffs(32, 0)
                .addBox(-.3F, 18.5F, -4.55F, .6F, .4F, .3F)
                .texOffs(32, 32)
                .addBox(-4.3F, 18.2F, -4.2F, .3F, .6F, 7.5F)
                .texOffs(32, 32)
                .addBox(4.0F, 18.2F, -4.2F, .3F, .6F, 7.5F),
            PartPose.ZERO);
    mesh.getRoot()
        .addOrReplaceChild(
            "lenses",
            CubeListBuilder.create()
                .texOffs(0, 32)
                .addBox(-3.7F, 17.9F, -4.5F, 3.0F, 2.2F, .1F)
                .texOffs(0, 32)
                .addBox(.7F, 17.9F, -4.5F, 3.0F, 2.2F, .1F),
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
    if (miner instanceof io.github.meistermods.slimic.entity.NextGenerationMiningSlime) {
      goggles.render(
          pose,
          buffers.getBuffer(RenderType.entityCutoutNoCull(GOGGLES)),
          light,
          OverlayTexture.NO_OVERLAY);
      lenses.render(
          pose,
          buffers.getBuffer(RenderType.entityTranslucent(GOGGLES)),
          light,
          OverlayTexture.NO_OVERLAY,
          1,
          1,
          1,
          .4F);
      pose.pushPose();
      pose.translate(.225, .82, -.02);
      pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
      pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180));
      pose.scale(.16F, .16F, .16F);
      context
          .getItemRenderer()
          .renderStatic(
              io.github.meistermods.slimic.registry.ModItems.ROYAL_SYNTHESIS_CORE
                  .get()
                  .getDefaultInstance(),
              net.minecraft.world.item.ItemDisplayContext.FIXED,
              light,
              OverlayTexture.NO_OVERLAY,
              pose,
              buffers,
              slime.level(),
              slime.getId());
      pose.popPose();
    }
    SlimeEquipmentRenderer.held(context, pose, buffers, light, miner, miner.getPickaxe(), false);
    SlimeEquipmentRenderer.held(context, pose, buffers, light, miner, miner.getShovel(), true);
  }
}
