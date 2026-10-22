package io.github.meistermods.slimic.menu;

import io.github.meistermods.slimic.entity.BagSlime;
import io.github.meistermods.slimic.entity.CleaningSlime;
import io.github.meistermods.slimic.entity.CombatSlime;
import io.github.meistermods.slimic.entity.CombatTarget;
import io.github.meistermods.slimic.entity.FieldSlime;
import io.github.meistermods.slimic.entity.FishingSlime;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.GuardianAngelSlime;
import io.github.meistermods.slimic.entity.HealingSlime;
import io.github.meistermods.slimic.entity.MeowSlime;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.SaddleSlime;
import io.github.meistermods.slimic.entity.SignalingSlime;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.entity.SoggySlime;
import io.github.meistermods.slimic.entity.TransportSlime;
import io.github.meistermods.slimic.entity.TunnelSlime;
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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.SlotItemHandler;

public final class SlimeMenu extends AbstractContainerMenu {
  public static final int POLICY = 20, ORE_MASK = 21, POLICY_BUTTON = 70, ORE_BUTTON = 80;
  public static final int SLOT_SPACING = 18, SLOT_COLUMNS = 9, BAG_ROWS = 3;
  public static final int BAG_PAGE_SIZE = SLOT_COLUMNS * BAG_ROWS;
  public static final int SLOTS_X = 196, EQUIPMENT_Y = 49, BAG_Y = 90;
  public static final int INVENTORY_Y = 158, HOTBAR_Y = 216;
  public static final int BAG_LABEL_Y = 78, INVENTORY_LABEL_Y = 147;
  public static final int ROSTER = 52;
  public static final int TARGET_TOGGLE = 40, TARGET_PAGE_BUTTON = 46;
  public static final int FILTERS = 18, TARGET_PAGE = 19;
  public static final int FIRE_SIGNAL = 28, ROAM = 29, DIRECTION = 50, ACTIVE = 51;
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
  private final NumericContainerData wireData;
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
    wireData =
        new NumericContainerData(
            22,
            inventory.player.level().isClientSide,
            inventory.player.level().isClientSide
                ? null
                : index -> {
                  if (index < SlimeStat.values().length) {
                    SlimeStat stat = SlimeStat.values()[index];
                    return stat.value(slime);
                  }
                  return switch (index) {
                    case POLICY ->
                        slime
                                instanceof
                                io.github.meistermods.slimic.entity.NextGenerationMiningSlime next
                            ? next.policy().ordinal()
                            : 0;
                    case ORE_MASK ->
                        slime
                                instanceof
                                io.github.meistermods.slimic.entity.NextGenerationMiningSlime next
                            ? next.oreMask()
                            : 0;
                    case POINTS -> slime.getStatPoints();
                    case ACTIVITY -> slime.getActivityProgress();
                    case NEXT_POINT -> slime.getNextPointCost();
                    case STAMINA -> slime.getStamina();
                    case HEALTH -> slime.getHealth();
                    case MODE ->
                        slime.isOrderedToSit()
                            ? WAIT
                            : (slime instanceof SoggySlime soggy && soggy.isRoaming())
                                    || (slime instanceof MeowSlime cat && cat.isRoaming())
                                    || (slime
                                            instanceof
                                            io.github.meistermods.slimic.entity.AttentionSlime
                                                attention
                                        && attention.isRoaming())
                                ? ROAM
                                : slime.isWorking() ? WORK : FOLLOW;
                    case STATUS ->
                        slime instanceof FishingSlime fisher
                            ? fisher.getFishingStatus().ordinal()
                            : slime instanceof TunnelSlime tunnel
                                ? tunnel.getTunnelStatus().ordinal()
                                : slime instanceof HealingSlime healer
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
                        slime instanceof SignalingSlime signaling ? signaling.getDelayMinutes() : 0;
                    default -> 0;
                  };
                },
            POINTS,
            slime::getStatPoints);
    addDataSlots(wireData);
    int toolSlots = equipmentSlots(slime);
    for (int tool = 0; tool < toolSlots; tool++) {
      final int equipment = tool;
      addSlot(
          new Slot(new ToolContainer(slime), tool, SLOTS_X + tool * SLOT_SPACING, EQUIPMENT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
              return matchesEquipment(slime, equipment, stack);
            }

            @Override
            public boolean mayPickup(Player player) {
              return !(slime instanceof TunnelSlime && equipment == 2) && super.mayPickup(player);
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
      var view =
          new BagView(
              carrier.getBag(), () -> data(BAG_PAGE), inventory.player.level().isClientSide);
      for (int index = 0; index < view.getSlots(); index++) {
        final int slot = index;
        addSlot(
            new SlotItemHandler(
                view,
                index,
                SLOTS_X + index % SLOT_COLUMNS * SLOT_SPACING,
                BAG_Y + index % BAG_PAGE_SIZE / SLOT_COLUMNS * SLOT_SPACING) {
              @Override
              public boolean isActive() {
                return isBagVisible()
                    && view.logicalSlot(slot) < getSynchronizedCapacity()
                    && view.logicalSlot(slot) / BAG_PAGE_SIZE == data(BAG_PAGE);
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
                view.setStackInSlot(slot, getItem());
              }
            });
      }
      petSlots = toolSlots + view.getSlots();
    } else petSlots = toolSlots;
    for (int row = 0; row < 3; row++) {
      for (int column = 0; column < SLOT_COLUMNS; column++)
        addSlot(
            new Slot(
                inventory,
                SLOT_COLUMNS + row * SLOT_COLUMNS + column,
                SLOTS_X + column * SLOT_SPACING,
                INVENTORY_Y + row * SLOT_SPACING));
    }
    for (int column = 0; column < SLOT_COLUMNS; column++)
      addSlot(new Slot(inventory, column, SLOTS_X + column * SLOT_SPACING, HOTBAR_Y));
  }

  private static int equipmentSlots(FriendlySlime slime) {
    if (slime instanceof TunnelSlime) return 3;
    if (slime instanceof FishingSlime || slime instanceof GuardianAngelSlime) return 1;
    return slime instanceof MiningSlime
        ? 2
        : slime instanceof CombatSlime || slime instanceof SaddleSlime
            ? 3
            : slime instanceof FieldSlime ? 1 : 0;
  }

  private static boolean matchesEquipment(FriendlySlime slime, int slot, ItemStack stack) {
    if (slime instanceof TunnelSlime && slot == 2) return false;
    if (slime instanceof FishingSlime) return slot == 0 && FishingSlime.isRod(stack);
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

  public long points() {
    return wireData.longValue(POINTS);
  }

  public int data(int index) {
    double value = wireData.value(index);
    if (index < SlimeStat.values().length) value *= SlimeStat.values()[index].syncScale();
    else if (index == HEALTH || index == STAMINA) value *= 10;
    return io.github.meistermods.slimic.entity.CreativeStats.asInt(Math.round(value));
  }

  public double value(SlimeStat stat) {
    return wireData.value(stat.ordinal());
  }

  public float health() {
    return (float) wireData.value(HEALTH);
  }

  public float stamina() {
    return (float) wireData.value(STAMINA);
  }

  public boolean isBagVisible() {
    return slime instanceof BagSlime
        && !isPolicyEditor()
        && !(slime instanceof CombatSlime && data(TARGET_PAGE) == 1);
  }

  public boolean isPolicyEditor() {
    return slime instanceof io.github.meistermods.slimic.entity.NextGenerationMiningSlime
        && data(TARGET_PAGE) == 1;
  }

  private int getSynchronizedCapacity() {
    return (int) value(SlimeStat.CAPACITY);
  }

  public int bagPages() {
    return Math.max(1, (getSynchronizedCapacity() - 1) / BAG_PAGE_SIZE + 1);
  }

  @Override
  public void broadcastChanges() {
    if (!slime.level().isClientSide)
      bagPage = Math.min(bagPage, Math.max(0, (slime.getCapacity() - 1) / BAG_PAGE_SIZE));
    super.broadcastChanges();
  }

  @Override
  public boolean stillValid(Player player) {
    return !player.isSpectator()
        && SlimeTunerItem.isHolding(player)
        && slime.isAlive()
        && !slime.isRemoved()
        && player.level() == slime.level()
        && slime.canManage(player)
        && player.distanceToSqr(slime) <= 64.0;
  }

  @Override
  public boolean clickMenuButton(Player player, int button) {
    if (!stillValid(player) || player.level().isClientSide) return false;
    if (slime instanceof io.github.meistermods.slimic.entity.NextGenerationMiningSlime next) {
      if (button == TARGET_PAGE_BUTTON) {
        targetPage = 1 - targetPage;
        return true;
      }
      if (button >= POLICY_BUTTON && button < POLICY_BUTTON + 3) {
        next.setPolicy(
            io.github.meistermods.slimic.entity.NextGenerationMiningSlime.Policy.values()[
                button - POLICY_BUTTON]);
        return true;
      }
      if (button >= ORE_BUTTON
          && button
              < ORE_BUTTON
                  + io.github.meistermods.slimic.entity.NextGenerationMiningSlime.ORES.size()) {
        next.toggleOre(button - ORE_BUTTON);
        return true;
      }
    }
    if (button == ROSTER
        && slime instanceof io.github.meistermods.slimic.entity.CommandSlime command)
      return command.open(player).consumesAction();
    if (button == ACTIVE && slime instanceof io.github.meistermods.slimic.entity.GlowSlime glow) {
      glow.setActive(!glow.isActive());
      return true;
    }
    if (button == ROAM
        && slime instanceof io.github.meistermods.slimic.entity.AttentionSlime attention) {
      attention.setRoaming(true);
      return true;
    }
    if (button == ACTIVE && slime instanceof MeowSlime cat) {
      cat.setActive(!cat.isActive());
      return true;
    }
    if (button == ROAM && slime instanceof MeowSlime cat) {
      cat.setRoaming(true);
      return true;
    }
    if (button == DIRECTION && slime instanceof TunnelSlime tunnel) {
      tunnel.setWorking(false);
      tunnel.setWaiting(true);
      tunnel.setTunnelDirection(tunnel.getTunnelDirection().getClockWise());
      return true;
    }
    if (button == ROAM && slime instanceof SoggySlime soggy) {
      soggy.setRoaming(true);
      return true;
    }
    if (button == FIRE_SIGNAL && slime instanceof SignalingSlime signaling)
      return signaling.launchManually();
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
    if ((button == DELAY_DOWN || button == DELAY_UP) && slime instanceof SignalingSlime signaling) {
      int next = signaling.getDelayMinutes() + (button == DELAY_UP ? 1 : -1);
      if (next < 1 || next > 60) return false;
      signaling.setDelayMinutes(next);
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
    if (button == FOLLOW) slime.setFollowingOwner(player);
    if (button == WORK && !(slime instanceof BagSlime) && !(slime instanceof SignalingSlime))
      return false;
    if (slime instanceof SoggySlime soggy) soggy.setRoaming(false);
    if (slime instanceof MeowSlime cat) cat.setRoaming(false);
    if (slime instanceof BagSlime worker) worker.setWorking(button == WORK);
    if (slime instanceof SignalingSlime signaling) signaling.setMonitoring(button == WORK);
    slime.setWaiting(button == WAIT);
    return true;
  }

  public boolean rename(Player player, String name) {
    return stillValid(player) && slime.rename(player, name);
  }

  @Override
  public void clicked(int slot, int button, ClickType type, Player player) {
    if (!stillValid(player)) return;
    if (slot == 2 && slime instanceof TunnelSlime tunnel) {
      if (type == ClickType.PICKUP && (getCarried().isEmpty() || tunnel.validLining(getCarried())))
        tunnel.setLiningTemplate(getCarried());
      return;
    }
    if (slot == 0
        && slime instanceof GuardianAngelSlime guardian
        && type == ClickType.SWAP
        && guardian.getTotems().getCount() > 1) return;
    super.clicked(slot, button, type, player);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
    if (slime instanceof TunnelSlime && index == 2) return ItemStack.EMPTY;
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
        if (!isBagVisible()) return ItemStack.EMPTY;
        int normalPages = (petSlots - equipmentSlots - 1) / BAG_PAGE_SIZE + 1;
        int start =
            equipmentSlots + (data(BAG_PAGE) >= normalPages ? 0 : data(BAG_PAGE) * BAG_PAGE_SIZE);
        int end =
            Math.min(
                petSlots,
                start
                    + Math.min(
                        BAG_PAGE_SIZE,
                        (int) value(SlimeStat.CAPACITY) - data(BAG_PAGE) * BAG_PAGE_SIZE));
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
      if (slime instanceof TunnelSlime tunnel && slot == 2)
        return tunnel.getLiningTemplate().copy();
      if (slime instanceof FishingSlime fisher) return fisher.getRod();
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
      if (slime instanceof TunnelSlime && slot == 2) return ItemStack.EMPTY;
      ItemStack result =
          getItem(slot).split(slime instanceof GuardianAngelSlime ? Math.min(1, count) : count);
      setChanged();
      return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
      if (slime instanceof TunnelSlime && slot == 2) return ItemStack.EMPTY;
      ItemStack result = getItem(slot);
      setItem(slot, ItemStack.EMPTY);
      return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
      if (slime instanceof TunnelSlime tunnel && slot == 2) {
        if (slime.level().isClientSide) tunnel.setLiningTemplate(stack);
      } else if (slime instanceof FishingSlime fisher) fisher.setRod(stack);
      else if (slime instanceof GuardianAngelSlime guardian) guardian.setTotems(stack);
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
      return SlimeTunerItem.isHolding(player) && slime.isAlive() && slime.canManage(player);
    }

    @Override
    public void clearContent() {
      for (int slot = 0; slot < getContainerSize(); slot++) setItem(slot, ItemStack.EMPTY);
    }
  }
}
