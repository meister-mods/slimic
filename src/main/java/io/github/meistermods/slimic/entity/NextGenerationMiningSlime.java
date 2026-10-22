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
import net.minecraft.world.phys.Vec3;

/** Policy selection is the reward; ordinary tool, stamina, logistics and safety rules remain. */
public final class NextGenerationMiningSlime extends MiningSlime {
  public enum Policy {
    NORMAL,
    ORE_PRIORITY,
    SELECTIVE_ORE
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
  private int scanCursor;
  private boolean sawOre, blockedOre, missingTool, insufficientTool;

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

  public int oreMask() {
    return entityData.get(ORE_MASK);
  }

  public void setPolicy(Policy policy) {
    entityData.set(POLICY, policy.ordinal());
    resetSelection();
  }

  public void toggleOre(int ore) {
    if (ore >= 0 && ore < ORES.size()) {
      entityData.set(ORE_MASK, oreMask() ^ (1 << ore));
      resetSelection();
    }
  }

  private void resetSelection() {
    clearTarget();
    scanCursor = 0;
    sawOre = blockedOre = missingTool = insufficientTool = false;
    status = Status.IDLE;
    refreshFluidSourceGuard();
  }

  @Override
  protected boolean selectiveMining() {
    return policy() == Policy.SELECTIVE_ORE;
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
  protected boolean terrain(BlockPos pos, BlockState state) {
    return super.terrain(pos, state) && (!selectiveMining() || allowsOre(state));
  }

  @Override
  protected boolean rampFloor(BlockPos pos) {
    return selectiveMining()
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
  protected BlockPos findTarget(ServerLevel server) {
    if (!selectiveMining()) return super.findTarget(server);
    pruneSkipped(server.getGameTime());
    if (!server.hasChunk(getMiningChunk().x, getMiningChunk().z)) {
      status = Status.UNREACHABLE;
      return null;
    }
    if (oreMask() == 0) {
      status = Status.COMPLETE;
      return null;
    }
    double detection = SlimeStat.DETECTION_RANGE.value(this);
    int minY = (int) Math.max(server.getMinBuildHeight(), getY() - detection),
        maxY = (int) Math.min(server.getMaxBuildHeight() - 1, getY() + detection);
    int height = Math.max(1, maxY - minY + 1), total = height * 256;
    int firstLayer = Math.max(0, Math.min(height - 1, getBlockY() - minY));
    var chunk = getMiningChunk();
    BlockPos best = null;
    double closest = Double.MAX_VALUE;
    // Scan at most 1,024 positions per search slice, irrespective of Creative Detection Range.
    for (int budget = 0; budget < 1024 && scanCursor < total; budget++, scanCursor++) {
      int value = scanCursor;
      var pos =
          new BlockPos(
              chunk.getMinBlockX() + (value & 15),
              minY + (firstLayer + value / 256) % height,
              chunk.getMinBlockZ() + (value >> 4 & 15));
      var state = server.getBlockState(pos);
      if (!allowsOre(state)
          || position().distanceToSqr(Vec3.atCenterOf(pos)) > detection * detection) continue;
      sawOre = true;
      if (!terrain(pos, state)) {
        blockedOre = true;
        continue;
      }
      if (targetSkipped(pos)) {
        blockedOre = true;
        continue;
      }
      if (!canSee(pos)) {
        blockedOre = true;
        continue;
      }
      int tool = toolFor(state);
      if (tool < 0) {
        if (missingRequiredTool(state)) missingTool = true;
        else insufficientTool = true;
        continue;
      }
      double distance = pos.distToCenterSqr(position());
      if (distance < closest) {
        closest = distance;
        best = pos;
      }
    }
    if (best != null) {
      scanCursor = 0;
      status = Status.IDLE;
      return best;
    }
    if (scanCursor >= total) {
      status =
          missingTool
              ? Status.NO_TOOL
              : insufficientTool
                  ? Status.NEEDS_TIER
                  : sawOre || blockedOre ? Status.UNREACHABLE : Status.COMPLETE;
      scanCursor = 0;
      sawOre = blockedOre = missingTool = insufficientTool = false;
    } else if (status != Status.UNREACHABLE
        && status != Status.NO_TOOL
        && status != Status.NEEDS_TIER
        && status != Status.COMPLETE) status = Status.IDLE;
    return null;
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putInt("NextGenerationPolicy", policy().ordinal());
    tag.putInt("NextGenerationOreMask", oreMask());
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
    scanCursor = 0;
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.NEXT_GENERATION_MINING_SLIME.get());
  }
}
