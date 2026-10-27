package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.meistermods.slimic.entity.BoundCombatSlime;
import io.github.meistermods.slimic.entity.FriendlySlime;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Independent combat bodies share the same cord as transport colonies. */
public final class BoundColonyRenderer extends LinkedColonyRenderer {
  public BoundColonyRenderer(EntityRendererProvider.Context context) {
    super(context);
  }

  @Override
  protected void scale(FriendlySlime slime, PoseStack pose, float partial) {
    super.scale(slime, pose, partial);
    CombatColonyBodyRenderer.motion((BoundCombatSlime) slime, 1, pose, partial);
  }
}
