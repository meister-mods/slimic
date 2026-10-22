package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.client.*;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.menu.*;
import io.github.meistermods.slimic.network.*;
import io.github.meistermods.slimic.registry.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Native client rendering and real client/server interactions in the isolated smoke world. */
public final class OrderThirteenClientSmoke {
  private static int phase, ticks;
  private static boolean iconsShown;
  private static CompletableFuture<Void> task, reload;
  private static final Map<String, UUID> ids = new HashMap<>();
  private static final UUID SHARED = UUID.randomUUID(), ENEMY = UUID.randomUUID();
  private static UUID selected;
  private static Vec3 beforeTeleport;
  private static String language = "en_us";

  private static void next() {
    phase++;
    ticks = 0;
    System.out.println("ORDER THIRTEEN CLIENT PHASE " + phase);
  }

  private static void server(Minecraft c, Consumer<ServerPlayer> action) {
    task =
        (task == null ? CompletableFuture.<Void>completedFuture(null) : task)
            .thenRunAsync(
                () -> {
                  var player =
                      c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
                  action.accept(player);
                },
                c.getSingleplayerServer());
  }

  private static boolean done() {
    if (task != null) {
      if (!task.isDone()) return false;
      task.join();
      task = null;
    }
    return true;
  }

  private static FriendlySlime slime(ServerPlayer p, String kind) {
    return (FriendlySlime) p.serverLevel().getEntity(ids.get(kind));
  }

  private static FriendlySlime clientSlime(Minecraft c, String kind) {
    return c
        .level
        .getEntitiesOfClass(
            FriendlySlime.class,
            new AABB(0, 90, 0, 32, 120, 32),
            e -> e.getUUID().equals(ids.get(kind)))
        .stream()
        .findFirst()
        .orElse(null);
  }

