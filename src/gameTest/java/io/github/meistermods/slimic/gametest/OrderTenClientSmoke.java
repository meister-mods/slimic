package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.client.SlimeScreen;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;

final class OrderTenClientSmoke {
  private static int phase, ticks, pageReadyTicks;

  static boolean tick(Minecraft c) {
    if (phase == 6) return true;
    if (phase == 0) {
      phase = 1;
      ticks = 0;
      c.getSingleplayerServer()
          .execute(
              () -> {
                var owner = c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
                var level = owner.serverLevel();
                level
                    .getEntitiesOfClass(FriendlySlime.class, owner.getBoundingBox().inflate(40))
                    .forEach(FriendlySlime::discard);
                var healer = ModEntities.HEALING_SLIME.get().create(level);
                healer.tame(owner);
                healer.moveTo(10.5, 101, 8.5);
                healer.setNoAi(true);
                healer.getBag().setStackInSlot(0, new ItemStack(Items.SLIME_BALL, 32));
                level.addFreshEntity(healer);
                var warehouse = ModEntities.WAREHOUSE_SLIME.get().create(level);
                warehouse.tame(owner);
                warehouse.moveTo(10.5, 101, 11.5);
                warehouse.setNoAi(true);
                SlimeStat.CAPACITY.tune(warehouse, 27);
                warehouse.getBag().setStackInSlot(215, new ItemStack(Items.DIAMOND, 17));
                level.addFreshEntity(warehouse);
                owner.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
                owner.getInventory().setItem(1, new ItemStack(ModItems.HEALING_SLIME.get()));
                owner.getInventory().setItem(2, new ItemStack(ModItems.WAREHOUSE_SLIME.get()));
                owner.teleportTo(level, 7.5, 101, 8.5, -70, 14);
              });
      return false;
    }
    if (phase == 1) {
      var pets =
          c.level.getEntitiesOfClass(HealingSlime.class, c.player.getBoundingBox().inflate(15));
      if (pets.isEmpty() || ++ticks < 35) return false;
      for (var entry :
          java.util.Map.of(
                  ModItems.HEALING_SLIME.get(),
                  "golden_carrot",
                  ModItems.WAREHOUSE_SLIME.get(),
                  "chest_minecart")
              .entrySet()) {
        var model =
            c.getItemRenderer().getModel(new ItemStack(entry.getKey()), c.level, c.player, 0);
        if (!model.getParticleIcon().contents().name().getPath().equals("item/friendly_slime"))
          throw new IllegalStateException("Shared summon base missing");
        var quads = model.getQuads(null, null, net.minecraft.util.RandomSource.create(0));
        if (quads.stream()
            .noneMatch(
                q -> q.getSprite().contents().name().getPath().equals("item/" + entry.getValue())))
          throw new IllegalStateException("Profession crest missing");
      }
      if (ModList.get().isLoaded("jei")
          && (JeiClientSmoke.healingRecipes() != 1 || JeiClientSmoke.warehouseRecipes() != 1))
        throw new IllegalStateException("New JEI recipes missing");
      shot(c, "healing-warehouse-appearance");
      c.gameMode.interact(c.player, pets.get(0), InteractionHand.MAIN_HAND);
      phase = 2;
      ticks = 0;
      return false;
    }
    if (phase == 2) {
      if (!(c.screen instanceof SlimeScreen s) || !s.getMenu().isSynchronized() || ++ticks < 15)
        return false;
      if (!(s.getMenu().slime() instanceof HealingSlime)
          || s.getMenu().slots.size() != 63
          || s.getMenu().data(SlimeMenu.MODE) != SlimeMenu.WAIT)
        throw new IllegalStateException("Healer initially waits with bag only");
      label(c, s, "heal");
      shot(c, "healing-menu");
      c.gameMode.handleInventoryButtonClick(s.getMenu().containerId, SlimeMenu.WORK);
      phase = 3;
      ticks = 0;
      return false;
    }
    if (phase == 3) {
      if (!(c.screen instanceof SlimeScreen s)
          || s.getMenu().data(SlimeMenu.MODE) != SlimeMenu.WORK
          || ++ticks < 10) return false;
      c.getSingleplayerServer()
          .execute(
              () -> {
                var owner = c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
                var healer = (HealingSlime) ((SlimeMenu) owner.containerMenu).slime();
                for (int i = 0; i < healer.getCapacity(); i++)
                  healer.getBag().setStackInSlot(i, new ItemStack(Items.DIRT, 64));
                healer.setNoAi(false);
              });
      phase = 7;
      ticks = 0;
      return false;
    }
    if (phase == 7) {
      if (!(c.screen instanceof SlimeScreen s)
          || s.getMenu().data(SlimeMenu.STATUS) != HealingSlime.Status.FULL.ordinal()
          || ++ticks < 15) return false;
      var status = Component.translatable("status.slimic.healing.full");
      if (status.getString().equals("status.slimic.healing.full")
          || c.font.split(status, 174).size() > 3)
        throw new IllegalStateException("Missing or overflowing full-healer status");
      shot(c, "healing-full-menu");
      c.player.closeContainer();
      c.getSingleplayerServer()
          .execute(
              () -> {
                var owner = c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
                owner
                    .serverLevel()
                    .getEntitiesOfClass(HealingSlime.class, owner.getBoundingBox().inflate(15))
                    .forEach(
                        p -> {
                          p.setWorking(false);
                          p.setWaiting(true);
                          p.setNoAi(true);
                        });
                owner.teleportTo(owner.serverLevel(), 7.5, 101, 11.5, -90, 12);
              });
      phase = 4;
      ticks = 0;
      return false;
    }
    if (phase == 4) {
      if (++ticks < 20) return false;
      var pets =
          c.level.getEntitiesOfClass(WarehouseSlime.class, c.player.getBoundingBox().inflate(12));
      if (pets.isEmpty()) return false;
      c.gameMode.interact(c.player, pets.get(0), InteractionHand.MAIN_HAND);
      phase = 5;
      ticks = 0;
      return false;
    }
    if (phase == 5) {
      if (!(c.screen instanceof SlimeScreen s) || !s.getMenu().isSynchronized() || ++ticks < 15)
        return false;
      if (!(s.getMenu().slime() instanceof WarehouseSlime)
          || s.getMenu().slots.size() != 252
          || s.getMenu().bagPages() != 8)
        throw new IllegalStateException("Warehouse capacity or menu layout");
      label(c, s, "store");
      if (ticks == 15) {
        for (int i = 0; i < 7; i++)
          c.gameMode.handleInventoryButtonClick(s.getMenu().containerId, SlimeMenu.NEXT_PAGE);
        return false;
      }
      if (s.getMenu().data(SlimeMenu.BAG_PAGE) != 7
          || !s.getMenu().getSlot(215).getItem().is(Items.DIAMOND)) return false;
      if (++pageReadyTicks < 10) return false;
      shot(c, "warehouse-last-page");
      c.player.closeContainer();
      phase = 6;
      System.out.println(
          "SLIMIC ORDER TEN CLIENT SMOKE PASSED: shared crests, healer/warehouse renders,"
              + " translated labels, search command, full-bag status, 216-slot paging, JEI="
              + ModList.get().isLoaded("jei"));
      return true;
    }
    return false;
  }

  private static void label(Minecraft c, SlimeScreen s, String key) {
    var label = Component.translatable("screen.slimic." + key);
    if (s.children().stream()
        .noneMatch(
            w ->
                w instanceof Button b
                    && b.getMessage().equals(label)
                    && c.font.width(label) <= b.getWidth() - 6))
      throw new IllegalStateException("Missing or overflowing command label " + key);
  }

  private static void shot(Minecraft c, String name) {
    var p =
        c.gameDirectory
            .toPath()
            .resolve(
                "../verification/0.1.24-"
                    + (ModList.get().isLoaded("jei") ? "jei-" : "no-jei-")
                    + name
                    + ".png")
            .normalize();
    try (var image = Screenshot.takeScreenshot(c.getMainRenderTarget())) {
      java.nio.file.Files.createDirectories(p.getParent());
      image.writeToFile(p);
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
