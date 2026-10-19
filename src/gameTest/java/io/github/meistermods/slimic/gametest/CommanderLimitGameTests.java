package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.*;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.*;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CommanderLimitGameTests {
  private static ServerPlayer owner(GameTestHelper h) {
    var player =
        new ServerPlayer(
            h.getLevel().getServer(),
            h.getLevel(),
            new GameProfile(UUID.randomUUID(), "CommanderOwner"));
    player.connection =
        new ServerGamePacketListenerImpl(
            player.server,
            new Connection(PacketFlow.SERVERBOUND) {
              @Override
              public void send(Packet<?> packet) {}
            },
            player);
    return player;
  }

  private static InteractionResult summon(PlayerContext c, ItemStack stack, int x, int z) {
    var floor = BlockPos.containing(c.area.point(x, 100, z));
    c.player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    return ((FriendlySlimeItem) stack.getItem())
        .useOn(
            new UseOnContext(
                c.player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                    Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false)));
  }

  private record PlayerContext(GameTestHelper helper, LoadedWorkArea area, ServerPlayer player) {
    CommanderSlime release(int x, int z) {
      var stack = new ItemStack(ModItems.COMMANDER_SLIME.get());
      helper.assertTrue(summon(this, stack, x, z).consumesAction(), "Release the first commander");
      return helper
          .getLevel()
          .getEntitiesOfClass(
              CommanderSlime.class,
              new AABB(BlockPos.containing(area.point(x, 101, z))).inflate(.25))
          .get(0);
    }
  }

  @GameTest(template = "empty", batch = "commander_limits", timeoutTicks = 100)
  public static void secondCommanderIsRejectedInSurvivalAndCreative(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var c = new PlayerContext(h, a, owner(h));
    var first = c.release(3, 3);
    var stack = new ItemStack(ModItems.COMMANDER_SLIME.get(), 2);
    h.assertTrue(
        summon(c, stack, 9, 3) == InteractionResult.FAIL && stack.getCount() == 2,
        "Survival cannot release a second commander or consume the item");
    c.player.getAbilities().instabuild = true;
    h.assertTrue(
        summon(c, stack, 9, 3) == InteractionResult.FAIL && stack.getCount() == 2,
        "Creative respects the same population limit");
    first.discard();
    var replacement = c.release(9, 3);
    replacement.discard();
    c.player.discard();
    a.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "commander_limits", timeoutTicks = 100)
  public static void indirectSharedOwnersCannotBypassTheLimitAndConflictingMergesFail(
      GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var first = new PlayerContext(h, a, owner(h));
    var second = new PlayerContext(h, a, owner(h));
    var third = new PlayerContext(h, a, owner(h));
    var bridge = owner(h);
    var registry = CompanionRegistry.get(h.getLevel());
    h.assertTrue(
        registry.setRelation(first.player.getUUID(), bridge.getUUID(), true, true)
            && registry.setRelation(second.player.getUUID(), bridge.getUUID(), true, true)
            && registry.setRelation(third.player.getUUID(), second.player.getUUID(), true, true),
        "One-sided edges and a common shared owner connect the full group");
    var commander = first.release(3, 3);
    var item = new ItemStack(ModItems.COMMANDER_SLIME.get());
    h.assertTrue(
        summon(third, item, 9, 3) == InteractionResult.FAIL && item.getCount() == 1,
        "The indirect shared owner cannot release another commander");
    h.assertTrue(
        registry.setRelation(third.player.getUUID(), second.player.getUUID(), true, false),
        "Removing the last connecting edge splits a group");
    var independent = third.release(9, 3);
    long revision = registry.relationshipRevision();
    h.assertTrue(
        !registry.setRelation(bridge.getUUID(), third.player.getUUID(), true, true)
            && registry.relationshipRevision() == revision
            && !registry.owns(bridge.getUUID(), third.player.getUUID()),
        "Merging two groups with world commanders fails without changing access");
    independent.discard();
    h.assertTrue(
        registry.setRelation(bridge.getUUID(), third.player.getUUID(), true, true),
        "The groups can merge after one commander leaves the world");
    commander.discard();
    first.player.discard();
    second.player.discard();
    third.player.discard();
    bridge.discard();
    a.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "commander_limits", timeoutTicks = 1600)
  public static void unloadedCommanderStillOccupiesItsSavedWorldSlot(GameTestHelper h) {
    var source = new LoadedWorkArea(h);
    var destination = new LoadedWorkArea(h);
    var player = owner(h);
    var c = new PlayerContext(h, source, player);
    var commander = c.release(3, 3);
    commander.setNoAi(true);
    var id = commander.getUUID();
    var center = commander.blockPosition();
    player.moveTo(destination.point(7, 101, 7));
    source.close();
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    !h.getLevel().hasChunkAt(center) && h.getLevel().getEntity(id) == null,
                    "The commander's source actually unloads"))
        .thenExecute(
            () -> {
              var copy =
                  CompanionRegistry.load(
                      CompanionRegistry.get(h.getLevel()).save(new CompoundTag()));
              h.assertTrue(
                  !copy.reserveCommander(player.getUUID()),
                  "Population remains occupied after registry serialization");
              var stack = new ItemStack(ModItems.COMMANDER_SLIME.get());
              h.assertTrue(
                  summon(new PlayerContext(h, destination, player), stack, 3, 3)
                          == InteractionResult.FAIL
                      && stack.getCount() == 1,
                  "Unloaded commanders still block fresh releases");
              player.discard();
              destination.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "commander_limits", timeoutTicks = 100)
  public static void otherDimensionsAndOriginalOwnersOfStoredItemsShareTheLimit(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var primary = owner(h);
    var shared = new PlayerContext(h, a, owner(h));
    var registry = CompanionRegistry.get(h.getLevel());
    h.assertTrue(
        registry.setRelation(primary.getUUID(), shared.player.getUUID(), true, true),
        "Grant shared access");
    var nether = h.getLevel().getServer().getLevel(Level.NETHER);
    var distant = ModEntities.COMMANDER_SLIME.get().create(nether);
    distant.tame(primary);
    distant.moveTo(4000.5, 140, 4000.5);
    registry.observe(distant);
    var stack = new ItemStack(ModItems.COMMANDER_SLIME.get());
    h.assertTrue(
        summon(shared, stack, 3, 3) == InteractionResult.FAIL && stack.getCount() == 1,
        "A recorded commander in another dimension blocks the entire connected group");
    registry.mark(distant, CompanionRegistry.Location.ITEM);
    var stored = FriendlySlimeItem.fromSlime(distant);
    registry.setRelation(primary.getUUID(), shared.player.getUUID(), true, false);
    var independent = shared.release(9, 3);
    h.assertTrue(
        summon(shared, stored, 3, 3).consumesAction() && stored.isEmpty(),
        "A stored release counts its original owner rather than the player holding it");
    var restored = (CommanderSlime) h.getLevel().getEntity(distant.getUUID());
    h.assertTrue(
        restored != null && restored.getOwnerUUID().equals(primary.getUUID()),
        "The stored commander retains its original owner");
    restored.discard();
    independent.discard();
    primary.discard();
    shared.player.discard();
    a.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "commander_limits", timeoutTicks = 100)
  public static void blockedStoredReleaseAndRevivalPreserveAllResources(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var c = new PlayerContext(h, a, owner(h));
    var original = c.release(3, 3);
    original.setTunedVitals(7, 37, 3);
    var savedId = original.getUUID();
    var stored = FriendlySlimeItem.fromSlime(original);
    var registry = CompanionRegistry.get(h.getLevel());
    registry.mark(original, CompanionRegistry.Location.ITEM);
    original.discard();
    var active = c.release(9, 3);
    var before = stored.save(new CompoundTag());
    h.assertTrue(
        summon(c, stored, 3, 3) == InteractionResult.FAIL
            && stored.save(new CompoundTag()).equals(before),
        "Blocked restoration preserves exact item data");
    active.discard();
    h.assertTrue(
        summon(c, stored, 3, 3).consumesAction() && stored.isEmpty(),
        "A commander can be restored after the occupied slot is released");
    original = (CommanderSlime) h.getLevel().getEntity(savedId);
    h.assertTrue(
        original != null && original.getHealth() == 7 && original.getStatPoints() == 3,
        "Restoration preserves health, growth, and identity");
    var brokenPosition = original.blockPosition();
    original.setHealth(0);
    BrokenSlimeBlock.preserve(original);
    active = c.release(9, 3);
    var ball = new ItemStack(Items.SLIME_BALL, 2);
    c.player.setItemInHand(InteractionHand.MAIN_HAND, ball);
    var state = h.getLevel().getBlockState(brokenPosition);
    var block = (BrokenSlimeBlock) state.getBlock();
    var hit =
        new BlockHitResult(Vec3.atCenterOf(brokenPosition), Direction.UP, brokenPosition, false);
    var backup =
        ((BrokenSlimeBlockEntity) h.getLevel().getBlockEntity(brokenPosition))
            .getSlime()
            .save(new CompoundTag());
    h.assertTrue(
        block.use(state, h.getLevel(), brokenPosition, c.player, InteractionHand.MAIN_HAND, hit)
                == InteractionResult.FAIL
            && ball.getCount() == 2
            && ((BrokenSlimeBlockEntity) h.getLevel().getBlockEntity(brokenPosition))
                .getSlime()
                .save(new CompoundTag())
                .equals(backup),
        "Failed revival preserves the ball, block, and saved companion data");
    active.discard();
    h.assertTrue(
        block
                .use(state, h.getLevel(), brokenPosition, c.player, InteractionHand.MAIN_HAND, hit)
                .consumesAction()
            && ball.getCount() == 1,
        "Revival succeeds and consumes one ball after capacity is freed");
    var revived = (CommanderSlime) h.getLevel().getEntity(savedId);
    h.assertTrue(
        revived != null && revived.getHealth() == 1 && revived.getStatPoints() == 3,
        "Revival retains identity and growth and restores exactly one health");
    revived.discard();
    c.player.discard();
    a.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "commander_limits", timeoutTicks = 100)
  public static void reentrantSpawnAndMergingCannotBypassReservations(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var c = new PlayerContext(h, a, owner(h));
    var other = new PlayerContext(h, a, owner(h));
    var independent = other.release(12, 12);
    var registry = CompanionRegistry.get(h.getLevel());
    var outer = new ItemStack(ModItems.COMMANDER_SLIME.get());
    var nested = new ItemStack(ModItems.COMMANDER_SLIME.get());
    Consumer<EntityJoinLevelEvent> listener =
        event -> {
          if (!(event.getEntity() instanceof CommanderSlime slime)
              || !c.player.getUUID().equals(slime.getOwnerUUID())) return;
          h.assertTrue(
              summon(c, nested, 9, 3) == InteractionResult.FAIL && nested.getCount() == 1,
              "A recursive join callback cannot release another same-group commander");
          h.assertTrue(
              !registry.setRelation(c.player.getUUID(), other.player.getUUID(), true, true),
              "A join callback cannot merge two occupied groups");
          event.setCanceled(true);
        };
    MinecraftForge.EVENT_BUS.addListener(listener);
    try {
      h.assertTrue(
          summon(c, outer, 3, 3) == InteractionResult.FAIL && outer.getCount() == 1,
          "A cancelled outer spawn leaves its item intact");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(listener);
    }
    var commander = c.release(3, 3);
    h.assertTrue(
        !registry.owns(c.player.getUUID(), other.player.getUUID()),
        "The rejected merge did not grant ownership");
    commander.discard();
    independent.discard();
    c.player.discard();
    other.player.discard();
    a.close();
    h.succeed();
  }
}
