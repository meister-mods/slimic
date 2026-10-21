package io.github.meistermods.slimic.gametest;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Loaded only by the development GameTest server. */
@Mod("slimic_tests")
@Mod.EventBusSubscriber(modid = "slimic_tests")
public final class SlimicTestMod {
  @SubscribeEvent
  public static void start(ServerStartedEvent event) {
    if (event.getServer() instanceof GameTestServer server) {
      server.setDifficulty(Difficulty.NORMAL, true);
      // Cases create their own targets and explicitly set any weather or time they require.
      var rules = server.getGameRules();
      rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
      rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
      rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
      for (var level : server.getAllLevels()) {
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
      }
    }
  }
}
