package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SupportRouteGameTests {
  @GameTest(template = "empty", batch = "support_routes", timeoutTicks = 1600)
  public static void transportActuallyDeliversAroundWideWall(GameTestHelper h) {
    route(h, 0);
  }

  @GameTest(template = "empty", batch = "support_routes", timeoutTicks = 1600)
  public static void healerActuallyTreatsAroundWideWall(GameTestHelper h) {
    route(h, 1);
  }

  @GameTest(template = "empty", batch = "support_routes", timeoutTicks = 1600)
  public static void warehouseActuallyCollectsAroundWideWall(GameTestHelper h) {
    route(h, 2);
  }

  private static int count(BagSlime slime, Item item) {
    int count = 0;
    for (int i = 0; i < slime.getBag().getSlots(); i++)
      if (slime.getBag().getStackInSlot(i).is(item))
        count += slime.getBag().getStackInSlot(i).getCount();
    return count;
  }

  private static void route(GameTestHelper h, int kind) {
    var a = new LoadedWorkArea(h);
    var level = h.getLevel();
    var owner = h.makeMockSurvivalPlayer();
    BagSlime actor =
        kind == 0
            ? ModEntities.TRANSPORT_SLIME.get().create(level)
            : kind == 1
                ? ModEntities.HEALING_SLIME.get().create(level)
                : ModEntities.WAREHOUSE_SLIME.get().create(level);
    actor.tame(owner);
    actor.moveTo(a.point(4.5, 101, 8.5));
    actor.onReleased();
    actor.setWorking(true);
    SlimeStat.DETECTION_RANGE.tune(actor, 16);
    var patient = ModEntities.HEALING_SLIME.get().create(level);
    patient.tame(owner);
    patient.moveTo(a.point(12.5, 101, 8.5));
    patient.onReleased();
    if (kind == 1) {
      patient.setNoAi(true);
      patient.setHealth(12);
      actor.getBag().setStackInSlot(0, new ItemStack(Items.SLIME_BALL, 2));
    } else if (kind == 2) {
      patient.setWorking(true);
      for (int slot = 0; slot < patient.getCapacity(); slot++)
        patient.getBag().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
    } else actor.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 11));
    var chest = a.chest(12, 101, 8);
    if (kind != 0) level.removeBlock(chest.getBlockPos(), false);
    for (int z = 4; z <= 12; z++)
      for (int y = 101; y <= 104; y++)
        level.setBlockAndUpdate(
            BlockPos.containing(a.point(8, y, z)), Blocks.BEDROCK.defaultBlockState());
    h.startSequence()
        .thenWaitUntil(
            () -> h.assertTrue(level.isPositionEntityTicking(actor.blockPosition()), "Arena ticks"))
        .thenExecute(
            () -> {
              if (kind != 0) level.addFreshEntity(patient);
              level.addFreshEntity(actor);
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    kind == 0
                        ? chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 11
                        : kind == 1
                            ? patient.getHealth() == patient.getMaxHealth()
                            : count(actor, Items.DIRT) == 576,
                    "Work completes after navigating a wide wall: "
                        + actor.getWorkStatus()
                        + ", position="
                        + actor.position()
                        + ", path="
                        + actor.getNavigation().getPath()))
        .thenExecute(
            () -> {
              h.assertTrue(
                  level.getBlockState(BlockPos.containing(a.point(8, 101, 8))).is(Blocks.BEDROCK),
                  "Navigation preserves the obstacle");
              if (kind == 0) h.assertTrue(!actor.hasCargo(), "Delivered cargo leaves the carrier");
              if (kind == 1)
                h.assertTrue(
                    count(actor, Items.SLIME_BALL) == 1, "Only the completed treatment is charged");
              if (kind == 2)
                h.assertTrue(!patient.hasCargo(), "All collected cargo leaves the worker");
              actor.discard();
              patient.discard();
              a.close();
            })
        .thenSucceed();
  }
}
