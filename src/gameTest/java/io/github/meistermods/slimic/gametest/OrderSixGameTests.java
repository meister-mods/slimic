package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.BrokenSlimeBlockEntity;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OrderSixGameTests {
  private OrderSixGameTests() {}

  private static List<EntityType<? extends FriendlySlime>> types() {
    return List.of(
        ModEntities.FRIENDLY_SLIME.get(),
        ModEntities.MINING_SLIME.get(),
        ModEntities.TRANSPORT_SLIME.get(),
        ModEntities.HERALD_SLIME.get(),
        ModEntities.COMBAT_SLIME.get(),
        ModEntities.CLEANING_SLIME.get(),
        ModEntities.SADDLE_SLIME.get(),
        ModEntities.FARMER_SLIME.get(),
        ModEntities.LUMBERJACK_SLIME.get(),
        ModEntities.GUARDIAN_ANGEL_SLIME.get());
  }

  @GameTest(template = "empty", batch = "order_six", timeoutTicks = 80)
  public static void allTenDeathsPreserveAndReviveExactCompanions(GameTestHelper h) {
    LoadedWorkArea area = new LoadedWorkArea(h);
    Player owner = h.makeMockSurvivalPlayer();
    owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SLIME_BALL, 20));
    List<FriendlySlime> pets = new ArrayList<>();
    List<BlockPos> positions = new ArrayList<>();
    for (int i = 0; i < types().size(); i++) {
      FriendlySlime pet = types().get(i).create(h.getLevel());
      pet.moveTo(area.point(3 + (i % 4) * 3, 101, 3 + (i / 4) * 4));
      pet.tame(owner);
      pet.setNoAi(true);
      pet.setWaiting(true);
      pet.setCustomName(Component.literal("Memory" + i));
      pet.setTunedVitals(6, 39, 7);
      pet.upgradeStat(SlimeStat.HEALTH);
      if (pet instanceof BagSlime bag)
        bag.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 13));
      if (pet instanceof FieldSlime field) {
        var tool =
            new ItemStack(field instanceof FarmerSlime ? Items.DIAMOND_HOE : Items.DIAMOND_AXE);
        tool.setDamageValue(12);
        field.setTool(tool);
      }
      if (pet instanceof CombatSlime fighter)
        fighter.setEquipment(0, new ItemStack(Items.DIAMOND_SWORD));
      if (pet instanceof SaddleSlime mount) {
        mount.setEquipment(0, new ItemStack(Items.SADDLE));
        mount.setEquipment(1, new ItemStack(Items.ELYTRA));
      }
      h.getLevel().addFreshEntity(pet);
      pets.add(pet);
      positions.add(pet.blockPosition());
      pet.hurt(h.getLevel().damageSources().genericKill(), 10000);
      h.assertTrue(
          pet.isRemoved()
              && h.getLevel().getBlockState(positions.get(i)).is(ModBlocks.BROKEN_SLIME.get()),
          "Every specialization must leave a recovery block at its death position");
    }
    h.runAfterDelay(
        3,
        () -> {
          for (int i = 0; i < pets.size(); i++) {
            FriendlySlime old = pets.get(i);
            BlockPos pos = positions.get(i);
            var block = h.getLevel().getBlockState(pos);
            h.assertTrue(
                block
                    .use(
                        h.getLevel(),
                        owner,
                        InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false))
                    .consumesAction(),
                "The owner can revive with one slimeball: " + old.getType());
            FriendlySlime revived = (FriendlySlime) h.getLevel().getEntity(old.getUUID());
            h.assertTrue(
                revived != null
                    && revived.getType() == old.getType()
                    && revived.getOwnerUUID().equals(owner.getUUID())
                    && revived.getHealth() == revived.getMaxHealth()
                    && revived.getStamina() == 39
                    && revived.getStatPoints() == 6
                    && revived.getStatLevel(SlimeStat.HEALTH) == 1
                    && revived.getCustomName().equals(old.getCustomName())
                    && revived.isOrderedToSit(),
                "Identity, specialization, owner, name, stats, points, stamina and order survive"
                    + " revival");
            if (revived instanceof BagSlime bag)
              h.assertTrue(
                  bag.getBag().getStackInSlot(0).getCount() == 13,
                  "Cargo stays inside the revived companion");
            if (revived instanceof FieldSlime field)
              h.assertTrue(
                  field.getTool().getDamageValue() == 12 && field.matchesTool(field.getTool()),
                  "Field tool and wear survive revival");
            if (revived instanceof CombatSlime fighter)
              h.assertTrue(fighter.getEquipment(0).is(Items.DIAMOND_SWORD), "Weapon is retained");
            if (revived instanceof SaddleSlime mount)
              h.assertTrue(
                  mount.isSaddled() && mount.hasWings() && !mount.isVehicle(),
                  "Mount gear survives without a passenger");
            revived.discard();
          }
          h.assertTrue(
              owner.getMainHandItem().getCount() == 20 - types().size(),
              "Each revival consumes exactly one ball");
          h.assertTrue(
              h.getLevel()
                  .getEntitiesOfClass(ItemEntity.class, new AABB(positions.get(0)).inflate(16))
                  .isEmpty(),
              "Death cannot duplicate retained equipment or cargo as loot");
          area.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_six")
  public static void recoveryLootRetainsPayloadAndForeignPlayersCannotRevive(GameTestHelper h) {
    LoadedWorkArea area = new LoadedWorkArea(h);
    Player owner = h.makeMockSurvivalPlayer();
    var pet = ModEntities.MINING_SLIME.get().create(h.getLevel());
    pet.tame(owner);
    pet.moveTo(area.point(8, 101, 8));
    pet.setNoAi(true);
    pet.getBag().setStackInSlot(0, new ItemStack(Items.EMERALD, 17));
    h.getLevel().addFreshEntity(pet);
    BlockPos pos = pet.blockPosition();
    pet.hurt(h.getLevel().damageSources().genericKill(), 10000);
    var be = (BrokenSlimeBlockEntity) h.getLevel().getBlockEntity(pos);
    var state = h.getLevel().getBlockState(pos);
    Player other = h.makeMockSurvivalPlayer();
    other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SLIME_BALL, 2));
    h.assertTrue(
        !state
                .use(
                    h.getLevel(),
                    other,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false))
                .consumesAction()
            && other.getMainHandItem().getCount() == 2,
        "Other players cannot consume or revive an owned payload");
    List<ItemStack> drops = Block.getDrops(state, h.getLevel(), pos, be);
    h.assertTrue(
        drops.size() == 1 && drops.get(0).is(ModItems.BROKEN_SLIME.get()),
        "Breaking yields one recovery block");
    CompoundTag data = BlockItem.getBlockEntityData(drops.get(0));
    ItemStack stored = ItemStack.of(data.getCompound("Slime"));
    h.assertTrue(
        stored.getTag().getCompound("SlimeData").getUUID("UUID").equals(pet.getUUID())
            && stored
                    .getTag()
                    .getCompound("SlimeData")
                    .getCompound("MiningBag")
                    .getList("Items", 10)
                    .size()
                == 1,
        "Native loot functions preserve the companion identity and inventory");
    h.getLevel().removeBlock(pos, false);
    BlockPos floor = pos.east(4).below();
    owner.setItemInHand(InteractionHand.MAIN_HAND, drops.get(0));
    h.assertTrue(
        drops
            .get(0)
            .useOn(
                new net.minecraft.world.item.context.UseOnContext(
                    owner,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(
                        Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false)))
            .consumesAction(),
        "The native block item can be placed again");
    var placed = (BrokenSlimeBlockEntity) h.getLevel().getBlockEntity(floor.above());
    h.assertTrue(
        placed != null
            && placed
                .getSlime()
                .getTag()
                .getCompound("SlimeData")
                .getUUID("UUID")
                .equals(pet.getUUID()),
        "Native placement loads the retained companion payload into its new block entity");
    h.getLevel().removeBlock(floor.above(), false);
    area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_six", timeoutTicks = 80)
  public static void occupiedRevivalKeepsBothBlockAndBall(GameTestHelper h) {
    LoadedWorkArea area = new LoadedWorkArea(h);
    Player owner = h.makeMockSurvivalPlayer();
    var pet = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    pet.tame(owner);
    pet.moveTo(area.point(8, 101, 8));
    pet.setNoAi(true);
    h.getLevel().addFreshEntity(pet);
    BlockPos pos = pet.blockPosition();
    pet.hurt(h.getLevel().damageSources().genericKill(), 10000);
    h.getLevel()
        .setBlockAndUpdate(
            pos.above(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
    owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SLIME_BALL, 2));
    h.runAfterDelay(
        3,
        () -> {
          var state = h.getLevel().getBlockState(pos);
          h.assertTrue(
              !state
                      .use(
                          h.getLevel(),
                          owner,
                          InteractionHand.MAIN_HAND,
                          new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false))
                      .consumesAction()
                  && owner.getMainHandItem().getCount() == 2
                  && h.getLevel().getBlockEntity(pos) instanceof BrokenSlimeBlockEntity,
              "A colliding revival rolls back without losing its block, data or slimeball");
          h.getLevel().removeBlock(pos.above(), false);
          h.getLevel().removeBlock(pos, false);
          area.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_six", timeoutTicks = 120)
  public static void hostileMobsAcquireEverySpecializationButCreepersDoNot(GameTestHelper h) {
    List<LoadedWorkArea> areas = new ArrayList<>();
    List<Mob> enemies = new ArrayList<>();
    List<FriendlySlime> pets = new ArrayList<>();
    for (int i = 0; i < types().size(); i++) {
      LoadedWorkArea a = new LoadedWorkArea(h);
      areas.add(a);
      var pet = types().get(i).create(h.getLevel());
      pet.moveTo(a.point(8, 101, 8));
      pet.setNoAi(true);
      h.getLevel().addFreshEntity(pet);
      pets.add(pet);
      Mob enemy =
          (i % 3 == 0 ? EntityType.ZOMBIE : i % 3 == 1 ? EntityType.SKELETON : EntityType.SPIDER)
              .create(h.getLevel());
      enemy.moveTo(a.point(8, 101, 12));
      enemy.setPersistenceRequired();
      h.getLevel().addFreshEntity(enemy);
      enemies.add(enemy);
    }
    LoadedWorkArea ca = new LoadedWorkArea(h);
    areas.add(ca);
    var quiet = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    quiet.moveTo(ca.point(8, 101, 8));
    quiet.setNoAi(true);
    h.getLevel().addFreshEntity(quiet);
    var creeper = EntityType.CREEPER.create(h.getLevel());
    creeper.moveTo(ca.point(8, 101, 10));
    h.getLevel().addFreshEntity(creeper);
    h.succeedWhen(
        () -> {
          for (int i = 0; i < enemies.size(); i++)
            h.assertTrue(
                enemies.get(i).getTarget() == pets.get(i),
                "Zombie, skeleton and spider goals must recognize every companion specialization");
          h.assertTrue(
              creeper.getTarget() == null && creeper.getSwellDir() < 1,
              "Creepers receive no companion targeting or swelling");
          enemies.forEach(Mob::discard);
          pets.forEach(FriendlySlime::discard);
          quiet.discard();
          creeper.discard();
          areas.forEach(LoadedWorkArea::close);
        });
  }

  @GameTest(template = "empty", batch = "order_six", timeoutTicks = 200)
  public static void aWaitingWorkerEscapesRealDamageWithoutLosingItsOrder(GameTestHelper h) {
    LoadedWorkArea a = new LoadedWorkArea(h);
    // Keep every random escape candidate on a loaded, level surface.
    for (int x = -8; x < 24; x++)
      for (int z = -8; z < 24; z++) {
        BlockPos floor = BlockPos.containing(a.point(x, 100, z));
        h.getLevel()
            .setBlock(floor, net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState(), 2);
        for (int y = 1; y <= 7; y++)
          h.getLevel()
              .setBlock(
                  floor.above(y),
                  net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                  2);
      }
    var pet = ModEntities.MINING_SLIME.get().create(h.getLevel());
    pet.getRandom().setSeed(1);
    pet.moveTo(a.point(8, 101, 8));
    pet.tame(h.makeMockSurvivalPlayer());
    pet.setWaiting(true);
    h.getLevel().addFreshEntity(pet);
    var threat = EntityType.HUSK.create(h.getLevel());
    threat.setPersistenceRequired();
    threat.moveTo(a.point(8, 101, 10));
    threat.setNoAi(true);
    h.getLevel().addFreshEntity(threat);
    double initial = pet.distanceToSqr(threat);
    pet.hurt(h.getLevel().damageSources().mobAttack(threat), 2);
    h.assertTrue(
        pet.getActivityProgress() == 1 && pet.isFleeing(),
        "A real attack creates one defense activity and an escape response");
    h.succeedWhen(
        () -> {
          h.assertTrue(
              pet.distanceToSqr(threat) > initial + 9 && pet.isOrderedToSit(),
              "Escape movement overrides waiting while preserving the saved wait order; pet="
                  + pet.position()
                  + " threatAlive="
                  + threat.isAlive()
                  + " fleeing="
                  + pet.isFleeing()
                  + " ticks="
                  + pet.tickCount);
          pet.discard();
          threat.discard();
          a.close();
        });
  }

  @GameTest(template = "empty", batch = "order_six", timeoutTicks = 160)
  public static void anAiFighterRaisesItsShieldBetweenFastAttacks(GameTestHelper h) {
    LoadedWorkArea a = new LoadedWorkArea(h);
    var pet = ModEntities.COMBAT_SLIME.get().create(h.getLevel());
    pet.tame(h.makeMockSurvivalPlayer());
    pet.moveTo(a.point(8, 101, 8));
    pet.setEquipment(0, new ItemStack(Items.IRON_SWORD));
    pet.setEquipment(1, new ItemStack(Items.SHIELD));
    SlimeStat.WORK_SPEED.tune(pet, 10);
    h.getLevel().addFreshEntity(pet);
    var threat = EntityType.HUSK.create(h.getLevel());
    threat.setPersistenceRequired();
    threat.moveTo(a.point(8, 101, 10));
    threat.setNoAi(true);
    threat.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
    threat.setHealth(100);
    h.getLevel().addFreshEntity(threat);
    h.succeedWhen(
        () -> {
          h.assertTrue(
              pet.isBlocking(),
              "The AI must hold its shield through the native five-tick activation delay even with"
                  + " fast work speed");
          float health = pet.getHealth();
          int progress = pet.getActivityProgress();
          pet.hurt(h.getLevel().damageSources().mobAttack(threat), 8);
          h.assertTrue(
              pet.getHealth() == health
                  && pet.getOffhandItem().getDamageValue() > 0
                  && pet.getActivityProgress() == progress + 1,
              "An automatically raised shield blocks a frontal hit, wears and awards actual defense"
                  + " activity");
          pet.discard();
          threat.discard();
          a.close();
        });
  }

  @GameTest(template = "empty", batch = "order_six", timeoutTicks = 100)
  public static void flightBoostConsumesEnergyThenBeginsAControlledDescent(GameTestHelper h) {
    LoadedWorkArea a = new LoadedWorkArea(h);
    Player owner = h.makeMockSurvivalPlayer();
    var pet = ModEntities.SADDLE_SLIME.get().create(h.getLevel());
    pet.moveTo(a.point(8, 103, 8));
    pet.tame(owner);
    pet.setNoAi(true);
    pet.setEquipment(0, new ItemStack(Items.SADDLE));
    pet.setEquipment(1, new ItemStack(Items.ELYTRA));
    h.getLevel().addFreshEntity(pet);
    owner.moveTo(pet.position());
    pet.mount(owner);
    pet.setTunedVitals(10, 1, 0);
    h.onEachTick(() -> pet.setRiderInput(owner, false, true));
    h.runAfterDelay(
        10,
        () ->
            h.assertTrue(
                !pet.isBoosting() && pet.getStamina() == 0 && pet.isGlidingDown(),
                "Boost uses server-owned stamina and exhaustion enters controlled descent"));
    h.runAfterDelay(
        50,
        () -> {
          h.assertTrue(
              !pet.isBoosting() && pet.isGlidingDown() && pet.getStamina() == 0,
              "Holding Ctrl cannot bypass exhaustion or recharge in the air");
          owner.stopRiding();
          pet.discard();
          a.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_six", timeoutTicks = 480)
  public static void heraldRepeatsEachIncidentEveryTwoHundredTicksUntilResolved(GameTestHelper h) {
    LoadedWorkArea a = new LoadedWorkArea(h);
    Player owner = h.makeMockSurvivalPlayer();
    var worker = ModEntities.MINING_SLIME.get().create(h.getLevel());
    worker.moveTo(a.point(8, 101, 8));
    worker.tame(owner);
    worker.setNoAi(true);
    worker.onReleased();
    worker.setWorking(true);
    CompoundTag data = worker.saveWithoutId(new CompoundTag());
    data.putBoolean("MiningWorking", true);
    data.putInt("MiningStatus", MiningSlime.Status.NO_TOOL.ordinal());
    data.putInt("BlockedWorkTicks", 1200);
    data.putLong("BlockedEpisode", 5);
    worker.load(data);
    h.getLevel().addFreshEntity(worker);
    var herald = ModEntities.HERALD_SLIME.get().create(h.getLevel());
    herald.moveTo(a.point(10, 101, 8));
    herald.tame(owner);
    herald.setDelayMinutes(1);
    h.getLevel().addFreshEntity(herald);
    Set<UUID> seen = new HashSet<>();
    List<Integer> fired = new ArrayList<>();
    int[] elapsed = {0};
    h.onEachTick(
        () -> {
          elapsed[0]++;
          for (var rocket :
              h.getLevel()
                  .getEntitiesOfClass(
                      net.minecraft.world.entity.projectile.FireworkRocketEntity.class,
                      herald.getBoundingBox().inflate(64)))
            if (rocket.getOwner() == herald && seen.add(rocket.getUUID())) fired.add(elapsed[0]);
        });
    h.runAfterDelay(
        235,
        () -> {
          h.assertTrue(
              fired.size() == 2 && fired.get(1) - fired.get(0) == 200,
              "An unresolved incident repeats ten seconds after its first signal: " + fired);
          worker.setWaiting(true);
        });
    h.runAfterDelay(
        450,
        () -> {
          h.assertTrue(fired.size() == 2, "Resolving or pausing the work stops repeated signals");
          worker.discard();
          herald.discard();
          a.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_six")
  public static void creativeBreakingPreservesOneRecoveryItem(GameTestHelper h) {
    LoadedWorkArea a = new LoadedWorkArea(h);
    Player owner = h.makeMockSurvivalPlayer();
    var pet = ModEntities.TRANSPORT_SLIME.get().create(h.getLevel());
    pet.tame(owner);
    pet.moveTo(a.point(8, 101, 8));
    pet.setNoAi(true);
    pet.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
    h.getLevel().addFreshEntity(pet);
    BlockPos pos = pet.blockPosition();
    pet.hurt(h.getLevel().damageSources().genericKill(), 10000);
    owner.getAbilities().instabuild = true;
    var state = h.getLevel().getBlockState(pos);
    h.assertTrue(
        state.is(ModBlocks.BROKEN_SLIME.get())
            && h.getLevel().getBlockEntity(pos) instanceof BrokenSlimeBlockEntity broken
            && !broken.getSlime().isEmpty(),
        "A creative recovery block must contain its payload before destruction");
    state.getBlock().playerWillDestroy(h.getLevel(), pos, state, owner);
    h.getLevel().removeBlock(pos, false);
    h.runAfterDelay(
        2,
        () -> {
          var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3));
          h.assertTrue(
              drops.size() == 1
                  && drops.get(0).getItem().is(ModItems.BROKEN_SLIME.get())
                  && ItemStack.of(
                          BlockItem.getBlockEntityData(drops.get(0).getItem()).getCompound("Slime"))
                      .getTag()
                      .getCompound("SlimeData")
                      .getUUID("UUID")
                      .equals(pet.getUUID()),
              "Creative destruction must preserve exactly one stored recovery block; drops="
                  + drops.stream().map(e -> e.getItem().toString()).toList()
                  + " rules="
                  + h.getLevel()
                      .getGameRules()
                      .getBoolean(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS));
          drops.forEach(ItemEntity::discard);
          a.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_six", timeoutTicks = 80)
  public static void groundSprintHasItsOwnSubstantialServerEnergyCost(GameTestHelper h) {
    LoadedWorkArea a = new LoadedWorkArea(h);
    Player owner = h.makeMockSurvivalPlayer();
    var pet = ModEntities.SADDLE_SLIME.get().create(h.getLevel());
    pet.moveTo(a.point(8, 101, 8));
    pet.tame(owner);
    pet.setNoAi(true);
    pet.setEquipment(0, new ItemStack(Items.SADDLE));
    h.getLevel().addFreshEntity(pet);
    owner.moveTo(pet.position());
    pet.mount(owner);
    pet.setTunedVitals(10, 50, 0);
    h.onEachTick(() -> pet.setRiderInput(owner, false, true));
    h.runAfterDelay(
        20,
        () -> {
          h.assertTrue(
              pet.isBoosting()
                  && !pet.hasWings()
                  && pet.getStamina() >= 39
                  && pet.getStamina() <= 41,
              "Ctrl sprint works without wings and uses about ten stamina per second: "
                  + pet.getStamina());
          owner.stopRiding();
          pet.discard();
          a.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_six", timeoutTicks = 80)
  public static void ordinaryFlightConsumesLessEnergyThanBoostedFlight(GameTestHelper h) {
    LoadedWorkArea a = new LoadedWorkArea(h);
    Player owner = h.makeMockSurvivalPlayer();
    var pet = ModEntities.SADDLE_SLIME.get().create(h.getLevel());
    pet.moveTo(a.point(8, 103, 8));
    pet.tame(owner);
    pet.setNoAi(true);
    pet.setEquipment(0, new ItemStack(Items.SADDLE));
    pet.setEquipment(1, new ItemStack(Items.ELYTRA));
    h.getLevel().addFreshEntity(pet);
    owner.moveTo(pet.position());
    pet.mount(owner);
    pet.setTunedVitals(10, 50, 0);
    boolean[] boosting = {false};
    h.onEachTick(() -> pet.setRiderInput(owner, false, boosting[0]));
    h.runAfterDelay(
        20,
        () -> {
          h.assertTrue(
              !pet.isBoosting() && pet.getStamina() > 47.5F && pet.getStamina() < 48.5F,
              "Ordinary airborne flight consumes two stamina per second without recovering in the"
                  + " air: "
                  + pet.getStamina());
          boosting[0] = true;
        });
    h.runAfterDelay(
        40,
        () -> {
          h.assertTrue(
              pet.isBoosting() && pet.getStamina() > 32 && pet.getStamina() < 34,
              "Boosted flight consumes fifteen stamina per second: " + pet.getStamina());
          owner.stopRiding();
          pet.discard();
          a.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_six", timeoutTicks = 80)
  public static void canceledAttacksCannotGenerateShieldActivity(GameTestHelper h) {
    LoadedWorkArea a = new LoadedWorkArea(h);
    var pet = ModEntities.COMBAT_SLIME.get().create(h.getLevel());
    pet.tame(h.makeMockSurvivalPlayer());
    pet.moveTo(a.point(8, 101, 8));
    pet.setNoAi(true);
    pet.setEquipment(1, new ItemStack(Items.SHIELD));
    pet.startUsingItem(InteractionHand.OFF_HAND);
    h.getLevel().addFreshEntity(pet);
    var attacker = EntityType.HUSK.create(h.getLevel());
    attacker.moveTo(a.point(8, 101, 10));
    h.runAfterDelay(
        8,
        () -> {
          java.util.function.Consumer<net.minecraftforge.event.entity.living.LivingAttackEvent>
              cancel =
                  event -> {
                    if (event.getEntity() == pet) event.setCanceled(true);
                  };
          net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(cancel);
          try {
            pet.hurt(h.getLevel().damageSources().mobAttack(attacker), 8);
            h.assertTrue(
                pet.getActivityProgress() == 0 && pet.getEquipment(1).getDamageValue() == 0,
                "A canceled incoming attack must grant neither shield wear nor defense activity");
          } finally {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(cancel);
            pet.discard();
            a.close();
          }
          h.succeed();
        });
  }

  private static final class HopProbe extends FriendlySlime {
    HopProbe(net.minecraft.world.level.Level level) {
      super(ModEntities.FRIENDLY_SLIME.get(), level);
    }

    @Override
    protected void registerGoals() {}
  }

  @GameTest(template = "empty", batch = "order_six", timeoutTicks = 200)
  public static void movementStatChangesHopFrequencyWithoutLengtheningTheFirstHop(
      GameTestHelper h) {
    LoadedWorkArea a = new LoadedWorkArea(h);
    HopProbe[] pets = {new HopProbe(h.getLevel()), new HopProbe(h.getLevel())};
    double[] start = {0, 0}, distance = {0, 0};
    int[] hops = {0, 0};
    boolean[] airborne = {false, false};
    for (int i = 0; i < 2; i++) {
      pets[i].moveTo(a.point(3, 101, 4 + i * 6));
      pets[i].setOnGround(true);
      SlimeStat.MOVEMENT_SPEED.tune(pets[i], i == 0 ? .15 : .6);
      h.getLevel().addFreshEntity(pets[i]);
    }
    h.onEachTick(
        () -> {
          for (int i = 0; i < 2; i++) {
            var p = pets[i];
            p.getMoveControl().setWantedPosition(p.getX() + 10, 101, p.getZ(), 1);
            if (!p.onGround() && !airborne[i]) {
              airborne[i] = true;
              start[i] = p.getX();
              hops[i]++;
            }
            if (p.onGround() && airborne[i]) {
              if (distance[i] == 0) distance[i] = p.getX() - start[i];
              airborne[i] = false;
            }
          }
        });
    h.runAfterDelay(
        70,
        () -> {
          h.assertTrue(
              distance[0] > .1 && Math.abs(distance[0] - distance[1]) < .1 && hops[1] > hops[0],
              "Speed must shorten the inter-hop delay without increasing launch distance: "
                  + Arrays.toString(distance)
                  + " hops="
                  + Arrays.toString(hops));
          for (var p : pets) p.discard();
          a.close();
          h.succeed();
        });
  }
}
