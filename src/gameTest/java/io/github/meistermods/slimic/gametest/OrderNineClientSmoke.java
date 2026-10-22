package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.client.SlimeScreen;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.ModList;

final class OrderNineClientSmoke {
  private static int phase, ticks;

  static boolean tick(Minecraft c) {
    if (phase == 4) return true;
    if (phase == 0) {
      phase = 1;
      c.getSingleplayerServer()
          .execute(
              () -> {
                var owner = c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
                var level = owner.serverLevel();
                level
                    .getEntitiesOfClass(FriendlySlime.class, owner.getBoundingBox().inflate(32))
                    .forEach(FriendlySlime::discard);
                var soggy = ModEntities.SOGGY_SLIME.get().create(level);
                soggy.tame(owner);
                soggy.moveTo(10.5, 101, 8.5);
                soggy.setNoAi(true);
                soggy.setWaiting(false);
                soggy.setOnGround(true);
                level.setBlockAndUpdate(soggy.blockPosition(), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(
                    soggy.blockPosition().below(), Blocks.STONE.defaultBlockState());
                level
                    .getEntitiesOfClass(
                        net.minecraft.world.entity.decoration.ArmorStand.class,
                        owner.getBoundingBox().inflate(32))
                    .forEach(net.minecraft.world.entity.Entity::discard);
                level.addFreshEntity(soggy);
                owner.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
                owner.getInventory().setItem(1, new ItemStack(ModItems.SOGGY_SLIME.get()));
                owner.getInventory().setItem(2, new ItemStack(ModItems.SLIME_SLAG.get(), 4));
                owner.getInventory().setItem(3, new ItemStack(ModItems.SLIME_TRAIL.get()));
                owner.teleportTo(level, 7.5, 101, 8.5, -90, 12);
              });
      return false;
    }
    if (phase == 1) {
      var pets =
          c.level.getEntitiesOfClass(SoggySlime.class, c.player.getBoundingBox().inflate(12));
      if (pets.isEmpty()
          || !c.level.getBlockState(new BlockPos(10, 101, 8)).is(ModBlocks.SLIME_TRAIL.get())
          || ++ticks < 30) return false;
      var pet = pets.get(0);
      if (ticks == 30)
        c.getSingleplayerServer()
            .execute(
                () -> {
                  var e = c.getSingleplayerServer().overworld().getEntity(pet.getUUID());
                  if (e != null) e.moveTo(11.5, 101, 8.5);
                });
      if (ticks < 45) return false;
      var model =
          c.getItemRenderer()
              .getModel(new ItemStack(ModItems.SOGGY_SLIME.get()), c.level, c.player, 0);
      if (model == c.getModelManager().getMissingModel()
          || !model.getParticleIcon().contents().name().getPath().equals("item/friendly_slime"))
        throw new IllegalStateException("Soggy summon shared base model");
      var slag =
          c.getItemRenderer()
              .getModel(new ItemStack(ModItems.SLIME_SLAG.get()), c.level, c.player, 0);
      if (!slag.getParticleIcon().contents().name().getPath().equals("item/slime_slag"))
        throw new IllegalStateException("Slag texture missing");
      if (ModList.get().isLoaded("jei")
          && (JeiClientSmoke.soggyRecipes() != 1 || JeiClientSmoke.slagRecipes() != 1))
        return false;
      shot(c, "soggy-trail");
      c.gameMode.interact(c.player, pet, InteractionHand.MAIN_HAND);
      phase = 2;
      ticks = 0;
      return false;
    }
    if (phase == 2) {
      if (!(c.screen instanceof SlimeScreen s) || !s.getMenu().isSynchronized() || ++ticks < 20)
        return false;
      if (!(s.getMenu().slime() instanceof SoggySlime) || s.getMenu().slots.size() != 36)
        throw new IllegalStateException("Soggy menu has no equipment or hidden bag slots");
      shot(c, "soggy-menu");
      c.gameMode.handleInventoryButtonClick(s.getMenu().containerId, SlimeMenu.ROAM);
      phase = 5;
      ticks = 0;
      return false;
    }
    if (phase == 5) {
      if (!(c.screen instanceof SlimeScreen s)
          || s.getMenu().data(SlimeMenu.MODE) != SlimeMenu.ROAM
          || ++ticks < 20) return false;
      boolean found =
          s.children().stream()
              .anyMatch(
                  w ->
                      w instanceof net.minecraft.client.gui.components.Button b
                          && b.getMessage()
                              .equals(
                                  net.minecraft.network.chat.Component.translatable(
                                      "screen.slimic.roam"))
                          && !b.active);
      if (!found)
        throw new IllegalStateException("Roam button must reflect the synchronized order");
      for (var widget : s.children()) {
        if (widget instanceof net.minecraft.client.gui.components.Button button
            && button
                .getMessage()
                .equals(net.minecraft.network.chat.Component.translatable("screen.slimic.roam"))
            && c.font.width(button.getMessage()) > button.getWidth() - 6)
          throw new IllegalStateException("Roam label must fit without scrolling");
      }
      shot(c, "soggy-roaming");
      c.gameMode.handleInventoryButtonClick(s.getMenu().containerId, SlimeMenu.WAIT);
      phase = 6;
      ticks = 0;
      return false;
    }
    if (phase == 6) {
      if (!(c.screen instanceof SlimeScreen s)
          || s.getMenu().data(SlimeMenu.MODE) != SlimeMenu.WAIT
          || ++ticks < 15) return false;
      c.gameMode.handleInventoryButtonClick(s.getMenu().containerId, SlimeMenu.FOLLOW);
      phase = 7;
      ticks = 0;
      return false;
    }
    if (phase == 7) {
      if (!(c.screen instanceof SlimeScreen s)
          || s.getMenu().data(SlimeMenu.MODE) != SlimeMenu.FOLLOW
          || ++ticks < 15) return false;
      c.player.closeContainer();
      c.getSingleplayerServer()
          .execute(
              () -> {
                var owner = c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
                var level = owner.serverLevel();
                level
                    .getEntitiesOfClass(FriendlySlime.class, owner.getBoundingBox().inflate(32))
                    .forEach(FriendlySlime::discard);
                level.setBlockAndUpdate(new BlockPos(10, 101, 8), Blocks.AIR.defaultBlockState());
                var miner = ModEntities.MINING_SLIME.get().create(level);
                miner.setWorking(true);
                miner.tame(owner);
                miner.moveTo(10.5, 101, 8.5);
                miner.setTool(0, new ItemStack(Items.IRON_PICKAXE));
                miner.onReleased();
                miner.setWorking(true);
                level.setBlockAndUpdate(new BlockPos(12, 102, 8), Blocks.WATER.defaultBlockState());
                level.setBlockAndUpdate(
                    new BlockPos(12, 101, 8), Blocks.BEDROCK.defaultBlockState());
                level.addFreshEntity(miner);
              });
      phase = 3;
      ticks = 0;
      return false;
    }
    if (phase == 3) {
      var pets =
          c.level.getEntitiesOfClass(MiningSlime.class, c.player.getBoundingBox().inflate(12));
      if (pets.isEmpty()) return false;
      var pet = pets.get(0);
      if (c.screen == null) {
        c.gameMode.interact(c.player, pet, InteractionHand.MAIN_HAND);
        return false;
      }
      if (!(c.screen instanceof SlimeScreen s) || !s.getMenu().isSynchronized() || ++ticks < 20)
        return false;
      if (s.getMenu().data(io.github.meistermods.slimic.menu.SlimeMenu.STATUS)
          != MiningSlime.Status.NO_BUCKET.ordinal()) return false;
      shot(c, "mining-no-bucket");
      c.player.closeContainer();
      c.getSingleplayerServer()
          .execute(
              () -> {
                var level = c.getSingleplayerServer().overworld();
                var miner = (MiningSlime) level.getEntity(pet.getUUID());
                level.setBlockAndUpdate(new BlockPos(12, 102, 8), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(16, 102, 8), Blocks.WATER.defaultBlockState());
                miner.moveTo(14, 101, 8.5);
                miner.setWorking(true);
              });
      phase = 8;
      ticks = 0;
      return false;
    }
    if (phase == 8) {
      var pets =
          c.level.getEntitiesOfClass(MiningSlime.class, c.player.getBoundingBox().inflate(12));
      if (pets.isEmpty()) return false;
      if (c.screen == null) {
        c.gameMode.interact(c.player, pets.get(0), InteractionHand.MAIN_HAND);
        return false;
      }
      if (!(c.screen instanceof SlimeScreen s)
          || !s.getMenu().isSynchronized()
          || s.getMenu().data(SlimeMenu.STATUS) != MiningSlime.Status.NO_COBBLESTONE.ordinal()
          || ++ticks < 20) return false;
      shot(c, "mining-no-cobblestone");
      c.player.closeContainer();
      System.out.println(
          "SLIMIC ORDER NINE CLIENT SMOKE PASSED: soggy shared summon model, slag sprite, trail"
              + " rendering, equipment-free menu, roaming/follow/wait synchronization, bucket and"
              + " cobblestone request UI, JEI="
              + ModList.get().isLoaded("jei"));
      phase = 4;
      return true;
    }
    return false;
  }

  private static void shot(Minecraft c, String label) {
    var path =
        c.gameDirectory
            .toPath()
            .resolve(
                "../verification/0.1.24-"
                    + (ModList.get().isLoaded("jei") ? "jei-" : "no-jei-")
                    + label
                    + ".png")
            .normalize();
    try (var image = Screenshot.takeScreenshot(c.getMainRenderTarget())) {
      java.nio.file.Files.createDirectories(path.getParent());
      image.writeToFile(path);
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
