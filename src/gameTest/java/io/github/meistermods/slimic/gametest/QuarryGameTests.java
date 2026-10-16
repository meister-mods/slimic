package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.QuarryPlan;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.recipe.MiningSlimeRecipe;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class QuarryGameTests {
  private static final BlockPos PET = new BlockPos(5, 2, 5);

  private QuarryGameTests() {}

  @GameTest(template = "empty")
  public static void perimeterRampHasTwoWideTreadsAndFlatCornerLandings(GameTestHelper helper) {
    QuarryPlan plan = new QuarryPlan(new ChunkPos(0, 0), 100, 102);
    for (int[] corner : new int[][] {{0, 0}, {14, 0}, {14, 14}, {0, 14}}) {
      int phase = QuarryPlan.rampPhase(corner[0], corner[1]);
      for (int x = corner[0]; x < corner[0] + 2; x++)
        for (int z = corner[1]; z < corner[1] + 2; z++)
          helper.assertTrue(
              QuarryPlan.rampPhase(x, z) == phase
                  && plan.isRampFloor(new BlockPos(x, 100 - phase / 2, z)),
              "Every corner must have a flat 2x2 landing");
    }
    for (int x = 2; x <= 13; x++) {
      int floor = 100 - QuarryPlan.rampPhase(x, 0) / 2;
      helper.assertTrue(
          plan.isRampFloor(new BlockPos(x, floor, 0))
              && plan.isRampFloor(new BlockPos(x, floor, 1))
              && plan.isRampFloor(new BlockPos(x, floor - 28, 0)),
          "Straight steps must remain two blocks wide across rotations");
    }
    helper.assertTrue(
        !plan.isRampFloor(new BlockPos(8, 100, 8)) && !plan.isRampFloor(new BlockPos(16, 100, 0)),
        "The ramp must not reserve the interior or cross the chunk edge");
    for (int i = 0; i < 23; i++) plan.descend();
    QuarryPlan restored = QuarryPlan.load(new ChunkPos(0, 0), plan.save(), -64, 320);
    helper.assertTrue(
        restored.layer() == plan.layer() && restored.topFloor() == plan.topFloor(),
        "Layer progress and the ramp origin must survive saves");
    var level = helper.getLevel();
    BlockPos marker = helper.absolutePos(BlockPos.ZERO);
    int cx = (marker.getX() + 4096) >> 4, cz = (marker.getZ() + 4096) >> 4;
    forceRegion(level, cx, cz, true);
    QuarryPlan stairs = new QuarryPlan(new ChunkPos(cx, cz), 99, 96);
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++)
        for (int y = 96; y <= 104; y++) {
          BlockPos pos = new BlockPos(cx * 16 + x, y, cz * 16 + z);
          level.setBlockAndUpdate(
              pos,
              (y == 96 ? Blocks.BEDROCK : stairs.isRampFloor(pos) ? Blocks.STONE : Blocks.AIR)
                  .defaultBlockState());
        }
    MiningSlime probe = ModEntities.MINING_SLIME.get().create(level);
    probe.setWorking(true);
    probe.moveTo(cx * 16 + 8, 97, cz * 16 + 8);
    probe.onReleased();
    probe.setOnGround(true);
    var exit = probe.getNavigation().createPath(new BlockPos(cx * 16, 100, cz * 16), 0);
    forceRegion(level, cx, cz, false);
    helper.assertTrue(
        exit != null && exit.canReach(),
        "Native navigation must climb the two-wide perimeter staircase");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 160)
  public static void brokenPickaxeAndEmptyShovelUseExactBagSpares(GameTestHelper helper) {
    floor(helper);
    MiningSlime miner = helper.spawn(ModEntities.MINING_SLIME.get(), PET);
    miner.setWorking(true);
    miner.tame(helper.makeMockSurvivalPlayer());
    miner.onReleased();
    ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
    worn.setDamageValue(worn.getMaxDamage() - 1);
    miner.setTool(0, worn);
    ItemStack spare = new ItemStack(Items.DIAMOND_PICKAXE);
    spare.setDamageValue(33);
    spare.enchant(Enchantments.BLOCK_FORTUNE, 2);
    ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);
    shovel.setDamageValue(5);
    miner.getBag().setStackInSlot(0, spare.copy());
    miner.getBag().setStackInSlot(1, shovel.copy());
    helper.setBlock(PET.east(), Blocks.STONE);
    helper.succeedWhen(
        () -> {
          helper.assertTrue(
              ItemStack.matches(miner.getPickaxe(), spare)
                  && ItemStack.matches(miner.getShovel(), shovel),
              "Native equipment must reuse the exact worn/enchanted spare tools");
          for (int slot = 0; slot < miner.getBag().getSlots(); slot++)
            helper.assertTrue(
                !MiningSlime.isPickaxe(miner.getBag().getStackInSlot(slot))
                    && !MiningSlime.isShovel(miner.getBag().getStackInSlot(slot)),
                "Each spare must be removed exactly once");
          miner.setWorking(false);
        });
  }

  @GameTest(template = "empty")
  public static void craftedMiningItemSummonsWithItsActualPickaxe(GameTestHelper helper) {
    floor(helper);
    Player owner = helper.makeMockSurvivalPlayer();
    ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
    tool.setDamageValue(73);
    tool.enchant(Enchantments.SILK_TOUCH, 1);
    ItemStack summon =
        MiningSlimeRecipe.specialize(new ItemStack(ModItems.FRIENDLY_SLIME.get()), tool);
    use(helper, owner, summon);
    MiningSlime miner = helper.getEntities(ModEntities.MINING_SLIME.get(), PET, 5).get(0);
    helper.assertTrue(
        ItemStack.matches(miner.getPickaxe(), tool) && miner.getShovel().isEmpty(),
        "The recipe's tool must be equipped at the actual summon boundary");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void creativeDefaultMiningItemHasEmptyEquipment(GameTestHelper helper) {
    floor(helper);
    Player owner = helper.makeMockSurvivalPlayer();
    owner.getAbilities().instabuild = true;
    ItemStack summon = new ItemStack(ModItems.MINING_SLIME.get());
    use(helper, owner, summon);
    MiningSlime miner = helper.getEntities(ModEntities.MINING_SLIME.get(), PET, 5).get(0);
    helper.assertTrue(
        miner.getPickaxe().isEmpty() && miner.getShovel().isEmpty() && summon.getCount() == 1,
        "Creative-tab defaults must use empty tool slots and retain normal creative reuse");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void sameChunkKeepsProgressAndNewChunkRestartsWork(GameTestHelper helper) {
    MiningSlime miner = ModEntities.MINING_SLIME.get().create(helper.getLevel());
    miner.setWorking(true);
    miner.moveTo(helper.absoluteVec(Vec3.atCenterOf(PET)));
    miner.tame(helper.makeMockSurvivalPlayer());
    miner.onReleased();
    miner.setTool(0, new ItemStack(Items.IRON_PICKAXE));
    miner.setTool(1, new ItemStack(Items.IRON_SHOVEL));
    miner.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 7));
    miner.setHealth(7);
    miner.setWorking(false);
    miner.setWaiting(true);
    QuarryPlan previous = miner.getQuarryPlan();
    previous.descend();
    previous.descend();
    CompoundTag saved = miner.saveWithoutId(new CompoundTag());
    miner.load(saved);
    miner.onReleased();
    helper.assertTrue(
        miner.isOrderedToSit() && miner.getQuarryPlan().layer() == previous.layer(),
        "Releasing in the same chunk must retain the order and layer");
    ChunkPos old = miner.getMiningChunk();
    miner.moveTo((old.x + 1) * 16 + 8, miner.getY(), old.z * 16 + 8);
    miner.onReleased();
    helper.assertTrue(
        miner.isWorking()
            && !miner.isOrderedToSit()
            && miner.getMiningChunk().equals(miner.chunkPosition())
            && miner.getQuarryPlan().layer() == miner.getQuarryPlan().topFloor() + 2,
        "A different chunk must start a new quarry automatically");
    helper.assertTrue(
        miner.getHealth() == 7
            && miner.getBag().getStackInSlot(0).getCount() == 7
            && miner.getShovel().is(Items.IRON_SHOVEL),
        "Changing work areas must not reset traits, cargo, or tools");
    helper.succeed();
  }

  @GameTest(template = "empty", batch = "quarry_integration", timeoutTicks = 30000)
  public static void excavatesWholeChunkAndLeavesAnExitRamp(GameTestHelper helper) {
    var level = helper.getLevel();
    BlockPos marker = helper.absolutePos(BlockPos.ZERO);
    int chunkX = (marker.getX() + 1024) >> 4, chunkZ = (marker.getZ() + 1024) >> 4;
    int minX = chunkX * 16, minZ = chunkZ * 16;
    forceRegion(level, chunkX, chunkZ, true);
    level
        .getEntitiesOfClass(
            MiningSlime.class,
            new net.minecraft.world.phys.AABB(minX - 64, -64, minZ - 64, minX + 80, 320, minZ + 80))
        .forEach(MiningSlime::discard);
    helper.runAfterDelay(29990, () -> forceRegion(level, chunkX, chunkZ, false));
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) {
        for (int y = 96; y <= 104; y++)
          level.setBlockAndUpdate(
              new BlockPos(minX + x, y, minZ + z),
              (y == 96
                      ? Blocks.BEDROCK
                      : y == 97
                          ? Blocks.DIRT
                          : y == 98 ? Blocks.STONE : y == 99 ? Blocks.DIRT : Blocks.AIR)
                  .defaultBlockState());
      }
    MiningSlime miner = ModEntities.MINING_SLIME.get().create(level);
    miner.setWorking(true);
    miner.moveTo(minX + 8, 100, minZ + 8);
    miner.tame(helper.makeMockSurvivalPlayer());
    ItemStack pickaxe = new ItemStack(Items.NETHERITE_PICKAXE);
    pickaxe.enchant(Enchantments.BLOCK_EFFICIENCY, 5);
    ItemStack shovel = new ItemStack(Items.NETHERITE_SHOVEL);
    shovel.enchant(Enchantments.BLOCK_EFFICIENCY, 5);
    miner.setTool(0, pickaxe);
    miner.setTool(1, shovel);
    SlimeStat.CAPACITY.tune(miner, 27);
    SlimeStat.STAMINA.tune(miner, 2000);
    SlimeStat.WORK_SPEED.tune(miner, 32);
    SlimeStat.MOVEMENT_SPEED.tune(miner, 0.3);
    miner.setTunedVitals(16, 2000, 0);
    miner.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 16));
    level.addFreshEntity(miner);
    miner.onReleased();
    BlockPos gap = miner.getQuarryPlan().floorsAt(98).get(0);
    level.setBlockAndUpdate(gap, Blocks.AIR.defaultBlockState());
    BlockPos outside = new BlockPos(minX - 1, 99, minZ + 8);
    level.setBlockAndUpdate(outside, Blocks.DIAMOND_ORE.defaultBlockState());
    helper.succeedWhen(
        () -> {
          helper.assertTrue(
              miner.getMiningStatus() == MiningSlime.Status.COMPLETE,
              "Quarry is still working: "
                  + miner.getMiningStatus()
                  + " at "
                  + miner.blockPosition()
                  + " layer "
                  + miner.getQuarryPlan().layer());
          for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++)
              for (int y = 97; y <= 99; y++) {
                BlockPos pos = new BlockPos(minX + x, y, minZ + z);
                helper.assertTrue(
                    miner.getQuarryPlan().isRampFloor(pos)
                        ? level.getBlockState(pos).isCollisionShapeFullBlock(level, pos)
                        : level.getBlockState(pos).isAir(),
                    "Every terrain block except the two-wide ramp must be excavated: " + pos);
              }
          helper.assertTrue(
              level.getBlockState(gap).is(Blocks.COBBLESTONE)
                  && level.getBlockState(outside).is(Blocks.DIAMOND_ORE),
              "Ramp repair must consume supports while respecting the chunk boundary");
          var path = miner.getNavigation().createPath(new BlockPos(minX, 100, minZ), 0);
          helper.assertTrue(
              path != null && path.canReach(),
              "The finished quarry must have a navigable exit to its top landing");
          miner.setWorking(false);
          forceRegion(level, chunkX, chunkZ, false);
        });
  }

  private static void floor(GameTestHelper helper) {
    for (int x = 0; x < 12; x++)
      for (int z = 0; z < 12; z++) helper.setBlock(x, 1, z, Blocks.BEDROCK);
  }

  private static void forceRegion(
      net.minecraft.server.level.ServerLevel level, int x, int z, boolean forced) {
    for (int dx = -1; dx <= 1; dx++)
      for (int dz = -1; dz <= 1; dz++) level.setChunkForced(x + dx, z + dz, forced);
  }

  private static void use(GameTestHelper helper, Player owner, ItemStack stack) {
    owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
    BlockPos floor = helper.absolutePos(PET.below());
    stack
        .getItem()
        .useOn(
            new UseOnContext(
                helper.getLevel(),
                owner,
                InteractionHand.MAIN_HAND,
                stack,
                new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
  }
}
