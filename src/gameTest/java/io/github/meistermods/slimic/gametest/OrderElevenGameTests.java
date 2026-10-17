package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.*;
import io.github.meistermods.slimic.registry.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OrderElevenGameTests {
  private record Ride(GameTestHelper helper, Player owner, SaddleSlime slime)
      implements AutoCloseable {
    double speed(double movement, boolean boost) {
      SlimeStat.MOVEMENT_SPEED.tune(slime, movement);
      owner.zza = 1;
      owner.xxa = 0;
      owner.setYRot(0);
      owner.setXRot(0);
      slime.setTunedVitals(slime.getMaxHealth(), 100, 0);
      slime.setRiderInput(owner, false, boost);
      slime.tick();
      return slime.getDeltaMovement().length();
    }

    @Override
    public void close() {
      owner.stopRiding();
      slime.discard();
    }
  }

  private static Ride ride(GameTestHelper h) {
    var owner =
        new FakePlayer(
            h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "ArmorRider"));
    owner.setGameMode(GameType.SURVIVAL);
    var slime = ModEntities.SADDLE_SLIME.get().create(h.getLevel());
    var pos = Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 7, 2)));
    owner.moveTo(pos);
    slime.moveTo(pos);
    slime.tame(owner);
    slime.setNoAi(true);
    slime.setEquipment(0, new ItemStack(Items.SADDLE));
    slime.setEquipment(1, new ItemStack(Items.ELYTRA));
    h.assertTrue(slime.mount(owner), "Owned mount accepts its rider");
    return new Ride(h, owner, slime);
  }

  @GameTest(template = "empty", batch = "order_eleven")
  public static void ordinaryFlightIsSlowerAndHasAnExplicitCruiseLimit(GameTestHelper h) {
    try (var r = ride(h)) {
      var speed = r.speed(.3, false);
      h.assertTrue(speed > .23 && speed < .25, "Ordinary flight stays near 4.8 blocks per second");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_eleven")
  public static void normalUpgradesHaveDiminishingFlightReturns(GameTestHelper h) {
    try (var r = ride(h)) {
      var base = r.speed(.3, false);
      var upgraded = r.speed(.6, false);
      h.assertTrue(
          upgraded > base && upgraded < base * 1.5,
          "Doubling movement improves flight without doubling velocity");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_eleven")
  public static void maximumTuningCannotProduceExtremeFlight(GameTestHelper h) {
    try (var r = ride(h)) {
      var normal = r.speed(10, false);
      var boost = r.speed(10, true);
      h.assertTrue(
          normal <= .450001 && boost <= .787501 && boost > normal * 1.7,
          "Tuned flight remains bounded while Ctrl remains faster");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_eleven")
  public static void zeroMovementCannotLaunchFromGroundOrAir(GameTestHelper h) {
    try (var r = ride(h)) {
      h.assertTrue(
          r.speed(0, false) == 0 && r.speed(0, true) == 0,
          "Zero Movement Speed produces no cruise or boost thrust");
      r.slime.setOnGround(true);
      r.slime.setRiderInput(r.owner, true, true);
      r.slime.tick();
      h.assertTrue(
          r.slime.getDeltaMovement().equals(Vec3.ZERO),
          "Takeoff uses the same bounded movement stat");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_eleven")
  public static void diagonalFlightCannotExceedTheBoostLimit(GameTestHelper h) {
    try (var r = ride(h)) {
      r.speed(10, true);
      r.owner.xxa = 1;
      r.owner.zza = 1;
      r.owner.setXRot(-40);
      r.slime.setRiderInput(r.owner, true, true);
      r.slime.tick();
      h.assertTrue(
          r.slime.getDeltaMovement().length() <= .787501,
          "Steering and ascent share one total speed limit");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_eleven")
  public static void speedAdjustmentPreservesFlightAndBoostStaminaCosts(GameTestHelper h) {
    try (var r = ride(h)) {
      r.speed(.3, false);
      var normal = r.slime.getStamina();
      r.speed(.3, true);
      var boost = r.slime.getStamina();
      h.assertTrue(
          Math.abs(normal - 99.9) < .001 && Math.abs(boost - 99.25) < .001,
          "Ordinary and boosted flight retain server-owned costs");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_eleven")
  public static void mountCanWearSaddleWingsAndArmorTogether(GameTestHelper h) {
    try (var r = ride(h)) {
      var armor = SlimeArmorItem.fromChestplate(new ItemStack(Items.DIAMOND_CHESTPLATE));
      r.owner.setItemInHand(InteractionHand.MAIN_HAND, armor.copy());
      r.slime.mobInteract(r.owner, InteractionHand.MAIN_HAND);
      h.assertTrue(
          r.slime.hasWings()
              && r.slime.isSaddled()
              && r.slime.getEquipment(2).getItem() instanceof SlimeArmorItem
              && r.owner.getMainHandItem().isEmpty(),
          "Armor equips without replacing the saddle, wings or rider");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_eleven")
  public static void armorAddsNativeDefenseAndWearsWithoutDamagingWings(GameTestHelper h) {
    try (var r = ride(h)) {
      var armor = SlimeArmorItem.fromChestplate(new ItemStack(Items.DIAMOND_CHESTPLATE));
      armor.setDamageValue(7);
      armor.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 3);
      r.slime.setEquipment(2, armor);
      r.slime.tick();
      var bare = ModEntities.SADDLE_SLIME.get().create(h.getLevel());
      bare.tame(r.owner);
      bare.setNoAi(true);
      bare.moveTo(r.slime.position());
      var attacker = net.minecraft.world.entity.EntityType.ZOMBIE.create(h.getLevel());
      var source = h.getLevel().damageSources().mobAttack(attacker);
      h.assertTrue(
          r.slime.getAttributeValue(Attributes.ARMOR) >= 8, "Native chest armor attributes apply");
      bare.hurt(source, 8);
      r.slime.hurt(source, 8);
      h.assertTrue(
          r.slime.getHealth() > bare.getHealth()
              && r.slime.getEquipment(2).getDamageValue() == 9
              && r.slime.getEquipment(1).getDamageValue() == 0,
          "Armor and enchantments mitigate damage, with separate armor durability");
      bare.discard();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_eleven")
  public static void removingArmorRestoresDefenseWithoutRemovingWings(GameTestHelper h) {
    try (var r = ride(h)) {
      r.slime.setEquipment(
          2, SlimeArmorItem.fromChestplate(new ItemStack(Items.DIAMOND_CHESTPLATE)));
      r.slime.tick();
      r.slime.setEquipment(2, ItemStack.EMPTY);
      r.slime.tick();
      h.assertTrue(
          r.slime.hasWings() && r.slime.getAttributeValue(Attributes.ARMOR) == 0,
          "Removing armor removes its attributes and retains flight equipment");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_eleven")
  public static void allArmorMaterialsKeepExactMetadataThroughRecovery(GameTestHelper h) {
    for (var item :
        List.of(
            Items.LEATHER_CHESTPLATE,
            Items.CHAINMAIL_CHESTPLATE,
            Items.IRON_CHESTPLATE,
            Items.GOLDEN_CHESTPLATE,
            Items.DIAMOND_CHESTPLATE,
            Items.NETHERITE_CHESTPLATE)) {
      try (var r = ride(h)) {
        var chest = new ItemStack(item);
        chest.setDamageValue(11);
        chest.enchant(Enchantments.UNBREAKING, 2);
        chest.setHoverName(Component.literal("Travel armor"));
        if (item instanceof DyeableLeatherItem leather) leather.setColor(chest, 0x24a5cd);
        var armor = SlimeArmorItem.fromChestplate(chest);
        r.slime.setEquipment(2, armor);
        r.slime.setTunedVitals(7, 63, 9);
        var data = FriendlySlimeItem.fromSlime(r.slime).getTag().getCompound("SlimeData");
        var restored = ModEntities.SADDLE_SLIME.get().create(h.getLevel());
        restored.load(data);
        h.assertTrue(
            ItemStack.matches(restored.getEquipment(2), armor)
                && restored.hasWings()
                && restored.isSaddled()
                && restored.getHealth() == 7
                && restored.getStamina() == 63
                && restored.getStatPoints() == 9
                && !restored.isVehicle(),
            "All three equipment items and companion traits survive recovery: " + item);
        restored.discard();
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_eleven")
  public static void invalidExternalMovementModifierCannotCreateInvalidFlight(GameTestHelper h) {
    try (var r = ride(h)) {
      r.slime
          .getAttribute(Attributes.MOVEMENT_SPEED)
          .addTransientModifier(
              new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                  UUID.randomUUID(),
                  "Invalid external modifier",
                  Double.NaN,
                  net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
      h.assertTrue(
          r.speed(.3, true) == 0,
          "A non-finite external attribute produces zero thrust instead of invalid vehicle motion");
    }
    h.succeed();
  }

  private static CompoundTag legacy(Ride r, ItemStack offhand) {
    var old = r.slime.saveWithoutId(new CompoundTag());
    old.remove("SaddleEquipmentVersion");
    var armor = old.getList("ArmorItems", Tag.TAG_COMPOUND);
    armor.set(2, r.slime.getEquipment(1).save(new CompoundTag()));
    var hands = old.getList("HandItems", Tag.TAG_COMPOUND);
    hands.set(1, offhand.save(new CompoundTag()));
    return old;
  }

  @GameTest(template = "empty", batch = "order_eleven")
  public static void oldChestWingsMigrateWithoutLosingDurabilityOrEnchantments(GameTestHelper h) {
    try (var r = ride(h)) {
      var wings = r.slime.getEquipment(1);
      wings.setDamageValue(51);
      wings.enchant(Enchantments.MENDING, 1);
      wings.setHoverName(Component.literal("Old wings"));
      var restored = ModEntities.SADDLE_SLIME.get().create(h.getLevel());
      restored.load(legacy(r, ItemStack.EMPTY));
      h.assertTrue(
          ItemStack.matches(restored.getEquipment(1), wings)
              && restored.getEquipment(2).isEmpty()
              && restored.hasWings(),
          "Legacy chest wings move to the independent wing slot intact");
      restored.discard();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_eleven")
  public static void legacyUnexpectedOffhandIsPreservedDuringWingMigration(GameTestHelper h) {
    try (var r = ride(h)) {
      var old = new ItemStack(Items.STICK, 3);
      old.setHoverName(Component.literal("Preserved legacy item"));
      var restored = ModEntities.SADDLE_SLIME.get().create(h.getLevel());
      restored.load(legacy(r, old));
      h.assertTrue(
          restored.hasWings() && ItemStack.matches(restored.getEquipment(2), old),
          "A nonstandard old offhand is preserved for retrieval rather than overwritten");
      restored.discard();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_eleven")
  public static void migratedEquipmentDoesNotSwapAgainOnLaterLoads(GameTestHelper h) {
    try (var r = ride(h)) {
      var data = legacy(r, ItemStack.EMPTY);
      for (int i = 0; i < 3; i++) {
        var restored = ModEntities.SADDLE_SLIME.get().create(h.getLevel());
        restored.load(data);
        h.assertTrue(
            restored.hasWings() && restored.getEquipment(2).isEmpty(),
            "Wing migration is idempotent");
        data = restored.saveWithoutId(new CompoundTag());
        h.assertTrue(
            data.getInt("SaddleEquipmentVersion") == 1,
            "New equipment layout is explicitly marked");
        restored.discard();
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_eleven")
  public static void armorMenuTransfersExactItemsWithoutAcceptingCargo(GameTestHelper h) {
    try (var r = ride(h)) {
      r.owner.stopRiding();
      var menu = TestMenus.create(1, r.owner.getInventory(), r.slime);
      var armor = SlimeArmorItem.fromChestplate(new ItemStack(Items.IRON_CHESTPLATE));
      armor.setDamageValue(23);
      r.owner.getInventory().setItem(9, armor.copy());
      r.owner.getInventory().setItem(10, new ItemStack(Items.DIAMOND, 4));
      h.assertTrue(
          menu.slots.size() == 39
              && !menu.quickMoveStack(r.owner, 3).isEmpty()
              && ItemStack.matches(r.slime.getEquipment(2), armor),
          "Third equipment slot receives exact armor");
      h.assertTrue(
          menu.quickMoveStack(r.owner, 4).isEmpty()
              && r.owner.getInventory().getItem(10).getCount() == 4,
          "Mount has no hidden cargo bag");
      h.assertTrue(
          !menu.quickMoveStack(r.owner, 2).isEmpty()
              && r.slime.hasWings()
              && r.slime.getEquipment(2).isEmpty(),
          "Armor can be withdrawn independently");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_eleven")
  public static void ordinaryChestplatesCannotReplaceSlimeArmorOrWings(GameTestHelper h) {
    try (var r = ride(h)) {
      var menu = TestMenus.create(1, r.owner.getInventory(), r.slime);
      h.assertTrue(
          !menu.slots.get(2).mayPlace(new ItemStack(Items.DIAMOND_CHESTPLATE))
              && !menu.slots.get(2).mayPlace(new ItemStack(Items.ELYTRA))
              && !menu.slots
                  .get(1)
                  .mayPlace(SlimeArmorItem.fromChestplate(new ItemStack(Items.IRON_CHESTPLATE))),
          "Wing and converted-armor slots remain distinct");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_eleven")
  public static void spectatorOwnerCannotEquipOrMountByDirectInteraction(GameTestHelper h) {
    try (var r = ride(h)) {
      r.owner.stopRiding();
      ((FakePlayer) r.owner).setGameMode(GameType.SPECTATOR);
      r.owner.setItemInHand(
          InteractionHand.MAIN_HAND,
          SlimeArmorItem.fromChestplate(new ItemStack(Items.IRON_CHESTPLATE)));
      r.slime.mobInteract(r.owner, InteractionHand.MAIN_HAND);
      h.assertTrue(
          r.slime.getEquipment(2).isEmpty()
              && !r.owner.getMainHandItem().isEmpty()
              && !r.slime.isVehicle(),
          "Spectator interaction cannot mutate equipment or ride");
    }
    h.succeed();
  }
}
