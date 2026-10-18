package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.meistermods.slimic.entity.AbundanceSlime;
import io.github.meistermods.slimic.entity.DragonSlime;
import io.github.meistermods.slimic.entity.FishingSlime;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.MeowSlime;
import io.github.meistermods.slimic.entity.TunnelSlime;
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
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Profession silhouettes share the companion body and its dye-aware texture. */
public final class AdvancedSlimeLayer
    extends RenderLayer<FriendlySlime, SlimeModel<FriendlySlime>> {
  public static final ModelLayerLocation DETAILS =
      new ModelLayerLocation(
          ResourceLocation.fromNamespaceAndPath("slimic", "advanced_companions"), "main");
  private final ModelPart root, catHead;
  private final EntityRendererProvider.Context context;

  public AdvancedSlimeLayer(
      RenderLayerParent<FriendlySlime, SlimeModel<FriendlySlime>> parent,
      EntityRendererProvider.Context context) {
    super(parent);
    this.context = context;
    root = context.bakeLayer(DETAILS);
    catHead = context.bakeLayer(ModelLayers.CAT).getChild("head");
    catHead.x = 0;
    catHead.y = 14;
    catHead.z = 0;
  }

  public static LayerDefinition createLayer() {
    var mesh = new MeshDefinition();
    var parts = mesh.getRoot();
    parts.addOrReplaceChild(
        "overalls",
        CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-4.3F, 21, -4.3F, 8.6F, 2.8F, 8.6F)
            .addBox(-2.8F, 18.5F, -4.4F, 5.6F, 2.5F, .5F)
            .addBox(-3, 16, -4.45F, .8F, 2.5F, .5F)
            .addBox(2.2F, 16, -4.45F, .8F, 2.5F, .5F),
        PartPose.ZERO);
    parts.addOrReplaceChild(
        "buttons",
        CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-2.85F, 18.5F, -4.75F, .6F, .6F, .25F)
            .addBox(2.25F, 18.5F, -4.75F, .6F, .6F, .25F),
        PartPose.ZERO);
    parts.addOrReplaceChild(
        "lyre",
        CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-3.2F, 18, -5.5F, .7F, 5, .7F)
            .addBox(2.5F, 18, -5.5F, .7F, 5, .7F)
            .addBox(-2.5F, 22.3F, -5.5F, 5, .7F, .7F)
            .addBox(-2.5F, 17.4F, -5.5F, 5, .7F, .7F),
        PartPose.ZERO);
    var strings = CubeListBuilder.create().texOffs(0, 0);
    for (int i = -2; i <= 2; i++) strings.addBox(i, 18.1F, -5.25F, .12F, 4.2F, .12F);
    parts.addOrReplaceChild("strings", strings, PartPose.ZERO);
    parts.addOrReplaceChild(
        "laurel",
        CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-4.6F, 14.6F, -2.5F, .7F, .7F, 5)
            .addBox(3.9F, 14.6F, -2.5F, .7F, .7F, 5)
            .addBox(-3.5F, 14.2F, 2.5F, 7, .7F, .7F),
        PartPose.ZERO);
    parts.addOrReplaceChild(
        "cart",
        CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-5.5F, 23, -5.5F, 11, .8F, 11)
            .addBox(-5.5F, 20, -5.5F, 11, 3, .8F)
            .addBox(-5.5F, 20, 4.7F, 11, 3, .8F)
            .addBox(-5.5F, 20, -4.7F, .8F, 3, 9.4F)
            .addBox(4.7F, 20, -4.7F, .8F, 3, 9.4F),
        PartPose.ZERO);
    parts.addOrReplaceChild(
        "wheels",
        CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-6, 22, -4, 1, 2.5F, 2.5F)
            .addBox(5, 22, -4, 1, 2.5F, 2.5F)
            .addBox(-6, 22, 1.5F, 1, 2.5F, 2.5F)
            .addBox(5, 22, 1.5F, 1, 2.5F, 2.5F),
        PartPose.ZERO);
    for (boolean left : new boolean[] {true, false}) {
      var wing =
          CubeListBuilder.create()
              .texOffs(0, 0)
              .addBox(left ? 0 : -8, 0, 0, 8, .5F, .6F)
              .addBox(left ? 0 : -7, .5F, .1F, 7, 2.2F, .3F)
              .addBox(left ? 0 : -5, 2.7F, .1F, 5, 1.5F, .3F)
              .addBox(left ? 0 : -3, 4.2F, .1F, 3, 1.2F, .3F);
      parts.addOrReplaceChild(
          left ? "bat_left" : "bat_right", wing, PartPose.offset(left ? 4 : -4, 17, 2));
    }
    parts.addOrReplaceChild(
        "horns",
        CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-3.5F, 13.5F, 0, .8F, 2.5F, .8F)
            .addBox(2.7F, 13.5F, 0, .8F, 2.5F, .8F),
        PartPose.ZERO);
    return LayerDefinition.create(mesh, 16, 16);
  }

  private void part(
      String name, String block, PoseStack pose, MultiBufferSource buffers, int light) {
    root.getChild(name)
        .render(
            pose,
            buffers.getBuffer(
                RenderType.entityCutoutNoCull(
                    ResourceLocation.fromNamespaceAndPath(
                        "minecraft", "textures/block/" + block + ".png"))),
            light,
            OverlayTexture.NO_OVERLAY);
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
      float yaw,
      float pitch) {
    if (slime.isInvisible()) return;
    if (slime instanceof FishingSlime fisher) {
      part("overalls", "blue_wool", pose, buffers, light);
      part("buttons", "gold_block", pose, buffers, light);
      SlimeEquipmentRenderer.held(context, pose, buffers, light, slime, fisher.getRod(), false);
    }
    if (slime instanceof AbundanceSlime) {
      part("lyre", "gold_block", pose, buffers, light);
      part("strings", "white_wool", pose, buffers, light);
      part("laurel", "lime_terracotta", pose, buffers, light);
    }
    if (slime instanceof TunnelSlime) {
      part("cart", "iron_block", pose, buffers, light);
      part("wheels", "black_concrete", pose, buffers, light);
    }
    if (slime instanceof DragonSlime) {
      root.getChild("bat_left").zRot = -.2F + Mth.sin(age * .5F) * .4F;
      root.getChild("bat_right").zRot = -root.getChild("bat_left").zRot;
      part("bat_left", "purple_wool", pose, buffers, light);
      part("bat_right", "purple_wool", pose, buffers, light);
      part("horns", "black_concrete", pose, buffers, light);
    }
    if (slime instanceof MeowSlime)
      catHead.render(
          pose,
          buffers.getBuffer(
              RenderType.entityCutoutNoCull(
                  ResourceLocation.fromNamespaceAndPath(
                      "minecraft", "textures/entity/cat/tabby.png"))),
          light,
          OverlayTexture.NO_OVERLAY);
  }
}
