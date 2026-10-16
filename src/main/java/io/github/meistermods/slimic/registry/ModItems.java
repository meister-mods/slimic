package io.github.meistermods.slimic.registry;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.item.CleaningSlimeItem;
import io.github.meistermods.slimic.item.CombatSlimeItem;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.item.HeraldSlimeItem;
import io.github.meistermods.slimic.item.MiningSlimeItem;
import io.github.meistermods.slimic.item.SlimeArmorItem;
import io.github.meistermods.slimic.item.SlimeTunerItem;
import io.github.meistermods.slimic.item.TransportSlimeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
  public static final DeferredRegister<Item> ITEMS =
      DeferredRegister.create(ForgeRegistries.ITEMS, Slimic.MOD_ID);

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
                  net.minecraft.world.item.ArmorMaterials.IRON,
                  net.minecraft.world.item.Items.IRON_CHESTPLATE,
                  new Item.Properties()));

  public static final RegistryObject<Item> LEATHER_SLIME_ARMOR =
      ITEMS.register(
          "leather_slime_armor",
          () ->
              new SlimeArmorItem(
                  net.minecraft.world.item.ArmorMaterials.LEATHER,
                  net.minecraft.world.item.Items.LEATHER_CHESTPLATE,
                  new Item.Properties()));
  public static final RegistryObject<Item> CHAINMAIL_SLIME_ARMOR =
      ITEMS.register(
          "chainmail_slime_armor",
          () ->
              new SlimeArmorItem(
                  net.minecraft.world.item.ArmorMaterials.CHAIN,
                  net.minecraft.world.item.Items.CHAINMAIL_CHESTPLATE,
                  new Item.Properties()));
  public static final RegistryObject<Item> GOLDEN_SLIME_ARMOR =
      ITEMS.register(
          "golden_slime_armor",
          () ->
              new SlimeArmorItem(
                  net.minecraft.world.item.ArmorMaterials.GOLD,
                  net.minecraft.world.item.Items.GOLDEN_CHESTPLATE,
                  new Item.Properties()));
  public static final RegistryObject<Item> DIAMOND_SLIME_ARMOR =
      ITEMS.register(
          "diamond_slime_armor",
          () ->
              new SlimeArmorItem(
                  net.minecraft.world.item.ArmorMaterials.DIAMOND,
                  net.minecraft.world.item.Items.DIAMOND_CHESTPLATE,
                  new Item.Properties()));
  public static final RegistryObject<Item> NETHERITE_SLIME_ARMOR =
      ITEMS.register(
          "netherite_slime_armor",
          () ->
              new SlimeArmorItem(
                  net.minecraft.world.item.ArmorMaterials.NETHERITE,
                  net.minecraft.world.item.Items.NETHERITE_CHESTPLATE,
                  new Item.Properties().fireResistant()));

  private ModItems() {}

  public static final RegistryObject<Item> HERALD_SLIME =
      ITEMS.register("herald_slime", () -> new HeraldSlimeItem(new Item.Properties()));
  public static final RegistryObject<Item> SLIME_TUNER =
      ITEMS.register("slime_tuner", () -> new SlimeTunerItem(new Item.Properties().stacksTo(1)));
}
