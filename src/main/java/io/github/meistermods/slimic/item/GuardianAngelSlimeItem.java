package io.github.meistermods.slimic.item;

import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;

public final class GuardianAngelSlimeItem extends FriendlySlimeItem {
  public GuardianAngelSlimeItem(Properties properties) {
    super(properties);
  }

  @Override
  protected EntityType<? extends FriendlySlime> entityType() {
    return ModEntities.GUARDIAN_ANGEL_SLIME.get();
  }

  @Override
  protected void initializeSlime(FriendlySlime slime, ItemStack stack) {
    var guardian = (GuardianAngelSlime) slime;
    if (stack.hasTag() && stack.getTag().contains("InitialGuardianTotem"))
      guardian.setTotems(ItemStack.of(stack.getTag().getCompound("InitialGuardianTotem")));
  }
}
