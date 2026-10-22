package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PrecisionProspectingGameTests {
  private record Fixture(GameTestHelper h, LoadedWorkArea area, NextGenerationMiningSlime slime) {
    BlockPos at(int x, int y, int z) {
      return BlockPos.containing(area.point(x, y, z));
    }
  }

  private static Fixture fixture(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var slime = ModEntities.NEXT_GENERATION_MINING_SLIME.get().create(h.getLevel());
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++)
        for (int y = 90; y <= 104; y++)
          h.getLevel()
              .setBlock(
                  BlockPos.containing(area.point(x, y, z)), Blocks.STONE.defaultBlockState(), 2);
    for (int x = 4; x < 6; x++)
      for (int z = 6; z < 8; z++)
        for (int y = 101; y < 103; y++)
          h.getLevel()
              .setBlock(
                  BlockPos.containing(area.point(x, y, z)), Blocks.AIR.defaultBlockState(), 2);
    owner.moveTo(area.point(5, 101, 7));
    slime.tame(owner);
    slime.moveTo(area.point(5, 101, 7));
    slime.onReleased();
    slime.setPolicy(NextGenerationMiningSlime.Policy.PRECISION_PROSPECTING);
    for (int i = 0; i < NextGenerationMiningSlime.ORES.size(); i++) if (i != 6) slime.toggleOre(i);
    slime.setTool(0, new ItemStack(Items.DIAMOND_PICKAXE));
    slime.setTunedStat(SlimeStat.WORK_SPEED, 64);
    slime.setWorking(true);
    return new Fixture(h, area, slime);
  }

  private static void add(Fixture f) {
    f.h.getLevel().addFreshEntity(f.slime);
  }

  @GameTest(template = "empty", batch = "precision", timeoutTicks = 800)
  public static void shaftDescendsAsATwoByTwoOpeningAndActuallyMoves(GameTestHelper h) {
    var f = fixture(h);
    var before = f.slime.position();
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(f.slime.blockPosition()), "Loaded"))
        .thenExecute(() -> add(f))
        .thenWaitUntil(() -> h.assertTrue(f.slime.getY() < 99.2, "Slime descends physically"))
        .thenExecute(
            () -> {
              for (int x = 4; x < 6; x++)
                for (int z = 6; z < 8; z++)
                  h.assertTrue(
                      h.getLevel().getBlockState(f.at(x, 100, z)).isAir(),
                      "Entire two-by-two shaft is opened");
              h.assertTrue(
                  h.getLevel().getBlockState(f.at(3, 100, 6)).is(Blocks.STONE)
                      && h.getLevel().getBlockState(f.at(6, 100, 6)).is(Blocks.STONE),
                  "Shaft does not widen");
              h.assertTrue(
                  f.slime.getBbWidth() < 1 && f.slime.getBbHeight() < 1, "Compact physical size");
              f.slime.discard();
              f.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "precision", timeoutTicks = 1000)
  public static void lavaBelowTheShaftCausesASafeHorizontalBypass(GameTestHelper h) {
    var f = fixture(h);
    var lava = f.at(4, 98, 6);
    h.getLevel().setBlock(lava, Blocks.LAVA.defaultBlockState(), 2);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(f.slime.blockPosition()), "Loaded"))
        .thenExecute(() -> add(f))
        .thenWaitUntil(
            () -> h.assertTrue(f.slime.getY() < 98.2, "Safe descent resumes beside lava"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockState(lava).is(Blocks.LAVA),
                  "Lava was bypassed, not removed");
              h.assertTrue(
                  !f.slime.isOnFire() && f.slime.getHealth() == f.slime.getMaxHealth(),
                  "No lava or fall damage");
              h.assertTrue(
                  f.slime.prospecting().shaftFront().getX() != f.at(4, 100, 6).getX()
                      || f.slime.prospecting().shaftFront().getZ() != f.at(4, 100, 6).getZ(),
                  "Shaft moved to another column");
              f.slime.discard();
              f.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "precision", timeoutTicks = 1000)
  public static void unsupportedCavityIsBypassedWithoutFallingIntoIt(GameTestHelper h) {
    var f = fixture(h);
    for (int x = 3; x < 7; x++)
      for (int z = 5; z < 9; z++)
        for (int y = 94; y <= 98; y++)
          h.getLevel().setBlock(f.at(x, y, z), Blocks.AIR.defaultBlockState(), 2);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(f.slime.blockPosition()), "Loaded"))
        .thenExecute(() -> add(f))
        .thenWaitUntil(
            () -> h.assertTrue(f.slime.getY() < 98.2, "Bypass reaches a supported descent"))
        .thenExecute(
            () -> {
              h.assertTrue(f.slime.getHealth() == f.slime.getMaxHealth(), "No unsupported fall");
              h.assertTrue(
                  h.getLevel().getBlockState(f.at(4, 99, 6)).is(Blocks.STONE),
                  "Roof over the cavity remains intact");
              f.slime.discard();
              f.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "precision")
  public static void legacySelectionPausesAndNewSavedPoliciesAndIdentityRemainCompatible(
      GameTestHelper h) {
    var f = fixture(h);
    var tag = f.slime.saveWithoutId(new CompoundTag());
    var id = f.slime.getUUID();
    tag.remove("PrecisionProspecting");
    var restored = ModEntities.NEXT_GENERATION_MINING_SLIME.get().create(h.getLevel());
    restored.load(tag);
    h.assertTrue(
        restored.getUUID().equals(id)
            && restored.policy() == NextGenerationMiningSlime.Policy.PRECISION_PROSPECTING
            && !restored.isWorking()
            && restored.isOrderedToSit(),
        "Legacy destructive policy requires explicit restart and retains identity");
    h.assertTrue(
        restored.getPickaxe().is(Items.DIAMOND_PICKAXE) && restored.oreMask() == (1 << 6),
        "Old tool and ore mask survive");
    f.slime.discard();
    f.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "precision")
  public static void allPoliciesUseTheAncientHalfCostPassive(GameTestHelper h) throws Exception {
    var slime = ModEntities.NEXT_GENERATION_MINING_SLIME.get().create(h.getLevel());
    var method = NextGenerationMiningSlime.class.getDeclaredMethod("spendStamina", float.class);
    method.setAccessible(true);
    for (var policy : NextGenerationMiningSlime.Policy.values()) {
      slime.setPolicy(policy);
      float before = slime.getStamina();
      h.assertTrue(
          (boolean) method.invoke(slime, 8f) && slime.getStamina() == before - 4,
          "Half stamina cost in " + policy);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "precision", timeoutTicks = 1600)
  public static void higherHiddenOreGetsACompactRisingBranchAndReturnRoute(GameTestHelper h) {
    var f = fixture(h);
    var ore = f.at(9, 103, 6);
    h.getLevel().setBlock(ore, Blocks.DIAMOND_ORE.defaultBlockState(), 2);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(f.slime.blockPosition()), "Loaded"))
        .thenExecute(() -> add(f))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getBlockState(ore).isAir(), "Higher hidden ore is reached"))
        .thenWaitUntil(
            () -> h.assertTrue(f.slime.getY() < 101, "Slime returns down its excavated branch"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  f.slime.getHealth() == f.slime.getMaxHealth(),
                  "Rising branch and return cause no fall damage");
              f.slime.discard();
              f.area.close();
            })
        .thenSucceed();
  }

  private static void cleanup(GameTestHelper h, Runnable task) {
    try {
      var field = GameTestHelper.class.getDeclaredField("testInfo");
      field.setAccessible(true);
      ((GameTestInfo) field.get(h))
          .addListener(
              new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) {}

                public void testPassed(GameTestInfo info) {
                  task.run();
                }

                public void testFailed(GameTestInfo info) {
                  task.run();
                }
              });
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @GameTest(template = "empty", batch = "precision", timeoutTicks = 400)
  public static void anEmptyOreMaskDoesNotStartDestructiveShaftWork(GameTestHelper h) {
    var f = fixture(h);
    f.slime.toggleOre(6);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(f.slime.blockPosition()), "Loaded"))
        .thenExecute(() -> add(f))
        .thenIdle(50)
        .thenExecute(
            () -> {
              h.assertTrue(
                  f.slime.getMiningStatus() == MiningSlime.Status.COMPLETE,
                  "Empty selection is complete");
              h.assertTrue(
                  f.slime.getPickaxe().getDamageValue() == 0
                      && f.slime.getBag().occupiedSlots().isEmpty(),
                  "No tool use or cargo");
              for (int x = 4; x < 6; x++)
                for (int z = 6; z < 8; z++)
                  h.assertTrue(
                      h.getLevel().getBlockState(f.at(x, 100, z)).is(Blocks.STONE),
                      "Shaft floor remains");
              f.slime.discard();
              f.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "precision", timeoutTicks = 700)
  public static void anUnavoidableCavityKeepsAStableBlockedReasonAndSolidFloor(GameTestHelper h) {
    var f = fixture(h);
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) {
        h.getLevel().setBlock(f.at(x, 99, z), Blocks.AIR.defaultBlockState(), 2);
        if (x == 0 || x == 15 || z == 0 || z == 15)
          for (int y = 100; y < 104; y++)
            h.getLevel().setBlock(f.at(x, y, z), Blocks.BEDROCK.defaultBlockState(), 2);
      }
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(f.slime.blockPosition()), "Loaded"))
        .thenExecute(() -> add(f))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    f.slime.getMiningStatus() == MiningSlime.Status.UNREACHABLE
                        && f.slime.getBlockedWorkTicks() >= 200,
                    "Unsafe episode remains eligible for signaling"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  f.slime.getY() >= 101 && f.slime.getPickaxe().getDamageValue() == 0,
                  "No drop into the cavity");
              for (int x = 4; x < 6; x++)
                for (int z = 6; z < 8; z++)
                  h.assertTrue(
                      h.getLevel().getBlockState(f.at(x, 100, z)).is(Blocks.STONE),
                      "No unsafe floor opening");
              f.slime.discard();
              f.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "precision", timeoutTicks = 500)
  public static void aFullBagPreservesTheBlockUntilPhysicalSpaceIsAvailable(GameTestHelper h) {
    var f = fixture(h);
    for (int slot = 0; slot < f.slime.getBag().getSlots(); slot++)
      f.slime.getBag().setStackInSlot(slot, new ItemStack(Items.BEDROCK, 64));
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(f.slime.blockPosition()), "Loaded"))
        .thenExecute(() -> add(f))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    f.slime.getMiningStatus() == MiningSlime.Status.FULL,
                    "Cargo blocks the action"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  f.slime.getPickaxe().getDamageValue() == 0
                      && h.getLevel().getBlockState(f.at(4, 100, 6)).is(Blocks.STONE),
                  "No destructive action before cargo fits");
              f.slime.getBag().extractItem(0, 64, false);
            })
        .thenWaitUntil(
            () -> h.assertTrue(f.slime.getY() < 100.3, "Freeing cargo resumes physical descent"))
        .thenExecute(
            () -> {
              f.slime.discard();
              f.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "precision", timeoutTicks = 500)
  public static void aCancelledForgeBreakConsumesNeitherToolNorBlock(GameTestHelper h) {
    var f = fixture(h);
    var hits = new java.util.concurrent.atomic.AtomicInteger();
    java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.BreakEvent> deny =
        e -> {
          if (e.getPlayer().getUUID().equals(f.slime.getOwnerUUID())) {
            hits.incrementAndGet();
            e.setCanceled(true);
          }
        };
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(deny);
    cleanup(h, () -> net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(deny));
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(f.slime.blockPosition()), "Loaded"))
        .thenExecute(() -> add(f))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    hits.get() > 0 && f.slime.getBlockedWorkTicks() >= 20,
                    "Real Forge veto is reported"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  f.slime.getPickaxe().getDamageValue() == 0
                      && f.slime.getBag().occupiedSlots().isEmpty(),
                  "Veto has no wear or loot");
              h.assertTrue(
                  h.getLevel().getBlockState(f.at(4, 100, 6)).is(Blocks.STONE),
                  "Protected block remains");
              f.slime.discard();
              f.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "precision", timeoutTicks = 500)
  public static void aWaitCommandInTheBreakCallbackCancelsThePendingAction(GameTestHelper h) {
    var f = fixture(h);
    var hits = new java.util.concurrent.atomic.AtomicInteger();
    java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.BreakEvent> command =
        e -> {
          if (e.getPlayer().getUUID().equals(f.slime.getOwnerUUID())) {
            hits.incrementAndGet();
            f.slime.setWaiting(true);
          }
        };
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(command);
    cleanup(h, () -> net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(command));
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(f.slime.blockPosition()), "Loaded"))
        .thenExecute(() -> add(f))
        .thenWaitUntil(
            () -> h.assertTrue(hits.get() > 0 && f.slime.isOrderedToSit(), "Callback issues Wait"))
        .thenIdle(10)
        .thenExecute(
            () -> {
              h.assertTrue(
                  f.slime.getPickaxe().getDamageValue() == 0
                      && f.slime.getBag().occupiedSlots().isEmpty(),
                  "Stopped action creates no wear or loot");
              h.assertTrue(
                  h.getLevel().getBlockState(f.at(4, 100, 6)).is(Blocks.STONE),
                  "No block is destroyed after Wait");
              f.slime.discard();
              f.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "precision", timeoutTicks = 1200)
  public static void filterChangesKeepTheShaftAndRetargetTheRequestedOre(GameTestHelper h) {
    var f = fixture(h);
    var diamond = f.at(2, 100, 6);
    var coal = f.at(9, 100, 8);
    h.getLevel().setBlock(diamond, Blocks.DIAMOND_ORE.defaultBlockState(), 2);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(f.slime.blockPosition()), "Loaded"))
        .thenExecute(() -> add(f))
        .thenWaitUntil(() -> h.assertTrue(f.slime.getY() < 100.2, "Initial shaft exists"))
        .thenExecute(
            () -> {
              f.slime.setWaiting(true);
              var shaft = f.slime.prospecting().shaftFront();
              f.slime.toggleOre(6);
              f.slime.toggleOre(0);
              h.assertTrue(
                  f.slime.prospecting().shaftFront().equals(shaft),
                  "Filters do not reanchor the shaft");
              h.getLevel().setBlock(coal, Blocks.COAL_ORE.defaultBlockState(), 2);
              f.slime.setWorking(true);
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getBlockState(coal).isAir(), "New selected ore is recovered"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockState(diamond).is(Blocks.DIAMOND_ORE),
                  "Removed target on the other side is not pursued");
              f.slime.discard();
              f.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "precision", timeoutTicks = 800)
  public static void aSaveDuringShaftExcavationRetainsIdentityAndResumesThePendingLevel(
      GameTestHelper h) {
    var f = fixture(h);
    var saved = new CompoundTag[1];
    var restored = new NextGenerationMiningSlime[1];
    var id = f.slime.getUUID();
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(f.slime.blockPosition()), "Loaded"))
        .thenExecute(() -> add(f))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    f.slime.getPickaxe().getDamageValue() >= 1, "Shaft excavation started"))
        .thenExecute(
            () -> {
              saved[0] = f.slime.saveWithoutId(new CompoundTag());
              f.slime.discard();
            })
        .thenIdle(2)
        .thenExecute(
            () -> {
              restored[0] = ModEntities.NEXT_GENERATION_MINING_SLIME.get().create(h.getLevel());
              restored[0].load(saved[0]);
              h.assertTrue(
                  restored[0].getUUID().equals(id) && restored[0].oreMask() == (1 << 6),
                  "Identity and filter survive native serialization");
              restored[0].onReleased();
              h.getLevel().addFreshEntity(restored[0]);
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    restored[0].getY() < 100.2, "Pending descent resumes without being stranded"))
        .thenExecute(
            () -> {
              restored[0].discard();
              f.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "precision", timeoutTicks = 1200)
  public static void aContainerAlongTheOreRouteIsBypassedWithoutLosingItsCargo(GameTestHelper h) {
    var f = fixture(h);
    var chest = f.at(7, 100, 6);
    var ore = f.at(10, 100, 6);
    h.getLevel().setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
    ((net.minecraft.world.level.block.entity.ChestBlockEntity) h.getLevel().getBlockEntity(chest))
        .setItem(0, new ItemStack(Items.DIAMOND, 7));
    h.getLevel().setBlock(ore, Blocks.DIAMOND_ORE.defaultBlockState(), 2);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(f.slime.blockPosition()), "Loaded"))
        .thenExecute(() -> add(f))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getBlockState(ore).isAir(),
                    "A safe path reaches beyond the chest"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockEntity(chest)
                      instanceof net.minecraft.world.level.block.entity.ChestBlockEntity,
                  "Existing container remains");
              h.assertTrue(
                  ((net.minecraft.world.level.block.entity.ChestBlockEntity)
                              h.getLevel().getBlockEntity(chest))
                          .getItem(0)
                          .getCount()
                      == 7,
                  "Existing physical cargo remains intact");
              f.slime.discard();
              f.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "precision", timeoutTicks = 1000)
  public static void hotMagmaFloorIsAvoidedWithoutTakingContactDamage(GameTestHelper h) {
    var f = fixture(h);
    var magma = f.at(4, 98, 6);
    h.getLevel().setBlock(magma, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(f.slime.blockPosition()), "Loaded"))
        .thenExecute(() -> add(f))
        .thenWaitUntil(() -> h.assertTrue(f.slime.getY() < 98.2, "Magma floor is bypassed"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockState(magma).is(Blocks.MAGMA_BLOCK),
                  "Magma block remains outside the passage");
              h.assertTrue(f.slime.getHealth() == f.slime.getMaxHealth(), "No hot-floor damage");
              f.slime.discard();
              f.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "precision_no_load", timeoutTicks = 600)
  public static void extremeDetectionNeverAcquiresUnloadedSurveyChunks(GameTestHelper h) {
    var f = fixture(h);
    SlimeStat.DETECTION_RANGE.tune(f.slime, 1e300);
    var unloaded = f.at(4, 100, -256);
    int[] count = {0};
    var tickets = new java.util.HashSet<Long>();
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(f.slime.blockPosition()), "Loaded"))
        .thenIdle(30)
        .thenExecute(
            () -> {
              h.assertTrue(
                  !h.getLevel().hasChunkAt(unloaded), "Distant chunk starts genuinely unloaded");
              count[0] = h.getLevel().getChunkSource().getLoadedChunksCount();
              tickets.addAll(h.getLevel().getForcedChunks());
              add(f);
            })
        .thenIdle(320)
        .thenExecute(
            () -> {
              h.assertTrue(
                  !h.getLevel().hasChunkAt(unloaded), "Survey leaves the unloaded chunk absent");
              h.assertTrue(
                  h.getLevel().getChunkSource().getLoadedChunksCount() <= count[0]
                      && tickets.equals(h.getLevel().getForcedChunks()),
                  "Prospecting adds neither loaded chunks nor tickets");
              f.slime.discard();
              f.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "precision", timeoutTicks = 1200)
  public static void floatingGravelOverACavityIsRejectedBeforeOpeningTheShaftFloor(
      GameTestHelper h) {
    var f = fixture(h);
    for (int x = 3; x < 7; x++)
      for (int z = 5; z < 9; z++)
        h.getLevel().setBlock(f.at(x, 99, z), Blocks.GRAVEL.defaultBlockState(), 2);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(f.slime.blockPosition()), "Loaded"))
        .thenIdle(20)
        .thenExecute(
            () -> {
              // Simulate world-generated unsupported gravel without notifying its neighbors first.
              for (int x = 3; x < 7; x++)
                for (int z = 5; z < 9; z++)
                  for (int y = 94; y < 99; y++)
                    h.getLevel().setBlock(f.at(x, y, z), Blocks.AIR.defaultBlockState(), 18);
              add(f);
            })
        .thenWaitUntil(() -> h.assertTrue(f.slime.getY() < 98.2, "A stable column is used instead"))
        .thenExecute(
            () -> {
              for (int x = 4; x < 6; x++)
                for (int z = 6; z < 8; z++)
                  h.assertTrue(
                      h.getLevel().getBlockState(f.at(x, 100, z)).is(Blocks.STONE),
                      "Unstable floor is detected before its cover is mined");
              h.assertTrue(f.slime.getHealth() == f.slime.getMaxHealth(), "No collapse damage");
              f.slime.discard();
              f.area.close();
            })
        .thenSucceed();
  }
}
