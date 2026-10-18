package io.github.meistermods.slimic.client;

import io.github.meistermods.slimic.menu.CommanderMenu;
import io.github.meistermods.slimic.network.CommanderPackets;
import net.minecraft.client.Minecraft;

public final class CommanderClient {
  private CommanderClient() {}

  public static void receive(CommanderPackets.Page packet) {
    var player = Minecraft.getInstance().player;
    if (player != null
        && player.containerMenu instanceof CommanderMenu menu
        && menu.containerId == packet.menuId()) menu.accept(packet);
  }
}
