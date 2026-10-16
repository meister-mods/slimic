package io.github.meistermods.slimic.registry;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.SlimeChestBlock;
import io.github.meistermods.slimic.block.SlimeChestBlockEntity;
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

  private ModBlocks() {}
}
