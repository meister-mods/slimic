package io.github.meistermods.slimic.compat.jei;

import io.github.meistermods.slimic.entity.SynthesisFamily;
import io.github.meistermods.slimic.registry.ModItems;
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
import net.minecraft.world.item.*;

/**
 * Display-only station recipe: primary ownership and growth are validated against actual parents.
 */
public final class RoyalSynthesisCategory implements IRecipeCategory<SynthesisFamily> {
  public static final RecipeType<SynthesisFamily> TYPE =
      RecipeType.create("slimic", "royal_synthesis", SynthesisFamily.class);
  private final IDrawable icon;

  public RoyalSynthesisCategory(IGuiHelper gui) {
    icon = gui.createDrawableItemLike(ModItems.ROYAL_SYNTHESIS_STATION.get());
  }

  @Override
  public RecipeType<SynthesisFamily> getRecipeType() {
    return TYPE;
  }

  @Override
  public Component getTitle() {
    return Component.translatable("block.slimic.royal_synthesis_station");
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
  public void setRecipe(IRecipeLayoutBuilder builder, SynthesisFamily recipe, IFocusGroup focuses) {
    builder
        .addInputSlot(1, 5)
        .setStandardSlotBackground()
        .addItemStack(recipe.normalItem().getDefaultInstance());
    builder
        .addInputSlot(37, 5)
        .setStandardSlotBackground()
        .addItemStack(recipe.ancientItem().getDefaultInstance());
    builder
        .addInputSlot(73, 5)
        .setStandardSlotBackground()
        .addItemStack(new ItemStack(Items.SLIME_BALL, 4));
    builder
        .addInputSlot(109, 5)
        .setStandardSlotBackground()
        .addItemStack(new ItemStack(ModItems.OLD_COMMAND_FRAGMENT.get(), 2));
    builder
        .addOutputSlot(180, 5)
        .setOutputSlotBackground()
        .addItemStack(recipe.resultItem().getDefaultInstance());
    builder
        .addSlot(RecipeIngredientRole.CATALYST, 1, 35)
        .setStandardSlotBackground()
        .addItemStack(ModItems.ROYAL_SYNTHESIS_STATION.get().getDefaultInstance());
  }

  @Override
  public void draw(
      SynthesisFamily recipe,
      IRecipeSlotsView slots,
      GuiGraphics graphics,
      double mouseX,
      double mouseY) {
    var font = Minecraft.getInstance().font;
    graphics.drawString(font, Component.literal("+"), 23, 9, 0x404040, false);
    graphics.drawString(font, Component.literal("+"), 59, 9, 0x404040, false);
    graphics.drawString(font, Component.literal("+"), 95, 9, 0x404040, false);
    graphics.drawString(font, Component.literal("→"), 148, 9, 0x404040, false);
    graphics.drawWordWrap(
        font, Component.translatable("jei.slimic.synthesis.controls"), 23, 36, 180, 0x404040);
    graphics.drawWordWrap(
        font, Component.translatable("jei.slimic.synthesis.refund"), 0, 64, 204, 0x404040);
    graphics.drawWordWrap(
        font, Component.translatable("jei.slimic.synthesis.safety"), 0, 98, 204, 0x404040);
  }

  @Override
  public ResourceLocation getRegistryName(SynthesisFamily recipe) {
    return ResourceLocation.fromNamespaceAndPath("slimic", "synthesis/" + recipe.id());
  }
}
