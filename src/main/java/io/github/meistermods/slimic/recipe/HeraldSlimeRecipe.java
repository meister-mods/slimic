package io.github.meistermods.slimic.recipe;

import io.github.meistermods.slimic.item.HeraldSlimeItem;
import io.github.meistermods.slimic.registry.ModItems;
import io.github.meistermods.slimic.registry.ModRecipes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class HeraldSlimeRecipe extends CustomRecipe {
  public HeraldSlimeRecipe(ResourceLocation id, CraftingBookCategory category) {
    super(id, category);
  }

  @Override
  public boolean matches(CraftingContainer grid, Level level) {
    boolean slime = false, rocket = false;
    for (int slot = 0; slot < grid.getContainerSize(); slot++) {
      ItemStack item = grid.getItem(slot);
      if (item.isEmpty()) continue;
      if (!slime && item.is(ModItems.FRIENDLY_SLIME.get())) slime = true;
      else if (!rocket && item.is(Items.FIREWORK_ROCKET)) rocket = true;
      else return false;
    }
    return slime && rocket;
  }

  public static ItemStack specialize(ItemStack friendly, ItemStack rocket) {
    ItemStack result = new ItemStack(ModItems.HERALD_SLIME.get());
    if (friendly.hasTag()) result.setTag(friendly.getTag().copy());
    result
        .getOrCreateTag()
        .put(HeraldSlimeItem.INITIAL_FIREWORK, rocket.copyWithCount(1).save(new CompoundTag()));
    return result;
  }

  @Override
  public ItemStack assemble(CraftingContainer grid, RegistryAccess registry) {
    ItemStack friendly = ItemStack.EMPTY, rocket = ItemStack.EMPTY;
    for (int slot = 0; slot < grid.getContainerSize(); slot++) {
      ItemStack item = grid.getItem(slot);
      if (item.is(ModItems.FRIENDLY_SLIME.get())) friendly = item;
      else if (item.is(Items.FIREWORK_ROCKET)) rocket = item;
    }
    return friendly.isEmpty() || rocket.isEmpty() ? ItemStack.EMPTY : specialize(friendly, rocket);
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
    return ModRecipes.HERALD_SLIME.get();
  }
}
