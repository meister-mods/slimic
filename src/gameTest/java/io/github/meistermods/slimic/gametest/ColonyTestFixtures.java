package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.SlimeArmorItem;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;

/** Complete unspawned member records for native visual fixtures; no empty colony is deployed. */
final class ColonyTestFixtures {
  private ColonyTestFixtures() {}

  static void appearance(FriendlySlime representative) {
    if (!(representative instanceof BoundColony colony)) return;
    UUID owner =
        representative.getOwnerUUID() == null ? UUID.randomUUID() : representative.getOwnerUUID();
    ListTag list = new ListTag();
    for (int i = 0; i < 2; i++) {
      BagSlime member =
          representative instanceof BoundTransportSlime
              ? ModEntities.TRANSPORT_SLIME.get().create(representative.level())
              : ModEntities.COMBAT_SLIME.get().create(representative.level());
      member.setOwnerUUID(owner);
      member.setTame(true);
      member.setDyeColorId((i == 0 ? DyeColor.LIME : DyeColor.CYAN).getId());
      if (member instanceof CombatSlime combat) {
        combat.setEquipment(0, new ItemStack(i == 0 ? Items.IRON_SWORD : Items.GOLDEN_SWORD));
        combat.setEquipment(1, new ItemStack(Items.SHIELD));
        combat.setEquipment(
            2,
            SlimeArmorItem.fromChestplate(
                new ItemStack(i == 0 ? Items.IRON_CHESTPLATE : Items.GOLDEN_CHESTPLATE)));
      }
      CompoundTag tag = new CompoundTag();
      member.save(tag);
      list.add(tag);
    }
    CompoundTag data = new CompoundTag();
    data.putInt("Schema", 1);
    data.put("Members", list);
    colony.members().load(data);
    representative.setDyeColorId(DyeColor.LIME.getId());
    if (representative.level().isClientSide)
      for (EquipmentSlot slot : EquipmentSlot.values())
        representative.setItemSlot(
            slot, colony.members().records().get(0).getItemBySlot(slot).copy());
  }
}
