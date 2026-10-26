package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.*;
import io.github.meistermods.slimic.entity.AimlessColony;
import java.util.ArrayList;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Overlapping cuboid lobes share a skin, while their eyes and exposed cores stay opaque. */
public final class AimlessColonyModel extends EntityModel<AimlessColony> {
  public static final ModelLayerLocation BODY =
      new ModelLayerLocation(
          ResourceLocation.fromNamespaceAndPath("slimic", "aimless_colony"), "body");
  private static final String[] LOBES = {
    "middle", "head", "crown", "left", "right", "tail", "front_sprawl", "rear_sprawl"
  };
  private final ModelPart[] shells = new ModelPart[LOBES.length];
  private final ModelPart[] features = new ModelPart[LOBES.length];
  private final ModelPart[] cores = new ModelPart[LOBES.length];
  private final ModelPart[][] eyes = new ModelPart[LOBES.length][];
  private boolean exposed;

  public AimlessColonyModel(ModelPart root) {
    for (int i = 0; i < LOBES.length; i++) {
      shells[i] = root.getChild("shell").getChild(LOBES[i]);
      ModelPart feature = features[i] = root.getChild("features").getChild(LOBES[i]);
      cores[i] = feature.hasChild("core") ? feature.getChild("core") : null;
      var lobeEyes = new ArrayList<ModelPart>();
      for (String name : new String[] {"eye_left", "eye_right", "eye_tail"})
        if (feature.hasChild(name)) lobeEyes.add(feature.getChild(name));
      eyes[i] = lobeEyes.toArray(ModelPart[]::new);
    }
  }

  public static LayerDefinition bodyLayer() {
    MeshDefinition mesh = new MeshDefinition();
    PartDefinition shell =
        mesh.getRoot().addOrReplaceChild("shell", CubeListBuilder.create(), PartPose.ZERO);
    PartDefinition features =
        mesh.getRoot().addOrReplaceChild("features", CubeListBuilder.create(), PartPose.ZERO);
    // Skin UVs remain inside x=0..47, y=0..39 of the shared 64x64 atlas.
    lobe(shell, features, "middle", 0, 0, -6, -10, -6, 12, 10, 12, 0, 0);
    lobe(shell, features, "head", 0, 4, -5, -12, -5, 10, 12, 8, -1, -6);
    lobe(shell, features, "crown", 0, 12, -3, -13, -4, 7, 5, 7, 1, -1);
    lobe(shell, features, "left", 0, 16, -3, -8, -4, 6, 8, 9, -5, 0);
    lobe(shell, features, "right", 12, 6, -2, -9, -4, 5, 9, 9, 5, 1);
    lobe(shell, features, "tail", 0, 8, -4, -7, -3, 8, 7, 8, -1, 6);
    lobe(shell, features, "front_sprawl", 0, 20, -2, -5, -3, 5, 5, 6, 5, -5);
    lobe(shell, features, "rear_sprawl", 16, 20, -2, -4, -3, 5, 4, 7, -5, 6);

    eye(features.getChild("head"), "eye_left", -3.2F, -9.5F, -5.45F, 0, -.10F);
    eye(features.getChild("head"), "eye_right", 2.8F, -6.5F, -5.45F, 0, .16F);
    eye(features.getChild("left"), "eye_left", -3.45F, -5.5F, -1, Mth.HALF_PI, -.13F);
    eye(features.getChild("right"), "eye_right", 3.45F, -6, 1.8F, -Mth.HALF_PI, .08F);
    eye(features.getChild("tail"), "eye_tail", -1.5F, -4.5F, 5.45F, Mth.PI, -.12F);
    core(features.getChild("head"), -1, -10, -4.4F, .08F, .08F);
    core(features.getChild("crown"), 3.4F, -11.2F, 0, -.12F, -.10F);
    core(features.getChild("left"), -2.1F, -4.8F, 3.2F, .13F, .07F);
    core(features.getChild("right"), 1.8F, -6.3F, 1.2F, -.10F, -.08F);
    core(features.getChild("tail"), .5F, -6.1F, 3.5F, .08F, -.10F);
    return LayerDefinition.create(mesh, 64, 64);
  }

