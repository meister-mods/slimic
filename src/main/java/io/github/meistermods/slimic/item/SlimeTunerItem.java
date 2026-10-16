package io.github.meistermods.slimic.item;

import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.menu.TunerMenu;
import java.util.Comparator;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

public final class SlimeTunerItem extends Item {
  public SlimeTunerItem(Properties properties) {
    super(properties);
  }

  public static InteractionResult open(Player player, FriendlySlime target) {
    if (!player.getAbilities().instabuild) return InteractionResult.FAIL;
    if (player instanceof ServerPlayer serverPlayer) {
      NetworkHooks.openScreen(
          serverPlayer,
          new SimpleMenuProvider(
              (id, inventory, viewer) -> new TunerMenu(id, inventory, target),
              Component.translatable("screen.slimic.tuner")),
          buffer -> buffer.writeVarInt(target == null ? -1 : target.getId()));
    }
    return InteractionResult.sidedSuccess(player.level().isClientSide);
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    var start = player.getEyePosition();
    var end = start.add(player.getLookAngle().scale(16));
    var hit =
        ProjectileUtil.getEntityHitResult(
            player,
            start,
            end,
            player.getBoundingBox().expandTowards(player.getLookAngle().scale(16)).inflate(1),
            entity -> entity instanceof FriendlySlime && entity.isAlive(),
            256);
    FriendlySlime target =
        hit != null
            ? (FriendlySlime) hit.getEntity()
            : level
                .getEntitiesOfClass(
                    FriendlySlime.class,
                    player.getBoundingBox().inflate(16),
                    FriendlySlime::isAlive)
                .stream()
                .filter(slime -> player.distanceToSqr(slime) <= 256)
                .min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);
    return new InteractionResultHolder<>(open(player, target), player.getItemInHand(hand));
  }

  @Override
  public InteractionResult useOn(UseOnContext context) {
    return context.getPlayer() == null
        ? InteractionResult.FAIL
        : use(context.getLevel(), context.getPlayer(), context.getHand()).getResult();
  }
}
