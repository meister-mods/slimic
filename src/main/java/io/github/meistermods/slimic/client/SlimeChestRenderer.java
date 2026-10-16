package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.meistermods.slimic.block.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "slimic", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SlimeChestRenderer implements BlockEntityRenderer<SlimeChestBlockEntity> {
  private static final ResourceLocation
      BASE = ResourceLocation.fromNamespaceAndPath("slimic", "block/slime_chest_base"),
      LID = ResourceLocation.fromNamespaceAndPath("slimic", "block/slime_chest_lid");

  public SlimeChestRenderer(BlockEntityRendererProvider.Context context) {}

  @SubscribeEvent
  public static void registerModels(ModelEvent.RegisterAdditional event) {
    event.register(BASE);
    event.register(LID);
  }

  @Override
  public void render(
      SlimeChestBlockEntity chest,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    pose.pushPose();
    pose.translate(.5, 0, .5);
    pose.mulPose(
        Axis.YP.rotationDegrees(
            180 - chest.getBlockState().getValue(SlimeChestBlock.FACING).toYRot()));
    pose.translate(-.5, 0, -.5);
    draw(BASE, chest, pose, buffers, light, overlay);
    float open = chest.getOpenness(partial);
    open = 1 - (1 - open) * (1 - open) * (1 - open);
    pose.translate(0, 9 / 16D, 15 / 16D);
    pose.mulPose(Axis.XP.rotation(open * (float) Math.PI / 2));
    pose.translate(0, -9 / 16D, -15 / 16D);
    draw(LID, chest, pose, buffers, light, overlay);
    pose.popPose();
  }

  private void draw(
      ResourceLocation id,
      SlimeChestBlockEntity chest,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    var dispatcher = Minecraft.getInstance().getBlockRenderer();
    BakedModel model = Minecraft.getInstance().getModelManager().getModel(id);
    dispatcher
        .getModelRenderer()
        .renderModel(
            pose.last(),
            buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)),
            chest.getBlockState(),
            model,
            1,
            1,
            1,
            light,
            overlay,
            ModelData.EMPTY,
            RenderType.cutout());
  }
}
