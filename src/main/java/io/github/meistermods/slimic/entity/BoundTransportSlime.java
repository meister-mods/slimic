package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class BoundTransportSlime extends TransportSlime implements BoundColony {
  private final ColonyMembers members = new ColonyMembers(this);

  public BoundTransportSlime(EntityType<? extends BoundTransportSlime> type, Level level) {
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
  public int capacityMultiplier() {
    return 1;
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.BOUND_TRANSPORT_SLIME.get());
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
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    members.clear();
    super.readAdditionalSaveData(tag);
    members.load(tag.getCompound("ColonyMembers"));
  }
}
