package io.github.meistermods.slimic.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Short field records remain readable without an optional guidebook mod. */
public final class AssemblyRecordItem extends RoyalRelicItem {
  private final String record;

  public AssemblyRecordItem(Properties properties, String record) {
    super(properties);
    this.record = record;
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    if (player instanceof ServerPlayer server) {
      server.sendSystemMessage(Component.translatable(getDescriptionId()));
      for (int page = 1; page <= 3; page++)
        server.sendSystemMessage(Component.translatable("record.slimic." + record + "." + page));
    }
    return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
  }
}
