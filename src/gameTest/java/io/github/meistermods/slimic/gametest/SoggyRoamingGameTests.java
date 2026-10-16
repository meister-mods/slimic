package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SoggyRoamingGameTests {
  private static SoggySlime pet(GameTestHelper h, LoadedWorkArea a) {
    var s = ModEntities.SOGGY_SLIME.get().create(h.getLevel());
    s.tame(h.makeMockSurvivalPlayer());
    s.moveTo(a.point(8, 101, 8));
    s.onReleased();
    return s;
  }

  @GameTest(template = "empty", batch = "roaming")
  public static void menuRequiresOwnerTunerAndCorrectProfession(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var s = pet(h, a);
    var owner = h.makeMockSurvivalPlayer();
    owner.setUUID(s.getOwnerUUID());
    owner.moveTo(s.position());
    h.getLevel().addFreshEntity(s);
    h.assertTrue(s.isOrderedToSit() && !s.isRoaming(), "Initially waits");
    var menu = TestMenus.create(1, owner.getInventory(), s);
    h.assertTrue(
        menu.clickMenuButton(owner, SlimeMenu.ROAM)
            && menu.data(SlimeMenu.MODE) == SlimeMenu.ROAM
            && s.getWorkStatus().order() == WorkStatus.Order.ROAM,
        "Roam selected and synchronized");
    var stranger = h.makeMockSurvivalPlayer();
    stranger.moveTo(s.position());
    h.assertTrue(
        !menu.clickMenuButton(stranger, SlimeMenu.WAIT) && s.isRoaming(),
        "Other owner cannot change roam order");
    owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    h.assertTrue(
        !menu.clickMenuButton(owner, SlimeMenu.WAIT) && s.isRoaming(), "Missing tuner rejected");
    var ordinary = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    ordinary.tame(owner);
    ordinary.moveTo(s.position());
    h.getLevel().addFreshEntity(ordinary);
    h.assertTrue(
        !TestMenus.create(2, owner.getInventory(), ordinary).clickMenuButton(owner, SlimeMenu.ROAM),
        "Roam is specific to Soggy Slimes");
    s.discard();
    ordinary.discard();
    a.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "roaming")
  public static void followAndWaitReplaceFreeRoaming(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var s = pet(h, a);
    var owner = h.makeMockSurvivalPlayer();
    owner.setUUID(s.getOwnerUUID());
    owner.moveTo(s.position());
    h.getLevel().addFreshEntity(s);
    var menu = TestMenus.create(1, owner.getInventory(), s);
    s.setRoaming(true);
    h.assertTrue(
        menu.clickMenuButton(owner, SlimeMenu.WAIT) && !s.isRoaming() && s.isOrderedToSit(),
        "Wait cancels free roaming");
    s.setRoaming(true);
    h.assertTrue(
        menu.clickMenuButton(owner, SlimeMenu.FOLLOW)
            && !s.isRoaming()
            && !s.isOrderedToSit()
            && !s.isWorking(),
        "Follow cancels free roaming");
    s.discard();
    a.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "roaming", timeoutTicks = 800)
  public static void walksAndMakesTrailsWithoutOnlineOwner(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var s = pet(h, a);
    var origin = s.position();
    s.setRoaming(true);
    SlimeStat.DETECTION_RANGE.tune(s, 5);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(s.blockPosition()), "Roaming area ticks"))
        .thenExecute(() -> h.getLevel().addFreshEntity(s))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    s.position().distanceToSqr(origin) > 4 && s.getActivityProgress() >= 2,
                    "Roaming moves independently and lays trails: "
                        + s.position()
                        + " age="
                        + s.tickCount
                        + " progress="
                        + s.getActivityProgress()))
        .thenExecute(
            () -> {
              h.assertTrue(
                  s.getOwner() == null
                      && s.isRoaming()
                      && s.getRoamHome().equals(BlockPos.containing(origin)),
                  "Offline owner and anchored roaming after successful trail production");
              s.setWaiting(true);
              s.getNavigation().stop();
            })
        .thenIdle(30)
        .thenExecute(
            () -> {
              var feet = s.blockPosition();
              h.getLevel().setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState());
            })
        .thenIdle(25)
        .thenExecute(
            () -> {
              h.assertTrue(
                  s.isOrderedToSit()
                      && !s.isRoaming()
                      && h.getLevel().getBlockState(s.blockPosition()).isAir(),
                  "Wait stops autonomous trail production");
              s.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "roaming")
  public static void recoveryPreservesTraitsAndReanchorsAtRelease(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var s = pet(h, a);
    s.setRoaming(true);
    s.setHealth(7);
    s.setTunedVitals(7, 43, 2);
    SlimeStat.DETECTION_RANGE.tune(s, 12);
    var home = s.getRoamHome();
    var copy = ModEntities.SOGGY_SLIME.get().create(h.getLevel());
    copy.load(FriendlySlimeItem.fromSlime(s).getTag().getCompound("SlimeData"));
    h.assertTrue(
        copy.isRoaming()
            && copy.getRoamHome().equals(home)
            && copy.getHealth() == 7
            && copy.getStamina() == 43
            && copy.getUUID().equals(s.getUUID())
            && SlimeStat.DETECTION_RANGE.value(copy) == 12,
        "Saved roam order, anchor, identity and vitals");
    copy.moveTo(a.point(4, 101, 4));
    copy.onReleased();
    h.assertTrue(
        copy.isRoaming()
            && copy.getRoamHome().equals(copy.blockPosition())
            && !copy.getRoamHome().equals(home),
        "Recovered release establishes a local roaming area");
    var data = FriendlySlimeItem.fromSlime(s).getTag().getCompound("SlimeData");
    data.remove("SoggyRoaming");
    data.remove("SoggyRoamHome");
    copy.load(data);
    h.assertTrue(
        !copy.isRoaming() && !copy.isOrderedToSit(),
        "Existing follow saves do not acquire a roam command");
    a.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "roaming", timeoutTicks = 160)
  public static void depletedRoamerCanRecoverStamina(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var s = pet(h, a);
    s.setRoaming(true);
    s.setNoAi(true);
    s.setTunedVitals(16, 0, 0);
    h.getLevel()
        .setBlockAndUpdate(s.blockPosition(), ModBlocks.SLIME_TRAIL.get().defaultBlockState());
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(s.blockPosition()), "Roaming area ticks"))
        .thenExecute(() -> h.getLevel().addFreshEntity(s))
        .thenWaitUntil(
            () ->
                h.assertTrue(s.getStamina() >= 2, "Working roam command permits stamina recovery"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  s.isRoaming() && SlimeStat.DETECTION_RANGE.isUsedBy(s),
                  "Roam remains selected and detection stat is useful");
              s.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "roaming")
  public static void roamingPathsRejectUnloadedChunksWithoutLoadingThem(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var s = pet(h, a);
    s.setRoaming(true);
    s.setOnGround(true);
    var destination = BlockPos.containing(a.point(1024, 101, 8));
    var level = h.getLevel();
    h.assertTrue(!level.hasChunkAt(destination), "Distant destination is initially unloaded");
    var forced = new java.util.HashSet<>(level.getForcedChunks());
    h.assertTrue(
        s.getNavigation().createPath(destination, 0) == null
            && !level.hasChunkAt(destination)
            && forced.equals(level.getForcedChunks()),
        "Path region check adds no chunk loads or tickets");
    a.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "roaming")
  public static void roamingDoesNotReusePreviousFollowDestination(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var s = pet(h, a);
    s.setWaiting(false);
    s.setOnGround(true);
    var ownerDestination = BlockPos.containing(a.point(12, 101, 8));
    var followed = s.getNavigation().createPath(ownerDestination, 0);
    h.assertTrue(
        followed != null && followed.canReach() && s.getNavigation().moveTo(followed, 1),
        "A previous Follow path exists");
    s.setRoaming(true);
    var local = s.getNavigation().createPath(BlockPos.containing(a.point(8, 101, 10)), 0);
    h.assertTrue(
        local != null && local.canReach() && s.getNavigation().moveTo(local, 1),
        "Roaming uses a local destination");
    s.getNavigation().recomputePath();
    h.assertTrue(
        s.isRoaming() && s.getNavigation().isDone(),
        "Terrain changes release the roaming path instead of restoring the old owner destination");
    a.close();
    h.succeed();
  }
}
