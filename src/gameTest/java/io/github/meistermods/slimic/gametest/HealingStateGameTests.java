package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.*;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;
import net.minecraftforge.items.IItemHandler;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HealingStateGameTests {
  private record Site(
      GameTestHelper h,
      LoadedWorkArea area,
      Player owner,
      HealingSlime medic,
      FriendlySlime patient) {
    GameTestSequence start() {
      return h.startSequence()
          .thenWaitUntil(
              () ->
                  h.assertTrue(
                      h.getLevel().isPositionEntityTicking(medic.blockPosition()), "Arena ticks"))
          .thenExecute(
              () -> {
                h.getLevel().addFreshEntity(patient);
                h.getLevel().addFreshEntity(medic);
              });
    }

    void close() {
      medic.discard();
      patient.discard();
      area.close();
    }
  }

  private static Site site(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var medic = ModEntities.HEALING_SLIME.get().create(h.getLevel());
    medic.tame(owner);
    medic.moveTo(a.point(6.5, 101, 8.5));
    medic.onReleased();
    medic.setWorking(true);
    medic.getBag().setStackInSlot(0, new ItemStack(Items.SLIME_BALL, 5));
    var patient = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    patient.tame(owner);
    patient.moveTo(a.point(8.5, 101, 8.5));
    patient.setNoAi(true);
    patient.setHealth(5);
    return new Site(h, a, owner, medic, patient);
  }

  private static int balls(IItemHandler bag) {
    int n = 0;
    for (int i = 0; i < bag.getSlots(); i++)
      if (bag.getStackInSlot(i).is(Items.SLIME_BALL)) n += bag.getStackInSlot(i).getCount();
    return n;
  }

  @GameTest(template = "empty", batch = "healing_state", timeoutTicks = 180)
  public static void cancelledTreatmentReportsContinuousBlockedWork(GameTestHelper h) {
    var s = site(h);
    Consumer<LivingHealEvent> cancel =
        e -> {
          if (e.getEntity() == s.patient) e.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(cancel);
    h.runAfterDelay(179, () -> MinecraftForge.EVENT_BUS.unregister(cancel));
    s.start()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    s.medic.getBlockedWorkTicks() >= 25,
                    "Rejected treatment remains a blocked job for herald monitoring"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  s.patient.getHealth() == 5
                      && balls(s.medic.getBag()) == 5
                      && s.medic.getWorkStatus().reason() == WorkStatus.Reason.UNREACHABLE,
                  "Failed treatment preserves all supplies and describes the obstacle");
              MinecraftForge.EVENT_BUS.unregister(cancel);
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "healing_state", timeoutTicks = 220)
  public static void completedPatientClearsPreviousTreatmentBlock(GameTestHelper h) {
    var s = site(h);
    Consumer<LivingHealEvent> cancel =
        e -> {
          if (e.getEntity() == s.patient) e.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(cancel);
    h.runAfterDelay(219, () -> MinecraftForge.EVENT_BUS.unregister(cancel));
    s.start()
        .thenWaitUntil(
            () -> h.assertTrue(s.medic.getBlockedWorkTicks() >= 10, "Treatment obstacle detected"))
        .thenExecute(
            () -> {
              MinecraftForge.EVENT_BUS.unregister(cancel);
              s.patient.setHealth(s.patient.getMaxHealth());
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    s.medic.getWorkStatus().state() == WorkStatus.State.IDLE
                        && s.medic.getBlockedWorkTicks() == 0,
                    "Another healer completing the patient clears the stale obstacle"))
        .thenExecute(
            () -> {
              h.assertTrue(balls(s.medic.getBag()) == 5, "No unused treatment is charged");
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "healing_state", timeoutTicks = 180)
  public static void protectedRevivalReportsBlockedWork(GameTestHelper h) {
    var s = site(h);
    var pos = BlockPos.containing(s.patient.position());
    h.getLevel().setBlockAndUpdate(pos, ModBlocks.BROKEN_SLIME.get().defaultBlockState());
    ((BrokenSlimeBlockEntity) h.getLevel().getBlockEntity(pos))
        .setSlime(FriendlySlimeItem.fromSlime(s.patient));
    Consumer<BlockEvent.BreakEvent> cancel =
        e -> {
          if (e.getPos().equals(pos)) e.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(cancel);
    h.runAfterDelay(179, () -> MinecraftForge.EVENT_BUS.unregister(cancel));
    h.startSequence()
        .thenWaitUntil(() -> h.assertTrue(h.getLevel().isPositionEntityTicking(pos), "Arena ticks"))
        .thenExecute(() -> h.getLevel().addFreshEntity(s.medic))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    s.medic.getBlockedWorkTicks() >= 25, "Protected revival remains a blocked job"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockEntity(pos) instanceof BrokenSlimeBlockEntity
                      && balls(s.medic.getBag()) == 5,
                  "Protection retains the stored companion and balls");
              MinecraftForge.EVENT_BUS.unregister(cancel);
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "healing_state", timeoutTicks = 180)
  public static void twoHealersChargeOnlyOneCompletedTreatment(GameTestHelper h) {
    var s = site(h);
    s.patient.setHealth(12);
    var other = ModEntities.HEALING_SLIME.get().create(h.getLevel());
    other.tame(s.owner);
    other.moveTo(s.area.point(8.5, 101, 6.5));
    other.onReleased();
    other.setWorking(true);
    other.getBag().setStackInSlot(0, new ItemStack(Items.SLIME_BALL, 5));
    s.start()
        .thenExecute(() -> h.getLevel().addFreshEntity(other))
        .thenWaitUntil(
            () -> h.assertTrue(s.patient.getHealth() == s.patient.getMaxHealth(), "Patient healed"))
        .thenIdle(25)
        .thenExecute(
            () -> {
              h.assertTrue(
                  balls(s.medic.getBag()) + balls(other.getBag()) == 9
                      && s.medic.getActivityProgress() + other.getActivityProgress() == 1,
                  "Concurrent healers spend only for the actual health gain");
              other.discard();
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "healing_watchdog", timeoutTicks = 4000)
  public static void repeatedUnreachableRetriesStillReachHeraldDelay(GameTestHelper h) {
    var s = site(h);
    s.patient.moveTo(s.area.point(11.5, 101, 8.5));
    SlimeStat.MOVEMENT_SPEED.tune(s.medic, 0);
    var herald = ModEntities.HERALD_SLIME.get().create(h.getLevel());
    herald.tame(s.owner);
    herald.moveTo(s.area.point(7.5, 101, 5.5));
    herald.setDelayMinutes(1);
    herald.setMonitoring(true);
    s.start()
        .thenExecute(() -> h.getLevel().addFreshEntity(herald))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    herald
                        .saveWithoutId(new CompoundTag())
                        .getCompound("HeraldWarnings")
                        .contains(s.medic.getUUID().toString()),
                    "Repeated unsuccessful retries must not reset the one-minute warning delay"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  s.patient.getHealth() == 5 && balls(s.medic.getBag()) == 5,
                  "Unreachable work spends no treatment supplies");
              SlimeStat.MOVEMENT_SPEED.tune(s.medic, .2);
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    s.patient.getHealth() > 5 && s.medic.getBlockedWorkTicks() == 0,
                    "Productive work clears the blocked episode after movement resumes: health="
                        + s.patient.getHealth()
                        + ", medic="
                        + s.medic.position()
                        + ", patient="
                        + s.patient.position()
                        + ", status="
                        + s.medic.getWorkStatus()
                        + ", blocked="
                        + s.medic.getBlockedWorkTicks()
                        + ", path="
                        + s.medic.getNavigation().getPath()))
        .thenExecute(
            () -> {
              herald.discard();
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "healing_state", timeoutTicks = 120)
  public static void warehouseRevivalKeepsLastSlotAndTunedCapacity(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var w = ModEntities.WAREHOUSE_SLIME.get().create(h.getLevel());
    w.tame(owner);
    w.moveTo(a.point(8.5, 101, 8.5));
    w.onReleased();
    w.setWorking(true);
    w.setNoAi(true);
    SlimeStat.CAPACITY.tune(w, 27);
    w.setTunedVitals(7, 44, 5);
    var special = new ItemStack(Items.DIAMOND, 17);
    special.getOrCreateTag().putString("CargoMark", "last-slot");
    w.getBag().setStackInSlot(215, special.copy());
    var id = w.getUUID();
    var pos = w.blockPosition();
    owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SLIME_BALL, 2));
    h.startSequence()
        .thenWaitUntil(
            () -> h.assertTrue(h.getLevel().isPositionEntityTicking(pos), "Warehouse arena ticks"))
        .thenExecute(
            () -> {
              h.getLevel().addFreshEntity(w);
              w.hurt(h.getLevel().damageSources().genericKill(), 10000);
            })
        .thenIdle(3)
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel()
                      .getBlockState(pos)
                      .use(
                          h.getLevel(),
                          owner,
                          InteractionHand.MAIN_HAND,
                          new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false))
                      .consumesAction(),
                  "Stored warehouse revives");
              var restored = (WarehouseSlime) h.getLevel().getEntity(id);
              h.assertTrue(
                  restored.getHealth() == 1
                      && restored.getStamina() == 44
                      && restored.getCapacity() == 216
                      && !restored.isWorking()
                      && restored.isOrderedToSit()
                      && ItemStack.matches(restored.getBag().getStackInSlot(215), special),
                  "Revival starts in Wait with 216-slot capacity, final-slot NBT and one health");
              restored.discard();
              a.close();
            })
        .thenSucceed();
  }
}
