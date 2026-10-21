package io.github.meistermods.slimic.block;

import io.github.meistermods.slimic.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class BrokenSlimeBlockEntity extends BlockEntity {
  private ItemStack slime = ItemStack.EMPTY;
  private ItemStack ancientCore = ItemStack.EMPTY;

  public BrokenSlimeBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlocks.BROKEN_SLIME_ENTITY.get(), pos, state);
  }

  public ItemStack getSlime() {
    return slime.copy();
  }

  public ItemStack getAncientCore() {
    return ancientCore.copy();
  }

  /** Ancient corpses have no owner and never carry a player companion snapshot. */
  public void setAncientCore(ItemStack core) {
    var role = io.github.meistermods.slimic.item.AncientSlimeCoreItem.role(core);
    if (role == null || !role.isMine()) throw new IllegalArgumentException("Not a mine core");
    ancientCore = core.copyWithCount(1);
    slime = ItemStack.EMPTY;
    setChanged();
  }

  public void setSlime(ItemStack stack) {
    ancientCore = ItemStack.EMPTY;
    slime = stack.copyWithCount(1);
    setChanged();
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    slime = ItemStack.of(tag.getCompound("Slime"));
    var core = ItemStack.of(tag.getCompound("AncientCore"));
    var role = io.github.meistermods.slimic.item.AncientSlimeCoreItem.role(core);
    ancientCore = slime.isEmpty() && role != null && role.isMine() ? core : ItemStack.EMPTY;
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    if (!ancientCore.isEmpty()) tag.put("AncientCore", ancientCore.save(new CompoundTag()));
    if (!slime.isEmpty()) tag.put("Slime", slime.save(new CompoundTag()));
  }
}
