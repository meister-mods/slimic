package io.github.meistermods.slimic.client;

import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.menu.CreativeSlimeTunerMenu;
import io.github.meistermods.slimic.network.ModNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

public final class CreativeSlimeTunerScreen
    extends AbstractContainerScreen<CreativeSlimeTunerMenu> {
  private static final ResourceLocation BACKGROUND = SlimeTextures.gui("creative_tuner");
  private final List<EditBox> fields = new ArrayList<>();
  private Button apply;
  private boolean loaded;

  public CreativeSlimeTunerScreen(
      CreativeSlimeTunerMenu menu, Inventory inventory, Component title) {
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
          Tooltip.create(
              Component.translatable(
                  "screen.slimic.recommended_range", stat.minTunedValue(), stat.maxTunedValue())));
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
    box.setMaxLength(64);
    fields.add(box);
    return box;
  }

  private void refresh() {
    for (SlimeStat stat : SlimeStat.values())
      fields.get(stat.ordinal()).setValue(Double.toString(menu.value(stat)));
    fields.get(9).setValue(Float.toString(menu.health()));
    fields.get(10).setValue(Float.toString(menu.stamina()));
    fields.get(11).setValue(Long.toString(menu.points()));
  }

  private void apply() {
    try {
      double[] values = new double[9];
      for (int i = 0; i < 9; i++) values[i] = Double.parseDouble(fields.get(i).getValue());
      float health = Float.parseFloat(fields.get(9).getValue());
      float stamina = Float.parseFloat(fields.get(10).getValue());
      long points = Long.parseLong(fields.get(11).getValue());
      if (!Float.isFinite(health)
          || !Float.isFinite(stamina)
          || health < 0
          || stamina < 0
          || points < 0) return;
      for (SlimeStat stat : SlimeStat.values())
        if (!stat.acceptsTunedValue(values[stat.ordinal()])) return;
      ModNetwork.CHANNEL.sendToServer(
          new ModNetwork.CreativeTuneSlime(menu.containerId, values, health, stamina, points));
    } catch (NumberFormatException ignored) {
    }
  }

  @Override
  protected void containerTick() {
    super.containerTick();
    fields.forEach(EditBox::tick);
    if (!loaded
        && menu.isSynchronized()
        && menu.slime() != null
        && menu.value(SlimeStat.HEALTH) > 0) {
      refresh();
      loaded = true;
    }
    apply.active = loaded && validInput();
  }

  private boolean validInput() {
    if (!loaded) return false;
    try {
      for (SlimeStat stat : SlimeStat.values()) {
        double value = Double.parseDouble(fields.get(stat.ordinal()).getValue());
        boolean valid = stat.acceptsTunedValue(value);
        fields
            .get(stat.ordinal())
            .setTextColor(
                !valid ? 0xffff7777 : stat.isRecommendedValue(value) ? 0xffe0e0e0 : 0xffffd77e);
        if (!valid) return false;
      }
      float health = Float.parseFloat(fields.get(9).getValue());
      float stamina = Float.parseFloat(fields.get(10).getValue());
      long points = Long.parseLong(fields.get(11).getValue());
      return Float.isFinite(health)
          && Float.isFinite(stamina)
          && health >= 0
          && stamina >= 0
          && points >= 0;
    } catch (NumberFormatException invalid) {
      return false;
    }
  }

  private boolean beyondRecommendations() {
    try {
      for (SlimeStat stat : SlimeStat.values())
        if (!stat.isRecommendedValue(Double.parseDouble(fields.get(stat.ordinal()).getValue())))
          return true;
      return Float.parseFloat(fields.get(9).getValue()) > SlimeStat.HEALTH.maxTunedValue()
          || Float.parseFloat(fields.get(10).getValue()) > SlimeStat.STAMINA.maxTunedValue()
          || Long.parseLong(fields.get(11).getValue()) > 32767;
    } catch (NumberFormatException invalid) {
      return false;
    }
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
    graphics.blit(
        BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
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
    String status =
        menu.result() == 4
            ? "screen.slimic.capacity_unrepresentable"
            : menu.result() == 3
                ? "screen.slimic.capacity_occupied"
                : !validInput() && loaded
                    ? "screen.slimic.invalid_tuning"
                    : menu.result() == 2
                        ? "screen.slimic.tuned_recommended_warning"
                        : beyondRecommendations()
                            ? "screen.slimic.recommended_warning"
                            : menu.result() == 1 ? "screen.slimic.tuned" : null;
    if (status != null)
      graphics.drawWordWrap(font, Component.translatable(status), 10, 208, 338, 0xffffd77e);
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    renderBackground(graphics);
    super.render(graphics, mouseX, mouseY, partialTick);
    renderTooltip(graphics, mouseX, mouseY);
  }
}
