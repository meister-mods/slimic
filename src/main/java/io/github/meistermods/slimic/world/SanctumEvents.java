package io.github.meistermods.slimic.world;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Slimic.MOD_ID)
public final class SanctumEvents {
  private SanctumEvents() {}

  @SubscribeEvent
  public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
    io.github.meistermods.slimic.item.KingdomMapItem.clearSearches();
  }

  @SubscribeEvent
  public static void load(LevelEvent.Load event) {
    if (event.getLevel() instanceof ServerLevel level) SanctumPlacement.track(level);
  }

  @SubscribeEvent
  public static void unload(LevelEvent.Unload event) {
    if (event.getLevel() instanceof ServerLevel level
        && level.dimension().equals(SlimicWorld.DIMENSION)) SanctumPlacement.forget(level);
  }

  @SubscribeEvent
  public static void chunkLoad(net.minecraftforge.event.level.ChunkEvent.Load event) {
    if (event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)
      SanctumPlacement.chunk(chunk, true);
  }

  @SubscribeEvent
  public static void chunkUnload(net.minecraftforge.event.level.ChunkEvent.Unload event) {
    if (event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)
      SanctumPlacement.chunk(chunk, false);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void join(EntityJoinLevelEvent event) {
    if (event.getLevel() instanceof ServerLevel level
        && event.getEntity() instanceof FriendlySlime slime
        && level.getServer().overworld() != null) {
      var data = RoyalSynthesisData.get(level);
      if (data.unavailable(slime.getUUID())
          || slime.getPersistentData().hasUUID("SynthesisTransaction")
              && !data.mayRestore(slime.saveWithoutId(new net.minecraft.nbt.CompoundTag())))
        event.setCanceled(true);
    }
    if (event.getLevel() instanceof ServerLevel level
        && event.getEntity() instanceof AncientSlime ancient
        && level.getServer().overworld() != null
        && ancient.getPersistentData().hasUUID(SanctumEncounters.SITE)) {
      var site =
          SanctumSites.get(level).find(ancient.getPersistentData().getUUID(SanctumEncounters.SITE));
      if (site != null && site.defeated.contains(ancient.getUUID())) event.setCanceled(true);
    }
  }

  @SubscribeEvent
  public static void leave(EntityLeaveLevelEvent event) {
    if (!(event.getLevel() instanceof ServerLevel level)
        || !(event.getEntity() instanceof AncientSlime mob)
        || mob.getRemovalReason() != Entity.RemovalReason.KILLED
            && !(level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL
                && mob.getRemovalReason() == Entity.RemovalReason.DISCARDED)
        || !mob.getPersistentData().hasUUID(SanctumEncounters.SITE)) return;
    SanctumSites.get(level)
        .defeated(level, mob.getPersistentData().getUUID(SanctumEncounters.SITE), mob.getUUID());
  }
}
