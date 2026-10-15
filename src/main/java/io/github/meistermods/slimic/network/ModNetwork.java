package io.github.meistermods.slimic.network;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.menu.TunerMenu;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
  public static final SimpleChannel CHANNEL =
      NetworkRegistry.newSimpleChannel(
          ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "main"),
          () -> "2",
          "2"::equals,
          "2"::equals);

  private ModNetwork() {}

  public static void register() {
    CHANNEL.registerMessage(
        0,
        RenameSlime.class,
        RenameSlime::encode,
        RenameSlime::decode,
        RenameSlime::handle,
        Optional.of(NetworkDirection.PLAY_TO_SERVER));
    CHANNEL.registerMessage(
        1,
        TuneSlime.class,
        TuneSlime::encode,
        TuneSlime::decode,
        TuneSlime::handle,
        Optional.of(NetworkDirection.PLAY_TO_SERVER));
  }

  public record TuneSlime(int menuId, double[] values, float health, float stamina, int points) {
    private void encode(FriendlyByteBuf buffer) {
      buffer.writeVarInt(menuId);
      for (int i = 0; i < 9; i++) buffer.writeDouble(values[i]);
      buffer.writeFloat(health);
      buffer.writeFloat(stamina);
      buffer.writeVarInt(points);
    }

    private static TuneSlime decode(FriendlyByteBuf buffer) {
      int id = buffer.readVarInt();
      double[] values = new double[9];
      for (int i = 0; i < 9; i++) values[i] = buffer.readDouble();
      return new TuneSlime(id, values, buffer.readFloat(), buffer.readFloat(), buffer.readVarInt());
    }

    private static void handle(TuneSlime packet, Supplier<NetworkEvent.Context> supplier) {
      NetworkEvent.Context context = supplier.get();
      context.enqueueWork(
          () -> {
            var player = context.getSender();
            if (player != null
                && player.containerMenu instanceof TunerMenu menu
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
