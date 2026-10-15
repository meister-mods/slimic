package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.Slimic;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Slimic.MOD_ID)
public final class FriendlySlimeInteractions {
  private FriendlySlimeInteractions() {}

  @SubscribeEvent
  public static void captureOnSneakInteract(PlayerInteractEvent.EntityInteract event) {
    if (event.getTarget() instanceof FriendlySlime slime && event.getEntity().isShiftKeyDown()) {
      // Run before vanilla consumes leads, name tags, or other held items.
      event.setCancellationResult(slime.capture(event.getEntity()));
      event.setCanceled(true);
    }
  }
}
