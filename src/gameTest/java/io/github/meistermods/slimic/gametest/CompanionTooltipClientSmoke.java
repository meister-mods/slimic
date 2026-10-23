package io.github.meistermods.slimic.gametest;

import com.google.gson.GsonBuilder;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.ModEntities;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/** Localized native tooltips for fresh and stored companions, both fonts, and optional JEI. */
public final class CompanionTooltipClientSmoke {
  private static final List<String> LANGUAGES = List.of("en_us", "ko_kr", "fr_fr");
  private static final List<Map<String, Object>> report = new ArrayList<>();
  private static final List<ItemStack> samples = new ArrayList<>();
  private static int phase, language, mode, index, ticks, checks;
  private static CompletableFuture<Void> reload;
  private static boolean checkedInformation;

  private CompanionTooltipClientSmoke() {}

  private static String locale() {
    return LANGUAGES.get(language);
  }

  private static void prepare(Minecraft client) {
    samples.clear();
    for (var type : ModEntities.individualCompanionTypes()) {
      var item = ForgeRegistries.ITEMS.getValue(type.getId());
      samples.add(new ItemStack(item));
      var pet = (FriendlySlime) type.get().create(client.level);
      pet.tame(client.player);
      pet.setHealth(3.5F);
      pet.setWaiting(false);
      pet.setCustomName(Component.literal("Saved " + item.getDescription().getString()));
      var stored = FriendlySlimeItem.fromSlime(pet);
      if (!stored.is(item))
        throw new IllegalStateException("Recovery changed the profession item: " + type.getId());
      samples.add(stored);
    }
  }

  private static void verify(Minecraft client, ItemStack stack) {
    var own = stack.getItem().getDescriptionId() + ".tooltip";
    if (!I18n.exists(own)) throw new IllegalStateException("Missing profession help: " + own);
    boolean stored = stack.hasTag() && stack.getTag().contains("SlimeData");
    var before = stack.save(new CompoundTag());
    for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
      var lines = stack.getTooltipLines(client.player, flag);
      var plain = lines.stream().map(Component::getString).toList();
      if (plain.stream().filter(Component.translatable(own).getString()::equals).count() != 1)
        throw new IllegalStateException(
            "Wrong or duplicate profession description: " + own + plain);
      if (plain.contains(I18n.get("item.slimic.companion.summon_tooltip")) == stored
          || plain.contains(I18n.get("item.slimic.friendly_slime.stored_tooltip")) != stored)
        throw new IllegalStateException("Fresh/restored help mismatch " + own);
      if (plain.stream().anyMatch(s -> s.contains("Format error:") || s.contains("item.slimic.")))
        throw new IllegalStateException("Untranslated or malformed help " + own + plain);
      int width = client.getWindow().getGuiScaledWidth(),
          height = client.getWindow().getGuiScaledHeight();
      for (int mx : new int[] {8, width / 2, width - 8}) {
        var components =
            ForgeHooksClient.gatherTooltipComponents(
                stack, lines, stack.getTooltipImage(), mx, width, height, client.font);
        int w = components.stream().mapToInt(c -> c.getWidth(client.font)).max().orElse(0);
        int h = components.stream().mapToInt(c -> c.getHeight()).sum() + 2;
        var pos =
            DefaultTooltipPositioner.INSTANCE.positionTooltip(width, height, mx, height / 2, w, h);
        if (w > width - 8
            || h > height - 8
            || pos.x() < 0
            || pos.y() < 0
            || pos.x() + w > width
            || pos.y() + h > height)
          throw new IllegalStateException(
              "Tooltip outside screen " + locale() + " " + own + " " + w + "x" + h);
      }
      report.add(
          Map.of(
              "language",
              locale(),
              "unicode",
              mode == 1,
              "stored",
              stored,
              "advanced",
              flag.isAdvanced(),
              "item",
              ForgeRegistries.ITEMS.getKey(stack.getItem()).toString(),
              "lines",
              plain));
      checks++;
    }
    if (!stack.save(new CompoundTag()).equals(before))
      throw new IllegalStateException("Tooltip mutated companion data");
  }

  public static boolean tick(Minecraft client) {
    if (client.getOverlay() != null) return false;
    if (++ticks > 900)
      throw new IllegalStateException("Localized tooltip phase timed out " + phase);
    if (phase == 0) {
      if (!checkedInformation) {
        if (ModList.get().isLoaded("jei") && !JeiClientSmoke.companionInformationReady())
          return false;
        checkedInformation = true;
      }
      client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
      client.options.guiScale().set(3);
      client.options.forceUnicodeFont().set(mode == 1);
      org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1280, 960);
      client.resizeDisplay();
      client.options.languageCode = locale();
      client.getLanguageManager().setSelected(locale());
      reload = client.reloadResourcePacks();
      phase = 1;
      ticks = 0;
      return false;
    }
    if (phase == 1) {
      if (!reload.isDone()) return false;
      reload.join();
      prepare(client);
      index = 0;
      client.setScreen(new Sample());
      phase = 2;
      ticks = 0;
      return false;
    }
    if (ticks < 3) return false;
    var stack = samples.get(index);
    verify(client, stack);
    try {
      var directory = client.gameDirectory.toPath().resolve("companion-tooltips-audit");
      Files.createDirectories(directory);
      if (index == 26
          || index == 28
          || index == 30
          || index == 32
          || index == 34
          || index == 36
          || index == 38
          || index == 40
          || index == 42)
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
          image.writeToFile(
              directory.resolve(
                  locale()
                      + "-"
                      + mode
                      + "-"
                      + ForgeRegistries.ITEMS.getKey(stack.getItem()).getPath()
                      + ".png"));
        }
      ticks = 0;
      if (++index < samples.size()) return false;
      client.setScreen(null);
      System.out.println(
          "SLIMIC COMPANION TOOLTIPS LANGUAGE PASSED: " + locale() + " unicode=" + (mode == 1));
      if (++language < LANGUAGES.size()) {
        phase = 0;
        return false;
      }
      language = 0;
      if (++mode < 2) {
        phase = 0;
        return false;
      }
      Files.writeString(
          directory.resolve("tooltips.json"),
          new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report));
      if (checks != ModEntities.individualCompanionTypes().size() * 2 * 2 * 3 * 2)
        throw new IllegalStateException(
            "Expected every profession, stored state, font, language, and tooltip mode");
      System.out.println(
          "SLIMIC COMPANION TOOLTIP CLIENT SMOKE PASSED: "
              + checks
              + " fresh/stored NORMAL/ADVANCED tooltips;"
              + " EN/KR/fallback; default/Unicode fonts; native bounds and exact data; JEI="
              + ModList.get().isLoaded("jei"));
      return true;
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final class Sample extends Screen {
    Sample() {
      super(Component.literal("Companion tooltip verification"));
    }

    @Override
    public boolean isPauseScreen() {
      return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
      g.fill(0, 0, width, height, 0xff18232e);
      if (index >= samples.size()) return;
      var stack = samples.get(index);
      g.drawCenteredString(
          font,
          locale()
              + " / "
              + (mode == 1 ? "Unicode" : "Default")
              + " / "
              + stack.getHoverName().getString(),
          width / 2,
          15,
          0xffffff);
      g.renderItem(stack, width / 2 - 8, 45);
      g.renderTooltip(font, stack, width / 2, height / 2);
    }
  }
}
