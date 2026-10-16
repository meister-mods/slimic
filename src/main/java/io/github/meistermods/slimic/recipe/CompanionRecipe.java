package io.github.meistermods.slimic.recipe;

import io.github.meistermods.slimic.entity.CombatSlime;
import io.github.meistermods.slimic.entity.FarmerSlime;
import io.github.meistermods.slimic.entity.LumberjackSlime;
import io.github.meistermods.slimic.item.CombatSlimeItem;
import io.github.meistermods.slimic.item.SlimeArmorItem;
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

public final class CompanionRecipe extends CustomRecipe {
  public enum Kind {
    COMBAT,
    CLEANING,
    ARMOR,
    SADDLE,
    FARMER,
    LUMBERJACK,
    SOGGY
  }

  private final Kind kind;

  public CompanionRecipe(ResourceLocation id, CraftingBookCategory category, Kind kind) {
    super(id, category);
    this.kind = kind;
  }

  private boolean primary(ItemStack stack) {
    return kind == Kind.ARMOR
        ? SlimeArmorItem.accepts(stack)
        : stack.is(ModItems.FRIENDLY_SLIME.get());
  }

  private boolean secondary(ItemStack stack) {
    return switch (kind) {
      case COMBAT -> CombatSlime.isSword(stack);
      case CLEANING -> stack.is(Items.ROTTEN_FLESH);
      case ARMOR -> stack.is(Items.SLIME_BALL);
      case SADDLE -> stack.is(Items.SADDLE);
      case FARMER -> FarmerSlime.isHoe(stack);
      case LUMBERJACK -> LumberjackSlime.isAxe(stack);
      case SOGGY -> stack.is(Items.SLIME_BALL);
    };
  }

  @Override
  public boolean matches(CraftingContainer grid, Level level) {
    boolean first = false, second = false;
    for (int slot = 0; slot < grid.getContainerSize(); slot++) {
      ItemStack stack = grid.getItem(slot);
      if (stack.isEmpty()) continue;
      if (!first && primary(stack)) first = true;
      else if (!second && secondary(stack)) second = true;
      else return false;
    }
    return first && second;
  }

  public static ItemStack combat(ItemStack friendly, ItemStack sword) {
    ItemStack result = new ItemStack(ModItems.COMBAT_SLIME.get());
    if (friendly.hasTag()) result.setTag(friendly.getTag().copy());
    result
        .getOrCreateTag()
        .put(CombatSlimeItem.INITIAL_SWORD, sword.copyWithCount(1).save(new CompoundTag()));
    return result;
  }

  public static ItemStack cleaning(ItemStack friendly) {
    ItemStack result = new ItemStack(ModItems.CLEANING_SLIME.get());
    if (friendly.hasTag()) result.setTag(friendly.getTag().copy());
    return result;
  }

  public static ItemStack soggy(ItemStack friendly) {
    ItemStack result = new ItemStack(ModItems.SOGGY_SLIME.get());
    if (friendly.hasTag()) result.setTag(friendly.getTag().copy());
    return result;
  }

  public static ItemStack saddle(ItemStack friendly, ItemStack saddle) {
    ItemStack result = new ItemStack(ModItems.SADDLE_SLIME.get());
    if (friendly.hasTag()) result.setTag(friendly.getTag().copy());
    result.getOrCreateTag().put("InitialSaddle", saddle.copyWithCount(1).save(new CompoundTag()));
    return result;
  }

  public static ItemStack field(ItemStack friendly, ItemStack tool, boolean farmer) {
    ItemStack result =
        new ItemStack(farmer ? ModItems.FARMER_SLIME.get() : ModItems.LUMBERJACK_SLIME.get());
    if (friendly.hasTag()) result.setTag(friendly.getTag().copy());
    result.getOrCreateTag().put("InitialWorkTool", tool.copyWithCount(1).save(new CompoundTag()));
    return result;
  }

  @Override
  public ItemStack assemble(CraftingContainer grid, RegistryAccess registry) {
    ItemStack first = ItemStack.EMPTY, second = ItemStack.EMPTY;
    for (int slot = 0; slot < grid.getContainerSize(); slot++) {
      ItemStack stack = grid.getItem(slot);
      if (primary(stack)) first = stack;
      else if (secondary(stack)) second = stack;
    }
    if (first.isEmpty() || second.isEmpty()) return ItemStack.EMPTY;
    return switch (kind) {
      case COMBAT -> combat(first, second);
      case CLEANING -> cleaning(first);
      case ARMOR -> SlimeArmorItem.fromChestplate(first);
      case SADDLE -> saddle(first, second);
      case FARMER -> field(first, second, true);
      case LUMBERJACK -> field(first, second, false);
      case SOGGY -> soggy(first);
    };
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
    return switch (kind) {
      case COMBAT -> ModRecipes.COMBAT_SLIME.get();
      case CLEANING -> ModRecipes.CLEANING_SLIME.get();
      case ARMOR -> ModRecipes.SLIME_ARMOR.get();
      case SADDLE -> ModRecipes.SADDLE_SLIME.get();
      case FARMER -> ModRecipes.FARMER_SLIME.get();
      case LUMBERJACK -> ModRecipes.LUMBERJACK_SLIME.get();
      case SOGGY -> ModRecipes.SOGGY_SLIME.get();
    };
  }
}
