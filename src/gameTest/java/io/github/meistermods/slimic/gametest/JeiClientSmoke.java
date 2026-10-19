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

  public static boolean companionInformationReady() {
    if (runtime == null) return false;
    var found = new java.util.HashSet<net.minecraft.resources.ResourceLocation>();
    runtime
        .getRecipeManager()
        .createRecipeLookup(RecipeTypes.INFORMATION)
        .get()
        .forEach(
            recipe -> {
              for (var ingredient : recipe.getIngredients()) {
                var stack =
                    ingredient.getIngredient(mezz.jei.api.constants.VanillaTypes.ITEM_STACK);
                if (stack.isEmpty()
                    || !(stack.get().getItem()
                        instanceof io.github.meistermods.slimic.item.FriendlySlimeItem)) continue;
                var id =
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(
                        stack.get().getItem());
                var expected =
                    net.minecraft.network.chat.Component.translatable("jei.slimic." + id.getPath())
                        .getString();
                var text =
                    recipe.getDescription().stream()
                        .map(net.minecraft.network.chat.FormattedText::getString)
                        .collect(java.util.stream.Collectors.joining());
                if (!text.replaceAll("\\s+", "").equals(expected.replaceAll("\\s+", "")))
                  throw new IllegalStateException(
                      "Wrong JEI profession information "
                          + id
                          + ": actual="
                          + text
                          + "; expected="
                          + expected);
                found.add(id);
              }
            });
    if (found.size() != 22)
      throw new IllegalStateException("Missing JEI profession information: " + found);
    return true;
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

  public static boolean attentionBaseRecipesReady() {
    if (runtime == null) return false;
    var client = net.minecraft.client.Minecraft.getInstance();
    var friendly =
        new net.minecraft.world.item.ItemStack(
            io.github.meistermods.slimic.registry.ModItems.FRIENDLY_SLIME.get());
    var attention =
        new net.minecraft.world.item.ItemStack(
            io.github.meistermods.slimic.registry.ModItems.ATTENTION_SLIME.get());
    var outputs = new java.util.HashSet<String>();
    runtime
        .getRecipeManager()
        .createRecipeLookup(RecipeTypes.CRAFTING)
        .get()
        .filter(recipe -> recipe.getId().getNamespace().equals("slimic"))
        .forEach(
            recipe -> {
              var result = recipe.getResultItem(client.level.registryAccess());
              String name =
                  net.minecraftforge.registries.ForgeRegistries.ITEMS
                      .getKey(result.getItem())
                      .toString();
              boolean alternatives =
                  recipe.getIngredients().stream()
                      .anyMatch(i -> i.test(friendly) && i.test(attention));
              if (alternatives) outputs.add(name);
              if (name.equals("slimic:attention_slime") && alternatives)
                throw new IllegalStateException(
                    "Attention must not be its own specialization base");
            });
    var expected =
        java.util.Set.of(
            "mining",
            "transport",
            "herald",
            "combat",
            "cleaning",
            "saddle",
            "farmer",
            "lumberjack",
            "soggy",
            "healing",
            "guardian_angel",
            "fishing",
            "meow",
            "glow",
            "commander",
            "ender");
    for (String name : expected)
      if (!outputs.contains("slimic:" + name + "_slime"))
        throw new IllegalStateException("JEI is missing Attention as an alternative for " + name);
    return true;
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
