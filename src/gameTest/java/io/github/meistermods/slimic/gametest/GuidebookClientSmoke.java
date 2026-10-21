package io.github.meistermods.slimic.gametest;

import com.google.gson.GsonBuilder;
import io.github.meistermods.slimic.registry.ModCreativeTabs;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/** Exercises the installed guide renderer without adding a Patchouli Java dependency to Slimic. */
public final class GuidebookClientSmoke {
  private static final ResourceLocation BOOK =
      ResourceLocation.fromNamespaceAndPath("slimic", "guide");
  private static final List<String> LANGUAGES = List.of("en_us", "ko_kr", "fr_fr");
  private static final List<Map<String, Object>> measurements = new ArrayList<>();
  private static final List<String> problems = new ArrayList<>();
  private static final Set<Supplier<Boolean>> links =
      Collections.newSetFromMap(new IdentityHashMap<>());
  private static int checkedLinks, checkedLookups, checkedSearches;
  private static int phase, ticks, language, index, spread, rendered;
  private static Object book, entry;
  private static List<?> entries;
  private static CompletableFuture<Void> reload, crafting;

  private GuidebookClientSmoke() {}

  private static Object field(Object o, String name) throws ReflectiveOperationException {
    for (Class<?> t = o.getClass(); t != null; t = t.getSuperclass()) {
      try {
        var f = t.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
      } catch (NoSuchFieldException ignored) {
      }
    }
    throw new NoSuchFieldException(o.getClass().getName() + "." + name);
  }

  private static Object invoke(Object o, String name) throws ReflectiveOperationException {
    return o.getClass().getMethod(name).invoke(o);
  }

  private static Object singleton(String name) throws ReflectiveOperationException {
    return Class.forName(name).getField("INSTANCE").get(null);
  }

  private static Path output(Minecraft c) throws java.io.IOException {
    var p = c.gameDirectory.toPath().resolve("guidebook-audit");
    Files.createDirectories(p);
    return p;
  }

  private static void shot(Minecraft c, String name) throws java.io.IOException {
    try (var image = Screenshot.takeScreenshot(c.getMainRenderTarget())) {
      image.writeToFile(output(c).resolve(LANGUAGES.get(language) + "-" + name + ".png"));
    }
  }

  private static void openEntry(Minecraft c) throws ReflectiveOperationException {
    var pages = (List<?>) invoke(entry, "getPages");
    var type = Class.forName("vazkii.patchouli.client.book.gui.GuiBookEntry");
    var screen =
        type.getConstructor(book.getClass(), entry.getClass(), int.class)
            .newInstance(book, entry, spread);
    c.setScreen((Screen) screen);
    ticks = 0;
    if (spread * 2 >= pages.size()) throw new IllegalStateException("Invalid book spread");
  }

  private static void measure(Object renderer, String id) throws ReflectiveOperationException {
    var words = (List<?>) field(renderer, "words");
    if (words.isEmpty()) return;
    float scale = ((Number) field(renderer, "scale")).floatValue();
    double first = ((Number) field(words.get(0), "y")).doubleValue(), bottom = first;
    for (var word : words)
      bottom =
          Math.max(
              bottom,
              ((Number) field(word, "y")).doubleValue()
                  + ((Number) field(word, "height")).doubleValue());
    var plain = new StringBuilder();
    for (var word : words) {
      var text = (Component) field(word, "text");
      plain.append(text.getString());
      var hover = text.getStyle().getHoverEvent();
      if (hover != null && hover.toString().contains("BAD LINK:"))
        problems.add(LANGUAGES.get(language) + " invalid link in " + id);
      if (field(word, "onClick") instanceof Supplier<?> callback) {
        @SuppressWarnings("unchecked")
        var link = (Supplier<Boolean>) callback;
        links.add(link);
      }
    }
    if (plain.toString().replaceAll("\\s+", "").contains("Formaterror:"))
      problems.add(LANGUAGES.get(language) + " formatting error in " + id);
    double painted = first + (bottom - first) * scale;
    measurements.add(
        Map.of(
            "language",
            LANGUAGES.get(language),
            "page",
            id,
            "scale",
            scale,
            "bottom",
            painted,
            "plain_text",
            plain.toString()));
    if (painted > 156.5 || scale < 0.85f)
      problems.add(LANGUAGES.get(language) + " " + id + " scale=" + scale + " bottom=" + painted);
  }

