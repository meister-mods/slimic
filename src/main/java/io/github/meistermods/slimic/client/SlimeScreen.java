package io.github.meistermods.slimic.client;

import io.github.meistermods.slimic.entity.BagSlime;
import io.github.meistermods.slimic.entity.CleaningSlime;
import io.github.meistermods.slimic.entity.CombatSlime;
import io.github.meistermods.slimic.entity.CombatTarget;
import io.github.meistermods.slimic.entity.FarmerSlime;
import io.github.meistermods.slimic.entity.FieldSlime;
import io.github.meistermods.slimic.entity.GuardianAngelSlime;
import io.github.meistermods.slimic.entity.HealingSlime;
import io.github.meistermods.slimic.entity.HeraldSlime;
import io.github.meistermods.slimic.entity.LumberjackSlime;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.SaddleSlime;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.entity.SoggySlime;
import io.github.meistermods.slimic.entity.TransportSlime;
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
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

public final class SlimeScreen extends AbstractContainerScreen<SlimeMenu> {
  private EditBox name;
  private final Map<SlimeStat, Button> upgrades = new EnumMap<>(SlimeStat.class);
  private final Map<Integer, Button> orders = new java.util.HashMap<>();
  private Button previousPage;
  private Button nextPage;

  public SlimeScreen(SlimeMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 368;
    imageHeight = 240;
    inventoryLabelX = 194;
    inventoryLabelY = 146;
  }

  private final java.util.List<Button> filters = new java.util.ArrayList<>();
  private Button targetPageButton;

