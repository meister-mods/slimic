package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.SaddleSlime;
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
      ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "textures/entity/slime_wings.png");
  private final ModelPart saddle;
  private final ModelPart stirrups;
  private final ModelPart wingRoot;
  private final ModelPart leftWing;
  private final ModelPart rightWing;
  public static final ModelLayerLocation WING_LAYER =
      new ModelLayerLocation(
          ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "slime_wings"), "main");

  public SaddleSlimeLayer(
      RenderLayerParent<FriendlySlime, SlimeModel<FriendlySlime>> parent,
      EntityRendererProvider.Context context) {
    super(parent);
    ModelPart root = context.bakeLayer(SADDLE);
    saddle = root.getChild("saddle");
    stirrups = root.getChild("stirrups");
    wingRoot = context.bakeLayer(WING_LAYER);
    leftWing = wingRoot.getChild("left");
    rightWing = wingRoot.getChild("right");
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

  public static LayerDefinition createWings() {
    MeshDefinition mesh = new MeshDefinition();
    for (int side : new int[] {-1, 1}) {
      CubeListBuilder wing = CubeListBuilder.create().texOffs(0, 0);
      wing.addBox(side < 0 ? -6 : 0, -.4F, -3, 6, .8F, 6);
      wing.texOffs(0, 8).addBox(side < 0 ? -11 : 6, -.3F, -2.5F, 5, .6F, 6);
      wing.texOffs(0, 16).addBox(side < 0 ? -15 : 11, -.2F, -1.5F, 4, .4F, 5);
      mesh.getRoot()
          .addOrReplaceChild(
              side < 0 ? "left" : "right", wing, PartPose.offset(side * 4.4F, 18, 0));
    }
    return LayerDefinition.create(mesh, 32, 32);
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
      float flap =
          (mount.isFlyingMount() && !mount.isGroundedMount() && !mount.isGlidingDown())
              ? net.minecraft.util.Mth.sin(age * (mount.isBoosting() ? .9F : .5F)) * .38F
              : mount.isFlyingMount() && !mount.isGroundedMount() ? 0 : -.3F;
      leftWing.zRot = flap;
      rightWing.zRot = -flap;
      leftWing.yRot = mount.isFlyingMount() && !mount.isGroundedMount() ? .12F : 1.2F;
      rightWing.yRot = -leftWing.yRot;
      wingRoot.render(
          pose,
          ItemRenderer.getArmorFoilBuffer(
              buffers, RenderType.armorCutoutNoCull(WINGS), false, mount.getEquipment(1).hasFoil()),
          light,
          OverlayTexture.NO_OVERLAY);
    }
  }
}
