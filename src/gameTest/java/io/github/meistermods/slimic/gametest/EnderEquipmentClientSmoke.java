package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.client.SlimeScreen;
import io.github.meistermods.slimic.entity.EnderSlime;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.*;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Native tracking, localized equipment screen, item model and live JEI recipe checks. */
public final class EnderEquipmentClientSmoke {
  private static final String[] LANGUAGES = {"en_us", "ko_kr", "ja_jp", "es_es"};
  private static CompletableFuture<?> action;
  private static int phase, ticks, language, entity;

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  public static boolean tick(Minecraft mc) {
    try {
      if (action != null && !action.isDone()) return false;
      if (action != null) {
        action.join();
        action = null;
      }
      if (phase == 0) {
        if (!JeiClientSmoke.enderEquipmentRecipesReady()) return false;
        require(
            JeiClientSmoke.attentionBaseRecipesReady(),
            "All fifteen direct alternatives remain in JEI");
        mc.options.guiScale().set(2);
        mc.resizeDisplay();
        mc.options.languageCode = LANGUAGES[0];
        mc.getLanguageManager().setSelected(LANGUAGES[0]);
        action =
            mc.getSingleplayerServer()
                .submit(
                    () -> {
                      var p =
                          mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                      var level = p.serverLevel();
                      level.getWorldBorder().setCenter(0, 0);
                      level.getWorldBorder().setSize(59999968);
                      for (int x = 700008; x < 700025; x++)
                        for (int z = 700008; z < 700025; z++) {
                          level.setBlock(
                              new BlockPos(x, 100, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                          for (int y = 101; y < 106; y++)
                            level.setBlock(
                                new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                        }
                      p.setGameMode(GameType.CREATIVE);
                      p.getInventory().clearContent();
                      p.teleportTo(level, 700016.5, 101, 700018.5, 180, 0);
                      p.setItemInHand(
                          InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
                      var s = ModEntities.ENDER_SLIME.get().create(level);
                      s.tame(p);
                      s.moveTo(700016.5, 101, 700016.5);
                      s.setNoAi(true);
                      s.setEquipment(0, new ItemStack(Items.ENDER_PEARL, 16));
                      s.setEquipment(1, new ItemStack(ModItems.DIMENSIONAL_ATTUNEMENT_SEAL.get()));
                      level.addFreshEntity(s);
                      require(s.bind(p), "The native client fixture has a real pinned anchor");
                      s.setTunedVitals(s.getHealth(), s.getMaxStamina(), 0);
                      entity = s.getId();
                    });
        phase = 1;
        return false;
      }
      if (phase == 1) {
        if (ticks == 0) {
          action = mc.reloadResourcePacks();
          ticks++;
          return false;
        }
        if (++ticks < 25 || !(mc.level.getEntity(entity) instanceof EnderSlime)) return false;
        action =
            mc.getSingleplayerServer()
                .submit(
                    () -> {
                      var p =
                          mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                      p.interactOn(p.serverLevel().getEntity(entity), InteractionHand.MAIN_HAND);
                    });
        phase = 2;
        ticks = 0;
        return false;
      }
      if (phase == 2) {
        if (mc.getOverlay() != null || ++ticks < 15) return false;
        require(
            mc.screen instanceof SlimeScreen && mc.player.containerMenu instanceof SlimeMenu,
            "Native Ender equipment screen opens");
        var menu = (SlimeMenu) mc.player.containerMenu;
        require(
            menu.slots.get(0).getItem().is(Items.ENDER_PEARL)
                && menu.slots.get(0).getItem().getCount() == 16
                && menu.slots.get(1).getItem().is(ModItems.DIMENSIONAL_ATTUNEMENT_SEAL.get()),
            "Both equipment stacks synchronize");
        for (var key :
            new String[] {
              "screen.slimic.ender_help",
              "screen.slimic.ender_pearls",
              "screen.slimic.ender_seal",
              "item.slimic.dimensional_attunement_seal"
            })
          require(
              !Component.translatable(key).getString().equals(key),
              "Localized equipment text exists: " + key);
        var model =
            mc.getItemRenderer()
                .getModel(
                    new ItemStack(ModItems.DIMENSIONAL_ATTUNEMENT_SEAL.get()),
                    mc.level,
                    mc.player,
                    0);
        require(
            !model.getParticleIcon().contents().name().getPath().equals("missingno"),
            "The seal has a native atlas texture");
        var out = mc.gameDirectory.toPath().resolve("ender-equipment-audit");
        Files.createDirectories(out);
        try (var shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
          shot.writeToFile(out.resolve(LANGUAGES[language] + ".png"));
        }
        if (++language < LANGUAGES.length) {
          mc.options.languageCode = LANGUAGES[language];
          mc.getLanguageManager().setSelected(mc.options.languageCode);
          action = mc.reloadResourcePacks();
          ticks = 0;
          return false;
        }
        action =
            mc.getSingleplayerServer()
                .submit(
                    () -> {
                      var p =
                          mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                      var s = (EnderSlime) p.serverLevel().getEntity(entity);
                      var data = s.saveWithoutId(new net.minecraft.nbt.CompoundTag());
                      data.putBoolean("EnderPinned", false);
                      data.put("EnderPearls", new net.minecraft.nbt.CompoundTag());
                      s.load(data);
                      require(
                          !s.isPinned()
                              && !s.isNoGravity()
                              && !io.github.meistermods.slimic.entity.CompanionRegistry.get(
                                      p.serverLevel())
                                  .find(s.getUUID())
                                  .chunkLoading(),
                          "A live NBT reload reconciles server anchor state");
                    });
        phase = 3;
        ticks = 0;
        return false;
      }
      if (phase == 3) {
        if (++ticks < 15) return false;
        var s = (EnderSlime) mc.level.getEntity(entity);
        require(
            mc.screen instanceof SlimeScreen && mc.player.containerMenu instanceof SlimeMenu,
            "The management menu remains valid across live reload");
        var menu = (SlimeMenu) mc.player.containerMenu;
        require(
            !s.isPinned()
                && !s.isNoGravity()
                && s.getEquipment(0).isEmpty()
                && s.allowsDimensionalTravel()
                && menu.slots.get(0).getItem().isEmpty()
                && menu.slots.get(1).getItem().is(ModItems.DIMENSIONAL_ATTUNEMENT_SEAL.get()),
            "Pin, gravity and both equipment slots synchronize after live reload");
        var out = mc.gameDirectory.toPath().resolve("ender-equipment-audit");
        try (var shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
          shot.writeToFile(out.resolve("live-reload.png"));
        }
        mc.player.closeContainer();
        action =
            mc.getSingleplayerServer()
                .submit(
                    () -> {
                      var p =
                          mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                      var server = p.serverLevel().getEntity(entity);
                      if (server != null) server.discard();
                    });
        phase = 4;
        return false;
      }
      System.out.println(
          "SLIMIC ENDER EQUIPMENT CLIENT SMOKE PASSED: four locales; native two-slot screen; unique"
              + " item atlas; guardian and seal JEI recipes");
      return true;
    } catch (Exception failure) {
      throw new IllegalStateException(
          "Ender equipment client check failed at phase " + phase, failure);
    }
  }
}
