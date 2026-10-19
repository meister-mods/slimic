package io.github.meistermods.slimic.item;

import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.SlimeWorkArea;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.network.ModNetwork;
import io.github.meistermods.slimic.network.WorkAreaPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;

/** Owner's management tool, available in Survival. */
public final class SlimeTunerItem extends Item {
  public SlimeTunerItem(Properties properties) {
    super(properties);
  }

  public static boolean isHolding(Player player) {
    return player.getMainHandItem().getItem() instanceof SlimeTunerItem
        || player.getOffhandItem().getItem() instanceof SlimeTunerItem;
  }

  public static boolean isHoldingPreviewTool(Player player) {
    return isHolding(player)
        || player.getMainHandItem().getItem() instanceof CreativeSlimeTunerItem
        || player.getOffhandItem().getItem() instanceof CreativeSlimeTunerItem;
  }

  public static InteractionResult preview(Player player, FriendlySlime target) {
    if (!isHoldingPreviewTool(player)
        || (!isHolding(player) && !player.getAbilities().instabuild)
        || player.isSpectator()
        || !target.isAlive()
        || target.level() != player.level()
        || !target.canManage(player)
        || player.distanceToSqr(target) > 64) return InteractionResult.FAIL;
    if (player instanceof ServerPlayer server) {
      var area = SlimeWorkArea.of(target);
      ModNetwork.CHANNEL.send(
          PacketDistributor.PLAYER.with(() -> server),
          new WorkAreaPacket(
              target.level().dimension().location(), target.getId(), target.getUUID(), area));
      player.displayClientMessage(
          Component.translatable(
              area.shapes().isEmpty() ? "message.slimic.no_work_area" : "message.slimic.work_area",
              target.getDisplayName(),
              Component.translatable(
                  area.planned()
                      ? "message.slimic.work_area.planned"
                      : "message.slimic.work_area.current")),
          true);
    }
    return InteractionResult.sidedSuccess(player.level().isClientSide);
  }

  public static InteractionResult open(Player player, FriendlySlime target) {
    if (!isHolding(player)
        || player.isSpectator()
        || !target.isAlive()
        || !target.canManage(player)
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
