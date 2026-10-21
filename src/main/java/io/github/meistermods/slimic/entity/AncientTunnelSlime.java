package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A restored mining veteran; the ordinary worker's safety and logistics contracts still apply. */
public final class AncientTunnelSlime extends TunnelSlime {
  public AncientTunnelSlime(EntityType<? extends AncientTunnelSlime> type, Level level) {
    super(type, level);
    setAncientOrigin(true);
  }

  @Override
  protected boolean spendStamina(float amount) {
    return super.spendStamina(amount * .5F);
  }

  public static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block>
      ANCIENT_MASONRY =
          net.minecraft.tags.TagKey.create(
              net.minecraft.core.registries.Registries.BLOCK,
              net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                  "slimic", "ancient_masonry"));

  @Override
  public double blockWorkMultiplier(net.minecraft.world.level.block.state.BlockState state) {
    return state.is(ANCIENT_MASONRY) ? 1.5 : 1;
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    setAncientOrigin(true);
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.ANCIENT_TUNNEL_SLIME.get());
  }
}
