package io.github.meistermods.slimic.registry;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.RuinDecorationBlock;
import io.github.meistermods.slimic.block.SlimicPlantBlock;
import io.github.meistermods.slimic.block.SlimicSoilBlock;
import io.github.meistermods.slimic.world.SlimicPortalBlock;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.*;

public final class ModWorldBlocks {
  public enum Category {
    BUILDING,
    DECORATION
  }

  public record Entry(
      String id,
      Category category,
      RegistryObject<Block> block,
      RegistryObject<Item> item,
      int color,
      boolean cutout,
      boolean translucent) {}

  public static final DeferredRegister<Block> BLOCKS =
      DeferredRegister.create(ForgeRegistries.BLOCKS, Slimic.MOD_ID);
  public static final DeferredRegister<Item> ITEMS =
      DeferredRegister.create(ForgeRegistries.ITEMS, Slimic.MOD_ID);
  private static final Map<String, Entry> CATALOG = new LinkedHashMap<>();
  public static final RegistryObject<Block> PORTAL =
      BLOCKS.register("slimic_portal", SlimicPortalBlock::new);

  static {
    full("slimic_stone", "stone", 0xFFFFFF);
    full("slimic_cobblestone", "stone", 0xB4C69E);
    full("mossy_slimic_cobblestone", "stone", 0xC5D6AD);
    full("slimic_stone_bricks", "stone", 0xFFFFFF);
    full("cracked_slimic_stone_bricks", "stone", 0xABBC96);
    full("mossy_slimic_stone_bricks", "stone", 0xB6CD9C);
    full("chiseled_slimic_stone_bricks", "stone", 0xBACDAF);
    full("slimic_tiles", "stone", 0xFFFFFF);
    full("cracked_slimic_tiles", "stone", 0xB6CB97);
    full("ancient_foundation", "stone", 0xB0BE94);
    full("ancient_foundation_bricks", "stone", 0x88997B);
    full("pale_slimic_stone", "stone", 0xF5FFF1);
    full("polished_slimic_stone", "stone", 0xC4D9B3);
    full("slime_marble", "stone", 0xFFFFFF);
    full("slime_marble_bricks", "stone", 0xE9F3D5);
    full("chiseled_slime_marble", "stone", 0xD1E6B7);
    full("black_slime_stone", "stone", 0x9DB3A2);
    full("polished_black_slime_stone", "stone", 0x9DBCA2);
    full("slimic_slate", "stone", 0xB4CBA7);
    full("slimic_slate_bricks", "stone", 0xB4CBA7);
    full("ruined_bricks", "stone", 0xC2C49A);
    full("slimic_soil", "soil", 0xFFFFFF);
    full("damp_grass_block", "grass", 0xA0B54A);
    full("slime_silt", "soil", 0xFFFFFF);
    full("slimic_clay", "soil", 0xB2C5A6);
    full("slime_mud", "soil", 0xA2B78E);
    full("congealed_slime", "gel", 0xFFFFFF);
    full("dried_slime", "stone", 0xB7CA8C);
    full("hardened_slime", "stone", 0x91AA78);
    full("luminous_slime", "light", 0xD5FFBC);
    full("gel_glass", "glass", 0xFFFFFF);
    full("slime_infused_planks", "wood", 0xB3C88E);
    shaped("slimic_stone_stairs", "slimic_stone", "stairs", 0xFFFFFF);
    shaped("slimic_stone_slab", "slimic_stone", "slab", 0xFFFFFF);
    shaped("slimic_cobblestone_stairs", "slimic_cobblestone", "stairs", 0xB4C69E);
    shaped("slimic_cobblestone_slab", "slimic_cobblestone", "slab", 0xB4C69E);
    shaped("slimic_stone_bricks_stairs", "slimic_stone_bricks", "stairs", 0xFFFFFF);
    shaped("slimic_stone_bricks_slab", "slimic_stone_bricks", "slab", 0xFFFFFF);
    shaped("mossy_slimic_stone_bricks_stairs", "mossy_slimic_stone_bricks", "stairs", 0xB6CD9C);
    shaped("mossy_slimic_stone_bricks_slab", "mossy_slimic_stone_bricks", "slab", 0xB6CD9C);
    shaped("slimic_tiles_stairs", "slimic_tiles", "stairs", 0xFFFFFF);
    shaped("slimic_tiles_slab", "slimic_tiles", "slab", 0xFFFFFF);
    shaped("slime_marble_stairs", "slime_marble", "stairs", 0xFFFFFF);
    shaped("slime_marble_slab", "slime_marble", "slab", 0xFFFFFF);
    shaped("black_slime_stone_stairs", "black_slime_stone", "stairs", 0x9DB3A2);
    shaped("black_slime_stone_slab", "black_slime_stone", "slab", 0x9DB3A2);
    shaped("slime_infused_planks_stairs", "slime_infused_planks", "stairs", 0xB3C88E);
    shaped("slime_infused_planks_slab", "slime_infused_planks", "slab", 0xB3C88E);
    shaped("slimic_cobblestone_wall", "slimic_cobblestone", "wall", 0xB4C69E);
    shaped("mossy_slimic_cobblestone_wall", "mossy_slimic_cobblestone", "wall", 0xC5D6AD);
    shaped("slimic_stone_bricks_wall", "slimic_stone_bricks", "wall", 0xFFFFFF);
    shaped("mossy_slimic_stone_bricks_wall", "mossy_slimic_stone_bricks", "wall", 0xB6CD9C);
    shaped("slimic_slate_bricks_wall", "slimic_slate_bricks", "wall", 0xB4CBA7);
    shaped("slime_marble_bricks_wall", "slime_marble_bricks", "wall", 0xE9F3D5);
    decoration(
        "gel_lantern",
        false,
        10,
        0xD5FFBC,
        List.of(
            new double[] {3, 0, 3, 13, 2, 13},
            new double[] {4, 2, 4, 12, 12, 12},
            new double[] {3, 12, 3, 13, 14, 13},
            new double[] {7, 14, 7, 9, 16, 9}));
    decoration(
        "ancient_brazier",
        false,
        12,
        0xB0BE94,
        List.of(
            new double[] {2, 0, 2, 14, 3, 14},
            new double[] {5, 3, 5, 11, 7, 11},
            new double[] {1, 7, 1, 15, 10, 15},
            new double[] {3, 10, 3, 13, 12, 13}));
    decoration(
        "dormant_beacon",
        false,
        0,
        0xFFFFFF,
        List.of(
            new double[] {2, 0, 2, 14, 3, 14},
            new double[] {4, 3, 4, 12, 12, 12},
            new double[] {6, 12, 6, 10, 16, 10}));
    decoration(
        "command_pedestal",
        false,
        0,
        0xBACDAF,
        List.of(
            new double[] {1, 0, 1, 15, 3, 15},
            new double[] {4, 3, 4, 12, 12, 12},
            new double[] {0, 12, 0, 16, 16, 16}));
    decoration(
        "cracked_command_pedestal",
        false,
        0,
        0xABBC96,
        List.of(
            new double[] {1, 0, 1, 15, 3, 15},
            new double[] {4, 3, 4, 12, 8, 12},
            new double[] {3, 8, 4, 9, 11, 12}));
    decoration(
        "fallen_pillar",
        false,
        0,
        0xFFFFFF,
        List.of(new double[] {0, 0, 4, 16, 8, 12}, new double[] {0, 0, 2, 3, 11, 14}));
    decoration(
        "broken_pillar",
        false,
        0,
        0xC4D9B3,
        List.of(
            new double[] {2, 0, 2, 14, 3, 14},
            new double[] {4, 3, 4, 12, 11, 12},
            new double[] {4, 11, 4, 9, 14, 11}));
    decoration(
        "royal_waymarker",
        false,
        0,
        0xFFFFFF,
        List.of(
            new double[] {3, 0, 3, 13, 3, 13},
            new double[] {5, 3, 6, 11, 15, 10},
            new double[] {4, 14, 5, 12, 16, 11}));
    decoration(
        "weathered_waymarker",
        false,
        0,
        0xB6CD9C,
        List.of(
            new double[] {3, 0, 3, 13, 2, 13},
            new double[] {5, 2, 6, 11, 11, 10},
            new double[] {5, 11, 6, 9, 14, 9}));
    decoration(
        "royal_crest",
        false,
        0,
        0xD1E6B7,
        List.of(
            new double[] {1, 0, 5, 15, 2, 11},
            new double[] {3, 2, 6, 13, 15, 10},
            new double[] {5, 15, 6, 11, 16, 10}));
    decoration(
        "stone_tablet",
        false,
        0,
        0xBACDAF,
        List.of(new double[] {1, 0, 4, 15, 2, 12}, new double[] {2, 2, 7, 14, 15, 10}));
    decoration(
        "cracked_stone_tablet",
        false,
        0,
        0xABBC96,
        List.of(
            new double[] {2, 0, 4, 14, 2, 12},
            new double[] {3, 2, 7, 13, 10, 10},
            new double[] {3, 10, 7, 8, 14, 10}));
    decoration(
        "abandoned_crate",
        true,
        0,
        0xB3C88E,
        List.of(
            new double[] {1, 0, 1, 15, 13, 15},
            new double[] {0, 0, 0, 3, 15, 3},
            new double[] {13, 0, 0, 16, 15, 3},
            new double[] {0, 0, 13, 3, 15, 16},
            new double[] {13, 0, 13, 16, 15, 16}));
    decoration(
        "broken_crate",
        true,
        0,
        0xB3C88E,
        List.of(
            new double[] {1, 0, 1, 15, 2, 15},
            new double[] {1, 2, 1, 3, 11, 15},
            new double[] {13, 2, 1, 15, 7, 15},
            new double[] {3, 2, 13, 13, 10, 15}));
    decoration(
        "supply_urn",
        false,
        0,
        0xB2C5A6,
        List.of(
            new double[] {4, 0, 4, 12, 2, 12},
            new double[] {2, 2, 2, 14, 10, 14},
            new double[] {4, 10, 4, 12, 13, 12},
            new double[] {3, 13, 3, 13, 15, 13}));
    decoration(
        "cracked_supply_urn",
        false,
        0,
        0xC2C49A,
        List.of(
            new double[] {4, 0, 4, 12, 2, 12},
            new double[] {2, 2, 2, 14, 6, 14},
            new double[] {2, 6, 2, 5, 10, 14},
            new double[] {5, 6, 11, 13, 9, 14}));
    decoration(
        "tool_rack",
        true,
        0,
        0xB3C88E,
        List.of(
            new double[] {2, 0, 3, 4, 16, 6},
            new double[] {12, 0, 3, 14, 16, 6},
            new double[] {1, 11, 2, 15, 14, 7},
            new double[] {6, 4, 3, 8, 13, 5}));
    decoration(
        "broken_workbench",
        true,
        0,
        0xB3C88E,
        List.of(
            new double[] {0, 10, 0, 16, 14, 16},
            new double[] {1, 0, 1, 4, 10, 4},
            new double[] {12, 0, 1, 15, 10, 4},
            new double[] {1, 0, 12, 4, 7, 15}));
    decoration(
        "ruined_bench",
        true,
        0,
        0xB3C88E,
        List.of(
            new double[] {0, 5, 3, 16, 8, 12},
            new double[] {2, 0, 4, 5, 5, 11},
            new double[] {11, 0, 4, 14, 5, 11},
            new double[] {0, 8, 10, 16, 14, 12}));
    decoration(
        "low_stone_table",
        false,
        0,
        0xFFFFFF,
        List.of(
            new double[] {0, 8, 0, 16, 11, 16},
            new double[] {3, 0, 3, 6, 8, 6},
            new double[] {10, 0, 3, 13, 8, 6},
            new double[] {3, 0, 10, 6, 8, 13},
            new double[] {10, 0, 10, 13, 8, 13}));
    decoration(
        "stone_bench",
        false,
        0,
        0xFFFFFF,
        List.of(
            new double[] {0, 5, 3, 16, 8, 13},
            new double[] {2, 0, 4, 6, 5, 12},
            new double[] {10, 0, 4, 14, 5, 12}));
    decoration(
        "gel_basin",
        false,
        0,
        0xFFFFFF,
        List.of(
            new double[] {1, 0, 1, 15, 2, 15},
            new double[] {1, 2, 1, 3, 7, 15},
            new double[] {13, 2, 1, 15, 7, 15},
            new double[] {3, 2, 1, 13, 7, 3},
            new double[] {3, 2, 13, 13, 7, 15},
            new double[] {3, 2, 3, 13, 4, 13}));
    decoration(
        "dried_gel_basin",
        false,
        0,
        0xB7CA8C,
        List.of(
            new double[] {2, 0, 2, 14, 2, 14},
            new double[] {2, 2, 2, 4, 6, 14},
            new double[] {12, 2, 2, 14, 6, 14},
            new double[] {4, 2, 2, 12, 6, 4},
            new double[] {4, 2, 12, 12, 6, 14}));
    decoration(
        "relic_shard_pile",
        false,
        0,
        0xFFFFFF,
        List.of(
            new double[] {1, 0, 2, 7, 3, 8},
            new double[] {8, 0, 6, 14, 5, 13},
            new double[] {4, 0, 11, 9, 2, 15}));
    decoration(
        "fallen_masonry_pile",
        false,
        0,
        0xC2C49A,
        List.of(
            new double[] {0, 0, 1, 8, 4, 9},
            new double[] {7, 0, 6, 16, 6, 15},
            new double[] {3, 4, 4, 10, 8, 10}));
    decoration(
        "gel_crystal_cluster",
        false,
        6,
        0xD5FFBC,
        List.of(
            new double[] {5, 0, 5, 11, 15, 11},
            new double[] {1, 0, 7, 5, 9, 12},
            new double[] {10, 0, 2, 15, 11, 7}));
    decoration(
        "small_gel_crystal_cluster",
        false,
        4,
        0xD5FFBC,
        List.of(new double[] {5, 0, 5, 10, 9, 10}, new double[] {10, 0, 8, 13, 6, 12}));
    decoration(
        "old_slime_banner",
        true,
        0,
        0xB3C88E,
        List.of(
            new double[] {3, 0, 3, 13, 2, 13},
            new double[] {7, 2, 7, 9, 16, 9},
            new double[] {3, 8, 8, 13, 15, 9}));
    plant("slime_roots", 0, 0xC7EDAC);
    plant("slime_tuft", 0, 0xC7EDAC);
    plant("gel_cap", 0, 0xC7EDAC);
    plant("glowing_bloom", 7, 0xC7EDAC);
  }

