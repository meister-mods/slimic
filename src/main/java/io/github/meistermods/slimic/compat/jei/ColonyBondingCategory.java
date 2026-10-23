package io.github.meistermods.slimic.compat.jei;

import io.github.meistermods.slimic.registry.ModItems;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public final class ColonyBondingCategory implements IRecipeCategory<ColonyBondingCategory.Recipe> {
  public record Recipe(String id, Item ordinary, Item colony, boolean unbind) {}

  public static List<Recipe> recipes() {
    return List.of(
        new Recipe(
            "transport_bind",
            ModItems.TRANSPORT_SLIME.get(),
            ModItems.BOUND_TRANSPORT_SLIME.get(),
            false),
        new Recipe(
            "combat_bind", ModItems.COMBAT_SLIME.get(), ModItems.BOUND_COMBAT_SLIME.get(), false),
        new Recipe(
            "transport_unbind",
            ModItems.TRANSPORT_SLIME.get(),
            ModItems.BOUND_TRANSPORT_SLIME.get(),
            true),
        new Recipe(
            "combat_unbind", ModItems.COMBAT_SLIME.get(), ModItems.BOUND_COMBAT_SLIME.get(), true));
  }

  public static final RecipeType<Recipe> TYPE =
      RecipeType.create("slimic", "colony_bonding", Recipe.class);
  private final IDrawable icon;

  public ColonyBondingCategory(IGuiHelper gui) {
    icon = gui.createDrawableItemLike(ModItems.COLONY_BONDING_STATION.get());
  }

  @Override
  public RecipeType<Recipe> getRecipeType() {
    return TYPE;
  }

  @Override
  public Component getTitle() {
    return Component.translatable("block.slimic.colony_bonding_station");
  }

  @Override
  public int getWidth() {
    return 206;
  }

  @Override
  public int getHeight() {
    return 132;
  }

  @Override
  public IDrawable getIcon() {
    return icon;
  }

  @Override
  public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
    builder
        .addInputSlot(1, 5)
        .setStandardSlotBackground()
        .addItemStack((recipe.unbind ? recipe.colony : recipe.ordinary).getDefaultInstance());
    if (!recipe.unbind)
      builder
          .addInputSlot(37, 5)
          .setStandardSlotBackground()
          .addItemStack(recipe.ordinary.getDefaultInstance());
    builder
        .addOutputSlot(180, 5)
        .setOutputSlotBackground()
        .addItemStack((recipe.unbind ? recipe.ordinary : recipe.colony).getDefaultInstance());
    if (recipe.unbind)
      builder
          .addOutputSlot(144, 5)
          .setOutputSlotBackground()
          .addItemStack(recipe.ordinary.getDefaultInstance());
    builder
        .addSlot(RecipeIngredientRole.CATALYST, 1, 35)
        .setStandardSlotBackground()
        .addItemStack(ModItems.COLONY_BONDING_STATION.get().getDefaultInstance());
  }

  @Override
  public void draw(
      Recipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double x, double y) {
    var font = Minecraft.getInstance().font;
    graphics.drawString(
        font,
        Component.literal(recipe.unbind ? "→" : "+"),
        recipe.unbind ? 94 : 23,
        9,
        0x404040,
        false);
    if (!recipe.unbind) graphics.drawString(font, Component.literal("→"), 110, 9, 0x404040, false);
    graphics.drawWordWrap(
        font, Component.translatable("jei.slimic.bonding.controls"), 24, 35, 180, 0x404040);
    graphics.drawWordWrap(
        font, Component.translatable("jei.slimic.bonding.members"), 0, 65, 204, 0x404040);
    graphics.drawWordWrap(
        font, Component.translatable("jei.slimic.bonding.safety"), 0, 99, 204, 0x404040);
  }

  @Override
  public ResourceLocation getRegistryName(Recipe recipe) {
    return ResourceLocation.fromNamespaceAndPath("slimic", "bonding/" + recipe.id);
  }
}
