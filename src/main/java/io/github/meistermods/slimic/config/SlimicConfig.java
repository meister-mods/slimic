package io.github.meistermods.slimic.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class SlimicConfig {
  public static final ForgeConfigSpec SPEC;
  public static final ForgeConfigSpec.IntValue ENDER_ANCHORS;
  public static final ForgeConfigSpec.IntValue SIGNALING_DELAY_MINUTES;
  public static final ForgeConfigSpec.IntValue TRANSPORT_ACTIVITY;
  public static final ForgeConfigSpec.IntValue FOLLOW_ACTIVITY_INTERVAL;
  public static final ForgeConfigSpec.IntValue MONITOR_ACTIVITY_INTERVAL;
  public static final ForgeConfigSpec.IntValue SANCTUM_SEARCH_RADIUS;

  static {
    ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
    SANCTUM_SEARCH_RADIUS =
        builder
            .comment("Maximum kingdom map search radius in chunks; search never acquires chunks.")
            .defineInRange("sanctumMapSearchRadiusChunks", 160, 16, 512);
    SIGNALING_DELAY_MINUTES =
        builder
            .comment(
                "Minutes a worker must remain blocked before a Signaling Slime sends a signal.")
            .defineInRange("heraldDelayMinutes", 5, 1, 60);
    TRANSPORT_ACTIVITY =
        builder
            .comment(
                "Activity units per cargo stack delivered to storage, collected by a warehouse, or"
                    + " supplied to a worker.")
            .defineInRange("transportActivityPerDelivery", 4, 1, 64);
    FOLLOW_ACTIVITY_INTERVAL =
        builder
            .comment(
                "Ticks of movement while following, riding, Attention roaming, or fleeing per"
                    + " activity unit.")
            .defineInRange("followingActivityIntervalTicks", 100, 20, 1200);
    MONITOR_ACTIVITY_INTERVAL =
        builder
            .comment(
                "Ticks monitoring an active or blocked same-owner or mutually shared worker per"
                    + " activity unit.")
            .defineInRange("monitoringActivityIntervalTicks", 100, 20, 1200);
    ENDER_ANCHORS =
        builder
            .comment("Maximum pinned Ender Slimes per primary owner. Each keeps one chunk ticking.")
            .defineInRange("enderAnchorsPerOwner", 8, 1, 128);
    SPEC = builder.build();
  }

  private SlimicConfig() {}
}
