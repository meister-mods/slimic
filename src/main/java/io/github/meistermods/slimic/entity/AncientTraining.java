package io.github.meistermods.slimic.entity;

import java.util.Arrays;
import java.util.Random;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Fixed per-core veteran training, using the same upgrades and growth accounting as companions. */
public final class AncientTraining {
  private static final String VERSION = "SlimicAncientTrainingVersion";

  private AncientTraining() {}

  public static void applyOnce(FriendlySlime slime, UUID identity, int rank) {
    if (slime.getPersistentData().getInt(VERSION) >= 1) return;
    var random =
        new Random(
            identity.getMostSignificantBits()
                ^ Long.rotateLeft(identity.getLeastSignificantBits(), 17));
    int points = 12 + Math.max(0, Math.min(3, rank)) * 4 + random.nextInt(5);
    int inherited = Arrays.stream(SlimeStat.values()).mapToInt(slime::getStatLevel).sum();
    slime.setTunedVitals(slime.getHealth(), slime.getStamina(), Math.max(0, points - inherited));
    while (slime.getStatPoints() > 0) {
      var available =
          Arrays.stream(SlimeStat.values())
              .filter(stat -> stat.isUsedBy(slime) && slime.getStatLevel(stat) < stat.maxLevel())
              .toList();
      if (available.isEmpty()) break;
      if (!slime.upgradeStat(available.get(random.nextInt(available.size())))) break;
    }
    int spent = Arrays.stream(SlimeStat.values()).mapToInt(slime::getStatLevel).sum();
    slime.getPersistentData().putInt(VERSION, 1);
    slime.getPersistentData().putUUID("SlimicAncientTrainingIdentity", identity);
    // Invested veteran points count toward subsequent growth costs, rather than becoming free
    // points.
    CompoundTag saved = slime.saveWithoutId(new CompoundTag());
    saved.putInt("EarnedPoints", spent);
    slime.load(saved);
  }
}
