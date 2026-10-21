package io.github.meistermods.slimic.entity;

import java.util.List;
import java.util.TreeMap;
import java.util.function.IntSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

/** Sparse storage keeps large Creative bags proportional to their actual contents. */
public final class SlimeBag extends ItemStackHandler {
  private final TreeMap<Integer, ItemStack> contents = new TreeMap<>();
  private final int baseSlots;
  private final IntSupplier capacity;
  private final Runnable changed;

  public SlimeBag(int baseSlots, IntSupplier capacity, Runnable changed) {
    super(0);
    this.baseSlots = baseSlots;
    this.capacity = capacity;
    this.changed = changed;
  }

  public int baseSlots() {
    return baseSlots;
  }

  public List<Integer> occupiedSlots() {
    contents.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    return List.copyOf(contents.keySet());
  }

  public static Iterable<Integer> contentsSlots(IItemHandler inventory) {
    return inventory instanceof SlimeBag bag
        ? bag.occupiedSlots()
        : () -> java.util.stream.IntStream.range(0, inventory.getSlots()).iterator();
  }

  @Override
  public int getSlots() {
    return Math.max(baseSlots, capacity.getAsInt());
  }

  @Override
  protected void validateSlotIndex(int slot) {
    if (slot < 0 || slot >= getSlots()) throw new IndexOutOfBoundsException("Bag slot " + slot);
  }

  @Override
  public ItemStack getStackInSlot(int slot) {
    validateSlotIndex(slot);
    return contents.getOrDefault(slot, ItemStack.EMPTY);
  }

  @Override
  public void setStackInSlot(int slot, ItemStack stack) {
    validateSlotIndex(slot);
    if (stack.isEmpty()) contents.remove(slot);
    else contents.put(slot, stack);
    onContentsChanged(slot);
  }

  @Override
  public int getSlotLimit(int slot) {
    return slot < capacity.getAsInt() ? 64 : 0;
  }

  @Override
  public boolean isItemValid(int slot, ItemStack stack) {
    return slot < capacity.getAsInt();
  }

  @Override
  public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
    if (stack.isEmpty()) return ItemStack.EMPTY;
    validateSlotIndex(slot);
    if (!isItemValid(slot, stack)) return stack;
    var existing = getStackInSlot(slot);
    if (!existing.isEmpty() && !ItemHandlerHelper.canItemStacksStack(existing, stack)) return stack;
    int moved =
        Math.min(
            stack.getCount(),
            Math.max(
                0, Math.min(getSlotLimit(slot), stack.getMaxStackSize()) - existing.getCount()));
    if (moved == 0) return stack;
    if (!simulate) setStackInSlot(slot, stack.copyWithCount(existing.getCount() + moved));
    return moved == stack.getCount()
        ? ItemStack.EMPTY
        : stack.copyWithCount(stack.getCount() - moved);
  }

  @Override
  public ItemStack extractItem(int slot, int amount, boolean simulate) {
    validateSlotIndex(slot);
    var existing = getStackInSlot(slot);
    if (amount <= 0 || existing.isEmpty()) return ItemStack.EMPTY;
    int moved = Math.min(amount, existing.getCount());
    if (!simulate) setStackInSlot(slot, existing.copyWithCount(existing.getCount() - moved));
    return existing.copyWithCount(moved);
  }

  @Override
  protected void onContentsChanged(int slot) {
    changed.run();
  }

  @Override
  public CompoundTag serializeNBT() {
    var tag = new CompoundTag();
    tag.putInt("Size", getSlots());
    var items = new ListTag();
    contents.forEach(
        (slot, stack) -> {
          if (!stack.isEmpty()) {
            var item = new CompoundTag();
            item.putInt("Slot", slot);
            stack.save(item);
            items.add(item);
          }
        });
    tag.put("Items", items);
    return tag;
  }

  @Override
  public void deserializeNBT(CompoundTag tag) {
    contents.clear();
    for (var entry : tag.getList("Items", Tag.TAG_COMPOUND)) {
      var item = (CompoundTag) entry;
      int slot = item.getInt("Slot");
      var stack = ItemStack.of(item);
      if (slot >= 0 && slot < getSlots() && !stack.isEmpty()) contents.put(slot, stack);
    }
    changed.run();
  }

  public SlimeBag copyForSimulation() {
    int limit = capacity.getAsInt();
    var copy = new SlimeBag(baseSlots, () -> limit, () -> {});
    contents.forEach((slot, stack) -> copy.contents.put(slot, stack.copy()));
    return copy;
  }

  public static ItemStack insertItemStacked(
      IItemHandler handler, ItemStack stack, boolean simulate) {
    if (!(handler instanceof SlimeBag bag))
      return ItemHandlerHelper.insertItemStacked(handler, stack, simulate);
    if (simulate) return insertItemStacked(bag.copyForSimulation(), stack, false);
    var remaining = stack;
    for (int slot : bag.occupiedSlots()) {
      if (remaining.isEmpty()) break;
      remaining = bag.insertItem(slot, remaining, false);
    }
    while (!remaining.isEmpty()) {
      int empty = 0;
      for (int slot : bag.contents.keySet()) {
        if (slot > empty) break;
        if (slot == empty) empty++;
      }
      if (empty >= bag.capacity.getAsInt()) break;
      remaining = bag.insertItem(empty, remaining, false);
    }
    return remaining;
  }
}
