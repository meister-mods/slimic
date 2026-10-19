package io.github.meistermods.slimic.recipe;

import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.item.MiningSlimeItem;
import io.github.meistermods.slimic.registry.ModItems;
import io.github.meistermods.slimic.registry.ModRecipes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class MiningSlimeRecipe extends CustomRecipe {
  public MiningSlimeRecipe(ResourceLocation id, CraftingBookCategory category) {
    super(id, category);
  }

  @Override
  public boolean matches(CraftingContainer grid, Level level) {
    boolean slime = false, tool = false;
    for (int slot = 0; slot < grid.getContainerSize(); slot++) {
      ItemStack item = grid.getItem(slot);
      if (item.isEmpty()) continue;
      if (!slime && CompanionBase.accepts(item)) slime = true;
      else if (!tool && MiningSlime.isPickaxe(item)) tool = true;
      else return false;
    }
    return slime && tool;
  }

  public static ItemStack specialize(ItemStack friendly, ItemStack pickaxe) {
    ItemStack result = new ItemStack(ModItems.MINING_SLIME.get());
    CompanionBase.copyData(friendly, result);
    ItemStack tool = pickaxe.copy();
    tool.setCount(1);
    result.getOrCreateTag().put(MiningSlimeItem.INITIAL_PICKAXE, tool.save(new CompoundTag()));
    return result;
  }

  @Override
  public ItemStack assemble(CraftingContainer grid, RegistryAccess registry) {
    ItemStack friendly = ItemStack.EMPTY, pickaxe = ItemStack.EMPTY;
    for (int slot = 0; slot < grid.getContainerSize(); slot++) {
      ItemStack item = grid.getItem(slot);
      if (CompanionBase.accepts(item)) friendly = item;
      else if (MiningSlime.isPickaxe(item)) pickaxe = item;
    }
    return friendly.isEmpty() || pickaxe.isEmpty()
        ? ItemStack.EMPTY
        : specialize(friendly, pickaxe);
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
    return ModRecipes.MINING_SLIME.get();
  }
}
