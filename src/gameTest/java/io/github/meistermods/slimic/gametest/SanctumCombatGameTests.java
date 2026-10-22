package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.world.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SanctumCombatGameTests {
  @GameTest(template = "empty", batch = "sanctum_combat", timeoutTicks = 300)
  public static void changedJoinIdentityIsRemovedAndDoesNotConsumeTheStableEncounter(
      GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var anchor = BlockPos.containing(area.point(8, 105, -24));
    var site = SanctumSites.get(h.getLevel()).register(h.getLevel(), anchor, 0);
    UUID changed = UUID.randomUUID();
    boolean[] mutate = {true};
    java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> listener =
        event -> {
          var tag = event.getEntity().getPersistentData();
          if (mutate[0]
              && tag.hasUUID(SanctumEncounters.SITE)
              && site.id.equals(tag.getUUID(SanctumEncounters.SITE)))
            event.getEntity().setUUID(changed);
        };
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(site.at(0, -4, 32)), "Arena ticks"))
        .thenExecute(
            () -> {
              net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener);
              try {
                SanctumEncounters.activate(h.getLevel(), site, 4);
                h.assertTrue(
                    site.spawned.isEmpty() && h.getLevel().getEntity(changed) == null,
                    "A modified provisional identity leaves no rogue mob or consumed slot");
                mutate[0] = false;
                SanctumEncounters.activate(h.getLevel(), site, 4);
                var elite = h.getLevel().getEntity(site.encounter(4, 0));
                h.assertTrue(
                    elite != null && site.spawned.size() == 1,
                    "The stable encounter can retry after a rejected join");
                elite.discard();
              } finally {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);
                area.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "sanctum_combat", timeoutTicks = 300)
  public static void forgeStructureSpawnVetoKeepsWavesRetryableAndReentryCannotCloneSupport(
      GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var anchor = BlockPos.containing(area.point(8, 105, -24));
    var site = SanctumSites.get(h.getLevel()).register(h.getLevel(), anchor, 0);
    int[] observed = {0};
    boolean[] deny = {true};
    java.util.function.Consumer<net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn>
        listener =
            event -> {
              var tag = event.getEntity().getPersistentData();
              if (!tag.hasUUID(SanctumEncounters.SITE)
                  || !site.id.equals(tag.getUUID(SanctumEncounters.SITE))) return;
              h.assertTrue(
                  event.getSpawnType() == net.minecraft.world.entity.MobSpawnType.STRUCTURE,
                  "Native structure spawn reason");
              observed[0]++;
              if (deny[0]) event.setSpawnCancelled(true);
              else if (event.getEntity().getUUID().equals(site.encounter(4, 1)))
                SanctumEncounters.tick((AncientSlime) h.getLevel().getEntity(site.encounter(4, 0)));
            };
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(site.at(0, -4, 32)), "Arena ticks"))
        .thenExecute(
            () -> {
              net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener);
              try {
                SanctumEncounters.activate(h.getLevel(), site, 4);
                h.assertTrue(
                    observed[0] == 1
                        && site.spawned.isEmpty()
                        && h.getLevel().getEntity(site.encounter(4, 0)) == null,
                    "Finalize veto admits no mob and consumes no encounter flag");
                deny[0] = false;
                SanctumEncounters.activate(h.getLevel(), site, 4);
                var elite = (AncientSlime) h.getLevel().getEntity(site.encounter(4, 0));
                h.assertTrue(
                    elite != null && observed[0] == 2, "A later permitted attempt can progress");
                elite.setNoAi(true);
                elite.setHealth(79);
                elite.tickCount = 20;
                deny[0] = true;
                SanctumEncounters.tick(elite);
                h.assertTrue(
                    !site.reinforcement
                        && observed[0] == 3
                        && h.getLevel().getEntity(site.encounter(4, 1)) == null,
                    "Canceled support is still retryable");
                deny[0] = false;
                SanctumEncounters.tick(elite);
                var guard = (AncientSlime) h.getLevel().getEntity(site.encounter(4, 1));
                h.assertTrue(
                    guard != null
                        && site.reinforcement
                        && observed[0] == 4
                        && site.spawned.size() == 2,
                    "Reentrant finalize callback cannot clone a support guard");
                guard.discard();
                elite.discard();
              } finally {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);
                area.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "sanctum_combat", timeoutTicks = 400)
  public static void eliteCallsOneGuardAndReentryNeverClonesEitherEncounter(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var anchor = BlockPos.containing(area.point(8, 105, -24));
    var site = SanctumSites.get(h.getLevel()).register(h.getLevel(), anchor, 0);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(site.at(0, -4, 32)), "Arena ticking"))
        .thenExecute(
            () -> {
              SanctumEncounters.activate(h.getLevel(), site, 4);
              var elite = (AncientSlime) h.getLevel().getEntity(site.encounter(4, 0));
              h.assertTrue(
                  elite != null && elite.getMaxHealth() == 160 && site.spawned.size() == 1,
                  "One native elite");
              elite.setNoAi(true);
              elite.setHealth(79);
              elite.tickCount = 20;
              for (int i = 0; i < 10; i++) SanctumEncounters.tick(elite);
              var guard = (AncientSlime) h.getLevel().getEntity(site.encounter(4, 1));
              h.assertTrue(
                  guard != null && site.reinforcement && site.spawned.size() == 2,
                  "One reinforcement below half health");
              var saved =
                  SanctumSites.load(SanctumSites.get(h.getLevel()).save(new CompoundTag()))
                      .find(site.id);
              h.assertTrue(
                  saved.reinforcement && saved.spawned.size() == 2,
                  "Reinforcement receipt persists");
              site.spawned.clear();
              SanctumEncounters.activate(h.getLevel(), site, 4);
              h.assertTrue(
                  site.spawned.size() == 2
                      && h.getLevel().getEntity(elite.getUUID()) == elite
                      && h.getLevel().getEntity(guard.getUUID()) == guard,
                  "Existing native UUIDs recover a missing spawn flag");
              guard.remove(Entity.RemovalReason.KILLED);
              elite.remove(Entity.RemovalReason.KILLED);
              SanctumEncounters.activate(h.getLevel(), site, 4);
              h.assertTrue(
                  SanctumEncounters.cleared(h.getLevel(), site, 4)
                      && h.getLevel().getEntity(guard.getUUID()) == null,
                  "Defeated wave never respawns");
              area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "sanctum_combat", timeoutTicks = 300)
  public static void remnantRaisesShieldForAnIntruderAndLowersItWhenTheTargetLeaves(
      GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var guard = ModEntities.ANCIENT_GUARD_SLIME.get().create(h.getLevel());
    var owner = h.makeMockSurvivalPlayer();
    var target = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    target.tame(owner);
    target.setNoAi(true);
    var anchor = BlockPos.containing(area.point(6, 101, 8));
    var site = SanctumSites.get(h.getLevel()).register(h.getLevel(), anchor, 0);
    guard.bindToRuin(anchor, site.id, null, null, 2);
    guard.moveTo(area.point(6, 101, 8));
    target.moveTo(area.point(10, 101, 8));
    guard.getPersistentData().putUUID(SanctumEncounters.SITE, site.id);
    guard.getPersistentData().putInt(SanctumEncounters.ROLE, 0);
    guard.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
    guard.setNoAi(true);
    h.startSequence()
        .thenWaitUntil(
            () -> h.assertTrue(h.getLevel().isPositionEntityTicking(anchor), "Arena ticking"))
        .thenExecute(
            () -> {
              h.getLevel().addFreshEntity(guard);
              h.getLevel().addFreshEntity(target);
              guard.alert(target);
              SanctumEncounters.tick(guard);
              h.assertTrue(
                  guard.getTarget() == target
                      && guard.isUsingItem()
                      && guard.getUseItem().is(Items.SHIELD),
                  "Visible raised shield against a valid companion intruder");
              target.moveTo(area.point(40, 101, 8));
              SanctumEncounters.tick(guard);
              h.assertTrue(
                  !guard.isUsingItem() && !guard.mayAttack(target),
                  "Shield lowers outside defense range");
              guard.discard();
              target.discard();
              area.close();
            })
        .thenSucceed();
  }
}
