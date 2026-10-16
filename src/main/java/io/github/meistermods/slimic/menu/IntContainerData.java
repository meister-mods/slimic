package io.github.meistermods.slimic.menu;

import net.minecraft.world.inventory.ContainerData;

/** Preserves full integers over Minecraft's signed-short container-data packets. */
public final class IntContainerData implements ContainerData {
  private final ContainerData values;
  private boolean ready;

  public IntContainerData(ContainerData values, boolean clientSide) {
    this.values = values;
    ready = !clientSide;
  }

  public boolean isReady() {
    return ready;
  }

  @Override
  public int get(int index) {
    if (index == getCount() - 1) return ready ? 1 : 0;
    int value = values.get(index / 2);
    return (index % 2 == 0 ? value : value >>> 16) & 0xffff;
  }

  @Override
  public void set(int index, int value) {
    if (index == getCount() - 1) {
      ready = value != 0;
      return;
    }
    int current = values.get(index / 2);
    int half = value & 0xffff;
    values.set(
        index / 2,
        index % 2 == 0 ? (current & 0xffff0000) | half : (current & 0xffff) | (half << 16));
  }

  @Override
  public int getCount() {
    return values.getCount() * 2 + 1;
  }
}
