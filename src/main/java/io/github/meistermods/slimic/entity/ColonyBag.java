package io.github.meistermods.slimic.entity;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** A view into the real member inventories, never a second copy of their cargo. */
public final class ColonyBag extends SlimeBag {
  private final Supplier<List<SlimeBag>> members;
  private final Runnable changed;
  private final Supplier<List<Integer>> spans;

  public ColonyBag(
      int baseSlots,
      Supplier<List<SlimeBag>> members,
      Supplier<List<Integer>> spans,
      Runnable changed) {
    super(baseSlots, () -> 0, changed);
    this.members = members;
    this.changed = changed;
    this.spans = spans;
  }

  private SlimeBag bag(int slot) {
    validateSlotIndex(slot);
    var bags = members.get();
    var widths = spans.get();
    for (int i = 0; i < bags.size(); i++) {
      if (slot < widths.get(i)) return bags.get(i);
      slot -= widths.get(i);
    }
    throw new IndexOutOfBoundsException();
  }

  private int local(int slot) {
    for (int width : spans.get()) {
      if (slot < width) return slot;
      slot -= width;
    }
    throw new IndexOutOfBoundsException();
  }

  @Override
  public int getSlots() {
    return Math.toIntExact(spans.get().stream().mapToLong(Integer::longValue).sum());
  }

  @Override
  public int storageSlots() {
    return getSlots();
  }

  @Override
  public List<Integer> occupiedSlots() {
    List<Integer> slots = new ArrayList<>();
    int offset = 0;
    var bags = members.get();
    var widths = spans.get();
    for (int i = 0; i < bags.size(); i++) {
      SlimeBag bag = bags.get(i);
      for (int slot : bag.occupiedSlots()) slots.add(offset + slot);
      offset += widths.get(i);
    }
    return slots;
  }

  @Override
  public ItemStack getStackInSlot(int slot) {
    SlimeBag bag = bag(slot);
    int local = local(slot);
    return local < bag.getSlots() ? bag.getStackInSlot(local) : ItemStack.EMPTY;
  }

  @Override
  public void setStackInSlot(int slot, ItemStack stack) {
    if (local(slot) >= bag(slot).getSlots() && stack.isEmpty()) return;
    bag(slot).setStackInSlot(local(slot), stack);
    changed.run();
  }

  @Override
  public int getSlotLimit(int slot) {
    return bag(slot).getSlotLimit(local(slot));
  }

  @Override
  public boolean isItemValid(int slot, ItemStack stack) {
    return bag(slot).isItemValid(local(slot), stack);
  }

  @Override
  public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
    if (local(slot) >= bag(slot).getSlots()) return stack;
    ItemStack left = bag(slot).insertItem(local(slot), stack, simulate);
    if (!simulate) changed.run();
    return left;
  }

  @Override
  public ItemStack extractItem(int slot, int amount, boolean simulate) {
    if (local(slot) >= bag(slot).getSlots()) return ItemStack.EMPTY;
    ItemStack out = bag(slot).extractItem(local(slot), amount, simulate);
    if (!simulate) changed.run();
    return out;
  }

  @Override
  public int firstEmptySlot() {
    int offset = 0;
    var bags = members.get();
    var widths = spans.get();
    for (int i = 0; i < bags.size(); i++) {
      SlimeBag bag = bags.get(i);
      int slot = bag.firstEmptySlot();
      if (slot >= 0) return offset + slot;
      offset += widths.get(i);
    }
    return -1;
  }

  @Override
  public boolean sortContents() {
    if (!sortMemberBags(members.get())) return false;
    changed.run();
    return true;
  }

  @Override
  public SlimeBag copyForSimulation() {
    List<SlimeBag> copies = members.get().stream().map(SlimeBag::copyForSimulation).toList();
    List<Integer> widths = List.copyOf(spans.get());
    return new ColonyBag(baseSlots(), () -> copies, () -> widths, () -> {});
  }

  // Member NBT is canonical. Native BagSlime's parallel inventory field intentionally stays empty.
  @Override
  public CompoundTag serializeNBT() {
    return new CompoundTag();
  }

  @Override
  public void deserializeNBT(CompoundTag tag) {}
}
