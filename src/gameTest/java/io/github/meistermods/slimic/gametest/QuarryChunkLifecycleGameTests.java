package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class QuarryChunkLifecycleGameTests {
  @GameTest(template = "empty", batch = "quarry_chunk_lifecycle", timeoutTicks = 1200)
  public static void unloadedQuarryStopsItsRetainedPathWithoutReacquisition(GameTestHelper h) {
    var source = new LoadedWorkArea(h, 1500);
    var retreat = new LoadedWorkArea(h, 1500);
    var miner = ModEntities.MINING_SLIME.get().create(h.getLevel());
    miner.tame(h.makeMockSurvivalPlayer());
    miner.moveTo(source.point(8.5, 101, 8.5));
    miner.onReleased();
    miner.setWorking(true);
    miner.setNoAi(true);
    miner.setOnGround(true);
    miner.setNoGravity(true);
    miner.setTool(0, new ItemStack(Items.DIAMOND_PICKAXE));
    var original = miner.blockPosition();
    var plan = miner.getQuarryPlan().save();
    var cargo = new ItemStack(Items.DIAMOND, 37);
    cargo.getOrCreateTag().putString("Cargo", "Retained quarry");
    miner.getBag().setStackInSlot(0, cargo.copy());
    miner.setHealth(7);
    var path = miner.getNavigation().createPath(java.util.Set.of(original.offset(3, 0, 0)), 0);
    h.assertTrue(
        path != null && path.getNodeCount() > 0 && miner.getNavigation().moveTo(path, 1),
        "A real retained quarry path exists before displacement");
    miner.moveTo(retreat.point(8.5, 101, 8.5));
    miner.setOnGround(true);
    source.close();
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(miner.blockPosition()),
                    "Retreat is ticking"))
        .thenExecute(() -> h.getLevel().addFreshEntity(miner))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    !h.getLevel().hasChunkAt(original),
                    "Original quarry truly becomes unavailable"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  !miner.getNavigation().isDone(),
                  "Displacement retains the old path before the next work tick");
              try {
                var f = Mob.class.getDeclaredField("goalSelector");
                f.setAccessible(true);
                ((GoalSelector) f.get(miner)).tick();
              } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
              }
              miner.getNavigation().tick();
              h.assertTrue(
                  !h.getLevel().hasChunkAt(original) && miner.getNavigation().isDone(),
                  "Unavailable quarry cancels its old route before navigation can read and reload"
                      + " the old nodes");
              h.assertTrue(
                  miner.isWorking()
                      && miner.getQuarryPlan().save().equals(plan)
                      && ItemStack.matches(miner.getBag().getStackInSlot(0), cargo)
                      && miner.getHealth() == 7,
                  "Unavailable terrain preserves the command, completed quarry progress, cargo and"
                      + " health");
              h.getLevel().getChunk(original.getX() >> 4, original.getZ() >> 4);
              miner.moveTo(original.getX() + .5, original.getY(), original.getZ() + .5);
              miner.setOnGround(true);
              var resumed =
                  miner.getNavigation().createPath(java.util.Set.of(original.offset(3, 0, 0)), 0);
              h.assertTrue(
                  resumed != null && resumed.canReach(),
                  "Loaded quarry terrain supports a fresh route without replacing the work order");
              miner.discard();
              retreat.close();
            })
        .thenSucceed();
  }
}
