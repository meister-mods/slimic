package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.item.MiningSlimeItem;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = "slimic_tests")
public final class MiningSlimeGameTests {
  private static final BlockPos FLOOR = new BlockPos(5, 1, 5);
  private static final BlockPos ORE = new BlockPos(6, 2, 5);
  private static final Set<BlockPos> DENIED = ConcurrentHashMap.newKeySet();

  private MiningSlimeGameTests() {}

  @SubscribeEvent
  public static void denyProtectedTestBlock(BlockEvent.BreakEvent event) {
    if (DENIED.contains(event.getPos())) event.setCanceled(true);
  }

  @GameTest(template = "empty")
  public static void specializationPreservesEveryVanillaPickaxeAndSavedSlime(
      GameTestHelper helper) {
    Player owner = helper.makeMockSurvivalPlayer();
    FriendlySlime pet = helper.spawn(ModEntities.FRIENDLY_SLIME.get(), FLOOR.above());
    pet.tame(owner);
    pet.setHealth(5.0F);
    ItemStack friendly = FriendlySlimeItem.fromSlime(pet);
    for (var item :
        new net.minecraft.world.item.Item[] {
          Items.WOODEN_PICKAXE,
          Items.STONE_PICKAXE,
          Items.IRON_PICKAXE,
          Items.GOLDEN_PICKAXE,
          Items.DIAMOND_PICKAXE,
          Items.NETHERITE_PICKAXE
        }) {
      ItemStack pickaxe = new ItemStack(item);
      pickaxe.setDamageValue(5);
      pickaxe.enchant(Enchantments.BLOCK_EFFICIENCY, 2);
      pickaxe.setHoverName(Component.literal("My tool"));
      var grid = grid();
      grid.setItem(0, friendly);
      grid.setItem(3, pickaxe);
      var recipe =
          helper
              .getLevel()
              .getRecipeManager()
              .getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel())
              .orElseThrow();
      ItemStack result = recipe.assemble(grid, helper.getLevel().registryAccess());
      helper.assertTrue(
          result.is(ModItems.MINING_SLIME.get()), "Any vanilla pickaxe must specialize the slime");
      helper.assertTrue(
          result
              .getTag()
              .getCompound("SlimeData")
              .equals(friendly.getTag().getCompound("SlimeData")),
          "Specializing must retain the saved companion state");
      helper.assertTrue(
          ItemStack.matches(
              pickaxe, ItemStack.of(result.getTag().getCompound(MiningSlimeItem.INITIAL_PICKAXE))),
          "The exact damaged, named, enchanted tool must transfer");
      helper.assertTrue(
          pickaxe.getDamageValue() == 5
              && recipe.getRemainingItems(grid).stream().allMatch(ItemStack::isEmpty),
          "Crafting must neither mutate inputs nor duplicate the tool as a remainder");
    }
    var invalid = grid();
    invalid.setItem(0, new ItemStack(ModItems.FRIENDLY_SLIME.get()));
    invalid.setItem(1, new ItemStack(Items.IRON_AXE));
    helper.assertTrue(
        helper
            .getLevel()
            .getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, invalid, helper.getLevel())
            .isEmpty(),
        "Axes must not count as pickaxes");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void ownerMenuUpgradesAndRenamesWithoutHealing(GameTestHelper helper) {
    Player owner = helper.makeMockSurvivalPlayer();
    FriendlySlime pet = helper.spawn(ModEntities.FRIENDLY_SLIME.get(), FLOOR.above());
    pet.tame(owner);
    pet.setHealth(8.0F);
    seed(pet, 3, 0, 100);
    owner.moveTo(pet.getX() + 1, pet.getY(), pet.getZ());
    SlimeMenu menu = new SlimeMenu(1, owner.getInventory(), pet);
    helper.assertTrue(
        menu.clickMenuButton(owner, SlimeStat.HEALTH.ordinal()),
        "Available points must upgrade health");
    helper.assertTrue(
        pet.getMaxHealth() == 20 && pet.getHealth() == 8, "Health upgrades must not heal");
    helper.assertTrue(
        menu.clickMenuButton(owner, SlimeStat.DEFENSE.ordinal()) && pet.getArmorValue() == 1,
        "Defense must upgrade the native armor attribute");
    helper.assertTrue(
        menu.clickMenuButton(owner, SlimeStat.CAPACITY.ordinal()) && pet.getCapacity() == 12,
        "Capacity upgrades must unlock three bag slots");
    helper.assertTrue(
        !menu.clickMenuButton(owner, SlimeStat.ATTACK.ordinal())
            && !menu.clickMenuButton(owner, 999),
        "No points and invalid button IDs must be rejected");
    helper.assertTrue(
        menu.rename(owner, "광부 슬라임")
            && pet.isCustomNameVisible()
            && pet.getName().getString().equals("광부 슬라임"),
        "The management screen must set a visible name");
    Player stranger = helper.makeMockSurvivalPlayer();
    stranger.moveTo(owner.position());
    helper.assertTrue(
        !menu.rename(stranger, "Stolen") && !menu.clickMenuButton(stranger, SlimeMenu.WAIT),
        "Other players must not manage the companion");
    owner.moveTo(pet.getX() + 20, pet.getY(), pet.getZ());
    helper.assertTrue(
        !menu.stillValid(owner) && !menu.rename(owner, "Remote"),
        "A stale distant menu must be rejected");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void defenseUsesMinecraftArmorAndUpgradeLimits(GameTestHelper helper) {
    Player owner = helper.makeMockSurvivalPlayer();
    FriendlySlime pet = helper.spawn(ModEntities.FRIENDLY_SLIME.get(), FLOOR.above());
    pet.tame(owner);
    seed(pet, 30, 0, 100);
    for (int level = 0; level < 20; level++)
      helper.assertTrue(
          pet.upgradeStat(SlimeStat.DEFENSE), "Defense upgrades should reach their limit");
    helper.assertTrue(
        !pet.upgradeStat(SlimeStat.DEFENSE) && pet.getArmorValue() == 20,
        "Defense must stop at its upgrade limit");
    pet.hurt(helper.getLevel().damageSources().mobAttack(owner), 10.0F);
    helper.assertTrue(
        Math.abs(pet.getHealth() - 12.0F) < 0.001F,
        "Twenty armor must use Minecraft's standard ten-damage reduction");
    for (int level = 0; level < 6; level++)
      helper.assertTrue(pet.upgradeStat(SlimeStat.CAPACITY), "Capacity should reach 27 slots");
    helper.assertTrue(
        pet.getCapacity() == 27 && !pet.upgradeStat(SlimeStat.CAPACITY),
        "Capacity must not exceed the physical bag");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 160)
  public static void miningCollectsLootWearsToolAndEarnsOnlyCompletedActivity(
      GameTestHelper helper) {
    MiningSlime miner = miner(helper, new ItemStack(Items.IRON_PICKAXE));
    seed(miner, 0, 255, 100);
    helper.setBlock(ORE, Blocks.STONE);
    helper.succeedWhen(
        () -> {
          helper.assertBlockPresent(Blocks.AIR, ORE);
          helper.assertTrue(
              count(miner, Items.COBBLESTONE) == 1, "The mined result must enter the bag");
          helper.assertTrue(
              miner.getPickaxe().getDamageValue() == 1, "A successful block must wear the tool");
          helper.assertTrue(
              miner.getStatPoints() == 1
                  && miner.getActivityProgress() == 0
                  && miner.getNextPointCost() == 320,
              "Only successful work must cross the progressive point threshold");
          helper.assertTrue(
              miner.getStamina() < miner.getMaxStamina(), "Successful work must consume stamina");
          miner.setWorking(false);
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void insufficientTierDoesNotDestroyOreOrFarmActivity(GameTestHelper helper) {
    MiningSlime miner = miner(helper, new ItemStack(Items.WOODEN_PICKAXE));
    helper.setBlock(ORE, Blocks.DIAMOND_ORE);
    helper.runAfterDelay(
        60,
        () -> {
          helper.assertBlockPresent(Blocks.DIAMOND_ORE, ORE);
          helper.assertTrue(
              miner.getActivityProgress() == 0 && miner.getPickaxe().getDamageValue() == 0,
              "An insufficient tool must neither wear nor award progress");
          miner.setWorking(false);
          helper.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 180)
  public static void fullBagPausesUntilItsContentsChange(GameTestHelper helper) {
    MiningSlime miner = miner(helper, new ItemStack(Items.IRON_PICKAXE));
    for (int slot = 0; slot < miner.getCapacity(); slot++)
      miner.getBag().setStackInSlot(slot, new ItemStack(Items.DIAMOND, 64));
    helper.setBlock(ORE, Blocks.STONE);
    helper.runAfterDelay(
        60,
        () -> {
          helper.assertBlockPresent(Blocks.STONE, ORE);
          helper.assertTrue(
              miner.getMiningStatus() == MiningSlime.Status.FULL
                  && miner.getPickaxe().getDamageValue() == 0
                  && miner.getActivityProgress() == 0,
              "A full bag must pause without damage or rewards");
          miner.getBag().extractItem(0, 64, false);
          helper.succeedWhen(
              () -> {
                helper.assertBlockPresent(Blocks.AIR, ORE);
                helper.assertTrue(
                    count(miner, Items.COBBLESTONE) == 1, "Freeing a bag slot must resume work");
                miner.setWorking(false);
              });
        });
  }

  @GameTest(template = "empty", timeoutTicks = 140)
  public static void aBrokenPickaxeStopsFurtherMining(GameTestHelper helper) {
    ItemStack tool = new ItemStack(Items.WOODEN_PICKAXE);
    tool.setDamageValue(tool.getMaxDamage() - 1);
    MiningSlime miner = miner(helper, tool);
    helper.setBlock(ORE, Blocks.STONE);
    helper.setBlock(ORE.east(), Blocks.STONE);
    helper.runAfterDelay(
        90,
        () -> {
          helper.assertTrue(
              miner.getPickaxe().isEmpty()
                  && miner.getActivityProgress() == 1
                  && count(miner, Items.COBBLESTONE) == 1,
              "The last durability must produce exactly one harvested block");
          helper.assertBlockPresent(Blocks.STONE, ORE.east());
          helper.assertTrue(
              miner.getMiningStatus() == MiningSlime.Status.NO_TOOL,
              "A broken tool must stop the miner");
          miner.setWorking(false);
          helper.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 380)
  public static void staminaExhaustionRestsThenResumes(GameTestHelper helper) {
    MiningSlime miner = miner(helper, new ItemStack(Items.IRON_PICKAXE));
    seed(miner, 0, 0, 0);
    BlockPos restingOre = ORE.above();
    helper.setBlock(restingOre, Blocks.STONE);
    helper.runAfterDelay(
        40,
        () -> {
          helper.assertBlockPresent(Blocks.STONE, restingOre);
          helper.assertTrue(
              miner.getMiningStatus() == MiningSlime.Status.RESTING
                  && miner.getStamina() > 0
                  && miner.getActivityProgress() == 0,
              "Exhausted miners must rest without free activity");
        });
    helper.succeedWhen(
        () -> {
          helper.assertBlockPresent(Blocks.AIR, restingOre);
          helper.assertTrue(
              miner.getActivityProgress() == 1, "Recovered stamina must allow work to resume");
          miner.setWorking(false);
        });
  }

  @GameTest(template = "empty")
  public static void miningRecoveryPreservesBagToolStatsAndMode(GameTestHelper helper) {
    MiningSlime miner = miner(helper, new ItemStack(Items.DIAMOND_PICKAXE));
    miner.setWorking(false);
    miner.getPickaxe().setDamageValue(50);
    miner.getPickaxe().enchant(Enchantments.BLOCK_FORTUNE, 3);
    miner.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 10));
    miner.setHealth(7.0F);
    seed(miner, 2, 37, 13);
    miner.upgradeStat(SlimeStat.CAPACITY);
    Player owner = helper.makeMockSurvivalPlayer();
    miner.tame(owner);
    ItemStack tool = miner.getPickaxe().copy();
    var identity = miner.getUUID();
    helper.assertTrue(miner.capture(owner).consumesAction(), "A miner must be recoverable");
    ItemStack stored =
        owner.getInventory().items.stream()
            .filter(stack -> stack.is(ModItems.MINING_SLIME.get()))
            .findFirst()
            .orElseThrow();
    ItemStack reloaded = ItemStack.of(stored.save(new CompoundTag()));
    owner.getInventory().clearContent();
    owner.setItemInHand(InteractionHand.MAIN_HAND, reloaded);
    BlockPos floor = helper.absolutePos(new BlockPos(8, 1, 8));
    helper.assertTrue(
        reloaded
            .getItem()
            .useOn(
                new UseOnContext(
                    owner,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(
                        Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false)))
            .consumesAction(),
        "A stored miner must release as a miner");
    MiningSlime restored = helper.getEntities(ModEntities.MINING_SLIME.get(), FLOOR, 7).get(0);
    helper.assertTrue(
        identity.equals(restored.getUUID()) && restored.getHealth() == 7 && !restored.isWorking(),
        "Miner identity, health, and order must survive");
    helper.assertTrue(
        ItemStack.matches(tool, restored.getPickaxe()) && count(restored, Items.DIAMOND) == 10,
        "Exact tool and bag contents must survive");
    helper.assertTrue(
        restored.getStatLevel(SlimeStat.CAPACITY) == 1
            && restored.getStatPoints() == 1
            && restored.getActivityProgress() == 37
            && restored.getStamina() == 13,
        "Stats, points, activity, and current stamina must survive");
    helper.assertTrue(
        restored.getMiningChunk().equals(restored.chunkPosition()),
        "Releasing must anchor work to the destination chunk");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void equipmentSlotAndBagTransfersEnforceOwnership(GameTestHelper helper) {
    MiningSlime miner = miner(helper, ItemStack.EMPTY);
    miner.setWorking(false);
    Player owner = helper.makeMockSurvivalPlayer();
    miner.tame(owner);
    owner.moveTo(miner.getX() + 1, miner.getY(), miner.getZ());
    owner.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
    miner.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 5));
    SlimeMenu menu = new SlimeMenu(3, owner.getInventory(), miner);
    helper.assertTrue(
        menu.clickMenuButton(owner, SlimeMenu.WORK)
            && miner.isWorking()
            && menu.clickMenuButton(owner, SlimeMenu.FOLLOW)
            && miner.getMiningStatus() == MiningSlime.Status.FOLLOWING,
        "Follow orders must stop work and report the matching status");
    helper.assertTrue(
        !menu.getSlot(0).mayPlace(new ItemStack(Items.IRON_AXE)),
        "The equipment slot must reject non-pickaxes");
    helper.assertTrue(
        !menu.quickMoveStack(owner, 56).isEmpty() && miner.getPickaxe().is(Items.IRON_PICKAXE),
        "Shift-click must equip the pickaxe");
    helper.assertTrue(
        !menu.quickMoveStack(owner, 2).isEmpty() && miner.getBag().getStackInSlot(0).isEmpty(),
        "Shift-click must withdraw bag contents");
    Player stranger = helper.makeMockSurvivalPlayer();
    stranger.moveTo(owner.position());
    helper.assertTrue(
        menu.quickMoveStack(stranger, 0).isEmpty() && miner.getPickaxe().is(Items.IRON_PICKAXE),
        "A foreign player must not remove equipment");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void deniedBreakEventPreservesBlockToolAndProgress(GameTestHelper helper) {
    MiningSlime miner = miner(helper, new ItemStack(Items.IRON_PICKAXE));
    helper.setBlock(ORE, Blocks.STONE);
    BlockPos protectedPosition = helper.absolutePos(ORE);
    DENIED.add(protectedPosition);
    helper.runAfterDelay(
        60,
        () -> {
          try {
            helper.assertBlockPresent(Blocks.STONE, ORE);
            helper.assertTrue(
                miner.getPickaxe().getDamageValue() == 0
                    && miner.getActivityProgress() == 0
                    && count(miner, Items.COBBLESTONE) == 0,
                "Cancelled mining must not consume durability, award points, or duplicate loot");
            helper.succeed();
          } finally {
            DENIED.remove(protectedPosition);
            miner.setWorking(false);
          }
        });
  }

  @GameTest(template = "empty", batch = "chunk_boundary", timeoutTicks = 100)
  public static void miningNeverTargetsTheNeighbourChunk(GameTestHelper helper) {
    MiningSlime miner = miner(helper, new ItemStack(Items.IRON_PICKAXE));
    miner.moveTo(helper.absoluteVec(new Vec3(10.5, 2, 5.5)));
    miner.onReleased();
    CompoundTag state = miner.saveWithoutId(new CompoundTag());
    state.getCompound("SlimeStats").putInt(SlimeStat.REACH.key(), 20);
    miner.load(state);
    var anchor = miner.getMiningChunk();
    for (int x = anchor.getMinBlockX(); x <= anchor.getMaxBlockX(); x++) {
      for (int z = anchor.getMinBlockZ(); z <= anchor.getMaxBlockZ(); z++) {
        helper
            .getLevel()
            .setBlockAndUpdate(
                new BlockPos(x, miner.getBlockY() - 1, z), Blocks.BEDROCK.defaultBlockState());
        for (int y = miner.getBlockY(); y <= miner.getBlockY() + 8; y++) {
          helper
              .getLevel()
              .setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }
      }
    }
    BlockPos outside =
        new BlockPos(anchor.getMaxBlockX() + 1, miner.getBlockY(), miner.getBlockZ());
    helper.getLevel().setBlockAndUpdate(outside, Blocks.STONE.defaultBlockState());
    helper.runAfterDelay(
        60,
        () -> {
          try {
            helper.assertTrue(
                helper.getLevel().getBlockState(outside).is(Blocks.STONE),
                "The neighbour-chunk block must remain untouched");
            helper.assertTrue(
                miner.getActivityProgress() == 0,
                "No activity may be awarded for the neighbour-chunk target");
            helper.succeed();
          } finally {
            miner.setWorking(false);
            helper.getLevel().removeBlock(outside, false);
          }
        });
  }

  @GameTest(template = "empty", batch = "mob_griefing", timeoutTicks = 100)
  public static void disabledMobGriefingStopsMining(GameTestHelper helper) {
    MiningSlime miner = miner(helper, new ItemStack(Items.IRON_PICKAXE));
    helper.setBlock(ORE, Blocks.STONE);
    var rule = helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
    rule.set(false, helper.getLevel().getServer());
    helper.runAfterDelay(
        40,
        () -> {
          try {
            helper.assertBlockPresent(Blocks.STONE, ORE);
            helper.assertTrue(
                miner.getActivityProgress() == 0
                    && miner.getMiningStatus() == MiningSlime.Status.DISABLED,
                "The gamerule must stop mining and rewards");
            helper.succeed();
          } finally {
            miner.setWorking(false);
            rule.set(true, helper.getLevel().getServer());
          }
        });
  }

  private static MiningSlime miner(GameTestHelper helper, ItemStack tool) {
    for (int x = 0; x < 12; x++)
      for (int z = 0; z < 12; z++) helper.setBlock(x, 1, z, Blocks.BEDROCK);
    MiningSlime miner = helper.spawn(ModEntities.MINING_SLIME.get(), FLOOR.above());
    miner.tame(helper.makeMockSurvivalPlayer());
    miner.setItemSlot(EquipmentSlot.MAINHAND, tool);
    miner.onReleased();
    return miner;
  }

  private static void seed(FriendlySlime slime, int points, int activity, float stamina) {
    CompoundTag state = slime.saveWithoutId(new CompoundTag());
    state.putInt("StatPoints", points);
    state.putInt("ActivityProgress", activity);
    state.putFloat("Stamina", stamina);
    slime.load(state);
  }

  private static int count(MiningSlime miner, net.minecraft.world.item.Item item) {
    int count = 0;
    for (int slot = 0; slot < miner.getBag().getSlots(); slot++)
      if (miner.getBag().getStackInSlot(slot).is(item))
        count += miner.getBag().getStackInSlot(slot).getCount();
    return count;
  }

  private static TransientCraftingContainer grid() {
    return new TransientCraftingContainer(
        new AbstractContainerMenu(null, -1) {
          @Override
          public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
          }

          @Override
          public boolean stillValid(Player player) {
            return true;
          }
        },
        2,
        2);
  }
}
