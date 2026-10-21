package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A restored mining veteran; the ordinary worker's safety and logistics contracts still apply. */
public final class AncientMiningSlime extends MiningSlime {
  public AncientMiningSlime(EntityType<? extends AncientMiningSlime> type, Level level) {
    super(type, level);
    setAncientOrigin(true);
  }

  @Override
  protected boolean spendStamina(float amount) {
    return super.spendStamina(amount * .5F);
  }

  @Override
  protected double targetPriority(
      net.minecraft.core.BlockPos pos,
      net.minecraft.world.level.block.state.BlockState state,
      boolean visible) {
    return super.targetPriority(pos, state, visible)
        - (visible && state.is(net.minecraftforge.common.Tags.Blocks.ORES) ? 4096 : 0);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    setAncientOrigin(true);
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.ANCIENT_MINING_SLIME.get());
  }
}
