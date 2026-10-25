package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.CompanionRegistry;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModItems;
import io.github.meistermods.slimic.world.ColonyBondingData;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionDeathAdmissionGameTests {
  @GameTest(template = "empty", batch = "companion_death_admission", timeoutTicks = 400)
  public static void rejectedDeathRecoveryKeepsTheCompanionUntilASuccessfulRetry(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var level = h.getLevel();
    var point = area.point(8, 101, 8);
    var owner = h.makeMockSurvivalPlayer();
    owner.moveTo(point.add(0, 0, -2));
    var pet = ModEntities.MINING_SLIME.get().create(level);
    pet.tame(owner);
    pet.setNoAi(true);
    pet.moveTo(point);
    pet.setTunedVitals(7, 41, 3);
    pet.getBag().setStackInSlot(0, new ItemStack(Items.EMERALD, 17));
    boolean[] reject = {true}, leadAfterRemoval = {false};
    int[] attempts = {0};
    Consumer<EntityJoinLevelEvent> admission =
        event -> {
          if (event.getLevel() != level
              || !(event.getEntity() instanceof ItemEntity item)
              || item.position().distanceToSqr(point) >= 4) return;
          if (item.getItem().is(ModItems.BROKEN_SLIME.get())) {
            attempts[0]++;
            if (reject[0]) event.setCanceled(true);
          }
          if (item.getItem().is(Items.LEAD)) leadAfterRemoval[0] = pet.isRemoved();
        };
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    level.isPositionEntityTicking(BlockPos.containing(point)), "Arena ticks"))
        .thenExecute(
            () -> {
              try {
                h.assertTrue(level.addFreshEntity(pet), "Original companion is registered");
                pet.onReleased();
                pet.setLeashedTo(owner, true);
                level.setBlockAndUpdate(
                    BlockPos.containing(point), Blocks.STONE.defaultBlockState());
                MinecraftForge.EVENT_BUS.addListener(admission);
                pet.hurt(level.damageSources().genericKill(), 10000);
                h.assertTrue(
                    attempts[0] == 1
                        && level.getEntity(pet.getUUID()) == pet
                        && !pet.isRemoved()
                        && pet.getHealth() == 1,
                    "Rejected death recovery retains the same living companion at one health");
                h.assertTrue(
                    pet.isLeashed()
                        && pet.getStamina() == 41
                        && pet.getStatPoints() == 3
                        && pet.getBag().getStackInSlot(0).getCount() == 17
                        && level.getBlockState(BlockPos.containing(point)).is(Blocks.STONE)
                        && owner.getInventory().isEmpty(),
                    "The rejected destination changes no leash, cargo, growth, block or inventory");
                h.assertTrue(
                    CompanionRegistry.get(level).find(pet.getUUID()).location()
                            == CompanionRegistry.Location.WORLD
                        && CompanionRegistry.get(level).find(pet.getUUID()).health() == 1,
                    "The registry retains the living original");
                reject[0] = false;
                pet.invulnerableTime = 0;
                pet.hurt(level.damageSources().genericKill(), 10000);
                var drops =
                    level.getEntitiesOfClass(
                        ItemEntity.class,
                        new AABB(BlockPos.containing(point)).inflate(3),
                        item -> item.getItem().is(ModItems.BROKEN_SLIME.get()));
                h.assertTrue(
                    attempts[0] == 2
                        && pet.isRemoved()
                        && level.getEntity(pet.getUUID()) == null
                        && drops.size() == 1
                        && drops.get(0).getItem().getCount() == 1,
                    "A permitted retry replaces the original with exactly one recovery item");
                var data =
                    drops
                        .get(0)
                        .getItem()
                        .getTag()
                        .getCompound("BlockEntityTag")
                        .getCompound("Slime")
                        .getCompound("tag")
                        .getCompound("SlimeData");
                var restored = ModEntities.MINING_SLIME.get().create(level);
                restored.load(data.copy());
                h.assertTrue(
                    data.getUUID("UUID").equals(pet.getUUID())
                        && restored.getHealth() == 0
                        && restored.getStamina() == 41
                        && restored.getStatPoints() == 3
                        && restored.getBag().getStackInSlot(0).getCount() == 17,
                    "The sole broken item contains the same damaged identity and full saved state");
                h.assertTrue(
                    leadAfterRemoval[0]
                        && CompanionRegistry.get(level).find(pet.getUUID()).location()
                            == CompanionRegistry.Location.BROKEN,
                    "The original is removed before lead callbacks and its registry remains"
                        + " broken");
              } finally {
                MinecraftForge.EVENT_BUS.unregister(admission);
                pet.discard();
                level
                    .getEntitiesOfClass(
                        ItemEntity.class, new AABB(BlockPos.containing(point)).inflate(3))
                    .forEach(ItemEntity::discard);
                area.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "companion_death_admission", timeoutTicks = 400)
  public static void rejectedColonyDeathRecoveryRetainsItsDurableStationFallback(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var level = h.getLevel();
    var point = area.point(8, 101, 8);
    var owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "DeathRecovery"));
    var pet = ModEntities.BOUND_TRANSPORT_SLIME.get().create(level);
    pet.tame(owner);
    ColonyTestFixtures.appearance(pet);
    pet.moveTo(point);
    pet.setNoAi(true);
    pet.setTunedVitals(7, 41, 3);
    pet.getBag().setStackInSlot(0, new ItemStack(Items.EMERALD, 17));
    var ledger = ColonyBondingData.get(level);
    var binding = new ColonyBondingData.Binding();
    binding.id = UUID.randomUUID();
    binding.owner = owner.getUUID();
    binding.representative = pet.getUUID();
    binding.first = pet.members().records().get(0).getUUID();
    binding.second = pet.members().records().get(1).getUUID();
    int[] rejected = {0};
    Consumer<EntityJoinLevelEvent> admission =
        event -> {
          if (event.getLevel() == level
              && event.getEntity() instanceof ItemEntity item
              && item.getItem().is(ModItems.BROKEN_SLIME.get())
              && item.position().distanceToSqr(point) < 4) {
            rejected[0]++;
            event.setCanceled(true);
          }
        };
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    level.isPositionEntityTicking(BlockPos.containing(point)), "Arena ticks"))
        .thenExecute(
            () -> {
              try {
                h.assertTrue(
                    ledger.commit(level, binding, pet),
                    "The real binding ledger controls both members");
                ledger.admitting(pet.getUUID(), true);
                h.assertTrue(
                    level.addFreshEntity(pet) && ledger.released(level, pet),
                    "The colony enters the world with an authoritative epoch");
                ledger.admitting(pet.getUUID(), false);
                pet.onReleased();
                level.setBlockAndUpdate(
                    BlockPos.containing(point), Blocks.STONE.defaultBlockState());
                MinecraftForge.EVENT_BUS.addListener(admission);
                pet.hurt(level.damageSources().genericKill(), 10000);
                h.assertTrue(
                    rejected[0] == 1 && pet.isRemoved() && level.getEntity(pet.getUUID()) == null,
                    "A rejected physical drop leaves no live colony once the durable damaged"
                        + " fallback exists");
                var fallback = ledger.recovery().get(pet.getUUID());
                h.assertTrue(
                    fallback != null
                        && ledger.mayRestore(fallback.getTag().getCompound("SlimeData"))
                        && fallback.getTag().getCompound("SlimeData").getFloat("Health") == 0,
                    "The durable fallback retains the current epoch and damaged state");
                h.assertTrue(
                    ledger.recover(owner), "The station can deliver the rejected damaged colony");
                int recovered = 0;
                for (var item : owner.getInventory().items) {
                  if (!item.is(ModItems.BROKEN_SLIME.get())) continue;
                  recovered += item.getCount();
                  var saved =
                      item.getTag()
                          .getCompound("BlockEntityTag")
                          .getCompound("Slime")
                          .getCompound("tag")
                          .getCompound("SlimeData");
                  var restored = ModEntities.BOUND_TRANSPORT_SLIME.get().create(level);
                  restored.load(saved.copy());
                  h.assertTrue(
                      saved.getUUID("UUID").equals(pet.getUUID())
                          && ledger.mayRestore(saved)
                          && restored.getHealth() == 0
                          && restored.getStamina() == 41
                          && restored.getStatPoints() == 3
                          && restored.getBag().getStackInSlot(0).getCount() == 17,
                      "Station recovery preserves the single current colony identity, members and"
                          + " cargo");
                }
                h.assertTrue(
                    recovered == 1 && !ledger.recovery().containsKey(pet.getUUID()),
                    "The fallback is delivered once and then acknowledged");
              } finally {
                MinecraftForge.EVENT_BUS.unregister(admission);
                ledger.admitting(pet.getUUID(), false);
                pet.discard();
                area.close();
              }
            })
        .thenSucceed();
  }
}
