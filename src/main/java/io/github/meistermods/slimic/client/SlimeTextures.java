package io.github.meistermods.slimic.client;

import io.github.meistermods.slimic.Slimic;
import net.minecraft.resources.ResourceLocation;

/** Dedicated, resource-pack-replaceable surfaces for companion accessories and screens. */
public final class SlimeTextures {
  private SlimeTextures() {}

  public static ResourceLocation accessory(String name) {
    return ResourceLocation.fromNamespaceAndPath(
        Slimic.MOD_ID, "textures/entity/accessories/" + name + ".png");
  }

  public static ResourceLocation gui(String name) {
    return ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "textures/gui/" + name + ".png");
  }
}
