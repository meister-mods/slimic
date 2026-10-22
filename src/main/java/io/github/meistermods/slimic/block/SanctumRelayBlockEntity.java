package io.github.meistermods.slimic.block;

import io.github.meistermods.slimic.registry.*;
import io.github.meistermods.slimic.world.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A replaceable interface to site-owned progress; its own NBT cannot reset a royal reward. */
public final class SanctumRelayBlockEntity extends BlockEntity {
  private BlockPos anchor;
  private int rotation, zone = 1;

  public SanctumRelayBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlocks.SANCTUM_RELAY_ENTITY.get(), pos, state);
  }

  public void bind(BlockPos anchor, int rotation, int zone) {
    this.anchor = anchor.immutable();
    this.rotation = Math.floorMod(rotation, 4);
    this.zone = Math.max(1, Math.min(4, zone));
    setChanged();
  }

  public SanctumSites.Site site(ServerLevel level) {
    if (anchor == null || !level.dimension().equals(SlimicWorld.DIMENSION)) return null;
    int z = -40 + zone * 16 + 8, y = z < 20 ? 0 : -Math.min(4, (z - 20) / 2);
    if (!worldPosition.equals(FallenSanctumPiece.position(anchor, rotation, 6, y, z))) return null;
    return SanctumSites.get(level).register(level, anchor, rotation);
  }

  @Override
  public void onLoad() {
    super.onLoad();
    if (anchor == null && level instanceof ServerLevel server) {
      var candidate = SanctumSites.get(server).near(worldPosition, 96);
      if (candidate != null)
        for (int zone = 1; zone <= 4; zone++) {
          int z = -40 + zone * 16 + 8, y = z < 20 ? 0 : -Math.min(4, (z - 20) / 2);
          if (worldPosition.equals(candidate.at(6, y, z))) {
            bind(candidate.anchor, candidate.rotation, zone);
            break;
          }
        }
    }
    if (level instanceof ServerLevel server) site(server);
  }

  public static void tick(
      Level level, BlockPos pos, BlockState state, SanctumRelayBlockEntity relay) {
    if (!(level instanceof ServerLevel server)
        || Math.floorMod(server.getGameTime() + pos.asLong(), 20) != 0) return;
    var site = relay.site(server);
    if (site == null) return;
    var player =
        server.getNearestPlayer(
            pos.getX() + .5,
            pos.getY() + .5,
            pos.getZ() + .5,
            28,
            p -> p.isAlive() && !p.isSpectator());
    if (player == null) return;
    SanctumSites.get(server).visit(site, player.getUUID());
    SanctumEncounters.activate(server, site, relay.zone);
    if (relay.zone == 1) SanctumEncounters.activate(server, site, 0);
  }

  public void use(ServerPlayer player) {
    if (!(level instanceof ServerLevel server)) return;
    var site = site(server);
    if (site == null) {
      message(player, "relay_unbound");
      return;
    }
    var data = SanctumSites.get(server);
    data.visit(site, player.getUUID());
    var pending = site.deliveries.get(zone);
    if (pending != null) {
      if (!pending.delivered && pending.owner.equals(player.getUUID()))
        grantMessage(player, data.grantResult(player, site, zone, pending.items));
      else message(player, zone == 4 ? "sanctum_claimed" : "sanctum_recorded");
      return;
    }
    if (zone == 4 && site.seals != (SanctumSites.COURTYARD | SanctumSites.GALLERY)) {
      message(player, "sanctum_seals_required");
      return;
    }
    SanctumEncounters.activate(server, site, zone);
    if (!SanctumEncounters.cleared(server, site, zone)) {
      message(player, "sanctum_defended");
      return;
    }
    if (zone == 3) {
      message(player, "sanctum_laboratory");
      return;
    }
    List<ItemStack> rewards =
        zone == 1
            ? List.of(new ItemStack(ModItems.ROYAL_COURTYARD_SEAL.get()))
            : zone == 2
                ? List.of(
                    new ItemStack(ModItems.ROYAL_GALLERY_SEAL.get()),
                    new ItemStack(ModItems.ROYAL_SURVEY_COMPASS.get()))
                : List.of(new ItemStack(ModItems.ROYAL_SYNTHESIS_CORE.get()));
    grantMessage(player, data.grantResult(player, site, zone, rewards));
  }

  private static void grantMessage(ServerPlayer player, SanctumSites.GrantResult result) {
    message(
        player,
        switch (result) {
          case DELIVERED -> "sanctum_reward";
          case OUTPUT_FULL -> "synthesis_inventory_full";
          case UNAVAILABLE -> "sanctum_recorded";
          case STORAGE_ERROR -> "sanctum_storage_error";
        });
  }

  private static void message(ServerPlayer player, String key) {
    player.displayClientMessage(Component.translatable("message.slimic." + key), true);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    anchor =
        tag.contains("SanctumAnchor", Tag.TAG_COMPOUND)
            ? NbtUtils.readBlockPos(tag.getCompound("SanctumAnchor"))
            : null;
    rotation = Math.floorMod(tag.getInt("RoyalRotation"), 4);
    zone = Math.max(1, Math.min(4, tag.getInt("SanctumZone")));
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    if (anchor != null) tag.put("SanctumAnchor", NbtUtils.writeBlockPos(anchor));
    tag.putInt("RoyalRotation", rotation);
    tag.putInt("SanctumZone", zone);
  }
}
