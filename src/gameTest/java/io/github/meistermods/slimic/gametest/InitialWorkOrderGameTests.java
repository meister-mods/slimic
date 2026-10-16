package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.*;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InitialWorkOrderGameTests {
  private static List<EntityType<? extends FriendlySlime>> workers() {
    return List.of(
        ModEntities.MINING_SLIME.get(),
        ModEntities.TRANSPORT_SLIME.get(),
        ModEntities.HERALD_SLIME.get(),
        ModEntities.COMBAT_SLIME.get(),
        ModEntities.CLEANING_SLIME.get(),
        ModEntities.FARMER_SLIME.get(),
        ModEntities.LUMBERJACK_SLIME.get(),
        ModEntities.GUARDIAN_ANGEL_SLIME.get(),
        ModEntities.SOGGY_SLIME.get());
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
                new AABB(floor.above()).inflate(1.5),
                e -> e.isAlive() && !e.isRemoved());
    h.assertTrue(pets.size() == 1, "Exactly one companion released");
    return pets.get(0);
  }

  @GameTest(template = "empty", batch = "order_nine", timeoutTicks = 80)
  public static void newSummonItemsWaitAndRecoveredExplicitCommandsSurvive(GameTestHelper h) {
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
              for (int i = 0; i < workers().size(); i++) {
                var type = workers().get(i);
                var preview = type.create(h.getLevel());
                var floor = BlockPos.containing(a.point(3 + (i % 3) * 4, 100, 3 + (i / 3) * 4));
                var pet = release(h, owner, preview.getPickResult(), floor);
                h.assertTrue(
                    pet.getType() == type && pet.isOrderedToSit() && !pet.isWorking(),
                    "Fresh recipe/creative-default item waits: " + type);
                pet.setNoAi(true);
                pet.setTunedVitals(7, 41, 3);
                owner.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
                var menu = TestMenus.create(10 + i, owner.getInventory(), pet);
                var work = pet instanceof BagSlime || pet instanceof HeraldSlime;
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
                        && !restored.isOrderedToSit()
                        && restored.isWorking() == work
                        && restored.getHealth() == 7
                        && restored.getStamina() == 41
                        && restored.getStatPoints() == 3,
                    "Recovery preserves explicit command, identity and resources: " + type);
                restored.discard();
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
                        BlockPos.containing(a.point(3 + (i % 3) * 4, 100, 3 + (i / 3) * 4)));
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
}
