package io.github.meistermods.slimic.gametest;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Measures full button labels with the actual client font and the native two-pixel inset. */
final class LocalizedWidgetChecks {
  private static final List<Map<String, Object>> measurements = new ArrayList<>();

  private LocalizedWidgetChecks() {}

  static void record(
      Minecraft client, Screen screen, String sample, int panelWidth, int panelHeight) {
    int left = (screen.width - panelWidth) / 2, top = (screen.height - panelHeight) / 2;
    for (var child : screen.children()) {
      if (!(child instanceof Button button) || !button.visible) continue;
      if (button.getX() < left
          || button.getY() < top
          || button.getX() + button.getWidth() > left + panelWidth
          || button.getY() + button.getHeight() > top + panelHeight)
        throw new IllegalStateException("Localized button leaves its panel: " + sample);
      String label = button.getMessage().getString();
      String key =
          button.getMessage().getContents() instanceof TranslatableContents text
              ? text.getKey()
              : "";
      int width = client.font.width(button.getMessage()), available = button.getWidth() - 4;
      if (label.contains(".slimic.") || label.contains("Format error:"))
        throw new IllegalStateException("Untranslated localized button: " + sample + "/" + key);
      measurements.add(
          Map.of(
              "sample", sample,
              "language", client.options.languageCode,
              "key", key,
              "label", label,
              "label_width", width,
              "available_width", available,
              "fits", width <= available));
    }
  }

  /**
   * Native buttons scroll inside their bounds; report long labels without treating them as clipped.
   */
  static void finish(Minecraft client, String category) {
    var samples =
        measurements.stream()
            .filter(row -> row.get("sample").toString().startsWith(category + "/"))
            .toList();
    var overflow = samples.stream().filter(row -> !Boolean.TRUE.equals(row.get("fits"))).toList();
    try {
      var output = client.gameDirectory.toPath().resolve("localized-widget-audit");
      Files.createDirectories(output);
      Files.writeString(
          output.resolve(category + ".json"),
          new GsonBuilder()
              .setPrettyPrinting()
              .disableHtmlEscaping()
              .create()
              .toJson(Map.of("samples", samples, "overflow", overflow)));
    } catch (java.io.IOException failure) {
      throw new IllegalStateException(failure);
    }
    if (!overflow.isEmpty())
      System.out.println(
          "SLIMIC LOCALIZED BUTTON SCROLLING: " + category + " labels=" + overflow.size());
  }
}
