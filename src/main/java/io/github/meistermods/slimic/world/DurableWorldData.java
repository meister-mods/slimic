package io.github.meistermods.slimic.world;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
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

  public static boolean savePlayer(net.minecraft.server.level.ServerPlayer player) {
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
