package io.github.meistermods.slimic.block;

import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.menu.ColonyBondingMenu;
import io.github.meistermods.slimic.registry.ModBlocks;
import io.github.meistermods.slimic.world.ColonyBondingData;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Physical inputs stay recoverable; the ledger owns committed membership and output delivery. */
public final class ColonyBondingStationBlockEntity extends BlockEntity
    implements Container, MenuProvider {
  private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
  public boolean busy;
  private boolean reconciling;

  public ColonyBondingStationBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlocks.COLONY_BONDING_ENTITY.get(), pos, state);
  }

  public boolean canUse(Player player) {
    return level instanceof ServerLevel server
        && player.isAlive()
        && !player.isSpectator()
        && player.level() == level
        && player.distanceToSqr(worldPosition.getCenter()) <= 64
        && server.hasChunkAt(worldPosition)
        && server.getBlockEntity(worldPosition) == this
        && !isRemoved()
        && server.mayInteract(player, worldPosition);
  }

  public List<ItemStack> contents() {
    reconcile();
    return items.stream().map(ItemStack::copy).toList();
  }

  public boolean matches(List<ItemStack> before) {
    return before.size() == 2
        && ItemStack.matches(before.get(0), items.get(0))
        && ItemStack.matches(before.get(1), items.get(1));
  }

  public void clearCommitted() {
    items.set(0, ItemStack.EMPTY);
    items.set(1, ItemStack.EMPTY);
    setChanged();
  }

  public void reconcile() {
    if (busy || reconciling || !(level instanceof ServerLevel server)) return;
    reconciling = true;
    try {
      var ledger = ColonyBondingData.get(server);
      for (int slot = 0; slot < 2; slot++) {
        ItemStack item = items.get(slot);
        if (!item.hasTag()) continue;
        var data = item.getTag().getCompound("SlimeData");
        if (!data.hasUUID("UUID")) continue;
        UUID id = data.getUUID("UUID");
        var binding = ledger.binding(id);
        if (ledger.memberBound(id)
            || binding != null && binding.state == ColonyBondingData.State.RESTORED) {
          items.set(slot, ItemStack.EMPTY);
          setChanged();
        }
      }
    } finally {
      reconciling = false;
    }
  }

  @Override
  public void onLoad() {
    super.onLoad();
    reconcile();
  }

  @Override
  public int getContainerSize() {
    return 2;
  }

  @Override
  public int getMaxStackSize() {
    return 1;
  }

  @Override
  public boolean isEmpty() {
    reconcile();
    return items.stream().allMatch(ItemStack::isEmpty);
  }

  @Override
  public ItemStack getItem(int slot) {
    reconcile();
    return slot >= 0 && slot < 2 ? items.get(slot) : ItemStack.EMPTY;
  }

  @Override
  public ItemStack removeItem(int slot, int count) {
    reconcile();
    if (busy || slot < 0 || slot >= 2) return ItemStack.EMPTY;
    var result = ContainerHelper.removeItem(items, slot, count);
    setChanged();
    return result;
  }

  @Override
  public ItemStack removeItemNoUpdate(int slot) {
    reconcile();
    return busy || slot < 0 || slot >= 2 ? ItemStack.EMPTY : ContainerHelper.takeItem(items, slot);
  }

  @Override
  public void setItem(int slot, ItemStack item) {
    reconcile();
    if (busy || slot < 0 || slot >= 2) return;
    items.set(slot, item);
    setChanged();
  }

  @Override
  public boolean canPlaceItem(int slot, ItemStack item) {
    return !busy && item.getItem() instanceof FriendlySlimeItem;
  }

  @Override
  public void clearContent() {
    if (!busy) {
      items.clear();
      setChanged();
    }
  }

  @Override
  public boolean stillValid(Player player) {
    return canUse(player);
  }

  @Override
  public Component getDisplayName() {
    return Component.translatable("block.slimic.colony_bonding_station");
  }

  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
    return new ColonyBondingMenu(id, inventory, this, worldPosition);
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    ContainerHelper.saveAllItems(tag, items);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    ContainerHelper.loadAllItems(tag, items);
  }
}
