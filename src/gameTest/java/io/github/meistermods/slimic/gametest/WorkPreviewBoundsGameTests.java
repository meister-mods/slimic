package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.network.WorkAreaPacket;
import io.github.meistermods.slimic.registry.ModEntities;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkPreviewBoundsGameTests {
  private static void packetRoundTrip(GameTestHelper h, FriendlySlime slime, SlimeWorkArea area) {
    var packet =
        new WorkAreaPacket(
            slime.level().dimension().location(), slime.getId(), slime.getUUID(), area);
    var buffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
      packet.encode(buffer);
      h.assertTrue(WorkAreaPacket.decode(buffer).equals(packet), "The existing packet round-trips");
    } finally {
      buffer.release();
    }
    for (var shape : area.shapes()) {
      var box = shape.bounds();
      h.assertTrue(
          box.getXsize() <= 65536 && box.getYsize() <= 65536 && box.getZsize() <= 65536,
          "Every displayed shape fits the packet geometry limit");
    }
  }

  @GameTest(template = "empty", batch = "work_preview_bounds", timeoutTicks = 200)
  public static void unlimitedCreativeRangesRemainReadableWithoutChangingStatsOrOrders(
      GameTestHelper h) {
    for (var type : ModEntities.individualCompanionTypes()) {
      for (int sign : new int[] {-1, 1}) {
        var slime = (FriendlySlime) type.get().create(h.getLevel());
        slime.moveTo(sign * 29_999_980.5, 101, sign * 29_999_980.5);
        for (double value : new double[] {0, 64, 32768, 32769, Double.MAX_VALUE}) {
          SlimeStat.DETECTION_RANGE.tune(slime, value);
          double actualRange = SlimeStat.DETECTION_RANGE.value(slime);
          var before = slime.saveWithoutId(new CompoundTag());
          var area = SlimeWorkArea.of(slime);
          packetRoundTrip(h, slime, area);
          h.assertTrue(
              SlimeStat.DETECTION_RANGE.tunerValue(slime) == value
                  && SlimeStat.DETECTION_RANGE.value(slime) == actualRange
                  && slime.saveWithoutId(new CompoundTag()).equals(before)
                  && slime.isOrderedToSit()
                  && !slime.isWorking(),
              "Reading a large preview changes neither the saved companion nor its effective"
                  + " range");
          if (slime instanceof CleaningSlime && value == 64)
            h.assertTrue(
                area.shapes().get(0).bounds().getXsize() == 640,
                "Normal profession range multipliers retain their complete display");
        }
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "work_preview_bounds", timeoutTicks = 200)
  public static void distantRegisteredFarmPlotsFitTheDisplayWithoutLosingSavedPlots(
      GameTestHelper h) {
    var farm = ModEntities.FARMING_SLIME.get().create(h.getLevel());
    farm.tame(h.makeMockSurvivalPlayer());
    farm.moveTo(100.5, 101, 100.5);
    SlimeStat.DETECTION_RANGE.tune(farm, Double.MAX_VALUE);
    var saved = farm.saveWithoutId(new CompoundTag());
    saved.getCompound("FarmingWork").putBoolean("Working", true);
    saved.putBoolean("Sitting", false);
    var near = new BlockPos(100, 100, 100);
    var distant = near.east(100_000);
    saved.putLongArray("FarmPlots", new long[] {near.asLong(), distant.asLong()});
    farm.load(saved);
    farm.setWaiting(false);
    var before = farm.saveWithoutId(new CompoundTag());
    var area = SlimeWorkArea.of(farm);
    packetRoundTrip(h, farm, area);
    h.assertTrue(
        farm.isWorking()
            && farm.getFarmPlots().containsAll(java.util.List.of(near, distant))
            && farm.getFarmPlots().size() == 2
            && farm.saveWithoutId(new CompoundTag()).equals(before)
            && area.shapes().size() == 2
            && area.shapes().get(1).next(),
        "The cropped plot highlight preserves both registered plots and the active work order");
    h.succeed();
  }
}
