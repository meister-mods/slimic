package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MiningAuditGameTests {
  private MiningAuditGameTests() {}

  private static List<EntityType<? extends FriendlySlime>> types() {
    return List.of(
        ModEntities.FRIENDLY_SLIME.get(),
        ModEntities.MINING_SLIME.get(),
        ModEntities.TRANSPORT_SLIME.get(),
        ModEntities.HERALD_SLIME.get(),
        ModEntities.COMBAT_SLIME.get(),
        ModEntities.CLEANING_SLIME.get(),
        ModEntities.SADDLE_SLIME.get(),
        ModEntities.FARMER_SLIME.get(),
        ModEntities.LUMBERJACK_SLIME.get(),
        ModEntities.GUARDIAN_ANGEL_SLIME.get(),
        ModEntities.SOGGY_SLIME.get(),
        ModEntities.HEALING_SLIME.get(),
        ModEntities.WAREHOUSE_SLIME.get());
  }

  @GameTest(template = "empty", batch = "mining_audit")
  public static void allCompanionCompanionsUseThirtyTwoThenNinetySixActivity(GameTestHelper h) {
    for (var type : types()) {
      var pet = type.create(h.getLevel());
      h.assertTrue(pet.getNextPointCost() == 32, "Every specialization must start at32: " + type);
      CompoundTag saved = pet.saveWithoutId(new CompoundTag());
      saved.putInt("ActivityProgress", 31);
      pet.load(saved);
      h.assertTrue(
          pet.getStatPoints() == 0 && pet.getActivityProgress() == 31,
          "The first point must still require all32 units");
      saved.putInt("ActivityProgress", 32);
      pet.load(saved);
      h.assertTrue(
          pet.getStatPoints() == 1
              && pet.getActivityProgress() == 0
              && pet.getNextPointCost() == 96,
          "The first32 units award one point and the next threshold increases by64");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "mining_audit")
  public static void legacyActivityBecomesPointsWithoutLossOrRepeatedAwards(GameTestHelper h) {
    for (var type : types()) {
      var pet = type.create(h.getLevel());
      pet.tame(h.makeMockSurvivalPlayer());
      pet.setTunedVitals(6, 39, 7);
      CompoundTag saved = pet.saveWithoutId(new CompoundTag());
      saved.putInt("ActivityProgress", 200);
      saved.putInt("EarnedPoints", 0);
      pet.load(saved);
      h.assertTrue(
          pet.getStatPoints() == 9
              && pet.getActivityProgress() == 72
              && pet.getNextPointCost() == 160
              && pet.getHealth() == 6
              && pet.getStamina() == 39,
          "Old200 activity must pay32+96 and retain72, existing points and vitals: " + type);
      var restored = type.create(h.getLevel());
      restored.load(FriendlySlimeItem.fromSlime(pet).getTag().getCompound("SlimeData"));
      h.assertTrue(
          restored.getStatPoints() == 9
              && restored.getActivityProgress() == 72
              && restored.getNextPointCost() == 160
              && restored.getUUID().equals(pet.getUUID()),
          "Recovery must retain converted growth without paying it again");
    }
    h.succeed();
  }

  private static MiningSlime miner(GameTestHelper h, LoadedWorkArea arena, Vec3 point) {
    var pet = ModEntities.MINING_SLIME.get().create(h.getLevel());
    pet.setWorking(true);
    pet.moveTo(arena.point(point.x, point.y, point.z));
    pet.tame(h.makeMockSurvivalPlayer());
    pet.setTool(0, new ItemStack(Items.DIAMOND_PICKAXE));
    pet.onReleased();
    return pet;
  }

  @GameTest(template = "empty", batch = "mining_audit", timeoutTicks = 400)
  public static void startingAfterFollowingIntoAnotherChunkUsesTheCurrentQuarry(GameTestHelper h) {
    LoadedWorkArea oldArea = new LoadedWorkArea(h);
    LoadedWorkArea newArea = new LoadedWorkArea(h);
    var pet = miner(h, oldArea, new Vec3(8, 101, 8));
    pet.getQuarryPlan().descend();
    pet.getQuarryPlan().descend();
    BlockPos oldTarget = BlockPos.containing(oldArea.point(9, 101, 8));
    BlockPos nextTarget = BlockPos.containing(newArea.point(9, 101, 8));
    h.getLevel().setBlockAndUpdate(oldTarget, Blocks.STONE.defaultBlockState());
    h.getLevel().setBlockAndUpdate(nextTarget, Blocks.STONE.defaultBlockState());
    pet.setWorking(false);
    pet.moveTo(newArea.point(8, 101, 8));
    pet.setWorking(true);
    try {
      h.assertTrue(
          pet.getMiningChunk().equals(pet.chunkPosition()) && pet.getQuarryPlan().layer() == 102,
          "Mine after following into a different chunk must rebase to that chunk, not the old"
              + " excavation");
    } catch (RuntimeException failure) {
      oldArea.close();
      newArea.close();
      throw failure;
    }
    h.getLevel().addFreshEntity(pet);
    h.succeedWhen(
        () -> {
          h.assertTrue(
              h.getLevel().getBlockState(nextTarget).isAir(),
              "The new work command must actually mine its new area");
          h.assertTrue(
              h.getLevel().getBlockState(oldTarget).is(Blocks.STONE),
              "Restarting elsewhere must leave the previous area alone");
          pet.discard();
          oldArea.close();
          newArea.close();
        });
  }

  @GameTest(template = "empty", batch = "mining_audit", timeoutTicks = 1000)
  public static void verySlowMiningApproachDoesNotAbandonAnAdvancingRoute(GameTestHelper h) {
    LoadedWorkArea arena = new LoadedWorkArea(h);
    var pet = miner(h, arena, new Vec3(1, 101, 8));
    SlimeStat.MOVEMENT_SPEED.tune(pet, .03);
    SlimeStat.DETECTION_RANGE.tune(pet, 16);
    BlockPos ore = BlockPos.containing(arena.point(14, 101, 8));
    h.getLevel().setBlockAndUpdate(ore, Blocks.STONE.defaultBlockState());
    h.getLevel().addFreshEntity(pet);
    h.onEachTick(
        () -> {
          if (pet.tickCount < 450) return;
          h.assertTrue(
              !pet.isWorkBlocked() && pet.position().distanceToSqr(arena.point(1, 101, 8)) > .001,
              "A slowly advancing miner must keep its route beyond400 ticks: "
                  + pet.getMiningStatus()
                  + " position="
                  + pet.position()
                  + " blocked="
                  + pet.isWorkBlocked());
          pet.discard();
          arena.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "mining_audit", timeoutTicks = 400)
  public static void replacingTheBlockResetsItsAccumulatedMiningDamage(GameTestHelper h) {
    LoadedWorkArea arena = new LoadedWorkArea(h);
    var pet = miner(h, arena, new Vec3(8, 101, 8));
    SlimeStat.WORK_SPEED.tune(pet, .1);
    BlockPos ore = BlockPos.containing(arena.point(9, 101, 8));
    h.getLevel().setBlockAndUpdate(ore, Blocks.STONE.defaultBlockState());
    h.getLevel().addFreshEntity(pet);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    pet.getMiningStatus() == MiningSlime.Status.MINING,
                    "Begin mining the original block"))
        .thenIdle(40)
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockState(ore).is(Blocks.STONE),
                  "The original block must still be partly mined");
              h.getLevel().setBlockAndUpdate(ore, Blocks.DEEPSLATE.defaultBlockState());
            })
        .thenIdle(45)
        .thenExecute(
            () ->
                h.assertTrue(
                    h.getLevel().getBlockState(ore).is(Blocks.DEEPSLATE)
                        && pet.getPickaxe().getDamageValue() == 0
                        && pet.getActivityProgress() == 0,
                    "A replacement block must not inherit the original block's mining damage or"
                        + " award premature drops"))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getBlockState(ore).isAir(),
                    "The new block must finish after its own work interval"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  pet.getBag().getStackInSlot(0).is(Items.COBBLED_DEEPSLATE)
                      && pet.getBag().getStackInSlot(0).getCount() == 1
                      && pet.getPickaxe().getDamageValue() == 1,
                  "Only the replacement's exact drop and one durability point may be produced");
              pet.discard();
              arena.close();
            })
        .thenSucceed();
  }
}
