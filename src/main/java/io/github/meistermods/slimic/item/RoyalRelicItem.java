package io.github.meistermods.slimic.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public class RoyalRelicItem extends Item {
  public RoyalRelicItem(Properties properties) {
    super(properties);
  }

  @Override
  public void appendHoverText(
      ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
    lines.add(Component.translatable(getDescriptionId() + ".tooltip"));
  }
}
