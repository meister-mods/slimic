package io.github.meistermods.slimic.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;

/** Frozen palettes from loaded sections; inspection is spread across server ticks. */
final class BlockSurvey {
  private record Section(BlockPos origin, PalettedContainer<BlockState> states) {}

  private final ArrayDeque<Section> sections = new ArrayDeque<>();
  private final BlockPos center;
  private final double radiusSquared;
  private final Predicate<BlockState> predicate;
  private int cursor;

  BlockSurvey(ServerLevel level, BlockPos center, double radius, Predicate<BlockState> predicate) {
    this.center = center;
    this.radiusSquared = radius * radius;
    this.predicate = predicate;
    double r = Math.ceil(radius);
    for (var chunk : WorkSearch.loadedChunks(level, center, radius)) {
      for (int i = 0; i < chunk.getSections().length; i++) {
        int y = level.getSectionYFromSectionIndex(i) << 4;
        if (y > center.getY() + r || y + 15 < center.getY() - r) continue;
        var section = chunk.getSections()[i];
        if (section.maybeHas(predicate))
          sections.add(
              new Section(
                  new BlockPos(chunk.getPos().getMinBlockX(), y, chunk.getPos().getMinBlockZ()),
                  section.getStates().copy()));
      }
    }
  }

  List<BlockPos> next(int budget) {
    List<BlockPos> result = new ArrayList<>();
    while (budget-- > 0 && !sections.isEmpty()) {
      Section section = sections.peek();
      int x = cursor & 15, z = (cursor >> 4) & 15, y = cursor >> 8;
      BlockPos pos = section.origin.offset(x, y, z);
      if (pos.distSqr(center) <= radiusSquared && predicate.test(section.states.get(x, y, z)))
        result.add(pos);
      if (++cursor == 4096) {
        cursor = 0;
        sections.remove();
      }
    }
    return result;
  }

  boolean isDone() {
    return sections.isEmpty();
  }
}
