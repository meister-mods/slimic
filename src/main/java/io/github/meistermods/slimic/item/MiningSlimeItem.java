package io.github.meistermods.slimic.item;

import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class MiningSlimeItem extends FriendlySlimeItem {
  public static final String INITIAL_PICKAXE = "InitialPickaxe";

  public MiningSlimeItem(Properties properties) {
    super(properties);
  }

  @Override
  protected EntityType<? extends FriendlySlime> entityType() {
    return ModEntities.MINING_SLIME.get();
  }

  @Override
  protected void initializeSlime(FriendlySlime slime, ItemStack stack) {
    if (stack.hasTag() && stack.getTag().contains(INITIAL_PICKAXE)) {
      ItemStack tool = ItemStack.of(stack.getTag().getCompound(INITIAL_PICKAXE));
      if (MiningSlime.isPickaxe(tool)) {
        tool.setCount(1);
        slime.setItemSlot(EquipmentSlot.MAINHAND, tool);
      }
    }
  }

  @Override
  public void appendHoverText(
      ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
    super.appendHoverText(stack, level, tooltip, flag);
    tooltip.add(
        Component.translatable("item.slimic.mining_slime.tooltip").withStyle(ChatFormatting.GRAY));
    if (stack.hasTag() && stack.getTag().contains(INITIAL_PICKAXE)) {
      ItemStack pickaxe = ItemStack.of(stack.getTag().getCompound(INITIAL_PICKAXE));
      if (!pickaxe.isEmpty())
        tooltip.add(
            Component.translatable("item.slimic.mining_slime.pickaxe", pickaxe.getHoverName())
                .withStyle(ChatFormatting.GRAY));
    }
  }
}
