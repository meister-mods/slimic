package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.*;
import io.github.meistermods.slimic.menu.*;
import io.github.meistermods.slimic.recipe.*;
import io.github.meistermods.slimic.registry.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OrderEightGameTests {
  private static FriendlySlime pet(GameTestHelper h, Player owner) {
    var pet = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    pet.tame(owner);
    return pet;
  }

  @GameTest(template = "empty", batch = "order_eight")
  public static void slimeballsHealAndBucketsWashOnlyOwnedSlimes(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var pet = pet(h, owner);
    pet.setHealth(9);
    owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SLIME_BALL, 3));
    pet.mobInteract(owner, InteractionHand.MAIN_HAND);
    h.assertTrue(
        pet.getHealth() == 13 && owner.getMainHandItem().getCount() == 2, "One ball heals four HP");
    pet.mobInteract(owner, InteractionHand.MAIN_HAND);
    pet.mobInteract(owner, InteractionHand.MAIN_HAND);
    h.assertTrue(
        pet.getHealth() == 16 && owner.getMainHandItem().getCount() == 1,
        "Health capped without consuming at full health");
    pet.dye(owner, new ItemStack(Items.RED_DYE));
    owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
    pet.mobInteract(owner, InteractionHand.MAIN_HAND);
    h.assertTrue(
        pet.getDyeColorId() == -1 && owner.getMainHandItem().is(Items.BUCKET),
        "Washing consumes water and returns the bucket");
    var other = h.makeMockSurvivalPlayer();
    pet.setHealth(9);
    other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SLIME_BALL));
    pet.mobInteract(other, InteractionHand.MAIN_HAND);
    h.assertTrue(pet.getHealth() == 9, "Foreign players cannot change health");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_eight")
  public static void survivalTunerRequiredForManagementAndCreativeEditorStaysRestricted(
      GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var pet = pet(h, owner);
    owner.moveTo(pet.position());
    var menu = new SlimeMenu(1, owner.getInventory(), pet);
    h.assertTrue(
        !menu.stillValid(owner) && !menu.clickMenuButton(owner, SlimeMenu.WAIT),
        "Packets without the management tool rejected");
    owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
    h.assertTrue(
        menu.stillValid(owner) && menu.clickMenuButton(owner, SlimeMenu.WAIT),
        "Survival tool enables normal commands");
    owner.setItemInHand(
        InteractionHand.MAIN_HAND, new ItemStack(ModItems.CREATIVE_SLIME_TUNER.get()));
    h.assertTrue(
        !menu.stillValid(owner)
            && CreativeSlimeTunerItem.open(owner, pet) == InteractionResult.FAIL,
        "Creative editor cannot replace Survival permissions");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_eight")
  public static void saddleMountsWithOrdinaryHeldItems(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var mount = ModEntities.SADDLE_SLIME.get().create(h.getLevel());
    mount.tame(owner);
    mount.setEquipment(0, new ItemStack(Items.SADDLE));
    owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 8));
    h.assertTrue(
        mount.mobInteract(owner, InteractionHand.MAIN_HAND).consumesAction()
            && owner.getVehicle() == mount
            && owner.getMainHandItem().getCount() == 8,
        "Held dirt does not prevent riding or get consumed");
    owner.stopRiding();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_eight", timeoutTicks = 100)
  public static void signalingCopiesOneRocketAndFiresWithoutConsumingMore(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var signaling = ModEntities.SIGNALING_SLIME.get().create(h.getLevel());
    signaling.setMonitoring(true);
    signaling.tame(owner);
    signaling.moveTo(area.point(8, 101, 8));
    h.getLevel().addFreshEntity(signaling);
    owner.moveTo(signaling.position());
    var rockets = SignalingSlime.defaultFirework();
    rockets.setCount(4);
    rockets.getOrCreateTag().putString("PatternName", "kept");
    owner.setItemInHand(InteractionHand.MAIN_HAND, rockets);
    signaling.mobInteract(owner, InteractionHand.MAIN_HAND);
    h.assertTrue(
        owner.getMainHandItem().getCount() == 3
            && signaling.getFirework().getTag().getString("PatternName").equals("kept"),
        "One original firework configures the design");
    owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    signaling.mobInteract(owner, InteractionHand.MAIN_HAND);
    h.runAfterDelay(
        12,
        () -> {
          owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
          var menu = new SlimeMenu(1, owner.getInventory(), signaling);
          h.assertTrue(
              menu.clickMenuButton(owner, SlimeMenu.FIRE_SIGNAL),
              "Management button launches stored design");
          var signals =
              h.getLevel()
                  .getEntitiesOfClass(
                      FireworkRocketEntity.class, signaling.getBoundingBox().inflate(32));
          h.assertTrue(
              signals.size() == 2
                  && signals.stream()
                      .allMatch(
                          r -> r.getPersistentData().getBoolean(SignalingSlime.SIGNAL_MARKER)),
              "Two harmless manual signals without rocket supply");
          area.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_eight")
  public static void guardianCraftingKeepsExactTotemAndSavedCompanion(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var friendly = ModEntities.HEALING_SLIME.get().create(h.getLevel());
    friendly.tame(owner);
    friendly.setHealth(7);
    var input = FriendlySlimeItem.fromSlime(friendly);
    var totem = new ItemStack(Items.TOTEM_OF_UNDYING);
    totem.getOrCreateTag().putString("Mark", "original");
    var recipe =
        new GuardianAngelSlimeRecipe(
            ResourceLocation.fromNamespaceAndPath("slimic", "audit"), CraftingBookCategory.MISC);
    var grid =
        new TransientCraftingContainer(
            new AbstractContainerMenu(null, 0) {
              @Override
              public boolean stillValid(Player p) {
                return true;
              }

              @Override
              public ItemStack quickMoveStack(Player p, int i) {
                return ItemStack.EMPTY;
              }
            },
            2,
            2);
    grid.setItem(0, input);
    grid.setItem(1, new ItemStack(Items.NETHER_STAR));
    grid.setItem(3, totem);
    h.assertTrue(recipe.matches(grid, h.getLevel()), "Three shapeless ingredients accepted");
    var result = recipe.assemble(grid, h.getLevel().registryAccess());
    h.assertTrue(
        result.is(ModItems.GUARDIAN_ANGEL_SLIME.get())
            && ItemStack.matches(
                ItemStack.of(result.getTag().getCompound("InitialGuardianTotem")), totem)
            && result.getTag().getCompound("SlimeData").getFloat("Health") == 7
            && recipe.getRemainingItems(grid).stream().allMatch(ItemStack::isEmpty),
        "Exact totem and health transfer without duplicate remainders");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_eight")
  public static void guardianHealthRemainsTenfoldAfterGrowthAndReload(GameTestHelper h) {
    var pet = ModEntities.GUARDIAN_ANGEL_SLIME.get().create(h.getLevel());
    pet.setWaiting(false);
    pet.tame(h.makeMockSurvivalPlayer());
    h.assertTrue(pet.getMaxHealth() == 64, "Fresh health is fourfold");
    pet.setTunedVitals(37, 41, 1);
    pet.upgradeStat(SlimeStat.HEALTH);
    pet.setTotems(new ItemStack(Items.TOTEM_OF_UNDYING, 12));
    var saved = pet.saveWithoutId(new CompoundTag());
    var loaded = ModEntities.GUARDIAN_ANGEL_SLIME.get().create(h.getLevel());
    loaded.setWaiting(false);
    loaded.load(saved);
    h.assertTrue(
        loaded.getMaxHealth() == 80
            && loaded.getHealth() == 37
            && loaded.getTotems().getCount() == 12
            && loaded.getStamina() == 41
            && FriendlySlimeItem.fromSlime(loaded).is(ModItems.GUARDIAN_ANGEL_SLIME.get()),
        "Health multiplier, resources and reserve survive serialization");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_eight")
  public static void guardianReserveAcceptsStacksThroughManagement(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var pet = ModEntities.GUARDIAN_ANGEL_SLIME.get().create(h.getLevel());
    pet.setWaiting(false);
    pet.tame(owner);
    owner.moveTo(pet.position());
    owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
    owner.getInventory().setItem(9, new ItemStack(Items.TOTEM_OF_UNDYING));
    var menu = new SlimeMenu(1, owner.getInventory(), pet);
    h.assertTrue(
        menu.getSlot(0).getMaxStackSize(new ItemStack(Items.TOTEM_OF_UNDYING)) == 64
            && !menu.getSlot(0).mayPlace(new ItemStack(Items.DIRT)),
        "Reserve slot accepts only totems up to64");
    pet.setTotems(new ItemStack(Items.TOTEM_OF_UNDYING, 63));
    menu.quickMoveStack(owner, 1);
    h.assertTrue(
        pet.getTotems().getCount() == 64 && owner.getInventory().getItem(9).isEmpty(),
        "Shift transfer fills one reserve without duplication");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_eight", timeoutTicks = 60)
  public static void guardianPreventsRealLethalDamageWithOneTotem(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    owner.moveTo(area.point(8, 101, 8));
    var guardian = ModEntities.GUARDIAN_ANGEL_SLIME.get().create(h.getLevel());
    guardian.setWaiting(false);
    guardian.tame(owner);
    guardian.moveTo(owner.position().add(2, 0, 0));
    guardian.setTotems(new ItemStack(Items.TOTEM_OF_UNDYING, 2));
    h.getLevel().addFreshEntity(guardian);
    owner.setHealth(2);
    h.startSequence()
        .thenWaitUntil(
            () -> h.assertTrue(guardian.tickCount > 0, "Guardian is indexed in the live chunk"))
        .thenExecute(
            () -> {
              owner.hurt(h.getLevel().damageSources().generic(), 40);
              h.assertTrue(
                  owner.isAlive()
                      && owner.getHealth() == 1
                      && owner.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION)
                      && owner.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION)
                      && guardian.getTotems().getCount() == 1,
                  "Native lethal damage invokes a single guardian totem; hp="
                      + owner.getHealth()
                      + " reserve="
                      + guardian.getTotems().getCount());
              area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_eight")
  public static void guardianHonorsTotemCancellationOwnerAndBypassDamage(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var guardian = ModEntities.GUARDIAN_ANGEL_SLIME.get().create(h.getLevel());
    guardian.setWaiting(false);
    guardian.tame(owner);
    guardian.setTotems(new ItemStack(Items.TOTEM_OF_UNDYING));
    owner.moveTo(guardian.position());
    Consumer<LivingUseTotemEvent> cancel =
        e -> {
          if (e.getEntity() == owner) e.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(cancel);
    boolean used = guardian.protect(owner, h.getLevel().damageSources().generic());
    MinecraftForge.EVENT_BUS.unregister(cancel);
    h.assertTrue(
        !used
            && guardian.getTotems().getCount() == 1
            && !guardian.protect(owner, h.getLevel().damageSources().fellOutOfWorld())
            && !guardian.protect(
                h.makeMockSurvivalPlayer(), h.getLevel().damageSources().generic()),
        "Cancelled, bypassing or foreign protection does not consume reserve");
    h.succeed();
  }

  private static LoggingSlime logger(GameTestHelper h, LoadedWorkArea a) {
    a.clearSurroundingTrees();
    var pet = ModEntities.LOGGING_SLIME.get().create(h.getLevel());
    pet.setWorking(true);
    pet.moveTo(a.point(6.5, 101, 5.5));
    pet.tame(h.makeMockSurvivalPlayer());
    pet.setTool(new ItemStack(Items.DIAMOND_AXE));
    pet.onReleased();
    h.getLevel().addFreshEntity(pet);
    return pet;
  }

  private static BlockPos tree(GameTestHelper h, LoadedWorkArea a, int x, Block log, Block leaves) {
    var root = BlockPos.containing(a.point(x, 101, 8));
    h.getLevel().setBlockAndUpdate(root.below(), Blocks.DIRT.defaultBlockState());
    for (int y = 0; y < 3; y++)
      h.getLevel().setBlockAndUpdate(root.above(y), log.defaultBlockState());
    h.getLevel()
        .setBlockAndUpdate(
            root.above(3),
            leaves
                .defaultBlockState()
                .setValue(LeavesBlock.PERSISTENT, false)
                .setValue(LeavesBlock.DISTANCE, 1));
    return root;
  }

  @GameTest(template = "empty", batch = "order_eight", timeoutTicks = 900)
  public static void suppliedSaplingRestartsWaitingLoggingAcrossDistance(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var pet = logger(h, a);
    pet.moveTo(a.point(3.5, 101, 5.5));
    pet.onReleased();
    var root = tree(h, a, 14, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    pet.getRequiredSapling().is(Items.OAK_SAPLING)
                        && pet.getRequiredSaplingCount() == 1,
                    "Shows exact missing sapling and quantity"))
        .thenExecute(() -> pet.getBag().setStackInSlot(0, new ItemStack(Items.OAK_SAPLING)))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getBlockState(root).is(Blocks.OAK_SAPLING),
                    "Supplying requested sapling resumes travel, logging and planting; status="
                        + pet.getFieldStatus()
                        + " pos="
                        + pet.position()
                        + " pending="
                        + pet.getPendingPlantings()
                        + " root="
                        + h.getLevel().getBlockState(root)
                        + " path="
                        + pet.getNavigation().getPath()))
        .thenExecute(
            () -> {
              pet.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_eight", timeoutTicks = 900)
  public static void unavailableSaplingDoesNotBlockOtherTreeSpecies(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var pet = logger(h, a);
    var birch = tree(h, a, 4, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES);
    var oak = tree(h, a, 9, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
    pet.getBag().setStackInSlot(0, new ItemStack(Items.OAK_SAPLING));
    h.succeedWhen(
        () -> {
          h.assertTrue(
              h.getLevel().getBlockState(oak).is(Blocks.OAK_SAPLING)
                  && h.getLevel().getBlockState(birch).is(Blocks.BIRCH_LOG),
              "Works a supplied species instead of blocking on missing birch saplings: "
                  + pet.getFieldStatus()
                  + " @"
                  + pet.position()
                  + " ticks="
                  + pet.tickCount
                  + " oak="
                  + h.getLevel().getBlockState(oak)
                  + " tool="
                  + pet.getTool());
          pet.discard();
          a.close();
        });
  }

  @GameTest(template = "empty", batch = "order_eight", timeoutTicks = 450)
  public static void minerRoutesAroundWoodWithoutHarvestingIt(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var pet = ModEntities.MINING_SLIME.get().create(h.getLevel());
    pet.setWorking(true);
    pet.moveTo(a.point(3.5, 101, 3.5));
    pet.tame(h.makeMockSurvivalPlayer());
    pet.setTool(0, new ItemStack(Items.DIAMOND_PICKAXE));
    pet.onReleased();
    pet.setWorking(true);
    h.getLevel().addFreshEntity(pet);
    var target = BlockPos.containing(a.point(12, 100, 12));
    h.getLevel().setBlockAndUpdate(target, Blocks.COBBLESTONE.defaultBlockState());
    var wood = BlockPos.containing(a.point(8, 101, 8));
    h.getLevel().setBlockAndUpdate(wood.below(), Blocks.OAK_LOG.defaultBlockState());
    h.getLevel().setBlockAndUpdate(wood, Blocks.OAK_LOG.defaultBlockState());
    h.getLevel().setBlockAndUpdate(wood.above(), Blocks.OAK_LOG.defaultBlockState());
    h.succeedWhen(
        () -> {
          h.assertTrue(
              h.getLevel().getBlockState(target).isAir()
                  && h.getLevel().getBlockState(wood).is(Blocks.OAK_LOG)
                  && h.getLevel().getBlockState(wood.below()).is(Blocks.OAK_LOG),
              "Miner advances around the wood and finishes stone");
          pet.discard();
          a.close();
        });
  }

  @GameTest(template = "empty", batch = "order_eight")
  public static void chestLidOpensAndClosesWithoutChangingInventory(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var chest = a.chest(8, 101, 8);
    chest.setItem(0, new ItemStack(Items.DIAMOND, 13));
    chest.triggerEvent(1, 1);
    for (int i = 0; i < 12; i++) chest.tickLid();
    h.assertTrue(chest.getOpenness(1) > .99F, "Lid reaches open position");
    chest.triggerEvent(1, 0);
    for (int i = 0; i < 12; i++) chest.tickLid();
    h.assertTrue(
        chest.getOpenness(1) < .01F
            && chest.getItem(0).getCount() == 13
            && chest.getContainerSize() == 27,
        "Lid closes while inventory remains intact");
    a.close();
    h.succeed();
  }

  private static net.minecraftforge.common.util.FakePlayer realOwner(
      GameTestHelper h, LoadedWorkArea a) {
    var p =
        new net.minecraftforge.common.util.FakePlayer(
            h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "GuardianOwner")) {
          @Override
          public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
            return false;
          }

          @Override
          public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
            actuallyHurt(source, amount);
            return true;
          }
        };
    p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    p.moveTo(a.point(8, 101, 8));
    h.getLevel().addNewPlayer(p);
    return p;
  }

  @GameTest(template = "empty", batch = "order_eight", timeoutTicks = 1000)
  public static void guardianFliesAndRetaliatesAfterActualOwnerDamage(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var previousDifficulty = h.getLevel().getDifficulty();
    h.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
    var owner = realOwner(h, a);
    h.runAfterDelay(
        999,
        () -> {
          owner.discard();
          h.getLevel().getServer().setDifficulty(previousDifficulty, true);
        });
    var guard = ModEntities.GUARDIAN_ANGEL_SLIME.get().create(h.getLevel());
    guard.setWaiting(false);
    guard.tame(owner);
    guard.moveTo(a.point(3, 103, 8));
    h.getLevel().addFreshEntity(guard);
    var attacker = EntityType.HUSK.create(h.getLevel());
    attacker.setPersistenceRequired();
    attacker.setNoAi(true);
    attacker.moveTo(a.point(9, 101, 8));
    h.getLevel().addFreshEntity(attacker);
    h.startSequence()
        .thenWaitUntil(() -> h.assertTrue(guard.tickCount > 0, "Guard is active"))
        .thenExecute(
            () ->
                h.assertTrue(
                    owner.isAlive()
                        && owner.hurt(h.getLevel().damageSources().mobAttack(attacker), 2),
                    "Owner remains alive and receives actual damage before retaliation"))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    attacker.getHealth() < 20
                        && guard.getY() > 101.5
                        && guard.distanceToSqr(owner) < 16,
                    "Flying guard retaliates through native player damage events; guard="
                        + guard.position()
                        + " owner="
                        + owner.position()
                        + " enemyHP="
                        + attacker.getHealth()))
        .thenExecute(
            () -> {
              guard.discard();
              attacker.discard();
              owner.discard();
              h.getLevel().getServer().setDifficulty(previousDifficulty, true);
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_eight")
  public static void guardianReserveNativeClicksAndUnloadingConserveTotems(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var guard = ModEntities.GUARDIAN_ANGEL_SLIME.get().create(h.getLevel());
    guard.setWaiting(false);
    guard.tame(owner);
    owner.moveTo(guard.position());
    owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
    var menu = new SlimeMenu(1, owner.getInventory(), guard);
    guard.setTotems(new ItemStack(Items.TOTEM_OF_UNDYING, 63));
    menu.setCarried(new ItemStack(Items.TOTEM_OF_UNDYING));
    menu.clicked(0, 0, ClickType.PICKUP, owner);
    h.assertTrue(
        guard.getTotems().getCount() == 64 && menu.getCarried().isEmpty(),
        "Native click stacks a nonstackable totem into reserve");
    menu.clicked(0, 0, ClickType.PICKUP, owner);
    h.assertTrue(
        menu.getCarried().getCount() == 1 && guard.getTotems().getCount() == 63,
        "Native pickup returns an ordinary single totem");
    menu.clicked(0, 0, ClickType.PICKUP, owner);
    menu.quickMoveStack(owner, 0);
    int count = guard.getTotems().getCount();
    for (int slot = 0; slot < 36; slot++) {
      var stack = owner.getInventory().getItem(slot);
      if (stack.is(Items.TOTEM_OF_UNDYING)) {
        h.assertTrue(stack.getCount() == 1, "Player inventory respects native totem stack limit");
        count += stack.getCount();
      }
    }
    h.assertTrue(
        count == 64 && guard.getTotems().getCount() == 28,
        "Shift unloading spreads totems and preserves overflow");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_eight", timeoutTicks = 100)
  public static void heldTotemTakesPriorityAndMultipleGuardiansConsumeOnlyOne(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    owner.moveTo(area.point(8, 101, 8));
    var first = ModEntities.GUARDIAN_ANGEL_SLIME.get().create(h.getLevel());
    first.setWaiting(false);
    var second = ModEntities.GUARDIAN_ANGEL_SLIME.get().create(h.getLevel());
    second.setWaiting(false);
    for (var guard : List.of(first, second)) {
      guard.tame(owner);
      guard.moveTo(owner.position().add(2, 0, 0));
      guard.setTotems(new ItemStack(Items.TOTEM_OF_UNDYING, 2));
      h.getLevel().addFreshEntity(guard);
    }
    owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(first.tickCount > 0 && second.tickCount > 0, "Both guardians active"))
        .thenExecute(
            () -> {
              owner.setHealth(2);
              owner.hurt(h.getLevel().damageSources().generic(), 40);
              h.assertTrue(
                  owner.isAlive()
                      && owner.getOffhandItem().isEmpty()
                      && first.getTotems().getCount() + second.getTotems().getCount() == 4,
                  "Held native totem acts before reserve");
              owner.removeAllEffects();
              owner.setAbsorptionAmount(0);
              owner.invulnerableTime = 0;
              owner.setHealth(2);
              owner.hurt(h.getLevel().damageSources().generic(), 40);
              h.assertTrue(
                  owner.isAlive()
                      && owner.getHealth() == 1
                      && first.getTotems().getCount() + second.getTotems().getCount() == 3,
                  "Only one reserve totem consumed among nearby guardians");
              first.discard();
              second.discard();
              area.close();
            })
        .thenSucceed();
  }
}
