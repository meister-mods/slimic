package io.github.meistermods.slimic.entity;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class CompanionOwnership {
  private CompanionOwnership() {}

  public static boolean owns(Level level, UUID owner, UUID player) {
    return owner != null
        && player != null
        && (owner.equals(player)
            || level instanceof ServerLevel server
                && CompanionRegistry.get(server.getServer()).owns(owner, player));
  }

  public static boolean shares(Level level, UUID first, UUID second) {
    return first != null
        && second != null
        && (first.equals(second)
            || level instanceof ServerLevel server
                && CompanionRegistry.get(server.getServer()).shares(first, second));
  }

  public static boolean cooperates(Level level, UUID first, UUID second) {
    return first != null
        && second != null
        && (first.equals(second)
            || level instanceof ServerLevel server
                && CompanionRegistry.get(server.getServer()).owns(first, second)
                && CompanionRegistry.get(server.getServer()).owns(second, first));
  }
}
