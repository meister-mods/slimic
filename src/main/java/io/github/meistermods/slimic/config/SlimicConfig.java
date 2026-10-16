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
            .comment("Default minutes of continuous work blockage before a Herald Slime signals.")
            .defineInRange("heraldDelayMinutes", 5, 1, 60);
    TRANSPORT_ACTIVITY =
        builder
            .comment("Activity units per completed cargo-stack delivery or supplied tool.")
            .defineInRange("transportActivityPerDelivery", 4, 1, 64);
    FOLLOW_ACTIVITY_INTERVAL =
        builder
            .comment("Ticks of actual movement while following the owner per activity unit.")
            .defineInRange("followingActivityIntervalTicks", 100, 20, 1200);
    MONITOR_ACTIVITY_INTERVAL =
        builder
            .comment("Ticks monitoring an active same-owner worker per activity unit.")
            .defineInRange("monitoringActivityIntervalTicks", 100, 20, 1200);
    SPEC = builder.build();
  }

  private SlimicConfig() {}
}
