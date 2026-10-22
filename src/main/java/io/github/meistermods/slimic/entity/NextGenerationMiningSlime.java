package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.registry.ModItems;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** A compact mining veteran with inherited efficiency and selectable excavation policies. */
public final class NextGenerationMiningSlime extends MiningSlime {
  public enum Policy {
    NORMAL,
    ORE_PRIORITY,
    PRECISION_PROSPECTING
  }

  public static final List<String> ORES =
      List.of(
          "coal",
          "copper",
          "iron",
          "gold",
          "redstone",
          "lapis",
          "diamond",
          "emerald",
          "quartz",
          "netherite_scrap");
  private static final EntityDataAccessor<Integer> POLICY =
      SynchedEntityData.defineId(NextGenerationMiningSlime.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<Integer> ORE_MASK =
      SynchedEntityData.defineId(NextGenerationMiningSlime.class, EntityDataSerializers.INT);
  private final PrecisionProspecting prospecting = new PrecisionProspecting(this);

  public NextGenerationMiningSlime(
      EntityType<? extends NextGenerationMiningSlime> type, Level level) {
    super(type, level);
  }

  @Override
  protected void defineSynchedData() {
    super.defineSynchedData();
    entityData.define(POLICY, 0);
    entityData.define(ORE_MASK, (1 << ORES.size()) - 1);
  }

  public Policy policy() {
    return Policy.values()[entityData.get(POLICY)];
  }

  private void updateNavigation() {
    getNavigation().stop();
    navigation =
        specializedMining()
            ? new WorkNavigation(this, level())
            : new QuarryNavigation(this, level());
  }

  public int oreMask() {
    return entityData.get(ORE_MASK);
  }

  public void setPolicy(Policy policy) {
    if (policy() == policy) return;
    entityData.set(POLICY, policy.ordinal());
    resetSelection();
    updateNavigation();
  }

  public void toggleOre(int ore) {
    if (ore >= 0 && ore < ORES.size()) {
      entityData.set(ORE_MASK, oreMask() ^ (1 << ore));
      clearTarget();
      prospecting.filtersChanged();
      status = Status.IDLE;
    }
  }

  private void resetSelection() {
    clearTarget();
    prospecting.reset();
    status = Status.IDLE;
    refreshFluidSourceGuard();
  }

  @Override
  protected boolean specializedMining() {
    return policy() == Policy.PRECISION_PROSPECTING;
  }

  public boolean allowsOre(BlockState state) {
    for (int i = 0; i < ORES.size(); i++)
      if ((oreMask() & (1 << i)) != 0
          && state.is(
              TagKey.create(
                  Registries.BLOCK,
                  ResourceLocation.fromNamespaceAndPath("forge", "ores/" + ORES.get(i)))))
        return true;
    return false;
  }

  @Override
  protected boolean rampFloor(BlockPos pos) {
    return specializedMining()
        ? level() instanceof ServerLevel server && QuarryStaircaseData.get(server).protects(pos)
        : super.rampFloor(pos);
  }

  @Override
  protected double targetPriority(BlockPos pos, BlockState state, boolean visible) {
    return super.targetPriority(pos, state, visible)
        - (policy() == Policy.ORE_PRIORITY
                && visible
                && state.is(net.minecraftforge.common.Tags.Blocks.ORES)
            ? 4096
            : 0);
  }

  @Override
  protected boolean spendStamina(float amount) {
    return super.spendStamina(amount * .5F);
  }

  @Override
  protected boolean tickSpecializedMining(ServerLevel server) {
    if (!specializedMining()) return false;
    prospecting.tick(server);
    return true;
  }

  public PrecisionProspecting prospecting() {
    return prospecting;
  }

  @Override
  public void setWorking(boolean value) {
    super.setWorking(value);
    if (prospecting != null) prospecting.interrupt();
  }

  @Override
  public void onReleased() {
    boolean dimensionChanged = hasChangedWorkDimension();
    super.onReleased();
    prospecting.relocate(dimensionChanged);
  }

  @Override
  protected void clearTarget() {
    super.clearTarget();
    if (prospecting != null) prospecting.interrupt();
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putInt("NextGenerationPolicy", policy().ordinal());
    tag.putInt("NextGenerationOreMask", oreMask());
    tag.put("PrecisionProspecting", prospecting.save());
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    int selected = tag.getInt("NextGenerationPolicy");
    entityData.set(POLICY, selected >= 0 && selected < Policy.values().length ? selected : 0);
    entityData.set(
        ORE_MASK,
        tag.contains("NextGenerationOreMask")
            ? tag.getInt("NextGenerationOreMask") & ((1 << ORES.size()) - 1)
            : (1 << ORES.size()) - 1);
    updateNavigation();
    prospecting.load(tag.getCompound("PrecisionProspecting"));
    if (selected == 2
        && tag.contains("NextGenerationPolicy")
        && !tag.contains("PrecisionProspecting")) {
      setWorking(false);
      setWaiting(true);
    }
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.NEXT_GENERATION_MINING_SLIME.get());
  }
}
