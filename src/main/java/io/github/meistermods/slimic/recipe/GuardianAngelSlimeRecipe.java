package io.github.meistermods.slimic.recipe;

import io.github.meistermods.slimic.registry.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
    int healing = 0, star = 0, totem = 0;
    for (int i = 0; i < grid.getContainerSize(); i++) {
      var item = grid.getItem(i);
      if (item.isEmpty()) continue;
      if (item.is(ModItems.HEALING_SLIME.get())) {
        // The guardian has no bag; require unloading instead of discarding stored supplies.
        if (item.hasTag()
            && !item.getTag()
                .getCompound("SlimeData")
                .getCompound("HealingBag")
                .getList("Items", Tag.TAG_COMPOUND)
                .isEmpty()) return false;
        healing++;
      } else if (item.is(Items.NETHER_STAR)) star++;
      else if (item.is(Items.TOTEM_OF_UNDYING)) totem++;
      else return false;
    }
    return healing == 1 && star == 1 && totem == 1;
  }

  public static ItemStack specialize(ItemStack healing, ItemStack totem) {
    var result = new ItemStack(ModItems.GUARDIAN_ANGEL_SLIME.get());
    CompanionBase.copyData(healing, result);
    if (result.hasTag() && result.getTag().contains("SlimeData"))
      io.github.meistermods.slimic.entity.ProfessionHealth.addToData(
          result.getTag().getCompound("SlimeData"),
          io.github.meistermods.slimic.entity.ProfessionHealth.GUARDIAN);
    result
        .getOrCreateTag()
        .put("InitialGuardianTotem", totem.copyWithCount(1).save(new CompoundTag()));
    return result;
  }

  @Override
  public ItemStack assemble(CraftingContainer grid, RegistryAccess registry) {
    if (!matches(grid, null)) return ItemStack.EMPTY;
    ItemStack healing = ItemStack.EMPTY, totem = ItemStack.EMPTY;
    for (int i = 0; i < grid.getContainerSize(); i++) {
      var stack = grid.getItem(i);
      if (stack.is(ModItems.HEALING_SLIME.get())) healing = stack;
      if (stack.is(Items.TOTEM_OF_UNDYING)) totem = stack;
    }
    return specialize(healing, totem);
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
