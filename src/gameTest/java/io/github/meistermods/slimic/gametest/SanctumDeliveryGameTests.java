package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.SanctumRelayBlockEntity;
import io.github.meistermods.slimic.registry.ModBlocks;
import io.github.meistermods.slimic.registry.ModItems;
import io.github.meistermods.slimic.world.DurableWorldData;
import io.github.meistermods.slimic.world.SanctumSites;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SanctumDeliveryGameTests {
  private static final class Receiver extends FakePlayer {
    private Component last;

    Receiver(ServerLevel level) {
      this(level, UUID.randomUUID());
    }

    Receiver(ServerLevel level, UUID id) {
      super(level, new GameProfile(id, "DeliveryRetry"));
    }

    @Override
    public void displayClientMessage(Component message, boolean actionBar) {
      last = message;
    }

    String key() {
      return last != null && last.getContents() instanceof TranslatableContents text
          ? text.getKey()
          : "";
    }
  }

  private record Fixture(
      ServerLevel level,
      Receiver owner,
      SanctumSites ledger,
      SanctumSites.Site site,
      SanctumRelayBlockEntity relay) {}

  private static Fixture fixture(GameTestHelper h) {
    var level =
        h.getLevel().getServer().getLevel(io.github.meistermods.slimic.world.SlimicWorld.DIMENSION);
    var origin = h.absolutePos(BlockPos.ZERO);
    var anchor = new BlockPos(730008 + origin.getX() * 16, 100, 730008 + origin.getZ() * 16);
    var ledger = SanctumSites.get(level);
    var site = ledger.register(level, anchor, 0);
    var relay =
        new SanctumRelayBlockEntity(
            site.at(6, 0, -16), ModBlocks.SANCTUM_RELAY.get().defaultBlockState());
    relay.setLevel(level);
    relay.bind(anchor, 0, 1);
    return new Fixture(level, new Receiver(level), ledger, site, relay);
  }

  private static List<ItemStack> seal() {
    return List.of(new ItemStack(ModItems.ROYAL_COURTYARD_SEAL.get()));
  }

  private static Path playerFault(Fixture f) throws Exception {
    var folder = f.level.getServer().getWorldPath(LevelResource.ROOT).resolve("playerdata");
    Files.createDirectories(folder);
    var path = folder.resolve(f.owner.getStringUUID() + ".pending");
    Files.createDirectory(path);
    return path;
  }

  private static void fillEmptySlots(Receiver owner) {
    for (int i = 0; i < 36; i++)
      if (owner.getInventory().getItem(i).isEmpty())
        owner.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
  }

  @GameTest(template = "empty", batch = "sanctum_delivery")
  public static void failedPlayerSaveRetryStillReportsStorageFailure(GameTestHelper h)
      throws Exception {
    var f = fixture(h);
    var fault = playerFault(f);
    try {
      h.assertTrue(
          f.ledger.grantResult(f.owner, f.site, 1, seal())
              == SanctumSites.GrantResult.STORAGE_ERROR,
          "Player checkpoint fails");
      f.relay.use(f.owner);
      h.assertTrue(
          f.owner.key().equals("message.slimic.sanctum_storage_error"),
          "Retry reports the actual disk failure rather than a full inventory");
      h.assertTrue(
          !f.site.deliveries.get(1).delivered
              && f.owner.getInventory().countItem(ModItems.ROYAL_COURTYARD_SEAL.get()) == 0,
          "Uncheckpointed reward remains reserved at the relay");
    } finally {
      Files.delete(fault);
    }
    f.relay.use(f.owner);
    h.assertTrue(
        f.owner.key().equals("message.slimic.sanctum_reward") && f.site.deliveries.get(1).delivered,
        "Successful retry gives the reward message");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "sanctum_delivery")
  public static void fullInventoryCannotReserveAFirstCoreClaim(GameTestHelper h) {
    var f = fixture(h);
    fillEmptySlots(f.owner);
    h.assertTrue(
        f.ledger.grantResult(
                f.owner, f.site, 4, List.of(new ItemStack(ModItems.ROYAL_SYNTHESIS_CORE.get())))
            == SanctumSites.GrantResult.OUTPUT_FULL,
        "Space refusal is explicit");
    h.assertTrue(
        !f.site.claimed
            && !f.site.cleared
            && f.site.claimant == null
            && f.site.deliveries.isEmpty(),
        "No core or progress is reserved without room");
    var other = new Receiver(f.level);
    h.assertTrue(
        f.ledger.grant(
            other, f.site, 4, List.of(new ItemStack(ModItems.ROYAL_SYNTHESIS_CORE.get()))),
        "Another player can make the first successful claim");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "sanctum_delivery")
  public static void anotherPlayerCannotStealAPendingReward(GameTestHelper h) throws Exception {
    var f = fixture(h);
    var fault = playerFault(f);
    try {
      h.assertTrue(
          f.ledger.grantResult(f.owner, f.site, 1, seal())
              == SanctumSites.GrantResult.STORAGE_ERROR,
          "Delivery remains pending");
      var other = new Receiver(f.level);
      h.assertTrue(
          f.ledger.grantResult(other, f.site, 1, seal()) == SanctumSites.GrantResult.UNAVAILABLE
              && other.getInventory().isEmpty(),
          "Pending delivery retains its original recipient");
      f.relay.use(other);
      h.assertTrue(
          other.key().equals("message.slimic.sanctum_recorded"),
          "Another player sees the shared progress message");
    } finally {
      Files.delete(fault);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "sanctum_delivery")
  public static void existingReceiptsNeedNoExtraInventorySpace(GameTestHelper h) throws Exception {
    var f = fixture(h);
    var rewards =
        List.of(
            new ItemStack(ModItems.ROYAL_GALLERY_SEAL.get()),
            new ItemStack(ModItems.ROYAL_SURVEY_COMPASS.get()));
    var fault = playerFault(f);
    try {
      h.assertTrue(
          f.ledger.grantResult(f.owner, f.site, 2, rewards)
              == SanctumSites.GrantResult.STORAGE_ERROR,
          "The reward pair remains reserved after the failed checkpoint");
    } finally {
      Files.delete(fault);
    }
    // A persisted player image may contain receipts whose site acknowledgement was interrupted.
    for (var item : f.site.deliveries.get(2).items) f.owner.getInventory().add(item.copy());
    fillEmptySlots(f.owner);
    h.assertTrue(
        f.ledger.grantResult(f.owner, f.site, 2, rewards) == SanctumSites.GrantResult.DELIVERED,
        "A full inventory can acknowledge its existing receipts");
    h.assertTrue(
        f.owner.getInventory().countItem(ModItems.ROYAL_GALLERY_SEAL.get()) == 1
            && f.owner.getInventory().countItem(ModItems.ROYAL_SURVEY_COMPASS.get()) == 1,
        "Neither reward is duplicated");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "sanctum_delivery")
  public static void failedSiteCheckpointPreservesTheClaimAndAllRewards(GameTestHelper h)
      throws Exception {
    var f = fixture(h);
    var path =
        f.level
            .getServer()
            .getWorldPath(LevelResource.ROOT)
            .resolve("data/slimic_sanctum_sites.pending");
    Files.createDirectory(path);
    try {
      h.assertTrue(
          f.ledger.grantResult(
                  f.owner, f.site, 4, List.of(new ItemStack(ModItems.ROYAL_SYNTHESIS_CORE.get())))
              == SanctumSites.GrantResult.STORAGE_ERROR,
          "Site checkpoint fails before issuing rewards");
      h.assertTrue(
          !f.site.claimed
              && !f.site.cleared
              && f.site.claimant == null
              && f.site.deliveries.isEmpty()
              && f.owner.getInventory().isEmpty(),
          "Failed site commit preserves both progress and inventory");
    } finally {
      Files.delete(path);
    }
    h.assertTrue(
        f.ledger.grant(
            f.owner, f.site, 4, List.of(new ItemStack(ModItems.ROYAL_SYNTHESIS_CORE.get()))),
        "Retry claims the same core once");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "sanctum_delivery")
  public static void movingARewardAfterAFailedSaveCannotMintAnotherCore(GameTestHelper h)
      throws Exception {
    var f = fixture(h);
    var pos = h.absolutePos(new BlockPos(2, 2, 2));
    h.getLevel().setBlockAndUpdate(pos, ModBlocks.SLIME_CHEST.get().defaultBlockState());
    var chest =
        (io.github.meistermods.slimic.block.SlimeChestBlockEntity) h.getLevel().getBlockEntity(pos);
    var rewards = List.of(new ItemStack(ModItems.ROYAL_SYNTHESIS_CORE.get()));
    var fault = playerFault(f);
    try {
      h.assertTrue(!f.ledger.grant(f.owner, f.site, 4, rewards), "Player checkpoint fails");
      for (int i = 0; i < 36; i++)
        if (f.owner.getInventory().getItem(i).is(ModItems.ROYAL_SYNTHESIS_CORE.get()))
          chest.setItem(0, f.owner.getInventory().removeItemNoUpdate(i));
    } finally {
      Files.delete(fault);
    }
    h.assertTrue(f.ledger.grant(f.owner, f.site, 4, rewards), "Retry eventually delivers the core");
    h.assertTrue(
        f.owner.getInventory().countItem(ModItems.ROYAL_SYNTHESIS_CORE.get())
                + chest.getItem(0).getCount()
            == 1,
        "There is one physical core across player and chest");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "sanctum_delivery")
  public static void consumedCoreStaysDeliveredAfterFailedWorldAcknowledgementAndReload(
      GameTestHelper h) throws Exception {
    var f = fixture(h);
    var rewards = List.of(new ItemStack(ModItems.ROYAL_SYNTHESIS_CORE.get()));
    var firstFault = playerFault(f);
    try {
      h.assertTrue(
          f.ledger.grantResult(f.owner, f.site, 4, rewards)
              == SanctumSites.GrantResult.STORAGE_ERROR,
          "A durable reservation exists before its player delivery succeeds");
    } finally {
      Files.delete(firstFault);
    }
    var root = f.level.getServer().getWorldPath(LevelResource.ROOT);
    var worldFault = root.resolve("data/slimic_sanctum_sites.pending");
    CompoundTag staleWorld, persistedPlayer;
    Files.createDirectory(worldFault);
    try {
      h.assertTrue(
          f.ledger.grantResult(f.owner, f.site, 4, rewards) == SanctumSites.GrantResult.DELIVERED
              && f.owner.getInventory().countItem(ModItems.ROYAL_SYNTHESIS_CORE.get()) == 1,
          "The player receives one core despite failure of the final world acknowledgement");
      staleWorld =
          NbtIo.readCompressed(root.resolve("data/slimic_sanctum_sites.dat").toFile())
              .getCompound("data");
      h.assertTrue(
          !SanctumSites.load(staleWorld).find(f.site.id).deliveries.get(4).delivered,
          "The actual disk ledger still contains the unacknowledged delivery");
      f.owner.getInventory().clearContent();
      h.assertTrue(
          DurableWorldData.savePlayer(f.owner),
          "Using the core is checkpointed independently of the unavailable world acknowledgement");
      persistedPlayer =
          NbtIo.readCompressed(
              root.resolve("playerdata").resolve(f.owner.getStringUUID() + ".dat").toFile());
    } finally {
      Files.delete(worldFault);
    }
    var restored = new Receiver(f.level, f.owner.getUUID());
    restored.load(persistedPlayer);
    var reloaded = SanctumSites.load(staleWorld);
    var reloadedSite = reloaded.find(f.site.id);
    h.assertTrue(
        restored.getInventory().isEmpty(), "Reloaded player has already consumed the core");
    h.assertTrue(
        reloaded.grantResult(restored, reloadedSite, 4, rewards)
                == SanctumSites.GrantResult.DELIVERED
            && reloadedSite.deliveries.get(4).delivered
            && restored.getInventory().isEmpty(),
        "A persisted player receipt acknowledges the old world reservation without another core");
    h.succeed();
  }
}
