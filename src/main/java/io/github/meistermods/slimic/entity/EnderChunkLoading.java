package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.Slimic;
import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;

/** Distinguishes an anchor's persistent ticket from independent destination loaders. */
public final class EnderChunkLoading {
  private EnderChunkLoading() {}

  public static boolean independentlyLoaded(ServerLevel level, ChunkPos destination, UUID anchor) {
    // Query the server's actual tracking range, rather than estimating distance in blocks.
    for (var player : level.getChunkSource().chunkMap.getPlayers(destination, false))
      if (!player.isSpectator()
          || level.getGameRules().getBoolean(GameRules.RULE_SPECTATORSGENERATECHUNKS)) return true;
    // Vanilla keeps the Overworld's starting region accessible with a radius-11 START ticket.
    if (level == level.getServer().overworld()
        && covers(new ChunkPos(level.getSharedSpawnPos()), destination, 11)) return true;
    for (long chunk : level.getForcedChunks())
      if (covers(new ChunkPos(chunk), destination, 2)) return true;

    var data = level.getDataStorage().get(ForcedChunksSavedData::load, "chunks");
    if (data == null) return false;
    // Forge exposes ticket maps but keeps owner identity private. Its public serialization
    // retains both mod and UUID, allowing us to exclude exactly this anchor without reflection.
    var saved = new CompoundTag();
    net.minecraftforge.common.world.ForgeChunkManager.writeForgeForcedChunks(
        saved, data.getBlockForcedChunks(), data.getEntityForcedChunks());
    for (var element : saved.getList("ForgeForced", Tag.TAG_COMPOUND)) {
      var mod = (CompoundTag) element;
      for (var entry : mod.getList("ModForced", Tag.TAG_COMPOUND)) {
        var ticket = (CompoundTag) entry;
        if (!covers(new ChunkPos(ticket.getLong("Chunk")), destination, 2)) continue;
        if (!ticket.getList("Blocks", Tag.TAG_COMPOUND).isEmpty()
            || !ticket.getList("TickingBlocks", Tag.TAG_COMPOUND).isEmpty()) return true;
        for (var key : new String[] {"Entities", "TickingEntities"})
          for (var identity : ticket.getList(key, Tag.TAG_INT_ARRAY))
            if (!Slimic.MOD_ID.equals(mod.getString("Mod"))
                || !anchor.equals(NbtUtils.loadUUID(identity))) return true;
      }
    }
    return false;
  }

  private static boolean covers(ChunkPos source, ChunkPos target, int radius) {
    return Math.abs((long) source.x - target.x) <= radius
        && Math.abs((long) source.z - target.z) <= radius;
  }
}
