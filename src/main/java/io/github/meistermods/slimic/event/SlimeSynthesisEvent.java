package io.github.meistermods.slimic.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/** Cancellation occurs before the durable commit; event payloads are detached preview copies. */
@Cancelable
public final class SlimeSynthesisEvent extends PlayerEvent {
  private final ItemStack normal, ancient, result;

  public SlimeSynthesisEvent(
      ServerPlayer player, ItemStack normal, ItemStack ancient, ItemStack result) {
    super(player);
    this.normal = normal.copy();
    this.ancient = ancient.copy();
    this.result = result.copy();
  }

  public ItemStack normal() {
    return normal.copy();
  }

  public ItemStack ancient() {
    return ancient.copy();
  }

  public ItemStack result() {
    return result.copy();
  }
}
