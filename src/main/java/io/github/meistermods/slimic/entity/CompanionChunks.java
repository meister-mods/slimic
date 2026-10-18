package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.Slimic;
import java.util.ArrayList;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.world.ForgeChunkManager;

public final class CompanionChunks {
  private CompanionChunks() {}

  public static void register() {
    ForgeChunkManager.setForcedChunkLoadingCallback(
        Slimic.MOD_ID,
        (level, helper) -> {
          var registry = CompanionRegistry.get(level);
          for (var ticket : helper.getEntityTickets().entrySet()) {
            var entry = registry.find(ticket.getKey());
            if (entry == null
                || entry.location() != CompanionRegistry.Location.WORLD
                || !entry.pinned()
                || !entry.type().equals("slimic:ender_slime")
                || !entry.dimension().equals(level.dimension().location().toString())) {
              helper.removeAllTickets(ticket.getKey());
              continue;
            }
            long expected = new ChunkPos(entry.position()).toLong();
            for (long pos : new ArrayList<Long>(ticket.getValue().getFirst()))
              helper.removeTicket(ticket.getKey(), pos, false);
            for (long pos : new ArrayList<Long>(ticket.getValue().getSecond()))
              if (pos != expected) helper.removeTicket(ticket.getKey(), pos, true);
          }
          for (var pos : helper.getBlockTickets().keySet()) helper.removeAllTickets(pos);
        });
  }
}
