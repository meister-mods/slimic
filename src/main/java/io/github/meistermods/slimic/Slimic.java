package io.github.meistermods.slimic;

import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModItems;
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
    ModItems.ITEMS.register(bus);
    bus.addListener(this::addCreativeItems);
  }

  private void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
    if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
      event.accept(ModItems.FRIENDLY_SLIME);
    }
  }
}
