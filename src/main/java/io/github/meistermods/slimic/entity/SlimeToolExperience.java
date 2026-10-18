package io.github.meistermods.slimic.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

/** Work-earned experience repairs the working tool before the remaining native orbs are awarded. */
final class SlimeToolExperience {
  private SlimeToolExperience() {}

  static int mend(ItemStack tool, int experience) {
    if (experience <= 0
        || tool.isEmpty()
        || !tool.isDamaged()
        || EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MENDING, tool) <= 0)
      return experience;
    float ratio = tool.getXpRepairRatio();
    if (!Float.isFinite(ratio) || ratio <= 0) return experience;
    int repaired = Math.min(tool.getDamageValue(), (int) (experience * ratio));
    tool.setDamageValue(tool.getDamageValue() - repaired);
    // Native ExperienceOrb converts repaired durability to spent XP with integer division by two.
    return Math.max(0, experience - repaired / 2);
  }

  static void award(ServerLevel level, Vec3 position, ItemStack tool, int experience) {
    int remaining = mend(tool, experience);
    if (remaining > 0) ExperienceOrb.award(level, position, remaining);
  }
}