  @Override
  protected void init() {
    super.init();
    upgrades.clear();
    filters.clear();
    targetPageButton = null;
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
    addOrder(SlimeMenu.FOLLOW, "follow", 194);
    addOrder(SlimeMenu.WAIT, "wait", 250);
    if (menu.slime() instanceof SoggySlime) addOrder(SlimeMenu.ROAM, "roam", 306);
    if (menu.slime() instanceof CombatSlime) {
      addOrder(SlimeMenu.WORK, "fight", 306);
      targetPageButton =
          addRenderableWidget(
              Button.builder(
                      Component.translatable("screen.slimic.targets"),
                      button -> sendButton(SlimeMenu.TARGET_PAGE_BUTTON))
                  .bounds(leftPos + 294, topPos + 73, 62, 14)
                  .build());
      for (CombatTarget kind : CombatTarget.values()) {
        Button button =
            Button.builder(
                    Component.empty(),
                    pressed -> sendButton(SlimeMenu.TARGET_TOGGLE + kind.ordinal()))
                .bounds(
                    leftPos + 194 + kind.ordinal() % 2 * 82,
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
              .bounds(leftPos + 306, topPos + 25, 50, 18)
              .build());
    if (menu.slime() instanceof HealingSlime) addOrder(SlimeMenu.WORK, "heal", 306);
    if (menu.slime() instanceof FarmerSlime) addOrder(SlimeMenu.WORK, "farm", 306);
    if (menu.slime() instanceof LumberjackSlime) addOrder(SlimeMenu.WORK, "log", 306);
    if (menu.slime() instanceof CleaningSlime) addOrder(SlimeMenu.WORK, "clean", 306);
    if (menu.slime() instanceof MiningSlime) addOrder(SlimeMenu.WORK, "mine", 306);
    if (menu.slime() instanceof HeraldSlime) {
      addOrder(SlimeMenu.WORK, "monitor", 306);
      addRenderableWidget(
          Button.builder(
                  Component.translatable("screen.slimic.fire_signal"),
                  b -> sendButton(SlimeMenu.FIRE_SIGNAL))
              .bounds(leftPos + 222, topPos + 137, 106, 16)
              .build());
      addRenderableWidget(
          Button.builder(Component.literal("-"), button -> sendButton(SlimeMenu.DELAY_DOWN))
              .bounds(leftPos + 194, topPos + 112, 24, 18)
              .build());
      addRenderableWidget(
          Button.builder(Component.literal("+"), button -> sendButton(SlimeMenu.DELAY_UP))
              .bounds(leftPos + 332, topPos + 112, 24, 18)
              .build());
    }
    if (menu.slime() instanceof TransportSlime)
      addOrder(SlimeMenu.WORK, menu.slime() instanceof WarehouseSlime ? "store" : "transport", 306);
    if (menu.slime() instanceof TransportSlime) {
      previousPage =
          addRenderableWidget(
              Button.builder(Component.literal("<"), button -> sendButton(SlimeMenu.PREVIOUS_PAGE))
                  .bounds(leftPos + 286, topPos + 74, 12, 12)
                  .tooltip(Tooltip.create(Component.translatable("screen.slimic.previous_page")))
                  .build());
      nextPage =
          addRenderableWidget(
              Button.builder(Component.literal(">"), button -> sendButton(SlimeMenu.NEXT_PAGE))
                  .bounds(leftPos + 344, topPos + 74, 12, 12)
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

  private void saveName() {
    ModNetwork.CHANNEL.sendToServer(new ModNetwork.RenameSlime(menu.containerId, name.getValue()));
  }

  @Override
  protected void containerTick() {
    super.containerTick();
    if (!menu.slime().isAlive() || menu.slime().isRemoved()) {
      onClose();
      return;
    }
    if (!menu.isSynchronized()) return;
    name.tick();
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
    if (previousPage != null) {
      previousPage.active = menu.data(SlimeMenu.BAG_PAGE) > 0;
      nextPage.active = menu.data(SlimeMenu.BAG_PAGE) + 1 < menu.bagPages();
    }
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
        (menu.slime() instanceof CombatSlime || menu.slime() instanceof SaddleSlime)
            ? 252
            : menu.slime() instanceof MiningSlime
                ? 234
                : menu.slime() instanceof FieldSlime || menu.slime() instanceof GuardianAngelSlime
                    ? 216
                    : 194,
        49,
        0xffe5e9e9,
        false);
    graphics.drawString(
        font,
        Component.translatable(
            "screen.slimic.stamina",
            menu.data(SlimeMenu.STAMINA) / 10,
            (int) menu.value(SlimeStat.STAMINA)),
        (menu.slime() instanceof CombatSlime || menu.slime() instanceof SaddleSlime)
            ? 252
            : menu.slime() instanceof MiningSlime
                ? 234
                : menu.slime() instanceof FieldSlime || menu.slime() instanceof GuardianAngelSlime
                    ? 216
                    : 194,
        62,
        0xffe5e9e9,
        false);
    if (menu.slime() instanceof BagSlime) {
      graphics.drawString(
          font,
          Component.translatable("screen.slimic.bag", (int) menu.value(SlimeStat.CAPACITY)),
          194,
          77,
          0xffe5e9e9,
          false);
      Enum<?>[] statuses =
          menu.slime() instanceof HealingSlime
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
          menu.slime() instanceof LumberjackSlime logger
                  && key.equals("no_seeds")
                  && !logger.getRequiredSapling().isEmpty()
              ? Component.translatable(
                  "status.slimic.lumberjack.saplings",
                  logger.getRequiredSapling().getHoverName(),
                  logger.getRequiredSaplingCount())
              : Component.translatable(
                  "status.slimic."
                      + (menu.slime() instanceof HealingSlime
                          ? "healing."
                          : menu.slime() instanceof WarehouseSlime
                              ? "warehouse."
                              : menu.slime() instanceof FieldSlime
                                  ? (menu.slime() instanceof FarmerSlime
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
      if (menu.slime() instanceof TransportSlime)
        graphics.drawCenteredString(
            font,
            Component.literal((menu.data(SlimeMenu.BAG_PAGE) + 1) + "/" + menu.bagPages()),
            321,
            77,
            0xffe5e9e9);
    } else if (menu.slime() instanceof SoggySlime) {
      graphics.drawWordWrap(
          font, Component.translatable("screen.slimic.soggy_info"), 194, 84, 162, 0xffb4c9d1);
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
          font, Component.translatable("screen.slimic.guardian_help"), 194, 84, 162, 0xffb4c9d1);
    } else if (menu.slime() instanceof SaddleSlime) {
      graphics.drawWordWrap(
          font, Component.translatable("screen.slimic.saddle_help"), 194, 84, 162, 0xffb4c9d1);
    } else if (menu.slime() instanceof HeraldSlime) {
      graphics.drawWordWrap(
          font, Component.translatable("screen.slimic.herald_help"), 194, 84, 162, 0xffb4c9d1);
      graphics.drawCenteredString(
          font,
          Component.translatable("screen.slimic.alert_minutes", menu.data(SlimeMenu.ALERT_MINUTES)),
          275,
          117,
          0xffe5e9e9);
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
