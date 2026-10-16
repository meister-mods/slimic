package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.*;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.recipe.CompanionRecipe;
import io.github.meistermods.slimic.registry.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatCleaningGameTests {
  private static final int ARENA_OFFSET =
      131072 + (int) Math.floorMod(System.nanoTime(), 1_000_000L) * 16;

  private record Arena(GameTestHelper helper, Vec3 home, Player owner, int cx, int cz) {
    void done() {
      force(helper, cx, cz, false);
      helper.succeed();
    }
  }

  private static Arena arena(GameTestHelper helper) {
    helper.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
    BlockPos marker = helper.absolutePos(BlockPos.ZERO);
    int x = marker.getX() * 16 + ARENA_OFFSET, z = marker.getZ() * 16 + ARENA_OFFSET;
    int cx = x >> 4, cz = z >> 4;
    force(helper, cx, cz, true);
    Vec3 home = new Vec3(x + 5.5, 101, z + 5.5);
    helper
        .getLevel()
        .getEntitiesOfClass(
            net.minecraft.world.entity.Entity.class, new AABB(home, home).inflate(64))
        .forEach(net.minecraft.world.entity.Entity::discard);
    var drops =
        helper
            .getLevel()
            .getGameRules()
            .getRule(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS);
    boolean previousDrops = drops.get();
    drops.set(false, helper.getLevel().getServer());
    try {
      // Preparing a new arena can break natural plants above the replaced floor.
      // Their incidental drops must not become targets in pickup assertions.
      for (int dx = 0; dx < 12; dx++)
        for (int dz = 0; dz < 12; dz++) {
          helper
              .getLevel()
              .setBlockAndUpdate(
                  new BlockPos(x + dx, 100, z + dz), Blocks.BEDROCK.defaultBlockState());
          for (int y = 101; y <= 108; y++)
            helper
                .getLevel()
                .setBlockAndUpdate(new BlockPos(x + dx, y, z + dz), Blocks.AIR.defaultBlockState());
        }
    } finally {
      drops.set(previousDrops, helper.getLevel().getServer());
    }
    Player owner = helper.makeMockSurvivalPlayer();
    owner.moveTo(home);
    return new Arena(helper, home, owner, cx, cz);
  }

  private static void force(GameTestHelper h, int cx, int cz, boolean value) {
    for (int x = -1; x <= 1; x++)
      for (int z = -1; z <= 1; z++) h.getLevel().setChunkForced(cx + x, cz + z, value);
  }

  private static CombatSlime fighter(Arena a) {
    CombatSlime pet = ModEntities.COMBAT_SLIME.get().create(a.helper.getLevel());
    pet.setWorking(true);
    pet.moveTo(a.home);
    pet.tame(a.owner);
    a.helper.getLevel().addFreshEntity(pet);
    return pet;
  }

  private static CleaningSlime cleaner(Arena a) {
    CleaningSlime pet = ModEntities.CLEANING_SLIME.get().create(a.helper.getLevel());
    pet.setWorking(true);
    pet.moveTo(a.home);
    pet.tame(a.owner);
    a.helper.getLevel().addFreshEntity(pet);
    pet.onReleased();
    return pet;
  }

  private static ItemEntity drop(Arena a, ItemStack stack, Vec3 pos) {
    ItemEntity item = new ItemEntity(a.helper.getLevel(), pos.x, pos.y + .1, pos.z, stack);
    item.setDeltaMovement(Vec3.ZERO);
    item.setNoGravity(true);
    item.setNoPickUpDelay();
    a.helper.getLevel().addFreshEntity(item);
    return item;
  }

  private static void enclose(Arena a, ItemEntity item) {
    BlockPos center = item.blockPosition();
    for (Direction side : Direction.Plane.HORIZONTAL)
      for (int y = 0; y < 2; y++)
        a.helper
            .getLevel()
            .setBlockAndUpdate(center.relative(side).above(y), Blocks.STONE.defaultBlockState());
    a.helper.getLevel().setBlockAndUpdate(center.above(2), Blocks.STONE.defaultBlockState());
  }

  private static int count(BagSlime pet, Item item) {
    int count = 0;
    for (int slot = 0; slot < pet.getBag().getSlots(); slot++)
      if (pet.getBag().getStackInSlot(slot).is(item))
        count += pet.getBag().getStackInSlot(slot).getCount();
    return count;
  }

  @GameTest(template = "empty", batch = "combat_cleaning")
  public static void combatDefaultsAndAllSixTargetFilters(GameTestHelper h) {
    var server = h.getLevel().getServer();
    boolean originalPvp = server.isPvpAllowed();
    server.setPvpAllowed(true);
    try {
      Arena a = arena(h);
      CombatSlime pet = fighter(a);
      var zombie = EntityType.HUSK.create(h.getLevel());
      var creeper = EntityType.CREEPER.create(h.getLevel());
      var neutral = EntityType.ENDERMAN.create(h.getLevel());
      var cow = EntityType.COW.create(h.getLevel());
      var wolf = EntityType.WOLF.create(h.getLevel());
      Player stranger = h.makeMockSurvivalPlayer();
      wolf.tame(stranger);
      h.assertTrue(
          pet.mayAttack(zombie)
              && !pet.mayAttack(creeper)
              && !pet.mayAttack(neutral)
              && !pet.mayAttack(cow)
              && !pet.mayAttack(stranger)
              && !pet.mayAttack(wolf),
          "Defaults must enable only hostile mobs except creepers");
      for (CombatTarget kind : CombatTarget.values()) pet.setAllowed(kind, true);
      h.assertTrue(
          pet.mayAttack(creeper)
              && pet.mayAttack(neutral)
              && pet.mayAttack(cow)
              && pet.mayAttack(stranger)
              && pet.mayAttack(wolf),
          "Every independent target type must be selectable");
      wolf.tame(a.owner);
      h.assertTrue(
          !pet.mayAttack(a.owner) && !pet.mayAttack(wolf),
          "Owner and same-owner pets must always remain protected");
      pet.setAllowed(CombatTarget.HOSTILE, false);
      h.assertTrue(
          !pet.mayAttack(zombie) && pet.mayAttack(creeper),
          "Creepers must have an independent switch");
      a.done();
    } finally {
      server.setPvpAllowed(originalPvp);
    }
  }

  @GameTest(template = "empty", batch = "combat_cleaning")
  public static void combatMenuOwnsFiltersAndThreeEquipmentSlots(GameTestHelper h) {
    Arena a = arena(h);
    CombatSlime pet = fighter(a);
    SlimeMenu menu = TestMenus.create(1, a.owner.getInventory(), pet);
    h.assertTrue(
        menu.slots.size() == 66
            && menu.getSlot(0).mayPlace(new ItemStack(Items.IRON_SWORD))
            && menu.getSlot(1).mayPlace(new ItemStack(Items.SHIELD))
            && !menu.getSlot(2).mayPlace(new ItemStack(Items.DIAMOND_CHESTPLATE)),
        "Combat needs sword, shield, slime armor and a bag");
    h.assertTrue(
        menu.getSlot(2)
            .mayPlace(SlimeArmorItem.fromChestplate(new ItemStack(Items.DIAMOND_CHESTPLATE))),
        "Slime armor must fit its own slot");
    Player stranger = h.makeMockSurvivalPlayer();
    stranger.moveTo(a.home);
    h.assertTrue(
        !menu.clickMenuButton(stranger, SlimeMenu.TARGET_TOGGLE + CombatTarget.PLAYERS.ordinal())
            && !pet.allows(CombatTarget.PLAYERS),
        "Nonowners cannot edit attack filters");
    h.assertTrue(
        menu.clickMenuButton(a.owner, SlimeMenu.TARGET_TOGGLE + CombatTarget.NEUTRAL.ordinal())
            && pet.allows(CombatTarget.NEUTRAL),
        "Owner toggle must update server state");
    menu.clickMenuButton(a.owner, SlimeMenu.TARGET_PAGE_BUTTON);
    h.assertTrue(
        !menu.getSlot(3).isActive() && menu.getSlot(0).isActive(),
        "Target settings must hide bag slots without hiding equipment");
    a.done();
  }

  @GameTest(template = "empty", batch = "combat_cleaning")
  public static void armorAllMaterialsKeepNativeStatsAndCompleteItemData(GameTestHelper h) {
    for (Item chestplate :
        new Item[] {
          Items.LEATHER_CHESTPLATE,
          Items.CHAINMAIL_CHESTPLATE,
          Items.IRON_CHESTPLATE,
          Items.GOLDEN_CHESTPLATE,
          Items.DIAMOND_CHESTPLATE,
          Items.NETHERITE_CHESTPLATE
        }) {
      ItemStack source = new ItemStack(chestplate);
      source.setDamageValue(37);
      source.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 3);
      source.setHoverName(net.minecraft.network.chat.Component.literal("Guardian"));
      if (source.getItem() instanceof DyeableLeatherItem leather)
        leather.setColor(source, 0x3366ff);
      ItemStack armor = SlimeArmorItem.fromChestplate(source);
      h.assertTrue(
          armor.getItem() instanceof SlimeArmorItem
              && ItemStack.matches(SlimeArmorItem.baseArmor(armor), source)
              && armor.getMaxDamage() == source.getMaxDamage()
              && armor
                  .getAttributeModifiers(EquipmentSlot.CHEST)
                  .equals(source.getAttributeModifiers(EquipmentSlot.CHEST)),
          "Each material must preserve attributes, durability, enchantments, names and dye");
      armor.setDamageValue(41);
      h.assertTrue(
          SlimeArmorItem.baseArmor(armor).getDamageValue() == 41,
          "Current durability must remain authoritative");
    }
    ItemStack leather = SlimeArmorItem.fromChestplate(new ItemStack(Items.LEATHER_CHESTPLATE));
    ItemStack diamond = SlimeArmorItem.fromChestplate(new ItemStack(Items.DIAMOND_CHESTPLATE));
    ItemStack netherite = SlimeArmorItem.fromChestplate(new ItemStack(Items.NETHERITE_CHESTPLATE));
    h.assertTrue(
        leather.getItem().isValidRepairItem(leather, new ItemStack(Items.LEATHER))
            && !leather.getItem().isValidRepairItem(leather, new ItemStack(Items.IRON_INGOT))
            && netherite.getItem().isFireResistant(),
        "Repair ingredients and netherite fire resistance must match the material");
    var repair =
        new net.minecraft.world.item.crafting.RepairItemRecipe(
            ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "repair"),
            CraftingBookCategory.MISC);
    var repairGrid =
        new TransientCraftingContainer(
            new AbstractContainerMenu(null, 1) {
              public ItemStack quickMoveStack(Player p, int slot) {
                return ItemStack.EMPTY;
              }

              public boolean stillValid(Player p) {
                return true;
              }
            },
            2,
            2);
    leather.setDamageValue(50);
    repairGrid.setItem(0, leather);
    repairGrid.setItem(1, leather.copy());
    ItemStack repaired = repair.assemble(repairGrid, h.getLevel().registryAccess());
    h.assertTrue(
        repair.matches(repairGrid, h.getLevel())
            && repaired.getItem() == leather.getItem()
            && SlimeArmorItem.baseArmor(repaired).is(Items.LEATHER_CHESTPLATE),
        "Crafting repairs must preserve the armor material");
    repairGrid.setItem(1, diamond);
    h.assertTrue(
        !repair.matches(repairGrid, h.getLevel()),
        "Different armor materials cannot be mixed in repair recipes");
    h.assertTrue(
        !SlimeArmorItem.accepts(new ItemStack(Items.DIAMOND_HELMET))
            && !SlimeArmorItem.accepts(
                SlimeArmorItem.fromChestplate(new ItemStack(Items.IRON_CHESTPLATE))),
        "Only original chestplates may be converted");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "combat_cleaning")
  public static void specializationRecipesValidateIngredientsAndPreserveTraits(GameTestHelper h) {
    var grid =
        new TransientCraftingContainer(
            new AbstractContainerMenu(null, 1) {
              public ItemStack quickMoveStack(Player p, int i) {
                return ItemStack.EMPTY;
              }

              public boolean stillValid(Player p) {
                return true;
              }
            },
            2,
            2);
    ItemStack friendly = new ItemStack(ModItems.FRIENDLY_SLIME.get());
    friendly.getOrCreateTag().putString("Marker", "retained");
    for (CompanionRecipe.Kind kind : CompanionRecipe.Kind.values()) {
      CompanionRecipe recipe =
          new CompanionRecipe(
              ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "test"),
              CraftingBookCategory.MISC,
              kind);
      grid.clearContent();
      ItemStack first =
          kind == CompanionRecipe.Kind.ARMOR ? new ItemStack(Items.DIAMOND_CHESTPLATE) : friendly;
      ItemStack second =
          new ItemStack(
              switch (kind) {
                case COMBAT -> Items.IRON_SWORD;
                case CLEANING -> Items.ROTTEN_FLESH;
                case SADDLE -> Items.SADDLE;
                case ARMOR, SOGGY -> Items.SLIME_BALL;
                case FARMER -> Items.IRON_HOE;
                case LUMBERJACK -> Items.IRON_AXE;
              });
      grid.setItem(3, first);
      grid.setItem(0, second);
      h.assertTrue(
          recipe.matches(grid, h.getLevel())
              && !recipe.assemble(grid, h.getLevel().registryAccess()).isEmpty()
              && recipe.getRemainingItems(grid).stream().allMatch(ItemStack::isEmpty),
          "Specialization requires two shapeless ingredients without duplicate remainders");
      grid.setItem(1, new ItemStack(Items.STICK));
      h.assertTrue(!recipe.matches(grid, h.getLevel()), "Extra ingredients must be rejected");
    }
    h.assertTrue(
        CompanionRecipe.cleaning(friendly).getTag().getString("Marker").equals("retained"),
        "Cleaning specialization retains data");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "combat_cleaning", timeoutTicks = 240)
  public static void combatActuallyAttacksHostilesAndWearsItsSword(GameTestHelper h) {
    Arena a = arena(h);
    CombatSlime pet = fighter(a);
    ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
    sword.enchant(Enchantments.SHARPNESS, 5);
    pet.setEquipment(0, sword);
    var enemy = EntityType.HUSK.create(h.getLevel());
    enemy.moveTo(a.home.add(2.5, 0, 0));
    enemy.setNoAi(true);
    h.getLevel().addFreshEntity(enemy);
    var cow = EntityType.COW.create(h.getLevel());
    cow.moveTo(a.home.add(0, 0, 2));
    cow.setNoAi(true);
    h.getLevel().addFreshEntity(cow);
    h.succeedWhen(
        () -> {
          h.assertTrue(
              !enemy.isAlive()
                  && cow.getHealth() == cow.getMaxHealth()
                  && pet.getMainHandItem().getDamageValue() > 0
                  && pet.getActivityProgress() > 0,
              "Enabled hostile must be defeated using native sword damage/durability without"
                  + " harming passive mobs");
          force(h, a.cx, a.cz, false);
        });
  }

  @GameTest(template = "empty", batch = "combat_cleaning", timeoutTicks = 60)
  public static void slimeArmorReducesDamageAndConsumesDurability(GameTestHelper h) {
    Arena a = arena(h);
    CombatSlime armored = fighter(a);
    armored.setEquipment(2, SlimeArmorItem.fromChestplate(new ItemStack(Items.DIAMOND_CHESTPLATE)));
    CombatSlime bare = fighter(a);
    bare.moveTo(a.home.add(3, 0, 0));
    h.runAfterDelay(
        30,
        () -> {
          var attacker = EntityType.HUSK.create(h.getLevel());
          attacker.moveTo(a.home.add(0, 0, 2));
          armored.hurt(h.getLevel().damageSources().mobAttack(attacker), 8);
          bare.hurt(h.getLevel().damageSources().mobAttack(attacker), 8);
          h.assertTrue(
              armored.getHealth() > bare.getHealth()
                  && armored.getEquipment(2).getDamageValue() > 0,
              "Native armor mitigation and durability must both apply");
          a.done();
        });
  }

  @GameTest(template = "empty", batch = "combat_cleaning", timeoutTicks = 60)
  public static void equippedShieldBlocksFrontalAttacksAndWears(GameTestHelper h) {
    Arena a = arena(h);
    CombatSlime pet = fighter(a);
    pet.setNoAi(true);
    pet.setEquipment(1, new ItemStack(Items.SHIELD));
    pet.setYRot(0);
    pet.setYHeadRot(0);
    pet.setYBodyRot(0);
    var attacker = EntityType.HUSK.create(h.getLevel());
    attacker.moveTo(a.home.add(0, 0, 2));
    attacker.setNoAi(true);
    h.getLevel().addFreshEntity(attacker);
    pet.startUsingItem(InteractionHand.OFF_HAND);
    h.runAfterDelay(
        10,
        () -> {
          pet.hurt(h.getLevel().damageSources().mobAttack(attacker), 6);
          h.assertTrue(
              pet.getHealth() == pet.getMaxHealth() && pet.getOffhandItem().getDamageValue() > 0,
              "Native shield facing and durability must work for combat slimes");
          a.done();
        });
  }

  @GameTest(template = "empty", batch = "combat_cleaning", timeoutTicks = 160)
  public static void transportSuppliesCombatSwordWithoutReplacingEquipment(GameTestHelper h) {
    Arena a = arena(h);
    CombatSlime pet = fighter(a);
    pet.setNoAi(true);
    TransportSlime carrier = ModEntities.TRANSPORT_SLIME.get().create(h.getLevel());
    carrier.setWorking(true);
    carrier.moveTo(a.home.add(1, 0, 0));
    carrier.tame(a.owner);
    h.getLevel().addFreshEntity(carrier);
    carrier.onReleased();
    BlockPos chestPos = BlockPos.containing(a.home.add(2, 0, 0));
    h.getLevel().setBlockAndUpdate(chestPos, ModBlocks.SLIME_CHEST.get().defaultBlockState());
    var chest =
        (io.github.meistermods.slimic.block.SlimeChestBlockEntity)
            h.getLevel().getBlockEntity(chestPos);
    ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
    sword.setDamageValue(12);
    sword.enchant(Enchantments.SHARPNESS, 2);
    chest.setItem(0, sword.copy());
    h.succeedWhen(
        () -> {
          h.assertTrue(
              ItemStack.matches(pet.getMainHandItem(), sword) && chest.getItem(0).isEmpty(),
              "Carrier must fetch the exact compatible sword for a stopped combat slime");
          force(h, a.cx, a.cz, false);
        });
  }

  @GameTest(template = "empty", batch = "combat_cleaning", timeoutTicks = 400)
  public static void cleaningCollectsExactItemsAndReturnsToItsFixedHome(GameTestHelper h) {
    Arena a = arena(h);
    CleaningSlime pet = cleaner(a);
    ItemStack loot = new ItemStack(Items.DIAMOND_PICKAXE);
    loot.setDamageValue(27);
    loot.enchant(Enchantments.BLOCK_FORTUNE, 2);
    ItemEntity item = drop(a, loot.copy(), a.home.add(4, 0, 0));
    h.succeedWhen(
        () -> {
          h.assertTrue(
              item.isRemoved()
                  && ItemStack.matches(pet.getBag().getStackInSlot(0), loot)
                  && pet.position().distanceToSqr(a.home) <= 1.21
                  && pet.getCleaningStatus() == CleaningSlime.Status.IDLE,
              "Pickup/return: status="
                  + pet.getCleaningStatus()
                  + ", pos="
                  + pet.position()
                  + ", home="
                  + a.home
                  + ", item removed="
                  + item.isRemoved()
                  + ", bag="
                  + pet.getBag().getStackInSlot(0)
                  + ", ticks="
                  + pet.tickCount);
          force(h, a.cx, a.cz, false);
        });
  }

  @GameTest(template = "empty", batch = "combat_cleaning", timeoutTicks = 120)
  public static void cleaningHasFivefoldRangeAndNeverWandersOutsideItsHomeArea(GameTestHelper h) {
    Arena a = arena(h);
    CleaningSlime pet = cleaner(a);
    h.assertTrue(
        SlimeStat.DETECTION_RANGE.value(pet) == 40
            && SlimeStat.DETECTION_RANGE.tunerValue(pet) == 8,
        "Cleaning uses five times the common base detection");
    pet.setTunedVitals(16, 100, 1);
    pet.upgradeStat(SlimeStat.DETECTION_RANGE);
    h.assertTrue(
        SlimeStat.DETECTION_RANGE.value(pet) == 45,
        "Range upgrades must also be multiplied by five");
    ItemEntity outside = drop(a, new ItemStack(Items.DIAMOND), a.home.add(46, 0, 0));
    h.runAfterDelay(
        80,
        () -> {
          h.assertTrue(
              pet.position().distanceToSqr(a.home) < .01
                  && pet.getPickupTarget() == null
                  && outside.isAlive()
                  && !pet.hasCargo(),
              "Idle cleaners must stay home and ignore items outside the fixed radius");
          a.done();
        });
  }

  @GameTest(template = "empty", batch = "combat_cleaning", timeoutTicks = 100)
  public static void cleaningSwitchesAfterThreeMinutesWithoutAPickup(GameTestHelper h) {
    Arena a = arena(h);
    CleaningSlime pet = cleaner(a);
    ItemEntity blocked = drop(a, new ItemStack(Items.STICK), a.home.add(4, 0, 0));
    enclose(a, blocked);
    ItemEntity other = drop(a, new ItemStack(Items.DIAMOND), a.home.add(2, 0, 0));
    CompoundTag state = pet.saveWithoutId(new CompoundTag());
    state.putUUID("CleaningTarget", blocked.getUUID());
    state.putInt("CleaningAttemptTicks", 3599);
    pet.load(state);
    h.succeedWhen(
        () -> {
          h.assertTrue(
              (other.getUUID().equals(pet.getPickupTarget()) || count(pet, Items.DIAMOND) == 1)
                  && pet.getAttemptTicks() < 10
                  && pet.saveWithoutId(new CompoundTag())
                      .getUUID("CleaningFailed")
                      .equals(blocked.getUUID()),
              "Three-minute failure must choose another available item");
          a.done();
        });
  }

  @GameTest(template = "empty", batch = "combat_cleaning", timeoutTicks = 100)
  public static void cleaningRetriesTheSameItemWhenNoAlternativeExists(GameTestHelper h) {
    Arena a = arena(h);
    CleaningSlime pet = cleaner(a);
    ItemEntity blocked = drop(a, new ItemStack(Items.STICK), a.home.add(4, 0, 0));
    enclose(a, blocked);
    CompoundTag state = pet.saveWithoutId(new CompoundTag());
    state.putUUID("CleaningTarget", blocked.getUUID());
    state.putInt("CleaningAttemptTicks", 3599);
    pet.load(state);
    h.succeedWhen(
        () -> {
          h.assertTrue(
              blocked.getUUID().equals(pet.getPickupTarget())
                  && pet.getAttemptTicks() < 10
                  && blocked.isAlive(),
              "Without alternatives the three-minute timer must reset and retry the same item");
          a.done();
        });
  }

  @GameTest(template = "empty", batch = "combat_cleaning", timeoutTicks = 160)
  public static void cleaningPartialPickupKeepsRemainderAndFullBagCanResume(GameTestHelper h) {
    Arena a = arena(h);
    CleaningSlime pet = cleaner(a);
    pet.getBag().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 63));
    for (int slot = 1; slot < 9; slot++)
      pet.getBag().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
    ItemEntity item = drop(a, new ItemStack(Items.COBBLESTONE, 10), a.home.add(1, 0, 0));
    h.runAfterDelay(
        60,
        () -> {
          h.assertTrue(
              count(pet, Items.COBBLESTONE) == 64
                  && item.isAlive()
                  && item.getItem().getCount() == 9
                  && pet.needsUnloading(),
              "Partial pickup must preserve the remainder and request transport on full bags");
          pet.getBag().extractItem(0, 64, false);
        });
    h.runAfterDelay(
        100,
        () -> {
          h.assertTrue(
              item.isRemoved() && count(pet, Items.COBBLESTONE) == 9,
              "Clearing cargo space must resume pickup without deleting items");
          a.done();
        });
  }

  @GameTest(template = "empty", batch = "combat_cleaning")
  public static void actualSpecializationAndRecoveryKeepCombatAndCleaningState(GameTestHelper h) {
    recover(arena(h), true);
  }

  @GameTest(template = "empty", batch = "combat_cleaning", timeoutTicks = 120)
  public static void cleaningRespectsDelayOwnershipAndPickupCancellation(GameTestHelper h) {
    Arena a = arena(h);
    CleaningSlime pet = cleaner(a);
    ItemEntity delayed = drop(a, new ItemStack(Items.DIAMOND), a.home.add(1, 0, 0));
    delayed.setPickUpDelay(1000);
    ItemEntity reserved = drop(a, new ItemStack(Items.EMERALD), a.home.add(0, 0, 1));
    reserved.setTarget(UUID.randomUUID());
    ItemEntity canceled = drop(a, new ItemStack(Items.GOLD_INGOT), a.home.add(-1, 0, 0));
    java.util.function.Consumer<net.minecraftforge.event.entity.player.EntityItemPickupEvent>
        listener =
            event -> {
              if (event.getItem() == canceled) event.setCanceled(true);
            };
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener);
    h.runAfterDelay(
        60,
        () -> {
          net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);
          h.assertTrue(
              !pet.hasCargo() && delayed.isAlive() && reserved.isAlive() && canceled.isAlive(),
              "Protected pickups: cargo="
                  + pet.hasCargo()
                  + ", delayed="
                  + delayed.isAlive()
                  + ", reserved="
                  + reserved.isAlive()
                  + ", canceled="
                  + canceled.isAlive());
          a.done();
        });
  }

  @GameTest(template = "empty", batch = "combat_cleaning", timeoutTicks = 240)
  public static void transportUnloadsFullCleanerAndCollectionResumes(GameTestHelper h) {
    Arena a = arena(h);
    CleaningSlime pet = cleaner(a);
    for (int slot = 0; slot < 9; slot++)
      pet.getBag().setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
    ItemEntity loot = drop(a, new ItemStack(Items.DIAMOND), a.home.add(1, 0, 0));
    TransportSlime carrier = ModEntities.TRANSPORT_SLIME.get().create(h.getLevel());
    carrier.setWorking(true);
    carrier.moveTo(a.home.add(2, 0, 0));
    carrier.tame(a.owner);
    h.getLevel().addFreshEntity(carrier);
    carrier.onReleased();
    BlockPos chestPos = BlockPos.containing(a.home.add(3, 0, 0));
    h.getLevel().setBlockAndUpdate(chestPos, ModBlocks.SLIME_CHEST.get().defaultBlockState());
    var chest =
        (io.github.meistermods.slimic.block.SlimeChestBlockEntity)
            h.getLevel().getBlockEntity(chestPos);
    h.succeedWhen(
        () -> {
          h.assertTrue(
              !loot.isAlive()
                  && count(pet, Items.DIAMOND) == 1
                  && chest.getItem(0).is(Items.COBBLESTONE),
              "Transport must unload a stopped cleaner into its chest so collection resumes");
          force(h, a.cx, a.cz, false);
        });
  }

  private static void recover(Arena a, boolean combat) {
    GameTestHelper h = a.helper;
    FriendlySlime pet = combat ? fighter(a) : cleaner(a);
    pet.setNoAi(true);
    pet.dye(a.owner, new ItemStack(Items.BLUE_DYE));
    pet.setTunedVitals(7, 63, 5);
    pet.setCustomName(net.minecraft.network.chat.Component.literal("Keeper"));
    ((BagSlime) pet).getBag().setStackInSlot(0, new ItemStack(Items.EMERALD, 13));
    if (pet instanceof CombatSlime fighter) {
      fighter.setEquipment(0, new ItemStack(Items.DIAMOND_SWORD));
      fighter.setEquipment(1, new ItemStack(Items.SHIELD));
      fighter.setEquipment(
          2, SlimeArmorItem.fromChestplate(new ItemStack(Items.NETHERITE_CHESTPLATE)));
      fighter.setAllowed(CombatTarget.NEUTRAL, true);
      fighter.setWorking(false);
    } else {
      CompoundTag data = pet.saveWithoutId(new CompoundTag());
      data.putInt("CleaningAttemptTicks", 1234);
      data.putUUID("CleaningTarget", UUID.randomUUID());
      data.putInt("CleaningFailedCooldown", 555);
      pet.load(data);
    }
    h.runAfterDelay(
        5,
        () -> {
          // Snapshot current timers after registration, then compare the release before another
          // tick.
          CompoundTag before = pet.saveWithoutId(new CompoundTag());
          UUID id = pet.getUUID();
          h.assertTrue(pet.capture(a.owner).consumesAction(), "New companion must be recoverable");
          ItemStack stored =
              a.owner.getInventory().items.stream()
                  .filter(
                      stack ->
                          stack.is(
                              combat ? ModItems.COMBAT_SLIME.get() : ModItems.CLEANING_SLIME.get()))
                  .findFirst()
                  .orElseThrow();
          ItemStack reloaded = ItemStack.of(stored.save(new CompoundTag()));
          a.owner.getInventory().clearContent();
          a.owner.setItemInHand(InteractionHand.MAIN_HAND, reloaded);
          BlockPos floor = BlockPos.containing(a.home).below();
          h.assertTrue(
              reloaded
                  .getItem()
                  .useOn(
                      new UseOnContext(
                          a.owner,
                          InteractionHand.MAIN_HAND,
                          new BlockHitResult(
                              Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false)))
                  .consumesAction(),
              "Saved item must release as the same specialization");
          h.runAfterDelay(
              2,
              () -> {
                FriendlySlime restored =
                    h
                        .getLevel()
                        .getEntitiesOfClass(
                            FriendlySlime.class, new AABB(a.home, a.home).inflate(4))
                        .stream()
                        .filter(entity -> entity.getUUID().equals(id) && !entity.isRemoved())
                        .findFirst()
                        .orElse(null);
                h.assertTrue(
                    restored != null
                        && restored.getHealth() == 7
                        && restored.getDyeColorId() == DyeColor.BLUE.getId()
                        && restored.getStatPoints() == 5
                        && ((BagSlime) restored).getBag().getStackInSlot(0).getCount() == 13
                        && reloaded.isEmpty(),
                    "Actual recovery must retain health, dye, stats, cargo, identity and item"
                        + " consumption: "
                        + restored);
                if (restored instanceof CombatSlime fighter)
                  h.assertTrue(
                      fighter.allows(CombatTarget.NEUTRAL)
                          && !fighter.isWorking()
                          && fighter.getEquipment(1).is(Items.SHIELD)
                          && SlimeArmorItem.baseArmor(fighter.getEquipment(2))
                              .is(Items.NETHERITE_CHESTPLATE),
                      "Combat filters, order and all equipment must survive release");
                else
                  h.assertTrue(
                      ((CleaningSlime) restored).getAttemptTicks()
                              == before.getInt("CleaningAttemptTicks")
                          && restored
                                  .saveWithoutId(new CompoundTag())
                                  .getInt("CleaningFailedCooldown")
                              >= before.getInt("CleaningFailedCooldown") - 2,
                      "Cleaning retry state must survive item storage");
                restored.discard();
                a.owner.getInventory().clearContent();
                if (combat) recover(a, false);
                else a.done();
              });
        });
  }
}
