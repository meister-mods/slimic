package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.client.SlimeScreen;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.SlimeArmorItem;
import io.github.meistermods.slimic.registry.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Native model, equipment synchronization and menu coverage for profession details. */
public final class OrderElevenClientSmoke {
  private static int phase, ticks;
  private static final AtomicBoolean confirmed = new AtomicBoolean();
  private static volatile String serverState = "waiting for equipment synchronization";

  private OrderElevenClientSmoke() {}

  public static boolean tick(Minecraft c) {
    if (phase > 6) return true;
    if (phase == 0) {
      phase++;
      c.getSingleplayerServer()
          .execute(
              () -> {
                var owner = c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
                var level = owner.serverLevel();
                level
                    .getEntitiesOfClass(
                        net.minecraft.world.entity.Entity.class,
                        new AABB(0, 90, 0, 24, 320, 24),
                        entity -> !(entity instanceof net.minecraft.world.entity.player.Player))
                    .forEach(net.minecraft.world.entity.Entity::discard);
                for (int x = 1; x <= 19; x++)
                  for (int z = 3; z <= 20; z++) {
                    level.setBlock(
                        new BlockPos(x, 100, z), Blocks.SEA_LANTERN.defaultBlockState(), 2);
                    for (int y = 101; y < 112; y++)
                      level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                  }
                int i = 0;
                for (var type :
                    java.util.List.of(
                        ModEntities.CLEANING_SLIME.get(),
                        ModEntities.COMBAT_SLIME.get(),
                        ModEntities.SADDLE_SLIME.get(),
                        ModEntities.SOGGY_SLIME.get(),
                        ModEntities.FRIENDLY_SLIME.get())) {
                  var pet = type.create(level);
                  pet.tame(owner);
                  pet.moveTo(3.5 + (i++ % 4) * 4, 101, i <= 4 ? 8.5 : 13.5);
                  pet.setWaiting(true);
                  pet.setNoAi(true);
                  level.addFreshEntity(pet);
                }
                var mount = ModEntities.SADDLE_SLIME.get().create(level);
                mount.tame(owner);
                mount.moveTo(9.5, 101, 13.5);
                mount.setWaiting(true);
                mount.setNoAi(true);
                mount.setEquipment(0, new ItemStack(Items.SADDLE));
                mount.setEquipment(1, new ItemStack(Items.ELYTRA));
                var armor = SlimeArmorItem.fromChestplate(new ItemStack(Items.DIAMOND_CHESTPLATE));
                armor.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 3);
                mount.setEquipment(2, armor);
                level.addFreshEntity(mount);
                var leather = ModEntities.SADDLE_SLIME.get().create(level);
                leather.tame(owner);
                leather.moveTo(15.5, 101, 13.5);
                leather.setWaiting(true);
                leather.setNoAi(true);
                leather.setEquipment(0, new ItemStack(Items.SADDLE));
                leather.setEquipment(1, new ItemStack(Items.ELYTRA));
                var original = new ItemStack(Items.LEATHER_CHESTPLATE);
                ((DyeableLeatherItem) original.getItem()).setColor(original, 0x235ac8);
                leather.setEquipment(2, SlimeArmorItem.fromChestplate(original));
                level.addFreshEntity(leather);
                owner.getInventory().clearContent();
                owner.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
                owner.teleportTo(level, 9.5, 104, 21.5, 180, 18);
              });
      return false;
    }
    if (phase == 1) {
      if (++ticks < 45) return false;
      checkCrest(
          c,
          ModItems.FRIENDLY_SLIME.get(),
          ResourceLocation.fromNamespaceAndPath("minecraft", "block/gold_block"),
          "heart_of_the_sea");
      checkCrest(
          c,
          ModItems.SOGGY_SLIME.get(),
          ResourceLocation.fromNamespaceAndPath("slimic", "item/slime_slag"),
          "water_bucket");
      shot(c, "order11-profession-gallery");
      c.getSingleplayerServer()
          .execute(
              () -> {
                var owner = c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
                owner.teleportTo(owner.serverLevel(), 9.5, 101, 16.5, 180, 12);
              });
      next();
      return false;
    }
    var mount =
        c.level.getEntitiesOfClass(SaddleSlime.class, new AABB(8, 100, 12, 11, 105, 15)).stream()
            .findFirst()
            .orElse(null);
    if (mount == null) return false;
    if (phase == 2) {
      if (++ticks < 20) return false;
      c.gameMode.interact(c.player, mount, InteractionHand.MAIN_HAND);
      next();
      return false;
    }
    if (phase == 3) {
      if (!(c.screen instanceof SlimeScreen screen)
          || !screen.getMenu().isSynchronized()
          || ++ticks < 20) return false;
      var menu = screen.getMenu();
      if (menu.slots.size() != 39
          || !menu.slots.get(1).getItem().is(Items.ELYTRA)
          || !(menu.slots.get(2).getItem().getItem() instanceof SlimeArmorItem))
        throw new IllegalStateException("All three saddle equipment slots must synchronize");
      shot(c, "order11-armored-saddle-menu");
      c.gameMode.handleInventoryMouseClick(menu.containerId, 2, 0, ClickType.QUICK_MOVE, c.player);
      next();
      return false;
    }
    if (phase == 4) {
      if (!(c.screen instanceof SlimeScreen screen) || !(mount.getEquipment(2).isEmpty()))
        return false;
      var menu = screen.getMenu();
      int armor = -1;
      for (int i = 3; i < menu.slots.size(); i++)
        if (menu.slots.get(i).getItem().getItem() instanceof SlimeArmorItem) armor = i;
      if (armor < 0) return false;
      c.gameMode.handleInventoryMouseClick(
          menu.containerId, armor, 0, ClickType.QUICK_MOVE, c.player);
      next();
      return false;
    }
    if (phase == 5) {
      if (!(mount.getEquipment(2).getItem() instanceof SlimeArmorItem)) return false;
      if (ticks == 0) c.player.closeContainer();
      if (++ticks > 200)
        throw new IllegalStateException("Native armor synchronization timed out: " + serverState);
      if (ticks % 5 == 0) {
        int id = mount.getId();
        c.getSingleplayerServer()
            .execute(
                () -> {
                  var pet = (SaddleSlime) c.getSingleplayerServer().overworld().getEntity(id);
                  serverState =
                      pet == null
                          ? "mount disappeared"
                          : "wings="
                              + pet.hasWings()
                              + ", saddle="
                              + pet.isSaddled()
                              + ", armor="
                              + pet.getAttributeValue(
                                  net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)
                              + ", chest="
                              + pet.getEquipment(2);
                  if (pet == null
                      || !pet.hasWings()
                      || !pet.isSaddled()
                      || pet.getAttributeValue(
                              net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)
                          < 8) return;
                  var owner =
                      c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
                  owner.teleportTo(owner.serverLevel(), 9.5, 102, 8.5, 0, 8);
                  confirmed.set(true);
                });
      }
      if (confirmed.get()) next();
      return false;
    }
    if (phase == 6) {
      if (!confirmed.get() || ++ticks < 35) return false;
      shot(c, "order11-armored-saddle-rear");
      System.out.println(
          "SLIMIC ORDER ELEVEN CLIENT SMOKE PASSED: permanent profession details, plain gold"
              + " Friendly crest, Slime Slag crest, armor/wing/saddle synchronization, native armor"
              + " attributes, exact Shift transfers");
      phase++;
    }
    return phase > 6;
  }

  private static void checkCrest(
      Minecraft c, Item item, ResourceLocation expected, String forbidden) {
    var model = c.getItemRenderer().getModel(new ItemStack(item), c.level, c.player, 0);
    var quads = model.getQuads(null, null, net.minecraft.util.RandomSource.create(0));
    if (quads.stream().noneMatch(q -> q.getSprite().contents().name().equals(expected))
        || quads.stream()
            .anyMatch(q -> q.getSprite().contents().name().getPath().endsWith(forbidden)))
      throw new IllegalStateException("Summon crest incorrect: " + item);
  }

  private static void shot(Minecraft c, String name) {
    var path =
        c.gameDirectory
            .toPath()
            .resolve(
                "../verification/order11/"
                    + (net.minecraftforge.fml.ModList.get().isLoaded("jei") ? "jei-" : "no-jei-")
                    + name
                    + ".png")
            .normalize();
    try (var capture = Screenshot.takeScreenshot(c.getMainRenderTarget())) {
      capture.writeToFile(path);
    } catch (java.io.IOException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static void next() {
    phase++;
    ticks = 0;
  }
}
