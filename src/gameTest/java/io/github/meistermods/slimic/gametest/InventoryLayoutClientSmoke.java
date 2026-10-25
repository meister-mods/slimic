package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.client.SlimeScreen;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.*;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Real synchronized bag pages, widget collision checks, and full-texture flat gold badge rendering.
 */
public final class InventoryLayoutClientSmoke {
  private static final List<String> LANGUAGES = List.of("en_us", "ko_kr", "ja_jp", "es_es");

  private record Case(
      String name,
      EntityType<? extends FriendlySlime> type,
      int upgrades,
      int page,
      boolean targets) {}

  private static List<Case> cases() {
    return List.of(
        new Case("mining-9", ModEntities.MINING_SLIME.get(), 0, 0, false),
        new Case("mining-12", ModEntities.MINING_SLIME.get(), 1, 0, false),
        new Case("mining-27", ModEntities.MINING_SLIME.get(), 6, 0, false),
        new Case("transport-18", ModEntities.TRANSPORT_SLIME.get(), 0, 0, false),
        new Case("transport-36-first", ModEntities.TRANSPORT_SLIME.get(), 3, 0, false),
        new Case("transport-36-last", ModEntities.TRANSPORT_SLIME.get(), 3, 1, false),
        new Case("warehouse-216-last", ModEntities.WAREHOUSE_SLIME.get(), 6, 7, false),
        new Case("combat-bag", ModEntities.COMBAT_SLIME.get(), 0, 0, false),
        new Case("combat-targets", ModEntities.COMBAT_SLIME.get(), 0, 0, true),
        new Case("fishing", ModEntities.FISHING_SLIME.get(), 0, 0, false),
        new Case("tunnel", ModEntities.TUNNEL_SLIME.get(), 0, 0, false),
        new Case("farming", ModEntities.FARMING_SLIME.get(), 0, 0, false),
        new Case("abundance", ModEntities.ABUNDANCE_SLIME.get(), 0, 0, false));
  }

  private static int phase, ticks, index, language, screens;
  private static CompletableFuture<?> task;
  private static UUID petId;
  private static FriendlySlime pet;

  private static String locale() {
    return LANGUAGES.get(Math.min(language, LANGUAGES.size() - 1));
  }

  private static void shot(Minecraft c, String name) {
    try {
      var out = c.gameDirectory.toPath().resolve("inventory-layout-audit");
      Files.createDirectories(out);
      try (var image = Screenshot.takeScreenshot(c.getMainRenderTarget())) {
        image.writeToFile(out.resolve(locale() + "-" + name + ".png"));
      }
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void prepare(Minecraft c) {
    var sample = cases().get(index);
    task =
        CompletableFuture.runAsync(
            () -> {
              var owner = c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
              owner.closeContainer();
              if (pet != null) pet.discard();
              var level = owner.serverLevel();
              for (int x = 7; x <= 13; x++)
                for (int z = 5; z <= 12; z++) {
                  level.setBlock(new BlockPos(x, 100, z), Blocks.STONE.defaultBlockState(), 2);
                  for (int y = 101; y < 106; y++)
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
              owner.getInventory().clearContent();
              owner.setItemInHand(
                  InteractionHand.OFF_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
              owner.getInventory().setItem(0, new ItemStack(ModItems.FRIENDLY_SLIME.get()));
              owner.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 12));
              owner.getInventory().setItem(17, new ItemStack(Items.EMERALD, 34));
              owner.getInventory().setItem(27, new ItemStack(Items.REDSTONE, 56));
              pet = sample.type.create(level);
              pet.tame(owner);
              pet.setNoAi(true);
              pet.setTunedVitals(pet.getMaxHealth(), 100, 20);
              for (int i = 0; i < sample.upgrades; i++) pet.upgradeStat(SlimeStat.CAPACITY);
              if (pet instanceof BagSlime bag)
                for (int slot = 0; slot < pet.getCapacity(); slot++)
                  bag.getBag()
                      .setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, slot % 64 + 1));
              if (pet instanceof MiningSlime miner) {
                miner.setTool(0, new ItemStack(Items.DIAMOND_PICKAXE));
                miner.setTool(1, new ItemStack(Items.IRON_SHOVEL));
              }
              if (pet instanceof TunnelSlime tunnel)
                tunnel.setLiningTemplate(new ItemStack(Items.STONE_BRICKS));
              if (pet instanceof FishingSlime fishing)
                fishing.setRod(new ItemStack(Items.FISHING_ROD));
              if (pet instanceof FarmingSlime farming)
                farming.setTool(new ItemStack(Items.IRON_HOE));
              if (pet instanceof CombatSlime combat) {
                combat.setEquipment(0, new ItemStack(Items.DIAMOND_SWORD));
                combat.setEquipment(1, new ItemStack(Items.SHIELD));
                combat.setEquipment(
                    2, SlimeArmorItem.fromChestplate(new ItemStack(Items.IRON_CHESTPLATE)));
              }
              pet.moveTo(10.5, 101, 8.5);
              level.addFreshEntity(pet);
              pet.onReleased();
              petId = pet.getUUID();
              owner.teleportTo(level, 10.5, 101, 6.5, 0, 10);
            },
            c.getSingleplayerServer());
    phase = 2;
    ticks = 0;
  }

