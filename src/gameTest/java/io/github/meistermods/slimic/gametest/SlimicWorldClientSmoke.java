package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.registry.*;
import io.github.meistermods.slimic.world.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;

/**
 * Real models, translated item names, world generation, player portal travel and written records.
 */
public final class SlimicWorldClientSmoke {
  private static int phase, ticks, language;
  private static CompletableFuture<?> task;
  private static ItemStack ledger;
  private static boolean returned;
  private static boolean clickedPortal, enteredPortal;
  private static boolean ruinShown;
  private static BlockPos returnPortal;

  private static void check(boolean ok, String message) {
    if (!ok) throw new IllegalStateException(message);
  }

  private static void server(Minecraft c, Consumer<ServerPlayer> action) {
    task =
        CompletableFuture.runAsync(
            () ->
                action.accept(
                    c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID())),
            c.getSingleplayerServer());
  }

  private static void next() {
    phase++;
    ticks = 0;
  }

  private static void shot(Minecraft c, String name) {
    try {
      var out = c.gameDirectory.toPath().resolve("world-audit");
      Files.createDirectories(out);
      try (var img = Screenshot.takeScreenshot(c.getMainRenderTarget())) {
        img.writeToFile(out.resolve((language == 0 ? "en_us-" : "ko_kr-") + name + ".png"));
      }
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void checkModels(Minecraft c) {
    var missing = c.getModelManager().getMissingModel();
    CreativeModeTabs.tryRebuildTabContents(
        c.level.enabledFeatures(), true, c.level.registryAccess());
    for (var entry : ModWorldBlocks.entries()) {
      var stack = new ItemStack(entry.item().get());
      check(
          !stack.getHoverName().getString().equals(entry.block().get().getDescriptionId()),
          "Translated name for " + entry.id());
      check(
          c.getItemRenderer().getModel(stack, c.level, c.player, 0) != missing,
          "Item model " + entry.id());
      var state = entry.block().get().defaultBlockState();
      var model = c.getBlockRenderer().getBlockModel(state);
      if (entry.category() == ModWorldBlocks.Category.BUILDING
          && !entry.id().endsWith("_stairs")
          && !entry.id().endsWith("_slab")
          && !entry.id().endsWith("_wall"))
        check(
            model.getQuads(state, null, RandomSource.create(1)).isEmpty(),
            "Buried cube faces are culled " + entry.id());
      check(model != missing, "World model " + entry.id());
      List<BakedQuad> quads = new ArrayList<>(model.getQuads(state, null, RandomSource.create(1)));
      for (var face : Direction.values())
        quads.addAll(model.getQuads(state, face, RandomSource.create(1)));
      check(!quads.isEmpty(), "Visible model geometry " + entry.id());
      for (var quad : quads)
        check(
            !quad.getSprite().contents().name().getPath().contains("missingno"),
            "Texture " + entry.id());
      var tab =
          entry.category() == ModWorldBlocks.Category.BUILDING
              ? ModCreativeTabs.BUILDING.get()
              : ModCreativeTabs.DECORATIONS.get();
      check(
          tab.getDisplayItems().stream().anyMatch(i -> i.is(stack.getItem())),
          "Correct Creative tab " + entry.id());
      check(
          ModCreativeTabs.SLIMIC.get().getDisplayItems().stream()
              .noneMatch(i -> i.is(stack.getItem())),
          "World material stays in its own tab " + entry.id());
    }
    check(
        ModCreativeTabs.BUILDING.get().getDisplayItems().size() == 54
            && ModCreativeTabs.DECORATIONS.get().getDisplayItems().size() == 32,
        "Separate 54/32 Creative tabs");
  }

  private static final class Gallery extends Screen {
    Gallery() {
      super(Component.literal("The Slimic — 54 building blocks / 32 decorations"));
    }

    @Override
    public boolean isPauseScreen() {
      return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
      g.fill(0, 0, width, height, 0xff14201b);
      g.drawString(font, title, 8, 8, 0xe0edcf);
      int y = 28;
      for (var category : ModWorldBlocks.Category.values()) {
        var entries =
            ModWorldBlocks.entries().stream().filter(e -> e.category() == category).toList();
        g.drawString(
            font,
            Component.translatable(
                category == ModWorldBlocks.Category.BUILDING
                    ? "itemGroup.slimic.building"
                    : "itemGroup.slimic.decorations"),
            8,
            y,
            0xaacb92);
        y += 13;
        for (int i = 0; i < entries.size(); i++) {
          int x = 10 + i % 18 * 22, row = y + i / 18 * 24;
          var stack = new ItemStack(entries.get(i).item().get());
          g.renderItem(stack, x, row);
          if (mx >= x && mx < x + 16 && my >= row && my < row + 16)
            g.renderTooltip(font, stack, mx, my);
        }
        y += ((entries.size() + 17) / 18) * 24 + 8;
      }
    }
  }

  public static boolean tick(Minecraft c) {
    if (task != null) {
      if (!task.isDone()) return false;
      task.join();
      task = null;
    }
    if (c.getOverlay() != null) return false;
    ticks++;
    if (ticks > 900) throw new IllegalStateException("World client phase timed out: " + phase);
    switch (phase) {
      case 0 -> {
        String lang = language == 0 ? "en_us" : "ko_kr";
        c.getLanguageManager().setSelected(lang);
        c.options.languageCode = lang;
        task = c.reloadResourcePacks();
        next();
      }
      case 1 -> {
        checkModels(c);
        c.setScreen(new Gallery());
        next();
      }
      case 2 -> {
        if (ticks < 20) return false;
        shot(c, "catalog");
        if (language++ == 0) {
          phase = 0;
          ticks = 0;
          return false;
        }
        language = 1;
        c.setScreen(null);
        c.player.getInventory().selected = 0;
        server(
            c,
            p -> {
              p.setGameMode(GameType.CREATIVE);
              var level = p.server.overworld();
              if (p.level() != level) p.teleportTo(level, 1.5, 150, -1.5, 0, 0);
              p.getInventory().selected = 0;
              var shape = new SlimicPortalShape(new BlockPos(1, 150, 1), Direction.Axis.X, 2, 3);
              for (int x = -3; x <= 4; x++)
                for (int z = -3; z <= 3; z++) {
                  var floor = shape.bottom().offset(x, -1, z);
                  level.setBlock(floor, Blocks.STONE.defaultBlockState(), 18);
                  for (int y = 1; y < 7; y++)
                    level.setBlock(floor.above(y), Blocks.AIR.defaultBlockState(), 18);
                }
              for (int x = -1; x <= 2; x++)
                for (int y = -1; y <= 3; y++)
                  if (x == -1 || x == 2 || y == -1 || y == 3)
                    level.setBlock(shape.cell(x, y), Blocks.SLIME_BLOCK.defaultBlockState(), 18);
              p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SLIME_BALL));
              p.inventoryMenu.broadcastChanges();
              p.teleportTo(level, 1.5, 150, -1.5, 0, 0);
              p.setPortalCooldown(0);
            });
        next();
      }
      case 3 -> {
        if (!clickedPortal) {
          if (ticks < 20) return false;
          var floor = new BlockPos(1, 149, 1);
          c.gameMode.useItemOn(
              c.player,
              InteractionHand.MAIN_HAND,
              new net.minecraft.world.phys.BlockHitResult(
                  Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false));
          clickedPortal = true;
          return false;
        }
        if (!enteredPortal) {
          if (!c.level.getBlockState(new BlockPos(1, 150, 1)).is(ModWorldBlocks.PORTAL.get()))
            return false;
          server(
              c,
              p -> {
                check(
                    new SlimicPortalShape(new BlockPos(1, 150, 1), Direction.Axis.X, 2, 3)
                        .complete(p.serverLevel()),
                    "A real right-click packet activates the entire portal");
                p.teleportTo(p.serverLevel(), 1.5, 150, 1.5, 0, 0);
                p.setPortalCooldown(0);
              });
          enteredPortal = true;
          return false;
        }
        if (!c.level.dimension().equals(SlimicWorld.DIMENSION) || ticks < 50) return false;
        if (!c.level.hasChunkAt(c.player.blockPosition())) return false;
        check(
            c.level.getBiome(c.player.blockPosition()).is(SlimicWorld.DAMP_PLAINS),
            "The client receives Damp Plains");
        c.setScreen(null);
        server(
            c,
            p -> {
              var exits = SlimicPortalIndex.get(p.serverLevel()).near(p.blockPosition());
              check(!exits.isEmpty(), "Arrival created a persistent return portal");
              returnPortal = exits.get(0);
              p.teleportTo(p.serverLevel(), p.getX() + 5, p.getY() + 18, p.getZ() - 12, 25, 35);
              p.getAbilities().flying = true;
              p.onUpdateAbilities();
              var params =
                  new LootParams.Builder(p.serverLevel())
                      .withParameter(LootContextParams.ORIGIN, p.position())
                      .create(LootContextParamSets.CHEST);
              var drops =
                  p.server
                      .getLootData()
                      .getLootTable(SlimicWorld.id("chests/slimic_ruins"))
                      .getRandomItems(params);
              ledger =
                  drops.stream().filter(s -> s.is(Items.WRITTEN_BOOK)).findFirst().orElseThrow();
              check(
                  ledger.getTag().getList("pages", 8).size() == 2,
                  "The actual ruin loot includes two written pages");
            });
        next();
      }
      case 4 -> {
        if (ticks < 60) return false;
        shot(c, "damp-plains");
        if (Boolean.getBoolean("slimic.ruinPreview")) {
          server(
              c,
              p -> {
                var level = p.serverLevel();
                var chest = new BlockPos(1147, 95, 1082);
                check(
                    level.getBlockState(chest).is(Blocks.CHEST),
                    "Inspect the naturally generated saved workshop");
                level.setDayTime(6000);
                level.setWeatherParameters(6000, 0, false, false);
                p.teleportTo(level, 1156.5, 104, 1069.5, 38, 32);
              });
          phase = 7;
          ticks = 0;
          return false;
        }
        c.setScreen(new BookViewScreen(BookViewScreen.BookAccess.fromItem(ledger)));
        next();
      }
      case 5 -> {
        if (ticks < 20) return false;
        var text =
            Component.Serializer.fromJson(ledger.getTag().getList("pages", 8).getString(0))
                .getString();
        check(
            !text.contains("lore.slimic") && text.contains("보급"), "Ledger pages render in Korean");
        shot(c, "ledger");
        c.setScreen(null);
        server(
            c,
            p -> {
              var level = p.serverLevel();
              var pos = returnPortal;
              p.teleportTo(level, pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
              p.setPortalCooldown(0);
            });
        next();
      }
      case 6 -> {
        if (!c.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD) || ticks < 40)
          return false;
        returned = true;
        var out = c.gameDirectory.toPath().resolve("world-audit/result.txt");
        try {
          Files.writeString(
              out,
              "PASS: 86 models and textures; 54/32 separate tabs; EN/KR names; native player portal"
                  + " round trip; Damp Plains; translated ruin ledger; returned="
                  + returned
                  + "\n");
        } catch (java.io.IOException e) {
          throw new IllegalStateException(e);
        }
        System.out.println("SLIMIC WORLD CLIENT PASS");
        return true;
      }
      case 7 -> {
        if (ticks < 70) return false;
        shot(c, "generated-workshop");
        ruinShown = true;
        c.setScreen(new BookViewScreen(BookViewScreen.BookAccess.fromItem(ledger)));
        phase = 5;
        ticks = 0;
      }
      default -> throw new IllegalStateException("Unknown world client phase");
    }
    return false;
  }
}
