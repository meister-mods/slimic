package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.nbt.*;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The saved authority owns shared life/cargo; each physical member owns its equipment and attacks.
 */
public final class BoundCombatSlime extends CombatSlime implements LinkedColony {
  private static final EntityDataAccessor<Integer> LINKED_BODY =
      SynchedEntityData.defineId(BoundCombatSlime.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<Integer> ASSIST_MEMBER =
      SynchedEntityData.defineId(BoundCombatSlime.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<Integer> ASSIST_TICKS =
      SynchedEntityData.defineId(BoundCombatSlime.class, EntityDataSerializers.INT);
  private final ColonyMembers members = new ColonyMembers(this);
  private final ColonyAssistCharge assist = new ColonyAssistCharge(this);
  private CombatColonyBody linkedBody;
  private CombatColonyBody damageBody;
  private int nextBodyAttempt, returnTicks;
  private boolean admittingBody;

  public BoundCombatSlime(EntityType<? extends BoundCombatSlime> type, Level level) {
    super(type, level);
  }

  @Override
  protected void defineSynchedData() {
    super.defineSynchedData();
    entityData.define(LINKED_BODY, -1);
    entityData.define(ASSIST_MEMBER, 0);
    entityData.define(ASSIST_TICKS, 0);
  }

  @Override
  public ColonyMembers members() {
    return members;
  }

  @Override
  public double getLinkRange() {
    return Math.min(64, Math.max(16, SlimeStat.DETECTION_RANGE.value(this) * 2));
  }

  @Nullable
  @Override
  public CombatColonyBody getLinkedBody() {
    if (level().isClientSide) {
      var entity = level().getEntity(entityData.get(LINKED_BODY));
      return entity instanceof CombatColonyBody body && !body.isRemoved() ? body : null;
    }
    return linkedBody != null && !linkedBody.isRemoved() && linkedBody.level() == level()
        ? linkedBody
        : null;
  }

  public int assistMember() {
    return entityData.get(ASSIST_MEMBER);
  }

  public int assistTicks() {
    return entityData.get(ASSIST_TICKS);
  }

  void setAssistState(int member, int ticks) {
    entityData.set(ASSIST_MEMBER, member);
    entityData.set(ASSIST_TICKS, ticks);
  }

  @Override
  public boolean isBodyInMotion(LinkedColonyBody<?> body) {
    return body == getLinkedBody() && assistMember() == 2;
  }

  @Override
  protected boolean hasCombatMotion() {
    return assistMember() == 1;
  }

  @Override
  protected void combatTargetAcquired(LivingEntity target) {
    assist.begin(0, target);
  }

  void linkedTargetAcquired(LivingEntity target) {
    assist.begin(1, target);
  }

  @Override
  protected SlimeBag createBag(int slots) {
    return new ColonyBag(
        slots * 2,
        () -> members == null ? List.of() : members.bags(),
        () -> members == null ? List.of() : members.spans(),
        () -> bagRevision++,
        () -> {
          if (members != null) members.compactBagSpans();
        });
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.BOUND_COMBAT_SLIME.get());
  }

  public ItemStack memberEquipment(int member, EquipmentSlot slot) {
    if (member == 0)
      return !level().isClientSide && members != null && members.ready()
          ? members.equipment(slot)
          : super.getItemBySlot(slot);
    if (!level().isClientSide && members != null && members.ready())
      return members.records().get(1).getItemBySlot(slot);
    for (var raw : colonyAppearance().getList("SecondEquipment", Tag.TAG_COMPOUND)) {
      var item = (CompoundTag) raw;
      if (item.getInt("Slot") == slot.ordinal()) return ItemStack.of(item);
    }
    return ItemStack.EMPTY;
  }

  public void setMemberEquipment(int member, EquipmentSlot slot, ItemStack stack) {
    if (!level().isClientSide && members != null && members.ready()) {
      members.records().get(member).setItemSlot(slot, stack);
      refreshEquipmentAppearance();
    } else if (member == 0) super.setItemSlot(slot, stack);
    else {
      var appearance = colonyAppearance().copy();
      var items = appearance.getList("SecondEquipment", Tag.TAG_COMPOUND);
      items.removeIf(raw -> ((CompoundTag) raw).getInt("Slot") == slot.ordinal());
      var item = stack.save(new CompoundTag());
      item.putInt("Slot", slot.ordinal());
      items.add(item);
      appearance.put("SecondEquipment", items);
      setColonyAppearance(appearance);
    }
  }

  private static EquipmentSlot equipmentSlot(int slot) {
    return switch (slot % 3) {
      case 0 -> EquipmentSlot.MAINHAND;
      case 1 -> EquipmentSlot.OFFHAND;
      default -> EquipmentSlot.CHEST;
    };
  }

  @Override
  public ItemStack getEquipment(int slot) {
    return memberEquipment(slot / 3, equipmentSlot(slot));
  }

  @Override
  public void setEquipment(int slot, ItemStack stack) {
    setMemberEquipment(slot / 3, equipmentSlot(slot), stack);
  }

  @Override
  public ItemStack getItemBySlot(EquipmentSlot slot) {
    return memberEquipment(level().isClientSide && isRenderingSecondMember() ? 1 : 0, slot);
  }

  @Override
  public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    setMemberEquipment(0, slot, stack);
  }

