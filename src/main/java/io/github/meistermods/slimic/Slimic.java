package io.github.meistermods.slimic;

import io.github.meistermods.slimic.config.SlimicConfig;
import io.github.meistermods.slimic.network.ModNetwork;
import io.github.meistermods.slimic.registry.ModBlocks;
import io.github.meistermods.slimic.registry.ModCreativeTabs;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModItems;
import io.github.meistermods.slimic.registry.ModMenus;
import io.github.meistermods.slimic.registry.ModRecipes;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Slimic.MOD_ID)
public final class Slimic {
  public static final String MOD_ID = "slimic";

  public Slimic(FMLJavaModLoadingContext context) {
    var bus = context.getModEventBus();
    ModEntities.ENTITY_TYPES.register(bus);
    ModBlocks.BLOCKS.register(bus);
    ModBlocks.BLOCK_ENTITIES.register(bus);
    io.github.meistermods.slimic.registry.ModWorldBlocks.BLOCKS.register(bus);
    io.github.meistermods.slimic.registry.ModWorldBlocks.ITEMS.register(bus);
    io.github.meistermods.slimic.registry.ModWorldgen.FEATURES.register(bus);
    ModItems.ITEMS.register(bus);
    ModMenus.MENUS.register(bus);
    ModRecipes.SERIALIZERS.register(bus);
    ModCreativeTabs.TABS.register(bus);
    context.registerConfig(ModConfig.Type.SERVER, SlimicConfig.SPEC);
    io.github.meistermods.slimic.registry.ModParticles.PARTICLES.register(bus);
    bus.addListener(
        (net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) ->
            event.enqueueWork(io.github.meistermods.slimic.entity.CompanionChunks::register));
    ModNetwork.register();
  }
}