  private static void shot(Minecraft c, String name) {
    try {
      var out = c.gameDirectory.toPath().resolve("order13-audit");
      Files.createDirectories(out);
      try (var image = Screenshot.takeScreenshot(c.getMainRenderTarget())) {
        image.writeToFile(out.resolve(language + "-" + name + ".png"));
      }
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void prepareInteraction(Minecraft c, String kind, boolean tuner) {
    server(
        c,
        p -> {
          p.closeContainer();
          var s = slime(p, kind);
          p.teleportTo(p.serverLevel(), s.getX(), 101, s.getZ() - 3, 0, 18);
          p.setItemInHand(
              InteractionHand.MAIN_HAND,
              tuner ? new ItemStack(ModItems.SLIME_TUNER.get()) : ItemStack.EMPTY);
          p.inventoryMenu.broadcastChanges();
        });
  }

  private static boolean interact(Minecraft c, String kind) {
    var s = clientSlime(c, kind);
    if (s == null || c.player.distanceToSqr(s) > 36) return false;
    c.gameMode.interact(c.player, s, InteractionHand.MAIN_HAND);
    return true;
  }

  private static void action(CommanderMenu menu, int action, int page, UUID id, String tag) {
    ModNetwork.CHANNEL.sendToServer(
        new CommanderPackets.Action(
            menu.containerId, action, page, id == null ? new UUID(0, 0) : id, tag));
  }

  @SuppressWarnings("unchecked")
  private static void setup(ServerPlayer p) {
    var level = p.serverLevel();
    level
        .getEntitiesOfClass(
            Entity.class,
            new AABB(0, 90, 0, 32, 330, 32),
            e -> !(e instanceof net.minecraft.world.entity.player.Player))
        .forEach(Entity::discard);
    var remaining = new ArrayList<FriendlySlime>();
    for (var entity : level.getAllEntities())
      if (entity instanceof FriendlySlime companion && p.getUUID().equals(companion.getOwnerUUID()))
        remaining.add(companion);
    remaining.forEach(Entity::discard);
    var registry = CompanionRegistry.get(level);
    for (var id : registry.coOwners(p.getUUID()))
      registry.setRelation(p.getUUID(), id, true, false);
    for (var id : registry.enemies(p.getUUID()))
      registry.setRelation(p.getUUID(), id, false, false);
    try {
      var field = CompanionRegistry.class.getDeclaredField("companions");
      field.setAccessible(true);
      ((Map<UUID, CompanionRegistry.Entry>) field.get(registry))
          .values()
          .removeIf(e -> e.owner().equals(p.getUUID()));
      registry.setDirty();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    for (int x = 1; x < 30; x++)
      for (int z = 2; z < 30; z++) {
        level.setBlock(new BlockPos(x, 100, z), Blocks.SEA_LANTERN.defaultBlockState(), 2);
        for (int y = 101; y < 111; y++)
          level.setBlock(
              new BlockPos(x, y, z),
              (x == 1 || x == 29 || z == 2 || z == 29 || y == 110)
                  ? Blocks.STONE.defaultBlockState()
                  : Blocks.AIR.defaultBlockState(),
              2);
      }
    level.setDayTime(18000);
    p.setGameMode(GameType.CREATIVE);
    p.getAbilities().flying = true;
    p.onUpdateAbilities();
    p.getInventory().clearContent();
    int i = 0;
    for (var value : ModEntities.companionTypes()) {
      var s = (FriendlySlime) value.get().create(level);
      s.tame(p);
      s.moveTo(4.5 + i % 6 * 4, 101, 10.5 + i / 6 * 3.5, 180, 0);
      s.yBodyRot = 180;
      s.setYHeadRot(180);
      s.setNoAi(true);
      if (!s.isOrderedToSit())
        throw new IllegalStateException("Nonwaiting gallery companion " + value.getId());
      level.addFreshEntity(s);
      s.onReleased();
      ids.put(value.getId().getPath(), s.getUUID());
      i++;
    }
    if (i != 26) throw new IllegalStateException("Expected all twenty-six models");
    registry.setRelation(p.getUUID(), SHARED, true, true);
    registry.setRelation(p.getUUID(), ENEMY, false, true);
    p.server.getProfileCache().add(new GameProfile(SHARED, "SharedOwner"));
    p.server.getProfileCache().add(new GameProfile(ENEMY, "EnemyPlayer"));
    p.teleportTo(level, 14.5, 108, 3.5, 0, 25);
  }

  public static boolean tick(Minecraft c) {
    if (c.getOverlay() != null) return false;
    ticks++;
    if (ticks == 160)
      throw new IllegalStateException(
          "Order thirteen client phase timed out: "
              + phase
              + " screen="
              + c.screen
              + " snapshot="
              + (c.player.containerMenu instanceof CommanderMenu m ? m.snapshot() : "none"));
    if (!done()) return false;
    if (phase == 0) {
      c.options.guiScale().set(2);
      org.lwjgl.glfw.GLFW.glfwSetWindowSize(c.getWindow().getWindow(), 1280, 900);
      c.resizeDisplay();
      c.options.languageCode = "en_us";
      c.getLanguageManager().setSelected("en_us");
      reload = c.reloadResourcePacks();
      next();
      return false;
    }
    if (phase == 1) {
      if (!reload.isDone()) return false;
      reload.join();
      c.options.fov().set(90);
      server(c, OrderThirteenClientSmoke::setup);
      next();
      return false;
    }
    if (phase == 2) {
      if (ticks < 40
          || c.level.getEntitiesOfClass(FriendlySlime.class, new AABB(0, 90, 0, 32, 120, 32)).size()
              != 26) return false;
      if (!iconsShown) {
        shot(c, "all-26-models");
        c.setScreen(new SummonGallery());
        iconsShown = true;
        ticks = 0;
        return false;
      }
      shot(c, "all-26-summon-items");
      c.setScreen(null);
      server(
          c,
          p -> {
            var s = slime(p, "commander_slime");
            p.teleportTo(p.serverLevel(), s.getX(), 101, s.getZ() - 3.5, 0, 18);
          });
      next();
      return false;
    }
    if (phase == 3) {
      if (ticks < 25) return false;
      shot(c, "commander-front");
      server(
          c,
          p -> {
            var s = slime(p, "commander_slime");
            p.teleportTo(p.serverLevel(), s.getX() + 3.5, 101, s.getZ(), 90, 18);
          });
      next();
      return false;
    }
    if (phase == 4) {
      if (ticks < 25) return false;
      shot(c, "commander-side");
      prepareInteraction(c, "commander_slime", true);
      next();
      return false;
    }
    if (phase == 5) {
      if (ticks < 15
          || !c.player.getMainHandItem().is(ModItems.SLIME_TUNER.get())
          || !interact(c, "commander_slime")) return false;
      next();
      return false;
    }
    if (phase == 6) {
      if (!(c.screen instanceof SlimeScreen screen)
          || !screen.getMenu().isSynchronized()
          || ticks < 10) return false;
      shot(c, "commander-stats");
      c.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, SlimeMenu.ROSTER);
      c.options.guiScale().set(4);
      org.lwjgl.glfw.GLFW.glfwSetWindowSize(c.getWindow().getWindow(), 1280, 960);
      c.resizeDisplay();
      next();
      return false;
    }
    if (phase == 7) {
      if (!(c.screen instanceof CommanderScreen screen)
          || screen.getMenu().snapshot() == null
          || ticks < 10) return false;
      var menu = screen.getMenu();
      var snap = menu.snapshot();
      if (snap.players() || snap.pages() != 5 || snap.rows().size() != 6 || !snap.primaryOwner())
        throw new IllegalStateException("Roster bounds or owner view incorrect");
      if (c.getWindow().getGuiScaledWidth() != 320 || c.getWindow().getGuiScaledHeight() != 240) {
        // Resize callbacks update the allowed GUI-scale range on the next native frame.
        c.options.guiScale().set(4);
        c.resizeDisplay();
        if (ticks < 60) return false;
        throw new IllegalStateException(
            "Expected minimum-size native GUI, actual="
                + c.getWindow().getGuiScaledWidth()
                + "x"
                + c.getWindow().getGuiScaledHeight()
                + " scale="
                + c.options.guiScale().get());
      }
      for (var child : screen.children())
        if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget
            && widget.visible)
          if (widget.getX() < 0
              || widget.getY() < 0
              || widget.getX() + widget.getWidth() > screen.width
              || widget.getY() + widget.getHeight() > screen.height)
            throw new IllegalStateException(
                "Roster widget clips at minimum native GUI size: " + widget.getMessage());
      shot(c, "roster");
      selected = snap.rows().get(0).id();
      action(menu, CommanderMenu.SET_TAG, 0, selected, "pals");
      next();
      return false;
    }
    if (phase == 8) {
      if (ticks < 6) return false;
      if (!(c.screen instanceof CommanderScreen screen) || screen.getMenu().snapshot() == null)
        return false;
      var p = screen.getMenu().snapshot();
      if (p.rows().stream().noneMatch(r -> r.id().equals(selected) && r.tag().equals("pals")))
        return false;
      shot(c, "roster-tag-saved");
      action(screen.getMenu(), CommanderMenu.PLAYERS, 0, null, "");
      next();
      return false;
    }
    if (phase == 9) {
      if (!(c.screen instanceof CommanderScreen screen) || screen.getMenu().snapshot() == null)
        return false;
      var p = screen.getMenu().snapshot();
      if (!p.players() || p.rows().size() != 2 || ticks < 10) return false;
      shot(c, "player-relations");
      action(screen.getMenu(), CommanderMenu.SHARE, 0, SHARED, "");
      next();
      return false;
    }
    if (phase == 10) {
      if (!(c.screen instanceof CommanderScreen screen) || screen.getMenu().snapshot() == null)
        return false;
      var p = screen.getMenu().snapshot();
      if (!p.players() || p.rows().stream().anyMatch(r -> r.id().equals(SHARED))) return false;
      c.player.closeContainer();
      language = "ko_kr";
      c.options.languageCode = language;
      c.getLanguageManager().setSelected(language);
      reload = c.reloadResourcePacks();
      next();
      return false;
    }
    if (phase == 11) {
      if (!reload.isDone()) return false;
      reload.join();
      prepareInteraction(c, "commander_slime", false);
      next();
      return false;
    }
    if (phase == 12) {
      if (ticks < 15 || !c.player.getMainHandItem().isEmpty() || !interact(c, "commander_slime"))
        return false;
      next();
      return false;
    }
    if (phase == 13) {
      if (!(c.screen instanceof CommanderScreen screen)
          || screen.getMenu().snapshot() == null
          || ticks < 10) return false;
      shot(c, "roster");
      action(screen.getMenu(), CommanderMenu.PLAYERS, 0, null, "");
      next();
      return false;
    }
    if (phase == 14) {
      if (!(c.screen instanceof CommanderScreen screen) || screen.getMenu().snapshot() == null)
        return false;
      var p = screen.getMenu().snapshot();
      if (!p.players() || ticks < 10) return false;
      action(screen.getMenu(), CommanderMenu.SHARE, 0, ENEMY, "");
      next();
      return false;
    }
    if (phase == 15) {
      if (!(c.screen instanceof CommanderScreen screen) || screen.getMenu().snapshot() == null)
        return false;
      var p = screen.getMenu().snapshot();
      if (ticks < 10
          || p.rows().stream().noneMatch(r -> r.id().equals(ENEMY) && r.shared() && !r.enemy()))
        return false;
      shot(c, "player-relations");
      c.player.closeContainer();
      c.options.guiScale().set(2);
      org.lwjgl.glfw.GLFW.glfwSetWindowSize(c.getWindow().getWindow(), 1280, 900);
      c.resizeDisplay();
      server(
          c,
          actor -> {
            for (int x = 1; x < 30; x++)
              for (int z = 2; z < 30; z++)
                actor
                    .serverLevel()
                    .setBlock(
                        new BlockPos(x, 100, z), Blocks.BLACK_CONCRETE.defaultBlockState(), 2);
          });
      prepareInteraction(c, "glow_slime", true);
      next();
      return false;
    }
    if (phase == 16) {
      if (ticks < 15
          || !c.player.getMainHandItem().is(ModItems.SLIME_TUNER.get())
          || !interact(c, "glow_slime")) return false;
      next();
      return false;
    }
    if (phase == 17) {
      if (!(c.screen instanceof SlimeScreen screen)
          || !screen.getMenu().isSynchronized()
          || ticks < 10) return false;
      shot(c, "light-on-controls");
      c.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, SlimeMenu.ACTIVE);
      next();
      return false;
    }
    if (phase == 18) {
      if (!(clientSlime(c, "glow_slime") instanceof GlowSlime glow)
          || glow.isActive()
          || ticks < 20) return false;
      shot(c, "light-off-controls");
      c.player.closeContainer();
      prepareInteraction(c, "glow_slime", false);
      next();
      return false;
    }
    if (phase == 19) {
      if (ticks < 15 || !c.player.getMainHandItem().isEmpty() || !interact(c, "glow_slime"))
        return false;
      next();
      return false;
    }
    if (phase == 20) {
      if (ticks < 20) return false;
      shot(c, "fireflies");
      server(
          c,
          p -> {
            var glow = (GlowSlime) slime(p, "glow_slime");
            if (glow.getStamina() < 90 || glow.getStamina() > 94)
              throw new IllegalStateException("Firefly C2S stamina mismatch");
            var ender = (EnderSlime) slime(p, "ender_slime");
            p.teleportTo(p.serverLevel(), ender.getX() + 2, 101, ender.getZ(), 90, 12);
            ender.setTunedVitals(ender.getHealth(), ender.getMaxStamina(), 0);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (!ender.bind(p))
              throw new IllegalStateException("Native client endpoint link failed");
            ender.setTunedVitals(ender.getHealth(), ender.getMaxStamina(), 0);
            p.teleportTo(p.serverLevel(), 14.5, 101, 3.5, 0, 15);
            p.inventoryMenu.broadcastChanges();
          });
      next();
      return false;
    }
    if (phase == 21) {
      if (ticks < 20 || !c.player.getMainHandItem().is(ModItems.SLIME_TELEPORTER.get()))
        return false;
      shot(c, "teleporter-held");
      beforeTeleport = c.player.position();
      c.gameMode.useItem(c.player, InteractionHand.MAIN_HAND);
      next();
      return false;
    }
    if (phase == 22) {
      if (c.player.position().distanceToSqr(beforeTeleport) < 4 || ticks < 15) return false;
      shot(c, "teleporter-arrival");
      server(
          c,
          p -> {
            var ender = (EnderSlime) slime(p, "ender_slime");
            if (p.distanceToSqr(ender) > 100 || ender.getStamina() > 4)
              throw new IllegalStateException("Actual client teleport endpoint/energy mismatch");
          });
      prepareInteraction(c, "attention_slime", true);
      next();
      return false;
    }
    if (phase == 23) {
      if (ticks < 15
          || !c.player.getMainHandItem().is(ModItems.SLIME_TUNER.get())
          || !interact(c, "attention_slime")) return false;
      next();
      return false;
    }
    if (phase == 24) {
      if (!(c.screen instanceof SlimeScreen screen)
          || !screen.getMenu().isSynchronized()
          || ticks < 10) return false;
      c.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, SlimeMenu.ROAM);
      next();
      return false;
    }
    if (phase == 25) {
      if (!(c.screen instanceof SlimeScreen screen)
          || !(clientSlime(c, "attention_slime") instanceof AttentionSlime attention)
          || !attention.isRoaming()
          || screen.getMenu().data(SlimeMenu.MODE) != SlimeMenu.ROAM
          || ticks < 15) return false;
      shot(c, "attention-roam-controls");
      c.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, SlimeMenu.WAIT);
      next();
      return false;
    }
    if (phase == 26) {
      if (!(clientSlime(c, "attention_slime") instanceof AttentionSlime attention)
          || attention.isRoaming()
          || ticks < 15) return false;
      shot(c, "attention-wait-controls");
      c.player.closeContainer();
      server(
          c,
          p -> {
            for (var id : ids.values()) {
              var entity = p.serverLevel().getEntity(id);
              if (entity != null) entity.discard();
            }
            var registry = CompanionRegistry.get(p.serverLevel());
            registry.setRelation(p.getUUID(), SHARED, true, false);
            registry.setRelation(p.getUUID(), ENEMY, true, false);
            registry.setRelation(p.getUUID(), ENEMY, false, false);
          });
      next();
      return false;
    }
    if (phase == 27) {
      System.out.println(
          "SLIMIC ORDER THIRTEEN CLIENT SMOKE PASSED: 26 models, shared crests, commander mustache"
              + " and baton, real roster/tag/relation packets, EN/KR screens, independent light,"
              + " fireflies, bound token travel and Attention controls");
      return true;
    }
    return false;
  }

  private static final class SummonGallery extends net.minecraft.client.gui.screens.Screen {
    SummonGallery() {
      super(net.minecraft.network.chat.Component.literal("Companion summon items and Slime Armor"));
    }

    @Override
    public boolean isPauseScreen() {
      return false;
    }

    @Override
    public void render(
        net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partial) {
      g.fill(0, 0, width, height, 0xff202830);
      g.drawCenteredString(font, title, width / 2, 12, 0xffffff);
      int i = 0;
      for (var registered : ModEntities.companionTypes()) {
        var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(registered.getId());
        int x = width / 2 - 265 + (i % 6) * 90, y = 45 + (i / 6) * 70;
        g.pose().pushPose();
        g.pose().translate(x + 30, y, 0);
        g.pose().scale(2, 2, 1);
        g.renderItem(new ItemStack(item), 0, 0);
        g.pose().popPose();
        var name = item.getDescription();
        int lineY = y + 36;
        for (var line : font.split(name, 85)) {
          g.drawString(font, line, x, lineY, 0xffffff, false);
          lineY += 10;
        }
        i++;
      }
      g.drawCenteredString(
          font,
          net.minecraft.network.chat.Component.translatable("item.slimic.slime_armor"),
          width / 2,
          328,
          0xffffff);
      Item[] materials = {
        Items.LEATHER_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE,
        Items.GOLDEN_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE,
        Items.LEATHER_CHESTPLATE, Items.LEATHER_CHESTPLATE
      };
      for (int n = 0; n < materials.length; n++) {
        var chestplate = new ItemStack(materials[n]);
        if (n >= 6)
          ((DyeableLeatherItem) chestplate.getItem())
              .setColor(chestplate, n == 6 ? 0x346AD9 : 0xDD3939);
        var armor = io.github.meistermods.slimic.item.SlimeArmorItem.fromChestplate(chestplate);
        int x = width / 2 - 280 + n * 70;
        g.pose().pushPose();
        g.pose().translate(x + 17, 349, 0);
        g.pose().scale(2, 2, 1);
        g.renderItem(armor, 0, 0);
        g.pose().popPose();
        int lineY = 389;
        for (var line : font.split(armor.getHoverName(), 66)) {
          g.drawString(font, line, x, lineY, 0xffffff, false);
          lineY += 10;
        }
      }
    }
  }
}
