package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.BrokenSlimeBlockEntity;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.items.IItemHandler;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SupportBoundaryGameTests {
  private static int count(IItemHandler bag, Item item) {
    int count = 0;
    for (int i = 0; i < bag.getSlots(); i++)
      if (bag.getStackInSlot(i).is(item)) count += bag.getStackInSlot(i).getCount();
    return count;
  }

  private static HealingSlime healer(
      GameTestHelper h, LoadedWorkArea a, Player owner, double x, double z) {
    var slime = ModEntities.HEALING_SLIME.get().create(h.getLevel());
    slime.tame(owner);
    slime.moveTo(a.point(x, 101, z));
    slime.onReleased();
    slime.setWorking(true);
    return slime;
  }

  private static void adventureRevival(GameTestHelper h, InteractionHand hand, Direction face) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    owner.getAbilities().mayBuild = false;
    var old = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    old.tame(owner);
    old.setWaiting(true);
    old.setHealth(5);
    var pos = BlockPos.containing(a.point(8, 101, 8));
    h.getLevel().setBlockAndUpdate(pos, ModBlocks.BROKEN_SLIME.get().defaultBlockState());
    ((BrokenSlimeBlockEntity) h.getLevel().getBlockEntity(pos))
        .setSlime(FriendlySlimeItem.fromSlime(old));
    var ball = new ItemStack(Items.SLIME_BALL, 2);
    var permitted = new ListTag();
    if (face == Direction.UP) permitted.add(StringTag.valueOf("minecraft:bedrock"));
    else {
      h.getLevel()
          .setBlockAndUpdate(pos.relative(face.getOpposite()), Blocks.TORCH.defaultBlockState());
      permitted.add(StringTag.valueOf("minecraft:torch"));
    }
    ball.getOrCreateTag().put("CanPlaceOn", permitted);
    owner.setItemInHand(hand, ball);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(pos), "Revival area is entity-ticking"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  owner.mayUseItemAt(pos, face, ball),
                  "Original held slimeball grants native Adventure permission");
              h.assertTrue(
                  h.getLevel()
                      .getBlockState(pos)
                      .use(
                          h.getLevel(),
                          owner,
                          hand,
                          new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false))
                      .consumesAction(),
                  "Native permission-bearing slimeball can revive from the actual hand and face");
              var restored = (FriendlySlime) h.getLevel().getEntity(old.getUUID());
              h.assertTrue(
                  restored != null && restored.getHealth() == 1 && ball.getCount() == 1,
                  "Revival retains the one-health and one-ball rules; hp="
                      + (restored == null ? null : restored.getHealth())
                      + " balls="
                      + ball.getCount());
              restored.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "support_permissions", timeoutTicks = 400)
  public static void adventureMainHandPermissionIsPreserved(GameTestHelper h) {
    adventureRevival(h, InteractionHand.MAIN_HAND, Direction.UP);
  }

  @GameTest(template = "empty", batch = "support_permissions", timeoutTicks = 400)
  public static void adventureOffhandPermissionIsPreserved(GameTestHelper h) {
    adventureRevival(h, InteractionHand.OFF_HAND, Direction.UP);
  }

  @GameTest(template = "empty", batch = "support_permissions", timeoutTicks = 400)
  public static void adventureUsesActualClickedFace(GameTestHelper h) {
    adventureRevival(h, InteractionHand.OFF_HAND, Direction.EAST);
  }

  @GameTest(template = "empty", batch = "support_permissions", timeoutTicks = 400)
  public static void adventureWithoutPermissionCannotRevive(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    owner.getAbilities().mayBuild = false;
    var old = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    old.tame(owner);
    var pos = BlockPos.containing(a.point(8, 101, 8));
    h.getLevel().setBlockAndUpdate(pos, ModBlocks.BROKEN_SLIME.get().defaultBlockState());
    ((BrokenSlimeBlockEntity) h.getLevel().getBlockEntity(pos))
        .setSlime(FriendlySlimeItem.fromSlime(old));
    var ball = new ItemStack(Items.SLIME_BALL, 2);
    owner.setItemInHand(InteractionHand.MAIN_HAND, ball);
    h.assertTrue(
        !h.getLevel()
                .getBlockState(pos)
                .use(
                    h.getLevel(),
                    owner,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false))
                .consumesAction()
            && ball.getCount() == 2
            && h.getLevel().getBlockEntity(pos) instanceof BrokenSlimeBlockEntity,
        "Missing Adventure permission preserves block and balls");
    a.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "support_logistics", timeoutTicks = 180)
  public static void warehouseWithoutSupplySpaceStillCollectsCompatibleCargo(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var owner = h.makeMockSurvivalPlayer();
    var warehouse = ModEntities.WAREHOUSE_SLIME.get().create(h.getLevel());
    warehouse.tame(owner);
    warehouse.moveTo(a.point(7.5, 101, 8.5));
    warehouse.onReleased();
    warehouse.setWorking(true);
    SlimeStat.CAPACITY.tune(warehouse, 1);
    for (int i = 0; i < 8; i++) warehouse.getBag().setStackInSlot(i, new ItemStack(Items.DIRT, 32));
    var empty = healer(h, a, owner, 5.5, 8.5);
    var loaded = healer(h, a, owner, 7.5, 10.5);
    for (int i = 0; i < loaded.getCapacity(); i++)
      loaded.getBag().setStackInSlot(i, new ItemStack(Items.DIRT, 64));
    var chest = a.chest(7, 101, 6);
    chest.setItem(0, new ItemStack(Items.SLIME_BALL, 37));
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(warehouse.blockPosition()), "Arena ticks"))
        .thenExecute(
            () -> {
              h.getLevel().addFreshEntity(empty);
              h.getLevel().addFreshEntity(loaded);
              h.getLevel().addFreshEntity(warehouse);
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    count(warehouse.getBag(), Items.DIRT) > 256,
                    "Uncollectable supplies must not starve compatible cargo collection: "
                        + warehouse.getTransportStatus()))
        .thenExecute(
            () -> {
              h.assertTrue(
                  count(chest.inventory(), Items.SLIME_BALL) == 37
                      && count(warehouse.getBag(), Items.DIRT) + count(loaded.getBag(), Items.DIRT)
                          == 832,
                  "No lost cargo or failed supply charges");
              empty.discard();
              loaded.discard();
              warehouse.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "support_logistics")
  public static void healerSupplyApiHonorsOneStackLimit(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var healer = healer(h, a, h.makeMockSurvivalPlayer(), 8, 8);
    var offered = new ItemStack(Items.SLIME_BALL, 128);
    int received = healer.receiveWorkSupply(offered);
    h.assertTrue(
        received == 64
            && count(healer.getBag(), Items.SLIME_BALL) == 64
            && offered.getCount() == 128,
        "One supply operation accepts at most one stack and does not mutate the offer");
    a.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "support_logistics", timeoutTicks = 400)
  public static void suppliedHealerImmediatelyWithdrawsMissingMaterialRequest(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var healer = healer(h, a, h.makeMockSurvivalPlayer(), 8, 8);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(healer.blockPosition()), "Arena ticks"))
        .thenExecute(() -> h.getLevel().addFreshEntity(healer))
        .thenWaitUntil(() -> h.assertTrue(healer.needsWorkSupply(), "Supply requested"))
        .thenExecute(
            () -> {
              healer.setNoAi(true);
              healer.receiveWorkSupply(new ItemStack(Items.SLIME_BALL, 4));
              h.assertTrue(
                  !healer.needsWorkSupply()
                      && healer.getWorkStatus().reason() == WorkStatus.Reason.NONE,
                  "Request reflects accepted supplies before another AI tick");
              healer.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "support_logistics", timeoutTicks = 400)
  public static void fullHealerRequestsCargoWithoutLosingResourceIdentity(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var healer = healer(h, a, h.makeMockSurvivalPlayer(), 8, 8);
    for (int i = 0; i < healer.getCapacity(); i++)
      healer.getBag().setStackInSlot(i, new ItemStack(Items.DIRT, 64));
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().isPositionEntityTicking(healer.blockPosition()), "Arena ticks"))
        .thenExecute(() -> h.getLevel().addFreshEntity(healer))
        .thenWaitUntil(
            () -> h.assertTrue(healer.needsUnloading(), "Full resource bag requests unloading"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  !healer.needsWorkSupply()
                      && healer.getWorkStatus().reason() == WorkStatus.Reason.INVENTORY_FULL
                      && healer.getHealingStatus() == HealingSlime.Status.FULL,
                  "Cargo request is distinct from a slimeball request");
              healer.discard();
              a.close();
            })
        .thenSucceed();
  }
}
