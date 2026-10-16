package io.github.meistermods.slimic.entity;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.registry.ModBlocks;
import io.github.meistermods.slimic.registry.ModItems;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;

public final class SoggySlime extends FriendlySlime {
  private int trailCooldown;
  private int recoveryDelay;

  public SoggySlime(EntityType<? extends SoggySlime> type, Level level) {
    super(type, level);
    setWaiting(true);
  }

  @Override
  protected boolean canRecoverStamina() {
    return super.canRecoverStamina() && (recoveryDelay == 0 || getStamina() < 1);
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
    if (ForgeEventFactory.onBlockPlace(player, snapshot, Direction.UP)) {
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
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    if (!tag.contains("SoggyTrailCooldown")) setWaiting(true);
    trailCooldown = Math.max(0, Math.min(100, tag.getInt("SoggyTrailCooldown")));
    recoveryDelay = Math.max(0, Math.min(20, tag.getInt("SoggyRecoveryDelay")));
  }
}
