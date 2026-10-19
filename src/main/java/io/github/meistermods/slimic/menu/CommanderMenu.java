package io.github.meistermods.slimic.menu;

import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.network.*;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModMenus;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

public final class CommanderMenu extends AbstractContainerMenu {
  public static final int PAGE_SIZE = 6,
      COMPANIONS = 0,
      PLAYERS = 1,
      SET_TAG = 2,
      GATHER = 3,
      SHARE = 4,
      ENEMY = 5;
  private final CommanderSlime commander;
  private final Player viewer;
  private boolean players;
  private int page, refresh;
  private long lastActionTick = Long.MIN_VALUE;
  private CommanderPackets.Page snapshot;

  public CommanderMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
    this(id, inventory, resolve(inventory, buffer));
  }

  private static CommanderSlime resolve(Inventory inventory, FriendlyByteBuf buffer) {
    var entity = inventory.player.level().getEntity(buffer.readVarInt());
    if (entity instanceof CommanderSlime commander) return commander;
    // An entity removal can reach the client before its menu-opening packet.
    var missing =
        io.github.meistermods.slimic.registry.ModEntities.COMMANDER_SLIME
            .get()
            .create(inventory.player.level());
    if (missing == null) throw new IllegalStateException("Commander entity type is unavailable");
    missing.discard();
    return missing;
  }

  public CommanderMenu(int id, Inventory inventory, CommanderSlime slime) {
    super(ModMenus.COMMANDER.get(), id);
    commander = Objects.requireNonNull(slime);
    viewer = inventory.player;
  }

  public CommanderSlime commander() {
    return commander;
  }

  public CommanderPackets.Page snapshot() {
    return snapshot;
  }

  public void accept(CommanderPackets.Page value) {
    if (value.menuId() == containerId) snapshot = value;
  }

  @Override
  public ItemStack quickMoveStack(Player player, int slot) {
    return ItemStack.EMPTY;
  }

  @Override
  public boolean stillValid(Player player) {
    return !player.isSpectator()
        && player.isAlive()
        && commander.isAlive()
        && !commander.isRemoved()
        && player.level() == commander.level()
        && commander.canManage(player)
        && player.distanceToSqr(commander) <= 64;
  }

  private boolean knownPlayer(ServerPlayer player, UUID target, CompanionRegistry registry) {
    return player.server.getPlayerList().getPlayer(target) != null
        || registry.coOwners(commander.getOwnerUUID()).contains(target)
        || registry.enemies(commander.getOwnerUUID()).contains(target);
  }

  public boolean perform(
      ServerPlayer player, int action, int selectedPage, UUID target, String tag) {
    if (!stillValid(player) || player.containerMenu != this || tag.length() > 24) return false;
    long now = player.level().getGameTime();
    if (lastActionTick != Long.MIN_VALUE && now - lastActionTick < 2) return false;
    var registry = CompanionRegistry.get(player.server);
    boolean accepted;
    switch (action) {
      case COMPANIONS, PLAYERS -> {
        if (selectedPage < 0 || selectedPage > 16666) return false;
        players = action == PLAYERS;
        page = selectedPage;
        accepted = true;
      }
      case SET_TAG -> accepted = registry.setTag(commander.getOwnerUUID(), target, tag);
      case GATHER -> accepted = commander.gather(player, tag);
      case SHARE, ENEMY -> {
        if (!player.getUUID().equals(commander.getOwnerUUID())
            || !knownPlayer(player, target, registry)) return false;
        boolean shared = action == SHARE;
        boolean enabled =
            !(shared
                    ? registry.coOwners(commander.getOwnerUUID())
                    : registry.enemies(commander.getOwnerUUID()))
                .contains(target);
        if (shared && enabled && registry.commanderMergeConflict(commander.getOwnerUUID(), target))
          player.displayClientMessage(
              Component.translatable("message.slimic.commander_group_merge"), true);
        accepted = registry.setRelation(commander.getOwnerUUID(), target, shared, enabled);
        if (accepted)
          for (var level : player.server.getAllLevels())
            for (var entity : level.getAllEntities())
              if (entity instanceof FriendlySlime slime
                  && commander.getOwnerUUID().equals(slime.getOwnerUUID()))
                slime.refreshOwnerAccess();
      }
      default -> {
        return false;
      }
    }
    if (accepted) lastActionTick = now;
    send(player);
    return accepted;
  }

  public CommanderPackets.Page createPage(ServerPlayer player) {
    var registry = CompanionRegistry.get(player.server);
    var rows = new ArrayList<CommanderPackets.Row>();
    int pages;
    if (players) {
      var ids = new TreeSet<UUID>();
      player.server.getPlayerList().getPlayers().forEach(p -> ids.add(p.getUUID()));
      ids.addAll(registry.coOwners(commander.getOwnerUUID()));
      ids.addAll(registry.enemies(commander.getOwnerUUID()));
      ids.remove(commander.getOwnerUUID());
      var all = new ArrayList<>(ids);
      pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
      page = Math.min(page, pages - 1);
      for (int i = page * PAGE_SIZE; i < Math.min(all.size(), (page + 1) * PAGE_SIZE); i++) {
        var id = all.get(i);
        var online = player.server.getPlayerList().getPlayer(id);
        var cached = player.server.getProfileCache().get(id);
        String name =
            online != null
                ? online.getGameProfile().getName()
                : cached
                    .map(com.mojang.authlib.GameProfile::getName)
                    .orElse(id.toString().substring(0, 8));
        rows.add(
            new CommanderPackets.Row(
                id,
                Component.literal(name),
                Component.translatable(
                    "screen.slimic.roster." + (online != null ? "online" : "offline")),
                Component.empty(),
                "",
                registry.coOwners(commander.getOwnerUUID()).contains(id),
                registry.enemies(commander.getOwnerUUID()).contains(id),
                online != null));
      }
    } else {
      var all = registry.owned(commander.getOwnerUUID());
      pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
      page = Math.min(page, pages - 1);
      for (int i = page * PAGE_SIZE; i < Math.min(all.size(), (page + 1) * PAGE_SIZE); i++) {
        var e = all.get(i);
        var dimension = ResourceLocation.tryParse(e.dimension());
        var level =
            dimension == null
                ? null
                : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        boolean loaded = level != null && level.getEntity(e.id()) instanceof FriendlySlime;
        var name =
            !e.name().isEmpty()
                ? Component.literal(e.name())
                : Component.translatable("entity." + e.type().replace(':', '.'));
        var location =
            Component.translatable(
                "screen.slimic.roster."
                    + (e.location() == CompanionRegistry.Location.WORLD
                        ? loaded ? "loaded" : "unloaded"
                        : e.location().name().toLowerCase(Locale.ROOT)));
        var order =
            Component.translatable(
                e.order().equals("HEAL")
                        && e.type().equals(ModEntities.ABUNDANCE_SLIME.getId().toString())
                    ? "screen.slimic.abundance"
                    : "roster.slimic.order." + e.order().toLowerCase(Locale.ROOT));
        var status =
            e.reason().equals("NONE")
                ? Component.translatable(
                    "roster.slimic.state." + e.state().toLowerCase(Locale.ROOT))
                : Component.translatable(
                    "roster.slimic.reason."
                        + (e.reason().equals("SEEDS_MISSING")
                                && e.type().equals(ModEntities.LUMBERJACK_SLIME.getId().toString())
                            ? "saplings_missing"
                            : e.reason().toLowerCase(Locale.ROOT)));
        var detail =
            Component.translatable(
                "screen.slimic.roster.detail",
                location,
                order,
                status,
                (int) Math.ceil(e.health()),
                (int) Math.ceil(e.maximumHealth()));
        var pos =
            Component.literal(
                e.dimension()
                    + " "
                    + e.position().getX()
                    + ", "
                    + e.position().getY()
                    + ", "
                    + e.position().getZ());
        rows.add(
            new CommanderPackets.Row(e.id(), name, detail, pos, e.tag(), false, false, loaded));
      }
    }
    return new CommanderPackets.Page(
        containerId,
        players,
        page,
        pages,
        commander.getStamina(),
        commander.getMaxStamina(),
        commander.gathered(),
        commander.requested(),
        player.getUUID().equals(commander.getOwnerUUID()),
        rows);
  }

  private void send(ServerPlayer player) {
    if (stillValid(player) && player.containerMenu == this)
      ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), createPage(player));
  }

  @Override
  public void broadcastChanges() {
    super.broadcastChanges();
    if (viewer instanceof ServerPlayer player && refresh++ % 20 == 0) send(player);
  }
}
