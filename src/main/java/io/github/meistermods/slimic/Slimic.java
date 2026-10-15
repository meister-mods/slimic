package io.github.meistermods.slimic;

import io.github.meistermods.slimic.network.ModNetwork;
import io.github.meistermods.slimic.registry.ModBlocks;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModItems;
import io.github.meistermods.slimic.registry.ModMenus;
import io.github.meistermods.slimic.registry.ModRecipes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Slimic.MOD_ID)
public final class Slimic {
  public static final String MOD_ID = "slimic";

  public Slimic(FMLJavaModLoadingContext context) {
    var bus = context.getModEventBus();
    ModEntities.ENTITY_TYPES.register(bus);
    ModBlocks.BLOCKS.register(bus);
    ModBlocks.BLOCK_ENTITIES.register(bus);
    ModItems.ITEMS.register(bus);
    ModMenus.MENUS.register(bus);
    ModRecipes.SERIALIZERS.register(bus);
    ModNetwork.register();
    bus.addListener(this::addCreativeItems);
  }

  private void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
    if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
      event.accept(ModItems.FRIENDLY_SLIME);
      event.accept(ModItems.MINING_SLIME);
      event.accept(ModItems.TRANSPORT_SLIME);
    }
    if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(ModItems.SLIME_CHEST);
  }
}