  private static void verify(SlimeScreen screen) {
    var menu = screen.getMenu();
    var sample = cases().get(index);
    LocalizedWidgetChecks.record(
        Minecraft.getInstance(), screen, "inventory/" + locale() + "/" + sample.name, 368, 240);
    for (var child : screen.children())
      if (child instanceof AbstractWidget widget
          && widget.visible
          && (widget.getMessage().getString().contains(".slimic.")
              || widget.getMessage().getString().contains("Format error:")))
        throw new IllegalStateException(
            "Untranslated inventory widget: " + locale() + " / " + widget.getMessage());
    var origins = new HashSet<String>();
    int active = 0;
    int left = (screen.width - 368) / 2, top = (screen.height - 240) / 2;
    for (var slot : menu.slots) {
      if (!slot.isActive()) continue;
      active++;
      if (!origins.add(slot.x + ":" + slot.y))
        throw new IllegalStateException("Overlapping active slots");
      if (slot.x < 190 || slot.x + 16 > 362 || slot.y < 45 || slot.y + 16 > 237)
        throw new IllegalStateException("Slot outside its panel " + slot.x + "," + slot.y);
      for (var child : screen.children())
        if (child instanceof AbstractWidget widget
            && widget.visible
            && widget.getX() < left + slot.x + 16
            && widget.getX() + widget.getWidth() > left + slot.x
            && widget.getY() < top + slot.y + 16
            && widget.getY() + widget.getHeight() > top + slot.y)
          throw new IllegalStateException(
              "Widget overlaps an active inventory cell: " + widget.getMessage());
    }
    int equipment = menu.slots.size() - 36 - ((BagSlime) menu.slime()).getBag().getSlots();
    int visibleBag =
        sample.targets ? 0 : Math.min(27, (int) menu.value(SlimeStat.CAPACITY) - sample.page * 27);
    if (active != 36 + equipment + visibleBag)
      throw new IllegalStateException("Unexpected active slot count " + active);
    long pageButtons =
        screen.children().stream()
            .filter(
                child ->
                    child instanceof AbstractWidget widget
                        && widget.visible
                        && (widget.getMessage().getString().equals("<")
                            || widget.getMessage().getString().equals(">")))
            .count();
    if (pageButtons != (menu.slime() instanceof TransportSlime && menu.bagPages() > 1 ? 2 : 0))
      throw new IllegalStateException("Single-page bag exposes paging controls");
  }

