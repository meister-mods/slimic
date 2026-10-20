package io.github.meistermods.slimic.world;

import io.github.meistermods.slimic.Slimic;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public final class SlimicWorld {
  public static final ResourceKey<Level> DIMENSION =
      ResourceKey.create(Registries.DIMENSION, id("the_slimic"));
  public static final ResourceKey<Biome> DAMP_PLAINS =
      ResourceKey.create(Registries.BIOME, id("damp_plains"));

  private SlimicWorld() {}

  public static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, path);
  }

  public static boolean allowsPortal(Level level) {
    return level.dimension().equals(Level.OVERWORLD) || level.dimension().equals(DIMENSION);
  }
}
