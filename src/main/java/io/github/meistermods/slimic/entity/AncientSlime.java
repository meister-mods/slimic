package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.block.SlimeChestBlockEntity;
import io.github.meistermods.slimic.item.AncientSlimeCoreItem;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.items.ItemHandlerHelper;

/** A remnant of the old kingdom, bound to its own ruin and never a player-owned companion. */
public final class AncientSlime extends Monster {
  public enum Role {
    GUARD,
    TRANSPORT,
    COMMANDER
  }

  public static final String DEPOT_KEY = "SlimicAncientDepot";
  private BlockPos home, sourceDepot, destinationDepot;
  private UUID ruin;
  private ItemStack parcel = ItemStack.EMPTY;
  private boolean returning, coreDropped;
  private int attackCooldown, patrolStep, experience;
  private Vec3 pathDestination;

  public AncientSlime(EntityType<? extends AncientSlime> type, Level level) {
    super(type, level);
    setPersistenceRequired();
    setMaxUpStep(1);
    moveControl =
        new MoveControl(this) {
          private int jumpDelay;

          @Override
          public void setWantedPosition(double x, double y, double z, double speed) {
            if (isInWater() && operation == Operation.JUMPING) operation = Operation.WAIT;
            super.setWantedPosition(x, y, z, speed);
          }

          @Override
          public void tick() {
            if (isInWater() && operation == Operation.JUMPING) operation = Operation.WAIT;
            boolean moving = hasWanted() || operation == Operation.JUMPING;
            super.tick();
            if (moving && onGround() && --jumpDelay <= 0) {
              jumpDelay = 12;
              getJumpControl().jump();
            }
            if (moving && isInWater()) setZza(1);
          }
        };
    xpReward = 5;
  }

  public static AttributeSupplier.Builder createAttributes() {
    return createAttributes(Role.GUARD);
  }

  public static AttributeSupplier.Builder createAttributes(Role role) {
    return Monster.createMonsterAttributes()
        .add(Attributes.MAX_HEALTH, role == Role.COMMANDER ? 96 : role == Role.TRANSPORT ? 48 : 64)
        .add(Attributes.ATTACK_DAMAGE, role == Role.COMMANDER ? 12 : role == Role.TRANSPORT ? 6 : 8)
        .add(Attributes.ARMOR, role == Role.COMMANDER ? 12 : role == Role.TRANSPORT ? 6 : 8)
        .add(Attributes.MOVEMENT_SPEED, .25)
        .add(Attributes.FOLLOW_RANGE, 16);
  }

  public Role role() {
    return getType() == ModEntities.FORGOTTEN_TRANSPORT_SLIME.get()
        ? Role.TRANSPORT
        : getType() == ModEntities.ANCIENT_COMMANDER_SLIME.get() ? Role.COMMANDER : Role.GUARD;
  }

  public BlockPos getRuinHome() {
    return home == null ? blockPosition() : home;
  }

  public UUID getRuinId() {
    return ruin;
  }

  public int getVeterancy() {
    return experience;
  }

  public ItemStack getParcel() {
    return parcel;
  }

  public void bindToRuin(
      BlockPos origin, UUID id, BlockPos source, BlockPos destination, int rank) {
    home = origin.immutable();
    ruin = id;
    sourceDepot = source == null ? null : source.immutable();
    destinationDepot = destination == null ? null : destination.immutable();
    experience = Math.max(0, Math.min(3, rank));
    setHealth(getMaxHealth());
    setCustomName(
        Component.translatable(
            "name.slimic.ancient_" + role().name().toLowerCase(java.util.Locale.ROOT)));
  }

  @Override
  protected PathNavigation createNavigation(Level level) {
    return new AncientNavigation(this, level);
  }

  @Override
  protected void registerGoals() {
    goalSelector.addGoal(0, new FloatGoal(this));
  }

  @Override
  public boolean isAlliedTo(Entity other) {
    return other instanceof AncientSlime || super.isAlliedTo(other);
  }

  @Override
  public boolean canChangeDimensions() {
    return false;
  }

  @Override
  public ItemStack getPickResult() {
    var egg = net.minecraftforge.common.ForgeSpawnEggItem.fromEntityType(getType());
    return egg == null ? ItemStack.EMPTY : new ItemStack(egg);
  }

  public boolean mayAttack(LivingEntity target) {
    return target != this
        && target.isAlive()
        && !target.isSpectator()
        && target.level() == level()
        && canAttack(target)
        && !isAlliedTo(target)
        && target.distanceToSqr(Vec3.atCenterOf(getRuinHome())) <= 24 * 24
        && (target instanceof Player player && !player.getAbilities().instabuild
            || target instanceof FriendlySlime companion && companion.isTame());
  }

