package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.FarmerSlime;
import io.github.meistermods.slimic.entity.FieldSlime;
import io.github.meistermods.slimic.entity.FriendlySlime;
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

/** Straw brim and leather cap share the companion's native equipment transform. */
public final class FieldSlimeLayer extends RenderLayer<FriendlySlime, SlimeModel<FriendlySlime>> {
  public static final ModelLayerLocation HATS =
      new ModelLayerLocation(
          ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "field_hats"), "main");
  private static final ResourceLocation FARMER_TEXTURE = SlimeTextures.accessory("farmer");
  private static final ResourceLocation LUMBERJACK_TEXTURE = SlimeTextures.accessory("lumberjack");
  private final ModelPart straw, cap, band;
  private final EntityRendererProvider.Context context;

  public FieldSlimeLayer(
      RenderLayerParent<FriendlySlime, SlimeModel<FriendlySlime>> parent,
      EntityRendererProvider.Context context) {
    super(parent);
    this.context = context;
    var root = context.bakeLayer(HATS);
    straw = root.getChild("straw");
    cap = root.getChild("cap");
    band = root.getChild("band");
  }

  public static LayerDefinition createLayer() {
    var mesh = new MeshDefinition();
    mesh.getRoot()
        .addOrReplaceChild(
            "straw",
            CubeListBuilder.create()
                .texOffs(1, 1)
                .addBox(-6, 15, -6, 12, 1, 12)
                .texOffs(1, 16)
                .addBox(-3.5F, 12, -3.5F, 7, 3, 7),
            PartPose.ZERO);
    mesh.getRoot()
        .addOrReplaceChild(
            "band",
            CubeListBuilder.create().texOffs(31, 16).addBox(-3.6F, 14, -3.6F, 7.2F, .7F, 7.2F),
            PartPose.ZERO);
    mesh.getRoot()
        .addOrReplaceChild(
            "cap",
            CubeListBuilder.create()
                .texOffs(1, 1)
                .addBox(-4, 12.5F, -4, 8, 3.5F, 8)
                .texOffs(35, 1)
                .addBox(-4.5F, 15, -6, 9, 1, 3),
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
    if (!(slime instanceof FieldSlime field) || slime.isInvisible()) return;
    if (field instanceof FarmerSlime) {
      straw.render(
          pose,
          buffers.getBuffer(RenderType.entityCutoutNoCull(FARMER_TEXTURE)),
          light,
          OverlayTexture.NO_OVERLAY);
      band.render(
          pose,
          buffers.getBuffer(RenderType.entityCutoutNoCull(FARMER_TEXTURE)),
          light,
          OverlayTexture.NO_OVERLAY);
    } else
      cap.render(
          pose,
          buffers.getBuffer(RenderType.entityCutoutNoCull(LUMBERJACK_TEXTURE)),
          light,
          OverlayTexture.NO_OVERLAY);
    SlimeEquipmentRenderer.held(context, pose, buffers, light, field, field.getTool(), false);
  }
}
