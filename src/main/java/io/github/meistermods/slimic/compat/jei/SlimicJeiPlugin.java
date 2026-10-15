package io.github.meistermods.slimic.compat.jei;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.recipe.MiningSlimeRecipe;
import io.github.meistermods.slimic.recipe.TransportSlimeRecipe;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.registries.ForgeRegistries;

@JeiPlugin
public final class SlimicJeiPlugin implements IModPlugin {
  @Override
  public ResourceLocation getPluginUid() {
    return ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "jei");
  }

  @Override
  public void registerRecipes(IRecipeRegistration registration) {
    List<CraftingRecipe> recipes = new ArrayList<>();
    for (var item : ForgeRegistries.ITEMS.getValues()) {
      ItemStack tool = new ItemStack(item);
      if (!MiningSlime.isPickaxe(tool)) continue;
      var key = ForgeRegistries.ITEMS.getKey(item);
      ItemStack friendly = new ItemStack(ModItems.FRIENDLY_SLIME.get());
      recipes.add(
          new ShapelessRecipe(
              ResourceLocation.fromNamespaceAndPath(
                  Slimic.MOD_ID, "mining_slime/" + key.getNamespace() + "/" + key.getPath()),
              "slimic:mining_slime",
              CraftingBookCategory.MISC,
              MiningSlimeRecipe.specialize(friendly, tool),
              NonNullList.of(Ingredient.EMPTY, Ingredient.of(friendly), Ingredient.of(tool))));
    }
    recipes.add(
        new ShapelessRecipe(
            ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "transport_slime/chest"),
            "slimic:transport_slime",
            CraftingBookCategory.MISC,
            TransportSlimeRecipe.specialize(new ItemStack(ModItems.FRIENDLY_SLIME.get())),
            NonNullList.of(
                Ingredient.EMPTY,
                Ingredient.of(ModItems.FRIENDLY_SLIME.get()),
                Ingredient.of(Items.CHEST))));
    registration.addRecipes(RecipeTypes.CRAFTING, recipes);
    registration.addIngredientInfo(
        new ItemStack(ModItems.FRIENDLY_SLIME.get()),
        VanillaTypes.ITEM_STACK,
        Component.translatable("jei.slimic.friendly_slime"));
    registration.addIngredientInfo(
        new ItemStack(ModItems.MINING_SLIME.get()),
        VanillaTypes.ITEM_STACK,
        Component.translatable("jei.slimic.mining_slime"));
    registration.addIngredientInfo(
        new ItemStack(ModItems.TRANSPORT_SLIME.get()),
        VanillaTypes.ITEM_STACK,
        Component.translatable("jei.slimic.transport_slime"));
    registration.addIngredientInfo(
        new ItemStack(ModItems.SLIME_CHEST.get()),
        VanillaTypes.ITEM_STACK,
        Component.translatable("jei.slimic.slime_chest"));
  }
}
