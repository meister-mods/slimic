package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.client.SlimeScreen;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.SlimeArmorItem;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;

/**
 * Native portraits of hostile professions and both acquisition paths, plus bilingual management.
 */
public final class ForgottenOrdersClientSmoke {
  private static final List<LivingEntity> models = new ArrayList<>();
  private static final List<String> names = new ArrayList<>();
  private static final List<ItemStack> relics = new ArrayList<>();
  private static int phase, ticks;
  private static CompletableFuture<Void> reload;
  private static RoyalGuardSlime royal;

  public static boolean tick(Minecraft c) {
    if (phase == 0) {
      if (reload == null) {
        c.getLanguageManager().setSelected("en_us");
        c.options.languageCode = "en_us";
        reload = c.reloadResourcePacks();
        return false;
      }
      if (!reload.isDone() || c.getOverlay() != null) return false;
      reload.join();
      c.options.forceUnicodeFont().set(false);
      c.options.guiScale().set(1);
      c.resizeDisplay();
      if (c.getWindow().getGuiScaledWidth() < 1200 || c.getWindow().getGuiScaledHeight() < 850)
        return false;
      royal = ModEntities.ROYAL_GUARD_SLIME.get().create(c.level);
      for (int i = 0; i < 2; i++) {
        var guard = i == 0 ? royal : ModEntities.ROYAL_GUARD_SLIME.get().create(c.level);
        guard.setAncientOrigin(i == 1);
        guard.setEquipment(0, new ItemStack(Items.IRON_SWORD));
        guard.setEquipment(1, new ItemStack(Items.SHIELD));
        guard.setEquipment(2, SlimeArmorItem.fromChestplate(new ItemStack(Items.IRON_CHESTPLATE)));
        models.add(guard);
        names.add(i == 0 ? "Crafted Royal Guard" : "Restored Royal Guard");
      }
      for (var type :
          List.of(
              ModEntities.ANCIENT_GUARD_SLIME.get(),
              ModEntities.FORGOTTEN_TRANSPORT_SLIME.get(),
              ModEntities.ANCIENT_COMMANDER_SLIME.get())) {
        var slime = type.create(c.level);
        models.add(slime);
        names.add(slime.getType().getDescription().getString());
      }
      var transport = ModEntities.TRANSPORT_SLIME.get().create(c.level);
      transport.setAncientOrigin(true);
      models.add(transport);
      names.add("Restored Transport");
      var commander = ModEntities.COMMANDER_SLIME.get().create(c.level);
      commander.setAncientOrigin(true);
      models.add(commander);
      names.add("Restored Commander");
      for (var model : models) ((Mob) model).setNoAi(true);
      for (var item :
          List.of(
              ModItems.ROYAL_GUARD_SLIME.get(),
              ModItems.ANCIENT_COMMAND_SEAL.get(),
              ModItems.DAMAGED_ANCIENT_SLIME_CORE.get())) {
        var stack = new ItemStack(item);
        var model = c.getItemRenderer().getModel(stack, c.level, c.player, 0);
        if (model == c.getModelManager().getMissingModel()
            || model.getParticleIcon().contents().name().getPath().equals("missingno"))
          throw new IllegalStateException("Missing relic model: " + item);
        relics.add(stack);
      }
      c.setScreen(new Portraits());
      phase = 1;
      ticks = 0;
      return false;
    }
    if (phase == 1 || phase == 2) {
      if (++ticks < 20) return false;
      shot(c, phase == 1 ? "front" : "rear");
      ticks = 0;
      phase++;
      if (phase == 3) {
        royal.setOwnerUUID(c.player.getUUID());
        royal.setTame(true);
        royal.setWaiting(true);
        c.setScreen(
            new SlimeScreen(
                new SlimeMenu(81, c.player.getInventory(), royal),
                c.player.getInventory(),
                Component.translatable("entity.slimic.royal_guard_slime")));
      }
      return false;
    }
    if (phase == 3) {
      if (++ticks < 20) return false;
      shot(c, "management-en");
      c.setScreen(null);
      c.getLanguageManager().setSelected("ko_kr");
      c.options.languageCode = "ko_kr";
      reload = c.reloadResourcePacks();
      phase = 4;
      ticks = 0;
      return false;
    }
    if (phase == 4) {
      if (!reload.isDone() || c.getOverlay() != null) return false;
      reload.join();
      if (!Component.translatable("entity.slimic.royal_guard_slime")
          .getString()
          .equals("왕실 근위 슬라임")) throw new IllegalStateException("Missing Korean guard name");
      c.setScreen(
          new SlimeScreen(
              new SlimeMenu(82, c.player.getInventory(), royal),
              c.player.getInventory(),
              Component.translatable("entity.slimic.royal_guard_slime")));
      phase = 5;
      return false;
    }
    if (++ticks < 20) return false;
    shot(c, "management-ko");
    c.setScreen(null);
    System.out.println(
        "SLIMIC FORGOTTEN ORDERS CLIENT SMOKE PASSED: three hostile roles, both guard paths,"
            + " restored professions, three relic models, English and Korean management");
    return true;
  }

  private static void shot(Minecraft c, String name) {
    try {
      var folder = c.gameDirectory.toPath().resolve("forgotten-orders-audit");
      Files.createDirectories(folder);
      try (var image = Screenshot.takeScreenshot(c.getMainRenderTarget())) {
        image.writeToFile(folder.resolve(name + ".png"));
      }
    } catch (java.io.IOException error) {
      throw new IllegalStateException(error);
    }
  }

  private static final class Portraits extends Screen {
    Portraits() {
      super(Component.literal("Forgotten Orders"));
    }

    @Override
    public boolean isPauseScreen() {
      return false;
    }

    @Override
    public void render(GuiGraphics g, int x, int y, float partial) {
      g.fill(0, 0, width, height, 0xff18232e);
      g.drawCenteredString(
          font, "Forgotten Orders / " + (phase == 1 ? "Front" : "Rear"), width / 2, 20, 0xffffff);
      for (int i = 0; i < models.size(); i++) {
        int px = width / 2 - 450 + (i % 4) * 300, py = 370 + (i / 4) * 350;
        g.fill(px - 140, py - 280, px + 140, py + 15, 0xff263849);
        InventoryScreen.renderEntityInInventoryFollowsAngle(
            g, px, py - 35, 90, phase == 1 ? .4F : 9.4F, .16F, models.get(i));
        g.drawCenteredString(font, names.get(i), px, py - 270, 0xffffff);
      }
      for (int i = 0; i < relics.size(); i++) {
        g.renderItem(relics.get(i), width / 2 - 60 + i * 40, height - 55);
      }
    }
  }
}
