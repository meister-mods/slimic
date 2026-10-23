package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InitialWorkOrderGameTests {
  private static List<EntityType<? extends FriendlySlime>> workers() {
    return List.of(
        ModEntities.GLOW_SLIME.get(),
        ModEntities.COMMAND_SLIME.get(),
        ModEntities.ENDER_SLIME.get(),
        ModEntities.ATTENTION_SLIME.get(),
        ModEntities.FISHING_SLIME.get(),
        ModEntities.ABUNDANCE_SLIME.get(),
        ModEntities.TUNNEL_SLIME.get(),
        ModEntities.DRAGON_SLIME.get(),
        ModEntities.MEOW_SLIME.get(),
        ModEntities.HEALING_SLIME.get(),
        ModEntities.WAREHOUSE_SLIME.get(),
        ModEntities.MINING_SLIME.get(),
        ModEntities.ANCIENT_MINING_SLIME.get(),
        ModEntities.NEXT_GENERATION_MINING_SLIME.get(),
        ModEntities.ANCIENT_TUNNEL_SLIME.get(),
        ModEntities.NEXT_GENERATION_TUNNEL_SLIME.get(),
        ModEntities.TRANSPORT_SLIME.get(),
        ModEntities.SIGNALING_SLIME.get(),
        ModEntities.COMBAT_SLIME.get(),
        ModEntities.ROYAL_GUARD_SLIME.get(),
        ModEntities.CLEANING_SLIME.get(),
        ModEntities.FARMING_SLIME.get(),
        ModEntities.LOGGING_SLIME.get(),
        ModEntities.GUARDIAN_ANGEL_SLIME.get(),
        ModEntities.SOGGY_SLIME.get());
  }

  private static List<EntityType<? extends FriendlySlime>> companions() {
    var types = new ArrayList<>(workers());
    types.add(ModEntities.FRIENDLY_SLIME.get());
    types.add(ModEntities.SADDLE_SLIME.get());
    return types;
  }

  @GameTest(template = "empty", batch = "order_nine")
  public static void allCompanionConstructorsAndCommandlessEntityNbtStartInWait(GameTestHelper h) {
    h.assertTrue(
        companions().size() == ModEntities.individualCompanionTypes().size(),
        "Initial-order coverage includes every registered companion type");
    var owner = h.makeMockSurvivalPlayer();
    for (var type : companions()) {
      var pet = type.create(h.getLevel());
      h.assertTrue(
          pet.isOrderedToSit()
              && pet.isInSittingPose()
              && !pet.isWorking()
              && pet.getWorkStatus().order() == WorkStatus.Order.WAIT,
          "Every newly constructed companion waits: " + type);
      pet.tame(owner);
      pet.setWaiting(false);
      var saved = pet.saveWithoutId(new CompoundTag());
      saved.putString("id", EntityType.getKey(type).toString());
      var restored = (FriendlySlime) EntityType.loadEntityRecursive(saved, h.getLevel(), e -> e);
      h.assertTrue(
          restored != null
              && restored.getType() == type
              && !restored.isOrderedToSit()
              && restored.getUUID().equals(pet.getUUID()),
          "Full world data preserves an explicit Follow order: " + type);
      var commandless = new CompoundTag();
      commandless.putString("id", EntityType.getKey(type).toString());
      commandless.putUUID("Owner", owner.getUUID());
      commandless.putFloat("Health", 7);
      var summoned =
          (FriendlySlime) EntityType.loadEntityRecursive(commandless, h.getLevel(), e -> e);
      h.assertTrue(
          summoned != null
              && summoned.getType() == type
              && summoned.isOrderedToSit()
              && summoned.isInSittingPose()
              && !summoned.isWorking()
              && summoned.getWorkStatus().order() == WorkStatus.Order.WAIT,
          "Commandless direct entity loading starts in Wait: " + type);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "order_nine")
  public static void allFreshWorkersAndMissingOrderDataDefaultToWait(GameTestHelper h) {
    for (var type : workers()) {
      var pet = type.create(h.getLevel());
      pet.tame(h.makeMockSurvivalPlayer());
      h.assertTrue(
          pet.isOrderedToSit()
              && !pet.isWorking()
              && pet.getWorkStatus().order() == WorkStatus.Order.WAIT,
          "New worker waits: " + type);
      pet.setWaiting(false);
      h.assertTrue(
          !pet.isWorking(), "Leaving initial Wait does not implicitly start a job: " + type);
      var data = new CompoundTag();
      data.putBoolean("Sitting", false);
      pet.load(data);
      h.assertTrue(
          pet.isOrderedToSit() && !pet.isWorking(),
          "Missing job data defaults to Wait rather than Follow or automatic work: " + type);
    }
    h.succeed();
  }

  private static FriendlySlime release(
      GameTestHelper h, Player owner, ItemStack item, BlockPos floor) {
    owner.setItemInHand(InteractionHand.MAIN_HAND, item);
    var result =
        ((FriendlySlimeItem) item.getItem())
            .useOn(
                new UseOnContext(
                    owner,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(
                        Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false)));
    h.assertTrue(result.consumesAction(), "Native summon item succeeds: " + item);
    var pets =
        h.getLevel()
            .getEntitiesOfClass(
                FriendlySlime.class,
                new AABB(floor.above()).inflate(.25),
                e -> e.isAlive() && !e.isRemoved());
    h.assertTrue(pets.size() == 1, "Exactly one companion released");
    return pets.get(0);
  }

  @GameTest(template = "empty", batch = "order_nine", timeoutTicks = 120)
  public static void allFreshCompanionsRemainWaitingNearAnOnlineOwner(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var pets = new ArrayList<FriendlySlime>();
    var positions = new ArrayList<Vec3>();
    var owner = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "WaitingOwner"));
    owner.setGameMode(GameType.SURVIVAL);
    owner.moveTo(a.point(14, 101, 14));
    h.getLevel().addNewPlayer(owner);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(BlockPos.containing(a.point(7, 101, 7))),
                    "Work area is entity-ticking"))
        .thenExecute(
            () -> {
              for (int i = 0; i < companions().size(); i++) {
                var preview = companions().get(i).create(h.getLevel());
                var floor = BlockPos.containing(a.point(2 + (i % 5) * 2.5, 100, 2 + (i / 5) * 2.5));
                var pet = release(h, owner, preview.getPickResult(), floor);
                pets.add(pet);
                positions.add(pet.position());
              }
            })
        .thenIdle(60)
        .thenExecute(
            () -> {
              for (int i = 0; i < pets.size(); i++) {
                var pet = pets.get(i);
                var delta = pet.position().subtract(positions.get(i));
                h.assertTrue(
                    pet.isOrderedToSit()
                        && !pet.isWorking()
                        && pet.getWorkStatus().order() == WorkStatus.Order.WAIT
                        && delta.horizontalDistanceSqr() < .04
                        && pet.getActivityProgress() == 0,
                    "Initial Wait survives native AI without following, roaming or work: "
                        + pet.getType());
                pet.discard();
              }
              owner.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_nine", timeoutTicks = 80)
  public static void newAndRecoveredSummonItemsWaitWhileRetainingIdentityAndResources(
      GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(BlockPos.containing(a.point(7, 101, 7))),
                    "Work area is entity-ticking"))
        .thenExecute(
            () -> {
              var owner = h.makeMockSurvivalPlayer();
              owner.moveTo(a.point(7, 101, 7));
              for (int i = 0; i < companions().size(); i++) {
                var type = companions().get(i);
                var preview = type.create(h.getLevel());
                var floor = BlockPos.containing(a.point(2 + (i % 5) * 2.5, 100, 2 + (i / 5) * 2.5));
                var pet = release(h, owner, preview.getPickResult(), floor);
                h.assertTrue(
                    pet.getType() == type && pet.isOrderedToSit() && !pet.isWorking(),
                    "Fresh recipe/creative-default item waits: " + type);
                pet.setNoAi(true);
                pet.setTunedVitals(7, 41, 3);
                owner.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
                owner.moveTo(pet.position());
                var menu = TestMenus.create(10 + i, owner.getInventory(), pet);
                var work = pet instanceof BagSlime || pet instanceof SignalingSlime;
                h.assertTrue(
                    menu.clickMenuButton(owner, work ? SlimeMenu.WORK : SlimeMenu.FOLLOW)
                        && !pet.isOrderedToSit(),
                    "Owner explicitly starts work/follow: " + type);
                var id = pet.getUUID();
                var saved = FriendlySlimeItem.fromSlime(pet);
                pet.discard();
                var restored = release(h, owner, saved, floor);
                h.assertTrue(
                    restored.getUUID().equals(id)
                        && restored.isOrderedToSit()
                        && !restored.isWorking()
                        && restored.getWorkStatus().order() == WorkStatus.Order.WAIT
                        && restored.getHealth() == 7
                        && restored.getStamina() == 41
                        && restored.getStatPoints() == 3,
                    "Recovery starts in Wait while preserving identity and resources: " + type);
                var revival = FriendlySlimeItem.fromSlime(restored);
                restored.discard();
                h.assertTrue(
                    ((FriendlySlimeItem) revival.getItem())
                        .revive(h.getLevel(), owner, revival, floor.above()),
                    "Stored revival succeeds: " + type);
                var revived = (FriendlySlime) h.getLevel().getEntity(id);
                h.assertTrue(
                    revived != null
                        && revived.isOrderedToSit()
                        && !revived.isWorking()
                        && revived.getHealth() == 1
                        && revived.getStamina() == 41,
                    "Revival starts in Wait with exact identity and stored stamina: " + type);
                revived.discard();
              }
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_nine", timeoutTicks = 80)
  public static void specializingAStoredFollowingCompanionStartsEveryWorkerInWait(
      GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(BlockPos.containing(a.point(7, 101, 7))),
                    "Work area is entity-ticking"))
        .thenExecute(
            () -> {
              var owner = h.makeMockSurvivalPlayer();
              owner.moveTo(a.point(7, 101, 7));
              var friendly = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
              friendly.tame(owner);
              friendly.setWaiting(false);
              friendly.setTunedVitals(7, 41, 3);
              var original = FriendlySlimeItem.fromSlime(friendly);
              for (int i = 0; i < workers().size(); i++) {
                var preview = workers().get(i).create(h.getLevel());
                var specialized = preview.getPickResult();
                specialized.setTag(original.getTag().copy());
                var pet =
                    release(
                        h,
                        owner,
                        specialized,
                        BlockPos.containing(a.point(2 + (i % 5) * 2.5, 100, 2 + (i / 5) * 2.5)));
                h.assertTrue(
                    pet.isOrderedToSit()
                        && !pet.isWorking()
                        && pet.getUUID().equals(friendly.getUUID())
                        && pet.getHealth() == 7
                        && pet.getStamina() == 41,
                    "New specialization waits while retaining stored traits: " + pet.getType());
                pet.discard();
              }
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "order_nine", timeoutTicks = 80)
  public static void saddleRecipePausesStoredFollowWithoutLosingTraitsOrEquipment(
      GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(BlockPos.containing(a.point(7, 101, 7))),
                    "Work area is entity-ticking"))
        .thenExecute(
            () -> {
              var owner = h.makeMockSurvivalPlayer();
              owner.moveTo(a.point(7, 101, 7));
              var friendly = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
              friendly.tame(owner);
              friendly.setWaiting(false);
              friendly.setTunedVitals(7, 41, 3);
              var original = FriendlySlimeItem.fromSlime(friendly);
              var before = original.copy();
              var saddle = new ItemStack(Items.SADDLE);
              saddle.setHoverName(net.minecraft.network.chat.Component.literal("Travel saddle"));
              var grid =
                  new TransientCraftingContainer(
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
              grid.setItem(0, original);
              grid.setItem(8, saddle);
              var recipe =
                  h.getLevel()
                      .getRecipeManager()
                      .getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel())
                      .orElseThrow();
              var result = recipe.assemble(grid, h.getLevel().registryAccess());
              h.assertTrue(
                  result.is(ModItems.SADDLE_SLIME.get()), "Native recipe specializes with saddle");
              h.assertTrue(
                  ItemStack.matches(original, before), "Specialization leaves source data intact");
              var pet = release(h, owner, result, BlockPos.containing(a.point(7, 100, 7)));
              h.assertTrue(
                  pet instanceof SaddleSlime mount
                      && pet.isOrderedToSit()
                      && pet.isInSittingPose()
                      && !pet.isWorking()
                      && pet.getWorkStatus().order() == WorkStatus.Order.WAIT
                      && pet.getUUID().equals(friendly.getUUID())
                      && pet.getHealth() == 7
                      && pet.getStamina() == 41
                      && pet.getStatPoints() == 3
                      && ItemStack.matches(mount.getEquipment(0), saddle),
                  "First saddle specialization waits while retaining identity, vitals and exact"
                      + " saddle");
              pet.discard();
              a.close();
            })
        .thenSucceed();
  }
}
