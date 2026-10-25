package io.github.meistermods.slimic.world;

import io.github.meistermods.slimic.Slimic;
import net.minecraft.nbt.Tag;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Delivery receipts survive native player replacement without copying unrelated mod state. */
@Mod.EventBusSubscriber(modid = Slimic.MOD_ID)
public final class DurablePlayerEvents {
  private DurablePlayerEvents() {}

  @SubscribeEvent
  public static void cloneReceipts(PlayerEvent.Clone event) {
    if (event.getEntity().level().isClientSide) return;
    var previous = event.getOriginal().getPersistentData();
    var restored = event.getEntity().getPersistentData();
    for (String key : previous.getAllKeys())
      if ((key.startsWith("SanctumReward_")
              || key.startsWith("AssemblyReward_")
              || key.startsWith("ColonyRecovery_"))
          && previous.contains(key, Tag.TAG_BYTE)
          && previous.getBoolean(key)) restored.putBoolean(key, true);
  }
}
