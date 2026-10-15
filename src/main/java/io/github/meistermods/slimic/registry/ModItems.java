package io.github.meistermods.slimic.registry;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.item.MiningSlimeItem;
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

  private ModItems() {}
}
