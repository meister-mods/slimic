package io.github.meistermods.slimic.menu;

import io.github.meistermods.slimic.entity.BagSlime;
import io.github.meistermods.slimic.entity.CleaningSlime;
import io.github.meistermods.slimic.entity.CombatSlime;
import io.github.meistermods.slimic.entity.CombatTarget;
import io.github.meistermods.slimic.entity.FieldSlime;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.GuardianAngelSlime;
import io.github.meistermods.slimic.entity.HealingSlime;
import io.github.meistermods.slimic.entity.HeraldSlime;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.SaddleSlime;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.entity.SoggySlime;
import io.github.meistermods.slimic.entity.TransportSlime;
import io.github.meistermods.slimic.item.SlimeTunerItem;
import io.github.meistermods.slimic.registry.ModMenus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.SlotItemHandler;

public final class SlimeMenu extends AbstractContainerMenu {
  public static final int TARGET_TOGGLE = 40, TARGET_PAGE_BUTTON = 46;
  public static final int FILTERS = 18, TARGET_PAGE = 19;
  public static final int FIRE_SIGNAL = 28, ROAM = 29;
  public static final int FOLLOW = 20, WAIT = 21, WORK = 22;
  public static final int PREVIOUS_PAGE = 23, NEXT_PAGE = 24;
  public static final int DELAY_DOWN = 25, DELAY_UP = 26, RIDE = 27;
  public static final int POINTS = 9,
      ACTIVITY = 10,
      NEXT_POINT = 11,
      STAMINA = 12,
      HEALTH = 13,
      MODE = 14,
      STATUS = 15,
      BAG_PAGE = 16,
      ALERT_MINUTES = 17;
  private final FriendlySlime slime;
  private final ContainerData data;
  private final IntContainerData wireData;
  private final int petSlots;
  private int bagPage;
  private int targetPage;

