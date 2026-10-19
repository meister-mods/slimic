package io.github.meistermods.slimic.recipe;

import io.github.meistermods.slimic.entity.AttentionSlime;
import io.github.meistermods.slimic.registry.ModItems;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** Common alternatives for recipes that specialize an ordinary companion. */
public final class CompanionBase {
  private CompanionBase() {}

  public static boolean accepts(ItemStack stack) {
    return stack.is(ModItems.FRIENDLY_SLIME.get()) || stack.is(ModItems.ATTENTION_SLIME.get());
  }

  public static void copyData(ItemStack source, ItemStack result) {
    if (!source.hasTag()) return;
    var data = source.getTag().copy();
    if (source.is(ModItems.ATTENTION_SLIME.get()) && data.contains("SlimeData", Tag.TAG_COMPOUND))
      AttentionSlime.removeProfessionData(data.getCompound("SlimeData"));
    result.setTag(data);
  }

  public static Ingredient ingredient() {
    return Ingredient.of(ModItems.FRIENDLY_SLIME.get(), ModItems.ATTENTION_SLIME.get());
  }
}