  public static boolean tick(Minecraft c) {
    if (c.getOverlay() != null) return false;
    if (++ticks > 400)
      throw new IllegalStateException(
          "Inventory client timeout at phase " + phase + " case " + index);
    if (phase == 0) {
      c.options.guiScale().set(2);
      c.options.forceUnicodeFont().set(false);
      org.lwjgl.glfw.GLFW.glfwSetWindowSize(c.getWindow().getWindow(), 1280, 900);
      c.resizeDisplay();
      c.getLanguageManager().setSelected(locale());
      c.options.languageCode = locale();
      task = c.reloadResourcePacks();
      phase = 1;
      ticks = 0;
      return false;
    }
    if (task != null) {
      if (!task.isDone()) return false;
      task.join();
      task = null;
    }
    if (phase == 1) {
      prepare(c);
      return false;
    }
    if (phase == 2) {
      var tracked =
          c.level.getEntitiesOfClass(
              FriendlySlime.class, new AABB(7, 99, 5, 14, 108, 13), e -> e.getUUID().equals(petId));
      if (tracked.isEmpty() || ticks < 6) return false;
      var sample = cases().get(index);
      task =
          CompletableFuture.runAsync(
              () -> {
                var owner = c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
                if (!SlimeTunerItem.open(owner, pet).consumesAction())
                  throw new IllegalStateException("Menu open failed");
                var menu = (SlimeMenu) owner.containerMenu;
                for (int i = 0; i < sample.page; i++)
                  menu.clickMenuButton(owner, SlimeMenu.NEXT_PAGE);
                if (sample.targets) menu.clickMenuButton(owner, SlimeMenu.TARGET_PAGE_BUTTON);
              },
              c.getSingleplayerServer());
      phase = 3;
      ticks = 0;
      return false;
    }
    if (phase == 3) {
      if (!(c.screen instanceof SlimeScreen screen)
          || !screen.getMenu().isSynchronized()
          || screen.getMenu().data(SlimeMenu.BAG_PAGE) != cases().get(index).page
          || ticks < 10) return false;
      verify(screen);
      shot(c, cases().get(index).name);
      screens++;
      if (!cases().get(index).targets) {
        var button =
            screen.children().stream()
                .filter(
                    child ->
                        child instanceof net.minecraft.client.gui.components.Button widget
                            && widget.visible
                            && widget
                                .getMessage()
                                .getString()
                                .equals(
                                    Component.translatable("screen.slimic.sort_bag").getString()))
                .map(child -> (net.minecraft.client.gui.components.Button) child)
                .findFirst()
                .orElseThrow();
        if (!button.active
            || !screen.mouseClicked(
                button.getX() + button.getWidth() / 2D, button.getY() + button.getHeight() / 2D, 0))
          throw new IllegalStateException("Native Sort click was not accepted");
        phase = 5;
        ticks = 0;
        return false;
      }
      nextCase(c);
      return false;
    }
    if (phase == 5) {
      if (!(c.screen instanceof SlimeScreen screen)
          || ticks < 10
          || screen.getMenu().data(SlimeMenu.BAG_PAGE) != 0) return false;
      int total = java.util.stream.IntStream.range(0, pet.getCapacity()).map(i -> i % 64 + 1).sum();
      int equipment = screen.getMenu().slots.size() - 36 - ((BagSlime) pet).getBag().baseSlots();
      if (screen.getMenu().getSlot(equipment).getItem().getCount() != Math.min(64, total))
        throw new IllegalStateException("Sorted first stack did not synchronize");
      shot(c, cases().get(index).name + "-sorted");
      task =
          c.getSingleplayerServer()
              .submit(
                  () -> {
                    var bag = ((BagSlime) pet).getBag();
                    int actual =
                        bag.occupiedSlots().stream()
                            .mapToInt(i -> bag.getStackInSlot(i).getCount())
                            .sum();
                    var expected =
                        java.util.stream.IntStream.range(0, (total + 63) / 64).boxed().toList();
                    if (actual != total || !bag.occupiedSlots().equals(expected))
                      throw new IllegalStateException("Native Sort packet lost cargo or left gaps");
                    var owner =
                        c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
                    if (owner.getInventory().getItem(9).getCount() != 12
                        || owner.getInventory().getItem(17).getCount() != 34
                        || owner.getInventory().getItem(27).getCount() != 56)
                      throw new IllegalStateException("Bag sorting changed player inventory");
                  });
      phase = 6;
      return false;
    }
    if (phase == 6) {
      nextCase(c);
      return false;
    }
    if (ticks < 10) return false;
    shot(c, "flat-gold-badge");
    LocalizedWidgetChecks.finish(c, "inventory");
    if (screens != cases().size() * LANGUAGES.size())
      throw new IllegalStateException(
          "Every inventory case must render in each supported language");
    System.out.println(
        "SLIMIC INVENTORY LAYOUT CLIENT SMOKE PASSED: "
            + screens
            + " EN/KR/JA/ES synchronized screens, partial/expanded pages, locked cells, filters,"
            + " widget bounds, native Sort clicks and cargo synchronization, full-UV flat gold"
            + " badge");
    return true;
  }

