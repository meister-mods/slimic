package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.meistermods.slimic.entity.AncientSlime;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Ruin remnants retain the familiar slime body, worn royal insignia and their profession. */
public final class AncientSlimeRenderer
    extends MobRenderer<AncientSlime, SlimeModel<AncientSlime>> {
  private static final ResourceLocation BODY =
      ResourceLocation.fromNamespaceAndPath("slimic", "textures/entity/friendly_slime.png");
  private final ModelPart crown, pack, commander, helmet;

  public AncientSlimeRenderer(EntityRendererProvider.Context context) {
    super(context, new SlimeModel<>(context.bakeLayer(ModelLayers.SLIME)), .5F);
    helmet = context.bakeLayer(MiningSlimeLayer.HELMET);
    crown = context.bakeLayer(RoyalSlimeLayer.CROWN);
    pack = context.bakeLayer(TransportSlimeLayer.PACK);
    commander = context.bakeLayer(AdvancedSlimeLayer.DETAILS);
    var outer = new SlimeModel<AncientSlime>(context.bakeLayer(ModelLayers.SLIME_OUTER));
    addLayer(
        new RenderLayer<>(this) {
          @Override
          public void render(
              PoseStack pose,
              MultiBufferSource buffers,
              int light,
              AncientSlime slime,
              float swing,
              float amount,
              float partial,
              float age,
              float yaw,
              float pitch) {
            if (slime.isInvisible()) return;
            getParentModel().copyPropertiesTo(outer);
            outer.renderToBuffer(
                pose,
                buffers.getBuffer(RenderType.entityTranslucent(BODY)),
                light,
                OverlayTexture.NO_OVERLAY,
                .65F,
                .75F,
                .55F,
                1);
            var sink =
                buffers.getBuffer(
                    RenderType.entityCutoutNoCull(SlimeTextures.accessory("royal_guard")));
            pose.pushPose();
            if (slime.role() == AncientSlime.Role.MINER || slime.role() == AncientSlime.Role.TUNNEL)
              pose.translate(0, -.2, 0);
            crown
                .getChild("relic_crest")
                .render(pose, sink, light, OverlayTexture.NO_OVERLAY, .7F, .8F, .7F, 1);
            pose.popPose();
            if (slime.role() == AncientSlime.Role.GUARD
                || slime.role() == AncientSlime.Role.COMMANDER
                || slime.role() == AncientSlime.Role.MINE_GUARD)
              crown
                  .getChild("crown")
                  .render(pose, sink, light, OverlayTexture.NO_OVERLAY, .65F, .7F, .6F, 1);
            if (slime.role() == AncientSlime.Role.MINER
                || slime.role() == AncientSlime.Role.TUNNEL) {
              var metal =
                  buffers.getBuffer(
                      RenderType.entityCutoutNoCull(SlimeTextures.accessory("mining")));
              helmet
                  .getChild("helmet")
                  .render(pose, metal, light, OverlayTexture.NO_OVERLAY, .65F, .7F, .6F, 1);
              helmet
                  .getChild("lamp")
                  .render(pose, metal, 15728880, OverlayTexture.NO_OVERLAY, .8F, .7F, .6F, 1);
              SlimeEquipmentRenderer.held(
                  context, pose, buffers, light, slime, slime.getMainHandItem(), false);
            }
            if (slime.role() == AncientSlime.Role.TUNNEL) {
              var metal =
                  buffers.getBuffer(
                      RenderType.entityCutoutNoCull(SlimeTextures.accessory("tunnel")));
              for (var part : new String[] {"cart", "wheels"})
                commander
                    .getChild(part)
                    .render(pose, metal, light, OverlayTexture.NO_OVERLAY, .7F, .7F, .6F, 1);
            }
            if (slime.role() == AncientSlime.Role.MINE_GUARD) {
              SlimeEquipmentRenderer.held(
                  context,
                  pose,
                  buffers,
                  light,
                  slime,
                  new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD),
                  false);
              SlimeEquipmentRenderer.held(
                  context,
                  pose,
                  buffers,
                  light,
                  slime,
                  new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD),
                  true);
            }
            if (slime.role() == AncientSlime.Role.TRANSPORT) {
              var wood =
                  buffers.getBuffer(
                      RenderType.entityCutoutNoCull(SlimeTextures.accessory("transport")));
              pack.getChild("pack")
                  .render(pose, wood, light, OverlayTexture.NO_OVERLAY, .7F, .7F, .6F, 1);
              pack.getChild("straps")
                  .render(pose, wood, light, OverlayTexture.NO_OVERLAY, .7F, .7F, .6F, 1);
            }
            if (slime.role() == AncientSlime.Role.COMMANDER) {
              var baton =
                  buffers.getBuffer(
                      RenderType.entityCutoutNoCull(SlimeTextures.accessory("commander")));
              for (var part : new String[] {"mustache", "baton", "baton_tip"})
                commander
                    .getChild(part)
                    .render(pose, baton, light, OverlayTexture.NO_OVERLAY, .75F, .75F, .65F, 1);
            }
          }
        });
  }

  @Override
  protected void scale(AncientSlime slime, PoseStack pose, float partial) {
    pose.scale(2, 2, 2);
  }

  @Override
  public ResourceLocation getTextureLocation(AncientSlime slime) {
    return BODY;
  }
}
