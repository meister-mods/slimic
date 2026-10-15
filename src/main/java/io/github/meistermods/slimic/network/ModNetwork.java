package io.github.meistermods.slimic.network;

import io.github.meistermods.slimic.Slimic;
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
  public static final SimpleChannel CHANNEL =
      NetworkRegistry.newSimpleChannel(
          ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "main"),
          () -> "1",
          "1"::equals,
          "1"::equals);

  private ModNetwork() {}

  public static void register() {
    CHANNEL.registerMessage(
        0,
        RenameSlime.class,
        RenameSlime::encode,
        RenameSlime::decode,
        RenameSlime::handle,
        Optional.of(NetworkDirection.PLAY_TO_SERVER));
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
