package io.github.meistermods.slimic.client;

import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.network.ModNetwork;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

public final class SlimeScreen extends AbstractContainerScreen<SlimeMenu> {
  private EditBox name;
  private final Map<SlimeStat, Button> upgrades = new EnumMap<>(SlimeStat.class);
  private final Map<Integer, Button> orders = new java.util.HashMap<>();

  public SlimeScreen(SlimeMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 368;
    imageHeight = 240;
    inventoryLabelX = 194;
    inventoryLabelY = 146;
  }

  @Override
  protected void init() {
    super.init();
    upgrades.clear();
    orders.clear();
    name =
        new EditBox(
            font, leftPos + 10, topPos + 25, 140, 18, Component.translatable("screen.slimic.name"));
    name.setMaxLength(32);
    name.setHint(Component.translatable("screen.slimic.name"));
    name.setValue(menu.slime().hasCustomName() ? menu.slime().getCustomName().getString() : "");
    addRenderableWidget(name);
    addRenderableWidget(
        Button.builder(Component.translatable("screen.slimic.save"), button -> saveName())
            .bounds(leftPos + 154, topPos + 25, 30, 18)
            .build());
    for (SlimeStat stat : SlimeStat.values()) {
      Button button =
          Button.builder(Component.literal("+"), clicked -> sendButton(stat.ordinal()))
              .bounds(leftPos + 166, topPos + 80 + stat.ordinal() * 14, 18, 13)
              .tooltip(Tooltip.create(Component.translatable("screen.slimic.upgrade_tooltip")))
              .build();
      upgrades.put(stat, addRenderableWidget(button));
    }
    addOrder(SlimeMenu.FOLLOW, "follow", 194);
    addOrder(SlimeMenu.WAIT, "wait", 250);
    if (menu.slime() instanceof MiningSlime) addOrder(SlimeMenu.WORK, "mine", 306);
  }

  private void addOrder(int id, String key, int x) {
    orders.put(
        id,
        addRenderableWidget(
            Button.builder(Component.translatable("screen.slimic." + key), button -> sendButton(id))
                .bounds(leftPos + x, topPos + 25, 50, 18)
                .build()));
  }

  private void sendButton(int id) {
    if (minecraft != null && minecraft.gameMode != null)
      minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
  }

  private void saveName() {
    ModNetwork.CHANNEL.sendToServer(new ModNetwork.RenameSlime(menu.containerId, name.getValue()));
  }

  @Override
  protected void containerTick() {
    super.containerTick();
    name.tick();
    upgrades.forEach(
        (stat, button) ->
            button.active =
                menu.data(SlimeMenu.POINTS) > 0
                    && menu.slime().getStatLevel(stat) < stat.maxLevel());
    orders.forEach((id, button) -> button.active = menu.data(SlimeMenu.MODE) != id);
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (name.isFocused() && key != GLFW.GLFW_KEY_ESCAPE) {
      if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
        saveName();
        return true;
      }
      return name.keyPressed(key, scan, modifiers);
    }
    return super.keyPressed(key, scan, modifiers);
  }

  @Override
  protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    int x = leftPos, y = topPos;
    graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xff181f26);
    graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + 21, 0xff304338);
    graphics.fill(x + 190, y + 45, x + 362, y + 237, 0xff293139);
    for (var slot : menu.slots) {
      if (!slot.isActive()) continue;
      graphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xff11171b);
      graphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xff586066);
    }
  }

  @Override
  protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    graphics.drawString(font, menu.slime().getDisplayName(), 10, 7, 0xffe9f6df, false);
    graphics.drawString(
        font,
        Component.translatable("screen.slimic.points", menu.data(SlimeMenu.POINTS)),
        10,
        51,
        0xffffd77e,
        false);
    graphics.drawString(
        font,
        Component.translatable(
            "screen.slimic.activity",
            menu.data(SlimeMenu.ACTIVITY),
            menu.data(SlimeMenu.NEXT_POINT)),
        10,
        65,
        0xffa3b7bc,
        false);
    for (SlimeStat stat : SlimeStat.values()) {
      int y = 83 + stat.ordinal() * 14;
      graphics.drawString(
          font, Component.translatable(stat.translationKey()), 10, y, 0xffe2e9e9, false);
      double value = menu.value(stat);
      String formatted =
          String.format(
              Locale.ROOT,
              stat == SlimeStat.MOVEMENT_SPEED
                  ? "%.3f"
                  : stat == SlimeStat.WORK_SPEED || stat == SlimeStat.REACH ? "%.2f" : "%.0f",
              value);
      graphics.drawString(font, formatted, 158 - font.width(formatted), y, 0xff9de080, false);
    }
    graphics.drawString(
        font,
        Component.translatable(
            "screen.slimic.health",
            menu.data(SlimeMenu.HEALTH) / 10.0F,
            (int) menu.value(SlimeStat.HEALTH)),
        216,
        49,
        0xffe5e9e9,
        false);
    graphics.drawString(
        font,
        Component.translatable(
            "screen.slimic.stamina",
            menu.data(SlimeMenu.STAMINA) / 10,
            (int) menu.value(SlimeStat.STAMINA)),
        216,
        62,
        0xffe5e9e9,
        false);
    if (menu.slime() instanceof MiningSlime) {
      graphics.drawString(
          font,
          Component.translatable("screen.slimic.bag", (int) menu.value(SlimeStat.CAPACITY)),
          194,
          77,
          0xffe5e9e9,
          false);
      String key =
          MiningSlime.Status.values()[
              Math.min(
                  MiningSlime.Status.values().length - 1, Math.max(0, menu.data(SlimeMenu.STATUS)))]
              .name()
              .toLowerCase(Locale.ROOT);
      graphics.drawWordWrap(
          font, Component.translatable("status.slimic." + key), 10, 216, 174, 0xffb4c9d1);
    } else {
      graphics.drawWordWrap(
          font, Component.translatable("screen.slimic.specialize"), 194, 88, 162, 0xffb4c9d1);
    }
    graphics.drawString(
        font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xffe2e9e9, false);
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    renderBackground(graphics);
    super.render(graphics, mouseX, mouseY, partialTick);
    renderTooltip(graphics, mouseX, mouseY);
  }
}
