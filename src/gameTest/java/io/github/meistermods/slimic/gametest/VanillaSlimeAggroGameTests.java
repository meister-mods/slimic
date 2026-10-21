package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.CombatSlime;
import io.github.meistermods.slimic.entity.CombatTarget;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VanillaSlimeAggroGameTests {
  private static FriendlySlime waiting(
      GameTestHelper h, LoadedWorkArea area, EntityType<?> type, Player owner, int z) {
    var companion = (FriendlySlime) type.create(h.getLevel());
    companion.tame(owner);
    companion.setWaiting(true);
    companion.setNoAi(true);
    companion.moveTo(area.point(8, 101, z));
    companion.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
    companion.setHealth(1000);
    h.getLevel().addFreshEntity(companion);
    return companion;
  }

  private static Slime wild(
      GameTestHelper h, LoadedWorkArea area, EntityType<? extends Slime> type, int z) {
    var slime = type.create(h.getLevel());
    slime.setSize(2, true);
    slime.setPersistenceRequired();
    slime.getRandom().setSeed(7919);
    // Keep sight and distance stable while the native target selector continues ticking.
    slime.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
    slime.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
    slime.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
    slime.setHealth(1000);
    slime.moveTo(area.point(8, 101, z));
    h.getLevel().addFreshEntity(slime);
    return slime;
  }

  @GameTest(template = "empty", batch = "vanilla_slime_aggro", timeoutTicks = 180)
  public static void vanillaSlimesIgnoreEveryProfessionWhileOtherHostilesStillAcquire(
      GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    List<LoadedWorkArea> areas = new ArrayList<>();
    List<Slime> wildSlimes = new ArrayList<>();
    List<FriendlySlime> companions = new ArrayList<>();
    for (var registered : ModEntities.companionTypes()) {
      var area = new LoadedWorkArea(h);
      areas.add(area);
      companions.add(waiting(h, area, registered.get(), owner, 7));
      wildSlimes.add(wild(h, area, EntityType.SLIME, 11));
    }
    List<Mob> otherHostiles = new ArrayList<>();
    List<FriendlySlime> otherTargets = new ArrayList<>();
    var magmaArea = new LoadedWorkArea(h);
    areas.add(magmaArea);
    otherTargets.add(waiting(h, magmaArea, ModEntities.FRIENDLY_SLIME.get(), owner, 7));
    otherHostiles.add(wild(h, magmaArea, EntityType.MAGMA_CUBE, 11));
    var huskArea = new LoadedWorkArea(h);
    areas.add(huskArea);
    otherTargets.add(waiting(h, huskArea, ModEntities.FRIENDLY_SLIME.get(), owner, 7));
    var husk = EntityType.HUSK.create(h.getLevel());
    husk.setPersistenceRequired();
    husk.moveTo(huskArea.point(8, 101, 11));
    husk.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
    husk.getRandom().setSeed(7919);
    h.getLevel().addFreshEntity(husk);
    otherHostiles.add(husk);
    boolean[] unwantedTargets = new boolean[wildSlimes.size()];
    boolean[] ordinaryTargets = new boolean[otherHostiles.size()];
    h.onEachTick(
        () -> {
          for (int i = 0; i < wildSlimes.size(); i++)
            unwantedTargets[i] |= wildSlimes.get(i).getTarget() instanceof FriendlySlime;
          for (int i = 0; i < otherHostiles.size(); i++)
            ordinaryTargets[i] |= otherHostiles.get(i).getTarget() == otherTargets.get(i);
        });
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(wildSlimes.stream().allMatch(s -> s.tickCount >= 10), "AI is ticking"))
        .thenIdle(80)
        .thenExecute(
            () -> {
              h.assertTrue(wildSlimes.size() == 25, "All professions are covered");
              for (int i = 0; i < wildSlimes.size(); i++)
                h.assertTrue(
                    !unwantedTargets[i] && wildSlimes.get(i).getLastHurtByMob() == null,
                    "An unprovoked vanilla Slime never targets " + companions.get(i).getType());
              for (boolean acquired : ordinaryTargets)
                h.assertTrue(acquired, "Magma Cubes and ordinary hostiles retain first aggression");
              areas.forEach(LoadedWorkArea::close);
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "vanilla_slime_aggro", timeoutTicks = 120)
  public static void attackedVanillaSlimeSelectsItsAttackerInsteadOfANearbyBystander(
      GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var bystander = waiting(h, area, ModEntities.FRIENDLY_SLIME.get(), owner, 7);
    var attacker = waiting(h, area, ModEntities.MINING_SLIME.get(), owner, 13);
    var slime = wild(h, area, EntityType.SLIME, 9);
    boolean[] targetedBystander = {false};
    h.onEachTick(() -> targetedBystander[0] |= slime.getTarget() == bystander);
    h.startSequence()
        .thenWaitUntil(() -> h.assertTrue(slime.tickCount >= 10, "AI is ticking"))
        .thenExecute(
            () ->
                h.assertTrue(
                    slime.hurt(slime.damageSources().mobAttack(attacker), 1)
                        && slime.getLastHurtByMob() == attacker,
                    "Actual companion damage records the attacker"))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    slime.getTarget() == attacker, "The vanilla Slime acquires its attacker"))
        .thenExecute(
            () -> {
              h.assertTrue(!targetedBystander[0], "A closer uninvolved companion stays excluded");
              area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "vanilla_slime_aggro", timeoutTicks = 160)
  public static void combatAndDragonSlimesInitiateRealDamageAgainstUnprovokedVanillaSlimes(
      GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    List<LoadedWorkArea> areas = new ArrayList<>();
    List<CombatSlime> fighters = new ArrayList<>();
    List<Slime> victims = new ArrayList<>();
    for (var type : List.of(ModEntities.COMBAT_SLIME.get(), ModEntities.DRAGON_SLIME.get())) {
      var area = new LoadedWorkArea(h);
      areas.add(area);
      var victim = wild(h, area, EntityType.SLIME, 9);
      var fighter = type.create(h.getLevel());
      fighter.tame(owner);
      fighter.moveTo(area.point(8, 101, 7));
      fighter.setEquipment(0, new ItemStack(Items.IRON_SWORD));
      h.assertTrue(
          victim.getLastHurtByMob() == null
              && victim.getTarget() == null
              && fighter.mayAttack(victim),
          "An unprovoked vanilla Slime remains an allowed Hostile target");
      fighter.setAllowed(CombatTarget.HOSTILE, false);
      h.assertTrue(!fighter.mayAttack(victim), "The Hostile filter still controls attacks");
      fighter.setAllowed(CombatTarget.HOSTILE, true);
      fighter.setWorking(true);
      h.getLevel().addFreshEntity(fighter);
      fighters.add(fighter);
      victims.add(victim);
    }
    h.startSequence()
        .thenWaitUntil(
            () -> {
              for (int i = 0; i < fighters.size(); i++)
                h.assertTrue(
                    victims.get(i).getHealth() < 1000
                        && victims.get(i).getLastHurtByMob() == fighters.get(i),
                    "Native combat initiates damage before any provocation: "
                        + fighters.get(i).getType());
            })
        .thenExecute(() -> areas.forEach(LoadedWorkArea::close))
        .thenSucceed();
  }
}
