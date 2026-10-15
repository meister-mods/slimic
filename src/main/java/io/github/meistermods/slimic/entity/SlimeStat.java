package io.github.meistermods.slimic.entity;

import java.util.Locale;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;

public enum SlimeStat {
  HEALTH(Attributes.MAX_HEALTH, 4.0, 20, 10),
  ATTACK(Attributes.ATTACK_DAMAGE, 1.0, 20, 10),
  DEFENSE(Attributes.ARMOR, 1.0, 20, 10),
  MOVEMENT_SPEED(Attributes.MOVEMENT_SPEED, 0.015, 20, 1000),
  WORK_SPEED(null, 0.1, 20, 100),
  CAPACITY(null, 3.0, 6, 1),
  STAMINA(null, 20.0, 20, 10),
  REACH(null, 0.25, 20, 100),
  DETECTION_RANGE(Attributes.FOLLOW_RANGE, 1.0, 20, 10);

  private final Attribute attribute;
  private final double increment;
  private final int maxLevel;
  private final int syncScale;

  SlimeStat(Attribute attribute, double increment, int maxLevel, int syncScale) {
    this.attribute = attribute;
    this.increment = increment;
    this.maxLevel = maxLevel;
    this.syncScale = syncScale;
  }

  public String key() {
    return name().toLowerCase(Locale.ROOT);
  }

  public String translationKey() {
    return "stat.slimic." + key();
  }

  public int maxLevel() {
    return maxLevel;
  }

  public int syncScale() {
    return syncScale;
  }

  public double value(FriendlySlime slime) {
    if (attribute != null) return slime.getAttributeValue(attribute);
    int level = slime.getStatLevel(this);
    return switch (this) {
      case WORK_SPEED -> 1.0 + increment * level;
      case CAPACITY -> 9.0 + increment * level;
      case STAMINA -> 100.0 + increment * level;
      case REACH -> 3.0 + increment * level;
      default -> throw new IllegalStateException("Missing stat value: " + this);
    };
  }

  public void applyUpgrade(FriendlySlime slime) {
    if (attribute != null) {
      var instance = slime.getAttribute(attribute);
      instance.setBaseValue(instance.getBaseValue() + increment);
    }
  }
}
