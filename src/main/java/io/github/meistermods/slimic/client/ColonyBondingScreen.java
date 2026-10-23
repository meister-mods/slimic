package io.github.meistermods.slimic.client;

import io.github.meistermods.slimic.menu.ColonyBondingMenu;
import io.github.meistermods.slimic.world.ColonyBonding;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Names, retained state and the reversible result are visible before server confirmation. */
public final class ColonyBondingScreen extends AbstractContainerScreen<ColonyBondingMenu> {
  private Button bind, unbind, recover;

  public ColonyBondingScreen(ColonyBondingMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 320;
    imageHeight = 240;
    inventoryLabelX = 79;
    inventoryLabelY = 147;
  }

  @Override
  protected void init() {
    super.init();
    bind = button("bind", ColonyBondingMenu.BIND, 12, 124, 92);
    unbind = button("unbind", ColonyBondingMenu.UNBIND, 114, 124, 92);
    recover = button("recover", ColonyBondingMenu.RECOVER, 216, 124, 92);
    bind.setTooltip(Tooltip.create(Component.translatable("screen.slimic.bonding.retained")));
    unbind.setTooltip(Tooltip.create(Component.translatable("screen.slimic.bonding.retained")));
  }

  private Button button(String name, int action, int x, int y, int width) {
    return addRenderableWidget(
        Button.builder(
                Component.translatable("screen.slimic.bonding." + name),
                b -> {
                  if (minecraft != null && minecraft.gameMode != null)
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
                })
            .bounds(leftPos + x, topPos + y, width, 20)
            .build());
  }

  @Override
  protected void containerTick() {
    super.containerTick();
    bind.active = menu.problem() == ColonyBonding.Problem.READY;
    unbind.active = menu.problem() == ColonyBonding.Problem.READY_UNBIND;
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
  }

  @Override
  protected void renderLabels(GuiGraphics g, int x, int y) {
    g.drawString(font, title, 12, 8, 0xfff0ead1, false);
    List<CompoundTag> records = records();
    for (int index = 0; index < records.size(); index++) {
      CompoundTag record = records.get(index);
      int left = 14 + index * 160;
      Component name =
          record.contains("CustomName")
              ? Component.Serializer.fromJson(record.getString("CustomName"))
              : Component.translatable("screen.slimic.bonding.member", index + 1);
      if (name == null) name = Component.translatable("screen.slimic.bonding.member", index + 1);
      g.drawString(
          font, font.plainSubstrByWidth(name.getString(), 140), left, 24, 0xffffdc89, false);
      g.drawString(
          font,
          Component.translatable(
              "screen.slimic.bonding.vitals",
              compact(record.getFloat("Health")),
              compact(record.getFloat("Stamina"))),
          left,
          60,
          0xffd5dfd2,
          false);
      g.drawString(
          font,
          Component.translatable(
              "screen.slimic.bonding.points", compact(record.getLong("StatPoints"))),
          left,
          70,
          0xffd5dfd2,
          false);
      String bag = record.contains("TransportBag") ? "TransportBag" : "CombatBag";
      g.drawString(
          font,
          Component.translatable(
              "screen.slimic.bonding.cargo",
              record.getCompound(bag).getList("Items", Tag.TAG_COMPOUND).size()),
          left,
          80,
          0xffd5dfd2,
          false);
    }
    g.drawString(
        font,
        Component.translatable(
            "screen.slimic.bonding.preview",
            compact(menu.previewValue(2)),
            compact(menu.previewValue(3))),
        14,
        91,
        0xffffdc89,
        false);
    g.drawString(
        font,
        Component.translatable(
            "screen.slimic.bonding.performance",
            compact(menu.previewValue(4)),
            compact(menu.previewValue(5))),
        14,
        101,
        0xffd5dfd2,
        false);
    g.drawString(
        font,
        font.plainSubstrByWidth(ColonyBondingMenu.message(menu.problem()).getString(), 292),
        14,
        112,
        0xffe6e9e1,
        false);
    g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xffd5dfd2, false);
  }

  private List<CompoundTag> records() {
    ItemStack first = menu.getSlot(0).getItem();
    List<CompoundTag> result = new ArrayList<>();
    if (ColonyBonding.bound(first) && first.hasTag()) {
      ListTag list =
          first
              .getTag()
              .getCompound("SlimeData")
              .getCompound("ColonyMembers")
              .getList("Members", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(2, list.size()); i++) result.add(list.getCompound(i));
    } else
      for (int i = 0; i < 2; i++) {
        ItemStack item = menu.getSlot(i).getItem();
        result.add(item.hasTag() ? item.getTag().getCompound("SlimeData") : new CompoundTag());
      }
    return result;
  }

  private static String compact(double value) {
    return value >= 1000000
        ? String.format(Locale.ROOT, "%.1g", value)
        : String.format(Locale.ROOT, value == Math.rint(value) ? "%.0f" : "%.1f", value);
  }

  @Override
  public void render(GuiGraphics g, int x, int y, float partial) {
    renderBackground(g);
    super.render(g, x, y, partial);
    renderTooltip(g, x, y);
    if (x >= leftPos + 14 && x < leftPos + 306 && y >= topPos + 112 && y < topPos + 122)
      g.renderTooltip(font, ColonyBondingMenu.message(menu.problem()), x, y);
  }
}
