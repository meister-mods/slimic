package io.github.meistermods.slimic.compat.jei;

import io.github.meistermods.slimic.entity.AncientSlime;
import io.github.meistermods.slimic.item.AncientSlimeCoreItem;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** A display recipe for the station interaction, separate from workbench crafting. */
public record SlimeRestorationRecipe(AncientSlime.Role role) {
  public static List<SlimeRestorationRecipe> all() {
    return Arrays.stream(AncientSlime.Role.values()).map(SlimeRestorationRecipe::new).toList();
  }

  public ItemStack core() {
    return AncientSlimeCoreItem.creativeVariant(role);
  }

  public ItemStack reagent() {
    return new ItemStack(
        role.isMine() ? ModItems.OLD_COMMAND_FRAGMENT.get() : ModItems.ANCIENT_COMMAND_SEAL.get());
  }

  public ItemStack result() {
    return new ItemStack(
        switch (role) {
          case GUARD, MINE_GUARD -> ModItems.ROYAL_GUARD_SLIME.get();
          case TRANSPORT -> ModItems.TRANSPORT_SLIME.get();
          case COMMAND -> ModItems.COMMAND_SLIME.get();
          case MINING -> ModItems.ANCIENT_MINING_SLIME.get();
          case TUNNEL -> ModItems.ANCIENT_TUNNEL_SLIME.get();
        });
  }
}
