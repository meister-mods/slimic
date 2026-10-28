package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.world.ColonyBondingData;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ColonyCheckpointGameTests {
  @GameTest(template = "empty", batch = "colony_checkpoint", timeoutTicks = 500)
  public static void durableReleaseJournalRecoversWhenNativeEntityStorageIsMissing(
      GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    var level = h.getLevel();
    var owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "EpochRecovery"));
    var root = ModEntities.BOUND_COMBAT_SLIME.get().create(level);
    root.tame(owner);
    ColonyTestFixtures.appearance(root);
    root.setNoAi(true);
    root.moveTo(area.point(8, 101, 8));
    root.setTunedVitals(19, 53, 7);
    root.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 11));
    var ledger = ColonyBondingData.get(level);
    var binding = new ColonyBondingData.Binding();
    binding.id = UUID.randomUUID();
    binding.owner = owner.getUUID();
    binding.representative = root.getUUID();
    binding.first = root.members().records().get(0).getUUID();
    binding.second = root.members().records().get(1).getUUID();
    CompoundTag[] journal = {null}, oldItem = {null}, released = {null};
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(level.isPositionEntityTicking(root.blockPosition()), "Loaded origin"))
        .thenExecute(
            () -> {
              h.assertTrue(ledger.commit(level, binding, root), "Durable membership committed");
              oldItem[0] = binding.output.getTag().getCompound("SlimeData").copy();
              ledger.admitting(root.getUUID(), true);
              try {
                h.assertTrue(
                    level.addFreshEntity(root) && ledger.released(level, root), "Release commits");
              } finally {
                ledger.admitting(root.getUUID(), false);
              }
              released[0] = root.saveWithoutId(new CompoundTag());
              try {
                var path =
                    level
                        .getServer()
                        .getWorldPath(LevelResource.ROOT)
                        .resolve("data")
                        .resolve(ColonyBondingData.NAME + ".dat");
                journal[0] = NbtIo.readCompressed(path.toFile()).getCompound("data");
              } catch (java.io.IOException error) {
                throw new IllegalStateException(error);
              }
              var restarted = ColonyBondingData.load(journal[0].copy());
              h.assertTrue(
                  restarted.mayJoin(root)
                      && !restarted.mayRestore(oldItem[0])
                      && restarted.recovery().containsKey(root.getUUID()),
                  "The actual flushed journal has the new epoch and full fallback before any"
                      + " disk-admission proof");
              root.discard();
            })
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    level.getEntity(root.getUUID()) == null
                        && level.areEntitiesLoaded(
                            new net.minecraft.world.level.ChunkPos(root.blockPosition()).toLong()),
                    "No native entity survived in the fully loaded origin"))
        .thenExecute(
            () -> {
              try {
                var restarted = ColonyBondingData.load(journal[0].copy());
                restarted.recoverInterrupted(level.getServer());
                var item = restarted.recovery().get(root.getUUID());
                h.assertTrue(
                    item != null && restarted.hasRecovery(owner.getUUID()),
                    "Missing native storage becomes one recoverable item");
                var data = item.getTag().getCompound("SlimeData");
                var copy = ModEntities.BOUND_COMBAT_SLIME.get().create(level);
                copy.load(data.copy());
                var stale = ModEntities.BOUND_COMBAT_SLIME.get().create(level);
                stale.load(released[0]);
                h.assertTrue(
                    restarted.mayRestore(data)
                        && !restarted.mayRestore(oldItem[0])
                        && !restarted.mayJoin(stale)
                        && copy.getUUID().equals(root.getUUID())
                        && copy.getHealth() == 19
                        && copy.getStamina() == 53
                        && copy.getStatPoints() == 7
                        && copy.getBag().getStackInSlot(0).getCount() == 11
                        && copy.members().records().get(0).getUUID().equals(binding.first)
                        && copy.members().records().get(1).getUUID().equals(binding.second),
                    "Fallback preserves identity, both members, resources and cargo; old item and"
                        + " world epochs cannot duplicate it");
                var token = data.getCompound("ForgeData").getUUID("ColonyToken");
                restarted.recoverInterrupted(level.getServer());
                h.assertTrue(
                    token.equals(
                        restarted
                            .recovery()
                            .get(root.getUUID())
                            .getTag()
                            .getCompound("SlimeData")
                            .getCompound("ForgeData")
                            .getUUID("ColonyToken")),
                    "Repeated recovery is idempotent");
              } finally {
                root.discard();
                area.close();
              }
            })
        .thenSucceed();
  }
}
