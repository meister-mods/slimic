package io.github.meistermods.slimic.client;

import io.github.meistermods.slimic.menu.CommanderMenu;
import io.github.meistermods.slimic.network.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

public final class CommanderScreen extends AbstractContainerScreen<CommanderMenu> {
  private static final ResourceLocation BACKGROUND = SlimeTextures.gui("commander");
  private EditBox tag;
  private final List<Button> rows = new ArrayList<>(),
      shares = new ArrayList<>(),
      enemies = new ArrayList<>();
  private Button previous, next, assign, gather;
  private UUID selected;
  private CommanderPackets.Page lastPage;
  private static final UUID NONE = new UUID(0, 0);

  public CommanderScreen(CommanderMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 310;
    imageHeight = 235;
  }

  private Button button(String key, int x, int y, int w, Runnable action) {
    return addRenderableWidget(
        Button.builder(Component.translatable("screen.slimic.roster." + key), b -> action.run())
            .bounds(leftPos + x, topPos + y, w, 18)
            .build());
  }

  private void send(int action, int page, UUID target, String value) {
    ModNetwork.CHANNEL.sendToServer(
        new CommanderPackets.Action(
            menu.containerId, action, page, target == null ? NONE : target, value));
  }

  private void view(int action, int page) {
    selected = null;
    send(action, page, NONE, "");
  }

  @Override
  protected void init() {
    super.init();
    rows.clear();
    shares.clear();
    enemies.clear();
    lastPage = null;
    button("companions", 10, 25, 80, () -> view(CommanderMenu.COMPANIONS, 0));
    button("players", 95, 25, 80, () -> view(CommanderMenu.PLAYERS, 0));
    previous =
        button(
            "previous",
            230,
            25,
            33,
            () -> {
              var p = menu.snapshot();
              if (p != null)
                view(p.players() ? CommanderMenu.PLAYERS : CommanderMenu.COMPANIONS, p.page() - 1);
            });
    next =
        button(
            "next",
            268,
            25,
            33,
            () -> {
              var p = menu.snapshot();
              if (p != null)
                view(p.players() ? CommanderMenu.PLAYERS : CommanderMenu.COMPANIONS, p.page() + 1);
            });
    for (int i = 0; i < CommanderMenu.PAGE_SIZE; i++) {
      final int row = i;
      rows.add(
          button(
              "select",
              10,
              50 + i * 23,
              290,
              () -> {
                var p = menu.snapshot();
                if (p != null && !p.players() && row < p.rows().size()) {
                  selected = p.rows().get(row).id();
                  tag.setValue(p.rows().get(row).tag());
                }
              }));
      shares.add(button("share", 138, 51 + i * 23, 77, () -> relation(row, CommanderMenu.SHARE)));
      enemies.add(button("enemy", 219, 51 + i * 23, 80, () -> relation(row, CommanderMenu.ENEMY)));
    }
    tag =
        addRenderableWidget(
            new EditBox(
                font,
                leftPos + 10,
                topPos + 195,
                118,
                18,
                Component.translatable("screen.slimic.roster.tag")));
    tag.setMaxLength(24);
    tag.setHint(Component.translatable("screen.slimic.roster.tag"));
    tag.setTooltip(Tooltip.create(Component.translatable("screen.slimic.roster.tag_help")));
    assign =
        button(
            "assign", 134, 195, 79, () -> send(CommanderMenu.SET_TAG, 0, selected, tag.getValue()));
    gather =
        button("gather", 219, 195, 80, () -> send(CommanderMenu.GATHER, 0, NONE, tag.getValue()));
    gather.setTooltip(Tooltip.create(Component.translatable("screen.slimic.roster.gather_help")));
  }

  private void relation(int row, int action) {
    var p = menu.snapshot();
    if (p != null && p.players() && row < p.rows().size())
      send(action, 0, p.rows().get(row).id(), "");
  }

