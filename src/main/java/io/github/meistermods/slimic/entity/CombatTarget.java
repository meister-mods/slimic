package io.github.meistermods.slimic.entity;

import java.util.Locale;

public enum CombatTarget {
  HOSTILE,
  NEUTRAL,
  PASSIVE,
  PLAYERS,
  TAMED,
  CREEPERS;

  public String key() {
    return name().toLowerCase(Locale.ROOT);
  }
}
