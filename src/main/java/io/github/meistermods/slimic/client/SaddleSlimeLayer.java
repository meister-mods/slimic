package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.SaddleSlime;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class SaddleSlimeLayer extends RenderLayer<FriendlySlime, SlimeModel<FriendlySlime>> {
  public static final ModelLayerLocation SADDLE =
      new ModelLayerLocation(
          ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "slime_saddle"), "main");
  private static final ResourceLocation LEATHER =
      ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "textures/entity/slime_saddle.png");
  private static final ResourceLocation METAL =
      ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/iron_block.png");
  private static final ResourceLocation WINGS =
      ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/elytra.png");
  private final ModelPart saddle;
  private final ModelPart stirrups;
  private final ModelPart wingRoot;
  private final ElytraModel<FriendlySlime> wings;

  public SaddleSlimeLayer(
      RenderLayerParent<FriendlySlime, SlimeModel<FriendlySlime>> parent,
      EntityRendererProvider.Context context) {
    super(parent);
    ModelPart root = context.bakeLayer(SADDLE);
    saddle = root.getChild("saddle");
    stirrups = root.getChild("stirrups");
    wingRoot = context.bakeLayer(ModelLayers.ELYTRA);
    wings = new ElytraModel<>(wingRoot);
  }

  public static LayerDefinition createLayer() {
    MeshDefinition mesh = new MeshDefinition();
    mesh.getRoot()
        .addOrReplaceChild(
            "saddle",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-4.3F, 15.1F, -4.3F, 8.6F, 1.2F, 8.6F)
                .addBox(-4, 13.9F, -4, 8, 1.4F, 1.5F)
                .addBox(-4, 13.9F, 2.5F, 8, 1.4F, 1.5F)
                .addBox(-4.6F, 16, -1, .7F, 5.5F, 2)
                .addBox(3.9F, 16, -1, .7F, 5.5F, 2),
            PartPose.ZERO);
    mesh.getRoot()
        .addOrReplaceChild(
            "stirrups",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-5.1F, 21, -1.6F, 1.4F, .6F, 3.2F)
                .addBox(3.7F, 21, -1.6F, 1.4F, .6F, 3.2F),
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
    if (!(slime instanceof SaddleSlime mount) || slime.isInvisible()) return;
    if (mount.isSaddled()) {
      saddle.render(
          pose,
          buffers.getBuffer(RenderType.entityCutoutNoCull(LEATHER)),
          light,
          OverlayTexture.NO_OVERLAY);
      stirrups.render(
          pose,
          buffers.getBuffer(RenderType.entityCutoutNoCull(METAL)),
          light,
          OverlayTexture.NO_OVERLAY);
    }
    if (mount.getEquipment(1).is(net.minecraft.world.item.Items.ELYTRA)) {
      pose.pushPose();
      pose.translate(0, .92, .32);
      pose.scale(.45F, .45F, .45F);
      getParentModel().copyPropertiesTo(wings);
      wings.setupAnim(slime, limbSwing, limbAmount, age, headYaw, headPitch);
      var left = wingRoot.getChild("left_wing");
      var right = wingRoot.getChild("right_wing");
      left.zRot = mount.isFlyingMount() ? -1.05F : -.35F;
      right.zRot = -left.zRot;
      wings.renderToBuffer(
          pose,
          ItemRenderer.getArmorFoilBuffer(
              buffers, RenderType.armorCutoutNoCull(WINGS), false, mount.getEquipment(1).hasFoil()),
          light,
          OverlayTexture.NO_OVERLAY,
          1,
          1,
          1,
          1);
      pose.popPose();
    }
  }
}
