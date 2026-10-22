package io.github.meistermods.slimic.client;

import io.github.meistermods.slimic.menu.CommandMenu;
import io.github.meistermods.slimic.network.CommandPackets;
import net.minecraft.client.Minecraft;

public final class CommandClient {
  private CommandClient() {}

  public static void receive(CommandPackets.Page packet) {
    var player = Minecraft.getInstance().player;
    if (player != null
        && player.containerMenu instanceof CommandMenu menu
        && menu.containerId == packet.menuId()) menu.accept(packet);
  }
}
