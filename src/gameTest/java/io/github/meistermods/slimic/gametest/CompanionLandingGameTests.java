package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.CompanionTeleport;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionLandingGameTests {
  @GameTest(template = "empty", batch = "companion_landing", timeoutTicks = 400)
  public static void harmfulLandingBlocksAndLateHazardsAreRejected(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var level = h.getLevel();
    var origin = area.point(4, 101, 4);
    var feet = BlockPos.containing(area.point(8, 101, 8));
    var target = Vec3.atBottomCenterOf(feet);
    var player = h.makeMockSurvivalPlayer();
    var slime = ModEntities.FRIENDLY_SLIME.get().create(level);
    slime.tame(player);
    slime.setNoAi(true);
    slime.moveTo(origin);
    h.startSequence()
        .thenWaitUntil(() -> h.assertTrue(level.isPositionEntityTicking(feet), "Arena ticks"))
        .thenExecute(
            () -> {
              try {
                h.assertTrue(level.addFreshEntity(slime), "Original companion is registered");
                level.setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(feet, Blocks.AIR.defaultBlockState(), 2);
                h.assertTrue(
                    CompanionTeleport.safe(level, slime, target)
                        && CompanionTeleport.safe(level, player, target),
                    "Ordinary dry stone remains safe for companions and players");
                level.setBlock(feet.below(), Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                h.assertTrue(
                    !CompanionTeleport.safe(level, slime, target)
                        && !CompanionTeleport.safe(level, player, target),
                    "A solid magma floor is not a safe teleport landing");
                level.setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 2);
                for (BlockState hazard :
                    new BlockState[] {
                      Blocks.FIRE.defaultBlockState(),
                      Blocks.SOUL_FIRE.defaultBlockState(),
                      Blocks.WITHER_ROSE.defaultBlockState(),
                      Blocks.SWEET_BERRY_BUSH
                          .defaultBlockState()
                          .setValue(SweetBerryBushBlock.AGE, 3)
                    }) {
                  level.setBlock(feet, Blocks.AIR.defaultBlockState(), 2);
                  var support =
                      hazard.is(Blocks.SOUL_FIRE)
                          ? Blocks.SOUL_SOIL
                          : hazard.is(Blocks.WITHER_ROSE) || hazard.is(Blocks.SWEET_BERRY_BUSH)
                              ? Blocks.DIRT
                              : Blocks.STONE;
                  level.setBlock(feet.below(), support.defaultBlockState(), 2);
                  h.assertTrue(
                      CompanionTeleport.safe(level, slime, target)
                          && CompanionTeleport.safe(level, player, target),
                      "The hazard's supporting floor is safe before the hazard is placed");
                  level.setBlock(feet, hazard, 2);
                  var placed = level.getBlockState(feet);
                  h.assertTrue(
                      placed.is(hazard.getBlock()) && placed.canSurvive(level, feet),
                      "The hazard remains physically present on a valid support: " + hazard);
                  h.assertTrue(
                      !CompanionTeleport.safe(level, slime, target)
                          && !CompanionTeleport.safe(level, player, target),
                      "A contact-damage block cannot be hidden inside a safe landing: " + hazard);
                }
                level.setBlock(feet, Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 2);
                Consumer<EntityTeleportEvent> lateHazard =
                    event -> {
                      if (event.getEntity() == slime)
                        level.setBlock(feet, Blocks.FIRE.defaultBlockState(), 2);
                    };
                MinecraftForge.EVENT_BUS.addListener(lateHazard);
                try {
                  h.assertTrue(
                      CompanionTeleport.relocate(slime, level, target, ignored -> true) == null
                          && slime.isAlive()
                          && slime.position().equals(origin),
                      "A teleport callback cannot replace the checked landing with fire");
                } finally {
                  MinecraftForge.EVENT_BUS.unregister(lateHazard);
                }
                level.setBlock(feet, Blocks.AIR.defaultBlockState(), 2);
                h.assertTrue(
                    CompanionTeleport.relocate(slime, level, target, ignored -> true) == slime
                        && slime.position().distanceToSqr(target) < .01
                        && slime.isOrderedToSit(),
                    "Removing the hazard restores normal safe relocation");
              } finally {
                slime.discard();
                area.close();
              }
            })
        .thenSucceed();
  }
}
