package io.github.meistermods.slimic.menu;

import io.github.meistermods.slimic.entity.BagSlime;
import io.github.meistermods.slimic.entity.BoundColony;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.registry.ModItems;
import io.github.meistermods.slimic.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class CreativeSlimeTunerMenu extends AbstractContainerMenu {
  private final FriendlySlime slime;
  private final NumericContainerData wireData;
  private int result;

  public CreativeSlimeTunerMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
    this(id, inventory, resolve(inventory, buffer.readVarInt()));
  }

  private static FriendlySlime resolve(Inventory inventory, int id) {
    return inventory.player.level().getEntity(id) instanceof FriendlySlime slime ? slime : null;
  }

  public CreativeSlimeTunerMenu(int id, Inventory inventory, FriendlySlime slime) {
    super(ModMenus.CREATIVE_TUNER.get(), id);
    this.slime = slime;
    wireData =
        new NumericContainerData(
            13,
            inventory.player.level().isClientSide,
            inventory.player.level().isClientSide
                ? null
                : index -> {
                  if (slime == null) return 0;
                  if (index < 9) {
                    SlimeStat stat = SlimeStat.values()[index];
                    return stat.tunerValue(slime);
                  }
                  return switch (index) {
                    case 9 -> slime.getStatPoints();
                    case 10 -> slime.getHealth();
                    case 11 -> slime.getStamina();
                    case 12 -> result;
                    default -> 0;
                  };
                },
            9,
            () -> slime == null ? 0 : slime.getStatPoints());
    addDataSlots(wireData);
  }

  public FriendlySlime slime() {
    return slime;
  }

  public boolean isSynchronized() {
    return wireData.isReady();
  }

  public double value(SlimeStat stat) {
    return wireData.value(stat.ordinal());
  }

  public long points() {
    return wireData.longValue(9);
  }

  public float health() {
    return (float) wireData.value(10);
  }

  public float stamina() {
    return (float) wireData.value(11);
  }

  public int result() {
    return (int) wireData.value(12);
  }

  @Override
  public boolean stillValid(Player player) {
    return player.getAbilities().instabuild
        && !player.isSpectator()
        && (player.getMainHandItem().is(ModItems.CREATIVE_SLIME_TUNER.get())
            || player.getOffhandItem().is(ModItems.CREATIVE_SLIME_TUNER.get()))
        && (slime == null
            || (slime.isAlive()
                && !slime.isRemoved()
                && player.level() == slime.level()
                && slime.isWithinManagementRange(player, 256)));
  }

  public boolean apply(Player player, double[] values, float health, float stamina, long points) {
    if (player.level().isClientSide
        || !stillValid(player)
        || slime == null
        || values.length != 9
        || !Float.isFinite(health)
        || !Float.isFinite(stamina)
        || health < 0
        || stamina < 0
        || points < 0) return false;
    boolean warning =
        points > 32767
            || health > SlimeStat.HEALTH.maxTunedValue()
            || stamina > SlimeStat.STAMINA.maxTunedValue();
    boolean[] changed = new boolean[SlimeStat.values().length];
    for (SlimeStat stat : SlimeStat.values()) {
      if (!stat.acceptsTunedValue(values[stat.ordinal()])) return false;
      warning |= !stat.isRecommendedValue(values[stat.ordinal()]);
      changed[stat.ordinal()] = Double.compare(stat.tunerValue(slime), values[stat.ordinal()]) != 0;
    }
    var colony = BoundColony.ready(slime);
    if (changed[SlimeStat.CAPACITY.ordinal()]
        && colony != null
        && !colony.canTuneCapacity(values[SlimeStat.CAPACITY.ordinal()])) {
      result = 4;
      return false;
    }
    if (changed[SlimeStat.CAPACITY.ordinal()] && slime instanceof BagSlime bag) {
      int capacity = (int) (values[SlimeStat.CAPACITY.ordinal()] * slime.capacityMultiplier());
      for (int slot : bag.getBag().occupiedSlots())
        if (slot >= capacity && !bag.getBag().getStackInSlot(slot).isEmpty()) {
          result = 3;
          return false;
        }
    }
    for (SlimeStat stat : SlimeStat.values())
      if (changed[stat.ordinal()]) stat.tune(slime, values[stat.ordinal()]);
    slime.setTunedVitals(health, stamina, points);
    result = warning ? 2 : 1;
    return true;
  }

  @Override
  public ItemStack quickMoveStack(Player player, int slot) {
    return ItemStack.EMPTY;
  }
}
