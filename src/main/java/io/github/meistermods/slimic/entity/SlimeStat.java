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

  private final String key;
  private final Attribute attribute;
  private final double increment;
  private final int maxLevel;
  private final int syncScale;

  SlimeStat(Attribute attribute, double increment, int maxLevel, int syncScale) {
    this.key = name().toLowerCase(Locale.ROOT);
    this.attribute = attribute;
    this.increment = increment;
    this.maxLevel = maxLevel;
    this.syncScale = syncScale;
  }

  public String key() {
    return key;
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

  public boolean isUsedBy(FriendlySlime slime) {
    return switch (this) {
      case HEALTH, DEFENSE, MOVEMENT_SPEED -> true;
      case ATTACK -> slime instanceof CombatSlime || slime instanceof GuardianAngelSlime;
      case STAMINA ->
          slime instanceof BagSlime
              || slime instanceof SaddleSlime
              || slime instanceof SoggySlime
              || slime instanceof GuardianAngelSlime;
      case DETECTION_RANGE ->
          slime instanceof SoggySlime
              || slime instanceof BagSlime
              || slime instanceof HeraldSlime
              || slime instanceof GuardianAngelSlime;
      case WORK_SPEED ->
          slime instanceof BagSlime
              || slime instanceof GuardianAngelSlime
              || slime instanceof SoggySlime;
      case REACH -> slime instanceof BagSlime || slime instanceof GuardianAngelSlime;
      default -> slime instanceof BagSlime;
    };
  }

  public double value(FriendlySlime slime) {
    if (attribute != null)
      return slime.getAttributeValue(attribute)
          * (this == DETECTION_RANGE ? slime.detectionMultiplier() : 1);
    if (slime.hasTunedStat(this))
      return slime.tunedStat(this) * (this == CAPACITY ? slime.capacityMultiplier() : 1);
    int level = slime.getStatLevel(this);
    return switch (this) {
      case WORK_SPEED -> 1.0 + increment * level;
      case CAPACITY -> (9.0 + increment * level) * slime.capacityMultiplier();
      case STAMINA -> 100.0 + increment * level;
      case REACH -> 3.0 + increment * level;
      default -> throw new IllegalStateException("Missing stat value: " + this);
    };
  }

  public void applyUpgrade(FriendlySlime slime) {
    if (attribute != null) {
      var instance = slime.getAttribute(attribute);
      instance.setBaseValue(instance.getBaseValue() + increment);
    } else if (slime.hasTunedStat(this)) {
      slime.setTunedStat(this, Math.min(maxTunedValue(), slime.tunedStat(this) + increment));
    }
  }

  public double minTunedValue() {
    return this == HEALTH || this == CAPACITY || this == STAMINA ? 1 : this == WORK_SPEED ? 0.1 : 0;
  }

  public double maxTunedValue() {
    return switch (this) {
      case HEALTH -> 1024;
      case ATTACK -> 2048;
      case DEFENSE -> 30;
      case MOVEMENT_SPEED -> 10;
      case WORK_SPEED, REACH -> 32;
      case CAPACITY -> 27;
      case STAMINA -> 2000;
      case DETECTION_RANGE -> 64;
    };
  }

  public boolean acceptsTunedValue(double value) {
    return Double.isFinite(value)
        && value >= minTunedValue()
        && value <= maxTunedValue()
        && (this != CAPACITY || value == Math.rint(value));
  }

  public void tune(FriendlySlime slime, double value) {
    if (!acceptsTunedValue(value)) throw new IllegalArgumentException("Invalid stat value");
    if (attribute != null) slime.getAttribute(attribute).setBaseValue(value);
    else slime.setTunedStat(this, value);
  }

  public double tunerValue(FriendlySlime slime) {
    return attribute != null
        ? slime.getAttributeBaseValue(attribute)
        : value(slime) / (this == CAPACITY ? slime.capacityMultiplier() : 1);
  }
}
