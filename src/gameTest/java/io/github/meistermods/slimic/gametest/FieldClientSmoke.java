package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.client.SlimeScreen;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.*;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;

/** Checks synchronized field-worker equipment/layouts and renders both profession hats. */
final class FieldClientSmoke {
  private static int phase, ticks;

  static boolean tick(Minecraft client) {
    boolean jei = ModList.get().isLoaded("jei");
    if (phase == 0) {
      phase = 1;
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
                level
                    .getEntitiesOfClass(FriendlySlime.class, owner.getBoundingBox().inflate(32))
                    .forEach(FriendlySlime::discard);
                for (int x = 9; x <= 14; x++)
                  for (int z = 7; z <= 14; z++)
                    for (int y = 101; y < 106; y++)
                      level.setBlockAndUpdate(
                          new net.minecraft.core.BlockPos(x, y, z),
                          net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                var farmer = ModEntities.FARMER_SLIME.get().create(level);
                farmer.moveTo(10.5, 101, 8.5, 90, 0);
                farmer.setNoAi(true);
                farmer.tame(owner);
                farmer.setTool(new ItemStack(Items.DIAMOND_HOE));
                farmer.getBag().setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS, 12));
                level.addFreshEntity(farmer);
                var logger = ModEntities.LUMBERJACK_SLIME.get().create(level);
                logger.setWorking(true);
                logger.moveTo(10.5, 101, 12.5, 90, 0);
                logger.setNoAi(true);
                logger.tame(owner);
                logger.setTool(new ItemStack(Items.DIAMOND_AXE));
                logger.getBag().setStackInSlot(0, new ItemStack(Items.OAK_SAPLING, 8));
                level.addFreshEntity(logger);
                for (var pet : java.util.List.of(farmer, logger)) {
                  pet.setYBodyRot(90);
                  pet.setYHeadRot(90);
                }
                owner.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
                owner.teleportTo(level, 7.5, 101, 8.5, -90, 15);
                owner.inventoryMenu.broadcastChanges();
              });
      return false;
    }
    if (phase >= 5) return true;
    var found =
        client.level.getEntitiesOfClass(
            FieldSlime.class, client.player.getBoundingBox().inflate(16));
    FieldSlime pet =
        found.stream()
            .filter(s -> phase <= 2 ? s instanceof FarmerSlime : s instanceof LumberjackSlime)
            .findFirst()
            .orElse(null);
    if (pet == null) return false;
    if (phase == 1 || phase == 3) {
      if (++ticks < 30) return false;
      if (!client
          .getEntityRenderDispatcher()
          .getRenderer(pet)
          .getClass()
          .getSimpleName()
          .equals("FriendlySlimeRenderer"))
        throw new IllegalStateException("Missing worker renderer");
      shot(client, (phase == 1 ? "farming" : "lumberjack") + "-hat-tool");
      client.gameMode.interact(client.player, pet, InteractionHand.MAIN_HAND);
      phase++;
      ticks = 0;
      return false;
    }
    if (client.screen instanceof SlimeScreen screen && screen.getMenu().isSynchronized()) {
      if (++ticks < 20) return false;
      var menu = screen.getMenu();
      if (menu.slots.size() != 64
          || menu.value(SlimeStat.DETECTION_RANGE) != 16
          || !menu.getSlot(0).getItem().is(phase == 2 ? Items.DIAMOND_HOE : Items.DIAMOND_AXE)
          || menu.getSlot(1).getItem().getCount() != (phase == 2 ? 12 : 8))
        throw new IllegalStateException("Field worker synchronized gear/bag/range layout");
      if (jei && (JeiClientSmoke.farmingRecipes() < 6 || JeiClientSmoke.lumberjackRecipes() < 6))
        throw new IllegalStateException("Missing field worker recipe families");
      shot(client, (phase == 2 ? "farming" : "lumberjack") + "-menu");
      client.player.closeContainer();
      if (phase == 2) {
        client
            .getSingleplayerServer()
            .execute(
                () -> {
                  var owner =
                      client
                          .getSingleplayerServer()
                          .getPlayerList()
                          .getPlayer(client.player.getUUID());
                  owner.teleportTo(owner.serverLevel(), 7.5, 101, 12.5, -90, 15);
                });
        phase = 3;
        ticks = 0;
      } else {
        phase = 5;
        System.out.println(
            "SLIMIC FIELD CLIENT SMOKE PASSED: farming/lumberjack hats, exact tools, bag, range,"
                + " optional recipe families; JEI="
                + jei);
      }
    }
    return false;
  }

  private static void shot(Minecraft client, String label) {
    var path =
        client
            .gameDirectory
            .toPath()
            .resolve(
                "../verification/0.1.24-"
                    + (ModList.get().isLoaded("jei") ? "jei-" : "no-jei-")
                    + label
                    + ".png")
            .normalize();
    try {
      Files.createDirectories(path.getParent());
      try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
        image.writeToFile(path);
      }
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
