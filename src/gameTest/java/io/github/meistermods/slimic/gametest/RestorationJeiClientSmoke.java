package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.compat.jei.SlimeRestorationCategory;
import io.github.meistermods.slimic.compat.jei.SlimeRestorationRecipe;
import io.github.meistermods.slimic.item.AncientSlimeCoreItem;
import io.github.meistermods.slimic.registry.ModItems;
import io.github.meistermods.slimic.registry.ModWorldBlocks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Exercise actual JEI indexing, layouts, catalysts, and bilingual recipe screens. */
final class RestorationJeiClientSmoke {
  private static final String[] LANGUAGES = {"en_us", "ko_kr"};
  private static CompletableFuture<Void> reload;
  private static List<SlimeRestorationRecipe> recipes;
  private static int language, recipeIndex, ticks;
  private static boolean shown;

  static boolean tick(Minecraft client) {
    if (reload == null) {
      client.options.guiScale().set(2);
      client.resizeDisplay();
      client.getLanguageManager().setSelected(LANGUAGES[language]);
      client.options.languageCode = LANGUAGES[language];
      reload = client.reloadResourcePacks();
      return false;
    }
    if (!reload.isDone() || client.getOverlay() != null || JeiClientSmoke.runtime() == null)
      return false;
    reload.join();
    if (recipes == null) {
      if (!JeiClientSmoke.restorationInformationReady()) return false;
      recipes = verify(client);
    }
    if (!shown) {
      var runtime = JeiClientSmoke.runtime();
      runtime
          .getRecipesGui()
          .showRecipes(
              runtime.getRecipeManager().getRecipeCategory(SlimeRestorationCategory.TYPE),
              List.of(recipes.get(recipeIndex)),
              List.of());
      shown = true;
      ticks = 0;
      return false;
    }
    if (++ticks < 8) return false;
    if (client.screen == null || !client.screen.getClass().getName().contains("jei"))
      throw new IllegalStateException("Restoration recipe screen did not open");
    Path directory = Path.of("restoration-jei-audit");
    try {
      Files.createDirectories(directory);
      try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
        image.writeToFile(
            directory.resolve(
                LANGUAGES[language] + "-" + recipes.get(recipeIndex).role().name() + ".png"));
      }
    } catch (java.io.IOException error) {
      throw new IllegalStateException(error);
    }
    client.setScreen(null);
    shown = false;
    if (++recipeIndex < recipes.size()) return false;
    if (++language < LANGUAGES.length) {
      reload = null;
      recipes = null;
      recipeIndex = 0;
      return false;
    }
    System.out.println("SLIMIC RESTORATION JEI CLIENT SMOKE PASSED");
    return true;
  }

  private static List<SlimeRestorationRecipe> verify(Minecraft client) {
    if (!JeiClientSmoke.companionInformationReady() || !JeiClientSmoke.attentionBaseRecipesReady())
      throw new IllegalStateException("Missing companion information or specialization recipes");
    var runtime = JeiClientSmoke.runtime();
    var manager = runtime.getRecipeManager();
    var focuses = runtime.getJeiHelpers().getFocusFactory();
    var recipes = manager.createRecipeLookup(SlimeRestorationCategory.TYPE).get().toList();
    if (recipes.size() != 6
        || recipes.stream().map(SlimeRestorationRecipe::role).distinct().count() != 6)
      throw new IllegalStateException("Expected six distinct station restorations");
    var station = ModWorldBlocks.block("command_pedestal").asItem();
    if (manager
        .createRecipeCatalystLookup(SlimeRestorationCategory.TYPE)
        .getItemStack()
        .noneMatch(stack -> stack.is(station)))
      throw new IllegalStateException("Station is not linked as a restoration catalyst");
    var category = manager.getRecipeCategory(SlimeRestorationCategory.TYPE);
    var expectedTitle = language == 0 ? "Slime Restoration" : "슬라임 복원";
    if (!category.getTitle().getString().equals(expectedTitle))
      throw new IllegalStateException("Missing translated restoration title");
    for (var recipe : recipes) {
      for (var text :
          recipe
              .core()
              .getTooltipLines(
                  client.player, net.minecraft.world.item.TooltipFlag.Default.NORMAL)) {
        if (text.getString().contains("item.slimic.") || text.getString().contains("name.slimic."))
          throw new IllegalStateException("Untranslated core name or role: " + recipe.role());
      }
      for (int rank : new int[] {0, 3}) {
        var natural = AncientSlimeCoreItem.fromRemains(recipe.role(), UUID.randomUUID(), rank);
        natural.setHoverName(Component.literal("Old worker " + UUID.randomUUID()));
        for (var core : List.of(natural, recipe.core())) {
          var focus =
              focuses.createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, core);
          var matches =
              manager
                  .createRecipeLookup(SlimeRestorationCategory.TYPE)
                  .limitFocus(List.of(focus))
                  .get()
                  .toList();
          if (matches.size() != 1 || matches.get(0).role() != recipe.role())
            throw new IllegalStateException(
                "Core focus selected wrong restoration: " + recipe.role());
        }
      }
      var layout =
          manager
              .createRecipeLayoutDrawable(category, recipe, focuses.getEmptyFocusGroup())
              .orElseThrow(() -> new IllegalStateException("Invalid restoration layout"));
      var slots = layout.getRecipeSlotsView();
      if (slots.getSlotViews().size() != 5)
        throw new IllegalStateException("Missing restoration slots");
      var reagent =
          slots.findSlotByName("reagent").orElseThrow().getDisplayedItemStack().orElseThrow();
      if (!reagent.is(
              recipe.role().isMine()
                  ? ModItems.OLD_COMMAND_FRAGMENT.get()
                  : ModItems.ANCIENT_COMMAND_SEAL.get())
          || reagent.getCount() != 1) throw new IllegalStateException("Wrong station reagent");
      var ball =
          slots.findSlotByName("slimeball").orElseThrow().getDisplayedItemStack().orElseThrow();
      if (!ball.is(Items.SLIME_BALL) || ball.getCount() != 1)
        throw new IllegalStateException("Wrong slimeball cost");
      var core = slots.findSlotByName("core").orElseThrow().getDisplayedItemStack().orElseThrow();
      if (AncientSlimeCoreItem.role(core) != recipe.role() || core.getCount() != 1)
        throw new IllegalStateException("Wrong core ingredient");
      var output =
          slots.findSlotByName("companion").orElseThrow().getDisplayedItemStack().orElseThrow();
      var resultFocus =
          focuses.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, output);
      int expectedResults = output.is(ModItems.ROYAL_GUARD_SLIME.get()) ? 2 : 1;
      if (manager
              .createRecipeLookup(SlimeRestorationCategory.TYPE)
              .limitFocus(List.of(resultFocus))
              .get()
              .count()
          != expectedResults)
        throw new IllegalStateException("Companion does not link back to its station recipe");
      for (String key : List.of("interact", "overworld", "any_dimension", "arrival", "training")) {
        var text = Component.translatable("jei.slimic.restoration." + key);
        int availableWidth =
            key.equals("interact") ? category.getWidth() - 23 : category.getWidth();
        if (text.getString().equals("jei.slimic.restoration." + key)
            || client.font.width(text) > availableWidth)
          throw new IllegalStateException("Untranslated or overflowing restoration text: " + key);
      }
    }
    for (var item :
        List.of(
            ModItems.OLD_COMMAND_FRAGMENT.get(),
            ModItems.ANCIENT_COMMAND_SEAL.get(),
            Items.SLIME_BALL)) {
      var focus =
          focuses.createFocus(
              RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, new ItemStack(item));
      var matches =
          manager
              .createRecipeLookup(SlimeRestorationCategory.TYPE)
              .limitFocus(List.of(focus))
              .get()
              .toList();
      if (matches.size() != (item == Items.SLIME_BALL ? 6 : 3))
        throw new IllegalStateException("Reagent lookup mixes mine and ruin restorations");
    }
    var invalid =
        focuses.createFocus(
            RecipeIngredientRole.INPUT,
            VanillaTypes.ITEM_STACK,
            new ItemStack(ModItems.DAMAGED_ANCIENT_SLIME_CORE.get()));
    if (manager
        .createRecipeLookup(SlimeRestorationCategory.TYPE)
        .limitFocus(List.of(invalid))
        .get()
        .findAny()
        .isPresent())
      throw new IllegalStateException("An invalid core matches a valid restoration");
    return recipes;
  }
}
