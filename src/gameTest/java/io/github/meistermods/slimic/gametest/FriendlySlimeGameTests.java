package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FriendlySlimeGameTests {
  // GameTest templates are loaded one block above their structure marker.
  private static final BlockPos SPAWN_FLOOR = new BlockPos(5, 1, 5);

  private FriendlySlimeGameTests() {}

  @GameTest(template = "empty")
  public static void craftingAcceptsBothSoulBlocks(GameTestHelper helper) {
    for (Item core : new Item[] {Items.SOUL_SAND, Items.SOUL_SOIL}) {
      var grid = craftingGrid(core);
      var recipe =
          helper
              .getLevel()
              .getRecipeManager()
              .getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel());
      helper.assertTrue(recipe.isPresent(), "Both soul blocks must match the recipe");
      ItemStack result = recipe.orElseThrow().assemble(grid, helper.getLevel().registryAccess());
      helper.assertTrue(
          result.is(ModItems.FRIENDLY_SLIME.get()) && result.getCount() == 1,
          "Eight slime blocks and one soul block must produce one Friendly Slime");
    }
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void craftingRejectsMissingSlimeAndWrongCore(GameTestHelper helper) {
    var grid = craftingGrid(Items.SOUL_SAND);
    grid.setItem(0, ItemStack.EMPTY);
    helper.assertTrue(
        helper
            .getLevel()
            .getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel())
            .isEmpty(),
        "Seven slime blocks must not match");
    grid.setItem(0, new ItemStack(Items.SLIME_BLOCK));
    grid.setItem(4, new ItemStack(Items.DIRT));
    helper.assertTrue(
        helper
            .getLevel()
            .getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel())
            .isEmpty(),
        "Ordinary dirt must not replace a soul block");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void survivalUseSpawnsNamedOwnedSlime(GameTestHelper helper) {
    Player player = helper.makeMockSurvivalPlayer();
    ItemStack stack = new ItemStack(ModItems.FRIENDLY_SLIME.get(), 2);
    stack.setHoverName(Component.literal("Bouncy"));
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    helper.assertTrue(useOnFloor(helper, player).consumesAction(), "Item use must succeed");
    FriendlySlime slime = spawnedSlime(helper);
    helper.assertTrue(
        slime.isTame() && player.getUUID().equals(slime.getOwnerUUID()),
        "The spawned slime must already belong to the player");
    helper.assertTrue(slime.isPersistenceRequired(), "The companion must persist");
    helper.assertTrue(
        slime.hasCustomName() && slime.getName().getString().equals("Bouncy"),
        "An anvil name must transfer to the summoned slime");
    helper.assertTrue(stack.getCount() == 1, "A successful survival use must consume one item");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void creativeUsePreservesItem(GameTestHelper helper) {
    Player player = helper.makeMockPlayer();
    player.getAbilities().instabuild = true;
    ItemStack stack = new ItemStack(ModItems.FRIENDLY_SLIME.get());
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    helper.assertTrue(useOnFloor(helper, player).consumesAction(), "Creative use must succeed");
    helper.assertTrue(stack.getCount() == 1, "Creative use must preserve the item");
    helper.assertTrue(
        player.getUUID().equals(spawnedSlime(helper).getOwnerUUID()),
        "Creative companions must also be owned");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void blockedUsePreservesItem(GameTestHelper helper) {
    for (int x = 4; x <= 6; x++) {
      for (int z = 4; z <= 6; z++) {
        for (int y = 2; y <= 5; y++) {
          helper.setBlock(x, y, z, Blocks.STONE);
        }
      }
    }
    Player player = helper.makeMockSurvivalPlayer();
    ItemStack stack = new ItemStack(ModItems.FRIENDLY_SLIME.get());
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    helper.assertTrue(
        useOnFloor(helper, player) == InteractionResult.FAIL,
        "A slime must not spawn inside solid blocks");
    helper.assertTrue(stack.getCount() == 1, "A failed spawn must preserve its item");
    helper.assertTrue(
        helper.getEntities(ModEntities.FRIENDLY_SLIME.get(), SPAWN_FLOOR, 5).isEmpty(),
        "A failed use must not leave a companion in the world");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void waterUseSpawnsOwnedSlime(GameTestHelper helper) {
    BlockPos water = SPAWN_FLOOR.above();
    helper.setBlock(water, Blocks.WATER);
    Player player = helper.makeMockSurvivalPlayer();
    Vec3 position = helper.absoluteVec(new Vec3(5.5D, 3.0D, 5.5D));
    player.moveTo(position.x, position.y, position.z, 0.0F, 90.0F);
    ItemStack stack = new ItemStack(ModItems.FRIENDLY_SLIME.get());
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    helper.assertTrue(
        stack
            .getItem()
            .use(helper.getLevel(), player, InteractionHand.MAIN_HAND)
            .getResult()
            .consumesAction(),
        "Using the item on a water source must succeed");
    helper.assertTrue(
        player.getUUID().equals(spawnedSlime(helper).getOwnerUUID()),
        "A slime summoned in water must belong to the player");
    helper.assertTrue(stack.isEmpty(), "Survival water use must consume one item");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void ownershipAndWaitingSurviveReload(GameTestHelper helper) {
    Player owner = helper.makeMockSurvivalPlayer();
    FriendlySlime slime = helper.spawn(ModEntities.FRIENDLY_SLIME.get(), SPAWN_FLOOR.above());
    slime.tame(owner);
    owner.moveTo(slime.getX() + 2.0, slime.getY(), slime.getZ());
    new SlimeMenu(1, owner.getInventory(), slime).clickMenuButton(owner, SlimeMenu.WAIT);
    helper.assertTrue(slime.isOrderedToSit(), "An owner must be able to order waiting");
    slime.mobInteract(helper.makeMockSurvivalPlayer(), InteractionHand.MAIN_HAND);
    helper.assertTrue(slime.isOrderedToSit(), "Another player must not change its order");
    CompoundTag saved = slime.saveWithoutId(new CompoundTag());
    FriendlySlime restored = ModEntities.FRIENDLY_SLIME.get().create(helper.getLevel());
    helper.assertTrue(restored != null, "The companion type must support loading");
    restored.load(saved);
    helper.assertTrue(
        restored.isTame() && owner.getUUID().equals(restored.getOwnerUUID()),
        "Ownership must survive an NBT save and load");
    helper.assertTrue(
        restored.isPersistenceRequired() && restored.isOrderedToSit(),
        "Persistence and the wait order must survive loading");
    new SlimeMenu(2, owner.getInventory(), restored).clickMenuButton(owner, SlimeMenu.FOLLOW);
    helper.assertTrue(!restored.isOrderedToSit(), "The owner must be able to resume following");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 60)
  public static void companionIsHarmlessAndSurvivesPeaceful(GameTestHelper helper) {
    helper.getLevel().getServer().setDifficulty(Difficulty.PEACEFUL, true);
    Player player = helper.makeMockSurvivalPlayer();
    FriendlySlime slime = helper.spawn(ModEntities.FRIENDLY_SLIME.get(), SPAWN_FLOOR.above());
    slime.tame(player);
    float health = player.getHealth();
    slime.playerTouch(player);
    slime.push(player);
    helper.runAfterDelay(
        20,
        () -> {
          helper.assertTrue(
              slime.isAlive() && !slime.isRemoved(),
              "A companion must remain alive in Peaceful difficulty");
          helper.assertTrue(
              player.getHealth() == health && slime.getTarget() == null,
              "A friendly slime must not damage or target players");
          helper.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 180)
  public static void followsOwnerByHopping(GameTestHelper helper) {
    FakePlayer owner =
        new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "SlimeOwner"));
    owner.setGameMode(GameType.SURVIVAL);
    Vec3 position = helper.absoluteVec(new Vec3(8.5D, 2.0D, 5.5D));
    owner.moveTo(position.x, position.y, position.z);
    helper.getLevel().addNewPlayer(owner);
    FriendlySlime slime = helper.spawn(ModEntities.FRIENDLY_SLIME.get(), new BlockPos(3, 2, 5));
    slime.tame(owner);
    AtomicBoolean hopped = new AtomicBoolean();
    helper.onEachTick(
        () -> {
          if (slime.getY() > helper.absolutePos(new BlockPos(3, 2, 5)).getY() + 0.2D) {
            hopped.set(true);
          }
        });
    helper.succeedWhen(
        () -> {
          helper.assertTrue(hopped.get(), "The companion must hop while following");
          helper.assertTrue(
              slime.distanceToSqr(owner) < 9.0D, "The companion must approach its owner");
          owner.discard();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 60)
  public static void companionDoesNotSplitOnDeath(GameTestHelper helper) {
    FriendlySlime slime = helper.spawn(ModEntities.FRIENDLY_SLIME.get(), SPAWN_FLOOR.above());
    slime.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
    helper.runAfterDelay(
        25,
        () -> {
          helper.assertTrue(slime.isRemoved(), "The defeated companion must be removed");
          helper.assertTrue(
              helper.getEntities(ModEntities.FRIENDLY_SLIME.get(), SPAWN_FLOOR, 5).isEmpty(),
              "A defeated companion must not split into extra owned slimes");
          helper.assertTrue(
              helper
                  .getEntities(net.minecraft.world.entity.EntityType.SLIME, SPAWN_FLOOR, 5)
                  .isEmpty(),
              "A defeated companion must not create hostile slimes");
          helper.succeed();
        });
  }

  private static InteractionResult useOnFloor(GameTestHelper helper, Player player) {
    BlockPos position = helper.absolutePos(SPAWN_FLOOR);
    return player
        .getMainHandItem()
        .getItem()
        .useOn(
            new UseOnContext(
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                    Vec3.atCenterOf(position).add(0.0D, 0.5D, 0.0D),
                    Direction.UP,
                    position,
                    false)));
  }

  private static FriendlySlime spawnedSlime(GameTestHelper helper) {
    var slimes = helper.getEntities(ModEntities.FRIENDLY_SLIME.get(), SPAWN_FLOOR, 5);
    helper.assertTrue(slimes.size() == 1, "A use must summon exactly one Friendly Slime");
    return slimes.get(0);
  }

  private static TransientCraftingContainer craftingGrid(Item core) {
    var grid =
        new TransientCraftingContainer(
            new AbstractContainerMenu(null, -1) {
              @Override
              public ItemStack quickMoveStack(Player player, int slot) {
                return ItemStack.EMPTY;
              }

              @Override
              public boolean stillValid(Player player) {
                return true;
              }
            },
            3,
            3);
    for (int slot = 0; slot < 9; slot++) {
      grid.setItem(slot, new ItemStack(slot == 4 ? core : Items.SLIME_BLOCK));
    }
    return grid;
  }
}
