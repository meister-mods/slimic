package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.BrokenSlimeBlockEntity;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.*;
import io.github.meistermods.slimic.registry.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionInteractionGameTests {
  private static SaddleSlime mount(GameTestHelper h, LoadedWorkArea area, Player owner) {
    var mount = ModEntities.SADDLE_SLIME.get().create(h.getLevel());
    mount.tame(owner);
    mount.moveTo(area.point(8.5, 101, 8.5));
    mount.onReleased();
    mount.setWaiting(true);
    mount.setNoAi(true);
    mount.setEquipment(0, new ItemStack(Items.SADDLE));
    var wings = new ItemStack(Items.ELYTRA);
    wings.setDamageValue(21);
    mount.setEquipment(1, wings);
    var armor = SlimeArmorItem.fromChestplate(new ItemStack(Items.DIAMOND_CHESTPLATE));
    armor.setDamageValue(35);
    armor.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 3);
    mount.setEquipment(2, armor);
    return mount;
  }

  private static List<ItemStack> equipment(SaddleSlime slime) {
    return List.of(
        slime.getEquipment(0).copy(), slime.getEquipment(1).copy(), slime.getEquipment(2).copy());
  }

  private static void exact(GameTestHelper h, SaddleSlime mount, List<ItemStack> before) {
    for (int i = 0; i < 3; i++)
      h.assertTrue(
          ItemStack.matches(mount.getEquipment(i), before.get(i)),
          "Cooperating companions preserve exact saddle, wings and armor: " + i);
  }

  private static HealingSlime healer(GameTestHelper h, LoadedWorkArea area, Player owner) {
    var healer = ModEntities.HEALING_SLIME.get().create(h.getLevel());
    healer.tame(owner);
    healer.moveTo(area.point(6.5, 101, 8.5));
    healer.onReleased();
    healer.setWorking(true);
    healer.getBag().setStackInSlot(0, new ItemStack(Items.SLIME_BALL));
    return healer;
  }

  @GameTest(template = "empty", batch = "companion_interactions", timeoutTicks = 500)
  public static void healerTreatsArmoredMountWithoutChangingEquipment(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var mount = mount(h, area, owner);
    mount.setHealth(12);
    var before = equipment(mount);
    var healer = healer(h, area, owner);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(mount.blockPosition()),
                    "Treatment arena ticks"))
        .thenExecute(
            () -> {
              h.getLevel().addFreshEntity(mount);
              h.getLevel().addFreshEntity(healer);
            })
        .thenWaitUntil(
            () -> h.assertTrue(mount.getHealth() == 16, "Armored mount receives treatment"))
        .thenExecute(
            () -> {
              exact(h, mount, before);
              h.assertTrue(
                  healer.getBag().getStackInSlot(0).isEmpty()
                      && healer.getActivityProgress() == 1
                      && mount.isOrderedToSit(),
                  "One ball pays for treatment and preserves the patient's Wait order");
              mount.discard();
              healer.discard();
              area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "companion_interactions", timeoutTicks = 500)
  public static void healerRevivesArmoredMountAtOneHealthWithAllEquipment(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var mount = mount(h, area, owner);
    mount.setTunedVitals(7, 42, 6);
    var before = equipment(mount);
    var id = mount.getUUID();
    var stored = FriendlySlimeItem.fromSlime(mount);
    mount.discard();
    var position = BlockPos.containing(area.point(8.5, 101, 8.5));
    h.getLevel().setBlockAndUpdate(position, ModBlocks.BROKEN_SLIME.get().defaultBlockState());
    ((BrokenSlimeBlockEntity) h.getLevel().getBlockEntity(position)).setSlime(stored);
    var healer = healer(h, area, owner);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(healer.blockPosition()),
                    "Revival arena ticks"))
        .thenExecute(() -> h.getLevel().addFreshEntity(healer))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getEntity(id) instanceof SaddleSlime, "Armored mount revived"))
        .thenExecute(
            () -> {
              var restored = (SaddleSlime) h.getLevel().getEntity(id);
              exact(h, restored, before);
              h.assertTrue(
                  restored.getHealth() == 1
                      && restored.getStamina() == 42
                      && restored.getStatPoints() == 6
                      && restored.getOwnerUUID().equals(owner.getUUID())
                      && !restored.isVehicle()
                      && healer.getBag().getStackInSlot(0).isEmpty()
                      && h.getLevel().getBlockState(position).isAir(),
                  "Native healing revival preserves traits, costs one ball and restores one HP");
              restored.discard();
              healer.discard();
              area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "companion_interactions", timeoutTicks = 500)
  public static void guardianProtectsOwnerWhileRidingAnArmoredMount(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var mount = mount(h, area, owner);
    owner.moveTo(mount.position());
    var before = equipment(mount);
    var guardian = ModEntities.GUARDIAN_ANGEL_SLIME.get().create(h.getLevel());
    guardian.tame(owner);
    guardian.moveTo(area.point(10.5, 101, 8.5));
    guardian.setWaiting(true);
    guardian.setTotems(new ItemStack(Items.TOTEM_OF_UNDYING, 2));
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(guardian.blockPosition()),
                    "Protection arena ticks"))
        .thenExecute(
            () -> {
              h.getLevel().addFreshEntity(mount);
              h.getLevel().addFreshEntity(guardian);
              h.assertTrue(mount.mount(owner), "Owner rides armored mount");
              owner.setHealth(2);
            })
        .thenWaitUntil(() -> h.assertTrue(guardian.tickCount > 0, "Guardian is indexed"))
        .thenExecute(
            () -> {
              owner.hurt(h.getLevel().damageSources().generic(), 40);
              h.assertTrue(
                  owner.isAlive()
                      && owner.getHealth() == 1
                      && owner.getVehicle() == mount
                      && guardian.getTotems().getCount() == 1,
                  "One guardian totem protects the mounted owner without dismounting");
              exact(h, mount, before);
              owner.stopRiding();
              mount.discard();
              guardian.discard();
              area.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "companion_interactions")
  public static void everyProfessionRemainsAlliedToItsOwnersCombatSlime(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var fighter = ModEntities.COMBAT_SLIME.get().create(h.getLevel());
    fighter.tame(owner);
    for (var kind : CombatTarget.values()) fighter.setAllowed(kind, true);
    int checked = 0;
    for (var type :
        List.of(
            ModEntities.FISHING_SLIME.get(),
            ModEntities.ABUNDANCE_SLIME.get(),
            ModEntities.TUNNEL_SLIME.get(),
            ModEntities.DRAGON_SLIME.get(),
            ModEntities.NYAN_CAT_SLIME.get(),
            ModEntities.FRIENDLY_SLIME.get(),
            ModEntities.MINING_SLIME.get(),
            ModEntities.TRANSPORT_SLIME.get(),
            ModEntities.HERALD_SLIME.get(),
            ModEntities.COMBAT_SLIME.get(),
            ModEntities.CLEANING_SLIME.get(),
            ModEntities.SADDLE_SLIME.get(),
            ModEntities.FARMER_SLIME.get(),
            ModEntities.LUMBERJACK_SLIME.get(),
            ModEntities.GUARDIAN_ANGEL_SLIME.get(),
            ModEntities.SOGGY_SLIME.get(),
            ModEntities.HEALING_SLIME.get(),
            ModEntities.WAREHOUSE_SLIME.get())) {
      var companion = type.create(h.getLevel());
      companion.tame(owner);
      h.assertTrue(
          !fighter.mayAttack(companion), "All target filters still protect same-owner " + type);
      companion.discard();
      checked++;
    }
    var foreign = ModEntities.SADDLE_SLIME.get().create(h.getLevel());
    foreign.tame(h.makeMockSurvivalPlayer());
    h.assertTrue(
        checked == 18 && fighter.mayAttack(foreign),
        "A different owner's tame mount remains controlled by the enabled Tamed filter");
    foreign.discard();
    fighter.discard();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "companion_interactions", timeoutTicks = 500)
  public static void healerPreservesSoggyRoamingAndItsFixedOrigin(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var soggy = ModEntities.SOGGY_SLIME.get().create(h.getLevel());
    soggy.tame(owner);
    soggy.moveTo(area.point(8.5, 101, 8.5));
    soggy.onReleased();
    soggy.setRoaming(true);
    soggy.setNoAi(true);
    soggy.setHealth(12);
    var before = soggy.saveWithoutId(new net.minecraft.nbt.CompoundTag());
    var healer = healer(h, area, owner);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(soggy.blockPosition()),
                    "Roaming treatment arena ticks"))
        .thenExecute(
            () -> {
              h.getLevel().addFreshEntity(soggy);
              h.getLevel().addFreshEntity(healer);
            })
        .thenWaitUntil(
            () -> h.assertTrue(soggy.getHealth() == 16, "Roaming Soggy Slime receives treatment"))
        .thenExecute(
            () -> {
              var after = soggy.saveWithoutId(new net.minecraft.nbt.CompoundTag());
              h.assertTrue(
                  soggy.isRoaming()
                      && after.getLong("SoggyRoamHome") == before.getLong("SoggyRoamHome")
                      && healer.getBag().getStackInSlot(0).isEmpty(),
                  "Treatment keeps the patient's Roam command and original work center");
              soggy.discard();
              healer.discard();
              area.close();
            })
        .thenSucceed();
  }
}
