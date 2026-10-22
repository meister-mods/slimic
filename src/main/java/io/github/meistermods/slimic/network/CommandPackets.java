package io.github.meistermods.slimic.network;

import io.github.meistermods.slimic.menu.CommandMenu;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;

public final class CommandPackets {
  private CommandPackets() {}

  public record Row(
      UUID id,
      Component name,
      Component detail,
      Component position,
      String tag,
      boolean shared,
      boolean enemy,
      boolean online) {}

  public record Page(
      int menuId,
      boolean players,
      int page,
      int pages,
      float stamina,
      float maximumStamina,
      int gathered,
      int requested,
      boolean primaryOwner,
      List<Row> rows) {
    public Page {
      rows = List.copyOf(rows);
    }

    public void encode(FriendlyByteBuf b) {
      b.writeVarInt(menuId);
      b.writeBoolean(players);
      b.writeVarInt(page);
      b.writeVarInt(pages);
      b.writeFloat(stamina);
      b.writeFloat(maximumStamina);
      b.writeVarInt(gathered);
      b.writeVarInt(requested);
      b.writeBoolean(primaryOwner);
      b.writeByte(rows.size());
      for (var r : rows) {
        b.writeUUID(r.id);
        b.writeComponent(r.name);
        b.writeComponent(r.detail);
        b.writeComponent(r.position);
        b.writeUtf(r.tag, 24);
        b.writeBoolean(r.shared);
        b.writeBoolean(r.enemy);
        b.writeBoolean(r.online);
      }
    }

    public static Page decode(FriendlyByteBuf b) {
      int menu = b.readVarInt();
      boolean players = b.readBoolean();
      int page = b.readVarInt(), pages = b.readVarInt();
      float energy = b.readFloat(), maximum = b.readFloat();
      int gathered = b.readVarInt(), requested = b.readVarInt();
      boolean primary = b.readBoolean();
      int count = b.readUnsignedByte();
      if (count > CommandMenu.PAGE_SIZE)
        throw new IllegalArgumentException("Oversized roster page");
      var rows = new ArrayList<Row>();
      for (int i = 0; i < count; i++)
        rows.add(
            new Row(
                b.readUUID(),
                b.readComponent(),
                b.readComponent(),
                b.readComponent(),
                b.readUtf(24),
                b.readBoolean(),
                b.readBoolean(),
                b.readBoolean()));
      return new Page(
          menu, players, page, pages, energy, maximum, gathered, requested, primary, rows);
    }

    public static void handle(Page packet, Supplier<NetworkEvent.Context> supplier) {
      var context = supplier.get();
      context.enqueueWork(
          () ->
              DistExecutor.unsafeRunWhenOn(
                  Dist.CLIENT,
                  () -> () -> io.github.meistermods.slimic.client.CommandClient.receive(packet)));
      context.setPacketHandled(true);
    }
  }

  public record Action(int menuId, int action, int page, UUID target, String tag) {
    public void encode(FriendlyByteBuf b) {
      b.writeVarInt(menuId);
      b.writeVarInt(action);
      b.writeVarInt(page);
      b.writeUUID(target);
      b.writeUtf(tag, 24);
    }

    public static Action decode(FriendlyByteBuf b) {
      return new Action(
          b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readUUID(), b.readUtf(24));
    }

    public static void handle(Action packet, Supplier<NetworkEvent.Context> supplier) {
      var context = supplier.get();
      context.enqueueWork(
          () -> {
            var sender = context.getSender();
            if (sender != null
                && sender.containerMenu instanceof CommandMenu menu
                && menu.containerId == packet.menuId)
              menu.perform(sender, packet.action, packet.page, packet.target, packet.tag);
          });
      context.setPacketHandled(true);
    }
  }
}
