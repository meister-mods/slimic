package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.recipe.AdvancedCompanionRecipe;
import io.github.meistermods.slimic.registry.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AncientBalanceGameTests {
  @GameTest(template = "empty", batch = "ancient_balance")
  public static void royalBaseHealthMatchesGuardianAndTrainingScalesFourfold(GameTestHelper h) {
    var royal = ModEntities.ROYAL_GUARD_SLIME.get().create(h.getLevel());
    var guardian = ModEntities.GUARDIAN_ANGEL_SLIME.get().create(h.getLevel());
    h.assertTrue(
        royal.getMaxHealth() == 64
            && royal.getMaxHealth() == guardian.getMaxHealth()
            && royal.getHealth() == 64,
        "Fresh Royal Guard has the Guardian Angel's 64 health");
    SlimeStat.HEALTH.applyUpgrade(royal);
    royal.setHealth(7);
    for (int i = 0; i < 5; i++) royal.load(royal.saveWithoutId(new CompoundTag()));
    h.assertTrue(
        royal.getMaxHealth() == 80 && royal.getHealth() == 7,
        "Health training adds sixteen; repeated save/load never stacks or heals");
    var sparse = new CompoundTag();
    sparse.putFloat("Health", 7);
    royal = ModEntities.ROYAL_GUARD_SLIME.get().create(h.getLevel());
    royal.load(sparse);
    h.assertTrue(
        royal.getMaxHealth() == 64 && royal.getHealth() == 7,
        "Commandless data retains exactly one profession bonus");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ancient_balance")
  public static void specializationAndOldRoyalDataRetainIdentityHealthAndTraining(
      GameTestHelper h) {
    var combat = ModEntities.COMBAT_SLIME.get().create(h.getLevel());
    combat.tame(h.makeMockSurvivalPlayer());
    SlimeStat.HEALTH.applyUpgrade(combat);
    SlimeStat.HEALTH.applyUpgrade(combat);
    combat.setHealth(7);
    var old = combat.saveWithoutId(new CompoundTag());
    var royal = ModEntities.ROYAL_GUARD_SLIME.get().create(h.getLevel());
    royal.load(old);
    h.assertTrue(
        royal.getMaxHealth() == 96
            && royal.getHealth() == 7
            && royal.getUUID().equals(combat.getUUID()),
        "Legacy data gains the bonus while retaining current health and training");
    var source = FriendlySlimeItem.fromSlime(combat);
    var item =
        AdvancedCompanionRecipe.specialize(
            AdvancedCompanionRecipe.Kind.ROYAL_GUARD,
            source,
            new ItemStack(ModItems.ANCIENT_COMMAND_SEAL.get()));
    var data = item.getTag().getCompound("SlimeData");
    var before = data.copy();
    RoyalGuardSlime.addHealthBonusToData(data);
    h.assertTrue(before.equals(data), "Stored crafting bonus is idempotent");
    royal.load(data);
    h.assertTrue(
        royal.getMaxHealth() == 96
            && royal.getHealth() == 7
            && royal.getUUID().equals(combat.getUUID())
            && royal.isOrderedToSit(),
        "Specialization retains identity, health, growth and Wait");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ancient_balance")
  public static void storedRosterIncludesRoyalAndGuardianAttributeModifiers(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    for (var type :
        List.of(ModEntities.ROYAL_GUARD_SLIME.get(), ModEntities.GUARDIAN_ANGEL_SLIME.get())) {
      var pet = (FriendlySlime) type.create(h.getLevel());
      pet.tame(owner);
      pet.setHealth(7);
      var item = FriendlySlimeItem.fromSlime(pet);
      CompanionRegistry.get(h.getLevel()).observeStored(item, h.getLevel(), BlockPos.ZERO);
      var entry = CompanionRegistry.get(h.getLevel()).find(pet.getUUID());
      h.assertTrue(
          entry != null && entry.health() == 7 && entry.maximumHealth() == 64,
          "Stored roster uses permanent maximum-health modifiers for " + type);
    }
    h.succeed();
  }

  private static List<EntityType<AncientSlime>> types() {
    return List.of(
        ModEntities.ANCIENT_GUARD_SLIME.get(),
        ModEntities.FORGOTTEN_TRANSPORT_SLIME.get(),
        ModEntities.ANCIENT_COMMANDER_SLIME.get());
  }

  private static void checkStats(GameTestHelper h, AncientSlime mob, int index) {
    double[] hp = {64, 48, 96}, attack = {8, 6, 12}, armor = {8, 6, 12};
    h.assertTrue(
        mob.getMaxHealth() == hp[index]
            && mob.getAttributeValue(Attributes.ATTACK_DAMAGE) == attack[index]
            && mob.getAttributeValue(Attributes.ARMOR) == armor[index],
        "Role-specific combat defaults for " + mob.role());
  }

  @GameTest(template = "empty", batch = "ancient_balance")
  public static void allAncientRolesHaveStrongerDefaultsBeforeRuinBinding(GameTestHelper h) {
    for (int i = 0; i < types().size(); i++) {
      var mob = types().get(i).create(h.getLevel());
      checkStats(h, mob, i);
      h.assertTrue(
          mob.getHealth() == mob.getMaxHealth(),
          "Egg and command summons start at full role health");
      mob.bindToRuin(BlockPos.ZERO, UUID.randomUUID(), null, null, 2);
      checkStats(h, mob, i);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ancient_balance")
  public static void oldAncientDefaultsMigrateOnceWithHealthRatioAndParcelIntact(GameTestHelper h) {
    for (int i = 0; i < types().size(); i++) {
      var mob = types().get(i).create(h.getLevel());
      mob.bindToRuin(
          new BlockPos(4, 101, 4),
          UUID.randomUUID(),
          new BlockPos(5, 101, 4),
          new BlockPos(7, 101, 4),
          2);
      mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(i == 2 ? 40 : 28);
      mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(i == 2 ? 5 : 4);
      mob.getAttribute(Attributes.ARMOR).setBaseValue(3);
      mob.setHealth(mob.getMaxHealth() / 2);
      var data = mob.saveWithoutId(new CompoundTag());
      data.remove("AncientCombatVersion");
      var cargo = new ItemStack(Items.DIAMOND, 5);
      cargo.getOrCreateTag().putString("Lot", "old-five");
      data.put("AncientParcel", cargo.save(new CompoundTag()));
      var restored = types().get(i).create(h.getLevel());
      restored.load(data);
      checkStats(h, restored, i);
      h.assertTrue(
          restored.getHealth() == restored.getMaxHealth() / 2
              && restored.getRuinId().equals(mob.getRuinId())
              && restored.getRuinHome().equals(mob.getRuinHome())
              && restored.getParcel().getCount() == 5
              && restored.getParcel().getTag().getString("Lot").equals("old-five"),
          "Migration preserves wound proportion, ruin and exact parcel");
      var saved = restored.saveWithoutId(new CompoundTag());
      h.assertTrue(saved.getInt("AncientCombatVersion") == 1, "New balance version is stored");
      restored.load(saved);
      checkStats(h, restored, i);
      h.assertTrue(
          restored.getHealth() == restored.getMaxHealth() / 2,
          "Repeated reload does not heal or reapply balance");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ancient_balance")
  public static void oldCustomAncientAttributesArePreserved(GameTestHelper h) {
    var mob = types().get(0).create(h.getLevel());
    mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
    mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(20);
    mob.getAttribute(Attributes.ARMOR).setBaseValue(16);
    mob.setHealth(40);
    var data = mob.saveWithoutId(new CompoundTag());
    data.remove("AncientCombatVersion");
    mob.load(data);
    h.assertTrue(
        mob.getMaxHealth() == 100
            && mob.getHealth() == 40
            && mob.getAttributeValue(Attributes.ATTACK_DAMAGE) == 20
            && mob.getAttributeValue(Attributes.ARMOR) == 16,
        "Migration leaves customized attributes and current health intact");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ancient_balance", timeoutTicks = 200)
  public static void nativeSpawnEggsCreateEachUnownedHostileAndConsumeOne(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    area.sealSurroundingFluids();
    var owner = h.makeMockSurvivalPlayer();
    var eggs =
        List.of(
            ModItems.ANCIENT_GUARD_SLIME_SPAWN_EGG.get(),
            ModItems.FORGOTTEN_TRANSPORT_SLIME_SPAWN_EGG.get(),
            ModItems.ANCIENT_COMMANDER_SLIME_SPAWN_EGG.get());
    for (int i = 0; i < eggs.size(); i++) {
      var egg = (SpawnEggItem) eggs.get(i);
      h.assertTrue(
          egg.getType(null) == types().get(i)
              && net.minecraftforge.common.ForgeSpawnEggItem.fromEntityType(types().get(i)) == egg,
          "Native egg-to-entity registration");
      var stack = new ItemStack(egg, 2);
      owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
      var pos = BlockPos.containing(area.point(3 + i * 4, 100, 8));
      var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
      h.assertTrue(
          stack.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND, hit)).consumesAction()
              && stack.getCount() == 1,
          "Native Survival item use consumes exactly one egg");
      var mobs =
          h.getLevel()
              .getEntitiesOfClass(
                  AncientSlime.class,
                  new AABB(pos).inflate(2),
                  m -> m.getType() == egg.getType(null));
      h.assertTrue(mobs.size() == 1, "Exactly one actual hostile entity is spawned");
      var mob = mobs.get(0);
      checkStats(h, mob, i);
      h.assertTrue(mob.getPickResult().is(egg), "Creative pick returns the matching hostile egg");
      h.assertTrue(
          !mob.saveWithoutId(new CompoundTag()).hasUUID("Owner"),
          "Hostile eggs do not register companion ownership");
      mob.discard();
    }
    area.close();
    h.succeed();
  }
}
