package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.meistermods.slimic.entity.ColonyFragment;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class ColonyFragmentRenderer
    extends MobRenderer<ColonyFragment, SlimeModel<ColonyFragment>> {
  public static final ModelLayerLocation CORES =
      new ModelLayerLocation(
          ResourceLocation.fromNamespaceAndPath("slimic", "colony_fragment"), "cores");

  public static LayerDefinition coreLayer() {
    MeshDefinition mesh = new MeshDefinition();
    mesh.getRoot()
        .addOrReplaceChild(
            "attack",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-.5F, 19, -4.2F, 1, 1, .25F)
                .addBox(-1.5F, 20, -4.2F, 3, 1, .25F),
            PartPose.ZERO);
    mesh.getRoot()
        .addOrReplaceChild(
            "cohesion",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-.5F, 18.5F, -4.2F, 1, 1, .25F)
                .addBox(-1.5F, 19.5F, -4.2F, 3, 1, .25F)
                .addBox(-.5F, 20.5F, -4.2F, 1, 1, .25F),
            PartPose.ZERO);
    mesh.getRoot()
        .addOrReplaceChild(
            "returning",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-1, 19, -4.2F, 1, 3, .25F)
                .addBox(0, 20, -4.2F, 1, 1, .25F)
                .addBox(-2, 20, -4.2F, 1, 1, .25F),
            PartPose.ZERO);
    return LayerDefinition.create(mesh, 16, 16);
  }

  public ColonyFragmentRenderer(EntityRendererProvider.Context context) {
    super(context, new SlimeModel<>(context.bakeLayer(ModelLayers.SLIME)), .35F);
    var outer = new SlimeModel<ColonyFragment>(context.bakeLayer(ModelLayers.SLIME_OUTER));
    var cores = context.bakeLayer(CORES);
    addLayer(
        new RenderLayer<>(this) {
          @Override
          public void render(
              PoseStack pose,
              MultiBufferSource buffers,
              int light,
              ColonyFragment entity,
              float swing,
              float amount,
              float partial,
              float age,
              float yaw,
              float pitch) {
            if (entity.isInvisible()) return;
            getParentModel().copyPropertiesTo(outer);
            outer.renderToBuffer(
                pose,
                buffers.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity))),
                light,
                OverlayTexture.NO_OVERLAY,
                1,
                1,
                1,
                .85F);
            cores
                .getChild(entity.role().name().toLowerCase(java.util.Locale.ROOT))
                .render(
                    pose,
                    buffers.getBuffer(
                        RenderType.entityCutoutNoCull(
                            ResourceLocation.withDefaultNamespace(
                                "textures/block/gold_block.png"))),
                    light,
                    OverlayTexture.NO_OVERLAY);
          }
        });
  }

  @Override
  protected void scale(ColonyFragment entity, PoseStack pose, float partial) {
    pose.scale(1.5F, 1.5F, 1.5F);
  }

  @Override
  public ResourceLocation getTextureLocation(ColonyFragment entity) {
    return ResourceLocation.fromNamespaceAndPath(
        "slimic",
        "textures/entity/colony_fragment_"
            + entity.role().name().toLowerCase(java.util.Locale.ROOT)
            + ".png");
  }
}
