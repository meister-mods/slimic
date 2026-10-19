package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.block.SlimeChestBlock;
import io.github.meistermods.slimic.block.SlimeChestBlockEntity;
import io.github.meistermods.slimic.client.CreativeSlimeTunerScreen;
import io.github.meistermods.slimic.client.SlimeScreen;
import io.github.meistermods.slimic.entity.CleaningSlime;
import io.github.meistermods.slimic.entity.CombatSlime;
import io.github.meistermods.slimic.entity.CombatTarget;
import io.github.meistermods.slimic.entity.HeraldSlime;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.entity.TransportSlime;
import io.github.meistermods.slimic.item.CreativeSlimeTunerItem;
import io.github.meistermods.slimic.item.SlimeArmorItem;
import io.github.meistermods.slimic.menu.CreativeSlimeTunerMenu;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.ModBlocks;
import io.github.meistermods.slimic.registry.ModCreativeTabs;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
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
    int timeout = 3600;
    if (Boolean.getBoolean("slimic.workAreaOnly")) timeout = WorkAreaClientSmoke.maximumTicks();
    if (Boolean.getBoolean("slimic.accessoryTexturesOnly"))
      timeout +=
          400
              * (Math.max(1, Math.min(100, Integer.getInteger("slimic.textureReloadCycles", 1)))
                  - 1);
    if (++ticks > timeout)
      throw new IllegalStateException("Client smoke timed out in phase " + phase);
    if (client.level == null
        && client.screen != null
        && client
            .screen
            .getTitle()
            .getString()
            .equals(
                Component.translatable("selectWorld.backupQuestion.experimental").getString())) {
      client.screen.children().stream()
          .filter(Button.class::isInstance)
          .map(Button.class::cast)
          .filter(
              b ->
                  b.getMessage()
                      .getString()
                      .equals(Component.translatable("gui.proceed").getString()))
          .findFirst()
          .ifPresent(Button::onPress);
    }
    if (client.level == null || client.player == null || client.getSingleplayerServer() == null)
      return;
    if (Boolean.getBoolean("slimic.worldOnly")) {
      if (SlimicWorldClientSmoke.tick(client)) client.stop();
      return;
    }
    if (Boolean.getBoolean("slimic.workAreaOnly")) {
      if (WorkAreaClientSmoke.tick(client)) client.stop();
      return;
    }
    if (Boolean.getBoolean("slimic.companionTooltipsOnly")) {
      if (CompanionTooltipClientSmoke.tick(client)) client.stop();
      return;
    }
    if (Boolean.getBoolean("slimic.accessoryTexturesOnly")) {
      if (AccessoryTextureClientSmoke.tick(client)) client.stop();
      return;
    }
    if (Boolean.getBoolean("slimic.inventoryLayoutOnly")) {
      if (InventoryLayoutClientSmoke.tick(client)) client.stop();
      return;
    }
    if (Boolean.getBoolean("slimic.attentionBaseOnly")) {
      if (AttentionBaseClientSmoke.tick(client)) client.stop();
      return;
    }
    if (Boolean.getBoolean("slimic.orderThirteenOnly")) {
      if (OrderThirteenClientSmoke.tick(client)) client.stop();
      return;
    }
    if (Boolean.getBoolean("slimic.orderTwelveOnly")) {
      if (OrderTwelveClientSmoke.tick(client)) client.stop();
      return;
    }
    if (Boolean.getBoolean("slimic.guidebookOnly")) {
      if (GuidebookClientSmoke.tick(client)) client.stop();
      return;
    }
    if (Boolean.getBoolean("slimic.orderElevenOnly")) {
      if (OrderElevenClientSmoke.tick(client)) client.stop();
      return;
    }
    if (Boolean.getBoolean("slimic.saddleOnly")) {
      SaddleClientSmoke.tick(client);
      return;
    }
    if (phase >= 13) {
      newCompanions(client);
      return;
    }
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
                owner.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
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
                var stage = new net.minecraft.world.phys.AABB(0, 90, 0, 20, 320, 20);
                level
                    .getEntitiesOfClass(net.minecraft.world.entity.Mob.class, stage)
                    .forEach(net.minecraft.world.entity.Entity::discard);
                level
                    .getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, stage)
                    .forEach(net.minecraft.world.entity.Entity::discard);
                owner.getInventory().clearContent();
                owner.inventoryMenu.broadcastChanges();
                owner.setGameMode(GameType.CREATIVE);
                owner.teleportTo(level, 7.5, 101, 8.5, -90, 15);
                MiningSlime miner = ModEntities.MINING_SLIME.get().create(level);
                miner.setWorking(true);
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
                state.putInt("ActivityProgress", 17);
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
      if (phaseTicks < 10) {
        client.player.setYRot(-90);
        client.player.setXRot(15);
      }
      if (++phaseTicks < 50 || miner.getPickaxe().isEmpty()) return;
      if (phaseTicks == 50) {
        screenshot(client, jei ? "jei-world" : "no-jei-world");
        camera(client, 10.5, 5.5, 0);
        return;
      }
      if (phaseTicks == 75) {
        screenshot(client, jei ? "jei-mining-side" : "no-jei-mining-side");
        camera(client, 13.5, 8.5, 90);
        return;
      }
      if (phaseTicks == 100) {
        screenshot(client, jei ? "jei-mining-back" : "no-jei-mining-back");
        camera(client, 7.5, 8.5, -90);
        return;
      }
      if (phaseTicks < 125) return;
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
                owner.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
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
                owner.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
                var level = owner.serverLevel();
                if (level.getEntity(minerId) != null) level.getEntity(minerId).discard();
                TransportSlime carrier = ModEntities.TRANSPORT_SLIME.get().create(level);
                carrier.setWorking(true);
                carrier.moveTo(10.5, 101, 8.5, 90, 0);
                carrier.setYBodyRot(90);
                carrier.setYHeadRot(90);
                carrier.setNoAi(true);
                carrier.setNoAi(true);
                carrier.setYBodyRot(90);
                carrier.setYHeadRot(90);
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
      if (carrier.getCapacity() != 54) return;
      phaseTicks++;
      if (phaseTicks == 30) {
        screenshot(client, prefix + "transport-world");
        camera(client, 10.5, 5.5, 0);
        return;
      }
      if (phaseTicks == 55) {
        screenshot(client, prefix + "transport-side");
        camera(client, 13.5, 8.5, 90);
        return;
      }
      if (phaseTicks == 80) {
        screenshot(client, prefix + "transport-back");
        camera(client, 7.5, 8.5, -90);
        return;
      }
      if (phaseTicks < 105) return;
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
                owner.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
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
                owner.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
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
                owner.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
                var level = owner.serverLevel();
                if (level.getEntity(carrierId) != null) level.getEntity(carrierId).discard();
                HeraldSlime herald = ModEntities.HERALD_SLIME.get().create(level);
                herald.setMonitoring(true);
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
                owner.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
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
                owner.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(ModItems.CREATIVE_SLIME_TUNER.get()));
                owner.inventoryMenu.broadcastChanges();
                CreativeSlimeTunerItem.open(owner, (HeraldSlime) owner.serverLevel().getEntity(id));
              });
      phase = 11;
      phaseTicks = 0;
      return;
    }
    if (phase == 11 && client.screen instanceof CreativeSlimeTunerScreen screen) {
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
    if (phase == 12 && client.screen instanceof CreativeSlimeTunerScreen screen) {
      CreativeSlimeTunerMenu menu = screen.getMenu();
      if (menu.result() != 1
          || menu.health() != 35
          || menu.value(SlimeStat.WORK_SPEED) != 2.5
          || ++phaseTicks < 20) return;
      CreativeModeTabs.tryRebuildTabContents(
          client.level.enabledFeatures(), true, client.level.registryAccess());
      var items = ModCreativeTabs.SLIMIC.get().getDisplayItems();
      System.out.println(
          "SLIMIC CREATIVE CONTENTS: "
              + items.stream()
                  .map(
                      stack ->
                          net.minecraft.core.registries.BuiltInRegistries.ITEM
                              .getKey(stack.getItem())
                              .toString())
                  .toList());
      var registeredItems =
          ModItems.ITEMS.getEntries().stream()
              .map(value -> value.get())
              .collect(java.util.stream.Collectors.toSet());
      var expectedItems = new java.util.HashSet<>(registeredItems);
      expectedItems.remove(ModItems.SLIME_TELEPORTER.get());
      var displayedItems =
          items.stream()
              .map(ItemStack::getItem)
              .filter(registeredItems::contains)
              .collect(java.util.stream.Collectors.toSet());
      if (!displayedItems.equals(expectedItems)
          || items.size() != expectedItems.size() + (ModList.get().isLoaded("patchouli") ? 1 : 0))
        throw new IllegalStateException(
            "Creative tab must contain each listed item once and omit unbound teleporters");
      ItemStack miner =
          items.stream()
              .filter(stack -> stack.is(ModItems.MINING_SLIME.get()))
              .findFirst()
              .orElseThrow();
      if (miner.hasTag()) throw new IllegalStateException("Creative miner has initial equipment");
      if (jei && JeiClientSmoke.heraldRecipes() != 1) return;
      screenshot(client, prefix + "tuner");
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
                var level = owner.serverLevel();
                if (level.getEntity(id) != null) level.getEntity(id).discard();
                owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                CombatSlime fighter = ModEntities.COMBAT_SLIME.get().create(level);
                fighter.setWorking(true);
                fighter.moveTo(10.5, 101, 8.5, 90, 0);
                fighter.setYBodyRot(90);
                fighter.setYHeadRot(90);
                fighter.setNoAi(true);
                fighter
                    .getAttribute(
                        net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                    .setBaseValue(100);
                fighter.tame(owner);
                fighter.dye(owner, new ItemStack(Items.LIME_DYE));
                fighter.setCustomName(Component.literal("Guardian"));
                fighter.setCustomNameVisible(true);
                fighter.setEquipment(0, new ItemStack(Items.DIAMOND_SWORD));
                fighter.setEquipment(1, new ItemStack(Items.SHIELD));
                fighter.setEquipment(
                    2, SlimeArmorItem.fromChestplate(new ItemStack(Items.DIAMOND_CHESTPLATE)));
                level.addFreshEntity(fighter);
              });
      phase = 13;
      phaseTicks = 0;
    }
  }

  private static void newCompanions(Minecraft client) {
    if (phase >= 21) {
      if (FieldClientSmoke.tick(client)
          && OrderEightClientSmoke.tick(client)
          && OrderNineClientSmoke.tick(client)
          && OrderTenClientSmoke.tick(client)
          && OrderElevenClientSmoke.tick(client)) SaddleClientSmoke.tick(client);
      return;
    }
    boolean jei = ModList.get().isLoaded("jei");
    String prefix = jei ? "jei-" : "no-jei-";
    if (phase == 19) {
      if (++phaseTicks < 40) return;
      if (!client
          .getItemRenderer()
          .getModel(new ItemStack(ModItems.SLIME_CHEST.get()), client.level, client.player, 0)
          .isGui3d())
        throw new IllegalStateException("Slime Chest item must render its block model");
      if (!client
          .getItemRenderer()
          .getModel(new ItemStack(ModItems.BROKEN_SLIME.get()), client.level, client.player, 0)
          .isGui3d())
        throw new IllegalStateException("Recovery item must use its textured block model");
      var common =
          client
              .getItemRenderer()
              .getModel(
                  new ItemStack(ModItems.FRIENDLY_SLIME.get()), client.level, client.player, 0)
              .getParticleIcon()
              .contents()
              .name();
      for (var item :
          java.util.List.of(
              ModItems.MINING_SLIME.get(),
              ModItems.HEALING_SLIME.get(),
              ModItems.WAREHOUSE_SLIME.get(),
              ModItems.TRANSPORT_SLIME.get(),
              ModItems.HERALD_SLIME.get(),
              ModItems.COMBAT_SLIME.get(),
              ModItems.CLEANING_SLIME.get(),
              ModItems.SADDLE_SLIME.get(),
              ModItems.FARMER_SLIME.get(),
              ModItems.LUMBERJACK_SLIME.get(),
              ModItems.GUARDIAN_ANGEL_SLIME.get()))
        if (!client
            .getItemRenderer()
            .getModel(new ItemStack(item), client.level, client.player, 0)
            .getParticleIcon()
            .contents()
            .name()
            .equals(common))
          throw new IllegalStateException("All companion models must share their summoning icon");
      for (var emblem :
          java.util.Map.of(
                  ModItems.MINING_SLIME.get(),
                  "iron_pickaxe",
                  ModItems.TRANSPORT_SLIME.get(),
                  "bundle",
                  ModItems.HERALD_SLIME.get(),
                  "firework_rocket",
                  ModItems.COMBAT_SLIME.get(),
                  "iron_sword",
                  ModItems.CLEANING_SLIME.get(),
                  "brush",
                  ModItems.SADDLE_SLIME.get(),
                  "saddle",
                  ModItems.FARMER_SLIME.get(),
                  "iron_hoe",
                  ModItems.LUMBERJACK_SLIME.get(),
                  "iron_axe")
              .entrySet()) {
        var expected =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "minecraft", "item/" + emblem.getValue());
        var model =
            client
                .getItemRenderer()
                .getModel(new ItemStack(emblem.getKey()), client.level, client.player, 0);
        if (model.getQuads(null, null, net.minecraft.util.RandomSource.create()).stream()
            .noneMatch(quad -> quad.getSprite().contents().name().equals(expected)))
          throw new IllegalStateException("Missing profession crest: " + emblem.getValue());
      }
      screenshot(client, prefix + "chest-models-and-icons");
      var vanished = ModEntities.TRANSPORT_SLIME.get().create(client.level);
      vanished.setWorking(true);
      FriendlyByteBuf opening = new FriendlyByteBuf(Unpooled.buffer());
      try {
        SlimeMenu.writeOpeningData(opening, vanished);
        SlimeMenu stale = TestMenus.create(123, client.player.getInventory(), opening);
        if (stale.slots.size() != 90 || !stale.slime().isRemoved())
          throw new IllegalStateException("Missing companion must retain the carrier slot layout");
        client.player.containerMenu = stale;
        client.setScreen(
            new SlimeScreen(stale, client.player.getInventory(), vanished.getDisplayName()));
      } finally {
        opening.release();
      }
      phase = 20;
      phaseTicks = 0;
      return;
    }
    if (phase == 20 && ++phaseTicks >= 5) {
      if (client.screen != null || client.player.containerMenu instanceof SlimeMenu)
        throw new IllegalStateException("Missing companion menu must close safely");
      System.out.println(
          "SLIMIC CLIENT SMOKE PASSED: shared UI, equipment, dye, all specializations, 16px item"
              + " and armor textures, four chest facings and native block item, herald hat; JEI="
              + jei);
      phase = 21;
      return;
    }
    if (phase <= 16) {
      var fighters =
          client.level.getEntitiesOfClass(
              CombatSlime.class, client.player.getBoundingBox().inflate(12));
      if (fighters.isEmpty()) return;
      CombatSlime fighter = fighters.get(0);
      if (phase == 13) {
        if (fighter.getEquipment(2).isEmpty() || ++phaseTicks < 30) return;
        if (phaseTicks == 30) {
          screenshot(client, prefix + "combat-world");
          camera(client, 10.5, 5.5, 0);
          return;
        }
        if (phaseTicks == 55) {
          screenshot(client, prefix + "combat-side");
          camera(client, 13.5, 8.5, 90);
          return;
        }
        if (phaseTicks == 80) {
          screenshot(client, prefix + "combat-back");
          camera(client, 7.5, 8.5, -90);
          return;
        }
        if (phaseTicks < 105) return;
        int id = fighter.getId();
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
                  ((CombatSlime) owner.serverLevel().getEntity(id))
                      .mobInteract(owner, InteractionHand.MAIN_HAND);
                });
        phase = 14;
        phaseTicks = 0;
        return;
      }
      if (phase == 14 && client.screen instanceof SlimeScreen screen) {
        if (!screen.getMenu().isSynchronized()) return;
        if (screen.getMenu().value(SlimeStat.MOVEMENT_SPEED) != 100)
          throw new IllegalStateException(
              "Large native movement stat was truncated in the management screen");
        if (screen.getMenu().slots.size() != 66
            || screen.getMenu().getSlot(2).getItem().isEmpty()
            || ++phaseTicks < 20) return;
        screenshot(client, prefix + "combat-menu");
        client.gameMode.handleInventoryButtonClick(
            screen.getMenu().containerId, SlimeMenu.TARGET_PAGE_BUTTON);
        phase = 15;
        phaseTicks = 0;
        return;
      }
      if (phase == 15 && client.screen instanceof SlimeScreen screen) {
        if (screen.getMenu().data(SlimeMenu.TARGET_PAGE) != 1 || ++phaseTicks < 20) return;
        if (screen.getMenu().getSlot(3).isActive())
          throw new IllegalStateException("Filters overlap bag slots");
        screen.children().stream()
            .filter(Button.class::isInstance)
            .map(Button.class::cast)
            .filter(
                button ->
                    button.visible
                        && button
                            .getMessage()
                            .getString()
                            .startsWith(
                                Component.translatable("target.slimic.creepers").getString()))
            .findFirst()
            .orElseThrow()
            .onPress();
        phase = 16;
        phaseTicks = 0;
        return;
      }
      if (phase == 16 && client.screen instanceof SlimeScreen screen) {
        if ((screen.getMenu().data(SlimeMenu.FILTERS) & (1 << CombatTarget.CREEPERS.ordinal())) == 0
            || ++phaseTicks < 20) return;
        if (jei
            && (JeiClientSmoke.combatRecipes() < 6
                || JeiClientSmoke.armorRecipes() < 6
                || JeiClientSmoke.cleaningRecipes() != 1)) return;
        screenshot(client, prefix + "combat-targets");
        client.player.closeContainer();
        int id = fighter.getId();
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
                  var level = owner.serverLevel();
                  if (level.getEntity(id) != null) level.getEntity(id).discard();
                  CleaningSlime cleaner = ModEntities.CLEANING_SLIME.get().create(level);
                  cleaner.setWorking(true);
                  cleaner.moveTo(10.5, 101, 8.5, 90, 0);
                  cleaner.setNoAi(true);
                  cleaner.tame(owner);
                  cleaner.dye(owner, new ItemStack(Items.CYAN_DYE));
                  cleaner.setCustomName(Component.literal("Sweep"));
                  cleaner.setCustomNameVisible(true);
                  cleaner.getBag().setStackInSlot(0, new ItemStack(Items.ROTTEN_FLESH, 17));
                  level.addFreshEntity(cleaner);
                  cleaner.onReleased();
                  int slot = 0;
                  for (var chestplate :
                      new net.minecraft.world.item.Item[] {
                        Items.LEATHER_CHESTPLATE,
                        Items.CHAINMAIL_CHESTPLATE,
                        Items.IRON_CHESTPLATE,
                        Items.GOLDEN_CHESTPLATE,
                        Items.DIAMOND_CHESTPLATE,
                        Items.NETHERITE_CHESTPLATE
                      })
                    owner
                        .getInventory()
                        .setItem(slot++, SlimeArmorItem.fromChestplate(new ItemStack(chestplate)));
                  owner.getInventory().setItem(6, new ItemStack(ModItems.COMBAT_SLIME.get()));
                  owner.getInventory().setItem(7, new ItemStack(ModItems.CLEANING_SLIME.get()));
                  owner.inventoryMenu.broadcastChanges();
                });
        phase = 17;
        phaseTicks = 0;
        return;
      }
    }
    var cleaners =
        client.level.getEntitiesOfClass(
            CleaningSlime.class, client.player.getBoundingBox().inflate(12));
    if (cleaners.isEmpty()) return;
    CleaningSlime cleaner = cleaners.get(0);
    if (phase == 17) {
      if (SlimeStat.DETECTION_RANGE.value(cleaner) != 40 || ++phaseTicks < 40) return;
      screenshot(client, prefix + "cleaning-world");
      int id = cleaner.getId();
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
                ((CleaningSlime) owner.serverLevel().getEntity(id))
                    .mobInteract(owner, InteractionHand.MAIN_HAND);
              });
      phase = 18;
      phaseTicks = 0;
      return;
    }
    if (phase == 18 && client.screen instanceof SlimeScreen screen) {
      if (screen.getMenu().value(SlimeStat.DETECTION_RANGE) != 40
          || screen.getMenu().getSlot(0).getItem().getCount() != 17
          || ++phaseTicks < 20) return;
      screenshot(client, prefix + "cleaning-menu");
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
                owner.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
                var level = owner.serverLevel();
                int index = 0;
                for (var facing : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                  level.setBlockAndUpdate(
                      new BlockPos(10 + index % 2 * 2, 101, 7 + index / 2 * 3),
                      ModBlocks.SLIME_CHEST
                          .get()
                          .defaultBlockState()
                          .setValue(SlimeChestBlock.FACING, facing));
                  index++;
                }
                level.setBlockAndUpdate(
                    new BlockPos(10, 101, 9), ModBlocks.BROKEN_SLIME.get().defaultBlockState());
                int slot = 0;
                for (var item :
                    new net.minecraft.world.item.Item[] {
                      ModItems.SLIME_CHEST.get(), ModItems.FRIENDLY_SLIME.get(),
                          ModItems.MINING_SLIME.get(),
                      ModItems.TRANSPORT_SLIME.get(), ModItems.HERALD_SLIME.get(),
                          ModItems.COMBAT_SLIME.get(),
                      ModItems.CLEANING_SLIME.get(), ModItems.SADDLE_SLIME.get(),
                          ModItems.BROKEN_SLIME.get()
                    }) owner.getInventory().setItem(slot++, new ItemStack(item));
                owner.inventoryMenu.broadcastChanges();
                owner.teleportTo(level, 7.5, 103, 5.5, -40, 30);
              });
      phase = 19;
      phaseTicks = 0;
    }
  }

  private static void camera(Minecraft client, double x, double z, float yaw) {
    client
        .getSingleplayerServer()
        .execute(
            () -> {
              var owner =
                  client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
              owner.setItemInHand(
                  InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
              owner.teleportTo(owner.serverLevel(), x, 101, z, yaw, 15);
            });
  }

  private static void screenshot(Minecraft client, String suffix) {
    Path target =
        client
            .gameDirectory
            .toPath()
            .resolve("../verification/0.1.24-" + suffix + ".png")
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
