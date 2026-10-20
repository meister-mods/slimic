package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.client.SlimeScreen;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.SlimeArmorItem;
import io.github.meistermods.slimic.item.SlimeTunerItem;
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
import net.minecraft.world.InteractionHand;
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
  private static RoyalGuardSlime royal, serverRoyal;
  private static CompletableFuture<Void> serverTask;
  private static boolean menuRequested;

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
      if (net.minecraftforge.fml.ModList.get().isLoaded("jei")
          && !JeiClientSmoke.restorationInformationReady()) return false;
      SlimicWorldClientSmoke.checkModels(c);
      var coreEntries =
          ModCreativeTabs.SLIMIC.get().getDisplayItems().stream()
              .filter(stack -> stack.is(ModItems.DAMAGED_ANCIENT_SLIME_CORE.get()))
              .toList();
      if (coreEntries.size() != 3
          || coreEntries.stream()
                  .map(io.github.meistermods.slimic.item.AncientSlimeCoreItem::role)
                  .distinct()
                  .count()
              != 3
          || coreEntries.stream().anyMatch(stack -> stack.getTag().hasUUID("AncientIdentity")))
        throw new IllegalStateException(
            "Creative must expose three independently redeemable core variants");
      for (var core : coreEntries) {
        var model = c.getItemRenderer().getModel(core, c.level, c.player, 0);
        if (model == c.getModelManager().getMissingModel()
            || core.getTooltipLines(c.player, TooltipFlag.NORMAL).size() < 3)
          throw new IllegalStateException("Creative core must have a model and role description");
      }
      var station = new ItemStack(ModWorldBlocks.block("command_pedestal"));
      if (station.getTooltipLines(c.player, TooltipFlag.NORMAL).stream()
          .noneMatch(
              line ->
                  line.getString()
                      .equals(
                          Component.translatable("block.slimic.command_pedestal.tooltip")
                              .getString())))
        throw new IllegalStateException("Station item must explain its restoration materials");
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
              ModItems.DAMAGED_ANCIENT_SLIME_CORE.get(),
              ModItems.ANCIENT_GUARD_SLIME_SPAWN_EGG.get(),
              ModItems.FORGOTTEN_TRANSPORT_SLIME_SPAWN_EGG.get(),
              ModItems.ANCIENT_COMMANDER_SLIME_SPAWN_EGG.get(),
              ModWorldBlocks.block("command_pedestal").asItem())) {
        var stack = new ItemStack(item);
        var model = c.getItemRenderer().getModel(stack, c.level, c.player, 0);
        if (model == c.getModelManager().getMissingModel()
            || model.getParticleIcon().contents().name().getPath().equals("missingno"))
          throw new IllegalStateException("Missing relic model: " + item);
        relics.add(stack);
      }
      if (royal.getMaxHealth() != 160)
        throw new IllegalStateException("Royal Guard client maximum health must be 160");
      if (!c.getItemRenderer()
          .getModel(new ItemStack(ModItems.ANCIENT_COMMAND_SEAL.get()), c.level, c.player, 0)
          .getParticleIcon()
          .contents()
          .name()
          .getPath()
          .equals("item/ancient_command_seal"))
        throw new IllegalStateException("Seal must use its dedicated sprite");
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
        c.setScreen(null);
        serverTask =
            CompletableFuture.runAsync(
                () -> {
                  var owner =
                      c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
                  owner.closeContainer();
                  owner.setItemInHand(
                      InteractionHand.OFF_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
                  serverRoyal = ModEntities.ROYAL_GUARD_SLIME.get().create(owner.serverLevel());
                  serverRoyal.tame(owner);
                  serverRoyal.setNoAi(true);
                  serverRoyal.setNoGravity(true);
                  serverRoyal.moveTo(owner.position().add(1, 0, 0));
                  serverRoyal.setEquipment(0, new ItemStack(Items.IRON_SWORD));
                  serverRoyal.setEquipment(1, new ItemStack(Items.SHIELD));
                  serverRoyal.setEquipment(
                      2, SlimeArmorItem.fromChestplate(new ItemStack(Items.IRON_CHESTPLATE)));
                  owner.serverLevel().addFreshEntity(serverRoyal);
                  serverRoyal.onReleased();
                },
                c.getSingleplayerServer());
      }
      return false;
    }
    if (serverTask != null) {
      if (!serverTask.isDone()) return false;
      serverTask.join();
      serverTask = null;
    }
    if (phase == 3) {
      if (!menuRequested) {
        if (c.level.getEntity(serverRoyal.getId()) == null) return false;
        openMenu(c);
        menuRequested = true;
        ticks = 0;
        return false;
      }
      if (!ready(c) || ++ticks < 20) return false;
      shot(c, "management-en");
      c.player.closeContainer();
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
      SlimicWorldClientSmoke.checkModels(c);
      if (net.minecraftforge.fml.ModList.get().isLoaded("jei")
          && !JeiClientSmoke.restorationInformationReady()) return false;
      openMenu(c);
      phase = 5;
      return false;
    }
    if (phase == 5) {
      if (!ready(c) || ++ticks < 20) return false;
      shot(c, "management-ko");
      c.player.closeContainer();
      serverTask =
          CompletableFuture.runAsync(() -> serverRoyal.discard(), c.getSingleplayerServer());
      phase = 6;
      return false;
    }
    System.out.println(
        "SLIMIC FORGOTTEN ORDERS CLIENT SMOKE PASSED: three hostile roles, both guard paths,"
            + " restored professions, seven relic, egg and station models, English and Korean"
            + " synchronized management at 160 maximum and current health");
    return true;
  }

  private static void openMenu(Minecraft c) {
    serverTask =
        CompletableFuture.runAsync(
            () -> {
              var owner = c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
              if (!SlimeTunerItem.open(owner, serverRoyal).consumesAction())
                throw new IllegalStateException("Real Royal Guard menu could not open");
            },
            c.getSingleplayerServer());
  }

  private static boolean ready(Minecraft c) {
    if (!(c.screen instanceof SlimeScreen screen) || !screen.getMenu().isSynchronized())
      return false;
    var menu = screen.getMenu();
    if (menu.value(SlimeStat.HEALTH) != 160 || menu.data(SlimeMenu.HEALTH) != 1600)
      throw new IllegalStateException("Synchronized Royal Guard menu must display 160/160 health");
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
        g.renderItem(relics.get(i), width / 2 - (relics.size() - 1) * 20 + i * 40, height - 55);
      }
    }
  }
}
