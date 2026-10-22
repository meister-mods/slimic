package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.*;
import io.github.meistermods.slimic.item.KingdomMapItem;
import io.github.meistermods.slimic.registry.*;
import io.github.meistermods.slimic.world.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SanctumGameTests {
  private static ItemStack take(
      GameTestHelper h, net.minecraft.server.level.ServerPlayer player, Item item, int count) {
    var result = new ItemStack(item, 0);
    for (int slot = 0; slot < 36 && result.getCount() < count; slot++) {
      var available = player.getInventory().getItem(slot);
      if (available.is(item))
        result.grow(player.getInventory().removeItem(slot, count - result.getCount()).getCount());
    }
    h.assertTrue(result.getCount() == count, "Guaranteed supplies cover " + count + " × " + item);
    return result;
  }

  private static ItemStack craftFromLoot(
      GameTestHelper h, net.minecraft.server.level.ServerPlayer player, String id) {
    var recipe = recipe(h, id);
    var grid = grid();
    var ingredients = recipe.getIngredients();
    for (int i = 0; i < ingredients.size(); i++) {
      var ingredient = ingredients.get(i);
      if (ingredient.isEmpty()) continue;
      int found = -1;
      for (int slot = 0; slot < 36; slot++)
        if (ingredient.test(player.getInventory().getItem(slot))) {
          found = slot;
          break;
        }
      h.assertTrue(found >= 0, "Guaranteed supplies cover recipe " + id + " ingredient " + i);
      grid.setItem(i, player.getInventory().removeItem(found, 1));
    }
    h.assertTrue(recipe.matches(grid, h.getLevel()), "Actual shaped recipe " + id);
    return recipe.assemble(grid, h.getLevel().registryAccess());
  }

  @GameTest(template = "empty", batch = "sanctum_supplies")
  public static void guaranteedLootCoversBothStationsCoreRestorationAndTheFirstSynthesis(
      GameTestHelper h) {
    var level = h.getLevel();
    var owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "RoyalSupplies"));
    for (String room : List.of("courtyard", "gallery", "laboratory", "core")) {
      var params =
          new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
              .withParameter(
                  net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                  net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(BlockPos.ZERO)))
              .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
      for (var item :
          level
              .getServer()
              .getLootData()
              .getLootTable(SlimicWorld.id("chests/sanctum_" + room))
              .getRandomItems(params))
        h.assertTrue(owner.getInventory().add(item), "Loot fits the expedition inventory");
    }
    var supplies = SanctumSites.get(level);
    var site = supplies.register(level, h.absolutePos(new BlockPos(2, 1, 2)), 0);
    site.seals = 3;
    h.assertTrue(
        supplies.grant(owner, site, 4, List.of(new ItemStack(ModItems.ROYAL_SYNTHESIS_CORE.get()))),
        "Guaranteed relay core");
    var restoration = craftFromLoot(h, owner, "world/command_pedestal");
    var restorationPos = h.absolutePos(new BlockPos(2, 2, 2));
    level.setBlockAndUpdate(
        restorationPos, Block.byItem(restoration.getItem()).defaultBlockState());
    var damaged =
        io.github.meistermods.slimic.item.AncientSlimeCoreItem.fromRemains(
            io.github.meistermods.slimic.entity.AncientSlime.Role.MINER, UUID.randomUUID(), 2);
    owner.setItemInHand(InteractionHand.OFF_HAND, damaged);
    owner.moveTo(net.minecraft.world.phys.Vec3.atCenterOf(restorationPos));
    h.assertTrue(
        damaged
            .useOn(
                new net.minecraft.world.item.context.UseOnContext(
                    owner,
                    InteractionHand.OFF_HAND,
                    new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(restorationPos),
                        Direction.UP,
                        restorationPos,
                        false)))
            .consumesAction(),
        "Real courtyard core restoration uses its catalyst and ball");
    var ancient = owner.getOffhandItem().copy();
    owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    h.assertTrue(ancient.is(ModItems.ANCIENT_MINING_SLIME.get()), "Actual restored ancient parent");
    var apparatus = craftFromLoot(h, owner, "royal_synthesis_station");
    var at = h.absolutePos(new BlockPos(5, 2, 2));
    level.setBlockAndUpdate(at, Block.byItem(apparatus.getItem()).defaultBlockState());
    var station = (RoyalSynthesisStationBlockEntity) level.getBlockEntity(at);
    var normal = ModEntities.MINING_SLIME.get().create(level);
    normal.tame(owner);
    normal.inheritGrowth(25);
    station.setItem(0, io.github.meistermods.slimic.item.FriendlySlimeItem.fromSlime(normal));
    station.setItem(1, ancient);
    station.setItem(2, take(h, owner, Items.SLIME_BALL, 4));
    station.setItem(3, take(h, owner, ModItems.OLD_COMMAND_FRAGMENT.get(), 2));
    owner.moveTo(net.minecraft.world.phys.Vec3.atCenterOf(at));
    h.assertTrue(
        RoyalSynthesis.execute(owner, station).problem() == RoyalSynthesis.Problem.COMPLETE
            && owner.getInventory().items.stream()
                .anyMatch(s -> s.is(ModItems.NEXT_GENERATION_MINING_SLIME.get())),
        "Loot alone funds both stations, restoration, and one real fusion with a separately raised"
            + " ordinary parent");
    h.succeed();
  }

  private static TransientCraftingContainer grid() {
    return new TransientCraftingContainer(
        new AbstractContainerMenu(null, 0) {
          public boolean stillValid(Player player) {
            return true;
          }

          public ItemStack quickMoveStack(Player player, int slot) {
            return ItemStack.EMPTY;
          }
        },
        3,
        3);
  }

  private static ServerLevel slimic(GameTestHelper h) {
    return h.getLevel().getServer().getLevel(SlimicWorld.DIMENSION);
  }

  private static CraftingRecipe recipe(GameTestHelper h, String id) {
    return (CraftingRecipe) h.getLevel().getRecipeManager().byKey(SlimicWorld.id(id)).orElseThrow();
  }

  @GameTest(template = "empty", batch = "sanctum_recipes")
  public static void fragmentsCraftShapelesslyAndBoundMapCopiesKeepTheSameDestination(
      GameTestHelper h) {
    var recipe = recipe(h, "ancient_kingdom_map");
    var grid = grid();
    for (int[] slots : List.of(new int[] {0, 1, 2}, new int[] {0, 4, 8}, new int[] {2, 3, 7})) {
      grid.clearContent();
      for (int slot : slots)
        grid.setItem(slot, ModItems.KINGDOM_MAP_FRAGMENT.get().getDefaultInstance());
      h.assertTrue(
          recipe.matches(grid, h.getLevel())
              && recipe
                  .assemble(grid, h.getLevel().registryAccess())
                  .is(ModItems.ANCIENT_KINGDOM_MAP.get()),
          "Three fragments in any arrangement");
    }
    var map = ModItems.ANCIENT_KINGDOM_MAP.get().getDefaultInstance();
    KingdomMapItem.bind(
        map, slimic(h), new SanctumPlacement.Candidate(new BlockPos(812, 80, -420), 2));
    var before = map.save(new CompoundTag());
    grid.clearContent();
    grid.setItem(1, map);
    grid.setItem(4, new ItemStack(Items.MAP));
    grid.setItem(8, new ItemStack(Items.MAP));
    var copying = recipe(h, "kingdom_map_copying");
    h.assertTrue(copying.matches(grid, h.getLevel()), "Bound map may be copied");
    var copied = copying.assemble(grid, h.getLevel().registryAccess());
    h.assertTrue(
        copied.getCount() == 3
            && copied.getTag().equals(map.getTag())
            && map.save(new CompoundTag()).equals(before),
        "Copies preserve native map id, anchor and source");
    grid.setItem(1, new ItemStack(ModItems.ANCIENT_KINGDOM_MAP.get()));
    h.assertTrue(!copying.matches(grid, h.getLevel()), "Unbound maps are not copy sources");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "sanctum_recipes")
  public static void stationShapedRecipeRetainsCoreOriginAndNativeRecipeSynchronization(
      GameTestHelper h) {
    var grid = grid();
    Item[] items = {
      Items.GOLD_INGOT,
      ModItems.ROYAL_SYNTHESIS_CORE.get(),
      Items.GOLD_INGOT,
      Items.REDSTONE,
      ModItems.ANCIENT_COMMAND_SEAL.get(),
      Items.REDSTONE,
      Items.STONE_BRICKS,
      Items.STONE_BRICKS,
      Items.STONE_BRICKS
    };
    for (int i = 0; i < 9; i++) grid.setItem(i, new ItemStack(items[i]));
    UUID site = UUID.randomUUID();
    grid.getItem(1).getOrCreateTag().putUUID("OriginSanctum", site);
    var recipe = recipe(h, "royal_synthesis_station");
    h.assertTrue(
        recipe instanceof ShapedRecipe && recipe.matches(grid, h.getLevel()),
        "Real shaped station recipe");
    var item = recipe.assemble(grid, h.getLevel().registryAccess());
    h.assertTrue(
        item.is(ModItems.ROYAL_SYNTHESIS_STATION.get())
            && site.equals(item.getTag().getCompound("BlockEntityTag").getUUID("OriginSanctum")),
        "Crafted station keeps origin");
    var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
    try {
      var serializer = ModRecipes.ROYAL_SYNTHESIS_STATION.get();
      serializer.toNetwork(
          buffer, (io.github.meistermods.slimic.recipe.RoyalSynthesisStationRecipe) recipe);
      var decoded = serializer.fromNetwork(recipe.getId(), buffer);
      h.assertTrue(
          decoded.matches(grid, h.getLevel())
              && decoded
                  .assemble(grid, h.getLevel().registryAccess())
                  .getTag()
                  .equals(item.getTag()),
          "Origin survives native recipe wire round trip");
    } finally {
      buffer.release();
    }
    grid.setItem(4, ModItems.OLD_COMMAND_FRAGMENT.get().getDefaultInstance());
    h.assertTrue(!recipe.matches(grid, h.getLevel()), "Seal and command fragment are distinct");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "sanctum_maps")
  public static void boundMapWrongDimensionAndRepeatedUsesNeverSearchAgain(GameTestHelper h) {
    var map = ModItems.ANCIENT_KINGDOM_MAP.get().getDefaultInstance();
    var level = slimic(h);
    KingdomMapItem.bind(map, level, new SanctumPlacement.Candidate(new BlockPos(-200, 90, 700), 1));
    var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "MapBearer"));
    player.setItemInHand(InteractionHand.MAIN_HAND, map);
    var before = map.save(new CompoundTag());
    h.assertTrue(
        map.use(h.getLevel(), player, InteractionHand.MAIN_HAND).getResult()
            == InteractionResult.FAIL,
        "Outside-dimension use fails clearly");
    h.assertTrue(
        map.save(new CompoundTag()).equals(before), "Wrong-dimension use preserves binding");
    h.assertTrue(
        MapItem.getSavedData(map, level) != null
            && MapItem.getSavedData(map, level).dimension.equals(SlimicWorld.DIMENSION),
        "Native map belongs to The Slimic");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "sanctum_progress")
  public static void siteMemoriesAndSingleSharedCoreSurviveCopiesAndSavedDataReload(
      GameTestHelper h) {
    var owner = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "CoreBearer"));
    var data = SanctumSites.get(h.getLevel());
    var site = data.register(h.getLevel(), h.absolutePos(new BlockPos(2, 1, 2)), 3);
    h.assertTrue(
        data.grant(
                owner, site, 1, List.of(ModItems.ROYAL_COURTYARD_SEAL.get().getDefaultInstance()))
            && data.grant(
                owner, site, 2, List.of(ModItems.ROYAL_GALLERY_SEAL.get().getDefaultInstance())),
        "Both memories granted");
    h.assertTrue(site.seals == 3, "Site flags independent of tokens");
    h.assertTrue(
        data.grant(
            owner, site, 4, List.of(ModItems.ROYAL_SYNTHESIS_CORE.get().getDefaultInstance())),
        "First core awarded");
    var second = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "SecondBearer"));
    h.assertTrue(
        !data.grant(
            second, site, 4, List.of(ModItems.ROYAL_SYNTHESIS_CORE.get().getDefaultInstance())),
        "One shared core across players");
    var saved = SanctumSites.load(data.save(new CompoundTag())).find(site.id);
    h.assertTrue(
        saved.seals == 3 && saved.claimed && saved.claimant.equals(owner.getUUID()),
        "Progress and claimant survive restart");
    var item =
        owner.getInventory().items.stream()
            .filter(s -> s.is(ModItems.ROYAL_SYNTHESIS_CORE.get()))
            .findFirst()
            .orElseThrow();
    h.assertTrue(item.getTag().getUUID("OriginSanctum").equals(site.id), "Core carries provenance");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "sanctum_progress")
  public static void destroyedRelayCanRebindWithoutResettingSiteProgress(GameTestHelper h) {
    var level = slimic(h);
    var anchor = new BlockPos(1700 + h.absolutePos(BlockPos.ZERO).getX() * 2, 110, 1700);
    var site = SanctumSites.get(level).register(level, anchor, 0);
    site.seals = 3;
    site.claimed = true;
    var pos = site.at(6, -4, 32);
    level.setBlockAndUpdate(pos, ModBlocks.SANCTUM_RELAY.get().defaultBlockState());
    var relay = (SanctumRelayBlockEntity) level.getBlockEntity(pos);
    relay.onLoad();
    h.assertTrue(
        relay.site(level) == site && site.claimed && site.seals == 3,
        "Replacement recovers original shared record");
    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    h.succeed();
  }

  @GameTest(template = "empty", batch = "sanctum_native", timeoutTicks = 4000)
  public static void naturalStructureGeneratesFiveConnectedRoomsWithoutSearchChunkLoads(
      GameTestHelper h) {
    var level = slimic(h);
    int before = level.getChunkSource().getLoadedChunksCount();
    var forced = Set.copyOf(level.getForcedChunks());
    long start = System.nanoTime();
    var candidate =
        SanctumPlacement.find(
            level, new BlockPos(0, 90, Integer.getInteger("slimic.sanctumTestOriginZ", 0)), 512);
    int originZ = Integer.getInteger("slimic.sanctumTestOriginZ", 0);
    for (int attempt = 1; candidate == null && attempt <= 4; attempt++) {
      int direction = originZ < 0 ? -1 : 1;
      candidate =
          SanctumPlacement.find(
              level, new BlockPos(0, 90, originZ + direction * attempt * 16384), 512);
    }
    h.assertTrue(
        before == level.getChunkSource().getLoadedChunksCount()
            && forced.equals(level.getForcedChunks()),
        "Prediction creates no chunks or force tickets");
    if (candidate == null) {
      var set =
          level
              .registryAccess()
              .registryOrThrow(Registries.STRUCTURE_SET)
              .get(SanctumPlacement.SET.location());
      System.out.println(
          "SLIMIC SANCTUM SEARCH DIAGNOSTICS: set="
              + set
              + " seed="
              + level.getSeed()
              + " sea="
              + level.getSeaLevel()
              + " dimension="
              + level.dimension());
      if (set != null && set.placement() instanceof RandomSpreadStructurePlacement placement)
        for (int rx = -1; rx <= 1; rx++)
          for (int rz = -1; rz <= 1; rz++) {
            var pos =
                placement.getPotentialStructureChunk(
                    level.getSeed(), rx * placement.spacing(), rz * placement.spacing());
            var generator = level.getChunkSource().getGenerator();
            var random = level.getChunkSource().randomState();
            int min = 999, max = -999, wet = 0;
            for (int dx : new int[] {-40, -20, 0, 20, 40})
              for (int dz : new int[] {-40, -20, 0, 20, 40}) {
                int y =
                    generator.getBaseHeight(
                        pos.getMiddleBlockX() + dx,
                        pos.getMiddleBlockZ() + dz,
                        net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                        level,
                        random);
                min = Math.min(min, y);
                max = Math.max(max, y);
                if (!generator
                    .getBaseColumn(
                        pos.getMiddleBlockX() + dx, pos.getMiddleBlockZ() + dz, level, random)
                    .getBlock(y - 1)
                    .getFluidState()
                    .isEmpty()) wet++;
              }
            var biome =
                generator
                    .getBiomeSource()
                    .getNoiseBiome(
                        QuartPos.fromBlock(pos.getMiddleBlockX()),
                        QuartPos.fromBlock(max),
                        QuartPos.fromBlock(pos.getMiddleBlockZ()),
                        random.sampler());
            System.out.println(
                "SLIMIC SANCTUM REJECT: chunk="
                    + pos
                    + " min="
                    + min
                    + " max="
                    + max
                    + " wet="
                    + wet
                    + " biome="
                    + biome
                    + " existing="
                    + SanctumPlacement.existingTerrainAllows(level, pos));
          }
    }
    h.assertTrue(candidate != null, "At least one valid native candidate for this fixture seed");
    System.out.println(
        "SLIMIC SANCTUM CANDIDATE: seed="
            + level.getSeed()
            + " anchor="
            + candidate.anchor()
            + " rotation="
            + candidate.rotation()
            + " search_ms="
            + (System.nanoTime() - start) / 1_000_000);
    var key =
        level
            .registryAccess()
            .registryOrThrow(Registries.STRUCTURE)
            .get(SlimicWorld.id("fallen_sanctum"));
    var chunk = new ChunkPos(candidate.anchor());
    for (int x = chunk.x - 3; x <= chunk.x + 3; x++)
      for (int z = chunk.z - 3; z <= chunk.z + 3; z++) level.getChunk(x, z);
    var nativeStart = level.getChunk(chunk.x, chunk.z).getStartForStructure(key);
    h.assertTrue(
        nativeStart != null && nativeStart.isValid() && nativeStart.getPieces().size() == 5,
        "Native five-piece structure start");
    var corpsePos =
        FallenSanctumPiece.position(candidate.anchor(), candidate.rotation(), -16, 0, -13);
    var corpse = (BrokenSlimeBlockEntity) level.getBlockEntity(corpsePos);
    h.assertTrue(
        corpse != null
            && level.getBlockState(corpsePos).getValue(BrokenSlimeBlock.MINERALIZED)
            && io.github.meistermods.slimic.item.AncientSlimeCoreItem.role(corpse.getAncientCore())
                == io.github.meistermods.slimic.entity.AncientSlime.Role.MINER,
        "Courtyard mining remains use their mineralized appearance and contain the promised core");
    for (int zone = 1; zone <= 4; zone++) {
      int z = -40 + zone * 16 + 8, y = z < 20 ? 0 : -Math.min(4, (z - 20) / 2);
      var pos = FallenSanctumPiece.position(candidate.anchor(), candidate.rotation(), 6, y, z);
      h.assertTrue(
          level.getBlockEntity(pos) instanceof SanctumRelayBlockEntity,
          "Zone " + zone + " relay generated at " + pos);
    }
    for (int z = -40; z < 40; z++) {
      int floor = ((FallenSanctumPiece) nativeStart.getPieces().get(z < -24 ? 0 : 4)).floorLevel(z);
      var footing =
          FallenSanctumPiece.position(candidate.anchor(), candidate.rotation(), 0, floor - 1, z);
      h.assertTrue(
          level.getBlockState(footing).isFaceSturdy(level, footing, Direction.UP),
          "Continuous solid footing across room seam z=" + z);
      for (int y = floor; y < floor + 3; y++)
        h.assertTrue(
            level
                .getBlockState(
                    FallenSanctumPiece.position(candidate.anchor(), candidate.rotation(), 0, y, z))
                .isAir(),
            "Open route across seam z=" + z);
    }
    var entry = FallenSanctumPiece.position(candidate.anchor(), candidate.rotation(), 0, 0, -40);
    int surface =
        level
            .getChunkSource()
            .getGenerator()
            .getBaseHeight(
                entry.getX(),
                entry.getZ(),
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                level,
                level.getChunkSource().randomState());
    h.assertTrue(
        candidate.anchor().getY()
                + ((FallenSanctumPiece) nativeStart.getPieces().get(0)).floorLevel(-40)
            == surface,
        "Approach meets the original terrain without a cliff");
    h.assertTrue(
        SanctumPlacement.existingTerrainAllows(level, chunk),
        "Existing native site can be found again");
    var placement =
        (RandomSpreadStructurePlacement)
            level
                .registryAccess()
                .registryOrThrow(Registries.STRUCTURE_SET)
                .get(SanctumPlacement.SET.location())
                .placement();
    h.assertTrue(
        placement.spacing() == 96 && placement.separation() == 32, "Datapack placement defaults");
    var difficulty = level.getDifficulty();
    var owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "PeacefulSurvey"));
    var site = SanctumSites.get(level).register(level, candidate.anchor(), candidate.rotation());
    try {
      level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
      var deepPos = site.at(6, -4, 32);
      var deep = (SanctumRelayBlockEntity) level.getBlockEntity(deepPos);
      owner.moveTo(net.minecraft.world.phys.Vec3.atCenterOf(deepPos));
      deep.use(owner);
      h.assertTrue(!site.claimed, "Deep reward requires both chamber memories even in Peaceful");
      for (int zone : new int[] {1, 2, 4}) {
        int z = -40 + zone * 16 + 8, y = zone == 4 ? -4 : 0;
        var pos = site.at(6, y, z);
        owner.moveTo(net.minecraft.world.phys.Vec3.atCenterOf(pos));
        ((SanctumRelayBlockEntity) level.getBlockEntity(pos)).use(owner);
      }
      h.assertTrue(
          site.seals == 3
              && site.claimed
              && site.cleared
              && owner.getInventory().items.stream()
                      .filter(s -> s.is(ModItems.ROYAL_SYNTHESIS_CORE.get()))
                      .count()
                  == 1
              && owner.getInventory().items.stream()
                  .anyMatch(s -> s.is(ModItems.ROYAL_SURVEY_COMPASS.get())),
          "Actual generated relays provide the complete noncombat route");
      level.setBlockAndUpdate(deepPos, Blocks.AIR.defaultBlockState());
      level.setBlockAndUpdate(deepPos, ModBlocks.SANCTUM_RELAY.get().defaultBlockState());
      var replacement = (SanctumRelayBlockEntity) level.getBlockEntity(deepPos);
      replacement.onLoad();
      replacement.use(owner);
      var second = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "OtherSurvey"));
      replacement.use(second);
      h.assertTrue(
          owner.getInventory().items.stream()
                      .filter(s -> s.is(ModItems.ROYAL_SYNTHESIS_CORE.get()))
                      .count()
                  == 1
              && second.getInventory().items.stream()
                  .noneMatch(s -> s.is(ModItems.ROYAL_SYNTHESIS_CORE.get())),
          "A replacement relay and another player cannot reset the shared core");
    } finally {
      level.getServer().setDifficulty(difficulty, true);
    }
    int spawned = site.spawned.size();
    for (int zone = 0; zone < 5; zone++) SanctumEncounters.activate(level, site, zone);
    h.assertTrue(
        site.spawned.size() == spawned,
        "A Peaceful-cleared sanctum does not create new waves after difficulty changes");
    h.succeed();
  }
}
