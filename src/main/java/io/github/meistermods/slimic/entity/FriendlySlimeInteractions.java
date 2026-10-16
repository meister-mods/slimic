package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.BrokenSlimeBlock;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Slimic.MOD_ID)
public final class FriendlySlimeInteractions {
  private FriendlySlimeInteractions() {}

  @SubscribeEvent
  public static void preserveFarmland(
      net.minecraftforge.event.level.BlockEvent.FarmlandTrampleEvent event) {
    if (event.getEntity() instanceof FarmerSlime) event.setCanceled(true);
  }

  @SubscribeEvent
  public static void hostileTargets(EntityJoinLevelEvent event) {
    if (!event.getLevel().isClientSide
        && event.getEntity() instanceof Mob mob
        && mob instanceof Enemy
        && !(mob instanceof Creeper))
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
