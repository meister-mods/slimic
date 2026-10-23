package io.github.meistermods.slimic.compat.jei;

import java.util.Arrays;
import java.util.List;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.vanilla.IJeiIngredientInfoRecipe;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

/** Retains translation components so information follows the client's current language. */
final class LocalizedIngredientInformation implements IJeiIngredientInfoRecipe {
  private final List<ITypedIngredient<?>> ingredients;
  private final List<Component> descriptions;

  private LocalizedIngredientInformation(
      List<ITypedIngredient<?>> ingredients, Component[] descriptions) {
    this.ingredients = List.copyOf(ingredients);
    this.descriptions = Arrays.stream(descriptions).map(c -> (Component) c.copy()).toList();
  }

  static <T> void add(
      IRecipeRegistration registration,
      T ingredient,
      IIngredientType<T> type,
      Component... descriptions) {
    add(registration, List.of(ingredient), type, descriptions);
  }

  static <T> void add(
      IRecipeRegistration registration,
      List<T> ingredients,
      IIngredientType<T> type,
      Component... descriptions) {
    List<ITypedIngredient<?>> typed = new java.util.ArrayList<>();
    for (T value : ingredients)
      typed.add(
          registration.getIngredientManager().createTypedIngredient(type, value).orElseThrow());
    registration.addRecipes(
        RecipeTypes.INFORMATION, List.of(new LocalizedIngredientInformation(typed, descriptions)));
  }

  @Override
  public List<ITypedIngredient<?>> getIngredients() {
    return ingredients;
  }

  @Override
  public List<FormattedText> getDescription() {
    return descriptions.stream().map(c -> (FormattedText) c.copy()).toList();
  }
}
