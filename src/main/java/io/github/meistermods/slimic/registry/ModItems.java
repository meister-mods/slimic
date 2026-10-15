package io.github.meistermods.slimic.registry;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.item.MiningSlimeItem;
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

  private ModItems() {}
}
