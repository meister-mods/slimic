package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.QuarryPlan;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MiningReliabilityGameTests {
  private static final int OFFSET = 262144 + (int) Math.floorMod(System.nanoTime(), 500000L) * 16;

  private MiningReliabilityGameTests() {}

  @GameTest(template = "empty", batch = "quarry_reliability")
  public static void defaultDetectionNavigatesFourteenLevelsOfExitStairs(GameTestHelper helper) {
    Arena arena = new Arena(helper, 99, 85);
    MiningSlime miner = arena.miner(8, 8);
    QuarryPlan plan = miner.getQuarryPlan();
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++)
        for (int y = 86; y <= 99; y++) {
          BlockPos pos = arena.pos(x, y, z);
          arena
              .level()
              .setBlockAndUpdate(
                  pos, (plan.isRampFloor(pos) ? Blocks.STONE : Blocks.AIR).defaultBlockState());
        }
    miner.moveTo(arena.x + 8, 86, arena.z + 8);
    miner.setOnGround(true);
    var path =
        miner
            .getNavigation()
            .createPath(
                java.util.Set.of(
                    arena.pos(0, 100, 0), arena.pos(1, 100, 0),
                    arena.pos(0, 100, 1), arena.pos(1, 100, 1)),
                0);
    helper.assertTrue(
        SlimeStat.DETECTION_RANGE.value(miner) == 8
            && path != null
            && path.canReach()
            && path.getNodeCount() > 20,
        "A default miner must find the long staircase without stat or test-only navigation boosts: "
            + path
            + " nodes="
            + (path == null ? 0 : path.getNodeCount())
            + " reachable="
            + (path != null && path.canReach())
            + " goal="
            + arena.pos(0, 100, 0)
            + " end="
            + (path == null ? null : path.getEndNode()));
    for (int index = 0; index < path.getNodeCount(); index++) {
      var node = path.getNode(index);
      helper.assertTrue(
          plan.contains(new BlockPos(node.x, node.y, node.z))
              && plan.contains(new BlockPos(node.x + 1, node.y, node.z + 1)),
          "Every wide-mob path node must remain in the work chunk");
    }
    miner.discard();
    arena.close();
    helper.succeed();
  }

  @GameTest(template = "empty", batch = "quarry_reliability", timeoutTicks = 2400)
  public static void repairsDistantStepByWalkingAroundAnUnbreakableWall(GameTestHelper helper) {
    Arena arena = new Arena(helper, 100, 100);
    arena.wall(false);
    MiningSlime miner = arena.miner(5, 12);
    arena.start(miner);
    arena.assertRepairFinishes(helper, miner);
  }

  @GameTest(template = "empty", batch = "quarry_reliability", timeoutTicks = 2400)
  public static void inaccessibleRepairReportsBlockedAndResumesAfterRouteOpens(
      GameTestHelper helper) {
    Arena arena = new Arena(helper, 100, 100);
    arena.wall(true);
    MiningSlime miner = arena.miner(5, 12);
    arena.start(miner);
    helper.runAfterDelay(
        450,
        () -> {
          helper.assertTrue(
              miner.getMiningStatus() == MiningSlime.Status.UNREACHABLE
                  && miner.isWorkBlocked()
                  && miner.getBag().getStackInSlot(0).getCount() == 3
                  && arena.level().getBlockState(arena.gap()).isAir(),
              "Failed support travel must report blocked without placing or consuming a block");
          arena.wall(false);
          arena.assertRepairFinishes(helper, miner);
        });
  }

  private static final class Arena {
    private final GameTestHelper helper;
    private final int x, z, top;

    Arena(GameTestHelper helper, int top, int bottom) {
      this.helper = helper;
      this.top = top;
      BlockPos marker = helper.absolutePos(BlockPos.ZERO);
      x = ((marker.getX() * 16 + OFFSET) >> 4) * 16;
      z = ((marker.getZ() * 16 + OFFSET) >> 4) * 16;
      force(true);
      helper.runAfterDelay(2390, this::close);
      for (int dx = 0; dx < 16; dx++)
        for (int dz = 0; dz < 16; dz++)
          for (int y = bottom; y <= 106; y++)
            level()
                .setBlockAndUpdate(
                    pos(dx, y, dz), (y <= top ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState());
    }

    private net.minecraft.server.level.ServerLevel level() {
      return helper.getLevel();
    }

    private BlockPos pos(int dx, int y, int dz) {
      return new BlockPos(x + dx, y, z + dz);
    }

    private BlockPos gap() {
      return pos(0, top, 0);
    }

    private MiningSlime miner(int dx, int dz) {
      MiningSlime miner = ModEntities.MINING_SLIME.get().create(level());
      miner.moveTo(x + dx, top + 1, z + dz);
      miner.tame(helper.makeMockSurvivalPlayer());
      miner.setTool(0, new ItemStack(Items.IRON_PICKAXE));
      miner.onReleased();
      return miner;
    }

    private void wall(boolean closed) {
      for (int dx = 0; dx < 16; dx++)
        for (int y = top + 1; y <= top + 5; y++)
          level()
              .setBlockAndUpdate(
                  pos(dx, y, 6),
                  (closed || dx < 12 ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState());
    }

    private void start(MiningSlime miner) {
      level().setBlockAndUpdate(gap(), Blocks.AIR.defaultBlockState());
      miner.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 3));
      level().addFreshEntity(miner);
    }

    private void assertRepairFinishes(GameTestHelper helper, MiningSlime miner) {
      helper.succeedWhen(
          () -> {
            helper.assertTrue(
                miner.getMiningStatus() == MiningSlime.Status.COMPLETE,
                "Default miner still repairing: "
                    + miner.getMiningStatus()
                    + " at "
                    + miner.position()
                    + " after "
                    + miner.tickCount
                    + " entity ticks; chunk="
                    + level().getChunkSource().getChunkNow(x >> 4, z >> 4).getFullStatus());
            helper.assertTrue(
                level().getBlockState(gap()).is(Blocks.COBBLESTONE)
                    && miner.getBag().getStackInSlot(0).getCount() == 2
                    && SlimeStat.DETECTION_RANGE.value(miner) == 8
                    && miner.getPickaxe().getDamageValue() == 0,
                "Repair must consume exactly one support while keeping stats and tools intact");
            miner.discard();
            close();
          });
    }

    private void force(boolean value) {
      for (int dx = -1; dx <= 1; dx++)
        for (int dz = -1; dz <= 1; dz++)
          level().setChunkForced((x >> 4) + dx, (z >> 4) + dz, value);
      if (value)
        for (int dx = -1; dx <= 1; dx++)
          for (int dz = -1; dz <= 1; dz++) level().getChunk((x >> 4) + dx, (z >> 4) + dz);
    }

    private void close() {
      force(false);
    }
  }
}
