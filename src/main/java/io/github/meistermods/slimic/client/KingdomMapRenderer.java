package io.github.meistermods.slimic.client;

import com.mojang.math.Axis;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.item.KingdomMapItem;
import io.github.meistermods.slimic.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.MapItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The native map renderer supplies player arrows, target decorations and the synchronized map id.
 */
@Mod.EventBusSubscriber(modid = Slimic.MOD_ID, value = Dist.CLIENT)
public final class KingdomMapRenderer {
  private static final RenderType BACKGROUND =
      RenderType.text(
          ResourceLocation.withDefaultNamespace("textures/map/map_background_checkerboard.png"));

  private KingdomMapRenderer() {}

  @SubscribeEvent
  public static void hand(RenderHandEvent event) {
    var stack = event.getItemStack();
    if (!stack.is(ModItems.ANCIENT_KINGDOM_MAP.get()) || !KingdomMapItem.bound(stack)) return;
    var minecraft = Minecraft.getInstance();
    var data = MapItem.getSavedData(stack, minecraft.level);
    var id = MapItem.getMapId(stack);
    if (data == null || id == null) return;
    event.setCanceled(true);
    var pose = event.getPoseStack();
    pose.pushPose();
    var arm =
        event.getHand() == InteractionHand.MAIN_HAND
            ? minecraft.player.getMainArm()
            : minecraft.player.getMainArm().getOpposite();
    float side = arm == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1;
    pose.translate(side * .18, -.24 - event.getEquipProgress() * .4, -.85);
    pose.mulPose(Axis.XP.rotationDegrees(180));
    pose.mulPose(Axis.ZP.rotationDegrees(side * -5));
    pose.scale(.0048F, .0048F, .0048F);
    pose.translate(-64, -64, 0);
    var matrix = pose.last().pose();
    var vertices = event.getMultiBufferSource().getBuffer(BACKGROUND);
    int light = event.getPackedLight();
    vertices.vertex(matrix, -7, 135, 0).color(255, 255, 255, 255).uv(0, 1).uv2(light).endVertex();
    vertices.vertex(matrix, 135, 135, 0).color(255, 255, 255, 255).uv(1, 1).uv2(light).endVertex();
    vertices.vertex(matrix, 135, -7, 0).color(255, 255, 255, 255).uv(1, 0).uv2(light).endVertex();
    vertices.vertex(matrix, -7, -7, 0).color(255, 255, 255, 255).uv(0, 0).uv2(light).endVertex();
    minecraft
        .gameRenderer
        .getMapRenderer()
        .render(pose, event.getMultiBufferSource(), id, data, false, light);
    pose.popPose();
  }
}