  private static void nextCase(Minecraft c) {
    if (++index < cases().size()) {
      prepare(c);
      return;
    }
    if (++language < LANGUAGES.size()) {
      index = 0;
      phase = 0;
      ticks = 0;
      return;
    }
    checkGold(c);
    c.setScreen(new Icons());
    phase = 4;
    ticks = 0;
  }

  private static void checkGold(Minecraft c) {
    var model =
        c.getItemRenderer()
            .getModel(new ItemStack(ModItems.FRIENDLY_SLIME.get()), c.level, c.player, 0);
    int gold = 0;
    for (var quad : model.getQuads(null, null, RandomSource.create(0))) {
      if (!quad.getSprite().contents().name().toString().equals("minecraft:block/gold_block"))
        continue;
      gold++;
      var vertices = quad.getVertices();
      int stride = vertices.length / 4;
      float loU = Float.POSITIVE_INFINITY, hiU = Float.NEGATIVE_INFINITY;
      float loV = loU, hiV = hiU, depth = Float.intBitsToFloat(vertices[2]);
      for (int i = 0; i < 4; i++) {
        float u = Float.intBitsToFloat(vertices[i * stride + 4]),
            v = Float.intBitsToFloat(vertices[i * stride + 5]);
        loU = Math.min(loU, u);
        hiU = Math.max(hiU, u);
        loV = Math.min(loV, v);
        hiV = Math.max(hiV, v);
        if (Float.intBitsToFloat(vertices[i * stride + 2]) != depth)
          throw new IllegalStateException("Gold badge is not planar");
      }
      var sprite = quad.getSprite();
      if ((hiU - loU) / (sprite.getU1() - sprite.getU0()) < .95
          || (hiV - loV) / (sprite.getV1() - sprite.getV0()) < .95)
        throw new IllegalStateException("Gold badge must display the whole block texture");
    }
    if (gold != 2) throw new IllegalStateException("Flat badge must have two gold faces");
  }

  private static final class Icons extends Screen {
    Icons() {
      super(Component.literal("Flat gold-block badge"));
    }

    @Override
    public boolean isPauseScreen() {
      return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
      g.fill(0, 0, width, height, 0xff202830);
      g.drawCenteredString(font, title, width / 2, 45, 0xffffff);
      for (int i = 0; i < 3; i++) {
        var stack =
            new ItemStack(
                i == 0
                    ? ModItems.FRIENDLY_SLIME.get()
                    : i == 1 ? ModItems.MINING_SLIME.get() : Items.GOLD_BLOCK);
        g.pose().pushPose();
        g.pose().translate(width / 2 - 220 + i * 170, 100, 0);
        g.pose().scale(8, 8, 1);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
        g.drawCenteredString(font, stack.getHoverName(), width / 2 - 156 + i * 170, 250, 0xffffff);
      }
    }
  }
}
