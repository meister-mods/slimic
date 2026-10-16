package io.github.meistermods.slimic.recipe;

import io.github.meistermods.slimic.registry.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

public final class GuardianAngelSlimeRecipe extends CustomRecipe {
  public GuardianAngelSlimeRecipe(ResourceLocation id, CraftingBookCategory category) {
    super(id, category);
  }

  @Override
  public boolean matches(CraftingContainer grid, Level level) {
    int friendly = 0, star = 0, totem = 0;
    for (int i = 0; i < grid.getContainerSize(); i++) {
      var item = grid.getItem(i);
      if (item.isEmpty()) continue;
      if (item.is(ModItems.FRIENDLY_SLIME.get())) friendly++;
      else if (item.is(Items.NETHER_STAR)) star++;
      else if (item.is(Items.TOTEM_OF_UNDYING)) totem++;
      else return false;
    }
    return friendly == 1 && star == 1 && totem == 1;
  }

  public static ItemStack specialize(ItemStack friendly, ItemStack totem) {
    var result = new ItemStack(ModItems.GUARDIAN_ANGEL_SLIME.get());
    if (friendly.hasTag()) result.setTag(friendly.getTag().copy());
    result
        .getOrCreateTag()
        .put("InitialGuardianTotem", totem.copyWithCount(1).save(new CompoundTag()));
    return result;
  }

  @Override
  public ItemStack assemble(CraftingContainer grid, RegistryAccess registry) {
    if (!matches(grid, null)) return ItemStack.EMPTY;
    ItemStack friendly = ItemStack.EMPTY, totem = ItemStack.EMPTY;
    for (int i = 0; i < grid.getContainerSize(); i++) {
      var stack = grid.getItem(i);
      if (stack.is(ModItems.FRIENDLY_SLIME.get())) friendly = stack;
      if (stack.is(Items.TOTEM_OF_UNDYING)) totem = stack;
    }
    return specialize(friendly, totem);
  }

  @Override
  public boolean canCraftInDimensions(int width, int height) {
    return width * height >= 3;
  }

  @Override
  public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid) {
    return NonNullList.withSize(grid.getContainerSize(), ItemStack.EMPTY);
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return ModRecipes.GUARDIAN_ANGEL_SLIME.get();
  }
}
