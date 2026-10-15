package io.github.meistermods.slimic.block;

import io.github.meistermods.slimic.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

public final class SlimeChestBlockEntity extends BaseContainerBlockEntity {
  private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
  private final IItemHandler inventory = new InvWrapper(this);

  public SlimeChestBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlocks.SLIME_CHEST_ENTITY.get(), pos, state);
  }

  public IItemHandler inventory() {
    return inventory;
  }

  @Override
  public int getContainerSize() {
    return items.size();
  }

  @Override
  public boolean isEmpty() {
    return items.stream().allMatch(ItemStack::isEmpty);
  }

  @Override
  public ItemStack getItem(int slot) {
    return items.get(slot);
  }

  @Override
  public ItemStack removeItem(int slot, int count) {
    ItemStack removed = ContainerHelper.removeItem(items, slot, count);
    if (!removed.isEmpty()) setChanged();
    return removed;
  }

  @Override
  public ItemStack removeItemNoUpdate(int slot) {
    return ContainerHelper.takeItem(items, slot);
  }

  @Override
  public void setItem(int slot, ItemStack stack) {
    items.set(slot, stack);
    if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
    setChanged();
  }

  @Override
  public boolean stillValid(Player player) {
    return net.minecraft.world.Container.stillValidBlockEntity(this, player);
  }

  @Override
  public void clearContent() {
    items.clear();
    setChanged();
  }

  @Override
  protected Component getDefaultName() {
    return Component.translatable("block.slimic.slime_chest");
  }

  @Override
  protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
    return ChestMenu.threeRows(id, inventory, this);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    items = NonNullList.withSize(27, ItemStack.EMPTY);
    ContainerHelper.loadAllItems(tag, items);
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    ContainerHelper.saveAllItems(tag, items);
  }
}
