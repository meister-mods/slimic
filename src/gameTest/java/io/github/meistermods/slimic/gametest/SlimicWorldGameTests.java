package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.RuinDecorationBlock;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.*;
import io.github.meistermods.slimic.world.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SlimicWorldGameTests {
  private static ServerPlayer player(ServerLevel level) {
    var p =
        new ServerPlayer(
            level.getServer(), level, new GameProfile(UUID.randomUUID(), "WorldVisitor"));
    p.connection =
        new ServerGamePacketListenerImpl(
            p.server,
            new Connection(PacketFlow.SERVERBOUND) {
              @Override
              public void send(Packet<?> packet) {}
            },
            p);
    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SLIME_BALL, 4));
    return p;
  }

  private static SlimicPortalShape frame(
      ServerLevel level,
      BlockPos bottom,
      Direction.Axis axis,
      int width,
      int height,
      boolean corners,
      boolean active) {
    var shape = new SlimicPortalShape(bottom, axis, width, height);
    for (int x = -1; x <= width; x++)
      for (int y = -1; y <= height; y++) {
        boolean edge = x == -1 || x == width || y == -1 || y == height;
        boolean corner = (x == -1 || x == width) && (y == -1 || y == height);
        BlockState state =
            edge && (!corner || corners)
                ? Blocks.SLIME_BLOCK.defaultBlockState()
                : !edge && active
                    ? ModWorldBlocks.PORTAL
                        .get()
                        .defaultBlockState()
                        .setValue(SlimicPortalBlock.AXIS, axis)
                    : Blocks.AIR.defaultBlockState();
        level.setBlock(shape.cell(x, y), state, 18);
      }
    if (active) SlimicPortalIndex.get(level).remember(bottom);
    return shape;
  }

  private static BlockPos high(LoadedWorkArea area) {
    return BlockPos.containing(area.point(3, 220, 5));
  }

  @GameTest(template = "empty", batch = "slimic_world")
  public static void dimensionBiomeFeaturesAndCatalogAreLoaded(GameTestHelper h) {
    var level = h.getLevel().getServer().getLevel(SlimicWorld.DIMENSION);
    h.assertTrue(
        level != null && SlimicWorld.allowsPortal(level), "The Slimic is a loaded dimension");
    var biome =
        level
            .registryAccess()
            .registryOrThrow(Registries.BIOME)
            .getHolderOrThrow(SlimicWorld.DAMP_PLAINS);
    for (var pos :
        List.of(new BlockPos(0, 70, 0), new BlockPos(980, -40, -760), new BlockPos(-741, 250, 553)))
      h.assertTrue(
          level.getBiome(pos).is(SlimicWorld.DAMP_PLAINS), "The entire dimension uses Damp Plains");
    h.assertTrue(
        !biome.value().getMobSettings().getMobs(MobCategory.MONSTER).isEmpty(),
        "The biome has native wildlife");
    h.assertTrue(
        ModWorldBlocks.entries().size() == 86
            && ModWorldBlocks.entries().stream()
                    .filter(e -> e.category() == ModWorldBlocks.Category.BUILDING)
                    .count()
                == 54
            && ModWorldBlocks.entries().stream()
                    .filter(e -> e.category() == ModWorldBlocks.Category.DECORATION)
                    .count()
                == 32,
        "Building and decoration have separate, complete catalogs");
    h.assertTrue(
        ModWorldBlocks.PORTAL.get().asItem() == Items.AIR,
        "The technical portal has no inventory item");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "slimic_world")
  public static void portalGeometryAcceptsBothAxesAndOptionalCorners(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    try {
      for (var axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z})
        for (boolean corners : new boolean[] {true, false}) {
          var shape = frame(h.getLevel(), high(area), axis, 2, 3, corners, false);
          h.assertTrue(
              shape.equals(SlimicPortalShape.find(h.getLevel(), shape.cell(1, 2), axis))
                  && shape.empty(h.getLevel()),
              "A 4x5 frame works on either horizontal axis with optional corners");
        }
    } finally {
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "slimic_world")
  public static void portalGeometryEnforcesNetherSizeLimits(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    try {
      for (int[] size : new int[][] {{2, 3}, {21, 21}, {1, 3}, {2, 2}, {22, 3}, {2, 22}}) {
        var shape =
            frame(h.getLevel(), high(area), Direction.Axis.X, size[0], size[1], true, false);
        var found = SlimicPortalShape.find(h.getLevel(), shape.bottom(), shape.axis());
        h.assertTrue(
            (found != null) == (size[0] >= 2 && size[0] <= 21 && size[1] >= 3 && size[1] <= 21),
            "Interior dimensions must be 2..21 by 3..21: " + Arrays.toString(size));
      }
    } finally {
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "slimic_world")
  public static void portalRejectsObsidianMixedFramesAndBlockedInteriors(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    try {
      for (var bad :
          List.of(Blocks.OBSIDIAN, ModWorldBlocks.block("congealed_slime"), Blocks.AIR)) {
        var shape = frame(h.getLevel(), high(area), Direction.Axis.X, 2, 3, true, false);
        h.getLevel().setBlock(shape.cell(-1, 1), bad.defaultBlockState(), 18);
        h.assertTrue(
            SlimicPortalShape.find(h.getLevel(), shape.bottom(), shape.axis()) == null,
            "Only vanilla slime blocks count as frame material");
      }
      var shape = frame(h.getLevel(), high(area), Direction.Axis.X, 2, 3, true, false);
      h.getLevel().setBlock(shape.cell(1, 1), Blocks.CHEST.defaultBlockState(), 18);
      h.assertTrue(
          SlimicPortalShape.find(h.getLevel(), shape.bottom(), shape.axis()) == null,
          "A blocked interior cannot be activated or replaced");
    } finally {
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "slimic_world")
  public static void activationConsumesOneBallAndCannotReactivate(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var p = player(h.getLevel());
    try {
      var shape = frame(h.getLevel(), high(area), Direction.Axis.X, 2, 3, false, false);
      h.assertTrue(
          SlimicPortalTravel.activate(p, InteractionHand.MAIN_HAND, shape.bottom())
              && shape.complete(h.getLevel())
              && p.getMainHandItem().getCount() == 3,
          "Survival activates the entire frame with exactly one slimeball");
      h.assertTrue(
          !SlimicPortalTravel.activate(p, InteractionHand.MAIN_HAND, shape.bottom())
              && p.getMainHandItem().getCount() == 3,
          "An active portal does not consume more items");
      h.assertTrue(
          SlimicPortalIndex.get(h.getLevel()).near(shape.bottom()).contains(shape.bottom()),
          "An activated portal is remembered for return travel");
    } finally {
      p.discard();
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "slimic_world")
  public static void creativeAndOffhandActivationKeepTheirNativeItemRules(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var p = player(h.getLevel());
    try {
      var shape = frame(h.getLevel(), high(area), Direction.Axis.Z, 2, 3, true, false);
      p.getAbilities().instabuild = true;
      p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SLIME_BALL, 2));
      h.assertTrue(
          SlimicPortalTravel.activate(p, InteractionHand.OFF_HAND, shape.bottom())
              && p.getOffhandItem().getCount() == 2
              && p.getMainHandItem().getCount() == 4,
          "Creative offhand activation preserves both stacks");
      frame(h.getLevel(), high(area), Direction.Axis.Z, 2, 3, true, false);
      p.getAbilities().instabuild = false;
      h.assertTrue(
          SlimicPortalTravel.activate(p, InteractionHand.OFF_HAND, shape.bottom())
              && p.getOffhandItem().getCount() == 1,
          "Survival consumes from the actual interaction hand");
    } finally {
      p.discard();
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "slimic_world")
  public static void cancelledPlacementRollsBackAllCellsAndItems(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var p = player(h.getLevel());
    Consumer<BlockEvent.EntityMultiPlaceEvent> cancel =
        e -> {
          if (e.getEntity() == p) e.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(cancel);
    try {
      var shape = frame(h.getLevel(), high(area), Direction.Axis.X, 2, 3, true, false);
      h.assertTrue(
          !SlimicPortalTravel.activate(p, InteractionHand.MAIN_HAND, shape.bottom())
              && shape.empty(h.getLevel())
              && shape.sameFrame(h.getLevel())
              && p.getMainHandItem().getCount() == 4,
          "A protection cancellation restores the frame interior and inventory");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(cancel);
      p.discard();
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "slimic_world")
  public static void changedActivationItemCannotCommitAPortal(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var p = player(h.getLevel());
    Consumer<BlockEvent.EntityMultiPlaceEvent> change =
        e -> {
          if (e.getEntity() == p)
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
        };
    MinecraftForge.EVENT_BUS.addListener(change);
    try {
      var shape = frame(h.getLevel(), high(area), Direction.Axis.X, 2, 3, true, false);
      h.assertTrue(
          !SlimicPortalTravel.activate(p, InteractionHand.MAIN_HAND, shape.bottom())
              && shape.empty(h.getLevel())
              && p.getMainHandItem().is(Items.DIAMOND),
          "An interaction callback changing the held item aborts activation without consuming the"
              + " replacement");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(change);
      p.discard();
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "slimic_world", timeoutTicks = 80)
  public static void brokenFrameClearsTheWholePortal(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var shape = frame(h.getLevel(), high(area), Direction.Axis.X, 2, 3, true, true);
    h.getLevel().setBlockAndUpdate(shape.cell(-1, 1), Blocks.AIR.defaultBlockState());
    h.runAfterDelay(
        3,
        () -> {
          try {
            h.assertTrue(
                shape.empty(h.getLevel()),
                "Removing any required frame block clears every portal cell");
          } finally {
            area.close();
          }
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "slimic_world")
  public static void portalIndexRoundTripsAndUsesHorizontalRange(GameTestHelper h) {
    var index = new SlimicPortalIndex();
    var center = new BlockPos(100, 70, -100);
    index.remember(center);
    index.remember(center.above(100));
    index.remember(center.offset(127, 0, 0));
    index.remember(center.offset(129, 0, 0));
    var restored = SlimicPortalIndex.load(index.save(new CompoundTag()));
    h.assertTrue(
        restored.near(center).size() == 3 && restored.near(center).get(0).equals(center),
        "Return portals survive save/load and use a 128-block horizontal search radius");
    restored.forget(center);
    h.assertTrue(
        !restored.near(center).contains(center), "Stale portals can be removed from the index");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "slimic_world", timeoutTicks = 400)
  public static void everyCompanionTravelsWithItsIdentityCargoAndWaitOrder(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var source = frame(h.getLevel(), high(area), Direction.Axis.X, 2, 3, true, true);
    var target = h.getLevel().getServer().getLevel(SlimicWorld.DIMENSION);
    var end = source.bottom();
    var chunk = new net.minecraft.world.level.ChunkPos(end);
    target.setChunkForced(chunk.x, chunk.z, true);
    target.getChunk(chunk.x, chunk.z);
    long ready = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
    target
        .getServer()
        .managedBlock(
            () -> {
              target.getChunkSource().pollTask();
              return target.isPositionEntityTicking(end) || System.nanoTime() >= ready;
            });
    h.assertTrue(
        target.isPositionEntityTicking(end), "The destination fixture must expose world entities");
    for (var pos : BlockPos.betweenClosed(end.offset(-3, -1, -3), end.offset(4, 5, 3)))
      target.setBlock(
          pos,
          pos.getY() == end.getY() - 1
              ? Blocks.STONE.defaultBlockState()
              : Blocks.AIR.defaultBlockState(),
          18);
    frame(target, end, Direction.Axis.X, 2, 3, true, true);
    int[] count = {0};
    for (var entry : ModEntities.ENTITY_TYPES.getEntries()) {
      var entity = entry.get().create(h.getLevel());
      if (!(entity instanceof FriendlySlime slime)) continue;
      count[0]++;
      slime.moveTo(Vec3.atBottomCenterOf(source.bottom()));
      slime.setOwnerUUID(UUID.randomUUID());
      slime.setTame(true);
      slime.setNoAi(true);
      slime.setWaiting(false);
      slime.setTunedVitals(5, 37, 3);
      if (slime instanceof BagSlime worker) {
        worker.setWorking(true);
        worker.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 7));
      }
      h.getLevel().addFreshEntity(slime);
      UUID id = slime.getUUID(), owner = slime.getOwnerUUID();
      SlimicPortalTravel.inside(slime, source.bottom(), source.axis());
      var moved = target.getEntity(id);
      h.assertTrue(
          moved instanceof FriendlySlime,
          "The portal transfers "
              + entry.getId()
              + "; alive="
              + slime.isAlive()
              + "; removed="
              + slime.isRemoved()
              + "; cooldown="
              + slime.isOnPortalCooldown()
              + "; source="
              + source.complete(h.getLevel())
              + "; target="
              + source.complete(target));
      var pet = (FriendlySlime) moved;
      h.assertTrue(
          pet.getOwnerUUID().equals(owner)
              && pet.getHealth() == 5
              && pet.getStamina() == 37
              && pet.getStatPoints() == 3
              && pet.isOrderedToSit()
              && pet.isOnPortalCooldown(),
          "Travel preserves identity and vitals and always arrives in Wait");
      if (pet instanceof BagSlime worker)
        h.assertTrue(
            !worker.isWorking() && worker.getBag().getStackInSlot(0).getCount() == 7,
            "Worker cargo survives while work stays paused");
      pet.discard();
    }
    h.assertTrue(count[0] == 22, "All 22 companion professions are covered");
    target.setChunkForced(chunk.x, chunk.z, false);
    area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "slimic_world")
  public static void portalCooldownAndRidersCannotStartAnotherTransfer(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    try {
      var shape = frame(h.getLevel(), high(area), Direction.Axis.X, 2, 3, true, true);
      var slime = ModEntities.SADDLE_SLIME.get().create(h.getLevel());
      slime.moveTo(Vec3.atBottomCenterOf(shape.bottom()));
      slime.setPortalCooldown();
      SlimicPortalTravel.inside(slime, shape.bottom(), shape.axis());
      h.assertTrue(
          slime.level() == h.getLevel() && !slime.isRemoved(),
          "Portal cooldown prevents bounce-back");
      slime.setPortalCooldown(0);
      var rider = EntityType.PIG.create(h.getLevel());
      rider.startRiding(slime, true);
      SlimicPortalTravel.inside(slime, shape.bottom(), shape.axis());
      h.assertTrue(
          slime.level() == h.getLevel() && rider.getVehicle() == slime,
          "Mounted slimes remain with their passengers");
      rider.discard();
      slime.discard();
    } finally {
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "slimic_world")
  public static void terrainAndConstructionUseAppropriateTools(GameTestHelper h) {
    h.assertTrue(
        ModBlocks.SLIME_CHEST.get().defaultBlockState().is(BlockTags.MINEABLE_WITH_AXE)
            && ModBlocks.SLIME_TRAIL.get().defaultBlockState().is(BlockTags.MINEABLE_WITH_SHOVEL),
        "New material tags retain the original chest and trail tool classifications");
    for (var entry : ModWorldBlocks.entries()) {
      var state = entry.block().get().defaultBlockState();
      if (entry.category() != ModWorldBlocks.Category.BUILDING) continue;
      if (entry.id().startsWith("slime_infused_planks"))
        h.assertTrue(state.is(BlockTags.MINEABLE_WITH_AXE), "Infused wood is axe-mineable");
      else if (!List.of("congealed_slime", "luminous_slime", "gel_glass").contains(entry.id()))
        h.assertTrue(
            state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL),
            "Building material has a native mining-tool tag: " + entry.id());
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "slimic_world")
  public static void doubleSlabsDropTwoAndStoneDropsCobblestone(GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(4, 2, 4));
    var pick = new ItemStack(Items.DIAMOND_PICKAXE);
    var slab = ModWorldBlocks.block("slimic_stone_slab");
    var drops =
        Block.getDrops(
            slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE),
            h.getLevel(),
            pos,
            null,
            null,
            pick);
    h.assertTrue(
        drops.stream().mapToInt(ItemStack::getCount).sum() == 2
            && drops.stream().allMatch(s -> s.is(slab.asItem())),
        "Double slabs return exactly two slab items");
    var stone = ModWorldBlocks.block("slimic_stone");
    drops = Block.getDrops(stone.defaultBlockState(), h.getLevel(), pos, null, null, pick);
    h.assertTrue(
        drops.size() == 1 && drops.get(0).is(ModWorldBlocks.block("slimic_cobblestone").asItem()),
        "Natural stone drops its matching cobblestone");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "slimic_world", timeoutTicks = 80)
  public static void waterloggedDecorationKeepsWaterWhenSupportBreaks(GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(4, 2, 4));
    var block = ModWorldBlocks.block("stone_tablet");
    h.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
    h.getLevel()
        .setBlockAndUpdate(
            pos, block.defaultBlockState().setValue(RuinDecorationBlock.WATERLOGGED, true));
    h.getLevel().setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
    h.runAfterDelay(
        1,
        () -> {
          h.assertTrue(
              !h.getLevel().getBlockState(pos).is(block)
                  && !h.getLevel().getFluidState(pos).isEmpty(),
              "A removed support releases the water instead of deleting it");
          var drops =
              h.getLevel()
                  .getEntitiesOfClass(
                      ItemEntity.class,
                      new AABB(pos).inflate(2),
                      e -> e.getItem().is(block.asItem()));
          h.assertTrue(
              drops.stream().mapToInt(e -> e.getItem().getCount()).sum() == 1,
              "A waterlogged prop still drops exactly one item when its support disappears");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "slimic_world")
  public static void adventureSpectatorAndOtherDimensionsCannotActivate(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var p = player(h.getLevel());
    try {
      var shape = frame(h.getLevel(), high(area), Direction.Axis.X, 2, 3, true, false);
      p.getAbilities().mayBuild = false;
      h.assertTrue(
          !SlimicPortalTravel.activate(p, InteractionHand.MAIN_HAND, shape.bottom()),
          "Adventure permissions forbid activation");
      p.getAbilities().mayBuild = true;
      p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
      h.assertTrue(
          !SlimicPortalTravel.activate(p, InteractionHand.MAIN_HAND, shape.bottom()),
          "Spectators cannot activate a portal");
      var nether = h.getLevel().getServer().getLevel(Level.NETHER);
      var visitor = player(nether);
      var other = frame(nether, high(area), Direction.Axis.X, 2, 3, true, false);
      h.assertTrue(
          !SlimicPortalTravel.activate(visitor, InteractionHand.MAIN_HAND, other.bottom())
              && visitor.getMainHandItem().getCount() == 4,
          "Nether frames cannot activate this route");
      visitor.discard();
    } finally {
      p.discard();
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "slimic_world")
  public static void everyNewItemHasAnObtainingRoute(GameTestHelper h) {
    var natural = Set.of("slimic_soil", "slime_silt", "slime_mud");
    var recipes = h.getLevel().getRecipeManager().getRecipes();
    for (var entry : ModWorldBlocks.entries()) {
      boolean obtainable =
          natural.contains(entry.id())
              || recipes.stream()
                  .anyMatch(
                      r -> r.getResultItem(h.getLevel().registryAccess()).is(entry.item().get()));
      h.assertTrue(
          obtainable, "New item has a crafting, processing, or terrain source: " + entry.id());
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "slimic_world")
  public static void soilSupportsNativePlantsAndHoeActions(GameTestHelper h) {
    var p = player(h.getLevel());
    var pos = h.absolutePos(new BlockPos(4, 2, 4));
    try {
      p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
      var context =
          new net.minecraft.world.item.context.UseOnContext(
              p,
              InteractionHand.MAIN_HAND,
              new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
      for (var name : List.of("slimic_soil", "damp_grass_block", "slime_mud")) {
        var state = ModWorldBlocks.block(name).defaultBlockState();
        h.getLevel().setBlock(pos, state, 18);
        h.getLevel().setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 18);
        h.assertTrue(
            Blocks.OAK_SAPLING.defaultBlockState().canSurvive(h.getLevel(), pos.above()),
            "New soil supports native saplings: " + name);
        var tilled =
            state.getToolModifiedState(
                context, net.minecraftforge.common.ToolActions.HOE_TILL, true);
        h.assertTrue(
            tilled != null && tilled.is(Blocks.FARMLAND), "The native hoe can till " + name);
        var underside =
            new net.minecraft.world.item.context.UseOnContext(
                p,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.DOWN, pos, false));
        h.assertTrue(
            state.getToolModifiedState(
                    underside, net.minecraftforge.common.ToolActions.HOE_TILL, true)
                == null,
            "Native hoe rules do not till the underside of new soil");
        h.getLevel().setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 18);
        h.assertTrue(
            state.getToolModifiedState(
                    context, net.minecraftforge.common.ToolActions.HOE_TILL, true)
                == null,
            "Blocked soil cannot be tilled");
      }
    } finally {
      p.discard();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "slimic_world")
  public static void decorationsRotateWithoutChangingWaterOrVolume(GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(4, 2, 4));
    for (var entry : ModWorldBlocks.entries()) {
      if (!(entry.block().get() instanceof RuinDecorationBlock)) continue;
      var initial =
          entry.block().get().defaultBlockState().setValue(RuinDecorationBlock.WATERLOGGED, true);
      var state = initial;
      for (int i = 0; i < 4; i++) {
        h.assertTrue(
            !state.getShape(h.getLevel(), pos).isEmpty() && !state.getFluidState().isEmpty(),
            "Rotated decorations retain their shape and contained water: " + entry.id());
        state = state.rotate(Rotation.CLOCKWISE_90);
      }
      h.assertTrue(state.equals(initial), "Four rotations restore the exact original state");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "slimic_world")
  public static void ruinLootHasLocalizedRecordsAndRealSupplies(GameTestHelper h) {
    var table =
        h.getLevel().getServer().getLootData().getLootTable(SlimicWorld.id("chests/slimic_ruins"));
    var params =
        new net.minecraft.world.level.storage.loot.LootParams.Builder(h.getLevel())
            .withParameter(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                h.absoluteVec(new Vec3(4, 2, 4)))
            .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
    for (int seed = 0; seed < 16; seed++) {
      var drops = table.getRandomItems(params, seed);
      var books = drops.stream().filter(s -> s.is(Items.WRITTEN_BOOK)).toList();
      h.assertTrue(
          books.size() == 1 && books.get(0).getCount() == 1 && drops.size() >= 4,
          "Every ruin supply chest contains one ledger and ordinary supplies");
      var pages = books.get(0).getTag().getList("pages", 8);
      h.assertTrue(
          pages.size() == 2
              && net.minecraft.network.chat.Component.Serializer.fromJson(pages.getString(0))
                  != null
              && pages.getString(0).contains("lore.slimic.supply_ledger.page1"),
          "Ledger pages retain client-localized text components");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "blocked_player_portal")
  public static void playerWithoutASafeExitStaysRegisteredInTheSourceWorld(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var p = player(h.getLevel());
    var target = h.getLevel().getServer().getLevel(SlimicWorld.DIMENSION);
    var border = target.getWorldBorder();
    double oldX = border.getCenterX(), oldZ = border.getCenterZ(), size = border.getSize();
    try {
      var source = frame(h.getLevel(), high(area), Direction.Axis.X, 2, 3, true, true);
      p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
      p.moveTo(Vec3.atBottomCenterOf(source.bottom()));
      h.getLevel().addNewPlayer(p);
      border.setCenter(source.bottom().getX(), source.bottom().getZ());
      border.setSize(6);
      SlimicPortalTravel.inside(p, source.bottom(), source.axis());
      h.assertTrue(
          p.level() == h.getLevel()
              && h.getLevel().getEntity(p.getUUID()) == p
              && p.isAlive()
              && !p.isChangingDimension()
              && source.complete(h.getLevel()),
          "A failed exit must leave the player registered, alive and outside a dimension-change"
              + " transaction");
    } finally {
      border.setCenter(oldX, oldZ);
      border.setSize(size);
      p.discard();
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "slimic_world")
  public static void cancelledCompanionAdmissionPreservesTheOriginalAndCargo(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var shape = frame(h.getLevel(), high(area), Direction.Axis.X, 2, 3, true, true);
    var pet = ModEntities.MINING_SLIME.get().create(h.getLevel());
    pet.moveTo(Vec3.atBottomCenterOf(shape.bottom()));
    pet.setOwnerUUID(UUID.randomUUID());
    pet.setTame(true);
    pet.setNoAi(true);
    pet.onReleased();
    pet.setWorking(true);
    pet.setTunedVitals(7, 41, 3);
    pet.getBag().setStackInSlot(0, new ItemStack(Items.EMERALD, 7));
    h.getLevel().addFreshEntity(pet);
    var target = h.getLevel().getServer().getLevel(SlimicWorld.DIMENSION);
    Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> cancel =
        e -> {
          if (e.getLevel() == target && e.getEntity().getUUID().equals(pet.getUUID()))
            e.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(cancel);
    try {
      SlimicPortalTravel.inside(pet, shape.bottom(), shape.axis());
      h.assertTrue(
          pet.isAlive()
              && !pet.isRemoved()
              && h.getLevel().getEntity(pet.getUUID()) == pet
              && target.getEntity(pet.getUUID()) == null
              && pet.getHealth() == 7
              && pet.getStamina() == 41
              && pet.getStatPoints() == 3
              && pet.isWorking()
              && pet.getBag().getStackInSlot(0).getCount() == 7,
          "A destination spawn veto leaves the original identity, work order and exact cargo in its"
              + " source world");
      h.assertTrue(
          CompanionRegistry.get(h.getLevel())
              .find(pet.getUUID())
              .dimension()
              .equals(h.getLevel().dimension().location().toString()),
          "A cancelled copy cannot move the persistent companion roster to a phantom world");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(cancel);
      pet.discard();
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "slimic_world", timeoutTicks = 100)
  public static void invisibleCompanionLightDoesNotBlockOrLaterRemoveAPortal(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var p = player(h.getLevel());
    var frame = frame(h.getLevel(), high(area), Direction.Axis.X, 2, 3, true, false);
    var pos = frame.bottom().above();
    var id = UUID.randomUUID();
    h.assertTrue(
        io.github.meistermods.slimic.block.SlimeLightBlock.illuminate(
            h.getLevel(), pos, id, 200, false),
        "A Glow companion can illuminate the empty frame");
    var original =
        (io.github.meistermods.slimic.block.SlimeLightBlockEntity) h.getLevel().getBlockEntity(pos);
    Consumer<BlockEvent.EntityMultiPlaceEvent> cancel =
        e -> {
          if (e.getEntity() == p) e.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(cancel);
    try {
      h.assertTrue(
          !SlimicPortalTravel.activate(p, InteractionHand.MAIN_HAND, frame.bottom()),
          "A protected frame is unchanged");
      h.assertTrue(
          h.getLevel().getBlockEntity(pos)
                  instanceof io.github.meistermods.slimic.block.SlimeLightBlockEntity restored
              && restored.active(h.getLevel().getGameTime())
              && p.getMainHandItem().getCount() == 4,
          "Rollback restores the invisible light and its owner lease without charging an item");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(cancel);
    }
    h.assertTrue(
        SlimicPortalTravel.activate(p, InteractionHand.MAIN_HAND, frame.bottom()),
        "Invisible companion light does not obstruct permitted activation");
    original.release(id);
    h.runAfterDelay(
        12,
        () -> {
          try {
            h.assertTrue(
                frame.complete(h.getLevel()),
                "Stale light leases and scheduled light ticks cannot remove the active portal");
          } finally {
            p.discard();
            area.close();
          }
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "slimic_world")
  public static void gelGlassPreservesNativeSkylightAndSilkTouchDrops(GameTestHelper h) {
    var state = ModWorldBlocks.block("gel_glass").defaultBlockState();
    var pos = h.absolutePos(new BlockPos(4, 3, 4));
    h.assertTrue(
        state.propagatesSkylightDown(h.getLevel(), pos)
            && state.getLightBlock(h.getLevel(), pos) == 0
            && state
                .getVisualShape(
                    h.getLevel(), pos, net.minecraft.world.phys.shapes.CollisionContext.empty())
                .isEmpty()
            && state.is(net.minecraft.tags.BlockTags.IMPERMEABLE),
        "Gel glass transmits skylight and blocks roof drips like native glass");
    var pick = new ItemStack(Items.DIAMOND_PICKAXE);
    h.assertTrue(
        Block.getDrops(state, h.getLevel(), pos, null, null, pick).isEmpty(),
        "Ordinary glass mining produces no item");
    pick.enchant(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH, 1);
    var drops = Block.getDrops(state, h.getLevel(), pos, null, null, pick);
    h.assertTrue(
        drops.size() == 1
            && drops.get(0).is(state.getBlock().asItem())
            && drops.get(0).getCount() == 1,
        "Silk Touch returns exactly one gel glass block");
    h.succeed();
  }

  private static int anchorTickets(ServerLevel level) {
    var saved =
        level.getDataStorage().get(net.minecraft.world.level.ForcedChunksSavedData::load, "chunks");
    return saved == null ? 0 : saved.getEntityForcedChunks().getTickingChunks().size();
  }

  @GameTest(template = "empty", batch = "slimic_world")
  public static void changedOrDiscardedPortalCopiesCannotConsumeTheOriginal(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var shape = frame(h.getLevel(), high(area), Direction.Axis.X, 2, 3, true, true);
    var target = h.getLevel().getServer().getLevel(SlimicWorld.DIMENSION);
    var pet = ModEntities.TRANSPORT_SLIME.get().create(h.getLevel());
    pet.moveTo(Vec3.atBottomCenterOf(shape.bottom()));
    pet.tame(h.makeMockSurvivalPlayer());
    pet.setNoAi(true);
    pet.onReleased();
    pet.setWorking(true);
    pet.getBag().setStackInSlot(0, new ItemStack(Items.EMERALD, 7));
    h.getLevel().addFreshEntity(pet);
    int[] mode = {0};
    BlockPos[] rejectedAt = {null};
    Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> callback =
        e -> {
          if (e.getLevel() != target || !e.getEntity().getUUID().equals(pet.getUUID())) return;
          if (mode[0] == 0) e.getEntity().discard();
          else if (mode[0] == 1) pet.getBag().setStackInSlot(0, new ItemStack(Items.EMERALD, 3));
          else if (mode[0] == 2) ((FriendlySlime) e.getEntity()).setOwnerUUID(UUID.randomUUID());
          else {
            var copy = (FriendlySlime) e.getEntity();
            rejectedAt[0] = copy.blockPosition();
            copy.hurt(copy.damageSources().genericKill(), Float.MAX_VALUE);
          }
        };
    MinecraftForge.EVENT_BUS.addListener(callback);
    try {
      for (int i = 0; i < 4; i++) {
        mode[0] = i;
        pet.setPortalCooldown(0);
        SlimicPortalTravel.inside(pet, shape.bottom(), shape.axis());
        h.assertTrue(
            pet.isAlive()
                && !pet.isRemoved()
                && h.getLevel().getEntity(pet.getUUID()) == pet
                && target.getEntity(pet.getUUID()) == null
                && pet.isWorking()
                && pet.getBag().getStackInSlot(0).getCount() == (i == 0 ? 7 : 3),
            "Admission callbacks cannot consume the original, duplicate cargo, leave a ghost, or"
                + " change ownership: "
                + i);
        if (i == 3) {
          h.assertTrue(
              rejectedAt[0] != null
                  && !target.getBlockState(rejectedAt[0]).is(ModBlocks.BROKEN_SLIME.get())
                  && target
                      .getEntitiesOfClass(
                          ItemEntity.class,
                          new AABB(rejectedAt[0]).inflate(2),
                          item -> item.getItem().is(ModItems.BROKEN_SLIME.get()))
                      .isEmpty(),
              "A provisional copy killed by a spawn callback cannot create duplicate recovery data"
                  + " or cargo");
        }
      }
    } finally {
      MinecraftForge.EVENT_BUS.unregister(callback);
      pet.discard();
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "slimic_world")
  public static void rightClickActivationHonorsBothForgeDenialChannels(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var p = player(h.getLevel());
    try {
      var shape = frame(h.getLevel(), high(area), Direction.Axis.X, 2, 3, true, false);
      var floor = shape.bottom().below();
      var hit =
          new BlockHitResult(Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false);
      for (boolean denyItem : new boolean[] {true, false}) {
        var event =
            new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
                p, InteractionHand.MAIN_HAND, floor, hit);
        if (denyItem) event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);
        else event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
        MinecraftForge.EVENT_BUS.post(event);
        h.assertTrue(
            shape.empty(h.getLevel()) && p.getMainHandItem().getCount() == 4,
            "Either Forge denial prevents portal placement and item consumption");
      }
      var allowed =
          new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
              p, InteractionHand.MAIN_HAND, floor, hit);
      MinecraftForge.EVENT_BUS.post(allowed);
      h.assertTrue(
          allowed.isCanceled()
              && allowed.getCancellationResult().consumesAction()
              && shape.complete(h.getLevel())
              && p.getMainHandItem().getCount() == 3,
          "An ordinary permitted right-click activates the frame and consumes one ball");
    } finally {
      p.discard();
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "blocked_player_portal")
  public static void travelCallbacksCannotRemoveAPlayerWhenPermissionOrLandingChanges(
      GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var p = player(h.getLevel());
    var target = h.getLevel().getServer().getLevel(SlimicWorld.DIMENSION);
    var pos = BlockPos.containing(area.point(8, 210, 8));
    p.moveTo(area.point(5.5, 101, 5.5));
    h.getLevel().addNewPlayer(p);
    for (var cell : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 3, 2)))
      target.setBlock(
          cell,
          cell.getY() == pos.getY() - 1
              ? Blocks.STONE.defaultBlockState()
              : Blocks.AIR.defaultBlockState(),
          18);
    boolean[] allowed = {true};
    int[] mode = {0};
    Consumer<net.minecraftforge.event.entity.EntityTravelToDimensionEvent> change =
        e -> {
          if (e.getEntity() != p) return;
          if (mode[0] == 0) allowed[0] = false;
          else target.setBlock(pos, Blocks.STONE.defaultBlockState(), 18);
        };
    MinecraftForge.EVENT_BUS.addListener(change);
    try {
      for (int i = 0; i < 2; i++) {
        mode[0] = i;
        allowed[0] = true;
        boolean moved =
            CompanionTeleport.player(p, target, Vec3.atBottomCenterOf(pos), () -> allowed[0]);
        h.assertTrue(
            !moved
                && p.isAlive()
                && !p.isRemoved()
                && !p.isChangingDimension()
                && p.level() == h.getLevel()
                && h.getLevel().getEntity(p.getUUID()) == p,
            "Dimension callbacks revoke travel before respawn packets or source removal: " + i);
      }
    } finally {
      MinecraftForge.EVENT_BUS.unregister(change);
      p.discard();
      area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "pinned_world_portal")
  public static void pinnedEnderTravelMovesItsAnchorTicketWithoutLeavingTheSourceLoaded(
      GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var p = player(h.getLevel());
    var target = h.getLevel().getServer().getLevel(SlimicWorld.DIMENSION);
    var source = frame(h.getLevel(), high(area), Direction.Axis.X, 2, 3, true, true);
    var pet = ModEntities.ENDER_SLIME.get().create(h.getLevel());
    pet.moveTo(Vec3.atBottomCenterOf(source.bottom()));
    pet.tame(p);
    pet.setNoAi(true);
    p.moveTo(pet.position());
    h.getLevel().addFreshEntity(pet);
    int before = anchorTickets(h.getLevel()), targetBefore = anchorTickets(target);
    FriendlySlime moved = null;
    try {
      h.assertTrue(
          pet.bind(p) && anchorTickets(h.getLevel()) == before + 1,
          "Pin one real source chunk ticket");
      SlimicPortalTravel.inside(pet, source.bottom(), source.axis());
      var entry = CompanionRegistry.get(h.getLevel()).find(pet.getUUID());
      h.assertTrue(
          !pet.isAlive()
              && entry.dimension().equals(SlimicWorld.DIMENSION.location().toString())
              && entry.pinned()
              && anchorTickets(h.getLevel()) == before
              && anchorTickets(target) == targetBefore + 1,
          "Moving a pinned companion releases its old ticket and keeps exactly one destination"
              + " anchor");
      // The registry and native ticket map are available before destination entity visibility.
      var pos = entry.position();
      var chunk = new net.minecraft.world.level.ChunkPos(pos);
      target.getChunk(chunk.x, chunk.z);
      long ready = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
      target
          .getServer()
          .managedBlock(
              () -> {
                target.getChunkSource().pollTask();
                return target.getEntity(pet.getUUID()) != null || System.nanoTime() >= ready;
              });
      moved = (FriendlySlime) target.getEntity(pet.getUUID());
      h.assertTrue(
          moved instanceof EnderSlime endpoint && endpoint.isPinned() && endpoint.isOrderedToSit(),
          "The relocated endpoint remains pinned and waiting at its new anchor");
    } finally {
      if (moved != null) moved.discard();
      pet.discard();
      p.discard();
      area.close();
    }
    h.succeed();
  }
}
