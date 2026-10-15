package io.github.meistermods.slimic.registry;

import io.github.meistermods.slimic.Slimic;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
  public static final DeferredRegister<CreativeModeTab> TABS =
      DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Slimic.MOD_ID);
  public static final RegistryObject<CreativeModeTab> SLIMIC =
      TABS.register(
          "slimic",
          () ->
              CreativeModeTab.builder()
                  .title(Component.translatable("itemGroup.slimic"))
                  .icon(() -> new ItemStack(ModItems.FRIENDLY_SLIME.get()))
                  .displayItems(
                      (parameters, output) -> {
                        output.accept(ModItems.FRIENDLY_SLIME.get());
                        output.accept(ModItems.MINING_SLIME.get());
                        output.accept(ModItems.TRANSPORT_SLIME.get());
                        output.accept(ModItems.HERALD_SLIME.get());
                        output.accept(ModItems.SLIME_CHEST.get());
                        output.accept(ModItems.SLIME_TUNER.get());
                      })
                  .build());

  private ModCreativeTabs() {}
}
