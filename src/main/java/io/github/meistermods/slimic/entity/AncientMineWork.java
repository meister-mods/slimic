package io.github.meistermods.slimic.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;

/** A finite old work order: one natural 3x3 face, never a roaming terrain-destruction goal. */
final class AncientMineWork {
  private static final TagKey<Block> TERRAIN =
      TagKey.create(
          Registries.BLOCK,
          io.github.meistermods.slimic.world.SlimicWorld.id("collapsed_mine_terrain"));
  private BlockPos face, target;
  private Direction direction = Direction.NORTH;
  private BlockState state;
  private float progress;
  private int completed, retryAt;

  void bind(BlockPos pos, Direction facing) {
    face = pos.immutable();
    direction = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
    completed = 0;
    target = null;
    progress = 0;
  }

  boolean hasWorkFace() {
    return face != null;
  }

  private boolean allowed(AncientSlime mob, ServerLevel level, BlockPos pos) {
    if (!level.hasChunkAt(pos) || pos.distSqr(mob.getRuinHome()) > 8 * 8) return false;
    var block = level.getBlockState(pos);
    return block.is(TERRAIN)
        && !block.hasBlockEntity()
        && level.getBlockEntity(pos) == null
        && block.getFluidState().isEmpty()
        && block.getDestroySpeed(level, pos) >= 0
        && !QuarryStaircaseData.get(level).protects(pos)
        && block.canEntityDestroy(level, pos, mob);
  }

  void tick(AncientSlime mob) {
    if (face == null
        || completed >= 9
        || mob.tickCount < retryAt
        || !(mob.level() instanceof ServerLevel level)
        || !ForgeEventFactory.getMobGriefingEvent(level, mob)) {
      resetProgress(mob);
      return;
    }
    var tool = mob.getMainHandItem();
    if (!SlimeBlockBreaking.usable(tool)) {
      resetProgress(mob);
      return;
    }
    if (target == null) {
      var across = direction.getClockWise();
      double nearest = Double.MAX_VALUE;
      for (int y = 0; y < 3; y++)
        for (int x = -1; x <= 1; x++) {
          var next = face.relative(across, x).above(y);
          if (allowed(mob, level, next) && tool.isCorrectToolForDrops(level.getBlockState(next))) {
            double distance = mob.getEyePosition().distanceToSqr(Vec3.atCenterOf(next));
            if (!visible(mob, level, next)) distance += 1024;
            if (distance < nearest) {
              nearest = distance;
              target = next;
              state = level.getBlockState(next);
            }
          }
        }
      if (target == null) {
        retryAt = mob.tickCount + 80;
        return;
      }
    }
    if (!allowed(mob, level, target) || !level.getBlockState(target).equals(state)) {
      resetProgress(mob);
      return;
    }
    var stand = Vec3.atBottomCenterOf(face.relative(direction.getOpposite(), 2));
    if (mob.getEyePosition().distanceToSqr(Vec3.atCenterOf(target)) > 16) {
      resetProgress(mob);
      if (mob.tickCount % 20 == 0 && level.hasChunkAt(BlockPos.containing(stand)))
        mob.getNavigation().moveTo(stand.x, stand.y, stand.z, .7);
      return;
    }
    if (!visible(mob, level, target)) {
      resetProgress(mob);
      return;
    }
    mob.getNavigation().stop();
    mob.getLookControl().setLookAt(Vec3.atCenterOf(target));
    var worker = FakePlayerFactory.get(level, new GameProfile(mob.getUUID(), "[SlimicRemnant]"));
    worker.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
    worker.setGameMode(GameType.SURVIVAL);
    worker.setOnGround(true);
    ItemStack previous = worker.getMainHandItem();
    worker.setItemInHand(InteractionHand.MAIN_HAND, tool);
    try {
      progress += state.getDestroyProgress(worker, level, target) * .35F;
      level.destroyBlockProgress(mob.getId(), target, Math.min(9, (int) (progress * 10)));
      if (progress < 1) return;
      int experience = ForgeHooks.onBlockBreakEvent(level, GameType.SURVIVAL, worker, target);
      if (experience < 0
          || !mob.isAlive()
          || mob.level() != level
          || mob.getEyePosition().distanceToSqr(Vec3.atCenterOf(target)) > 16
          || mob.getMainHandItem() != tool
          || !SlimeBlockBreaking.usable(tool)
          || !tool.isCorrectToolForDrops(state)
          || mob.isNoAi()
          || mob.getTarget() != null
          || !ForgeEventFactory.getMobGriefingEvent(level, mob)
          || !allowed(mob, level, target)
          || !level.getBlockState(target).equals(state)) {
        retryAt = mob.tickCount + 100;
        resetProgress(mob);
        return;
      }
      BlockPos mined = target;
      var old = state;
      if (level.destroyBlock(mined, false, mob)) {
        if (level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS))
          Block.dropResources(old, level, mined, null, worker, tool);
        tool.mineBlock(level, old, mined, worker);
        completed++;
      }
      retryAt = mob.tickCount + 40;
      resetProgress(mob);
    } finally {
      worker.setItemInHand(InteractionHand.MAIN_HAND, previous);
    }
  }

  private boolean visible(AncientSlime mob, ServerLevel level, BlockPos pos) {
    return LoadedBlockView.clip(
            level,
            new net.minecraft.world.level.ClipContext(
                mob.getEyePosition(),
                Vec3.atCenterOf(pos),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                mob))
        .getBlockPos()
        .equals(pos);
  }

  void resetProgress(AncientSlime mob) {
    if (target != null && mob.level() instanceof ServerLevel level)
      level.destroyBlockProgress(mob.getId(), target, -1);
    target = null;
    state = null;
    progress = 0;
  }

  void save(CompoundTag tag) {
    if (face != null) tag.put("AncientMineFace", NbtUtils.writeBlockPos(face));
    tag.putInt("AncientMineDirection", direction.get3DDataValue());
    tag.putInt("AncientMineCompleted", completed);
  }

  void load(CompoundTag tag) {
    face =
        tag.contains("AncientMineFace", 10)
            ? NbtUtils.readBlockPos(tag.getCompound("AncientMineFace"))
            : null;
    direction = Direction.from3DDataValue(tag.getInt("AncientMineDirection"));
    if (!direction.getAxis().isHorizontal()) direction = Direction.NORTH;
    completed = Math.max(0, Math.min(9, tag.getInt("AncientMineCompleted")));
    target = null;
    state = null;
    progress = 0;
    retryAt = 0;
  }
}
