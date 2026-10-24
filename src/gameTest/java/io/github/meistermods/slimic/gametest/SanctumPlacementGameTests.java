package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.item.KingdomMapItem;
import io.github.meistermods.slimic.registry.ModItems;
import io.github.meistermods.slimic.world.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.*;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SanctumPlacementGameTests {
  private static final String STRUCTURE_ID = "slimic:fallen_sanctum";

  private static ServerLevel level(GameTestHelper h) {
    return Objects.requireNonNull(h.getLevel().getServer().getLevel(SlimicWorld.DIMENSION));
  }

  private static RandomSpreadStructurePlacement placement(ServerLevel level) {
    return (RandomSpreadStructurePlacement)
        level
            .registryAccess()
            .registryOrThrow(Registries.STRUCTURE_SET)
            .get(SanctumPlacement.SET.location())
            .placement();
  }

  private static SanctumPlacement.Candidate find(
      GameTestHelper h, ServerLevel level, BlockPos origin, int radius) {
    int loaded = level.getChunkSource().getLoadedChunksCount();
    var forced = Set.copyOf(level.getForcedChunks());
    var result = SanctumPlacement.find(level, origin, radius);
    h.assertTrue(
        level.getChunkSource().getLoadedChunksCount() == loaded
            && forced.equals(level.getForcedChunks()),
        "A map search reads metadata without loading chunks or adding force tickets");
    return result;
  }

  private static FallenSanctumPiece legacyPiece(
      ServerLevel level, BlockPos anchor, int rotation, int zone, int version) {
    var context = StructurePieceSerializationContext.fromLevel(level);
    var tag = new FallenSanctumPiece(anchor, rotation, zone).createTag(context);
    tag.putInt("ArchitectureVersion", version);
    int start = -40 + zone * 16;
    var a = FallenSanctumPiece.position(anchor, rotation, -32, -16, start);
    var b = FallenSanctumPiece.position(anchor, rotation, 31, version == 0 ? 15 : 28, start + 15);
    tag.putIntArray(
        "BB",
        new int[] {
          Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()),
          Math.max(a.getX(), b.getX()), b.getY(), Math.max(a.getZ(), b.getZ())
        });
    return new FallenSanctumPiece(context, tag);
  }

  private static final class Fixture implements AutoCloseable {
    final ServerLevel level;
    final LevelChunk chunk;
    final Structure structure;
    final Map<Structure, StructureStart> originalStarts;
    final CompoundTag originalData;
    final BlockPos anchor;
    final int rotation;
    boolean wroteDisk;

    Fixture(GameTestHelper h, int region) {
      level = level(h);
      var spread = placement(level);
      var pos =
          spread.getPotentialStructureChunk(
              level.getSeed(), region * spread.spacing(), -region * spread.spacing());
      chunk = level.getChunk(pos.x, pos.z);
      originalStarts = Map.copyOf(chunk.getAllStarts());
      originalData = ChunkSerializer.write(level, chunk);
      structure =
          level
              .registryAccess()
              .registryOrThrow(Registries.STRUCTURE)
              .get(SlimicWorld.id("fallen_sanctum"));
      int predicted =
          FallenSanctumStructure.floor(
              level.getChunkSource().getGenerator(),
              level.getChunkSource().randomState(),
              level,
              pos.getMiddleBlockX(),
              pos.getMiddleBlockZ());
      anchor = pos.getMiddleBlockPosition(predicted == 190 ? 210 : 190);
      rotation = (FallenSanctumStructure.rotation(level.getSeed(), pos.toLong()) + 1) % 4;
      h.assertTrue(
          anchor.getY() != predicted, "The saved castle deliberately differs from v4 height");
    }

    void install(int version) {
      List<StructurePiece> pieces = new ArrayList<>();
      for (int zone = 0; zone < 5; zone++)
        pieces.add(legacyPiece(level, anchor, rotation, zone, version));
      chunk.setStartForStructure(
          structure, new StructureStart(structure, chunk.getPos(), 0, new PiecesContainer(pieces)));
      SanctumPlacement.chunk(chunk, true);
    }

    void write(CompoundTag data) {
      level.getChunkSource().chunkMap.write(chunk.getPos(), data);
      level.getChunkSource().chunkMap.flushWorker();
      wroteDisk = true;
    }

    @Override
    public void close() {
      chunk.setAllStarts(originalStarts);
      SanctumPlacement.chunk(chunk, true);
      if (wroteDisk) {
        level.getChunkSource().chunkMap.write(chunk.getPos(), originalData);
        level.getChunkSource().chunkMap.flushWorker();
      }
    }
  }

  @GameTest(template = "empty", batch = "sanctum_placement", timeoutTicks = 1200)
  public static void loadedLegacyStartsKeepSavedHeightRotationAndMapIdentity(GameTestHelper h) {
    try (var fixture = new Fixture(h, 600)) {
      for (int version = 0; version <= 3; version++) {
        fixture.install(version);
        var start = fixture.chunk.getStartForStructure(fixture.structure);
        h.assertTrue(
            start != null && start.isValid() && start.getPieces().size() == 5,
            "The loaded chunk contains a real five-piece native start");
        var candidate = find(h, fixture.level, fixture.anchor, 1);
        h.assertTrue(
            candidate != null
                && candidate.anchor().equals(fixture.anchor)
                && candidate.rotation() == fixture.rotation,
            "A version-" + version + " castle keeps its saved anchor and rotation");
        var map = ModItems.ANCIENT_KINGDOM_MAP.get().getDefaultInstance();
        KingdomMapItem.bind(map, fixture.level, candidate);
        var binding = map.getTag().getCompound(KingdomMapItem.BINDING);
        h.assertTrue(
            NbtUtils.readBlockPos(binding.getCompound("Anchor")).equals(fixture.anchor)
                && binding.getInt("Rotation") == fixture.rotation
                && binding
                    .getUUID("Site")
                    .equals(SanctumSites.identity(fixture.level.getSeed(), fixture.anchor)),
            "A newly bound map preserves the existing castle's site and reward identity");
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "sanctum_placement", timeoutTicks = 1200)
  public static void unloadedCacheUsesNativeSavedChildrenAndRejectsDamagedStarts(GameTestHelper h) {
    try (var fixture = new Fixture(h, 602)) {
      fixture.install(2);
      var saved = ChunkSerializer.write(fixture.level, fixture.chunk);
      fixture.write(saved);
      var disk =
          fixture.level.getChunkSource().chunkMap.read(fixture.chunk.getPos()).join().orElseThrow();
      var nativeStart =
          StructureStart.loadStaticStart(
              StructurePieceSerializationContext.fromLevel(fixture.level),
              disk.getCompound("structures").getCompound("starts").getCompound(STRUCTURE_ID),
              fixture.level.getSeed());
      h.assertTrue(
          nativeStart != null && nativeStart.isValid() && nativeStart.getPieces().size() == 5,
          "The disk fixture is genuine native StructureStart serialization");
      SanctumPlacement.chunk(fixture.chunk, false);
      // Leave an incompatible in-memory value after cache unload: this branch must use disk.
      fixture.chunk.setStartForStructure(fixture.structure, StructureStart.INVALID_START);
      var candidate = find(h, fixture.level, fixture.anchor, 1);
      h.assertTrue(
          candidate != null
              && candidate.anchor().equals(fixture.anchor)
              && candidate.rotation() == fixture.rotation,
          "Unloaded metadata resolves the saved legacy height and rotation from Children");

      var damaged = saved.copy();
      var children =
          damaged
              .getCompound("structures")
              .getCompound("starts")
              .getCompound(STRUCTURE_ID)
              .getList("Children", Tag.TAG_COMPOUND);
      for (var child : children) ((CompoundTag) child).getCompound("Anchor").remove("Y");
      fixture.write(damaged);
      h.assertTrue(
          find(h, fixture.level, fixture.anchor, 1) == null,
          "A damaged saved anchor is rejected instead of replaced by a new predicted site");
      var absent = saved.copy();
      absent.getCompound("structures").getCompound("starts").remove(STRUCTURE_ID);
      fixture.write(absent);
      h.assertTrue(
          find(h, fixture.level, fixture.anchor, 1) == null,
          "A fully generated chunk without a castle never becomes a new predicted castle");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "sanctum_placement", timeoutTicks = 2400)
  public static void freshCastlesRespectExpandedBordersAndReserveAdjacentCornerChunks(
      GameTestHelper h) {
    var level = level(h);
    SanctumPlacement.Candidate candidate = null;
    for (int attempt = 0; candidate == null && attempt < 4; attempt++)
      candidate = find(h, level, new BlockPos(960000, 90, -960000 - attempt * 16384), 512);
    h.assertTrue(candidate != null, "The fixture has a fresh natural castle candidate");
    var anchor = candidate.anchor();
    var chunk = new ChunkPos(anchor);
    h.assertTrue(
        level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null,
        "Border checks use an ungenerated candidate, not an existing saved castle");
    var border = level.getWorldBorder();
    var settings = border.createSettings();
    try {
      border.setCenter(anchor.getX(), anchor.getZ());
      border.setSize(100);
      h.assertTrue(
          border.isWithinBounds(anchor.offset(-48, 0, -48))
              && border.isWithinBounds(anchor.offset(48, 0, 48)),
          "The former 96-block boundary would fit this border");
      h.assertTrue(
          find(h, level, anchor, 1) == null,
          "A fresh castle is rejected when its expanded outer walls cross the border");
      border.setSize(114);
      var allowed = find(h, level, anchor, 1);
      h.assertTrue(
          allowed != null && allowed.equals(candidate),
          "The same fresh castle is accepted when its complete 112-block footprint fits");
      for (int x : new int[] {-5, 5})
        for (int z : new int[] {-5, 5})
          h.assertTrue(
              SanctumPlacement.reserved(level, new ChunkPos(chunk.x + x, chunk.z + z)),
              "Adjacent corner chunks stay free of collapsed mines and minor ruins");
      h.assertTrue(
          !SanctumPlacement.reserved(level, new ChunkPos(chunk.x + 6, chunk.z + 6)),
          "The reservation remains bounded beyond the one-chunk margin");
    } finally {
      border.applySettings(settings);
    }
    h.succeed();
  }
}