  private static void inspect(Object page, String id) throws ReflectiveOperationException {
    if (page == null || page.getClass().getSimpleName().equals("PageEmpty")) return;
    rendered++;
    if (page.getClass().getSimpleName().equals("PageTemplate")) {
      var template = field(page, "template");
      for (var component : (List<?>) field(template, "components")) {
        if (component.getClass().getSimpleName().equals("ComponentText"))
          measure(field(component, "textRenderer"), id);
        if (component.getClass().getSimpleName().equals("ComponentItemStack")) {
          for (var stack : (ItemStack[]) field(component, "items"))
            if (stack.isEmpty()) throw new IllegalStateException("Empty diagram ingredient: " + id);
        }
      }
    } else {
      measure(field(page, "textRender"), id);
    }
  }

  private static void checkSearch(Minecraft c) throws ReflectiveOperationException {
    var landing = c.screen;
    var type = Class.forName("vazkii.patchouli.client.book.gui.GuiBookIndex");
    c.setScreen((Screen) type.getConstructor(book.getClass()).newInstance(book));
    if (((List<?>) field(c.screen, "allEntries")).size() != 55)
      throw new IllegalStateException("The searchable index must include all guide entries");
    String query = language == 1 ? "치유 슬라임" : "Healing Slime";
    for (char letter : query.toCharArray()) c.screen.charTyped(letter, 0);
    var matches = (List<?>) field(c.screen, "visibleEntries");
    var expected = ResourceLocation.fromNamespaceAndPath("slimic", "companions/healing");
    if (matches.size() != 1 || !expected.equals(invoke(matches.get(0), "getId")))
      throw new IllegalStateException("Localized guide search failed: " + query);
    c.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
    if (!expected.equals(invoke(field(c.screen, "entry"), "getId")))
      throw new IllegalStateException("The search result did not open its entry");
    checkedSearches++;
    c.setScreen(landing);
  }

