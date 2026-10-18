package io.github.meistermods.slimic.recipe;

import io.github.meistermods.slimic.entity.FishingSlime;
import io.github.meistermods.slimic.registry.ModItems;
import io.github.meistermods.slimic.registry.ModRecipes;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Data-preserving, shapeless specialization recipes. Ingredients are consumed exactly once. */
public final class AdvancedCompanionRecipe extends CustomRecipe {
  public enum Kind {
    FISHING,
    ABUNDANCE,
    TUNNEL,
    DRAGON,
    MEOW
  }

  private final Kind kind;

  public AdvancedCompanionRecipe(ResourceLocation id, CraftingBookCategory category, Kind kind) {
    super(id, category);
    this.kind = kind;
  }

  public static Item source(Kind kind) {
    return switch (kind) {
      case ABUNDANCE -> ModItems.HEALING_SLIME.get();
      case TUNNEL -> ModItems.MINING_SLIME.get();
      case DRAGON -> ModItems.COMBAT_SLIME.get();
      default -> ModItems.FRIENDLY_SLIME.get();
    };
  }

  public static Item result(Kind kind) {
    return switch (kind) {
      case FISHING -> ModItems.FISHING_SLIME.get();
      case ABUNDANCE -> ModItems.ABUNDANCE_SLIME.get();
      case TUNNEL -> ModItems.TUNNEL_SLIME.get();
      case DRAGON -> ModItems.DRAGON_SLIME.get();
      case MEOW -> ModItems.MEOW_SLIME.get();
    };
  }

  private boolean secondary(ItemStack stack) {
    return switch (kind) {
      case FISHING -> FishingSlime.isRod(stack);
      case ABUNDANCE -> stack.is(Items.BONE_MEAL);
      case TUNNEL -> stack.is(Items.NETHER_STAR);
      case DRAGON -> stack.is(Items.DRAGON_HEAD);
      case MEOW ->
          stack.is(Items.COD)
              || stack.is(Items.SALMON)
              || stack.is(Items.TROPICAL_FISH)
              || stack.is(Items.PUFFERFISH);
    };
  }

  @Override
  public boolean matches(CraftingContainer grid, Level level) {
    boolean primary = false, secondary = false, star = kind != Kind.ABUNDANCE;
    for (int i = 0; i < grid.getContainerSize(); i++) {
      var stack = grid.getItem(i);
      if (stack.isEmpty()) continue;
      if (!primary && stack.is(source(kind))) primary = true;
      else if (!secondary && secondary(stack)) secondary = true;
      else if (!star && stack.is(Items.NETHER_STAR)) star = true;
      else return false;
    }
    return primary && secondary && star;
  }

  public static ItemStack specialize(Kind kind, ItemStack source, ItemStack secondary) {
    var result = new ItemStack(result(kind));
    if (source.hasTag()) result.setTag(source.getTag().copy());
    if (result.getOrCreateTag().contains("SlimeData")) {
      var data = result.getTag().getCompound("SlimeData");
      data.putBoolean("Sitting", true);
      for (var key : List.of("MiningWorking", "CombatWorking", "HealingWorking"))
        data.putBoolean(key, false);
      for (var key :
          List.of(
              "MiningQuarry", "MiningWallPending", "MiningHasAnchor", "MiningDrainedWaterLayer"))
        data.remove(key);
    }
    if (kind == Kind.FISHING)
      result
          .getOrCreateTag()
          .put("InitialFishingRod", secondary.copyWithCount(1).save(new CompoundTag()));
    return result;
  }

  @Override
  public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
    if (!matches(grid, null)) return ItemStack.EMPTY;
    ItemStack first = ItemStack.EMPTY, second = ItemStack.EMPTY;
    for (int i = 0; i < grid.getContainerSize(); i++) {
      var stack = grid.getItem(i);
      if (stack.is(source(kind))) first = stack;
      else if (secondary(stack)) second = stack;
    }
    return specialize(kind, first, second);
  }

  @Override
  public boolean canCraftInDimensions(int width, int height) {
    return width * height >= (kind == Kind.ABUNDANCE ? 3 : 2);
  }

  @Override
  public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid) {
    return NonNullList.withSize(grid.getContainerSize(), ItemStack.EMPTY);
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return switch (kind) {
      case FISHING -> ModRecipes.FISHING_SLIME.get();
      case ABUNDANCE -> ModRecipes.ABUNDANCE_SLIME.get();
      case TUNNEL -> ModRecipes.TUNNEL_SLIME.get();
      case DRAGON -> ModRecipes.DRAGON_SLIME.get();
      case MEOW -> ModRecipes.MEOW_SLIME.get();
    };
  }
}
