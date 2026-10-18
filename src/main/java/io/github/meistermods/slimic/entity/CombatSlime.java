package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.item.SlimeArmorItem;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.EnumSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.items.ItemHandlerHelper;

public class CombatSlime extends BagSlime {
  public static final String LOOT_COLLECTOR = "SlimicCombatCollector";

  public enum Status {
    IDLE,
    MOVING,
    FIGHTING,
    NO_SWORD,
    RESTING,
    WAITING,
    FOLLOWING,
    UNREACHABLE
  }

  private static final EntityDataAccessor<Integer> FILTERS =
      SynchedEntityData.defineId(CombatSlime.class, EntityDataSerializers.INT);
  private boolean working;
  private boolean resting;
  private Status status = Status.IDLE;
  private int attackCooldown;
  private int shieldCooldown;
  private int travelTicks;
  private Vec3 lastTravelPosition;
  private Vec3 pathDestination;

  public CombatSlime(EntityType<? extends CombatSlime> type, Level level) {
    super(type, level, 27);
    for (EquipmentSlot slot :
        new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.CHEST})
      setDropChance(slot, 2);
    setMaxUpStep(1);
  }

  @Override
  protected void defineSynchedData() {
    super.defineSynchedData();
    entityData.define(FILTERS, 1 << CombatTarget.HOSTILE.ordinal());
  }

  @Override
  protected void registerGoals() {
    super.registerGoals();
    goalSelector.addGoal(1, new FightGoal());
  }

  @Override
  protected String bagTagName() {
    return "CombatBag";
  }

  public static boolean isSword(ItemStack stack) {
    return stack.getItem() instanceof SwordItem;
  }

  public static boolean matchesEquipment(int slot, ItemStack stack) {
    return switch (slot) {
      case 0 -> isSword(stack);
      case 1 -> !stack.isEmpty() && stack.canPerformAction(ToolActions.SHIELD_BLOCK);
      case 2 -> stack.getItem() instanceof SlimeArmorItem;
      default -> false;
    };
  }

  public ItemStack getEquipment(int slot) {
    return getItemBySlot(equipmentSlot(slot));
  }

  public void setEquipment(int slot, ItemStack stack) {
    setItemSlot(equipmentSlot(slot), stack);
  }

  private static EquipmentSlot equipmentSlot(int slot) {
    return slot == 0
        ? EquipmentSlot.MAINHAND
        : slot == 1 ? EquipmentSlot.OFFHAND : EquipmentSlot.CHEST;
  }

  public boolean allows(CombatTarget kind) {
    return (entityData.get(FILTERS) & (1 << kind.ordinal())) != 0;
  }

  public void setAllowed(CombatTarget kind, boolean enabled) {
    int bit = 1 << kind.ordinal();
    entityData.set(
        FILTERS, enabled ? entityData.get(FILTERS) | bit : entityData.get(FILTERS) & ~bit);
    if (getTarget() != null && !mayAttack(getTarget())) {
      setTarget(null);
      stopUsingItem();
      getNavigation().stop();
      resetTravel();
    }
  }

  public static CombatTarget targetKind(LivingEntity target) {
    if (target instanceof Player) return CombatTarget.PLAYERS;
    if ((target instanceof TamableAnimal tame && tame.isTame())
        || (target instanceof AbstractHorse horse && horse.isTamed())) return CombatTarget.TAMED;
    if (target instanceof Creeper) return CombatTarget.CREEPERS;
    if (target instanceof EnderMan enderman
        && enderman.getTarget() instanceof FriendlySlime companion
        && companion.isAlive()) return CombatTarget.HOSTILE;
    if (target instanceof NeutralMob) return CombatTarget.NEUTRAL;
    return target instanceof Enemy ? CombatTarget.HOSTILE : CombatTarget.PASSIVE;
  }

  public boolean mayAttack(LivingEntity candidate) {
    if (candidate == this
        || !candidate.isAlive()
        || candidate.isSpectator()
        || !canAttack(candidate)
        || candidate.getUUID().equals(getOwnerUUID())
        || isAlliedTo(candidate)) return false;
    if (candidate instanceof Player player) {
      if (player.getAbilities().instabuild
          || (level().getServer() != null && !level().getServer().isPvpAllowed())
          || (getOwner() instanceof Player owner && !owner.canHarmPlayer(player))) return false;
    }
    if (candidate instanceof TamableAnimal tame
        && getOwnerUUID() != null
        && CompanionOwnership.shares(level(), getOwnerUUID(), tame.getOwnerUUID())) return false;
    if (candidate instanceof AbstractHorse horse
        && getOwnerUUID() != null
        && CompanionOwnership.shares(level(), getOwnerUUID(), horse.getOwnerUUID())) return false;
    // Only actual mobs and players are targets; armor stands and decorative entities are excluded.
    return (candidate instanceof Mob || candidate instanceof Player)
        && allows(
            candidate instanceof Player player
                    && level() instanceof ServerLevel server
                    && CompanionRegistry.get(server.getServer())
                        .enemy(getOwnerUUID(), player.getUUID())
                ? CombatTarget.HOSTILE
                : targetKind(candidate));
  }

  @Override
  public boolean isWorking() {
    return working && !isOrderedToSit();
  }

  @Override
  public void setWorking(boolean value) {
    working = value;
    setWaiting(false);
    setTarget(null);
    stopUsingItem();
    getNavigation().stop();
    status = Status.IDLE;
    resetTravel();
  }

  private void resetTravel() {
    travelTicks = 0;
    lastTravelPosition = null;
    pathDestination = null;
  }

  public Status getCombatStatus() {
    return isOrderedToSit() ? Status.WAITING : working ? status : Status.FOLLOWING;
  }

  @Override
  protected WorkStatus describeWork() {
    var order = WorkStatus.Order.FIGHT;
    WorkStatus result =
        getMainHandItem().isEmpty()
            ? WorkStatus.blocked(
                order, WorkStatus.Reason.TOOL_MISSING, WorkStatus.Request.TOOL_SUPPLY)
            : switch (status) {
              case NO_SWORD -> WorkStatus.blocked(order, WorkStatus.Reason.TOOL_MISSING);
              case RESTING -> WorkStatus.blocked(order, WorkStatus.Reason.EXHAUSTED);
              case UNREACHABLE -> WorkStatus.blocked(order, WorkStatus.Reason.UNREACHABLE);
              default ->
                  WorkStatus.of(
                      order,
                      status == Status.IDLE ? WorkStatus.State.IDLE : WorkStatus.State.ACTIVE);
            };
    if (!hasCargo()) return result;
    var requests = new java.util.HashSet<>(result.requests());
    requests.add(WorkStatus.Request.CARGO_PICKUP);
    return new WorkStatus(result.order(), result.state(), result.reason(), requests);
  }

  protected boolean mayNavigate() {
    return onGround();
  }

  private boolean ownLoot(ItemEntity item) {
    CompoundTag data = item.saveWithoutId(new CompoundTag());
    return item.isAlive()
        && !item.hasPickUpDelay()
        && item.getPersistentData().hasUUID(LOOT_COLLECTOR)
        && getUUID().equals(item.getPersistentData().getUUID(LOOT_COLLECTOR))
        && (!data.hasUUID("Owner")
            || CompanionOwnership.cooperates(level(), data.getUUID("Owner"), getOwnerUUID()))
        && ItemHandlerHelper.insertItemStacked(getBag(), item.getItem().copy(), true).getCount()
            < item.getItem().getCount();
  }

  private boolean collectLoot(boolean move) {
    double range = SlimeStat.DETECTION_RANGE.value(this);
    ItemEntity nearest = null;
    double distance = range * range;
    for (var item : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(range))) {
      if (!ownLoot(item) || !hasLineOfSight(item)) continue;
      double next = distanceToSqr(item);
      if (next <= SlimeStat.REACH.value(this) * SlimeStat.REACH.value(this)) {
        var player =
            net.minecraftforge.common.util.FakePlayerFactory.get(
                (net.minecraft.server.level.ServerLevel) level(),
                new com.mojang.authlib.GameProfile(getOwnerUUID(), "SlimeCollector"));
        player.moveTo(position());
        if (net.minecraftforge.event.ForgeEventFactory.onItemPickup(item, player) < 0
            || !canContinueWork(player)
            || !ownLoot(item)) continue;
        var before = item.getItem().copy();
        var remaining = ItemHandlerHelper.insertItemStacked(getBag(), before.copy(), false);
        int moved = before.getCount() - remaining.getCount();
        if (moved > 0) {
          take(item, moved);
          item.setItem(remaining);
          net.minecraftforge.event.ForgeEventFactory.firePlayerItemPickupEvent(
              player, item, before.copyWithCount(moved));
        }
        if (remaining.isEmpty()) item.discard();
        else item.setItem(remaining);
      } else if (next < distance) {
        distance = next;
        nearest = item;
      }
    }
    if (move && nearest != null && mayNavigate() && tickCount % 10 == 0)
      getNavigation().moveTo(nearest, 1);
    return nearest != null;
  }

  @Override
  public boolean acceptsWorkTool(ItemStack stack) {
    return isSword(stack)
        && (!stack.isDamageableItem() || stack.getDamageValue() < stack.getMaxDamage());
  }

  @Override
  public boolean receiveWorkTool(ItemStack stack) {
    if (!needsReplacementTool() || !acceptsWorkTool(stack)) return false;
    setEquipment(0, stack.copyWithCount(1));
    return true;
  }

  @Override
  public boolean doHurtTarget(Entity target) {
    if (!(target instanceof LivingEntity victim)
        || !isWorking()
        || !mayAttack(victim)
        || !isSword(getMainHandItem())
        || getStamina() < 1) return false;
    stopUsingItem();
    boolean hit = super.doHurtTarget(target);
    if (hit) {
      getMainHandItem().getItem().hurtEnemy(getMainHandItem(), victim, this);
      spendStamina(1);
      recordCompletedWork();
      resetTravel();
    }
    return hit;
  }

  @Override
  protected void hurtArmor(DamageSource source, float damage) {
    ItemStack armor = getItemBySlot(EquipmentSlot.CHEST);
    if (armor.getItem() instanceof SlimeArmorItem
        && (!source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
            || !SlimeArmorItem.baseArmor(armor).getItem().isFireResistant()))
      armor.hurtAndBreak(
          Math.max(1, (int) (damage / 4)),
          this,
          slime -> slime.broadcastBreakEvent(EquipmentSlot.CHEST));
  }

  @Override
  protected void hurtCurrentlyUsedShield(float damage) {
    super.hurtCurrentlyUsedShield(damage);
    if (getUseItem().canPerformAction(ToolActions.SHIELD_BLOCK) && damage >= 3)
      getUseItem()
          .hurtAndBreak(
              1 + (int) damage,
              this,
              slime -> {
                slime.broadcastBreakEvent(EquipmentSlot.OFFHAND);
                slime.stopUsingItem();
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

  private void equipSpares() {
    for (int equip = 0; equip < 3; equip++) {
      if (!getEquipment(equip).isEmpty()) continue;
      for (int slot = 0; slot < getBag().getSlots(); slot++)
        if (matchesEquipment(equip, getBag().getStackInSlot(slot))) {
          setEquipment(equip, getBag().extractItem(slot, 1, false));
          break;
        }
    }
  }

  @Override
  public void tick() {
    super.tick();
    if (!level().isClientSide) {
      if (isWorking() && isTame() && getTarget() != null && tickCount % 5 == 0) collectLoot(false);
      if (attackCooldown > 0) attackCooldown--;
      if (shieldCooldown > 0) shieldCooldown--;
      if (isWorking() && tickCount % 20 == 0 && status != Status.FIGHTING)
        recoverStamina(getStaminaRecoveryPerSecond());
    }
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putBoolean("CombatWorking", working);
    tag.putBoolean("CombatResting", resting);
    tag.putInt("CombatFilters", entityData.get(FILTERS));
    tag.putInt("CombatAttackCooldown", attackCooldown);
    tag.putInt("CombatShieldCooldown", shieldCooldown);
    tag.putInt("CombatStatus", status.ordinal());
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    working = tag.getBoolean("CombatWorking");
    if (!tag.contains("CombatWorking")) setWaiting(true);
    resting = tag.getBoolean("CombatResting");
    entityData.set(FILTERS, tag.contains("CombatFilters") ? tag.getInt("CombatFilters") & 63 : 1);
    attackCooldown = Math.max(0, Math.min(200, tag.getInt("CombatAttackCooldown")));
    shieldCooldown = Math.max(0, Math.min(100, tag.getInt("CombatShieldCooldown")));
    int saved = tag.getInt("CombatStatus");
    status = saved >= 0 && saved < Status.values().length ? Status.values()[saved] : Status.IDLE;
    setTarget(null);
    resetTravel();
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.COMBAT_SLIME.get());
  }

  private final class FightGoal extends Goal {
    FightGoal() {
      setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
      return isWorking() && isTame();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
      return true;
    }

    @Override
    public void stop() {
      setTarget(null);
      stopUsingItem();
      getNavigation().stop();
      resetTravel();
    }

    @Override
    public void tick() {
      if (!canUse()) {
        stop();
        return;
      }
      equipSpares();
      if (!isSword(getMainHandItem())) {
        status = Status.NO_SWORD;
        LivingEntity threat = getLastHurtByMob();
        if (threat != null && threat.isAlive() && mayAttack(threat)) {
          setYRot(
              (float)
                  (Math.toDegrees(Math.atan2(threat.getZ() - getZ(), threat.getX() - getX()))
                      - 90));
          yBodyRot = yHeadRot = getYRot();
          if (shieldCooldown == 0 && matchesEquipment(1, getOffhandItem()) && !isUsingItem())
            startUsingItem(InteractionHand.OFF_HAND);
        } else stopUsingItem();
        setTarget(null);
        resetTravel();
        getNavigation().stop();
        return;
      }
      if (getStamina() < 1) resting = true;
      if (resting && getStamina() < getMaxStamina() * .25F) {
        status = Status.RESTING;
        setTarget(null);
        resetTravel();
        stopUsingItem();
        getNavigation().stop();
        return;
      }
      resting = false;
      double range = SlimeStat.DETECTION_RANGE.value(CombatSlime.this);
      if (getTarget() != null
          && (!mayAttack(getTarget()) || distanceToSqr(getTarget()) > range * range)) {
        setTarget(null);
        resetTravel();
      }
      if (getTarget() == null && tickCount % 10 == 0) {
        LivingEntity nearest = null;
        double distance = range * range;
        for (LivingEntity candidate :
            level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range))) {
          double next = distanceToSqr(candidate);
          if (next <= distance && mayAttack(candidate) && getSensing().hasLineOfSight(candidate)) {
            nearest = candidate;
            distance = next;
          }
        }
        setTarget(nearest);
      }
      LivingEntity victim = getTarget();
      if (victim == null) {
        resetTravel();
        status = Status.IDLE;
        stopUsingItem();
        if (!collectLoot(true)) getNavigation().stop();
        return;
      }
      getLookControl().setLookAt(victim, 30, 30);
      // Shield coverage uses body yaw, not the independent look controller.
      setYRot(
          (float)
              (Math.toDegrees(Math.atan2(victim.getZ() - getZ(), victim.getX() - getX())) - 90));
      yBodyRot = yHeadRot = getYRot();
      double reach = SlimeStat.REACH.value(CombatSlime.this);
      if (distanceToSqr(victim) <= reach * reach && getSensing().hasLineOfSight(victim)) {
        status = Status.FIGHTING;
        getNavigation().stop();
        resetTravel();
        if (attackCooldown == 0) {
          doHurtTarget(victim);
          swing(InteractionHand.MAIN_HAND);
          attackCooldown =
              Math.max(
                  matchesEquipment(1, getOffhandItem()) ? 10 : 4,
                  (int) Math.ceil(20 / SlimeStat.WORK_SPEED.value(CombatSlime.this)));
        }
      } else {
        if (lastTravelPosition == null
            || position().subtract(lastTravelPosition).horizontalDistanceSqr() >= .0025
            || (onGround() && Math.abs(getY() - lastTravelPosition.y) >= .5)) {
          lastTravelPosition = position();
          travelTicks = 0;
        } else travelTicks++;
        status = travelTicks > 400 ? Status.UNREACHABLE : Status.MOVING;
        if (tickCount % 10 == 0
            && mayNavigate()
            && (getNavigation().isDone()
                || pathDestination == null
                || pathDestination.distanceToSqr(victim.position()) > 1)) {
          getNavigation().moveTo(victim, 1);
          pathDestination = victim.position();
        }
      }
      if (shieldCooldown == 0
          && matchesEquipment(1, getOffhandItem())
          && (status != Status.FIGHTING || attackCooldown > 0)) {
        if (!isUsingItem()) startUsingItem(InteractionHand.OFF_HAND);
      } else stopUsingItem();
    }
  }
}