  public static boolean tick(Minecraft c) {
    try {
      if (phase == 8) return true;
      if (!ModList.get().isLoaded("patchouli")) {
        System.out.println("SLIMIC GUIDEBOOK CLIENT SMOKE PASSED: optional Patchouli absent");
        phase = 8;
        return true;
      }
      if (phase == 0) {
        crafting =
            CompletableFuture.runAsync(
                () -> {
                  var owner =
                      c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
                  owner.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                  var recipeId = ResourceLocation.fromNamespaceAndPath("slimic", "guide_book");
                  var recipe = owner.serverLevel().getRecipeManager().byKey(recipeId).orElseThrow();
                  var advancement =
                      owner
                          .server
                          .getAdvancements()
                          .getAdvancement(
                              ResourceLocation.fromNamespaceAndPath(
                                  "slimic", "recipes/misc/guide_book"));
                  owner.resetRecipes(java.util.List.of(recipe));
                  owner.getAdvancements().revoke(advancement, "has_materials");
                  owner.getAdvancements().revoke(advancement, "has_the_recipe");
                  owner.getInventory().setItem(1, new ItemStack(Items.BOOK));
                  owner.getInventory().setItem(2, new ItemStack(Items.SLIME_BALL));
                  net.minecraft.advancements.CriteriaTriggers.INVENTORY_CHANGED.trigger(
                      owner, owner.getInventory(), owner.getInventory().getItem(2));
                  if (!owner.getAdvancements().getOrStartProgress(advancement).isDone()
                      || !owner.getRecipeBook().contains(recipe))
                    throw new IllegalStateException(
                        "Acquiring materials must unlock the guide recipe");
                  var menu = owner.inventoryMenu;
                  menu.getSlot(1).set(owner.getInventory().removeItem(1, 1));
                  menu.getSlot(2).set(owner.getInventory().removeItem(2, 1));
                  var resultSlot = menu.getSlot(0);
                  var stack = resultSlot.remove(1);
                  if (stack.isEmpty()
                      || !stack.hasTag()
                      || !BOOK.toString().equals(stack.getTag().getString("patchouli:book")))
                    throw new IllegalStateException("Inventory crafting did not produce the guide");
                  resultSlot.onTake(owner, stack);
                  if (!menu.getSlot(1).getItem().isEmpty() || !menu.getSlot(2).getItem().isEmpty())
                    throw new IllegalStateException(
                        "Crafting must consume exactly one book and slimeball");
                  owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
                  owner.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                },
                c.getSingleplayerServer());
        phase = 1;
        return false;
      }
      if (phase == 1) {
        if (!crafting.isDone()) return false;
        crafting.join();
        var held = c.player.getMainHandItem();
        if (!held.hasTag() || !BOOK.toString().equals(held.getTag().getString("patchouli:book")))
          return false;
        c.gameMode.useItem(c.player, InteractionHand.MAIN_HAND);
        phase = 2;
        ticks = 0;
        return false;
      }
      if (phase == 2) {
        if (c.screen == null
            || !c.screen.getClass().getName().contains("patchouli.client.book.gui.GuiBook")) {
          if (++ticks > 400) throw new IllegalStateException("The crafted guide never opens");
          return false;
        }
        if (ModList.get().isLoaded("jei")) {
          long recipes = JeiClientSmoke.guidebookRecipes();
          if (recipes == 0 && ++ticks < 400) return false;
          if (recipes != 1)
            throw new IllegalStateException("JEI must expose exactly one guide recipe: " + recipes);
        }
        CreativeModeTabs.tryRebuildTabContents(
            c.level.enabledFeatures(), true, c.level.registryAccess());
        if (ModCreativeTabs.SLIMIC.get().getDisplayItems().stream()
            .noneMatch(
                s -> s.hasTag() && BOOK.toString().equals(s.getTag().getString("patchouli:book"))))
          throw new IllegalStateException("Slimic Creative tab is missing the guide");
        phase = 3;
        return false;
      }
      if (phase == 3) {
        c.setScreen(null);
        var lang = LANGUAGES.get(language);
        c.getLanguageManager().setSelected(lang);
        c.options.languageCode = lang;
        c.options.save();
        reload = c.reloadResourcePacks();
        phase = 4;
        return false;
      }
      if (phase == 4) {
        if (!reload.isDone()) return false;
        reload.join();
        if (c.getOverlay() != null) return false;
        var registry = singleton("vazkii.patchouli.common.book.BookRegistry");
        book = ((Map<?, ?>) field(registry, "books")).get(BOOK);
        if (book == null)
          throw new IllegalStateException("Guide definition absent after resource reload");
        var contents = invoke(book, "getContents");
        if (Boolean.TRUE.equals(invoke(contents, "isErrored")))
          throw new IllegalStateException(
              "Guide contents failed to build: " + invoke(contents, "getException"));
        var all = (Map<?, ?>) field(contents, "entries");
        if (all.size() != 55 || ((Map<?, ?>) field(contents, "categories")).size() != 7)
          throw new IllegalStateException("Incomplete guide contents: " + all.size());
        for (var item : ForgeRegistries.ITEMS.getEntries()) {
          if (!item.getKey().location().getNamespace().equals("slimic")) continue;
          var mapping =
              contents
                  .getClass()
                  .getMethod("getEntryForStack", ItemStack.class)
                  .invoke(contents, new ItemStack(item.getValue()));
          if (mapping == null)
            throw new IllegalStateException("Missing item lookup: " + item.getKey().location());
          String path = item.getKey().location().getPath();
          if (path.equals("creative_slime_tuner") || path.equals("slime_slag")) {
            var target =
                ResourceLocation.fromNamespaceAndPath(
                    "slimic", path.equals("slime_slag") ? "companions/soggy" : "basics/tuner");
            if (!target.equals(invoke(invoke(mapping, "getFirst"), "getId"))
                || ((Number) invoke(mapping, "getSecond")).intValue() != 1)
              throw new IllegalStateException("Item lookup must open its specific page: " + path);
          }
          checkedLookups++;
        }
        entries =
            all.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().toString()))
                .map(Map.Entry::getValue)
                .toList();
        var client = singleton("vazkii.patchouli.client.book.ClientBookRegistry");
        client
            .getClass()
            .getMethod("displayBookGui", ResourceLocation.class, ResourceLocation.class, int.class)
            .invoke(client, BOOK, null, 0);
        String expected = language == 1 ? "슬라임 동료 안내서" : "Slimic Companion Guide";
        if (!c.player.getMainHandItem().getHoverName().getString().equals(expected))
          throw new IllegalStateException(
              "Incorrect guide language: " + c.player.getMainHandItem().getHoverName());
        var subtitle = (Component) invoke(book, "getSubtitle");
        if (!subtitle
                .getString()
                .equals(Component.translatable("book.slimic.guide.subtitle").getString())
            || c.font.width(subtitle) > 108
            || c.font.width(c.player.getMainHandItem().getHoverName()) > 119)
          throw new IllegalStateException(
              "The localized book name and subtitle must fit the cover");
        ticks = 0;
        phase = 5;
        return false;
      }
      if (phase == 5) {
        if (++ticks < 4) return false;
        measure(field(c.screen, "text"), "landing");
        shot(c, "landing");
        checkSearch(c);
        index = 0;
        spread = 0;
        entry = entries.get(0);
        rendered = 0;
        openEntry(c);
        phase = 6;
        return false;
      }
      if (phase == 6) {
        if (++ticks < 3) return false;
        links.clear();
        String id = invoke(entry, "getId").toString();
        Component title = (Component) invoke(entry, "getName");
        if (c.font.width(title) > 116)
          problems.add(LANGUAGES.get(language) + " title too wide: " + title.getString());
        inspect(field(c.screen, "leftPage"), id + "/" + (spread * 2));
        inspect(field(c.screen, "rightPage"), id + "/" + (spread * 2 + 1));
        if (spread == 0
            && (id.endsWith("/guide")
                || id.endsWith("/stats")
                || id.endsWith("/mining")
                || id.endsWith("/warehouse")
                || id.endsWith("/flight")
                || id.endsWith("/guardian"))) shot(c, id.replace(':', '-').replace('/', '-'));
        var original = c.screen;
        for (var link : links) {
          if (!link.get()
              || !c.screen.getClass().getSimpleName().equals("GuiBookEntry")
              || Boolean.TRUE.equals(invoke(field(c.screen, "entry"), "isLocked")))
            throw new IllegalStateException("Guide link did not open an available entry: " + id);
          checkedLinks++;
          c.setScreen(original);
        }
        var pages = (List<?>) invoke(entry, "getPages");
        if ((spread + 1) * 2 < pages.size()) {
          spread++;
          openEntry(c);
          return false;
        }
        if (++index < entries.size()) {
          entry = entries.get(index);
          spread = 0;
          openEntry(c);
          return false;
        }
        if (rendered != 177)
          throw new IllegalStateException("Expected 177 content pages, rendered " + rendered);
        System.out.println(
            "SLIMIC GUIDEBOOK LANGUAGE PASSED: "
                + LANGUAGES.get(language)
                + "; 55 entries; 177 rendered pages");
        if (++language < LANGUAGES.size()) {
          phase = 3;
          return false;
        }
        phase = 7;
        return false;
      }
      if (phase == 7) {
        var result =
            Map.of(
                "languages",
                LANGUAGES,
                "entries",
                55,
                "pages_per_language",
                177,
                "item_lookups",
                checkedLookups,
                "entry_links",
                checkedLinks,
                "localized_searches",
                checkedSearches,
                "display",
                Map.of(
                    "gui_scale", c.getWindow().getGuiScale(),
                    "width", c.getWindow().getWidth(),
                    "height", c.getWindow().getHeight()),
                "measurements",
                measurements,
                "problems",
                problems);
        Files.writeString(
            output(c).resolve("layout.json"),
            new GsonBuilder().setPrettyPrinting().create().toJson(result));
        if (!problems.isEmpty())
          throw new IllegalStateException("Guide layout issues: " + problems);
        System.out.println(
            "SLIMIC GUIDEBOOK CLIENT SMOKE PASSED: native opening, Creative tab, EN/KR/fallback"
                + " reload, every page and diagram");
        phase = 8;
        return true;
      }
      return false;
    } catch (ReflectiveOperationException | java.io.IOException e) {
      throw new IllegalStateException("Guidebook client validation failed", e);
    }
  }
}