  public SlimeMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
    this(id, inventory, resolve(inventory, buffer));
  }

  public static void writeOpeningData(FriendlyByteBuf buffer, FriendlySlime slime) {
    buffer.writeVarInt(slime.getId());
    buffer.writeVarInt(BuiltInRegistries.ENTITY_TYPE.getId(slime.getType()));
  }

  private static FriendlySlime resolve(Inventory inventory, FriendlyByteBuf buffer) {
    int entityId = buffer.readVarInt();
    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.byId(buffer.readVarInt());
    if (inventory.player.level().getEntity(entityId) instanceof FriendlySlime slime
        && slime.getType() == type) return slime;
    // Keep the server's slot layout until the client closes a menu whose entity disappeared.
    if (type != null && type.create(inventory.player.level()) instanceof FriendlySlime missing) {
      missing.discard();
      return missing;
    }
    throw new IllegalArgumentException("Invalid slime menu type");
  }

  public SlimeMenu(int id, Inventory inventory, FriendlySlime slime) {
    super(ModMenus.SLIME.get(), id);
    this.slime = slime;
    this.data =
        inventory.player.level().isClientSide
            ? new SimpleContainerData(20)
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
                  case MODE ->
                      slime.isOrderedToSit()
                          ? WAIT
                          : slime instanceof SoggySlime soggy && soggy.isRoaming()
                              ? ROAM
                              : slime.isWorking() ? WORK : FOLLOW;
                  case STATUS ->
                      slime instanceof HealingSlime healer
                          ? healer.getHealingStatus().ordinal()
                          : slime instanceof FieldSlime field
                              ? field.getFieldStatus().ordinal()
                              : slime instanceof MiningSlime miner
                                  ? miner.getMiningStatus().ordinal()
                                  : slime instanceof TransportSlime carrier
                                      ? carrier.getTransportStatus().ordinal()
                                      : slime instanceof CombatSlime fighter
                                          ? fighter.getCombatStatus().ordinal()
                                          : slime instanceof CleaningSlime cleaner
                                              ? cleaner.getCleaningStatus().ordinal()
                                              : 0;
                  case FILTERS -> {
                    int mask = 0;
                    if (slime instanceof CombatSlime fighter)
                      for (CombatTarget kind : CombatTarget.values())
                        if (fighter.allows(kind)) mask |= 1 << kind.ordinal();
                    yield mask;
                  }
                  case TARGET_PAGE -> targetPage;
                  case BAG_PAGE -> bagPage;
                  case ALERT_MINUTES ->
                      slime instanceof HeraldSlime herald ? herald.getDelayMinutes() : 0;
                  default -> 0;
                };
              }

              @Override
              public void set(int index, int value) {}

              @Override
              public int getCount() {
                return 20;
              }
            };
    wireData = new IntContainerData(data, inventory.player.level().isClientSide);
    addDataSlots(wireData);
    int toolSlots = equipmentSlots(slime);
    for (int tool = 0; tool < toolSlots; tool++) {
      final int equipment = tool;
      addSlot(
          new Slot(new ToolContainer(slime), tool, 194 + tool * 18, 49) {
            @Override
            public boolean mayPlace(ItemStack stack) {
              return matchesEquipment(slime, equipment, stack);
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
              return slime instanceof GuardianAngelSlime ? 64 : 1;
            }

            @Override
            public int getMaxStackSize() {
              return slime instanceof GuardianAngelSlime ? 64 : 1;
            }
          });
    }
    if (slime instanceof BagSlime carrier) {
      for (int index = 0; index < carrier.getBag().getSlots(); index++) {
        final int slot = index;
        addSlot(
            new SlotItemHandler(
                carrier.getBag(), index, 194 + index % 9 * 18, 89 + index % 27 / 9 * 18) {
              @Override
              public boolean isActive() {
                return !(slime instanceof CombatSlime && data(TARGET_PAGE) == 1)
                    && slot < (int) value(SlimeStat.CAPACITY)
                    && slot / 27 == data(BAG_PAGE);
              }

              @Override
              public boolean mayPlace(ItemStack stack) {
                return isActive() && super.mayPlace(stack);
              }

              @Override
              public boolean mayPickup(Player player) {
                return isActive() && stillValid(player) && super.mayPickup(player);
              }

              @Override
              public void setChanged() {
                // Vanilla transfers can mutate an existing stack without calling Slot.set.
                carrier.getBag().setStackInSlot(slot, getItem());
              }
            });
      }
      petSlots = toolSlots + carrier.getBag().getSlots();
    } else petSlots = toolSlots;
    for (int row = 0; row < 3; row++) {
      for (int column = 0; column < 9; column++)
        addSlot(new Slot(inventory, 9 + row * 9 + column, 194 + column * 18, 157 + row * 18));
    }
    for (int column = 0; column < 9; column++)
      addSlot(new Slot(inventory, column, 194 + column * 18, 215));
  }

  private static int equipmentSlots(FriendlySlime slime) {
    if (slime instanceof GuardianAngelSlime) return 1;
    return slime instanceof MiningSlime
        ? 2
        : slime instanceof CombatSlime || slime instanceof SaddleSlime
            ? 3
            : slime instanceof FieldSlime ? 1 : 0;
  }

  private static boolean matchesEquipment(FriendlySlime slime, int slot, ItemStack stack) {
    if (slime instanceof GuardianAngelSlime) return slot == 0 && stack.is(Items.TOTEM_OF_UNDYING);
    return slime instanceof FieldSlime field
        ? slot == 0 && field.matchesTool(stack)
        : slime instanceof MiningSlime
            ? MiningSlime.matchesToolSlot(slot, stack)
            : slime instanceof SaddleSlime
                ? SaddleSlime.matchesEquipment(slot, stack)
                : slime instanceof CombatSlime && CombatSlime.matchesEquipment(slot, stack);
  }

  public FriendlySlime slime() {
    return slime;
  }

  public boolean isSynchronized() {
    return wireData.isReady();
  }

  public int data(int index) {
    return data.get(index);
  }

  public double value(SlimeStat stat) {
    return data.get(stat.ordinal()) / (double) stat.syncScale();
  }

  public int bagPages() {
    return Math.max(1, ((int) value(SlimeStat.CAPACITY) + 26) / 27);
  }

  @Override
  public void broadcastChanges() {
    if (!slime.level().isClientSide)
      bagPage = Math.min(bagPage, Math.max(0, (slime.getCapacity() - 1) / 27));
    super.broadcastChanges();
  }

  @Override
  public boolean stillValid(Player player) {
    return !player.isSpectator()
        && SlimeTunerItem.isHolding(player)
        && slime.isAlive()
        && !slime.isRemoved()
        && player.level() == slime.level()
        && player.getUUID().equals(slime.getOwnerUUID())
        && player.distanceToSqr(slime) <= 64.0;
  }

  @Override
  public boolean clickMenuButton(Player player, int button) {
    if (!stillValid(player) || player.level().isClientSide) return false;
    if (button == ROAM && slime instanceof SoggySlime soggy) {
      soggy.setRoaming(true);
      return true;
    }
    if (button == FIRE_SIGNAL && slime instanceof HeraldSlime herald)
      return herald.launchManually();
    if (button == RIDE && slime instanceof SaddleSlime mount) {
      if (!mount.mount(player)) return false;
      player.closeContainer();
      return true;
    }
    if (slime instanceof CombatSlime fighter) {
      if (button == TARGET_PAGE_BUTTON) {
        targetPage = 1 - targetPage;
        return true;
      }
      if (button >= TARGET_TOGGLE && button < TARGET_TOGGLE + CombatTarget.values().length) {
        CombatTarget kind = CombatTarget.values()[button - TARGET_TOGGLE];
        fighter.setAllowed(kind, !fighter.allows(kind));
        return true;
      }
    }
    if ((button == DELAY_DOWN || button == DELAY_UP) && slime instanceof HeraldSlime herald) {
      int next = herald.getDelayMinutes() + (button == DELAY_UP ? 1 : -1);
      if (next < 1 || next > 60) return false;
      herald.setDelayMinutes(next);
      return true;
    }
    if (button == PREVIOUS_PAGE || button == NEXT_PAGE) {
      int next = bagPage + (button == NEXT_PAGE ? 1 : -1);
      if (next < 0 || next >= bagPages()) return false;
      bagPage = next;
      return true;
    }
    if (button >= 0 && button < SlimeStat.values().length)
      return slime.upgradeStat(SlimeStat.values()[button]);
    if (button != FOLLOW && button != WAIT && button != WORK) return false;
    if (button == WORK && !(slime instanceof BagSlime) && !(slime instanceof HeraldSlime))
      return false;
    if (slime instanceof SoggySlime soggy) soggy.setRoaming(false);
    if (slime instanceof BagSlime worker) worker.setWorking(button == WORK);
    if (slime instanceof HeraldSlime herald) herald.setMonitoring(button == WORK);
    slime.setWaiting(button == WAIT);
    return true;
  }

  public boolean rename(Player player, String name) {
    return stillValid(player) && slime.rename(player, name);
  }

  @Override
  public void clicked(int slot, int button, ClickType type, Player player) {
    if (!stillValid(player)) return;
    if (slot == 0
        && slime instanceof GuardianAngelSlime guardian
        && type == ClickType.SWAP
        && guardian.getTotems().getCount() > 1) return;
    super.clicked(slot, button, type, player);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
    Slot slot = slots.get(index);
    if (!slot.isActive() || !slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
    ItemStack original = slot.getItem();
    ItemStack copy = original.copy();
    if (index >= petSlots
        && slime instanceof GuardianAngelSlime guardian
        && original.is(Items.TOTEM_OF_UNDYING)) {
      int moved = guardian.addTotems(original);
      if (moved == 0) return ItemStack.EMPTY;
      original.shrink(moved);
      slot.setChanged();
      slot.onTake(player, original);
      return copy;
    }
    if (index < petSlots) {
      if (slime instanceof GuardianAngelSlime) {
        for (int playerSlot = slots.size() - 1;
            playerSlot >= petSlots && !original.isEmpty();
            playerSlot--) {
          var destination = slots.get(playerSlot);
          if (!destination.hasItem() && destination.mayPlace(original))
            destination.setByPlayer(original.split(1));
        }
        if (original.getCount() == copy.getCount()) return ItemStack.EMPTY;
      } else if (!moveItemStackTo(original, petSlots, slots.size(), true)) return ItemStack.EMPTY;
    } else if (petSlots > 0) {
      boolean equipped = false;
      int equipmentSlots = equipmentSlots(slime);
      for (int equipment = 0; equipment < equipmentSlots; equipment++) {
        boolean matches = matchesEquipment(slime, equipment, original);
        if (matches && moveItemStackTo(original, equipment, equipment + 1, false)) {
          equipped = true;
          break;
        }
      }
      if (!equipped) {
        if (!(slime instanceof BagSlime)) return ItemStack.EMPTY;
        if (slime instanceof CombatSlime && data(TARGET_PAGE) == 1) return ItemStack.EMPTY;
        int start = equipmentSlots + data(BAG_PAGE) * 27;
        int end =
            Math.min(
                petSlots,
                start + Math.min(27, (int) value(SlimeStat.CAPACITY) - data(BAG_PAGE) * 27));
        if (!moveItemStackTo(original, start, end, false)) return ItemStack.EMPTY;
      }
    } else return ItemStack.EMPTY;
    if (original.isEmpty()) slot.set(ItemStack.EMPTY);
    else slot.setChanged();
    slot.onTake(player, original);
    return copy;
  }

  private static final class ToolContainer implements Container {
    private final FriendlySlime slime;

    ToolContainer(FriendlySlime slime) {
      this.slime = slime;
    }

    @Override
    public int getContainerSize() {
      return equipmentSlots(slime);
    }

    @Override
    public boolean isEmpty() {
      for (int slot = 0; slot < getContainerSize(); slot++)
        if (!getItem(slot).isEmpty()) return false;
      return true;
    }

    @Override
    public ItemStack getItem(int slot) {
      if (slime instanceof GuardianAngelSlime guardian) return guardian.getTotems();
      return slime instanceof FieldSlime field
          ? field.getTool()
          : slime instanceof MiningSlime miner
              ? miner.getTool(slot)
              : slime instanceof SaddleSlime mount
                  ? mount.getEquipment(slot)
                  : ((CombatSlime) slime).getEquipment(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
      ItemStack result =
          getItem(slot).split(slime instanceof GuardianAngelSlime ? Math.min(1, count) : count);
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
      if (slime instanceof GuardianAngelSlime guardian) guardian.setTotems(stack);
      else if (slime instanceof FieldSlime field) field.setTool(stack);
      else if (slime instanceof MiningSlime miner) miner.setTool(slot, stack);
      else if (slime instanceof SaddleSlime mount) mount.setEquipment(slot, stack);
      else ((CombatSlime) slime).setEquipment(slot, stack);
    }

    @Override
    public void setChanged() {
      if (slime instanceof GuardianAngelSlime guardian) guardian.setTotems(guardian.getTotems());
    }

    @Override
    public boolean stillValid(Player player) {
      return SlimeTunerItem.isHolding(player)
          && slime.isAlive()
          && player.getUUID().equals(slime.getOwnerUUID());
    }

    @Override
    public void clearContent() {
      for (int slot = 0; slot < getContainerSize(); slot++) setItem(slot, ItemStack.EMPTY);
    }
  }
}
