package io.github.meistermods.slimic.entity;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;

/** Retains healing and revival, then spends stamina on nearby native bonemeal targets. */
public final class AbundanceSlime extends HealingSlime {
  private BlockSurvey growthSurvey;
  private BlockPos growthTarget, growthCandidate;
  private int growthCooldown, growthTravelTicks;
  private boolean growthResting;
  private Vec3 growthLastPosition;

  public AbundanceSlime(EntityType<? extends AbundanceSlime> type, Level level) {
    super(type, level);
  }

  @Override
  protected WorkStatus describeWork() {
    var result = super.describeWork();
    if (ballSlot() >= 0) return result;
    var requests = new java.util.HashSet<>(result.requests());
    requests.add(WorkStatus.Request.SLIMEBALL_SUPPLY);
    if (!net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(
            getBag(), new ItemStack(Items.SLIME_BALL), true)
        .isEmpty()) requests.add(WorkStatus.Request.CARGO_PICKUP);
    return new WorkStatus(result.order(), result.state(), result.reason(), requests);
  }

  private void resetGrowth() {
    growthSurvey = null;
    growthTarget = null;
    growthCandidate = null;
    growthTravelTicks = 0;
    growthLastPosition = null;
  }

  @Override
  public void setWorking(boolean value) {
    super.setWorking(value);
    resetGrowth();
  }

  @Override
  public void onReleased() {
    super.onReleased();
    resetGrowth();
  }

  private boolean eligibleGrowth(ServerLevel server, BlockPos pos) {
    if (!server.hasChunkAt(pos)
        || !server.getWorldBorder().isWithinBounds(pos)
        || pos.distSqr(getWorkOrigin()) > Math.pow(SlimeStat.DETECTION_RANGE.value(this), 2))
      return false;
    var state = server.getBlockState(pos);
    return state.getBlock() instanceof BonemealableBlock growable
        && growable.isValidBonemealTarget(server, pos, state, false)
        // Tree features may extend into neighboring sections. Never acquire an unloaded border.
        && server.hasChunksAt(pos.offset(-16, -16, -16), pos.offset(16, 32, 16));
  }

  private int growthPriority(ServerLevel server, BlockPos pos) {
    var state = server.getBlockState(pos);
    if (state.is(net.minecraft.tags.BlockTags.CROPS)
        || state.is(net.minecraft.tags.BlockTags.SAPLINGS)) return 0;
    return state.getBlock() instanceof net.minecraft.world.level.block.GrassBlock ? 2 : 1;
  }

  @Override
  protected boolean performSecondaryWork(ServerLevel server) {
    if (!ForgeEventFactory.getMobGriefingEvent(server, this)) {
      status = Status.DISABLED;
      getNavigation().stop();
      return true;
    }
    if (getStamina() < 2) growthResting = true;
    if (growthResting && getStamina() < getMaxStamina() * .25F) {
      status = Status.RESTING;
      getNavigation().stop();
      return true;
    }
    growthResting = false;
    if (growthCooldown > 0) {
      status = Status.IDLE;
      return true;
    }
    if (growthTarget != null && !eligibleGrowth(server, growthTarget)) growthTarget = null;
    if (growthTarget == null) {
      if (growthSurvey == null)
        growthSurvey =
            new BlockSurvey(
                server,
                getWorkOrigin(),
                SlimeStat.DETECTION_RANGE.value(this),
                state -> state.getBlock() instanceof BonemealableBlock);
      for (var pos : growthSurvey.next(1024))
        if (eligibleGrowth(server, pos)
            && (growthCandidate == null
                || !eligibleGrowth(server, growthCandidate)
                || growthPriority(server, pos) < growthPriority(server, growthCandidate)
                || (growthPriority(server, pos) == growthPriority(server, growthCandidate)
                    && pos.distToCenterSqr(position())
                        < growthCandidate.distToCenterSqr(position())))) growthCandidate = pos;
      if (!growthSurvey.isDone()) {
        status = Status.IDLE;
        return true;
      }
      growthSurvey = null;
      growthTarget = growthCandidate;
      growthCandidate = null;
      if (growthTarget == null) return false;
    }
    var destination = Vec3.atCenterOf(growthTarget);
    var hit =
        LoadedBlockView.clip(
            server,
            new ClipContext(
                getEyePosition(),
                destination,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this));
    boolean visible =
        hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(growthTarget);
    if (getEyePosition().distanceToSqr(destination) > Math.pow(SlimeStat.REACH.value(this), 2)
        || !visible) {
      status = Status.GROWING;
      if (tickCount % 10 == 0 && (onGround() || isInWater()))
        getNavigation().moveTo(destination.x, destination.y, destination.z, 1);
      if (growthLastPosition == null || position().distanceToSqr(growthLastPosition) > .01) {
        growthLastPosition = position();
        growthTravelTicks = 0;
      } else if (++growthTravelTicks > 400) {
        resetGrowth();
        growthCooldown = 100;
        status = Status.UNREACHABLE;
      }
      return true;
    }
    getNavigation().stop();
    var pos = growthTarget;
    var state = server.getBlockState(pos);
    var player = FakePlayerFactory.get(server, new GameProfile(getOwnerUUID(), "SlimeGardener"));
    player.moveTo(position());
    if (!server.mayInteract(player, pos)) {
      resetGrowth();
      growthCooldown = 100;
      status = Status.UNREACHABLE;
      return true;
    }
    var meal = new ItemStack(Items.BONE_MEAL);
    int result = ForgeEventFactory.onApplyBonemeal(player, server, pos, state, meal);
    if (result < 0
        || !canContinueWork(player)
        || !eligibleGrowth(server, pos)
        || !server.getBlockState(pos).equals(state)) {
      resetGrowth();
      growthCooldown = 100;
      return true;
    }
    if (result == 0
        && state.getBlock() instanceof BonemealableBlock growable
        && growable.isBonemealSuccess(server, server.random, pos, state))
      growable.performBonemeal(server, server.random, pos, state);
    spendStamina(2);
    recordCompletedWork();
    server.levelEvent(1505, pos, 0);
    status = Status.GROWING;
    growthTarget = null;
    growthTravelTicks = 0;
    growthLastPosition = null;
    growthCooldown = Math.max(1, (int) Math.ceil(40 / SlimeStat.WORK_SPEED.value(this)));
    return true;
  }

  @Override
  public void tick() {
    super.tick();
    if (!level().isClientSide && growthCooldown > 0) growthCooldown--;
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.ABUNDANCE_SLIME.get());
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putInt("AbundanceCooldown", growthCooldown);
    tag.putBoolean("AbundanceResting", growthResting);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    growthCooldown = Math.max(0, Math.min(400, tag.getInt("AbundanceCooldown")));
    growthResting = tag.getBoolean("AbundanceResting");
    resetGrowth();
  }
}
