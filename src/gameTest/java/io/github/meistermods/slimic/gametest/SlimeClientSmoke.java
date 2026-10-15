package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.block.SlimeChestBlockEntity;
import io.github.meistermods.slimic.client.SlimeScreen;
import io.github.meistermods.slimic.client.TunerScreen;
import io.github.meistermods.slimic.entity.HeraldSlime;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.entity.TransportSlime;
import io.github.meistermods.slimic.item.SlimeTunerItem;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.menu.TunerMenu;
import io.github.meistermods.slimic.registry.ModBlocks;
import io.github.meistermods.slimic.registry.ModCreativeTabs;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModItems;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "slimic_tests", value = Dist.CLIENT)
public final class SlimeClientSmoke {
  private static int ticks;
  private static int phase;
  private static int phaseTicks;

  private SlimeClientSmoke() {}

  @SubscribeEvent
  public static void tick(TickEvent.ClientTickEvent event) {
    if (!Boolean.getBoolean("slimic.clientSmoke") || event.phase != TickEvent.Phase.END) return;
    Minecraft client = Minecraft.getInstance();
    if (++ticks > 2400) throw new IllegalStateException("Client smoke timed out in phase " + phase);
    if (client.level == null || client.player == null || client.getSingleplayerServer() == null)
      return;
    if (phase >= 8) {
      toolsSmoke(client);
      return;
    }
    if (phase >= 4) {
      transportSmoke(client);
      return;
    }
    if (phase == 0) {
      phase = 1;
      client
          .getSingleplayerServer()
          .execute(
              () -> {
                var server = client.getSingleplayerServer();
                var owner = server.getPlayerList().getPlayer(client.player.getUUID());
                if (owner == null) throw new IllegalStateException("Missing smoke player");
                var level = owner.serverLevel();
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                level.setDayTime(6000);
                for (int x = 3; x <= 15; x++)
                  for (int z = 3; z <= 14; z++) {
                    level.setBlockAndUpdate(
                        new BlockPos(x, 100, z), Blocks.SEA_LANTERN.defaultBlockState());
                    for (int y = 101; y < level.getMaxBuildHeight(); y++)
                      level.setBlockAndUpdate(
                          new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                  }
                owner.setGameMode(GameType.CREATIVE);
                owner.teleportTo(level, 7.5, 101, 8.5, -90, 15);
                MiningSlime miner = ModEntities.MINING_SLIME.get().create(level);
                miner.moveTo(10.5, 101, 8.5, 90, 0);
                miner.setYBodyRot(90);
                miner.setYHeadRot(90);
                miner.setNoAi(true);
                miner.tame(owner);
                miner.dye(owner, new ItemStack(Items.RED_DYE));
                miner.setTool(1, new ItemStack(Items.IRON_SHOVEL));
                miner.setWorking(false);
                miner.setCustomName(Component.literal("Quartz"));
                miner.setCustomNameVisible(true);
                miner.setHealth(12);
                ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
                tool.enchant(Enchantments.BLOCK_FORTUNE, 3);
                miner.setItemSlot(EquipmentSlot.MAINHAND, tool);
                miner.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
                miner.getBag().setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 32));
                miner.getBag().setStackInSlot(2, new ItemStack(Items.DIAMOND, 3));
                CompoundTag state = miner.saveWithoutId(new CompoundTag());
                state.putInt("StatPoints", 7);
                state.putInt("ActivityProgress", 71);
                miner.load(state);
                level.addFreshEntity(miner);
                miner.onReleased();
              });
      return;
    }
    var miners =
        client
            .level
            .getEntitiesOfClass(MiningSlime.class, client.player.getBoundingBox().inflate(12))
            .stream()
            .filter(
                entity ->
                    entity.hasCustomName()
                        && (entity.getName().getString().equals("Quartz")
                            || entity.getName().getString().equals("Atlas")))
            .toList();
    if (miners.isEmpty()) return;
    MiningSlime miner = miners.get(0);
    boolean jei = ModList.get().isLoaded("jei");
    if (phase == 1) {
      client.player.setYRot(-90);
      client.player.setXRot(15);
      if (++phaseTicks < 50 || miner.getPickaxe().isEmpty()) return;
      screenshot(client, jei ? "jei-world" : "no-jei-world");
      phase = 2;
      phaseTicks = 0;
      int entityId = miner.getId();
      client
          .getSingleplayerServer()
          .execute(
              () -> {
                var owner =
                    client
                        .getSingleplayerServer()
                        .getPlayerList()
                        .getPlayer(client.player.getUUID());
                if (owner.serverLevel().getEntity(entityId) instanceof MiningSlime companion)
                  companion.mobInteract(owner, InteractionHand.MAIN_HAND);
              });
      return;
    }
    if (!(client.screen instanceof SlimeScreen screen)) return;
    SlimeMenu menu = screen.getMenu();
    if (phase == 2) {
      if (menu.data(SlimeMenu.POINTS) != 7 || ++phaseTicks < 20) return;
      client.gameMode.handleInventoryButtonClick(menu.containerId, SlimeStat.HEALTH.ordinal());
      screen.children().stream()
          .filter(EditBox.class::isInstance)
          .map(EditBox.class::cast)
          .findFirst()
          .orElseThrow()
          .setValue("Atlas");
      screen.children().stream()
          .filter(Button.class::isInstance)
          .map(Button.class::cast)
          .filter(
              button ->
                  button
                      .getMessage()
                      .getString()
                      .equals(Component.translatable("screen.slimic.save").getString()))
          .findFirst()
          .orElseThrow()
          .onPress();
      phase = 3;
      phaseTicks = 0;
      return;
    }
    if (phase == 3) {
      if (menu.data(SlimeMenu.POINTS) != 6
          || menu.value(SlimeStat.HEALTH) != 20
          || !miner.getName().getString().equals("Atlas")) return;
      if (jei && JeiClientSmoke.miningRecipes() < 6) return;
      if (++phaseTicks < 20) return;
      screenshot(client, jei ? "jei-menu" : "no-jei-menu");
      phase = 4;
      phaseTicks = 0;
      client.player.closeContainer();
      int minerId = miner.getId();
      client
          .getSingleplayerServer()
          .execute(
              () -> {
                var server = client.getSingleplayerServer();
                var owner = server.getPlayerList().getPlayer(client.player.getUUID());
                var level = owner.serverLevel();
                if (level.getEntity(minerId) != null) level.getEntity(minerId).discard();
                TransportSlime carrier = ModEntities.TRANSPORT_SLIME.get().create(level);
                carrier.moveTo(10.5, 101, 8.5, 90, 0);
                carrier.setYBodyRot(90);
                carrier.setYHeadRot(90);
                carrier.setNoAi(true);
                carrier.tame(owner);
                carrier.dye(owner, new ItemStack(Items.BLUE_DYE));
                carrier.setWorking(false);
                carrier.setCustomName(Component.literal("Porter"));
                carrier.setCustomNameVisible(true);
                CompoundTag state = carrier.saveWithoutId(new CompoundTag());
                state.putInt("StatPoints", 6);
                carrier.load(state);
                for (int index = 0; index < 6; index++) carrier.upgradeStat(SlimeStat.CAPACITY);
                carrier.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
                carrier.getBag().setStackInSlot(27, new ItemStack(Items.IRON_PICKAXE));
                carrier.getBag().setStackInSlot(53, new ItemStack(Items.EMERALD, 17));
                level.addFreshEntity(carrier);
                carrier.onReleased();
                BlockPos chestPos = new BlockPos(11, 101, 10);
                level.setBlockAndUpdate(chestPos, ModBlocks.SLIME_CHEST.get().defaultBlockState());
                SlimeChestBlockEntity chest =
                    (SlimeChestBlockEntity) level.getBlockEntity(chestPos);
                chest.setItem(0, new ItemStack(Items.DIAMOND, 12));
                chest.setItem(1, new ItemStack(Items.DIAMOND_PICKAXE));
              });
    }
  }

  private static void transportSmoke(Minecraft client) {
    boolean jei = ModList.get().isLoaded("jei");
    String prefix = jei ? "jei-" : "no-jei-";
    var carriers =
        client.level.getEntitiesOfClass(
            TransportSlime.class, client.player.getBoundingBox().inflate(12));
    if (carriers.isEmpty()) return;
    TransportSlime carrier = carriers.get(0);
    if (phase == 4) {
      if (++phaseTicks < 50 || carrier.getCapacity() != 54) return;
      screenshot(client, prefix + "transport-world");
      int entityId = carrier.getId();
      client
          .getSingleplayerServer()
          .execute(
              () -> {
                var owner =
                    client
                        .getSingleplayerServer()
                        .getPlayerList()
                        .getPlayer(client.player.getUUID());
                if (owner.serverLevel().getEntity(entityId) instanceof TransportSlime companion)
                  companion.mobInteract(owner, InteractionHand.MAIN_HAND);
              });
      phase = 5;
      phaseTicks = 0;
      return;
    }
    if (phase == 5 && client.screen instanceof SlimeScreen screen) {
      SlimeMenu menu = screen.getMenu();
      if (menu.value(SlimeStat.CAPACITY) != 54 || ++phaseTicks < 20) return;
      if (menu.slots.size() != 90 || !carrier.getMainHandItem().isEmpty())
        throw new IllegalStateException("Transport has an unexpected equipment slot");
      screen.children().stream()
          .filter(Button.class::isInstance)
          .map(Button.class::cast)
          .filter(button -> button.getMessage().getString().equals(">"))
          .findFirst()
          .orElseThrow()
          .onPress();
      phase = 6;
      phaseTicks = 0;
      return;
    }
    if (phase == 6 && client.screen instanceof SlimeScreen screen) {
      SlimeMenu menu = screen.getMenu();
      if (menu.data(SlimeMenu.BAG_PAGE) != 1
          || !menu.getSlot(53).getItem().is(Items.EMERALD)
          || ++phaseTicks < 20) return;
      if (jei && (JeiClientSmoke.transportRecipes() != 1 || JeiClientSmoke.chestRecipes() != 1))
        return;
      screenshot(client, prefix + "transport-menu");
      client.player.closeContainer();
      client
          .getSingleplayerServer()
          .execute(
              () -> {
                var owner =
                    client
                        .getSingleplayerServer()
                        .getPlayerList()
                        .getPlayer(client.player.getUUID());
                owner.openMenu(
                    (SlimeChestBlockEntity)
                        owner.serverLevel().getBlockEntity(new BlockPos(11, 101, 10)));
              });
      phase = 7;
      phaseTicks = 0;
      return;
    }
    if (phase == 7 && client.player.containerMenu instanceof ChestMenu menu) {
      if (!menu.getSlot(0).getItem().is(Items.DIAMOND) || ++phaseTicks < 20) return;
      screenshot(client, prefix + "chest-menu");
      client.player.closeContainer();
      int carrierId = carrier.getId();
      client
          .getSingleplayerServer()
          .execute(
              () -> {
                var owner =
                    client
                        .getSingleplayerServer()
                        .getPlayerList()
                        .getPlayer(client.player.getUUID());
                var level = owner.serverLevel();
                if (level.getEntity(carrierId) != null) level.getEntity(carrierId).discard();
                HeraldSlime herald = ModEntities.HERALD_SLIME.get().create(level);
                herald.moveTo(10.5, 101, 8.5, 90, 0);
                herald.setNoAi(true);
                herald.tame(owner);
                herald.dye(owner, new ItemStack(Items.WHITE_DYE));
                herald.setCustomName(Component.literal("Beacon"));
                herald.setCustomNameVisible(true);
                herald.setDelayMinutes(3);
                level.addFreshEntity(herald);
              });
      phase = 8;
      phaseTicks = 0;
    }
  }

  private static void toolsSmoke(Minecraft client) {
    boolean jei = ModList.get().isLoaded("jei");
    String prefix = jei ? "jei-" : "no-jei-";
    var heralds =
        client.level.getEntitiesOfClass(
            HeraldSlime.class, client.player.getBoundingBox().inflate(12));
    if (heralds.isEmpty()) return;
    HeraldSlime herald = heralds.get(0);
    if (phase == 8) {
      if (herald.getDyeColorId() != DyeColor.WHITE.getId() || ++phaseTicks < 40) return;
      screenshot(client, prefix + "herald-world");
      int id = herald.getId();
      client
          .getSingleplayerServer()
          .execute(
              () -> {
                var owner =
                    client
                        .getSingleplayerServer()
                        .getPlayerList()
                        .getPlayer(client.player.getUUID());
                ((HeraldSlime) owner.serverLevel().getEntity(id))
                    .mobInteract(owner, InteractionHand.MAIN_HAND);
              });
      phase = 9;
      phaseTicks = 0;
      return;
    }
    if (phase == 9 && client.screen instanceof SlimeScreen screen) {
      if (screen.getMenu().data(SlimeMenu.ALERT_MINUTES) != 3 || ++phaseTicks < 20) return;
      client.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, SlimeMenu.DELAY_UP);
      phase = 10;
      phaseTicks = 0;
      return;
    }
    if (phase == 10 && client.screen instanceof SlimeScreen screen) {
      if (screen.getMenu().data(SlimeMenu.ALERT_MINUTES) != 4 || ++phaseTicks < 20) return;
      screenshot(client, prefix + "herald-menu");
      client.player.closeContainer();
      int id = herald.getId();
      client
          .getSingleplayerServer()
          .execute(
              () -> {
                var owner =
                    client
                        .getSingleplayerServer()
                        .getPlayerList()
                        .getPlayer(client.player.getUUID());
                owner.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
                owner.inventoryMenu.broadcastChanges();
                SlimeTunerItem.open(owner, (HeraldSlime) owner.serverLevel().getEntity(id));
              });
      phase = 11;
      phaseTicks = 0;
      return;
    }
    if (phase == 11 && client.screen instanceof TunerScreen screen) {
      if (screen.getMenu().value(SlimeStat.HEALTH) != 16 || ++phaseTicks < 20) return;
      var fields =
          screen.children().stream()
              .filter(EditBox.class::isInstance)
              .map(EditBox.class::cast)
              .toList();
      if (fields.size() != 12) throw new IllegalStateException("Tuner field count");
      fields.get(0).setValue("48");
      fields.get(2).setValue("12");
      fields.get(4).setValue("2.5");
      fields.get(5).setValue("12");
      fields.get(6).setValue("200");
      fields.get(9).setValue("35");
      fields.get(10).setValue("150");
      fields.get(11).setValue("12");
      screen.children().stream()
          .filter(Button.class::isInstance)
          .map(Button.class::cast)
          .filter(
              button ->
                  button
                      .getMessage()
                      .getString()
                      .equals(Component.translatable("screen.slimic.apply").getString()))
          .findFirst()
          .orElseThrow()
          .onPress();
      phase = 12;
      phaseTicks = 0;
      return;
    }
    if (phase == 12 && client.screen instanceof TunerScreen screen) {
      TunerMenu menu = screen.getMenu();
      if (menu.result() != 1
          || menu.health() != 35
          || menu.value(SlimeStat.WORK_SPEED) != 2.5
          || ++phaseTicks < 20) return;
      CreativeModeTabs.tryRebuildTabContents(
          client.level.enabledFeatures(), true, client.level.registryAccess());
      var items = ModCreativeTabs.SLIMIC.get().getDisplayItems();
      if (items.size() != 6
          || items.stream().noneMatch(stack -> stack.is(ModItems.SLIME_TUNER.get())))
        throw new IllegalStateException("Creative tab entries");
      ItemStack miner =
          items.stream()
              .filter(stack -> stack.is(ModItems.MINING_SLIME.get()))
              .findFirst()
              .orElseThrow();
      if (miner.hasTag()) throw new IllegalStateException("Creative miner has initial equipment");
      if (jei && JeiClientSmoke.heraldRecipes() != 1) return;
      screenshot(client, prefix + "tuner");
      System.out.println(
          "SLIMIC CLIENT SMOKE PASSED: rename, upgrades, mining, transport, 54-slot paging, chest,"
              + " red/blue/white dye rendering, Herald delay, tuner packet and UI, six creative"
              + " entries; JEI="
              + jei
              + (jei
                  ? ", mining="
                      + JeiClientSmoke.miningRecipes()
                      + ", transport="
                      + JeiClientSmoke.transportRecipes()
                      + ", herald="
                      + JeiClientSmoke.heraldRecipes()
                      + ", chest="
                      + JeiClientSmoke.chestRecipes()
                  : ""));
      phase = 13;
      client.stop();
    }
  }

  private static void screenshot(Minecraft client, String suffix) {
    Path target =
        client
            .gameDirectory
            .toPath()
            .resolve("../verification/0.1.5-" + suffix + ".png")
            .normalize();
    try {
      Files.createDirectories(target.getParent());
      try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
        image.writeToFile(target);
      }
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
