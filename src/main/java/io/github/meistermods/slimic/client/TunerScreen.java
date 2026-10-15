package io.github.meistermods.slimic.client;

import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.menu.TunerMenu;
import io.github.meistermods.slimic.network.ModNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

public final class TunerScreen extends AbstractContainerScreen<TunerMenu> {
  private final List<EditBox> fields = new ArrayList<>();
  private Button apply;
  private boolean loaded;

  public TunerScreen(TunerMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 360;
    imageHeight = 230;
  }

  @Override
  protected void init() {
    super.init();
    fields.clear();
    for (SlimeStat stat : SlimeStat.values()) {
      EditBox box = field(110, 38 + stat.ordinal() * 18, stat.translationKey());
      box.setTooltip(
          Tooltip.create(Component.literal(stat.minTunedValue() + " – " + stat.maxTunedValue())));
    }
    field(280, 38, "screen.slimic.current_health");
    field(280, 56, "screen.slimic.current_stamina");
    field(280, 74, "screen.slimic.points_label");
    apply =
        addRenderableWidget(
            Button.builder(Component.translatable("screen.slimic.apply"), button -> apply())
                .bounds(leftPos + 192, topPos + 184, 72, 20)
                .build());
    addRenderableWidget(
        Button.builder(Component.translatable("screen.slimic.refresh"), button -> refresh())
            .bounds(leftPos + 274, topPos + 184, 72, 20)
            .build());
    loaded = false;
  }

  private EditBox field(int x, int y, String key) {
    EditBox box =
        addRenderableWidget(
            new EditBox(font, leftPos + x, topPos + y, 64, 16, Component.translatable(key)));
    box.setMaxLength(16);
    fields.add(box);
    return box;
  }

  private void refresh() {
    for (SlimeStat stat : SlimeStat.values())
      fields.get(stat.ordinal()).setValue(String.format(Locale.ROOT, "%.3f", menu.value(stat)));
    fields.get(9).setValue(Float.toString(menu.health()));
    fields.get(10).setValue(Float.toString(menu.stamina()));
    fields.get(11).setValue(Integer.toString(menu.points()));
  }

  private void apply() {
    try {
      double[] values = new double[9];
      for (int i = 0; i < 9; i++) values[i] = Double.parseDouble(fields.get(i).getValue());
      float health = Float.parseFloat(fields.get(9).getValue());
      float stamina = Float.parseFloat(fields.get(10).getValue());
      int points = Integer.parseInt(fields.get(11).getValue());
      if (!Float.isFinite(health)
          || !Float.isFinite(stamina)
          || health < 0
          || stamina < 0
          || health > 1024
          || stamina > values[6]
          || points < 0
          || points > 32767) return;
      for (SlimeStat stat : SlimeStat.values())
        if (!stat.acceptsTunedValue(values[stat.ordinal()])) return;
      ModNetwork.CHANNEL.sendToServer(
          new ModNetwork.TuneSlime(menu.containerId, values, health, stamina, points));
    } catch (NumberFormatException ignored) {
    }
  }

  @Override
  protected void containerTick() {
    super.containerTick();
    fields.forEach(EditBox::tick);
    if (!loaded && menu.slime() != null && menu.value(SlimeStat.HEALTH) > 0) {
      refresh();
      loaded = true;
    }
    apply.active = loaded;
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (key != GLFW.GLFW_KEY_ESCAPE)
      for (EditBox field : fields)
        if (field.isFocused()) return field.keyPressed(key, scan, modifiers);
    return super.keyPressed(key, scan, modifiers);
  }

  @Override
  protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff181f26);
    graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 20, 0xff304338);
  }

  @Override
  protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    graphics.drawString(font, title, 10, 6, 0xffe9f6df, false);
    graphics.drawString(
        font,
        menu.slime() == null
            ? Component.translatable("screen.slimic.no_target")
            : menu.slime().getDisplayName(),
        10,
        23,
        0xffe2e9e9,
        false);
    for (SlimeStat stat : SlimeStat.values())
      graphics.drawString(
          font,
          Component.translatable(stat.translationKey()),
          10,
          42 + stat.ordinal() * 18,
          0xffe2e9e9,
          false);
    for (int i = 0; i < 3; i++)
      graphics.drawString(
          font,
          Component.translatable(
              new String[] {
                    "screen.slimic.current_health",
                    "screen.slimic.current_stamina",
                    "screen.slimic.points_label"
                  }
                  [i]),
          192,
          42 + i * 18,
          0xffe2e9e9,
          false);
    graphics.drawWordWrap(
        font, Component.translatable("screen.slimic.tuner_help"), 192, 106, 156, 0xffb4c9d1);
    if (menu.result() != 0)
      graphics.drawString(
          font,
          Component.translatable(
              menu.result() == 1 ? "screen.slimic.tuned" : "screen.slimic.capacity_occupied"),
          10,
          212,
          0xffffd77e,
          false);
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    renderBackground(graphics);
    super.render(graphics, mouseX, mouseY, partialTick);
    renderTooltip(graphics, mouseX, mouseY);
  }
}
