package io.github.meistermods.slimic.menu;

import io.github.meistermods.slimic.entity.BagSlime;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.GuardianAngelSlime;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.registry.ModItems;
import io.github.meistermods.slimic.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public final class CreativeSlimeTunerMenu extends AbstractContainerMenu {
  private final FriendlySlime slime;
  private final ContainerData data;
  private final IntContainerData wireData;
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
    data =
        inventory.player.level().isClientSide
            ? new SimpleContainerData(13)
            : new ContainerData() {
              @Override
              public int get(int index) {
                if (slime == null) return 0;
                if (index < 9) {
                  SlimeStat stat = SlimeStat.values()[index];
                  return (int) Math.round(stat.tunerValue(slime) * stat.syncScale());
                }
                return switch (index) {
                  case 9 -> slime.getStatPoints();
                  case 10 -> (int) (slime.getHealth() * 10);
                  case 11 -> (int) (slime.getStamina() * 10);
                  case 12 -> result;
                  default -> 0;
                };
              }

              @Override
              public void set(int index, int value) {}

              @Override
              public int getCount() {
                return 13;
              }
            };
    wireData = new IntContainerData(data, inventory.player.level().isClientSide);
    addDataSlots(wireData);
  }

  public FriendlySlime slime() {
    return slime;
  }

  public boolean isSynchronized() {
    return wireData.isReady();
  }

  public double value(SlimeStat stat) {
    return data.get(stat.ordinal()) / (double) stat.syncScale();
  }

  public int points() {
    return data.get(9);
  }

  public float health() {
    return data.get(10) / 10.0F;
  }

  public float stamina() {
    return data.get(11) / 10.0F;
  }

  public int result() {
    return data.get(12);
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
                && player.distanceToSqr(slime) <= 256));
  }

  public boolean apply(Player player, double[] values, float health, float stamina, int points) {
    if (player.level().isClientSide
        || !stillValid(player)
        || slime == null
        || values.length != 9
        || !Float.isFinite(health)
        || !Float.isFinite(stamina)
        || health < 0
        || stamina < 0
        || points < 0
        || points > 32767) return false;
    for (SlimeStat stat : SlimeStat.values())
      if (!stat.acceptsTunedValue(values[stat.ordinal()])
          || (stat == SlimeStat.HEALTH
              && slime instanceof GuardianAngelSlime
              && values[stat.ordinal()] > 102.4)) return false;
    if (health > 1024 || stamina > values[SlimeStat.STAMINA.ordinal()]) return false;
    if (slime instanceof BagSlime bag) {
      int capacity = (int) values[SlimeStat.CAPACITY.ordinal()] * slime.capacityMultiplier();
      for (int slot = capacity; slot < bag.getBag().getSlots(); slot++)
        if (!bag.getBag().getStackInSlot(slot).isEmpty()) {
          result = 3;
          return false;
        }
    }
    for (SlimeStat stat : SlimeStat.values()) stat.tune(slime, values[stat.ordinal()]);
    slime.setTunedVitals(health, stamina, points);
    result = 1;
    return true;
  }

  @Override
  public ItemStack quickMoveStack(Player player, int slot) {
    return ItemStack.EMPTY;
  }
}
