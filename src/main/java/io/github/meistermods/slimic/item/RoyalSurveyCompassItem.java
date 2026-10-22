package io.github.meistermods.slimic.item;

import io.github.meistermods.slimic.world.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** A horizontal bearing to the next sealed zone, not a coordinate reveal or teleporter. */
public final class RoyalSurveyCompassItem extends RoyalRelicItem {
  public RoyalSurveyCompassItem(Properties properties) {
    super(properties);
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player actor, InteractionHand hand) {
    var stack = actor.getItemInHand(hand);
    if (!(actor instanceof ServerPlayer player)) return InteractionResultHolder.success(stack);
    if (!level.dimension().equals(SlimicWorld.DIMENSION)) {
      player.displayClientMessage(Component.translatable("message.slimic.map_dimension"), true);
      return InteractionResultHolder.fail(stack);
    }
    var site = SanctumSites.get(player.serverLevel()).near(player.blockPosition(), 112);
    if (site == null) {
      player.displayClientMessage(Component.translatable("message.slimic.survey_far"), true);
      return InteractionResultHolder.fail(stack);
    }
    BlockPos target =
        site.at(0, 0, (site.seals & SanctumSites.GALLERY) == 0 ? 0 : site.claimed ? 18 : 32);
    double dx = target.getX() - player.getX(), dz = target.getZ() - player.getZ();
    stack
        .getOrCreateTag()
        .putString("LodestoneDimension", SlimicWorld.DIMENSION.location().toString());
    stack
        .getTag()
        .put(
            "LodestonePos",
            net.minecraft.nbt.NbtUtils.writeBlockPos(
                new BlockPos(target.getX(), player.getBlockY(), target.getZ())));
    stack.getTag().putBoolean("LodestoneTracked", false);
    Direction direction = Direction.fromYRot(Math.toDegrees(Math.atan2(-dx, dz)));
    player.displayClientMessage(
        Component.translatable(
            Math.hypot(dx, dz) < 10
                ? "message.slimic.survey_close"
                : "message.slimic.survey_direction",
            Component.translatable("direction.slimic." + direction.getName())),
        true);
    return InteractionResultHolder.success(stack);
  }
}
