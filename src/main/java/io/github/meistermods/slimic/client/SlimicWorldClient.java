package io.github.meistermods.slimic.client;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.registry.ModWorldBlocks;
import java.util.*;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
    modid = Slimic.MOD_ID,
    bus = Mod.EventBusSubscriber.Bus.MOD,
    value = Dist.CLIENT)
public final class SlimicWorldClient {
  private SlimicWorldClient() {}

  private static Map<Block, Integer> colors() {
    Map<Block, Integer> values = new IdentityHashMap<>();
    for (var entry : ModWorldBlocks.entries()) values.put(entry.block().get(), entry.color());
    return values;
  }

  @SubscribeEvent
  public static void blockColors(RegisterColorHandlersEvent.Block event) {
    var colors = colors();
    for (var block : colors.keySet())
      event.register(
          (state, level, pos, tint) -> tint == 0 ? colors.get(state.getBlock()) : -1, block);
  }

  @SubscribeEvent
  public static void itemColors(RegisterColorHandlersEvent.Item event) {
    var colors = colors();
    for (var entry : ModWorldBlocks.entries())
      event.register(
          (stack, tint) ->
              tint == 0 && stack.getItem() instanceof BlockItem block
                  ? colors.getOrDefault(block.getBlock(), -1)
                  : -1,
          entry.item().get());
  }
}
