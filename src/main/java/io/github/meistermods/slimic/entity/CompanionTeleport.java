package io.github.meistermods.slimic.entity;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTeleportEvent;

public final class CompanionTeleport {
  private CompanionTeleport() {}

  public static boolean safe(ServerLevel level, Entity entity, Vec3 point) {
    if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z))
      return false;
    var box = entity.getBoundingBox().move(point.subtract(entity.position()));
    if (box.minY < level.getMinBuildHeight() + 1
        || box.maxY >= level.getMaxBuildHeight()
        || !level.getWorldBorder().isWithinBounds(box)) return false;
    for (var p :
        BlockPos.betweenClosed(
            BlockPos.containing(box.minX, box.minY - 1, box.minZ),
            BlockPos.containing(box.maxX, box.maxY, box.maxZ)))
      if (!level.hasChunkAt(p)) return false;
    // Inspect actual contact cells, including the surface under the feet. Touching the outer
    // boundary of a neighboring cell does not put that cell inside the body.
    for (var p :
        BlockPos.betweenClosed(
            BlockPos.containing(box.minX + .001, box.minY - .001, box.minZ + .001),
            BlockPos.containing(box.maxX - .001, box.maxY - .001, box.maxZ - .001)))
      if (harmful(level.getBlockState(p))) return false;
    var floor = BlockPos.containing(point).below();
    return !harmful(level.getBlockState(floor))
        && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
        && level.noCollision(entity, box)
        && !level.containsAnyLiquid(box);
  }

  private static boolean harmful(BlockState state) {
    return WalkNodeEvaluator.isBurningBlock(state)
        || state.is(Blocks.CACTUS)
        || state.is(Blocks.SWEET_BERRY_BUSH)
        || state.is(Blocks.WITHER_ROSE)
        || state.is(Blocks.POWDER_SNOW)
        || state.is(Blocks.POINTED_DRIPSTONE);
  }

  @Nullable
  public static Vec3 landing(
      ServerLevel level, Entity entity, BlockPos center, int start, int radius) {
    int width = radius * 2 + 1, total = width * width;
    for (int i = 0; i < Math.min(total, 256); i++) {
      int n = Math.floorMod(start + i, total);
      int ring = (int) Math.ceil((Math.sqrt(n + 1) - 1) / 2), edge = ring * 2;
      int distance = (ring * 2 + 1) * (ring * 2 + 1) - 1 - n;
      int x, z;
      if (ring == 0) {
        x = z = 0;
      } else if (distance < edge) {
        x = ring - distance;
        z = -ring;
      } else if (distance < edge * 2) {
        x = -ring;
        z = -ring + distance - edge;
      } else if (distance < edge * 3) {
        x = -ring + distance - edge * 2;
        z = ring;
      } else {
        x = ring;
        z = ring - (distance - edge * 3);
      }
      for (int dy : new int[] {0, 1, -1, 2, -2, 3, -3}) {
        var point = Vec3.atBottomCenterOf(center.offset(x * 2, dy, z * 2));
        if (safe(level, entity, point)) return point;
      }
    }
    return null;
  }

  public static void pause(FriendlySlime slime) {
    if (slime instanceof BagSlime worker) worker.setWorking(false);
    if (slime instanceof SignalingSlime signaling) signaling.setMonitoring(false);
    slime.setWaiting(true);
    slime.getNavigation().stop();
    slime.setDeltaMovement(Vec3.ZERO);
    slime.fallDistance = 0;
  }

  @Nullable
  public static FriendlySlime relocate(
      FriendlySlime slime, ServerLevel level, Vec3 point, Predicate<FriendlySlime> authorized) {
    var owner = slime.getOwnerUUID();
    Predicate<FriendlySlime> valid =
        pet ->
            pet.isAlive()
                && !pet.isPendingSpawn()
                && !pet.isRemoved()
                && !pet.isVehicle()
                && !pet.isPassenger()
                && (pet.level() == level
                    || !(pet instanceof EnderSlime ender)
                    || ender.allowsDimensionalTravel())
                && java.util.Objects.equals(owner, pet.getOwnerUUID())
                && authorized.test(pet);
    if (!valid.test(slime)) return null;
    if (!slime.isAlive() || slime.isRemoved() || slime.isVehicle() || slime.isPassenger())
      return null;
    var event = new EntityTeleportEvent(slime, point.x, point.y, point.z);
    if (MinecraftForge.EVENT_BUS.post(event)
        || !safe(level, slime, event.getTarget())
        || !valid.test(slime)) return null;
    var target = event.getTarget();
    FriendlySlime moved;
    if (slime.level() == level) {
      slime.teleportTo(target.x, target.y, target.z);
      moved = slime;
    } else {
      moved = transfer(slime, level, target, valid, authorized);
      if (moved == null) return null;
    }
    pause(moved);
    moved.onReleased();
    pause(moved);
    CompanionRegistry.get(level.getServer()).observe(moved);
    return moved;
  }

  @Nullable
  private static FriendlySlime transfer(
      FriendlySlime source,
      ServerLevel destination,
      Vec3 target,
      Predicate<FriendlySlime> validSource,
      Predicate<FriendlySlime> authorized) {
    if (!(source.level() instanceof ServerLevel origin)
        || !net.minecraftforge.common.ForgeHooks.onTravelToDimension(
            source, destination.dimension())
        || source.level() != origin
        || !validSource.test(source)
        || !safe(destination, source, target)
        || destination.getEntity(source.getUUID()) != null) return null;
    var snapshot = source.saveWithoutId(new CompoundTag());
    var created = source.getType().create(destination);
    if (!(created instanceof FriendlySlime copy)) return null;
    copy.restoreFrom(source);
    copy.moveTo(target.x, target.y, target.z, source.getYRot(), source.getXRot());
    copy.dropLeash(false, false);
    pause(copy);
    boolean committed = false;
    copy.setPendingSpawn(true);
    try {
      if (!destination.addFreshEntity(copy)
          || !copy.isAddedToWorld()
          || !copy.isAlive()
          || copy.isRemoved()
          || copy.level() != destination
          || !copy.getUUID().equals(source.getUUID())
          || !java.util.Objects.equals(copy.getOwnerUUID(), source.getOwnerUUID())
          || copy.isVehicle()
          || copy.isPassenger()
          || copy instanceof EnderSlime ender && !ender.allowsDimensionalTravel()
          || !authorized.test(copy)
          || copy.position().distanceToSqr(target) > .01
          || !safe(destination, copy, target)
          || source.level() != origin
          || !validSource.test(source)
          || !snapshot.equals(source.saveWithoutId(new CompoundTag()))) return null;
      // Native changeDimension removes the source even if a destination join event vetoes its
      // copy. Commit only after admission, authorization and the untouched source agree.
      source.remove(Entity.RemovalReason.CHANGED_DIMENSION);
      committed = true;
      copy.setPendingSpawn(false);
      source.dropLeash(true, true);
      origin.resetEmptyTime();
      destination.resetEmptyTime();
      return copy;
    } finally {
      if (!committed) {
        if (copy.isAddedToWorld() && copy.isRemoved()) copy.revive();
        copy.discard();
        if (source.isAlive() && source.level() instanceof ServerLevel current)
          CompanionRegistry.get(current).observe(source);
      }
      copy.setPendingSpawn(false);
    }
  }

  public static boolean player(
      ServerPlayer player, ServerLevel level, Vec3 point, BooleanSupplier authorized) {
    if (!authorized.getAsBoolean()) return false;
    if (!player.isAlive() || player.isSpectator() || player.isSleeping()) return false;
    var event = new EntityTeleportEvent(player, point.x, point.y, point.z);
    if (MinecraftForge.EVENT_BUS.post(event)
        || !safe(level, player, event.getTarget())
        || !player.isAlive()
        || player.isRemoved()
        || player.isSpectator()
        || !authorized.getAsBoolean()) return false;
    var target = event.getTarget();
    player.stopRiding();
    if (player.level() == level) {
      player.teleportTo(level, target.x, target.y, target.z, player.getYRot(), player.getXRot());
    } else {
      var origin = player.level();
      java.util.function.Consumer<net.minecraftforge.event.entity.EntityTravelToDimensionEvent>
          guard =
              travel -> {
                if (travel.getEntity() == player
                    && travel.getDimension().equals(level.dimension())
                    && (!player.isAlive()
                        || player.isRemoved()
                        || player.isSpectator()
                        || player.isSleeping()
                        || player.level() != origin
                        || !authorized.getAsBoolean()
                        || !safe(level, player, target))) travel.setCanceled(true);
              };
      MinecraftForge.EVENT_BUS.addListener(
          net.minecraftforge.eventbus.api.EventPriority.LOWEST,
          false,
          net.minecraftforge.event.entity.EntityTravelToDimensionEvent.class,
          guard);
      try {
        // Revalidate before the native API removes the player or sends a respawn packet.
        player.teleportTo(level, target.x, target.y, target.z, player.getYRot(), player.getXRot());
      } finally {
        MinecraftForge.EVENT_BUS.unregister(guard);
      }
    }
    boolean moved = player.level() == level && player.position().distanceToSqr(target) < .01;
    if (moved) {
      player.fallDistance = 0;
      player.setDeltaMovement(Vec3.ZERO);
    }
    return moved;
  }
}
