package io.github.meistermods.slimic.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemStackHandler;

public abstract class BagSlime extends FriendlySlime {
  private final ItemStackHandler bag;
  protected int bagRevision;

  protected BagSlime(EntityType<? extends BagSlime> type, Level level, int slots) {
    super(type, level);
    bag =
        new ItemStackHandler(slots) {
          @Override
          public int getSlotLimit(int slot) {
            return slot < getCapacity() ? 64 : 0;
          }

          @Override
          public boolean isItemValid(int slot, ItemStack stack) {
            return slot < getCapacity();
          }

          @Override
          protected void onContentsChanged(int slot) {
            bagRevision++;
          }
        };
  }

  public final ItemStackHandler getBag() {
    return bag;
  }

  public final boolean hasCargo() {
    for (int slot = 0; slot < bag.getSlots(); slot++)
      if (!bag.getStackInSlot(slot).isEmpty()) return true;
    return false;
  }

  public abstract void setWorking(boolean working);

  public boolean needsUnloading() {
    return false;
  }

  public boolean needsReplacementTool() {
    return false;
  }

  public boolean acceptsWorkTool(ItemStack stack) {
    return false;
  }

  public boolean receiveWorkTool(ItemStack stack) {
    return false;
  }

  protected abstract String bagTagName();

  @Override
  public boolean upgradeStat(SlimeStat stat) {
    boolean upgraded = super.upgradeStat(stat);
    if (upgraded && stat == SlimeStat.CAPACITY) bagRevision++;
    return upgraded;
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.put(bagTagName(), bag.serializeNBT());
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    CompoundTag inventory = tag.getCompound(bagTagName()).copy();
    inventory.putInt("Size", bag.getSlots());
    bag.deserializeNBT(inventory);
  }

  @Override
  protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
    super.dropCustomDeathLoot(source, looting, recentlyHit);
    for (int slot = 0; slot < bag.getSlots(); slot++) {
      if (!bag.getStackInSlot(slot).isEmpty()) spawnAtLocation(bag.getStackInSlot(slot).copy());
      bag.setStackInSlot(slot, ItemStack.EMPTY);
    }
  }
}
