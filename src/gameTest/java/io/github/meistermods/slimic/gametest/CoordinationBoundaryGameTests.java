package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.*;
import io.github.meistermods.slimic.menu.*;
import io.github.meistermods.slimic.network.CommandPackets;
import io.github.meistermods.slimic.registry.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoordinationBoundaryGameTests {
  private static ServerPlayer owner(GameTestHelper h) {
    var id = UUID.randomUUID();
    var player =
        new ServerPlayer(
            h.getLevel().getServer(),
            h.getLevel(),
            new GameProfile(id, "Owner" + id.toString().substring(0, 8)));
    var connection =
        new Connection(PacketFlow.SERVERBOUND) {
          @Override
          public void send(Packet<?> packet) {}
        };
    player.connection =
        new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, player);
    player.setGameMode(GameType.SURVIVAL);
    return player;
  }

  private static void ready(GameTestHelper h, LoadedWorkArea a) {
    h.assertTrue(
        h.getLevel().isPositionEntityTicking(BlockPos.containing(a.point(7, 101, 7))),
        "Arena ticks");
  }

  private static <T extends FriendlySlime> T pet(
      GameTestHelper h,
      LoadedWorkArea a,
      net.minecraft.world.entity.EntityType<T> type,
      Player owner,
      int x,
      int z) {
    var slime = type.create(h.getLevel());
    slime.tame(owner);
    slime.moveTo(a.point(x, 101, z));
    h.getLevel().addFreshEntity(slime);
    slime.onReleased();
    return slime;
  }

  private static int count(BagSlime slime, Item item) {
    int total = 0;
    for (int i = 0; i < slime.getBag().getSlots(); i++)
      if (slime.getBag().getStackInSlot(i).is(item))
        total += slime.getBag().getStackInSlot(i).getCount();
    return total;
  }

  @GameTest(template = "empty", batch = "coordination_boundaries", timeoutTicks = 500)
  public static void oneSidedGrantsNeverOpenForeignWarehousesAndMutualRevocationStopsTransfers(
      GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var first = owner(h);
    var second = owner(h);
    var registry = CompanionRegistry.get(h.getLevel());
    var carrier = pet(h, a, ModEntities.TRANSPORT_SLIME.get(), first, 4, 8);
    var warehouse = pet(h, a, ModEntities.WAREHOUSE_SLIME.get(), second, 10, 8);
    warehouse.setNoAi(true);
    registry.setRelation(first.getUUID(), second.getUUID(), true, true);
    carrier.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 9));
    carrier.setWorking(true);
    h.startSequence()
        .thenWaitUntil(() -> ready(h, a))
        .thenIdle(80)
        .thenExecute(
            () -> {
              h.assertTrue(
                  count(carrier, Items.DIAMOND) == 9 && count(warehouse, Items.DIAMOND) == 0,
                  "An unsolicited grant cannot create write access to a foreign warehouse");
              registry.setRelation(second.getUUID(), first.getUUID(), true, true);
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    count(warehouse, Items.DIAMOND) == 9 && count(carrier, Items.DIAMOND) == 0,
                    "Mutual consent permits native delivery"))
        .thenExecute(
            () -> {
              registry.setRelation(second.getUUID(), first.getUUID(), true, false);
              carrier.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 9));
            })
        .thenIdle(80)
        .thenExecute(
            () -> {
              h.assertTrue(
                  count(warehouse, Items.DIAMOND) == 9 && count(carrier, Items.DIAMOND) == 9,
                  "Revocation is a live transfer-boundary check");
              carrier.discard();
              warehouse.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "coordination_boundaries", timeoutTicks = 100)
  public static void delegatedCommandCannotGrantRelationsOrEditForeignRecords(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    h.startSequence()
        .thenWaitUntil(() -> ready(h, a))
        .thenExecute(
            () -> {
              var primary = owner(h);
              var shared = owner(h);
              var foreign = owner(h);
              var registry = CompanionRegistry.get(h.getLevel());
              var command = pet(h, a, ModEntities.COMMAND_SLIME.get(), primary, 7, 7);
              shared.moveTo(command.position());
              var own = pet(h, a, ModEntities.FRIENDLY_SLIME.get(), primary, 3, 3);
              var other = pet(h, a, ModEntities.FRIENDLY_SLIME.get(), foreign, 11, 11);
              registry.setRelation(primary.getUUID(), shared.getUUID(), true, true);
              registry.setRelation(primary.getUUID(), foreign.getUUID(), false, true);
              var menu = new CommandMenu(18, shared.getInventory(), command);
              shared.containerMenu = menu;
              h.assertTrue(
                  !menu.perform(shared, CommandMenu.SHARE, 0, foreign.getUUID(), "")
                      && !registry.owns(primary.getUUID(), foreign.getUUID()),
                  "A shared owner cannot delegate primary authority");
              h.assertTrue(
                  !menu.perform(shared, CommandMenu.SET_TAG, 0, other.getUUID(), "stolen"),
                  "A menu cannot choose a foreign primary owner");
              h.assertTrue(
                  menu.perform(shared, CommandMenu.SET_TAG, 0, own.getUUID(), "pals")
                      && own.getCompanionTag().equals("pals"),
                  "Delegated tagging remains available");
              h.assertTrue(
                  menu.createPage(shared).rows().stream()
                      .noneMatch(r -> r.id().equals(other.getUUID())),
                  "Private foreign roster rows are never sent");
              registry.setRelation(primary.getUUID(), shared.getUUID(), true, false);
              h.assertTrue(
                  !menu.stillValid(shared), "Open commander menus lose revoked access immediately");
              own.discard();
              other.discard();
              command.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "coordination_boundaries")
  public static void malformedRosterFramesAndLongTagsAreRejected(GameTestHelper h) {
    var b = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
    boolean rejected = false;
    try {
      b.writeVarInt(1);
      b.writeBoolean(false);
      b.writeVarInt(0);
      b.writeVarInt(1);
      b.writeFloat(10);
      b.writeFloat(100);
      b.writeVarInt(0);
      b.writeVarInt(0);
      b.writeBoolean(true);
      b.writeByte(CommandMenu.PAGE_SIZE + 1);
      try {
        CommandPackets.Page.decode(b);
      } catch (IllegalArgumentException expected) {
        rejected = true;
      }
    } finally {
      b.release();
    }
    h.assertTrue(rejected, "Oversized snapshot rejects before reading attacker-sized rows");
    for (var value : List.of("a".repeat(25), "bad\ncontrol")) {
      boolean bad = false;
      try {
        CompanionRegistry.normalizeTag(value);
      } catch (IllegalArgumentException expected) {
        bad = true;
      }
      h.assertTrue(bad, "Malformed tag is rejected");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "coordination_boundaries", timeoutTicks = 100)
  public static void captureInsideTeleportEventCancelsTravelWithoutChargingTheStoredEndpoint(
      GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    h.startSequence()
        .thenWaitUntil(() -> ready(h, a))
        .thenExecute(
            () -> {
              var actor = owner(h);
              var endpoint = pet(h, a, ModEntities.ENDER_SLIME.get(), actor, 9, 9);
              actor.moveTo(endpoint.position().add(2, 0, 0));
              endpoint.bind(actor);
              endpoint.setTunedVitals(8, 100, 2);
              var token = actor.getInventory().getItem(0).copy();
              var before = actor.position();
              Consumer<EntityTeleportEvent> listener =
                  e -> {
                    if (e.getEntity() == actor) endpoint.capture(actor);
                  };
              MinecraftForge.EVENT_BUS.addListener(listener);
              try {
                h.assertTrue(
                    !SlimeTeleporterItem.teleport(actor, token) && actor.position().equals(before),
                    "A captured endpoint invalidates travel during the event");
                h.assertTrue(
                    endpoint.getStamina() == 100
                        && CompanionRegistry.get(h.getLevel()).find(endpoint.getUUID()).location()
                            == CompanionRegistry.Location.ITEM,
                    "No energy is charged after capture");
              } finally {
                MinecraftForge.EVENT_BUS.unregister(listener);
                endpoint.discard();
                a.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "coordination_boundaries", timeoutTicks = 100)
  public static void revokedAccessDuringTeleportCannotMoveOrChargeTheEndpoint(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    h.startSequence()
        .thenWaitUntil(() -> ready(h, a))
        .thenExecute(
            () -> {
              var primary = owner(h);
              var shared = owner(h);
              var registry = CompanionRegistry.get(h.getLevel());
              var endpoint = pet(h, a, ModEntities.ENDER_SLIME.get(), primary, 9, 9);
              primary.moveTo(endpoint.position().add(2, 0, 0));
              endpoint.bind(primary);
              endpoint.setTunedVitals(8, 100, 2);
              registry.setRelation(primary.getUUID(), shared.getUUID(), true, true);
              shared.moveTo(a.point(2, 101, 2));
              var before = shared.position();
              var token = primary.getInventory().getItem(0).copy();
              Consumer<EntityTeleportEvent> listener =
                  e -> {
                    if (e.getEntity() == shared)
                      registry.setRelation(primary.getUUID(), shared.getUUID(), true, false);
                  };
              MinecraftForge.EVENT_BUS.addListener(listener);
              try {
                h.assertTrue(
                    !SlimeTeleporterItem.teleport(shared, token)
                        && shared.position().equals(before)
                        && endpoint.getStamina() == 100,
                    "Event-time revocation is revalidated before movement and spending");
              } finally {
                MinecraftForge.EVENT_BUS.unregister(listener);
                endpoint.discard();
                a.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "coordination_boundaries", timeoutTicks = 100)
  public static void unsafeEventRedirectDoesNotSpendDestinationStamina(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    h.startSequence()
        .thenWaitUntil(() -> ready(h, a))
        .thenExecute(
            () -> {
              var actor = owner(h);
              var endpoint = pet(h, a, ModEntities.ENDER_SLIME.get(), actor, 9, 9);
              actor.moveTo(endpoint.position().add(2, 0, 0));
              endpoint.bind(actor);
              endpoint.setTunedVitals(8, 100, 2);
              var token = actor.getInventory().getItem(0).copy();
              var before = actor.position();
              Consumer<EntityTeleportEvent> listener =
                  e -> {
                    if (e.getEntity() == actor) e.setTargetY(h.getLevel().getMaxBuildHeight() + 10);
                  };
              MinecraftForge.EVENT_BUS.addListener(listener);
              try {
                h.assertTrue(
                    !SlimeTeleporterItem.teleport(actor, token)
                        && actor.position().equals(before)
                        && endpoint.getStamina() == 100,
                    "Unsafe redirected coordinates are rejected without energy cost");
              } finally {
                MinecraftForge.EVENT_BUS.unregister(listener);
                endpoint.discard();
                a.close();
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "coordination_boundaries", timeoutTicks = 100)
  public static void eachOwnerHasABoundedNumberOfPinnedEndpoints(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    h.startSequence()
        .thenWaitUntil(() -> ready(h, a))
        .thenExecute(
            () -> {
              var actor = owner(h);
              var endpoints = new ArrayList<EnderSlime>();
              int maximum = io.github.meistermods.slimic.config.SlimicConfig.ENDER_ANCHORS.get();
              for (int i = 0; i <= maximum; i++) {
                var endpoint =
                    pet(
                        h,
                        a,
                        ModEntities.ENDER_SLIME.get(),
                        actor,
                        3 + (i % 3) * 3,
                        3 + (i / 3) * 3);
                endpoints.add(endpoint);
                actor.moveTo(endpoint.position().add(2, 0, 0));
                h.assertTrue(
                    endpoint.bind(actor) == (i < maximum),
                    "Pin limit applies before issuing another token");
              }
              h.assertTrue(
                  CompanionRegistry.get(h.getLevel())
                          .pinnedCount(actor.getUUID(), UUID.randomUUID())
                      == maximum,
                  "Only valid pinned companions count");
              endpoints.forEach(Entity::discard);
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "coordination_boundaries", timeoutTicks = 100)
  public static void sharedRiderIsEjectedWhenDelegationIsRevoked(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    h.startSequence()
        .thenWaitUntil(() -> ready(h, a))
        .thenExecute(
            () -> {
              var primary = owner(h);
              var shared = owner(h);
              var registry = CompanionRegistry.get(h.getLevel());
              registry.setRelation(primary.getUUID(), shared.getUUID(), true, true);
              var mount = pet(h, a, ModEntities.SADDLE_SLIME.get(), primary, 7, 7);
              mount.setEquipment(0, new ItemStack(Items.SADDLE));
              shared.moveTo(mount.position());
              h.assertTrue(mount.mount(shared), "Current shared owner can ride");
              registry.setRelation(primary.getUUID(), shared.getUUID(), true, false);
              mount.tick();
              h.assertTrue(
                  shared.getVehicle() == null && mount.getControllingPassenger() == null,
                  "A revoked rider cannot retain control or occupy the saddle");
              mount.discard();
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "coordination_boundaries", timeoutTicks = 180)
  public static void followUsesTheIssuingSharedOwnerAndFallsBackAfterRevocation(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var primary = owner(h);
    var shared = owner(h);
    var teammate = owner(h);
    var team =
        h.getLevel()
            .getScoreboard()
            .addPlayerTeam("p" + primary.getUUID().toString().substring(0, 8));
    team.setAllowFriendlyFire(false);
    h.getLevel().getScoreboard().addPlayerToTeam(primary.getScoreboardName(), team);
    h.getLevel().getScoreboard().addPlayerToTeam(teammate.getScoreboardName(), team);
    teammate.moveTo(a.point(3, 101, 5));
    h.getLevel().addNewPlayer(teammate);
    var registry = CompanionRegistry.get(h.getLevel());
    var pets = new ArrayList<FriendlySlime>();
    primary.moveTo(a.point(3, 101, 3));
    shared.moveTo(a.point(12, 101, 12));
    h.getLevel().addNewPlayer(primary);
    h.getLevel().addNewPlayer(shared);
    h.startSequence()
        .thenWaitUntil(() -> ready(h, a))
        .thenExecute(
            () -> {
              registry.setRelation(primary.getUUID(), shared.getUUID(), true, true);
              var slime = pet(h, a, ModEntities.FRIENDLY_SLIME.get(), primary, 7, 7);
              pets.add(slime);
              primary.setGameMode(GameType.SPECTATOR);
              slime.setFollowingOwner(primary);
              h.assertTrue(
                  slime.getOwner() == shared,
                  "Spectator primary owner falls back to an available shared owner");
              h.assertTrue(
                  slime.isAlliedTo(teammate),
                  "Primary team alliances survive an unavailable primary and a delegated follow"
                      + " target");
              shared.setGameMode(GameType.SPECTATOR);
              h.assertTrue(slime.getOwner() == null, "Spectators are unavailable follow targets");
              primary.setGameMode(GameType.SURVIVAL);
              shared.setGameMode(GameType.SURVIVAL);
              h.assertTrue(
                  TestMenus.create(1, shared.getInventory(), slime)
                          .clickMenuButton(shared, SlimeMenu.FOLLOW)
                      && slime.getOwner() == shared
                      && slime.isAlliedTo(teammate),
                  "Follow selects its authorized issuer without changing the primary team's"
                      + " alliances");
            })
        .thenIdle(20)
        .thenExecute(
            () -> {
              registry.setRelation(primary.getUUID(), shared.getUUID(), true, false);
              h.assertTrue(
                  pets.get(0).getOwner() == primary, "Server ownership falls back immediately");
            })
        .thenIdle(40)
        .thenExecute(
            () -> {
              h.assertTrue(
                  pets.get(0).getOwner() != shared,
                  "A running follow goal cannot retain a revoked target");
              pets.get(0).discard();
              primary.discard();
              shared.discard();
              teammate.discard();
              h.getLevel().getScoreboard().removePlayerTeam(team);
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "coordination_boundaries", timeoutTicks = 300)
  public static void teleporterAndGatherCrossDimensionsWithExactIdentityAndPausedMining(
      GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var actor = owner(h);
    var refs = new ArrayList<FriendlySlime>();
    var ids = new ArrayList<UUID>();
    h.startSequence()
        .thenWaitUntil(() -> ready(h, a))
        .thenExecute(
            () -> {
              var nether = h.getLevel().getServer().getLevel(Level.NETHER);
              h.assertTrue(nether != null, "Nether exists");
              nether.setChunkForced(250, 250, true);
              nether.getChunk(250, 250);
              long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
              h.getLevel()
                  .getServer()
                  .managedBlock(
                      () -> {
                        nether.getChunkSource().pollTask();
                        return nether.isPositionEntityTicking(new BlockPos(4007, 141, 4007))
                            || System.nanoTime() >= deadline;
                      });
              h.assertTrue(
                  nether.isPositionEntityTicking(new BlockPos(4007, 141, 4007)),
                  "Nether fixture ticks before entities join");
              for (int x = 4000; x < 4016; x++)
                for (int z = 4000; z < 4016; z++) {
                  nether.setBlockAndUpdate(
                      new BlockPos(x, 140, z), Blocks.BEDROCK.defaultBlockState());
                  for (int y = 141; y < 145; y++)
                    nether.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
              var endpoint = ModEntities.ENDER_SLIME.get().create(nether);
              endpoint.tame(actor);
              endpoint.moveTo(4007.5, 141, 4007.5);
              nether.addFreshEntity(endpoint);
              endpoint.onReleased();
              refs.add(endpoint);
              actor.teleportTo(nether, 4009.5, 141, 4007.5, 0, 0);
              h.assertTrue(
                  endpoint.bind(actor), "Native player links an endpoint in the other dimension");
              var token = actor.getInventory().getItem(0).copy();
              endpoint.setTunedVitals(8, 100, 2);
              actor.teleportTo(h.getLevel(), a.point(7, 101, 7).x, 101, a.point(7, 101, 7).z, 0, 0);
              boolean travelled = SlimeTeleporterItem.teleport(actor, token);
              h.assertTrue(
                  travelled && actor.level() == nether && endpoint.getStamina() == 0,
                  "Native dimension travel reaches the endpoint and drains once: moved="
                      + travelled
                      + " at="
                      + actor.position()
                      + " dim="
                      + actor.level().dimension().location()
                      + " removed="
                      + actor.isRemoved()
                      + " stamina="
                      + endpoint.getStamina());
              var miner = ModEntities.MINING_SLIME.get().create(nether);
              miner.tame(actor);
              miner.moveTo(4003.5, 141, 4003.5);
              miner.setNoAi(true);
              nether.addFreshEntity(miner);
              miner.onReleased();
              miner.setTool(0, new ItemStack(Items.IRON_PICKAXE));
              miner.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 7));
              miner.setTunedVitals(7, 33, 4);
              miner.setWorking(true);
              ids.add(miner.getUUID());
              var registry = CompanionRegistry.get(h.getLevel());
              registry.observe(miner);
              registry.setTag(actor.getUUID(), miner.getUUID(), "realm");
              actor.teleportTo(h.getLevel(), a.point(7, 101, 7).x, 101, a.point(7, 101, 7).z, 0, 0);
              var command = pet(h, a, ModEntities.COMMAND_SLIME.get(), actor, 7, 7);
              refs.add(command);
              h.assertTrue(
                  command.gather(actor, "realm"),
                  "A tagged companion in another dimension can gather");
            })
        .thenWaitUntil(
            () -> {
              h.assertTrue(refs.size() == 2, "Cross-dimension setup completed");
              h.assertTrue(
                  !((CommandSlime) refs.get(1)).isGathering(), "Cross-dimension gather completes");
            })
        .thenExecute(
            () -> {
              var miner = (MiningSlime) h.getLevel().getEntity(ids.get(0));
              h.assertTrue(
                  miner != null
                      && miner.isOrderedToSit()
                      && !miner.isWorking()
                      && miner.getHealth() == 7
                      && miner.getStatPoints() == 4
                      && count(miner, Items.DIAMOND) == 7
                      && miner.getTool(0).is(Items.IRON_PICKAXE),
                  "Native dimension copy preserves exact identity, supplies and paused state");
              int copies = 0;
              for (var level : h.getLevel().getServer().getAllLevels())
                if (level.getEntity(ids.get(0)) != null) copies++;
              h.assertTrue(copies == 1, "There is exactly one live identity across dimensions");
              miner.discard();
              refs.forEach(Entity::discard);
              actor.discard();
              h.getLevel().getServer().getLevel(Level.NETHER).setChunkForced(250, 250, false);
              a.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "coordination_boundaries", timeoutTicks = 100)
  public static void restoredEndpointCanReleasePersistentTicketBeforeItsFirstTick(
      GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var actor = owner(h);
    var endpoint = pet(h, a, ModEntities.ENDER_SLIME.get(), actor, 9, 9);
    actor.moveTo(endpoint.position().add(2, 0, 0));
    h.assertTrue(endpoint.bind(actor), "Fixture binds");
    var data = new net.minecraft.nbt.CompoundTag();
    endpoint.saveWithoutId(data);
    var id = endpoint.getUUID();
    var pos = endpoint.chunkPosition();
    endpoint.discard();
    var restored = ModEntities.ENDER_SLIME.get().create(h.getLevel());
    restored.load(data);
    h.getLevel().addFreshEntity(restored);
    net.minecraftforge.common.world.ForgeChunkManager.forceChunk(
        h.getLevel(), Slimic.MOD_ID, id, pos.x, pos.z, true, true);
    var saved =
        h.getLevel()
            .getDataStorage()
            .get(net.minecraft.world.level.ForcedChunksSavedData::load, "chunks");
    int before = saved.getEntityForcedChunks().getTickingChunks().size();
    restored.unpin();
    h.assertTrue(
        saved.getEntityForcedChunks().getTickingChunks().size() == before - 1,
        "Unpin before tick removes a restored native ticket");
    restored.setTunedVitals(restored.getHealth(), restored.getMaxStamina(), 0);
    h.assertTrue(restored.bind(actor), "Endpoint can pin again");
    restored.saveWithoutId(data);
    restored.discard();
    var second = ModEntities.ENDER_SLIME.get().create(h.getLevel());
    second.load(data);
    h.getLevel().addFreshEntity(second);
    net.minecraftforge.common.world.ForgeChunkManager.forceChunk(
        h.getLevel(), Slimic.MOD_ID, id, pos.x, pos.z, true, true);
    before = saved.getEntityForcedChunks().getTickingChunks().size();
    second.discard();
    h.assertTrue(
        saved.getEntityForcedChunks().getTickingChunks().size() == before - 1,
        "Discard before tick removes a restored native ticket");
    a.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "coordination_boundaries", timeoutTicks = 100)
  public static void attentionMenuReportsActualRoamFollowAndWaitOrders(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var actor = owner(h);
    var attention = pet(h, a, ModEntities.ATTENTION_SLIME.get(), actor, 7, 7);
    actor.moveTo(attention.position().add(2, 0, 0));
    var menu = TestMenus.create(1, actor.getInventory(), attention);
    h.assertTrue(menu.data(SlimeMenu.MODE) == SlimeMenu.WAIT, "Initial menu reports Wait");
    h.assertTrue(
        menu.clickMenuButton(actor, SlimeMenu.ROAM) && menu.data(SlimeMenu.MODE) == SlimeMenu.ROAM,
        "Roam is labeled as Roam");
    h.assertTrue(
        menu.clickMenuButton(actor, SlimeMenu.FOLLOW)
            && menu.data(SlimeMenu.MODE) == SlimeMenu.FOLLOW
            && !attention.isRoaming(),
        "Follow cancels Roam and reports Follow");
    h.assertTrue(
        menu.clickMenuButton(actor, SlimeMenu.WAIT) && menu.data(SlimeMenu.MODE) == SlimeMenu.WAIT,
        "Wait reports Wait");
    attention.discard();
    a.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "coordination_boundaries", timeoutTicks = 100)
  public static void commandOpeningAfterEntityRemovalRetainsAnInvalidSafeMenu(GameTestHelper h) {
    var actor = owner(h);
    var buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
    try {
      buffer.writeVarInt(Integer.MAX_VALUE);
      var menu = new CommandMenu(2, actor.getInventory(), buffer);
      actor.containerMenu = menu;
      h.assertTrue(
          !menu.stillValid(actor)
              && !menu.perform(actor, CommandMenu.PLAYERS, 0, UUID.randomUUID(), ""),
          "A missing companion opens without a cast or null crash and cannot accept commands");
    } finally {
      buffer.release();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "coordination_boundaries", timeoutTicks = 100)
  public static void storedPinnedEndpointCannotBypassTheOwnersAnchorLimit(GameTestHelper h) {
    var a = new LoadedWorkArea(h);
    var actor = owner(h);
    var first = pet(h, a, ModEntities.ENDER_SLIME.get(), actor, 9, 9);
    actor.moveTo(first.position().add(2, 0, 0));
    first.setTunedVitals(7, 100, 3);
    h.assertTrue(first.bind(actor), "Original endpoint binds");
    var id = first.getUUID();
    h.assertTrue(first.capture(actor).consumesAction(), "Original endpoint is recovered");
    ItemStack stored = ItemStack.EMPTY;
    int storedSlot = -1;
    for (int slot = 0; slot < actor.getInventory().getContainerSize(); slot++)
      if (actor.getInventory().getItem(slot).is(ModItems.ENDER_SLIME.get())) {
        stored = actor.getInventory().getItem(slot);
        storedSlot = slot;
      }
    h.assertTrue(!stored.isEmpty(), "Recovered endpoint item exists");
    var original = stored.getTag().copy();
    var active = new ArrayList<EnderSlime>();
    int limit = io.github.meistermods.slimic.config.SlimicConfig.ENDER_ANCHORS.get();
    for (int n = 0; n < limit; n++) {
      var s = pet(h, a, ModEntities.ENDER_SLIME.get(), actor, 3 + (n % 4) * 2, 3 + (n / 4) * 2);
      actor.moveTo(s.position().add(1, 0, 0));
      h.assertTrue(s.bind(actor), "Other endpoints fill available slots");
      active.add(s);
    }
    actor.getInventory().selected = storedSlot;
    var point = a.point(12, 100, 12);
    var hit =
        new net.minecraft.world.phys.BlockHitResult(
            point, net.minecraft.core.Direction.UP, BlockPos.containing(point), false);
    var context =
        new net.minecraft.world.item.context.UseOnContext(
            actor, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
    h.assertTrue(
        stored.getItem().useOn(context) == net.minecraft.world.InteractionResult.FAIL
            && stored.getCount() == 1
            && stored.getTag().equals(original)
            && h.getLevel().getEntity(id) == null,
        "A full limit rejects restoration without consuming or changing saved traits");
    active.get(0).unpin();
    h.assertTrue(
        stored.getItem().useOn(context).consumesAction() && stored.isEmpty(),
        "Restoration succeeds once a slot is available");
    var restored = (EnderSlime) h.getLevel().getEntity(id);
    h.assertTrue(
        restored != null
            && restored.isPinned()
            && restored.getHealth() == 7
            && restored.getStatPoints() == 3,
        "Restored endpoint retains identity, pinning, health and growth");
    restored.discard();
    active.forEach(Entity::discard);
    a.close();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "coordination_boundaries", timeoutTicks = 500)
  public static void attentionKeepsRoamingWhenOwnerFliesOrMovesFarAway(GameTestHelper h) {
    var local = new LoadedWorkArea(h);
    var distant = new LoadedWorkArea(h);
    var actor = owner(h);
    actor.moveTo(local.point(7, 130, 7));
    actor.setNoGravity(true);
    h.getLevel().addNewPlayer(actor);
    var attention = pet(h, local, ModEntities.ATTENTION_SLIME.get(), actor, 7, 7);
    attention.setRoaming(true);
    var initial = attention.position();
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    attention.position().distanceToSqr(initial) > 1,
                    "Airborne owner does not stop local roaming"))
        .thenExecute(
            () -> {
              attention.setWaiting(true);
              attention.teleportTo(initial.x, initial.y, initial.z);
              actor.moveTo(distant.point(7, 101, 7));
              attention.setRoaming(true);
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    attention.position().distanceToSqr(initial) > 1,
                    "A distant owner does not cause unreachable repeated long routes"))
        .thenExecute(
            () -> {
              attention.discard();
              actor.discard();
              local.close();
              distant.close();
            })
        .thenSucceed();
  }
}
