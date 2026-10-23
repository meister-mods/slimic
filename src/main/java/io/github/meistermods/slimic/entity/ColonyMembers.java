package io.github.meistermods.slimic.entity;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

/** Two unspawned native records own their inventories, growth and identity while bound. */
public final class ColonyMembers {
  private final FriendlySlime representative;
  private final List<BagSlime> members = new ArrayList<>(2);
  private boolean projecting;
  private float health, energy, extraLife;
  private int nextWorker;
  private List<Integer> spans = List.of();

  public ColonyMembers(FriendlySlime representative) {
    this.representative = representative;
  }

  public boolean ready() {
    return members.size() == 2;
  }

  public boolean projecting() {
    return projecting;
  }

  public List<BagSlime> records() {
    return List.copyOf(members);
  }

  public List<SlimeBag> bags() {
    return members.stream().map(BagSlime::getBag).toList();
  }

  public List<Integer> spans() {
    return spans;
  }

  public BagSlime trainingMember() {
    return members.get(0);
  }

  public void clear() {
    members.clear();
    extraLife = 0;
    spans = List.of();
  }

  public void load(CompoundTag tag) {
    members.clear();
    if (tag.contains("Schema") && tag.getInt("Schema") != 1) return;
    ListTag entries = tag.getList("Members", Tag.TAG_COMPOUND);
    if (entries.size() != 2) return;
    var expected =
        representative instanceof BoundTransportSlime
            ? io.github.meistermods.slimic.registry.ModEntities.TRANSPORT_SLIME.get()
            : io.github.meistermods.slimic.registry.ModEntities.COMBAT_SLIME.get();
    Set<UUID> identities = new HashSet<>();
    UUID owner = null;
    for (Tag raw : entries) {
      CompoundTag data = (CompoundTag) raw;
      if (!SlimeData.canLoad(data)
          || !data.hasUUID("UUID")
          || !data.hasUUID("Owner")
          || !identities.add(data.getUUID("UUID"))
          || data.getUUID("UUID").equals(representative.getUUID())
          || owner != null && !owner.equals(data.getUUID("Owner"))) {
        members.clear();
        return;
      }
      var entity = EntityType.loadEntityRecursive(data.copy(), representative.level(), e -> e);
      if (!(entity instanceof BagSlime member) || member.getType() != expected) {
        members.clear();
        return;
      }
      owner = member.getOwnerUUID();
      member.setWaiting(true);
      members.add(member);
    }
    if (bags().stream().mapToLong(SlimeBag::storageSlots).sum() > Integer.MAX_VALUE) {
      members.clear();
      return;
    }
    if (members.stream().mapToDouble(FriendlySlime::getMaxHealth).sum() > Float.MAX_VALUE
        || members.stream().mapToDouble(FriendlySlime::getMaxStamina).sum() > Float.MAX_VALUE) {
      members.clear();
      return;
    }
    nextWorker = Math.floorMod(tag.getInt("NextWorker"), 2);
    extraLife =
        Float.isFinite(tag.getFloat("ExtraLife")) ? Math.max(0, tag.getFloat("ExtraLife")) : 0;
    representative.setOwnerUUID(owner);
    representative.setTame(true);
    project();
    int firstSpan = Math.max(spans.get(0), tag.getInt("FirstSpan"));
    int secondSpan = Math.max(spans.get(1), tag.getInt("SecondSpan"));
    if ((long) firstSpan + secondSpan <= Integer.MAX_VALUE) spans = List.of(firstSpan, secondSpan);
  }

  public CompoundTag save() {
    syncVitals();
    CompoundTag tag = new CompoundTag();
    tag.putInt("Schema", 1);
    tag.putInt("NextWorker", nextWorker);
    tag.putFloat("ExtraLife", extraLife);
    if (spans.size() == 2) {
      tag.putInt("FirstSpan", spans.get(0));
      tag.putInt("SecondSpan", spans.get(1));
    }
    ListTag list = new ListTag();
    for (BagSlime member : members) {
      CompoundTag nativeData = new CompoundTag();
      member.save(nativeData);
      list.add(nativeData);
    }
    tag.put("Members", list);
    return tag;
  }

