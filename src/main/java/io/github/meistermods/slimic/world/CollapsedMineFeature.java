package io.github.meistermods.slimic.world;

import io.github.meistermods.slimic.entity.QuarryPlan;
import io.github.meistermods.slimic.entity.QuarryStaircaseData;
import io.github.meistermods.slimic.registry.ModBlocks;
import io.github.meistermods.slimic.registry.ModWorldBlocks;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Abandoned slime excavations with a surface entrance, bounded to their generating chunk. */
public final class CollapsedMineFeature extends Feature<NoneFeatureConfiguration> {
  private static final TagKey<Block> TERRAIN =
      TagKey.create(Registries.BLOCK, SlimicWorld.id("collapsed_mine_terrain"));

  public CollapsedMineFeature() {
    super(NoneFeatureConfiguration.CODEC);
  }

  private static BlockState state(String id) {
    return ModWorldBlocks.block(id).defaultBlockState();
  }

  private static final class Layout {
    final ChunkPos chunk;
    final int top;
    final Rotation rotation;
    final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
    final Map<BlockPos, io.github.meistermods.slimic.entity.AncientSlime.Role> remains =
        new LinkedHashMap<>();
    BlockPos supplies, worker, guardian, face;
    io.github.meistermods.slimic.entity.AncientSlime.Role workRole;

    Layout(ChunkPos chunk, int top, Rotation rotation) {
      this.chunk = chunk;
      this.top = top;
      this.rotation = rotation;
    }

    BlockPos position(int x, int y, int z) {
      int rx = x, rz = z;
      switch (rotation) {
        case CLOCKWISE_90 -> {
          rx = 15 - z;
          rz = x;
        }
        case CLOCKWISE_180 -> {
          rx = 15 - x;
          rz = 15 - z;
        }
        case COUNTERCLOCKWISE_90 -> {
          rx = z;
          rz = 15 - x;
        }
        default -> {}
      }
      return new BlockPos(chunk.getMinBlockX() + rx, y, chunk.getMinBlockZ() + rz);
    }

    void put(int x, int y, int z, BlockState state) {
      blocks.put(position(x, y, z), state.rotate(rotation));
    }

    void broken(int x, int y, int z) {
      put(
          x,
          y,
          z,
          ModBlocks.BROKEN_SLIME
              .get()
              .defaultBlockState()
              .setValue(io.github.meistermods.slimic.block.BrokenSlimeBlock.MINERALIZED, true));
      remains.put(position(x, y, z), workRole);
    }

    void air(int x, int y, int z) {
      put(x, y, z, Blocks.AIR.defaultBlockState());
    }
  }

