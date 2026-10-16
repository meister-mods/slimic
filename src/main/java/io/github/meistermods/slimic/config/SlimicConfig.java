package io.github.meistermods.slimic.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class SlimicConfig {
  public static final ForgeConfigSpec SPEC;
  public static final ForgeConfigSpec.IntValue HERALD_DELAY_MINUTES;
  public static final ForgeConfigSpec.IntValue TRANSPORT_ACTIVITY;
  public static final ForgeConfigSpec.IntValue FOLLOW_ACTIVITY_INTERVAL;
  public static final ForgeConfigSpec.IntValue MONITOR_ACTIVITY_INTERVAL;

  static {
    ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
    HERALD_DELAY_MINUTES =
        builder
            .comment("Minutes a worker must remain blocked before a Herald Slime sends a signal.")
            .defineInRange("heraldDelayMinutes", 5, 1, 60);
    TRANSPORT_ACTIVITY =
        builder
            .comment(
                "Activity units per cargo stack delivered to a Slime Chest or item supplied to a"
                    + " worker.")
            .defineInRange("transportActivityPerDelivery", 4, 1, 64);
    FOLLOW_ACTIVITY_INTERVAL =
        builder
            .comment(
                "Ticks of movement while following an owner, carrying a rider, or fleeing per"
                    + " activity unit.")
            .defineInRange("followingActivityIntervalTicks", 100, 20, 1200);
    MONITOR_ACTIVITY_INTERVAL =
        builder
            .comment(
                "Ticks monitoring an active or blocked worker with the same owner per activity"
                    + " unit.")
            .defineInRange("monitoringActivityIntervalTicks", 100, 20, 1200);
    SPEC = builder.build();
  }

  private SlimicConfig() {}
}