  public void project() {
    if (!ready() || projecting) return;
    projecting = true;
    try {
      for (BagSlime member : members)
        member.setTunedVitals(member.getHealth(), member.getStamina(), member.getStatPoints());
      for (SlimeStat stat : SlimeStat.values()) {
        double a = stat.value(members.get(0)), b = stat.value(members.get(1));
        double value =
            switch (stat) {
              case HEALTH, STAMINA -> a + b;
              case CAPACITY -> a + b;
              case ATTACK -> (a + b) * .6;
              case MOVEMENT_SPEED, WORK_SPEED -> Math.min(a, b);
              default -> a / 2 + b / 2;
            };
        stat.tune(representative, value);
      }
      health = (float) Math.min(Float.MAX_VALUE, lifeTotal() + extraLife);
      energy =
          (float)
              Math.min(
                  Float.MAX_VALUE, members.stream().mapToDouble(FriendlySlime::getStamina).sum());
      representative.setTunedVitals(health, energy, 0);
      if (representative.getHealth() < health) changeLife(representative.getHealth() - health);
      health = representative.getHealth();
      representative.setDyeColorId(members.get(0).getDyeColorId());
      representative.setAncientOrigin(members.get(0).hasAncientOrigin());
      CompoundTag appearance = new CompoundTag();
      appearance.putInt("SecondDye", members.get(1).getDyeColorId());
      appearance.putBoolean("SecondAncient", members.get(1).hasAncientOrigin());
      ListTag equipment = new ListTag();
      for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
        CompoundTag item = members.get(1).getItemBySlot(slot).save(new CompoundTag());
        item.putInt("Slot", slot.ordinal());
        equipment.add(item);
      }
      appearance.put("SecondEquipment", equipment);
      representative.setColonyAppearance(appearance);
      spans = bags().stream().map(SlimeBag::storageSlots).toList();
    } finally {
      projecting = false;
    }
  }

  /** Share net damage and expenditure; never refill either constituent merely by unpacking it. */
  public void syncVitals() {
    if (!ready() || projecting || representative.level().isClientSide) return;
    float currentHealth = representative.getHealth(), currentEnergy = representative.getStamina();
    changeLife(currentHealth - health);
    distribute(currentEnergy - energy, false);
    health = currentHealth;
    energy = currentEnergy;
  }

  private double lifeTotal() {
    return members.stream().mapToDouble(FriendlySlime::getHealth).sum();
  }

  private void changeLife(float delta) {
    if (!Float.isFinite(delta) || delta == 0) return;
    if (delta < 0 && extraLife > 0) {
      float spent = Math.min(extraLife, -delta);
      extraLife -= spent;
      delta += spent;
    }
    double before = lifeTotal();
    distribute(delta, true);
    if (delta > 0) extraLife += (float) Math.max(0, delta - (lifeTotal() - before));
  }

  /** New effects belong to the shared body; transfer them once, with their remaining duration. */
  public void prepareUnbind() {
    syncVitals();
    BagSlime first = trainingMember();
    for (var effect : representative.getActiveEffects())
      first.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect));
    first.setTunedVitals(first.getHealth() + extraLife, first.getStamina(), first.getStatPoints());
    first.setAbsorptionAmount(representative.getAbsorptionAmount());
    first.setRemainingFireTicks(representative.getRemainingFireTicks());
    first.setTicksFrozen(representative.getTicksFrozen());
    first.setAirSupply(representative.getAirSupply());
    extraLife = 0;
  }

  private void distribute(float delta, boolean life) {
    if (delta == 0 || !Float.isFinite(delta)) return;
    double available = 0;
    for (BagSlime member : members) {
      float value = life ? member.getHealth() : member.getStamina();
      float maximum = life ? member.getMaxHealth() : member.getMaxStamina();
      available += delta > 0 ? maximum - value : value;
    }
    if (available <= 0) return;
    for (BagSlime member : members) {
      float value = life ? member.getHealth() : member.getStamina();
      float maximum = life ? member.getMaxHealth() : member.getMaxStamina();
      float change = (float) (delta * (delta > 0 ? maximum - value : value) / available);
      member.setTunedVitals(
          life ? value + change : member.getHealth(),
          life ? member.getStamina() : value + change,
          member.getStatPoints());
    }
  }

  public void activity(int units) {
    syncVitals();
    members.get(nextWorker).recordCompletedWork(units);
    nextWorker = 1 - nextWorker;
  }

  public boolean upgrade(SlimeStat stat) {
    syncVitals();
    if (stat == SlimeStat.CAPACITY
        && (long)
                    Math.max(
                        trainingMember().getBag().storageSlots(),
                        Math.min(
                            Integer.MAX_VALUE,
                            (long) trainingMember().getCapacity()
                                + 3L * trainingMember().capacityMultiplier()))
                + members.get(1).getBag().storageSlots()
            > Integer.MAX_VALUE) return false;
    boolean success = trainingMember().upgradeStat(stat);
    if (success) project();
    return success;
  }

  public void tune(SlimeStat stat, double value) {
    syncVitals();
    double each =
        switch (stat) {
          case HEALTH, STAMINA, CAPACITY -> value / 2;
          case ATTACK -> value / 1.2;
          default -> value;
        };
    if (stat == SlimeStat.CAPACITY) each = Math.max(1, Math.floor(each));
    if (stat == SlimeStat.CAPACITY) {
      long slots = 0;
      for (BagSlime member : members) {
        long capacity =
            Math.min(
                Integer.MAX_VALUE,
                (long) Math.max(1, Math.floor(each / member.capacityMultiplier()))
                    * member.capacityMultiplier());
        var occupied = member.getBag().occupiedSlots();
        slots +=
            Math.max(
                capacity, occupied.isEmpty() ? 0L : (long) occupied.get(occupied.size() - 1) + 1);
      }
      if (slots > Integer.MAX_VALUE)
        throw new IllegalArgumentException("Unrepresentable combined bag");
    }
    for (BagSlime member : members) {
      double tuned =
          stat == SlimeStat.CAPACITY ? Math.floor(each / member.capacityMultiplier()) : each;
      stat.tune(member, Math.max(stat.minTunedValue(), tuned));
    }
    project();
  }

  public void setVitals(float life, float stamina, long points) {
    syncVitals();
    changeLife(net.minecraft.util.Mth.clamp(life, 0, representative.getMaxHealth()) - health);
    distribute(stamina - energy, false);
    BagSlime first = trainingMember();
    first.setTunedVitals(first.getHealth(), first.getStamina(), points);
    project();
  }

  public ItemStack equipment(net.minecraft.world.entity.EquipmentSlot slot) {
    return ready() ? trainingMember().getItemBySlot(slot) : ItemStack.EMPTY;
  }
}
