package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModMenus;
import java.io.IOException;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(
    modid = Slimic.MOD_ID,
    bus = Mod.EventBusSubscriber.Bus.MOD,
    value = Dist.CLIENT)
public final class SlimicClient {
  private SlimicClient() {}

  @SubscribeEvent
  public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
    event.registerEntityRenderer(ModEntities.FRIENDLY_SLIME.get(), FriendlySlimeRenderer::new);
    event.registerEntityRenderer(ModEntities.MINING_SLIME.get(), FriendlySlimeRenderer::new);
    event.registerEntityRenderer(ModEntities.TRANSPORT_SLIME.get(), FriendlySlimeRenderer::new);
    event.registerEntityRenderer(ModEntities.HERALD_SLIME.get(), FriendlySlimeRenderer::new);
  }

  @SubscribeEvent
  public static void registerModelLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
    event.registerLayerDefinition(MiningSlimeLayer.HELMET, MiningSlimeLayer::createLayer);
    event.registerLayerDefinition(TransportSlimeLayer.PACK, TransportSlimeLayer::createLayer);
  }

  @SubscribeEvent
  public static void clientSetup(FMLClientSetupEvent event) {
    event.enqueueWork(() -> MenuScreens.register(ModMenus.SLIME.get(), SlimeScreen::new));
    event.enqueueWork(() -> MenuScreens.register(ModMenus.TUNER.get(), TunerScreen::new));
  }

  @SubscribeEvent
  public static void registerShaders(RegisterShadersEvent event) throws IOException {
    event.registerShader(
        new ShaderInstance(
            event.getResourceProvider(),
            ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "dyed_slime"),
            DefaultVertexFormat.NEW_ENTITY),
        shader -> DyedSlimeRenderType.shader = shader);
  }
}
