package io.github.meistermods.slimic.block;

import io.github.meistermods.slimic.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class BrokenSlimeBlockEntity extends BlockEntity {
  private ItemStack slime = ItemStack.EMPTY;

  public BrokenSlimeBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlocks.BROKEN_SLIME_ENTITY.get(), pos, state);
  }

  public ItemStack getSlime() {
    return slime.copy();
  }

  public void setSlime(ItemStack stack) {
    slime = stack.copyWithCount(1);
    setChanged();
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    slime = ItemStack.of(tag.getCompound("Slime"));
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    if (!slime.isEmpty()) tag.put("Slime", slime.save(new CompoundTag()));
  }
}