  private void refreshEquipmentAppearance() {
    if (!members.ready()) return;
    var appearance = colonyAppearance().copy();
    var list = new ListTag();
    for (var slot : EquipmentSlot.values()) {
      var item = members.records().get(1).getItemBySlot(slot).save(new CompoundTag());
      item.putInt("Slot", slot.ordinal());
      list.add(item);
    }
    appearance.put("SecondEquipment", list);
    setColonyAppearance(appearance);
  }

  @Override
  public boolean needsReplacementTool() {
    return isWorking() && (!isSword(getEquipment(0)) || !isSword(getEquipment(3)));
  }

  @Override
  public boolean receiveWorkTool(ItemStack stack) {
    if (!needsReplacementTool() || !acceptsWorkTool(stack)) return false;
    setEquipment(!isSword(getEquipment(0)) ? 0 : 3, stack.copyWithCount(1));
    return true;
  }

  @Override
  protected WorkStatus describeWork() {
    var result = super.describeWork();
    var status = getCombatStatus();
    if (status == Status.MOVING || status == Status.FIGHTING)
      result =
          new WorkStatus(
              result.order(), WorkStatus.State.ACTIVE, WorkStatus.Reason.NONE, result.requests());
    if (!needsReplacementTool()) return result;
    var requests = new java.util.HashSet<>(result.requests());
    requests.add(WorkStatus.Request.TOOL_SUPPLY);
    return new WorkStatus(result.order(), result.state(), result.reason(), requests);
  }

  @Override
  protected boolean combatBusy() {
    return super.combatBusy() || getLinkedBody() != null && getLinkedBody().hasCombatTarget();
  }

  @Override
  public Status getCombatStatus() {
    Status first = super.getCombatStatus();
    var body = getLinkedBody();
    if (isWorking() && body != null && body.hasCombatTarget() && first != Status.FIGHTING)
      return body.distanceToSqr(body.getTarget()) <= Math.pow(SlimeStat.REACH.value(this), 2)
          ? Status.FIGHTING
          : Status.MOVING;
    return first;
  }

  @Override
  public boolean hurt(DamageSource source, float amount) {
    return damageBody == null && super.hurt(source, amount);
  }

  boolean activeBody(CombatColonyBody body) {
    return body != null
        && body == getLinkedBody()
        && body.isAddedToWorld()
        && body.getColony() == this
        && level() instanceof ServerLevel server
        && server.isPositionEntityTicking(body.blockPosition())
        && distanceToSqr(body) <= getLinkRange() * getLinkRange();
  }

  boolean canFight(CombatColonyBody body, LivingEntity target) {
    return activeBody(body)
        && isWorking()
        && isTame()
        && !isPendingSpawn()
        && isAlive()
        && mayAttack(target)
        && getStamina() >= 1
        && isSword(body.getMainHandItem());
  }

  boolean strikeFromBody(CombatColonyBody body, LivingEntity target, boolean critical) {
    if (!canFight(body, target)) return false;
    body.stopUsingItem();
    boolean hit = body.nativeStrike(target, critical);
    if (hit) {
      body.getMainHandItem().getItem().hurtEnemy(body.getMainHandItem(), target, body);
      spendStamina(1);
      recordCompletedWork();
      members.syncVitals();
      refreshEquipmentAppearance();
    }
    return hit;
  }

