package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.item.SlimeArmorItem;
import java.util.List;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A separately moving fighter backed by member two's actual equipment and the shared life pool. */
public final class CombatColonyBody extends LinkedColonyBody<BoundCombatSlime> {
  private int attackCooldown, shieldCooldown, defenseCooldown;
  private boolean resting, blockedHit;

  public CombatColonyBody(EntityType<? extends CombatColonyBody> type, Level level) {
    super(type, level, BoundCombatSlime.class);
    setMaxUpStep(1);
  }

  @Override
  public ItemStack getItemBySlot(EquipmentSlot slot) {
    var colony = getColony();
    return colony == null ? super.getItemBySlot(slot) : colony.memberEquipment(1, slot);
  }

  @Override
  public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    var colony = getColony();
    if (colony == null) super.setItemSlot(slot, stack);
    else colony.setMemberEquipment(1, slot, stack);
  }

  @Override
  public Iterable<ItemStack> getArmorSlots() {
    return List.of(
        ItemStack.EMPTY, ItemStack.EMPTY, getItemBySlot(EquipmentSlot.CHEST), ItemStack.EMPTY);
  }

  @Override
  public Iterable<ItemStack> getHandSlots() {
    return List.of(getMainHandItem(), getOffhandItem());
  }

  @Override
  public double getAttributeValue(net.minecraft.world.entity.ai.attributes.Attribute attribute) {
    return attribute == Attributes.ATTACK_DAMAGE
        ? CreativeStats.asFloat(CreativeStats.attributeValue(getAttribute(attribute)))
        : super.getAttributeValue(attribute);
  }

  @Override
  public boolean hurt(DamageSource source, float amount) {
    var colony = getColony();
    if (level().isClientSide || colony == null || isRemoved()) return false;
    blockedHit = false;
    boolean damaged = colony.hurtFromBody(this, source, amount);
    if ((damaged || blockedHit)
        && source.getEntity() instanceof LivingEntity attacker
        && !colony.isAlliedTo(attacker)) setLastHurtByMob(attacker);
    if (damaged && !isRemoved()) {
      hurtDuration = hurtTime = 10;
      level().broadcastEntityEvent(this, (byte) 2);
    }
    return damaged;
  }

  boolean blocks(DamageSource source) {
    return super.isDamageSourceBlocked(source);
  }

  void damageArmor(DamageSource source, float damage) {
    ItemStack armor = getItemBySlot(EquipmentSlot.CHEST);
    if (armor.getItem() instanceof SlimeArmorItem
        && (!source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
            || !SlimeArmorItem.baseArmor(armor).getItem().isFireResistant()))
      armor.hurtAndBreak(
          Math.max(1, (int) (damage / 4)),
          this,
          body -> body.broadcastBreakEvent(EquipmentSlot.CHEST));
  }

  void damageShield(float damage) {
    blockedHit = true;
    if (defenseCooldown == 0 && getColony() != null) {
      getColony().recordCompletedWork();
      defenseCooldown = 20;
    }
    if (getUseItem().canPerformAction(net.minecraftforge.common.ToolActions.SHIELD_BLOCK)
        && damage >= 3)
      getUseItem()
          .hurtAndBreak(
              1 + (int) damage,
              this,
              body -> {
                body.broadcastBreakEvent(EquipmentSlot.OFFHAND);
                body.stopUsingItem();
              });
  }

  @Override
  public void blockUsingShield(LivingEntity attacker) {
    super.blockUsingShield(attacker);
    if (attacker.getMainHandItem().canDisableShield(getUseItem(), this, attacker)) {
      shieldCooldown = 100;
      stopUsingItem();
      level().broadcastEntityEvent(this, (byte) 30);
    }
  }

  boolean nativeStrike(LivingEntity victim, boolean critical) {
    var colony = getColony();
    if (colony == null) return false;
    getAttribute(Attributes.ATTACK_DAMAGE)
        .setBaseValue(colony.getAttributeBaseValue(Attributes.ATTACK_DAMAGE));
    var modifier = ColonyAssistCharge.criticalModifier();
    if (critical) getAttribute(Attributes.ATTACK_DAMAGE).addTransientModifier(modifier);
    try {
      return super.doHurtTarget(victim);
    } finally {
      if (critical) getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(modifier);
    }
  }

  net.minecraft.nbt.CompoundTag combatTiming() {
    var tag = new net.minecraft.nbt.CompoundTag();
    tag.putInt("CombatAttackCooldown", attackCooldown);
    tag.putInt("CombatShieldCooldown", shieldCooldown);
    tag.putBoolean("CombatResting", resting);
    return tag;
  }

  void setCombatTiming(net.minecraft.nbt.CompoundTag tag) {
    attackCooldown = Math.max(0, Math.min(200, tag.getInt("CombatAttackCooldown")));
    shieldCooldown = Math.max(0, Math.min(100, tag.getInt("CombatShieldCooldown")));
    resting = tag.getBoolean("CombatResting");
  }

  public boolean hasCombatTarget() {
    return getTarget() != null && getTarget().isAlive();
  }

  public void stopCombat() {
    setTarget(null);
    stopUsingItem();
    getNavigation().stop();
    setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
    setSpeed(0);
    setZza(0);
    setXxa(0);
  }

  private void equipSpares(BoundCombatSlime colony) {
    for (int equip = 0; equip < 3; equip++) {
      if (!colony.getEquipment(3 + equip).isEmpty()) continue;
      for (int slot : colony.getBag().occupiedSlots())
        if (CombatSlime.matchesEquipment(equip, colony.getBag().getStackInSlot(slot))) {
          colony.setEquipment(3 + equip, colony.getBag().extractItem(slot, 1, false));
          break;
        }
    }
  }

  @Override
  public void tick() {
    super.tick();
    if (level().isClientSide || isRemoved()) return;
    var colony = getColony();
    if (colony == null) return;
    getAttribute(Attributes.ARMOR).setBaseValue(colony.getAttributeBaseValue(Attributes.ARMOR));
    if (isNoAi()) return;
    if (attackCooldown > 0) attackCooldown--;
    if (shieldCooldown > 0) shieldCooldown--;
    if (defenseCooldown > 0) defenseCooldown--;
    if (colony.isRegrouping() && !colony.isOrderedToSit()) {
      setTarget(null);
      stopUsingItem();
      return;
    }
    if (!colony.isWorking() || colony.isFleeing() || !colony.isTame() || !colony.activeBody(this)) {
      stopCombat();
      return;
    }
    equipSpares(colony);
    if (colony.isBodyInMotion(this)) {
      getNavigation().stop();
      stopUsingItem();
      return;
    }
    if (colony.getStamina() < 1) resting = true;
    if (resting && colony.getStamina() < colony.getMaxStamina() * .25F) {
      stopCombat();
      return;
    }
    resting = false;
    double range = SlimeStat.DETECTION_RANGE.value(colony);
    if (getTarget() != null
        && (!colony.mayAttack(getTarget()) || distanceToSqr(getTarget()) > range * range))
      setTarget(null);
    if (!CombatSlime.isSword(getMainHandItem())) {
      var threat = getLastHurtByMob();
      setTarget(null);
      getNavigation().stop();
      if (threat != null && threat.isAlive() && colony.mayAttack(threat)) {
        float yaw =
            (float)
                (Math.toDegrees(Math.atan2(threat.getZ() - getZ(), threat.getX() - getX())) - 90);
        setYRot(yaw);
        yBodyRot = yHeadRot = yaw;
        getLookControl().setLookAt(threat);
        if (shieldCooldown == 0
            && CombatSlime.matchesEquipment(1, getOffhandItem())
            && !isUsingItem()) startUsingItem(InteractionHand.OFF_HAND);
      } else stopUsingItem();
      return;
    } else if (getTarget() == null && tickCount % 10 == 0) {
      LivingEntity nearest = null;
      double distance = range * range;
      for (var candidate :
          WorkSearch.entities(level(), LivingEntity.class, getBoundingBox().inflate(range))) {
        double next = distanceToSqr(candidate);
        if (next <= distance && colony.mayAttack(candidate) && hasLineOfSight(candidate)) {
          nearest = candidate;
          distance = next;
        }
      }
      setTarget(nearest);
      if (nearest != null) colony.linkedTargetAcquired(nearest);
    }
    var target = getTarget();
    if (colony.isBodyInMotion(this)) return;
    if (target == null) {
      stopUsingItem();
      colony.collectLoot(this, true);
      return;
    }
    getLookControl().setLookAt(target, 30, 30);
    double reach = SlimeStat.REACH.value(colony);
    if (distanceToSqr(target) <= reach * reach && hasLineOfSight(target)) {
      float yaw =
          (float) (Math.toDegrees(Math.atan2(target.getZ() - getZ(), target.getX() - getX())) - 90);
      setYRot(yaw);
      yBodyRot = yHeadRot = yaw;
      getNavigation().stop();
      if (attackCooldown == 0) {
        colony.strikeFromBody(this, target, false);
        swing(InteractionHand.MAIN_HAND);
        attackCooldown =
            Math.max(
                CombatSlime.matchesEquipment(1, getOffhandItem()) ? 10 : 4,
                (int) Math.ceil(20 / SlimeStat.WORK_SPEED.value(colony)));
      }
    } else if (onGround()
        && tickCount % 10 == 0
        && (getNavigation().isDone()
            || getNavigation().getTargetPos() == null
            || getNavigation().getTargetPos().distSqr(target.blockPosition()) > 4))
      getNavigation().moveTo(target, 1);
    if (shieldCooldown == 0
        && CombatSlime.matchesEquipment(1, getOffhandItem())
        && attackCooldown > 0) {
      if (!isUsingItem()) startUsingItem(InteractionHand.OFF_HAND);
    } else stopUsingItem();
  }
}
