package io.github.meistermods.slimic.item;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public final class WorldBlockItem extends BlockItem {
  private final boolean decoration;

  public WorldBlockItem(Block block, Properties properties, boolean decoration) {
    super(block, properties);
    this.decoration = decoration;
  }

  @Override
  public void appendHoverText(
      ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
    super.appendHoverText(stack, level, tooltip, flag);
    if (decoration) tooltip.add(Component.translatable("tooltip.slimic.world_decoration"));
  }
}
