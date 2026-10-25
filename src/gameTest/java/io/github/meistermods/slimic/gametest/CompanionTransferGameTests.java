package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.CompanionRegistry;
import io.github.meistermods.slimic.entity.CompanionTeleport;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionTransferGameTests {
  @GameTest(template = "empty", batch = "companion_transfer", timeoutTicks = 400)
  public static void cancelledRallyAdmissionRetainsTheOriginalAndAllowsRetry(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var origin = h.getLevel();
    var target = origin.getServer().getLevel(Level.NETHER);
    var sourcePos = BlockPos.containing(area.point(8, 101, 8));
    var targetPos = BlockPos.containing(area.point(8, 201, 8));
    int cx = targetPos.getX() >> 4, cz = targetPos.getZ() >> 4;
    for (int dx = -1; dx <= 1; dx++)
      for (int dz = -1; dz <= 1; dz++) {
        target.setChunkForced(cx + dx, cz + dz, true);
        target.getChunk(cx + dx, cz + dz);
      }
    for (int dx = -2; dx <= 2; dx++)
      for (int dz = -2; dz <= 2; dz++) {
        target.setBlockAndUpdate(targetPos.offset(dx, -1, dz), Blocks.BEDROCK.defaultBlockState());
        for (int dy = 0; dy < 4; dy++)
          target.setBlockAndUpdate(targetPos.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
      }
    Runnable cleanup =
        () -> {
          for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) target.setChunkForced(cx + dx, cz + dz, false);
          area.close();
        };
    h.runAfterDelay(399, cleanup);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    origin.isPositionEntityTicking(sourcePos)
                        && target.isPositionEntityTicking(targetPos),
                    "Both transfer endpoints tick"))
        .thenExecute(
            () -> {
              var pet = ModEntities.MINING_SLIME.get().create(origin);
              UUID owner = UUID.randomUUID();
              pet.setOwnerUUID(owner);
              pet.setTame(true);
              pet.setNoAi(true);
              pet.moveTo(Vec3.atBottomCenterOf(sourcePos));
              pet.onReleased();
              pet.setWorking(true);
              pet.setTunedVitals(7, 41, 3);
              pet.getBag().setStackInSlot(0, new ItemStack(Items.EMERALD, 7));
              h.assertTrue(origin.addFreshEntity(pet), "The original is registered before moving");
              var registry = CompanionRegistry.get(origin);
              registry.observe(pet);
              var previous = registry.find(pet.getUUID());
              boolean[] attempted = {false};
              Consumer<EntityJoinLevelEvent> veto =
                  event -> {
                    if (event.getLevel() == target
                        && event.getEntity().getUUID().equals(pet.getUUID())) {
                      attempted[0] = true;
                      event.setCanceled(true);
                    }
                  };
              FriendlySlime[] moved = {null};
              MinecraftForge.EVENT_BUS.addListener(veto);
              try {
                moved[0] =
                    CompanionTeleport.relocate(
                        pet,
                        target,
                        Vec3.atBottomCenterOf(targetPos),
                        candidate -> owner.equals(candidate.getOwnerUUID()));
                h.assertTrue(attempted[0], "Destination protection actually vetoes admission");
                h.assertTrue(
                    moved[0] == null
                        && pet.isAlive()
                        && !pet.isRemoved()
                        && origin.getEntity(pet.getUUID()) == pet
                        && target.getEntity(pet.getUUID()) == null,
                    "A rejected rally transfer retains exactly the original in its source world");
                h.assertTrue(
                    pet.getHealth() == 7
                        && pet.getStamina() == 41
                        && pet.getStatPoints() == 3
                        && pet.isWorking()
                        && pet.getBag().getStackInSlot(0).getCount() == 7
                        && previous.equals(registry.find(pet.getUUID())),
                    "A rejected transfer preserves health, work, cargo and the source roster");
                MinecraftForge.EVENT_BUS.unregister(veto);
                moved[0] =
                    CompanionTeleport.relocate(
                        pet,
                        target,
                        Vec3.atBottomCenterOf(targetPos),
                        candidate -> owner.equals(candidate.getOwnerUUID()));
                h.assertTrue(
                    moved[0] instanceof MiningSlime
                        && moved[0] != pet
                        && pet.isRemoved()
                        && origin.getEntity(pet.getUUID()) == null
                        && target.getEntity(pet.getUUID()) == moved[0]
                        && moved[0].isAlive()
                        && !moved[0].isPendingSpawn()
                        && moved[0].isOrderedToSit()
                        && !moved[0].isWorking()
                        && moved[0].getHealth() == 7
                        && moved[0].getStamina() == 41
                        && moved[0].getStatPoints() == 3
                        && ((MiningSlime) moved[0]).getBag().getStackInSlot(0).getCount() == 7
                        && registry
                            .find(pet.getUUID())
                            .dimension()
                            .equals(target.dimension().location().toString()),
                    "A successful retry moves one identity with its traits and starts in Wait");
              } finally {
                MinecraftForge.EVENT_BUS.unregister(veto);
                if (moved[0] != null) moved[0].discard();
                pet.discard();
                var copy = target.getEntity(pet.getUUID());
                if (copy != null) copy.discard();
                cleanup.run();
              }
            })
        .thenSucceed();
  }
}
