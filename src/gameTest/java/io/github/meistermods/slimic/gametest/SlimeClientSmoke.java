package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.client.SlimeScreen;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.ModEntities;
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
      System.out.println(
          "SLIMIC CLIENT SMOKE PASSED: rename, upgrade, synchronized menu, rendering; JEI="
              + jei
              + (jei ? ", mining recipes=" + JeiClientSmoke.miningRecipes() : ""));
      phase = 4;
      client.stop();
    }
  }

  private static void screenshot(Minecraft client, String suffix) {
    Path target =
        client
            .gameDirectory
            .toPath()
            .resolve("../verification/0.1.3-" + suffix + ".png")
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
