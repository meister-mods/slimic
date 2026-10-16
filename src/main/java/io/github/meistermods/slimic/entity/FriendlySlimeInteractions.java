package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.BrokenSlimeBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
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
  public static void preventQuarryWaterRegeneration(BlockEvent.CreateFluidSourceEvent event) {
    if (!(event.getLevel() instanceof ServerLevel server)
        || event.getResult() == Event.Result.DENY
        || !event.getState().getFluidState().is(FluidTags.WATER)) return;
    if (MiningSlime.preventsFluidSourceFormation(server, event.getPos()))
      event.setResult(Event.Result.DENY);
  }

  @SubscribeEvent
  public static void trackQuarryDrainage(EntityJoinLevelEvent event) {
    if (!event.getLevel().isClientSide && event.getEntity() instanceof MiningSlime miner)
      miner.refreshFluidSourceGuard();
  }

  @SubscribeEvent
  public static void clearQuarryDrainage(LevelEvent.Unload event) {
    if (event.getLevel() instanceof ServerLevel server) MiningSlime.clearFluidSourceGuards(server);
  }

  @SubscribeEvent
  public static void preserveFarmland(BlockEvent.FarmlandTrampleEvent event) {
    if (event.getEntity() instanceof FarmerSlime) event.setCanceled(true);
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
                  g ->
                      g.isAlive()
                          && owner.getUUID().equals(g.getOwnerUUID())
                          && g.distanceToSqr(owner) <= 256)) guard.defendAgainst(attacker);
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
                  g ->
                      g.isAlive()
                          && player.getUUID().equals(g.getOwnerUUID())
                          && g.distanceToSqr(player) <= 256);
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
        && !(mob instanceof Creeper)
        && !(mob instanceof EnderMan))
      mob.targetSelector.addGoal(
          3,
          new NearestAttackableTargetGoal<>(
              mob, FriendlySlime.class, 10, true, false, slime -> slime.isAlive()));
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void preserveOnDeath(LivingDeathEvent event) {
    if (!event.getEntity().level().isClientSide
        && event.getEntity() instanceof FriendlySlime slime) {
      BrokenSlimeBlock.preserve(slime);
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

  @SubscribeEvent
  public static void harmlessSignal(LivingAttackEvent event) {
    if (event.getSource().getDirectEntity() instanceof FireworkRocketEntity rocket
        && rocket.getPersistentData().getBoolean(HeraldSlime.SIGNAL_MARKER))
      event.setCanceled(true);
  }
}
