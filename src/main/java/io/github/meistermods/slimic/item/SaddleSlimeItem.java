package io.github.meistermods.slimic.item;

import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.SaddleSlime;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SaddleSlimeItem extends FriendlySlimeItem {
  public SaddleSlimeItem(Properties properties) {
    super(properties);
  }

  @Override
  protected EntityType<? extends FriendlySlime> entityType() {
    return ModEntities.SADDLE_SLIME.get();
  }

  @Override
  protected void initializeSlime(FriendlySlime slime, ItemStack stack) {
    // Recovered equipment is authoritative; new specialization items provide the input saddle.
    if (stack.hasTag() && stack.getTag().contains("InitialSaddle")) {
      slime.setWaiting(true);
      ItemStack saddle = ItemStack.of(stack.getTag().getCompound("InitialSaddle"));
      if (saddle.is(Items.SADDLE)) ((SaddleSlime) slime).setEquipment(0, saddle.copyWithCount(1));
    }
  }
}
