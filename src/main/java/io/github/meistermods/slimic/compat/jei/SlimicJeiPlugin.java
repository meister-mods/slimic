package io.github.meistermods.slimic.compat.jei;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.CombatSlime;
import io.github.meistermods.slimic.entity.HeraldSlime;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.item.SlimeArmorItem;
import io.github.meistermods.slimic.recipe.CompanionRecipe;
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
              NonNullList.of(Ingredient.EMPTY, Ingredient.of(friendly), Ingredient.of(tool))));
    }
    recipes.add(
        new ShapelessRecipe(
            ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "transport_slime/chest"),
            "slimic:transport_slime",
            CraftingBookCategory.MISC,
            TransportSlimeRecipe.specialize(new ItemStack(ModItems.FRIENDLY_SLIME.get())),
            NonNullList.of(
                Ingredient.EMPTY,
                Ingredient.of(ModItems.FRIENDLY_SLIME.get()),
                Ingredient.of(Items.CHEST))));
    recipes.add(
        new ShapelessRecipe(
            ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "herald_slime/firework"),
            "slimic:herald_slime",
            CraftingBookCategory.MISC,
            HeraldSlimeRecipe.specialize(
                new ItemStack(ModItems.FRIENDLY_SLIME.get()), HeraldSlime.defaultFirework()),
            NonNullList.of(
                Ingredient.EMPTY,
                Ingredient.of(ModItems.FRIENDLY_SLIME.get()),
                Ingredient.of(Items.FIREWORK_ROCKET))));
    for (var item : ForgeRegistries.ITEMS.getValues()) {
      ItemStack stack = new ItemStack(item);
      var key = ForgeRegistries.ITEMS.getKey(item);
      if (CombatSlime.isSword(stack))
        recipes.add(
            new ShapelessRecipe(
                ResourceLocation.fromNamespaceAndPath(
                    Slimic.MOD_ID, "combat_slime/" + key.getNamespace() + "/" + key.getPath()),
                "slimic:combat_slime",
                CraftingBookCategory.MISC,
                CompanionRecipe.combat(new ItemStack(ModItems.FRIENDLY_SLIME.get()), stack),
                NonNullList.of(
                    Ingredient.EMPTY,
                    Ingredient.of(ModItems.FRIENDLY_SLIME.get()),
                    Ingredient.of(stack))));
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
                Ingredient.EMPTY,
                Ingredient.of(ModItems.FRIENDLY_SLIME.get()),
                Ingredient.of(Items.ROTTEN_FLESH))));
    for (var item :
        new net.minecraft.world.item.Item[] {
          ModItems.COMBAT_SLIME.get(), ModItems.CLEANING_SLIME.get(), ModItems.SLIME_ARMOR.get()
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
                Ingredient.EMPTY,
                Ingredient.of(ModItems.FRIENDLY_SLIME.get()),
                Ingredient.of(Items.SADDLE))));
    registration.addIngredientInfo(
        new ItemStack(ModItems.SADDLE_SLIME.get()),
        VanillaTypes.ITEM_STACK,
        Component.translatable("jei.slimic.saddle_slime"));
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
        new ItemStack(ModItems.SLIME_TUNER.get()),
        VanillaTypes.ITEM_STACK,
        Component.translatable("jei.slimic.slime_tuner"));
  }
}
