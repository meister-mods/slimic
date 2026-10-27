package io.github.meistermods.slimic.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** The transport member uses the shared authority, interaction and lifetime rules. */
public final class TransportColonyBody extends LinkedColonyBody<BoundTransportSlime> {
  public TransportColonyBody(EntityType<? extends TransportColonyBody> type, Level level) {
    super(type, level, BoundTransportSlime.class);
  }
}
