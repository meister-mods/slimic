package io.github.meistermods.slimic.item;

import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.world.entity.EntityType;

public final class HealingSlimeItem extends FriendlySlimeItem {
  public HealingSlimeItem(Properties properties) {
    super(properties);
  }

  @Override
  protected EntityType<? extends FriendlySlime> entityType() {
    return ModEntities.HEALING_SLIME.get();
  }
}