  @Override
  public boolean hasLineOfSight(Entity entity) {
    if (!(level() instanceof ServerLevel)) return super.hasLineOfSight(entity);
    return entity.level() == level()
        && distanceToSqr(entity) <= 48 * 48
        && LoadedBlockView.clip(
                    level(),
                    new ClipContext(
                        getEyePosition(),
                        entity.getEyePosition(),
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        this))
                .getType()
            == HitResult.Type.MISS;
  }

  public void alert(LivingEntity intruder) {
    if (!mayAttack(intruder)) return;
    setTarget(intruder);
    if (role() != Role.COMMANDER || ruin == null) return;
    for (var guard :
        level()
            .getEntitiesOfClass(
                AncientSlime.class,
                getBoundingBox().inflate(16),
                other ->
                    other != this && ruin.equals(other.ruin) && other.role() != Role.TRANSPORT))
      if (guard.mayAttack(intruder) && guard.hasLineOfSight(intruder)) guard.setTarget(intruder);
  }

  @Override
  public boolean hurt(DamageSource source, float amount) {
    boolean hit = super.hurt(source, amount);
    if (hit && source.getEntity() instanceof LivingEntity attacker && mayAttack(attacker))
      alert(attacker);
    return hit;
  }

  @Override
  public boolean doHurtTarget(Entity target) {
    return target instanceof LivingEntity victim
        && mayAttack(victim)
        && hasLineOfSight(victim)
        && super.doHurtTarget(target);
  }

