package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.RoyalGuardSlime;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** A permanent gold crown; restored veterans also retain a weathered purple crest. */
public final class RoyalSlimeLayer extends RenderLayer<FriendlySlime, SlimeModel<FriendlySlime>> {
  public static final ModelLayerLocation CROWN =
      new ModelLayerLocation(
          ResourceLocation.fromNamespaceAndPath("slimic", "royal_crown"), "main");
  private final ModelPart crown;

  public RoyalSlimeLayer(
      RenderLayerParent<FriendlySlime, SlimeModel<FriendlySlime>> parent,
      EntityRendererProvider.Context context) {
    super(parent);
    crown = context.bakeLayer(CROWN);
  }

  public static LayerDefinition createLayer() {
    var mesh = new MeshDefinition();
    mesh.getRoot()
        .addOrReplaceChild(
            "crown",
            CubeListBuilder.create()
                .texOffs(1, 1)
                .addBox(-4.55F, 15.1F, -4.55F, 9.1F, .8F, 9.1F)
                .texOffs(12, 2)
                .addBox(-3.5F, 13.6F, -4.5F, 1, 1.5F, 1)
                .texOffs(12, 2)
                .addBox(-.5F, 13.1F, -4.5F, 1, 2, 1)
                .texOffs(12, 2)
                .addBox(2.5F, 13.6F, -4.5F, 1, 1.5F, 1),
            PartPose.ZERO);
    mesh.getRoot()
        .addOrReplaceChild(
            "relic_crest",
            CubeListBuilder.create().texOffs(40, 1).addBox(-.6F, 13.6F, -3.1F, 1.2F, 1.5F, 6.2F),
            PartPose.ZERO);
    return LayerDefinition.create(mesh, 64, 64);
  }

  @Override
  public void render(
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      FriendlySlime slime,
      float swing,
      float amount,
      float partial,
      float age,
      float yaw,
      float pitch) {
    if (slime.isInvisible() || !(slime instanceof RoyalGuardSlime || slime.hasAncientOrigin()))
      return;
    float shade = slime.hasAncientOrigin() ? .75F : 1;
    var sink =
        buffers.getBuffer(RenderType.entityCutoutNoCull(SlimeTextures.accessory("royal_guard")));
    if (slime instanceof RoyalGuardSlime)
      crown
          .getChild("crown")
          .render(pose, sink, light, OverlayTexture.NO_OVERLAY, shade, shade, shade, 1);
    if (slime.hasAncientOrigin())
      crown
          .getChild("relic_crest")
          .render(pose, sink, light, OverlayTexture.NO_OVERLAY, .7F, .8F, .7F, 1);
  }
}
