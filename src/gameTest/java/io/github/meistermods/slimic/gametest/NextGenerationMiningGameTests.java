package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NextGenerationMiningGameTests {
  @GameTest(template = "empty", batch = "next_generation_mining")
  public static void selectiveScannerShowsItsLocalSearchWindowWithoutAQuarryLayer(
      GameTestHelper h) {
    var miner = ModEntities.NEXT_GENERATION_MINING_SLIME.get().create(h.getLevel());
    miner.moveTo(123.5, 101.25, 234.5);
    miner.onReleased();
    miner.setPolicy(NextGenerationMiningSlime.Policy.SELECTIVE_ORE);
    var plan = miner.getQuarryPlan().save();
    int loaded = h.getLevel().getChunkSource().getLoadedChunksCount();
    var forced = Set.copyOf(h.getLevel().getForcedChunks());
    var area = SlimeWorkArea.of(miner);
    h.assertTrue(
        area.planned() && area.shapes().size() == 1,
        "Selective preview has one search window and no invented excavation layer");
    var box = area.shapes().get(0).bounds();
    h.assertTrue(
        box.minY == 93
            && box.maxY == 110
            && box.minX >= 112
            && box.maxX <= 128
            && box.minZ >= 224
            && box.maxZ <= 240
            && !area.shapes().get(0).next(),
        "Detection-height window stays inside the current chunk");
    for (double radius : new double[] {0, 1e300}) {
      SlimeStat.DETECTION_RANGE.tune(miner, radius);
      var shapes = SlimeWorkArea.of(miner).shapes();
      h.assertTrue(shapes.size() == 1, "Extreme search range remains bounded");
      var bounds = shapes.get(0).bounds();
      h.assertTrue(
          bounds.getXsize() <= 16
              && bounds.getZsize() <= 16
              && bounds.minY >= h.getLevel().getMinBuildHeight()
              && bounds.maxY <= h.getLevel().getMaxBuildHeight(),
          "Creative range never expands the preview beyond the chunk or build height");
    }
    h.assertTrue(
        miner.isOrderedToSit()
            && !miner.isWorking()
            && miner.getQuarryPlan().save().equals(plan)
            && loaded == h.getLevel().getChunkSource().getLoadedChunksCount()
            && forced.equals(h.getLevel().getForcedChunks()),
        "Preview does not start work, change the quarry plan or request chunks");
    miner.setPolicy(NextGenerationMiningSlime.Policy.NORMAL);
    h.assertTrue(
        SlimeWorkArea.of(miner).shapes().size() == 2,
        "Normal policy retains the full quarry and next-layer preview");
    h.succeed();
  }

  private record Fixture(
      GameTestHelper h, LoadedWorkArea area, Player owner, NextGenerationMiningSlime miner) {}

  private static Fixture fixture(GameTestHelper h, Item tool, int selected) {
    var area = new LoadedWorkArea(h);
    area.sealSurroundingFluids();
    var owner = h.makeMockSurvivalPlayer();
    owner.moveTo(area.point(4, 101, 4));
    var miner = ModEntities.NEXT_GENERATION_MINING_SLIME.get().create(h.getLevel());
    miner.tame(owner);
    miner.moveTo(area.point(4, 101, 6));
    miner.onReleased();
    if (tool != Items.AIR) miner.setTool(0, new ItemStack(tool));
    miner.setPolicy(NextGenerationMiningSlime.Policy.SELECTIVE_ORE);
    for (int i = 0; i < NextGenerationMiningSlime.ORES.size(); i++)
      if (i != selected) miner.toggleOre(i);
    miner.setWorking(true);
    return new Fixture(h, area, owner, miner);
  }

  private static BlockPos pos(Fixture f, int x, int y, int z) {
    return BlockPos.containing(f.area.point(x, y, z));
  }

  private static void addWhenReady(Fixture f, Runnable after) {
    f.h
        .startSequence()
        .thenWaitUntil(
            () ->
                f.h.assertTrue(
                    f.h.getLevel().isPositionEntityTicking(f.miner.blockPosition()),
                    "Fixture loaded"))
        .thenExecute(
            () -> {
              f.h.getLevel().addFreshEntity(f.miner);
              after.run();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "next_generation_mining")
  public static void policyPageHidesBagOnServerAndCannotStartWorkOrMoveHiddenCargo(
      GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var miner = ModEntities.NEXT_GENERATION_MINING_SLIME.get().create(h.getLevel());
    miner.tame(owner);
    miner.moveTo(owner.position());
    owner.setItemInHand(
        net.minecraft.world.InteractionHand.MAIN_HAND,
        ModItems.SLIME_TUNER.get().getDefaultInstance());
    owner.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 3));
    var menu = new io.github.meistermods.slimic.menu.SlimeMenu(1, owner.getInventory(), miner);
    h.assertTrue(
        menu.clickMenuButton(owner, io.github.meistermods.slimic.menu.SlimeMenu.TARGET_PAGE_BUTTON)
            && menu.isPolicyEditor()
            && !menu.isBagVisible(),
        "Policy page is server authoritative");
    int slot = -1;
    for (int i = 0; i < menu.slots.size(); i++)
      if (menu.slots.get(i).getItem().is(Items.DIAMOND)) slot = i;
    h.assertTrue(
        slot >= 0
            && menu.quickMoveStack(owner, slot).isEmpty()
            && owner.getInventory().getItem(9).getCount() == 3,
        "Hidden cargo cannot receive a shift transfer");
    h.assertTrue(
        menu.clickMenuButton(owner, io.github.meistermods.slimic.menu.SlimeMenu.POLICY_BUTTON + 2)
            && miner.policy() == NextGenerationMiningSlime.Policy.SELECTIVE_ORE
            && miner.isOrderedToSit(),
        "Policy selection keeps Wait");
    h.assertTrue(
        menu.clickMenuButton(owner, io.github.meistermods.slimic.menu.SlimeMenu.TARGET_PAGE_BUTTON)
            && menu.isBagVisible()
            && !menu.quickMoveStack(owner, slot).isEmpty(),
        "Returning to the bag restores transfer");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "next_generation_mining", timeoutTicks = 500)
  public static void hiddenSelectedOreStaysBlockedUntilExposedWithoutExcavatingItsShell(
      GameTestHelper h) {
    var f = fixture(h, Items.DIAMOND_PICKAXE, 6);
    var ore = pos(f, 7, 101, 6);
    var shell = new HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
    for (var direction : Direction.values()) {
      var block = ore.relative(direction);
      var state = Blocks.STONE.defaultBlockState();
      h.getLevel().setBlockAndUpdate(block, state);
      shell.put(block, state);
    }
    h.getLevel().setBlockAndUpdate(ore, Blocks.DIAMOND_ORE.defaultBlockState());
    var opening = ore.west();
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(f.miner.blockPosition()),
                    "Fixture loaded"))
        .thenExecute(() -> h.getLevel().addFreshEntity(f.miner))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    f.miner.getMiningStatus() == MiningSlime.Status.UNREACHABLE
                        && f.miner.getBlockedWorkTicks() >= 60,
                    "Blocked episode persists across incremental rescans"))
        .thenExecute(
            () -> {
              for (var entry : shell.entrySet())
                h.assertTrue(
                    h.getLevel().getBlockState(entry.getKey()).equals(entry.getValue()),
                    "No shell excavation");
              h.getLevel().setBlockAndUpdate(opening, Blocks.AIR.defaultBlockState());
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getBlockState(ore).isAir(), "Exposed selected ore can be mined"))
        .thenExecute(
            () -> {
              for (var entry : shell.entrySet())
                if (!entry.getKey().equals(opening))
                  h.assertTrue(
                      h.getLevel().getBlockState(entry.getKey()).equals(entry.getValue()),
                      "Unselected shell remains");
              h.assertTrue(f.miner.getPickaxe().getDamageValue() == 1, "Real tool wear");
              f.miner.setWaiting(true);
              f.miner.discard();
              f.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "next_generation_mining", timeoutTicks = 250)
  public static void insufficientToolTierCannotDestroySelectedOreAtHighWorkSpeed(GameTestHelper h) {
    var f = fixture(h, Items.WOODEN_PICKAXE, 6);
    f.miner.setTunedStat(SlimeStat.WORK_SPEED, 1e12);
    var ore = pos(f, 5, 101, 6);
    h.getLevel().setBlockAndUpdate(ore, Blocks.DIAMOND_ORE.defaultBlockState());
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(f.miner.blockPosition()),
                    "Fixture loaded"))
        .thenExecute(() -> h.getLevel().addFreshEntity(f.miner))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    f.miner.getMiningStatus() == MiningSlime.Status.NEEDS_TIER
                        && f.miner.getBlockedWorkTicks() >= 40,
                    "Tier reason persists"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockState(ore).is(Blocks.DIAMOND_ORE)
                      && f.miner.getPickaxe().getDamageValue() == 0,
                  "Work Speed never bypasses required tier");
              f.miner.discard();
              f.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "next_generation_mining", timeoutTicks = 500)
  public static void selectiveToolRequestReplacesAnOldShovelHintAndAcceptsWarehouseSupply(
      GameTestHelper h) {
    var f = fixture(h, Items.AIR, 2);
    var raw = f.miner.saveWithoutId(new CompoundTag());
    raw.putInt("MiningRequiredTool", 1);
    f.miner.load(raw);
    f.miner.onReleased();
    f.miner.setWorking(true);
    var ore = pos(f, 5, 101, 6);
    h.getLevel().setBlockAndUpdate(ore, Blocks.IRON_ORE.defaultBlockState());
    var warehouse = ModEntities.WAREHOUSE_SLIME.get().create(h.getLevel());
    warehouse.tame(f.owner);
    warehouse.moveTo(f.area.point(11, 101, 8));
    warehouse.setWaiting(true);
    var tool = new ItemStack(Items.IRON_PICKAXE);
    tool.setDamageValue(7);
    tool.getOrCreateTag().putString("Lot", "royal-supply");
    warehouse.getBag().setStackInSlot(0, tool);
    var carrier = ModEntities.TRANSPORT_SLIME.get().create(h.getLevel());
    carrier.tame(f.owner);
    carrier.moveTo(f.area.point(9, 101, 8));
    carrier.setWorking(true);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(f.miner.blockPosition()),
                    "Fixture loaded"))
        .thenExecute(() -> h.getLevel().addFreshEntity(f.miner))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    f.miner.getMiningStatus() == MiningSlime.Status.NO_TOOL
                        && f.miner.getRequiredToolSlot() == 0,
                    "Current selected ore requests a pickaxe"))
        .thenExecute(
            () -> {
              h.getLevel().addFreshEntity(warehouse);
              h.getLevel().addFreshEntity(carrier);
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getBlockState(ore).isAir(),
                    "Supplied pickaxe resumes selected mining"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  f.miner.getPickaxe().is(Items.IRON_PICKAXE)
                      && f.miner.getPickaxe().getDamageValue() == 8
                      && f.miner.getPickaxe().getTag().getString("Lot").equals("royal-supply"),
                  "Tool identity, data and wear retained");
              h.assertTrue(
                  warehouse.getBag().getStackInSlot(0).isEmpty(),
                  "One physical supply item consumed from warehouse");
              f.miner.discard();
              warehouse.discard();
              carrier.discard();
              f.area.close();
            })
        .thenSucceed();
  }
}