  @Override
  public boolean doHurtTarget(Entity target) {
    return !hasCombatMotion() && super.doHurtTarget(target);
  }

  boolean assistStrike(LivingEntity target, boolean second) {
    if (second) return strikeFromBody(getLinkedBody(), target, true);
    if (!isWorking() || !isAlive() || !mayAttack(target)) return false;
    var attack = getAttribute(Attributes.ATTACK_DAMAGE);
    var modifier = ColonyAssistCharge.criticalModifier();
    attack.addTransientModifier(modifier);
    try {
      return super.doHurtTarget(target);
    } finally {
      attack.removeModifier(modifier);
    }
  }

  /** Damage still commits through the one saved life pool, using the body that was actually hit. */
  boolean hurtFromBody(CombatColonyBody body, DamageSource source, float amount) {
    if (!activeBody(body) || damageBody != null) return false;
    damageBody = body;
    try {
      return super.hurt(source, amount);
    } finally {
      damageBody = null;
      members.syncVitals();
    }
  }

  @Override
  public int getArmorValue() {
    return damageBody != null ? damageBody.getArmorValue() : super.getArmorValue();
  }

  @Override
  public double getAttributeValue(Attribute attribute) {
    return damageBody != null
            && (attribute == Attributes.ARMOR_TOUGHNESS
                || attribute == Attributes.KNOCKBACK_RESISTANCE)
        ? damageBody.getAttributeValue(attribute)
        : super.getAttributeValue(attribute);
  }

  @Override
  public Iterable<ItemStack> getArmorSlots() {
    return damageBody != null
        ? damageBody.getArmorSlots()
        : List.of(
            getItemBySlot(EquipmentSlot.FEET),
            getItemBySlot(EquipmentSlot.LEGS),
            getItemBySlot(EquipmentSlot.CHEST),
            getItemBySlot(EquipmentSlot.HEAD));
  }

  @Override
  public Iterable<ItemStack> getHandSlots() {
    return List.of(getMainHandItem(), getOffhandItem());
  }

  @Override
  public boolean isDamageSourceBlocked(DamageSource source) {
    return damageBody != null ? damageBody.blocks(source) : super.isDamageSourceBlocked(source);
  }

  @Override
  protected void hurtArmor(DamageSource source, float damage) {
    if (damageBody != null) damageBody.damageArmor(source, damage);
    else super.hurtArmor(source, damage);
  }

  @Override
  protected void hurtCurrentlyUsedShield(float damage) {
    if (damageBody != null) damageBody.damageShield(damage);
    else super.hurtCurrentlyUsedShield(damage);
  }

  @Override
  public void blockUsingShield(LivingEntity attacker) {
    if (damageBody != null) damageBody.blockUsingShield(attacker);
    else super.blockUsingShield(attacker);
  }

  @Override
  public void knockback(double strength, double x, double z) {
    if (damageBody != null) damageBody.knockback(strength, x, z);
    else super.knockback(strength, x, z);
  }

  @Override
  public boolean isWithinManagementRange(Player player, double squaredRange) {
    if (super.isWithinManagementRange(player, squaredRange)) return true;
    var body = getLinkedBody();
    return body != null
        && body.isAddedToWorld()
        && body.getColony() == this
        && player.level() == level()
        && player.distanceToSqr(body) <= squaredRange
        && distanceToSqr(body) <= getLinkRange() * getLinkRange();
  }

  private void stopLinkedCombat() {
    if (assist != null) assist.cancel();
    var body = getLinkedBody();
    if (body != null) body.stopCombat();
  }

  @Override
  public void setWaiting(boolean waiting) {
    super.setWaiting(waiting);
    if (waiting) stopLinkedCombat();
  }

  @Override
  public void setWorking(boolean value) {
    super.setWorking(value);
    stopLinkedCombat();
  }

  @Override
  public void setAllowed(CombatTarget kind, boolean enabled) {
    super.setAllowed(kind, enabled);
    if (assist != null) assist.cancelIfInvalid();
    var body = getLinkedBody();
    if (body != null && body.getTarget() != null && !mayAttack(body.getTarget())) body.stopCombat();
  }

  private void removeLinkedBody() {
    syncCombatTiming();
    stopLinkedCombat();
    var previous = linkedBody;
    linkedBody = null;
    entityData.set(LINKED_BODY, -1);
    if (previous != null && !previous.isRemoved()) previous.discard();
    returnTicks = 0;
  }

