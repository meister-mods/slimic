package io.github.meistermods.slimic.entity;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** Numeric representation and modifier arithmetic, without gameplay tuning ceilings. */
public final class CreativeStats {
  private CreativeStats() {}

  public static double finite(double value) {
    if (Double.isNaN(value)) return 0;
    return Math.max(-Double.MAX_VALUE, Math.min(Double.MAX_VALUE, value));
  }

  public static float asFloat(double value) {
    return (float) Math.max(-Float.MAX_VALUE, Math.min(Float.MAX_VALUE, finite(value)));
  }

  public static int asInt(double value) {
    return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, finite(value)));
  }

  public static double attributeValue(AttributeInstance attribute) {
    double base = attribute.getBaseValue();
    for (var modifier : attribute.getModifiers(AttributeModifier.Operation.ADDITION))
      base = finite(base + modifier.getAmount());
    double value = base;
    for (var modifier : attribute.getModifiers(AttributeModifier.Operation.MULTIPLY_BASE))
      value = finite(value + finite(base * modifier.getAmount()));
    for (var modifier : attribute.getModifiers(AttributeModifier.Operation.MULTIPLY_TOTAL))
      value = finite(value * (1 + modifier.getAmount()));
    return value;
  }
}
