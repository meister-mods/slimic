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
  private static int phase, ticks, index, language, completedCycles;
  private static UUID identity;
  private static String runId = UUID.randomUUID().toString().substring(0, 8);
  private static final int SESSION_CYCLES =
      Math.max(1, Math.min(60, Integer.getInteger("slimic.workAreaCycles", 1)));
  private static CompletableFuture<?> task;
  private static final List<EntityType<? extends FriendlySlime>> TYPES =
      List.of(
          ModEntities.MINING_SLIME.get(),
          ModEntities.TUNNEL_SLIME.get(),
          ModEntities.FARMER_SLIME.get(),
          ModEntities.TRANSPORT_SLIME.get(),
          ModEntities.CLEANING_SLIME.get(),
          ModEntities.COMBAT_SLIME.get(),
          ModEntities.DRAGON_SLIME.get());

  private static String locale() {
    return language == 0 ? "en_us" : "ko_kr";
  }

  public static int maximumTicks() {
    return SESSION_CYCLES * 3600;
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

  private static void checkPacketLifecycle(Minecraft c, WorkAreaPacket valid) {
    var tick =
        new net.minecraftforge.event.TickEvent.ClientTickEvent(
            net.minecraftforge.event.TickEvent.Phase.END);
    WorkAreaPreview.receive(
        new WorkAreaPacket(valid.dimension(), valid.entityId(), UUID.randomUUID(), valid.area()));
    WorkAreaPreview.tick(tick);
    check(active() == null, "An old entity ID cannot reveal another companion's area");
    WorkAreaPreview.receive(
        new WorkAreaPacket(valid.dimension(), -1, valid.companion(), valid.area()));
    WorkAreaPreview.tick(tick);
    check(active() == null, "An unloaded or removed companion clears its preview");
    WorkAreaPreview.receive(
        new WorkAreaPacket(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("slimic", "other_world"),
            valid.entityId(),
            valid.companion(),
            valid.area()));
    WorkAreaPreview.tick(tick);
    check(active() == null, "A late packet from another dimension is discarded");
    WorkAreaPreview.receive(valid);
    var held = c.player.getMainHandItem();
    c.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    WorkAreaPreview.tick(tick);
    check(active() == null, "A late packet cannot restore a preview without a tuner");
    c.player.setItemInHand(InteractionHand.MAIN_HAND, held);
    WorkAreaPreview.receive(valid);
    WorkAreaPreview.tick(tick);
    check(active() != null, "The valid current companion remains previewable");
    WorkAreaPreview.receive(
        new WorkAreaPacket(
            valid.dimension(),
            valid.entityId(),
            valid.companion(),
            new SlimeWorkArea(true, List.of())));
    check(active() == null, "An unsupported companion clears the previous area");
    WorkAreaPreview.receive(valid);
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
            farmer.moveTo(5.5, 101, 8.5);
            farmer.onReleased();
            farmer.setWorking(true);
            farmer.setWaiting(true);
            farmer.moveTo(8.5, 101, 8.5);
            farmer.onReleased();
            farmer.setWorking(true);
            farmer.load(farmer.saveWithoutId(new CompoundTag()));
            farmer.moveTo(11.5, 101, 8.5);
          }
          if (pet instanceof TransportSlime transport) transport.setWorking(true);
          if (pet instanceof CombatSlime fighter) {
            fighter.setItemSlot(
                net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                new ItemStack(Items.DIAMOND_SWORD));
            fighter.setItemSlot(
                net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            fighter.setItemSlot(
                net.minecraft.world.entity.EquipmentSlot.CHEST,
                io.github.meistermods.slimic.item.SlimeArmorItem.fromChestplate(
                    new ItemStack(Items.DIAMOND_CHESTPLATE)));
            fighter.setWorking(true);
          }
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
      c.options.guiScale().set(2 + completedCycles % 3);
      if (SESSION_CYCLES > 1) {
        org.lwjgl.glfw.GLFW.glfwSetWindowSize(c.getWindow().getWindow(), 1280, 960);
        c.resizeDisplay();
        c.options.forceUnicodeFont().set(completedCycles % 2 == 0);
      }
      task = c.reloadResourcePacks();
      phase = 1;
      ticks = 0;
      return false;
    }
    if (phase == 1) {
      if (c.getOverlay() != null) return false;
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
      if (index == 2)
        check(
            preview
                .area()
                .shapes()
                .get(0)
                .bounds()
                .getCenter()
                .equals(new net.minecraft.world.phys.Vec3(8.5, 101.5, 8.5)),
            "A relocated farmer's preview stays centered on its registered field during travel");
      if (index == 0) checkPacketLifecycle(c, preview);
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
      if (ticks < 20 || c.player.getX() < 20 || c.getOverlay() != null) return false;
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
            miner.setCustomName(Component.literal("Qurry-" + locale() + "-" + runId));
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
              .filter(s -> s.contains("Qurry-" + locale() + "-" + runId))
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
              + ", "
              + TYPES.size()
              + " native Shift previews, visible outlines, tool-change clearing, expiry and"
              + " named herald chat");
      if (++language == 2) {
        System.out.println("SLIMIC WORK AREA SESSION CYCLE PASSED: " + ++completedCycles);
        if (completedCycles == SESSION_CYCLES) return true;
        language = 0;
        runId = UUID.randomUUID().toString().substring(0, 8);
      }
      index = 0;
      phase = 0;
      ticks = 0;
    }
    return false;
  }
}
