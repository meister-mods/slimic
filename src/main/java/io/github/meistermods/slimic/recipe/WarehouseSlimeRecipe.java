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

public final class WarehouseSlimeRecipe extends CustomRecipe {
  public WarehouseSlimeRecipe(ResourceLocation id, CraftingBookCategory category) {
    super(id, category);
  }

  @Override
  public boolean matches(CraftingContainer grid, Level level) {
    int slimes = 0, chests = 0;
    for (int slot = 0; slot < grid.getContainerSize(); slot++) {
      ItemStack stack = grid.getItem(slot);
      if (stack.is(ModItems.TRANSPORT_SLIME.get())) slimes++;
      else if (stack.is(Items.CHEST)) chests++;
      else if (!stack.isEmpty()) return false;
    }
    return slimes == 1 && chests == 8;
  }

  public static ItemStack specialize(ItemStack transport) {
    ItemStack result = new ItemStack(ModItems.WAREHOUSE_SLIME.get());
    if (transport.hasTag()) {
      result.setTag(transport.getTag().copy());
      var data = result.getTag().getCompound("SlimeData");
      // A new profession starts in Wait; the shared cargo, identity and stats remain intact.
      data.remove("TransportWorking");
      data.remove("TransportResting");
      data.remove("TransportStatus");
      data.remove("TransportHome");
      data.remove("TransportTravelBlocked");
    }
    return result;
  }

  @Override
  public ItemStack assemble(CraftingContainer grid, RegistryAccess registry) {
    if (!matches(grid, null)) return ItemStack.EMPTY;
    for (int slot = 0; slot < grid.getContainerSize(); slot++)
      if (grid.getItem(slot).is(ModItems.TRANSPORT_SLIME.get()))
        return specialize(grid.getItem(slot));
    return ItemStack.EMPTY;
  }

  @Override
  public boolean canCraftInDimensions(int width, int height) {
    return width >= 3 && height >= 3;
  }

  @Override
  public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid) {
    return NonNullList.withSize(grid.getContainerSize(), ItemStack.EMPTY);
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return ModRecipes.WAREHOUSE_SLIME.get();
  }
}
