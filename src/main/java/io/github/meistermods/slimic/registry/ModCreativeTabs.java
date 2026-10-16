package io.github.meistermods.slimic.registry;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.item.SlimeArmorItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
                        output.accept(ModItems.WAREHOUSE_SLIME.get());
                        output.accept(ModItems.HEALING_SLIME.get());
                        output.accept(ModItems.MINING_SLIME.get());
                        output.accept(ModItems.TRANSPORT_SLIME.get());
                        output.accept(ModItems.HERALD_SLIME.get());
                        output.accept(ModItems.COMBAT_SLIME.get());
                        output.accept(ModItems.CLEANING_SLIME.get());
                        output.accept(ModItems.SADDLE_SLIME.get());
                        output.accept(ModItems.FARMER_SLIME.get());
                        output.accept(ModItems.LUMBERJACK_SLIME.get());
                        output.accept(ModItems.GUARDIAN_ANGEL_SLIME.get());
                        output.accept(ModItems.SOGGY_SLIME.get());
                        output.accept(ModItems.SLIME_TRAIL.get());
                        output.accept(ModItems.SLIME_SLAG.get());
                        output.accept(ModItems.SLIME_CHEST.get());
                        output.accept(ModItems.BROKEN_SLIME.get());
                        for (var chestplate :
                            new Item[] {
                              Items.LEATHER_CHESTPLATE,
                              Items.CHAINMAIL_CHESTPLATE,
                              Items.IRON_CHESTPLATE,
                              Items.GOLDEN_CHESTPLATE,
                              Items.DIAMOND_CHESTPLATE,
                              Items.NETHERITE_CHESTPLATE
                            })
                          output.accept(SlimeArmorItem.fromChestplate(new ItemStack(chestplate)));
                        output.accept(ModItems.SLIME_TUNER.get());
                        output.accept(ModItems.CREATIVE_SLIME_TUNER.get());
                      })
                  .build());

  private ModCreativeTabs() {}
}
