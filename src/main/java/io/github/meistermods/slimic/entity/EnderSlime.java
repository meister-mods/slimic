package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.config.SlimicConfig;
import io.github.meistermods.slimic.item.SlimeTeleporterItem;
import io.github.meistermods.slimic.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.common.world.ForgeChunkManager;

public final class EnderSlime extends FriendlySlime {
  private static final EntityDataAccessor<Boolean> PINNED =
      SynchedEntityData.defineId(EnderSlime.class, EntityDataSerializers.BOOLEAN);
  private static final EntityDataAccessor<ItemStack> PEARLS =
      SynchedEntityData.defineId(EnderSlime.class, EntityDataSerializers.ITEM_STACK);
  private static final EntityDataAccessor<ItemStack> SEAL =
      SynchedEntityData.defineId(EnderSlime.class, EntityDataSerializers.ITEM_STACK);
  private Vec3 anchor;
  private ServerLevel ticketLevel;
  private ChunkPos ticketChunk;

  public EnderSlime(EntityType<? extends EnderSlime> type, Level level) {
    super(type, level);
  }

  @Override
  protected void defineSynchedData() {
    super.defineSynchedData();
    entityData.define(PINNED, false);
    entityData.define(PEARLS, ItemStack.EMPTY);
    entityData.define(SEAL, ItemStack.EMPTY);
  }

  public static boolean matchesEquipment(int slot, ItemStack stack) {
    return slot == 0
        ? stack.is(Items.ENDER_PEARL)
        : slot == 1 && stack.is(ModItems.DIMENSIONAL_ATTUNEMENT_SEAL.get());
  }

  public ItemStack getEquipment(int slot) {
    return slot == 0 ? entityData.get(PEARLS) : slot == 1 ? entityData.get(SEAL) : ItemStack.EMPTY;
  }

  public void setEquipment(int slot, ItemStack stack) {
    if (slot < 0 || slot > 1) return;
    entityData.set(slot == 0 ? PEARLS : SEAL, normalizedEquipment(slot, stack));
    equipmentChanged();
  }

  private static ItemStack normalizedEquipment(int slot, ItemStack stack) {
    return matchesEquipment(slot, stack)
        ? stack.copyWithCount(Math.min(slot == 0 ? 16 : 1, stack.getCount()))
        : ItemStack.EMPTY;
  }

  public void equipmentChanged() {
    entityData.set(PEARLS, getEquipment(0).copy());
    entityData.set(SEAL, getEquipment(1).copy());
    if (level() instanceof ServerLevel level
        && isAddedToWorld()
        && isAlive()
        && !isRemoved()
        && !isPendingSpawn()) {
      refreshTicket();
      CompanionRegistry.get(level).observe(this);
    }
  }

  public boolean isChunkLoading() {
    return isPinned() && !getEquipment(0).isEmpty();
  }

  public boolean allowsDimensionalTravel() {
    return getEquipment(1).is(ModItems.DIMENSIONAL_ATTUNEMENT_SEAL.get());
  }

  @Override
  public boolean canChangeDimensions() {
    return allowsDimensionalTravel() && super.canChangeDimensions();
  }

