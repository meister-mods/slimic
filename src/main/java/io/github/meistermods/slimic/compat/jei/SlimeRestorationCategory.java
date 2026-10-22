package io.github.meistermods.slimic.compat.jei;

import io.github.meistermods.slimic.registry.ModWorldBlocks;
import java.util.Locale;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SlimeRestorationCategory implements IRecipeCategory<SlimeRestorationRecipe> {
  public static final RecipeType<SlimeRestorationRecipe> TYPE =
      RecipeType.create("slimic", "slime_restoration", SlimeRestorationRecipe.class);
  private final IDrawable icon;
  private final IDrawableStatic arrow;
  private final IDrawableStatic plus;

  public SlimeRestorationCategory(IGuiHelper gui) {
    icon = gui.createDrawableItemLike(ModWorldBlocks.block("command_pedestal"));
    arrow = gui.getRecipeArrow();
    plus = gui.getRecipePlusSign();
  }

  @Override
  public RecipeType<SlimeRestorationRecipe> getRecipeType() {
    return TYPE;
  }

  @Override
  public Component getTitle() {
    return Component.translatable("jei.slimic.restoration.title");
  }

  @Override
  public int getWidth() {
    return 168;
  }

  @Override
  public int getHeight() {
    return 121;
  }

  @Override
  public IDrawable getIcon() {
    return icon;
  }

  @Override
  public void setRecipe(
      IRecipeLayoutBuilder builder, SlimeRestorationRecipe recipe, IFocusGroup focuses) {
    builder
        .addInputSlot(1, 25)
        .setStandardSlotBackground()
        .setSlotName("core")
        .addItemStack(recipe.core());
    builder
        .addInputSlot(37, 25)
        .setStandardSlotBackground()
        .setSlotName("reagent")
        .addItemStack(recipe.reagent());
    builder
        .addInputSlot(73, 25)
        .setStandardSlotBackground()
        .setSlotName("slimeball")
        .addItemStack(new ItemStack(Items.SLIME_BALL));
    builder
        .addOutputSlot(139, 25)
        .setOutputSlotBackground()
        .setSlotName("companion")
        .addItemStack(recipe.result());
    builder
        .addSlot(RecipeIngredientRole.CATALYST, 1, 56)
        .setStandardSlotBackground()
        .setSlotName("station")
        .addItemStack(new ItemStack(ModWorldBlocks.block("command_pedestal")));
  }

  @Override
  public void draw(
      SlimeRestorationRecipe recipe,
      IRecipeSlotsView slots,
      GuiGraphics graphics,
      double mouseX,
      double mouseY) {
    var font = Minecraft.getInstance().font;
    graphics.drawString(
        font,
        Component.translatable(
            "name.slimic.ancient_" + recipe.role().savedName().toLowerCase(Locale.ROOT)),
        0,
        2,
        0x404040,
        false);
    plus.draw(graphics, 21, 28);
    plus.draw(graphics, 57, 28);
    arrow.draw(graphics, 103, 25);
    graphics.drawString(
        font, Component.translatable("jei.slimic.restoration.interact"), 23, 60, 0x404040, false);
    graphics.drawString(
        font,
        Component.translatable(
            recipe.role().isMine()
                ? "jei.slimic.restoration.overworld"
                : "jei.slimic.restoration.any_dimension"),
        0,
        82,
        0x404040,
        false);
    graphics.drawString(
        font, Component.translatable("jei.slimic.restoration.arrival"), 0, 96, 0x404040, false);
    graphics.drawString(
        font, Component.translatable("jei.slimic.restoration.training"), 0, 110, 0x404040, false);
  }

  @Override
  public ResourceLocation getRegistryName(SlimeRestorationRecipe recipe) {
    return ResourceLocation.fromNamespaceAndPath(
        "slimic", "restoration/" + recipe.role().savedName().toLowerCase(Locale.ROOT));
  }
}
