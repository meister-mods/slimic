package io.github.meistermods.slimic.client;

import io.github.meistermods.slimic.entity.AbundanceSlime;
import io.github.meistermods.slimic.entity.BagSlime;
import io.github.meistermods.slimic.entity.CleaningSlime;
import io.github.meistermods.slimic.entity.CombatSlime;
import io.github.meistermods.slimic.entity.CombatTarget;
import io.github.meistermods.slimic.entity.FarmingSlime;
import io.github.meistermods.slimic.entity.FieldSlime;
import io.github.meistermods.slimic.entity.FishingSlime;
import io.github.meistermods.slimic.entity.GuardianAngelSlime;
import io.github.meistermods.slimic.entity.HealingSlime;
import io.github.meistermods.slimic.entity.LoggingSlime;
import io.github.meistermods.slimic.entity.MeowSlime;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.SaddleSlime;
import io.github.meistermods.slimic.entity.SignalingSlime;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.entity.SoggySlime;
import io.github.meistermods.slimic.entity.TransportSlime;
import io.github.meistermods.slimic.entity.TunnelSlime;
import io.github.meistermods.slimic.entity.WarehouseSlime;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

public final class SlimeScreen extends AbstractContainerScreen<SlimeMenu> {
  private static final ResourceLocation BACKGROUND = SlimeTextures.gui("slime");
  private static final ResourceLocation SLOTS = SlimeTextures.gui("slots");
  private EditBox name, tunnelSize;
  private Button applyTunnelSize, tunnelPageButton;
  private boolean tunnelSizeInitialized;
  private final Map<SlimeStat, Button> upgrades = new EnumMap<>(SlimeStat.class);
  private final Map<Integer, Button> orders = new java.util.HashMap<>();
  private Button previousPage;
  private Button nextPage;
  private Button sortBag;
  private String pageCounter;
  private Button directionButton;
  private Button lightButton;
  private final java.util.List<Button> policies = new java.util.ArrayList<>();
  private final java.util.List<Button> ores = new java.util.ArrayList<>();

  public SlimeScreen(SlimeMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 368;
    imageHeight = 240;
    inventoryLabelX = SlimeMenu.SLOTS_X;
    inventoryLabelY = SlimeMenu.INVENTORY_LABEL_Y;
  }

  private final java.util.List<Button> filters = new java.util.ArrayList<>();
  private Button targetPageButton;

