package io.github.meistermods.slimic.world;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/** Write-ahead checkpoints use the native SavedData envelope and atomic file replacement. */
public final class DurableWorldData {
  private DurableWorldData() {}

  public static boolean flush(ServerLevel level, String name, SavedData data) {
    return checkpoint(
        level
            .getServer()
            .getWorldPath(LevelResource.ROOT)
            .resolve("data")
            .resolve(name + ".dat")
            .toFile(),
        data);
  }

  public static boolean checkpoint(java.io.File file, SavedData data) {
    var envelope = new CompoundTag();
    envelope.put("data", data.save(new CompoundTag()));
    envelope.putInt(
        "DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
    String name = file.getName();
    if (!name.endsWith(".dat"))
      throw new IllegalArgumentException("Expected a native SavedData file");
    boolean success =
        write(file.toPath().getParent(), name.substring(0, name.length() - 4), envelope);
    if (success) data.setDirty(false);
    return success;
  }

  public static boolean savePlayer(ServerPlayer player) {
    var data = player.saveWithoutId(new CompoundTag());
    data.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
    if (!write(
        player.server.getWorldPath(LevelResource.ROOT).resolve("playerdata"),
        player.getStringUUID(),
        data)) return false;
    // The integrated server also embeds its owner's inventory in level.dat. Checkpoint that
    // native owner snapshot before acknowledging a cross-file delivery.
    if (player.server.isSingleplayer()) player.server.saveEverything(false, true, true);
    return true;
  }

  /** Only publish new rewards after their player checkpoint succeeds on the server thread. */
  public static boolean givePlayerItems(ServerPlayer player, List<ItemStack> rewards) {
    var inventory = player.getInventory();
    var available = new ArrayList<Integer>();
    for (int i = 0; i < inventory.items.size(); i++)
      if (inventory.getItem(i).isEmpty()) available.add(i);
    if (available.size() < rewards.size()) return false;
    var provisional = new LinkedHashMap<Integer, ItemStack>();
    boolean saved = false;
    try {
      for (int i = 0; i < rewards.size(); i++) {
        var item = rewards.get(i).copy();
        provisional.put(available.get(i), item);
        inventory.setItem(available.get(i), item);
      }
      saved = savePlayer(player);
      return saved;
    } finally {
      if (!saved)
        provisional.forEach(
            (slot, item) -> {
              // Touch only the exact stack provisionally placed into a previously empty slot.
              // Existing inventory contents and already acknowledged receipts remain intact.
              if (inventory.getItem(slot) == item) inventory.setItem(slot, ItemStack.EMPTY);
            });
      inventory.setChanged();
    }
  }

  private static boolean write(Path folder, String name, CompoundTag envelope) {
    Path target = folder.resolve(name + ".dat"), temporary = folder.resolve(name + ".pending");
    try {
      Files.createDirectories(folder);
      NbtIo.writeCompressed(envelope, temporary.toFile());
      try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
        channel.force(true);
      }
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException unsupported) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
      // Directory fsync is supported on Unix. The data file itself was already forced above.
      try (var channel = FileChannel.open(folder, StandardOpenOption.READ)) {
        channel.force(true);
      } catch (IOException | UnsupportedOperationException unsupported) {
      }
      return true;
    } catch (IOException error) {
      LogUtils.getLogger().error("Could not checkpoint {}", name, error);
      return false;
    }
  }
}
