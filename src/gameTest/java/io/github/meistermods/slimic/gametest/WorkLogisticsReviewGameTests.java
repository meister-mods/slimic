package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkLogisticsReviewGameTests {
  @GameTest(template = "empty", batch = "work_logistics_review", timeoutTicks = 200)
  public static void tunnelSupplyAndWarningAgreeOnTheActualMissingTool(GameTestHelper h)
      throws Exception {
    var area = new LoadedWorkArea(h);
    var mine = TunnelSlime.class.getDeclaredMethod("mine", ServerLevel.class, BlockPos.class);
    mine.setAccessible(true);
    for (boolean nextGeneration : new boolean[] {false, true}) {
      for (boolean dirt : new boolean[] {false, true}) {
        TunnelSlime worker =
            (nextGeneration ? ModEntities.NEXT_GENERATION_TUNNEL_SLIME : ModEntities.TUNNEL_SLIME)
                .get()
                .create(h.getLevel());
        worker.tame(h.makeMockSurvivalPlayer());
        worker.moveTo(area.point(7.5, 101, 7.5));
        worker.onReleased();
        worker.setWorking(true);
        var front = worker.getTunnelFront();
        var state = (dirt ? Blocks.DIRT : Blocks.STONE).defaultBlockState();
        h.getLevel().setBlockAndUpdate(front, state);
        mine.invoke(worker, h.getLevel(), front);
        var status = worker.getWorkStatus();
        var needed = new ItemStack(dirt ? Items.IRON_SHOVEL : Items.IRON_PICKAXE);
        var wrong = new ItemStack(dirt ? Items.IRON_PICKAXE : Items.IRON_SHOVEL);
        h.assertTrue(
            status.reason() == WorkStatus.Reason.TOOL_MISSING
                && status.requests(WorkStatus.Request.TOOL_SUPPLY),
            "The blocked tunneler requests a replacement tool");
        h.assertTrue(
            worker.acceptsWorkTool(needed) && !worker.acceptsWorkTool(wrong),
            "Logistics recognizes the tool required by the actual face");
        var reason = SlimeWorkWarning.reason(worker, status);
        h.assertTrue(
            worker.getRequiredToolSlot() == (dirt ? 1 : 0)
                && reason.getContents() instanceof TranslatableContents translated
                && translated
                    .getKey()
                    .equals("message.slimic.blocked.tool_missing." + (dirt ? "shovel" : "pickaxe")),
            "The reported missing tool agrees with logistics for both tunneler variants");
        h.assertTrue(
            worker.receiveWorkSupply(needed) == 1
                && worker.getTool(dirt ? 1 : 0).is(needed.getItem())
                && h.getLevel().getBlockState(front).equals(state),
            "The requested tool reaches the correct equipment slot without breaking terrain early");
        worker.discard();
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "work_logistics_review", timeoutTicks = 200)
  public static void quarryRepairsRespectTheBorderBeforeAndAfterPlacementCallbacks(GameTestHelper h)
      throws Exception {
    var area = new LoadedWorkArea(h);
    var level = h.getLevel();
    var worker = ModEntities.MINING_SLIME.get().create(level);
    worker.tame(h.makeMockSurvivalPlayer());
    worker.moveTo(area.point(2.5, 101, 1.5));
    worker.onReleased();
    worker.setWorking(true);
    worker.getQuarryPlan().descend();
    worker.getQuarryPlan().descend();
    worker.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 8));
    var floor = worker.getQuarryPlan().floorsAt(100).get(0);
    level.setBlockAndUpdate(floor, Blocks.AIR.defaultBlockState());
    var repair = MiningSlime.class.getDeclaredMethod("ensureRamp", ServerLevel.class);
    repair.setAccessible(true);
    var border = level.getWorldBorder();
    var savedBorder = border.createSettings();
    Runnable excludeFloor =
        () -> {
          border.setCenter(floor.getX() + 2, floor.getZ() + .5);
          border.setSize(2);
        };
    try {
      excludeFloor.run();
      repair.invoke(worker, level);
      h.assertTrue(
          level.getBlockState(floor).isAir()
              && worker.getBag().getStackInSlot(0).getCount() == 8
              && worker.getMiningStatus() == MiningSlime.Status.UNREACHABLE,
          "A staircase outside the border is left unchanged without consuming a support block");
    } finally {
      border.applySettings(savedBorder);
    }
    int[] calls = {0};
    java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent> hook =
        event -> {
          if (!event.getPos().equals(floor)) return;
          calls[0]++;
          excludeFloor.run();
        };
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(hook);
    try {
      repair.invoke(worker, level);
      h.assertTrue(
          calls[0] == 1
              && level.getBlockState(floor).isAir()
              && worker.getBag().getStackInSlot(0).getCount() == 8,
          "A changed border during the native callback rolls back placement and preserves"
              + " material");
    } finally {
      net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(hook);
      border.applySettings(savedBorder);
    }
    repair.invoke(worker, level);
    h.assertTrue(
        level.getBlockState(floor).is(Blocks.COBBLESTONE)
            && worker.getBag().getStackInSlot(0).getCount() == 7,
        "The same valid repair still places and consumes exactly one support block");
    worker.discard();
    h.succeed();
  }
}
