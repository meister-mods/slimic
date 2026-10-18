package io.github.meistermods.slimic.entity;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.registry.ModBlocks;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;

public final class SoggySlime extends FriendlySlime {
  private int trailCooldown;
  private int recoveryDelay;
  private boolean roaming;
  private BlockPos roamHome;

  public SoggySlime(EntityType<? extends SoggySlime> type, Level level) {
    super(type, level);
    setWaiting(true);
  }

  @Override
  protected void registerGoals() {
    super.registerGoals();
    goalSelector.addGoal(1, new RoamGoal());
  }

  @Override
  protected PathNavigation createNavigation(Level level) {
    return new RoamNavigation(this, level);
  }

  public boolean isRoaming() {
    return roaming && !isOrderedToSit();
  }

  public BlockPos getRoamHome() {
    return roamHome == null ? blockPosition() : roamHome;
  }

  public void setRoaming(boolean value) {
    roaming = value;
    roamHome = value ? blockPosition() : null;
    super.setWaiting(false);
  }

  @Override
  public void setWaiting(boolean value) {
    roaming = false;
    roamHome = null;
    super.setWaiting(value);
  }

  @Override
  public boolean isWorking() {
    return isRoaming();
  }

  @Override
  protected WorkStatus describeWork() {
    return WorkStatus.of(WorkStatus.Order.ROAM, WorkStatus.State.ACTIVE);
  }

  @Override
  public void onReleased() {
    super.onReleased();
    if (isRoaming()) roamHome = blockPosition();
  }

  @Override
  protected boolean canRecoverStamina() {
    return !isFleeing() && (recoveryDelay == 0 || getStamina() < 1);
  }

