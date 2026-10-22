package io.github.meistermods.slimic.menu;

import java.util.function.IntToDoubleFunction;
import net.minecraft.world.inventory.ContainerData;

/** Exact finite numbers transported through vanilla signed-short menu packets. */
public final class NumericContainerData implements ContainerData {
  private final long[] bits;
  private final IntToDoubleFunction source;
  private boolean ready;
  private final java.util.Map<Integer, java.util.function.LongSupplier> integers;
  private final boolean clientSide;

  public NumericContainerData(int count, boolean clientSide, IntToDoubleFunction source) {
    this(count, clientSide, source, -1, null);
  }

  public NumericContainerData(
      int count,
      boolean clientSide,
      IntToDoubleFunction source,
      int longIndex,
      java.util.function.LongSupplier longSource) {
    this(
        count,
        clientSide,
        source,
        longIndex < 0 ? java.util.Map.of() : java.util.Map.of(longIndex, longSource));
  }

  public NumericContainerData(
      int count,
      boolean clientSide,
      IntToDoubleFunction source,
      java.util.Map<Integer, java.util.function.LongSupplier> integers) {
    bits = new long[count];
    this.source = clientSide ? null : source;
    this.integers = java.util.Map.copyOf(integers);
    this.clientSide = clientSide;
    ready = !clientSide;
  }

  public double value(int index) {
    if (integers.containsKey(index)) return longValue(index);
    return source == null ? Double.longBitsToDouble(bits[index]) : source.applyAsDouble(index);
  }

  public long longValue(int index) {
    return clientSide ? bits[index] : integers.get(index).getAsLong();
  }

  public boolean isReady() {
    return ready;
  }

  @Override
  public int get(int index) {
    if (index == getCount() - 1) return ready ? 1 : 0;
    long raw =
        integers.containsKey(index / 4)
            ? longValue(index / 4)
            : Double.doubleToLongBits(value(index / 4));
    return (int) (raw >>> (index % 4 * 16)) & 0xffff;
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
