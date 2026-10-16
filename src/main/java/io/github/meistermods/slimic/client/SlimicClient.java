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
    event.registerEntityRenderer(ModEntities.SADDLE_SLIME.get(), FriendlySlimeRenderer::new);
    event.registerEntityRenderer(ModEntities.FRIENDLY_SLIME.get(), FriendlySlimeRenderer::new);
    event.registerEntityRenderer(ModEntities.MINING_SLIME.get(), FriendlySlimeRenderer::new);
    event.registerEntityRenderer(ModEntities.TRANSPORT_SLIME.get(), FriendlySlimeRenderer::new);
    event.registerEntityRenderer(ModEntities.COMBAT_SLIME.get(), FriendlySlimeRenderer::new);
    event.registerEntityRenderer(ModEntities.CLEANING_SLIME.get(), FriendlySlimeRenderer::new);
    event.registerEntityRenderer(ModEntities.HERALD_SLIME.get(), FriendlySlimeRenderer::new);
  }

  @SubscribeEvent
  public static void registerModelLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
    event.registerLayerDefinition(SaddleSlimeLayer.SADDLE, SaddleSlimeLayer::createLayer);
    event.registerLayerDefinition(CombatSlimeLayer.ARMOR, CombatSlimeLayer::createLayer);
    event.registerLayerDefinition(MiningSlimeLayer.HELMET, MiningSlimeLayer::createLayer);
    event.registerLayerDefinition(TransportSlimeLayer.PACK, TransportSlimeLayer::createLayer);
  }

  @SubscribeEvent
  public static void clientSetup(FMLClientSetupEvent event) {
    event.enqueueWork(() -> MenuScreens.register(ModMenus.SLIME.get(), SlimeScreen::new));
    event.enqueueWork(() -> MenuScreens.register(ModMenus.TUNER.get(), TunerScreen::new));
    event.enqueueWork(
        () ->
            net.minecraft.client.renderer.item.ItemProperties.register(
                io.github.meistermods.slimic.registry.ModItems.SLIME_ARMOR.get(),
                ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "armor_material"),
                (stack, level, entity, seed) ->
                    io.github.meistermods.slimic.item.SlimeArmorItem.materialIndex(stack)));
  }

  @SubscribeEvent
  public static void registerItemColors(
      net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event) {
    event.register(
        (stack, tint) -> {
          var base = io.github.meistermods.slimic.item.SlimeArmorItem.baseArmor(stack);
          return tint == 0
                  && base.getItem() instanceof net.minecraft.world.item.DyeableLeatherItem leather
              ? leather.getColor(base)
              : -1;
        },
        io.github.meistermods.slimic.registry.ModItems.SLIME_ARMOR.get(),
        io.github.meistermods.slimic.registry.ModItems.LEATHER_SLIME_ARMOR.get(),
        io.github.meistermods.slimic.registry.ModItems.CHAINMAIL_SLIME_ARMOR.get(),
        io.github.meistermods.slimic.registry.ModItems.GOLDEN_SLIME_ARMOR.get(),
        io.github.meistermods.slimic.registry.ModItems.DIAMOND_SLIME_ARMOR.get(),
        io.github.meistermods.slimic.registry.ModItems.NETHERITE_SLIME_ARMOR.get());
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
