package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class BoundCombatSlime extends CombatSlime implements BoundColony {
  private final ColonyMembers members = new ColonyMembers(this);

  public BoundCombatSlime(EntityType<? extends BoundCombatSlime> type, Level level) {
    super(type, level);
  }

  @Override
  public ColonyMembers members() {
    return members;
  }

  @Override
  protected SlimeBag createBag(int slots) {
    return new ColonyBag(
        slots * 2,
        () -> members == null ? java.util.List.of() : members.bags(),
        () -> members == null ? java.util.List.of() : members.spans(),
        () -> bagRevision++);
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.BOUND_COMBAT_SLIME.get());
  }

  @Override
  public ItemStack getItemBySlot(EquipmentSlot slot) {
    if (level().isClientSide && isRenderingSecondMember()) {
      for (var raw :
          colonyAppearance().getList("SecondEquipment", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
        CompoundTag item = (CompoundTag) raw;
        if (item.getInt("Slot") == slot.ordinal()) return ItemStack.of(item);
      }
      return ItemStack.EMPTY;
    }
    return !level().isClientSide && members != null && members.ready()
        ? members.equipment(slot)
        : super.getItemBySlot(slot);
  }

  /** The representative's armor covers both bodies without moving either member's equipment. */
  public ItemStack sharedArmor() {
    return !level().isClientSide && members != null && members.ready()
        ? members.equipment(EquipmentSlot.CHEST)
        : super.getItemBySlot(EquipmentSlot.CHEST);
  }

  @Override
  public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    if (!level().isClientSide && members != null && members.ready())
      members.trainingMember().setItemSlot(slot, stack);
    else super.setItemSlot(slot, stack);
  }

  @Override
  public void tick() {
    super.tick();
    members.syncVitals();
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    members.syncVitals();
    super.addAdditionalSaveData(tag);
    tag.put("ColonyMembers", members.save());
    // Native equipment is a network/render view of member one, not additional owned equipment.
    tag.remove("HandItems");
    tag.remove("ArmorItems");
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    members.clear();
    super.readAdditionalSaveData(tag);
    members.load(tag.getCompound("ColonyMembers"));
  }
}
