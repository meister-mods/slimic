package io.github.meistermods.slimic.world;

import io.github.meistermods.slimic.Slimic;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Slimic.MOD_ID)
public final class ColonyEvents {
  private static final java.util.Map<java.util.UUID, Integer> MISSING = new java.util.HashMap<>();

  private ColonyEvents() {}

  @SubscribeEvent
  public static void joined(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
    if (event.loadedFromDisk()
        && event.getLevel() instanceof net.minecraft.server.level.ServerLevel level
        && event.getEntity() instanceof io.github.meistermods.slimic.entity.FriendlySlime slime)
      ColonyBondingData.get(level).diskAdmission(slime);
  }

  @SubscribeEvent
  public static void left(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
    if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level
        && event.getEntity() instanceof io.github.meistermods.slimic.entity.FriendlySlime slime
        && (slime.getRemovalReason()
                == net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK
            || slime.getRemovalReason()
                == net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_WITH_PLAYER))
      ColonyBondingData.get(level).leaving(level, slime);
  }

  @SubscribeEvent
  public static void chunk(net.minecraftforge.event.level.ChunkEvent.Load event) {
    if (event.isNewChunk()
        || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
    for (var position : event.getChunk().getBlockEntitiesPos())
      if (event.getChunk().getBlockEntity(position)
          instanceof io.github.meistermods.slimic.block.BrokenSlimeBlockEntity broken)
        ColonyBondingData.get(level).storedDiskProof(level, broken.getSlime());
  }

  @SubscribeEvent
  public static void tick(TickEvent.ServerTickEvent event) {
    if (event.phase != TickEvent.Phase.END) return;
    var server = event.getServer();
    if (server.getTickCount() % 100 == 0)
      ColonyBondingData.get(server.overworld()).recoverInterrupted(server);
    var data = AssemblySites.get(server.overworld());
    for (var site : data.sites()) {
      var dimension = net.minecraft.resources.ResourceLocation.tryParse(site.dimension);
      if (dimension == null) continue;
      var level =
          server.getLevel(
              net.minecraft.resources.ResourceKey.create(
                  net.minecraft.core.registries.Registries.DIMENSION, dimension));
      if (level == null || !level.isPositionEntityTicking(site.anchor)) continue;
      if (site.state == AssemblySites.State.AWAKENING)
        io.github.meistermods.slimic.block.AssemblyCoreBlockEntity.tickEncounter(level, site);
      if (site.state != AssemblySites.State.ACTIVE || site.boss == null) {
        MISSING.remove(site.id);
        continue;
      }
      if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
        data.reset(level, site);
        continue;
      }
      if (level.getEntity(site.boss) != null) {
        MISSING.remove(site.id);
        continue;
      }
      // Wait for entity storage across the entire possible arena footprint. No ticket or
      // chunk request is issued; an unloaded boss is never treated as proof of death.
      boolean ready = true;
      for (int dx = -4; dx <= 4; dx++)
        for (int dz = -4; dz <= 4; dz++)
          if (!level.areEntitiesLoaded(
              net.minecraft.world.level.ChunkPos.asLong(
                  (site.anchor.getX() >> 4) + dx, (site.anchor.getZ() >> 4) + dz))) ready = false;
      if (!ready || site.body.isEmpty()) {
        MISSING.remove(site.id);
        continue;
      }
      if (MISSING.merge(site.id, 1, Integer::sum) < 200) continue;
      var boss =
          io.github.meistermods.slimic.registry.ModEntities.AIMLESS_COLONY.get().create(level);
      if (boss == null) continue;
      boss.load(site.body.copy());
      boss.setUUID(site.boss);
      boss.bind(site);
      if (level.addFreshEntity(boss)) MISSING.remove(site.id);
    }
  }

  @SubscribeEvent
  public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
    MISSING.clear();
  }
}
