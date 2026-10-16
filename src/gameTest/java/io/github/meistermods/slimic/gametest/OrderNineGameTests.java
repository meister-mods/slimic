package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.recipe.CompanionRecipe;
import io.github.meistermods.slimic.registry.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.gametest.*;
import net.minecraftforge.items.IItemHandler;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OrderNineGameTests {
  private static SoggySlime soggy(GameTestHelper h, LoadedWorkArea a) {
    var pet = ModEntities.SOGGY_SLIME.get().create(h.getLevel());
    pet.tame(h.makeMockSurvivalPlayer());
    pet.moveTo(a.point(6.5, 101, 8.5));
    pet.setNoAi(true);
    pet.setWaiting(false);
    pet.setOnGround(true);
    h.getLevel().addFreshEntity(pet);
    return pet;
  }

  private static MiningSlime miner(GameTestHelper h, LoadedWorkArea a) {
    var pet = ModEntities.MINING_SLIME.get().create(h.getLevel());
    pet.setWorking(true);
    pet.tame(h.makeMockSurvivalPlayer());
    pet.moveTo(a.point(7.5, 101, 8.5));
    pet.setTool(0, new ItemStack(Items.IRON_PICKAXE));
    pet.onReleased();
    pet.setWorking(true);
    h.getLevel().addFreshEntity(pet);
    return pet;
  }

  private static BlockPos basin(GameTestHelper h, LoadedWorkArea a, BlockState fluid) {
    var pos = BlockPos.containing(a.point(9, 100, 8));
    h.getLevel().setBlockAndUpdate(pos.below(), Blocks.BEDROCK.defaultBlockState());
    h.getLevel().setBlockAndUpdate(pos, fluid);
    return pos;
  }

  private static int count(IItemHandler items, Item item) {
    int total = 0;
    for (int i = 0; i < items.getSlots(); i++)
      if (items.getStackInSlot(i).is(item)) total += items.getStackInSlot(i).getCount();
    return total;
  }

  private static ItemStack bucket(int count) {
    var bucket = new ItemStack(Items.BUCKET, count);
    bucket.getOrCreateTag().putString("OrderNine", "retained-empty-bucket");
    return bucket;
  }

  @GameTest(template = "empty", batch = "order_nine")
  public static void soggyRecipePreservesStoredTraitsAndRejectsExtraIngredients(GameTestHelper h) {
    var pet = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    pet.tame(h.makeMockSurvivalPlayer());
    pet.setHealth(7);
    pet.setTunedVitals(7, 43, 3);
    pet.setWaiting(true);
    var input = FriendlySlimeItem.fromSlime(pet);
    var recipe =
        new CompanionRecipe(
            ResourceLocation.fromNamespaceAndPath("slimic", "test"),
            CraftingBookCategory.MISC,
            CompanionRecipe.Kind.SOGGY);
    var grid =
        new TransientCraftingContainer(
            new AbstractContainerMenu(null, 0) {
              public boolean stillValid(Player p) {
                return true;
              }

              public ItemStack quickMoveStack(Player p, int i) {
                return ItemStack.EMPTY;
              }
            },
            2,
            2);
    grid.setItem(0, input);
    grid.setItem(3, new ItemStack(Items.SLIME_BALL));
    h.assertTrue(recipe.matches(grid, h.getLevel()), "Friendly and slime ball recipe");
    var result = recipe.assemble(grid, h.getLevel().registryAccess());
    h.assertTrue(
        result.is(ModItems.SOGGY_SLIME.get())
            && result
                .getTag()
                .getCompound("SlimeData")
                .equals(input.getTag().getCompound("SlimeData")),
        "All stored identity, health, order and stats retained");
    grid.setItem(2, new ItemStack(Items.DIRT));
    h.assertTrue(!recipe.matches(grid, h.getLevel()), "Extra ingredient rejected");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_nine", timeoutTicks = 80)
  public static void soggyLeavesThinTrailAndPaysStaminaForSuccessfulPlacement(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var pet = soggy(h, a);
    var pos = pet.blockPosition();
    pet.setTunedVitals(16, 50, 0);
    h.succeedWhen(
        () -> {
          h.assertTrue(
              h.getLevel().getBlockState(pos).is(ModBlocks.SLIME_TRAIL.get())
                  && pet.getActivityProgress() == 1,
              "Native tick creates one trail and work unit");
          h.assertTrue(pet.getStamina() == 49, "Successful placement costs one stamina");
          var trail = h.getLevel().getBlockState(pos);
          h.assertTrue(
              trail.getShape(h.getLevel(), pos).max(Direction.Axis.Y) == 1 / 16D
                  && trail.is(BlockTags.MINEABLE_WITH_SHOVEL),
              "Carpet height and shovel mining tag");
          var drops =
              Block.getDrops(trail, h.getLevel(), pos, null, pet, new ItemStack(Items.IRON_SHOVEL));
          h.assertTrue(
              drops.size() == 1
                  && drops.get(0).is(ModItems.SLIME_SLAG.get())
                  && drops.get(0).getCount() == 1,
              "Exactly one slag per trail");
          pet.discard();
          a.close();
        });
  }

  @GameTest(template = "empty", batch = "order_nine", timeoutTicks = 60)
  public static void emptyEnergyWaitAndOccupiedGroundDoNotProduceTrails(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var empty = soggy(h, a);
    empty.setTunedVitals(16, 0, 0);
    var waiting = soggy(h, a);
    waiting.moveTo(a.point(9.5, 101, 8.5));
    waiting.setWaiting(true);
    var occupied = soggy(h, a);
    occupied.moveTo(a.point(12.5, 101, 8.5));
    h.getLevel().setBlockAndUpdate(occupied.blockPosition(), Blocks.DANDELION.defaultBlockState());
    var emptyPos = empty.blockPosition();
    var waitPos = waiting.blockPosition();
    var flower = occupied.blockPosition();
    h.runAfterDelay(
        12,
        () -> {
          h.assertTrue(
              h.getLevel().getBlockState(emptyPos).isAir()
                  && h.getLevel().getBlockState(waitPos).isAir()
                  && h.getLevel().getBlockState(flower).is(Blocks.DANDELION),
              "No energy, Wait, or occupied ground prevents placement");
          h.assertTrue(
              empty.getActivityProgress() == 0
                  && waiting.getActivityProgress() == 0
                  && occupied.getActivityProgress() == 0,
              "No unsuccessful-placement growth");
          empty.discard();
          waiting.discard();
          occupied.discard();
          a.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_nine", timeoutTicks = 65)
  public static void canceledTrailPlacementRestoresGroundWithoutEnergyOrGrowthCost(
      GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var pet = soggy(h, a);
    var pos = pet.blockPosition();
    Consumer<BlockEvent.EntityPlaceEvent> cancel =
        e -> {
          if (e.getPos().equals(pos)) e.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(cancel);
    h.runAfterDelay(60, () -> MinecraftForge.EVENT_BUS.unregister(cancel));
    h.runAfterDelay(
        30,
        () -> {
          MinecraftForge.EVENT_BUS.unregister(cancel);
          h.assertTrue(
              h.getLevel().getBlockState(pos).isAir()
                  && pet.getStamina() == 100
                  && pet.getActivityProgress() == 0,
              "Canceled trail rolls back and costs nothing");
          pet.discard();
          a.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_nine", timeoutTicks = 65)
  public static void deniedMobGriefingPreventsTrailWorldEdits(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var pet = soggy(h, a);
    var pos = pet.blockPosition();
    Consumer<EntityMobGriefingEvent> deny =
        e -> {
          if (e.getEntity() == pet) e.setResult(Event.Result.DENY);
        };
    MinecraftForge.EVENT_BUS.addListener(deny);
    h.runAfterDelay(60, () -> MinecraftForge.EVENT_BUS.unregister(deny));
    h.runAfterDelay(
        25,
        () -> {
          MinecraftForge.EVENT_BUS.unregister(deny);
          h.assertTrue(
              h.getLevel().getBlockState(pos).isAir() && pet.getStamina() == 100,
              "Mob griefing denied");
          pet.discard();
          a.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_nine")
  public static void soggyRecoveryKeepsIdentityVitalsDyeAndTrailTimers(GameTestHelper h) {
    var pet = ModEntities.SOGGY_SLIME.get().create(h.getLevel());
    var owner = h.makeMockSurvivalPlayer();
    pet.tame(owner);
    pet.dye(owner, new ItemStack(Items.BLUE_DYE));
    pet.setTunedVitals(7, 41, 5);
    var data = pet.saveWithoutId(new CompoundTag());
    data.putInt("SoggyTrailCooldown", 7);
    data.putInt("SoggyRecoveryDelay", 12);
    pet.load(data);
    var item = FriendlySlimeItem.fromSlime(pet);
    var copy = ModEntities.SOGGY_SLIME.get().create(h.getLevel());
    copy.load(item.getTag().getCompound("SlimeData"));
    h.assertTrue(
        item.is(ModItems.SOGGY_SLIME.get())
            && copy.getUUID().equals(pet.getUUID())
            && copy.getOwnerUUID().equals(owner.getUUID())
            && copy.getHealth() == 7
            && copy.getStamina() == 41
            && copy.getStatPoints() == 5
            && copy.getDyeColorId() == DyeColor.BLUE.getId(),
        "Soggy recovery preserves complete common data");
    var saved = copy.saveWithoutId(new CompoundTag());
    h.assertTrue(
        saved.getInt("SoggyTrailCooldown") == 7 && saved.getInt("SoggyRecoveryDelay") == 12,
        "Trail timing survives storage");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_nine")
  public static void fourSlagCraftExactlyOneSlimeBall(GameTestHelper h) {
    var recipe =
        h.getLevel()
            .getRecipeManager()
            .byKey(ResourceLocation.fromNamespaceAndPath("slimic", "slime_ball_from_slag"))
            .orElseThrow();
    h.assertTrue(
        recipe.getResultItem(h.getLevel().registryAccess()).is(Items.SLIME_BALL)
            && recipe.getResultItem(h.getLevel().registryAccess()).getCount() == 1
            && recipe.getIngredients().size() == 4
            && recipe.getIngredients().stream()
                .allMatch(i -> i.test(new ItemStack(ModItems.SLIME_SLAG.get()))),
        "Four slag to one ball");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_nine", timeoutTicks = 90)
  public static void actualFallDeathConsumesOneNearbyGuardianTotem(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    owner.moveTo(a.point(7, 101, 8));
    var guard = ModEntities.GUARDIAN_ANGEL_SLIME.get().create(h.getLevel());
    guard.tame(owner);
    guard.moveTo(a.point(9, 103, 8));
    guard.setTotems(new ItemStack(Items.TOTEM_OF_UNDYING, 2));
    h.getLevel().addFreshEntity(guard);
    owner.setHealth(2);
    h.startSequence()
        .thenWaitUntil(() -> h.assertTrue(guard.tickCount > 0, "Guardian registered"))
        .thenExecute(
            () -> {
              owner.causeFallDamage(80, 1, h.getLevel().damageSources().fall());
              h.assertTrue(
                  owner.isAlive() && owner.getHealth() == 1 && guard.getTotems().getCount() == 1,
                  "Native fall damage rescued exactly once");
              guard.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_nine", timeoutTicks = 90)
  public static void lavaDrowningAndFireDeathsUseTheSameTotemProtection(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    owner.moveTo(a.point(7, 101, 8));
    var guard = ModEntities.GUARDIAN_ANGEL_SLIME.get().create(h.getLevel());
    guard.tame(owner);
    guard.moveTo(a.point(9, 103, 8));
    guard.setTotems(new ItemStack(Items.TOTEM_OF_UNDYING, 4));
    h.getLevel().addFreshEntity(guard);
    h.startSequence()
        .thenWaitUntil(() -> h.assertTrue(guard.tickCount > 0, "Guardian registered"))
        .thenExecute(
            () -> {
              int left = 4;
              for (var source :
                  List.of(
                      h.getLevel().damageSources().lava(),
                      h.getLevel().damageSources().drown(),
                      h.getLevel().damageSources().onFire())) {
                owner.removeAllEffects();
                owner.setAbsorptionAmount(0);
                owner.invulnerableTime = 0;
                owner.setHealth(2);
                owner.hurt(source, 100);
                h.assertTrue(
                    owner.isAlive()
                        && owner.getHealth() == 1
                        && guard.getTotems().getCount() == --left,
                    "Environmental damage rescued: " + source);
              }
              guard.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_nine", timeoutTicks = 100)
  public static void guardianDefendsItselfWhileWaitingWithoutALoadedOwner(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var guard = ModEntities.GUARDIAN_ANGEL_SLIME.get().create(h.getLevel());
    guard.tame(h.makeMockSurvivalPlayer());
    guard.moveTo(a.point(6, 103, 8));
    guard.setWaiting(true);
    h.getLevel().addFreshEntity(guard);
    var attacker = EntityType.IRON_GOLEM.create(h.getLevel());
    attacker.setNoAi(true);
    attacker.moveTo(a.point(9, 101, 8));
    h.getLevel().addFreshEntity(attacker);
    h.startSequence()
        .thenWaitUntil(() -> h.assertTrue(guard.tickCount > 0, "Guard active"))
        .thenExecute(
            () -> {
              h.assertTrue(guard.getOwner() == null, "Owner is offline in this fixture");
              guard.hurt(h.getLevel().damageSources().mobAttack(attacker), 2);
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    attacker.getHealth() < 100 && guard.getActivityProgress() >= 2,
                    "Actual self damage causes beam retaliation even during Wait"))
        .thenExecute(
            () -> {
              attacker.discard();
              guard.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_nine", timeoutTicks = 70)
  public static void guardianNeverRetaliatesAgainstItsOwner(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var guard = ModEntities.GUARDIAN_ANGEL_SLIME.get().create(h.getLevel());
    guard.tame(owner);
    guard.moveTo(a.point(6, 103, 8));
    h.getLevel().addFreshEntity(guard);
    guard.hurt(h.getLevel().damageSources().playerAttack(owner), 2);
    h.runAfterDelay(
        30,
        () -> {
          h.assertTrue(
              guard.getBeamTargetId() < 0 && guard.getStamina() == 100,
              "Owner excluded from self-defense");
          guard.discard();
          a.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_nine")
  public static void jumpingStepKeepsPropulsionBoundedForEveryGroundRole(GameTestHelper h)
      throws Exception {
    var operation = MoveControl.class.getDeclaredField("operation");
    operation.setAccessible(true);
    for (var type :
        List.of(
            ModEntities.FRIENDLY_SLIME.get(),
            ModEntities.MINING_SLIME.get(),
            ModEntities.TRANSPORT_SLIME.get(),
            ModEntities.HERALD_SLIME.get(),
            ModEntities.COMBAT_SLIME.get(),
            ModEntities.CLEANING_SLIME.get(),
            ModEntities.SADDLE_SLIME.get(),
            ModEntities.FARMER_SLIME.get(),
            ModEntities.LUMBERJACK_SLIME.get(),
            ModEntities.SOGGY_SLIME.get(),
            ModEntities.HEALING_SLIME.get(),
            ModEntities.WAREHOUSE_SLIME.get())) {
      var pet = type.create(h.getLevel());
      pet.tame(h.makeMockSurvivalPlayer());
      pet.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(10);
      pet.setOnGround(false);
      pet.setWaiting(false);
      var control = pet.getMoveControl();
      control.setWantedPosition(4, 2, 4, 1.2);
      operation.set(
          control,
          Arrays.stream(operation.getType().getEnumConstants())
              .filter(v -> v.toString().equals("JUMPING"))
              .findFirst()
              .orElseThrow());
      control.tick();
      h.assertTrue(
          pet.getSpeed() > 0 && pet.getSpeed() <= .45F,
          "Jumping-state propulsion remains bounded for " + type + ": " + pet.getSpeed());
    }
    h.succeed();
  }

  private static void missingBucket(GameTestHelper h, BlockState fluid) {
    var a = new LoadedWorkArea(h);
    var pet = miner(h, a);
    var pos = basin(h, a, fluid);
    h.succeedWhen(
        () -> {
          h.assertTrue(
              pet.getWorkStatus().requests(WorkStatus.Request.BUCKET_SUPPLY)
                  && pet.getWorkStatus().reason() == WorkStatus.Reason.BUCKET_MISSING,
              "Missing bucket pauses mining with supply request; status=" + pet.getMiningStatus());
          h.assertTrue(
              !h.getLevel().getFluidState(pos).isEmpty() && pet.getActivityProgress() == 0,
              "Unbucketed fluid remains intact and yields no growth");
          pet.discard();
          a.close();
        });
  }

  @GameTest(template = "empty", batch = "order_nine", timeoutTicks = 100)
  public static void waterWithoutBucketStopsMiningAndRequestsSupply(GameTestHelper h) {
    missingBucket(h, Blocks.WATER.defaultBlockState());
  }

  @GameTest(template = "empty", batch = "order_nine", timeoutTicks = 100)
  public static void lavaWithoutBucketStopsMiningAndRequestsSupply(GameTestHelper h) {
    missingBucket(h, Blocks.LAVA.defaultBlockState());
  }

  private static void removal(GameTestHelper h, BlockState fluid) {
    var a = new LoadedWorkArea(h);
    var pet = miner(h, a);
    var retained = bucket(3);
    pet.getBag().setStackInSlot(0, retained.copy());
    var pos = basin(h, a, fluid);
    h.succeedWhen(
        () -> {
          h.assertTrue(
              h.getLevel().getFluidState(pos).isEmpty() && pet.getActivityProgress() >= 1,
              "Fluid physically removed");
          h.assertTrue(
              ItemStack.matches(pet.getBag().getStackInSlot(0), retained)
                  && count(pet.getBag(), Items.WATER_BUCKET) == 0
                  && count(pet.getBag(), Items.LAVA_BUCKET) == 0,
              "Empty bucket count and custom data unchanged, no filled bucket produced");
          pet.discard();
          a.close();
        });
  }

  @GameTest(template = "empty", batch = "order_nine", timeoutTicks = 120)
  public static void waterRemovalNeverConsumesOrFillsTheBucket(GameTestHelper h) {
    removal(h, Blocks.WATER.defaultBlockState());
  }

  @GameTest(template = "empty", batch = "order_nine", timeoutTicks = 120)
  public static void lavaRemovalNeverConsumesOrFillsTheBucket(GameTestHelper h) {
    removal(h, Blocks.LAVA.defaultBlockState());
  }

  @GameTest(template = "empty", batch = "order_nine", timeoutTicks = 120)
  public static void waterloggedContainerKeepsItsBlockEntityAndInventory(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var pet = miner(h, a);
    pet.getBag().setStackInSlot(0, bucket(1));
    var pos =
        basin(
            h,
            a,
            Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
    ((ChestBlockEntity) h.getLevel().getBlockEntity(pos))
        .setItem(0, new ItemStack(Items.DIAMOND, 7));
    h.succeedWhen(
        () -> {
          h.assertTrue(
              h.getLevel().getBlockState(pos).is(Blocks.CHEST)
                  && !h.getLevel().getBlockState(pos).getValue(BlockStateProperties.WATERLOGGED)
                  && ((ChestBlockEntity) h.getLevel().getBlockEntity(pos)).getItem(0).getCount()
                      == 7,
              "Water removed without breaking or emptying the container");
          pet.discard();
          a.close();
        });
  }

  @GameTest(template = "empty", batch = "order_nine", timeoutTicks = 100)
  public static void canceledFluidBreakKeepsFluidBucketAndGrowth(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var pet = miner(h, a);
    pet.getBag().setStackInSlot(0, bucket(1));
    var pos = basin(h, a, Blocks.WATER.defaultBlockState());
    Consumer<BlockEvent.BreakEvent> cancel =
        e -> {
          if (e.getPos().equals(pos)) e.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(cancel);
    h.runAfterDelay(95, () -> MinecraftForge.EVENT_BUS.unregister(cancel));
    h.runAfterDelay(
        50,
        () -> {
          MinecraftForge.EVENT_BUS.unregister(cancel);
          h.assertTrue(
              !h.getLevel().getFluidState(pos).isEmpty()
                  && pet.hasBucket()
                  && pet.getActivityProgress() == 0,
              "Break protection prevents drainage and costs no resources");
          pet.discard();
          a.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_nine", timeoutTicks = 100)
  public static void canceledFluidReplacementRollsBackItsSnapshot(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var pet = miner(h, a);
    pet.getBag().setStackInSlot(0, bucket(1));
    var pos = basin(h, a, Blocks.LAVA.defaultBlockState());
    Consumer<BlockEvent.EntityPlaceEvent> cancel =
        e -> {
          if (e.getPos().equals(pos)) e.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(cancel);
    h.runAfterDelay(95, () -> MinecraftForge.EVENT_BUS.unregister(cancel));
    h.runAfterDelay(
        50,
        () -> {
          MinecraftForge.EVENT_BUS.unregister(cancel);
          h.assertTrue(
              h.getLevel().getBlockState(pos).is(Blocks.LAVA)
                  && pet.hasBucket()
                  && pet.getActivityProgress() == 0,
              "Placement protection restores fluid and preserves bucket");
          pet.discard();
          a.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_nine", timeoutTicks = 360)
  public static void transportDeliversOneExactBucketAndMiningResumes(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var pet = miner(h, a);
    var pos = basin(h, a, Blocks.WATER.defaultBlockState());
    var chest = a.chest(4, 101, 6);
    var stock = bucket(2);
    chest.setItem(0, stock.copy());
    var carrier = a.carrier(new Vec3(3.5, 101, 8.5));
    carrier.setOwnerUUID(pet.getOwnerUUID());
    h.succeedWhen(
        () -> {
          h.assertTrue(
              pet.hasBucket() && h.getLevel().getFluidState(pos).isEmpty() && !carrier.hasCargo(),
              "Bucket delivered and native mining resumes; "
                  + pet.getMiningStatus()
                  + " carrier="
                  + carrier.getTransportStatus());
          h.assertTrue(
              count(pet.getBag(), Items.BUCKET) == 1
                  && count(chest.inventory(), Items.BUCKET) == 1
                  && pet.getBag().getStackInSlot(0).getTag().equals(stock.getTag()),
              "One original bucket transferred without consuming or filling it");
          pet.discard();
          carrier.discard();
          a.close();
        });
  }

  @GameTest(template = "empty", batch = "order_nine", timeoutTicks = 500)
  public static void transportUnloadsAFullBagBeforeSupplyingItsMissingBucket(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var pet = miner(h, a);
    for (int i = 0; i < pet.getCapacity(); i++)
      pet.getBag().setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
    var pos = basin(h, a, Blocks.LAVA.defaultBlockState());
    var chest = a.chest(4, 101, 6);
    chest.setItem(0, bucket(1));
    var carrier = a.carrier(new Vec3(3.5, 101, 8.5));
    carrier.setOwnerUUID(pet.getOwnerUUID());
    h.succeedWhen(
        () -> {
          h.assertTrue(
              pet.hasBucket() && h.getLevel().getFluidState(pos).isEmpty() && !carrier.hasCargo(),
              "Full worker bag unloaded before bucket supply");
          h.assertTrue(
              count(pet.getBag(), Items.COBBLESTONE)
                          + count(carrier.getBag(), Items.COBBLESTONE)
                          + count(chest.inventory(), Items.COBBLESTONE)
                      == 9 * 64
                  && count(chest.inventory(), Items.BUCKET) == 0,
              "All cargo conserved and exactly one bucket supplied");
          pet.discard();
          carrier.discard();
          a.close();
        });
  }
}