  private ModWorldBlocks() {}

  public static Collection<Entry> entries() {
    return Collections.unmodifiableCollection(CATALOG.values());
  }

  public static Block block(String id) {
    Entry entry = CATALOG.get(id);
    if (entry == null) throw new IllegalArgumentException("Unknown Slimic world block: " + id);
    return entry.block().get();
  }

  private static void add(
      String id,
      Category category,
      Supplier<Block> factory,
      int color,
      boolean cutout,
      boolean translucent) {
    var block = BLOCKS.register(id, factory);
    RegistryObject<Item> item =
        ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
    if (CATALOG.putIfAbsent(id, new Entry(id, category, block, item, color, cutout, translucent))
        != null) throw new IllegalStateException("Duplicate world block: " + id);
  }

  private static BlockBehaviour.Properties material(String kind) {
    return switch (kind) {
      case "soil", "grass" ->
          BlockBehaviour.Properties.copy(Blocks.DIRT).mapColor(MapColor.COLOR_GREEN);
      case "wood" ->
          BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).mapColor(MapColor.COLOR_GREEN);
      case "gel" -> BlockBehaviour.Properties.copy(Blocks.SLIME_BLOCK).strength(.8F);
      case "light" ->
          BlockBehaviour.Properties.copy(Blocks.SLIME_BLOCK).strength(.8F).lightLevel(s -> 10);
      case "glass" ->
          BlockBehaviour.Properties.copy(Blocks.GLASS).mapColor(MapColor.COLOR_GREEN).noOcclusion();
      default ->
          BlockBehaviour.Properties.copy(Blocks.STONE)
              .mapColor(MapColor.COLOR_GREEN)
              .strength(2.5F, 6);
    };
  }

  private static void full(String id, String kind, int color) {
    add(
        id,
        Category.BUILDING,
        () -> {
          var properties = material(kind);
          if (id.equals("slimic_soil") || id.equals("damp_grass_block") || id.equals("slime_mud"))
            return new SlimicSoilBlock(properties);
          return switch (kind) {
            case "glass" -> new GlassBlock(properties);
            case "gel", "light" -> new SlimeBlock(properties);
            default -> new Block(properties);
          };
        },
        color,
        false,
        kind.equals("glass"));
  }

  private static void shaped(String id, String base, String shape, int color) {
    add(
        id,
        Category.BUILDING,
        () ->
            switch (shape) {
              case "stairs" ->
                  new StairBlock(
                      () -> block(base).defaultBlockState(),
                      BlockBehaviour.Properties.copy(block(base)));
              case "slab" -> new SlabBlock(BlockBehaviour.Properties.copy(block(base)));
              case "wall" -> new WallBlock(BlockBehaviour.Properties.copy(block(base)));
              default -> throw new IllegalArgumentException(shape);
            },
        color,
        false,
        false);
  }

  private static void decoration(
      String id, boolean wood, int light, int color, List<double[]> boxes) {
    add(
        id,
        Category.DECORATION,
        () ->
            new RuinDecorationBlock(
                material(wood ? "wood" : "stone").noOcclusion().lightLevel(s -> light), boxes),
        color,
        false,
        false);
  }

  private static void plant(String id, int light, int color) {
    add(
        id,
        Category.DECORATION,
        () ->
            new SlimicPlantBlock(
                BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT)
                    .noCollission()
                    .noOcclusion()
                    .instabreak()
                    .sound(SoundType.WET_GRASS)
                    .lightLevel(s -> light),
                id.equals("gel_cap") ? 7 : 13),
        color,
        true,
        false);
  }
}
