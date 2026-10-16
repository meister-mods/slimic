package io.github.meistermods.slimic.item;

import io.github.meistermods.slimic.entity.FieldSlime;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

public final class LumberjackSlimeItem extends FriendlySlimeItem {
  public LumberjackSlimeItem(Properties properties) {
    super(properties);
  }

  @Override
  protected EntityType<? extends FriendlySlime> entityType() {
    return ModEntities.LUMBERJACK_SLIME.get();
  }

  @Override
  protected void initializeSlime(FriendlySlime slime, ItemStack stack) {
    if (stack.hasTag() && stack.getTag().contains("InitialWorkTool")) {
      ItemStack tool = ItemStack.of(stack.getTag().getCompound("InitialWorkTool"));
      if (((FieldSlime) slime).matchesTool(tool))
        ((FieldSlime) slime).setTool(tool.copyWithCount(1));
    }
  }
}
