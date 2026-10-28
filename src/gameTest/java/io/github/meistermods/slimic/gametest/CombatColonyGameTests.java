package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.SlimeArmorItem;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.*;
import io.github.meistermods.slimic.world.ColonyBondingData;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatColonyGameTests {
  private record Fixture(LoadedWorkArea area, FakePlayer owner, BoundCombatSlime root) {}

  private static Fixture fixture(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var level = h.getLevel();
    var owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "LinkedCombat"));
    var root = ModEntities.BOUND_COMBAT_SLIME.get().create(level);
    root.tame(owner);
    ColonyTestFixtures.appearance(root);
    root.setNoAi(true);
    root.moveTo(area.point(4, 101, 8));
    owner.moveTo(root.position().add(0, 0, -2));
    var binding = new ColonyBondingData.Binding();
    binding.id = UUID.randomUUID();
    binding.owner = owner.getUUID();
    binding.representative = root.getUUID();
    binding.first = root.members().records().get(0).getUUID();
    binding.second = root.members().records().get(1).getUUID();
    var ledger = ColonyBondingData.get(level);
    h.assertTrue(ledger.commit(level, binding, root), "A real two-member authority is committed");
    ledger.admitting(root.getUUID(), true);
    try {
      h.assertTrue(
          level.addFreshEntity(root) && ledger.released(level, root), "Native colony admission");
    } finally {
      ledger.admitting(root.getUUID(), false);
    }
    root.onReleased();
    root.setWaiting(true);
    return new Fixture(area, owner, root);
  }

  private static CombatColonyBody body(GameTestHelper h, Fixture f) {
    var body = f.root.getLinkedBody();
    h.assertTrue(body != null && body.getColony() == f.root, "One linked combat body is admitted");
    body.setNoAi(true);
    return body;
  }

  private static net.minecraft.world.entity.monster.Husk target(GameTestHelper h, Vec3 at) {
    var target = EntityType.HUSK.create(h.getLevel());
    target.setNoAi(true);
    target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
    target.setHealth(200);
    target.moveTo(at);
    h.assertTrue(h.getLevel().addFreshEntity(target), "Native victim is added");
    return target;
  }

  private static void invoke(BoundCombatSlime root, String method, LivingEntity target) {
    try {
      var call = BoundCombatSlime.class.getDeclaredMethod(method, LivingEntity.class);
      call.setAccessible(true);
      call.invoke(root, target);
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static void normalHit(BoundCombatSlime root, CombatColonyBody body, LivingEntity target) {
    try {
      var call =
          BoundCombatSlime.class.getDeclaredMethod(
              "strikeFromBody", CombatColonyBody.class, LivingEntity.class, boolean.class);
      call.setAccessible(true);
      call.invoke(root, body, target, false);
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException(failure);
    }
  }

  @GameTest(template = "empty", batch = "combat_colony", timeoutTicks = 500)
  public static void sixEquipmentSlotsUseBothOriginalRecordsAndSurviveSaving(GameTestHelper h) {
    var f = fixture(h);
    h.startSequence()
        .thenWaitUntil(() -> body(h, f))
        .thenExecute(
            () -> {
              try {
                var body = body(h, f);
                f.owner.setItemInHand(
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(ModItems.SLIME_TUNER.get()));
                var menu = new SlimeMenu(1, f.owner.getInventory(), f.root);
                h.assertTrue(
                    menu.slots.get(3).x - menu.slots.get(2).x == 36
                        && menu.slots.get(0).y == menu.slots.get(5).y,
                    "Two labelled equipment groups have a one-slot gap and share one row");
                for (int index = 0; index < 6; index++) {
                  h.assertTrue(
                      !menu.slots.get(index).getItem().isEmpty(),
                      "All six real equipment slots are visible");
                  h.assertTrue(
                      menu.slots
                          .get(index)
                          .mayPlace(
                              new ItemStack(
                                  index % 3 == 0
                                      ? Items.DIAMOND_SWORD
                                      : index % 3 == 1
                                          ? Items.SHIELD
                                          : ModItems.SLIME_ARMOR.get())),
                      "Each group accepts sword/shield/slime armor by slot");
                  h.assertTrue(
                      !menu.slots.get(index).mayPlace(new ItemStack(Items.COBBLESTONE)),
                      "Cargo cannot enter equipment slots");
                }
                menu.slots.get(3).set(new ItemStack(Items.DIAMOND_SWORD));
                menu.slots.get(4).set(ItemStack.EMPTY);
                var armor = SlimeArmorItem.fromChestplate(new ItemStack(Items.DIAMOND_CHESTPLATE));
                menu.slots.get(5).set(armor);
                h.assertTrue(
                    f.root.getEquipment(0).is(Items.IRON_SWORD)
                        && body.getMainHandItem().is(Items.DIAMOND_SWORD)
                        && body.getOffhandItem().isEmpty(),
                    "Editing member two leaves member one's real equipment intact");
                var copy = ModEntities.BOUND_COMBAT_SLIME.get().create(h.getLevel());
                copy.load(f.root.saveWithoutId(new CompoundTag()));
                h.assertTrue(
                    copy.getEquipment(0).is(Items.IRON_SWORD)
                        && copy.getEquipment(3).is(Items.DIAMOND_SWORD)
                        && copy.getEquipment(4).isEmpty()
                        && ItemStack.matches(copy.getEquipment(5), armor)
                        && copy.getLinkedBody() == null,
                    "Saving stores two equipment records and reconstructs no extra inventory");
                var split = copy.members().prepareUnbind();
                h.assertTrue(
                    split.get(0).getMainHandItem().is(Items.IRON_SWORD)
                        && split.get(1).getMainHandItem().is(Items.DIAMOND_SWORD),
                    "Unbinding returns each original's current weapon");
                f.root.setEquipment(3, ItemStack.EMPTY);
                f.root.setWorking(true);
                h.assertTrue(
                    f.root.needsReplacementTool()
                        && f.root.receiveWorkTool(new ItemStack(Items.STONE_SWORD))
                        && f.root.getEquipment(3).is(Items.STONE_SWORD),
                    "Transport supply can replace member two's missing sword");
              } finally {
                f.root.discard();
                f.area.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "combat_colony", timeoutTicks = 500)
  public static void eachBodyAttacksWithItsOwnWeaponAndChargesOneSharedPool(GameTestHelper h) {
    var f = fixture(h);
    h.startSequence()
        .thenWaitUntil(() -> body(h, f))
        .thenExecute(
            () -> {
              try {
                var body = body(h, f);
                body.moveTo(f.root.position().add(4, 0, 0));
                var first = target(h, f.root.position().add(0, 0, 1.5));
                var second = target(h, body.position().add(0, 0, 1.5));
                f.root.setWorking(true);
                float stamina = f.root.getStamina();
                int swordA = f.root.getEquipment(0).getDamageValue(),
                    swordB = f.root.getEquipment(3).getDamageValue();
                h.assertTrue(
                    f.root.doHurtTarget(first), "Member one performs a normal native attack");
                normalHit(f.root, body, second);
                h.assertTrue(
                    first.getHealth() < 200
                        && second.getHealth() < 200
                        && 200 - first.getHealth() > 200 - second.getHealth(),
                    "Iron and gold swords produce their distinct native damage");
                h.assertTrue(
                    f.root.getEquipment(0).getDamageValue() == swordA + 1
                        && f.root.getEquipment(3).getDamageValue() == swordB + 1
                        && f.root.getStamina() == stamina - 2,
                    "Each real sword wears once and each hit spends one shared stamina");
                h.assertTrue(
                    body.isAlliedTo(f.root) && f.root.isAlliedTo(body) && !f.root.mayAttack(body),
                    "Bodies never select one another as enemies");
              } finally {
                f.root.discard();
                f.area.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "combat_colony", timeoutTicks = 500)
  public static void struckBodyUsesItsOwnArmorAndShieldWithoutSwappingOtherEquipment(
      GameTestHelper h) {
    var f = fixture(h);
    h.startSequence()
        .thenWaitUntil(() -> body(h, f))
        .thenExecute(
            () -> {
              try {
                var body = body(h, f);
                body.moveTo(f.root.position().add(4, 0, 0));
                f.root.setEquipment(2, ItemStack.EMPTY);
                f.root.setEquipment(
                    5, SlimeArmorItem.fromChestplate(new ItemStack(Items.DIAMOND_CHESTPLATE)));
                h.getLevel().tickNonPassenger(f.root);
                h.getLevel().tickNonPassenger(body);
                var damageSource =
                    h.getLevel().damageSources().mobAttack(target(h, body.position().add(0, 0, 3)));
                f.root.invulnerableTime = 0;
                float initial = f.root.getHealth();
                f.root.hurt(damageSource, 8);
                float bareLoss = initial - f.root.getHealth();
                f.root.invulnerableTime = 0;
                float before = f.root.getHealth();
                body.hurt(damageSource, 8);
                float armoredLoss = before - f.root.getHealth();
                h.assertTrue(
                    armoredLoss < bareLoss
                        && f.root.getEquipment(2).isEmpty()
                        && f.root.getEquipment(5).getDamageValue() > 0,
                    "Only the struck second body's diamond armor reduces damage and wears: bare="
                        + bareLoss
                        + " armored="
                        + armoredLoss
                        + " armor="
                        + body.getArmorValue()
                        + " wear="
                        + f.root.getEquipment(5).getDamageValue());
                var enemy = target(h, body.position().add(0, 0, 2));
                body.setYRot(0);
                body.yBodyRot = 0;
                body.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
                for (int i = 0; i < 8; i++) h.getLevel().tickNonPassenger(body);
                f.root.invulnerableTime = 0;
                before = f.root.getHealth();
                int shield = f.root.getEquipment(4).getDamageValue();
                body.hurt(h.getLevel().damageSources().mobAttack(enemy), 6);
                h.assertTrue(
                    f.root.getHealth() == before
                        && f.root.getEquipment(4).getDamageValue() > shield
                        && f.root.getEquipment(1).getDamageValue() == 0,
                    "Second-body shield blocks from its physical facing and wears only its own"
                        + " item");
                enemy.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                body.blockUsingShield(enemy);
                var stored = ModEntities.BOUND_COMBAT_SLIME.get().create(h.getLevel());
                stored.load(f.root.saveWithoutId(new CompoundTag()));
                var secondTiming = ((CombatSlime) stored.members().records().get(1)).combatTiming();
                h.assertTrue(
                    secondTiming.getInt("CombatShieldCooldown") == 100,
                    "Recovery and unbinding retain the second member's shield-disable cooldown");
              } finally {
                f.root.discard();
                f.area.close();
              }
            })
        .thenSucceed();
  }

  private static void critical(GameTestHelper h, boolean firstInitiates) {
    var f = fixture(h);
    float[] damage = {0};
    int[] impacts = {0};
    Object[] listener = {null};
    h.startSequence()
        .thenWaitUntil(() -> body(h, f))
        .thenExecute(
            () -> {
              var body = body(h, f);
              body.moveTo(f.area.point(11, 101, 8));
              body.setOnGround(true);
              f.root.setOnGround(true);
              var target = target(h, f.area.point(firstInitiates ? 4 : 11, 101, 10));
              f.root.setWorking(true);
              f.root.setOnGround(true);
              body.setOnGround(true);
              listener[0] =
                  new Object() {
                    @net.minecraftforge.eventbus.api.SubscribeEvent
                    public void hit(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
                      if (event.getEntity() == target) {
                        damage[0] = event.getAmount();
                        impacts[0]++;
                      }
                    }
                  };
              net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(listener[0]);
              invoke(
                  f.root, firstInitiates ? "combatTargetAcquired" : "linkedTargetAcquired", target);
              h.assertTrue(
                  f.root.assistMember() == (firstInitiates ? 2 : 1),
                  "The other physical member prepares the joining leap");
            })
        .thenExecuteAfter(
            30,
            () -> {
              try {
                float expected =
                    (float)
                        ((f.root.getAttributeBaseValue(Attributes.ATTACK_DAMAGE)
                                + (firstInitiates ? 3 : 5))
                            * 1.5);
                h.assertTrue(
                    impacts[0] == 1
                        && Math.abs(damage[0] - expected) < .01
                        && f.root.assistMember() == 0,
                    "A joining leap deals exactly one native 1.5x critical with the joining body's"
                        + " actual sword: hits="
                        + impacts[0]
                        + " damage="
                        + damage[0]
                        + " expected="
                        + expected);
                var attacker = firstInitiates ? f.root.getLinkedBody() : f.root;
                h.assertTrue(
                    attacker
                            .position()
                            .distanceToSqr(f.area.point(firstInitiates ? 4 : 11, 101, 10))
                        < 5,
                    "The joining body physically travels to the encounter");
              } finally {
                if (listener[0] != null)
                  net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener[0]);
                f.root.discard();
                f.area.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "combat_colony", timeoutTicks = 500)
  public static void firstInitiationCallsSecondBodyForOneCritical(GameTestHelper h) {
    critical(h, true);
  }

  @GameTest(template = "empty", batch = "combat_colony", timeoutTicks = 500)
  public static void secondInitiationCallsFirstBodyForOneCritical(GameTestHelper h) {
    critical(h, false);
  }

  @GameTest(template = "empty", batch = "combat_colony", timeoutTicks = 500)
  public static void waitingAndObstaclesCancelAssistWithoutDamageOrTeleport(GameTestHelper h) {
    var f = fixture(h);
    Vec3[] stopped = {null};
    h.startSequence()
        .thenWaitUntil(() -> body(h, f))
        .thenExecute(
            () -> {
              var body = body(h, f);
              body.moveTo(f.area.point(11, 101, 8));
              f.root.setOnGround(true);
              body.setOnGround(true);
              var target = target(h, f.area.point(4, 101, 10));
              f.root.setWorking(true);
              body.setOnGround(true);
              invoke(f.root, "combatTargetAcquired", target);
              h.assertTrue(f.root.assistMember() == 2, "The clear joining arc starts");
              f.root.setWaiting(true);
              stopped[0] = body.position();
              h.assertTrue(
                  f.root.assistMember() == 0, "Wait cancels the special attack immediately");
            })
        .thenExecuteAfter(
            12,
            () -> {
              try {
                var body = body(h, f);
                h.assertTrue(
                    body.position().distanceToSqr(stopped[0]) < .01,
                    "Waiting leaves the body at its own position");
                f.root.setWorking(true);
                f.root.setOnGround(true);
                body.setOnGround(true);
                for (int y = 101; y < 106; y++)
                  for (int z = 6; z < 13; z++)
                    h.getLevel()
                        .setBlock(
                            net.minecraft.core.BlockPos.containing(f.area.point(8, y, z)),
                            Blocks.STONE.defaultBlockState(),
                            2);
                var target = target(h, f.area.point(4, 101, 11));
                invoke(f.root, "combatTargetAcquired", target);
                h.assertTrue(
                    f.root.assistMember() == 0 && target.getHealth() == 200,
                    "A solid wall does not allow a joining attack");
              } finally {
                f.root.discard();
                f.area.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "combat_colony", timeoutTicks = 500)
  public static void commandCallbackDuringCriticalCannotReuseRetiredMotionState(GameTestHelper h) {
    var f = fixture(h);
    int[] hits = {0};
    Object[] callback = {null};
    h.startSequence()
        .thenWaitUntil(() -> body(h, f))
        .thenExecute(
            () -> {
              var body = body(h, f);
              body.moveTo(f.area.point(11, 101, 8));
              body.setOnGround(true);
              var target = target(h, f.area.point(4, 101, 10));
              f.root.setWorking(true);
              body.setOnGround(true);
              callback[0] =
                  new Object() {
                    @net.minecraftforge.eventbus.api.SubscribeEvent
                    public void pauseJoiningCritical(
                        net.minecraftforge.event.entity.living.LivingDamageEvent event) {
                      if (event.getEntity() == target) {
                        hits[0]++;
                        f.root.setWaiting(true);
                      }
                    }
                  };
              net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(callback[0]);
              invoke(f.root, "combatTargetAcquired", target);
              h.assertTrue(
                  f.root.assistMember() == 2, "The joining attack is staged before the callback");
            })
        .thenExecuteAfter(
            35,
            () -> {
              try {
                h.assertTrue(
                    hits[0] == 1 && f.root.isOrderedToSit() && f.root.assistMember() == 0,
                    "A damage callback may retire the motion safely after one hit");
                var copy = ModEntities.BOUND_COMBAT_SLIME.get().create(h.getLevel());
                copy.load(f.root.saveWithoutId(new CompoundTag()));
                h.assertTrue(
                    copy.assistMember() == 0 && copy.getLinkedBody() == null,
                    "Reload never resumes an airborne critical or saves a helper");
              } finally {
                if (callback[0] != null)
                  net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(callback[0]);
                f.root.discard();
                f.area.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "combat_colony", timeoutTicks = 500)
  public static void helperKillsPreserveAncientCoreLootAndCollectorOwnership(GameTestHelper h) {
    var f = fixture(h);
    h.startSequence()
        .thenWaitUntil(() -> body(h, f))
        .thenExecute(
            () -> {
              try {
                var body = body(h, f);
                body.moveTo(f.area.point(10, 101, 8));
                var ancient = ModEntities.ANCIENT_GUARD_SLIME.get().create(h.getLevel());
                ancient.setNoAi(true);
                ancient.moveTo(body.position().add(0, 0, 1.2));
                ancient.setHealth(1);
                h.getLevel().addFreshEntity(ancient);
                h.assertTrue(
                    ancient.mayAttack(body),
                    "Ancient enemies recognize the actual second body as a companion");
                f.root.setWorking(true);
                normalHit(f.root, body, ancient);
                var drops =
                    h.getLevel()
                        .getEntitiesOfClass(
                            net.minecraft.world.entity.item.ItemEntity.class,
                            body.getBoundingBox().inflate(4));
                h.assertTrue(
                    drops.stream()
                        .anyMatch(
                            item -> item.getItem().is(ModItems.DAMAGED_ANCIENT_SLIME_CORE.get())),
                    "A helper's kill grants the same ancient restoration core");
                h.assertTrue(
                    drops.stream()
                        .allMatch(
                            item ->
                                f.root
                                    .getUUID()
                                    .equals(
                                        item.getPersistentData()
                                            .getUUID(CombatSlime.LOOT_COLLECTOR))),
                    "Native loot remains collectable by the shared colony, without a second owner");
              } finally {
                f.root.discard();
                f.area.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "combat_colony", timeoutTicks = 500)
  public static void joiningLeapDoesNotCrossAnUnsupportedDrop(GameTestHelper h) {
    var f = fixture(h);
    h.startSequence()
        .thenWaitUntil(() -> body(h, f))
        .thenExecute(
            () -> {
              try {
                var body = body(h, f);
                body.moveTo(f.area.point(11, 101, 8));
                body.setOnGround(true);
                f.root.setOnGround(true);
                var target = target(h, f.area.point(4, 101, 10));
                f.root.setWorking(true);
                body.setOnGround(true);
                for (int x = 7; x <= 8; x++)
                  for (int z = 6; z <= 12; z++)
                    h.getLevel()
                        .setBlock(
                            net.minecraft.core.BlockPos.containing(f.area.point(x, 100, z)),
                            Blocks.AIR.defaultBlockState(),
                            2);
                invoke(f.root, "combatTargetAcquired", target);
                h.assertTrue(
                    f.root.assistMember() == 0,
                    "The leap keeps both bodies safe when an excavated gap has no fallback"
                        + " landing");
              } finally {
                f.root.discard();
                f.area.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "combat_colony", timeoutTicks = 500)
  public static void normalHopDoesNotLoseTheJoiningAttack(GameTestHelper h) {
    var f = fixture(h);
    h.startSequence()
        .thenWaitUntil(() -> body(h, f))
        .thenExecute(
            () -> {
              var body = body(h, f);
              body.moveTo(f.area.point(11, 101, 8).add(0, .5, 0));
              body.setOnGround(false);
              var target = target(h, f.area.point(4, 101, 10));
              f.root.setWorking(true);
              body.setOnGround(false);
              invoke(f.root, "combatTargetAcquired", target);
              h.assertTrue(
                  f.root.assistMember() == 2,
                  "A partner already in an ordinary hop can join along a safe supported arc");
            })
        .thenExecuteAfter(
            30,
            () -> {
              try {
                h.assertTrue(
                    f.root.assistMember() == 0,
                    "The airborne start completes and leaves no stuck motion");
              } finally {
                f.root.discard();
                f.area.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "combat_colony", timeoutTicks = 500)
  public static void linkedHealthHealingAndStaminaReadOnePool(GameTestHelper h) {
    var f = fixture(h);
    h.startSequence()
        .thenWaitUntil(() -> body(h, f))
        .thenExecute(
            () -> {
              try {
                var body = body(h, f);
                f.root.setTunedVitals(13, 37, 0);
                h.assertTrue(
                    body.getHealth() == 13
                        && body.getMaxHealth() == f.root.getMaxHealth()
                        && body.getStamina() == 37
                        && body.getMaxStamina() == f.root.getMaxStamina(),
                    "Both physical bodies expose the same health, energy and maxima");
                body.heal(3);
                h.assertTrue(
                    f.root.getHealth() == 16 && body.getHealth() == 16,
                    "Native healing on the helper changes the shared pool once");
                body.hurt(h.getLevel().damageSources().generic(), 4);
                h.assertTrue(
                    f.root.getHealth() < 16 && body.getHealth() == f.root.getHealth(),
                    "Damage to the helper immediately agrees with the representative");
                f.root.setWorking(true);
                normalHit(f.root, body, target(h, f.area.point(4, 101, 10)));
                h.assertTrue(
                    body.getStamina() == 36 && f.root.getStamina() == 36,
                    "Energy expenditure has no second independent reserve");
                var copy = ModEntities.BOUND_COMBAT_SLIME.get().create(h.getLevel());
                copy.load(f.root.saveWithoutId(new CompoundTag()));
                h.assertTrue(
                    copy.getHealth() == body.getHealth() && copy.getStamina() == 36,
                    "Stored colony traits preserve that same remaining pool");
              } finally {
                f.root.discard();
                f.area.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "combat_colony", timeoutTicks = 500)
  public static void existingEncounterRetriesJoiningAfterAnObstructionClears(GameTestHelper h) {
    var f = fixture(h);
    LivingEntity[] victim = {null};
    h.startSequence()
        .thenWaitUntil(() -> body(h, f))
        .thenExecute(
            () -> {
              var body = body(h, f);
              body.moveTo(f.area.point(11, 101, 8));
              victim[0] = target(h, f.area.point(4, 101, 10));
              for (int z = 7; z <= 11; z++)
                for (int y = 101; y <= 104; y++)
                  h.getLevel()
                      .setBlock(
                          net.minecraft.core.BlockPos.containing(f.area.point(8, y, z)),
                          Blocks.STONE.defaultBlockState(),
                          3);
              f.root.setWorking(true);
              f.root.setTarget(victim[0]);
            })
        .thenExecuteAfter(
            20,
            () -> {
              h.assertTrue(
                  f.root.assistMember() == 0 && victim[0].getHealth() == 200,
                  "The blocked initial encounter cannot start a joining attack");
              for (int z = 7; z <= 11; z++)
                for (int y = 101; y <= 104; y++)
                  h.getLevel()
                      .setBlock(
                          net.minecraft.core.BlockPos.containing(f.area.point(8, y, z)),
                          Blocks.AIR.defaultBlockState(),
                          3);
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    f.root.assistMember() == 2,
                    "The ongoing encounter retries without acquiring a new target"))
        .thenExecuteAfter(
            30,
            () -> {
              try {
                h.assertTrue(
                    victim[0].getHealth() < 200,
                    "The retried physical joining leap reaches and damages the same enemy");
              } finally {
                f.root.discard();
                f.area.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "combat_colony", timeoutTicks = 500)
  public static void separatedFightersRejoinWithoutCompetingPursuits(GameTestHelper h) {
    var f = fixture(h);
    CombatColonyBody[] original = {null};
    h.startSequence()
        .thenWaitUntil(() -> body(h, f))
        .thenExecute(
            () -> {
              var body = body(h, f);
              original[0] = body;
              f.root.moveTo(f.area.point(1, 101, 8));
              body.moveTo(f.area.point(14, 101, 14));
              f.root.setWorking(true);
              f.root.setNoAi(false);
              body.setNoAi(false);
              f.root.setTarget(target(h, f.area.point(1, 101, 3)));
              body.setTarget(target(h, f.area.point(14, 101, 13)));
            })
        .thenWaitUntil(
            () -> {
              h.assertTrue(
                  f.root.distanceToSqr(original[0]) < 25,
                  "The second body walks back while the representative holds position: root="
                      + f.root.position()
                      + " body="
                      + original[0].position()
                      + " removed="
                      + original[0].isRemoved()
                      + " nav="
                      + original[0].getNavigation().isDone()
                      + " ground="
                      + original[0].onGround());
            })
        .thenExecute(
            () -> {
              try {
                h.assertTrue(
                    f.root.getLinkedBody() == original[0] && !original[0].isRemoved(),
                    "Normal regrouping keeps the same physical body instead of respawning it");
              } finally {
                f.root.discard();
                f.area.close();
              }
            })
        .thenSucceed();
  }
}
