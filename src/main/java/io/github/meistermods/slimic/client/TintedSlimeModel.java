package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.meistermods.slimic.entity.FriendlySlime;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.item.DyeColor;

public final class TintedSlimeModel extends SlimeModel<FriendlySlime> {
  private float red = 1, green = 1, blue = 1;

  public TintedSlimeModel(ModelPart root) {
    super(root);
  }

  @Override
  public void setupAnim(
      FriendlySlime slime,
      float limbSwing,
      float limbAmount,
      float age,
      float headYaw,
      float headPitch) {
    super.setupAnim(slime, limbSwing, limbAmount, age, headYaw, headPitch);
    float[] tint =
        slime.getDyeColorId() < 0
            ? new float[] {1, 1, 1}
            : DyeColor.byId(slime.getDyeColorId()).getTextureDiffuseColors();
    red = tint[0];
    green = tint[1];
    blue = tint[2];
  }

  @Override
  public void renderToBuffer(
      PoseStack pose,
      VertexConsumer vertices,
      int light,
      int overlay,
      float r,
      float g,
      float b,
      float alpha) {
    super.renderToBuffer(pose, vertices, light, overlay, r * red, g * green, b * blue, alpha);
  }
}
