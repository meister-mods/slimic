package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AttentionBaseGameTests {
  private record Specialization(Item result, Item core, boolean star) {}

  private static net.minecraft.server.level.ServerPlayer owner(GameTestHelper h) {
    var player =
        new net.minecraft.server.level.ServerPlayer(
            h.getLevel().getServer(),
            h.getLevel(),
            new GameProfile(UUID.randomUUID(), "AttentionTrainer"));
    player.connection =
        new net.minecraft.server.network.ServerGamePacketListenerImpl(
            player.server,
            new net.minecraft.network.Connection(
                net.minecraft.network.protocol.PacketFlow.SERVERBOUND) {
              @Override
              public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            },
            player);
    return player;
  }

  private static List<Specialization> specializations() {
    return List.of(
        new Specialization(ModItems.MINING_SLIME.get(), Items.IRON_PICKAXE, false),
        new Specialization(ModItems.TRANSPORT_SLIME.get(), Items.CHEST, false),
        new Specialization(ModItems.HERALD_SLIME.get(), Items.FIREWORK_ROCKET, false),
        new Specialization(ModItems.COMBAT_SLIME.get(), Items.IRON_SWORD, false),
        new Specialization(ModItems.CLEANING_SLIME.get(), Items.ROTTEN_FLESH, false),
        new Specialization(ModItems.SADDLE_SLIME.get(), Items.SADDLE, false),
        new Specialization(ModItems.FARMER_SLIME.get(), Items.IRON_HOE, false),
        new Specialization(ModItems.LUMBERJACK_SLIME.get(), Items.IRON_AXE, false),
        new Specialization(ModItems.SOGGY_SLIME.get(), Items.SLIME_BALL, false),
        new Specialization(ModItems.HEALING_SLIME.get(), Items.GOLDEN_CARROT, false),
        new Specialization(ModItems.GUARDIAN_ANGEL_SLIME.get(), Items.TOTEM_OF_UNDYING, true),
        new Specialization(ModItems.FISHING_SLIME.get(), Items.FISHING_ROD, false),
        new Specialization(ModItems.MEOW_SLIME.get(), Items.SALMON, false),
        new Specialization(ModItems.GLOW_SLIME.get(), Items.GLOWSTONE_DUST, false),
        new Specialization(ModItems.COMMANDER_SLIME.get(), Items.BOOK, false),
        new Specialization(ModItems.ENDER_SLIME.get(), Items.ENDER_PEARL, false));
  }

  private static TransientCraftingContainer grid(int size) {
    return new TransientCraftingContainer(
        new AbstractContainerMenu(null, -1) {
          @Override
          public boolean stillValid(Player player) {
            return false;
          }

          @Override
          public ItemStack quickMoveStack(Player player, int slot) {
            return ItemStack.EMPTY;
          }
        },
        size,
        size);
  }

  private static AttentionSlime trained(GameTestHelper h) {
    var slime = ModEntities.ATTENTION_SLIME.get().create(h.getLevel());
    slime.tame(h.makeMockSurvivalPlayer());
    slime.setTunedVitals(7, 37, 100);
    for (var stat : SlimeStat.values()) {
      h.assertTrue(slime.upgradeStat(stat) && slime.upgradeStat(stat), "Train every common stat");
    }
    slime.setCustomName(Component.literal("Trained companion"));
    slime.setRoaming(true);
    return slime;
  }

  private static ItemStack craft(GameTestHelper h, CraftingContainer grid, Item expected) {
    var recipe =
        h.getLevel()
            .getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel())
            .orElseThrow();
    var output = recipe.assemble(grid, h.getLevel().registryAccess());
    h.assertTrue(
        output.is(expected) && output.getCount() == 1, "The native manager resolves " + expected);
    h.assertTrue(
        recipe.getRemainingItems(grid).stream().allMatch(ItemStack::isEmpty),
        "Specialization consumes ingredients without returning tools or extra bases");
    return output;
  }

  private static FriendlySlime load(GameTestHelper h, ItemStack output) {
    var type =
        ForgeRegistries.ENTITY_TYPES.getValue(ForgeRegistries.ITEMS.getKey(output.getItem()));
    var slime = (FriendlySlime) type.create(h.getLevel());
    slime.load(output.getTag().getCompound("SlimeData"));
    return slime;
  }

  private static void checkConverted(GameTestHelper h, AttentionSlime original, ItemStack output) {
    var saved = output.getTag().getCompound("SlimeData");
    h.assertTrue(
        !saved.contains("AttentionRoaming") && !saved.contains("AttentionActivity"),
        "Attention-only roaming and activity are removed from " + output.getItem());
    var restored = load(h, output);
    h.assertTrue(
        restored.isOrderedToSit()
            && !restored.isWorking()
            && restored.getWorkStatus().order() == WorkStatus.Order.WAIT,
        "Every new specialization waits");
    h.assertTrue(
        restored.getUUID().equals(original.getUUID())
            && restored.getOwnerUUID().equals(original.getOwnerUUID())
            && restored.getHealth() == original.getHealth()
            && restored.getStatPoints() == original.getStatPoints()
            && restored.getName().getString().equals(original.getName().getString()),
        "Identity, ownership, health, available points, and name survive");
    for (var stat : SlimeStat.values())
      h.assertTrue(restored.getStatLevel(stat) == 2, "Keep the invested level for " + stat);
    h.assertTrue(
        Math.abs(
                    restored.getAttributeBaseValue(Attributes.MOVEMENT_SPEED)
                        - original.getAttributeBaseValue(Attributes.MOVEMENT_SPEED))
                < .00001
            && Math.abs(
                    restored.getAttributeValue(Attributes.MOVEMENT_SPEED)
                        - original.getAttributeBaseValue(Attributes.MOVEMENT_SPEED))
                < .00001,
        "Invested speed survives without the Attention profession's extra 50 percent");
    var again = (FriendlySlime) restored.getType().create(h.getLevel());
    again.load(restored.saveWithoutId(new CompoundTag()));
    h.assertTrue(
        Math.abs(
                again.getAttributeValue(Attributes.MOVEMENT_SPEED)
                    - restored.getAttributeValue(Attributes.MOVEMENT_SPEED))
            < .00001,
        "The removed profession bonus cannot return after another save and load");
  }

  @GameTest(template = "empty", batch = "attention_base", timeoutTicks = 100)
  public static void trainedAttentionSpecializesIntoEveryBasicProfessionInAnySlot(
      GameTestHelper h) {
    var original = trained(h);
    var source = FriendlySlimeItem.fromSlime(original);
    var before = source.save(new CompoundTag());
    for (var target : specializations())
      for (int first = 0; first < 9; first++)
        for (int second = 0; second < 9; second++) {
          if (first == second) continue;
          var grid = grid(3);
          grid.setItem(first, source.copy());
          grid.setItem(second, new ItemStack(target.core));
          if (target.star)
            for (int slot = 0; slot < 9; slot++)
              if (slot != first && slot != second) {
                grid.setItem(slot, new ItemStack(Items.NETHER_STAR));
                break;
              }
          var output = craft(h, grid, target.result);
          checkConverted(h, original, output);
          h.assertTrue(
              source.save(new CompoundTag()).equals(before),
              "Recipe previews never change the source");
        }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "attention_base", timeoutTicks = 100)
  public static void bothFreshBasesWorkInInventoryCraftingAndExtraInputsAreRejected(
      GameTestHelper h) {
    for (var base : List.of(ModItems.FRIENDLY_SLIME.get(), ModItems.ATTENTION_SLIME.get()))
      for (var target : specializations()) {
        var grid = grid(2);
        grid.setItem(3, new ItemStack(base));
        grid.setItem(0, new ItemStack(target.core));
        if (target.star) grid.setItem(1, new ItemStack(Items.NETHER_STAR));
        craft(h, grid, target.result);
        grid.setItem(2, new ItemStack(base));
        h.assertTrue(
            h.getLevel()
                .getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel())
                .isEmpty(),
            "Two slime bases cannot be consumed by one specialization");
      }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "attention_base", timeoutTicks = 100)
  public static void higherProfessionsAndAttentionItselfKeepTheirExistingPrerequisites(
      GameTestHelper h) {
    for (var core :
        List.of(Items.BONE_MEAL, Items.NETHER_STAR, Items.DRAGON_HEAD, Items.REDSTONE)) {
      var grid = grid(3);
      grid.setItem(0, new ItemStack(ModItems.ATTENTION_SLIME.get()));
      grid.setItem(1, new ItemStack(core));
      if (core == Items.BONE_MEAL) grid.setItem(2, new ItemStack(Items.NETHER_STAR));
      h.assertTrue(
          h.getLevel()
              .getRecipeManager()
              .getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel())
              .isEmpty(),
          "Attention is not a substitute for a required specialized profession or itself");
    }
    var grid = grid(3);
    grid.setItem(0, new ItemStack(ModItems.ATTENTION_SLIME.get()));
    for (int slot = 1; slot < 9; slot++) grid.setItem(slot, new ItemStack(Items.CHEST));
    h.assertTrue(
        h.getLevel()
            .getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel())
            .isEmpty(),
        "Warehouse still requires Transport");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "attention_base", timeoutTicks = 100)
  public static void craftingAndReleaseKeepTheTrainedStatsAndExactInputTool(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var original = trained(h);
    var owner = owner(h);
    original.tame(owner);
    var source = FriendlySlimeItem.fromSlime(original);
    var tool = new ItemStack(Items.DIAMOND_PICKAXE);
    tool.setDamageValue(37);
    tool.setHoverName(Component.literal("Named pickaxe"));
    tool.enchant(Enchantments.BLOCK_EFFICIENCY, 3);
    var menu = owner.inventoryMenu;
    menu.getSlot(1).set(source);
    menu.getSlot(4).set(tool.copy());
    var resultSlot = menu.getSlot(0);
    var output = resultSlot.remove(1);
    h.assertTrue(
        output.is(ModItems.MINING_SLIME.get()), "The native inventory result is a Mining Slime");
    resultSlot.onTake(owner, output);
    h.assertTrue(
        menu.getSlot(1).getItem().isEmpty() && menu.getSlot(4).getItem().isEmpty(),
        "Taking the output consumes exactly the base and pickaxe");
    owner.setItemInHand(InteractionHand.MAIN_HAND, output);
    var floor = BlockPos.containing(area.point(7, 100, 7));
    var result =
        ((FriendlySlimeItem) output.getItem())
            .useOn(
                new UseOnContext(
                    owner,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(
                        Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false)));
    h.assertTrue(
        result.consumesAction() && output.isEmpty(),
        "Survival releases and consumes the stored item");
    var miner = (MiningSlime) h.getLevel().getEntity(original.getUUID());
    h.assertTrue(
        miner != null
            && miner.isOrderedToSit()
            && !miner.isWorking()
            && ItemStack.matches(miner.getTool(0), tool)
            && miner.getStatLevel(SlimeStat.MOVEMENT_SPEED) == 2
            && Math.abs(miner.getAttributeValue(Attributes.MOVEMENT_SPEED) - .33) < .00001,
        "Actual release keeps the exact named enchanted tool and invested speed without Attention"
            + " traits");
    miner.discard();
    area.close();
    h.succeed();
  }
}
