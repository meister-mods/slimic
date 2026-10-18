package io.github.meistermods.slimic.gametest;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class JeiClientSmoke implements IModPlugin {
  private static IJeiRuntime runtime;

  @Override
  public ResourceLocation getPluginUid() {
    return ResourceLocation.fromNamespaceAndPath("slimic_tests", "client_smoke");
  }

  @Override
  public void onRuntimeAvailable(IJeiRuntime value) {
    runtime = value;
  }

  public static long healingRecipes() {
    return recipes("healing_slime/");
  }

  public static long warehouseRecipes() {
    return recipes("warehouse_slime/");
  }

  public static long farmingRecipes() {
    return recipes("farmer_slime/");
  }

  public static long lumberjackRecipes() {
    return recipes("lumberjack_slime/");
  }

  public static long miningRecipes() {
    return recipes("mining_slime/");
  }

  public static long transportRecipes() {
    return recipes("transport_slime/");
  }

  public static long chestRecipes() {
    return recipes("slime_chest");
  }

  public static long guidebookRecipes() {
    return recipes("guide_book");
  }

  public static long heraldRecipes() {
    return recipes("herald_slime/");
  }

  public static long combatRecipes() {
    return recipes("combat_slime/");
  }

  public static long cleaningRecipes() {
    return recipes("cleaning_slime/");
  }

  public static long saddleRecipes() {
    return recipes("saddle_slime/");
  }

  public static long soggyRecipes() {
    return recipes("soggy_slime/");
  }

  public static long slagRecipes() {
    return recipes("slime_ball_from_slag");
  }

  public static long armorRecipes() {
    return recipes("slime_armor/");
  }

  private static long recipes(String path) {
    if (runtime == null) return 0;
    return runtime
        .getRecipeManager()
        .createRecipeLookup(RecipeTypes.CRAFTING)
        .get()
        .filter(
            recipe ->
                recipe.getId().getNamespace().equals("slimic")
                    && recipe.getId().getPath().startsWith(path))
        .count();
  }
}
