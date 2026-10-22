package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.*;
import io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe;
import io.github.meistermods.slimic.registry.*;
import io.github.meistermods.slimic.world.*;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForgottenOrdersGameTests {
  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void legacyCoreRoleNamesRemainReadableAndStable(GameTestHelper h) {
    var names = List.of("GUARD", "TRANSPORT", "COMMANDER", "MINER", "TUNNEL", "MINE_GUARD");
    var roles = AncientSlime.Role.values();
    for (int i = 0; i < roles.length; i++) {
      var core = AncientSlimeCoreItem.fromRemains(roles[i], UUID.randomUUID(), 2);
      core.getTag().putString("AncientRole", names.get(i));
      h.assertTrue(
          AncientSlimeCoreItem.role(ItemStack.of(core.save(new CompoundTag()))) == roles[i],
          "Previously saved core roles remain readable after Java enum renaming");
      h.assertTrue(
          AncientSlimeCoreItem.creativeVariant(roles[i])
              .getTag()
              .getString("AncientRole")
              .equals(names.get(i)),
          "New cores retain the established role identifiers");
    }
    h.succeed();
  }

  private record Site(GameTestHelper h, LoadedWorkArea area, Player owner, BlockPos pedestal) {}

  private static Site site(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    area.sealSurroundingFluids();
    var owner = h.makeMockSurvivalPlayer();
    owner.moveTo(area.point(8, 101, 8));
    var pos = BlockPos.containing(area.point(3, 101, 3));
    h.getLevel()
        .setBlockAndUpdate(pos, ModWorldBlocks.block("command_pedestal").defaultBlockState());
    return new Site(h, area, owner, pos);
  }

  private static AncientSlime ancient(Site s, AncientSlime.Role role, double x) {
    var type =
        switch (role) {
          case GUARD -> ModEntities.ANCIENT_GUARD_SLIME.get();
          case TRANSPORT -> ModEntities.FORGOTTEN_TRANSPORT_SLIME.get();
          case COMMAND -> ModEntities.ANCIENT_COMMAND_SLIME.get();
          case MINING -> ModEntities.FORGOTTEN_MINING_SLIME.get();
          case TUNNEL -> ModEntities.FORGOTTEN_TUNNEL_SLIME.get();
          case MINE_GUARD -> ModEntities.MINE_GUARDIAN_SLIME.get();
        };
    var slime = type.create(s.h.getLevel());
    slime.moveTo(s.area.point(x, 101, 8));
    slime.bindToRuin(slime.blockPosition(), UUID.randomUUID(), null, null, 2);
    return slime;
  }

  private static ItemStack core(Site s, AncientSlime.Role role) {
    return AncientSlimeCoreItem.fromSlime(ancient(s, role, 5));
  }

  private static void materials(Site s, int count) {
    s.owner.getInventory().setItem(9, new ItemStack(ModItems.ANCIENT_COMMAND_SEAL.get(), count));
    s.owner.getInventory().setItem(10, new ItemStack(Items.SLIME_BALL, count));
  }

  private static UseOnContext context(Site s, InteractionHand hand) {
    return new UseOnContext(
        s.owner,
        hand,
        new BlockHitResult(Vec3.atCenterOf(s.pedestal), Direction.UP, s.pedestal, false));
  }

  private static boolean restore(Site s, InteractionHand hand) {
    return s.owner.getItemInHand(hand).useOn(context(s, hand)).consumesAction();
  }

  private static int count(Site s, Item item) {
    int total = 0;
    for (int i = 0; i < s.owner.getInventory().getContainerSize(); i++)
      if (s.owner.getInventory().getItem(i).is(item))
        total += s.owner.getInventory().getItem(i).getCount();
    return total;
  }

  private static RoyalGuardSlime guard(Site s, double x) {
    var guard = ModEntities.ROYAL_GUARD_SLIME.get().create(s.h.getLevel());
    guard.tame(s.owner);
    guard.moveTo(s.area.point(x, 101, 8));
    guard.setEquipment(0, new ItemStack(Items.IRON_SWORD));
    guard.setEquipment(1, new ItemStack(Items.SHIELD));
    guard.setWorking(true);
    guard.setNoAi(true);
    s.h.getLevel().addFreshEntity(guard);
    guard.onReleased();
    return guard;
  }

  private static FriendlySlime ally(Site s) {
    var ally = ModEntities.FRIENDLY_SLIME.get().create(s.h.getLevel());
    ally.tame(s.owner);
    ally.moveTo(s.area.point(8, 101, 8));
    ally.setNoAi(true);
    s.h.getLevel().addFreshEntity(ally);
    return ally;
  }

  private static net.minecraft.world.entity.monster.Husk attacker(Site s) {
    var mob = EntityType.HUSK.create(s.h.getLevel());
    mob.moveTo(s.area.point(10, 101, 8));
    mob.setNoAi(true);
    s.h.getLevel().addFreshEntity(mob);
    return mob;
  }

  private static float hit(Site s, LivingEntity ally, LivingEntity attacker) {
    ally.invulnerableTime = 0;
    float before = ally.getHealth();
    ally.hurt(s.h.getLevel().damageSources().mobAttack(attacker), 8);
    return before - ally.getHealth();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void hostileRemnantsHaveASeparateCompanionAndItemContract(GameTestHelper h) {
    h.assertTrue(
        ModEntities.companionTypes().size() == 27
            && ModEntities.ENTITY_TYPES.getEntries().size() == 33,
        "27 companions and six hostile remnants");
    var s = site(h);
    for (var role :
        new AncientSlime.Role[] {
          AncientSlime.Role.GUARD, AncientSlime.Role.TRANSPORT, AncientSlime.Role.COMMAND
        }) {
      var enemy = ancient(s, role, 5);
      h.assertTrue(
          enemy instanceof net.minecraft.world.entity.monster.Enemy && !enemy.canChangeDimensions(),
          "Remnants stay hostile and bound to their world");
      h.assertTrue(
          !enemy.saveWithoutId(new CompoundTag()).hasUUID("Owner"),
          "Ancient data has no companion ownership");
    }
    s.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void allThreeCoreRolesRestoreWithNewIdentityOwnershipAndOneHealth(
      GameTestHelper h) {
    var s = site(h);
    materials(s, 4);
    for (var role :
        new AncientSlime.Role[] {
          AncientSlime.Role.GUARD, AncientSlime.Role.TRANSPORT, AncientSlime.Role.COMMAND
        }) {
      var core = core(s, role);
      var old = core.getTag().getUUID("AncientIdentity");
      s.owner.setItemInHand(InteractionHand.MAIN_HAND, core);
      h.assertTrue(restore(s, InteractionHand.MAIN_HAND), "Restore role " + role);
      var item = s.owner.getMainHandItem();
      var data = item.getTag().getCompound("SlimeData");
      var expected =
          switch (role) {
            case GUARD, MINE_GUARD -> ModItems.ROYAL_GUARD_SLIME.get();
            case TRANSPORT -> ModItems.TRANSPORT_SLIME.get();
            case COMMAND -> ModItems.COMMAND_SLIME.get();
            case MINING -> ModItems.ANCIENT_MINING_SLIME.get();
            case TUNNEL -> ModItems.ANCIENT_TUNNEL_SLIME.get();
          };
      h.assertTrue(
          item.is(expected)
              && !old.equals(data.getUUID("UUID"))
              && data.getUUID("Owner").equals(s.owner.getUUID()),
          "New owned identity and correct profession");
      h.assertTrue(
          data.getFloat("Health") == 1
              && data.getBoolean("Sitting")
              && data.getBoolean("AncientOrigin")
              && data.getCompound("SlimeStats").getInt("stamina") >= 2,
          "One health, Wait and retained veteran traits");
      h.assertTrue(
          ForgottenOrders.get(h.getLevel()).isRedeemed(old)
              && CompanionRegistry.get(h.getLevel()).find(data.getUUID("UUID")).location()
                  == CompanionRegistry.Location.ITEM,
          "Persistent redemption and stored roster");
    }
    h.assertTrue(
        count(s, ModItems.ANCIENT_COMMAND_SEAL.get()) == 1 && count(s, Items.SLIME_BALL) == 1,
        "Each restoration consumes exactly one of each material");
    s.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void duplicateCoreCannotRestoreAfterLedgerSerialization(GameTestHelper h) {
    var s = site(h);
    materials(s, 3);
    var core = core(s, AncientSlime.Role.GUARD);
    var duplicate = core.copy();
    var identity = core.getTag().getUUID("AncientIdentity");
    s.owner.setItemInHand(InteractionHand.MAIN_HAND, core);
    h.assertTrue(restore(s, InteractionHand.MAIN_HAND), "First restoration succeeds");
    var loaded = ForgottenOrders.load(ForgottenOrders.get(h.getLevel()).save(new CompoundTag()));
    h.assertTrue(
        loaded.isRedeemed(identity) && !loaded.reserve(identity),
        "Redeemed token survives serialization");
    s.owner.setItemInHand(InteractionHand.MAIN_HAND, duplicate);
    h.assertTrue(
        !restore(s, InteractionHand.MAIN_HAND)
            && ItemStack.matches(duplicate, s.owner.getMainHandItem())
            && count(s, Items.SLIME_BALL) == 2,
        "Duplicate preserves materials and core");
    s.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void missingMaterialsAndCrackedPedestalNeverConsumeCore(GameTestHelper h) {
    var s = site(h);
    var core = core(s, AncientSlime.Role.GUARD);
    s.owner.setItemInHand(InteractionHand.MAIN_HAND, core);
    h.assertTrue(!restore(s, InteractionHand.MAIN_HAND), "Missing both materials");
    s.owner.getInventory().setItem(9, new ItemStack(ModItems.ANCIENT_COMMAND_SEAL.get()));
    h.assertTrue(!restore(s, InteractionHand.MAIN_HAND), "Missing slimeball");
    materials(s, 1);
    h.getLevel()
        .setBlockAndUpdate(
            s.pedestal, ModWorldBlocks.block("cracked_command_pedestal").defaultBlockState());
    h.assertTrue(
        !restore(s, InteractionHand.MAIN_HAND)
            && s.owner.getMainHandItem() == core
            && count(s, Items.SLIME_BALL) == 1
            && count(s, ModItems.ANCIENT_COMMAND_SEAL.get()) == 1,
        "Cracked pedestal does not start restoration");
    s.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void malformedFutureAndStackedCoresAreRejectedWithoutMutation(GameTestHelper h) {
    var s = site(h);
    materials(s, 8);
    for (int error = 0; error < 5; error++) {
      var stack = core(s, AncientSlime.Role.GUARD);
      switch (error) {
        case 0 -> stack.getTag().remove("AncientIdentity");
        case 1 -> stack.getTag().putInt("AncientCoreVersion", 2);
        case 2 -> stack.getTag().putString("AncientRole", "BAD");
        case 3 -> stack.setCount(2);
        default -> stack.setTag(null);
      }
      var original = stack.copy();
      s.owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
      h.assertTrue(
          !restore(s, InteractionHand.MAIN_HAND)
              && ItemStack.matches(original, s.owner.getMainHandItem()),
          "Invalid core preserved: " + error);
    }
    h.assertTrue(count(s, Items.SLIME_BALL) == 8, "No invalid attempt consumes materials");
    s.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void offhandRestorationWorksWithAFullInventory(GameTestHelper h) {
    var s = site(h);
    for (int i = 0; i < 36; i++) s.owner.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
    materials(s, 2);
    s.owner.setItemInHand(InteractionHand.OFF_HAND, core(s, AncientSlime.Role.TRANSPORT));
    h.assertTrue(
        restore(s, InteractionHand.OFF_HAND)
            && s.owner.getOffhandItem().is(ModItems.TRANSPORT_SLIME.get())
            && s.owner.getMainHandItem().is(Items.STONE),
        "Core is replaced in its own hand without dropping a result");
    s.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void coreWhitelistNeverImportsHostileEquipmentCargoTargetsOrOwner(
      GameTestHelper h) {
    var s = site(h);
    materials(s, 1);
    var stack = core(s, AncientSlime.Role.GUARD);
    var injected = new CompoundTag();
    injected.putUUID("UUID", UUID.randomUUID());
    injected.putUUID("Owner", UUID.randomUUID());
    injected.putBoolean("CombatWorking", true);
    stack.getOrCreateTag().put("SlimeData", injected);
    stack.getTag().putString("AncientName", "malformed json");
    s.owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
    h.assertTrue(
        restore(s, InteractionHand.MAIN_HAND),
        "Malformed optional name does not destroy a valid core");
    var data = s.owner.getMainHandItem().getTag().getCompound("SlimeData");
    h.assertTrue(
        !data.getUUID("UUID").equals(injected.getUUID("UUID"))
            && data.getUUID("Owner").equals(s.owner.getUUID())
            && !data.getBoolean("CombatWorking")
            && data.getBoolean("Sitting"),
        "Untrusted hostile snapshot is ignored");
    s.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void nestedCoreRestorationCommitsOnceAndConsumesOnce(GameTestHelper h) {
    for (boolean creative : new boolean[] {false, true}) {
      var s = site(h);
      materials(s, 3);
      s.owner.setItemInHand(
          InteractionHand.MAIN_HAND,
          creative
              ? AncientSlimeCoreItem.creativeVariant(AncientSlime.Role.GUARD)
              : core(s, AncientSlime.Role.GUARD));
      boolean[] attempted = {false}, nested = {true};
      Consumer<EntityEvent.EntityConstructing> hook =
          event -> {
            if (!attempted[0]
                && event.getEntity().getType() == ModEntities.ROYAL_GUARD_SLIME.get()) {
              attempted[0] = true;
              nested[0] = restore(s, InteractionHand.MAIN_HAND);
            }
          };
      MinecraftForge.EVENT_BUS.addListener(hook);
      try {
        h.assertTrue(
            restore(s, InteractionHand.MAIN_HAND) && attempted[0] && !nested[0],
            "Nested callback cannot redeem the pending core");
      } finally {
        MinecraftForge.EVENT_BUS.unregister(hook);
      }
      h.assertTrue(
          count(s, Items.SLIME_BALL) == 2 && count(s, ModItems.ANCIENT_COMMAND_SEAL.get()) == 2,
          "Exactly one commit");
      s.area.close();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void combatSpecializationPreservesExactIdentityGrowthEquipmentAndCargo(
      GameTestHelper h) {
    var s = site(h);
    var fighter = ModEntities.COMBAT_SLIME.get().create(h.getLevel());
    fighter.tame(s.owner);
    fighter.setHealth(7);
    fighter.setEquipment(0, new ItemStack(Items.DIAMOND_SWORD));
    fighter.setEquipment(1, new ItemStack(Items.SHIELD));
    fighter.setEquipment(2, SlimeArmorItem.fromChestplate(new ItemStack(Items.DIAMOND_CHESTPLATE)));
    fighter.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 17));
    fighter.setWorking(true);
    var saved = fighter.saveWithoutId(new CompoundTag());
    saved.putFloat("Stamina", 37);
    saved.putInt("StatPoints", 5);
    saved.putInt("EarnedPoints", 4);
    saved.putInt("ActivityProgress", 51);
    fighter.load(saved);
    var source = FriendlySlimeItem.fromSlime(fighter);
    var before = source.copy();
    var result =
        AdvancedCompanionRecipe.specialize(
            AdvancedCompanionRecipe.Kind.ROYAL_GUARD,
            source,
            new ItemStack(ModItems.ANCIENT_COMMAND_SEAL.get()));
    var royal = ModEntities.ROYAL_GUARD_SLIME.get().create(h.getLevel());
    royal.load(result.getTag().getCompound("SlimeData"));
    h.assertTrue(
        result.is(ModItems.ROYAL_GUARD_SLIME.get())
            && ItemStack.matches(source, before)
            && royal.getUUID().equals(fighter.getUUID())
            && royal.getOwnerUUID().equals(s.owner.getUUID()),
        "Specialization preserves identity without mutating the source");
    h.assertTrue(
        royal.getHealth() == 7
            && royal.getStamina() == 37
            && royal.getStatPoints() == 5
            && royal.isOrderedToSit()
            && !royal.isWorking()
            && royal.getBag().getStackInSlot(0).getCount() == 17,
        "Growth, vitals, cargo and safe initial order");
    for (int i = 0; i < 3; i++)
      h.assertTrue(
          ItemStack.matches(royal.getEquipment(i), fighter.getEquipment(i)),
          "Exact equipment " + i);
    s.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void guardPostPersistsOnWorldLoadAndMovesOnRelease(GameTestHelper h) {
    var s = site(h);
    var guard = guard(s, 6);
    var post = guard.getGuardPost();
    var restored = ModEntities.ROYAL_GUARD_SLIME.get().create(h.getLevel());
    restored.load(guard.saveWithoutId(new CompoundTag()));
    h.assertTrue(
        restored.getGuardPost().equals(post) && restored.isWorking(),
        "Ordinary load preserves an active post");
    restored.moveTo(s.area.point(12, 101, 12));
    restored.onReleased();
    restored.setWaiting(true);
    h.assertTrue(
        restored.getGuardPost().equals(restored.blockPosition()) && !restored.isWorking(),
        "Re-release relocates the post and waits");
    h.assertTrue(
        SlimeWorkArea.of(restored).shapes().size() == 2 && SlimeWorkArea.of(restored).planned(),
        "Scanner includes planned guard and protection radii");
    s.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void guardFiltersTargetsByPostAndProtectsUsingRealDamageEvents(GameTestHelper h) {
    var s = site(h);
    var guard = guard(s, 6);
    var ally = ally(s);
    var attacker = attacker(s);
    float stamina = guard.getStamina();
    h.assertTrue(guard.canProtect(ally, attacker), "Visible cooperating ally in reach");
    float damage = hit(s, ally, attacker);
    h.assertTrue(
        Math.abs(damage - 5.2F) < .02
            && guard.getHealth() < guard.getMaxHealth()
            && guard.getStamina() < stamina,
        "35% interception changes actual damage and costs resources");
    attacker.moveTo(s.area.point(15, 101, 15));
    h.assertTrue(!guard.mayAttack(attacker), "Target outside post range is excluded");
    s.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void waitMissingShieldFiltersAndExhaustionDisableProtection(GameTestHelper h) {
    var s = site(h);
    var guard = guard(s, 6);
    var ally = ally(s);
    var attacker = attacker(s);
    guard.setWaiting(true);
    h.assertTrue(!guard.canProtect(ally, attacker), "Wait never intercepts");
    guard.setWorking(true);
    guard.setEquipment(1, ItemStack.EMPTY);
    h.assertTrue(!guard.canProtect(ally, attacker), "Shield required");
    guard.setEquipment(1, new ItemStack(Items.SHIELD));
    guard.setAllowed(CombatTarget.HOSTILE, false);
    h.assertTrue(!guard.canProtect(ally, attacker), "Target filter applies");
    guard.setAllowed(CombatTarget.HOSTILE, true);
    var saved = guard.saveWithoutId(new CompoundTag());
    saved.putFloat("Stamina", 3);
    guard.load(saved);
    h.assertTrue(!guard.canProtect(ally, attacker), "At least four stamina required");
    h.assertTrue(hit(s, ally, attacker) == 8, "Unprotected damage proceeds normally");
    s.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void multipleGuardsInterceptOnceAndNeverChain(GameTestHelper h) {
    var s = site(h);
    var first = guard(s, 6);
    var second = guard(s, 5);
    var ally = ally(s);
    var attacker = attacker(s);
    float damage = hit(s, ally, attacker);
    h.assertTrue(
        Math.abs(damage - 5.2F) < .02
            && first.getHealth() < first.getMaxHealth()
            && second.getHealth() == second.getMaxHealth(),
        "One closest guard redirects once");
    h.assertTrue(!second.canProtect(first, attacker), "Guard-to-guard redirection cannot recurse");
    s.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void raisedShieldInterceptsWithoutHealthOrDurabilityCostsButSpendsStamina(
      GameTestHelper h) {
    var s = site(h);
    var guard = guard(s, 6);
    var ally = ally(s);
    var enemy = attacker(s);
    guard.startUsingItem(InteractionHand.OFF_HAND);
    h.runAfterDelay(
        8,
        () -> {
          var source = h.getLevel().damageSources().mobAttack(enemy);
          for (int yaw = 0; yaw < 360; yaw += 90) {
            guard.setYRot(yaw);
            guard.setYHeadRot(yaw);
            if (guard.isDamageSourceBlocked(source)) break;
          }
          h.assertTrue(
              guard.isBlocking() && guard.isDamageSourceBlocked(source),
              "The raised native shield faces this exact attacker");
          float stamina = guard.getStamina();
          h.assertTrue(
              Math.abs(hit(s, ally, enemy) - 5.2F) < .02,
              "A fully shielded redirected hit still protects the ally");
          h.assertTrue(
              guard.getHealth() == guard.getMaxHealth()
                  && guard.getOffhandItem().getDamageValue() == 0
                  && Math.abs(stamina - guard.getStamina() - 5.6F) < .02,
              "A small blocked hit costs stamina even below the native shield-wear threshold");
          s.area.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void canceledShieldAndGuardDamageCannotCreateAnInterception(GameTestHelper h) {
    var s = site(h);
    var guard = guard(s, 6);
    var ally = ally(s);
    var enemy = attacker(s);
    guard.startUsingItem(InteractionHand.OFF_HAND);
    h.runAfterDelay(
        8,
        () -> {
          var source = h.getLevel().damageSources().mobAttack(enemy);
          for (int yaw = 0; yaw < 360; yaw += 90) {
            guard.setYRot(yaw);
            guard.setYHeadRot(yaw);
            if (guard.isDamageSourceBlocked(source)) break;
          }
          h.assertTrue(
              guard.isDamageSourceBlocked(source),
              "The shield faces the incoming hit before the protection veto");
          Consumer<net.minecraftforge.event.entity.living.ShieldBlockEvent> cancelShield =
              event -> {
                if (event.getEntity() == guard) event.setCanceled(true);
              };
          Consumer<LivingHurtEvent> cancelDamage =
              event -> {
                if (event.getEntity() == guard) event.setCanceled(true);
              };
          MinecraftForge.EVENT_BUS.addListener(cancelShield);
          MinecraftForge.EVENT_BUS.addListener(cancelDamage);
          try {
            h.assertTrue(
                hit(s, ally, enemy) == 8
                    && guard.getHealth() == guard.getMaxHealth()
                    && guard.getStamina() == 100,
                "Canceled shield blocking and canceled guard damage never erase an ally hit");
          } finally {
            MinecraftForge.EVENT_BUS.unregister(cancelShield);
            MinecraftForge.EVENT_BUS.unregister(cancelDamage);
          }
          s.area.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void canceledGuardDamageCannotGrantFreeProtection(GameTestHelper h) {
    var s = site(h);
    var guard = guard(s, 6);
    var ally = ally(s);
    var attacker = attacker(s);
    Consumer<LivingHurtEvent> cancel =
        event -> {
          if (event.getEntity() == guard) event.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(cancel);
    try {
      h.assertTrue(
          hit(s, ally, attacker) == 8
              && guard.getHealth() == guard.getMaxHealth()
              && guard.getStamina() == 100,
          "Canceled guard hit does not erase ally damage or consume stamina");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(cancel);
    }
    s.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void protectionHonorsSharedOwnershipRevocationAndPvP(GameTestHelper h) {
    var s = site(h);
    var guard = guard(s, 6);
    var ally = ally(s);
    var attacker = attacker(s);
    var other = h.makeMockSurvivalPlayer();
    ally.tame(other);
    h.assertTrue(!guard.canProtect(ally, attacker), "Unrelated companion not protected");
    var registry = CompanionRegistry.get(h.getLevel());
    registry.setRelation(s.owner.getUUID(), other.getUUID(), true, true);
    registry.setRelation(other.getUUID(), s.owner.getUUID(), true, true);
    h.assertTrue(guard.canProtect(ally, attacker), "Mutually shared ally protected");
    registry.setRelation(other.getUUID(), s.owner.getUUID(), true, false);
    h.assertTrue(!guard.canProtect(ally, attacker), "Revocation takes effect immediately");
    ally.tame(s.owner);
    other.moveTo(attacker.position());
    guard.setAllowed(CombatTarget.PLAYERS, true);
    boolean pvp = h.getLevel().getServer().isPvpAllowed();
    h.getLevel().getServer().setPvpAllowed(false);
    try {
      h.assertTrue(
          !guard.canProtect(ally, other),
          "Disabled PvP also disables interception against players");
    } finally {
      h.getLevel().getServer().setPvpAllowed(pvp);
    }
    s.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void protectionNeedsSightAndDoesNotRedirectEnvironment(GameTestHelper h) {
    var s = site(h);
    var guard = guard(s, 6);
    var ally = ally(s);
    var attacker = attacker(s);
    h.getLevel()
        .setBlockAndUpdate(
            BlockPos.containing(s.area.point(7, 101, 8)), Blocks.STONE.defaultBlockState());
    h.getLevel()
        .setBlockAndUpdate(
            BlockPos.containing(s.area.point(7, 102, 8)), Blocks.STONE.defaultBlockState());
    h.assertTrue(!guard.canProtect(ally, attacker), "No protection through a wall");
    float before = guard.getHealth();
    ally.hurt(h.getLevel().damageSources().fall(), 4);
    h.assertTrue(guard.getHealth() == before, "Environmental damage remains on the victim");
    s.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void ancientFactionTargetsOnlyIntrudersAndRespectsTheHomeBoundary(
      GameTestHelper h) {
    var s = site(h);
    var guard = ancient(s, AncientSlime.Role.GUARD, 5);
    var ally = ally(s);
    var peer = ancient(s, AncientSlime.Role.TRANSPORT, 7);
    h.assertTrue(
        guard.mayAttack(s.owner)
            && guard.mayAttack(ally)
            && !guard.mayAttack(peer)
            && !guard.mayAttack(attacker(s)),
        "Ancient faction targets players and companions, not peers or wildlife");
    s.owner.getAbilities().instabuild = true;
    h.assertTrue(!guard.mayAttack(s.owner), "Creative immunity");
    s.owner.getAbilities().instabuild = false;
    s.owner.moveTo(guard.position().add(25, 0, 0));
    h.assertTrue(!guard.mayAttack(s.owner), "Pursuit stops outside the ruin");
    s.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void commandAlertsOnlyGuardsOfItsOwnRuin(GameTestHelper h) {
    var s = site(h);
    var command = ancient(s, AncientSlime.Role.COMMAND, 5);
    var guard = ancient(s, AncientSlime.Role.GUARD, 6);
    var outsider = ancient(s, AncientSlime.Role.GUARD, 7);
    var carrier = ancient(s, AncientSlime.Role.TRANSPORT, 9);
    var ally = ally(s);
    guard.bindToRuin(command.getRuinHome(), command.getRuinId(), null, null, 1);
    carrier.bindToRuin(command.getRuinHome(), command.getRuinId(), null, null, 1);
    for (var npc : new AncientSlime[] {command, guard, outsider, carrier}) {
      npc.setNoAi(true);
      h.getLevel().addFreshEntity(npc);
    }
    command.alert(ally);
    h.assertTrue(
        command.getTarget() == ally
            && guard.getTarget() == ally
            && outsider.getTarget() == null
            && carrier.getTarget() == null,
        "Shared ruin guards rally without waking other ruins or passive carriers");
    s.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders", timeoutTicks = 500)
  public static void ancientGuardReallyAcquiresCompanionAndReturnsAfterItsEscape(GameTestHelper h) {
    var s = site(h);
    var npc = ancient(s, AncientSlime.Role.GUARD, 5);
    var ally = ally(s);
    h.getLevel().addFreshEntity(npc);
    h.startSequence()
        .thenWaitUntil(
            () -> h.assertTrue(npc.getTarget() == ally, "Real hostile AI acquires a companion"))
        .thenExecute(() -> ally.moveTo(npc.position().add(30, 0, 0)))
        .thenWaitUntil(() -> h.assertTrue(npc.getTarget() == null, "Escaped target is released"))
        .thenExecute(
            () -> {
              npc.discard();
              ally.discard();
              s.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders", timeoutTicks = 500)
  public static void ancientCarrierMovesRealItemsAndStopsAfterPlayerClaimsDepot(GameTestHelper h) {
    var s = site(h);
    var source = s.area.chest(6, 101, 7);
    var destination = s.area.chest(9, 101, 7);
    var npc = ancient(s, AncientSlime.Role.TRANSPORT, 7);
    var ruin = UUID.randomUUID();
    source.getPersistentData().putUUID(AncientSlime.DEPOT_KEY, ruin);
    destination.getPersistentData().putUUID(AncientSlime.DEPOT_KEY, ruin);
    source.setItem(0, new ItemStack(Items.DIAMOND, 8));
    npc.bindToRuin(npc.blockPosition(), ruin, source.getBlockPos(), destination.getBlockPos(), 1);
    h.getLevel().addFreshEntity(npc);
    h.startSequence()
        .thenWaitUntil(
            () -> h.assertTrue(!destination.isEmpty(), "A physical item reaches the paired depot"))
        .thenExecute(
            () -> {
              int total =
                  source.getItem(0).getCount()
                      + destination.getItem(0).getCount()
                      + npc.getParcel().getCount();
              h.assertTrue(total == 8, "Transport never duplicates its parcel");
              var block = destination.getBlockState();
              block.use(
                  h.getLevel(),
                  s.owner,
                  InteractionHand.MAIN_HAND,
                  new BlockHitResult(
                      Vec3.atCenterOf(destination.getBlockPos()),
                      Direction.UP,
                      destination.getBlockPos(),
                      false));
              h.assertTrue(
                  !destination.getPersistentData().contains(AncientSlime.DEPOT_KEY),
                  "Player opening claims the destination");
              npc.discard();
              s.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders", timeoutTicks = 180)
  public static void mobGriefingDisabledPreventsAncientTransport(GameTestHelper h) {
    var s = site(h);
    var source = s.area.chest(6, 101, 7);
    var destination = s.area.chest(9, 101, 7);
    var npc = ancient(s, AncientSlime.Role.TRANSPORT, 7);
    var ruin = UUID.randomUUID();
    for (var chest :
        new io.github.meistermods.slimic.block.SlimeChestBlockEntity[] {source, destination})
      chest.getPersistentData().putUUID(AncientSlime.DEPOT_KEY, ruin);
    source.setItem(0, new ItemStack(Items.DIAMOND, 8));
    npc.bindToRuin(npc.blockPosition(), ruin, source.getBlockPos(), destination.getBlockPos(), 1);
    boolean before = h.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    h.getLevel()
        .getGameRules()
        .getRule(GameRules.RULE_MOBGRIEFING)
        .set(false, h.getLevel().getServer());
    h.getLevel().addFreshEntity(npc);
    h.runAfterDelay(
        100,
        () -> {
          try {
            h.assertTrue(
                source.getItem(0).getCount() == 8
                    && destination.isEmpty()
                    && npc.getParcel().isEmpty(),
                "Mob griefing denies both pickup and deposit");
            npc.discard();
            s.area.close();
            h.succeed();
          } finally {
            h.getLevel()
                .getGameRules()
                .getRule(GameRules.RULE_MOBGRIEFING)
                .set(before, h.getLevel().getServer());
          }
        });
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void eachAncientDeathDropsOneCoreOneSealAndNoCompanionRecoveryBlock(
      GameTestHelper h) {
    var s = site(h);
    var ally = ally(s);
    for (var role :
        new AncientSlime.Role[] {
          AncientSlime.Role.GUARD, AncientSlime.Role.TRANSPORT, AncientSlime.Role.COMMAND
        }) {
      var npc = ancient(s, role, 5);
      npc.setNoAi(true);
      h.getLevel().addFreshEntity(npc);
      npc.hurt(h.getLevel().damageSources().mobAttack(ally), 1000);
    }
    var drops =
        h.getLevel()
            .getEntitiesOfClass(
                ItemEntity.class, new AABB(s.area.point(0, 100, 0), s.area.point(16, 106, 16)));
    h.assertTrue(
        drops.stream()
                .filter(i -> i.getItem().is(ModItems.DAMAGED_ANCIENT_SLIME_CORE.get()))
                .mapToInt(i -> i.getItem().getCount())
                .sum()
            == 3,
        "Exactly one core per remnant");
    h.assertTrue(
        drops.stream()
                    .filter(i -> i.getItem().is(ModItems.ANCIENT_COMMAND_SEAL.get()))
                    .mapToInt(i -> i.getItem().getCount())
                    .sum()
                == 3
            && drops.stream().noneMatch(i -> i.getItem().is(ModItems.BROKEN_SLIME.get())),
        "One seal each and no tame recovery payload");
    s.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void remnantSaveRetainsHomeRuinAndVeterancyWithoutAPlayerOwner(GameTestHelper h) {
    var s = site(h);
    for (var role :
        new AncientSlime.Role[] {
          AncientSlime.Role.GUARD, AncientSlime.Role.TRANSPORT, AncientSlime.Role.COMMAND
        }) {
      var npc = ancient(s, role, 5);
      var copy = (AncientSlime) npc.getType().create(h.getLevel());
      copy.load(npc.saveWithoutId(new CompoundTag()));
      h.assertTrue(
          copy.getRuinHome().equals(npc.getRuinHome())
              && copy.getRuinId().equals(npc.getRuinId())
              && copy.getVeterancy() == 2
              && copy.role() == role,
          "Bound duty round trip: " + role);
    }
    s.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void ruinChestsHaveOptionalSingleSealsAndGuaranteedRecords(GameTestHelper h) {
    for (var path : new String[] {"slimic_ruins", "slimic_archive", "slimic_royal_ward"}) {
      var table =
          h.getLevel().getServer().getLootData().getLootTable(SlimicWorld.id("chests/" + path));
      var params =
          new net.minecraft.world.level.storage.loot.LootParams.Builder(h.getLevel())
              .withParameter(
                  net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                  Vec3.ZERO)
              .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
      int found = 0;
      for (int i = 0; i < 256; i++) {
        var loot = table.getRandomItems(params, 17L + i * 104729L);
        int count =
            loot.stream()
                .filter(item -> item.is(ModItems.ANCIENT_COMMAND_SEAL.get()))
                .mapToInt(ItemStack::getCount)
                .sum();
        h.assertTrue(
            count <= 1 && loot.stream().anyMatch(item -> item.is(Items.WRITTEN_BOOK)),
            "At most one seal and a guaranteed record: " + path);
        found += count;
      }
      double chance =
          path.equals("slimic_archive") ? .35 : path.equals("slimic_royal_ward") ? .5 : .2;
      h.assertTrue(
          Math.abs(found / 256.0 - chance) < .12,
          "Seeded native loot samples reflect the optional seal rate: " + path + " " + found);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void stationExplainsRestorationWithoutConsumingMaterials(GameTestHelper h) {
    var s = site(h);
    var player =
        net.minecraftforge.common.util.FakePlayerFactory.get(
            h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "StationHelp"));
    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    player.moveTo(Vec3.atCenterOf(s.pedestal));
    player.getInventory().setItem(9, new ItemStack(ModItems.ANCIENT_COMMAND_SEAL.get()));
    player.getInventory().setItem(10, new ItemStack(Items.SLIME_BALL));
    var result =
        player.gameMode.useItemOn(
            player,
            h.getLevel(),
            ItemStack.EMPTY,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(s.pedestal), Direction.UP, s.pedestal, false));
    h.assertTrue(
        result.consumesAction()
            && player.getMainHandItem().isEmpty()
            && player.getInventory().getItem(9).getCount() == 1
            && player.getInventory().getItem(10).getCount() == 1,
        "Empty-hand station help is handled without spending restoration materials");
    s.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void stationHelpAllowsNativeRestorationInEitherHand(GameTestHelper h) {
    var s = site(h);
    for (var hand : InteractionHand.values()) {
      var player =
          net.minecraftforge.common.util.FakePlayerFactory.get(
              h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "StationCore"));
      player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
      player.moveTo(Vec3.atCenterOf(s.pedestal));
      var core = core(s, AncientSlime.Role.GUARD);
      player.setItemInHand(hand, core);
      player.getInventory().setItem(9, new ItemStack(ModItems.ANCIENT_COMMAND_SEAL.get()));
      player.getInventory().setItem(10, new ItemStack(Items.SLIME_BALL));
      var hit = new BlockHitResult(Vec3.atCenterOf(s.pedestal), Direction.UP, s.pedestal, false);
      if (hand == InteractionHand.OFF_HAND)
        h.assertTrue(
            !player
                .gameMode
                .useItemOn(player, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit)
                .consumesAction(),
            "Station help must let an offhand core handle the click");
      var result = player.gameMode.useItemOn(player, h.getLevel(), core, hand, hit);
      h.assertTrue(
          result.consumesAction()
              && player.getItemInHand(hand).is(ModItems.ROYAL_GUARD_SLIME.get())
              && player.getItemInHand(hand).getTag().getCompound("SlimeData").getFloat("Health")
                  == 1
              && player.getInventory().getItem(9).isEmpty()
              && player.getInventory().getItem(10).isEmpty(),
          "Native station use restores at one health and consumes each ingredient once: " + hand);
    }
    s.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders", timeoutTicks = 500)
  public static void activeRoyalGuardActuallyReturnsToItsPostAfterDisplacement(GameTestHelper h) {
    var s = site(h);
    var guard = guard(s, 5);
    var home = Vec3.atBottomCenterOf(guard.getGuardPost());
    guard.moveTo(s.area.point(13, 101, 8));
    guard.setNoAi(false);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    guard.distanceToSqr(home) <= 4,
                    "The idle guard follows a continuous route back to its assigned post"))
        .thenExecute(
            () -> {
              guard.discard();
              s.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders", timeoutTicks = 800)
  public static void ancientGuardFollowsARealRouteToHitAnIntruderAndThenDisengages(
      GameTestHelper h) {
    var s = site(h);
    var npc = ancient(s, AncientSlime.Role.GUARD, 3);
    var companion = ally(s);
    companion.moveTo(s.area.point(12, 101, 8));
    h.getLevel().addFreshEntity(npc);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    companion.getHealth() < companion.getMaxHealth(),
                    "A real bounded path reaches the intruder and performs melee"))
        .thenExecute(() -> companion.moveTo(npc.position().add(30, 0, 0)))
        .thenWaitUntil(
            () -> h.assertTrue(npc.getTarget() == null, "The escaped intruder is released"))
        .thenExecute(
            () -> {
              npc.discard();
              companion.discard();
              s.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders", timeoutTicks = 1000)
  public static void carrierCanReachDepotsBeyondTheGuardsIdlePatrolRadius(GameTestHelper h) {
    var s = site(h);
    var source = s.area.chest(2, 101, 2);
    var destination = s.area.chest(13, 101, 13);
    var npc = ancient(s, AncientSlime.Role.TRANSPORT, 7);
    var ruin = UUID.randomUUID();
    source.getPersistentData().putUUID(AncientSlime.DEPOT_KEY, ruin);
    destination.getPersistentData().putUUID(AncientSlime.DEPOT_KEY, ruin);
    source.setItem(0, new ItemStack(Items.DIAMOND));
    npc.bindToRuin(npc.blockPosition(), ruin, source.getBlockPos(), destination.getBlockPos(), 1);
    h.getLevel().addFreshEntity(npc);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    destination.getItem(0).is(Items.DIAMOND),
                    "A distant depot receives the actual parcel without the four-block idle return"
                        + " interrupting transport"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  source.isEmpty() && npc.getParcel().isEmpty(),
                  "One physical parcel is conserved");
              npc.discard();
              s.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void nativeBlockAndItemUseVetoesPreserveCoreAndMaterials(GameTestHelper h) {
    var s = site(h);
    for (int mode = 0; mode < 6; mode++) {
      var player =
          net.minecraftforge.common.util.FakePlayerFactory.get(
              h.getLevel(),
              new com.mojang.authlib.GameProfile(UUID.randomUUID(), "CorePermission"));
      player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
      player.moveTo(Vec3.atCenterOf(s.pedestal));
      var stack =
          mode < 3
              ? core(s, AncientSlime.Role.GUARD)
              : AncientSlimeCoreItem.creativeVariant(AncientSlime.Role.GUARD);
      var original = stack.copy();
      player.setItemInHand(InteractionHand.MAIN_HAND, stack);
      player.getInventory().setItem(9, new ItemStack(ModItems.ANCIENT_COMMAND_SEAL.get()));
      player.getInventory().setItem(10, new ItemStack(Items.SLIME_BALL));
      int veto = mode % 3;
      Consumer<net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock> hook =
          event -> {
            if (event.getEntity() != player) return;
            if (veto == 0) event.setCanceled(true);
            else if (veto == 1) event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);
            else event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
          };
      MinecraftForge.EVENT_BUS.addListener(hook);
      try {
        player.gameMode.useItemOn(
            player,
            h.getLevel(),
            stack,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(s.pedestal), Direction.UP, s.pedestal, false));
      } finally {
        MinecraftForge.EVENT_BUS.unregister(hook);
      }
      h.assertTrue(
          ItemStack.matches(original, player.getMainHandItem())
              && player.getInventory().getItem(9).getCount() == 1
              && player.getInventory().getItem(10).getCount() == 1,
          "Native permission veto preserves every input: " + mode);
    }
    s.area.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders")
  public static void creativeCoreCopiesRestoreEachRoleIndependently(GameTestHelper h) {
    var s = site(h);
    var identities = new java.util.HashSet<UUID>();
    var companionIds = new java.util.HashSet<UUID>();
    for (var role :
        new AncientSlime.Role[] {
          AncientSlime.Role.GUARD, AncientSlime.Role.TRANSPORT, AncientSlime.Role.COMMAND
        }) {
      var prototype = AncientSlimeCoreItem.creativeVariant(role);
      var original = prototype.copy();
      s.owner.setItemInHand(InteractionHand.MAIN_HAND, prototype.copy());
      h.assertTrue(
          !restore(s, InteractionHand.MAIN_HAND)
              && ItemStack.matches(original, s.owner.getMainHandItem()),
          "Missing materials leave a Creative template unchanged");
      materials(s, 2);
      for (int i = 0; i < 2; i++) {
        var held = prototype.copy();
        s.owner.setItemInHand(InteractionHand.MAIN_HAND, held);
        h.assertTrue(
            restore(s, InteractionHand.MAIN_HAND), "Restore another Creative copy: " + role);
        var expected =
            switch (role) {
              case GUARD, MINE_GUARD -> ModItems.ROYAL_GUARD_SLIME.get();
              case TRANSPORT -> ModItems.TRANSPORT_SLIME.get();
              case COMMAND -> ModItems.COMMAND_SLIME.get();
              case MINING -> ModItems.ANCIENT_MINING_SLIME.get();
              case TUNNEL -> ModItems.ANCIENT_TUNNEL_SLIME.get();
            };
        var result = s.owner.getMainHandItem();
        var data = result.getTag().getCompound("SlimeData");
        var identity = held.getTag().getUUID("AncientIdentity");
        h.assertTrue(
            result.is(expected)
                && data.getFloat("Health") == 1
                && data.getBoolean("Sitting")
                && data.getUUID("Owner").equals(s.owner.getUUID())
                && companionIds.add(data.getUUID("UUID"))
                && identities.add(identity)
                && ForgottenOrders.get(h.getLevel()).isRedeemed(identity),
            "Each copy restores the right waiting companion at one health, once");
      }
      h.assertTrue(
          ItemStack.matches(original, prototype)
              && count(s, ModItems.ANCIENT_COMMAND_SEAL.get()) == 0
              && count(s, Items.SLIME_BALL) == 0,
          "Exact costs and unchanged tab prototype");
    }
    s.area.close();
    h.succeed();
  }

  private static void river(Site s) {
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++)
        s.h
            .getLevel()
            .setBlock(
                BlockPos.containing(s.area.point(x, 101, z)),
                (x <= 3 || x >= 12 ? Blocks.BEDROCK : Blocks.WATER).defaultBlockState(),
                2);
  }

  @GameTest(template = "empty", batch = "forgotten_orders", timeoutTicks = 1000)
  public static void ancientCarrierReplansWhileSwimmingAndDeliversAcrossWater(GameTestHelper h) {
    var s = site(h);
    river(s);
    var source = s.area.chest(2, 102, 8);
    var destination = s.area.chest(13, 102, 8);
    var npc = ancient(s, AncientSlime.Role.TRANSPORT, 8);
    npc.moveTo(s.area.point(8, 101.4, 8));
    var ruin = UUID.randomUUID();
    source.getPersistentData().putUUID(AncientSlime.DEPOT_KEY, ruin);
    destination.getPersistentData().putUUID(AncientSlime.DEPOT_KEY, ruin);
    source.setItem(0, new ItemStack(Items.DIAMOND));
    npc.bindToRuin(
        BlockPos.containing(s.area.point(8, 102, 8)),
        ruin,
        source.getBlockPos(),
        destination.getBlockPos(),
        1);
    h.getLevel().addFreshEntity(npc);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    npc.isInWater() && !npc.onGround(),
                    "The courier starts swimming rather than navigating from a dry bank"))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    destination.getItem(0).is(Items.DIAMOND),
                    "A swimming courier acquires and delivers its exact parcel"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  source.isEmpty() && npc.getParcel().isEmpty(), "No duplicate river cargo");
              npc.discard();
              s.area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "forgotten_orders", timeoutTicks = 1000)
  public static void royalGuardCanReturnTowardItsPostFromWater(GameTestHelper h) {
    var s = site(h);
    river(s);
    var guard = ModEntities.ROYAL_GUARD_SLIME.get().create(h.getLevel());
    guard.tame(s.owner);
    guard.moveTo(s.area.point(2, 102, 8));
    guard.setEquipment(0, new ItemStack(Items.IRON_SWORD));
    guard.setWorking(true);
    h.getLevel().addFreshEntity(guard);
    guard.onReleased();
    var post = Vec3.atBottomCenterOf(guard.getGuardPost());
    guard.moveTo(s.area.point(8, 101.4, 8));
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    guard.isInWater() && !guard.onGround(),
                    "The guard begins in water away from the assigned post"))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    guard.distanceToSqr(post) <= 4,
                    "Water does not disable the guard's return route"))
        .thenExecute(
            () -> {
              guard.discard();
              s.area.close();
            })
        .thenSucceed();
  }
}
