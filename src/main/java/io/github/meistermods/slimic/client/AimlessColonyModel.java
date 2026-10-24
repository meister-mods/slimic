package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.*;
import io.github.meistermods.slimic.entity.AimlessColony;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class AimlessColonyModel extends EntityModel<AimlessColony> {
  public static final ModelLayerLocation BODY =
      new ModelLayerLocation(
          ResourceLocation.fromNamespaceAndPath("slimic", "aimless_colony"), "body");
  private final ModelPart head, middle, tail, cores;
  private boolean exposed;

  public AimlessColonyModel(ModelPart root) {
    head = root.getChild("head");
    middle = root.getChild("middle");
    tail = root.getChild("tail");
    cores = root.getChild("cores");
  }

  public static LayerDefinition bodyLayer() {
    MeshDefinition mesh = new MeshDefinition();
    PartDefinition root = mesh.getRoot();
    root.addOrReplaceChild(
        "cores",
        CubeListBuilder.create()
            .texOffs(56, 56)
            .addBox(-3, 18, -6, 2, 2, 2)
            .addBox(2, 19, -1, 2, 2, 2)
            .addBox(-1, 20, 5, 2, 2, 2),
        PartPose.ZERO);
    root.addOrReplaceChild(
        "head",
        CubeListBuilder.create().texOffs(0, 0).addBox(-5, -9, -7, 10, 9, 9),
        PartPose.offset(0, 24, -5));
    root.addOrReplaceChild(
        "middle",
        CubeListBuilder.create().texOffs(0, 12).addBox(-6, -8, -7, 12, 8, 14),
        PartPose.offset(0, 24, 0));
    root.addOrReplaceChild(
        "tail",
        CubeListBuilder.create().texOffs(0, 6).addBox(-4, -6, -3, 8, 6, 10),
        PartPose.offset(0, 24, 6));
    return LayerDefinition.create(mesh, 64, 64);
  }

  @Override
  public void setupAnim(
      AimlessColony slime, float swing, float amount, float age, float yaw, float pitch) {
    exposed = slime.exposed();
    float pulse = slime.pattern() == AimlessColony.Pattern.SPLIT ? .12F : .035F;
    float wave = Mth.sin(age * .14F);
    head.xScale = 1 + wave * pulse;
    head.yScale = 1 - wave * pulse;
    middle.xScale = 1 - wave * pulse;
    middle.yScale = 1 + wave * pulse;
    tail.xRot = Mth.sin(age * .1F + 1) * .04F;
    tail.zRot = Mth.sin(age * .1F) * .06F;
    head.yRot = yaw * .003F;
  }

  @Override
  public void renderToBuffer(
      PoseStack pose,
      VertexConsumer sink,
      int light,
      int overlay,
      float r,
      float g,
      float b,
      float alpha) {
    cores.render(pose, sink, exposed ? 15728880 : light, overlay, r, g, b, alpha);
  }

  public void renderShell(PoseStack pose, VertexConsumer sink, int light, int overlay) {
    head.render(pose, sink, light, overlay, 1, 1, 1, .85F);
    middle.render(pose, sink, light, overlay, 1, 1, 1, .85F);
    tail.render(pose, sink, light, overlay, 1, 1, 1, .85F);
  }
}
