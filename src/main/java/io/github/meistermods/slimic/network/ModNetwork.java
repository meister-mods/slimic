package io.github.meistermods.slimic.network;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.SaddleSlime;
import io.github.meistermods.slimic.menu.CreativeSlimeTunerMenu;
import io.github.meistermods.slimic.menu.SlimeMenu;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
  private static final String PROTOCOL_VERSION = "22";
  public static final SimpleChannel CHANNEL =
      NetworkRegistry.newSimpleChannel(
          ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "main"),
          () -> PROTOCOL_VERSION,
          PROTOCOL_VERSION::equals,
          PROTOCOL_VERSION::equals);

  private ModNetwork() {}

  public static void register() {
    CHANNEL.registerMessage(
        6,
        TunnelSize.class,
        TunnelSize::encode,
        TunnelSize::decode,
        TunnelSize::handle,
        Optional.of(NetworkDirection.PLAY_TO_SERVER));
    CHANNEL.registerMessage(
        5,
        WorkAreaPacket.class,
        WorkAreaPacket::encode,
        WorkAreaPacket::decode,
        WorkAreaPacket::handle,
        Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    CHANNEL.registerMessage(
        3,
        CommandPackets.Action.class,
        CommandPackets.Action::encode,
        CommandPackets.Action::decode,
        CommandPackets.Action::handle,
        Optional.of(NetworkDirection.PLAY_TO_SERVER));
    CHANNEL.registerMessage(
        4,
        CommandPackets.Page.class,
        CommandPackets.Page::encode,
        CommandPackets.Page::decode,
        CommandPackets.Page::handle,
        Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    CHANNEL.registerMessage(
        2,
        RideSlime.class,
        RideSlime::encode,
        RideSlime::decode,
        RideSlime::handle,
        Optional.of(NetworkDirection.PLAY_TO_SERVER));
    CHANNEL.registerMessage(
        0,
        RenameSlime.class,
        RenameSlime::encode,
        RenameSlime::decode,
        RenameSlime::handle,
        Optional.of(NetworkDirection.PLAY_TO_SERVER));
    CHANNEL.registerMessage(
        1,
        CreativeTuneSlime.class,
        CreativeTuneSlime::encode,
        CreativeTuneSlime::decode,
        CreativeTuneSlime::handle,
        Optional.of(NetworkDirection.PLAY_TO_SERVER));
  }

  public record TunnelSize(int menuId, int size) {
    private void encode(FriendlyByteBuf buffer) {
      buffer.writeVarInt(menuId);
      buffer.writeVarInt(size);
    }

    private static TunnelSize decode(FriendlyByteBuf buffer) {
      return new TunnelSize(buffer.readVarInt(), buffer.readVarInt());
    }

    private static void handle(TunnelSize packet, Supplier<NetworkEvent.Context> supplier) {
      var context = supplier.get();
      context.enqueueWork(
          () -> {
            var player = context.getSender();
            if (player != null
                && player.containerMenu instanceof SlimeMenu menu
                && menu.containerId == packet.menuId) menu.setTunnelSize(player, packet.size);
          });
      context.setPacketHandled(true);
    }
  }

  public record RideSlime(int entityId, boolean up, boolean boost) {
    private void encode(FriendlyByteBuf buffer) {
      buffer.writeVarInt(entityId);
      buffer.writeBoolean(up);
      buffer.writeBoolean(boost);
    }

    private static RideSlime decode(FriendlyByteBuf buffer) {
      return new RideSlime(buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean());
    }

    private static void handle(RideSlime packet, Supplier<NetworkEvent.Context> supplier) {
      var context = supplier.get();
      context.enqueueWork(
          () -> {
            var player = context.getSender();
            if (player != null
                && player.getVehicle() instanceof SaddleSlime slime
                && slime.getId() == packet.entityId)
              slime.setRiderInput(player, packet.up, packet.boost);
          });
      context.setPacketHandled(true);
    }
  }

  public record CreativeTuneSlime(
      int menuId, double[] values, float health, float stamina, long points) {
    private void encode(FriendlyByteBuf buffer) {
      buffer.writeVarInt(menuId);
      for (int i = 0; i < 9; i++) buffer.writeDouble(values[i]);
      buffer.writeFloat(health);
      buffer.writeFloat(stamina);
      buffer.writeVarLong(points);
    }

    private static CreativeTuneSlime decode(FriendlyByteBuf buffer) {
      int id = buffer.readVarInt();
      double[] values = new double[9];
      for (int i = 0; i < 9; i++) values[i] = buffer.readDouble();
      return new CreativeTuneSlime(
          id, values, buffer.readFloat(), buffer.readFloat(), buffer.readVarLong());
    }

    private static void handle(CreativeTuneSlime packet, Supplier<NetworkEvent.Context> supplier) {
      NetworkEvent.Context context = supplier.get();
      context.enqueueWork(
          () -> {
            var player = context.getSender();
            if (player != null
                && player.containerMenu instanceof CreativeSlimeTunerMenu menu
                && menu.containerId == packet.menuId)
              menu.apply(player, packet.values, packet.health, packet.stamina, packet.points);
          });
      context.setPacketHandled(true);
    }
  }

  public record RenameSlime(int menuId, String name) {
    private void encode(FriendlyByteBuf buffer) {
      buffer.writeVarInt(menuId);
      buffer.writeUtf(name, 128);
    }

    private static RenameSlime decode(FriendlyByteBuf buffer) {
      return new RenameSlime(buffer.readVarInt(), buffer.readUtf(128));
    }

    private static void handle(RenameSlime packet, Supplier<NetworkEvent.Context> supplier) {
      NetworkEvent.Context context = supplier.get();
      context.enqueueWork(
          () -> {
            var player = context.getSender();
            if (player != null
                && player.containerMenu instanceof SlimeMenu menu
                && menu.containerId == packet.menuId) menu.rename(player, packet.name);
          });
      context.setPacketHandled(true);
    }
  }
}
