package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.client.WorkAreaPreview;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.network.WorkAreaPacket;
import io.github.meistermods.slimic.registry.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.*;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Native Shift interaction, server snapshots, visible outlines, expiry and localized system chat.
 */
public final class WorkAreaClientSmoke {
  private static int phase, ticks, index, language;
  private static UUID identity;
  private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 4);
  private static CompletableFuture<?> task;
  private static final List<EntityType<? extends FriendlySlime>> TYPES =
      List.of(
          ModEntities.MINING_SLIME.get(),
          ModEntities.TUNNEL_SLIME.get(),
          ModEntities.FARMER_SLIME.get(),
          ModEntities.TRANSPORT_SLIME.get(),
          ModEntities.CLEANING_SLIME.get());

  private static String locale() {
    return language == 0 ? "en_us" : "ko_kr";
  }

  private static void server(Minecraft c, Consumer<ServerPlayer> action) {
    task =
        CompletableFuture.runAsync(
            () ->
                action.accept(
                    c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID())),
            c.getSingleplayerServer());
  }

  private static WorkAreaPacket active() {
    try {
      var f = WorkAreaPreview.class.getDeclaredField("current");
      f.setAccessible(true);
      return (WorkAreaPacket) f.get(null);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private static List<Component> messages(Minecraft c) {
    try {
      var f = ChatComponent.class.getDeclaredField("allMessages");
      f.setAccessible(true);
      return ((List<GuiMessage>) f.get(c.gui.getChat())).stream().map(GuiMessage::content).toList();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void check(boolean value, String text) {
    if (!value) throw new IllegalStateException(text);
  }

  private static void shot(Minecraft c, String label) {
    try {
      var out = c.gameDirectory.toPath().resolve("work-area-audit");
      Files.createDirectories(out);
      try (var image = Screenshot.takeScreenshot(c.getMainRenderTarget())) {
        image.writeToFile(out.resolve(locale() + "-" + label + ".png"));
      }
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void shift(Minecraft c, boolean pressed) {
    c.options.keyShift.setDown(pressed);
    c.player.input.shiftKeyDown = pressed;
    c.player.setShiftKeyDown(pressed);
    c.player.connection.send(
        new ServerboundPlayerCommandPacket(
            c.player,
            pressed
                ? ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY
                : ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
  }

  private static void prepare(Minecraft c) {
    c.player.getInventory().selected = 0;
    c.setScreen(null);
    server(
        c,
        p -> {
          var level = p.serverLevel();
          p.closeContainer();
          p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
          p.setHealth(p.getMaxHealth());
          p.deathTime = 0;
          p.fallDistance = 0;
          p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
          level
              .getEntitiesOfClass(FriendlySlime.class, new AABB(0, 90, 0, 32, 125, 32))
              .forEach(FriendlySlime::discard);
          for (int x = 0; x < 32; x++)
            for (int z = 0; z < 32; z++) {
              level.setBlock(new BlockPos(x, 100, z), Blocks.SEA_LANTERN.defaultBlockState(), 2);
              for (int y = 101; y < 116; y++)
                level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
          p.getInventory().clearContent();
          p.getInventory().selected = 0;
          p.setShiftKeyDown(false);
          p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
          p.inventoryMenu.broadcastChanges();
          var pet = TYPES.get(index).create(level);
          pet.moveTo(8.5, 101, 8.5);
          pet.tame(p);
          pet.setNoAi(true);
          pet.setCustomName(Component.literal("Work area " + index));
          level.addFreshEntity(pet);
          pet.onReleased();
          if (pet instanceof TunnelSlime tunnel) tunnel.setTunnelDirection(Direction.EAST);
          if (pet instanceof FarmerSlime farmer) {
            for (int x = 6; x <= 10; x++)
              for (int z = 6; z <= 10; z++)
                level.setBlock(new BlockPos(x, 100, z), Blocks.FARMLAND.defaultBlockState(), 2);
            farmer.setWorking(true);
            farmer.load(farmer.saveWithoutId(new CompoundTag()));
          }
          if (pet instanceof TransportSlime transport) transport.setWorking(true);
          identity = pet.getUUID();
          p.teleportTo(level, 8.5, 101, 5.5, 0, 12);
        });
  }

  public static boolean tick(Minecraft c) {
    if (task != null) {
      if (!task.isDone()) return false;
      task.join();
      task = null;
    }
    if (++ticks > 700)
      throw new IllegalStateException(
          "Work area smoke timeout: " + phase + "/" + index + "/" + locale());
    if (phase == 0) {
      c.options.languageCode = locale();
      c.getLanguageManager().setSelected(locale());
      c.options.hideGui = false;
      c.options.guiScale().set(2);
      task = c.reloadResourcePacks();
      phase = 1;
      ticks = 0;
      return false;
    }
    if (phase == 1) {
      prepare(c);
      phase = 2;
      ticks = 0;
      return false;
    }
    if (phase == 2) {
      if (ticks == 40) {
        shot(c, "pending-interaction");
        System.out.println(
            "WORK PREVIEW WAIT: client="
                + c.player.position()
                + " health="
                + c.player.getHealth()
                + " hand="
                + c.player.getMainHandItem()
                + " selected="
                + c.player.getInventory().selected
                + " wanted="
                + identity
                + " nearby="
                + c
                    .level
                    .getEntitiesOfClass(FriendlySlime.class, new AABB(0, 90, 0, 32, 125, 32))
                    .stream()
                    .map(e -> e.getUUID() + " " + e.position())
                    .toList());
        server(
            c,
            p ->
                System.out.println(
                    "WORK PREVIEW SERVER WAIT: pos="
                        + p.position()
                        + " health="
                        + p.getHealth()
                        + " hand="
                        + p.getMainHandItem()
                        + " target="
                        + p.serverLevel().getEntity(identity)));
      }
      var pet =
          c
              .level
              .getEntitiesOfClass(
                  FriendlySlime.class,
                  new AABB(0, 90, 0, 32, 125, 32),
                  e -> e.getUUID().equals(identity))
              .stream()
              .findFirst()
              .orElse(null);
      if (pet == null
          || !c.player.getMainHandItem().is(ModItems.SLIME_TUNER.get())
          || c.player.distanceToSqr(pet) > 25
          || ticks < 15) return false;
      shift(c, true);
      c.gameMode.interact(c.player, pet, InteractionHand.MAIN_HAND);
      phase = 3;
      ticks = 0;
      return false;
    }
    if (phase == 3) {
      var preview = active();
      if (preview == null || !preview.companion().equals(identity)) return false;
      check(!preview.area().shapes().isEmpty(), "Server supplied actual work geometry");
      shift(c, false);
      server(
          c,
          p -> {
            check(
                p.serverLevel().getEntity(identity) instanceof FriendlySlime,
                "Preview did not capture the companion");
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            p.teleportTo(p.serverLevel(), 23.5, 112, .5, 60, 30);
          });
      phase = 4;
      ticks = 0;
      return false;
    }
    if (phase == 4) {
      if (ticks < 20 || c.player.getX() < 20) return false;
      shot(c, "area-" + index);
      if (index == TYPES.size() - 1) {
        phase = 6;
        ticks = 0;
      } else {
        server(c, p -> p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        phase = 5;
        ticks = 0;
      }
      return false;
    }
    if (phase == 5) {
      if (!c.player.getMainHandItem().isEmpty() || active() != null) return false;
      index++;
      phase = 1;
      ticks = 0;
      return false;
    }
    if (phase == 6) {
      if (active() != null) return false;
      check(ticks > 200 && ticks < 320, "The preview expires after fifteen seconds");
      server(
          c,
          p -> {
            var level = p.serverLevel();
            level
                .getEntitiesOfClass(FriendlySlime.class, new AABB(0, 90, 0, 32, 125, 32))
                .forEach(FriendlySlime::discard);
            var miner = ModEntities.MINING_SLIME.get().create(level);
            miner.tame(p);
            miner.moveTo(8.5, 101, 8.5);
            miner.setNoAi(true);
            miner.onReleased();
            miner.setWorking(true);
            miner.setCustomName(Component.literal("Qurry-" + locale() + "-" + RUN_ID));
            var saved = miner.saveWithoutId(new CompoundTag());
            saved.putInt("MiningStatus", MiningSlime.Status.NO_TOOL.ordinal());
            saved.putInt("BlockedWorkTicks", 1200);
            saved.putLong("BlockedEpisode", 5);
            miner.load(saved);
            level.addFreshEntity(miner);
            var herald = ModEntities.HERALD_SLIME.get().create(level);
            herald.moveTo(10.5, 101, 8.5);
            herald.tame(p);
            herald.setMonitoring(true);
            herald.setDelayMinutes(1);
            level.addFreshEntity(herald);
          });
      phase = 7;
      ticks = 0;
      return false;
    }
    if (phase == 7) {
      var received =
          messages(c).stream()
              .map(Component::getString)
              .filter(s -> s.contains("Qurry-" + locale() + "-" + RUN_ID))
              .toList();
      if (received.isEmpty()) return false;
      check(
          received.size() == 1
              && received.get(0).contains(language == 0 ? "Mining Slime" : "채굴 슬라임")
              && received.get(0).contains(language == 0 ? "pickaxe" : "곡괭이가 없어"),
          "Localized chat contains type, name and cause: " + received);
      if (ticks < 45) return false;
      shot(c, "herald-message");
      System.out.println(
          "SLIMIC WORK AREA CLIENT PASSED: "
              + locale()
              + ", five native Shift previews, visible outlines, tool-change clearing, expiry and"
              + " named herald chat");
      if (++language == 2) return true;
      index = 0;
      phase = 0;
      ticks = 0;
    }
    return false;
  }
}
