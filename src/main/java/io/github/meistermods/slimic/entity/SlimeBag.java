package io.github.meistermods.slimic.entity;

import java.util.ArrayList;
import java.util.HashMap;
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
public class SlimeBag extends ItemStackHandler {
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

  public int storageSlots() {
    return Math.max(capacity.getAsInt(), contents.isEmpty() ? 0 : contents.lastKey() + 1);
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
    return Math.max(baseSlots, storageSlots());
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
      // Capacity may have been lowered in Creative. Existing cargo remains extractable;
      // the insertion limit still follows capacity, so this cannot create extra usable slots.
      if (slot >= 0 && slot < Integer.MAX_VALUE && !stack.isEmpty()) contents.put(slot, stack);
    }
    changed.run();
  }

  public SlimeBag copyForSimulation() {
    int limit = capacity.getAsInt();
    var copy = new SlimeBag(baseSlots, () -> limit, () -> {});
    contents.forEach((slot, stack) -> copy.contents.put(slot, stack.copy()));
    return copy;
  }

  /** Sorts actual cargo, including stored overflow, without iterating unused Creative capacity. */
  public boolean sortContents() {
    return sortMemberBags(List.of(this));
  }

  /** Plans all member inventories before changing any canonical cargo. */
  protected static boolean sortMemberBags(List<SlimeBag> bags) {
    var grouped = new HashMap<CompoundTag, List<CargoGroup>>();
    var overflow = new ArrayList<List<Integer>>();
    var limits = new ArrayList<Integer>();
    for (SlimeBag bag : bags) {
      int capacity = Math.max(0, bag.capacity.getAsInt());
      limits.add(capacity);
      var retained = new ArrayList<Integer>();
      for (int slot : bag.occupiedSlots()) {
        ItemStack stack = bag.getStackInSlot(slot);
        int limit = Math.min(64, stack.getMaxStackSize());
        // Malformed overfilled stacks remain untouched rather than creating extra storage.
        if (limit <= 0 || stack.getCount() > limit) return false;
        if (slot >= capacity) retained.add(slot);
        CompoundTag key = stack.copyWithCount(1).serializeNBT();
        var variants = grouped.computeIfAbsent(key, ignored -> new ArrayList<>());
        CargoGroup group = null;
        for (CargoGroup candidate : variants)
          if (ItemHandlerHelper.canItemStacksStack(candidate.stack, stack)) {
            group = candidate;
            break;
          }
        if (group == null) {
          group = new CargoGroup(stack.copyWithCount(1), limit);
          variants.add(group);
        }
        group.count += stack.getCount();
      }
      overflow.add(retained);
    }
    var groups = new ArrayList<CargoGroup>();
    grouped.values().forEach(groups::addAll);
    groups.sort(
        java.util.Comparator.comparing(
                (CargoGroup group) ->
                    net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(group.stack.getItem())
                        .toString())
            .thenComparing(group -> group.stack.serializeNBT().toString()));
    var ordered = new ArrayList<ItemStack>();
    for (CargoGroup group : groups)
      while (group.count > 0) {
        int count = (int) Math.min(group.limit, group.count);
        ordered.add(group.stack.copyWithCount(count));
        group.count -= count;
      }
    var plan = new ArrayList<TreeMap<Integer, ItemStack>>();
    int next = 0;
    for (int member = 0; member < bags.size(); member++) {
      var slots = new TreeMap<Integer, ItemStack>();
      for (int slot = 0; slot < limits.get(member) && next < ordered.size(); slot++)
        slots.put(slot, ordered.get(next++));
      plan.add(slots);
    }
    // Lowered capacity never discards cargo or grants new insertion slots. Only occupied
    // extraction-only positions may hold the remaining stacks after usable slots are packed.
    for (int member = 0; member < bags.size() && next < ordered.size(); member++)
      for (int slot : overflow.get(member)) {
        if (next == ordered.size()) break;
        plan.get(member).put(slot, ordered.get(next++));
      }
    if (next != ordered.size()) return false;
    for (int member = 0; member < bags.size(); member++) {
      bags.get(member).contents.clear();
      bags.get(member).contents.putAll(plan.get(member));
    }
    // Notify only after all member records contain the final result.
    for (SlimeBag bag : bags) bag.changed.run();
    return true;
  }

  private static final class CargoGroup {
    final ItemStack stack;
    final int limit;
    long count;

    CargoGroup(ItemStack stack, int limit) {
      this.stack = stack;
      this.limit = limit;
    }
  }

  /** Sparse gap lookup is also implemented by partitioned colony storage. */
  public int firstEmptySlot() {
    int empty = 0;
    for (int slot : contents.keySet()) {
      if (slot > empty) break;
      if (slot == empty) empty++;
    }
    return empty < capacity.getAsInt() ? empty : -1;
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
      int empty = bag.firstEmptySlot();
      if (empty < 0) break;
      remaining = bag.insertItem(empty, remaining, false);
    }
    return remaining;
  }
}
