package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.HealingSlime;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionAdmissionGameTests {
  @GameTest(template = "empty", batch = "companion_admission", timeoutTicks = 400)
  public static void provisionalAdmissionCannotCreateRecoveryItems(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var pos = BlockPos.containing(area.point(8, 101, 8));
    var owner = h.makeMockSurvivalPlayer();
    owner.moveTo(Vec3.atBottomCenterOf(pos).add(0, 0, -2));
    h.startSequence()
        .thenWaitUntil(() -> h.assertTrue(h.getLevel().isPositionEntityTicking(pos), "Arena ticks"))
        .thenExecute(
            () -> {
              try {
                for (boolean restoring : new boolean[] {true, false}) {
                  owner.getInventory().clearContent();
                  var original = ModEntities.HEALING_SLIME.get().create(h.getLevel());
                  original.tame(owner);
                  original.setNoAi(true);
                  original.setHealth(7);
                  original.getBag().setStackInSlot(8, new ItemStack(Items.EMERALD, 17));
                  var stack =
                      restoring
                          ? FriendlySlimeItem.fromSlime(original)
                          : new ItemStack(ModItems.HEALING_SLIME.get(), 3);
                  owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
                  var floor = pos.below();
                  var hit = new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false);
                  FriendlySlime[] candidate = {null};
                  InteractionResult[] capture = {null};
                  Consumer<EntityJoinLevelEvent> callback =
                      event -> {
                        if (event.getLevel() != h.getLevel()
                            || !(event.getEntity() instanceof HealingSlime slime)
                            || !owner.getUUID().equals(slime.getOwnerUUID())) return;
                        candidate[0] = slime;
                        h.assertTrue(slime.isPendingSpawn(), "The callback runs before admission");
                        capture[0] = slime.capture(owner);
                      };
                  MinecraftForge.EVENT_BUS.addListener(callback);
                  try {
                    var result =
                        stack
                            .getItem()
                            .useOn(
                                new UseOnContext(
                                    h.getLevel(), owner, InteractionHand.MAIN_HAND, stack, hit));
                    h.assertTrue(
                        candidate[0] != null && capture[0] == InteractionResult.FAIL,
                        "A provisional companion cannot hand out a second recovery item");
                    h.assertTrue(
                        result.consumesAction()
                            && h.getLevel().getEntity(candidate[0].getUUID()) == candidate[0]
                            && candidate[0].isAlive()
                            && !candidate[0].isPendingSpawn()
                            && stack.getCount() == (restoring ? 0 : 2),
                        "Rejecting reentrant capture preserves successful, single consumption");
                    for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
                      var held = owner.getInventory().getItem(slot);
                      h.assertTrue(
                          held.isEmpty()
                              || !held.hasTag()
                              || !held.getTag().getCompound("SlimeData").hasUUID("UUID")
                              || !candidate[0]
                                  .getUUID()
                                  .equals(held.getTag().getCompound("SlimeData").getUUID("UUID")),
                          "The admitted identity has no copied item in the player's inventory");
                    }
                    if (restoring)
                      h.assertTrue(
                          candidate[0].getUUID().equals(original.getUUID())
                              && candidate[0].getHealth() == 7
                              && ((HealingSlime) candidate[0]).getBag().getStackInSlot(8).getCount()
                                  == 17,
                          "Identity, health and cargo survive the rejected callback");
                  } finally {
                    MinecraftForge.EVENT_BUS.unregister(callback);
                    if (candidate[0] != null) candidate[0].discard();
                    h.getLevel()
                        .getEntitiesOfClass(
                            HealingSlime.class,
                            new AABB(pos).inflate(4),
                            slime -> owner.getUUID().equals(slime.getOwnerUUID()))
                        .forEach(FriendlySlime::discard);
                  }
                }
              } finally {
                area.close();
              }
            })
        .thenSucceed();
  }
}
