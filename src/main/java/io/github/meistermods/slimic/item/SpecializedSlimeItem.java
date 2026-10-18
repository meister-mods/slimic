package io.github.meistermods.slimic.item;

import io.github.meistermods.slimic.entity.CombatSlime;
import io.github.meistermods.slimic.entity.FishingSlime;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.MiningSlime;
import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Summons a specialization while keeping stored identity, inventory and equipment. */
public final class SpecializedSlimeItem extends FriendlySlimeItem {
  private final Supplier<? extends EntityType<? extends FriendlySlime>> type;

  public SpecializedSlimeItem(
      Properties properties, Supplier<? extends EntityType<? extends FriendlySlime>> type) {
    super(properties);
    this.type = type;
  }

  @Override
  protected EntityType<? extends FriendlySlime> entityType() {
    return type.get();
  }

  @Override
  protected void initializeSlime(FriendlySlime slime, ItemStack stack) {
    if (slime instanceof FishingSlime fisher) {
      if (stack.hasTag() && stack.getTag().contains("InitialFishingRod")) {
        var rod = ItemStack.of(stack.getTag().getCompound("InitialFishingRod"));
        if (FishingSlime.isRod(rod)) fisher.setRod(rod.copyWithCount(1));
      } else if ((!stack.hasTag() || !stack.getTag().contains("SlimeData")))
        fisher.setRod(new ItemStack(Items.FISHING_ROD));
    }
    if (!stack.hasTag()) return;
    if (slime instanceof MiningSlime miner
        && stack.getTag().contains(MiningSlimeItem.INITIAL_PICKAXE)) {
      var pick = ItemStack.of(stack.getTag().getCompound(MiningSlimeItem.INITIAL_PICKAXE));
      if (MiningSlime.isPickaxe(pick)) miner.setTool(0, pick.copyWithCount(1));
    }
    if (slime instanceof CombatSlime fighter
        && stack.getTag().contains(CombatSlimeItem.INITIAL_SWORD)) {
      var sword = ItemStack.of(stack.getTag().getCompound(CombatSlimeItem.INITIAL_SWORD));
      if (CombatSlime.isSword(sword)) fighter.setEquipment(0, sword.copyWithCount(1));
    }
  }
}
