package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.menu.CommandMenu;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.network.NetworkHooks;

/**
 * Owner-issued rallies process one recorded companion at a time, with one temporary source ticket.
 */
public final class CommandSlime extends FriendlySlime {
  private final ArrayDeque<UUID> rally = new ArrayDeque<>();
  private UUID rallyActor;
  private String rallyTag = "";
  private int cooldown, waitingTicks, cursor, gathered, requested;
  private ServerLevel sourceLevel;
  private ChunkPos sourceChunk;

  public CommandSlime(EntityType<? extends CommandSlime> type, Level level) {
    super(type, level);
  }

  public int gathered() {
    return gathered;
  }

  public int requested() {
    return requested;
  }

  public boolean isGathering() {
    return !rally.isEmpty();
  }

  public int gatherCooldown() {
    return cooldown;
  }

  public boolean gather(Player actor, String tag) {
    if (!(level() instanceof ServerLevel level)
        || !isAlive()
        || isRemoved()
        || !canManage(actor)
        || actor.isSpectator()
        || cooldown > 0
        || isGathering()) return false;
    String key;
    try {
      key = CompanionRegistry.normalizeTag(tag);
    } catch (IllegalArgumentException invalid) {
      return false;
    }
    var records =
        CompanionRegistry.get(level.getServer()).owned(getOwnerUUID()).stream()
            .filter(
                e ->
                    e.location() == CompanionRegistry.Location.WORLD
                        && e.health() > 0
                        && e.tag().equals(key)
                        && !e.id().equals(getUUID()))
            .toList();
    if (records.isEmpty() || !spendStamina(25)) return false;
    rallyActor = actor.getUUID();
    rallyTag = key;
    gathered = 0;
    requested = records.size();
    cursor = 0;
    for (var entry : records) rally.add(entry.id());
    cooldown = 100;
    return true;
  }

  @Override
  public void setWaiting(boolean waiting) {
    if (waiting) {
      rally.clear();
      releaseSource();
    }
    super.setWaiting(waiting);
  }

  private void releaseSource() {
    if (sourceLevel != null && sourceChunk != null)
      ForgeChunkManager.forceChunk(
          sourceLevel, Slimic.MOD_ID, getUUID(), sourceChunk.x, sourceChunk.z, false, true);
    sourceLevel = null;
    sourceChunk = null;
    waitingTicks = 0;
  }

  private void next() {
    rally.poll();
    releaseSource();
  }

  private void advanceRally(ServerLevel level) {
    var registry = CompanionRegistry.get(level.getServer());
    if (!registry.owns(getOwnerUUID(), rallyActor)) {
      rally.clear();
      releaseSource();
      return;
    }
    var entry = registry.find(rally.peek());
    if (entry == null
        || !entry.owner().equals(getOwnerUUID())
        || entry.location() != CompanionRegistry.Location.WORLD
        || !entry.tag().equals(rallyTag)) {
      next();
      return;
    }
    var id = ResourceLocation.tryParse(entry.dimension());
    var source =
        id == null
            ? null
            : level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
    if (source == null) {
      next();
      return;
    }
    var found = source.getEntity(entry.id());
    if (found == null) {
      if (sourceLevel == null) {
        sourceLevel = source;
        sourceChunk = new ChunkPos(entry.position());
        ForgeChunkManager.forceChunk(
            source, Slimic.MOD_ID, getUUID(), sourceChunk.x, sourceChunk.z, true, true);
      }
      if (++waitingTicks >= 100) next();
      return;
    }
    if (found instanceof FriendlySlime pet
        && pet.isAlive()
        && !pet.isRemoved()
        && getOwnerUUID().equals(pet.getOwnerUUID())
        && pet.getCompanionTag().equals(rallyTag)
        && !pet.isVehicle()
        && !pet.isPassenger()) {
      var landing = CompanionTeleport.landing(level, pet, blockPosition(), cursor, 10);
      cursor = (cursor + 1) % 441;
      if (landing != null
          && CompanionTeleport.relocate(
                  pet,
                  level,
                  landing,
                  candidate ->
                      registry.owns(getOwnerUUID(), rallyActor)
                          && getOwnerUUID().equals(candidate.getOwnerUUID())
                          && candidate.getCompanionTag().equals(rallyTag))
              != null) {
        gathered++;
        recordCompletedWork();
      }
    }
    next();
  }

  @Override
  public void tick() {
    super.tick();
    if (level() instanceof ServerLevel level) {
      if (cooldown > 0) cooldown--;
      if (isAlive() && isGathering()) advanceRally(level);
    }
  }

  public InteractionResult open(Player player) {
    if (!canManage(player)
        || player.isSpectator()
        || !isAlive()
        || isRemoved()
        || player.distanceToSqr(this) > 64) return InteractionResult.FAIL;
    if (player instanceof ServerPlayer server)
      NetworkHooks.openScreen(
          server,
          new SimpleMenuProvider(
              (id, inventory, viewer) -> new CommandMenu(id, inventory, this), getDisplayName()),
          buffer -> buffer.writeVarInt(getId()));
    return InteractionResult.sidedSuccess(level().isClientSide);
  }

  @Override
  public InteractionResult mobInteract(Player player, InteractionHand hand) {
    if (player.isShiftKeyDown() || isUtilityItem(player.getItemInHand(hand)))
      return super.mobInteract(player, hand);
    return open(player);
  }

  @Override
  public void remove(RemovalReason reason) {
    rally.clear();
    releaseSource();
    super.remove(reason);
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.COMMAND_SLIME.get());
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putInt("CommanderCooldown", cooldown);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    cooldown = Math.max(0, Math.min(100, tag.getInt("CommanderCooldown")));
    if (!tag.contains("CommanderCooldown")) setWaiting(true);
  }
}
