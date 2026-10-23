package io.github.meistermods.slimic.item;

import io.github.meistermods.slimic.entity.FriendlySlime;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Empty recipe-display icons cannot mint a colony or independent constituent identities. */
public final class BoundColonyItem extends FriendlySlimeItem {
  private final Supplier<? extends EntityType<? extends FriendlySlime>> type;

  public BoundColonyItem(
      Properties properties, Supplier<? extends EntityType<? extends FriendlySlime>> type) {
    super(properties);
    this.type = type;
  }

  @Override
  protected EntityType<? extends FriendlySlime> entityType() {
    return type.get();
  }

  @Override
  public void appendHoverText(
      ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
    if (stack.hasTag() && stack.getTag().contains("SlimeData")) {
      super.appendHoverText(stack, level, lines, flag);
      return;
    }
    lines.add(
        Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    lines.add(
        Component.translatable("item.slimic.colony.stored_only").withStyle(ChatFormatting.GRAY));
  }
}
