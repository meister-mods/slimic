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

  static IJeiRuntime runtime() {
    return runtime;
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
    if (found.size() != 29)
      throw new IllegalStateException("Missing JEI profession information: " + found);
    return true;
  }

  public static void localizedInformationReady() {
    if (runtime == null) throw new IllegalStateException("JEI is not ready");
    var building =
        io.github.meistermods.slimic.registry.ModWorldBlocks.entries().stream()
            .filter(
                entry ->
                    entry.category()
                        == io.github.meistermods.slimic.registry.ModWorldBlocks.Category.BUILDING)
            .map(entry -> entry.item().get())
            .collect(java.util.stream.Collectors.toSet());
    int checked = 0;
    for (var recipe :
        runtime.getRecipeManager().createRecipeLookup(RecipeTypes.INFORMATION).get().toList()) {
      for (var ingredient : recipe.getIngredients()) {
        var stack = ingredient.getIngredient(mezz.jei.api.constants.VanillaTypes.ITEM_STACK);
        if (stack.isEmpty()) continue;
        var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.get().getItem());
        if (!id.getNamespace().equals("slimic")) continue;
        String key =
            switch (id.getPath()) {
              case "colony_heart", "cohesion_shard", "assembly_clue", "assembly_core" ->
                  "assembly.path";
              case "royal_synthesis_core",
                  "ancient_kingdom_map",
                  "kingdom_map_fragment",
                  "royal_survey_compass" ->
                  "sanctum.path";
              default -> building.contains(stack.get().getItem()) ? "world_building" : id.getPath();
            };
        var expected =
            net.minecraft.network.chat.Component.translatable("jei.slimic." + key).getString();
        var actual =
            recipe.getDescription().stream()
                .map(net.minecraft.network.chat.FormattedText::getString)
                .collect(java.util.stream.Collectors.joining());
        if (!actual.replaceAll("\\s+", "").equals(expected.replaceAll("\\s+", "")))
          throw new IllegalStateException(
              "Stale localized JEI information " + id + ": " + actual + "; expected=" + expected);
        checked++;
      }
    }
    if (checked < 50)
      throw new IllegalStateException("Too few localized information ingredients: " + checked);
    System.out.println(
        "SLIMIC JEI LOCALIZED INFORMATION PASSED: "
            + net.minecraft.client.Minecraft.getInstance().options.languageCode
            + " ingredients="
            + checked);
  }

  public static boolean restorationInformationReady() {
    if (runtime == null) return false;
    var visibleCoreRoles =
        runtime
            .getIngredientManager()
            .getAllIngredients(mezz.jei.api.constants.VanillaTypes.ITEM_STACK)
            .stream()
            .filter(
                stack ->
                    stack.is(
                        io.github.meistermods.slimic.registry.ModItems.DAMAGED_ANCIENT_SLIME_CORE
                            .get()))
            .map(io.github.meistermods.slimic.item.AncientSlimeCoreItem::role)
            .collect(java.util.stream.Collectors.toSet());
    if (visibleCoreRoles.size() != 6 || visibleCoreRoles.contains(null))
      throw new IllegalStateException(
          "JEI must expose six searchable core roles: " + visibleCoreRoles);
    for (var role : io.github.meistermods.slimic.entity.AncientSlime.Role.values()) {
      var natural =
          io.github.meistermods.slimic.item.AncientSlimeCoreItem.fromRemains(
              role, java.util.UUID.randomUUID(), 2);
      boolean linked = false;
      for (var focusRole :
          java.util.List.of(
              mezz.jei.api.recipe.RecipeIngredientRole.INPUT,
              mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT,
              mezz.jei.api.recipe.RecipeIngredientRole.CATALYST)) {
        var focus =
            runtime
                .getJeiHelpers()
                .getFocusFactory()
                .createFocus(focusRole, mezz.jei.api.constants.VanillaTypes.ITEM_STACK, natural);
        if (runtime
            .getRecipeManager()
            .createRecipeLookup(RecipeTypes.INFORMATION)
            .limitFocus(java.util.List.of(focus))
            .get()
            .findAny()
            .isPresent()) linked = true;
      }
      if (!linked)
        throw new IllegalStateException(
            "Naturally recovered core has no focused JEI information: " + role);
    }
    var expectedItems =
        java.util.Set.of(
            io.github.meistermods.slimic.registry.ModItems.OLD_COMMAND_FRAGMENT.get(),
            io.github.meistermods.slimic.registry.ModItems.DAMAGED_ANCIENT_SLIME_CORE.get(),
            io.github.meistermods.slimic.registry.ModItems.ANCIENT_COMMAND_SEAL.get(),
            io.github.meistermods.slimic.registry.ModWorldBlocks.block("command_pedestal")
                .asItem());
    var found = new java.util.HashSet<net.minecraft.world.item.Item>();
    runtime
        .getRecipeManager()
        .createRecipeLookup(RecipeTypes.INFORMATION)
        .get()
        .forEach(
            recipe -> {
              for (var ingredient : recipe.getIngredients()) {
                var stack =
                    ingredient.getIngredient(mezz.jei.api.constants.VanillaTypes.ITEM_STACK);
                if (stack.isEmpty() || !expectedItems.contains(stack.get().getItem())) continue;
                var item = stack.get().getItem();
                var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
                var expected =
                    net.minecraft.network.chat.Component.translatable("jei.slimic." + id.getPath())
                        .getString();
                var actual =
                    recipe.getDescription().stream()
                        .map(net.minecraft.network.chat.FormattedText::getString)
                        .collect(java.util.stream.Collectors.joining());
                if (!actual.replaceAll("\\s+", "").equals(expected.replaceAll("\\s+", "")))
                  throw new IllegalStateException(
                      "Wrong restoration ingredient information: " + id);
                found.add(item);
              }
            });
    if (!found.equals(expectedItems))
      throw new IllegalStateException("Missing restoration ingredient information: " + found);
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

  public static long loggingRecipes() {
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

  public static long signalingRecipes() {
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

  public static boolean enderEquipmentRecipesReady() {
    if (runtime == null) return false;
    var client = net.minecraft.client.Minecraft.getInstance();
    var recipes =
        runtime.getRecipeManager().createRecipeLookup(RecipeTypes.CRAFTING).get().toList();
    var guardian =
        recipes.stream()
            .filter(r -> r.getId().toString().equals("slimic:guardian_angel_slime/totem"))
            .findFirst();
    if (guardian.isEmpty()) return false;
    var healing =
        new net.minecraft.world.item.ItemStack(
            io.github.meistermods.slimic.registry.ModItems.HEALING_SLIME.get());
    var friendly =
        new net.minecraft.world.item.ItemStack(
            io.github.meistermods.slimic.registry.ModItems.FRIENDLY_SLIME.get());
    var attention =
        new net.minecraft.world.item.ItemStack(
            io.github.meistermods.slimic.registry.ModItems.ATTENTION_SLIME.get());
    if (guardian.get().getIngredients().stream().noneMatch(i -> i.test(healing))
        || guardian.get().getIngredients().stream()
            .anyMatch(i -> i.test(friendly) || i.test(attention)))
      throw new IllegalStateException(
          "Guardian JEI ingredients must require Healing, without Friendly/Attention alternatives");
    return recipes.stream()
        .anyMatch(
            r ->
                r.getId().toString().equals("slimic:dimensional_attunement_seal")
                    && r.getResultItem(client.level.registryAccess())
                        .is(
                            io.github.meistermods.slimic.registry.ModItems
                                .DIMENSIONAL_ATTUNEMENT_SEAL
                                .get()));
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