  @Override
  public void remove(RemovalReason reason) {
    super.remove(reason);
    if (!level().isClientSide) removeLinkedBody();
  }

  @Override
  public void onReleased() {
    super.onReleased();
    if (!level().isClientSide) {
      removeLinkedBody();
      nextBodyAttempt = tickCount;
    }
  }

  private void ensureLinkedBody(ServerLevel server) {
    if (getLinkedBody() != null
        || admittingBody
        || tickCount < nextBodyAttempt
        || !members.ready()
        || isPendingSpawn()
        || !isAddedToWorld()
        || !isAlive()
        || !io.github.meistermods.slimic.world.ColonyBondingData.get(server).mayJoin(this)) return;
    removeLinkedBody();
    nextBodyAttempt = tickCount + 20;
    var body = ModEntities.COMBAT_COLONY_BODY.get().create(server);
    if (body == null) return;
    Vec3 landing = CompanionTeleport.landing(server, body, blockPosition(), getId(), 3);
    if (landing != null && landing.subtract(position()).horizontalDistanceSqr() < 2.25) {
      landing = null;
      for (int[] offset :
          new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2}}) {
        Vec3 candidate = Vec3.atBottomCenterOf(blockPosition().offset(offset[0], 0, offset[1]));
        if (CompanionTeleport.safe(server, body, candidate)) {
          landing = candidate;
          break;
        }
      }
    }
    if (landing == null) return;
    var owner = getOwnerUUID();
    body.moveTo(landing.x, landing.y, landing.z, getYRot(), 0);
    body.setColony(this);
    body.setCombatTiming(((CombatSlime) members.records().get(1)).combatTiming());
    linkedBody = body;
    admittingBody = true;
    try {
      if (!server.addFreshEntity(body)
          || !body.isAddedToWorld()
          || body.isRemoved()
          || body.level() != server
          || getLinkedBody() != body
          || body.getColony() != this
          || !isAddedToWorld()
          || !isAlive()
          || isRemoved()
          || isPendingSpawn()
          || !Objects.equals(owner, getOwnerUUID())
          || body.distanceToSqr(this) > getLinkRange() * getLinkRange()
          || !CompanionTeleport.safe(server, body, body.position())) {
        if (linkedBody == body) removeLinkedBody();
        else body.discard();
        return;
      }
      entityData.set(LINKED_BODY, body.getId());
    } finally {
      admittingBody = false;
    }
  }

  private void followLinkedBody() {
    var body = getLinkedBody();
    if (body == null) return;
    double distance = distanceToSqr(body);
    boolean stretched = distance > getLinkRange() * getLinkRange();
    if (stretched) {
      stopLinkedCombat();
      getNavigation().stop();
    }
    if (!isOrderedToSit()
        && !body.hasCombatTarget()
        && assistMember() != 2
        && distance > 9
        && body.tickCount % 10 == 0
        && body.onGround()) body.getNavigation().moveTo(this, 1.1);
    if (stretched && ++returnTicks >= 100) {
      removeLinkedBody();
      nextBodyAttempt = tickCount + 1;
    } else if (!stretched) returnTicks = 0;
  }

  @Override
  public void tick() {
    super.tick();
    if (level() instanceof ServerLevel server && isAlive() && !isRemoved()) {
      ensureLinkedBody(server);
      followLinkedBody();
      assist.tick(server);
      if (tickCount % 4 == 0) refreshEquipmentAppearance();
    }
    members.syncVitals();
  }

  private void syncCombatTiming() {
    if (level().isClientSide || members == null || !members.ready()) return;
    ((CombatSlime) members.records().get(0)).setCombatTiming(combatTiming());
    var body = getLinkedBody();
    if (body != null) ((CombatSlime) members.records().get(1)).setCombatTiming(body.combatTiming());
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    syncCombatTiming();
    members.syncVitals();
    super.addAdditionalSaveData(tag);
    tag.put("ColonyMembers", members.save());
    tag.remove("HandItems");
    tag.remove("ArmorItems");
    tag.putInt("ColonyAssistCooldown", assist.cooldown());
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    if (!level().isClientSide) removeLinkedBody();
    members.clear();
    super.readAdditionalSaveData(tag);
    members.load(tag.getCompound("ColonyMembers"));
    assist.loadCooldown(tag.getInt("ColonyAssistCooldown"));
  }
}
