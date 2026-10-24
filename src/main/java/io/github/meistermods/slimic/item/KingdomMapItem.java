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
  private final boolean assembly;

  public static void clearSearches() {
    SEARCHING.clear();
  }

  public KingdomMapItem(Properties properties) {
    this(properties, false);
  }

  public KingdomMapItem(Properties properties, boolean assembly) {
    super(properties);
    this.assembly = assembly;
  }

  private String bindingKey() {
    return assembly ? "SlimicAssemblyMap" : BINDING;
  }

  private String messageKey(String suffix) {
    return "message.slimic." + (assembly ? "assembly_map_" : "map_") + suffix;
  }

  private boolean isBound(ItemStack stack) {
    return stack.hasTag() && stack.getTag().getCompound(bindingKey()).hasUUID("Site");
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
    if (isBound(stack)) {
      var data = stack.getTag().getCompound(bindingKey());
      var anchor = NbtUtils.readBlockPos(data.getCompound("Anchor"));
      double distance = Math.hypot(anchor.getX() - player.getX(), anchor.getZ() - player.getZ());
      player.displayClientMessage(
          Component.translatable(
              messageKey(distance < 64 ? "arrived" : "bound"),
              (int) (Math.round(distance / 32) * 32)),
          true);
      return InteractionResultHolder.success(stack);
    }
    UUID nonce = UUID.randomUUID();
    if (SEARCHING.putIfAbsent(player.getUUID(), nonce) != null) {
      player.displayClientMessage(Component.translatable(messageKey("searching")), true);
      return InteractionResultHolder.success(stack);
    }
    stack.getOrCreateTag().putUUID("SlimicMapSearch", nonce);
    var level = player.serverLevel();
    var origin = player.blockPosition();
    int radius = SlimicConfig.SANCTUM_SEARCH_RADIUS.get();
    player.displayClientMessage(Component.translatable(messageKey("searching")), true);
    CompletableFuture.supplyAsync(
            () -> {
              if (!assembly) return SanctumPlacement.find(level, origin, radius);
              var candidate = AssemblyPlacement.find(level, origin, radius);
              return candidate == null
                  ? null
                  : new SanctumPlacement.Candidate(candidate.anchor(), 0);
            },
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
                        if (current.isEmpty() || isBound(current)) return;
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
                              Component.translatable(messageKey("not_found")), true);
                          return;
                        }
                        if (assembly) bindAssembly(current, level, candidate.anchor());
                        else bind(current, level, candidate);
                        player.inventoryMenu.broadcastChanges();
                        player.displayClientMessage(
                            Component.translatable(messageKey("found")), true);
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

  public static void bindAssembly(
      ItemStack stack, net.minecraft.server.level.ServerLevel level, BlockPos anchor) {
    var data = new CompoundTag();
    data.putInt("Schema", 1);
    data.putString("Destination", "abandoned_assembly_chamber");
    data.putString("Dimension", SlimicWorld.DIMENSION.location().toString());
    data.putUUID("Site", AssemblySites.identity(level.getSeed(), anchor));
    data.put("Anchor", NbtUtils.writeBlockPos(anchor));
    var vanilla = MapItem.create(level, anchor.getX(), anchor.getZ() - 62, (byte) 3, true, true);
    stack.getOrCreateTag().putInt("map", MapItem.getMapId(vanilla));
    stack.getTag().put("SlimicAssemblyMap", data);
    MapItemSavedData.addTargetDecoration(
        stack, anchor.offset(0, 0, -62), "assembly_chamber", MapDecoration.Type.MONUMENT);
  }

  @Override
  public void inventoryTick(
      ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
    if (!level.isClientSide && isBound(stack) && entity instanceof Player player) {
      var data = MapItem.getSavedData(stack, level);
      if (data != null) data.tickCarriedBy(player, stack);
    }
  }

  @Override
  public void appendHoverText(
      ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
    lines.add(
        Component.translatable(
            assembly
                ? isBound(stack)
                    ? "item.slimic.assembly_clue.bound"
                    : "item.slimic.assembly_clue.unbound"
                : isBound(stack)
                    ? "item.slimic.ancient_kingdom_map.bound"
                    : "item.slimic.ancient_kingdom_map.unbound"));
    lines.add(Component.translatable("message.slimic.map_dimension"));
  }
}
