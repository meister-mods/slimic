package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe;
import io.github.meistermods.slimic.registry.*;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OrderTwelveGameTests {
  private static List<EntityType<? extends FriendlySlime>> types() {
    return List.of(
        ModEntities.FISHING_SLIME.get(),
        ModEntities.ABUNDANCE_SLIME.get(),
        ModEntities.TUNNEL_SLIME.get(),
        ModEntities.DRAGON_SLIME.get(),
        ModEntities.MEOW_SLIME.get());
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

  private static <T extends FriendlySlime> T companion(
      GameTestHelper h,
      LoadedWorkArea a,
      EntityType<T> type,
      Player owner,
      double x,
      double y,
      double z) {
    T slime = type.create(h.getLevel());
    slime.tame(owner);
    slime.moveTo(a.point(x, y, z));
    slime.onReleased();
    h.getLevel().addFreshEntity(slime);
    return slime;
  }

  private static void set(Object target, String field, Object value) {
    try {
      var f = target.getClass().getDeclaredField(field);
      f.setAccessible(true);
      f.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @GameTest(template = "empty", batch = "order_twelve")
  public static void everyNewSpecializationStartsInWaitAndKeepsItsOwnRecoveryItem(
      GameTestHelper h) {
    for (var type : types()) {
      var slime = type.create(h.getLevel());
      slime.tame(h.makeMockSurvivalPlayer());
      h.assertTrue(
          slime.isOrderedToSit() && !slime.isWorking(), "Fresh specialization waits: " + type);
      slime.setHealth(5);
      slime.setCustomName(Component.literal("Same companion"));
      slime.setTunedVitals(5, 37, 4);
      if (slime instanceof BagSlime bag)
        bag.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 7));
      if (slime instanceof FishingSlime fisher) {
        var rod = new ItemStack(Items.FISHING_ROD);
        rod.enchant(Enchantments.FISHING_LUCK, 3);
        rod.setDamageValue(9);
        fisher.setRod(rod);
      }
      var item = FriendlySlimeItem.fromSlime(slime);
      h.assertTrue(
          item.is(slime.getPickResult().getItem()),
          "Recovery retains specialized item type: " + type);
      var restored = type.create(h.getLevel());
      restored.load(item.getTag().getCompound("SlimeData"));
      h.assertTrue(
          restored.getUUID().equals(slime.getUUID())
              && restored.getHealth() == 5
              && restored.getStamina() == 37
              && restored.getStatPoints() == 4
              && restored.getOwnerUUID().equals(slime.getOwnerUUID()),
          "Identity and vitals persist: " + type);
      if (restored instanceof BagSlime bag)
        h.assertTrue(bag.getBag().getStackInSlot(0).getCount() == 7, "Cargo persists");
      if (restored instanceof FishingSlime fisher)
        h.assertTrue(
            ItemStack.matches(fisher.getRod(), ((FishingSlime) slime).getRod()),
            "Exact rod persists");
      restored.setWaiting(false);
      restored.load(new CompoundTag());
      h.assertTrue(
          restored.isOrderedToSit() && !restored.isWorking(),
          "Missing order data safely waits: " + type);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_twelve")
  public static void allFiveShapelessRecipesWorkInEveryInventoryGridPosition(GameTestHelper h) {
    for (var kind : AdvancedCompanionRecipe.Kind.values()) {
      var second =
          new ItemStack(
              switch (kind) {
                case FISHING -> Items.FISHING_ROD;
                case ABUNDANCE -> Items.BONE_MEAL;
                case GLOW -> Items.GLOWSTONE_DUST;
                case COMMAND -> Items.BOOK;
                case ENDER -> Items.ENDER_PEARL;
                case ATTENTION -> Items.REDSTONE;
                case ROYAL_GUARD -> ModItems.ANCIENT_COMMAND_SEAL.get();

                case TUNNEL -> Items.MINECART;
                case DRAGON -> Items.DRAGON_HEAD;
                case MEOW -> Items.COD;
              });
      for (int first = 0; first < 4; first++)
        for (int other = 0; other < 4; other++) {
          if (first == other) continue;
          var grid = grid(2);
          var source = new ItemStack(AdvancedCompanionRecipe.source(kind));
          source.setHoverName(Component.literal("Named"));
          source.getOrCreateTag().putString("CustomData", "retained");
          grid.setItem(first, source);
          grid.setItem(other, second.copy());
          if (kind == AdvancedCompanionRecipe.Kind.ABUNDANCE)
            for (int i = 0; i < 4; i++)
              if (i != first && i != other) {
                grid.setItem(i, new ItemStack(Items.NETHER_STAR));
                break;
              }
          var recipe =
              h.getLevel()
                  .getRecipeManager()
                  .getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel())
                  .orElseThrow();
          var result = recipe.assemble(grid, h.getLevel().registryAccess());
          h.assertTrue(
              result.is(AdvancedCompanionRecipe.result(kind))
                  && result.getCount() == 1
                  && result.getTag().getString("CustomData").equals("retained")
                  && result.getHoverName().getString().equals("Named"),
              "Correct result and preserved data: " + kind);
          h.assertTrue(
              recipe.getRemainingItems(grid).stream().allMatch(ItemStack::isEmpty),
              "No duplicated ingredients");
        }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_twelve")
  public static void fishingUsesExactEnchantedRodAndMeowRejectsCookedFish(GameTestHelper h) {
    var rod = new ItemStack(Items.FISHING_ROD);
    rod.enchant(Enchantments.FISHING_LUCK, 3);
    rod.enchant(Enchantments.FISHING_SPEED, 2);
    rod.enchant(Enchantments.UNBREAKING, 3);
    rod.setDamageValue(14);
    rod.setHoverName(Component.literal("Lucky rod"));
    var grid = grid(2);
    grid.setItem(0, new ItemStack(ModItems.FRIENDLY_SLIME.get()));
    grid.setItem(3, rod);
    var recipe =
        h.getLevel()
            .getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel())
            .orElseThrow();
    var output = recipe.assemble(grid, h.getLevel().registryAccess());
    h.assertTrue(
        ItemStack.matches(ItemStack.of(output.getTag().getCompound("InitialFishingRod")), rod),
        "Full rod data retained");
    for (var fish : new Item[] {Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH}) {
      grid.setItem(3, new ItemStack(fish));
      h.assertTrue(
          h.getLevel()
              .getRecipeManager()
              .getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel())
              .orElseThrow()
              .assemble(grid, h.getLevel().registryAccess())
              .is(ModItems.MEOW_SLIME.get()),
          "Every raw fish accepted");
    }
    for (var fish : new Item[] {Items.COOKED_COD, Items.COOKED_SALMON, Items.INK_SAC}) {
      grid.setItem(3, new ItemStack(fish));
      h.assertTrue(
          h.getLevel()
              .getRecipeManager()
              .getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel())
              .isEmpty(),
          "Cooked fish and unrelated ingredients rejected");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_twelve", timeoutTicks = 100)
  public static void nativeSummoningEquipsTheOriginalRodAndFreshCreativeDefaultsHaveARod(
      GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var rod = new ItemStack(Items.FISHING_ROD);
    rod.enchant(Enchantments.FISHING_SPEED, 3);
    rod.setDamageValue(11);
    var item =
        AdvancedCompanionRecipe.specialize(
            AdvancedCompanionRecipe.Kind.FISHING,
            new ItemStack(ModItems.FRIENDLY_SLIME.get()),
            rod);
    owner.moveTo(a.point(5, 101, 5));
    owner.setItemInHand(InteractionHand.MAIN_HAND, item);
    var floor = BlockPos.containing(a.point(5, 100, 5));
    var outcome =
        ((FriendlySlimeItem) item.getItem())
            .useOn(
                new UseOnContext(
                    owner,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(
                        Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false)));
    h.assertTrue(
        outcome.consumesAction() && item.isEmpty(), "Native Survival use consumes one summon item");
    var fishers =
        h.getLevel()
            .getEntitiesOfClass(
                FishingSlime.class, new net.minecraft.world.phys.AABB(floor.above()).inflate(2));
    h.assertTrue(
        fishers.size() == 1
            && ItemStack.matches(fishers.get(0).getRod(), rod)
            && fishers.get(0).isOrderedToSit(),
        "Rod is equipped exactly once and fisher waits");
    fishers.forEach(FriendlySlime::discard);
    a.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_twelve")
  public static void healerAndAbundanceKeepDoubleRangeAtAllUpgradesAndTuning(GameTestHelper h) {
    for (var type : List.of(ModEntities.HEALING_SLIME.get(), ModEntities.ABUNDANCE_SLIME.get())) {
      var healer = type.create(h.getLevel());
      healer.setTunedVitals(16, 100, 100);
      for (int i = 0; i <= 20; i++) {
        h.assertTrue(
            SlimeStat.DETECTION_RANGE.value(healer) == (8 + i) * 2,
            "Every range upgrade stays doubled");
        if (i < 20)
          h.assertTrue(healer.upgradeStat(SlimeStat.DETECTION_RANGE), "Range upgrade succeeds");
      }
      SlimeStat.DETECTION_RANGE.tune(healer, 40);
      h.assertTrue(SlimeStat.DETECTION_RANGE.value(healer) == 80, "Tuning retains multiplier");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_twelve")
  public static void tunnelTemplateCannotBeConsumedMovedOrDuplicated(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var tunnel = ModEntities.TUNNEL_SLIME.get().create(h.getLevel());
    tunnel.tame(owner);
    tunnel.moveTo(owner.position());
    var menu = TestMenus.create(1, owner.getInventory(), tunnel);
    var stack = new ItemStack(Items.STONE_BRICKS, 37);
    menu.setCarried(stack);
    menu.clicked(2, 0, ClickType.PICKUP, owner);
    h.assertTrue(
        menu.getCarried().getCount() == 37
            && tunnel.getLiningTemplate().is(Items.STONE_BRICKS)
            && tunnel.getLiningTemplate().getCount() == 1,
        "Setting copies a one-item template without consumption");
    for (var type :
        List.of(
            ClickType.QUICK_MOVE,
            ClickType.SWAP,
            ClickType.CLONE,
            ClickType.THROW,
            ClickType.PICKUP_ALL)) menu.clicked(2, 0, type, owner);
    h.assertTrue(
        menu.quickMoveStack(owner, 2).isEmpty()
            && menu.getCarried().getCount() == 37
            && tunnel.getLiningTemplate().getCount() == 1,
        "Template is never a transferable inventory stack");
    menu.clicked(menu.slots.size() - 1, 0, ClickType.PICKUP_ALL, owner);
    h.assertTrue(
        menu.getCarried().getCount() == 37 && !menu.slots.get(2).mayPickup(owner),
        "Double-clicking another slot cannot collect the ghost template");
    h.assertTrue(
        menu.slots.get(2).container.removeItem(2, 1).isEmpty()
            && menu.slots.get(2).container.removeItemNoUpdate(2).isEmpty(),
        "Direct container extraction cannot mint a block");
    menu.setCarried(new ItemStack(Items.TNT));
    menu.clicked(2, 0, ClickType.PICKUP, owner);
    h.assertTrue(tunnel.getLiningTemplate().is(Items.STONE_BRICKS), "Unstable materials rejected");
    menu.setCarried(ItemStack.EMPTY);
    menu.clicked(2, 0, ClickType.PICKUP, owner);
    h.assertTrue(tunnel.getLiningTemplate().isEmpty(), "Empty cursor clears the template");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_twelve")
  public static void directionChangesPauseTunnelingAndPreserveTheChosenTemplate(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var tunnel = ModEntities.TUNNEL_SLIME.get().create(h.getLevel());
    tunnel.tame(owner);
    tunnel.moveTo(owner.position());
    tunnel.setWorking(true);
    var menu = TestMenus.create(2, owner.getInventory(), tunnel);
    var before = tunnel.getTunnelDirection();
    h.assertTrue(
        menu.clickMenuButton(owner, SlimeMenu.DIRECTION)
            && tunnel.getTunnelDirection() == before.getClockWise()
            && tunnel.isOrderedToSit()
            && !tunnel.isWorking(),
        "Direction changes stop work before reanchoring");
    var saved = FriendlySlimeItem.fromSlime(tunnel);
    var restored = ModEntities.TUNNEL_SLIME.get().create(h.getLevel());
    restored.load(saved.getTag().getCompound("SlimeData"));
    h.assertTrue(
        restored.getTunnelDirection() == tunnel.getTunnelDirection()
            && restored.getLiningTemplate().is(Items.COBBLESTONE),
        "Route and template persist");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_twelve")
  public static void meowActivationAndMovementAreIndependentAndOwnerOnly(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var cat = ModEntities.MEOW_SLIME.get().create(h.getLevel());
    cat.tame(owner);
    cat.moveTo(owner.position());
    var menu = TestMenus.create(3, owner.getInventory(), cat);
    h.assertTrue(cat.isActive() && cat.isOrderedToSit(), "Default is active and waiting");
    h.assertTrue(
        menu.clickMenuButton(owner, SlimeMenu.ACTIVE) && !cat.isActive() && cat.isOrderedToSit(),
        "Toggling activation does not move it");
    h.assertTrue(
        menu.clickMenuButton(owner, SlimeMenu.ROAM) && cat.isRoaming() && !cat.isActive(),
        "Roaming does not reactivate the aura");
    menu.clickMenuButton(owner, SlimeMenu.ACTIVE);
    menu.clickMenuButton(owner, SlimeMenu.WAIT);
    h.assertTrue(
        cat.isActive() && cat.isOrderedToSit() && !cat.isRoaming(),
        "Waiting retains the active aura");
    var foreign = h.makeMockSurvivalPlayer();
    foreign.setUUID(UUID.randomUUID());
    foreign.moveTo(owner.position());
    h.assertTrue(!menu.clickMenuButton(foreign, SlimeMenu.ACTIVE), "Foreign player cannot toggle");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_twelve", timeoutTicks = 900)
  public static void fishingActuallyProducesNativeLootAndWearsTheRod(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    for (int x = 5; x <= 11; x++)
      for (int z = 4; z <= 10; z++) {
        h.getLevel()
            .setBlock(
                BlockPos.containing(a.point(x, 99, z)), Blocks.BEDROCK.defaultBlockState(), 2);
        h.getLevel()
            .setBlock(BlockPos.containing(a.point(x, 100, z)), Blocks.WATER.defaultBlockState(), 2);
      }
    var fisher = companion(h, a, ModEntities.FISHING_SLIME.get(), owner, 3.5, 101, 7.5);
    fisher.setRod(new ItemStack(Items.FISHING_ROD));
    SlimeStat.WORK_SPEED.tune(fisher, 8);
    fisher.setWorking(true);
    h.startSequence()
        .thenWaitUntil(() -> h.assertTrue(fisher.hasCargo(), "Native fishing produces bag cargo"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  fisher.getRod().getDamageValue() > 0
                      && fisher.getActivityProgress() > 0
                      && fisher.getStamina() < 100,
                  "Fishing wears the rod and awards paid activity");
              fisher.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_twelve", timeoutTicks = 250)
  public static void abundanceGrowsCropsUsingStaminaWithoutConsumingBonemeal(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var crop = BlockPos.containing(a.point(8, 101, 8));
    h.getLevel().setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState(), 2);
    h.getLevel()
        .setBlock(
            BlockPos.containing(a.point(7, 100, 7)), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
    h.getLevel().setBlock(crop.above(2), Blocks.SEA_LANTERN.defaultBlockState(), 2);
    var slime = companion(h, a, ModEntities.ABUNDANCE_SLIME.get(), owner, 6.5, 101, 8.5);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    Blocks.WHEAT.defaultBlockState().canSurvive(h.getLevel(), crop),
                    "Crop site supports native survival"))
        .thenExecute(
            () -> {
              h.getLevel().setBlock(crop, Blocks.WHEAT.defaultBlockState(), 2);
              slime.setWorking(true);
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getBlockState(crop).is(Blocks.WHEAT)
                        && h.getLevel().getBlockState(crop).getValue(CropBlock.AGE) > 0,
                    "Crop receives a bonemeal effect; block="
                        + h.getLevel().getBlockState(crop)
                        + ", status="
                        + slime.getHealingStatus()
                        + ", position="
                        + slime.position()))
        .thenExecute(
            () -> {
              h.assertTrue(
                  slime.getStamina() < 100 && !slime.hasCargo() && slime.getActivityProgress() > 0,
                  "Growth spends stamina and needs no bonemeal stock");
              slime.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_twelve", timeoutTicks = 300)
  public static void tunnelBoresThreeByThreeAndNeverStartsAChunkQuarry(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    for (int x = 6; x <= 8; x++)
      for (int y = 101; y <= 104; y++)
        for (int z = 6; z <= 10; z++)
          h.getLevel()
              .setBlock(BlockPos.containing(a.point(x, y, z)), Blocks.STONE.defaultBlockState(), 2);
    var tunnel = companion(h, a, ModEntities.TUNNEL_SLIME.get(), owner, 5.5, 101, 8.5);
    tunnel.setTunnelDirection(Direction.EAST);
    var pick = new ItemStack(Items.DIAMOND_PICKAXE);
    pick.enchant(Enchantments.BLOCK_EFFICIENCY, 5);
    tunnel.setTool(0, pick);
    tunnel.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
    SlimeStat.WORK_SPEED.tune(tunnel, 8);
    SlimeStat.STAMINA.tune(tunnel, 1000);
    tunnel.setTunedVitals(16, 1000, 0);
    tunnel.setWorking(true);
    var untouched = BlockPos.containing(a.point(3, 100, 3));
    h.startSequence()
        .thenWaitUntil(
            () -> {
              for (int x = 6; x <= 8; x++)
                for (int y = 101; y <= 103; y++)
                  for (int z = 7; z <= 9; z++)
                    h.assertTrue(
                        h.getLevel().getBlockState(BlockPos.containing(a.point(x, y, z))).isAir(),
                        "Every tunnel cell clears: "
                            + x
                            + ","
                            + y
                            + ","
                            + z
                            + " status="
                            + tunnel.getTunnelStatus()
                            + " at="
                            + tunnel.position()
                            + " front="
                            + tunnel.getTunnelFront()
                            + " nav="
                            + tunnel.getNavigation().isDone());
            })
        .thenExecute(
            () -> {
              h.assertTrue(
                  tunnel.getQuarryPlan() == null
                      && h.getLevel().getBlockState(untouched).is(Blocks.BEDROCK),
                  "Tunnel has no quarry plan and preserves unrelated terrain");
              tunnel.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_twelve", timeoutTicks = 250)
  public static void transportSuppliesOneStackOfTheSelectedTunnelMaterial(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var chest = a.chest(8, 101, 6);
    chest.inventory().insertItem(0, new ItemStack(Items.STONE_BRICKS, 64), false);
    var tunnel = companion(h, a, ModEntities.TUNNEL_SLIME.get(), owner, 6.5, 101, 8.5);
    tunnel.setTunnelDirection(Direction.EAST);
    tunnel.setLiningTemplate(new ItemStack(Items.STONE_BRICKS));
    tunnel.setWorking(true);
    var carrier = companion(h, a, ModEntities.TRANSPORT_SLIME.get(), owner, 7.5, 101, 7.5);
    carrier.setWorking(true);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    tunnel.getActivityProgress() > 0,
                    "Carrier delivers selected material and lining resumes"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  tunnel.getLiningTemplate().is(Items.STONE_BRICKS)
                      && tunnel.getLiningTemplate().getCount() == 1
                      && chest.inventory().getStackInSlot(0).isEmpty(),
                  "One real stack is delivered while template survives");
              carrier.discard();
              tunnel.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_twelve", timeoutTicks = 150)
  public static void dragonFliesAndBreathesAtEnabledHostiles(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var dragon = companion(h, a, ModEntities.DRAGON_SLIME.get(), owner, 5.5, 104, 8.5);
    dragon.setEquipment(0, new ItemStack(Items.IRON_SWORD));
    dragon.setWorking(true);
    var target = EntityType.ZOMBIE.create(h.getLevel());
    target.moveTo(a.point(10.5, 106, 8.5));
    target.setNoAi(true);
    target.setNoGravity(true);
    target.setItemSlot(
        net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
    target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
    target.setHealth(100);
    h.getLevel().addFreshEntity(target);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    target.getHealth() < 100 && target.isOnFire(),
                    "Ranged flame damages and ignites enabled enemy"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  dragon.isNoGravity() && dragon.getY() > 104 && dragon.getStamina() < 100,
                  "Dragon ascends without gravity and pays for breath");
              target.discard();
              dragon.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_twelve", timeoutTicks = 150)
  public static void combatCollectsItsKillDropsAndRequestsCargoWithoutBlockingCombat(
      GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var fighter = companion(h, a, ModEntities.COMBAT_SLIME.get(), owner, 6.5, 101, 8.5);
    fighter.setEquipment(0, new ItemStack(Items.DIAMOND_SWORD));
    fighter.setWorking(true);
    var zombie = EntityType.ZOMBIE.create(h.getLevel());
    zombie.moveTo(a.point(7.5, 101, 8.5));
    zombie.setNoAi(true);
    zombie.setItemSlot(
        net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
    zombie.setHealth(1);
    h.getLevel().addFreshEntity(zombie);
    Consumer<LivingDropsEvent> drops =
        event -> {
          if (event.getEntity() != zombie) return;
          event.getDrops().clear();
          event
              .getDrops()
              .add(
                  new ItemEntity(
                      h.getLevel(),
                      zombie.getX(),
                      zombie.getY(),
                      zombie.getZ(),
                      new ItemStack(Items.ROTTEN_FLESH, 3)));
        };
    MinecraftForge.EVENT_BUS.addListener(drops);
    try {
      h.assertTrue(fighter.doHurtTarget(zombie), "Sword kills target");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(drops);
    }
    h.startSequence()
        .thenWaitUntil(() -> h.assertTrue(fighter.hasCargo(), "Own kill loot enters bag"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  fighter.getWorkStatus().requests(WorkStatus.Request.CARGO_PICKUP)
                      && !fighter.isWorkBlocked(),
                  "Cargo pickup requests do not stop combat");
              for (int i = 0; i < fighter.getCapacity(); i++)
                fighter.getBag().setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
              h.assertTrue(
                  !fighter.isWorkBlocked() && fighter.isWorking(),
                  "Full inventory remains working");
              fighter.discard();
              zombie.discard();
              a.close();
            })
        .thenSucceed();
  }
}
