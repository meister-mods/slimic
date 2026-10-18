package io.github.meistermods.slimic.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

public final class FireflyParticle extends TextureSheetParticle {
  private FireflyParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
    super(level, x, y, z, 0, 0, 0);
    pickSprite(sprites);
    xd = yd = zd = 0;
    lifetime = 35;
    gravity = 0;
    hasPhysics = false;
    quadSize = .045F;
    rCol = .8F;
    gCol = 1;
    bCol = .25F;
  }

  @Override
  public ParticleRenderType getRenderType() {
    return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
  }

  @Override
  public int getLightColor(float partial) {
    return 0xf000f0;
  }

  @Override
  public void tick() {
    super.tick();
    alpha = .55F + .4F * (float) Math.sin(age * .45);
  }

  public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
    @Override
    public Particle createParticle(
        SimpleParticleType type,
        ClientLevel level,
        double x,
        double y,
        double z,
        double a,
        double b,
        double c) {
      return new FireflyParticle(level, x, y, z, sprites);
    }
  }
}
