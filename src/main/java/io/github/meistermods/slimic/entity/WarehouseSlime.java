package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.registry.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class WarehouseSlime extends TransportSlime {
  public WarehouseSlime(EntityType<? extends WarehouseSlime> type, Level level) {
    super(type, level, 216);
  }

  @Override
  public int capacityMultiplier() {
    return 8;
  }

  @Override
  protected boolean storesCargo() {
    return true;
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.WAREHOUSE_SLIME.get());
  }
}
