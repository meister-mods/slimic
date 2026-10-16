package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.meistermods.slimic.entity.*;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class GuardianAngelSlimeLayer
    extends RenderLayer<FriendlySlime, SlimeModel<FriendlySlime>> {
  public static final ModelLayerLocation HALO =
      new ModelLayerLocation(
          ResourceLocation.fromNamespaceAndPath("slimic", "guardian_halo"), "main");
  private final ModelPart halo, left, right;

  public GuardianAngelSlimeLayer(
      RenderLayerParent<FriendlySlime, SlimeModel<FriendlySlime>> parent,
      EntityRendererProvider.Context context) {
    super(parent);
    halo = context.bakeLayer(HALO);
    var wings = context.bakeLayer(SaddleSlimeLayer.WING_LAYER);
    left = wings.getChild("left");
    right = wings.getChild("right");
  }

  public static LayerDefinition createHalo() {
    var mesh = new MeshDefinition();
    mesh.getRoot()
        .addOrReplaceChild(
            "ring",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-4, 10, -4, 8, .6F, .7F)
                .addBox(-4, 10, 3.3F, 8, .6F, .7F)
                .addBox(-4, 10, -3.3F, .7F, .6F, 6.6F)
                .addBox(3.3F, 10, -3.3F, .7F, .6F, 6.6F),
            PartPose.ZERO);
    return LayerDefinition.create(mesh, 16, 16);
  }

  @Override
  public void render(
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      FriendlySlime slime,
      float a,
      float b,
      float partial,
      float age,
      float yaw,
      float pitch) {
    if (!(slime instanceof GuardianAngelSlime) || slime.isInvisible()) return;
    halo.render(
        pose,
        buffers.getBuffer(
            RenderType.entityCutoutNoCull(
                ResourceLocation.fromNamespaceAndPath(
                    "minecraft", "textures/block/gold_block.png"))),
        15728880,
        OverlayTexture.NO_OVERLAY);
    left.zRot = Mth.sin(age * .5F) * .4F;
    right.zRot = -left.zRot;
    left.yRot = .2F;
    right.yRot = -.2F;
    var vertices =
        buffers.getBuffer(
            RenderType.entityCutoutNoCull(
                ResourceLocation.fromNamespaceAndPath(
                    "slimic", "textures/entity/slime_wings.png")));
    left.render(pose, vertices, light, OverlayTexture.NO_OVERLAY);
    right.render(pose, vertices, light, OverlayTexture.NO_OVERLAY);
  }
}
