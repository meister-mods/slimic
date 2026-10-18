package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.item.SlimeArmorItem;
import io.github.meistermods.slimic.registry.ModBlocks;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModItems;
import io.github.meistermods.slimic.registry.ModMenus;
import java.io.IOException;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
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
    event.registerEntityRenderer(ModEntities.FISHING_SLIME.get(), FriendlySlimeRenderer::new);
    event.registerEntityRenderer(ModEntities.ABUNDANCE_SLIME.get(), FriendlySlimeRenderer::new);
    event.registerEntityRenderer(ModEntities.TUNNEL_SLIME.get(), FriendlySlimeRenderer::new);
    event.registerEntityRenderer(ModEntities.DRAGON_SLIME.get(), FriendlySlimeRenderer::new);
    event.registerEntityRenderer(ModEntities.NYAN_CAT_SLIME.get(), FriendlySlimeRenderer::new);

    event.registerEntityRenderer(ModEntities.WAREHOUSE_SLIME.get(), FriendlySlimeRenderer::new);
    event.registerEntityRenderer(ModEntities.HEALING_SLIME.get(), FriendlySlimeRenderer::new);
    event.registerEntityRenderer(ModEntities.SOGGY_SLIME.get(), FriendlySlimeRenderer::new);
    event.registerEntityRenderer(
        ModEntities.GUARDIAN_ANGEL_SLIME.get(), GuardianAngelSlimeRenderer::new);
    event.registerBlockEntityRenderer(ModBlocks.SLIME_CHEST_ENTITY.get(), SlimeChestRenderer::new);
    event.registerEntityRenderer(ModEntities.LUMBERJACK_SLIME.get(), FriendlySlimeRenderer::new);
    event.registerEntityRenderer(ModEntities.FARMER_SLIME.get(), FriendlySlimeRenderer::new);
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
    event.registerLayerDefinition(AdvancedSlimeLayer.DETAILS, AdvancedSlimeLayer::createLayer);
    event.registerLayerDefinition(HealingSlimeLayer.BAND, HealingSlimeLayer::createLayer);
    event.registerLayerDefinition(ProfessionSlimeLayer.DETAILS, ProfessionSlimeLayer::createLayer);
    event.registerLayerDefinition(
        GuardianAngelSlimeLayer.HALO, GuardianAngelSlimeLayer::createHalo);
    event.registerLayerDefinition(FieldSlimeLayer.HATS, FieldSlimeLayer::createLayer);
    event.registerLayerDefinition(SaddleSlimeLayer.WING_LAYER, SaddleSlimeLayer::createWings);
    event.registerLayerDefinition(SaddleSlimeLayer.SADDLE, SaddleSlimeLayer::createLayer);
    event.registerLayerDefinition(CombatSlimeLayer.ARMOR, CombatSlimeLayer::createLayer);
    event.registerLayerDefinition(MiningSlimeLayer.HELMET, MiningSlimeLayer::createLayer);
    event.registerLayerDefinition(TransportSlimeLayer.PACK, TransportSlimeLayer::createLayer);
  }

  @SubscribeEvent
  public static void clientSetup(FMLClientSetupEvent event) {
    event.enqueueWork(() -> MenuScreens.register(ModMenus.SLIME.get(), SlimeScreen::new));
    event.enqueueWork(
        () -> MenuScreens.register(ModMenus.CREATIVE_TUNER.get(), CreativeSlimeTunerScreen::new));
    event.enqueueWork(
        () ->
            ItemProperties.register(
                ModItems.SLIME_ARMOR.get(),
                ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "armor_material"),
                (stack, level, entity, seed) -> SlimeArmorItem.materialIndex(stack)));
  }

  @SubscribeEvent
  public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
    event.register(
        (stack, tint) -> {
          var base = SlimeArmorItem.baseArmor(stack);
          return tint == 0 && base.getItem() instanceof DyeableLeatherItem leather
              ? leather.getColor(base)
              : -1;
        },
        ModItems.SLIME_ARMOR.get(),
        ModItems.LEATHER_SLIME_ARMOR.get(),
        ModItems.CHAINMAIL_SLIME_ARMOR.get(),
        ModItems.GOLDEN_SLIME_ARMOR.get(),
        ModItems.DIAMOND_SLIME_ARMOR.get(),
        ModItems.NETHERITE_SLIME_ARMOR.get());
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
