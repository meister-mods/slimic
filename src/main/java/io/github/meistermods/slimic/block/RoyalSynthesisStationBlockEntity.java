package io.github.meistermods.slimic.block;

import io.github.meistermods.slimic.menu.RoyalSynthesisMenu;
import io.github.meistermods.slimic.registry.ModBlocks;
import io.github.meistermods.slimic.world.RoyalSynthesisData;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.*;

/** Physical inputs remain in the block; committed results belong to the persistent mailbox. */
public final class RoyalSynthesisStationBlockEntity extends BlockEntity
    implements Container, MenuProvider {
  private final NonNullList<ItemStack> items = NonNullList.withSize(4, ItemStack.EMPTY);
  private LazyOptional<IItemHandler> capability =
      LazyOptional.of(() -> new net.minecraftforge.items.wrapper.InvWrapper(this));
  private UUID applied;
  private UUID origin;

  public UUID origin() {
    return origin;
  }

  private boolean reconciling;
  public boolean busy;

  public RoyalSynthesisStationBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlocks.ROYAL_SYNTHESIS_ENTITY.get(), pos, state);
  }

  public List<ItemStack> contents() {
    reconcile();
    return items.stream().map(ItemStack::copy).toList();
  }

  public boolean matches(List<ItemStack> expected) {
    if (expected.size() != 4) return false;
    for (int i = 0; i < 4; i++) if (!ItemStack.matches(items.get(i), expected.get(i))) return false;
    return true;
  }

  public boolean canUse(Player player) {
    return level != null
        && !isRemoved()
        && player.level() == level
        && player.isAlive()
        && !player.isSpectator()
        && player.distanceToSqr(
                worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5)
            <= 64
        && level.hasChunkAt(worldPosition)
        && level.getBlockEntity(worldPosition) == this
        && !getBlockState().getValue(RoyalSynthesisStationBlock.RUINED)
        && level.mayInteract(player, worldPosition);
  }

  public void apply(RoyalSynthesisData.Transaction transaction) {
    reconciling = true;
    try {
      for (int i = 0; i < 4; i++) items.set(i, transaction.after.get(i).copy());
      applied = transaction.id;
      setChanged();
    } finally {
      reconciling = false;
    }
  }

  public void reconcile() {
    if (reconciling || !(level instanceof ServerLevel server)) return;
    var ledger = RoyalSynthesisData.get(server);
    String dimension = level.dimension().location().toString();
    var tx = ledger.lastAt(dimension, worldPosition);
    if (tx == null || tx.id.equals(applied)) return;
    boolean same = ledger.staleImage(dimension, worldPosition, applied, items);
    if (same) apply(tx);
    else {
      // A stale chunk image must not return either consumed parent, even if another slot differs.
      for (int i = 0; i < 2; i++) {
        var stack = items.get(i);
        if (stack.hasTag()) {
          var data = stack.getTag().getCompound("SlimeData");
          if (data.hasUUID("UUID") && ledger.consumed(data.getUUID("UUID")))
            items.set(i, ItemStack.EMPTY);
        }
      }
      applied = tx.id;
      setChanged();
    }
  }

  @Override
  public void onLoad() {
    super.onLoad();
    reconcile();
  }

  @Override
  public int getContainerSize() {
    return 4;
  }

  @Override
  public boolean canPlaceItem(int slot, ItemStack stack) {
    if (busy || isRemoved() || slot < 0 || slot >= 4) return false;
    return switch (slot) {
      case 0 -> stack.is(io.github.meistermods.slimic.registry.ModItems.MINING_SLIME.get());
      case 1 -> stack.is(io.github.meistermods.slimic.registry.ModItems.ANCIENT_MINING_SLIME.get());
      case 2 -> stack.is(net.minecraft.world.item.Items.SLIME_BALL);
      default ->
          stack.is(io.github.meistermods.slimic.registry.ModItems.OLD_COMMAND_FRAGMENT.get());
    };
  }

  @Override
  public boolean canTakeItem(Container destination, int slot, ItemStack stack) {
    return !busy && !isRemoved();
  }

  @Override
  public boolean isEmpty() {
    reconcile();
    return items.stream().allMatch(ItemStack::isEmpty);
  }

  @Override
  public ItemStack getItem(int slot) {
    reconcile();
    return items.get(slot);
  }

  @Override
  public ItemStack removeItem(int slot, int count) {
    reconcile();
    if (busy) return ItemStack.EMPTY;
    var result = ContainerHelper.removeItem(items, slot, count);
    if (!result.isEmpty()) setChanged();
    return result;
  }

  @Override
  public ItemStack removeItemNoUpdate(int slot) {
    reconcile();
    return busy ? ItemStack.EMPTY : ContainerHelper.takeItem(items, slot);
  }

  @Override
  public void setItem(int slot, ItemStack stack) {
    reconcile();
    if (!busy) {
      items.set(slot, stack);
      setChanged();
    }
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
    return Component.translatable("block.slimic.royal_synthesis_station");
  }

  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
    reconcile();
    return new RoyalSynthesisMenu(id, inventory, this, worldPosition);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    ContainerHelper.loadAllItems(tag, items);
    applied = tag.hasUUID("AppliedSynthesis") ? tag.getUUID("AppliedSynthesis") : null;
    origin = tag.hasUUID("OriginSanctum") ? tag.getUUID("OriginSanctum") : null;
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    ContainerHelper.saveAllItems(tag, items);
    if (applied != null) tag.putUUID("AppliedSynthesis", applied);
    if (origin != null) tag.putUUID("OriginSanctum", origin);
  }

  @Override
  public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
    if (!isRemoved() && cap == ForgeCapabilities.ITEM_HANDLER) return capability.cast();
    return super.getCapability(cap, side);
  }

  @Override
  public void invalidateCaps() {
    super.invalidateCaps();
    capability.invalidate();
  }

  @Override
  public void reviveCaps() {
    super.reviveCaps();
    capability = LazyOptional.of(() -> new net.minecraftforge.items.wrapper.InvWrapper(this));
  }
}
