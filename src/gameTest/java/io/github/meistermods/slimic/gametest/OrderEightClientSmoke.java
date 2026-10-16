package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.block.SlimeChestBlockEntity;
import io.github.meistermods.slimic.client.SlimeScreen;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraftforge.fml.ModList;

final class OrderEightClientSmoke {
  private static int phase, ticks, beamFrames;

  static boolean tick(Minecraft c) {
    if (phase == 8) return true;
    if (phase == 0) {
      phase = 1;
      c.getSingleplayerServer()
          .execute(
              () -> {
                var owner = c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
                var level = owner.serverLevel();
                level
                    .getEntitiesOfClass(FriendlySlime.class, owner.getBoundingBox().inflate(32))
                    .forEach(FriendlySlime::discard);
                var guard = ModEntities.GUARDIAN_ANGEL_SLIME.get().create(level);
                guard.setWaiting(false);
                guard.tame(owner);
                guard.moveTo(10.5, 103, 8.5);
                guard.setNoAi(false);
                guard.setTotems(new ItemStack(Items.TOTEM_OF_UNDYING, 3));
                level.addFreshEntity(guard);
                var attacker = net.minecraft.world.entity.EntityType.HUSK.create(level);
                attacker.setNoAi(true);
                attacker.moveTo(12.5, 101, 8.5);
                level.addFreshEntity(attacker);
                guard.hurt(level.damageSources().mobAttack(attacker), 2);
                owner.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
                owner.teleportTo(level, 7.5, 101, 8.5, -90, -20);
                level.setBlockAndUpdate(
                    new BlockPos(10, 101, 11), ModBlocks.SLIME_CHEST.get().defaultBlockState());
              });
      return false;
    }
    if (phase == 1) {
      c.options.fov().set(90);
      var guards =
          c.level.getEntitiesOfClass(
              GuardianAngelSlime.class, c.player.getBoundingBox().inflate(12));
      if (guards.isEmpty()) return false;
      var guard = guards.get(0);
      var delta = guard.getEyePosition().subtract(c.player.getEyePosition());
      c.player.setYRot((float) (Math.atan2(delta.z, delta.x) * 180 / Math.PI) - 90);
      c.player.setXRot((float) (-Math.atan2(delta.y, delta.horizontalDistance()) * 180 / Math.PI));
      if (++ticks < 35) return false;
      if (guard.getBeamTargetId() < 0) {
        beamFrames = 0;
        return false;
      }
      if (++beamFrames < 3) return false;
      var renderer = c.getEntityRenderDispatcher().getRenderer(guard);
      var beamTarget = c.level.getEntity(guard.getBeamTargetId());
      if (beamTarget == null) return false;
      var beam =
          new net.minecraft.world.phys.AABB(
                  guard.getEyePosition(), beamTarget.getBoundingBox().getCenter())
              .inflate(.1);
      var beamOnly =
          new net.minecraft.client.renderer.culling.Frustum(
              new org.joml.Matrix4f(), new org.joml.Matrix4f()) {
            @Override
            public boolean isVisible(net.minecraft.world.phys.AABB bounds) {
              return bounds.equals(beam);
            }
          };
      if (!renderer.shouldRender(guard, beamOnly, guard.getX(), guard.getY(), guard.getZ()))
        throw new IllegalStateException("Guardian beam culled with body outside the view");
      if (!c.getEntityRenderDispatcher()
          .getRenderer(guard)
          .getClass()
          .getSimpleName()
          .equals("GuardianAngelSlimeRenderer"))
        throw new IllegalStateException("Guardian renderer unavailable");
      shot(c, "guardian-wings-halo");
      c.options.fov().set(70);
      c.getSingleplayerServer()
          .execute(
              () -> {
                var entity = c.getSingleplayerServer().overworld().getEntity(guard.getId());
                if (entity != null) ((GuardianAngelSlime) entity).setNoAi(true);
              });
      c.gameMode.interact(c.player, guard, InteractionHand.MAIN_HAND);
      phase = 2;
      ticks = 0;
      return false;
    }
    if (phase == 2) {
      if (!(c.screen instanceof SlimeScreen s) || !s.getMenu().isSynchronized() || ++ticks < 20)
        return false;
      var m = s.getMenu();
      if (m.slots.size() != 37
          || m.getSlot(0).getItem().getCount() != 3
          || m.value(SlimeStat.HEALTH) != 160)
        throw new IllegalStateException("Guardian synchronized reserve/health");
      shot(c, "guardian-menu");
      c.gameMode.handleInventoryMouseClick(m.containerId, 0, 0, ClickType.PICKUP, c.player);
      phase = 3;
      ticks = 0;
      return false;
    }
    if (phase == 3) {
      var m = c.player.containerMenu;
      if (++ticks < 10) return false;
      if (m.getCarried().getCount() != 1 || m.getSlot(0).getItem().getCount() != 2)
        throw new IllegalStateException("Guardian native reserve pickup");
      c.gameMode.handleInventoryMouseClick(m.containerId, 0, 0, ClickType.PICKUP, c.player);
      phase = 4;
      ticks = 0;
      return false;
    }
    if (phase == 4) {
      if (++ticks < 10) return false;
      var m = c.player.containerMenu;
      if (!m.getCarried().isEmpty() || m.getSlot(0).getItem().getCount() != 3)
        throw new IllegalStateException("Guardian native reserve merge");
      c.player.closeContainer();
      c.getSingleplayerServer()
          .execute(
              () -> {
                var o = c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
                o.teleportTo(o.serverLevel(), 7.5, 101, 11.5, -90, 15);
                o.openMenu(
                    (SlimeChestBlockEntity)
                        o.serverLevel().getBlockEntity(new BlockPos(10, 101, 11)));
              });
      phase = 5;
      ticks = 0;
      return false;
    }
    if (phase == 5) {
      if (!(c.player.containerMenu instanceof ChestMenu) || ++ticks < 25) return false;
      var chest = (SlimeChestBlockEntity) c.level.getBlockEntity(new BlockPos(10, 101, 11));
      if (chest.getOpenness(1) < .9)
        throw new IllegalStateException("Chest did not open from native menu event");
      shot(c, "animated-chest-menu");
      c.player.closeContainer();
      phase = 6;
      ticks = 0;
      return false;
    }
    if (phase == 6) {
      if (++ticks < 2) return false;
      shot(c, "animated-chest-closing");
      phase = 7;
      ticks = 0;
      return false;
    }
    if (phase == 7) {
      if (++ticks < 20) return false;
      var chest = (SlimeChestBlockEntity) c.level.getBlockEntity(new BlockPos(10, 101, 11));
      if (chest.getOpenness(1) > .01) throw new IllegalStateException("Chest did not close");
      shot(c, "animated-chest-closed");
      System.out.println(
          "SLIMIC ORDER EIGHT CLIENT SMOKE PASSED: guardian wings/halo, synchronized"
              + " health/reserve, native totem clicks, animated chest, offscreen beam culling; JEI="
              + ModList.get().isLoaded("jei"));
      phase = 8;
      return true;
    }
    return false;
  }

  private static void shot(Minecraft c, String label) {
    var p =
        c.gameDirectory
            .toPath()
            .resolve(
                "../verification/0.1.20-"
                    + (ModList.get().isLoaded("jei") ? "jei-" : "no-jei-")
                    + label
                    + ".png")
            .normalize();
    try (var im = Screenshot.takeScreenshot(c.getMainRenderTarget())) {
      im.writeToFile(p);
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
