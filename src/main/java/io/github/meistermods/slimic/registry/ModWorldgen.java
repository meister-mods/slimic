package io.github.meistermods.slimic.registry;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.world.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;

@Mod.EventBusSubscriber(modid = Slimic.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModWorldgen {
  public static final DeferredRegister<Feature<?>> FEATURES =
      DeferredRegister.create(ForgeRegistries.FEATURES, Slimic.MOD_ID);
  public static final RegistryObject<Feature<?>> RUINS =
      FEATURES.register("slimic_ruins", SlimicRuinFeature::new);
  public static final RegistryObject<Feature<?>> GEL_PATCHES =
      FEATURES.register("gel_patches", GelPatchFeature::new);
  public static final RegistryObject<Feature<?>> VEGETATION =
      FEATURES.register("damp_vegetation", DampVegetationFeature::new);

  private ModWorldgen() {}

  @SubscribeEvent
  public static void spawning(SpawnPlacementRegisterEvent event) {
    event.register(
        EntityType.SLIME,
        null,
        null,
        (type, level, reason, pos, random) ->
            level.getLevel().dimension().equals(SlimicWorld.DIMENSION)
                && level.getBiome(pos).is(SlimicWorld.DAMP_PLAINS)
                && level.getDifficulty() != Difficulty.PEACEFUL
                && pos.getY() >= level.getSeaLevel() - 2
                && level
                    .getBlockState(pos.below())
                    .isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
                && level.getFluidState(pos).isEmpty()
                && random.nextInt(8) == 0,
        SpawnPlacementRegisterEvent.Operation.OR);
  }
}
