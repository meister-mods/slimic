package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.Slimic;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
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

  @SubscribeEvent
  public static void harmlessSignal(LivingAttackEvent event) {
    if (event.getSource().getDirectEntity() instanceof FireworkRocketEntity rocket
        && rocket.getPersistentData().getBoolean(HeraldSlime.SIGNAL_MARKER))
      event.setCanceled(true);
  }
}
