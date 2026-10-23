package io.github.meistermods.slimic.menu;

import io.github.meistermods.slimic.block.ColonyBondingStationBlockEntity;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.ModMenus;
import io.github.meistermods.slimic.world.*;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class ColonyBondingMenu extends AbstractContainerMenu {
  public static final int BIND = 0, UNBIND = 1, RECOVER = 2;
  private final Container inputs;
  private final Inventory inventory;
  private final BlockPos position;
  private final NumericContainerData data;
  private ColonyBonding.Check preview =
      new ColonyBonding.Check(ColonyBonding.Problem.MISSING, null);
  private java.util.List<ItemStack> previous = java.util.List.of();
  private long checkedAt = Long.MIN_VALUE;

  public ColonyBondingMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
    this(id, inventory, new SimpleContainer(2), buffer.readBlockPos());
  }

  public ColonyBondingMenu(int id, Inventory inventory, Container inputs, BlockPos position) {
    super(ModMenus.COLONY_BONDING.get(), id);
    this.inputs = inputs;
    this.inventory = inventory;
    this.position = position;
    data =
        new NumericContainerData(
            6,
            inventory.player.level().isClientSide,
            index -> {
              if (index == 0) return preview.problem().ordinal();
              if (index == 1 && inventory.player instanceof ServerPlayer player) {
                var ledger = ColonyBondingData.get(player.serverLevel());
                return ledger.pending(player.getUUID()) != null
                        || ledger.hasRecovery(player.getUUID())
                    ? 1
                    : 0;
              }
              var result = preview.representative();
              if (result == null) return 0;
              return switch (index) {
                case 2 -> result.getMaxHealth();
                case 3 -> result.getCapacity();
                case 4 ->
                    result.getAttributeValue(
                        net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                case 5 -> result.getMaxStamina();
                default -> 0;
              };
            });
    addDataSlots(data);
    for (int i = 0; i < 2; i++)
      addSlot(
          new Slot(inputs, i, 22 + i * 160, 38) {
            @Override
            public boolean mayPlace(ItemStack stack) {
              return stack.getItem() instanceof FriendlySlimeItem;
            }

            @Override
            public int getMaxStackSize() {
              return 1;
            }

            @Override
            public boolean mayPickup(Player player) {
              return stillValid(player)
                  && (!(inputs instanceof ColonyBondingStationBlockEntity station)
                      || !station.busy);
            }
          });
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(inventory, 9 + row * 9 + col, 79 + col * 18, 158 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 79 + col * 18, 216));
    broadcastChanges();
  }

  public ColonyBonding.Problem problem() {
    return !data.isReady()
        ? ColonyBonding.Problem.MISSING
        : ColonyBonding.Problem.values()[
            Math.max(0, Math.min(ColonyBonding.Problem.values().length - 1, (int) data.value(0)))];
  }

  public boolean pending() {
    return data.isReady() && data.value(1) != 0;
  }

  public double previewValue(int index) {
    return data.isReady() && index >= 2 && index <= 5 ? data.value(index) : 0;
  }

  public static Component message(ColonyBonding.Problem problem) {
    return Component.translatable("bonding.slimic." + problem.name().toLowerCase(Locale.ROOT));
  }

  @Override
  public void broadcastChanges() {
    if (inventory.player instanceof ServerPlayer player
        && inputs instanceof ColonyBondingStationBlockEntity station) {
      var now = station.contents();
      long time = player.level().getGameTime();
      if (checkedAt == Long.MIN_VALUE
          || time - checkedAt >= 20
          || previous.size() != 2
          || !ItemStack.matches(previous.get(0), now.get(0))
          || !ItemStack.matches(previous.get(1), now.get(1))) {
        preview = ColonyBonding.check(player, now);
        previous = now;
        checkedAt = time;
      }
    }
    super.broadcastChanges();
  }

  @Override
  public boolean stillValid(Player player) {
    return player.isAlive()
        && !player.isSpectator()
        && player.distanceToSqr(position.getCenter()) <= 64
        && (player.level().isClientSide
            || inputs instanceof ColonyBondingStationBlockEntity station && station.canUse(player));
  }

  @Override
  public boolean clickMenuButton(Player player, int button) {
    if (!(player instanceof ServerPlayer server) || !stillValid(player)) return false;
    if ((button == BIND || button == UNBIND)
        && inputs instanceof ColonyBondingStationBlockEntity station) {
      server.displayClientMessage(
          message(ColonyBonding.execute(server, station, button == UNBIND)), false);
    } else if (button == RECOVER) {
      var ledger = ColonyBondingData.get(server.serverLevel());
      ledger.recover(server);
    } else return false;
    broadcastChanges();
    return true;
  }

  @Override
  public void clicked(int slot, int button, ClickType type, Player player) {
    if (stillValid(player)
        && (!(inputs instanceof ColonyBondingStationBlockEntity station) || !station.busy))
      super.clicked(slot, button, type, player);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    if (!stillValid(player)
        || index < 0
        || index >= slots.size()
        || inputs instanceof ColonyBondingStationBlockEntity station && station.busy)
      return ItemStack.EMPTY;
    Slot slot = slots.get(index);
    if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
    ItemStack item = slot.getItem(), before = item.copy();
    if (index < 2
        ? !moveItemStackTo(item, 2, slots.size(), true)
        : !moveItemStackTo(item, 0, 2, false)) return ItemStack.EMPTY;
    if (item.isEmpty()) slot.set(ItemStack.EMPTY);
    else slot.setChanged();
    slot.onTake(player, item);
    return before;
  }
}
