package io.github.meistermods.slimic.registry;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.BrokenSlimeBlock;
import io.github.meistermods.slimic.block.BrokenSlimeBlockEntity;
import io.github.meistermods.slimic.block.SlimeChestBlock;
import io.github.meistermods.slimic.block.SlimeChestBlockEntity;
import io.github.meistermods.slimic.block.SlimeTrailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
  public static final DeferredRegister<Block> BLOCKS =
      DeferredRegister.create(ForgeRegistries.BLOCKS, Slimic.MOD_ID);
  public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
      DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Slimic.MOD_ID);
  public static final RegistryObject<Block> SLIME_CHEST =
      BLOCKS.register(
          "slime_chest",
          () ->
              new SlimeChestBlock(
                  BlockBehaviour.Properties.of()
                      .mapColor(MapColor.WOOD)
                      .strength(2.5F)
                      .sound(SoundType.WOOD)
                      .noOcclusion()));
  public static final RegistryObject<BlockEntityType<SlimeChestBlockEntity>> SLIME_CHEST_ENTITY =
      BLOCK_ENTITIES.register(
          "slime_chest",
          () ->
              BlockEntityType.Builder.of(SlimeChestBlockEntity::new, SLIME_CHEST.get())
                  .build(null));

  public static final RegistryObject<Block> SLIME_LIGHT =
      BLOCKS.register(
          "slime_light",
          () ->
              new io.github.meistermods.slimic.block.SlimeLightBlock(
                  BlockBehaviour.Properties.of()
                      .noCollission()
                      .noOcclusion()
                      .replaceable()
                      .air()
                      .noLootTable()
                      .lightLevel(state -> 14)));
  public static final RegistryObject<
          BlockEntityType<io.github.meistermods.slimic.block.SlimeLightBlockEntity>>
      SLIME_LIGHT_ENTITY =
          BLOCK_ENTITIES.register(
              "slime_light",
              () ->
                  BlockEntityType.Builder.of(
                          io.github.meistermods.slimic.block.SlimeLightBlockEntity::new,
                          SLIME_LIGHT.get())
                      .build(null));

  private ModBlocks() {}

  public static final RegistryObject<Block> SLIME_TRAIL =
      BLOCKS.register(
          "slime_trail",
          () ->
              new SlimeTrailBlock(
                  BlockBehaviour.Properties.of()
                      .mapColor(MapColor.COLOR_LIGHT_GREEN)
                      .strength(.1F)
                      .sound(SoundType.SLIME_BLOCK)
                      .noOcclusion()));

  public static final RegistryObject<Block> BROKEN_SLIME =
      BLOCKS.register(
          "broken_slime",
          () ->
              new BrokenSlimeBlock(
                  BlockBehaviour.Properties.of()
                      .mapColor(MapColor.COLOR_GREEN)
                      .strength(.8F, 3_600_000F)
                      .sound(SoundType.SLIME_BLOCK)
                      .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
  public static final RegistryObject<BlockEntityType<BrokenSlimeBlockEntity>> BROKEN_SLIME_ENTITY =
      BLOCK_ENTITIES.register(
          "broken_slime",
          () ->
              BlockEntityType.Builder.of(BrokenSlimeBlockEntity::new, BROKEN_SLIME.get())
                  .build(null));
}
