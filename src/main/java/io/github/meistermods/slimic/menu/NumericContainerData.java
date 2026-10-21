package io.github.meistermods.slimic.menu;

import java.util.function.IntToDoubleFunction;
import net.minecraft.world.inventory.ContainerData;

/** Exact finite numbers transported through vanilla signed-short menu packets. */
public final class NumericContainerData implements ContainerData {
  private final long[] bits;
  private final IntToDoubleFunction source;
  private boolean ready;

  public NumericContainerData(int count, boolean clientSide, IntToDoubleFunction source) {
    bits = new long[count];
    this.source = source;
    ready = !clientSide;
  }

  public double value(int index) {
    return source == null ? Double.longBitsToDouble(bits[index]) : source.applyAsDouble(index);
  }

  public boolean isReady() {
    return ready;
  }

  @Override
  public int get(int index) {
    if (index == getCount() - 1) return ready ? 1 : 0;
    return (int) (Double.doubleToLongBits(value(index / 4)) >>> (index % 4 * 16)) & 0xffff;
  }

  @Override
  public void set(int index, int value) {
    if (index == getCount() - 1) {
      ready = value != 0;
      return;
    }
    int shift = index % 4 * 16;
    bits[index / 4] = (bits[index / 4] & ~(0xffffL << shift)) | ((value & 0xffffL) << shift);
  }

  @Override
  public int getCount() {
    return bits.length * 4 + 1;
  }
}
