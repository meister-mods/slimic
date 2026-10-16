package io.github.meistermods.slimic.gametest;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.Difficulty;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Loaded only by the development GameTest server. */
@Mod("slimic_tests")
@Mod.EventBusSubscriber(modid = "slimic_tests")
public final class SlimicTestMod {
  @SubscribeEvent
  public static void start(ServerStartedEvent event) {
    if (event.getServer() instanceof GameTestServer)
      event.getServer().setDifficulty(Difficulty.NORMAL, true);
  }
}
