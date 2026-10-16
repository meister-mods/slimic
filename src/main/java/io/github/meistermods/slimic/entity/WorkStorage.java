package io.github.meistermods.slimic.entity;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.block.SlimeChestBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.items.IItemHandler;

/** Loaded storage candidates. Every use rechecks ownership, range and availability. */
final class WorkStorage {
  record Target(BlockPos chest, UUID warehouse) {
    WarehouseSlime entity(ServerLevel level) {
      return warehouse != null
              && level.getEntity(warehouse) instanceof WarehouseSlime slime
              && slime.isAlive()
              && !slime.isRemoved()
              && level.hasChunkAt(slime.blockPosition())
          ? slime
          : null;
    }

    IItemHandler inventory(FriendlySlime actor, BlockPos origin) {
      if (!(actor.level() instanceof ServerLevel level) || actor.getOwnerUUID() == null)
        return null;
      double range = SlimeStat.DETECTION_RANGE.value(actor);
      if (warehouse != null) {
        WarehouseSlime slime = entity(level);
        return slime != null
                && slime != actor
                && actor.getOwnerUUID().equals(slime.getOwnerUUID())
                && slime.position().distanceToSqr(Vec3.atCenterOf(origin)) <= range * range
            ? slime.getBag()
            : null;
      }
      if (chest == null || origin.distSqr(chest) > range * range || !level.hasChunkAt(chest))
        return null;
      return level.getBlockEntity(chest) instanceof SlimeChestBlockEntity storage
              && !storage.isRemoved()
              && storage.canOpen(
                  FakePlayerFactory.get(
                      level, new GameProfile(actor.getOwnerUUID(), "SlimeWorker")))
          ? storage.inventory()
          : null;
    }

    Vec3 position(ServerLevel level) {
      var entity = entity(level);
      return warehouse != null
          ? entity == null ? null : entity.getBoundingBox().getCenter()
          : Vec3.atCenterOf(chest);
    }
  }

  static List<Target> find(FriendlySlime actor, BlockPos origin) {
    if (!(actor.level() instanceof ServerLevel level)) return List.of();
    int radius = (int) Math.ceil(SlimeStat.DETECTION_RANGE.value(actor));
    List<Target> result = new ArrayList<>();
    for (int x = (origin.getX() - radius) >> 4; x <= (origin.getX() + radius) >> 4; x++)
      for (int z = (origin.getZ() - radius) >> 4; z <= (origin.getZ() + radius) >> 4; z++) {
        var snapshot = WorkSearch.inChunk(level, new ChunkPos(x, z));
        for (var worker : snapshot.workers())
          if (worker instanceof WarehouseSlime) {
            var candidate = new Target(null, worker.getUUID());
            if (candidate.inventory(actor, origin) != null && !result.contains(candidate))
              result.add(candidate);
          }
        for (var pos : snapshot.chests()) {
          var candidate = new Target(pos, null);
          if (candidate.inventory(actor, origin) != null) result.add(candidate);
        }
      }
    result.sort(
        Comparator.<Target>comparingInt(target -> target.warehouse() != null ? 0 : 1)
            .thenComparingDouble(target -> target.position(level).distanceToSqr(actor.position())));
    return result;
  }

  private WorkStorage() {}
}
