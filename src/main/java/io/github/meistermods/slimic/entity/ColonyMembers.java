package io.github.meistermods.slimic.entity;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

/** Two unspawned native records preserve identity and cargo with mirrored shared growth. */
public final class ColonyMembers {
  private final FriendlySlime representative;
  private final List<BagSlime> members = new ArrayList<>(2);
  private boolean projecting;
  private float health, energy, extraLife;
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

  /** A completed explicit sort may retire empty spans; ordinary transfers keep stable indices. */
  void compactBagSpans() {
    if (ready()) spans = bags().stream().map(SlimeBag::storageSlots).toList();
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
    if (tag.contains("Schema") && tag.getInt("Schema") != 1 && tag.getInt("Schema") != 2) return;
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
    normalizeGrowth();
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
    tag.putInt("Schema", 2);
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
      CompoundTag levels = new CompoundTag();
      for (SlimeStat stat : SlimeStat.values()) levels.putInt(stat.key(), statLevel(stat));
      representative.setColonyStatLevels(levels);
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
    normalizeGrowth();
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

  private static long average(long first, long second) {
    return first / 2 + second / 2 + (first % 2 + second % 2) / 2;
  }

  public int statLevel(SlimeStat stat) {
    return Math.min(members.get(0).getStatLevel(stat), members.get(1).getStatLevel(stat));
  }

  public long statPoints() {
    return average(members.get(0).getStatPoints(), members.get(1).getStatPoints());
  }

  public long growthEarned() {
    return average(members.get(0).getGrowthEarned(), members.get(1).getGrowthEarned());
  }

  private int activityHalfUnits() {
    return (members.get(0).activityHalfUnits() + members.get(1).activityHalfUnits()) / 2;
  }

  public int activityProgress() {
    return activityHalfUnits() / 2;
  }

  private int growthCostLevel() {
    return Math.max(members.get(0).growthCostLevel(), members.get(1).growthCostLevel());
  }

  public int nextPointCost() {
    return FriendlySlime.pointCost(growthCostLevel());
  }

  private boolean trustedGrowth() {
    return members.stream().allMatch(FriendlySlime::hasTrustedGrowth);
  }

  private void setGrowth(long points, int halfUnits, int costLevel, boolean trusted) {
    for (BagSlime member : members) member.setColonyGrowth(points, halfUnits, costLevel, trusted);
  }

  private void normalizeGrowth() {
    setGrowth(statPoints(), activityHalfUnits(), growthCostLevel(), trustedGrowth());
  }

  private boolean canTrainTogether() {
    return Arrays.stream(SlimeStat.values())
        .anyMatch(
            stat ->
                stat.isUsedBy(representative)
                    && members.stream()
                        .allMatch(member -> member.getStatLevel(stat) < stat.maxLevel()));
  }

  public void activity(int units) {
    if (!ready() || representative.level().isClientSide || units <= 0) return;
    syncVitals();
    long points = statPoints();
    int halfUnits = activityHalfUnits() + Math.min(units, 64) * (canTrainTogether() ? 1 : 2);
    int costLevel = growthCostLevel();
    boolean trusted = trustedGrowth();
    long limit = Long.MAX_VALUE;
    if (trusted)
      for (BagSlime member : members) {
        long invested = Arrays.stream(SlimeStat.values()).mapToLong(member::getStatLevel).sum();
        limit = Math.min(limit, Long.MAX_VALUE - invested);
      }
    while (halfUnits / 2 >= FriendlySlime.pointCost(costLevel) && points < limit) {
      halfUnits -= FriendlySlime.pointCost(costLevel) * 2;
      costLevel = Math.min(1_000_000, costLevel + 1);
      points++;
    }
    // Match the native stored progress range when an edited balance cannot accept more points.
    halfUnits = Math.min(halfUnits, FriendlySlime.pointCost(1_000_000) * 2 - 1);
    setGrowth(points, halfUnits, costLevel, trusted);
  }

  public boolean upgrade(SlimeStat stat) {
    if (!ready()
        || representative.level().isClientSide
        || statPoints() <= 0
        || statLevel(stat) >= stat.maxLevel()) return false;
    if (stat == SlimeStat.CAPACITY) {
      long slots = 0;
      for (BagSlime member : members)
        slots +=
            Math.max(
                member.getBag().storageSlots(),
                Math.min(
                    Integer.MAX_VALUE,
                    (long) member.getCapacity()
                        + (member.getStatLevel(stat) < stat.maxLevel()
                            ? 3L * member.capacityMultiplier()
                            : 0)));
      if (slots > Integer.MAX_VALUE) return false;
    }
    syncVitals();
    normalizeGrowth();
    long remaining = statPoints() - 1;
    int halfUnits = activityHalfUnits(), costLevel = growthCostLevel();
    boolean trusted = trustedGrowth();
    for (BagSlime member : members)
      if (member.getStatLevel(stat) < stat.maxLevel()) member.upgradeStat(stat);
    setGrowth(remaining, halfUnits, costLevel, trusted);
    project();
    return true;
  }

  private double[] capacityParts(double value) {
    if (!ready() || !SlimeStat.CAPACITY.acceptsTunedValue(value) || value > Integer.MAX_VALUE)
      return null;
    int multiplier = members.get(0).capacityMultiplier();
    long total = Math.max(2, (long) Math.floor(value / multiplier));
    double[] parts = {(total + 1) / 2, total / 2};
    long slots = 0;
    for (int i = 0; i < members.size(); i++) {
      var occupied = members.get(i).getBag().occupiedSlots();
      slots +=
          Math.max(
              (long) parts[i] * multiplier,
              occupied.isEmpty() ? 0L : (long) occupied.get(occupied.size() - 1) + 1);
    }
    return slots <= Integer.MAX_VALUE ? parts : null;
  }

  public boolean canTuneCapacity(double value) {
    return capacityParts(value) != null;
  }

  public void tune(SlimeStat stat, double value) {
    double[] capacity = stat == SlimeStat.CAPACITY ? capacityParts(value) : null;
    if (stat == SlimeStat.CAPACITY && capacity == null)
      throw new IllegalArgumentException("Unrepresentable combined bag");
    syncVitals();
    double each =
        switch (stat) {
          case HEALTH, STAMINA -> value / 2;
          case ATTACK -> value / 1.2;
          default -> value;
        };
    for (int i = 0; i < members.size(); i++)
      stat.tune(
          members.get(i),
          Math.max(stat.minTunedValue(), stat == SlimeStat.CAPACITY ? capacity[i] : each));
    project();
  }

  public void setVitals(float life, float stamina, long points) {
    syncVitals();
    changeLife(net.minecraft.util.Mth.clamp(life, 0, representative.getMaxHealth()) - health);
    distribute(stamina - energy, false);
    long balance = Math.max(0, points);
    setGrowth(
        balance,
        activityHalfUnits(),
        growthCostLevel(),
        balance == statPoints() && trustedGrowth());
    project();
  }

  public ItemStack equipment(net.minecraft.world.entity.EquipmentSlot slot) {
    return ready() ? trainingMember().getItemBySlot(slot) : ItemStack.EMPTY;
  }
}
