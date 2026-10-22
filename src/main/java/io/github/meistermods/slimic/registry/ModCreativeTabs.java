package io.github.meistermods.slimic.registry;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.AncientSlime;
import io.github.meistermods.slimic.item.AncientSlimeCoreItem;
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
                        output.accept(ModItems.FISHING_SLIME.get());
                        output.accept(ModItems.ABUNDANCE_SLIME.get());
                        output.accept(ModItems.TUNNEL_SLIME.get());
                        output.accept(ModItems.DRAGON_SLIME.get());
                        output.accept(ModItems.MEOW_SLIME.get());
                        output.accept(ModItems.GLOW_SLIME.get());
                        output.accept(ModItems.COMMAND_SLIME.get());
                        output.accept(ModItems.ENDER_SLIME.get());
                        output.accept(ModItems.ATTENTION_SLIME.get());
                        output.accept(ModItems.WAREHOUSE_SLIME.get());
                        output.accept(ModItems.HEALING_SLIME.get());
                        output.accept(ModItems.MINING_SLIME.get());
                        output.accept(ModItems.ANCIENT_MINING_SLIME.get());
                        output.accept(ModItems.NEXT_GENERATION_MINING_SLIME.get());
                        output.accept(ModItems.ANCIENT_TUNNEL_SLIME.get());
                        output.accept(ModItems.TRANSPORT_SLIME.get());
                        output.accept(ModItems.SIGNALING_SLIME.get());
                        output.accept(ModItems.COMBAT_SLIME.get());
                        output.accept(ModItems.ROYAL_GUARD_SLIME.get());
                        output.accept(ModItems.CLEANING_SLIME.get());
                        output.accept(ModItems.SADDLE_SLIME.get());
                        output.accept(ModItems.FARMING_SLIME.get());
                        output.accept(ModItems.LOGGING_SLIME.get());
                        output.accept(ModItems.GUARDIAN_ANGEL_SLIME.get());
                        output.accept(ModItems.SOGGY_SLIME.get());
                        output.accept(ModItems.SLIME_TRAIL.get());
                        output.accept(ModItems.SLIME_SLAG.get());
                        for (var role : AncientSlime.Role.values())
                          output.accept(AncientSlimeCoreItem.creativeVariant(role));
                        output.accept(ModItems.ANCIENT_COMMAND_SEAL.get());
                        output.accept(ModItems.OLD_COMMAND_FRAGMENT.get());
                        output.accept(ModItems.KINGDOM_MAP_FRAGMENT.get());
                        output.accept(ModItems.ANCIENT_KINGDOM_MAP.get());
                        output.accept(ModItems.ROYAL_SURVEY_COMPASS.get());
                        output.accept(ModItems.ROYAL_COURTYARD_SEAL.get());
                        output.accept(ModItems.ROYAL_GALLERY_SEAL.get());
                        output.accept(ModItems.ROYAL_SYNTHESIS_CORE.get());
                        output.accept(ModItems.ROYAL_SYNTHESIS_STATION.get());
                        output.accept(ModItems.SANCTUM_RELAY.get());
                        output.accept(ModWorldBlocks.block("command_pedestal"));
                        output.accept(ModItems.ANCIENT_GUARD_SLIME_SPAWN_EGG.get());
                        output.accept(ModItems.FORGOTTEN_TRANSPORT_SLIME_SPAWN_EGG.get());
                        output.accept(ModItems.ANCIENT_COMMAND_SLIME_SPAWN_EGG.get());
                        output.accept(ModItems.FORGOTTEN_MINING_SLIME_SPAWN_EGG.get());
                        output.accept(ModItems.FORGOTTEN_TUNNEL_SLIME_SPAWN_EGG.get());
                        output.accept(ModItems.MINE_GUARDIAN_SLIME_SPAWN_EGG.get());
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
                        output.accept(ModItems.SLIME_SCANNER.get());
                        output.accept(ModItems.CREATIVE_SLIME_TUNER.get());
                      })
                  .build());

  public static final RegistryObject<CreativeModeTab> BUILDING =
      TABS.register(
          "slimic_building",
          () ->
              worldTab(
                  "itemGroup.slimic.building",
                  "slimic_stone_bricks",
                  ModWorldBlocks.Category.BUILDING));
  public static final RegistryObject<CreativeModeTab> DECORATIONS =
      TABS.register(
          "slimic_decorations",
          () ->
              worldTab(
                  "itemGroup.slimic.decorations",
                  "gel_lantern",
                  ModWorldBlocks.Category.DECORATION));

  private static CreativeModeTab worldTab(
      String title, String icon, ModWorldBlocks.Category category) {
    return CreativeModeTab.builder()
        .title(Component.translatable(title))
        .icon(() -> new ItemStack(ModWorldBlocks.block(icon)))
        .displayItems(
            (parameters, output) ->
                ModWorldBlocks.entries().stream()
                    .filter(entry -> entry.category() == category)
                    .forEach(entry -> output.accept(entry.item().get())))
        .build();
  }

  private ModCreativeTabs() {}
}
