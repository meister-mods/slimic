package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

public final class DyedSlimeRenderType extends RenderType {
  public static ShaderInstance shader;
  private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();

  private DyedSlimeRenderType(
      String name,
      VertexFormat format,
      VertexFormat.Mode mode,
      int size,
      boolean crumbling,
      boolean sort,
      Runnable setup,
      Runnable clear) {
    super(name, format, mode, size, crumbling, sort, setup, clear);
  }

  public static RenderType forTexture(ResourceLocation texture) {
    return TYPES.computeIfAbsent(
        texture,
        key ->
            create(
                "slimic_dyed_slime",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                true,
                true,
                CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shader))
                    .setTextureState(new TextureStateShard(key, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(true)));
  }
}
