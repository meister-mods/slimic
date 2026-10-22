package io.github.meistermods.slimic.registry;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.item.CleaningSlimeItem;
import io.github.meistermods.slimic.item.CombatSlimeItem;
import io.github.meistermods.slimic.item.CreativeSlimeTunerItem;
import io.github.meistermods.slimic.item.FarmerSlimeItem;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.item.GuardianAngelSlimeItem;
import io.github.meistermods.slimic.item.HealingSlimeItem;
import io.github.meistermods.slimic.item.HeraldSlimeItem;
import io.github.meistermods.slimic.item.LumberjackSlimeItem;
import io.github.meistermods.slimic.item.MiningSlimeItem;
import io.github.meistermods.slimic.item.SaddleSlimeItem;
import io.github.meistermods.slimic.item.SlimeArmorItem;
import io.github.meistermods.slimic.item.SlimeScannerItem;
import io.github.meistermods.slimic.item.SlimeTunerItem;
import io.github.meistermods.slimic.item.TransportSlimeItem;
import io.github.meistermods.slimic.item.WarehouseSlimeItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
  public static final DeferredRegister<Item> ITEMS =
      DeferredRegister.create(ForgeRegistries.ITEMS, Slimic.MOD_ID);

  public static final RegistryObject<Item> KINGDOM_MAP_FRAGMENT =
      royalRelic("kingdom_map_fragment");
  public static final RegistryObject<Item> ANCIENT_KINGDOM_MAP =
      ITEMS.register(
          "ancient_kingdom_map",
          () ->
              new io.github.meistermods.slimic.item.KingdomMapItem(
                  new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
  public static final RegistryObject<Item> ROYAL_SURVEY_COMPASS =
      ITEMS.register(
          "royal_survey_compass",
          () ->
              new io.github.meistermods.slimic.item.RoyalSurveyCompassItem(
                  new Item.Properties()
                      .stacksTo(1)
                      .rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
  public static final RegistryObject<Item> ROYAL_COURTYARD_SEAL =
      royalRelic("royal_courtyard_seal");
  public static final RegistryObject<Item> ROYAL_GALLERY_SEAL = royalRelic("royal_gallery_seal");
  public static final RegistryObject<Item> ROYAL_SYNTHESIS_CORE =
      royalRelic("royal_synthesis_core");
  public static final RegistryObject<Item> ROYAL_SYNTHESIS_STATION =
      ITEMS.register(
          "royal_synthesis_station",
          () -> new BlockItem(ModBlocks.ROYAL_SYNTHESIS_STATION.get(), new Item.Properties()));
  public static final RegistryObject<Item> SANCTUM_RELAY =
      ITEMS.register(
          "sanctum_relay",
          () -> new BlockItem(ModBlocks.SANCTUM_RELAY.get(), new Item.Properties()));
  public static final RegistryObject<Item> NEXT_GENERATION_MINING_SLIME =
      ITEMS.register(
          "next_generation_mining_slime",
          () ->
              new io.github.meistermods.slimic.item.SpecializedSlimeItem(
                  new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE),
                  ModEntities.NEXT_GENERATION_MINING_SLIME));

  private static RegistryObject<Item> royalRelic(String name) {
    return ITEMS.register(
        name,
        () ->
            new io.github.meistermods.slimic.item.RoyalRelicItem(
                new Item.Properties()
                    .rarity(net.minecraft.world.item.Rarity.UNCOMMON)
                    .fireResistant()));
  }

  public static final RegistryObject<Item> ROYAL_GUARD_SLIME =
      ITEMS.register(
          "royal_guard_slime",
          () ->
              new io.github.meistermods.slimic.item.SpecializedSlimeItem(
                  new Item.Properties(), ModEntities.ROYAL_GUARD_SLIME));
  public static final RegistryObject<Item> ANCIENT_MINING_SLIME =
      ITEMS.register(
          "ancient_mining_slime",
          () ->
              new io.github.meistermods.slimic.item.SpecializedSlimeItem(
                  new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON),
                  ModEntities.ANCIENT_MINING_SLIME));
  public static final RegistryObject<Item> ANCIENT_TUNNEL_SLIME =
      ITEMS.register(
          "ancient_tunnel_slime",
          () ->
              new io.github.meistermods.slimic.item.SpecializedSlimeItem(
                  new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON),
                  ModEntities.ANCIENT_TUNNEL_SLIME));
  public static final RegistryObject<Item> OLD_COMMAND_FRAGMENT =
      ITEMS.register(
          "old_command_fragment",
          () ->
              new Item(
                  new Item.Properties()
                      .rarity(net.minecraft.world.item.Rarity.UNCOMMON)
                      .fireResistant()));
  public static final RegistryObject<Item> FORGOTTEN_MINER_SLIME_SPAWN_EGG =
      ITEMS.register(
          "forgotten_miner_slime_spawn_egg",
          () ->
              new net.minecraftforge.common.ForgeSpawnEggItem(
                  ModEntities.FORGOTTEN_MINER_SLIME, 0x476339, 0xC19B3E, new Item.Properties()));
  public static final RegistryObject<Item> FORGOTTEN_TUNNEL_SLIME_SPAWN_EGG =
      ITEMS.register(
          "forgotten_tunnel_slime_spawn_egg",
          () ->
              new net.minecraftforge.common.ForgeSpawnEggItem(
                  ModEntities.FORGOTTEN_TUNNEL_SLIME, 0x476339, 0x68726D, new Item.Properties()));
  public static final RegistryObject<Item> MINE_GUARDIAN_SLIME_SPAWN_EGG =
      ITEMS.register(
          "mine_guardian_slime_spawn_egg",
          () ->
              new net.minecraftforge.common.ForgeSpawnEggItem(
                  ModEntities.MINE_GUARDIAN_SLIME, 0x384C35, 0xAC7A4B, new Item.Properties()));

  public static final RegistryObject<Item> ANCIENT_COMMAND_SEAL =
      ITEMS.register(
          "ancient_command_seal",
          () ->
              new Item(
                  new Item.Properties()
                      .rarity(net.minecraft.world.item.Rarity.UNCOMMON)
                      .fireResistant()));
  public static final RegistryObject<Item> DAMAGED_ANCIENT_SLIME_CORE =
      ITEMS.register(
          "damaged_ancient_slime_core",
          () ->
              new io.github.meistermods.slimic.item.AncientSlimeCoreItem(
                  new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

  public static final RegistryObject<Item> ANCIENT_GUARD_SLIME_SPAWN_EGG =
      ITEMS.register(
          "ancient_guard_slime_spawn_egg",
          () ->
              new net.minecraftforge.common.ForgeSpawnEggItem(
                  ModEntities.ANCIENT_GUARD_SLIME, 0x476339, 0xD5AC44, new Item.Properties()));
  public static final RegistryObject<Item> FORGOTTEN_TRANSPORT_SLIME_SPAWN_EGG =
      ITEMS.register(
          "forgotten_transport_slime_spawn_egg",
          () ->
              new net.minecraftforge.common.ForgeSpawnEggItem(
                  ModEntities.FORGOTTEN_TRANSPORT_SLIME,
                  0x476339,
                  0x8A582C,
                  new Item.Properties()));
  public static final RegistryObject<Item> ANCIENT_COMMANDER_SLIME_SPAWN_EGG =
      ITEMS.register(
          "ancient_commander_slime_spawn_egg",
          () ->
              new net.minecraftforge.common.ForgeSpawnEggItem(
                  ModEntities.ANCIENT_COMMANDER_SLIME, 0x476339, 0x713C91, new Item.Properties()));

  public static final RegistryObject<Item> FRIENDLY_SLIME =
      ITEMS.register("friendly_slime", () -> new FriendlySlimeItem(new Item.Properties()));

  public static final RegistryObject<Item> MINING_SLIME =
      ITEMS.register("mining_slime", () -> new MiningSlimeItem(new Item.Properties()));

  public static final RegistryObject<Item> TRANSPORT_SLIME =
      ITEMS.register("transport_slime", () -> new TransportSlimeItem(new Item.Properties()));
  public static final RegistryObject<Item> SLIME_CHEST =
      ITEMS.register(
          "slime_chest", () -> new BlockItem(ModBlocks.SLIME_CHEST.get(), new Item.Properties()));

  public static final RegistryObject<Item> COMBAT_SLIME =
      ITEMS.register("combat_slime", () -> new CombatSlimeItem(new Item.Properties()));
  public static final RegistryObject<Item> CLEANING_SLIME =
      ITEMS.register("cleaning_slime", () -> new CleaningSlimeItem(new Item.Properties()));
  public static final RegistryObject<Item> SLIME_ARMOR =
      ITEMS.register(
          "slime_armor",
          () ->
              new SlimeArmorItem(
                  ArmorMaterials.IRON, Items.IRON_CHESTPLATE, new Item.Properties()));

  public static final RegistryObject<Item> LEATHER_SLIME_ARMOR =
      ITEMS.register(
          "leather_slime_armor",
          () ->
              new SlimeArmorItem(
                  ArmorMaterials.LEATHER, Items.LEATHER_CHESTPLATE, new Item.Properties()));
  public static final RegistryObject<Item> CHAINMAIL_SLIME_ARMOR =
      ITEMS.register(
          "chainmail_slime_armor",
          () ->
              new SlimeArmorItem(
                  ArmorMaterials.CHAIN, Items.CHAINMAIL_CHESTPLATE, new Item.Properties()));
  public static final RegistryObject<Item> GOLDEN_SLIME_ARMOR =
      ITEMS.register(
          "golden_slime_armor",
          () ->
              new SlimeArmorItem(
                  ArmorMaterials.GOLD, Items.GOLDEN_CHESTPLATE, new Item.Properties()));
  public static final RegistryObject<Item> DIAMOND_SLIME_ARMOR =
      ITEMS.register(
          "diamond_slime_armor",
          () ->
              new SlimeArmorItem(
                  ArmorMaterials.DIAMOND, Items.DIAMOND_CHESTPLATE, new Item.Properties()));
  public static final RegistryObject<Item> NETHERITE_SLIME_ARMOR =
      ITEMS.register(
          "netherite_slime_armor",
          () ->
              new SlimeArmorItem(
                  ArmorMaterials.NETHERITE,
                  Items.NETHERITE_CHESTPLATE,
                  new Item.Properties().fireResistant()));

  public static final RegistryObject<Item> SADDLE_SLIME =
      ITEMS.register("saddle_slime", () -> new SaddleSlimeItem(new Item.Properties()));

  public static final RegistryObject<Item> BROKEN_SLIME =
      ITEMS.register(
          "broken_slime",
          () ->
              new BlockItem(
                  ModBlocks.BROKEN_SLIME.get(), new Item.Properties().stacksTo(1).fireResistant()));

  public static final RegistryObject<Item> FARMER_SLIME =
      ITEMS.register("farmer_slime", () -> new FarmerSlimeItem(new Item.Properties()));
  public static final RegistryObject<Item> LUMBERJACK_SLIME =
      ITEMS.register("lumberjack_slime", () -> new LumberjackSlimeItem(new Item.Properties()));

  public static final RegistryObject<Item> SLIME_TUNER =
      ITEMS.register("slime_tuner", () -> new SlimeTunerItem(new Item.Properties().stacksTo(1)));
  public static final RegistryObject<Item> SLIME_SCANNER =
      ITEMS.register(
          "slime_scanner", () -> new SlimeScannerItem(new Item.Properties().stacksTo(1)));
  public static final RegistryObject<Item> GUARDIAN_ANGEL_SLIME =
      ITEMS.register(
          "guardian_angel_slime", () -> new GuardianAngelSlimeItem(new Item.Properties()));

  public static final RegistryObject<Item> HEALING_SLIME =
      ITEMS.register("healing_slime", () -> new HealingSlimeItem(new Item.Properties()));
  public static final RegistryObject<Item> WAREHOUSE_SLIME =
      ITEMS.register("warehouse_slime", () -> new WarehouseSlimeItem(new Item.Properties()));

  public static final RegistryObject<Item> FISHING_SLIME =
      ITEMS.register(
          "fishing_slime",
          () ->
              new io.github.meistermods.slimic.item.SpecializedSlimeItem(
                  new Item.Properties(), ModEntities.FISHING_SLIME));
  public static final RegistryObject<Item> ABUNDANCE_SLIME =
      ITEMS.register(
          "abundance_slime",
          () ->
              new io.github.meistermods.slimic.item.SpecializedSlimeItem(
                  new Item.Properties(), ModEntities.ABUNDANCE_SLIME));
  public static final RegistryObject<Item> TUNNEL_SLIME =
      ITEMS.register(
          "tunnel_slime",
          () ->
              new io.github.meistermods.slimic.item.SpecializedSlimeItem(
                  new Item.Properties(), ModEntities.TUNNEL_SLIME));
  public static final RegistryObject<Item> DRAGON_SLIME =
      ITEMS.register(
          "dragon_slime",
          () ->
              new io.github.meistermods.slimic.item.SpecializedSlimeItem(
                  new Item.Properties(), ModEntities.DRAGON_SLIME));
  public static final RegistryObject<Item> MEOW_SLIME =
      ITEMS.register(
          "meow_slime",
          () ->
              new io.github.meistermods.slimic.item.SpecializedSlimeItem(
                  new Item.Properties(), ModEntities.MEOW_SLIME));

  public static final RegistryObject<Item> GLOW_SLIME =
      ITEMS.register(
          "glow_slime",
          () ->
              new io.github.meistermods.slimic.item.SpecializedSlimeItem(
                  new Item.Properties(), ModEntities.GLOW_SLIME));
  public static final RegistryObject<Item> COMMANDER_SLIME =
      ITEMS.register(
          "commander_slime",
          () ->
              new io.github.meistermods.slimic.item.SpecializedSlimeItem(
                  new Item.Properties(), ModEntities.COMMANDER_SLIME));
  public static final RegistryObject<Item> ENDER_SLIME =
      ITEMS.register(
          "ender_slime",
          () ->
              new io.github.meistermods.slimic.item.SpecializedSlimeItem(
                  new Item.Properties(), ModEntities.ENDER_SLIME));
  public static final RegistryObject<Item> ATTENTION_SLIME =
      ITEMS.register(
          "attention_slime",
          () ->
              new io.github.meistermods.slimic.item.SpecializedSlimeItem(
                  new Item.Properties(), ModEntities.ATTENTION_SLIME));
  public static final RegistryObject<Item> SLIME_TELEPORTER =
      ITEMS.register(
          "slime_teleporter",
          () ->
              new io.github.meistermods.slimic.item.SlimeTeleporterItem(
                  new Item.Properties().stacksTo(1)));

  private ModItems() {}

  public static final RegistryObject<Item> SOGGY_SLIME =
      ITEMS.register(
          "soggy_slime",
          () -> new io.github.meistermods.slimic.item.SoggySlimeItem(new Item.Properties()));
  public static final RegistryObject<Item> SLIME_SLAG =
      ITEMS.register("slime_slag", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> SLIME_TRAIL =
      ITEMS.register(
          "slime_trail", () -> new BlockItem(ModBlocks.SLIME_TRAIL.get(), new Item.Properties()));

  public static final RegistryObject<Item> HERALD_SLIME =
      ITEMS.register("herald_slime", () -> new HeraldSlimeItem(new Item.Properties()));
  public static final RegistryObject<Item> CREATIVE_SLIME_TUNER =
      ITEMS.register(
          "creative_slime_tuner",
          () -> new CreativeSlimeTunerItem(new Item.Properties().stacksTo(1)));
}
