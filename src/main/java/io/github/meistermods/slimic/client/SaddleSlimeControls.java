package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.SaddleSlime;
import io.github.meistermods.slimic.network.ModNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Slimic.MOD_ID, value = Dist.CLIENT)
public final class SaddleSlimeControls {
  public static final KeyMapping DESCEND =
      new KeyMapping(
          "key.slimic.descend",
          KeyConflictContext.IN_GAME,
          InputConstants.Type.KEYSYM,
          GLFW.GLFW_KEY_LEFT_CONTROL,
          "key.categories.slimic");

  private SaddleSlimeControls() {}

  @Mod.EventBusSubscriber(
      modid = Slimic.MOD_ID,
      bus = Mod.EventBusSubscriber.Bus.MOD,
      value = Dist.CLIENT)
  public static final class Registration {
    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
      event.register(DESCEND);
    }
  }

  @SubscribeEvent
  public static void tick(TickEvent.ClientTickEvent event) {
    if (event.phase != TickEvent.Phase.END) return;
    var client = Minecraft.getInstance();
    if (client.player == null || !(client.player.getVehicle() instanceof SaddleSlime slime)) return;
    boolean up = client.screen == null && client.options.keyJump.isDown();
    boolean down = client.screen == null && DESCEND.isDown();
    // Native riding packets supply steering; this packet only supplies vertical/jump intent.
    if (slime.setRiderInput(client.player, up, down))
      ModNetwork.CHANNEL.sendToServer(new ModNetwork.RideSlime(slime.getId(), up, down));
  }
}
