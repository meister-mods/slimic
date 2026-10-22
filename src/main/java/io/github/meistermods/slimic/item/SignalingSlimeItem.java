package io.github.meistermods.slimic.item;

import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.SignalingSlime;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

public final class SignalingSlimeItem extends FriendlySlimeItem {
  public static final String INITIAL_FIREWORK = "InitialFirework";

  public SignalingSlimeItem(Properties properties) {
    super(properties);
  }

  @Override
  protected EntityType<? extends FriendlySlime> entityType() {
    return ModEntities.SIGNALING_SLIME.get();
  }

  @Override
  protected void initializeSlime(FriendlySlime slime, ItemStack stack) {
    if (slime instanceof SignalingSlime signaling
        && stack.hasTag()
        && stack.getTag().contains(INITIAL_FIREWORK))
      signaling.setFirework(ItemStack.of(stack.getTag().getCompound(INITIAL_FIREWORK)));
  }
}
