package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.CombatTarget;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.menu.CreativeSlimeTunerMenu;
import io.github.meistermods.slimic.menu.IntContainerData;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AuditGameTests {
  private AuditGameTests() {}

  private static CompoundTag warnings(int size) {
    CompoundTag result = new CompoundTag();
    for (int index = 0; index < size; index++)
      result.putLong(new UUID(31, index + 1).toString(), index + 1);
    return result;
  }

  @GameTest(template = "empty", batch = "audit")
  public static void warningHistoryBeyond128SurvivesRecovery(GameTestHelper h) {
    var herald = ModEntities.HERALD_SLIME.get().create(h.getLevel());
    herald.tame(h.makeMockSurvivalPlayer());
    CompoundTag history = warnings(200);
    CompoundTag saved = herald.saveWithoutId(new CompoundTag());
    saved.put("HeraldWarnings", history);
    herald.load(saved);
    var restored = ModEntities.HERALD_SLIME.get().create(h.getLevel());
    restored.load(FriendlySlimeItem.fromSlime(herald).getTag().getCompound("SlimeData"));
    h.assertTrue(
        restored.saveWithoutId(new CompoundTag()).getCompound("HeraldWarnings").equals(history),
        "Recovery must retain all 200 warning records without truncation");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "audit", timeoutTicks = 400)
  public static void aNewWarningDoesNotEvictAnExistingWorkerEpisode(GameTestHelper h) {
    LoadedWorkArea arena = new LoadedWorkArea(h);
    Player owner = h.makeMockSurvivalPlayer();
    var herald = ModEntities.HERALD_SLIME.get().create(h.getLevel());
    herald.tame(owner);
    herald.moveTo(arena.point(8, 101, 8));
    herald.setDelayMinutes(1);
    CompoundTag saved = herald.saveWithoutId(new CompoundTag());
    saved.put("HeraldWarnings", warnings(128));
    herald.load(saved);
    CompoundTag history = herald.saveWithoutId(new CompoundTag()).getCompound("HeraldWarnings");
    String first = history.getAllKeys().iterator().next();
    var oldWorker =
        blockedWorker(
            h.getLevel(),
            owner,
            arena.point(5, 101, 8),
            UUID.fromString(first),
            history.getLong(first));
    var newWorker =
        blockedWorker(h.getLevel(), owner, arena.point(11, 101, 8), UUID.randomUUID(), 17);
    h.getLevel().addFreshEntity(herald);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    herald
                        .saveWithoutId(new CompoundTag())
                        .getCompound("HeraldWarnings")
                        .contains(newWorker.getUUID().toString()),
                    "A newly blocked worker must be signaled"))
        .thenExecute(
            () ->
                h.assertTrue(
                    herald
                            .saveWithoutId(new CompoundTag())
                            .getCompound("HeraldWarnings")
                            .getLong(first)
                        == history.getLong(first),
                    "A new warning must not evict a previously signaled worker"))
        .thenIdle(100)
        .thenExecute(
            () -> {
              CompoundTag state = herald.saveWithoutId(new CompoundTag());
              h.assertTrue(
                  state.getCompound("HeraldWarnings").size() == 129
                      && state.getInt("HeraldCooldown") == 0,
                  "The same two blocked episodes must not restart warning cooldowns");
              herald.discard();
              oldWorker.discard();
              newWorker.discard();
              arena.close();
            })
        .thenSucceed();
  }

  private static MiningSlime blockedWorker(
      ServerLevel level,
      Player owner,
      net.minecraft.world.phys.Vec3 position,
      UUID identity,
      long episode) {
    MiningSlime worker = ModEntities.MINING_SLIME.get().create(level);
    worker.tame(owner);
    worker.moveTo(position);
    worker.setNoAi(true);
    worker.onReleased();
    CompoundTag saved = worker.saveWithoutId(new CompoundTag());
    saved.putUUID("UUID", identity);
    saved.putInt("MiningStatus", MiningSlime.Status.NO_TOOL.ordinal());
    saved.putInt("BlockedWorkTicks", 1300);
    saved.putLong("BlockedEpisode", episode);
    worker.load(saved);
    level.addFreshEntity(worker);
    return worker;
  }

  @GameTest(template = "empty", batch = "audit")
  public static void combatHonorsServerPvpAndOwnerTeamRules(GameTestHelper h) {
    var level = h.getLevel();
    var server = level.getServer();
    var ownerPermission = new java.util.concurrent.atomic.AtomicBoolean(true);
    // Forge's ordinary fake player denies all PVP; expose a controlled owner permission.
    var owner =
        new FakePlayer(level, new GameProfile(UUID.randomUUID(), "AuditOwner")) {
          @Override
          public boolean canHarmPlayer(Player other) {
            return ownerPermission.get();
          }
        };
    var victim = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "AuditVictim"));
    owner.setGameMode(GameType.SURVIVAL);
    victim.setGameMode(GameType.SURVIVAL);
    owner.moveTo(h.absolutePos(new BlockPos(5, 2, 5)), 0, 0);
    victim.moveTo(owner.position().add(1, 0, 0));
    level.addNewPlayer(owner);
    level.addNewPlayer(victim);
    var fighter = ModEntities.COMBAT_SLIME.get().create(level);
    fighter.tame(h.makeMockSurvivalPlayer());
    fighter.moveTo(owner.position());
    fighter.setEquipment(0, new ItemStack(Items.IRON_SWORD));
    fighter.setAllowed(CombatTarget.PLAYERS, true);
    boolean pvp = server.isPvpAllowed();
    var difficulty = level.getDifficulty();
    var scoreboard = server.getScoreboard();
    var team = scoreboard.addPlayerTeam("audit_" + owner.getStringUUID().substring(0, 8));
    try {
      server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
      server.setPvpAllowed(false);
      h.assertTrue(
          !fighter.mayAttack(victim) && !fighter.doHurtTarget(victim) && victim.getHealth() == 20,
          "Enabling player targets must not bypass a server with PVP disabled");
      server.setPvpAllowed(true);
      h.assertTrue(
          fighter.mayAttack(victim),
          "Player targets remain available when server PVP permits them");
      fighter.setOwnerUUID(owner.getUUID());
      ownerPermission.set(false);
      h.assertTrue(
          !fighter.mayAttack(victim), "A loaded owner's damage permission must be respected");
      ownerPermission.set(true);
      team.setAllowFriendlyFire(false);
      scoreboard.addPlayerToTeam(owner.getScoreboardName(), team);
      scoreboard.addPlayerToTeam(victim.getScoreboardName(), team);
      h.assertTrue(
          !fighter.mayAttack(victim), "The owner's team friendly-fire rule must protect teammates");
      scoreboard.removePlayerFromTeam(victim.getScoreboardName(), team);
      h.assertTrue(
          fighter.mayAttack(victim), "Removing team protection must reenable an eligible target");
    } finally {
      server.setPvpAllowed(pvp);
      server.setDifficulty(difficulty, true);
      scoreboard.removePlayerTeam(team);
      owner.discard();
      victim.discard();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "audit")
  public static void menuStatsFitNativeShortPacketsWithoutLosingHighBits(GameTestHelper h) {
    Player owner = h.makeMockSurvivalPlayer();
    var slime = ModEntities.CLEANING_SLIME.get().create(h.getLevel());
    slime.tame(owner);
    slime
        .getAttribute(Attributes.FOLLOW_RANGE)
        .addPermanentModifier(
            new AttributeModifier(
                UUID.randomUUID(),
                "Large detection bonus",
                1000,
                AttributeModifier.Operation.ADDITION));
    SlimeMenu menu = TestMenus.create(1, owner.getInventory(), slime);
    CreativeSlimeTunerMenu tuner = new CreativeSlimeTunerMenu(2, owner.getInventory(), slime);
    assertRoundTrip(h, menu, false);
    assertRoundTrip(h, tuner, true);
    h.succeed();
  }

  private static void assertRoundTrip(
      GameTestHelper h, net.minecraft.world.inventory.AbstractContainerMenu menu, boolean tuner) {
    var slime = tuner ? ((CreativeSlimeTunerMenu) menu).slime() : ((SlimeMenu) menu).slime();
    slime.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(100);
    var values = new net.minecraft.world.inventory.SimpleContainerData(tuner ? 13 : 20);
    var receiver = new IntContainerData(values, true);
    h.assertTrue(!receiver.isReady(), "The client must wait for the complete initial snapshot");
    menu.addSlotListener(
        new net.minecraft.world.inventory.ContainerListener() {
          public void slotChanged(
              net.minecraft.world.inventory.AbstractContainerMenu source,
              int slot,
              ItemStack stack) {}

          public void dataChanged(
              net.minecraft.world.inventory.AbstractContainerMenu source, int index, int value) {
            var buffer =
                new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
              new net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket(
                      menu.containerId, index, value)
                  .write(buffer);
              var decoded =
                  new net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket(buffer);
              receiver.set(decoded.getId(), decoded.getValue());
            } finally {
              buffer.release();
            }
          }
        });
    menu.broadcastChanges();
    h.assertTrue(receiver.isReady(), "The final snapshot marker must arrive after the data");
    for (SlimeStat stat : SlimeStat.values()) {
      double expected =
          tuner ? ((CreativeSlimeTunerMenu) menu).value(stat) : ((SlimeMenu) menu).value(stat);
      h.assertTrue(
          values.get(stat.ordinal()) == Math.round(expected * stat.syncScale()),
          "Native packet round-trip must retain "
              + stat
              + " ("
              + (tuner ? "tuner" : "management")
              + ")");
    }
    h.assertTrue(
        values.get(SlimeStat.MOVEMENT_SPEED.ordinal()) == 100000,
        "High movement bits must survive a signed-short packet");
    if (!tuner)
      h.assertTrue(
          values.get(SlimeStat.DETECTION_RANGE.ordinal()) == 50400,
          "Effective cleaning range must retain both its modifier and fivefold multiplier");
    slime.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.3);
    menu.broadcastChanges();
    h.assertTrue(
        values.get(SlimeStat.MOVEMENT_SPEED.ordinal()) == 300,
        "Returning to a small value must clear previously synchronized high bits");
  }
}
