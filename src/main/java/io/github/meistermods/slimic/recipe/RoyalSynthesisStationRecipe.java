package io.github.meistermods.slimic.recipe;

import com.google.gson.JsonObject;
import io.github.meistermods.slimic.registry.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

/** A normal shaped recipe that also carries the core's origin into the reusable apparatus. */
public final class RoyalSynthesisStationRecipe extends ShapedRecipe {
  public RoyalSynthesisStationRecipe(ShapedRecipe base) {
    super(
        base.getId(),
        base.getGroup(),
        base.category(),
        base.getWidth(),
        base.getHeight(),
        base.getIngredients(),
        base.getResultItem(RegistryAccess.EMPTY),
        base.showNotification());
  }

  @Override
  public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
    var result = super.assemble(grid, access);
    for (int i = 0; i < grid.getContainerSize(); i++) {
      var item = grid.getItem(i);
      if (item.is(ModItems.ROYAL_SYNTHESIS_CORE.get())
          && item.hasTag()
          && item.getTag().hasUUID("OriginSanctum")) {
        var data = new CompoundTag();
        data.putUUID("OriginSanctum", item.getTag().getUUID("OriginSanctum"));
        result.getOrCreateTag().put("BlockEntityTag", data);
        break;
      }
    }
    return result;
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return ModRecipes.ROYAL_SYNTHESIS_STATION.get();
  }

  public static final class Serializer implements RecipeSerializer<RoyalSynthesisStationRecipe> {
    @Override
    public RoyalSynthesisStationRecipe fromJson(ResourceLocation id, JsonObject json) {
      return new RoyalSynthesisStationRecipe(RecipeSerializer.SHAPED_RECIPE.fromJson(id, json));
    }

    @Override
    public RoyalSynthesisStationRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
      return new RoyalSynthesisStationRecipe(
          RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buffer));
    }

    @Override
    public void toNetwork(FriendlyByteBuf buffer, RoyalSynthesisStationRecipe recipe) {
      RecipeSerializer.SHAPED_RECIPE.toNetwork(buffer, recipe);
    }
  }
}
