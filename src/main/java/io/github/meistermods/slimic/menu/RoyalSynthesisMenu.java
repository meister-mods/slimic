package io.github.meistermods.slimic.menu;

import io.github.meistermods.slimic.block.RoyalSynthesisStationBlockEntity;
import io.github.meistermods.slimic.registry.*;
import io.github.meistermods.slimic.world.*;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

/** Preview has no side effects. Only the explicit server confirmation can consume inputs. */
public final class RoyalSynthesisMenu extends AbstractContainerMenu {
  public static final int CONFIRM = 0, RECOVER = 1;
  private final Container inputs;
  private final Inventory inventory;
  private final BlockPos position;
  private final NumericContainerData data;
  private RoyalSynthesis.Check check =
      new RoyalSynthesis.Check(RoyalSynthesis.Problem.MISSING_PARENTS, 0, 0, "");

  public RoyalSynthesisMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
    this(id, inventory, new SimpleContainer(4), buffer.readBlockPos());
  }

  public RoyalSynthesisMenu(int id, Inventory inventory, Container inputs, BlockPos position) {
    super(ModMenus.ROYAL_SYNTHESIS.get(), id);
    this.inputs = inputs;
    this.inventory = inventory;
    this.position = position;
    data =
        new NumericContainerData(
            4,
            inventory.player.level().isClientSide,
            index ->
                index == 0
                    ? check.problem().ordinal()
                    : index == 3
                            && inventory.player instanceof ServerPlayer player
                            && RoyalSynthesisData.get(player.serverLevel())
                                    .pendingFor(player.getUUID())
                                != null
                        ? 1
                        : 0,
            Map.of(1, () -> check.normalPoints(), 2, () -> check.ancientPoints()));
    addDataSlots(data);
    for (int index = 0; index < 4; index++) {
      final int input = index;
      addSlot(
          new Slot(inputs, index, 22 + index * 48, 48) {
            @Override
            public boolean mayPlace(ItemStack stack) {
              return switch (input) {
                case 0 ->
                    io.github.meistermods.slimic.entity.SynthesisFamily.acceptsParent(stack, false);
                case 1 ->
                    io.github.meistermods.slimic.entity.SynthesisFamily.acceptsParent(stack, true);
                case 2 -> stack.is(Items.SLIME_BALL);
                default -> stack.is(ModItems.OLD_COMMAND_FRAGMENT.get());
              };
            }

            @Override
            public boolean mayPickup(Player player) {
              return stillValid(player)
                  && (!(inputs instanceof RoyalSynthesisStationBlockEntity station)
                      || !station.busy);
            }
          });
    }
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(inventory, 9 + row * 9 + col, 79 + col * 18, 153 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 79 + col * 18, 211));
    broadcastChanges();
  }

  public RoyalSynthesis.Problem problem() {
    return RoyalSynthesis.Problem.values()[
        Math.max(0, Math.min(RoyalSynthesis.Problem.values().length - 1, (int) data.value(0)))];
  }

  public boolean ready() {
    return data.isReady() && problem() == RoyalSynthesis.Problem.READY;
  }

  public boolean pending() {
    return data.isReady() && data.value(3) != 0;
  }

  public long normalPoints() {
    return data.longValue(1);
  }

  public long ancientPoints() {
    return data.longValue(2);
  }

  public long totalPoints() {
    try {
      return Math.addExact(normalPoints(), ancientPoints());
    } catch (ArithmeticException overflow) {
      return 0;
    }
  }

  public Component status() {
    return description(problem());
  }

  private Component description(RoyalSynthesis.Problem problem) {
    if (problem == RoyalSynthesis.Problem.EQUIPMENT || problem == RoyalSynthesis.Problem.CARGO)
      for (int parent = 0; parent < 2; parent++) {
        var item = inputs.getItem(parent);
        if (!item.hasTag()) continue;
        var saved = item.getTag().getCompound("SlimeData");
        var name =
            Component.translatable(
                "screen.slimic.synthesis." + (parent == 0 ? "normal" : "ancient"));
        var initial =
            ItemStack.of(
                item.getTag()
                    .getCompound(
                        io.github.meistermods.slimic.item.MiningSlimeItem.INITIAL_PICKAXE));
        if (problem == RoyalSynthesis.Problem.EQUIPMENT && !initial.isEmpty())
          return Component.translatable(
              "screen.slimic.synthesis.occupied",
              name,
              Component.translatable("equipment.slimic.mainhand"),
              initial.getHoverName());
        if (problem == RoyalSynthesis.Problem.EQUIPMENT)
          for (String key : java.util.List.of("HandItems", "ArmorItems")) {
            var list = saved.getList(key, net.minecraft.nbt.Tag.TAG_COMPOUND);
            String[] locations =
                key.equals("HandItems")
                    ? new String[] {"mainhand", "offhand"}
                    : new String[] {"feet", "legs", "chest", "head"};
            for (int slot = 0; slot < list.size(); slot++) {
              var content = ItemStack.of(list.getCompound(slot));
              if (!content.isEmpty())
                return Component.translatable(
                    "screen.slimic.synthesis.occupied",
                    name,
                    Component.translatable(
                        "equipment.slimic." + locations[Math.min(slot, locations.length - 1)]),
                    content.getHoverName());
            }
          }
        if (problem == RoyalSynthesis.Problem.CARGO)
          for (String key :
              java.util.List.of(
                  "MiningBag",
                  "TransportBag",
                  "FieldBag",
                  "CombatBag",
                  "HealingBag",
                  "CleaningBag"))
            for (var raw :
                saved.getCompound(key).getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
              var data = (net.minecraft.nbt.CompoundTag) raw;
              var content = ItemStack.of(data);
              if (!content.isEmpty())
                return Component.translatable(
                    "screen.slimic.synthesis.occupied",
                    name,
                    Component.translatable(
                        "screen.slimic.synthesis.bag_slot", data.getInt("Slot") + 1L),
                    content.getHoverName());
            }
      }
    return message(new RoyalSynthesis.Check(problem, normalPoints(), ancientPoints(), ""));
  }

  public static Component message(RoyalSynthesis.Check check) {
    return Component.translatable(
        "synthesis.slimic." + check.problem().name().toLowerCase(Locale.ROOT), check.detail());
  }

  @Override
  public void broadcastChanges() {
    if (inventory.player instanceof ServerPlayer player
        && inputs instanceof RoyalSynthesisStationBlockEntity station)
      check = RoyalSynthesis.check(player.serverLevel(), player, station.contents());
    super.broadcastChanges();
  }

  @Override
  public boolean stillValid(Player player) {
    return !player.isSpectator()
        && player.isAlive()
        && player.distanceToSqr(position.getX() + .5, position.getY() + .5, position.getZ() + .5)
            <= 64
        && (player.level().isClientSide
            || inputs instanceof RoyalSynthesisStationBlockEntity station
                && station.canUse(player));
  }

  @Override
  public boolean clickMenuButton(Player player, int button) {
    if (!(player instanceof ServerPlayer server) || !stillValid(player)) return false;
    if (button == CONFIRM && inputs instanceof RoyalSynthesisStationBlockEntity station) {
      var result = RoyalSynthesis.execute(server, station);
      server.displayClientMessage(description(result.problem()), false);
      broadcastChanges();
      return true;
    }
    if (button == RECOVER) {
      var ledger = RoyalSynthesisData.get(server.serverLevel());
      var transaction = ledger.pendingFor(server.getUUID());
      if (transaction != null) ledger.deliver(server, transaction);
      broadcastChanges();
      return true;
    }
    return false;
  }

  @Override
  public void clicked(int slot, int button, ClickType type, Player player) {
    if (!stillValid(player)
        || inputs instanceof RoyalSynthesisStationBlockEntity station && station.busy) return;
    super.clicked(slot, button, type, player);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    if (!stillValid(player)
        || index < 0
        || index >= slots.size()
        || inputs instanceof RoyalSynthesisStationBlockEntity station && station.busy)
      return ItemStack.EMPTY;
    Slot slot = slots.get(index);
    if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
    ItemStack stack = slot.getItem(), copy = stack.copy();
    if (index < 4) {
      if (!moveItemStackTo(stack, 4, slots.size(), true)) return ItemStack.EMPTY;
    } else {
      boolean moved = false;
      for (int i = 0; i < 4 && !moved; i++)
        if (slots.get(i).mayPlace(stack)) moved = moveItemStackTo(stack, i, i + 1, false);
      if (!moved) return ItemStack.EMPTY;
    }
    if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
    else slot.setChanged();
    slot.onTake(player, stack);
    return copy;
  }
}
