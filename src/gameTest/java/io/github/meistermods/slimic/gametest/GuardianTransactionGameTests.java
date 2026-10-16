package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.GuardianAngelSlime;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuardianTransactionGameTests {
  private static int nextSite;

  private static GuardianAngelSlime guardian(GameTestHelper h) {
    var guard = ModEntities.GUARDIAN_ANGEL_SLIME.get().create(h.getLevel());
    var owner = h.makeMockSurvivalPlayer();
    guard.tame(owner);
    guard.moveTo(owner.position());
    guard.setTotems(new ItemStack(Items.TOTEM_OF_UNDYING, 2));
    owner.setHealth(5);
    return guard;
  }

  private static void invalidatedRescue(GameTestHelper h, Consumer<GuardianAngelSlime> change) {
    var guard = guardian(h);
    var owner = h.makeMockSurvivalPlayer();
    guard.tame(owner);
    owner.setHealth(5);
    ItemStack[] remaining = {ItemStack.EMPTY};
    var protectedPlayer = owner;
    Consumer<LivingUseTotemEvent> listener =
        event -> {
          if (event.getEntity() == protectedPlayer) {
            change.accept(guard);
            remaining[0] = guard.getTotems().copy();
          }
        };
    boolean used;
    MinecraftForge.EVENT_BUS.addListener(listener);
    try {
      used = guard.protect(owner, h.getLevel().damageSources().generic());
    } finally {
      MinecraftForge.EVENT_BUS.unregister(listener);
    }
    h.assertTrue(
        !used
            && ItemStack.matches(remaining[0], guard.getTotems())
            && guard.getActivityProgress() == 0
            && owner.getHealth() == 5
            && !owner.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION),
        "Invalidated protection cannot rescue or alter the player");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "guardian_transactions")
  public static void withdrawnReserveCannotProvideFreeProtection(GameTestHelper h) {
    invalidatedRescue(h, guard -> guard.setTotems(ItemStack.EMPTY));
  }

  @GameTest(template = "empty", batch = "guardian_transactions")
  public static void transferredGuardianCannotProtectPreviousOwner(GameTestHelper h) {
    invalidatedRescue(h, guard -> guard.setOwnerUUID(UUID.randomUUID()));
  }

  @GameTest(template = "empty", batch = "guardian_transactions")
  public static void removedGuardianCannotCommitProtection(GameTestHelper h) {
    invalidatedRescue(h, GuardianAngelSlime::discard);
  }

  @GameTest(template = "empty", batch = "guardian_transactions")
  public static void recursiveTotemHookCannotConsumeASecondReserve(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var guard = guardian(h);
    guard.tame(owner);
    owner.setHealth(5);
    int[] calls = {0};
    boolean[] nested = {false};
    Consumer<LivingUseTotemEvent> listener =
        event -> {
          if (event.getEntity() != owner) return;
          if (++calls[0] == 1)
            nested[0] = guard.protect(owner, h.getLevel().damageSources().generic());
        };
    MinecraftForge.EVENT_BUS.addListener(listener);
    boolean used;
    try {
      used = guard.protect(owner, h.getLevel().damageSources().generic());
    } finally {
      MinecraftForge.EVENT_BUS.unregister(listener);
    }
    h.assertTrue(
        used
            && !nested[0]
            && calls[0] == 1
            && guard.getTotems().getCount() == 1
            && owner.getHealth() == 1
            && guard.getActivityProgress() == 4,
        "Reentrant protection consumes and rewards only one completed rescue");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "guardian_transactions")
  public static void changedReserveMetadataCannotConsumeAnotherTotem(GameTestHelper h) {
    var mark = new ItemStack(Items.TOTEM_OF_UNDYING, 2);
    mark.getOrCreateTag().putString("Reserve", "replacement");
    invalidatedRescue(h, guard -> guard.setTotems(mark));
  }

  private static GuardianAngelSlime coldGuardian(GameTestHelper h) {
    int cx = (7_000_000 + nextSite++ * 256) >> 4;
    int cz = -(7_000_000 >> 4);
    h.getLevel().getChunk(cx, cz);
    var guard = guardian(h);
    guard.moveTo((cx << 4) + 8.5, 310, (cz << 4) + 8.5);
    return guard;
  }

  @GameTest(template = "empty", batch = "guardian_transactions")
  public static void nearbyFlightPathCannotLoadSurroundingColdChunks(GameTestHelper h) {
    var guard = coldGuardian(h);
    var gap = guard.blockPosition().offset(16, 0, 0);
    h.assertTrue(!h.getLevel().hasChunkAt(gap), "Flight boundary begins unavailable");
    var path = guard.getNavigation().createPath(guard.blockPosition().offset(2, 0, 0), 0);
    h.assertTrue(
        path != null && path.canReach() && !h.getLevel().hasChunkAt(gap),
        "A nearby flying path cannot prefetch cold surrounding chunks");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "guardian_transactions")
  public static void unavailableFlightDestinationCannotBeLoaded(GameTestHelper h) {
    var guard = coldGuardian(h);
    var target = guard.blockPosition().offset(32, 0, 0);
    h.assertTrue(!h.getLevel().hasChunkAt(target), "Destination begins unavailable");
    var path = guard.getNavigation().createPath(target, 0);
    h.assertTrue(
        path == null && !h.getLevel().hasChunkAt(target),
        "Flying navigation refuses unavailable destinations");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "guardian_transactions")
  public static void flyingPathCannotTreatAnUnavailableGapAsAir(GameTestHelper h) {
    var guard = coldGuardian(h);
    guard
        .getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)
        .setBaseValue(64);
    var target = guard.blockPosition().offset(48, 0, 0);
    h.getLevel().getChunk(target.getX() >> 4, target.getZ() >> 4);
    var gap = guard.blockPosition().offset(16, 0, 0);
    var path = guard.getNavigation().createPath(target, 0);
    h.assertTrue(
        !h.getLevel().hasChunkAt(gap) && (path == null || !path.canReach()),
        "A flight route cannot cross unavailable terrain represented as empty air");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "guardian_transactions")
  public static void flightRouteRecoversWhenTheGapBecomesLoaded(GameTestHelper h) {
    var guard = coldGuardian(h);
    guard
        .getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)
        .setBaseValue(64);
    var target = guard.blockPosition().offset(48, 0, 0);
    var end = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    end.moveTo(target.getX() + .5, 310, target.getZ() + .5);
    h.getLevel().getChunk(target.getX() >> 4, target.getZ() >> 4);
    var first = guard.getNavigation().createPath(target, 0);
    h.assertTrue(
        (first == null || !first.canReach()) && !guard.hasLineOfSight(end),
        "Unavailable terrain first blocks both flight and beam sight");
    for (int i = 1; i < 3; i++)
      h.getLevel().getChunk((guard.blockPosition().getX() >> 4) + i, target.getZ() >> 4);
    var second = guard.getNavigation().createPath(target, 0);
    h.assertTrue(
        second != null && second.canReach() && guard.hasLineOfSight(end),
        "A fresh route and beam query can use newly available terrain");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "guardian_transactions")
  public static void flyingShortcutCannotLoadUnknownSightGap(GameTestHelper h) {
    var guard = coldGuardian(h);
    var target = guard.position().add(48, 0, 0);
    var end = BlockPos.containing(target);
    h.getLevel().getChunk(end.getX() >> 4, end.getZ() >> 4);
    boolean clear;
    try {
      var method =
          net.minecraft.world.entity.ai.navigation.FlyingPathNavigation.class.getDeclaredMethod(
              "canMoveDirectly",
              net.minecraft.world.phys.Vec3.class,
              net.minecraft.world.phys.Vec3.class);
      method.setAccessible(true);
      clear = (boolean) method.invoke(guard.getNavigation(), guard.position(), target);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    h.assertTrue(
        !clear && !h.getLevel().hasChunkAt(guard.blockPosition().offset(16, 0, 0)),
        "Flying shortcuts cannot raycast through unavailable terrain");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "guardian_transactions")
  public static void guardianBeamSightCannotLoadUnknownIntermediateChunk(GameTestHelper h) {
    var guard = coldGuardian(h);
    var end = guard.blockPosition().offset(48, 0, 0);
    h.getLevel().getChunk(end.getX() >> 4, end.getZ() >> 4);
    var target = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    target.moveTo(end.getX() + .5, 310, end.getZ() + .5);
    var gap = guard.blockPosition().offset(16, 0, 0);
    h.assertTrue(!h.getLevel().hasChunkAt(gap), "Beam sight gap begins unavailable");
    h.assertTrue(
        !guard.hasLineOfSight(target) && !h.getLevel().hasChunkAt(gap),
        "Guardian beam sight uses only already loaded terrain");
    h.succeed();
  }
}
