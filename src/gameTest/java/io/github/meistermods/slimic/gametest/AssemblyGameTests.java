package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.*;
import io.github.meistermods.slimic.world.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AssemblyGameTests {
  private static final class Fixture implements AutoCloseable {
    final GameTestHelper h;
    final ServerLevel level;
    final ServerPlayer player;
    final AssemblySites data;
    final AssemblySites.Site site;
    final AimlessColony boss;

    Fixture(GameTestHelper h) {
      this.h = h;
      level = h.getLevel();
      BlockPos anchor = h.absolutePos(new BlockPos(4, 2, 4));
      data = AssemblySites.get(level);
      site = data.register(level, anchor);
      h.assertTrue(site != null && data.awaken(level, site), "Durable site and encounter");
      site.state = AssemblySites.State.ACTIVE;
      boss = ModEntities.AIMLESS_COLONY.get().create(level);
      boss.setUUID(site.boss);
      boss.bind(site);
      boss.moveTo(anchor.getCenter());
      player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "AssemblyWitness"));
      player.moveTo(anchor.getCenter());
      level.players().add(player);
    }

    void participate() {
      for (int i = 0; i < 30; i++) data.presence(level, site);
    }

    void damage() {
      boss.invulnerableTime = 0;
      boss.hurt(level.damageSources().playerAttack(player), 4);
    }

    @Override
    public void close() {
      level.players().remove(player);
      if (!boss.isRemoved()) boss.discard();
    }
  }

  @GameTest(template = "empty", batch = "assembly")
  public static void firstPersonalRewardAndRepeatShardsAreExactlyOnce(GameTestHelper h) {
    try (var f = new Fixture(h)) {
      f.participate();
      f.damage();
      h.assertTrue(f.data.defeated(f.level, f.site), "Defeat persisted");
      h.assertTrue(f.data.claim(f.player) == AssemblySites.Claim.DELIVERED, "Personal first claim");
      h.assertTrue(
          count(f.player, ModItems.COLONY_HEART.get()) == 1
              && count(f.player, ModItems.LAST_RALLY_RECORD.get()) == 1,
          "Guaranteed first heart and record");
      h.assertTrue(f.data.claim(f.player) == AssemblySites.Claim.NONE, "No duplicate first claim");
      h.assertTrue(f.data.awaken(f.level, f.site), "Another encounter");
      f.site.state = AssemblySites.State.ACTIVE;
      f.participate();
      AssemblySites.contribute(f.player);
      h.assertTrue(f.data.defeated(f.level, f.site), "Repeat persisted");
      h.assertTrue(
          f.data.claim(f.player) == AssemblySites.Claim.DELIVERED
              && count(f.player, ModItems.COHESION_SHARD.get()) == 2
              && count(f.player, ModItems.COLONY_HEART.get()) == 1,
          "Repeat grants only two shards");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "assembly")
  public static void presenceWithoutContributionCannotClaim(GameTestHelper h) {
    try (var f = new Fixture(h)) {
      f.participate();
      h.assertTrue(f.data.defeated(f.level, f.site), "Empty contribution defeat recorded");
      h.assertTrue(
          f.data.claim(f.player) == AssemblySites.Claim.NONE && f.site.firstReward.isEmpty(),
          "AFK presence grants no entitlement");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "assembly")
  public static void fatalFirstHitCountsAfterEnoughParticipation(GameTestHelper h) {
    try (var f = new Fixture(h)) {
      f.participate();
      f.boss.hurt(f.level.damageSources().playerAttack(f.player), 10000);
      h.assertTrue(
          f.site.state == AssemblySites.State.DEFEATED
              && f.data.claim(f.player) == AssemblySites.Claim.DELIVERED,
          "The successful fatal hit is an actual contribution before entitlement creation");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "assembly")
  public static void ownedCombatDamageCreditsTheOwner(GameTestHelper h) {
    try (var f = new Fixture(h)) {
      f.participate();
      var companion = ModEntities.COMBAT_SLIME.get().create(f.level);
      companion.tame(f.player);
      companion.moveTo(f.site.anchor.getCenter());
      f.boss.hurt(f.level.damageSources().mobAttack(companion), 4);
      h.assertTrue(
          f.site.participants.get(f.player.getUUID()).contributed,
          "Owned native combat damage credited");
      f.data.defeated(f.level, f.site);
      h.assertTrue(
          f.data.claim(f.player) == AssemblySites.Claim.DELIVERED, "Companion owner eligible");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "assembly")
  public static void fullInventoryKeepsThePersonalEntitlement(GameTestHelper h) {
    try (var f = new Fixture(h)) {
      f.participate();
      f.damage();
      f.data.defeated(f.level, f.site);
      for (int i = 0; i < 36; i++)
        f.player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
      h.assertTrue(
          f.data.claim(f.player) == AssemblySites.Claim.OUTPUT_FULL
              && !f.site.deliveries.get(0).delivered,
          "Full inventory does not erase entitlement");
      f.player.getInventory().setItem(0, ItemStack.EMPTY);
      f.player.getInventory().setItem(1, ItemStack.EMPTY);
      h.assertTrue(
          f.data.claim(f.player) == AssemblySites.Claim.DELIVERED
              && count(f.player, ModItems.COLONY_HEART.get()) == 1,
          "Later delivery succeeds once");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "assembly")
  public static void peacefulEntitlementCannotBeRepeated(GameTestHelper h) {
    try (var f = new Fixture(h)) {
      h.assertTrue(
          f.data.peacefulReward(f.level, f.site, f.player.getUUID()),
          "First alternative entitlement");
      h.assertTrue(
          !f.data.peacefulReward(f.level, f.site, f.player.getUUID()),
          "One alternative per player/site");
      f.data.claim(f.player);
      h.assertTrue(count(f.player, ModItems.COLONY_HEART.get()) == 1, "One alternative heart");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "assembly")
  public static void failedPlayerCheckpointRollsBackOnlyProvisionalRewards(GameTestHelper h)
      throws Exception {
    try (var f = new Fixture(h)) {
      f.participate();
      f.damage();
      f.data.defeated(f.level, f.site);
      Path folder = f.level.getServer().getWorldPath(LevelResource.ROOT).resolve("playerdata");
      Files.createDirectories(folder);
      Path fault = folder.resolve(f.player.getStringUUID() + ".pending");
      Files.createDirectory(fault);
      f.player.getInventory().setItem(4, new ItemStack(Items.DIAMOND, 11));
      try {
        h.assertTrue(
            f.data.claim(f.player) == AssemblySites.Claim.STORAGE_ERROR
                && count(f.player, ModItems.COLONY_HEART.get()) == 0
                && count(f.player, Items.DIAMOND) == 11,
            "Failed write withdraws only provisional rewards");
      } finally {
        Files.delete(fault);
      }
      h.assertTrue(
          f.data.claim(f.player) == AssemblySites.Claim.DELIVERED, "Pending receipt retries");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "assembly")
  public static void consumedRewardCannotReappearAfterLostWorldAcknowledgement(GameTestHelper h) {
    try (var f = new Fixture(h)) {
      f.participate();
      f.damage();
      f.data.defeated(f.level, f.site);
      var before = f.data.save(new CompoundTag());
      f.data.claim(f.player);
      f.player.getInventory().clearContent();
      var stale = AssemblySites.load(before);
      h.assertTrue(
          stale.claim(f.player) == AssemblySites.Claim.DELIVERED
              && count(f.player, ModItems.COLONY_HEART.get()) == 0,
          "Saved player receipt survives consumed/moved item and stale world acknowledgement");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "assembly")
  public static void canceledNativeDamageCannotCreditOrChangeBossHealth(GameTestHelper h) {
    try (var f = new Fixture(h)) {
      java.util.function.Consumer<LivingAttackEvent> listener =
          e -> {
            if (e.getEntity() == f.boss) e.setCanceled(true);
          };
      MinecraftForge.EVENT_BUS.addListener(listener);
      try {
        f.damage();
        h.assertTrue(
            f.boss.getHealth() == f.boss.getMaxHealth() && f.site.participants.isEmpty(),
            "Native damage cancellation respected");
      } finally {
        MinecraftForge.EVENT_BUS.unregister(listener);
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "assembly")
  public static void cohesionDamageReductionEndsAfterActualFragmentLoss(GameTestHelper h) {
    try (var f = new Fixture(h)) {
      float full = f.boss.getHealth();
      f.boss.hurt(f.level.damageSources().magic(), 10);
      float protectedLoss = full - f.boss.getHealth();
      for (int i = 0; i < 5; i++) {
        UUID id = UUID.randomUUID();
        int seq = f.site.cohesion.reserve(id, 6);
        f.site.cohesion.killed(id, seq);
      }
      f.boss.invulnerableTime = 0;
      float previous = f.boss.getHealth();
      f.boss.hurt(f.level.damageSources().magic(), 10);
      h.assertTrue(
          Math.abs(protectedLoss - 9) < .001 && Math.abs(previous - f.boss.getHealth() - 10) < .001,
          "Destroyed allocations remove the ten-percent protection");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "assembly")
  public static void cancelledLivingDeathLeavesEncounterAndRewardsUnchanged(GameTestHelper h) {
    try (var f = new Fixture(h)) {
      java.util.function.Consumer<LivingDeathEvent> listener =
          e -> {
            if (e.getEntity() == f.boss) e.setCanceled(true);
          };
      MinecraftForge.EVENT_BUS.addListener(listener);
      try {
        f.boss.hurt(f.level.damageSources().playerAttack(f.player), 10000);
        h.assertTrue(
            f.site.state == AssemblySites.State.ACTIVE && f.site.deliveries.isEmpty(),
            "Death veto does not finalize rewards");
      } finally {
        MinecraftForge.EVENT_BUS.unregister(listener);
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "assembly")
  public static void globalSitesNeverAcceptSupportFromAnotherDimension(GameTestHelper h) {
    try (var f = new Fixture(h)) {
      var other = h.getLevel().getServer().getLevel(SlimicWorld.DIMENSION);
      var companion = ModEntities.COMBAT_SLIME.get().create(other);
      companion.setOwnerUUID(f.player.getUUID());
      companion.setTame(true);
      companion.moveTo(f.site.anchor.getCenter());
      AssemblySites.contribute(companion);
      h.assertTrue(
          f.site.participants.isEmpty(), "Same coordinates in another dimension cannot contribute");
    }
    h.succeed();
  }

  private static int count(ServerPlayer player, Item item) {
    return player.getInventory().items.stream()
        .filter(stack -> stack.is(item))
        .mapToInt(ItemStack::getCount)
        .sum();
  }
}
