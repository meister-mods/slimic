package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.item.MiningSlimeItem;
import io.github.meistermods.slimic.registry.*;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionTooltipGameTests {
  private static List<String> keys(ItemStack stack, GameTestHelper helper) {
    var lines = new ArrayList<Component>();
    stack.getItem().appendHoverText(stack, helper.getLevel(), lines, TooltipFlag.NORMAL);
    return lines.stream()
        .map(line -> ((TranslatableContents) line.getContents()).getKey())
        .toList();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void everyFreshProfessionHasExactlyItsOwnDescription(GameTestHelper h) {
    int count = 0;
    for (var type : ModEntities.companionTypes()) {
      var item = ForgeRegistries.ITEMS.getValue(type.getId());
      var stack = new ItemStack(item);
      var lines = keys(stack, h);
      h.assertTrue(
          lines.equals(
              List.of(
                  item.getDescriptionId() + ".tooltip",
                  "item.slimic.companion.summon_tooltip",
                  "item.slimic.friendly_slime.capture_tooltip",
                  "item.slimic.friendly_slime.manage_tooltip")),
          "Distinct profession and common fresh controls for " + type.getId() + ": " + lines);
      h.assertTrue(!stack.hasTag(), "Reading a fresh tooltip cannot add data");
      count++;
    }
    h.assertTrue(count == 23, "Every registered profession is covered");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void everyStoredProfessionRetainsItsDescriptionAndExactData(GameTestHelper h) {
    for (var type : ModEntities.companionTypes()) {
      var slime = (FriendlySlime) type.get().create(h.getLevel());
      slime.setOwnerUUID(UUID.randomUUID());
      slime.setTame(true);
      slime.setHealth(3.5F);
      slime.setWaiting(false);
      slime.setCustomName(Component.literal("Saved companion"));
      var stack = FriendlySlimeItem.fromSlime(slime);
      h.assertTrue(
          stack.is(ForgeRegistries.ITEMS.getValue(type.getId())),
          "Recovery retains the exact profession item: " + type.getId());
      var before = stack.save(new CompoundTag());
      var lines = keys(stack, h);
      h.assertTrue(
          lines.equals(
              List.of(
                  stack.getItem().getDescriptionId() + ".tooltip",
                  "item.slimic.friendly_slime.stored_tooltip",
                  "item.slimic.friendly_slime.capture_tooltip",
                  "item.slimic.friendly_slime.manage_tooltip")),
          "Stored help describes the correct profession and restoration: " + type.getId());
      h.assertTrue(
          stack.save(new CompoundTag()).equals(before), "Tooltip cannot alter saved state");
      h.assertTrue(stack.getMaxStackSize() == 1, "Stored companions remain unstackable");
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void initialMiningEquipmentHintIsPreservedWithoutDuplicateHelp(GameTestHelper h) {
    var stack = new ItemStack(ModItems.MINING_SLIME.get());
    var pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
    pickaxe.setDamageValue(37);
    pickaxe.setHoverName(Component.literal("Named pickaxe"));
    stack.getOrCreateTag().put(MiningSlimeItem.INITIAL_PICKAXE, pickaxe.save(new CompoundTag()));
    var before = stack.save(new CompoundTag());
    var lines = new ArrayList<Component>();
    stack.getItem().appendHoverText(stack, h.getLevel(), lines, TooltipFlag.ADVANCED);
    h.assertTrue(
        lines.size() == 5, "One profession description, three common hints, and exact tool");
    var tool = (TranslatableContents) lines.get(4).getContents();
    h.assertTrue(tool.getKey().equals("item.slimic.mining_slime.pickaxe"), "Initial pickaxe hint");
    h.assertTrue(
        ((Component) tool.getArgs()[0]).getString().equals("Named pickaxe"), "Exact tool name");
    h.assertTrue(stack.save(new CompoundTag()).equals(before), "Initial tool data remains intact");
    h.succeed();
  }
}
