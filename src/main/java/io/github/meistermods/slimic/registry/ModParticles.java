package io.github.meistermods.slimic.registry;

import io.github.meistermods.slimic.Slimic;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.*;

public final class ModParticles {
  public static final DeferredRegister<ParticleType<?>> PARTICLES =
      DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Slimic.MOD_ID);
  public static final RegistryObject<SimpleParticleType> FIREFLY =
      PARTICLES.register("firefly", () -> new SimpleParticleType(false));

  private ModParticles() {}
}
