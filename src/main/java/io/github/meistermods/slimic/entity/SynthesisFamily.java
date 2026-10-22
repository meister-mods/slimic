package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Family identifiers are stable; only the Mining mapping is enabled in this release. */
public enum SynthesisFamily {
  MINING(
      "mining",
      () -> ModItems.MINING_SLIME.get(),
      () -> ModItems.ANCIENT_MINING_SLIME.get(),
      () -> ModEntities.NEXT_GENERATION_MINING_SLIME.get()),
  TUNNEL("tunnel", null, null, null),
  GUARD("guard", null, null, null);
  private final String id;
  private final Supplier<Item> normal, ancient;
  private final Supplier<EntityType<? extends FriendlySlime>> result;

  SynthesisFamily(
      String id,
      Supplier<Item> normal,
      Supplier<Item> ancient,
      Supplier<EntityType<? extends FriendlySlime>> result) {
    this.id = id;
    this.normal = normal;
    this.ancient = ancient;
    this.result = result;
  }

  public String id() {
    return id;
  }

  public boolean enabled() {
    return normal != null && ancient != null && result != null;
  }

  public FriendlySlime create(ServerLevel level) {
    return enabled() ? result.get().create(level) : null;
  }

  public static SynthesisFamily match(ItemStack normal, ItemStack ancient) {
    for (var family : values())
      if (family.enabled() && normal.is(family.normal.get()) && ancient.is(family.ancient.get()))
        return family;
    return null;
  }
}
