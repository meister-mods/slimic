package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NextGenerationTunnelReviewGameTests {
  private static void work(NextGenerationTunnelSlime slime) throws Exception {
    var tick =
        NextGenerationTunnelSlime.class.getDeclaredMethod("tickTunnelWork", ServerLevel.class);
    tick.setAccessible(true);
    slime.tickCount++;
    tick.invoke(slime, slime.level());
  }

  private static NextGenerationTunnelSlime slime(
      GameTestHelper h, LoadedWorkArea area, int size, int y) {
    var slime = ModEntities.NEXT_GENERATION_TUNNEL_SLIME.get().create(h.getLevel());
    slime.tame(h.makeMockSurvivalPlayer());
    slime.moveTo(area.point(8.5, y, 8.5));
    slime.setTunnelDirection(Direction.EAST);
    slime.setTunnelSize(size);
    slime.setTool(0, new ItemStack(Items.NETHERITE_PICKAXE));
    SlimeStat.WORK_SPEED.tune(slime, 64);
    SlimeStat.CAPACITY.tune(slime, 27);
    SlimeStat.STAMINA.tune(slime, 2000);
    slime.setTunedVitals(16, 2000, 0);
    slime.setWorking(true);
    return slime;
  }

  private static int cargo(NextGenerationTunnelSlime slime) {
    int count = 0;
    for (int slot : slime.getBag().occupiedSlots())
      if (slime.getBag().getStackInSlot(slot).is(Items.COBBLESTONE))
        count += slime.getBag().getStackInSlot(slot).getCount();
    return count;
  }

  @GameTest(template = "empty", batch = "next_tunnel_review")
  public static void oddAndEvenBoresExcavateEachHorizontalDirection(GameTestHelper h)
      throws Exception {
    var area = new LoadedWorkArea(h);
    for (int size : new int[] {2, 3}) {
      for (var direction : Direction.Plane.HORIZONTAL) {
        for (int x = 4; x <= 12; x++)
          for (int z = 4; z <= 12; z++)
            for (int y = 101; y <= 104; y++)
              h.getLevel()
                  .setBlock(
                      BlockPos.containing(area.point(x, y, z)), Blocks.AIR.defaultBlockState(), 2);
        var slime = slime(h, area, size, 101);
        slime.setTunnelDirection(direction);
        slime.setWorking(true);
        var front = slime.getTunnelFront();
        for (int width = slime.getTunnelMinWidth() - 1;
            width <= slime.getTunnelMinWidth() + size;
            width++)
          for (int height = 0; height <= size; height++)
            h.getLevel()
                .setBlock(
                    front.relative(direction.getClockWise(), width).above(height),
                    Blocks.STONE.defaultBlockState(),
                    2);
        for (int tick = 0; tick < 40; tick++) work(slime);
        h.assertTrue(
            cargo(slime) == size * size && slime.getPickaxe().getDamageValue() == size * size,
            "Exact odd/even rotated face: " + direction + " " + size);
      }
    }
    area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "next_tunnel_review")
  public static void oversizedRoofCannotSpendMaterialsOnAPartialPlane(GameTestHelper h)
      throws Exception {
    var area = new LoadedWorkArea(h);
    var slime = slime(h, area, 5, h.getLevel().getMaxBuildHeight() - 3);
    slime.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
    var first = slime.getTunnelFront().relative(Direction.SOUTH, slime.getTunnelMinWidth()).below();
    h.getLevel().setBlock(first, Blocks.AIR.defaultBlockState(), 2);
    work(slime);
    h.assertTrue(
        slime.getTunnelStatus() == TunnelSlime.Status.UNREACHABLE
            && h.getLevel().getBlockState(first).isAir()
            && cargo(slime) == 64
            && slime.getActivityProgress() == 0,
        "Reject the complete out-of-height face before any lining or mining");
    area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "next_tunnel_review_border")
  public static void borderCrossingCannotSpendMaterialsOnAPartialPlane(GameTestHelper h)
      throws Exception {
    var area = new LoadedWorkArea(h);
    var slime = slime(h, area, 5, 101);
    slime.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
    var front = slime.getTunnelFront();
    var first = front.relative(Direction.SOUTH, slime.getTunnelMinWidth()).below();
    h.getLevel().setBlock(first, Blocks.AIR.defaultBlockState(), 2);
    var border = h.getLevel().getWorldBorder();
    double x = border.getCenterX(), z = border.getCenterZ(), size = border.getSize();
    try {
      border.setCenter(front.getX() + .5, front.getZ() - 2);
      border.setSize(8);
      h.assertTrue(
          border.isWithinBounds(first)
              && !border.isWithinBounds(front.relative(Direction.SOUTH, 3)),
          "Fixture crosses one side");
      work(slime);
      h.assertTrue(
          slime.getTunnelStatus() == TunnelSlime.Status.UNREACHABLE
              && h.getLevel().getBlockState(first).isAir()
              && cargo(slime) == 64
              && slime.getActivityProgress() == 0,
          "Reject the complete border-crossing face before any world modification");
    } finally {
      border.setCenter(x, z);
      border.setSize(size);
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "next_tunnel_review")
  public static void obstructionAfterFinalScanReturnsToExcavation(GameTestHelper h)
      throws Exception {
    var area = new LoadedWorkArea(h);
    var slime = slime(h, area, 1, 101);
    var front = slime.getTunnelFront();
    for (var direction : new Direction[] {Direction.UP, Direction.NORTH, Direction.SOUTH})
      h.getLevel().setBlock(front.relative(direction), Blocks.STONE.defaultBlockState(), 2);
    var saved = slime.saveWithoutId(new CompoundTag());
    saved.putLong("NextGenerationTunnelFront", front.asLong());
    saved.putInt("NextGenerationTunnelPhase", 4);
    saved.putInt("NextGenerationTunnelCursor", 0);
    slime.load(saved);
    h.getLevel().setBlock(front, Blocks.STONE.defaultBlockState(), 2);
    for (int tick = 0; tick < 10; tick++) work(slime);
    h.assertTrue(
        h.getLevel().getBlockState(front).isAir()
            && cargo(slime) == 1
            && slime.getPickaxe().getDamageValue() == 1,
        "An obstructed saved movement phase resumes actual mining exactly once");
    area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "next_tunnel_review")
  public static void obstructionBehindTheFaceStillReportsAnUnreachableApproach(GameTestHelper h)
      throws Exception {
    var area = new LoadedWorkArea(h);
    var slime = slime(h, area, 1, 101);
    var front = slime.getTunnelFront();
    var saved = slime.saveWithoutId(new CompoundTag());
    saved.putLong("NextGenerationTunnelFront", front.asLong());
    saved.putInt("NextGenerationTunnelPhase", 4);
    saved.putInt("NextGenerationTunnelCursor", 0);
    slime.load(saved);
    var behind = front.west();
    h.getLevel().setBlock(behind, Blocks.STONE.defaultBlockState(), 2);
    for (int tick = 0; tick < 420; tick++) work(slime);
    h.assertTrue(
        slime.getTunnelStatus() == TunnelSlime.Status.UNREACHABLE
            && h.getLevel().getBlockState(front).isAir()
            && h.getLevel().getBlockState(behind).is(Blocks.STONE)
            && slime.getPickaxe().getDamageValue() == 0
            && slime.getActivityProgress() == 0,
        "Obstructions outside the face use approach failure instead of an endless face rescan");
    area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "next_tunnel_review")
  public static void twoWorkersSharingAFaceCannotDuplicateDropsOrToolWear(GameTestHelper h)
      throws Exception {
    var area = new LoadedWorkArea(h);
    var first = slime(h, area, 5, 101);
    var front = first.getTunnelFront();
    for (int width = -3; width <= 3; width++)
      for (int height = 0; height <= 5; height++)
        h.getLevel()
            .setBlock(
                front.relative(Direction.SOUTH, width).above(height),
                Blocks.STONE.defaultBlockState(),
                2);
    var second = ModEntities.NEXT_GENERATION_TUNNEL_SLIME.get().create(h.getLevel());
    second.load(first.saveWithoutId(new CompoundTag()));
    second.setUUID(UUID.randomUUID());
    for (int tick = 0; tick < 45; tick++) {
      work(first);
      work(second);
    }
    h.assertTrue(
        cargo(first) + cargo(second) == 25
            && first.getPickaxe().getDamageValue() + second.getPickaxe().getDamageValue() == 25,
        "Two workers sharing real terrain collect and damage tools once per block");
    area.close();
    h.succeed();
  }
}
