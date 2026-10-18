package io.github.meistermods.slimic.entity;

import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ITeleporter;
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
    var floor = BlockPos.containing(point).below();
    return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
        && level.noCollision(entity, box)
        && !level.containsAnyLiquid(box);
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
    if (slime instanceof HeraldSlime herald) herald.setMonitoring(false);
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
                && !pet.isRemoved()
                && !pet.isVehicle()
                && !pet.isPassenger()
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
      Entity result =
          slime.changeDimension(
              level,
              new ITeleporter() {
                @Override
                public Entity placeEntity(
                    Entity entity,
                    ServerLevel origin,
                    ServerLevel destination,
                    float yaw,
                    Function<Boolean, Entity> reposition) {
                  return reposition.apply(false);
                }

                @Override
                public PortalInfo getPortalInfo(
                    Entity entity,
                    ServerLevel destination,
                    Function<ServerLevel, PortalInfo> fallback) {
                  return valid.test(slime) && safe(destination, slime, target)
                      ? new PortalInfo(target, Vec3.ZERO, entity.getYRot(), entity.getXRot())
                      : null;
                }
              });
      if (!(result instanceof FriendlySlime companion)) return null;
      moved = companion;
    }
    pause(moved);
    moved.onReleased();
    pause(moved);
    CompanionRegistry.get(level.getServer()).observe(moved);
    return moved;
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
      player.changeDimension(
          level,
          new ITeleporter() {
            @Override
            public Entity placeEntity(
                Entity entity,
                ServerLevel origin,
                ServerLevel destination,
                float yaw,
                Function<Boolean, Entity> reposition) {
              return reposition.apply(false);
            }

            @Override
            public PortalInfo getPortalInfo(
                Entity entity,
                ServerLevel destination,
                Function<ServerLevel, PortalInfo> fallback) {
              return authorized.getAsBoolean() && safe(destination, player, target)
                  ? new PortalInfo(target, Vec3.ZERO, player.getYRot(), player.getXRot())
                  : null;
            }
          });
    }
    boolean moved = player.level() == level && player.position().distanceToSqr(target) < .01;
    if (moved) {
      player.fallDistance = 0;
      player.setDeltaMovement(Vec3.ZERO);
    }
    return moved;
  }
}
