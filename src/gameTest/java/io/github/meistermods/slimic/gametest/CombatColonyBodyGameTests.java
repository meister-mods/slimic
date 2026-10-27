package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.menu.CreativeSlimeTunerMenu;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModItems;
import io.github.meistermods.slimic.world.ColonyBondingData;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatColonyBodyGameTests {
  private record Fixture(
      LoadedWorkArea area, FakePlayer owner, BoundCombatSlime root, ColonyBondingData ledger) {}

  private static Fixture fixture(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var level = h.getLevel();
    var owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "LinkedBlade"));
    var root = ModEntities.BOUND_COMBAT_SLIME.get().create(level);
    root.tame(owner);
    ColonyTestFixtures.appearance(root);
    root.moveTo(area.point(8, 101, 8));
    root.setNoAi(true);
    root.setTunedVitals(7, 41, 3);
    root.getBag().setStackInSlot(0, new ItemStack(Items.EMERALD, 17));
    owner.moveTo(root.position().add(0, 0, -2));
    return new Fixture(area, owner, root, ColonyBondingData.get(level));
  }

  private static void spawn(GameTestHelper h, Fixture f) {
    var binding = new ColonyBondingData.Binding();
    binding.id = UUID.randomUUID();
    binding.owner = f.owner.getUUID();
    binding.representative = f.root.getUUID();
    binding.first = f.root.members().records().get(0).getUUID();
    binding.second = f.root.members().records().get(1).getUUID();
    h.assertTrue(
        f.ledger.commit(h.getLevel(), binding, f.root), "The native ledger controls both members");
    f.ledger.admitting(f.root.getUUID(), true);
    try {
      h.assertTrue(
          h.getLevel().addFreshEntity(f.root) && f.ledger.released(h.getLevel(), f.root),
          "The representative is admitted with its current colony epoch");
    } finally {
      f.ledger.admitting(f.root.getUUID(), false);
    }
    f.root.onReleased();
    f.root.setWaiting(true);
  }

  private static CombatColonyBody linked(GameTestHelper h, Fixture f) {
    var body = f.root.getLinkedBody();
    h.assertTrue(
        body != null && body.getColony() == f.root,
        "One admitted body links to the actual representative");
    h.assertTrue(
        !body.getBoundingBox().intersects(f.root.getBoundingBox()),
        "The second body has its own initial space rather than spawning over the root");
    return body;
  }

  @GameTest(template = "empty", batch = "combat_colony_body", timeoutTicks = 500)
  public static void bodyInteractionUsesSharedStateAndPhysicalManagementAccess(GameTestHelper h) {
    var f = fixture(h);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(f.root.blockPosition()), "Arena ticks"))
        .thenExecute(() -> spawn(h, f))
        .thenWaitUntil(() -> linked(h, f))
        .thenExecute(
            () -> {
              try {
                var body = linked(h, f);
                body.setNoAi(true);
                double offset = Math.min(4, f.root.getLinkRange() * .5);
                body.moveTo(f.root.position().add(offset, 0, 0));
                f.owner.moveTo(body.position().add(7.5, 0, 0));
                f.owner.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
                var menu = new SlimeMenu(7, f.owner.getInventory(), f.root);
                h.assertTrue(
                    f.owner.distanceToSqr(f.root) > 64
                        && menu.stillValid(f.owner)
                        && f.root.rename(f.owner, "Linked cargo"),
                    "Management and rename use the nearby linked body even outside root reach");
                var outsider =
                    new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "OtherBody"));
                outsider.moveTo(body.position());
                outsider.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(Items.SLIME_BALL, 2));
                h.assertTrue(
                    outsider.interactOn(body, InteractionHand.MAIN_HAND) == InteractionResult.FAIL
                        && outsider.getMainHandItem().getCount() == 2
                        && f.root.getHealth() == 7,
                    "A body cannot grant an unrelated player permission to modify shared life");
                f.owner.moveTo(body.position().add(9, 0, 0));
                h.assertTrue(
                    !menu.stillValid(f.owner)
                        && f.owner.interactOn(body, InteractionHand.MAIN_HAND)
                            == InteractionResult.FAIL,
                    "Stale management and distant direct interaction fail against both bodies");
                f.owner.moveTo(body.position().add(1, 0, 0));
                f.owner.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(Items.SLIME_BALL, 2));
                int[] rootInteractions = {0};
                Object protection =
                    new Object() {
                      @net.minecraftforge.eventbus.api.SubscribeEvent(
                          priority = net.minecraftforge.eventbus.api.EventPriority.LOW)
                      public void protectCombatBody(
                          net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract
                              event) {
                        if (event.getTarget() == body) {
                          event.setCancellationResult(InteractionResult.FAIL);
                          event.setCanceled(true);
                        }
                      }

                      @net.minecraftforge.eventbus.api.SubscribeEvent
                      public void countCombatRoot(
                          net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract
                              event) {
                        if (event.getTarget() == f.root) rootInteractions[0]++;
                      }
                    };
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(protection);
                try {
                  h.assertTrue(
                      f.owner.interactOn(body, InteractionHand.MAIN_HAND) == InteractionResult.FAIL
                          && f.root.getHealth() == 7
                          && f.owner.getMainHandItem().getCount() == 2
                          && rootInteractions[0] == 0,
                      "A LOW-priority protection veto on the actual body prevents forwarding,"
                          + " healing and item consumption");
                } finally {
                  net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(protection);
                }
                h.assertTrue(
                    f.owner.interactOn(body, InteractionHand.MAIN_HAND).consumesAction()
                        && f.root.getHealth() == 11
                        && f.owner.getMainHandItem().getCount() == 1,
                    "Feeding either body heals the shared pool and consumes exactly one ball");
                f.owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.RED_DYE, 2));
                h.assertTrue(
                    f.owner.interactOn(body, InteractionHand.MAIN_HAND).consumesAction()
                        && f.root.getDyeColorId() == DyeColor.RED.getId()
                        && f.owner.getMainHandItem().getCount() == 1,
                    "Dye changes canonical colony appearance once");
                f.owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NAME_TAG));
                f.owner
                    .getMainHandItem()
                    .setHoverName(net.minecraft.network.chat.Component.literal("Shared name"));
                h.assertTrue(
                    f.owner.interactOn(body, InteractionHand.MAIN_HAND).consumesAction()
                        && f.root.getName().getString().equals("Shared name")
                        && body.getName().getString().equals("Shared name")
                        && body.getDisplayName().getString().equals("Shared name")
                        && !body.hasCustomName(),
                    "Native held-item fallback names the persistent root rather than the temporary"
                        + " body");
                h.assertTrue(
                    body.hurt(h.getLevel().damageSources().generic(), 2)
                        && f.root.getHealth() == 9
                        && body.isAlive(),
                    "Body damage changes the single shared life pool");
                var guard = ModEntities.COMBAT_SLIME.get().create(h.getLevel());
                guard.tame(f.owner);
                guard.setAllowed(CombatTarget.PASSIVE, true);
                guard.setAllowed(CombatTarget.TAMED, true);
                h.assertTrue(
                    CombatSlime.targetKind(body) == CombatTarget.TAMED && !guard.mayAttack(body),
                    "The helper is a tamed ally even when combat filters allow passive and tamed"
                        + " targets");
                CompanionRegistry.get(h.getLevel())
                    .setRelation(f.owner.getUUID(), outsider.getUUID(), true, true);
                guard.tame(outsider);
                h.assertTrue(
                    !guard.mayAttack(body), "Shared ownership protects the linked body as well");
                var enderman = EntityType.ENDERMAN.create(h.getLevel());
                enderman.setTarget(body);
                h.assertTrue(
                    CombatSlime.targetKind(enderman) == CombatTarget.HOSTILE,
                    "An enderman attacking the linked body threatens the same companion");
                outsider.getAbilities().instabuild = true;
                outsider.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(ModItems.CREATIVE_SLIME_TUNER.get()));
                outsider.moveTo(body.position().add(15.5, 0, 0));
                h.assertTrue(
                    outsider.distanceToSqr(f.root) > 256
                        && new CreativeSlimeTunerMenu(8, outsider.getInventory(), f.root)
                            .stillValid(outsider),
                    "Creative tuning retains its existing physical sixteen-block range through the"
                        + " body");
              } finally {
                f.root.discard();
                f.area.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "combat_colony_body", timeoutTicks = 500)
  public static void transientBodyCannotSaveDuplicateCargoAndCaptureRetiresBothBodies(
      GameTestHelper h) {
    var f = fixture(h);
    CombatColonyBody[] originalBody = {null};
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(f.root.blockPosition()), "Arena ticks"))
        .thenExecute(() -> spawn(h, f))
        .thenWaitUntil(() -> linked(h, f))
        .thenExecute(
            () -> {
              var body = linked(h, f);
              originalBody[0] = body;
              h.assertTrue(
                  f.root.isOrderedToSit() && body.getNavigation().isDone(),
                  "Initial deployment leaves both bodies waiting");
              h.assertTrue(
                  !body.shouldBeSaved()
                      && !body.save(new CompoundTag())
                      && !body.saveAsPassenger(new CompoundTag())
                      && body.getPickResult().isEmpty()
                      && !body.canChangeDimensions()
                      && !body.canBeLeashed(f.owner)
                      && !body.canTakeItem(new ItemStack(Items.IRON_CHESTPLATE))
                      && !body.getCapability(
                              net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER)
                          .isPresent()
                      && !body.startRiding(f.root, true),
                  "The helper cannot become saved cargo, a picked item, a portal traveler or a"
                      + " mounted pet");
              var passenger = EntityType.PIG.create(h.getLevel());
              h.assertTrue(!passenger.startRiding(body), "The helper cannot carry passengers");
              h.assertTrue(
                  !body.getUUID().equals(f.root.getUUID())
                      && f.root.members().records().stream()
                          .noneMatch(member -> member.getUUID().equals(body.getUUID())),
                  "Its transient UUID never impersonates either original member or the"
                      + " representative");
              var orphan = ModEntities.COMBAT_COLONY_BODY.get().create(h.getLevel());
              orphan.moveTo(body.position());
              orphan.setColony(f.root);
              h.assertTrue(
                  h.getLevel().addFreshEntity(orphan),
                  "A duplicate helper candidate reaches native admission");
              orphan.tick();
              h.assertTrue(
                  orphan.isRemoved() && body.getColony() == f.root,
                  "Only the representative's exact linked body survives; a duplicate has no"
                      + " authority");
              body.discard();
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    f.root.getLinkedBody() != null
                        && f.root.getLinkedBody() != originalBody[0]
                        && f.root.getLinkedBody().getColony() == f.root,
                    "A lost transient body is reconstructed without replacing the colony"))
        .thenExecute(
            () -> {
              try {
                var body = linked(h, f);
                f.owner.moveTo(body.position());
                f.owner.setShiftKeyDown(true);
                f.owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD));
                float captureHealth = f.root.getHealth(), captureStamina = f.root.getStamina();
                long capturePoints = f.root.getStatPoints();
                InteractionResult[] nestedCapture = {null};
                boolean[] rootAlreadyRemoved = {false};
                java.util.function.Consumer<net.minecraftforge.event.entity.EntityLeaveLevelEvent>
                    recapture =
                        event -> {
                          if (event.getEntity() == body && nestedCapture[0] == null) {
                            rootAlreadyRemoved[0] = f.root.isRemoved();
                            nestedCapture[0] = InteractionResult.PASS;
                            nestedCapture[0] = f.root.capture(f.owner);
                          }
                        };
                InteractionResult captureResult;
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(recapture);
                try {
                  captureResult = f.owner.interactOn(body, InteractionHand.MAIN_HAND);
                } finally {
                  net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(recapture);
                }
                h.assertTrue(
                    rootAlreadyRemoved[0] && nestedCapture[0] == InteractionResult.FAIL,
                    "A linked-body leave callback sees the retired root and cannot capture it"
                        + " again");
                h.assertTrue(
                    captureResult.consumesAction() && f.root.isRemoved() && body.isRemoved(),
                    "Shift interaction on the second body captures exactly the root and retires"
                        + " both bodies");
                int captured = 0;
                for (var item : f.owner.getInventory().items) {
                  if (!item.hasTag()) continue;
                  var saved = item.getTag().getCompound("SlimeData");
                  if (!saved.hasUUID("UUID") || !saved.getUUID("UUID").equals(f.root.getUUID()))
                    continue;
                  captured += item.getCount();
                  var restored = ModEntities.BOUND_COMBAT_SLIME.get().create(h.getLevel());
                  restored.load(saved.copy());
                  h.assertTrue(
                      restored.members().ready()
                          && f.ledger.mayRestore(saved)
                          && restored.getBag().getStackInSlot(0).getCount() == 17
                          && restored.getHealth() == captureHealth
                          && restored.getStamina() == captureStamina
                          && restored.getStatPoints() == capturePoints
                          && restored.getLinkedBody() == null,
                      "One recovery item preserves both members and shared cargo without"
                          + " serializing its helper: ready="
                          + restored.members().ready()
                          + " cargo="
                          + restored.getBag().getStackInSlot(0).getCount()
                          + " health="
                          + restored.getHealth()
                          + "/before="
                          + captureHealth
                          + " stamina="
                          + restored.getStamina()
                          + "/before="
                          + captureStamina
                          + " points="
                          + restored.getStatPoints()
                          + "/before="
                          + capturePoints
                          + " helper="
                          + restored.getLinkedBody()
                          + " rootTick="
                          + f.root.tickCount);
                }
                h.assertTrue(
                    captured == 1
                        && f.owner.getMainHandItem().is(Items.LEAD)
                        && CompanionRegistry.get(h.getLevel()).find(f.root.getUUID()).location()
                            == CompanionRegistry.Location.ITEM,
                    "The native capture issues one item, keeps the held lead and retains the ITEM"
                        + " registry state");
              } finally {
                f.root.discard();
                f.area.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "combat_colony_body", timeoutTicks = 500)
  public static void cancelledBodyAdmissionLeavesTheRootAndRetriesWithoutDuplicateCargo(
      GameTestHelper h) {
    var f = fixture(h);
    h.startSequence()
        .thenExecute(
            () -> {
              spawn(h, f);
              var before = f.root.members().save();
              var token = f.root.getPersistentData().getUUID("ColonyToken");
              int[] rejected = {0};
              boolean[] blockedInteraction = {false}, blockedDamage = {false};
              java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent>
                  veto =
                      event -> {
                        if (event.getLevel() == h.getLevel()
                            && event.getEntity() instanceof CombatColonyBody body
                            && body.distanceToSqr(f.root) < 256) {
                          rejected[0]++;
                          blockedInteraction[0] =
                              body.interactWithColony(f.owner, InteractionHand.MAIN_HAND)
                                  == InteractionResult.FAIL;
                          blockedDamage[0] = !body.hurt(h.getLevel().damageSources().generic(), 3);
                          event.setCanceled(true);
                        }
                      };
              net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(veto);
              try {
                h.getLevel().tickNonPassenger(f.root);
                h.assertTrue(
                    rejected[0] == 1 && blockedInteraction[0] && blockedDamage[0],
                    "A provisional second body grants no interaction or damage authority before"
                        + " rejected admission");
                h.assertTrue(
                    h.getLevel().getEntity(f.root.getUUID()) == f.root
                        && f.root.isAlive()
                        && f.root.getLinkedBody() == null
                        && f.root.isOrderedToSit()
                        && before.equals(f.root.members().save())
                        && token.equals(f.root.getPersistentData().getUUID("ColonyToken"))
                        && f.owner.getInventory().isEmpty(),
                    "Rejecting the transient body leaves exactly the root, member bags, epoch and"
                        + " waiting order untouched");
              } finally {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(veto);
              }
            })
        .thenWaitUntil(() -> linked(h, f))
        .thenExecute(
            () -> {
              try {
                var body = linked(h, f);
                h.assertTrue(
                    h.getLevel()
                                .getEntitiesOfClass(
                                    CombatColonyBody.class,
                                    f.root.getBoundingBox().inflate(f.root.getLinkRange()),
                                    candidate -> candidate.getColony() == f.root)
                                .size()
                            == 1
                        && body.isAlive()
                        && f.root.getBag().getStackInSlot(0).getCount() == 17
                        && f.ledger.mayJoin(f.root)
                        && f.owner.getInventory().isEmpty(),
                    "A later permitted admission creates one helper without issuing items or"
                        + " duplicating cargo");
              } finally {
                f.root.discard();
                f.area.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "combat_colony_body", timeoutTicks = 500)
  public static void rootUnloadAndReloadReconstructOneBodyWithoutResettingSavedWork(
      GameTestHelper h) {
    var f = fixture(h);
    CompoundTag[] saved = {null};
    BoundCombatSlime[] restored = {null};
    UUID[] originalBody = {null};
    UUID[] members = {
      f.root.members().records().get(0).getUUID(), f.root.members().records().get(1).getUUID()
    };
    h.startSequence()
        .thenExecute(() -> spawn(h, f))
        .thenWaitUntil(() -> linked(h, f))
        .thenExecute(
            () -> {
              var body = linked(h, f);
              h.assertTrue(
                  f.root.isOrderedToSit() && !f.root.isWorking(),
                  "A newly deployed colony initially waits with its second body");
              originalBody[0] = body.getUUID();
              f.root.setWorking(true);
              saved[0] = f.root.saveWithoutId(new CompoundTag());
              f.root.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
              h.assertTrue(
                  body.isRemoved() && f.root.getLinkedBody() == null,
                  "Native root chunk-unload removal immediately retires its transient body");
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getEntity(f.root.getUUID()) == null,
                    "The old root has left the native entity lookup"))
        .thenExecute(
            () -> {
              restored[0] = ModEntities.BOUND_COMBAT_SLIME.get().create(h.getLevel());
              restored[0].load(saved[0].copy());
              h.assertTrue(
                  restored[0].getLinkedBody() == null
                      && restored[0].isWorking()
                      && !restored[0].isOrderedToSit()
                      && f.ledger.mayJoin(restored[0]),
                  "Loading an existing world record retains the working order and current epoch"
                      + " without loading a saved helper");
              h.assertTrue(
                  h.getLevel().addFreshEntity(restored[0]),
                  "The saved representative returns through native entity admission");
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    restored[0].getLinkedBody() != null
                        && restored[0].getLinkedBody().getColony() == restored[0],
                    "The reloaded root reconstructs its one linked body"))
        .thenExecute(
            () -> {
              try {
                var root = restored[0];
                var body = root.getLinkedBody();
                h.assertTrue(
                    !body.getUUID().equals(originalBody[0])
                        && h.getLevel()
                                .getEntitiesOfClass(
                                    CombatColonyBody.class,
                                    root.getBoundingBox().inflate(root.getLinkRange()),
                                    candidate -> candidate.getColony() == root)
                                .size()
                            == 1
                        && root.getUUID().equals(f.root.getUUID())
                        && root.members().records().get(0).getUUID().equals(members[0])
                        && root.members().records().get(1).getUUID().equals(members[1])
                        && root.getBag().getStackInSlot(0).getCount() == 17
                        && root.getHealth() == saved[0].getFloat("Health")
                        && root.getStatPoints() == 3
                        && root.isWorking()
                        && !root.isOrderedToSit()
                        && f.ledger.mayJoin(root),
                    "Only the disposable body identity changes; the root, two originals, canonical"
                        + " cargo and saved work survive");
                h.assertTrue(
                    CompanionRegistry.get(h.getLevel()).find(body.getUUID()) == null
                        && CompanionRegistry.get(h.getLevel()).find(root.getUUID()) != null,
                    "The body is never a second owned companion in the registry");
              } finally {
                if (restored[0] != null) restored[0].discard();
                f.root.discard();
                f.area.close();
              }
            })
        .thenSucceed();
  }
}
