package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.meistermods.slimic.entity.BoundCombatSlime;
import io.github.meistermods.slimic.entity.CombatColonyBody;
import io.github.meistermods.slimic.entity.CombatSlime;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.registry.ModEntities;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/** A render-only proxy supplies existing slime layers without creating a second owned companion. */
public final class CombatColonyBodyRenderer extends EntityRenderer<CombatColonyBody> {
  private final FriendlySlimeRenderer renderer;
  private CombatSlime visual;
  private CombatColonyBody renderedBody;

  public CombatColonyBodyRenderer(EntityRendererProvider.Context context) {
    super(context);
    renderer =
        new FriendlySlimeRenderer(context) {
          @Override
          protected void scale(FriendlySlime slime, PoseStack pose, float partial) {
            super.scale(slime, pose, partial);
            if (renderedBody != null && renderedBody.getColony() != null)
              motion(renderedBody.getColony(), 2, pose, partial);
          }

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
      CombatColonyBody body, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
    var colony = body.getColony();
    return colony != null
        && colony.isAlive()
        && colony.level() == body.level()
        && colony.getLinkedBody() == body
        && super.shouldRender(body, frustum, cameraX, cameraY, cameraZ);
  }

  @Override
  public ResourceLocation getTextureLocation(CombatColonyBody body) {
    return ResourceLocation.fromNamespaceAndPath("slimic", "textures/entity/friendly_slime.png");
  }

  @Override
  public void render(
      CombatColonyBody body,
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
      visual = ModEntities.COMBAT_SLIME.get().create(body.level());
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
    for (var slot : EquipmentSlot.values())
      visual.setItemSlot(slot, colony.memberEquipment(1, slot).copy());
    visual.swinging = body.swinging;
    visual.swingTime = body.swingTime;
    visual.attackAnim = body.attackAnim;
    visual.oAttackAnim = body.oAttackAnim;
    if (body.isUsingItem()) visual.startUsingItem(body.getUsedItemHand());
    else visual.stopUsingItem();
    renderedBody = body;
    try {
      renderer.render(visual, yaw, partial, pose, buffers, light);
    } finally {
      renderedBody = null;
    }
  }

  static void motion(BoundCombatSlime colony, int member, PoseStack pose, float partial) {
    if (colony.assistMember() != member) return;
    float ticks = colony.assistTicks() + partial;
    if (ticks <= 6) {
      float coil = net.minecraft.util.Mth.clamp(ticks / 6, 0, 1);
      pose.scale(1 + coil * .15F, 1 - coil * .25F, 1 + coil * .1F);
    } else {
      pose.mulPose(Axis.XP.rotationDegrees(-18));
      pose.scale(.9F, .8F, 1.4F);
    }
  }
}
