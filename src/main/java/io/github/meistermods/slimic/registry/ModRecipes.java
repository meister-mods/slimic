package io.github.meistermods.slimic.registry;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.recipe.CompanionRecipe;
import io.github.meistermods.slimic.recipe.GuardianAngelSlimeRecipe;
import io.github.meistermods.slimic.recipe.HeraldSlimeRecipe;
import io.github.meistermods.slimic.recipe.MiningSlimeRecipe;
import io.github.meistermods.slimic.recipe.TransportSlimeRecipe;
import io.github.meistermods.slimic.recipe.WarehouseSlimeRecipe;
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

  public static final RegistryObject<SimpleCraftingRecipeSerializer<GuardianAngelSlimeRecipe>>
      GUARDIAN_ANGEL_SLIME =
          SERIALIZERS.register(
              "guardian_angel_slime",
              () -> new SimpleCraftingRecipeSerializer<>(GuardianAngelSlimeRecipe::new));

  public static final RegistryObject<SimpleCraftingRecipeSerializer<CompanionRecipe>>
      HEALING_SLIME =
          SERIALIZERS.register(
              "healing_slime",
              () ->
                  new SimpleCraftingRecipeSerializer<>(
                      (id, category) ->
                          new CompanionRecipe(id, category, CompanionRecipe.Kind.HEALING)));
  public static final RegistryObject<SimpleCraftingRecipeSerializer<WarehouseSlimeRecipe>>
      WAREHOUSE_SLIME =
          SERIALIZERS.register(
              "warehouse_slime",
              () -> new SimpleCraftingRecipeSerializer<>(WarehouseSlimeRecipe::new));

  public static final RegistryObject<
          SimpleCraftingRecipeSerializer<
              io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe>>
      FISHING_SLIME =
          SERIALIZERS.register(
              "fishing_slime",
              () ->
                  new SimpleCraftingRecipeSerializer<>(
                      (id, category) ->
                          new io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe(
                              id,
                              category,
                              io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe.Kind
                                  .FISHING)));
  public static final RegistryObject<
          SimpleCraftingRecipeSerializer<
              io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe>>
      ABUNDANCE_SLIME =
          SERIALIZERS.register(
              "abundance_slime",
              () ->
                  new SimpleCraftingRecipeSerializer<>(
                      (id, category) ->
                          new io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe(
                              id,
                              category,
                              io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe.Kind
                                  .ABUNDANCE)));
  public static final RegistryObject<
          SimpleCraftingRecipeSerializer<
              io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe>>
      TUNNEL_SLIME =
          SERIALIZERS.register(
              "tunnel_slime",
              () ->
                  new SimpleCraftingRecipeSerializer<>(
                      (id, category) ->
                          new io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe(
                              id,
                              category,
                              io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe.Kind
                                  .TUNNEL)));
  public static final RegistryObject<
          SimpleCraftingRecipeSerializer<
              io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe>>
      DRAGON_SLIME =
          SERIALIZERS.register(
              "dragon_slime",
              () ->
                  new SimpleCraftingRecipeSerializer<>(
                      (id, category) ->
                          new io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe(
                              id,
                              category,
                              io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe.Kind
                                  .DRAGON)));
  public static final RegistryObject<
          SimpleCraftingRecipeSerializer<
              io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe>>
      MEOW_SLIME =
          SERIALIZERS.register(
              "meow_slime",
              () ->
                  new SimpleCraftingRecipeSerializer<>(
                      (id, category) ->
                          new io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe(
                              id,
                              category,
                              io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe.Kind
                                  .MEOW)));

  public static final RegistryObject<
          SimpleCraftingRecipeSerializer<
              io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe>>
      GLOW_SLIME =
          SERIALIZERS.register(
              "glow_slime",
              () ->
                  new SimpleCraftingRecipeSerializer<>(
                      (id, category) ->
                          new io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe(
                              id,
                              category,
                              io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe.Kind
                                  .GLOW)));
  public static final RegistryObject<
          SimpleCraftingRecipeSerializer<
              io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe>>
      COMMANDER_SLIME =
          SERIALIZERS.register(
              "commander_slime",
              () ->
                  new SimpleCraftingRecipeSerializer<>(
                      (id, category) ->
                          new io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe(
                              id,
                              category,
                              io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe.Kind
                                  .COMMANDER)));
  public static final RegistryObject<
          SimpleCraftingRecipeSerializer<
              io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe>>
      ENDER_SLIME =
          SERIALIZERS.register(
              "ender_slime",
              () ->
                  new SimpleCraftingRecipeSerializer<>(
                      (id, category) ->
                          new io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe(
                              id,
                              category,
                              io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe.Kind
                                  .ENDER)));
  public static final RegistryObject<
          SimpleCraftingRecipeSerializer<
              io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe>>
      ATTENTION_SLIME =
          SERIALIZERS.register(
              "attention_slime",
              () ->
                  new SimpleCraftingRecipeSerializer<>(
                      (id, category) ->
                          new io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe(
                              id,
                              category,
                              io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe.Kind
                                  .ATTENTION)));

  private ModRecipes() {}

  public static final RegistryObject<SimpleCraftingRecipeSerializer<CompanionRecipe>> SOGGY_SLIME =
      SERIALIZERS.register(
          "soggy_slime",
          () ->
              new SimpleCraftingRecipeSerializer<>(
                  (id, category) -> new CompanionRecipe(id, category, CompanionRecipe.Kind.SOGGY)));

  public static final RegistryObject<SimpleCraftingRecipeSerializer<HeraldSlimeRecipe>>
      HERALD_SLIME =
          SERIALIZERS.register(
              "herald_slime", () -> new SimpleCraftingRecipeSerializer<>(HeraldSlimeRecipe::new));
}
