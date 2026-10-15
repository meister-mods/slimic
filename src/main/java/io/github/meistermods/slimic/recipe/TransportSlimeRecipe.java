package io.github.meistermods.slimic.recipe;

import io.github.meistermods.slimic.registry.ModItems;
import io.github.meistermods.slimic.registry.ModRecipes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class TransportSlimeRecipe extends CustomRecipe {
  public TransportSlimeRecipe(ResourceLocation id, CraftingBookCategory category) {
    super(id, category);
  }

  @Override
  public boolean matches(CraftingContainer grid, Level level) {
    boolean slime = false, chest = false;
    for (int slot = 0; slot < grid.getContainerSize(); slot++) {
      ItemStack item = grid.getItem(slot);
      if (item.isEmpty()) continue;
      if (!slime && item.is(ModItems.FRIENDLY_SLIME.get())) slime = true;
      else if (!chest && item.is(Items.CHEST)) chest = true;
      else return false;
    }
    return slime && chest;
  }

  public static ItemStack specialize(ItemStack friendly) {
    ItemStack result = new ItemStack(ModItems.TRANSPORT_SLIME.get());
    if (friendly.hasTag()) result.setTag(friendly.getTag().copy());
    return result;
  }

  @Override
  public ItemStack assemble(CraftingContainer grid, RegistryAccess registry) {
    for (int slot = 0; slot < grid.getContainerSize(); slot++)
      if (grid.getItem(slot).is(ModItems.FRIENDLY_SLIME.get()))
        return specialize(grid.getItem(slot));
    return ItemStack.EMPTY;
  }

  @Override
  public boolean canCraftInDimensions(int width, int height) {
    return width * height >= 2;
  }

  @Override
  public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid) {
    return NonNullList.withSize(grid.getContainerSize(), ItemStack.EMPTY);
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return ModRecipes.TRANSPORT_SLIME.get();
  }
}
