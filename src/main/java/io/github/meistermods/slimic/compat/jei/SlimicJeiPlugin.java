package io.github.meistermods.slimic.compat.jei;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.CombatSlime;
import io.github.meistermods.slimic.entity.FarmerSlime;
import io.github.meistermods.slimic.entity.HeraldSlime;
import io.github.meistermods.slimic.entity.LumberjackSlime;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.item.SlimeArmorItem;
import io.github.meistermods.slimic.recipe.CompanionBase;
import io.github.meistermods.slimic.recipe.CompanionRecipe;
import io.github.meistermods.slimic.recipe.GuardianAngelSlimeRecipe;
import io.github.meistermods.slimic.recipe.HeraldSlimeRecipe;
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
import net.minecraft.world.item.Item;
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
    recipes.add(
        new ShapelessRecipe(
            ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "healing_slime/golden_carrot"),
            "slimic:healing_slime",
            CraftingBookCategory.MISC,
            CompanionRecipe.healing(new ItemStack(ModItems.FRIENDLY_SLIME.get())),
            NonNullList.of(
                Ingredient.EMPTY, CompanionBase.ingredient(), Ingredient.of(Items.GOLDEN_CARROT))));
    NonNullList<Ingredient> warehouseIngredients = NonNullList.create();
    warehouseIngredients.add(Ingredient.of(ModItems.TRANSPORT_SLIME.get()));
    for (int chest = 0; chest < 8; chest++) warehouseIngredients.add(Ingredient.of(Items.CHEST));
    recipes.add(
        new ShapelessRecipe(
            ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "warehouse_slime/chests"),
            "slimic:warehouse_slime",
            CraftingBookCategory.MISC,
            new ItemStack(ModItems.WAREHOUSE_SLIME.get()),
            warehouseIngredients));
    for (var item : new Item[] {ModItems.HEALING_SLIME.get(), ModItems.WAREHOUSE_SLIME.get()})
      registration.addIngredientInfo(
          new ItemStack(item),
          VanillaTypes.ITEM_STACK,
          Component.translatable("jei.slimic." + ForgeRegistries.ITEMS.getKey(item).getPath()));
    recipes.add(
        new ShapelessRecipe(
            ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "soggy_slime/slime_ball"),
            "slimic:soggy_slime",
            CraftingBookCategory.MISC,
            CompanionRecipe.soggy(new ItemStack(ModItems.FRIENDLY_SLIME.get())),
            NonNullList.of(
                Ingredient.EMPTY, CompanionBase.ingredient(), Ingredient.of(Items.SLIME_BALL))));
    registration.addIngredientInfo(
        new ItemStack(ModItems.SOGGY_SLIME.get()),
        VanillaTypes.ITEM_STACK,
        Component.translatable("jei.slimic.soggy_slime"));
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
              NonNullList.of(Ingredient.EMPTY, CompanionBase.ingredient(), Ingredient.of(tool))));
    }
    recipes.add(
        new ShapelessRecipe(
            ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "transport_slime/chest"),
            "slimic:transport_slime",
            CraftingBookCategory.MISC,
            TransportSlimeRecipe.specialize(new ItemStack(ModItems.FRIENDLY_SLIME.get())),
            NonNullList.of(
                Ingredient.EMPTY, CompanionBase.ingredient(), Ingredient.of(Items.CHEST))));
    recipes.add(
        new ShapelessRecipe(
            ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "herald_slime/firework"),
            "slimic:herald_slime",
            CraftingBookCategory.MISC,
            HeraldSlimeRecipe.specialize(
                new ItemStack(ModItems.FRIENDLY_SLIME.get()), HeraldSlime.defaultFirework()),
            NonNullList.of(
                Ingredient.EMPTY,
                CompanionBase.ingredient(),
                Ingredient.of(Items.FIREWORK_ROCKET))));
    for (var item : ForgeRegistries.ITEMS.getValues()) {
      ItemStack stack = new ItemStack(item);
      var key = ForgeRegistries.ITEMS.getKey(item);
      if (FarmerSlime.isHoe(stack) || LumberjackSlime.isAxe(stack)) {
        boolean farmer = FarmerSlime.isHoe(stack);
        String role = farmer ? "farmer" : "lumberjack";
        recipes.add(
            new ShapelessRecipe(
                ResourceLocation.fromNamespaceAndPath(
                    Slimic.MOD_ID, role + "_slime/" + key.getNamespace() + "/" + key.getPath()),
                "slimic:" + role + "_slime",
                CraftingBookCategory.MISC,
                CompanionRecipe.field(new ItemStack(ModItems.FRIENDLY_SLIME.get()), stack, farmer),
                NonNullList.of(
                    Ingredient.EMPTY, CompanionBase.ingredient(), Ingredient.of(stack))));
      }
      if (CombatSlime.isSword(stack))
        recipes.add(
            new ShapelessRecipe(
                ResourceLocation.fromNamespaceAndPath(
                    Slimic.MOD_ID, "combat_slime/" + key.getNamespace() + "/" + key.getPath()),
                "slimic:combat_slime",
                CraftingBookCategory.MISC,
                CompanionRecipe.combat(new ItemStack(ModItems.FRIENDLY_SLIME.get()), stack),
                NonNullList.of(
                    Ingredient.EMPTY, CompanionBase.ingredient(), Ingredient.of(stack))));
      if (SlimeArmorItem.accepts(stack))
        recipes.add(
            new ShapelessRecipe(
                ResourceLocation.fromNamespaceAndPath(
                    Slimic.MOD_ID, "slime_armor/" + key.getNamespace() + "/" + key.getPath()),
                "slimic:slime_armor",
                CraftingBookCategory.MISC,
                SlimeArmorItem.fromChestplate(stack),
                NonNullList.of(
                    Ingredient.EMPTY, Ingredient.of(stack), Ingredient.of(Items.SLIME_BALL))));
    }
    recipes.add(
        new ShapelessRecipe(
            ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "cleaning_slime/rotten_flesh"),
            "slimic:cleaning_slime",
            CraftingBookCategory.MISC,
            new ItemStack(ModItems.CLEANING_SLIME.get()),
            NonNullList.of(
                Ingredient.EMPTY, CompanionBase.ingredient(), Ingredient.of(Items.ROTTEN_FLESH))));
    for (var item :
        new Item[] {
          ModItems.COMBAT_SLIME.get(),
          ModItems.CLEANING_SLIME.get(),
          ModItems.SLIME_ARMOR.get(),
          ModItems.FARMER_SLIME.get(),
          ModItems.LUMBERJACK_SLIME.get()
        }) {
      registration.addIngredientInfo(
          new ItemStack(item),
          VanillaTypes.ITEM_STACK,
          Component.translatable("jei.slimic." + ForgeRegistries.ITEMS.getKey(item).getPath()));
    }
    recipes.add(
        new ShapelessRecipe(
            ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "saddle_slime/saddle"),
            "slimic:saddle_slime",
            CraftingBookCategory.MISC,
            CompanionRecipe.saddle(
                new ItemStack(ModItems.FRIENDLY_SLIME.get()), new ItemStack(Items.SADDLE)),
            NonNullList.of(
                Ingredient.EMPTY, CompanionBase.ingredient(), Ingredient.of(Items.SADDLE))));
    registration.addIngredientInfo(
        new ItemStack(ModItems.SADDLE_SLIME.get()),
        VanillaTypes.ITEM_STACK,
        Component.translatable("jei.slimic.saddle_slime"));
    recipes.add(
        new ShapelessRecipe(
            ResourceLocation.fromNamespaceAndPath("slimic", "guardian_angel_slime/totem"),
            "slimic:guardian_angel_slime",
            CraftingBookCategory.MISC,
            GuardianAngelSlimeRecipe.specialize(
                new ItemStack(ModItems.FRIENDLY_SLIME.get()),
                new ItemStack(Items.TOTEM_OF_UNDYING)),
            NonNullList.of(
                Ingredient.EMPTY,
                CompanionBase.ingredient(),
                Ingredient.of(Items.NETHER_STAR),
                Ingredient.of(Items.TOTEM_OF_UNDYING))));
    registration.addIngredientInfo(
        new ItemStack(ModItems.GUARDIAN_ANGEL_SLIME.get()),
        VanillaTypes.ITEM_STACK,
        Component.translatable("jei.slimic.guardian_angel_slime"));
    registration.addIngredientInfo(
        new ItemStack(ModItems.SLIME_TUNER.get()),
        VanillaTypes.ITEM_STACK,
        Component.translatable("jei.slimic.slime_tuner"));
    for (var kind : io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe.Kind.values()) {
      List<ItemStack> secondary =
          switch (kind) {
            case FISHING ->
                ForgeRegistries.ITEMS.getValues().stream()
                    .map(ItemStack::new)
                    .filter(io.github.meistermods.slimic.entity.FishingSlime::isRod)
                    .toList();
            case ABUNDANCE -> List.of(new ItemStack(Items.BONE_MEAL));
            case GLOW -> List.of(new ItemStack(Items.GLOWSTONE_DUST));
            case COMMANDER -> List.of(new ItemStack(Items.BOOK));
            case ENDER -> List.of(new ItemStack(Items.ENDER_PEARL));
            case ATTENTION -> List.of(new ItemStack(Items.REDSTONE));

            case TUNNEL -> List.of(new ItemStack(Items.NETHER_STAR));
            case DRAGON -> List.of(new ItemStack(Items.DRAGON_HEAD));
            case MEOW ->
                List.of(
                    new ItemStack(Items.COD),
                    new ItemStack(Items.SALMON),
                    new ItemStack(Items.TROPICAL_FISH),
                    new ItemStack(Items.PUFFERFISH));
          };
      for (var ingredient : secondary) {
        var ingredients = NonNullList.<Ingredient>create();
        ingredients.add(
            io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe.primaryIngredient(kind));
        ingredients.add(Ingredient.of(ingredient));
        if (kind == io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe.Kind.ABUNDANCE)
          ingredients.add(Ingredient.of(Items.NETHER_STAR));
        String role = kind.name().toLowerCase(java.util.Locale.ROOT) + "_slime";
        var key = ForgeRegistries.ITEMS.getKey(ingredient.getItem());
        recipes.add(
            new ShapelessRecipe(
                ResourceLocation.fromNamespaceAndPath(
                    Slimic.MOD_ID, role + "/" + key.getNamespace() + "/" + key.getPath()),
                "slimic:" + role,
                CraftingBookCategory.MISC,
                io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe.specialize(
                    kind,
                    new ItemStack(
                        io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe.source(kind)),
                    ingredient),
                ingredients));
      }
      var item = io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe.result(kind);
      registration.addIngredientInfo(
          new ItemStack(item),
          VanillaTypes.ITEM_STACK,
          Component.translatable("jei.slimic." + ForgeRegistries.ITEMS.getKey(item).getPath()));
    }
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
    registration.addIngredientInfo(
        new ItemStack(ModItems.HERALD_SLIME.get()),
        VanillaTypes.ITEM_STACK,
        Component.translatable("jei.slimic.herald_slime"));
    registration.addIngredientInfo(
        new ItemStack(ModItems.CREATIVE_SLIME_TUNER.get()),
        VanillaTypes.ITEM_STACK,
        Component.translatable("jei.slimic.creative_slime_tuner"));
  }
}
