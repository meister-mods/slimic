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

  public static long miningRecipes() {
    return recipes("mining_slime/");
  }

  public static long transportRecipes() {
    return recipes("transport_slime/");
  }

  public static long chestRecipes() {
    return recipes("slime_chest");
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
