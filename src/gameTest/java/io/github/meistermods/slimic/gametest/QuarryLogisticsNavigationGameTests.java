package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.QuarryPlan;
import io.github.meistermods.slimic.entity.QuarryStaircaseData;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class QuarryLogisticsNavigationGameTests {
  @GameTest(template = "empty", timeoutTicks = 2400, batch = "quarry_logistics_navigation")
  public static void carrierDescendsTheLongPerimeterRouteToANearbyLowerWorker(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var level = h.getLevel();
    var chunk = new ChunkPos(BlockPos.containing(area.point(0, 100, 0)));
    var ramp = new QuarryPlan(chunk, 125, 102);
    QuarryStaircaseData.get(level).register(chunk, ramp);
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++)
        for (int y = 101; y <= 129; y++) {
          var pos = BlockPos.containing(area.point(x, y, z));
          level.setBlock(
              pos, (ramp.isRampFloor(pos) ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
        }
    // An intermediate landing recreates the observed position above a deeper excavation layer.
    for (int x = 8; x <= 13; x++)
      for (int z = 12; z <= 13; z++)
        level.setBlock(
            BlockPos.containing(area.point(x, 109, z)), Blocks.STONE.defaultBlockState(), 2);
    var chest = area.chest(11, 110, 12);
    level.setBlock(
        BlockPos.containing(area.point(13, 102, 10)), Blocks.STONE.defaultBlockState(), 2);
    var owner = h.makeMockSurvivalPlayer();
    var worker = ModEntities.MINING_SLIME.get().create(level);
    worker.tame(owner);
    worker.setInvulnerable(true);
    worker.moveTo(area.point(14.128, 101, 10.621));
    worker.setTool(0, new ItemStack(Items.NETHERITE_PICKAXE));
    SlimeStat.CAPACITY.tune(worker, 1);
    worker.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
    level.addFreshEntity(worker);
    worker.onReleased();
    worker.setWorking(true);
    var carrier = ModEntities.TRANSPORT_SLIME.get().create(level);
    carrier.tame(owner);
    carrier.setInvulnerable(true);
    carrier.moveTo(area.point(10.325, 110, 13.987));
    SlimeStat.DETECTION_RANGE.tune(carrier, 28);
    level.addFreshEntity(carrier);
    carrier.onReleased();
    carrier.setWorking(true);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    !chest.getItem(0).isEmpty(),
                    "Carrier descends the perimeter stairs and returns real cargo to the landing: "
                        + carrier.getTransportStatus()
                        + " at "
                        + carrier.position()
                        + "; worker="
                        + worker.getMiningStatus()))
        .thenExecute(
            () -> {
              h.assertTrue(
                  chest.getItem(0).is(Items.COBBLESTONE),
                  "The delivered cargo is the lower worker's cobblestone");
              worker.discard();
              carrier.discard();
              area.close();
            })
        .thenSucceed();
  }
}
