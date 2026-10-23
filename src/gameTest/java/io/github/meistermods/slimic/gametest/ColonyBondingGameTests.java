package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.ColonyBondingStationBlockEntity;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.*;
import io.github.meistermods.slimic.world.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ColonyBondingGameTests {
  @GameTest(template = "empty", batch = "colony_bonding")
  public static void nativeBagWindowIsFixedBeforeClientMemberDataArrives(GameTestHelper h) {
    var transport = ModEntities.BOUND_TRANSPORT_SLIME.get().create(h.getLevel());
    var combat = ModEntities.BOUND_COMBAT_SLIME.get().create(h.getLevel());
    int transportWindow = transport.getBag().baseSlots(),
        combatWindow = combat.getBag().baseSlots();
    h.assertTrue(
        transportWindow == 108 && combatWindow == 54,
        "Client constructor knows the fixed native slot window");
    var f = fixture(h, false);
    var prepared = (BoundTransportSlime) f.check().representative();
    transport.load(prepared.saveWithoutId(new CompoundTag()));
    h.assertTrue(
        transport.getBag().baseSlots() == transportWindow,
        "Server member records cannot change the menu slot count");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_bonding")
  public static void changedMemberIdentitiesCannotReuseAValidColonyEpoch(GameTestHelper h) {
    var f = fixture(h, false);
    h.assertTrue(
        ColonyBonding.execute(f.player, f.station, false) == ColonyBonding.Problem.COMPLETE,
        "Bind");
    CompoundTag saved = f.output().getTag().getCompound("SlimeData").copy();
    var ledger = ColonyBondingData.get(h.getLevel());
    h.assertTrue(ledger.mayRestore(saved), "Original item has current membership authority");
    saved
        .getCompound("ColonyMembers")
        .getList("Members", Tag.TAG_COMPOUND)
        .getCompound(0)
        .putUUID("UUID", UUID.randomUUID());
    h.assertTrue(
        !ledger.mayRestore(saved),
        "An unchanged release token cannot authorize substituted originals");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_bonding")
  public static void temporarySourceEffectsAreRejectedWithoutLosingTheOriginal(GameTestHelper h) {
    var f = fixture(h, false);
    var effects = new ListTag();
    effects.add(
        new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.HEALTH_BOOST, 200)
            .save(new CompoundTag()));
    f.station.getItem(0).getTag().getCompound("SlimeData").put("ActiveEffects", effects);
    var before = f.station.contents();
    h.assertTrue(
        ColonyBonding.execute(f.player, f.station, false) == ColonyBonding.Problem.EFFECTS
            && ItemStack.matches(before.get(0), f.station.getItem(0))
            && ItemStack.matches(before.get(1), f.station.getItem(1)),
        "Timed source bonuses cannot become permanent pooled attributes");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_bonding")
  public static void sharedBonusHealthAndEffectsSurviveRecoveryAndTransferOnceOnUnbinding(
      GameTestHelper h) {
    var f = fixture(h, false);
    h.assertTrue(
        ColonyBonding.execute(f.player, f.station, false) == ColonyBonding.Problem.COMPLETE,
        "Bind");
    var colony = ModEntities.BOUND_TRANSPORT_SLIME.get().create(h.getLevel());
    colony.load(f.output().getTag().getCompound("SlimeData"));
    colony.addEffect(
        new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.HEALTH_BOOST, 200, 1));
    colony.setTunedVitals(38, 73, colony.getStatPoints());
    colony.setAbsorptionAmount(3);
    var stored = FriendlySlimeItem.fromSlime(colony);
    var reloaded = ModEntities.BOUND_TRANSPORT_SLIME.get().create(h.getLevel());
    reloaded.load(stored.getTag().getCompound("SlimeData"));
    h.assertTrue(
        Math.abs(reloaded.getHealth() - 38) < .001 && reloaded.getAbsorptionAmount() == 3,
        "Temporary bonus life and absorption preserved in stored state");
    f.player.getInventory().clearContent();
    f.station.setItem(0, stored);
    h.assertTrue(
        ColonyBonding.execute(f.player, f.station, true) == ColonyBonding.Problem.COMPLETE,
        "Unbind");
    var first = (FriendlySlime) h.getLevel().getEntity(f.first);
    var second = (FriendlySlime) h.getLevel().getEntity(f.second);
    h.assertTrue(
        Math.abs(first.getHealth() + second.getHealth() - 38) < .001
            && first.getAbsorptionAmount() == 3
            && first.hasEffect(net.minecraft.world.effect.MobEffects.HEALTH_BOOST)
            && !second.hasEffect(net.minecraft.world.effect.MobEffects.HEALTH_BOOST),
        "One remaining effect and exact remaining health, without duplication");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_bonding")
  public static void dyeAndAncientAppearanceFollowTheirActualMemberRecords(GameTestHelper h) {
    var f = fixture(h, false);
    var colony = (BoundTransportSlime) f.check().representative();
    colony.setDyeColorId(DyeColor.RED.getId());
    colony.setAncientOrigin(true);
    h.assertTrue(
        colony.members().records().get(0).getDyeColorId() == DyeColor.RED.getId()
            && colony.members().records().get(0).hasAncientOrigin()
            && !colony.members().records().get(1).hasAncientOrigin(),
        "First member owns new dye and origin state");
    var reloaded = ModEntities.BOUND_TRANSPORT_SLIME.get().create(h.getLevel());
    reloaded.load(colony.saveWithoutId(new CompoundTag()));
    h.assertTrue(
        reloaded.getDyeColorId() == DyeColor.RED.getId() && reloaded.hasAncientOrigin(),
        "First appearance survives load");
    reloaded.setRenderingSecondMember(true);
    h.assertTrue(
        !reloaded.hasAncientOrigin() && reloaded.getDyeColorId() == -1,
        "Second appearance stays independent");
    reloaded.setRenderingSecondMember(false);
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_bonding")
  public static void shrinkingCreativeBagsCannotMoveTheSecondMembersCargoDuringUnloading(
      GameTestHelper h) {
    var f = fixture(h, false);
    var colony = (BoundTransportSlime) f.check().representative();
    var member = colony.members().records().get(0);
    SlimeStat.CAPACITY.tune(member, 64);
    member.getBag().setStackInSlot(100, new ItemStack(Items.EMERALD, 5));
    colony.members().project();
    SlimeStat.CAPACITY.tune(colony, 8);
    var slots = colony.getBag().occupiedSlots();
    int count = 0;
    for (int slot : slots) count += colony.getBag().extractItem(slot, 64, false).getCount();
    h.assertTrue(
        count == 69 && colony.getBag().occupiedSlots().isEmpty(),
        "23+41+5 items unload through a stable snapshot without shifted indices or losses");
    h.succeed();
  }

  private record Fixture(
      GameTestHelper h,
      ServerPlayer player,
      ColonyBondingStationBlockEntity station,
      UUID first,
      UUID second) {
    ColonyBonding.Check check() {
      return ColonyBonding.check(player, station.contents());
    }

    ItemStack output() {
      return player.getInventory().items.stream()
          .filter(ColonyBonding::bound)
          .findFirst()
          .orElse(ItemStack.EMPTY);
    }
  }

  private static Fixture fixture(GameTestHelper h, boolean combat) {
    ServerPlayer player =
        new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "ColonyStudy"));
    BlockPos pos = h.absolutePos(new BlockPos(3, 2, 3));
    for (int x = -6; x <= 6; x++)
      for (int z = -6; z <= 6; z++)
        h.getLevel().setBlockAndUpdate(pos.offset(x, -1, z), Blocks.STONE.defaultBlockState());
    player.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
    h.getLevel().setBlockAndUpdate(pos, ModBlocks.COLONY_BONDING_STATION.get().defaultBlockState());
    var station = (ColonyBondingStationBlockEntity) h.getLevel().getBlockEntity(pos);
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      BagSlime member =
          combat
              ? ModEntities.COMBAT_SLIME.get().create(h.getLevel())
              : ModEntities.TRANSPORT_SLIME.get().create(h.getLevel());
      member.tame(player);
      member.inheritGrowth(i + 3);
      member.setTunedVitals(5 + i * 2, 31 + i * 11, member.getStatPoints());
      member.setCustomName(net.minecraft.network.chat.Component.literal("Member " + i));
      member
          .getBag()
          .setStackInSlot(0, new ItemStack(i == 0 ? Items.DIAMOND : Items.GOLD_INGOT, 23 + i * 18));
      if (member instanceof CombatSlime fighter) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(17 + i);
        fighter.setEquipment(0, sword);
      }
      ids.add(member.getUUID());
      station.setItem(i, FriendlySlimeItem.fromSlime(member));
    }
    return new Fixture(h, player, station, ids.get(0), ids.get(1));
  }

  @GameTest(template = "empty", batch = "colony_bonding")
  public static void cohesionMassSurvivesRepeatedSplitsKillsReturnsAndReloads(GameTestHelper h) {
    ColonyCohesion pool = new ColonyCohesion();
    Random random = new Random(17);
    for (int tick = 0; tick < 400; tick++) {
      UUID id = UUID.randomUUID();
      int sequence = pool.reserve(id, 6);
      if (sequence >= 0 && random.nextBoolean()) pool.killed(id, sequence);
      else if (sequence >= 0) pool.absorb(List.of(id, id));
      pool = ColonyCohesion.load(pool.save());
      h.assertTrue(
          pool.free() + pool.count() * 12 + pool.lost() == 100
              && pool.count() <= 6
              && pool.free() >= 0
              && pool.healSpent() <= 48,
          "Conserved mass and finite healing after reload");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_bonding")
  public static void transportPreviewContainsActualPartitionedCargoAndVitals(GameTestHelper h) {
    var f = fixture(h, false);
    var check = f.check();
    h.assertTrue(check.problem() == ColonyBonding.Problem.READY, "Valid ordinary pair");
    var colony = (BoundTransportSlime) check.representative();
    h.assertTrue(
        colony.getCapacity() == 36 && colony.getBag().getSlots() == 36,
        "Capacity is the sum of actual member capacities");
    h.assertTrue(
        colony.getBag().getStackInSlot(0).getCount() == 23
            && colony.getBag().getStackInSlot(18).getCount() == 41,
        "Member inventories share a compact view without copied cargo");
    h.assertTrue(colony.getHealth() == 12 && colony.getStamina() == 73, "No free health or energy");
    h.assertTrue(
        colony.members().records().get(0).getUUID().equals(f.first)
            && colony.members().records().get(1).getUUID().equals(f.second),
        "Original identities retained");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_bonding")
  public static void spendingCargoAndVitalsPersistsInBothOriginalRecords(GameTestHelper h) {
    var f = fixture(h, false);
    var colony = (BoundTransportSlime) f.check().representative();
    colony.getBag().extractItem(0, 5, false);
    colony.getBag().insertItem(18, new ItemStack(Items.GOLD_INGOT, 2), false);
    colony.setHealth(6);
    colony.setTunedVitals(6, 20, colony.getStatPoints());
    CompoundTag saved = colony.saveWithoutId(new CompoundTag());
    var restored = ModEntities.BOUND_TRANSPORT_SLIME.get().create(h.getLevel());
    restored.load(saved);
    h.assertTrue(
        Math.abs(restored.getHealth() - 6) < .001 && Math.abs(restored.getStamina() - 20) < .001,
        "Actual remaining life and energy survive load");
    h.assertTrue(
        restored.getBag().getStackInSlot(0).getCount() == 18
            && restored.getBag().getStackInSlot(18).getCount() == 43,
        "Only actual cargo remains");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_bonding")
  public static void simulationDoesNotAlterCanonicalMemberCargo(GameTestHelper h) {
    var f = fixture(h, false);
    var colony = (BoundTransportSlime) f.check().representative();
    var before = colony.members().save();
    var left =
        SlimeBag.insertItemStacked(colony.getBag(), new ItemStack(Items.GOLD_INGOT, 64), true);
    h.assertTrue(
        left.isEmpty() && before.equals(colony.members().save()),
        "Simulation preserves both native records");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_bonding")
  public static void bindingRetiresParentItemsAndUnbindingRestoresOriginalEntities(
      GameTestHelper h) {
    var f = fixture(h, false);
    var before = f.station.contents();
    h.assertTrue(
        ColonyBonding.execute(f.player, f.station, false) == ColonyBonding.Problem.COMPLETE,
        "Bind committed");
    var output = f.output();
    var ledger = ColonyBondingData.get(h.getLevel());
    h.assertTrue(
        !output.isEmpty()
            && ledger.memberBound(f.first)
            && !ledger.mayRestore(before.get(0).getTag().getCompound("SlimeData")),
        "Copied parents unavailable while bound");
    ItemStack stale = output.copy();
    f.station.setItem(0, output.copy());
    f.player.getInventory().clearContent();
    h.assertTrue(
        ColonyBonding.execute(f.player, f.station, true) == ColonyBonding.Problem.COMPLETE,
        "Safe unbind committed");
    h.assertTrue(
        h.getLevel().getEntity(f.first) instanceof TransportSlime first
            && first.isOrderedToSit()
            && first.getHealth() == 5,
        "First UUID and health restored in Wait");
    h.assertTrue(
        h.getLevel().getEntity(f.second) instanceof TransportSlime second
            && second.isOrderedToSit()
            && second.getHealth() == 7,
        "Second UUID and health restored in Wait");
    h.assertTrue(
        !ledger.mayRestore(stale.getTag().getCompound("SlimeData"))
            && !ledger.mayRestore(before.get(0).getTag().getCompound("SlimeData")),
        "Retired colony and pre-binding parent copies are invalid");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_bonding")
  public static void cancelledSecondAdmissionKeepsTheColonyAndNoOriginalEntities(GameTestHelper h) {
    var f = fixture(h, false);
    h.assertTrue(
        ColonyBonding.execute(f.player, f.station, false) == ColonyBonding.Problem.COMPLETE,
        "Bind");
    var output = f.output().copy();
    f.player.getInventory().clearContent();
    f.station.setItem(0, output.copy());
    Object listener =
        new Object() {
          @SubscribeEvent
          public void join(EntityJoinLevelEvent e) {
            if (e.getEntity().getUUID().equals(f.second)) e.setCanceled(true);
          }
        };
    MinecraftForge.EVENT_BUS.register(listener);
    try {
      h.assertTrue(
          ColonyBonding.execute(f.player, f.station, true) == ColonyBonding.Problem.CANCELLED,
          "Second admission rejected");
      h.assertTrue(
          ItemStack.matches(output, f.station.getItem(0))
              && h.getLevel().getEntity(f.first) == null
              && h.getLevel().getEntity(f.second) == null,
          "Both provisional originals removed, colony unchanged");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(listener);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_bonding")
  public static void combatWeaponWearChangesTheFirstNativeRecord(GameTestHelper h) {
    var f = fixture(h, true);
    var colony = (BoundCombatSlime) f.check().representative();
    colony.getMainHandItem().setDamageValue(39);
    var saved = colony.members().save().getList("Members", Tag.TAG_COMPOUND);
    h.assertTrue(
        ItemStack.of(saved.getCompound(0).getList("HandItems", Tag.TAG_COMPOUND).getCompound(0))
                .getDamageValue()
            == 39,
        "Wear belongs to member one");
    h.assertTrue(
        ItemStack.of(saved.getCompound(1).getList("HandItems", Tag.TAG_COMPOUND).getCompound(0))
                .getDamageValue()
            == 18,
        "Member two's stored weapon is preserved");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "colony_bonding")
  public static void unsupportedPairAndDifferentPrimaryOwnerAreRejectedWithoutConsumption(
      GameTestHelper h) {
    var f = fixture(h, false);
    var before = f.station.contents();
    f.station.setItem(1, new ItemStack(ModItems.MINING_SLIME.get()));
    h.assertTrue(
        ColonyBonding.execute(f.player, f.station, false) == ColonyBonding.Problem.UNSUPPORTED,
        "Other jobs rejected");
    f.station.setItem(1, before.get(1));
    f.station.getItem(1).getTag().getCompound("SlimeData").putUUID("Owner", UUID.randomUUID());
    h.assertTrue(
        ColonyBonding.execute(f.player, f.station, false) == ColonyBonding.Problem.OWNER
            && ItemStack.matches(before.get(0), f.station.getItem(0)),
        "Primary ownership is required, originals retained");
    h.succeed();
  }
}
