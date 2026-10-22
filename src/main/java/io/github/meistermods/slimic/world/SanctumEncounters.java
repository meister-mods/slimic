package io.github.meistermods.slimic.world;

import io.github.meistermods.slimic.entity.AncientSlime;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;

/** Existing ancient AI supplies alliances, alarms, home bounds, melee and ordinary Forge hooks. */
public final class SanctumEncounters {
  public static final String SITE = "SanctumSite", ZONE = "SanctumZone", ROLE = "SanctumRole";
  private static final int[] COUNTS = {1, 2, 3, 2, 1};
  private static final Set<UUID> ADMITTING = new HashSet<>();

  private SanctumEncounters() {}

  private static boolean finalizeEncounter(
      ServerLevel level, AncientSlime mob, BlockPos home, UUID id) {
    net.minecraftforge.event.ForgeEventFactory.onFinalizeSpawn(
        mob,
        level,
        level.getCurrentDifficultyAt(home),
        net.minecraft.world.entity.MobSpawnType.STRUCTURE,
        null,
        null);
    return !mob.isSpawnCancelled()
        && mob.isAlive()
        && !mob.isRemoved()
        && mob.level() == level
        && id.equals(mob.getUUID())
        && mob.blockPosition().equals(home)
        && level.noCollision(mob);
  }

  private static boolean admit(ServerLevel level, AncientSlime mob, UUID id) {
    boolean accepted =
        level.addFreshEntity(mob)
            && mob.isAlive()
            && mob.isAddedToWorld()
            && mob.level() == level
            && id.equals(mob.getUUID());
    if (!accepted) {
      if (mob.isAddedToWorld() && mob.isRemoved()) mob.revive();
      mob.discard();
    }
    return accepted;
  }

