package io.github.meistermods.slimic.item;

import io.github.meistermods.slimic.entity.CombatSlime;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

public final class CombatSlimeItem extends FriendlySlimeItem {
  public static final String INITIAL_SWORD = "InitialSword";

  public CombatSlimeItem(Properties properties) {
    super(properties);
  }

  @Override
  protected EntityType<? extends FriendlySlime> entityType() {
    return ModEntities.COMBAT_SLIME.get();
  }

  @Override
  protected void initializeSlime(FriendlySlime slime, ItemStack stack) {
    if (stack.hasTag() && stack.getTag().contains(INITIAL_SWORD)) {
      ItemStack sword = ItemStack.of(stack.getTag().getCompound(INITIAL_SWORD));
      if (CombatSlime.isSword(sword)) ((CombatSlime) slime).setEquipment(0, sword.copyWithCount(1));
    }
  }
}
