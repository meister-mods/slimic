package io.github.meistermods.slimic.item;

import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.SlimeWorkArea;
import io.github.meistermods.slimic.network.ModNetwork;
import io.github.meistermods.slimic.network.WorkAreaPacket;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

/** Owner's tool for viewing current and planned companion work areas. */
public final class SlimeScannerItem extends Item {
  public SlimeScannerItem(Properties properties) {
    super(properties);
  }

  public static boolean isHolding(Player player) {
    return player.getMainHandItem().getItem() instanceof SlimeScannerItem
        || player.getOffhandItem().getItem() instanceof SlimeScannerItem;
  }

  public static InteractionResult preview(Player player, FriendlySlime target) {
    if (!isHolding(player)
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

  @Override
  public void appendHoverText(
      ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
    tooltip.add(
        Component.translatable("item.slimic.slime_scanner.tooltip").withStyle(ChatFormatting.GRAY));
    tooltip.add(
        Component.translatable("item.slimic.slime_scanner.detail").withStyle(ChatFormatting.GRAY));
  }
}
