package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.CompanionRegistry;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCaptureGameTests {
  @GameTest(template = "empty", batch = "companion_capture", timeoutTicks = 400)
  public static void droppedLeashCannotRecaptureTheSameCompanion(GameTestHelper h) {
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
    InteractionResult[] nested = {null};
    Consumer<EntityJoinLevelEvent> recapture =
        event -> {
          if (event.getLevel() == level
              && event.getEntity() instanceof ItemEntity item
              && item.getItem().is(Items.LEAD)
              && item.position().distanceToSqr(point) < 4
              && nested[0] == null) {
            nested[0] = InteractionResult.PASS;
            nested[0] = pet.capture(owner);
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
                h.assertTrue(level.addFreshEntity(pet), "Original companion is registered");
                pet.onReleased();
                pet.setLeashedTo(owner, true);
                h.assertTrue(pet.isLeashed(), "The capture has an actual lead to return");
                MinecraftForge.EVENT_BUS.addListener(recapture);
                var result = pet.capture(owner);
                h.assertTrue(
                    nested[0] == InteractionResult.FAIL,
                    "A leash drop cannot reenter capture before original removal");
                h.assertTrue(
                    result.consumesAction()
                        && pet.isRemoved()
                        && level.getEntity(pet.getUUID()) == null,
                    "One successful capture removes the original");
                int stored = 0;
                for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
                  var item = owner.getInventory().getItem(slot);
                  if (!item.hasTag()) continue;
                  var data = item.getTag().getCompound("SlimeData");
                  if (!data.hasUUID("UUID") || !pet.getUUID().equals(data.getUUID("UUID")))
                    continue;
                  stored += item.getCount();
                  var restored = ModEntities.MINING_SLIME.get().create(level);
                  restored.load(data.copy());
                  h.assertTrue(
                      restored.getHealth() == 7
                          && restored.getStamina() == 41
                          && restored.getStatPoints() == 3
                          && restored.getBag().getStackInSlot(0).getCount() == 17,
                      "The sole recovery item retains all stored traits and cargo");
                }
                h.assertTrue(stored == 1, "The player's inventory contains exactly one identity");
                h.assertTrue(
                    level
                            .getEntitiesOfClass(
                                ItemEntity.class,
                                new AABB(BlockPos.containing(point)).inflate(3),
                                item -> item.getItem().is(Items.LEAD))
                            .stream()
                            .mapToInt(item -> item.getItem().getCount())
                            .sum()
                        == 1,
                    "The original lead is returned exactly once");
                h.assertTrue(
                    CompanionRegistry.get(level).find(pet.getUUID()).location()
                        == CompanionRegistry.Location.ITEM,
                    "Removal callbacks retain the committed ITEM registry location");
              } finally {
                MinecraftForge.EVENT_BUS.unregister(recapture);
                pet.discard();
                level
                    .getEntitiesOfClass(
                        ItemEntity.class,
                        new AABB(BlockPos.containing(point)).inflate(3),
                        item -> item.getItem().is(Items.LEAD))
                    .forEach(ItemEntity::discard);
                area.close();
              }
            })
        .thenSucceed();
  }
}
