package io.github.meistermods.slimic.item;

import com.mojang.logging.LogUtils;
import io.github.meistermods.slimic.config.SlimicConfig;
import io.github.meistermods.slimic.world.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.*;

/** Uses native map rendering and player markers, without terrain scans or structure chunk loads. */
public final class KingdomMapItem extends MapItem {
  public static final String BINDING = "SlimicKingdomMap";
  private static final Map<UUID, UUID> SEARCHING = new java.util.concurrent.ConcurrentHashMap<>();

  public static void clearSearches() {
    SEARCHING.clear();
  }

  public KingdomMapItem(Properties properties) {
    super(properties);
  }

  public static boolean bound(ItemStack stack) {
    return stack.hasTag() && stack.getTag().getCompound(BINDING).hasUUID("Site");
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level world, Player actor, InteractionHand hand) {
    ItemStack stack = actor.getItemInHand(hand);
    if (!(actor instanceof ServerPlayer player)) return InteractionResultHolder.success(stack);
    if (!world.dimension().equals(SlimicWorld.DIMENSION)) {
      player.displayClientMessage(Component.translatable("message.slimic.map_dimension"), true);
      return InteractionResultHolder.fail(stack);
    }
    if (bound(stack)) {
      var data = stack.getTag().getCompound(BINDING);
      var anchor = NbtUtils.readBlockPos(data.getCompound("Anchor"));
      double distance = Math.hypot(anchor.getX() - player.getX(), anchor.getZ() - player.getZ());
      player.displayClientMessage(
          Component.translatable(
              distance < 64 ? "message.slimic.map_arrived" : "message.slimic.map_bound",
              (int) (Math.round(distance / 32) * 32)),
          true);
      return InteractionResultHolder.success(stack);
    }
    UUID nonce = UUID.randomUUID();
    if (SEARCHING.putIfAbsent(player.getUUID(), nonce) != null) {
      player.displayClientMessage(Component.translatable("message.slimic.map_searching"), true);
      return InteractionResultHolder.success(stack);
    }
    stack.getOrCreateTag().putUUID("SlimicMapSearch", nonce);
    var level = player.serverLevel();
    var origin = player.blockPosition();
    int radius = SlimicConfig.SANCTUM_SEARCH_RADIUS.get();
    player.displayClientMessage(Component.translatable("message.slimic.map_searching"), true);
    CompletableFuture.supplyAsync(
            () -> SanctumPlacement.find(level, origin, radius),
            net.minecraft.Util.backgroundExecutor())
        .whenComplete(
            (candidate, error) -> {
              if (level.getServer().isStopped()) {
                SEARCHING.remove(player.getUUID(), nonce);
                return;
              }
              level
                  .getServer()
                  .execute(
                      () -> {
                        SEARCHING.remove(player.getUUID(), nonce);
                        if (player.hasDisconnected() || player.isRemoved() || !player.isAlive())
                          return;
                        ItemStack current = ItemStack.EMPTY;
                        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                          var item = player.getInventory().getItem(i);
                          if (item.getItem() == this
                              && item.hasTag()
                              && item.getTag().hasUUID("SlimicMapSearch")
                              && nonce.equals(item.getTag().getUUID("SlimicMapSearch"))) {
                            current = item;
                            break;
                          }
                        }
                        if (current.isEmpty() || bound(current)) return;
                        current.getTag().remove("SlimicMapSearch");
                        if (error != null)
                          LogUtils.getLogger().warn("Kingdom map search failed", error);
                        if (player.level() != level) {
                          player.displayClientMessage(
                              Component.translatable("message.slimic.map_dimension"), true);
                          return;
                        }
                        if (candidate == null || error != null) {
                          player.displayClientMessage(
                              Component.translatable("message.slimic.map_not_found"), true);
                          return;
                        }
                        bind(current, level, candidate);
                        player.inventoryMenu.broadcastChanges();
                        player.displayClientMessage(
                            Component.translatable("message.slimic.map_found"), true);
                      });
            });
    return InteractionResultHolder.consume(stack);
  }

  public static void bind(
      ItemStack stack,
      net.minecraft.server.level.ServerLevel level,
      SanctumPlacement.Candidate candidate) {
    var anchor = candidate.anchor();
    var data = new CompoundTag();
    data.putInt("Schema", 1);
    data.putString("Dimension", SlimicWorld.DIMENSION.location().toString());
    data.putUUID("Site", SanctumSites.identity(level.getSeed(), anchor));
    data.put("Anchor", NbtUtils.writeBlockPos(anchor));
    data.putInt("Rotation", candidate.rotation());
    var vanilla = MapItem.create(level, anchor.getX(), anchor.getZ(), (byte) 3, true, true);
    stack.getOrCreateTag().putInt("map", MapItem.getMapId(vanilla));
    stack.getTag().put(BINDING, data);
    MapItemSavedData.addTargetDecoration(
        stack,
        new BlockPos(
            Math.floorDiv(anchor.getX(), 32) * 32 + 16,
            anchor.getY(),
            Math.floorDiv(anchor.getZ(), 32) * 32 + 16),
        "fallen_sanctum",
        MapDecoration.Type.MANSION);
  }

  @Override
  public void inventoryTick(
      ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
    if (!level.isClientSide && bound(stack) && entity instanceof Player player) {
      var data = MapItem.getSavedData(stack, level);
      if (data != null) data.tickCarriedBy(player, stack);
    }
  }

  @Override
  public void appendHoverText(
      ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
    lines.add(
        Component.translatable(
            bound(stack)
                ? "item.slimic.ancient_kingdom_map.bound"
                : "item.slimic.ancient_kingdom_map.unbound"));
    lines.add(Component.translatable("message.slimic.map_dimension"));
  }
}
