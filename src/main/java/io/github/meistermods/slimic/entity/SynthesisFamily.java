package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Family identifiers are stable; Mining and Tunnel mappings share the same transaction safeguards.
 */
public enum SynthesisFamily {
  MINING(
      "mining",
      () -> ModItems.MINING_SLIME.get(),
      () -> ModItems.ANCIENT_MINING_SLIME.get(),
      () -> ModEntities.NEXT_GENERATION_MINING_SLIME.get()),
  TUNNEL(
      "tunnel",
      () -> ModItems.TUNNEL_SLIME.get(),
      () -> ModItems.ANCIENT_TUNNEL_SLIME.get(),
      () -> ModEntities.NEXT_GENERATION_TUNNEL_SLIME.get()),
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

  public Item normalItem() {
    return normal.get();
  }

  public Item ancientItem() {
    return ancient.get();
  }

  public Item resultItem() {
    return switch (this) {
      case MINING -> ModItems.NEXT_GENERATION_MINING_SLIME.get();
      case TUNNEL -> ModItems.NEXT_GENERATION_TUNNEL_SLIME.get();
      default -> net.minecraft.world.item.Items.AIR;
    };
  }

  public FriendlySlime create(ServerLevel level) {
    return enabled() ? result.get().create(level) : null;
  }

  public static boolean acceptsParent(ItemStack stack, boolean ancientParent) {
    for (var family : values())
      if (family.enabled() && stack.is(ancientParent ? family.ancientItem() : family.normalItem()))
        return true;
    return false;
  }

  public static SynthesisFamily match(ItemStack normal, ItemStack ancient) {
    for (var family : values())
      if (family.enabled() && normal.is(family.normal.get()) && ancient.is(family.ancient.get()))
        return family;
    return null;
  }
}
