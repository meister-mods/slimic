package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DurablePlayerReceiptGameTests {
  @GameTest(template = "empty", batch = "durable_player_receipts")
  public static void nativePlayerRespawnPreservesOnlySuccessfulDeliveryReceipts(GameTestHelper h) {
    var profile = new GameProfile(UUID.randomUUID(), "ReceiptRespawn");
    var original = new FakePlayer(h.getLevel(), profile);
    String sanctum = "SanctumReward_" + UUID.randomUUID();
    String assembly = "AssemblyReward_" + UUID.randomUUID();
    String colony = "ColonyRecovery_" + UUID.randomUUID();
    String unpaid = "SanctumReward_" + UUID.randomUUID();
    String malformed = "AssemblyReward_" + UUID.randomUUID();
    original.getPersistentData().putBoolean(sanctum, true);
    original.getPersistentData().putBoolean(assembly, true);
    original.getPersistentData().putBoolean(colony, true);
    original.getPersistentData().putBoolean(unpaid, false);
    original.getPersistentData().putString(malformed, "true");
    original.getPersistentData().putBoolean("UnrelatedReviewFlag", true);
    var respawned = new FakePlayer(h.getLevel(), profile);
    respawned.getPersistentData().putBoolean("ReplacementReviewFlag", true);
    respawned.restoreFrom(original, false);
    var saved = respawned.getPersistentData();
    h.assertTrue(
        saved.getBoolean(sanctum) && saved.getBoolean(assembly) && saved.getBoolean(colony),
        "Native ServerPlayer.restoreFrom carries all three durable receipt types across death");
    h.assertTrue(
        !saved.contains(unpaid)
            && !saved.contains(malformed)
            && !saved.contains("UnrelatedReviewFlag")
            && saved.getBoolean("ReplacementReviewFlag"),
        "Only successful Boolean receipts are copied; unrelated replacement data stays intact");
    h.succeed();
  }
}
