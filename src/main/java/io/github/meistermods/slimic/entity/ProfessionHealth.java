package io.github.meistermods.slimic.entity;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Stable profession identities let old saves migrate without stacking health modifiers. */
public final class ProfessionHealth {
  public static final UUID GUARDIAN = UUID.fromString("67eaa475-2b01-4cdb-9636-ae9ce0e8a847");
  public static final UUID ROYAL_GUARD = UUID.fromString("b7856207-49f4-4778-a3b1-5da279b702f4");
  public static final int MULTIPLIER = 4;

  private ProfessionHealth() {}

  private static AttributeModifier modifier(UUID id) {
    return new AttributeModifier(
        id, "Slime profession health", MULTIPLIER - 1, AttributeModifier.Operation.MULTIPLY_TOTAL);
  }

  public static void apply(FriendlySlime slime, UUID id) {
    var attribute = slime.getAttribute(Attributes.MAX_HEALTH);
    var current = attribute.getModifier(id);
    if (current == null
        || current.getOperation() != AttributeModifier.Operation.MULTIPLY_TOTAL
        || current.getAmount() != MULTIPLIER - 1) {
      if (current != null) attribute.removeModifier(id);
      attribute.addPermanentModifier(modifier(id));
      slime.setHealth(Math.min(slime.getHealth(), slime.getMaxHealth()));
    }
  }

  public static void addToData(CompoundTag data, UUID id) {
    var attributes = data.getList("Attributes", Tag.TAG_COMPOUND);
    CompoundTag health = null;
    for (var entry : attributes) {
      var attribute = (CompoundTag) entry;
      if (attribute.getString("Name").equals("minecraft:generic.max_health")) health = attribute;
    }
    if (health == null) {
      health = new CompoundTag();
      health.putString("Name", "minecraft:generic.max_health");
      health.putDouble("Base", 16);
      attributes.add(health);
    }
    var modifiers = health.getList("Modifiers", Tag.TAG_COMPOUND);
    for (int i = modifiers.size() - 1; i >= 0; i--) {
      var entry = modifiers.getCompound(i);
      if (entry.hasUUID("UUID") && entry.getUUID("UUID").equals(id)) modifiers.remove(i);
    }
    modifiers.add(modifier(id).save());
    health.put("Modifiers", modifiers);
    data.put("Attributes", attributes);
  }

  public static void migrate(CompoundTag data) {
    for (var entry : data.getList("Attributes", Tag.TAG_COMPOUND)) {
      var attribute = (CompoundTag) entry;
      if (!attribute.getString("Name").equals("minecraft:generic.max_health")) continue;
      var modifiers = attribute.getList("Modifiers", Tag.TAG_COMPOUND);
      for (int i = 0; i < modifiers.size(); i++) {
        var saved = modifiers.getCompound(i);
        if (!saved.hasUUID("UUID")) continue;
        var id = saved.getUUID("UUID");
        if (id.equals(GUARDIAN) || id.equals(ROYAL_GUARD)) modifiers.set(i, modifier(id).save());
      }
    }
  }
}