  @Override
  public Entity changeDimension(ServerLevel destination, ITeleporter teleporter) {
    if (destination != level() && !allowsDimensionalTravel()) return null;
    java.util.function.Consumer<net.minecraftforge.event.entity.EntityTravelToDimensionEvent>
        guard =
            event -> {
              if (event.getEntity() == this
                  && event.getDimension().equals(destination.dimension())
                  && !allowsDimensionalTravel()) event.setCanceled(true);
            };
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
        net.minecraftforge.eventbus.api.EventPriority.LOWEST,
        false,
        net.minecraftforge.event.entity.EntityTravelToDimensionEvent.class,
        guard);
    try {
      return super.changeDimension(destination, teleporter);
    } finally {
      net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(guard);
    }
  }

  public boolean isPinned() {
    return entityData.get(PINNED);
  }

  public void unpin() {
    entityData.set(PINNED, false);
    anchor = null;
    setNoGravity(false);
    releaseTicket();
    if (level() instanceof ServerLevel level)
      CompanionRegistry.get(level.getServer()).observe(this);
  }

  @Override
  public void setWaiting(boolean value) {
    if (!value && isPinned()) unpin();
    super.setWaiting(value);
  }

  @Override
  public boolean isFleeing() {
    return !isPinned() && super.isFleeing();
  }

  @Override
  protected boolean allowsFloating() {
    return !isPinned();
  }

  @Override
  public boolean isPushable() {
    return !isPinned() && super.isPushable();
  }

  @Override
  public void push(double x, double y, double z) {
    if (!isPinned()) super.push(x, y, z);
  }

  @Override
  public void travel(Vec3 input) {
    if (isPinned()) {
      setDeltaMovement(Vec3.ZERO);
      return;
    }
    super.travel(input);
  }

  public boolean hasAnchorCapacity() {
    return level() instanceof ServerLevel level
        && getOwnerUUID() != null
        && CompanionRegistry.get(level.getServer()).pinnedCount(getOwnerUUID(), getUUID())
            < SlimicConfig.ENDER_ANCHORS.get();
  }

  public boolean bind(Player player) {
    if (!(level() instanceof ServerLevel level)
        || !isAlive()
        || isRemoved()
        || isVehicle()
        || isPassenger()
        || player.level() != level()
        || player.distanceToSqr(this) > 64
        || !canManage(player)
        || player.isSpectator()
        || getStamina() < getMaxStamina()
        || player.getInventory().getFreeSlot() < 0) return false;
    var registry = CompanionRegistry.get(level.getServer());
    if (!isPinned() && !hasAnchorCapacity()) return false;
    var item = SlimeTeleporterItem.bound(this);
    boolean wasPinned = isPinned();
    entityData.set(PINNED, true);
    anchor = position();
    super.setWaiting(true);
    setNoGravity(true);
    registry.observe(this);
    refreshTicket();
    int slot = player.getInventory().getFreeSlot();
    if (slot < 0) {
      if (!wasPinned) unpin();
      return false;
    }
    player.getInventory().setItem(slot, item);
    player.getInventory().setChanged();
    spendStamina(getStamina());
    recordCompletedWork();
    registry.observe(this);
    return true;
  }

  public void completeTeleport(boolean consumePearl) {
    if (consumePearl) {
      var pearls = getEquipment(0).copy();
      pearls.shrink(1);
      setEquipment(0, pearls);
    }
    spendStamina(getStamina());
    recordCompletedWork();
    if (level() instanceof ServerLevel level)
      CompanionRegistry.get(level.getServer()).observe(this);
  }

  private void refreshTicket() {
    if (!(level() instanceof ServerLevel level)) return;
    if (!isAlive() || isRemoved() || !isChunkLoading() || getOwnerUUID() == null) {
      releaseTicket();
      return;
    }
    var chunk = chunkPosition();
    if (ticketLevel == level && chunk.equals(ticketChunk)) return;
    releaseTicket();
    ForgeChunkManager.forceChunk(level, Slimic.MOD_ID, getUUID(), chunk.x, chunk.z, true, true);
    ticketLevel = level;
    ticketChunk = chunk;
  }

  private void releaseTicket() {
    if (ticketLevel != null && ticketChunk != null)
      ForgeChunkManager.forceChunk(
          ticketLevel, Slimic.MOD_ID, getUUID(), ticketChunk.x, ticketChunk.z, false, true);
    if (level() instanceof ServerLevel level
        && (ticketLevel != level || !chunkPosition().equals(ticketChunk)))
      ForgeChunkManager.forceChunk(
          level, Slimic.MOD_ID, getUUID(), chunkPosition().x, chunkPosition().z, false, true);
    ticketLevel = null;
    ticketChunk = null;
  }

  @Override
  public void onReleased() {
    if (isPinned()) anchor = position();
    super.onReleased();
    refreshTicket();
  }

  @Override
  public void teleportTo(double x, double y, double z) {
    super.teleportTo(x, y, z);
    if (isPinned()) {
      anchor = position();
      refreshTicket();
    }
  }

  @Override
  public void tick() {
    super.tick();
    if (isPinned() && !level().isClientSide) {
      if (anchor == null) anchor = position();
      getNavigation().stop();
      setNoGravity(true);
      setDeltaMovement(Vec3.ZERO);
      fallDistance = 0;
      setPos(anchor.x, anchor.y, anchor.z);
      refreshTicket();
    }
    if (level().isClientSide && tickCount % 5 == 0)
      level()
          .addParticle(
              ParticleTypes.PORTAL, getRandomX(.6), getRandomY(), getRandomZ(.6), 0, .02, 0);
  }

  @Override
  public InteractionResult mobInteract(Player player, InteractionHand hand) {
    if (player.isShiftKeyDown() || isUtilityItem(player.getItemInHand(hand)))
      return super.mobInteract(player, hand);
    if (!canManage(player) || player.isSpectator()) return InteractionResult.FAIL;
    if (!level().isClientSide && !bind(player))
      player.displayClientMessage(
          Component.translatable("message.slimic.ender_bind_unavailable"), true);
    return InteractionResult.sidedSuccess(level().isClientSide);
  }

  @Override
  public void remove(RemovalReason reason) {
    if (reason.shouldDestroy() || reason == RemovalReason.CHANGED_DIMENSION) releaseTicket();
    super.remove(reason);
  }

  @Override
  protected void removeAfterChangingDimensions() {
    // Native portal travel sets the removal reason directly, bypassing remove().
    releaseTicket();
    super.removeAfterChangingDimensions();
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.ENDER_SLIME.get());
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putBoolean("EnderPinned", isPinned());
    tag.put("EnderPearls", getEquipment(0).save(new CompoundTag()));
    tag.put("EnderDimensionalSeal", getEquipment(1).save(new CompoundTag()));
    var point = anchor == null ? position() : anchor;
    tag.putDouble("EnderAnchorX", point.x);
    tag.putDouble("EnderAnchorY", point.y);
    tag.putDouble("EnderAnchorZ", point.z);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    // Restore before pinning; legacy anchors have no equipment and no implicit loading rights.
    entityData.set(PEARLS, normalizedEquipment(0, ItemStack.of(tag.getCompound("EnderPearls"))));
    entityData.set(
        SEAL, normalizedEquipment(1, ItemStack.of(tag.getCompound("EnderDimensionalSeal"))));
    entityData.set(PINNED, tag.getBoolean("EnderPinned"));
    anchor =
        new Vec3(
            tag.getDouble("EnderAnchorX"),
            tag.getDouble("EnderAnchorY"),
            tag.getDouble("EnderAnchorZ"));
    if (!Double.isFinite(anchor.x) || !Double.isFinite(anchor.y) || !Double.isFinite(anchor.z))
      anchor = position();
    if (isPinned()) {
      super.setWaiting(true);
      setNoGravity(true);
    }
    if (!tag.contains("EnderPinned")) setWaiting(true);
  }
}