  @Override
  protected void containerTick() {
    super.containerTick();
    tag.tick();
    var p = menu.snapshot();
    previous.active = p != null && p.page() > 0;
    next.active = p != null && p.page() + 1 < p.pages();
    if (p != lastPage && p != null) {
      if (lastPage != null && (lastPage.players() != p.players() || lastPage.page() != p.page()))
        selected = null;
      lastPage = p;
    }
    assign.active = p != null && !p.players() && selected != null;
    gather.active = p != null && !p.players() && p.stamina() >= 25;
    tag.visible = p == null || !p.players();
    assign.visible = tag.visible;
    gather.visible = tag.visible;
    for (int i = 0; i < rows.size(); i++) {
      boolean visible = p != null && i < p.rows().size();
      rows.get(i).setHeight(23);
      rows.get(i).visible = visible;
      rows.get(i).active = visible && !p.players();
      rows.get(i).setMessage(Component.empty());
      shares.get(i).visible = enemies.get(i).visible = visible && p.players();
      shares.get(i).active = visible && p.primaryOwner();
      enemies.get(i).active = visible && p.primaryOwner() && !p.rows().get(i).shared();
      if (visible && p.players()) {
        shares
            .get(i)
            .setMessage(
                Component.translatable(
                    "screen.slimic.roster." + (p.rows().get(i).shared() ? "unshare" : "share")));
        enemies
            .get(i)
            .setMessage(
                Component.translatable(
                    "screen.slimic.roster." + (p.rows().get(i).enemy() ? "neutral" : "enemy")));
      }
    }
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (tag.isFocused() && key != GLFW.GLFW_KEY_ESCAPE) return tag.keyPressed(key, scan, modifiers);
    return super.keyPressed(key, scan, modifiers);
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
    g.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int mx, int my) {
    g.drawString(font, title, 10, 7, 0xffedf4e8, false);
    var p = menu.snapshot();
    if (p == null) {
      g.drawString(
          font, Component.translatable("screen.slimic.roster.loading"), 10, 54, 0xffffffff, false);
      return;
    }
    g.drawString(
        font,
        Component.translatable("screen.slimic.roster.page", p.page() + 1, p.pages()),
        180,
        30,
        0xffdddddd,
        false);
    for (int i = 0; i < p.rows().size(); i++) {
      var row = p.rows().get(i);
      int y = 52 + i * 23;
      int width = p.players() ? 115 : 270;
      if (row.id().equals(selected)) g.fill(12, y - 1, 299, y + 21, 0x445ee56a);
      g.drawString(
          font, font.plainSubstrByWidth(row.name().getString(), width), 15, y, 0xffffffff, false);
      g.drawString(
          font,
          font.plainSubstrByWidth(row.detail().getString(), width),
          15,
          y + 11,
          0xffc5d2bf,
          false);
    }
    g.drawString(
        font,
        Component.translatable(
            "screen.slimic.roster.energy",
            (int) p.stamina(),
            (int) p.maximumStamina(),
            p.gathered(),
            p.requested()),
        10,
        220,
        0xffe3dc9b,
        false);
    if (p.players() && !p.primaryOwner())
      g.drawString(
          font,
          Component.translatable("screen.slimic.roster.primary_only"),
          10,
          198,
          0xffe3dc9b,
          false);
  }

  @Override
  public void render(GuiGraphics g, int x, int y, float partial) {
    renderBackground(g);
    super.render(g, x, y, partial);
    renderTooltip(g, x, y);
    var p = menu.snapshot();
    if (p == null || p.players()) return;
    int mx = x - leftPos, my = y - topPos;
    if (mx < 10 || mx >= 300) return;
    for (int i = 0; i < p.rows().size(); i++) {
      int rowY = 50 + i * 23;
      if (my < rowY || my >= rowY + 23) continue;
      var row = p.rows().get(i);
      g.renderTooltip(
          font,
          List.of(
              row.name(),
              row.detail(),
              row.position(),
              Component.translatable("screen.slimic.roster.tag_value", row.tag())),
          Optional.empty(),
          x,
          y);
      break;
    }
  }
}