  @Override
  public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
    if (SanctumPlacement.reserved(context.level().getLevel(), new ChunkPos(context.origin()))
        || AssemblyPlacement.reserved(context.level().getLevel(), new ChunkPos(context.origin())))
      return false;
    var level = context.level();
    if (!level.getLevel().dimension().equals(SlimicWorld.DIMENSION)) return false;
    var chunk = new ChunkPos(context.origin());
    int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) {
        int y =
            level.getHeight(
                Heightmap.Types.WORLD_SURFACE_WG,
                chunk.getMinBlockX() + x,
                chunk.getMinBlockZ() + z);
        low = Math.min(low, y);
        high = Math.max(high, y);
      }
    if (low < level.getSeaLevel() + 2 || high - low > 2 || high + 4 >= level.getMaxBuildHeight())
      return false;
    var random = context.random();
    boolean quarry = random.nextBoolean();
    Rotation rotation = quarry ? Rotation.NONE : Rotation.values()[random.nextInt(4)];
    int top = high - 1;
    if (!quarry) {
      var entrance = new Layout(chunk, top, rotation).position(5, 0, 0);
      top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, entrance.getX(), entrance.getZ()) - 1;
    }
    int bottom = top - (quarry ? 12 + random.nextInt(7) : 6);
    if (bottom <= level.getMinBuildHeight() + 3) return false;
    var layout = new Layout(chunk, top, rotation);
    // Validate the complete work site before changing any terrain or persistent stair profiles.
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++)
        for (int y = bottom; y <= top + 4; y++) {
          var pos = new BlockPos(chunk.getMinBlockX() + x, y, chunk.getMinBlockZ() + z);
          var old = level.getBlockState(pos);
          if (old.hasBlockEntity()
              || level.getBlockEntity(pos) != null
              || !old.getFluidState().isEmpty()
              || old.getDestroySpeed(level, pos) < 0
              || !(old.isAir() || old.canBeReplaced() || old.is(TERRAIN))) return false;
        }
    QuarryPlan plan = null;
    if (quarry) {
      plan = new QuarryPlan(chunk, top, bottom + 2);
      quarry(layout, level, plan, bottom);
    } else {
      tunnel(layout);
    }
    for (var entry : layout.blocks.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 2);
    var ruin = new java.util.UUID(random.nextLong(), random.nextLong());
    for (var entry : layout.remains.entrySet()) {
      if (level.getBlockEntity(entry.getKey())
          instanceof io.github.meistermods.slimic.block.BrokenSlimeBlockEntity corpse)
        corpse.setAncientCore(
            io.github.meistermods.slimic.item.AncientSlimeCoreItem.fromRemains(
                entry.getValue(), new java.util.UUID(random.nextLong(), random.nextLong()), 2));
    }
    if (level.getBlockEntity(layout.supplies)
        instanceof io.github.meistermods.slimic.block.SlimeChestBlockEntity chest) {
      chest.setItem(
          0,
          new net.minecraft.world.item.ItemStack(
              io.github.meistermods.slimic.registry.ModItems.OLD_COMMAND_FRAGMENT.get(),
              layout.remains.size()));
      chest.setItem(
          1,
          new net.minecraft.world.item.ItemStack(
              net.minecraft.world.item.Items.SLIME_BALL, layout.remains.size()));
      chest.setItem(
          2,
          new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 32));
      chest.setItem(
          3, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
      chest.setItem(
          4,
          new net.minecraft.world.item.ItemStack(
              io.github.meistermods.slimic.registry.ModItems.KINGDOM_MAP_FRAGMENT.get()));
      if (random.nextFloat() < .35F)
        chest.setItem(
            5,
            new net.minecraft.world.item.ItemStack(
                io.github.meistermods.slimic.registry.ModItems.ASSEMBLY_CLUE.get()));
    }
    spawn(
        level,
        layout.worker,
        ruin,
        layout.workRole,
        layout.face,
        quarry
            ? net.minecraft.core.Direction.EAST
            : rotation.rotate(net.minecraft.core.Direction.SOUTH));
    spawn(
        level,
        layout.guardian,
        ruin,
        io.github.meistermods.slimic.entity.AncientSlime.Role.MINE_GUARD,
        null,
        net.minecraft.core.Direction.NORTH);
    if (plan != null) {
      var profile = plan;
      var serverLevel = level.getLevel();
      // Feature placement can run on a generation worker; SavedData belongs to the server thread.
      serverLevel
          .getServer()
          .execute(() -> QuarryStaircaseData.get(serverLevel).register(chunk, profile));
    }
    return true;
  }

  private static void spawn(
      WorldGenLevel level,
      BlockPos pos,
      java.util.UUID ruin,
      io.github.meistermods.slimic.entity.AncientSlime.Role role,
      BlockPos face,
      net.minecraft.core.Direction direction) {
    var type =
        role == io.github.meistermods.slimic.entity.AncientSlime.Role.MINING
            ? io.github.meistermods.slimic.registry.ModEntities.FORGOTTEN_MINING_SLIME.get()
            : role == io.github.meistermods.slimic.entity.AncientSlime.Role.TUNNEL
                ? io.github.meistermods.slimic.registry.ModEntities.FORGOTTEN_TUNNEL_SLIME.get()
                : io.github.meistermods.slimic.registry.ModEntities.MINE_GUARDIAN_SLIME.get();
    var slime = type.create(level.getLevel());
    if (slime == null) return;
    slime.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
    slime.bindToRuin(pos, ruin, null, null, 2);
    if (face != null) slime.bindMineFace(face, direction);
    net.minecraftforge.event.ForgeEventFactory.onFinalizeSpawn(
        slime,
        level,
        level.getCurrentDifficultyAt(pos),
        net.minecraft.world.entity.MobSpawnType.STRUCTURE,
        null,
        null);
    if (!slime.isSpawnCancelled()
        && slime.isAlive()
        && slime.level() == level.getLevel()
        && slime.blockPosition().equals(pos)
        && level.noCollision(slime)) level.addFreshEntity(slime);
  }

  private static void tunnel(Layout l) {
    l.workRole = io.github.meistermods.slimic.entity.AncientSlime.Role.TUNNEL;
    for (int x = 1; x <= 13; x++)
      for (int z = 0; z <= 5; z++) {
        for (int y = l.top - 2; y <= l.top; y++) l.put(x, y, z, state("slimic_cobblestone"));
        for (int y = l.top + 1; y <= l.top + 4; y++) l.air(x, y, z);
      }
    for (int z = 2; z <= 13; z++) {
      int floor = l.top - Math.min(6, z - 2);
      for (int x = 4; x <= 6; x++) {
        l.put(x, floor, z, state("slimic_cobblestone"));
        for (int y = 1; y <= 3; y++) l.air(x, floor + y, z);
      }
      if (z == 2 || z == 6 || z == 10 || z == 13) {
        for (int y = 1; y <= 3; y++) {
          l.put(3, floor + y, z, state("slime_infused_planks"));
          l.put(7, floor + y, z, state("slime_infused_planks"));
        }
        for (int x = 3; x <= 7; x++) l.put(x, floor + 4, z, state("slime_infused_planks"));
      }
    }
    int bottom = l.top - 6;
    for (int x = 4; x <= 6; x++)
      for (int y = 1; y <= 3; y++) l.put(x, bottom + y, 14, state("slimic_stone"));
    // A widened terminal alcove keeps living remnants clear of their fallen coworkers.
    for (int x = 3; x <= 7; x++)
      for (int z = 11; z <= 12; z++) {
        l.put(x, bottom, z, state("slimic_cobblestone"));
        for (int y = 1; y <= 3; y++) l.air(x, bottom + y, z);
      }
    l.broken(3, bottom + 1, 11);
    l.broken(7, bottom + 1, 12);
    l.worker = l.position(5, bottom + 1, 11);
    l.guardian = l.position(5, bottom + 1, 9);
    l.face = l.position(5, bottom + 1, 14);
    l.supplies = l.position(8, l.top + 1, 2);
    l.put(8, l.top + 1, 2, ModBlocks.SLIME_CHEST.get().defaultBlockState());
    // Keep a surface remnant within every ruin footprint so later features preserve the entrance.
    l.broken(9, l.top + 1, 4);
    l.put(4, bottom + 2, 8, Blocks.COBWEB.defaultBlockState());
    l.put(6, bottom + 3, 11, Blocks.COBWEB.defaultBlockState());
    l.put(4, bottom + 1, 13, state("slimic_cobblestone"));
    l.put(6, bottom + 1, 13, state("slimic_cobblestone"));
    l.put(6, bottom + 2, 13, Blocks.GRAVEL.defaultBlockState());
    l.put(10, l.top + 1, 3, state("broken_workbench"));
    l.put(2, l.top + 1, 1, state("weathered_waymarker"));

    l.put(5, l.top + 3, 2, Blocks.COBWEB.defaultBlockState());
  }

  private static void quarry(Layout l, WorldGenLevel level, QuarryPlan plan, int bottom) {
    l.workRole = io.github.meistermods.slimic.entity.AncientSlime.Role.MINING;
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) {
        var floor = new BlockPos(l.chunk.getMinBlockX() + x, bottom, l.chunk.getMinBlockZ() + z);
        if (level.getBlockState(floor).isAir()) l.put(x, bottom, z, state("slimic_stone"));
        for (int y = bottom + 1; y <= l.top + 4; y++) {
          var pos = new BlockPos(l.chunk.getMinBlockX() + x, y, l.chunk.getMinBlockZ() + z);
          if (plan.isRampFloor(pos)) {
            l.put(x, y, z, state("slimic_cobblestone"));
          } else l.air(x, y, z);
        }
      }
    // A remaining shelf and fallen workers mark an interrupted layer, above the undug rock.
    for (int x = 10; x <= 13; x++)
      for (int z = 6; z <= 10; z++) l.put(x, bottom + 1, z, state("slimic_stone"));
    l.worker = l.position(5, bottom + 1, 8);
    l.guardian = l.position(12, bottom + 2, 6);
    l.face = l.position(10, bottom + 1, 8);
    l.supplies = l.position(4, bottom + 1, 6);
    l.put(4, bottom + 1, 6, ModBlocks.SLIME_CHEST.get().defaultBlockState());
    l.broken(7, bottom + 1, 8);
    l.broken(11, bottom + 2, 8);
    l.put(8, bottom + 1, 9, state("slimic_cobblestone"));
    l.put(8, bottom + 2, 9, Blocks.GRAVEL.defaultBlockState());
    for (int x : new int[] {3, 12}) {
      l.put(x, bottom + 1, 2, Blocks.COBWEB.defaultBlockState());
      l.put(x, bottom + 2, 13, Blocks.COBWEB.defaultBlockState());
    }
    for (int y = 1; y <= 4; y++) l.put(0, l.top + y, 0, state("slime_infused_planks"));
    for (int x = 1; x <= 4; x++) l.put(x, l.top + 4, 0, state("slime_infused_planks"));
    l.put(1, l.top + 3, 0, Blocks.COBWEB.defaultBlockState());
  }
}