  @Override
  public void tick() {
    super.tick();
    if (level().isClientSide || !isAlive() || isNoAi()) return;
    if (attackCooldown > 0) attackCooldown--;
    if (home == null) home = blockPosition().immutable();
    if ((tickCount + getId()) % 10 != 0) return;
    var victim = getTarget();
    if (victim != null && (!mayAttack(victim) || distanceToSqr(Vec3.atCenterOf(home)) > 24 * 24)) {
      setTarget(null);
      getNavigation().stop();
      victim = null;
    }
    if (victim == null && role() != Role.TRANSPORT) {
      double closest = 16 * 16;
      for (var candidate :
          level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(16))) {
        double distance = distanceToSqr(candidate);
        if (distance < closest && mayAttack(candidate) && hasLineOfSight(candidate)) {
          victim = candidate;
          closest = distance;
        }
      }
      if (victim != null) alert(victim);
    }
    if (victim != null) {
      getLookControl().setLookAt(victim, 30, 30);
      if (distanceToSqr(victim) <= 3.5 && hasLineOfSight(victim)) {
        getNavigation().stop();
        if (attackCooldown == 0) {
          doHurtTarget(victim);
          attackCooldown = 20;
        }
      } else navigateTo(victim.position(), 1);
      return;
    }
    double homeRadius = role() == Role.TRANSPORT ? 12 : 4;
    if (distanceToSqr(Vec3.atCenterOf(home)) > homeRadius * homeRadius) {
      navigateTo(Vec3.atBottomCenterOf(home), 1);
      return;
    }
    if (role() == Role.TRANSPORT && tickCount % 40 < 10) haul();
    else if (role() != Role.TRANSPORT && tickCount % 80 < 10 && getNavigation().isDone()) {
      int[][] points = {{3, 2}, {-3, 2}, {-3, -2}, {3, -2}};
      int[] next = points[patrolStep++ & 3];
      var destination = home.offset(next[0], 0, next[1]);
      if (level().hasChunkAt(destination)) navigateTo(Vec3.atBottomCenterOf(destination), .8);
    }
  }

  private void navigateTo(Vec3 destination, double speed) {
    if ((onGround() || isInWater())
        && (getNavigation().isDone()
            || pathDestination == null
            || pathDestination.distanceToSqr(destination) > 1)) {
      if (getNavigation().moveTo(destination.x, destination.y, destination.z, speed))
        pathDestination = destination;
    }
  }

  private void haul() {
    if (ruin == null
        || sourceDepot == null
        || destinationDepot == null
        || !ForgeEventFactory.getMobGriefingEvent(level(), this)) return;
    BlockPos destination = returning ? sourceDepot : destinationDepot;
    BlockPos source = returning ? destinationDepot : sourceDepot;
    BlockPos target = parcel.isEmpty() ? source : destination;
    if (target.distSqr(home) > 12 * 12
        || !level().hasChunkAt(target)
        || !(level().getBlockEntity(target) instanceof SlimeChestBlockEntity chest)
        || !chest.getPersistentData().hasUUID(DEPOT_KEY)
        || !ruin.equals(chest.getPersistentData().getUUID(DEPOT_KEY))) return;
    if (distanceToSqr(Vec3.atCenterOf(target)) > 9) {
      navigateTo(Vec3.atBottomCenterOf(target), .8);
      return;
    }
    getNavigation().stop();
    if (parcel.isEmpty()) {
      for (int slot = 0; slot < chest.getContainerSize(); slot++) {
        if (!chest.getItem(slot).isEmpty()) {
          parcel = chest.removeItem(slot, 1);
          break;
        }
      }
      if (parcel.isEmpty()) returning = !returning;
    } else {
      parcel = ItemHandlerHelper.insertItemStacked(chest.inventory(), parcel, false);
      if (parcel.isEmpty()) returning = !returning;
    }
  }

  @Override
  protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
    super.dropCustomDeathLoot(source, looting, recentlyHit);
    if (!parcel.isEmpty()) {
      spawnAtLocation(parcel);
      parcel = ItemStack.EMPTY;
    }
    if (coreDropped
        || !(source.getEntity() instanceof Player
            || source.getEntity() instanceof FriendlySlime companion && companion.isTame())) return;
    coreDropped = true;
    spawnAtLocation(AncientSlimeCoreItem.fromSlime(this));
    spawnAtLocation(new ItemStack(ModItems.ANCIENT_COMMAND_SEAL.get()));
  }

  @Override
  protected SoundEvent getHurtSound(DamageSource source) {
    return SoundEvents.SLIME_HURT;
  }

  @Override
  protected SoundEvent getDeathSound() {
    return SoundEvents.SLIME_DEATH;
  }

  @Override
  protected SoundEvent getAmbientSound() {
    return SoundEvents.SLIME_SQUISH;
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    if (home != null) tag.put("AncientHome", NbtUtils.writeBlockPos(home));
    if (ruin != null) tag.putUUID("AncientRuin", ruin);
    if (sourceDepot != null) tag.put("AncientSource", NbtUtils.writeBlockPos(sourceDepot));
    if (destinationDepot != null)
      tag.put("AncientDestination", NbtUtils.writeBlockPos(destinationDepot));
    tag.put("AncientParcel", parcel.save(new CompoundTag()));
    tag.putBoolean("AncientReturning", returning);
    tag.putBoolean("AncientCoreDropped", coreDropped);
    tag.putInt("AncientVeterancy", experience);
    tag.putInt("AncientCombatVersion", 1);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    home =
        tag.contains("AncientHome", 10)
            ? NbtUtils.readBlockPos(tag.getCompound("AncientHome"))
            : null;
    ruin = tag.hasUUID("AncientRuin") ? tag.getUUID("AncientRuin") : null;
    sourceDepot =
        tag.contains("AncientSource", 10)
            ? NbtUtils.readBlockPos(tag.getCompound("AncientSource"))
            : null;
    destinationDepot =
        tag.contains("AncientDestination", 10)
            ? NbtUtils.readBlockPos(tag.getCompound("AncientDestination"))
            : null;
    parcel = ItemStack.of(tag.getCompound("AncientParcel"));
    returning = tag.getBoolean("AncientReturning");
    coreDropped = tag.getBoolean("AncientCoreDropped");
    experience = Math.max(0, Math.min(3, tag.getInt("AncientVeterancy")));
    if (tag.getInt("AncientCombatVersion") < 1) {
      float health = getHealth();
      float oldMaximum = getMaxHealth();
      var maximum = getAttribute(Attributes.MAX_HEALTH);
      double oldBase = role() == Role.COMMANDER ? 40 : 28;
      if (maximum.getBaseValue() == oldBase) {
        maximum.setBaseValue(role() == Role.COMMANDER ? 96 : role() == Role.TRANSPORT ? 48 : 64);
        setHealth(health * getMaxHealth() / oldMaximum);
      }
      var attack = getAttribute(Attributes.ATTACK_DAMAGE);
      if (attack.getBaseValue() == (role() == Role.COMMANDER ? 5 : 4))
        attack.setBaseValue(role() == Role.COMMANDER ? 12 : role() == Role.TRANSPORT ? 6 : 8);
      var armor = getAttribute(Attributes.ARMOR);
      if (armor.getBaseValue() == 3)
        armor.setBaseValue(role() == Role.COMMANDER ? 12 : role() == Role.TRANSPORT ? 6 : 8);
    }
    setTarget(null);
    pathDestination = null;
    getNavigation().stop();
  }
}
