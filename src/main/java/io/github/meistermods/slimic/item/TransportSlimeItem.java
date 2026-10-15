package io.github.meistermods.slimic.item;

import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class TransportSlimeItem extends FriendlySlimeItem {
  public TransportSlimeItem(Properties properties) {
    super(properties);
  }

  @Override
  protected EntityType<? extends FriendlySlime> entityType() {
    return ModEntities.TRANSPORT_SLIME.get();
  }

  @Override
  public void appendHoverText(
      ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
    super.appendHoverText(stack, level, tooltip, flag);
    tooltip.add(
        Component.translatable("item.slimic.transport_slime.tooltip")
            .withStyle(ChatFormatting.GRAY));
  }
}
