package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.client.CreativeSlimeTunerScreen;
import io.github.meistermods.slimic.client.SlimeScreen;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.CreativeSlimeTunerItem;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.*;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Native high-value editor packets, translated warnings, and overflow-page inventory packets. */
public final class CreativeFreedomClientSmoke {
  private static int phase, ticks, language;
  private static UUID identity;
  private static CompletableFuture<?> pending;
  private static final String[] HIGH = {
    "1000000", "1000000", "1000", "20", "100", "60", "1000000", "100", "128"
  };

  private static void check(boolean value, String message) {
    if (!value) throw new IllegalStateException(message);
  }

  private static void server(Minecraft c, Consumer<ServerPlayer> work) {
    pending =
        CompletableFuture.runAsync(
            () ->
                work.accept(
                    c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID())),
            c.getSingleplayerServer());
  }

  private static Button button(CreativeSlimeTunerScreen screen, String key) {
    return screen.children().stream()
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .filter(b -> b.getMessage().getString().equals(Component.translatable(key).getString()))
        .findFirst()
        .orElseThrow();
  }

  private static Button pageButton(SlimeScreen screen, boolean next) {
    return screen.children().stream()
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .filter(b -> b.getMessage().getString().equals(next ? ">" : "<"))
        .findFirst()
        .orElseThrow();
  }

  private static void nextPage(SlimeScreen screen) {
    var button = pageButton(screen, true);
    check(button.visible && button.active, "Every expanded bag must expose a usable page button");
    button.onPress();
  }

  private static void shot(Minecraft c, String name) {
    try {
      var dir = c.gameDirectory.toPath().resolve("creative-freedom-audit");
      Files.createDirectories(dir);
      try (var image = Screenshot.takeScreenshot(c.getMainRenderTarget())) {
        image.writeToFile(dir.resolve((language == 0 ? "en-" : "ko-") + name + ".png"));
      }
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static boolean tick(Minecraft c) {
    if (pending != null) {
      if (!pending.isDone()) return false;
      pending.join();
      pending = null;
    }
    if (++ticks < 10) return false;
    if (phase == 0) {
      c.options.guiScale().set(2);
      c.resizeDisplay();
      String locale = language == 0 ? "en_us" : "ko_kr";
      c.options.languageCode = locale;
      c.getLanguageManager().setSelected(locale);
      pending = c.reloadResourcePacks();
      phase = 1;
      ticks = 0;
      return false;
    }
    if (phase == 1) {
      check(
          c.getWindow().getGuiScaledWidth() >= 368 && c.getWindow().getGuiScaledHeight() >= 240,
          "Management panel fits the selected GUI scale");
      server(
          c,
          p -> {
            p.closeContainer();
            p.stopRiding();
            p.setGameMode(GameType.CREATIVE);
            p.getInventory().clearContent();
            p.getInventory().selected = 0;
            for (int x = 6; x <= 12; x++)
              for (int z = 6; z <= 12; z++) {
                p.serverLevel()
                    .setBlock(new BlockPos(x, 100, z), Blocks.STONE.defaultBlockState(), 2);
                for (int y = 101; y < 106; y++)
                  p.serverLevel()
                      .setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
              }
            p.teleportTo(8.5, 101, 8.5);
            p.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(ModItems.CREATIVE_SLIME_TUNER.get()));
            p.inventoryMenu.broadcastChanges();
            var pet = ModEntities.MINING_SLIME.get().create(p.serverLevel());
            pet.tame(p);
            pet.setNoAi(true);
            pet.moveTo(10.5, 101, 8.5);
            pet.setCustomName(Component.literal("Creative freedom"));
            p.serverLevel().addFreshEntity(pet);
            identity = pet.getUUID();
            CreativeSlimeTunerItem.open(p, pet);
          });
      phase = 2;
      ticks = 0;
      return false;
    }
    if (phase == 2 && c.screen instanceof CreativeSlimeTunerScreen screen) {
      if (!screen.getMenu().isSynchronized() || !button(screen, "screen.slimic.apply").active)
        return false;
      var fields =
          screen.children().stream()
              .filter(EditBox.class::isInstance)
              .map(EditBox.class::cast)
              .toList();
      for (int i = 0; i < 9; i++) fields.get(i).setValue(HIGH[i]);
      fields.get(9).setValue("5000");
      fields.get(10).setValue("900000");
      fields.get(11).setValue("1000000000");
      phase = 3;
      ticks = 0;
      return false;
    }
    if (phase == 3 && c.screen instanceof CreativeSlimeTunerScreen screen) {
      var apply = button(screen, "screen.slimic.apply");
      check(apply.active, "Recommendations must not disable Apply");
      check(
          c.font.split(Component.translatable("screen.slimic.tuner_help"), 156).size() * 9 <= 78,
          "Editor help must fit above buttons");
      check(
          c.font
                      .split(Component.translatable("screen.slimic.tuned_recommended_warning"), 338)
                      .size()
                  * 9
              <= 22,
          "Warning must fit inside panel");
      apply.onPress();
      phase = 4;
      ticks = 0;
      return false;
    }
    if (phase == 4 && c.screen instanceof CreativeSlimeTunerScreen screen) {
      var menu = screen.getMenu();
      if (menu.result() != 2) return false;
      for (var stat : SlimeStat.values())
        check(
            menu.value(stat) == Double.parseDouble(HIGH[stat.ordinal()]),
            "Exact client stat: " + stat);
      check(
          menu.health() == 5000 && menu.stamina() == 900000 && menu.points() == 1000000000,
          "Large client vitals/points");
      shot(c, "warning");
      server(
          c,
          p -> {
            var pet = (MiningSlime) p.serverLevel().getEntity(identity);
            check(
                pet.getHealth() == 5000 && pet.getMaxHealth() == 1000000,
                "Applied native server health");
            pet.getBag().setStackInSlot(0, new ItemStack(Items.APPLE));
            pet.getBag().setStackInSlot(27, new ItemStack(Items.DIAMOND, 7));
            pet.getBag().setStackInSlot(59, new ItemStack(Items.EMERALD, 3));
            p.closeContainer();
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
            p.inventoryMenu.broadcastChanges();
            io.github.meistermods.slimic.item.SlimeTunerItem.open(p, pet);
          });
      phase = 5;
      ticks = 0;
      return false;
    }
    if (phase == 5 && c.screen instanceof SlimeScreen screen) {
      var menu = screen.getMenu();
      if (!menu.isSynchronized()) return false;
      check(
          menu.bagPages() == 3 && menu.value(SlimeStat.HEALTH) == 1000000 && menu.health() == 5000,
          "Management exact stats and pages");
      check(menu.getSlot(2).getItem().is(Items.APPLE), "Initial bag page");
      nextPage(screen);
      phase = 6;
      ticks = 0;
      return false;
    }
    if (phase == 6 && c.screen instanceof SlimeScreen screen) {
      var menu = screen.getMenu();
      if (menu.data(SlimeMenu.BAG_PAGE) != 1) return false;
      check(
          menu.getSlot(2).getItem().is(Items.DIAMOND) && menu.getSlot(2).getItem().getCount() == 7,
          "Overflow page packet must map to slot27");
      c.gameMode.handleInventoryMouseClick(menu.containerId, 2, 0, ClickType.QUICK_MOVE, c.player);
      phase = 7;
      ticks = 0;
      return false;
    }
    if (phase == 7 && c.screen instanceof SlimeScreen screen) {
      var menu = screen.getMenu();
      check(menu.getSlot(2).getItem().isEmpty(), "Native Shift withdrawal synchronizes");
      nextPage(screen);
      phase = 8;
      ticks = 0;
      return false;
    }
    if (phase == 8 && c.screen instanceof SlimeScreen screen) {
      var menu = screen.getMenu();
      if (menu.data(SlimeMenu.BAG_PAGE) != 2) return false;
      check(
          menu.getSlot(7).getItem().is(Items.EMERALD) && menu.getSlot(7).getItem().getCount() == 3,
          "Partial final page maps slot59");
      shot(c, "overflow-page");
      server(
          c,
          p -> {
            var pet = (MiningSlime) p.serverLevel().getEntity(identity);
            check(
                pet.getBag().getStackInSlot(0).is(Items.APPLE)
                    && pet.getBag().getStackInSlot(27).isEmpty()
                    && pet.getBag().getStackInSlot(59).getCount() == 3,
                "Page transfer preserves other cargo");
            check(
                p.getInventory().countItem(Items.DIAMOND) == 7,
                "Cargo conservation after native Shift transfer");
            p.closeContainer();
            pet.discard();
          });
      phase = 9;
      ticks = 0;
      return false;
    }
    if (phase == 9) {
      server(
          c,
          p -> {
            var pet = ModEntities.COMBAT_SLIME.get().create(p.serverLevel());
            pet.tame(p);
            pet.setNoAi(true);
            pet.moveTo(10.5, 101, 8.5);
            SlimeStat.CAPACITY.tune(pet, 60);
            p.serverLevel().addFreshEntity(pet);
            identity = pet.getUUID();
            io.github.meistermods.slimic.item.SlimeTunerItem.open(p, pet);
          });
      phase = 10;
      ticks = 0;
      return false;
    }
    if (phase == 10 && c.screen instanceof SlimeScreen screen) {
      if (!screen.getMenu().isSynchronized()) return false;
      check(pageButton(screen, true).visible, "Combat expanded bag exposes paging");
      c.gameMode.handleInventoryButtonClick(
          screen.getMenu().containerId, SlimeMenu.TARGET_PAGE_BUTTON);
      phase = 11;
      ticks = 0;
      return false;
    }
    if (phase == 11 && c.screen instanceof SlimeScreen screen) {
      if (screen.getMenu().data(SlimeMenu.TARGET_PAGE) != 1) return false;
      check(
          !pageButton(screen, true).visible
              && !pageButton(screen, false).visible
              && !screen.getMenu().isBagVisible(),
          "Target filters hide bag paging");
      server(
          c,
          p -> {
            p.closeContainer();
            p.serverLevel().getEntity(identity).discard();
          });
      phase = 12;
      ticks = 0;
      return false;
    }
    if (phase == 12) {
      if (++language < 2) {
        phase = 0;
        ticks = 0;
        return false;
      }
      System.out.println("SLIMIC CREATIVE FREEDOM CLIENT PASSED");
      return true;
    }
    return false;
  }
}
