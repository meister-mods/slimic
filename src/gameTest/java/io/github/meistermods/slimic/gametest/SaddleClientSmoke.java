package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.client.SaddleSlimeControls;
import io.github.meistermods.slimic.client.SlimeScreen;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.SaddleSlime;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

/** Native local-player riding and vehicle packets, rather than direct entity movement calls. */
public final class SaddleClientSmoke {
  private static int phase;
  private static int ticks;
  private static Vec3 start;
  private static boolean jumped;
  private static final AtomicBoolean serverConfirmed = new AtomicBoolean();

  private SaddleClientSmoke() {}

  public static void tick(Minecraft client) {
    if (phase == 0) {
      phase++;
      client
          .getSingleplayerServer()
          .execute(
              () -> {
                var owner =
                    client
                        .getSingleplayerServer()
                        .getPlayerList()
                        .getPlayer(client.player.getUUID());
                var level = owner.serverLevel();
                level
                    .getEntitiesOfClass(FriendlySlime.class, new AABB(0, 90, 0, 20, 320, 20))
                    .forEach(FriendlySlime::discard);
                // Riding crosses the small UI showcase floor; provide a full travel runway.
                for (int x = 3; x <= 18; x++)
                  for (int z = 3; z <= 32; z++) {
                    level.setBlock(
                        new net.minecraft.core.BlockPos(x, 100, z),
                        net.minecraft.world.level.block.Blocks.SEA_LANTERN.defaultBlockState(),
                        2);
                    for (int y = 101; y <= 122; y++)
                      level.setBlock(
                          new net.minecraft.core.BlockPos(x, y, z),
                          net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                          2);
                  }
                owner.getInventory().clearContent();
                owner.inventoryMenu.broadcastChanges();
                client.getSingleplayerServer().setFlightAllowed(false);
                owner.setGameMode(GameType.SURVIVAL);
                owner.teleportTo(level, 7.5, 101, 8.5, -90, 15);
                var mount = ModEntities.SADDLE_SLIME.get().create(level);
                mount.moveTo(10.5, 101, 8.5, 90, 0);
                mount.tame(owner);
                mount.setWaiting(true);
                mount.setEquipment(0, new ItemStack(Items.SADDLE));
                level.addFreshEntity(mount);
              });
      return;
    }
    SaddleSlime mount =
        client.player.getVehicle() instanceof SaddleSlime ridden
            ? ridden
            : client
                .level
                .getEntitiesOfClass(SaddleSlime.class, client.player.getBoundingBox().inflate(32))
                .stream()
                .findFirst()
                .orElse(null);
    if (mount == null) return;
    if (phase == 1) {
      if (!mount.isSaddled() || ++ticks < 15) return;
      screenshot(client, "saddle-ground");
      client.gameMode.interact(client.player, mount, InteractionHand.MAIN_HAND);
      next();
    } else if (phase == 2) {
      if (client.player.getVehicle() != mount) return;
      start = mount.position();
      client.player.setYRot(0);
      client.player.setXRot(0);
      client.options.keyUp.setDown(true);
      next();
    } else if (phase == 3) {
      if (++ticks == 20) {
        normalGroundDistance = Math.sqrt(mount.position().subtract(start).horizontalDistanceSqr());
        check(normalGroundDistance > .3, "Native ground steering must move the mount");
        start = mount.position();
        SaddleSlimeControls.BOOST.setDown(true);
        return;
      }
      if (ticks < 40) return;
      check(
          Math.sqrt(mount.position().subtract(start).horizontalDistanceSqr())
              > normalGroundDistance * 1.2,
          "Ctrl must increase actual ground travel speed");
      SaddleSlimeControls.BOOST.setDown(false);
      int id = mount.getId();
      client
          .getSingleplayerServer()
          .execute(
              () -> {
                var pet = (SaddleSlime) client.getSingleplayerServer().overworld().getEntity(id);
                groundBoostConfirmed.set(pet != null && pet.getStamina() < 95);
              });
      client.options.keyUp.setDown(false);
      start = mount.position();
      client.options.keyJump.setDown(true);
      next();
    } else if (phase == 4) {
      jumped |= mount.getY() > start.y + .2;
      if (++ticks < 12) return;
      check(jumped, "Space must jump while riding without wings");
      check(
          groundBoostConfirmed.get(), "Ground sprint must spend substantial server-owned stamina");
      client.options.keyJump.setDown(false);
      client.options.keyShift.setDown(true);
      next();
    } else if (phase == 5) {
      if (client.player.isPassenger()) return;
      client.options.keyShift.setDown(false);
      int id = mount.getId();
      client
          .getSingleplayerServer()
          .execute(
              () -> {
                var owner =
                    client
                        .getSingleplayerServer()
                        .getPlayerList()
                        .getPlayer(client.player.getUUID());
                var pet = (SaddleSlime) owner.serverLevel().getEntity(id);
                pet.moveTo(10.5, 101, 8.5, 90, 0);
                pet.setDeltaMovement(Vec3.ZERO);
                pet.setWaiting(true);
                owner.teleportTo(owner.serverLevel(), 13.5, 101, 8.5, 90, 15);
                owner.setItemInHand(
                    InteractionHand.MAIN_HAND,
                    new ItemStack(
                        io.github.meistermods.slimic.registry.ModItems.SLIME_TUNER.get()));
                owner.getInventory().setItem(9, new ItemStack(Items.ELYTRA));
                owner.setShiftKeyDown(false);
                pet.mobInteract(owner, InteractionHand.MAIN_HAND);
              });
      next();
    } else if (phase == 6) {
      if (!(client.screen instanceof SlimeScreen screen) || !screen.getMenu().isSynchronized())
        return;
      check(
          screen.getMenu().slots.size() == 38,
          "The saddle menu has two equipment slots and no hidden bag");
      client.gameMode.handleInventoryMouseClick(
          screen.getMenu().containerId, 2, 0, ClickType.QUICK_MOVE, client.player);
      next();
    } else if (phase == 7) {
      if (!(client.screen instanceof SlimeScreen screen)
          || !screen.getMenu().getSlot(1).getItem().is(Items.ELYTRA)
          || ++ticks < 10) return;
      if (ModList.get().isLoaded("jei") && JeiClientSmoke.saddleRecipes() != 1) return;
      screenshot(client, "saddle-menu");
      client.player.closeContainer();
      client
          .getSingleplayerServer()
          .execute(
              () -> {
                var owner =
                    client
                        .getSingleplayerServer()
                        .getPlayerList()
                        .getPlayer(client.player.getUUID());
                owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
              });
      next();
    } else if (phase == 8) {
      if (!mount.hasWings() || !client.player.getMainHandItem().isEmpty() || ++ticks < 15) return;
      screenshot(client, "saddle-wings");
      client.gameMode.interact(client.player, mount, InteractionHand.MAIN_HAND);
      next();
    } else if (phase == 9) {
      if (client.player.getVehicle() != mount) return;
      check(
          !client.player.getAbilities().flying && !client.player.getAbilities().instabuild,
          "Mount flight must work for a Survival player without Creative flight");
      start = mount.position();
      client.player.setYRot(0);
      client.player.setXRot(0);
      client.options.keyJump.setDown(true);
      next();
    } else if (phase == 10) {
      if (++ticks < 12) return;
      check(mount.getY() > start.y + 1 && mount.isNoGravity(), "Space must lift the winged mount");
      client.options.keyJump.setDown(false);
      start = mount.position();
      next();
    } else if (phase == 11) {
      if (++ticks == 4) start = mount.position();
      if (ticks < 100) return;
      check(
          Math.abs(mount.getY() - start.y) < .2,
          "Releasing flight input must hover without drifting");
      client.options.keyUp.setDown(true);
      start = mount.position();
      next();
    } else if (phase == 12) {
      if (++ticks < 10) return;
      check(mount.getZ() > start.z + 1, "W must steer forward while airborne");
      SaddleSlimeControls.BOOST.setDown(true);
      start = mount.position();
      next();
    } else if (phase == 13) {
      if (++ticks < 10) return;
      check(
          mount.getZ() > start.z + 6 && Math.abs(mount.getY() - start.y) < .2,
          "Ctrl must boost forward flight without forcing descent");
      SaddleSlimeControls.BOOST.setDown(false);
      client.player.setXRot(-30);
      client.options.keyUp.setDown(true);
      start = mount.position();
      next();
    } else if (phase == 14) {
      if (++ticks < 10) return;
      check(mount.getY() > start.y + .5, "Forward flight must also follow upward view pitch");
      client.options.keyUp.setDown(false);
      client.player.setXRot(10);
      client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
      int id = mount.getId();
      client
          .getSingleplayerServer()
          .execute(
              () -> {
                var owner =
                    client
                        .getSingleplayerServer()
                        .getPlayerList()
                        .getPlayer(client.player.getUUID());
                var pet = (SaddleSlime) owner.serverLevel().getEntity(id);
                serverConfirmed.set(
                    owner.getVehicle() == pet
                        && pet.isFlyingMount()
                        && pet.isNoGravity()
                        && pet.getY() > 102
                        && !owner.getAbilities().flying
                        && !owner.getAbilities().instabuild);
              });
      next();
    } else if (phase == 15) {
      if (++ticks < 15) return;
      check(
          serverConfirmed.get(),
          "Native vehicle packets must keep the server's Survival rider flying");
      screenshot(client, "saddle-flight");
      int id = mount.getId();
      client
          .getSingleplayerServer()
          .execute(
              () -> {
                var pet = (SaddleSlime) client.getSingleplayerServer().overworld().getEntity(id);
                pet.setTunedVitals(pet.getHealth(), 0, pet.getStatPoints());
                energyDiagnostic.set(
                    "stamina="
                        + pet.getStamina()
                        + " position="
                        + pet.position()
                        + " grounded="
                        + pet.isGroundedMount());
              });
      start = mount.position();
      client.options.keyJump.setDown(true);
      next();
    } else if (phase == 16) {
      if (!glideStarted) {
        if (!mount.isGlidingDown()) {
          if (++ticks > 40)
            check(false, "Exhaustion state did not synchronize: " + energyDiagnostic.get());
          return;
        }
        glideStarted = true;
        start = mount.position();
      }
      if (++glideTicks < 14) return;
      check(
          mount.isGlidingDown() && mount.getY() < start.y - .7 && mount.getY() > start.y - 2.5,
          "Empty stamina must override ascent and produce a slow controlled descent: start="
              + start
              + " now="
              + mount.position()
              + " flag="
              + mount.isGlidingDown()
              + " server="
              + energyDiagnostic.get());
      client.options.keyJump.setDown(false);
      client.options.keyShift.setDown(true);
      next();
    } else if (phase == 17) {
      if (client.player.isPassenger()) return;
      client.options.keyShift.setDown(false);
      check(!mount.isNoGravity(), "Dismount must end transient mount flight");
      client.options.setCameraType(CameraType.FIRST_PERSON);
      System.out.println(
          "SLIMIC SADDLE CLIENT SMOKE PASSED: equipment menu, ground movement/jump, Survival"
              + " ascent, hover, steering, boost, stamina descent, pitch, server vehicle packets"
              + " and Shift dismount; JEI="
              + ModList.get().isLoaded("jei"));
      client.stop();
      phase++;
    }
  }

  private static boolean glideStarted;
  private static int glideTicks;
  private static final java.util.concurrent.atomic.AtomicReference<String> energyDiagnostic =
      new java.util.concurrent.atomic.AtomicReference<>("waiting");
  private static double normalGroundDistance;
  private static final java.util.concurrent.atomic.AtomicBoolean groundBoostConfirmed =
      new java.util.concurrent.atomic.AtomicBoolean();

  private static void next() {
    phase++;
    ticks = 0;
    System.out.println("SLIMIC SADDLE PHASE " + phase);
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message + " (saddle phase " + phase + ")");
  }

  private static void screenshot(Minecraft client, String suffix) {
    var target =
        client
            .gameDirectory
            .toPath()
            .resolve(
                "../verification/0.1.15-"
                    + (ModList.get().isLoaded("jei") ? "jei-" : "no-jei-")
                    + suffix
                    + ".png")
            .normalize();
    try (var bitmap = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
      bitmap.writeToFile(target);
    } catch (java.io.IOException error) {
      throw new IllegalStateException(error);
    }
  }
}
