package io.github.meistermods.slimic.client;

import io.github.meistermods.slimic.Slimic;
import java.util.Set;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Localizes the archive byline while preserving the written book's original saved author. */
@Mod.EventBusSubscriber(modid = Slimic.MOD_ID, value = Dist.CLIENT)
public final class SlimeRecordTooltips {
  private static final Set<String> FIRST_PAGES =
      Set.of(
          "lore.slimic.sanctum.courtyard.page1", "lore.slimic.sanctum.gallery.page1",
          "lore.slimic.sanctum.laboratory.page1", "lore.slimic.sanctum.core.page1");

  private SlimeRecordTooltips() {}

  private static boolean isArchiveRecord(ItemStack stack) {
    if (!stack.is(Items.WRITTEN_BOOK)
        || !stack.hasTag()
        || !stack.getTag().getString("author").equals("Royal Archives")) return false;
    var pages = stack.getTag().getList("pages", Tag.TAG_STRING);
    if (pages.isEmpty()) return false;
    try {
      Component first = Component.Serializer.fromJson(pages.getString(0));
      return first != null
          && first.getContents() instanceof TranslatableContents text
          && FIRST_PAGES.contains(text.getKey());
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  @SubscribeEvent
  public static void tooltip(ItemTooltipEvent event) {
    if (!isArchiveRecord(event.getItemStack())) return;
    var lines = event.getToolTip();
    for (int index = 0; index < lines.size(); index++) {
      var line = lines.get(index);
      if (line.getContents() instanceof TranslatableContents text
          && text.getKey().equals("book.byAuthor"))
        lines.set(
            index,
            Component.translatable(
                    "book.byAuthor", Component.translatable("lore.slimic.royal_archives.author"))
                .setStyle(line.getStyle()));
    }
  }
}
