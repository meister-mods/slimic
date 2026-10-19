package io.github.meistermods.slimic.entity;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public final class SlimeWorkWarning {
  private SlimeWorkWarning() {}

  public static Component identity(FriendlySlime worker) {
    return worker.hasCustomName()
        ? Component.translatable(
            "message.slimic.named_companion",
            worker.getType().getDescription(),
            worker.getCustomName())
        : worker.getType().getDescription();
  }

  public static Component reason(FriendlySlime worker, WorkStatus status) {
    if (status.reason() == WorkStatus.Reason.TOOL_MISSING) {
      String tool =
          worker instanceof MiningSlime miner
              ? miner.getRequiredToolSlot() == 1 ? "shovel" : "pickaxe"
              : worker instanceof FarmerSlime
                  ? "hoe"
                  : worker instanceof LumberjackSlime
                      ? "axe"
                      : worker instanceof FishingSlime
                          ? "fishing_rod"
                          : worker instanceof CombatSlime ? "sword" : "tool";
      return Component.translatable("message.slimic.blocked.tool_missing." + tool);
    }
    String key =
        status.reason() == WorkStatus.Reason.SEEDS_MISSING && worker instanceof LumberjackSlime
            ? "saplings_missing"
            : status.reason().name().toLowerCase(Locale.ROOT);
    return Component.translatable("message.slimic.blocked." + key);
  }

  public static Component message(FriendlySlime worker, WorkStatus status) {
    return Component.translatable(
        "message.slimic.herald_blocked", identity(worker), reason(worker, status));
  }

  public static void notifyOwners(FriendlySlime worker) {
    var state = worker.getWorkStatus();
    if (!(worker.level() instanceof ServerLevel level)
        || !worker.isAlive()
        || state.state() != WorkStatus.State.BLOCKED) return;
    var message = message(worker, state);
    for (var player : level.getServer().getPlayerList().getPlayers())
      if (worker.canManage(player)) player.sendSystemMessage(message);
  }
}
