package io.github.meistermods.slimic.menu;

import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

public final class SlimeMenu extends AbstractContainerMenu {
  public static final int FOLLOW = 20, WAIT = 21, WORK = 22;
  public static final int POINTS = 9,
      ACTIVITY = 10,
      NEXT_POINT = 11,
      STAMINA = 12,
      HEALTH = 13,
      MODE = 14,
      STATUS = 15;
  private final FriendlySlime slime;
  private final ContainerData data;
  private final int petSlots;

  public SlimeMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
    this(id, inventory, resolve(inventory, buffer.readVarInt()));
  }

  private static FriendlySlime resolve(Inventory inventory, int entityId) {
    if (inventory.player.level().getEntity(entityId) instanceof FriendlySlime slime) return slime;
    throw new IllegalArgumentException("Missing slime for menu");
  }

  public SlimeMenu(int id, Inventory inventory, FriendlySlime slime) {
    super(ModMenus.SLIME.get(), id);
    this.slime = slime;
    this.data =
        inventory.player.level().isClientSide
            ? new SimpleContainerData(16)
            : new ContainerData() {
              @Override
              public int get(int index) {
                if (index < SlimeStat.values().length) {
                  SlimeStat stat = SlimeStat.values()[index];
                  return (int) Math.round(stat.value(slime) * stat.syncScale());
                }
                return switch (index) {
                  case POINTS -> slime.getStatPoints();
                  case ACTIVITY -> slime.getActivityProgress();
                  case NEXT_POINT -> slime.getNextPointCost();
                  case STAMINA -> (int) (slime.getStamina() * 10);
                  case HEALTH -> (int) (slime.getHealth() * 10);
                  case MODE -> slime.isOrderedToSit() ? WAIT : slime.isWorking() ? WORK : FOLLOW;
                  case STATUS ->
                      slime instanceof MiningSlime miner ? miner.getMiningStatus().ordinal() : 0;
                  default -> 0;
                };
              }

              @Override
              public void set(int index, int value) {}

              @Override
              public int getCount() {
                return 16;
              }
            };
    addDataSlots(data);
    if (slime instanceof MiningSlime miner) {
      addSlot(
          new Slot(new ToolContainer(miner), 0, 194, 49) {
            @Override
            public boolean mayPlace(ItemStack stack) {
              return MiningSlime.isPickaxe(stack);
            }

            @Override
            public int getMaxStackSize() {
              return 1;
            }
          });
      for (int index = 0; index < 27; index++) {
        final int slot = index;
        addSlot(
            new SlotItemHandler(miner.getBag(), index, 194 + index % 9 * 18, 89 + index / 9 * 18) {
              @Override
              public boolean isActive() {
                return slot < (int) value(SlimeStat.CAPACITY);
              }
            });
      }
      petSlots = 28;
    } else petSlots = 0;
    for (int row = 0; row < 3; row++) {
      for (int column = 0; column < 9; column++)
        addSlot(new Slot(inventory, 9 + row * 9 + column, 194 + column * 18, 157 + row * 18));
    }
    for (int column = 0; column < 9; column++)
      addSlot(new Slot(inventory, column, 194 + column * 18, 215));
  }

  public FriendlySlime slime() {
    return slime;
  }

  public int data(int index) {
    return data.get(index);
  }

  public double value(SlimeStat stat) {
    return data.get(stat.ordinal()) / (double) stat.syncScale();
  }

  @Override
  public boolean stillValid(Player player) {
    return slime.isAlive()
        && !slime.isRemoved()
        && player.level() == slime.level()
        && player.getUUID().equals(slime.getOwnerUUID())
        && player.distanceToSqr(slime) <= 64.0;
  }

  @Override
  public boolean clickMenuButton(Player player, int button) {
    if (!stillValid(player) || player.level().isClientSide) return false;
    if (button >= 0 && button < SlimeStat.values().length)
      return slime.upgradeStat(SlimeStat.values()[button]);
    if (button != FOLLOW && button != WAIT && button != WORK) return false;
    if (button == WORK && !(slime instanceof MiningSlime)) return false;
    if (slime instanceof MiningSlime miner) miner.setWorking(button == WORK);
    slime.setWaiting(button == WAIT);
    return true;
  }

  public boolean rename(Player player, String name) {
    return stillValid(player) && slime.rename(player, name);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
    Slot slot = slots.get(index);
    if (!slot.hasItem()) return ItemStack.EMPTY;
    ItemStack original = slot.getItem();
    ItemStack copy = original.copy();
    if (index < petSlots) {
      if (!moveItemStackTo(original, petSlots, slots.size(), true)) return ItemStack.EMPTY;
    } else if (petSlots > 0 && MiningSlime.isPickaxe(original)) {
      if (!moveItemStackTo(original, 0, 1, false)) return ItemStack.EMPTY;
    } else return ItemStack.EMPTY;
    if (original.isEmpty()) slot.set(ItemStack.EMPTY);
    else slot.setChanged();
    slot.onTake(player, original);
    return copy;
  }

  private static final class ToolContainer implements Container {
    private final MiningSlime slime;

    ToolContainer(MiningSlime slime) {
      this.slime = slime;
    }

    @Override
    public int getContainerSize() {
      return 1;
    }

    @Override
    public boolean isEmpty() {
      return slime.getPickaxe().isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
      return slime.getPickaxe();
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
      ItemStack result = getItem(slot).split(count);
      setChanged();
      return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
      ItemStack result = getItem(slot);
      setItem(slot, ItemStack.EMPTY);
      return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
      slime.setItemSlot(EquipmentSlot.MAINHAND, stack);
    }

    @Override
    public void setChanged() {}

    @Override
    public boolean stillValid(Player player) {
      return slime.isAlive() && player.getUUID().equals(slime.getOwnerUUID());
    }

    @Override
    public void clearContent() {
      setItem(0, ItemStack.EMPTY);
    }
  }
}