  @Override
  protected void init() {
    super.init();
    directionButton = null;
    tunnelSize = null;
    applyTunnelSize = tunnelPageButton = null;
    tunnelSizeInitialized = false;
    previousPage = nextPage = sortBag = null;
    pageCounter = null;
    lightButton = null;
    policies.clear();
    ores.clear();
    upgrades.clear();
    filters.clear();
    targetPageButton = null;
    orders.clear();
    if (menu.slime() instanceof io.github.meistermods.slimic.entity.NextGenerationMiningSlime) {
      addRenderableWidget(
          Button.builder(
                  Component.translatable("screen.slimic.policy"),
                  b -> sendButton(SlimeMenu.TARGET_PAGE_BUTTON))
              .bounds(leftPos + 282, topPos + 73, 76, 14)
              .build());
      var values = io.github.meistermods.slimic.entity.NextGenerationMiningSlime.Policy.values();
      for (int i = 0; i < values.length; i++) {
        final int policy = i;
        String key = values[i].name().toLowerCase(Locale.ROOT);
        policies.add(
            addRenderableWidget(
                Button.builder(
                        Component.translatable("policy.slimic." + key + ".short"),
                        b -> sendButton(SlimeMenu.POLICY_BUTTON + policy))
                    .bounds(leftPos + 196 + i * 54, topPos + 90, 52, 15)
                    .tooltip(Tooltip.create(Component.translatable("policy.slimic." + key)))
                    .build()));
      }
      var types = io.github.meistermods.slimic.entity.NextGenerationMiningSlime.ORES;
      for (int i = 0; i < types.size(); i++) {
        final int ore = i;
        ores.add(
            addRenderableWidget(
                Button.builder(
                        Component.translatable("ore.slimic." + types.get(i)),
                        b -> sendButton(SlimeMenu.ORE_BUTTON + ore))
                    .bounds(leftPos + 196 + i % 3 * 54, topPos + 108 + i / 3 * 11, 52, 10)
                    .build()));
      }
    }
    if (menu.slime() instanceof io.github.meistermods.slimic.entity.NextGenerationTunnelSlime) {
      tunnelPageButton =
          addRenderableWidget(
              Button.builder(
                      Component.translatable("screen.slimic.tunnel_size"),
                      b -> sendButton(SlimeMenu.TARGET_PAGE_BUTTON))
                  .bounds(leftPos + 196, topPos + 73, 82, 14)
                  .build());
      tunnelSize =
          new EditBox(
              font,
              leftPos + 201,
              topPos + 106,
              76,
              18,
              Component.translatable("screen.slimic.tunnel_size"));
      tunnelSize.setMaxLength(10);
      tunnelSize.setFilter(value -> value.matches("[0-9]*"));
      tunnelSize.setHint(Component.literal("n"));
      addRenderableWidget(tunnelSize);
      applyTunnelSize =
          addRenderableWidget(
              Button.builder(Component.translatable("screen.slimic.apply"), b -> applyTunnelSize())
                  .bounds(leftPos + 284, topPos + 106, 70, 18)
                  .tooltip(Tooltip.create(Component.translatable("screen.slimic.tunnel_size.help")))
                  .build());
    }
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
      var upgradeHelp = Component.translatable("screen.slimic.upgrade_tooltip");
      if (!stat.isUsedBy(menu.slime()))
        upgradeHelp
            .append("\n")
            .append(Component.translatable("screen.slimic.specialization_stat"));
      Button button =
          Button.builder(Component.literal("+"), clicked -> sendButton(stat.ordinal()))
              .bounds(leftPos + 166, topPos + 80 + stat.ordinal() * 14, 18, 13)
              .tooltip(Tooltip.create(upgradeHelp))
              .build();
      upgrades.put(stat, addRenderableWidget(button));
    }
    addOrder(SlimeMenu.FOLLOW, "follow", SlimeMenu.SLOTS_X);
    addOrder(SlimeMenu.WAIT, "wait", 252);
    if (menu.slime() instanceof io.github.meistermods.slimic.entity.GlowSlime)
      lightButton =
          addRenderableWidget(
              Button.builder(
                      Component.translatable("screen.slimic.light"),
                      b -> sendButton(SlimeMenu.ACTIVE))
                  .bounds(leftPos + SlimeMenu.SLOTS_X, topPos + 89, 100, 18)
                  .build());
    if (menu.slime() instanceof io.github.meistermods.slimic.entity.CommandSlime)
      addRenderableWidget(
          Button.builder(
                  Component.translatable("screen.slimic.roster.open"),
                  b -> sendButton(SlimeMenu.ROSTER))
              .bounds(leftPos + SlimeMenu.SLOTS_X, topPos + 89, 150, 18)
              .build());
    if (menu.slime() instanceof io.github.meistermods.slimic.entity.AttentionSlime)
      addOrder(SlimeMenu.ROAM, "roam", 308);
    if (menu.slime() instanceof MeowSlime cat) {
      addOrder(SlimeMenu.ROAM, "roam", 308);
      addRenderableWidget(
          Button.builder(
                  Component.translatable("screen.slimic.active"), b -> sendButton(SlimeMenu.ACTIVE))
              .bounds(leftPos + SlimeMenu.SLOTS_X, topPos + 89, 100, 18)
              .tooltip(Tooltip.create(Component.translatable("screen.slimic.meow_help")))
              .build());
    }
    if (menu.slime() instanceof TunnelSlime)
      directionButton =
          addRenderableWidget(
              Button.builder(
                      Component.translatable("screen.slimic.direction"),
                      b -> sendButton(SlimeMenu.DIRECTION))
                  .bounds(leftPos + 282, topPos + 73, 76, 14)
                  .tooltip(Tooltip.create(Component.translatable("screen.slimic.lining_help")))
                  .build());
    if (menu.slime() instanceof FishingSlime) addOrder(SlimeMenu.WORK, "fish", 308);
    if (menu.slime() instanceof SoggySlime) addOrder(SlimeMenu.ROAM, "roam", 308);
    if (menu.slime() instanceof CombatSlime) {
      addOrder(SlimeMenu.WORK, "fight", 308);
      targetPageButton =
          addRenderableWidget(
              Button.builder(
                      Component.translatable("screen.slimic.targets"),
                      button -> sendButton(SlimeMenu.TARGET_PAGE_BUTTON))
                  .bounds(leftPos + 296, topPos + 73, 62, 14)
                  .build());
      for (CombatTarget kind : CombatTarget.values()) {
        Button button =
            Button.builder(
                    Component.empty(),
                    pressed -> sendButton(SlimeMenu.TARGET_TOGGLE + kind.ordinal()))
                .bounds(
                    leftPos + SlimeMenu.SLOTS_X + kind.ordinal() % 2 * 82,
                    topPos + 89 + kind.ordinal() / 2 * 18,
                    80,
                    16)
                .build();
        button.visible = false;
        filters.add(addRenderableWidget(button));
      }
    }
    if (menu.slime() instanceof SaddleSlime)
      addRenderableWidget(
          Button.builder(
                  Component.translatable("screen.slimic.ride"), b -> sendButton(SlimeMenu.RIDE))
              .bounds(leftPos + 308, topPos + 25, 50, 18)
              .build());
    if (menu.slime() instanceof HealingSlime)
      addOrder(SlimeMenu.WORK, menu.slime() instanceof AbundanceSlime ? "abundance" : "heal", 308);
    if (menu.slime() instanceof FarmingSlime) addOrder(SlimeMenu.WORK, "farm", 308);
    if (menu.slime() instanceof LoggingSlime) addOrder(SlimeMenu.WORK, "log", 308);
    if (menu.slime() instanceof CleaningSlime) addOrder(SlimeMenu.WORK, "clean", 308);
    if (menu.slime() instanceof MiningSlime)
      addOrder(SlimeMenu.WORK, menu.slime() instanceof TunnelSlime ? "tunnel" : "mine", 308);
    if (menu.slime() instanceof SignalingSlime) {
      addOrder(SlimeMenu.WORK, "monitor", 308);
      addRenderableWidget(
          Button.builder(
                  Component.translatable("screen.slimic.fire_signal"),
                  b -> sendButton(SlimeMenu.FIRE_SIGNAL))
              .bounds(leftPos + 222, topPos + 137, 106, 16)
              .build());
      addRenderableWidget(
          Button.builder(Component.literal("-"), button -> sendButton(SlimeMenu.DELAY_DOWN))
              .bounds(leftPos + SlimeMenu.SLOTS_X, topPos + 112, 24, 18)
              .build());
      addRenderableWidget(
          Button.builder(Component.literal("+"), button -> sendButton(SlimeMenu.DELAY_UP))
              .bounds(leftPos + 334, topPos + 112, 24, 18)
              .build());
    }
    if (menu.slime() instanceof TransportSlime)
      addOrder(SlimeMenu.WORK, menu.slime() instanceof WarehouseSlime ? "store" : "transport", 308);
    if (menu.slime() instanceof BagSlime) {
      sortBag =
          addRenderableWidget(
              Button.builder(
                      Component.translatable("screen.slimic.sort_bag"),
                      button -> sendButton(SlimeMenu.SORT_BAG))
                  .bounds(leftPos + 254, topPos + SlimeMenu.INVENTORY_LABEL_Y - 3, 32, 12)
                  .tooltip(Tooltip.create(Component.translatable("screen.slimic.sort_bag.tooltip")))
                  .build());
      previousPage =
          addRenderableWidget(
              Button.builder(Component.literal("<"), button -> sendButton(SlimeMenu.PREVIOUS_PAGE))
                  .bounds(leftPos + 288, topPos + SlimeMenu.INVENTORY_LABEL_Y - 3, 12, 12)
                  .tooltip(Tooltip.create(Component.translatable("screen.slimic.previous_page")))
                  .build());
      nextPage =
          addRenderableWidget(
              Button.builder(Component.literal(">"), button -> sendButton(SlimeMenu.NEXT_PAGE))
                  .bounds(leftPos + 346, topPos + SlimeMenu.INVENTORY_LABEL_Y - 3, 12, 12)
                  .tooltip(Tooltip.create(Component.translatable("screen.slimic.next_page")))
                  .build());
    }
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

