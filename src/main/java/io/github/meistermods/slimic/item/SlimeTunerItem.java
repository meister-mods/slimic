package io.github.meistermods.slimic.item;

import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.menu.SlimeMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.network.NetworkHooks;

/** Owner's management tool, available in Survival. */
public final class SlimeTunerItem extends Item {
  public SlimeTunerItem(Properties properties) {
    super(properties);
  }

  public static boolean isHolding(Player player) {
    return player.getMainHandItem().getItem() instanceof SlimeTunerItem
        || player.getOffhandItem().getItem() instanceof SlimeTunerItem;
  }

  public static InteractionResult open(Player player, FriendlySlime target) {
    if (!isHolding(player)
        || player.isSpectator()
        || !target.isAlive()
        || !player.getUUID().equals(target.getOwnerUUID())
        || player.distanceToSqr(target) > 64) return InteractionResult.FAIL;
    if (player instanceof ServerPlayer server)
      NetworkHooks.openScreen(
          server,
          new SimpleMenuProvider(
              (id, inventory, viewer) -> new SlimeMenu(id, inventory, target),
              target.getDisplayName()),
          buffer -> SlimeMenu.writeOpeningData(buffer, target));
    return InteractionResult.sidedSuccess(player.level().isClientSide);
  }
}
