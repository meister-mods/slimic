package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.BrokenSlimeBlock;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Slimic.MOD_ID)
public final class FriendlySlimeInteractions {
  private FriendlySlimeInteractions() {}

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void markCombatLoot(LivingDropsEvent event) {
    var attacker = event.getSource().getEntity();
    CombatSlime slime =
        attacker instanceof CombatSlime combat
            ? combat
            : attacker instanceof CombatColonyBody body ? body.getColony() : null;
    if (slime != null && slime.isAlive())
      for (var drop : event.getDrops())
        drop.getPersistentData().putUUID(CombatSlime.LOOT_COLLECTOR, slime.getUUID());
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void preventQuarryWaterRegeneration(BlockEvent.CreateFluidSourceEvent event) {
    if (!(event.getLevel() instanceof ServerLevel server)
        || event.getResult() == Event.Result.DENY
        || !event.getState().getFluidState().is(FluidTags.WATER)) return;
    if (MiningSlime.preventsFluidSourceFormation(server, event.getPos()))
      event.setResult(Event.Result.DENY);
  }

  @SubscribeEvent
  public static void trackQuarryDrainage(EntityJoinLevelEvent event) {
    if (!event.getLevel().isClientSide && event.getEntity() instanceof MiningSlime miner) {
      miner.shareStaircase();
      miner.refreshFluidSourceGuard();
    }
  }

  @SubscribeEvent
  public static void clearQuarryDrainage(LevelEvent.Unload event) {
    if (event.getLevel() instanceof ServerLevel server) MiningSlime.clearFluidSourceGuards(server);
  }

  @SubscribeEvent
  public static void preserveFarmland(BlockEvent.FarmlandTrampleEvent event) {
    if (event.getEntity() instanceof FarmingSlime
        || event.getEntity() instanceof AbundanceSlime
        || event.getEntity() instanceof AttentionSlime) event.setCanceled(true);
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void guardAttackedOwner(LivingDamageEvent event) {
    if (event.getAmount() > 0
        && !event.getEntity().level().isClientSide
        && event.getEntity() instanceof Player owner
        && event.getSource().getEntity() instanceof LivingEntity attacker)
      for (var guard :
          owner
              .level()
              .getEntitiesOfClass(
                  GuardianAngelSlime.class,
                  owner.getBoundingBox().inflate(16),
                  g -> g.isAlive() && g.canManage(owner) && g.distanceToSqr(owner) <= 256))
        guard.defendAgainst(attacker);
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void guardianTotem(LivingDeathEvent event) {
    if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
      var guardians =
          player
              .level()
              .getEntitiesOfClass(
                  GuardianAngelSlime.class,
                  player.getBoundingBox().inflate(16),
                  g -> g.isAlive() && g.canManage(player) && g.distanceToSqr(player) <= 256);
      guardians.sort(java.util.Comparator.comparingDouble(player::distanceToSqr));
      for (var guard : guardians)
        if (guard.protect(player, event.getSource())) {
          event.setCanceled(true);
          break;
        }
    }
  }

  @SubscribeEvent
  public static void hostileTargets(EntityJoinLevelEvent event) {
    if (!event.getLevel().isClientSide
        && event.getEntity() instanceof Mob mob
        && mob instanceof Enemy
        && !(mob instanceof AimlessColony)
        && !(mob instanceof ColonyFragment)
        && !(mob instanceof Creeper)
        && !(mob instanceof EnderMan)) {
      mob.targetSelector.addGoal(
          3,
          new NearestAttackableTargetGoal<>(
              mob,
              FriendlySlime.class,
              10,
              true,
              false,
              slime ->
                  slime.isAlive()
                      && (mob.getType() != EntityType.SLIME || mob.getLastHurtByMob() == slime)));
      mob.targetSelector.addGoal(
          3,
          new NearestAttackableTargetGoal<>(
              mob,
              LinkedColonyBody.class,
              10,
              true,
              false,
              candidate ->
                  candidate instanceof LinkedColonyBody<?> body
                      && body.getColony() != null
                      && (mob.getType() != EntityType.SLIME
                          || mob.getLastHurtByMob() == body
                          || mob.getLastHurtByMob() == body.getColony())));
    }
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void preserveOnDeath(LivingDeathEvent event) {
    if (!event.getEntity().level().isClientSide
        && event.getEntity() instanceof FriendlySlime slime) {
      if (slime.isPendingSpawn()) slime.discard();
      else {
        var level = (net.minecraft.server.level.ServerLevel) slime.level();
        var bonding = io.github.meistermods.slimic.world.ColonyBondingData.get(level);
        if (!bonding.store(level, slime)) {
          slime.setHealth(1);
          event.setCanceled(true);
          return;
        }
        if (!BrokenSlimeBlock.preserve(slime)) {
          slime.setHealth(1);
          CompanionRegistry.get(level).observe(slime);
          event.setCanceled(true);
          return;
        }
        if (bonding.controlled(slime.getUUID())) {
          try {
            level.save(null, true, false);
            // Retain its damaged fallback until recovery or a native stored-data proof.
          } catch (RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger()
                .error("Could not checkpoint broken colony {}", slime.getUUID(), failure);
          }
        }
      }
      event.setCanceled(true);
    }
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void interactWithLinkedBody(PlayerInteractEvent.EntityInteract event) {
    if (event.getTarget() instanceof LinkedColonyBody<?> body) {
      event.setCancellationResult(body.interactWithColony(event.getEntity(), event.getHand()));
      event.setCanceled(true);
    }
  }

  @SubscribeEvent
  public static void captureOnSneakInteract(PlayerInteractEvent.EntityInteract event) {
    if (event.getTarget() instanceof FriendlySlime slime && event.getEntity().isShiftKeyDown()) {
      // Run before vanilla consumes leads, name tags, or other held items.
      event.setCancellationResult(slime.capture(event.getEntity()));
      event.setCanceled(true);
    }
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void tameSmallWildSlime(PlayerInteractEvent.EntityInteract event) {
    var player = event.getEntity();
    if (!(event.getTarget() instanceof Slime wild)
        || wild.getType() != EntityType.SLIME
        || wild.getSize() != 1
        || !wild.isAlive()
        || wild.isRemoved()
        || player.isSpectator()
        || !event.getItemStack().is(Items.GOLDEN_APPLE)) return;
    if (event.getLevel() instanceof ServerLevel server) {
      var companion = ModEntities.FRIENDLY_SLIME.get().create(server);
      if (companion == null) return;
      companion.moveTo(wild.getX(), wild.getY(), wild.getZ(), wild.getYRot(), wild.getXRot());
      companion.setCustomName(wild.getCustomName());
      companion.setCustomNameVisible(wild.isCustomNameVisible());
      companion.tame(player);
      companion.setWaiting(true);
      if (!server.addFreshEntity(companion)) return;
      wild.discard();
      if (!player.getAbilities().instabuild) event.getItemStack().shrink(1);
      companion.onReleased();
      companion.gameEvent(GameEvent.ENTITY_INTERACT, player);
      companion.playSound(SoundEvents.SLIME_SQUISH, 1, 1);
      server.sendParticles(
          ParticleTypes.HEART,
          companion.getX(),
          companion.getEyeY(),
          companion.getZ(),
          7,
          .3,
          .2,
          .3,
          0);
    }
    event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
    event.setCanceled(true);
  }

  @SubscribeEvent
  public static void harmlessSignal(LivingAttackEvent event) {
    if (event.getSource().getDirectEntity() instanceof FireworkRocketEntity rocket
        && rocket.getPersistentData().getBoolean(SignalingSlime.SIGNAL_MARKER))
      event.setCanceled(true);
  }
}
