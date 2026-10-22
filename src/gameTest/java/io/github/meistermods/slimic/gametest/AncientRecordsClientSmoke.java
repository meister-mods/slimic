package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.world.SlimicWorld;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;

/** Resolve real ruin loot on the server, then render every written page with both client fonts. */
public final class AncientRecordsClientSmoke {
  private static final List<String> LANGUAGES = List.of("en_us", "ko_kr", "fr_fr");
  private static final List<Map<String, Object>> measurements = new ArrayList<>();
  private static CompletableFuture<List<CompoundTag>> loading;
  private static CompletableFuture<Void> reload;
  private static List<CompoundTag> books;
  private static int phase, scenario, bookIndex, pageIndex, ticks;
  private static BookViewScreen.BookAccess access;

  public static boolean tick(Minecraft c) {
    if (phase == 0) {
      loading =
          CompletableFuture.supplyAsync(
              () -> {
                var server = c.getSingleplayerServer();
                var owner = server.getPlayerList().getPlayer(c.player.getUUID());
                var result = new ArrayList<CompoundTag>();
                for (var path :
                    List.of(
                        "slimic_ruins",
                        "slimic_archive",
                        "slimic_royal_ward",
                        "sanctum_courtyard",
                        "sanctum_gallery",
                        "sanctum_laboratory",
                        "sanctum_core")) {
                  var params =
                      new LootParams.Builder(owner.serverLevel())
                          .withParameter(LootContextParams.ORIGIN, owner.position())
                          .create(LootContextParamSets.CHEST);
                  var stack =
                      server
                          .getLootData()
                          .getLootTable(SlimicWorld.id("chests/" + path))
                          .getRandomItems(params, 17)
                          .stream()
                          .filter(item -> item.is(Items.WRITTEN_BOOK))
                          .findFirst()
                          .orElseThrow();
                  if (!WrittenBookItem.makeSureTagIsValid(stack.getTag())
                      || !WrittenBookItem.resolveBookComponents(
                          stack, owner.createCommandSourceStack(), owner))
                    throw new IllegalStateException("Invalid generated written record: " + path);
                  for (var raw : stack.getTag().getList("pages", 8))
                    if (!raw.getAsString().contains("lore.slimic."))
                      throw new IllegalStateException(
                          "Server resolution must preserve localized page components");
                  result.add(stack.save(new CompoundTag()));
                }
                return result;
              },
              c.getSingleplayerServer());
      phase = 1;
      return false;
    }
    if (phase == 1) {
      if (!loading.isDone()) return false;
      books = loading.join();
      changeScenario(c);
      return false;
    }
    if (phase == 2) {
      if (!reload.isDone() || c.getOverlay() != null) return false;
      reload.join();
      openBook(c);
      phase = 3;
      return false;
    }
    if (phase == 3) {
      if (++ticks < 3) return false;
      ticks = 0;
      var text = access.getPage(pageIndex);
      var lines = c.font.split(text, RecordScreen.widthLimit());
      if (text.getString().isBlank()
          || text.getString().contains("lore.slimic.")
          || lines.size() * c.font.lineHeight > RecordScreen.heightLimit())
        throw new IllegalStateException(
            "Untranslated or overflowing ancient record: "
                + scenario
                + "/"
                + bookIndex
                + "/"
                + pageIndex
                + " lines="
                + lines.size());
      measurements.add(
          Map.of(
              "font",
              scenario < 3 ? "default" : "uniform",
              "language",
              LANGUAGES.get(scenario % 3),
              "book",
              bookIndex,
              "page",
              pageIndex,
              "lines",
              lines.size(),
              "text",
              text.getString()));
      if (pageIndex == 0) {
        try {
          var path = c.gameDirectory.toPath().resolve("ancient-records-audit");
          Files.createDirectories(path);
          try (var image = Screenshot.takeScreenshot(c.getMainRenderTarget())) {
            image.writeToFile(path.resolve("scenario-" + scenario + "-book-" + bookIndex + ".png"));
          }
        } catch (java.io.IOException failure) {
          throw new IllegalStateException(failure);
        }
      }
      if (++pageIndex < access.getPageCount()) {
        ((BookViewScreen) c.screen).setPage(pageIndex);
        return false;
      }
      if (++bookIndex < books.size()) {
        openBook(c);
        return false;
      }
      if (++scenario < 6) {
        changeScenario(c);
        return false;
      }
      phase = 4;
    }
    if (phase == 4) {
      if (books.size() != 7 || measurements.size() != 90)
        throw new IllegalStateException(
            "Every written page must render in six font/language scenarios");
      try {
        var path = c.gameDirectory.toPath().resolve("ancient-records-audit/measurements.json");
        Files.writeString(
            path,
            new com.google.gson.GsonBuilder()
                    .setPrettyPrinting()
                    .create()
                    .toJson(
                        Map.of(
                            "passed",
                            true,
                            "books",
                            books.size(),
                            "rendered_pages",
                            measurements.size(),
                            "measurements",
                            measurements))
                + "\n");
      } catch (java.io.IOException failure) {
        throw new IllegalStateException(failure);
      }
      c.setScreen(null);
      System.out.println(
          "SLIMIC ANCIENT RECORD CLIENT SMOKE PASSED: seven real loot books, server resolution, 90"
              + " native pages, English/Korean/fallback and both fonts");
      return true;
    }
    return false;
  }

  private static void changeScenario(Minecraft c) {
    c.setScreen(null);
    c.options.forceUnicodeFont().set(scenario >= 3);
    c.options.guiScale().set(scenario >= 3 ? 2 : 3);
    c.resizeDisplay();
    c.getLanguageManager().setSelected(LANGUAGES.get(scenario % 3));
    c.options.languageCode = LANGUAGES.get(scenario % 3);
    reload = c.reloadResourcePacks();
    bookIndex = pageIndex = ticks = 0;
    phase = 2;
  }

  private static void openBook(Minecraft c) {
    var item = ItemStack.of(books.get(bookIndex));
    if (item.getHoverName().getString().contains("lore.slimic."))
      throw new IllegalStateException("Missing localized record title");
    access = BookViewScreen.BookAccess.fromItem(item);
    pageIndex = 0;
    c.setScreen(new RecordScreen(access));
  }

  private static final class RecordScreen extends BookViewScreen {
    RecordScreen(BookAccess access) {
      super(access);
    }

    static int widthLimit() {
      return TEXT_WIDTH;
    }

    static int heightLimit() {
      return TEXT_HEIGHT;
    }
  }
}
