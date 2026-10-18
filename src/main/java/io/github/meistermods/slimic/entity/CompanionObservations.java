package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.BrokenSlimeBlockEntity;
import io.github.meistermods.slimic.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Slimic.MOD_ID)
public final class CompanionObservations {
  private CompanionObservations() {}

  @SubscribeEvent
  public static void join(EntityJoinLevelEvent event) {
    if (!(event.getLevel() instanceof ServerLevel level)) return;
    var registry = CompanionRegistry.get(level);
    if (event.getEntity() instanceof FriendlySlime slime && slime.getOwnerUUID() != null) {
      registry.observe(slime);
      slime.refreshOwnerAccess();
    } else if (event.getEntity() instanceof ItemEntity item)
      stored(item.getItem(), level, item.blockPosition());
  }

  @SubscribeEvent
  public static void leave(EntityLeaveLevelEvent event) {
    if (!(event.getLevel() instanceof ServerLevel level)
        || !(event.getEntity() instanceof FriendlySlime slime)) return;
    var registry = CompanionRegistry.get(level);
    var entry = registry.find(slime.getUUID());
    if (entry == null
        || entry.location() != CompanionRegistry.Location.WORLD
        || slime.getRemovalReason() == Entity.RemovalReason.CHANGED_DIMENSION) return;
    if (slime.getRemovalReason() != null && slime.getRemovalReason().shouldDestroy())
      registry.mark(slime, CompanionRegistry.Location.REMOVED);
    else registry.observe(slime);
  }

  @SubscribeEvent
  public static void inventory(TickEvent.PlayerTickEvent event) {
    var player = event.player;
    if (event.phase != TickEvent.Phase.END
        || !(player.level() instanceof ServerLevel level)
        || player.tickCount % 100 != Math.floorMod(player.getUUID().hashCode(), 100)) return;
    for (int i = 0; i < player.getInventory().getContainerSize(); i++)
      stored(player.getInventory().getItem(i), level, player.blockPosition());
  }

  private static void stored(ItemStack item, ServerLevel level, net.minecraft.core.BlockPos pos) {
    if (item.is(ModItems.BROKEN_SLIME.get())) {
      var data = BlockItem.getBlockEntityData(item);
      if (data != null)
        CompanionRegistry.get(level)
            .observeStored(ItemStack.of(data.getCompound("Slime")), level, pos);
    } else CompanionRegistry.get(level).observeStored(item, level, pos);
  }

  @SubscribeEvent
  public static void chunk(ChunkEvent.Load event) {
    if (!(event.getLevel() instanceof ServerLevel level)) return;
    for (var block : event.getChunk().getBlockEntitiesPos()) {
      var entity = event.getChunk().getBlockEntity(block);
      if (entity instanceof BrokenSlimeBlockEntity broken)
        CompanionRegistry.get(level).observeStored(broken.getSlime(), level, block);
    }
  }
}