  @Override
  public void tick() {
    super.tick();
    if (level().isClientSide) {
      if (tickCount % 8 == 0)
        level()
            .addParticle(
                ParticleTypes.DRIPPING_WATER,
                getRandomX(.5),
                getY() + .15,
                getRandomZ(.5),
                0,
                0,
                0);
      return;
    }
    if (trailCooldown > 0) trailCooldown--;
    if (recoveryDelay > 0) recoveryDelay--;
    if (!(level() instanceof ServerLevel server)
        || !isTame()
        || getOwnerUUID() == null
        || !isAlive()
        || isOrderedToSit()
        || isFleeing()
        || !onGround()
        || isInWaterOrBubble()
        || trailCooldown > 0
        || getStamina() < 1
        || !ForgeEventFactory.getMobGriefingEvent(server, this)) return;
    var pos = blockPosition();
    var state = ModBlocks.SLIME_TRAIL.get().defaultBlockState();
    if (!server.hasChunkAt(pos)
        || !server.hasChunkAt(pos.below())
        || !server.getBlockState(pos).isAir()
        || !state.canSurvive(server, pos)
        || !server.getWorldBorder().isWithinBounds(pos)) return;
    var player = FakePlayerFactory.get(server, new GameProfile(getOwnerUUID(), "SlimeTrailWorker"));
    player.moveTo(position());
    if (!server.mayInteract(player, pos)) return;
    var snapshot = BlockSnapshot.create(server.dimension(), server, pos);
    if (!server.setBlock(pos, state, 3)) return;
    if (ForgeEventFactory.onBlockPlace(player, snapshot, Direction.UP)
        || isOrderedToSit()
        || !isTame()
        || !isAlive()
        || isRemoved()
        || !canManage(player)) {
      snapshot.restore(true, false);
      trailCooldown = 20;
      return;
    }
    if (server.getBlockState(pos).is(ModBlocks.SLIME_TRAIL.get())) {
      spendStamina(1);
      recordCompletedWork();
      recoveryDelay = 20;
      trailCooldown = Math.max(1, (int) Math.ceil(10 / SlimeStat.WORK_SPEED.value(this)));
    }
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.SOGGY_SLIME.get());
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putInt("SoggyTrailCooldown", trailCooldown);
    tag.putInt("SoggyRecoveryDelay", recoveryDelay);
    tag.putBoolean("SoggyRoaming", isRoaming());
    if (isRoaming()) tag.putLong("SoggyRoamHome", getRoamHome().asLong());
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    if (!tag.contains("SoggyTrailCooldown")) setWaiting(true);
    trailCooldown = Math.max(0, Math.min(100, tag.getInt("SoggyTrailCooldown")));
    recoveryDelay = Math.max(0, Math.min(20, tag.getInt("SoggyRecoveryDelay")));
    roaming = tag.getBoolean("SoggyRoaming") && !isOrderedToSit();
    roamHome =
        roaming && tag.contains("SoggyRoamHome") ? BlockPos.of(tag.getLong("SoggyRoamHome")) : null;
  }

  private final class RoamGoal extends Goal {
    private int pause;

    RoamGoal() {
      setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
      return isRoaming() && isTame() && getOwnerUUID() != null;
    }

    @Override
    public boolean canContinueToUse() {
      return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
      return true;
    }

    @Override
    public void start() {
      pause = 0;
    }

    @Override
    public void stop() {
      getNavigation().stop();
    }

    @Override
    public void tick() {
      if (!canUse()) {
        stop();
        return;
      }
      if (!getNavigation().isDone() || (!onGround() && !isInWater())) return;
      if (pause-- > 0 || !(level() instanceof ServerLevel server)) return;
      pause = 20 + random.nextInt(21);
      if (roamHome == null) roamHome = blockPosition();
      double range = SlimeStat.DETECTION_RANGE.value(SoggySlime.this);
      for (int attempt = 0; attempt < 16; attempt++) {
        int dx = random.nextInt(17) - 8, dz = random.nextInt(17) - 8;
        if (getRoamHome().distSqr(blockPosition()) > range * range) {
          var homeward = Vec3.atCenterOf(getRoamHome()).subtract(position()).normalize().scale(5);
          dx = (int) Math.round(homeward.x);
          dz = (int) Math.round(homeward.z);
        }
        for (int dy = 3; dy >= -3; dy--) {
          var feet = blockPosition().offset(dx, dy, dz);
          if ((feet.distSqr(getRoamHome()) > range * range
                  && feet.distSqr(getRoamHome()) >= blockPosition().distSqr(getRoamHome()))
              || !server.hasChunkAt(feet)
              || !server.hasChunkAt(feet.below())
              || !server.getWorldBorder().isWithinBounds(feet)) continue;
          var shape = server.getBlockState(feet).getCollisionShape(server, feet);
          double lift = shape.isEmpty() ? 0 : shape.max(Direction.Axis.Y);
          if (lift > .5) continue;
          double offset = Math.floor(getBbWidth() + 1) * .5;
          var stand = Vec3.atLowerCornerOf(feet).add(offset, lift, offset);
          var body = getBoundingBox().move(stand.subtract(position()));
          if (!server.noCollision(SoggySlime.this, body)
              || !server
                  .getBlockCollisions(SoggySlime.this, body.move(0, -.05, 0))
                  .iterator()
                  .hasNext()) continue;
          var path = getNavigation().createPath(feet, 0);
          if (path != null && path.canReach() && getNavigation().moveTo(path, 1)) return;
        }
      }
    }
  }

  private static final class RoamNavigation extends GroundPathNavigation {
    private PathFinder loadedFinder;

    RoamNavigation(SoggySlime slime, Level level) {
      super(slime, level);
    }

    @Override
    public void recomputePath() {
      if (((SoggySlime) mob).isRoaming()) stop();
      else super.recomputePath();
    }

    @Override
    public Path createPath(BlockPos target, int accuracy) {
      if (((SoggySlime) mob).isRoaming() && !level.hasChunkAt(target)) return null;
      return super.createPath(target, accuracy);
    }

    @Override
    protected PathFinder createPathFinder(int nodes) {
      loadedFinder = super.createPathFinder(nodes);
      return loadedFinder;
    }

    @Override
    protected Path createPath(
        Set<BlockPos> targets, int padding, boolean above, int accuracy, float distance) {
      if (!((SoggySlime) mob).isRoaming())
        return super.createPath(targets, padding, above, accuracy, distance);
      if (targets.isEmpty() || !canUpdatePath() || mob.isPassenger()) return null;
      int minX = mob.getBlockX(), maxX = minX, minZ = mob.getBlockZ(), maxZ = minZ;
      for (var pos : targets) {
        minX = Math.min(minX, pos.getX());
        maxX = Math.max(maxX, pos.getX());
        minZ = Math.min(minZ, pos.getZ());
        maxZ = Math.max(maxZ, pos.getZ());
      }
      minX -= 2;
      minZ -= 2;
      maxX += 2;
      maxZ += 2;
      for (int x = minX >> 4; x <= maxX >> 4; x++)
        for (int z = minZ >> 4; z <= maxZ >> 4; z++) if (!level.hasChunk(x, z)) return null;
      var region =
          new PathNavigationRegion(
              level,
              new BlockPos(minX, mob.getBlockY() - 8, minZ),
              new BlockPos(maxX, mob.getBlockY() + 8, maxZ));
      return loadedFinder.findPath(region, mob, targets, Math.max(16, distance), accuracy, 1);
    }
  }
}
