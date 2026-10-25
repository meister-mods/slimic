package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.registry.ModItems;
import io.github.meistermods.slimic.world.SlimicWorld;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraftforge.fml.ModList;

/** Resolve real ruin loot on the server, then render every written page with both client fonts. */
public final class AncientRecordsClientSmoke {
  private static final List<String> LANGUAGES =
      List.of("en_us", "ko_kr", "ja_jp", "es_es", "fr_fr");
  private static final List<Map<String, Object>> measurements = new ArrayList<>();
  private static final List<Map<String, Object>> recordMeasurements = new ArrayList<>();
  private static final Map<String, String> englishRecords = new HashMap<>();

  private record Records(List<CompoundTag> books, List<String> messages) {}

  private static CompletableFuture<Records> loading;
  private static List<String> recordMessages;
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
                return new Records(result, recordMessages(owner.serverLevel()));
              },
              c.getSingleplayerServer());
      phase = 1;
      return false;
    }
    if (phase == 1) {
      if (!loading.isDone()) return false;
      books = loading.join().books();
      recordMessages = loading.join().messages();
      changeScenario(c);
      return false;
    }
    if (phase == 2) {
      if (!reload.isDone() || c.getOverlay() != null) return false;
      reload.join();
      if (ModList.get().isLoaded("jei")) {
        if (!JeiClientSmoke.companionInformationReady()) return false;
        JeiClientSmoke.localizedInformationReady();
      }
      checkRecordMessages();
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
              scenario < LANGUAGES.size() ? "default" : "uniform",
              "language",
              LANGUAGES.get(scenario % LANGUAGES.size()),
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
      if (++scenario < LANGUAGES.size() * 2) {
        changeScenario(c);
        return false;
      }
      phase = 4;
    }
    if (phase == 4) {
      if (books.size() != 7
          || measurements.size() != 15 * LANGUAGES.size() * 2
          || recordMeasurements.size() != 8 * LANGUAGES.size() * 2)
        throw new IllegalStateException(
            "Every written page and field record must resolve in all font/language scenarios");
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
                            measurements,
                            "field_record_messages",
                            recordMeasurements))
                + "\n");
      } catch (java.io.IOException failure) {
        throw new IllegalStateException(failure);
      }
      c.setScreen(null);
      System.out.println(
          "SLIMIC ANCIENT RECORD CLIENT SMOKE PASSED: seven real loot books, server resolution, "
              + measurements.size()
              + " native pages, "
              + recordMeasurements.size()
              + " field record messages, English/Korean/Japanese/Spanish/fallback and both fonts");
      return true;
    }
    return false;
  }

  /** Different server-side preferences must not bake one player's language into shared records. */
  private static List<String> recordMessages(ServerLevel level) {
    List<String> expected = null;
    for (String language : List.of("ja_jp", "es_es")) {
      var receiver = new RecordReceiver(level, language);
      for (var item :
          List.of(ModItems.LAST_RALLY_RECORD.get(), ModItems.ENDLESS_ASSEMBLY_RECORD.get())) {
        var stack = new ItemStack(item);
        var before = stack.save(new CompoundTag());
        receiver.setItemInHand(InteractionHand.MAIN_HAND, stack);
        if (!item.use(level, receiver, InteractionHand.MAIN_HAND).getResult().consumesAction()
            || !before.equals(stack.save(new CompoundTag())))
          throw new IllegalStateException("Reading a field record must preserve its item");
      }
      if (receiver.messages.size() != 8)
        throw new IllegalStateException(
            "Both field records must deliver their title and three pages");
      if (expected != null && !expected.equals(receiver.messages))
        throw new IllegalStateException(
            "Record payload must not depend on the recipient's language");
      expected = List.copyOf(receiver.messages);
    }
    return expected;
  }

  private static void checkRecordMessages() {
    String language = LANGUAGES.get(scenario % LANGUAGES.size());
    for (String json : recordMessages) {
      Component message = Component.Serializer.fromJson(json);
      if (message == null || !(message.getContents() instanceof TranslatableContents text))
        throw new IllegalStateException("Server field records must retain translation components");
      String key = text.getKey(), resolved = message.getString();
      if (resolved.isBlank() || resolved.equals(key))
        throw new IllegalStateException("Missing localized field record: " + language + "/" + key);
      if (language.equals("en_us")) englishRecords.put(key, resolved);
      else if (language.equals("fr_fr") && !resolved.equals(englishRecords.get(key)))
        throw new IllegalStateException(
            "Unsupported record languages must retain English fallback");
      else if ((language.equals("ja_jp") || language.equals("es_es"))
          && key.startsWith("record.slimic.")
          && resolved.equals(englishRecords.get(key)))
        throw new IllegalStateException(
            "Supported field record still falls back to English: " + key);
      recordMeasurements.add(
          Map.of(
              "language",
              language,
              "font",
              scenario < LANGUAGES.size() ? "default" : "uniform",
              "key",
              key,
              "text",
              resolved));
    }
  }

  private static final class RecordReceiver extends ServerPlayer {
    private final List<String> messages = new ArrayList<>();

    RecordReceiver(ServerLevel level, String language) {
      super(level.getServer(), level, new GameProfile(UUID.randomUUID(), "LocalizedRecords"));
      updateOptions(
          new ServerboundClientInformationPacket(
              language, 8, ChatVisiblity.FULL, true, 127, HumanoidArm.RIGHT, false, true));
      if (!getLanguage().equals(language))
        throw new IllegalStateException("Native player language preference was not applied");
    }

    @Override
    public void sendSystemMessage(Component message) {
      if (!(message.getContents() instanceof TranslatableContents))
        throw new IllegalStateException("Field records must not resolve in the server's locale");
      messages.add(Component.Serializer.toJson(message));
    }
  }

  private static void changeScenario(Minecraft c) {
    c.setScreen(null);
    c.options.forceUnicodeFont().set(scenario >= LANGUAGES.size());
    c.options.guiScale().set(scenario >= LANGUAGES.size() ? 2 : 3);
    c.resizeDisplay();
    c.getLanguageManager().setSelected(LANGUAGES.get(scenario % LANGUAGES.size()));
    c.options.languageCode = LANGUAGES.get(scenario % LANGUAGES.size());
    reload = c.reloadResourcePacks();
    bookIndex = pageIndex = ticks = 0;
    phase = 2;
  }

  private static void openBook(Minecraft c) {
    var item = ItemStack.of(books.get(bookIndex));
    if (item.getHoverName().getString().contains("lore.slimic."))
      throw new IllegalStateException("Missing localized record title");
    var before = item.save(new CompoundTag());
    if (item.getTag().getString("author").equals("Royal Archives")) {
      var byline =
          net.minecraft.network.chat.Component.translatable(
                  "book.byAuthor",
                  net.minecraft.network.chat.Component.translatable(
                      "lore.slimic.royal_archives.author"))
              .getString();
      if (item.getTooltipLines(c.player, TooltipFlag.NORMAL).stream()
          .map(net.minecraft.network.chat.Component::getString)
          .noneMatch(byline::equals))
        throw new IllegalStateException("Archive author is not localized");
      var unrelated = item.copy();
      var pages = new net.minecraft.nbt.ListTag();
      pages.add(net.minecraft.nbt.StringTag.valueOf("{\"text\":\"My own story\"}"));
      unrelated.getTag().put("pages", pages);
      var originalByline =
          net.minecraft.network.chat.Component.translatable("book.byAuthor", "Royal Archives")
              .getString();
      if (unrelated.getTooltipLines(c.player, TooltipFlag.NORMAL).stream()
          .map(net.minecraft.network.chat.Component::getString)
          .noneMatch(originalByline::equals))
        throw new IllegalStateException("Player-written author must remain unchanged");
    }
    if (!before.equals(item.save(new CompoundTag())))
      throw new IllegalStateException("Archive tooltip must not modify book data");
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
