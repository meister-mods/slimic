package io.github.meistermods.slimic.registry;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.recipe.CompanionRecipe;
import io.github.meistermods.slimic.recipe.HeraldSlimeRecipe;
import io.github.meistermods.slimic.recipe.MiningSlimeRecipe;
import io.github.meistermods.slimic.recipe.TransportSlimeRecipe;
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

  public static final RegistryObject<SimpleCraftingRecipeSerializer<TransportSlimeRecipe>>
      TRANSPORT_SLIME =
          SERIALIZERS.register(
              "transport_slime",
              () -> new SimpleCraftingRecipeSerializer<>(TransportSlimeRecipe::new));

  public static final RegistryObject<SimpleCraftingRecipeSerializer<CompanionRecipe>> COMBAT_SLIME =
      SERIALIZERS.register(
          "combat_slime",
          () ->
              new SimpleCraftingRecipeSerializer<>(
                  (id, category) ->
                      new CompanionRecipe(id, category, CompanionRecipe.Kind.COMBAT)));
  public static final RegistryObject<SimpleCraftingRecipeSerializer<CompanionRecipe>>
      CLEANING_SLIME =
          SERIALIZERS.register(
              "cleaning_slime",
              () ->
                  new SimpleCraftingRecipeSerializer<>(
                      (id, category) ->
                          new CompanionRecipe(id, category, CompanionRecipe.Kind.CLEANING)));
  public static final RegistryObject<SimpleCraftingRecipeSerializer<CompanionRecipe>> SLIME_ARMOR =
      SERIALIZERS.register(
          "slime_armor",
          () ->
              new SimpleCraftingRecipeSerializer<>(
                  (id, category) -> new CompanionRecipe(id, category, CompanionRecipe.Kind.ARMOR)));

  public static final RegistryObject<SimpleCraftingRecipeSerializer<CompanionRecipe>> SADDLE_SLIME =
      SERIALIZERS.register(
          "saddle_slime",
          () ->
              new SimpleCraftingRecipeSerializer<>(
                  (id, category) ->
                      new CompanionRecipe(id, category, CompanionRecipe.Kind.SADDLE)));

  public static final RegistryObject<SimpleCraftingRecipeSerializer<CompanionRecipe>> FARMER_SLIME =
      SERIALIZERS.register(
          "farmer_slime",
          () ->
              new SimpleCraftingRecipeSerializer<>(
                  (id, category) ->
                      new CompanionRecipe(id, category, CompanionRecipe.Kind.FARMER)));
  public static final RegistryObject<SimpleCraftingRecipeSerializer<CompanionRecipe>>
      LUMBERJACK_SLIME =
          SERIALIZERS.register(
              "lumberjack_slime",
              () ->
                  new SimpleCraftingRecipeSerializer<>(
                      (id, category) ->
                          new CompanionRecipe(id, category, CompanionRecipe.Kind.LUMBERJACK)));

  private ModRecipes() {}

  public static final RegistryObject<SimpleCraftingRecipeSerializer<HeraldSlimeRecipe>>
      HERALD_SLIME =
          SERIALIZERS.register(
              "herald_slime", () -> new SimpleCraftingRecipeSerializer<>(HeraldSlimeRecipe::new));
}
