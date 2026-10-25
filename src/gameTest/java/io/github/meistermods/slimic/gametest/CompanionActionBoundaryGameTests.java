package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.DragonSlime;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionActionBoundaryGameTests {
  @GameTest(template = "empty", batch = "companion_action_boundaries")
  public static void cancelledMountPreservesWaitUntilRidingActuallySucceeds(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var mount = ModEntities.SADDLE_SLIME.get().create(h.getLevel());
    mount.tame(owner);
    mount.moveTo(owner.position());
    mount.setEquipment(0, new ItemStack(Items.SADDLE));
    mount.setWaiting(true);
    Consumer<EntityMountEvent> reject =
        event -> {
          if (event.isMounting()
              && event.getEntityMounting() == owner
              && event.getEntityBeingMounted() == mount) event.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(reject);
    try {
      h.assertTrue(!mount.mount(owner), "A cancelled native mount reports failure");
      h.assertTrue(
          mount.isOrderedToSit() && owner.getVehicle() == null && !mount.isVehicle(),
          "A rejected mount preserves Wait and leaves both passenger lists unchanged");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(reject);
    }
    try {
      h.assertTrue(
          mount.mount(owner)
              && !mount.isOrderedToSit()
              && owner.getVehicle() == mount
              && mount.getControllingPassenger() == owner,
          "An accepted native mount releases Wait and installs the actual rider");
    } finally {
      owner.stopRiding();
      mount.discard();
    }
    h.succeed();
  }

  private static DragonSlime dragon(GameTestHelper h, LoadedWorkArea area, LivingEntity target) {
    var dragon = ModEntities.DRAGON_SLIME.get().create(h.getLevel());
    dragon.tame(h.makeMockSurvivalPlayer());
    dragon.setNoAi(true);
    dragon.setWorking(true);
    dragon.setEquipment(0, new ItemStack(Items.IRON_SWORD));
    dragon.moveTo(area.point(5.5, 101, 8.5));
    dragon.setTarget(target);
    dragon.tickCount = 1;
    return dragon;
  }

  @GameTest(template = "empty", batch = "companion_action_boundaries", timeoutTicks = 200)
  public static void dragonBreathChargesForAbsorptionHeartDamage(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var target = EntityType.HUSK.create(h.getLevel());
    target.setNoAi(true);
    target.moveTo(area.point(10.5, 101, 8.5));
    target.setAbsorptionAmount(20);
    var dragon = dragon(h, area, target);
    try {
      float health = target.getHealth();
      dragon.tick();
      h.assertTrue(
          target.getHealth() == health && target.getAbsorptionAmount() < 20,
          "The native breath hit consumes absorption without reducing health");
      h.assertTrue(
          dragon.getStamina() == 96 && dragon.getActivityProgress() == 1 && target.isOnFire(),
          "A successful absorbed breath spends four stamina and completes one attack");
    } finally {
      target.discard();
      dragon.discard();
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "companion_action_boundaries", timeoutTicks = 200)
  public static void cancelledBreathCannotChargeOrIgniteAnAbsorptionTarget(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var target = EntityType.HUSK.create(h.getLevel());
    target.setNoAi(true);
    target.moveTo(area.point(10.5, 101, 8.5));
    target.setAbsorptionAmount(20);
    var dragon = dragon(h, area, target);
    Consumer<LivingAttackEvent> reject =
        event -> {
          if (event.getEntity() == target) event.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(reject);
    try {
      float health = target.getHealth();
      dragon.tick();
      h.assertTrue(
          target.getHealth() == health
              && target.getAbsorptionAmount() == 20
              && !target.isOnFire()
              && dragon.getStamina() == 100
              && dragon.getActivityProgress() == 0,
          "Cancelled breath preserves health, absorption, fire state, stamina and progress");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(reject);
      target.discard();
      dragon.discard();
      area.close();
    }
    h.succeed();
  }
}
