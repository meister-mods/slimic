package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.SaddleSlime;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RidingPacketBoundaryGameTests {
  @GameTest(template = "empty", batch = "riding_packets")
  public static void ordinaryBoostSurvivesDedicatedVehicleValidation(GameTestHelper h) {
    legalFlight(h, .3, true);
  }

  @GameTest(template = "empty", batch = "riding_packets")
  public static void tunedNormalFlightSurvivesDedicatedVehicleValidation(GameTestHelper h) {
    legalFlight(h, 10, false);
  }

  @GameTest(template = "empty", batch = "riding_packets")
  public static void tunedBoostFlightSurvivesDedicatedVehicleValidation(GameTestHelper h) {
    legalFlight(h, 10, true);
  }

  @GameTest(template = "empty", batch = "riding_packets")
  public static void moderateBoostFlightSurvivesDedicatedVehicleValidation(GameTestHelper h) {
    legalFlight(h, 4, true);
  }

  private static void legalFlight(GameTestHelper h, double speed, boolean boost) {
    try (var ride = new Ride(h, speed, boost)) {
      var start = ride.slime.position();
      var target = start.add(0, 0, ride.speed());
      ride.packet(target);
      h.assertTrue(
          ride.slime.position().distanceToSqr(target) < .000001 && !ride.corrected(),
          "Legal flight survives native dedicated validation: speed="
              + speed
              + ", boost="
              + boost
              + ", expected="
              + target
              + ", actual="
              + ride.slime.position());
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "riding_packets")
  public static void impossibleFlightStillReceivesNativeCorrection(GameTestHelper h) {
    try (var ride = new Ride(h, 10, true)) {
      var start = ride.slime.position();
      ride.packet(start.add(0, 0, 90));
      h.assertTrue(
          ride.corrected() && ride.slime.position().equals(start),
          "Prediction preserves native rejection of impossible vehicle motion");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "riding_packets")
  public static void exhaustionPredictsOnlyTheControlledDescent(GameTestHelper h) {
    try (var ride = new Ride(h, 10, true)) {
      ride.slime.setTunedVitals(ride.slime.getHealth(), 0, 0);
      ride.slime.tick();
      var target = ride.slime.position().add(0, -.12, .15);
      ride.packet(target);
      h.assertTrue(
          ride.slime.isGlidingDown()
              && !ride.slime.isBoosting()
              && ride.slime.position().distanceToSqr(target) < .000001
              && !ride.corrected(),
          "Exhaustion retains native accepted slow descent without boost");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "riding_packets")
  public static void predictionDoesNotMoveTheVehicleTwiceOrSurviveStorage(GameTestHelper h) {
    try (var ride = new Ride(h, 10, true)) {
      var start = ride.slime.position();
      for (int i = 0; i < 10; i++) ride.slime.tick();
      h.assertTrue(
          ride.slime.position().equals(start)
              && Math.abs(ride.slime.getDeltaMovement().length() - ride.speed()) < .000001,
          "Server predicts velocity without applying a second movement step");
      var worldData = ride.slime.saveWithoutId(new CompoundTag());
      var stored = FriendlySlimeItem.fromSlime(ride.slime);
      for (var data : List.of(worldData, stored.getTag().getCompound("SlimeData"))) {
        var motion = data.getList("Motion", Tag.TAG_DOUBLE);
        h.assertTrue(
            motion.size() == 3
                && motion.getDouble(0) == 0
                && motion.getDouble(1) == 0
                && motion.getDouble(2) == 0,
            "Transient server prediction never becomes saved momentum");
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "riding_packets")
  public static void dismountClearsPredictionBeforeNormalPhysics(GameTestHelper h) {
    try (var ride = new Ride(h, 10, true)) {
      var start = ride.slime.position();
      ride.owner.stopRiding();
      h.assertTrue(
          ride.slime.getDeltaMovement().equals(Vec3.ZERO),
          "Dismount clears prediction immediately");
      ride.slime.setWaiting(true);
      ride.slime.tick();
      h.assertTrue(
          ride.slime.position().subtract(start).horizontalDistanceSqr() < .000001
              && !ride.slime.isNoGravity(),
          "Normal gravity resumes without a horizontal launch");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "riding_packets")
  public static void removingWingsClearsPredictionImmediately(GameTestHelper h) {
    try (var ride = new Ride(h, 10, true)) {
      ride.slime.setEquipment(1, ItemStack.EMPTY);
      h.assertTrue(
          ride.owner.getVehicle() == ride.slime
              && !ride.slime.isNoGravity()
              && ride.slime.getDeltaMovement().equals(Vec3.ZERO),
          "Wing removal retains riding and immediately clears predicted flight momentum");
    }
    h.succeed();
  }

  private static void field(Object target, String name, Object value) {
    try {
      var field = ServerGamePacketListenerImpl.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static final class Ride implements AutoCloseable {
    final LoadedWorkArea area;
    final GameTestHelper helper;
    final ServerPlayer owner;
    final SaddleSlime slime;
    final ServerGamePacketListenerImpl listener;
    final List<Packet<?>> sent = new ArrayList<>();

    Ride(GameTestHelper h, double speed, boolean boost) {
      helper = h;
      area = new LoadedWorkArea(h);
      owner =
          new ServerPlayer(
              h.getLevel().getServer(),
              h.getLevel(),
              new GameProfile(UUID.randomUUID(), "PacketRider"));
      var connection =
          new Connection(PacketFlow.SERVERBOUND) {
            @Override
            public void send(Packet<?> packet) {
              sent.add(packet);
            }
          };
      listener = new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, owner);
      owner.connection = listener;
      owner.setGameMode(GameType.SURVIVAL);
      owner.moveTo(area.point(8.5, 310, 8.5));
      owner.setYRot(0);
      owner.setXRot(0);
      owner.zza = 1;
      h.getLevel().addNewPlayer(owner);
      slime = ModEntities.SADDLE_SLIME.get().create(h.getLevel());
      slime.tame(owner);
      slime.moveTo(owner.position());
      slime.setEquipment(0, new ItemStack(Items.SADDLE));
      slime.setEquipment(1, new ItemStack(Items.ELYTRA));
      SlimeStat.MOVEMENT_SPEED.tune(slime, speed);
      h.getLevel().addFreshEntity(slime);
      h.assertTrue(slime.mount(owner), "Actual Survival player can mount");
      h.assertTrue(slime.setRiderInput(owner, false, boost), "Current rider input is accepted");
      slime.tick();
      for (int dz = 0; dz <= 3; dz++)
        h.getLevel()
            .getChunk(slime.blockPosition().getX() >> 4, (slime.blockPosition().getZ() >> 4) + dz);
    }

    float speed() {
      try {
        var method = SaddleSlime.class.getDeclaredMethod("getRiddenSpeed", Player.class);
        method.setAccessible(true);
        return (Float) method.invoke(slime, owner);
      } catch (ReflectiveOperationException failure) {
        throw new IllegalStateException(failure);
      }
    }

    void packet(Vec3 target) {
      field(listener, "lastVehicle", slime);
      for (String axis : new String[] {"X", "Y", "Z"}) {
        double value =
            axis.equals("X") ? slime.getX() : axis.equals("Y") ? slime.getY() : slime.getZ();
        field(listener, "vehicleFirstGood" + axis, value);
        field(listener, "vehicleLastGood" + axis, value);
      }
      var packetEntity = ModEntities.SADDLE_SLIME.get().create(helper.getLevel());
      packetEntity.moveTo(target);
      sent.clear();
      listener.handleMoveVehicle(new ServerboundMoveVehiclePacket(packetEntity));
      packetEntity.discard();
    }

    boolean corrected() {
      return sent.stream().anyMatch(p -> p instanceof ClientboundMoveVehiclePacket);
    }

    @Override
    public void close() {
      owner.stopRiding();
      owner.discard();
      slime.discard();
      area.close();
    }
  }
}
