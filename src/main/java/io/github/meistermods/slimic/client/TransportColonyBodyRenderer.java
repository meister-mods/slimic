package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.TransportColonyBody;
import io.github.meistermods.slimic.entity.TransportSlime;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** A render-only proxy supplies existing slime layers without creating a second owned companion. */
public final class TransportColonyBodyRenderer extends EntityRenderer<TransportColonyBody> {
  private final FriendlySlimeRenderer renderer;
  private TransportSlime visual;

  public TransportColonyBodyRenderer(EntityRendererProvider.Context context) {
    super(context);
    renderer =
        new FriendlySlimeRenderer(context) {
          @Override
          protected boolean shouldShowName(FriendlySlime slime) {
            return false;
          }

          @Override
          protected void renderNameTag(
              FriendlySlime slime,
              net.minecraft.network.chat.Component name,
              PoseStack pose,
              MultiBufferSource buffers,
              int light) {
            // The representative alone displays the colony's name and shared status.
          }
        };
    shadowRadius = .5F;
  }

  @Override
  public boolean shouldRender(
      TransportColonyBody body, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
    var colony = body.getColony();
    return colony != null
        && colony.isAlive()
        && colony.level() == body.level()
        && colony.getLinkedBody() == body
        && super.shouldRender(body, frustum, cameraX, cameraY, cameraZ);
  }

  @Override
  public ResourceLocation getTextureLocation(TransportColonyBody body) {
    return ResourceLocation.fromNamespaceAndPath("slimic", "textures/entity/friendly_slime.png");
  }

  @Override
  public void render(
      TransportColonyBody body,
      float yaw,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light) {
    var colony = body.getColony();
    if (colony == null
        || !colony.isAlive()
        || colony.level() != body.level()
        || colony.getLinkedBody() != body) return;
    if (visual == null || visual.level() != body.level())
      visual = ModEntities.TRANSPORT_SLIME.get().create(body.level());
    if (visual == null) return;
    var appearance = colony.colonyAppearance();
    visual.setTame(colony.isTame());
    visual.setOwnerUUID(colony.getOwnerUUID());
    visual.setDyeColorId(
        appearance.contains("SecondDye", Tag.TAG_INT) ? appearance.getInt("SecondDye") : -1);
    visual.setAncientOrigin(appearance.getBoolean("SecondAncient"));
    visual.setPos(body.position());
    visual.xOld = body.xOld;
    visual.yOld = body.yOld;
    visual.zOld = body.zOld;
    visual.xo = body.xo;
    visual.yo = body.yo;
    visual.zo = body.zo;
    visual.setYRot(body.getYRot());
    visual.yRotO = body.yRotO;
    visual.setXRot(body.getXRot());
    visual.xRotO = body.xRotO;
    visual.yBodyRot = body.yBodyRot;
    visual.yBodyRotO = body.yBodyRotO;
    visual.yHeadRot = body.yHeadRot;
    visual.yHeadRotO = body.yHeadRotO;
    visual.tickCount = body.tickCount;
    visual.oldSquish = body.oldSquish;
    visual.squish = body.squish;
    visual.hurtTime = body.hurtTime;
    visual.hurtDuration = body.hurtDuration;
    visual.deathTime = body.deathTime;
    visual.setPose(body.getPose());
    visual.setInvisible(body.isInvisible() || colony.isInvisible());
    visual.setGlowingTag(body.isCurrentlyGlowing() || colony.isCurrentlyGlowing());
    renderer.render(visual, yaw, partial, pose, buffers, light);
  }
}