  private int requestedTunnelSize() {
    try {
      return Integer.parseInt(tunnelSize.getValue());
    } catch (NumberFormatException failure) {
      return 0;
    }
  }

  private void applyTunnelSize() {
    if (menu.slime() instanceof io.github.meistermods.slimic.entity.NextGenerationTunnelSlime tunnel
        && requestedTunnelSize() >= 1
        && requestedTunnelSize() <= tunnel.maximumTunnelSize())
      ModNetwork.CHANNEL.sendToServer(
          new ModNetwork.TunnelSize(menu.containerId, requestedTunnelSize()));
  }

  private void saveName() {
    ModNetwork.CHANNEL.sendToServer(new ModNetwork.RenameSlime(menu.containerId, name.getValue()));
  }

  @Override
  protected void containerTick() {
    super.containerTick();
    if (tunnelSize != null
        && menu.slime()
            instanceof io.github.meistermods.slimic.entity.NextGenerationTunnelSlime tunnel) {
      if (!tunnelSizeInitialized && menu.isSynchronized()) {
        tunnelSize.setValue(Integer.toString(menu.data(SlimeMenu.TUNNEL_SIZE)));
        tunnelSizeInitialized = true;
      }
      tunnelSize.visible = applyTunnelSize.visible = menu.isPolicyEditor();
      tunnelPageButton.setMessage(
          Component.translatable(
              menu.isPolicyEditor() ? "screen.slimic.bag.short" : "screen.slimic.tunnel_size"));
      int size = requestedTunnelSize();
      applyTunnelSize.active =
          menu.isSynchronized()
              && size >= 1
              && size <= tunnel.maximumTunnelSize()
              && size != menu.data(SlimeMenu.TUNNEL_SIZE);
    }
    for (int i = 0; i < policies.size(); i++) {
      policies.get(i).visible = menu.isPolicyEditor();
      policies.get(i).active = menu.isSynchronized() && menu.data(SlimeMenu.POLICY) != i;
    }
    for (int i = 0; i < ores.size(); i++) {
      var button = ores.get(i);
      button.visible = menu.isPolicyEditor();
      button.active = menu.isSynchronized() && menu.data(SlimeMenu.POLICY) == 2;
      button.setMessage(
          Component.literal((menu.data(SlimeMenu.ORE_MASK) & 1 << i) != 0 ? "✓ " : "")
              .append(
                  Component.translatable(
                      "ore.slimic."
                          + io.github.meistermods.slimic.entity.NextGenerationMiningSlime.ORES.get(
                              i))));
    }
    if (!menu.slime().isAlive() || menu.slime().isRemoved()) {
      onClose();
      return;
    }
    if (!menu.isSynchronized()) return;
    name.tick();
    if (lightButton != null
        && menu.slime() instanceof io.github.meistermods.slimic.entity.GlowSlime glow)
      lightButton.setMessage(
          Component.translatable("screen.slimic.light_" + (glow.isActive() ? "on" : "off")));
    upgrades.forEach(
        (stat, button) ->
            button.active =
                menu.data(SlimeMenu.POINTS) > 0
                    && menu.slime().getStatLevel(stat) < stat.maxLevel());
    orders.forEach((id, button) -> button.active = menu.data(SlimeMenu.MODE) != id);
    if (targetPageButton != null) {
      boolean open = menu.data(SlimeMenu.TARGET_PAGE) == 1;
      targetPageButton.setMessage(
          Component.translatable(open ? "screen.slimic.bag_tab" : "screen.slimic.targets"));
      for (CombatTarget kind : CombatTarget.values()) {
        Button button = filters.get(kind.ordinal());
        button.visible = open;
        boolean on = (menu.data(SlimeMenu.FILTERS) & (1 << kind.ordinal())) != 0;
        button.setMessage(
            Component.translatable(
                on ? "screen.slimic.target_enabled" : "screen.slimic.target_disabled",
                Component.translatable("target.slimic." + kind.key())));
      }
    }
    if (directionButton != null && menu.slime() instanceof TunnelSlime tunnel)
      directionButton.setMessage(
          Component.translatable("direction.slimic." + tunnel.getTunnelDirection().getName()));
    if (sortBag != null) {
      sortBag.visible = menu.isBagVisible();
      sortBag.active = menu.isSynchronized();
    }
    if (previousPage != null) {
      previousPage.visible = nextPage.visible = menu.isBagVisible() && menu.bagPages() > 1;
      previousPage.active = menu.data(SlimeMenu.BAG_PAGE) > 0;
      nextPage.active = menu.data(SlimeMenu.BAG_PAGE) + 1 < menu.bagPages();
      String counter = (menu.data(SlimeMenu.BAG_PAGE) + 1) + "/" + menu.bagPages();
      if (!counter.equals(pageCounter)) {
        pageCounter = counter;
        previousPage.setTooltip(
            Tooltip.create(
                Component.translatable("screen.slimic.previous_page")
                    .append("\n")
                    .append(counter)));
        nextPage.setTooltip(
            Tooltip.create(
                Component.translatable("screen.slimic.next_page").append("\n").append(counter)));
      }
    }
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (tunnelSize != null && tunnelSize.isFocused() && key != GLFW.GLFW_KEY_ESCAPE) {
      if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
        applyTunnelSize();
        return true;
      }
      return tunnelSize.keyPressed(key, scan, modifiers);
    }
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
    graphics.blit(BACKGROUND, x, y, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
    if (menu.isBagVisible())
      for (int row = 0; row < SlimeMenu.BAG_ROWS; row++)
        for (int column = 0; column < SlimeMenu.SLOT_COLUMNS; column++) {
          int cellX = x + SlimeMenu.SLOTS_X + column * SlimeMenu.SLOT_SPACING;
          int cellY = y + SlimeMenu.BAG_Y + row * SlimeMenu.SLOT_SPACING;
          graphics.blit(SLOTS, cellX - 1, cellY - 1, 18, 0, 18, 18, 36, 18);
        }
    for (var slot : menu.slots) {
      if (!slot.isActive()) continue;
      graphics.blit(SLOTS, x + slot.x - 1, y + slot.y - 1, 0, 0, 18, 18, 36, 18);
    }
  }

