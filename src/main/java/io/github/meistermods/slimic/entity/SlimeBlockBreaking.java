package io.github.meistermods.slimic.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;

/** Native block-breaking time shared by quarry and surface workers. */
final class SlimeBlockBreaking {
  private BlockPos target;
  private BlockState state;
  private ItemStack tool = ItemStack.EMPTY;
  private float progress;
  private int lastTick;

  static boolean usable(ItemStack stack) {
    return !stack.isEmpty()
        && (!stack.isDamageableItem() || stack.getDamageValue() < stack.getMaxDamage());
  }

  boolean advance(
      FriendlySlime slime, FakePlayer worker, BlockPos pos, BlockState next, ItemStack held) {
    if (!usable(held)
        || (next.requiresCorrectToolForDrops() && !held.isCorrectToolForDrops(next))) {
      reset(slime);
      return false;
    }
    if (!pos.equals(target)
        || next != state
        || !ItemStack.matches(held, tool)
        || lastTick != slime.tickCount - 1) {
      reset(slime);
      target = pos.immutable();
      state = next;
      tool = held.copy();
    }
    lastTick = slime.tickCount;
    // Like native Efficiency, the work bonus requires a tool effective on this block.
    double multiplier = held.getDestroySpeed(next) > 1 ? SlimeStat.WORK_SPEED.value(slime) : 1;
    double amount = next.getDestroyProgress(worker, slime.level(), pos) * multiplier;
    if (Double.isNaN(amount) || amount <= 0) return false;
    progress = (float) Math.min(1, progress + amount);
    if (slime.level() instanceof ServerLevel server)
      server.destroyBlockProgress(slime.getId(), pos, Math.min(9, (int) (progress * 10)));
    return progress >= 1;
  }

  void reset(FriendlySlime slime) {
    if (target != null && slime.level() instanceof ServerLevel server)
      server.destroyBlockProgress(slime.getId(), target, -1);
    target = null;
    state = null;
    tool = ItemStack.EMPTY;
    progress = 0;
  }
}
