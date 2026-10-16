package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.ModItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Existing management tests explicitly equip the required Survival tool. */
final class TestMenus {
  static SlimeMenu create(int id, Inventory inventory, FriendlySlime slime) {
    inventory.player.setItemInHand(
        InteractionHand.OFF_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
    return new SlimeMenu(id, inventory, slime);
  }

  static SlimeMenu create(int id, Inventory inventory, FriendlyByteBuf buffer) {
    inventory.player.setItemInHand(
        InteractionHand.OFF_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
    return new SlimeMenu(id, inventory, buffer);
  }
}