  public static void activate(ServerLevel level, SanctumSites.Site site, int zone) {
    if (level.getDifficulty() == Difficulty.PEACEFUL || site.cleared) return;
    var data = SanctumSites.get(level);
    for (int i = 0; i < COUNTS[zone] + (zone == 4 && site.reinforcement ? 1 : 0); i++) {
      UUID id = site.encounter(zone, i);
      if (site.defeated.contains(id) || !ADMITTING.add(id)) continue;
      try {
        int z = -40 + zone * 16 + 8, x = i == 0 ? 0 : i == 1 ? -8 : 8;
        int y = z < 20 ? 0 : -Math.min(4, (z - 20) / 2);
        BlockPos home = site.at(x, y, z);
        if (!level.hasChunkAt(home) || !level.isPositionEntityTicking(home)) continue;
        if (level.getEntity(id) instanceof AncientSlime existing
            && site.id.equals(
                existing.getPersistentData().hasUUID(SITE)
                    ? existing.getPersistentData().getUUID(SITE)
                    : null)) {
          if (site.spawned.add(id)) data.setDirty();
          site.recoverableEncounters.remove(id);
          site.recoverySince.remove(id);
          continue;
        }
        if (site.spawned.contains(id)) {
          if (level.getEntity(id) != null) {
            site.recoverableEncounters.remove(id);
            site.recoverySince.remove(id);
            continue;
          }
          if (!site.recoverableEncounters.contains(id)) continue;
          boolean complete = true;
          for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
              var nearby = home.offset(dx * 16, 0, dz * 16);
              if (!level.hasChunkAt(nearby) || !level.isPositionEntityTicking(nearby))
                complete = false;
            }
          if (!complete) {
            site.recoverySince.remove(id);
            continue;
          }
          long since = site.recoverySince.computeIfAbsent(id, unused -> level.getGameTime());
          if (level.getGameTime() - since < 200) continue;
        }
        int role =
            zone == 4 ? i == 0 ? 3 : 0 : zone == 2 && i == 0 ? 2 : zone == 1 || zone == 3 ? 0 : 1;
        var type =
            role >= 2
                ? ModEntities.ANCIENT_COMMANDER_SLIME.get()
                : ModEntities.ANCIENT_GUARD_SLIME.get();
        var mob = type.create(level);
        if (mob == null) continue;
        mob.setUUID(id);
        mob.bindToRuin(home, site.id, null, null, role >= 2 ? 3 : 2);
        mob.moveTo(home.getX() + .5, home.getY(), home.getZ() + .5, 0, 0);
        mob.getPersistentData().putUUID(SITE, site.id);
        mob.getPersistentData().putInt(ZONE, zone);
        mob.getPersistentData().putInt(ROLE, role);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(role == 3 ? 160 : role == 2 ? 96 : 64);
        mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(role == 3 ? 12 : 8);
        mob.getAttribute(Attributes.ARMOR).setBaseValue(role == 3 ? 12 : 8);
        mob.setHealth(mob.getMaxHealth());
        mob.setItemSlot(
            EquipmentSlot.MAINHAND,
            new ItemStack(role == 3 ? Items.DIAMOND_SWORD : Items.IRON_SWORD));
        if (role == 0) mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        mob.setCustomName(
            net.minecraft.network.chat.Component.translatable(
                "entity.slimic.sanctum_role_" + role));
        if (!finalizeEncounter(level, mob, home, id)) continue;
        if (admit(level, mob, id)) {
          site.spawned.add(id);
          site.recoverableEncounters.remove(id);
          site.recoverySince.remove(id);
          site.activatedAt = level.getGameTime();
          data.setDirty();
        }
      } finally {
        ADMITTING.remove(id);
      }
    }
  }

  public static boolean cleared(ServerLevel level, SanctumSites.Site site, int zone) {
    if (level.getDifficulty() == Difficulty.PEACEFUL) return true;
    for (int i = 0; i < COUNTS[zone]; i++) {
      UUID id = site.encounter(zone, i);
      if (!site.spawned.contains(id) || !site.defeated.contains(id)) return false;
      if (level.getEntity(id) instanceof AncientSlime mob && mob.isAlive()) return false;
    }
    return zone != 4 || !site.reinforcement || site.defeated.contains(site.encounter(4, 1));
  }

  public static void tick(AncientSlime mob) {
    var tag = mob.getPersistentData();
    if (!(mob.level() instanceof ServerLevel level) || !tag.hasUUID(SITE)) return;
    int role = tag.getInt(ROLE);
    if (role == 0) {
      if (mob.getTarget() != null
          && mob.hasLineOfSight(mob.getTarget())
          && mob.distanceToSqr(mob.getTarget()) < 144) {
        if (!mob.isUsingItem()) mob.startUsingItem(InteractionHand.OFF_HAND);
      } else if (mob.isUsingItem()) mob.stopUsingItem();
    }
    if (role != 3 || mob.getHealth() > mob.getMaxHealth() * .5F) return;
    var data = SanctumSites.get(level);
    var site = data.find(tag.getUUID(SITE));
    if (site == null || site.reinforcement || mob.tickCount % 20 != 0) return;
    UUID id = site.encounter(4, 1);
    if (site.defeated.contains(id) || level.getEntity(id) != null) {
      site.reinforcement = true;
      data.setDirty();
      return;
    }
    if (!ADMITTING.add(id)) return;
    try {
      var guard = ModEntities.ANCIENT_GUARD_SLIME.get().create(level);
      if (guard == null) return;
      BlockPos at = site.at(-6, -4, 32);
      if (!level.hasChunkAt(at) || !level.isPositionEntityTicking(at)) return;
      guard.setUUID(id);
      guard.bindToRuin(at, site.id, null, null, 2);
      guard.moveTo(at.getX() + .5, at.getY(), at.getZ() + .5, 0, 0);
      guard.getPersistentData().putUUID(SITE, site.id);
      guard.getPersistentData().putInt(ZONE, 4);
      guard.getPersistentData().putInt(ROLE, 0);
      guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
      guard.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
      if (finalizeEncounter(level, guard, at, id) && admit(level, guard, id)) {
        site.reinforcement = true;
        site.spawned.add(id);
        data.setDirty();
      }
    } finally {
      ADMITTING.remove(id);
    }
  }
}
