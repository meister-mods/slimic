package io.github.meistermods.slimic.registry;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.recipe.MiningSlimeRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipes {
  public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
      DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, Slimic.MOD_ID);
  public static final RegistryObject<SimpleCraftingRecipeSerializer<MiningSlimeRecipe>>
      MINING_SLIME =
          SERIALIZERS.register(
              "mining_slime", () -> new SimpleCraftingRecipeSerializer<>(MiningSlimeRecipe::new));

  private ModRecipes() {}
}
