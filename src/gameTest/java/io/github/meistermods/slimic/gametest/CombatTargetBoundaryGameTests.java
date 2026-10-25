package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.CombatSlime;
import io.github.meistermods.slimic.entity.CombatTarget;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatTargetBoundaryGameTests {
  private static ServerPlayer player(GameTestHelper h) throws Exception {
    var player =
        new ServerPlayer(
            h.getLevel().getServer(),
            h.getLevel(),
            new GameProfile(UUID.randomUUID(), "DepartingTarget"));
    var connection =
        new Connection(PacketFlow.SERVERBOUND) {
          @Override
          public void send(Packet<?> packet) {}
        };
    player.connection =
        new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, player);
    player.setGameMode(GameType.SURVIVAL);
    player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
    player.setHealth(200);
    var protection = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
    protection.setAccessible(true);
    protection.setInt(player, 0);
    return player;
  }

  private static void movedTarget(GameTestHelper h, boolean dragon) throws Exception {
    var area = new LoadedWorkArea(h);
    var server = h.getLevel().getServer();
    boolean pvp = server.isPvpAllowed();
    var victim = player(h);
    CombatSlime fighter =
        dragon
            ? ModEntities.DRAGON_SLIME.get().create(h.getLevel())
            : ModEntities.COMBAT_SLIME.get().create(h.getLevel());
    fighter.tame(h.makeMockSurvivalPlayer());
    fighter.moveTo(area.point(6.5, 101, 8.5));
    fighter.setNoAi(true);
    fighter.setWorking(true);
    fighter.setAllowed(CombatTarget.PLAYERS, true);
    fighter.setEquipment(0, new ItemStack(Items.IRON_SWORD));
    victim.moveTo(area.point(8.5, 101, 8.5));
    h.getLevel().addNewPlayer(victim);
    try {
      server.setPvpAllowed(true);
      h.assertTrue(
          fighter.mayAttack(victim),
          "The same-dimension player is an allowed target before travel");
      fighter.setTarget(victim);
      var destination = server.getLevel(Level.NETHER);
      h.assertTrue(destination != null, "A separate native server dimension exists");
      victim.teleportTo(destination, victim.getX(), victim.getY(), victim.getZ(), 0, 0);
      h.assertTrue(
          victim.level() == destination && victim.isAlive() && fighter.getTarget() == victim,
          "Native player travel preserves the living cached target object across dimensions");
      var fight =
          fighter.goalSelector.getAvailableGoals().stream()
              .map(wrapper -> wrapper.getGoal())
              .filter(goal -> goal.getClass().getSimpleName().equals("FightGoal"))
              .findFirst()
              .orElseThrow();
      fighter.tickCount = 1;
      fight.tick();
      h.assertTrue(
          fighter.getTarget() == null && fighter.getNavigation().isDone(),
          "A player who changed dimensions is released instead of being chased at old coordinates");
      float health = victim.getHealth(), stamina = fighter.getStamina();
      int wear = fighter.getEquipment(0).getDamageValue();
      h.assertTrue(
          !fighter.mayAttack(victim)
              && !fighter.doHurtTarget(victim)
              && victim.getHealth() == health
              && fighter.getStamina() == stamina
              && fighter.getEquipment(0).getDamageValue() == wear,
          "Cross-dimension melee neither damages the player nor spends stamina or sword"
              + " durability");
      if (dragon) {
        fighter.setTarget(victim);
        fighter.tick();
        h.assertTrue(
            victim.getHealth() == health && !victim.isOnFire(),
            "Dragon breath also refuses the old target after a dimension change");
      }
    } finally {
      victim.discard();
      fighter.discard();
      server.setPvpAllowed(pvp);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "combat_target_boundaries", timeoutTicks = 200)
  public static void combatReleasesAPlayerWhoMovedToAnotherDimension(GameTestHelper h)
      throws Exception {
    movedTarget(h, false);
  }

  @GameTest(template = "empty", batch = "combat_target_boundaries", timeoutTicks = 200)
  public static void dragonReleasesAPlayerWhoMovedToAnotherDimension(GameTestHelper h)
      throws Exception {
    movedTarget(h, true);
  }
}
