package io.github.meistermods.slimic.client;

import io.github.meistermods.slimic.menu.RoyalSynthesisMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class RoyalSynthesisScreen extends AbstractContainerScreen<RoyalSynthesisMenu> {
  private Button confirm, recover;

  public RoyalSynthesisScreen(RoyalSynthesisMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 320;
    imageHeight = 235;
    inventoryLabelX = 79;
    inventoryLabelY = 142;
  }

  @Override
  protected void init() {
    super.init();
    confirm =
        addRenderableWidget(
            Button.builder(
                    Component.translatable("screen.slimic.synthesis.confirm"),
                    b -> send(RoyalSynthesisMenu.CONFIRM))
                .bounds(leftPos + 170, topPos + 119, 136, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.slimic.synthesis.excludes")))
                .build());
    recover =
        addRenderableWidget(
            Button.builder(
                    Component.translatable("screen.slimic.synthesis.recover"),
                    b -> send(RoyalSynthesisMenu.RECOVER))
                .bounds(leftPos + 14, topPos + 119, 150, 20)
                .build());
  }

  private void send(int button) {
    if (minecraft != null && minecraft.gameMode != null)
      minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
  }

  @Override
  protected void containerTick() {
    super.containerTick();
    confirm.active = menu.ready();
    recover.active = menu.pending();
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int x, int y) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff23322f);
    g.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xff34453e);
    for (var slot : menu.slots) {
      g.fill(
          leftPos + slot.x - 1,
          topPos + slot.y - 1,
          leftPos + slot.x + 17,
          topPos + slot.y + 17,
          0xff131f1b);
      g.fill(
          leftPos + slot.x,
          topPos + slot.y,
          leftPos + slot.x + 16,
          topPos + slot.y + 16,
          0xff879b8d);
    }
    var family =
        io.github.meistermods.slimic.entity.SynthesisFamily.match(
            menu.getSlot(0).getItem(), menu.getSlot(1).getItem());
    if (family != null)
      g.renderItem(family.resultItem().getDefaultInstance(), leftPos + 268, topPos + 48);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int x, int y) {
    g.drawString(font, title, 12, 8, 0xfff0ead1, false);
    String[] names = {"normal", "ancient", "slime_balls", "fragments"};
    for (int i = 0; i < 4; i++) {
      var label = Component.translatable("screen.slimic.synthesis." + names[i]);
      g.drawString(font, label, 30 + i * 48 - font.width(label) / 2, 33, 0xffd5dfd2, false);
    }
    g.drawString(font, Component.literal("→"), 231, 53, 0xffffdc89, false);
    g.drawString(
        font,
        Component.translatable("screen.slimic.synthesis.total", compact(menu.totalPoints())),
        14,
        73,
        0xffffdc89,
        false);
    g.drawString(
        font,
        Component.translatable(
            "screen.slimic.synthesis.points",
            compact(menu.normalPoints()),
            compact(menu.ancientPoints()),
            compact(menu.totalPoints())),
        14,
        85,
        0xffd5dfd2,
        false);
    var lines = font.split(menu.status(), 292);
    for (int line = 0; line < Math.min(2, lines.size()); line++)
      g.drawString(font, lines.get(line), 14, 97 + line * 9, 0xffe6e9e1, false);
    g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xffd5dfd2, false);
  }

  @Override
  public void render(GuiGraphics g, int x, int y, float partial) {
    renderBackground(g);
    super.render(g, x, y, partial);
    renderTooltip(g, x, y);
    if (x >= leftPos + 14 && x < leftPos + 306 && y >= topPos + 73 && y < topPos + 95)
      g.renderTooltip(
          font,
          Component.translatable(
              "screen.slimic.synthesis.points",
              menu.normalPoints(),
              menu.ancientPoints(),
              menu.totalPoints()),
          x,
          y);
    else if (x >= leftPos + 14 && x < leftPos + 306 && y >= topPos + 96 && y < topPos + 116)
      g.renderTooltip(font, menu.status(), x, y);
  }

  private static String compact(long points) {
    return points < 1_000_000_000L
        ? Long.toString(points)
        : String.format(java.util.Locale.ROOT, "%.2e", (double) points);
  }
}
