package io.github.meistermods.slimic.client;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
    modid = Slimic.MOD_ID,
    bus = Mod.EventBusSubscriber.Bus.MOD,
    value = Dist.CLIENT)
public final class SlimicClient {
  private SlimicClient() {}

  @SubscribeEvent
  public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
    event.registerEntityRenderer(ModEntities.FRIENDLY_SLIME.get(), FriendlySlimeRenderer::new);
  }
}
