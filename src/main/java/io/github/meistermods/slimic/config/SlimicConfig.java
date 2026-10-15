package io.github.meistermods.slimic.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class SlimicConfig {
  public static final ForgeConfigSpec SPEC;
  public static final ForgeConfigSpec.IntValue HERALD_DELAY_MINUTES;

  static {
    ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
    HERALD_DELAY_MINUTES =
        builder
            .comment("Default minutes of continuous work blockage before a Herald Slime signals.")
            .defineInRange("heraldDelayMinutes", 5, 1, 60);
    SPEC = builder.build();
  }

  private SlimicConfig() {}
}
