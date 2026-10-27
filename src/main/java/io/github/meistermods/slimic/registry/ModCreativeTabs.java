package io.github.meistermods.slimic.registry;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.AncientSlime;
import io.github.meistermods.slimic.item.AncientSlimeCoreItem;
import io.github.meistermods.slimic.item.SlimeArmorItem;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
  private static final List<String> BUILDING_MATERIALS =
      List.of(
          "slimic_stone",
          "slimic_cobblestone",
          "mossy_slimic_cobblestone",
          "pale_slimic_stone",
          "polished_slimic_stone",
          "slimic_stone_bricks",
          "cracked_slimic_stone_bricks",
          "mossy_slimic_stone_bricks",
          "chiseled_slimic_stone_bricks",
          "slimic_tiles",
          "cracked_slimic_tiles",
          "ancient_foundation",
          "ancient_foundation_bricks",
          "slime_marble",
          "slime_marble_bricks",
          "chiseled_slime_marble",
          "black_slime_stone",
          "polished_black_slime_stone",
          "slimic_slate",
          "slimic_slate_bricks",
          "ruined_bricks",
          "slime_infused_planks",
          "slimic_soil",
          "damp_grass_block",
          "slime_silt",
          "slimic_clay",
          "slime_mud",
          "congealed_slime",
          "dried_slime",
          "hardened_slime",
          "luminous_slime",
          "gel_glass");
  private static final List<String> DECORATION_ORDER =
      List.of(
          // Lights and crystals.
          "gel_lantern",
          "ancient_brazier",
          "dormant_beacon",
          "gel_crystal_cluster",
          "small_gel_crystal_cluster",
          // Masonry, markers, and records.
          "broken_pillar",
          "fallen_pillar",
          "fallen_masonry_pile",
          "royal_waymarker",
          "weathered_waymarker",
          "royal_crest",
          "old_slime_banner",
          "stone_tablet",
          "cracked_stone_tablet",
          "relic_shard_pile",
          // Workshop remnants and furniture.
          "cracked_command_pedestal",
          "broken_workbench",
          "tool_rack",
          "abandoned_crate",
          "broken_crate",
          "supply_urn",
          "cracked_supply_urn",
          "gel_basin",
          "dried_gel_basin",
          "low_stone_table",
          "stone_bench",
          "ruined_bench",
          // Plants.
          "slime_roots",
          "slime_tuft",
          "gel_cap",
          "glowing_bloom");

  public static final DeferredRegister<CreativeModeTab> TABS =
      DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Slimic.MOD_ID);
  public static final RegistryObject<CreativeModeTab> SLIMIC =
      TABS.register(
          "slimic",
          () ->
              CreativeModeTab.builder()
                  .title(Component.translatable("itemGroup.slimic"))
                  .icon(() -> new ItemStack(ModItems.FRIENDLY_SLIME.get()))
                  .displayItems((parameters, output) -> mainItems(output))
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

  private static void mainItems(CreativeModeTab.Output output) {
    // Basic companions and the mining/tunneling upgrade lines.
    accept(output, ModItems.FRIENDLY_SLIME.get(), ModItems.ATTENTION_SLIME.get());
    accept(
        output,
        ModItems.MINING_SLIME.get(),
        ModItems.ANCIENT_MINING_SLIME.get(),
        ModItems.NEXT_GENERATION_MINING_SLIME.get(),
        ModItems.TUNNEL_SLIME.get(),
        ModItems.ANCIENT_TUNNEL_SLIME.get(),
        ModItems.NEXT_GENERATION_TUNNEL_SLIME.get(),
        ModItems.FARMING_SLIME.get(),
        ModItems.LOGGING_SLIME.get(),
        ModItems.FISHING_SLIME.get());
    // Logistics, combat, and support.
    accept(
        output,
        ModItems.TRANSPORT_SLIME.get(),
        ModItems.WAREHOUSE_SLIME.get(),
        ModItems.CLEANING_SLIME.get());
    accept(
        output,
        ModItems.COMBAT_SLIME.get(),
        ModItems.ROYAL_GUARD_SLIME.get(),
        ModItems.DRAGON_SLIME.get(),
        ModItems.GUARDIAN_ANGEL_SLIME.get());
    accept(
        output,
        ModItems.HEALING_SLIME.get(),
        ModItems.ABUNDANCE_SLIME.get(),
        ModItems.SIGNALING_SLIME.get(),
        ModItems.COMMAND_SLIME.get());
    // Riding and special abilities complete the companion group.
    accept(
        output,
        ModItems.SADDLE_SLIME.get(),
        ModItems.MEOW_SLIME.get(),
        ModItems.GLOW_SLIME.get(),
        ModItems.SOGGY_SLIME.get(),
        ModItems.ENDER_SLIME.get());
    accept(
        output,
        ModItems.SLIME_TUNER.get(),
        ModItems.SLIME_SCANNER.get(),
        ModItems.CREATIVE_SLIME_TUNER.get());
    for (var chestplate :
        new Item[] {
          Items.LEATHER_CHESTPLATE,
          Items.CHAINMAIL_CHESTPLATE,
          Items.IRON_CHESTPLATE,
          Items.GOLDEN_CHESTPLATE,
          Items.DIAMOND_CHESTPLATE,
          Items.NETHERITE_CHESTPLATE
        }) output.accept(SlimeArmorItem.fromChestplate(new ItemStack(chestplate)));
    // Functional blocks stay together, separate from decorative ruins.
    accept(
        output,
        ModItems.SLIME_CHEST.get(),
        ModWorldBlocks.block("command_pedestal"),
        ModItems.ROYAL_SYNTHESIS_STATION.get(),
        ModItems.COLONY_BONDING_STATION.get(),
        ModItems.SANCTUM_RELAY.get(),
        ModItems.ASSEMBLY_CORE.get());
    accept(
        output,
        ModItems.KINGDOM_MAP_FRAGMENT.get(),
        ModItems.ANCIENT_KINGDOM_MAP.get(),
        ModItems.ROYAL_SURVEY_COMPASS.get(),
        ModItems.ASSEMBLY_CLUE.get());
    // Slime materials, restoration cores, expedition rewards, and records.
    accept(
        output, ModItems.SLIME_SLAG.get(), ModItems.SLIME_TRAIL.get(), ModItems.BROKEN_SLIME.get());
    for (var role : AncientSlime.Role.values())
      output.accept(AncientSlimeCoreItem.creativeVariant(role));
    accept(
        output,
        ModItems.ANCIENT_COMMAND_SEAL.get(),
        ModItems.DIMENSIONAL_ATTUNEMENT_SEAL.get(),
        ModItems.OLD_COMMAND_FRAGMENT.get(),
        ModItems.ROYAL_COURTYARD_SEAL.get(),
        ModItems.ROYAL_GALLERY_SEAL.get(),
        ModItems.ROYAL_SYNTHESIS_CORE.get(),
        ModItems.COLONY_HEART.get(),
        ModItems.COHESION_SHARD.get(),
        ModItems.LAST_RALLY_RECORD.get(),
        ModItems.ENDLESS_ASSEMBLY_RECORD.get());
    accept(
        output,
        ModItems.ANCIENT_GUARD_SLIME_SPAWN_EGG.get(),
        ModItems.FORGOTTEN_TRANSPORT_SLIME_SPAWN_EGG.get(),
        ModItems.ANCIENT_COMMAND_SLIME_SPAWN_EGG.get(),
        ModItems.FORGOTTEN_MINING_SLIME_SPAWN_EGG.get(),
        ModItems.FORGOTTEN_TUNNEL_SLIME_SPAWN_EGG.get(),
        ModItems.MINE_GUARDIAN_SLIME_SPAWN_EGG.get(),
        ModItems.AIMLESS_COLONY_SPAWN_EGG.get(),
        ModItems.ATTACK_COLONY_FRAGMENT_SPAWN_EGG.get(),
        ModItems.COHESION_COLONY_FRAGMENT_SPAWN_EGG.get(),
        ModItems.RETURNING_COLONY_FRAGMENT_SPAWN_EGG.get());
  }

  private static void accept(CreativeModeTab.Output output, ItemLike... items) {
    for (var item : items) output.accept(item);
  }

  private static String material(ModWorldBlocks.Entry entry) {
    if (entry.category() == ModWorldBlocks.Category.BUILDING)
      for (var suffix : List.of("_stairs", "_slab", "_wall"))
        if (entry.id().endsWith(suffix))
          return entry.id().substring(0, entry.id().length() - suffix.length());
    return entry.id();
  }

  private static int shapeOrder(ModWorldBlocks.Entry entry) {
    if (entry.id().endsWith("_stairs")) return 1;
    if (entry.id().endsWith("_slab")) return 2;
    if (entry.id().endsWith("_wall")) return 3;
    return 0;
  }

  private static CreativeModeTab worldTab(
      String title, String icon, ModWorldBlocks.Category category) {
    var order =
        category == ModWorldBlocks.Category.BUILDING ? BUILDING_MATERIALS : DECORATION_ORDER;
    var sorting =
        Comparator.comparingInt(
                (ModWorldBlocks.Entry entry) -> {
                  int index = order.indexOf(material(entry));
                  return index < 0 ? Integer.MAX_VALUE : index;
                })
            .thenComparing(ModCreativeTabs::material)
            .thenComparingInt(ModCreativeTabs::shapeOrder)
            .thenComparing(ModWorldBlocks.Entry::id);
    return CreativeModeTab.builder()
        .title(Component.translatable(title))
        .icon(() -> new ItemStack(ModWorldBlocks.block(icon)))
        .displayItems(
            (parameters, output) ->
                ModWorldBlocks.entries().stream()
                    .filter(entry -> entry.category() == category)
                    .sorted(sorting)
                    .forEach(entry -> output.accept(entry.item().get())))
        .build();
  }

  private ModCreativeTabs() {}
}
