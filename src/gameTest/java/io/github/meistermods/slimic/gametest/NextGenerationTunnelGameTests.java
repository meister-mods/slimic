package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.RoyalSynthesisStationBlockEntity;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.*;
import io.github.meistermods.slimic.world.RoyalSynthesis;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NextGenerationTunnelGameTests {
  @GameTest(template = "empty", batch = "next_tunnel_border")
  public static void nativeLiningAtLoadedChunkEdgeCannotLoadItsMissingNeighbor(GameTestHelper h)
      throws Exception {
    var area = new LoadedWorkArea(h);
    var base = BlockPos.containing(area.point(8, 101, 8));
    int chunkX = base.getX() >> 4, chunkZ = base.getZ() >> 4;
    int offset = 1;
    while (offset < 32
        && h.getLevel().getChunkSource().getChunkNow(chunkX + offset, chunkZ) != null) offset++;
    h.assertTrue(offset < 32, "Find the actual edge of the currently loaded chunk region");
    var face = new BlockPos(((chunkX + offset - 1) << 4) + 15, 101, base.getZ());
    h.getLevel().setBlock(face.below(), Blocks.BEDROCK.defaultBlockState(), 18);
    h.getLevel().setBlock(face.above(), Blocks.AIR.defaultBlockState(), 18);
    h.getLevel().setBlock(face.north(), Blocks.STONE.defaultBlockState(), 18);
    h.getLevel().setBlock(face.south(), Blocks.STONE.defaultBlockState(), 18);
    h.getLevel().setBlock(face, Blocks.STONE.defaultBlockState(), 18);
    var slime = ModEntities.NEXT_GENERATION_TUNNEL_SLIME.get().create(h.getLevel());
    slime.tame(h.makeMockSurvivalPlayer());
    slime.moveTo(face.getX() - .5, 101, face.getZ() + .5);
    slime.setTunnelDirection(Direction.EAST);
    slime.setTunnelSize(1);
    slime.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
    slime.setWorking(true);
    var tickets = Set.copyOf(h.getLevel().getForcedChunks());
    int chunks = h.getLevel().getChunkSource().getLoadedChunksCount();
    var tick =
        NextGenerationTunnelSlime.class.getDeclaredMethod(
            "tickTunnelWork", net.minecraft.server.level.ServerLevel.class);
    tick.setAccessible(true);
    tick.invoke(slime, h.getLevel());
    h.assertTrue(
        slime.getTunnelStatus() == TunnelSlime.Status.UNREACHABLE
            && count(slime, Items.COBBLESTONE) == 64
            && h.getLevel().getChunkSource().getChunkNow(chunkX + offset, chunkZ) == null
            && chunks == h.getLevel().getChunkSource().getLoadedChunksCount()
            && tickets.equals(h.getLevel().getForcedChunks()),
        "Block notifications cannot create a chunk across an otherwise loaded work cell");
    slime.discard();
    area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "next_tunnel")
  public static void fluidRemovalUsesOneActionEvenWithExtremeCreativeStamina(GameTestHelper h)
      throws Exception {
    var f = fixture(h, 1, 1);
    f.slime.getBag().setStackInSlot(0, new ItemStack(Items.BUCKET));
    SlimeStat.STAMINA.tune(f.slime, 1e30);
    f.slime.setTunedVitals(16, 1e30F, 0);
    f.slime.setWorking(true);
    var target = f.pos(9, 101, 8);
    h.getLevel().setBlockAndUpdate(target, Blocks.WATER.defaultBlockState());
    var mine =
        NextGenerationTunnelSlime.class.getDeclaredMethod(
            "mine", net.minecraft.server.level.ServerLevel.class, BlockPos.class);
    mine.setAccessible(true);
    float stamina = f.slime.getStamina();
    boolean continueThisTick = (boolean) mine.invoke(f.slime, h.getLevel(), target);
    h.assertTrue(
        !continueThisTick
            && h.getLevel().getBlockState(target).isAir()
            && f.slime.getActivityProgress() == 1
            && f.slime.getStamina() == stamina,
        "A real fluid action is bounded even when float stamina subtraction rounds away");
    close(f);
    h.succeed();
  }

  @GameTest(template = "empty", batch = "next_tunnel", timeoutTicks = 250)
  public static void toolRemovalInsideBreakCallbackDoesNotUseDetachedEquipment(GameTestHelper h) {
    var f = fixture(h, 5, 1);
    var target = f.pos(9, 105, 6);
    var tool = f.slime.getPickaxe();
    java.util.function.Consumer<BlockEvent.BreakEvent> remove =
        event -> {
          if (event.getPos().equals(target)) f.slime.setTool(0, ItemStack.EMPTY);
        };
    MinecraftForge.EVENT_BUS.addListener(remove);
    start(f);
    h.runAfterDelay(
        60,
        () -> {
          try {
            h.assertTrue(
                f.slime.getPickaxe().isEmpty()
                    && tool.getDamageValue() == 0
                    && h.getLevel().getBlockState(target).is(Blocks.STONE)
                    && count(f.slime, Items.COBBLESTONE) == 0,
                "A detached tool cannot authorize or receive wear for a completed break");
            close(f);
            h.succeed();
          } finally {
            MinecraftForge.EVENT_BUS.unregister(remove);
          }
        });
  }

  @GameTest(template = "empty", batch = "next_tunnel", timeoutTicks = 1000)
  public static void partialWorldSaveResumesWithoutDuplicatingCargoOrWear(GameTestHelper h) {
    var f = fixture(h, 5, 1);
    start(f);
    NextGenerationTunnelSlime[] restored = {null};
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    f.slime.getPickaxe().getDamageValue() > 0, "A real partial face is saved"))
        .thenExecute(
            () -> {
              var state = f.slime.saveWithoutId(new CompoundTag());
              var id = f.slime.getUUID();
              f.slime.discard();
              restored[0] = ModEntities.NEXT_GENERATION_TUNNEL_SLIME.get().create(h.getLevel());
              restored[0].load(state);
              h.assertTrue(
                  restored[0].getUUID().equals(id)
                      && restored[0].getTunnelSize() == 5
                      && restored[0].isWorking(),
                  "World reload retains identity, size, and explicit order");
              h.getLevel().addFreshEntity(restored[0]);
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    restored[0].getTunnelFront().getX() > f.pos(9, 101, 8).getX(),
                    "Reloaded partial face finishes"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  count(restored[0], Items.COBBLESTONE) == 25
                      && restored[0].getPickaxe().getDamageValue() == 25,
                  "Partial reload neither duplicates nor loses completed work");
              restored[0].setWorking(false);
              restored[0].discard();
              f.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "next_tunnel", timeoutTicks = 250)
  public static void sizeChangeInsideBreakCallbackCancelsTheOldFace(GameTestHelper h) {
    var f = fixture(h, 5, 1);
    var target = f.pos(9, 105, 6);
    java.util.function.Consumer<BlockEvent.BreakEvent> change =
        event -> {
          if (event.getPos().equals(target)) f.slime.setTunnelSize(2);
        };
    MinecraftForge.EVENT_BUS.addListener(change);
    start(f);
    h.runAfterDelay(
        60,
        () -> {
          try {
            h.assertTrue(
                f.slime.getTunnelSize() == 2
                    && f.slime.isOrderedToSit()
                    && !f.slime.isWorking()
                    && h.getLevel().getBlockState(target).is(Blocks.STONE)
                    && f.slime.getPickaxe().getDamageValue() == 0
                    && count(f.slime, Items.COBBLESTONE) == 0,
                "Reentrant size changes preserve the old block and resources in Wait");
            close(f);
            h.succeed();
          } finally {
            MinecraftForge.EVENT_BUS.unregister(change);
          }
        });
  }

  private record Fixture(GameTestHelper h, LoadedWorkArea area, NextGenerationTunnelSlime slime) {
    BlockPos pos(int x, int y, int z) {
      return BlockPos.containing(area.point(x, y, z));
    }
  }

  private static Fixture fixture(GameTestHelper h, int size, int depth) {
    var area = new LoadedWorkArea(h);
    var slime = ModEntities.NEXT_GENERATION_TUNNEL_SLIME.get().create(h.getLevel());
    slime.tame(h.makeMockSurvivalPlayer());
    slime.moveTo(area.point(8.5, 101, 8.5));
    slime.setTunnelDirection(Direction.EAST);
    slime.setTunnelSize(size);
    slime.setTool(0, new ItemStack(Items.NETHERITE_PICKAXE));
    slime.setTool(1, new ItemStack(Items.NETHERITE_SHOVEL));
    SlimeStat.WORK_SPEED.tune(slime, 64);
    SlimeStat.CAPACITY.tune(slime, 27);
    SlimeStat.STAMINA.tune(slime, 2000);
    slime.setTunedVitals(16, 2000, 0);
    int lo = slime.getTunnelMinWidth();
    for (int x = 9; x <= 9 + depth; x++)
      for (int w = lo - 1; w <= lo + size; w++)
        for (int y = 101; y <= 101 + size; y++)
          h.getLevel()
              .setBlockAndUpdate(
                  BlockPos.containing(area.point(x, y, 8 + w)),
                  (x == 9 + depth ? Blocks.BEDROCK : Blocks.STONE).defaultBlockState());
    slime.onReleased();
    return new Fixture(h, area, slime);
  }

  private static void start(Fixture f) {
    f.slime.setWorking(true);
    f.h.getLevel().addFreshEntity(f.slime);
  }

  private static void close(Fixture f) {
    f.slime.setWorking(false);
    f.slime.discard();
    f.area.close();
  }

  private static int count(FriendlySlime slime, Item item) {
    var bag = ((BagSlime) slime).getBag();
    int total = 0;
    for (int slot : bag.occupiedSlots())
      if (bag.getStackInSlot(slot).is(item)) total += bag.getStackInSlot(slot).getCount();
    return total;
  }

  @GameTest(template = "empty", batch = "next_tunnel")
  public static void freshBodyDefaultsAndInvalidSizesPreserveWait(GameTestHelper h) {
    var slime = ModEntities.NEXT_GENERATION_TUNNEL_SLIME.get().create(h.getLevel());
    h.assertTrue(
        slime.getTunnelSize() == 3
            && slime.isOrderedToSit()
            && !slime.isWorking()
            && slime.getBbWidth() < 1
            && slime.getBbHeight() < 1,
        "Compact fresh companion waits at size three");
    for (int size : new int[] {-1, 0, Integer.MAX_VALUE})
      h.assertTrue(
          !slime.setTunnelSize(size) && slime.getTunnelSize() == 3 && slime.isOrderedToSit(),
          "Invalid input preserves state");
    h.assertTrue(
        slime.setTunnelSize(1) && slime.setTunnelSize(slime.maximumTunnelSize()),
        "World-height-bounded positive sizes accepted");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "next_tunnel")
  public static void previewsMatchEvenAndOddFacesInAllDirectionsWithoutLoading(GameTestHelper h) {
    var slime = ModEntities.NEXT_GENERATION_TUNNEL_SLIME.get().create(h.getLevel());
    slime.moveTo(128.5, 100, 128.5);
    var tickets = Set.copyOf(h.getLevel().getForcedChunks());
    int loaded = h.getLevel().getChunkSource().getLoadedChunksCount();
    for (var direction : Direction.Plane.HORIZONTAL)
      for (int size : new int[] {1, 2, 5, 32}) {
        slime.setTunnelDirection(direction);
        slime.setTunnelSize(size);
        var area = SlimeWorkArea.of(slime);
        var box = area.shapes().get(1).bounds();
        h.assertTrue(
            area.planned()
                && area.shapes().size() == 2
                && box.getYsize() == size
                && (direction.getAxis() == Direction.Axis.X ? box.getZsize() : box.getXsize())
                    == size,
            "Scanner uses actual n by n face");
      }
    h.assertTrue(
        tickets.equals(h.getLevel().getForcedChunks())
            && loaded == h.getLevel().getChunkSource().getLoadedChunksCount(),
        "Previews never acquire chunks");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "next_tunnel")
  public static void sizeControlsRequireAuthorizedLiveTunerAndPreserveResources(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var slime = ModEntities.NEXT_GENERATION_TUNNEL_SLIME.get().create(h.getLevel());
    slime.tame(owner);
    slime.moveTo(owner.position());
    slime.setHealth(7);
    slime.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
    var menu = TestMenus.create(1, owner.getInventory(), slime);
    slime.setWorking(true);
    h.assertTrue(
        menu.setTunnelSize(owner, 5)
            && slime.getTunnelSize() == 5
            && slime.isOrderedToSit()
            && !slime.isWorking()
            && slime.getHealth() == 7
            && count(slime, Items.DIAMOND) == 3,
        "Size changes pause without changing resources");
    h.assertTrue(
        menu.clickMenuButton(owner, SlimeMenu.TARGET_PAGE_BUTTON)
            && menu.isPolicyEditor()
            && !menu.isBagVisible(),
        "Size panel hides cargo");
    var stranger = h.makeMockSurvivalPlayer();
    stranger.moveTo(owner.position());
    h.assertTrue(!menu.setTunnelSize(stranger, 7), "Foreign settings rejected");
    owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    h.assertTrue(!menu.setTunnelSize(owner, 7), "Missing tuner rejected");
    owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
    owner.moveTo(slime.getX() + 20, slime.getY(), slime.getZ());
    h.assertTrue(!menu.setTunnelSize(owner, 7), "Stale distant settings rejected");
    h.succeed();
  }

  private static void exactFace(GameTestHelper h, int size, int depth) {
    var f = fixture(h, size, depth);
    start(f);
    h.succeedWhen(
        () -> {
          h.assertTrue(
              f.slime.getTunnelFront().getX() >= f.pos(9 + depth, 101, 8).getX(),
              "Completed faces and physically advanced");
          int lo = f.slime.getTunnelMinWidth();
          for (int x = 9; x < 9 + depth; x++)
            for (int w = lo; w < lo + size; w++)
              for (int y = 101; y < 101 + size; y++)
                h.assertTrue(
                    h.getLevel().getBlockState(f.pos(x, y, 8 + w)).isAir(),
                    "Entire selected square is clear");
          h.assertTrue(
              count(f.slime, Items.COBBLESTONE) == size * size * depth
                  && f.slime.getPickaxe().getDamageValue() == size * size * depth,
              "Every block has exactly one drop and tool use");
          h.assertTrue(
              f.slime.getX() > f.pos(8 + depth, 101, 8).getX() + .15,
              "Worker moves through its bore");
          h.assertTrue(
              h.getLevel().getBlockState(f.pos(9, 101, 8 + lo - 1)).is(Blocks.STONE)
                  && h.getLevel().getBlockState(f.pos(9, 101 + size, 8)).is(Blocks.STONE),
              "Sides and roof are preserved");
          close(f);
        });
  }

  @GameTest(template = "empty", batch = "next_tunnel", timeoutTicks = 1200)
  public static void fiveByFiveIsExcavatedWithExactLootAndPhysicalAdvance(GameTestHelper h) {
    exactFace(h, 5, 1);
  }

  @GameTest(template = "empty", batch = "next_tunnel", timeoutTicks = 1200)
  public static void compactBodyTraversesThreeOneByOneFaces(GameTestHelper h) {
    exactFace(h, 1, 3);
  }

  @GameTest(template = "empty", batch = "next_tunnel", timeoutTicks = 1200)
  public static void evenTwoByTwoFaceUsesFourBlocks(GameTestHelper h) {
    exactFace(h, 2, 1);
  }

  @GameTest(template = "empty", batch = "next_tunnel", timeoutTicks = 1600)
  public static void sevenBySevenExceedsLegacyMaskWidth(GameTestHelper h) {
    exactFace(h, 7, 1);
  }

  @GameTest(template = "empty", batch = "next_tunnel", timeoutTicks = 700)
  public static void fullCargoPreservesBlocksAndToolUntilSpaceIsFreed(GameTestHelper h) {
    var f = fixture(h, 5, 1);
    for (int i = 0; i < f.slime.getBag().getSlots(); i++)
      f.slime.getBag().setStackInSlot(i, new ItemStack(Items.DIRT, 64));
    start(f);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    f.slime.getTunnelStatus() == TunnelSlime.Status.FULL, "Full cargo reported"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  f.slime.getPickaxe().getDamageValue() == 0, "Full cargo does not wear tool");
              h.assertTrue(
                  h.getLevel().getBlockState(f.pos(9, 105, 6)).is(Blocks.STONE),
                  "Pending block preserved");
              f.slime.getBag().extractItem(0, 64, false);
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    f.slime.getTunnelFront().getX() > f.pos(9, 101, 8).getX(),
                    "Space resumes excavation"))
        .thenExecute(
            () -> {
              h.assertTrue(count(f.slime, Items.COBBLESTONE) == 25, "Real cargo collected");
              close(f);
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "next_tunnel", timeoutTicks = 1000)
  public static void warehouseSuppliesRealLiningForAnExpandedRoof(GameTestHelper h) {
    var f = fixture(h, 5, 1);
    var hole = f.pos(9, 106, 6);
    h.getLevel().setBlockAndUpdate(hole, Blocks.AIR.defaultBlockState());
    var carrier = ModEntities.WAREHOUSE_SLIME.get().create(h.getLevel());
    carrier.setOwnerUUID(f.slime.getOwnerUUID());
    carrier.setTame(true);
    carrier.moveTo(f.area.point(5.5, 101, 8.5));
    carrier.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
    carrier.setWorking(true);
    carrier.onReleased();
    start(f);
    h.getLevel().addFreshEntity(carrier);
    h.succeedWhen(
        () -> {
          h.assertTrue(
              h.getLevel().getBlockState(hole).is(Blocks.COBBLESTONE),
              "Expanded roof is lined with supplied material");
          h.assertTrue(
              f.slime.getTunnelFront().getX() > f.pos(9, 101, 8).getX(),
              "Bore resumes after delivery");
          h.assertTrue(
              count(f.slime, Items.COBBLESTONE) + count(carrier, Items.COBBLESTONE) == 88,
              "One lining block consumed; 25 actual drops added");
          carrier.discard();
          close(f);
        });
  }

  @GameTest(template = "empty", batch = "next_tunnel", timeoutTicks = 250)
  public static void missingTierRemainsBlockedDespiteExtremeWorkSpeed(GameTestHelper h) {
    var f = fixture(h, 5, 1);
    f.slime.setTool(0, new ItemStack(Items.WOODEN_PICKAXE));
    h.getLevel().setBlockAndUpdate(f.pos(9, 105, 6), Blocks.DIAMOND_ORE.defaultBlockState());
    start(f);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    f.slime.getTunnelStatus() == TunnelSlime.Status.NEEDS_TIER,
                    "Tier shortage reported"))
        .thenIdle(50)
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockState(f.pos(9, 105, 6)).is(Blocks.DIAMOND_ORE)
                      && f.slime.getPickaxe().getDamageValue() == 0
                      && f.slime.getBlockedWorkTicks() >= 40,
                  "No tier bypass or false progress");
              close(f);
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "next_tunnel", timeoutTicks = 250)
  public static void forgeCancellationProtectsExpandedFaceWithoutWear(GameTestHelper h) {
    var f = fixture(h, 5, 1);
    var target = f.pos(9, 105, 6);
    java.util.function.Consumer<BlockEvent.BreakEvent> veto =
        e -> {
          if (e.getPos().equals(target)) e.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(veto);
    start(f);
    h.runAfterDelay(
        80,
        () -> {
          try {
            h.assertTrue(
                h.getLevel().getBlockState(target).is(Blocks.STONE)
                    && f.slime.getPickaxe().getDamageValue() == 0
                    && count(f.slime, Items.COBBLESTONE) == 0,
                "Veto retains all resources");
            close(f);
            h.succeed();
          } finally {
            MinecraftForge.EVENT_BUS.unregister(veto);
          }
        });
  }

  @GameTest(template = "empty", batch = "next_tunnel")
  public static void largeFluidBitsAndSettingsSurviveNativeSave(GameTestHelper h) throws Exception {
    var slime = ModEntities.NEXT_GENERATION_TUNNEL_SLIME.get().create(h.getLevel());
    slime.moveTo(128.5, 100, 128.5);
    slime.setTunnelDirection(Direction.EAST);
    slime.setTunnelSize(8);
    var mark =
        NextGenerationTunnelSlime.class.getDeclaredMethod("rememberDrainedFluid", BlockPos.class);
    mark.setAccessible(true);
    var read = NextGenerationTunnelSlime.class.getDeclaredMethod("wasFluidDrained", BlockPos.class);
    read.setAccessible(true);
    var front = slime.getTunnelFront();
    var low = front.relative(Direction.SOUTH, -2);
    var high = low.above(4);
    mark.invoke(slime, low);
    mark.invoke(slime, high);
    var restored = ModEntities.NEXT_GENERATION_TUNNEL_SLIME.get().create(h.getLevel());
    restored.load(slime.saveWithoutId(new CompoundTag()));
    h.assertTrue(
        restored.getTunnelSize() == 8
            && restored.getUUID().equals(slime.getUUID())
            && (boolean) read.invoke(restored, low)
            && (boolean) read.invoke(restored, high)
            && !(boolean) read.invoke(restored, low.above()),
        "Fluid cells separated by 32 bits remain independent and persist");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "next_tunnel_no_load", timeoutTicks = 350)
  public static void hugeBoreStopsAtUnloadedRingWithoutAcquiringChunks(GameTestHelper h) {
    var f = fixture(h, 3, 1);
    f.slime.setTunnelSize(f.slime.maximumTunnelSize());
    var first =
        f.slime.getTunnelFront().relative(Direction.SOUTH, f.slime.getTunnelMinWidth()).below();
    h.assertTrue(
        h.getLevel().getChunkSource().getChunkNow(first.getX() >> 4, first.getZ() >> 4) == null,
        "Ring crosses a genuinely absent chunk");
    var tickets = Set.copyOf(h.getLevel().getForcedChunks());
    int loaded = h.getLevel().getChunkSource().getLoadedChunksCount();
    start(f);
    h.runAfterDelay(
        60,
        () -> {
          h.assertTrue(
              f.slime.getTunnelStatus() == TunnelSlime.Status.UNREACHABLE
                  && tickets.equals(h.getLevel().getForcedChunks())
                  && h.getLevel().getChunkSource().getLoadedChunksCount() <= loaded,
              "Large bore never loads missing terrain");
          close(f);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "next_tunnel")
  public static void ancientEfficiencyIsInheritedWithoutAncientOwnershipMarker(GameTestHelper h)
      throws Exception {
    var slime = ModEntities.NEXT_GENERATION_TUNNEL_SLIME.get().create(h.getLevel());
    var spend = NextGenerationTunnelSlime.class.getDeclaredMethod("spendStamina", float.class);
    spend.setAccessible(true);
    float stamina = slime.getStamina();
    spend.invoke(slime, 8F);
    h.assertTrue(
        slime.getStamina() == stamina - 4 && !slime.hasAncientOrigin(),
        "Half costs without a restored-head marker");
    h.assertTrue(
        slime.blockWorkMultiplier(ModWorldBlocks.block("ancient_foundation").defaultBlockState())
            == 1.5,
        "Ancient masonry efficiency inherited");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "next_tunnel")
  public static void matchingTunnelParentsSynthesizeOneWaitingChildWithRefundedGrowth(
      GameTestHelper h) {
    var owner = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "TunnelSynthesis"));
    var pos = h.absolutePos(new BlockPos(2, 2, 2));
    owner.moveTo(Vec3.atCenterOf(pos));
    h.getLevel()
        .setBlockAndUpdate(pos, ModBlocks.ROYAL_SYNTHESIS_STATION.get().defaultBlockState());
    var station = (RoyalSynthesisStationBlockEntity) h.getLevel().getBlockEntity(pos);
    for (int i = 0; i < 2; i++) {
      var parent =
          i == 0
              ? ModEntities.TUNNEL_SLIME.get().create(h.getLevel())
              : ModEntities.ANCIENT_TUNNEL_SLIME.get().create(h.getLevel());
      parent.tame(owner);
      parent.inheritGrowth(i == 0 ? 25 : 18);
      parent.upgradeStat(SlimeStat.HEALTH);
      station.setItem(i, FriendlySlimeItem.fromSlime(parent));
    }
    station.setItem(2, new ItemStack(Items.SLIME_BALL, 4));
    station.setItem(3, new ItemStack(ModItems.OLD_COMMAND_FRAGMENT.get(), 2));
    h.assertTrue(
        station.canPlaceItem(0, new ItemStack(ModItems.TUNNEL_SLIME.get()))
            && station.canPlaceItem(1, new ItemStack(ModItems.ANCIENT_TUNNEL_SLIME.get())),
        "Station accepts tunnel parents");
    var check = RoyalSynthesis.execute(owner, station);
    h.assertTrue(
        check.problem() == RoyalSynthesis.Problem.COMPLETE && check.total() == 43,
        "Tunnel synthesis commits once");
    var result =
        owner.getInventory().items.stream()
            .filter(s -> s.is(ModItems.NEXT_GENERATION_TUNNEL_SLIME.get()))
            .findFirst()
            .orElseThrow();
    var child = ModEntities.NEXT_GENERATION_TUNNEL_SLIME.get().create(h.getLevel());
    child.load(result.getTag().getCompound("SlimeData"));
    h.assertTrue(
        child.isOrderedToSit()
            && !child.isWorking()
            && child.getTunnelSize() == 3
            && child.getStatPoints() == 43
            && child.getStatLevel(SlimeStat.HEALTH) == 0
            && child
                .getPersistentData()
                .getCompound("NextGenAncestry")
                .getString("Family")
                .equals("tunnel"),
        "Waiting child retains exact family and lawful growth");
    h.succeed();
  }
}
