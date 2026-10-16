package io.github.meistermods.slimic.item;

import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.world.entity.EntityType;

public final class SoggySlimeItem extends FriendlySlimeItem {
  public SoggySlimeItem(Properties properties) {
    super(properties);
  }

  @Override
  protected EntityType<? extends FriendlySlime> entityType() {
    return ModEntities.SOGGY_SLIME.get();
  }
}
