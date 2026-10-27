package io.github.meistermods.slimic.item;

import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class SlimeTeleporterItem extends Item {
  public SlimeTeleporterItem(Properties properties) {
    super(properties);
  }

  public static ItemStack bound(EnderSlime slime) {
    var item = new ItemStack(ModItems.SLIME_TELEPORTER.get());
    item.getOrCreateTag().putUUID("Companion", slime.getUUID());
    item.getOrCreateTag().putUUID("Owner", slime.getOwnerUUID());
    return item;
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    var item = player.getItemInHand(hand);
    if (level.isClientSide) return InteractionResultHolder.success(item);
    if (!(player instanceof ServerPlayer server) || !teleport(server, item)) {
      player.displayClientMessage(
          Component.translatable("message.slimic.teleporter_unavailable"), true);
      return InteractionResultHolder.fail(item);
    }
    player.getCooldowns().addCooldown(this, 20);
    return InteractionResultHolder.consume(item);
  }

  public static boolean teleport(ServerPlayer player, ItemStack item) {
    if (!item.is(ModItems.SLIME_TELEPORTER.get())
        || !item.hasTag()
        || player.isSpectator()
        || !player.isAlive()
        || !item.getTag().hasUUID("Companion")
        || !item.getTag().hasUUID("Owner")) return false;
    var registry = CompanionRegistry.get(player.server);
    var record = registry.find(item.getTag().getUUID("Companion"));
    if (record == null
        || record.location() != CompanionRegistry.Location.WORLD
        || !record.pinned()
        || !record.owner().equals(item.getTag().getUUID("Owner"))
        || !registry.owns(record.owner(), player.getUUID())) return false;
    var dimension = ResourceLocation.tryParse(record.dimension());
    if (dimension == null) return false;
    var target = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
    boolean dimensional = target != player.serverLevel();
    if (target == null
        || !(target.getEntity(record.id()) instanceof EnderSlime slime)
        || !slime.isAlive()
        || !slime.isPinned()
        || !slime.canManage(player)
        || dimensional && !slime.allowsDimensionalTravel()
        || slime.getStamina() < slime.getMaxStamina()) return false;
    // Capture the decision before arrival: the arriving player must not waive their own cost.
    var anchor = slime.position();
    boolean[] consumePearl = {false};
    java.util.function.BooleanSupplier ready =
        () -> {
          if (!slime.isAlive()
              || slime.isRemoved()
              || !slime.isPinned()
              || slime.level() != target
              || slime.position().distanceToSqr(anchor) > .0001
              || !slime.canManage(player)
              || slime.getStamina() < slime.getMaxStamina()
              || !record.owner().equals(slime.getOwnerUUID())
              || dimensional && !slime.allowsDimensionalTravel()) return false;
          consumePearl[0] =
              !EnderChunkLoading.independentlyLoaded(
                  target, slime.chunkPosition(), slime.getUUID());
          return !consumePearl[0] || slime.isChunkLoading();
        };
    var landing = CompanionTeleport.landing(target, player, slime.blockPosition(), 0, 4);
    if (landing == null || !CompanionTeleport.player(player, target, landing, ready)) return false;
    slime.completeTeleport(consumePearl[0]);
    registry.observe(slime);
    return true;
  }

  @Override
  public void appendHoverText(
      ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
    lines.add(
        Component.translatable(
            stack.hasTag() && stack.getTag().hasUUID("Companion")
                ? "item.slimic.slime_teleporter.bound"
                : "item.slimic.slime_teleporter.unbound"));
  }
}