  private static void lobe(
      PartDefinition shell,
      PartDefinition features,
      String name,
      int u,
      int v,
      float x,
      float y,
      float z,
      float width,
      float height,
      float depth,
      float pivotX,
      float pivotZ) {
    PartPose pose = PartPose.offset(pivotX, 24, pivotZ);
    shell.addOrReplaceChild(
        name, CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, width, height, depth), pose);
    features.addOrReplaceChild(name, CubeListBuilder.create(), pose);
  }

  private static void eye(
      PartDefinition lobe, String name, float x, float y, float z, float yaw, float roll) {
    // The forward eye face samples x=50..52, y=2..4; no procedural color patches.
    lobe.addOrReplaceChild(
        name,
        CubeListBuilder.create().texOffs(49, 1).addBox(-1.5F, -1.5F, -.5F, 3, 3, 1),
        PartPose.offsetAndRotation(x, y, z, 0, yaw, roll));
  }

  private static void core(PartDefinition lobe, float x, float y, float z, float yaw, float roll) {
    // Five command cores use the six 3x3 faces in x=48..59, y=52..57.
    lobe.addOrReplaceChild(
        "core",
        CubeListBuilder.create().texOffs(48, 52).addBox(-1.5F, -1.5F, -1.5F, 3, 3, 3),
        PartPose.offsetAndRotation(x, y, z, 0, yaw, roll));
  }

  @Override
  public void setupAnim(
      AimlessColony slime, float swing, float amount, float age, float yaw, float pitch) {
    exposed = slime.exposed();
    var pattern = slime.pattern();
    float partial = Mth.clamp(age - slime.tickCount, 0, 1);
    float warning =
        pattern.warning == 0
            ? 0
            : 1 - Mth.clamp((slime.patternTicks() - partial) / pattern.warning, 0, 1);
    float moving = Mth.clamp((float) slime.getDeltaMovement().horizontalDistance() * 5, 0, 1);
    float compression =
        switch (pattern) {
          case DASH -> ease(warning) * .36F;
          case ERUPTION -> warning * .25F;
          case SPLIT -> .10F + Mth.sin(age * .32F) * .06F;
          case PULL -> warning * .15F;
          case RALLY -> .06F + Mth.sin(age * .18F) * .035F;
          default -> 0;
        };
    for (int i = 0; i < shells.length; i++) {
      ModelPart shell = shells[i], feature = features[i];
      shell.resetPose();
      float wave = Mth.sin(age * .075F - i * .72F);
      float drag = Mth.sin(age * .11F - i * .8F);
      shell.xScale = 1 + wave * .035F + compression * .5F;
      shell.yScale = 1 - wave * .04F - compression;
      shell.zScale = 1 + compression * .35F;
      shell.x += Mth.cos(age * .055F + i) * .12F;
      shell.z += drag * (.06F + moving * .3F);
      shell.zRot += wave * (.008F + moving * .016F);
      if (pattern == AimlessColony.Pattern.DASHING) {
        float launch = ease(Mth.clamp(warning * 4, 0, 1));
        shell.xScale *= Mth.lerp(launch, 1.16F, 1.02F);
        shell.yScale *= Mth.lerp(launch, .64F, .76F);
        shell.zScale *= Mth.lerp(launch, .90F, 1.42F);
        shell.xRot += Mth.lerp(launch, -.08F, i < 3 ? .18F : -.08F);
        shell.z +=
            (1 - launch) * (i < 3 ? 1.35F : .2F) + Mth.sin(age * .75F - i * .8F) * launch * .42F;
      } else if (pattern == AimlessColony.Pattern.SLAM) {
        shell.yScale += warning * (i < 3 ? .22F : -.08F);
        shell.xScale += warning * .04F;
      } else if (pattern == AimlessColony.Pattern.SWEEP) {
        float coil = ease(warning);
        sweepPose(shell, i, (float) slime.sweepAngle(0) * coil, .8F * coil);
        shell.yScale *= 1 - coil * .23F;
      } else if (pattern == AimlessColony.Pattern.SWEEPING) {
        float progress = (float) slime.sweepProgress(partial);
        sweepPose(
            shell, i, (float) slime.sweepAngle(progress), .8F + .2F * Mth.sin(progress * Mth.PI));
        shell.yScale *= .77F + .23F * ease(progress);
      } else if (pattern == AimlessColony.Pattern.RECOVERY) {
        float settle = 1 - ease(warning);
        if (slime.recoveryMotion() == AimlessColony.Pattern.SWEEPING) {
          sweepPose(shell, i, (float) slime.sweepAngle(1) * settle, .8F * settle);
          shell.yScale += Mth.sin(warning * Mth.PI) * .12F;
        } else {
          float brake = Mth.sin(Mth.PI * Mth.clamp(warning * 3, 0, 1));
          float rebound = Mth.sin(Mth.PI * Mth.clamp((warning - .25F) / .75F, 0, 1));
          shell.xScale *= 1 + brake * .16F;
          shell.zScale *= 1 + .42F * settle - brake * .25F;
          shell.yScale *= 1 - .24F * settle - brake * .15F + rebound * .15F;
          shell.xRot += (i < 3 ? .18F : -.08F) * settle - rebound * .08F;
          shell.z += Mth.sin(warning * Mth.PI * 2 - i * .35F) * settle * .42F;
        }
      } else if (pattern == AimlessColony.Pattern.RING) {
        shell.xScale += warning * .10F;
        shell.zScale += warning * .10F;
      } else if (pattern == AimlessColony.Pattern.CROSS) {
        shell.yScale += warning * .10F;
        shell.x += Mth.sin(age * .24F + i) * warning * .1F;
      }
      if (pattern == AimlessColony.Pattern.DASH) {
        shell.zScale = 1 - ease(warning) * .10F;
        shell.z += ease(warning) * (i < 3 ? 1.35F : .2F);
        shell.xRot -= ease(warning) * .08F;
      } else if (pattern == AimlessColony.Pattern.PULL) {
        shell.x *= 1 - warning * .12F;
        shell.z *= 1 - warning * .12F;
        shell.yRot += Mth.sin(age * .22F - i) * warning * .06F;
      } else if (pattern == AimlessColony.Pattern.SPLIT) {
        shell.x *= 1 + warning * .14F;
        shell.z *= 1 + warning * .14F;
      } else if (pattern == AimlessColony.Pattern.RALLY) {
        shell.yScale += (1 + Mth.sin(age * .2F - i * .8F)) * .035F;
      } else if (pattern == AimlessColony.Pattern.ERUPTION) {
        shell.xRot += Mth.sin(age * .45F + i) * warning * .045F;
      }
      if (i == 1) shell.yRot += Mth.clamp(yaw * .002F, -.10F, .10F);
      feature.copyFrom(shell);
      if (cores[i] != null) {
        ModelPart core = cores[i];
        core.resetPose();
        float pulse = (exposed ? .08F : .025F) * (1 + Mth.sin(age * .22F + i));
        core.xScale = core.yScale = core.zScale = 1 + pulse;
        core.yRot += Mth.sin(age * .065F + i) * .10F;
      }
      for (ModelPart eye : eyes[i]) {
        eye.resetPose();
        eye.yScale =
            Math.floorMod(slime.tickCount + i * 17, 113) < 3
                ? .18F
                : pattern == AimlessColony.Pattern.DASHING ? .65F : 1;
      }
    }
  }

  private static float ease(float progress) {
    return progress * progress * (3 - 2 * progress);
  }

  /**
   * The featureless front lobe becomes a connected gel arm. Its long axis follows the same angle as
   * the server's moving sweep, with the tip remaining inside the announced eight-block radius.
   */
  private static void sweepPose(ModelPart part, int lobe, float angle, float extension) {
    if (lobe == 6) {
      part.x = Mth.lerp(extension, part.x, 0);
      part.z = Mth.lerp(extension, part.z, -17);
      part.xScale += extension * .35F;
      part.zScale += extension * 4;
    }
    float weight = lobe == 0 ? .30F : lobe == 1 || lobe == 6 ? 1 : .70F;
    // LivingEntityRenderer mirrors model X before turning it into world space.
    float turn = -angle * weight, x = part.x, z = part.z;
    part.x = x * Mth.cos(turn) + z * Mth.sin(turn);
    part.z = z * Mth.cos(turn) - x * Mth.sin(turn);
    part.yRot += turn;
    part.zRot += turn * (lobe == 6 ? .025F : .12F);
    if (lobe == 1 || lobe == 3 || lobe == 4) {
      part.xScale += extension * .14F;
      part.zScale += extension * .18F;
    }
    if (lobe == 5 || lobe == 7) {
      part.x -= Mth.sin(angle) * extension * 1.2F;
      part.z += extension * .6F;
    }
  }

  @Override
  public void renderToBuffer(
      PoseStack pose,
      VertexConsumer sink,
      int light,
      int overlay,
      float r,
      float g,
      float b,
      float alpha) {
    for (ModelPart feature : features)
      feature.render(pose, sink, exposed ? 15728880 : light, overlay, r, g, b, alpha);
  }

  public void renderShell(PoseStack pose, VertexConsumer sink, int light, int overlay) {
    for (ModelPart shell : shells) shell.render(pose, sink, light, overlay, 1, 1, 1, .85F);
  }
}