  private static String formatNumber(double value, String pattern) {
    return String.format(Locale.ROOT, Math.abs(value) >= 1000000 ? "%.2e" : pattern, value);
  }

  private void drawVital(GuiGraphics graphics, Component text, int x, int y) {
    float scale = Math.min(1, (imageWidth - x - 10f) / Math.max(1, font.width(text)));
    graphics.pose().pushPose();
    graphics.pose().translate(x, y, 0);
    graphics.pose().scale(scale, scale, 1);
    graphics.drawString(font, text, 0, 0, 0xffe5e9e9, false);
    graphics.pose().popPose();
  }

  @Override
  protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    graphics.drawString(font, menu.slime().getDisplayName(), 10, 7, 0xffe9f6df, false);
    var points = Component.translatable("screen.slimic.points", menu.points());
    graphics.pose().pushPose();
    graphics.pose().translate(10, 51, 0);
    float pointScale = Math.min(1, 174f / Math.max(1, font.width(points)));
    graphics.pose().scale(pointScale, pointScale, 1);
    graphics.drawString(font, points, 0, 0, 0xffffd77e, false);
    graphics.pose().popPose();
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
          formatNumber(
              value,
              stat == SlimeStat.MOVEMENT_SPEED
                  ? "%.3f"
                  : stat == SlimeStat.WORK_SPEED || stat == SlimeStat.REACH
                      ? "%.2f"
                      : value == Math.rint(value) ? "%.0f" : "%.2f");
      graphics.drawString(font, formatted, 158 - font.width(formatted), y, 0xff9de080, false);
    }
    drawVital(
        graphics,
        Component.translatable(
            "screen.slimic.health",
            formatNumber(menu.health(), "%.1f"),
            formatNumber(menu.value(SlimeStat.HEALTH), "%.0f")),
        (menu.slime() instanceof CombatSlime
                || menu.slime() instanceof SaddleSlime
                || menu.slime() instanceof TunnelSlime)
            ? 254
            : menu.slime() instanceof MiningSlime
                ? 236
                : menu.slime() instanceof FieldSlime
                        || menu.slime() instanceof GuardianAngelSlime
                        || menu.slime() instanceof FishingSlime
                    ? 218
                    : SlimeMenu.SLOTS_X,
        49);
    drawVital(
        graphics,
        Component.translatable(
            "screen.slimic.stamina",
            formatNumber(menu.stamina(), "%.1f"),
            formatNumber(menu.value(SlimeStat.STAMINA), "%.0f")),
        (menu.slime() instanceof CombatSlime
                || menu.slime() instanceof SaddleSlime
                || menu.slime() instanceof TunnelSlime)
            ? 254
            : menu.slime() instanceof MiningSlime
                ? 236
                : menu.slime() instanceof FieldSlime
                        || menu.slime() instanceof GuardianAngelSlime
                        || menu.slime() instanceof FishingSlime
                    ? 218
                    : SlimeMenu.SLOTS_X,
        62);
    if (menu.isPolicyEditor()) {
      if (tunnelSize == null)
        graphics.drawString(
            font,
            Component.translatable("screen.slimic.policy"),
            SlimeMenu.SLOTS_X,
            SlimeMenu.BAG_LABEL_Y,
            0xffe5e9e9,
            false);
      if (tunnelSize != null) {
        graphics.drawString(
            font,
            Component.translatable(
                "screen.slimic.tunnel_size.current",
                menu.data(SlimeMenu.TUNNEL_SIZE),
                menu.data(SlimeMenu.TUNNEL_SIZE)),
            SlimeMenu.SLOTS_X,
            90,
            0xffb4c9d1,
            false);
        graphics.drawString(
            font,
            Component.translatable("screen.slimic.tunnel_size.note"),
            SlimeMenu.SLOTS_X,
            130,
            0xffb4c9d1,
            false);
        graphics.drawString(
            font,
            Component.translatable(
                "screen.slimic.tunnel_size.range",
                ((io.github.meistermods.slimic.entity.NextGenerationTunnelSlime) menu.slime())
                    .maximumTunnelSize()),
            SlimeMenu.SLOTS_X,
            141,
            0xffb4c9d1,
            false);
      }
    } else if (menu.slime() instanceof BagSlime) {
      if (tunnelSize == null)
        graphics.drawString(
            font,
            menu.isBagVisible()
                ? Component.translatable("screen.slimic.bag", (int) menu.value(SlimeStat.CAPACITY))
                : Component.translatable("screen.slimic.targets"),
            SlimeMenu.SLOTS_X,
            SlimeMenu.BAG_LABEL_Y,
            0xffe5e9e9,
            false);
      Enum<?>[] statuses =
          menu.slime() instanceof FishingSlime
              ? FishingSlime.Status.values()
              : menu.slime() instanceof TunnelSlime
                  ? TunnelSlime.Status.values()
                  : menu.slime() instanceof HealingSlime
                      ? HealingSlime.Status.values()
                      : menu.slime() instanceof FieldSlime
                          ? FieldSlime.Status.values()
                          : menu.slime() instanceof TransportSlime
                              ? TransportSlime.Status.values()
                              : menu.slime() instanceof CombatSlime
                                  ? CombatSlime.Status.values()
                                  : menu.slime() instanceof CleaningSlime
                                      ? CleaningSlime.Status.values()
                                      : MiningSlime.Status.values();
      String key =
          statuses[Math.min(statuses.length - 1, Math.max(0, menu.data(SlimeMenu.STATUS)))]
              .name()
              .toLowerCase(Locale.ROOT);
      graphics.drawWordWrap(
          font,
          menu.slime() instanceof LoggingSlime logger
                  && key.equals("no_seeds")
                  && !logger.getRequiredSapling().isEmpty()
              ? Component.translatable(
                  "status.slimic.lumberjack.saplings",
                  logger.getRequiredSapling().getHoverName(),
                  logger.getRequiredSaplingCount())
              : Component.translatable(
                  "status.slimic."
                      + (menu.slime() instanceof FishingSlime
                          ? "fishing."
                          : menu.slime() instanceof TunnelSlime
                              ? "tunnel."
                              : menu.slime() instanceof HealingSlime
                                  ? "healing."
                                  : menu.slime() instanceof WarehouseSlime
                                      ? "warehouse."
                                      : menu.slime() instanceof FieldSlime
                                          ? (menu.slime() instanceof FarmingSlime
                                              ? "farmer."
                                              : "lumberjack.")
                                          : menu.slime() instanceof TransportSlime
                                              ? "transport."
                                              : menu.slime() instanceof CombatSlime
                                                  ? "combat."
                                                  : menu.slime() instanceof CleaningSlime
                                                      ? "cleaning."
                                                      : "")
                      + key),
          10,
          216,
          174,
          0xffb4c9d1);
      if (menu.isBagVisible() && menu.bagPages() > 1) {
        String counter = (menu.data(SlimeMenu.BAG_PAGE) + 1) + "/" + menu.bagPages();
        float scale = Math.min(1, 42f / Math.max(1, font.width(counter)));
        graphics.pose().pushPose();
        graphics.pose().translate(323, SlimeMenu.INVENTORY_LABEL_Y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawCenteredString(font, counter, 0, 0, 0xffe5e9e9);
        graphics.pose().popPose();
      }
    } else if (menu.slime() instanceof MeowSlime cat) {
      graphics.drawWordWrap(
          font,
          Component.translatable(
              "screen.slimic.meow_state",
              Component.translatable(
                  cat.isActive() ? "screen.slimic.enabled" : "screen.slimic.disabled")),
          SlimeMenu.SLOTS_X,
          112,
          162,
          0xffb4c9d1);
    } else if (menu.slime() instanceof SoggySlime) {
      graphics.drawWordWrap(
          font,
          Component.translatable("screen.slimic.soggy_info"),
          SlimeMenu.SLOTS_X,
          84,
          162,
          0xffb4c9d1);
      graphics.drawWordWrap(
          font,
          Component.translatable(
              menu.data(SlimeMenu.MODE) == SlimeMenu.ROAM
                  ? "status.slimic.roaming"
                  : menu.data(SlimeMenu.MODE) == SlimeMenu.WAIT
                      ? "status.slimic.waiting"
                      : "status.slimic.following"),
          10,
          216,
          174,
          0xffb4c9d1);
    } else if (menu.slime() instanceof GuardianAngelSlime) {
      graphics.drawWordWrap(
          font,
          Component.translatable("screen.slimic.guardian_help"),
          SlimeMenu.SLOTS_X,
          84,
          162,
          0xffb4c9d1);
    } else if (menu.slime() instanceof SaddleSlime) {
      graphics.drawWordWrap(
          font,
          Component.translatable("screen.slimic.saddle_help"),
          SlimeMenu.SLOTS_X,
          84,
          162,
          0xffb4c9d1);
    } else if (menu.slime() instanceof SignalingSlime) {
      graphics.drawWordWrap(
          font,
          Component.translatable("screen.slimic.herald_help"),
          SlimeMenu.SLOTS_X,
          84,
          162,
          0xffb4c9d1);
      graphics.drawCenteredString(
          font,
          Component.translatable("screen.slimic.alert_minutes", menu.data(SlimeMenu.ALERT_MINUTES)),
          275,
          117,
          0xffe5e9e9);
    } else {
      graphics.drawWordWrap(
          font,
          Component.translatable("screen.slimic.specialize"),
          SlimeMenu.SLOTS_X,
          88,
          162,
          0xffb4c9d1);
    }
    if (!menu.isPolicyEditor())
      graphics.drawString(
          font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xffe2e9e9, false);
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    renderBackground(graphics);
    super.render(graphics, mouseX, mouseY, partialTick);
    if (mouseX >= leftPos + 10
        && mouseX < leftPos + 184
        && mouseY >= topPos + 50
        && mouseY < topPos + 62)
      graphics.renderTooltip(
          font,
          Component.translatable("screen.slimic.points", menu.points())
              .append("\n")
              .append(Component.translatable("item.slimic.synthesis.unspent")),
          mouseX,
          mouseY);
    else if (hoveredSlot != null && hoveredSlot.index == 2 && menu.slime() instanceof TunnelSlime)
      graphics.renderTooltip(
          font,
          font.split(Component.translatable("screen.slimic.lining_help"), 220),
          mouseX,
          mouseY);
    else renderTooltip(graphics, mouseX, mouseY);
  }
}
