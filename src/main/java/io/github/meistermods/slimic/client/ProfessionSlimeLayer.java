package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Permanent profession details remain visible when optional equipment is absent. */
public final class ProfessionSlimeLayer
    extends RenderLayer<FriendlySlime, SlimeModel<FriendlySlime>> {
  public static final ModelLayerLocation DETAILS =
      new ModelLayerLocation(
          ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "profession_details"), "main");
  private static final ResourceLocation CLEANING = SlimeTextures.accessory("cleaning");
  private static final ResourceLocation COMBAT = SlimeTextures.accessory("combat");
  private static final ResourceLocation BRIDLE = SlimeTextures.accessory("bridle");
  private static final ResourceLocation SOGGY = SlimeTextures.accessory("soggy");

  private final ModelPart details;

  public ProfessionSlimeLayer(
      RenderLayerParent<FriendlySlime, SlimeModel<FriendlySlime>> parent,
      EntityRendererProvider.Context context) {
    super(parent);
    details = context.bakeLayer(DETAILS);
  }

  public static LayerDefinition createLayer() {
    var mesh = new MeshDefinition();
    var root = mesh.getRoot();
    root.addOrReplaceChild(
        "cleaning_cap",
        CubeListBuilder.create()
            .texOffs(1, 1)
            .addBox(-4.6F, 14.7F, -4.6F, 9.2F, 1.2F, 9.2F)
            .texOffs(40, 1)
            .addBox(-3.8F, 15.7F, -6.1F, 7.6F, .45F, 2.1F),
        PartPose.ZERO);
    root.addOrReplaceChild(
        "cleaning_badge",
        CubeListBuilder.create().texOffs(1, 14).addBox(-.75F, 14.85F, -4.75F, 1.5F, .8F, .25F),
        PartPose.ZERO);
    root.addOrReplaceChild(
        "combat_band",
        CubeListBuilder.create().texOffs(1, 1).addBox(-4.55F, 15.1F, -4.55F, 9.1F, .8F, 9.1F),
        PartPose.ZERO);
    root.addOrReplaceChild(
        "combat_crest",
        CubeListBuilder.create().texOffs(40, 1).addBox(-.6F, 12.6F, -3.1F, 1.2F, 2.5F, 6.2F),
        PartPose.ZERO);
    root.addOrReplaceChild(
        "saddle_bridle",
        CubeListBuilder.create()
            .texOffs(1, 1)
            .addBox(-4.7F, 19.2F, -4.7F, 9.4F, .65F, .35F)
            .texOffs(23, 1)
            .addBox(-4.7F, 19.2F, 4.35F, 9.4F, .65F, .35F)
            .texOffs(1, 4)
            .addBox(-4.7F, 19.2F, -4.35F, .35F, .65F, 8.7F)
            .texOffs(22, 4)
            .addBox(4.35F, 19.2F, -4.35F, .35F, .65F, 8.7F),
        PartPose.ZERO);
    root.addOrReplaceChild(
        "saddle_buckle",
        CubeListBuilder.create().texOffs(43, 4).addBox(-.65F, 18.9F, -4.95F, 1.3F, 1.2F, .3F),
        PartPose.ZERO);
    root.addOrReplaceChild(
        "soggy_drops",
        CubeListBuilder.create()
            .texOffs(1, 1)
            .addBox(-1.1F, 13.4F, -1.1F, 2.2F, 2.6F, 2.2F)
            .texOffs(12, 1)
            .addBox(-.6F, 12.4F, -.6F, 1.2F, 1, 1.2F)
            .texOffs(19, 1)
            .addBox(-3.4F, 14.4F, .4F, 1.6F, 1.6F, 1.6F)
            .texOffs(28, 1)
            .addBox(1.8F, 14.4F, -2F, 1.6F, 1.6F, 1.6F),
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
    if (slime.isInvisible()) return;
    if (slime instanceof CleaningSlime) {
      draw("cleaning_cap", CLEANING, pose, buffers, light);
      draw("cleaning_badge", CLEANING, pose, buffers, light);
    } else if (slime instanceof CombatSlime) {
      draw("combat_band", COMBAT, pose, buffers, light);
      draw("combat_crest", COMBAT, pose, buffers, light);
    } else if (slime instanceof SaddleSlime) {
      draw("saddle_bridle", BRIDLE, pose, buffers, light);
      draw("saddle_buckle", BRIDLE, pose, buffers, light);
    } else if (slime instanceof SoggySlime) {
      draw("soggy_drops", SOGGY, pose, buffers, light);
    }
  }

  private void draw(
      String part, ResourceLocation texture, PoseStack pose, MultiBufferSource buffers, int light) {
    details
        .getChild(part)
        .render(
            pose,
            buffers.getBuffer(RenderType.entityCutoutNoCull(texture)),
            light,
            OverlayTexture.NO_OVERLAY);
  }
}
