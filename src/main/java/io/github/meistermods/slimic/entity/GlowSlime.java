package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.block.*;
import io.github.meistermods.slimic.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class GlowSlime extends FriendlySlime {
  private static final EntityDataAccessor<Boolean> ACTIVE =
      SynchedEntityData.defineId(GlowSlime.class, EntityDataSerializers.BOOLEAN);
  private BlockPos lightPosition;
  private int burstCooldown;

  public GlowSlime(EntityType<? extends GlowSlime> type, Level level) {
    super(type, level);
  }

  @Override
  protected void defineSynchedData() {
    super.defineSynchedData();
    entityData.define(ACTIVE, true);
  }

  public boolean isActive() {
    return entityData.get(ACTIVE);
  }

  public void setActive(boolean value) {
    entityData.set(ACTIVE, value);
    if (!value) releaseLight();
  }

  private void releaseLight() {
    if (lightPosition != null
        && level() instanceof ServerLevel level
        && level.hasChunkAt(lightPosition)
        && level.getBlockEntity(lightPosition) instanceof SlimeLightBlockEntity light)
      light.release(getUUID());
    lightPosition = null;
  }

  @Override
  public void tick() {
    super.tick();
    if (level() instanceof ServerLevel level) {
      if (burstCooldown > 0) burstCooldown--;
      if (isAlive() && isActive() && tickCount % 2 == 0) {
        var pos = BlockPos.containing(getX(), getEyeY(), getZ());
        if (!pos.equals(lightPosition)) releaseLight();
        if (SlimeLightBlock.illuminate(level, pos, getUUID(), 10, false)) lightPosition = pos;
      } else if (!isActive()) releaseLight();
    }
  }

  public boolean releaseFireflies() {
    if (!(level() instanceof ServerLevel level)
        || !isAlive()
        || isRemoved()
        || burstCooldown > 0
        || !spendStamina(10)) return false;
    int placed = 0;
    for (int i = 0; i < 12 && placed < 3; i++) {
      var pos =
          BlockPos.containing(
              getX() + random.nextInt(5) - 2,
              getEyeY() + random.nextInt(2),
              getZ() + random.nextInt(5) - 2);
      if (SlimeLightBlock.illuminate(level, pos, getUUID(), 120, true)) {
        level.sendParticles(
            ModParticles.FIREFLY.get(),
            pos.getX() + .5,
            pos.getY() + .5,
            pos.getZ() + .5,
            1,
            0,
            0,
            0,
            0);
        placed++;
      }
    }
    if (placed == 0) {
      refundStamina(10);
      return false;
    }
    burstCooldown = 20;
    recordCompletedWork();
    return true;
  }

  @Override
  public InteractionResult mobInteract(Player player, InteractionHand hand) {
    if (player.isShiftKeyDown() || isUtilityItem(player.getItemInHand(hand)))
      return super.mobInteract(player, hand);
    if (!canManage(player) || player.isSpectator() || !isAlive() || isRemoved())
      return InteractionResult.FAIL;
    if (!level().isClientSide && !releaseFireflies())
      player.displayClientMessage(Component.translatable("message.slimic.glow_unavailable"), true);
    return InteractionResult.sidedSuccess(level().isClientSide);
  }

  @Override
  public void remove(RemovalReason reason) {
    releaseLight();
    super.remove(reason);
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.GLOW_SLIME.get());
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putBoolean("GlowActive", isActive());
    tag.putInt("GlowCooldown", burstCooldown);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    setActive(!tag.contains("GlowActive") || tag.getBoolean("GlowActive"));
    burstCooldown = Math.max(0, Math.min(20, tag.getInt("GlowCooldown")));
    if (!tag.contains("GlowActive")) setWaiting(true);
  }
}
