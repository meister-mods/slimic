package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WildSlimeTamingGameTests {
  private static final BlockPos FLOOR = new BlockPos(5, 1, 5);

  private WildSlimeTamingGameTests() {}

  @GameTest(template = "empty")
  public static void goldenAppleCreatesExactlyOneNamedOwnedWaitingCompanion(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var wild = wild(h, EntityType.SLIME, 1);
    wild.setCustomName(Component.literal("Golden"));
    wild.setCustomNameVisible(true);
    var position = wild.position();
    var apples = new ItemStack(Items.GOLDEN_APPLE, 3);
    owner.setItemInHand(InteractionHand.MAIN_HAND, apples);
    h.assertTrue(
        ForgeHooks.onInteractEntity(owner, wild, InteractionHand.MAIN_HAND)
            == InteractionResult.CONSUME,
        "Feeding consumes the entity interaction");
    var friend = companion(h);
    h.assertTrue(
        wild.isRemoved() && apples.getCount() == 2, "One wild slime and one apple are used");
    h.assertTrue(
        friend.isTame()
            && owner.getUUID().equals(friend.getOwnerUUID())
            && friend.isOrderedToSit()
            && !friend.isWorking()
            && friend.isPersistenceRequired(),
        "The companion belongs to its feeder and starts persistently in Wait");
    h.assertTrue(
        friend.position().equals(position)
            && friend.getName().getString().equals("Golden")
            && friend.isCustomNameVisible(),
        "Conversion retains position and custom name");
    h.assertTrue(
        ForgeHooks.onInteractEntity(owner, wild, InteractionHand.MAIN_HAND) == null
            && apples.getCount() == 2,
        "A repeated interaction with the removed wild slime spends nothing");
    companion(h);
    h.assertTrue(
        h.getEntities(EntityType.ITEM, FLOOR, 5).isEmpty(),
        "Conversion does not produce slime drops");
    friend.discard();
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void creativeOffhandFeedingPreservesBothHands(GameTestHelper h) {
    var owner = player(h, GameType.CREATIVE);
    var wild = wild(h, EntityType.SLIME, 1);
    var main = new ItemStack(Items.DIAMOND, 4);
    var apples = new ItemStack(Items.GOLDEN_APPLE, 2);
    owner.setItemInHand(InteractionHand.MAIN_HAND, main);
    owner.setItemInHand(InteractionHand.OFF_HAND, apples);
    h.assertTrue(
        ForgeHooks.onInteractEntity(owner, wild, InteractionHand.OFF_HAND).consumesAction(),
        "Creative offhand feeding succeeds");
    h.assertTrue(
        main.getCount() == 4 && apples.getCount() == 2, "Creative does not spend either hand");
    var friend = companion(h);
    h.assertTrue(
        friend.canManage(owner) && friend.isOrderedToSit(), "Creative feeder owns a waiting pet");
    friend.discard();
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void survivalOffhandFeedingWorksWithFullInventoryAndWhileSneaking(
      GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    for (int i = 0; i < owner.getInventory().items.size(); i++)
      owner.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
    owner.setShiftKeyDown(true);
    var apple = new ItemStack(Items.GOLDEN_APPLE);
    owner.setItemInHand(InteractionHand.OFF_HAND, apple);
    var wild = wild(h, EntityType.SLIME, 1);
    h.assertTrue(
        ForgeHooks.onInteractEntity(owner, wild, InteractionHand.OFF_HAND).consumesAction(),
        "Feeding creates an entity without needing a free inventory slot");
    var friend = companion(h);
    h.assertTrue(
        apple.isEmpty() && owner.getMainHandItem().getCount() == 64 && friend.isOrderedToSit(),
        "Only the offhand apple is consumed and sneaking does not immediately recover the new pet");
    h.assertTrue(
        ForgeHooks.onInteractEntity(owner, friend, InteractionHand.MAIN_HAND)
                == InteractionResult.FAIL
            && !friend.isRemoved(),
        "Normal recovery still requires a free main inventory slot");
    friend.discard();
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void largerSlimesMagmaCubesAndOtherFoodsAreIgnored(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var apples = new ItemStack(Items.GOLDEN_APPLE, 4);
    owner.setItemInHand(InteractionHand.MAIN_HAND, apples);
    for (var type : java.util.List.of(EntityType.SLIME, EntityType.MAGMA_CUBE))
      for (int size : new int[] {1, 2, 4}) {
        if (type == EntityType.SLIME && size == 1) continue;
        var wild = wild(h, type, size);
        h.assertTrue(
            ForgeHooks.onInteractEntity(owner, wild, InteractionHand.MAIN_HAND) == null
                && !wild.isRemoved()
                && apples.getCount() == 4,
            "Only size-one vanilla Slimes accept golden apples");
        wild.discard();
      }
    var wild = wild(h, EntityType.SLIME, 1);
    for (var food :
        new net.minecraft.world.item.Item[] {
          Items.APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.SLIME_BALL
        }) {
      var stack = new ItemStack(food, 2);
      owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
      h.assertTrue(
          ForgeHooks.onInteractEntity(owner, wild, InteractionHand.MAIN_HAND) == null
              && !wild.isRemoved()
              && stack.getCount() == 2,
          "Other items do not trigger conversion");
    }
    h.assertTrue(
        h.getEntities(ModEntities.FRIENDLY_SLIME.get(), FLOOR, 5).isEmpty(), "No pet is created");
    wild.discard();
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void spectatorsAndDeadOrRemovedTargetsCannotConvert(GameTestHelper h) {
    var spectator = player(h, GameType.SPECTATOR);
    spectator.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE));
    var wild = wild(h, EntityType.SLIME, 1);
    h.assertTrue(
        ForgeHooks.onInteractEntity(spectator, wild, InteractionHand.MAIN_HAND) == null
            && !wild.isRemoved()
            && spectator.getMainHandItem().getCount() == 1,
        "Spectators cannot tame");
    var owner = h.makeMockSurvivalPlayer();
    owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE));
    wild.setHealth(0);
    h.assertTrue(
        ForgeHooks.onInteractEntity(owner, wild, InteractionHand.MAIN_HAND) == null,
        "Dying targets cannot be converted");
    wild.discard();
    h.assertTrue(
        ForgeHooks.onInteractEntity(owner, wild, InteractionHand.MAIN_HAND) == null
            && owner.getMainHandItem().getCount() == 1,
        "Removed targets cannot be converted");
    h.assertTrue(
        h.getEntities(ModEntities.FRIENDLY_SLIME.get(), FLOOR, 5).isEmpty(), "No pet is created");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void canceledCompanionSpawnPreservesWildSlimeAndApple(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var wild = wild(h, EntityType.SLIME, 1);
    var apples = new ItemStack(Items.GOLDEN_APPLE, 2);
    owner.setItemInHand(InteractionHand.MAIN_HAND, apples);
    var attempted = new AtomicBoolean();
    Consumer<EntityJoinLevelEvent> cancel =
        event -> {
          if (event.getEntity() instanceof FriendlySlime friend && friend.canManage(owner)) {
            attempted.set(true);
            event.setCanceled(true);
          }
        };
    MinecraftForge.EVENT_BUS.addListener(cancel);
    try {
      h.assertTrue(
          ForgeHooks.onInteractEntity(owner, wild, InteractionHand.MAIN_HAND) == null
              && attempted.get()
              && !wild.isRemoved()
              && apples.getCount() == 2,
          "A vetoed spawn leaves the original slime and apple intact");
      h.assertTrue(
          h.getEntities(ModEntities.FRIENDLY_SLIME.get(), FLOOR, 5).isEmpty(), "No pet leaked");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(cancel);
      wild.discard();
    }
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void canceledEntityInteractionPreventsTaming(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var wild = wild(h, EntityType.SLIME, 1);
    owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE));
    Consumer<PlayerInteractEvent.EntityInteract> cancel =
        event -> {
          if (event.getTarget() == wild) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
          }
        };
    MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, cancel);
    try {
      h.assertTrue(
          ForgeHooks.onInteractEntity(owner, wild, InteractionHand.MAIN_HAND)
                  == InteractionResult.FAIL
              && !wild.isRemoved()
              && owner.getMainHandItem().getCount() == 1,
          "An earlier event veto prevents both conversion and item consumption");
    } finally {
      MinecraftForge.EVENT_BUS.unregister(cancel);
      wild.discard();
    }
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void tamedWildSlimeUsesOwnerOnlyShiftRecoveryAndRetainsTraitsOnRelease(
      GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var wild = wild(h, EntityType.SLIME, 1);
    owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE));
    ForgeHooks.onInteractEntity(owner, wild, InteractionHand.MAIN_HAND);
    var friend = companion(h);
    friend.setTunedVitals(3.5F, 42, 2);
    friend.setCustomName(Component.literal("Apple"));
    friend.setCustomNameVisible(true);
    var identity = friend.getUUID();
    h.assertTrue(
        ForgeHooks.onInteractEntity(owner, friend, InteractionHand.MAIN_HAND) == null
            && !friend.isRemoved(),
        "Ordinary right-click does not recover the companion");
    var stranger = h.makeMockSurvivalPlayer();
    stranger.setShiftKeyDown(true);
    h.assertTrue(
        ForgeHooks.onInteractEntity(stranger, friend, InteractionHand.MAIN_HAND)
                == InteractionResult.FAIL
            && !friend.isRemoved(),
        "A different player cannot recover the companion");
    owner.setShiftKeyDown(true);
    h.assertTrue(
        ForgeHooks.onInteractEntity(owner, friend, InteractionHand.MAIN_HAND).consumesAction()
            && friend.isRemoved(),
        "Owner Shift-right-click recovers the tamed wild slime");
    ItemStack stored =
        owner.getInventory().items.stream()
            .filter(s -> s.is(ModItems.FRIENDLY_SLIME.get()))
            .findFirst()
            .orElseThrow();
    owner.setItemInHand(InteractionHand.MAIN_HAND, stored);
    owner.setShiftKeyDown(false);
    var floor = h.absolutePos(FLOOR);
    h.assertTrue(
        stored
            .useOn(
                new UseOnContext(
                    owner,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(
                        Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false)))
            .consumesAction(),
        "The recovered companion can be released normally");
    var restored = companion(h);
    h.assertTrue(
        restored.getUUID().equals(identity)
            && restored.canManage(owner)
            && restored.getHealth() == 3.5F
            && restored.getStatPoints() == 2
            && restored.getName().getString().equals("Apple")
            && restored.isOrderedToSit()
            && stored.isEmpty(),
        "Release retains identity, owner, health, growth and name, starts in Wait and consumes the"
            + " item");
    restored.discard();
    h.succeed();
  }

  private static Slime wild(GameTestHelper h, EntityType<? extends Slime> type, int size) {
    h.setBlock(FLOOR, Blocks.BEDROCK);
    var wild = type.create(h.getLevel());
    wild.setSize(size, true);
    wild.setNoAi(true);
    wild.moveTo(h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.above())));
    h.assertTrue(h.getLevel().addFreshEntity(wild), "Wild fixture must spawn");
    return wild;
  }

  private static FriendlySlime companion(GameTestHelper h) {
    var pets = h.getEntities(ModEntities.FRIENDLY_SLIME.get(), FLOOR, 5);
    h.assertTrue(pets.size() == 1, "Exactly one Friendly Slime must be present");
    return pets.get(0);
  }

  private static FakePlayer player(GameTestHelper h, GameType mode) {
    var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "AppleFeeder"));
    player.setGameMode(mode);
    return player;
  }
}
