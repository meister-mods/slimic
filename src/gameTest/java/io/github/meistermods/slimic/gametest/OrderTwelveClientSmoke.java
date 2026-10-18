package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.client.SlimeScreen;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.SlimeArmorItem;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.*;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Native render, menu synchronization, ghost-slot and independent Nyan controls. */
public final class OrderTwelveClientSmoke {
  private static int phase, ticks, menuIndex, pendingMenu = -1, openTicks;
  private static CompletableFuture<Void> reload;
  private static final List<EntityType<? extends FriendlySlime>> TYPES =
      List.of(
          ModEntities.FISHING_SLIME.get(),
          ModEntities.ABUNDANCE_SLIME.get(),
          ModEntities.TUNNEL_SLIME.get(),
          ModEntities.DRAGON_SLIME.get(),
          ModEntities.NYAN_CAT_SLIME.get(),
          ModEntities.HEALING_SLIME.get());

  private static void shot(Minecraft c, String name) {
    try {
      var out = c.gameDirectory.toPath().resolve("order12-audit");
      Files.createDirectories(out);
      try (var image = Screenshot.takeScreenshot(c.getMainRenderTarget())) {
        image.writeToFile(out.resolve(name + ".png"));
      }
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void next() {
    phase++;
    ticks = 0;
    System.out.println("ORDER TWELVE CLIENT PHASE " + phase);
  }

  private static void openMenu(Minecraft c, int index) {
    pendingMenu = index;
    openTicks = 0;
    c.getSingleplayerServer()
        .execute(
            () -> {
              var owner = c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
              var pet =
                  owner
                      .serverLevel()
                      .getEntitiesOfClass(
                          FriendlySlime.class,
                          new AABB(0, 100, 0, 24, 110, 24),
                          s -> s.getType() == TYPES.get(index))
                      .get(0);
              owner.teleportTo(owner.serverLevel(), pet.getX(), 101, pet.getZ() + 2, 180, 5);
              owner.setItemInHand(
                  InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
            });
  }

  private static void closeup(Minecraft c, int index) {
    c.getSingleplayerServer()
        .execute(
            () -> {
              var owner = c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
              var pet =
                  owner
                      .serverLevel()
                      .getEntitiesOfClass(
                          FriendlySlime.class,
                          new AABB(0, 100, 0, 24, 110, 24),
                          s -> s.getType() == TYPES.get(index))
                      .get(0);
              owner.teleportTo(owner.serverLevel(), pet.getX(), 101, pet.getZ() + 4, 180, 12);
            });
  }

  public static boolean tick(Minecraft c) {
    if (pendingMenu >= 0 && ++openTicks >= 10 && c.screen == null) {
      var candidates =
          c.level.getEntitiesOfClass(
              FriendlySlime.class,
              new AABB(0, 100, 0, 24, 110, 24),
              s -> s.getType() == TYPES.get(pendingMenu));
      if (!candidates.isEmpty()
          && c.player.distanceToSqr(candidates.get(0)) < 64
          && c.player.getMainHandItem().is(ModItems.SLIME_TUNER.get())) {
        c.gameMode.interact(c.player, candidates.get(0), InteractionHand.MAIN_HAND);
        pendingMenu = -1;
      }
    }
    if (phase == 0) {
      c.getSingleplayerServer()
          .execute(
              () -> {
                var owner = c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
                var level = owner.serverLevel();
                level
                    .getEntitiesOfClass(
                        net.minecraft.world.entity.Entity.class,
                        new AABB(0, 90, 0, 24, 320, 24),
                        entity -> !(entity instanceof net.minecraft.world.entity.player.Player))
                    .forEach(net.minecraft.world.entity.Entity::discard);
                for (int x = 1; x <= 21; x++)
                  for (int z = 2; z <= 21; z++) {
                    level.setBlock(
                        new BlockPos(x, 100, z), Blocks.SEA_LANTERN.defaultBlockState(), 2);
                    for (int y = 101; y < 116; y++)
                      level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                  }
                owner.getInventory().clearContent();
                for (int i = 0; i < TYPES.size(); i++) {
                  var pet = TYPES.get(i).create(level);
                  pet.tame(owner);
                  pet.moveTo(4.5 + i % 3 * 6, 101, 8.5 + i / 3 * 6);
                  pet.setNoAi(true);
                  pet.setWaiting(true);
                  pet.setCustomName(
                      net.minecraft.network.chat.Component.translatable(
                          pet.getType().getDescriptionId()));
                  pet.setCustomNameVisible(true);
                  if (pet instanceof FishingSlime fisher) {
                    var rod = new ItemStack(Items.FISHING_ROD);
                    rod.enchant(Enchantments.FISHING_LUCK, 3);
                    fisher.setRod(rod);
                  }
                  if (pet instanceof TunnelSlime tunnel) {
                    tunnel.setTool(0, new ItemStack(Items.DIAMOND_PICKAXE));
                    tunnel.setTool(1, new ItemStack(Items.IRON_SHOVEL));
                    tunnel.setLiningTemplate(new ItemStack(Items.STONE_BRICKS));
                  }
                  if (pet instanceof DragonSlime dragon) {
                    dragon.setEquipment(0, new ItemStack(Items.IRON_SWORD));
                    dragon.setEquipment(1, new ItemStack(Items.SHIELD));
                    dragon.setEquipment(
                        2, SlimeArmorItem.fromChestplate(new ItemStack(Items.DIAMOND_CHESTPLATE)));
                  }
                  level.addFreshEntity(pet);
                }
                level.setDayTime(6000);
                owner.teleportTo(level, 10.5, 104, 22, 180, 17);
              });
      c.getLanguageManager().setSelected("ko_kr");
      c.options.languageCode = "ko_kr";
      reload = c.reloadResourcePacks();
      next();
      return false;
    }
    if (phase == 1) {
      if (!reload.isDone() || c.getOverlay() != null || ++ticks < 45) return false;
      for (var type : TYPES.subList(0, 5)) {
        var pet =
            c
                .level
                .getEntitiesOfClass(
                    FriendlySlime.class, new AABB(0, 100, 0, 24, 110, 24), s -> s.getType() == type)
                .stream()
                .findFirst()
                .orElse(null);
        if (pet == null) return false;
        var model = c.getItemRenderer().getModel(pet.getPickResult(), c.level, c.player, 0);
        var quads = model.getQuads(null, null, net.minecraft.util.RandomSource.create(0));
        if (quads.isEmpty()
            || quads.stream()
                .anyMatch(q -> q.getSprite().contents().name().getPath().contains("missingno")))
          throw new IllegalStateException("Missing summon crest: " + type);
      }
      shot(c, "gallery-front");
      openMenu(c, 2);
      next();
      return false;
    }
    if (phase == 2) {
      if (!(c.screen instanceof SlimeScreen screen)
          || !screen.getMenu().isSynchronized()
          || ++ticks < 20) return false;
      var menu = screen.getMenu();
      if (!(menu.slime() instanceof TunnelSlime)
          || menu.slots.size() != 66
          || !menu.slots.get(2).getItem().is(Items.STONE_BRICKS))
        throw new IllegalStateException("Tunnel tools and template did not synchronize");
      c.gameMode.handleInventoryMouseClick(menu.containerId, 2, 0, ClickType.QUICK_MOVE, c.player);
      next();
      return false;
    }
    if (phase == 3) {
      if (++ticks < 15 || !(c.screen instanceof SlimeScreen screen)) return false;
      var menu = screen.getMenu();
      if (!menu.slots.get(2).getItem().is(Items.STONE_BRICKS)
          || c.player.getInventory().countItem(Items.STONE_BRICKS) != 0)
        throw new IllegalStateException("Template was extracted");
      c.getSingleplayerServer()
          .execute(
              () -> {
                var owner = c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
                owner.containerMenu.setCarried(new ItemStack(Items.BRICKS, 9));
                owner.containerMenu.broadcastChanges();
              });
      next();
      return false;
    }
    if (phase == 4) {
      if (!(c.screen instanceof SlimeScreen screen)
          || !screen.getMenu().getCarried().is(Items.BRICKS)) return false;
      c.gameMode.handleInventoryMouseClick(
          screen.getMenu().containerId, 2, 0, ClickType.PICKUP, c.player);
      next();
      return false;
    }
    if (phase == 5) {
      if (++ticks < 20 || !(c.screen instanceof SlimeScreen screen)) return false;
      var menu = screen.getMenu();
      if (!menu.slots.get(2).getItem().is(Items.BRICKS) || menu.getCarried().getCount() != 9)
        throw new IllegalStateException("Template click consumed cursor blocks");
      shot(c, "tunnel-template-menu");
      c.gameMode.handleInventoryButtonClick(menu.containerId, SlimeMenu.DIRECTION);
      next();
      return false;
    }
    if (phase == 6) {
      if (!(c.screen instanceof SlimeScreen screen)
          || !(screen.getMenu().slime() instanceof TunnelSlime tunnel)
          || tunnel.getTunnelDirection() != net.minecraft.core.Direction.EAST
          || ++ticks < 15) return false;
      c.player.closeContainer();
      openMenu(c, 4);
      next();
      return false;
    }
    if (phase == 7) {
      if (!(c.screen instanceof SlimeScreen screen)
          || !(screen.getMenu().slime() instanceof NyanCatSlime)
          || !screen.getMenu().isSynchronized()
          || ++ticks < 15) return false;
      c.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, SlimeMenu.ACTIVE);
      c.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, SlimeMenu.ROAM);
      next();
      return false;
    }
    if (phase == 8) {
      if (!(c.screen instanceof SlimeScreen screen)
          || !(screen.getMenu().slime() instanceof NyanCatSlime cat)
          || cat.isActive()
          || !cat.isRoaming()
          || ++ticks < 20) return false;
      shot(c, "nyan-independent-controls");
      c.player.closeContainer();
      menuIndex = 0;
      openMenu(c, menuIndex);
      next();
      return false;
    }
    if (phase == 9) {
      if (!(c.screen instanceof SlimeScreen screen)
          || !screen.getMenu().isSynchronized()
          || ++ticks < 15) return false;
      int expected =
          switch (menuIndex) {
            case 0 -> 64;
            case 1 -> 63;
            case 3 -> 66;
            default -> 36;
          };
      if (screen.getMenu().slime().getType() != TYPES.get(menuIndex)
          || screen.getMenu().slots.size() != expected)
        throw new IllegalStateException("Profession menu layout: " + menuIndex);
      shot(c, "profession-menu-" + menuIndex);
      c.player.closeContainer();
      if (menuIndex == 0) {
        menuIndex = 1;
        openMenu(c, menuIndex);
        ticks = 0;
        return false;
      }
      if (menuIndex == 1) {
        menuIndex = 3;
        openMenu(c, menuIndex);
        ticks = 0;
        return false;
      }
      c.getSingleplayerServer()
          .execute(
              () -> {
                var owner = c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
                owner.teleportTo(owner.serverLevel(), 10.5, 104, 2, 0, 17);
              });
      next();
      return false;
    }
    if (phase == 10) {
      if (++ticks < 40) return false;
      shot(c, "gallery-rear");
      menuIndex = 0;
      closeup(c, menuIndex);
      next();
      return false;
    }
    if (phase == 11) {
      if (++ticks < 25) return false;
      shot(c, "closeup-" + menuIndex);
      if (++menuIndex < TYPES.size()) {
        closeup(c, menuIndex);
        ticks = 0;
        return false;
      }
      System.out.println(
          "SLIMIC ORDER TWELVE CLIENT SMOKE PASSED: five profession models and crests, four"
              + " synchronized menu layouts, ghost template preservation, direction update,"
              + " independent Nyan controls, six close-up renders");
      next();
      return true;
    }

    return phase > 11;
  }
}
