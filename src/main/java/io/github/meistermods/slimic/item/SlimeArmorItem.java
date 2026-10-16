package io.github.meistermods.slimic.item;

import com.google.common.collect.Multimap;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class SlimeArmorItem extends ArmorItem {
  public static final String BASE = "SlimeArmorBase";

  private final Item original;

  public SlimeArmorItem(ArmorMaterials material, Item original, Properties properties) {
    super(material, ArmorItem.Type.CHESTPLATE, properties);
    this.original = original;
  }

  public static boolean accepts(ItemStack stack) {
    return stack.getItem() instanceof ArmorItem armor
        && !(armor instanceof SlimeArmorItem)
        && armor.getType() == ArmorItem.Type.CHESTPLATE;
  }

  public static ItemStack fromChestplate(ItemStack chestplate) {
    if (!accepts(chestplate)) return ItemStack.EMPTY;
    var material = ((ArmorItem) chestplate.getItem()).getMaterial();
    Item item =
        material == ArmorMaterials.LEATHER
            ? ModItems.LEATHER_SLIME_ARMOR.get()
            : material == ArmorMaterials.CHAIN
                ? ModItems.CHAINMAIL_SLIME_ARMOR.get()
                : material == ArmorMaterials.GOLD
                    ? ModItems.GOLDEN_SLIME_ARMOR.get()
                    : material == ArmorMaterials.DIAMOND
                        ? ModItems.DIAMOND_SLIME_ARMOR.get()
                        : material == ArmorMaterials.NETHERITE
                            ? ModItems.NETHERITE_SLIME_ARMOR.get()
                            : ModItems.SLIME_ARMOR.get();
    ItemStack result = new ItemStack(item);
    if (chestplate.hasTag()) result.setTag(chestplate.getTag().copy());
    result.getOrCreateTag().put(BASE, chestplate.copyWithCount(1).save(new CompoundTag()));
    return result;
  }

  public static ItemStack baseArmor(ItemStack stack) {
    ItemStack armor =
        stack.hasTag() ? ItemStack.of(stack.getTag().getCompound(BASE)) : ItemStack.EMPTY;
    if (!accepts(armor))
      armor =
          new ItemStack(
              stack.getItem() instanceof SlimeArmorItem slimeArmor
                  ? slimeArmor.original
                  : Items.IRON_CHESTPLATE);
    if (stack.hasTag()) {
      CompoundTag data = stack.getTag().copy();
      data.remove(BASE);
      armor.setTag(data.isEmpty() ? null : data);
    }
    return armor;
  }

  @Override
  public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
    ItemStack original = baseArmor(stack);
    return original.getItem().isValidRepairItem(original, repair);
  }

  @Override
  public int getEnchantmentValue(ItemStack stack) {
    return baseArmor(stack).getEnchantmentValue();
  }

  @Override
  public int getMaxDamage(ItemStack stack) {
    return baseArmor(stack).getMaxDamage();
  }

  @Override
  public Multimap<Attribute, AttributeModifier> getAttributeModifiers(
      EquipmentSlot slot, ItemStack stack) {
    return baseArmor(stack).getAttributeModifiers(slot);
  }

  @Override
  public Component getName(ItemStack stack) {
    return Component.translatable(
        "item.slimic.slime_armor.material", baseArmor(stack).getHoverName());
  }

  public static int materialIndex(ItemStack stack) {
    Item item = baseArmor(stack).getItem();
    if (item == Items.LEATHER_CHESTPLATE) return 0;
    if (item == Items.CHAINMAIL_CHESTPLATE) return 1;
    if (item == Items.GOLDEN_CHESTPLATE) return 3;
    if (item == Items.DIAMOND_CHESTPLATE) return 4;
    if (item == Items.NETHERITE_CHESTPLATE) return 5;
    return 2;
  }

  @Override
  public void appendHoverText(
      ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
    tooltip.add(Component.translatable("item.slimic.slime_armor.tooltip"));
  }
}
