package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.*;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.*;
import io.github.meistermods.slimic.menu.*;
import io.github.meistermods.slimic.network.CommanderPackets;
import io.github.meistermods.slimic.registry.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OrderThirteenGameTests {
  private static net.minecraft.server.level.ServerPlayer owner(GameTestHelper h) {
    var id = UUID.randomUUID();
    var player =
        new net.minecraft.server.level.ServerPlayer(
            h.getLevel().getServer(),
            h.getLevel(),
            new GameProfile(id, "Owner" + id.toString().substring(0, 8)));
    var connection =
        new net.minecraft.network.Connection(
            net.minecraft.network.protocol.PacketFlow.SERVERBOUND) {
          @Override
          public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
    player.connection =
        new net.minecraft.server.network.ServerGamePacketListenerImpl(
            h.getLevel().getServer(), connection, player);
    player.setGameMode(GameType.SURVIVAL);
    return player;
  }

  private static <T extends FriendlySlime> T pet(
      GameTestHelper h, LoadedWorkArea a, EntityType<T> type, Player owner, int x, int z) {
    var pet = type.create(h.getLevel());
    pet.tame(owner);
    pet.moveTo(a.point(x, 101, z));
    h.getLevel().addFreshEntity(pet);
    pet.onReleased();
    if (pet instanceof EnderSlime) owner.moveTo(pet.position().add(2, 0, 0));
    return pet;
  }

  private static void ready(GameTestHelper h, LoadedWorkArea a) {
    h.assertTrue(
        h.getLevel().isPositionEntityTicking(BlockPos.containing(a.point(7, 101, 7))),
        "Arena is entity-ticking");
  }

  private static FriendlySlime release(
      GameTestHelper h, Player owner, ItemStack item, BlockPos floor) {
    owner.setItemInHand(InteractionHand.MAIN_HAND, item);
    var used =
        ((FriendlySlimeItem) item.getItem())
            .useOn(
                new UseOnContext(
                    owner,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(
                        Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false)));
    h.assertTrue(used.consumesAction(), "Native summon succeeds");
    var pets =
        h.getLevel()
            .getEntitiesOfClass(
                FriendlySlime.class,
                new AABB(floor.above()).inflate(.25),
                e -> e.isAlive() && !e.isRemoved());
    h.assertTrue(pets.size() == 1, "Exactly one companion released");
    return pets.get(0);
  }

  private static TransientCraftingContainer grid() {
    return new TransientCraftingContainer(
        new AbstractContainerMenu(null, -1) {
          @Override
          public boolean stillValid(Player player) {
            return false;
          }

          @Override
          public ItemStack quickMoveStack(Player player, int slot) {
            return ItemStack.EMPTY;
          }
        },
        3,
        3);
  }

  @GameTest(template = "empty", batch = "order_thirteen")
  public static void newSpecializationsPreserveStoredIdentityAndInitialWait(GameTestHelper h) {
    var friendly = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    friendly.tame(owner(h));
    friendly.setWaiting(false);
    friendly.setTunedVitals(5, 37, 4);
    friendly.setCustomName(Component.literal("Same companion"));
    var original = FriendlySlimeItem.fromSlime(friendly);
    var cores = List.of(Items.GLOWSTONE_DUST, Items.BOOK, Items.ENDER_PEARL, Items.REDSTONE);
    var results =
        List.of(
            ModItems.GLOW_SLIME.get(),
            ModItems.COMMANDER_SLIME.get(),
            ModItems.ENDER_SLIME.get(),
            ModItems.ATTENTION_SLIME.get());
    for (int role = 0; role < cores.size(); role++)
      for (int first = 0; first < 9; first++)
        for (int second = 0; second < 9; second++) {
          if (first == second) continue;
          var grid = grid();
          grid.setItem(first, original.copy());
          grid.setItem(second, new ItemStack(cores.get(role)));
          var recipe =
              h.getLevel()
                  .getRecipeManager()
                  .getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel())
                  .orElseThrow();
          var result = recipe.assemble(grid, h.getLevel().registryAccess());
          var data = result.getTag().getCompound("SlimeData");
          h.assertTrue(
              result.is(results.get(role))
                  && data.getBoolean("Sitting")
                  && data.getUUID("UUID").equals(friendly.getUUID())
                  && data.getFloat("Health") == 5
                  && ItemStack.matches(grid.getItem(first), original),
              "Recipe preserves original data and initially pauses: " + result);
        }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_thirteen")
  public static void attentionSpeedMultiplierSurvivesSpecializationTuningAndRepeatedLoads(
      GameTestHelper h) {
    var ordinary = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    ordinary.tame(owner(h));
    SlimeStat.MOVEMENT_SPEED.tune(ordinary, .4);
    var attention = ModEntities.ATTENTION_SLIME.get().create(h.getLevel());
    attention.load(ordinary.saveWithoutId(new CompoundTag()));
    for (int i = 0; i < 8; i++) {
      h.assertTrue(
          Math.abs(attention.getAttributeValue(Attributes.MOVEMENT_SPEED) - .6) < .0001,
          "Same-base speed is 1.5 times, with no stacked modifier");
      attention.load(attention.saveWithoutId(new CompoundTag()));
    }
    SlimeStat.MOVEMENT_SPEED.tune(attention, .2);
    h.assertTrue(
        Math.abs(attention.getAttributeValue(Attributes.MOVEMENT_SPEED) - .3) < .0001,
        "Tuning retains the profession multiplier");
    attention.setRoaming(true);
    attention.setWaiting(true);
    h.assertTrue(!attention.isRoaming() && !attention.isWorking(), "Wait stops attention roaming");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_thirteen", timeoutTicks = 100)
  public static void glowLightCostsNoStaminaAndDisablesWithoutRemovingAnotherSource(
      GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    h.startSequence()
        .thenWaitUntil(() -> ready(h, a))
        .thenExecute(
            () -> {
              var glow = pet(h, a, ModEntities.GLOW_SLIME.get(), owner(h), 7, 7);
              glow.setNoAi(true);
              var pos = BlockPos.containing(glow.getX(), glow.getEyeY(), glow.getZ());
              glow.tick();
              glow.tick();
              h.assertTrue(
                  h.getLevel().getBlockState(pos).is(ModBlocks.SLIME_LIGHT.get())
                      && glow.getStamina() == 100
                      && glow.isOrderedToSit(),
                  "Waiting glow creates torch-strength light without stamina or work");
              var other = UUID.randomUUID();
              SlimeLightBlock.illuminate(h.getLevel(), pos, other, 80, false);
              glow.setActive(false);
              h.assertTrue(
                  h.getLevel().getBlockState(pos).is(ModBlocks.SLIME_LIGHT.get()),
                  "Disabling one source preserves another lease");
              ((SlimeLightBlockEntity) h.getLevel().getBlockEntity(pos)).release(other);
              h.assertTrue(
                  h.getLevel().getBlockState(pos).isAir()
                      && !h.getLevel().getBlockState(pos).is(ModBlocks.SLIME_LIGHT.get()),
                  "Final lease removes light");
              glow.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_thirteen", timeoutTicks = 180)
  public static void firefliesConsumeStaminaIlluminateAndExpire(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var pets = new ArrayList<GlowSlime>();
    h.startSequence()
        .thenWaitUntil(() -> ready(h, a))
        .thenExecute(
            () -> {
              var glow = pet(h, a, ModEntities.GLOW_SLIME.get(), owner(h), 7, 7);
              pets.add(glow);
              glow.setActive(false);
              glow.setNoAi(true);
              h.assertTrue(
                  glow.releaseFireflies() && glow.getStamina() == 90,
                  "A firefly burst spends exactly ten stamina");
              h.assertTrue(
                  !glow.releaseFireflies() && glow.getStamina() == 90,
                  "Immediate repeated burst is rejected without charging");
            })
        .thenIdle(20)
        .thenExecute(
            () -> {
              var glow = pets.get(0);
              boolean lit = false;
              for (var p :
                  BlockPos.betweenClosed(
                      glow.blockPosition().offset(-3, 0, -3), glow.blockPosition().offset(3, 3, 3)))
                if (h.getLevel().getBlockState(p).is(ModBlocks.SLIME_LIGHT.get()))
                  lit |= h.getLevel().getBrightness(LightLayer.BLOCK, p) == 14;
              h.assertTrue(lit, "Temporary fireflies produce real torch-level block light");
            })
        .thenIdle(110)
        .thenExecute(
            () -> {
              var glow = pets.get(0);
              for (var p :
                  BlockPos.betweenClosed(
                      glow.blockPosition().offset(-3, 0, -3), glow.blockPosition().offset(3, 3, 3)))
                h.assertTrue(
                    !h.getLevel().getBlockState(p).is(ModBlocks.SLIME_LIGHT.get()),
                    "Expired fireflies leave no light node");
              glow.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_thirteen", timeoutTicks = 100)
  public static void temporaryLightsPreserveFluidsContainersAndPlayerReplacement(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var p = BlockPos.containing(a.point(7, 101, 7));
    var id = UUID.randomUUID();
    h.getLevel().setBlockAndUpdate(p, Blocks.CHEST.defaultBlockState());
    h.assertTrue(
        !SlimeLightBlock.illuminate(h.getLevel(), p, id, 10, false), "Never replace a container");
    h.getLevel().setBlockAndUpdate(p, Blocks.WATER.defaultBlockState());
    h.assertTrue(
        !SlimeLightBlock.illuminate(h.getLevel(), p, id, 10, false), "Never replace water");
    h.getLevel().setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
    h.assertTrue(SlimeLightBlock.illuminate(h.getLevel(), p, id, 10, true), "Air accepts light");
    h.getLevel().setBlockAndUpdate(p, Blocks.DIAMOND_BLOCK.defaultBlockState());
    h.runAfterDelay(
        30,
        () -> {
          h.assertTrue(
              h.getLevel().getBlockState(p).is(Blocks.DIAMOND_BLOCK),
              "Old expiry cannot remove a player's replacement block");
          a.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_thirteen", timeoutTicks = 180)
  public static void expiredFirefliesDoNotPersistOnSharedOrdinaryLight(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var p = BlockPos.containing(a.point(7, 101, 7));
    SlimeLightBlock.illuminate(h.getLevel(), p, UUID.randomUUID(), 170, false);
    SlimeLightBlock.illuminate(h.getLevel(), p, UUID.randomUUID(), 30, true);
    h.runAfterDelay(
        50,
        () -> {
          h.assertTrue(
              h.getLevel().getBlockState(p).is(ModBlocks.SLIME_LIGHT.get())
                  && !h.getLevel().getBlockState(p).getValue(SlimeLightBlock.FIREFLY),
              "Only ordinary illumination survives after the firefly deadline");
          a.close();
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "order_thirteen")
  public static void registryRetainsTagsHealthAndRelationshipsAcrossNbt(GameTestHelper h) {
    var registry = CompanionRegistry.get(h.getLevel());
    var owner = owner(h);
    var shared = owner(h);
    var slime = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    slime.tame(owner);
    slime.setHealth(7);
    registry.observe(slime);
    h.assertTrue(
        registry.setTag(owner.getUUID(), slime.getUUID(), "  QUARRY  "), "Assign a normalized tag");
    h.assertTrue(
        !registry.setTag(shared.getUUID(), slime.getUUID(), "foreign"),
        "Foreign primary owner cannot edit the record");
    h.assertTrue(
        registry.setRelation(owner.getUUID(), shared.getUUID(), true, true),
        "Grant delegated access");
    var copy = CompanionRegistry.load(registry.save(new CompoundTag()));
    var entry = copy.find(slime.getUUID());
    h.assertTrue(
        entry.tag().equals("quarry")
            && entry.health() == 7
            && copy.owns(owner.getUUID(), shared.getUUID()),
        "Identity, tag, health and delegation persist");
    registry.mark(slime, CompanionRegistry.Location.ITEM);
    registry.observeStored(FriendlySlimeItem.fromSlime(slime), h.getLevel(), new BlockPos(2, 3, 4));
    h.assertTrue(
        registry.find(slime.getUUID()).tag().equals("quarry")
            && registry.find(slime.getUUID()).location() == CompanionRegistry.Location.ITEM,
        "Stored observation retains authoritative tag");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_thirteen")
  public static void itemObservationCannotHideAKnownLiveCompanion(GameTestHelper h) {
    var registry = CompanionRegistry.get(h.getLevel());
    var slime = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    slime.tame(owner(h));
    registry.observe(slime);
    var before = registry.find(slime.getUUID());
    registry.observeStored(
        FriendlySlimeItem.fromSlime(slime), h.getLevel(), new BlockPos(99, 100, 99));
    h.assertTrue(
        before.equals(registry.find(slime.getUUID())),
        "A copied item never replaces a known live observation");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_thirteen", timeoutTicks = 80)
  public static void sharedOwnersManageEveryTypeAndRevocationInvalidatesMenus(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    h.startSequence()
        .thenWaitUntil(() -> ready(h, a))
        .thenExecute(
            () -> {
              var primary = owner(h);
              var shared = owner(h);
              var registry = CompanionRegistry.get(h.getLevel());
              registry.setRelation(primary.getUUID(), shared.getUUID(), true, true);
              int count = 0;
              for (var value : ModEntities.ENTITY_TYPES.getEntries()) {
                var type = value.get();
                var slime = (FriendlySlime) type.create(h.getLevel());
                slime.tame(primary);
                slime.moveTo(a.point(7, 101, 7));
                slime.refreshOwnerAccess();
                shared.moveTo(slime.position());
                var menu = TestMenus.create(2, shared.getInventory(), slime);
                h.assertTrue(
                    menu.stillValid(shared)
                        && menu.rename(shared, "Shared companion")
                        && menu.clickMenuButton(shared, SlimeMenu.FOLLOW),
                    "Shared owner can manage " + type);
                registry.setRelation(primary.getUUID(), shared.getUUID(), true, false);
                h.assertTrue(
                    !menu.stillValid(shared) && !menu.clickMenuButton(shared, SlimeMenu.WAIT),
                    "Revocation closes authority immediately");
                registry.setRelation(primary.getUUID(), shared.getUUID(), true, true);
                count++;
              }
              h.assertTrue(count == 22, "Permission coverage includes all twenty-two types");
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_thirteen", timeoutTicks = 80)
  public static void sharedCaptureAndRevivalKeepPrimaryOwnership(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    h.startSequence()
        .thenWaitUntil(() -> ready(h, a))
        .thenExecute(
            () -> {
              var primary = owner(h);
              var shared = owner(h);
              var registry = CompanionRegistry.get(h.getLevel());
              registry.setRelation(primary.getUUID(), shared.getUUID(), true, true);
              var slime = pet(h, a, ModEntities.FRIENDLY_SLIME.get(), primary, 7, 7);
              slime.setTunedVitals(6, 31, 2);
              var id = slime.getUUID();
              shared.moveTo(slime.position());
              h.assertTrue(slime.capture(shared).consumesAction(), "Shared owner can recover");
              var item = shared.getInventory().getItem(0).copy();
              var restored = release(h, shared, item, BlockPos.containing(a.point(7, 100, 7)));
              h.assertTrue(
                  restored.getUUID().equals(id)
                      && restored.getOwnerUUID().equals(primary.getUUID())
                      && restored.getHealth() == 6,
                  "Delegated recovery preserves the primary owner and exact health");
              restored.setHealth(0);
              var pos = restored.blockPosition();
              BrokenSlimeBlock.preserve(restored);
              shared.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SLIME_BALL));
              h.assertTrue(
                  BrokenSlimeBlock.revive(h.getLevel(), pos, shared), "Shared owner can revive");
              var revived = (FriendlySlime) h.getLevel().getEntity(id);
              h.assertTrue(
                  revived != null
                      && revived.getHealth() == 1
                      && revived.getOwnerUUID().equals(primary.getUUID()),
                  "Revival retains identity with one health");
              revived.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_thirteen")
  public static void declaredEnemiesRespectAlliesFiltersAndPvp(GameTestHelper h) {
    var primary = owner(h);
    var target = owner(h);
    var registry = CompanionRegistry.get(h.getLevel());
    var fighter = ModEntities.COMBAT_SLIME.get().create(h.getLevel());
    fighter.tame(primary);
    h.assertTrue(!fighter.mayAttack(target), "Unmarked players are excluded by default");
    registry.setRelation(primary.getUUID(), target.getUUID(), false, true);
    h.assertTrue(
        fighter.mayAttack(target) == h.getLevel().getServer().isPvpAllowed(),
        "An enemy is hostile while PvP still applies");
    fighter.setAllowed(CombatTarget.HOSTILE, false);
    h.assertTrue(!fighter.mayAttack(target), "Hostile filter still applies");
    fighter.setAllowed(CombatTarget.HOSTILE, true);
    registry.setRelation(primary.getUUID(), target.getUUID(), true, true);
    h.assertTrue(
        !registry.enemy(primary.getUUID(), target.getUUID()) && !fighter.mayAttack(target),
        "Shared access removes hostility and protects allies");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_thirteen", timeoutTicks = 180)
  public static void gatherPausesTaggedWorkersAndPreservesTheirResources(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var source = new LoadedWorkArea(h);
    var pets = new ArrayList<FriendlySlime>();
    var ids = new ArrayList<UUID>();
    h.startSequence()
        .thenWaitUntil(
            () -> {
              ready(h, a);
              ready(h, source);
            })
        .thenExecute(
            () -> {
              var owner = owner(h);
              var commander = pet(h, a, ModEntities.COMMANDER_SLIME.get(), owner, 7, 7);
              commander.setNoAi(true);
              pets.add(commander);
              var miner = pet(h, source, ModEntities.MINING_SLIME.get(), owner, 3, 3);
              miner.setNoAi(true);
              miner.setTunedVitals(7, 37, 3);
              miner.setTool(0, new ItemStack(Items.IRON_PICKAXE));
              miner.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 9));
              miner.setWorking(true);
              var registry = CompanionRegistry.get(h.getLevel());
              registry.observe(miner);
              registry.setTag(owner.getUUID(), miner.getUUID(), "ore");
              ids.add(miner.getUUID());
              var untargeted = pet(h, a, ModEntities.FRIENDLY_SLIME.get(), owner, 13, 13);
              pets.add(untargeted);
              registry.setTag(owner.getUUID(), untargeted.getUUID(), "other");
              h.assertTrue(
                  commander.gather(owner, "ORE") && commander.getStamina() == 75,
                  "Gather spends exactly 25 stamina on the selected tag");
            })
        .thenWaitUntil(
            () -> h.assertTrue(!((CommanderSlime) pets.get(0)).isGathering(), "Rally completes"))
        .thenExecute(
            () -> {
              var commander = (CommanderSlime) pets.get(0);
              var miner = (MiningSlime) h.getLevel().getEntity(ids.get(0));
              h.assertTrue(
                  commander.gathered() == 1
                      && miner != null
                      && miner.isOrderedToSit()
                      && !miner.isWorking()
                      && miner.getHealth() == 7
                      && miner.getStatPoints() == 3
                      && miner.getTool(0).is(Items.IRON_PICKAXE)
                      && miner.getBag().getStackInSlot(0).getCount() == 9,
                  "Tagged miner arrives waiting with exact cargo, tool, health and growth");
              h.assertTrue(
                  pets.get(1).distanceToSqr(a.point(13, 101, 13)) < .1,
                  "A different tag stays untouched");
              miner.discard();
              pets.forEach(Entity::discard);
              a.close();
              source.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_thirteen", timeoutTicks = 240)
  public static void gatherLeavesEveryProfessionWaitingAfterSubsequentTicks(GameTestHelper h) {
    var destination = new LoadedWorkArea(h);
    var source = new LoadedWorkArea(h);
    var companions = new ArrayList<FriendlySlime>();
    var owner = owner(h);
    var commander = pet(h, destination, ModEntities.COMMANDER_SLIME.get(), owner, 7, 7);
    commander.setNoAi(true);
    var registry = CompanionRegistry.get(h.getLevel());
    for (var registered : ModEntities.ENTITY_TYPES.getEntries()) {
      var slime = (FriendlySlime) registered.get().create(h.getLevel());
      slime.tame(owner);
      slime.setNoAi(true);
      slime.moveTo(source.point(3, 101, 3));
      h.getLevel().addFreshEntity(slime);
      slime.onReleased();
      slime.setWaiting(false);
      if (slime instanceof BagSlime worker) {
        worker.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 9));
        worker.setWorking(true);
      }
      if (slime instanceof HeraldSlime herald) herald.setMonitoring(true);
      if (slime instanceof SoggySlime soggy) soggy.setRoaming(true);
      if (slime instanceof MeowSlime meow) meow.setRoaming(true);
      if (slime instanceof AttentionSlime attention) attention.setRoaming(true);
      slime.setTunedVitals(7, 37, 3);
      registry.observe(slime);
      registry.setTag(owner.getUUID(), slime.getUUID(), "waiting");
      companions.add(slime);
    }
    h.assertTrue(companions.size() == 22, "All companion professions are covered");
    h.assertTrue(commander.gather(owner, "waiting"), "Gather every active profession");
    h.startSequence()
        .thenWaitUntil(() -> h.assertTrue(!commander.isGathering(), "All companions arrive"))
        .thenIdle(20)
        .thenExecute(
            () -> {
              h.assertTrue(commander.gathered() == 22, "Every profession was gathered");
              for (var slime : companions) {
                var label = EntityType.getKey(slime.getType()).toString();
                h.assertTrue(
                    slime.isOrderedToSit()
                        && !slime.isWorking()
                        && slime.getWorkStatus().order() == WorkStatus.Order.WAIT
                        && slime.getWorkStatus().requests().isEmpty()
                        && slime.distanceToSqr(commander) < 400,
                    label + " remains waiting without work or support requests after arrival");
                var saved = slime.saveWithoutId(new CompoundTag());
                var restored = (FriendlySlime) slime.getType().create(h.getLevel());
                restored.load(saved);
                h.assertTrue(
                    restored.isOrderedToSit()
                        && !restored.isWorking()
                        && restored.getWorkStatus().order() == WorkStatus.Order.WAIT
                        && restored.getUUID().equals(slime.getUUID())
                        && restored.getHealth() == 7
                        && restored.getStatPoints() == 3,
                    label + " retains identity, health, growth and Wait when restored");
                if (restored instanceof BagSlime worker)
                  h.assertTrue(
                      worker.getBag().getStackInSlot(0).is(Items.DIAMOND)
                          && worker.getBag().getStackInSlot(0).getCount() == 9,
                      label + " retains its cargo");
                h.assertTrue(
                    registry.find(slime.getUUID()).order().equals("WAIT"),
                    label + " also reports Wait in the commander roster");
              }
              companions.forEach(Entity::discard);
              commander.discard();
              owner.discard();
              source.close();
              destination.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_thirteen", timeoutTicks = 100)
  public static void gatheredCommanderCancelsItsOwnPendingGather(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = owner(h);
    var caller = pet(h, a, ModEntities.COMMANDER_SLIME.get(), owner, 7, 7);
    var arriving = pet(h, a, ModEntities.COMMANDER_SLIME.get(), owner, 3, 3);
    caller.setNoAi(true);
    arriving.setNoAi(true);
    var registry = CompanionRegistry.get(h.getLevel());
    // A last-known world observation whose entity is unavailable keeps the source ticket pending.
    var unavailable = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    unavailable.tame(owner);
    unavailable.moveTo(a.point(13, 101, 13));
    registry.observe(unavailable);
    registry.setTag(owner.getUUID(), unavailable.getUUID(), "pending");
    registry.setTag(owner.getUUID(), arriving.getUUID(), "arriving");
    h.assertTrue(arriving.gather(owner, "pending"), "The arriving commander has a pending rally");
    var tickets =
        h.getLevel()
            .getDataStorage()
            .get(net.minecraft.world.level.ForcedChunksSavedData::load, "chunks");
    int sourceTickets = tickets.getEntityForcedChunks().getTickingChunks().size();
    arriving.tick();
    h.assertTrue(
        arriving.isGathering()
            && tickets.getEntityForcedChunks().getTickingChunks().size() == sourceTickets + 1,
        "Unavailable source keeps exactly one temporary rally ticket pending");
    h.assertTrue(caller.gather(owner, "arriving"), "The caller gathers the other commander");
    caller.tick();
    h.assertTrue(
        caller.gathered() == 1
            && arriving.isOrderedToSit()
            && arriving.getWorkStatus().order() == WorkStatus.Order.WAIT
            && !arriving.isGathering()
            && tickets.getEntityForcedChunks().getTickingChunks().size() == sourceTickets,
        "A gathered commander waits, cancels its previous rally, and releases its source ticket");
    h.startSequence()
        .thenIdle(20)
        .thenExecute(
            () -> {
              h.assertTrue(!arriving.isGathering(), "The cancelled rally does not resume");
              arriving.setTunedVitals(7, 100, 3);
              for (int i = 0; i < 100; i++) arriving.tick();
              h.assertTrue(
                  arriving.gather(owner, "pending"), "A new explicit rally remains available");
              arriving.setWaiting(true);
              h.assertTrue(!arriving.isGathering(), "Explicit Wait cancels a rally too");
              caller.discard();
              arriving.discard();
              owner.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_thirteen", timeoutTicks = 100)
  public static void gatherExcludesStoredBrokenAndForeignRecords(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    h.startSequence()
        .thenWaitUntil(() -> ready(h, a))
        .thenExecute(
            () -> {
              var owner = owner(h);
              var commander = pet(h, a, ModEntities.COMMANDER_SLIME.get(), owner, 7, 7);
              var registry = CompanionRegistry.get(h.getLevel());
              for (var state :
                  List.of(
                      CompanionRegistry.Location.ITEM,
                      CompanionRegistry.Location.BROKEN,
                      CompanionRegistry.Location.REMOVED)) {
                var slime = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
                slime.tame(owner);
                registry.mark(slime, state);
              }
              var foreign = pet(h, a, ModEntities.FRIENDLY_SLIME.get(), owner(h), 3, 3);
              h.assertTrue(
                  !commander.gather(owner, "") && commander.getStamina() == 100,
                  "No eligible companions means no stamina charge");
              foreign.discard();
              commander.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_thirteen", timeoutTicks = 100)
  public static void cancelledGatherTeleportPreservesCompanionIdentityAndPosition(
      GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var pets = new ArrayList<FriendlySlime>();
    var listener =
        new java.util.function.Consumer<EntityTeleportEvent>() {
          public void accept(EntityTeleportEvent e) {
            if (pets.size() > 1 && e.getEntity() == pets.get(1)) e.setCanceled(true);
          }
        };
    h.startSequence()
        .thenWaitUntil(() -> ready(h, a))
        .thenExecute(
            () -> {
              var owner = owner(h);
              var commander = pet(h, a, ModEntities.COMMANDER_SLIME.get(), owner, 7, 7);
              pets.add(commander);
              var slime = pet(h, a, ModEntities.FRIENDLY_SLIME.get(), owner, 3, 3);
              slime.setNoAi(true);
              slime.setWaiting(false);
              pets.add(slime);
              MinecraftForge.EVENT_BUS.addListener(listener);
              h.assertTrue(commander.gather(owner, ""), "Issue a valid gather");
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    !((CommanderSlime) pets.get(0)).isGathering(), "Cancelled entry finishes"))
        .thenExecute(
            () -> {
              try {
                h.assertTrue(
                    ((CommanderSlime) pets.get(0)).gathered() == 0
                        && pets.get(1).distanceToSqr(a.point(3, 101, 3)) < .1
                        && !pets.get(1).isOrderedToSit()
                        && pets.get(1).getWorkStatus().order() == WorkStatus.Order.FOLLOW,
                    "Cancelled teleport preserves the source position and Follow order");
              } finally {
                MinecraftForge.EVENT_BUS.unregister(listener);
                pets.forEach(Entity::discard);
                a.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_thirteen", timeoutTicks = 100)
  public static void enderLinkRequiresFullStaminaAndStoresIdentity(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    h.startSequence()
        .thenWaitUntil(() -> ready(h, a))
        .thenExecute(
            () -> {
              var owner = owner(h);
              var slime = pet(h, a, ModEntities.ENDER_SLIME.get(), owner, 7, 7);
              slime.setTunedVitals(9, 99, 2);
              h.assertTrue(
                  !slime.bind(owner)
                      && !slime.isPinned()
                      && owner.getInventory().getItem(0).isEmpty(),
                  "Partial stamina cannot issue a token");
              slime.setTunedVitals(9, 100, 2);
              h.assertTrue(
                  slime.bind(owner) && slime.isPinned() && slime.getStamina() == 0,
                  "Full charge links and drains exactly once");
              var token = owner.getInventory().getItem(0);
              h.assertTrue(
                  token.is(ModItems.SLIME_TELEPORTER.get())
                      && token.getTag().getUUID("Companion").equals(slime.getUUID()),
                  "Token stores endpoint identity");
              slime.setWaiting(false);
              h.assertTrue(!slime.isPinned(), "Follow releases pinning");
              slime.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_thirteen", timeoutTicks = 100)
  public static void enderTeleportUsesFullDestinationEnergyAndRejectsForeignOwners(
      GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    h.startSequence()
        .thenWaitUntil(() -> ready(h, a))
        .thenExecute(
            () -> {
              var owner = owner(h);
              owner.moveTo(a.point(2, 101, 2));
              var slime = pet(h, a, ModEntities.ENDER_SLIME.get(), owner, 9, 9);
              h.assertTrue(slime.bind(owner), "Bind endpoint");
              var token = owner.getInventory().getItem(0).copy();
              var before = owner.position();
              h.assertTrue(
                  !SlimeTeleporterItem.teleport(owner, token) && owner.position().equals(before),
                  "An exhausted endpoint cannot teleport");
              slime.setTunedVitals(8, 100, 0);
              var foreign = owner(h);
              foreign.moveTo(a.point(2, 101, 2));
              h.assertTrue(
                  !SlimeTeleporterItem.teleport(foreign, token) && slime.getStamina() == 100,
                  "Foreign token use cannot spend endpoint energy");
              h.assertTrue(
                  SlimeTeleporterItem.teleport(owner, token)
                      && slime.getStamina() == 0
                      && owner.distanceToSqr(slime) < 400
                      && owner.fallDistance == 0,
                  "Authorized full-energy teleport arrives safely and drains the endpoint");
              slime.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_thirteen", timeoutTicks = 100)
  public static void enderRecoveryRebasesPinnedDestinationWithoutChangingIdentity(
      GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    h.startSequence()
        .thenWaitUntil(() -> ready(h, a))
        .thenExecute(
            () -> {
              var owner = owner(h);
              var slime = pet(h, a, ModEntities.ENDER_SLIME.get(), owner, 3, 3);
              slime.bind(owner);
              slime.setTunedVitals(7, 100, 3);
              var id = slime.getUUID();
              var token = owner.getInventory().getItem(0).copy();
              var stored = FriendlySlimeItem.fromSlime(slime);
              slime.discard();
              var restored =
                  (EnderSlime) release(h, owner, stored, BlockPos.containing(a.point(11, 100, 11)));
              h.assertTrue(
                  restored.isPinned()
                      && restored.getUUID().equals(id)
                      && restored.getHealth() == 7
                      && restored.distanceToSqr(
                              Vec3.atBottomCenterOf(BlockPos.containing(a.point(11, 101, 11))))
                          < .0001,
                  "Stored pinned state anchors at the new location with exact identity and health");
              owner.moveTo(a.point(3, 101, 3));
              h.assertTrue(
                  SlimeTeleporterItem.teleport(owner, token) && owner.distanceToSqr(restored) < 400,
                  "Existing token follows the same restored endpoint");
              restored.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_thirteen")
  public static void rosterPacketsPreserveComponentsAndRejectOversizedPages(GameTestHelper h) {
    var row =
        new CommanderPackets.Row(
            UUID.randomUUID(),
            Component.literal("Named"),
            Component.translatable("screen.slimic.roster.loaded"),
            Component.literal("0, 100, 0"),
            "ore",
            true,
            false,
            true);
    var page = new CommanderPackets.Page(4, false, 0, 1, 75, 100, 1, 2, true, List.of(row));
    var buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
    try {
      page.encode(buffer);
      var decoded = CommanderPackets.Page.decode(buffer);
      h.assertTrue(decoded.equals(page), "Bounded snapshot round-trips all visible metadata");
    } finally {
      buffer.release();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_thirteen", timeoutTicks = 240)
  public static void attentionRoamsNearAnOnlineOwnerAndWaitStopsIt(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = owner(h);
    owner.moveTo(a.point(8, 101, 8));
    h.getLevel().addNewPlayer(owner);
    var pets = new ArrayList<AttentionSlime>();
    var distance = new double[] {0};
    h.startSequence()
        .thenWaitUntil(() -> ready(h, a))
        .thenExecute(
            () -> {
              var slime = pet(h, a, ModEntities.ATTENTION_SLIME.get(), owner, 4, 4);
              pets.add(slime);
              slime.setRoaming(true);
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    pets.get(0).distanceToSqr(a.point(4, 101, 4)) > 1,
                    "Actual roaming moves the companion"))
        .thenExecute(
            () -> {
              pets.get(0).setWaiting(true);
              pets.get(0).setDeltaMovement(Vec3.ZERO);
            })
        .thenIdle(20)
        .thenExecute(
            () -> {
              var slime = pets.get(0);
              h.assertTrue(
                  slime.isOrderedToSit() && !slime.isRoaming() && slime.getNavigation().isDone(),
                  "Wait stops the roaming goal");
              slime.discard();
              owner.discard();
              a.close();
            })
        .thenSucceed();
  }
}
