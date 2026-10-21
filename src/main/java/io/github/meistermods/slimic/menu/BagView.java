package io.github.meistermods.slimic.menu;

import io.github.meistermods.slimic.entity.SlimeBag;
import java.util.function.IntSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

/** A fixed-size menu window over normal bags and arbitrarily many Creative pages. */
final class BagView implements IItemHandlerModifiable {
  private final SlimeBag bag;
  private final IntSupplier page;
  private final ItemStackHandler clientContents;

  BagView(SlimeBag bag, IntSupplier page, boolean clientSide) {
    this.bag = bag;
    this.page = page;
    clientContents = clientSide ? new ItemStackHandler(bag.baseSlots()) : null;
  }

  long logicalSlot(int slot) {
    int normalPages = (bag.baseSlots() - 1) / SlimeMenu.BAG_PAGE_SIZE + 1;
    return page.getAsInt() >= normalPages && slot < SlimeMenu.BAG_PAGE_SIZE
        ? (long) page.getAsInt() * SlimeMenu.BAG_PAGE_SIZE + slot
        : slot;
  }

  private boolean valid(int slot) {
    return logicalSlot(slot) >= 0 && logicalSlot(slot) < bag.getSlots();
  }

  @Override
  public int getSlots() {
    return bag.baseSlots();
  }

  @Override
  public ItemStack getStackInSlot(int slot) {
    if (clientContents != null) return clientContents.getStackInSlot(slot);
    return valid(slot) ? bag.getStackInSlot((int) logicalSlot(slot)) : ItemStack.EMPTY;
  }

  @Override
  public void setStackInSlot(int slot, ItemStack stack) {
    if (clientContents != null) clientContents.setStackInSlot(slot, stack);
    else if (valid(slot)) bag.setStackInSlot((int) logicalSlot(slot), stack);
  }

  @Override
  public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
    if (clientContents != null) return clientContents.insertItem(slot, stack, simulate);
    return valid(slot) ? bag.insertItem((int) logicalSlot(slot), stack, simulate) : stack;
  }

  @Override
  public ItemStack extractItem(int slot, int amount, boolean simulate) {
    if (clientContents != null) return clientContents.extractItem(slot, amount, simulate);
    return valid(slot)
        ? bag.extractItem((int) logicalSlot(slot), amount, simulate)
        : ItemStack.EMPTY;
  }

  @Override
  public int getSlotLimit(int slot) {
    return valid(slot) ? bag.getSlotLimit((int) logicalSlot(slot)) : 0;
  }

  @Override
  public boolean isItemValid(int slot, ItemStack stack) {
    return valid(slot) && bag.isItemValid((int) logicalSlot(slot), stack);
  }
}
