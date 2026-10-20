package io.github.meistermods.slimic.world;

import io.github.meistermods.slimic.Slimic;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Slimic.MOD_ID)
public final class SlimicWorldEvents {
  private SlimicWorldEvents() {}

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void activate(PlayerInteractEvent.RightClickBlock event) {
    if (event.getEntity() instanceof ServerPlayer player
        && event.getItemStack().is(Items.SLIME_BALL)
        && event.getUseItem() != net.minecraftforge.eventbus.api.Event.Result.DENY
        && event.getUseBlock() != net.minecraftforge.eventbus.api.Event.Result.DENY
        && SlimicPortalTravel.activate(
            player, event.getHand(), event.getPos().relative(event.getFace()))) {
      player.swing(event.getHand(), true);
      event.setCancellationResult(InteractionResult.CONSUME);
      event.setCanceled(true);
    }
  }
}
