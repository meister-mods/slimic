package io.github.meistermods.slimic.recipe;

import io.github.meistermods.slimic.item.KingdomMapItem;
import io.github.meistermods.slimic.registry.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Map copies share only the destination and vanilla map id, never ownership of a site reward. */
public final class KingdomMapCopyingRecipe extends CustomRecipe {
  public KingdomMapCopyingRecipe(ResourceLocation id, CraftingBookCategory category) {
    super(id, category);
  }

  @Override
  public boolean matches(CraftingContainer grid, Level level) {
    return !assemble(grid, level.registryAccess()).isEmpty();
  }

  @Override
  public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
    ItemStack source = ItemStack.EMPTY;
    int blanks = 0;
    for (int i = 0; i < grid.getContainerSize(); i++) {
      var item = grid.getItem(i);
      if (item.isEmpty()) continue;
      if (item.is(Items.MAP)) blanks++;
      else if (item.is(ModItems.ANCIENT_KINGDOM_MAP.get())
          && KingdomMapItem.bound(item)
          && source.isEmpty()) source = item;
      else return ItemStack.EMPTY;
    }
    return source.isEmpty() || blanks == 0 ? ItemStack.EMPTY : source.copyWithCount(blanks + 1);
  }

  @Override
  public boolean canCraftInDimensions(int width, int height) {
    return width * height >= 2;
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return ModRecipes.KINGDOM_MAP_COPYING.get();
  }
}
