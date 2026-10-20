package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.registry.ModItems;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A combat companion whose work order defends a fixed post instead of pursuing indefinitely. */
public final class RoyalGuardSlime extends CombatSlime {
  private static final UUID HEALTH_BONUS = UUID.fromString("b7856207-49f4-4778-a3b1-5da279b702f4");
  private BlockPos post;

  public RoyalGuardSlime(EntityType<? extends RoyalGuardSlime> type, Level level) {
    super(type, level);
    ensureHealthBonus();
    setHealth(getMaxHealth());
  }

  private static AttributeModifier healthBonus() {
    return new AttributeModifier(
        HEALTH_BONUS, "Royal guard health", 144, AttributeModifier.Operation.ADDITION);
  }

  private void ensureHealthBonus() {
    var attribute = getAttribute(Attributes.MAX_HEALTH);
    if (attribute.getModifier(HEALTH_BONUS) == null) attribute.addPermanentModifier(healthBonus());
  }

  /**
   * Apply the same profession bonus before a crafted companion is released or listed in a roster.
   */
  public static void addHealthBonusToData(CompoundTag data) {
    var attributes = data.getList("Attributes", 10);
    CompoundTag health = null;
    for (var value : attributes) {
      var attribute = (CompoundTag) value;
      if (attribute.getString("Name").equals("minecraft:generic.max_health")) {
        health = attribute;
        break;
      }
    }
    if (health == null) {
      health = new CompoundTag();
      health.putString("Name", "minecraft:generic.max_health");
      health.putDouble("Base", 16);
      attributes.add(health);
    }
    var modifiers = health.getList("Modifiers", 10);
    for (var value : modifiers)
      if (((CompoundTag) value).hasUUID("UUID")
          && ((CompoundTag) value).getUUID("UUID").equals(HEALTH_BONUS)) return;
    modifiers.add(healthBonus().save());
    health.put("Modifiers", modifiers);
    data.put("Attributes", attributes);
  }

  public BlockPos getGuardPost() {
    return post == null ? blockPosition() : post;
  }

  @Override
  public void setWorking(boolean value) {
    if (value) post = blockPosition().immutable();
    super.setWorking(value);
  }

  @Override
  public void onReleased() {
    post = blockPosition().immutable();
    super.onReleased();
  }

  @Override
  public boolean mayAttack(LivingEntity candidate) {
    double radius = SlimeStat.DETECTION_RANGE.value(this);
    return candidate.level() == level()
        && candidate.distanceToSqr(Vec3.atCenterOf(getGuardPost())) <= radius * radius
        && super.mayAttack(candidate);
  }

  /** The existing filters, ownership, PvP rules and line of sight also apply to interception. */
  public boolean canProtect(LivingEntity ally, LivingEntity attacker) {
    if (!isAlive() || !isTame() || !isWorking() || ally == this || ally instanceof RoyalGuardSlime)
      return false;
    boolean allied =
        ally instanceof net.minecraft.world.entity.player.Player p && canManage(p)
            || ally instanceof FriendlySlime companion && canCooperateWith(companion);
    return allied
        && ally.level() == level()
        && distanceToSqr(ally) <= 16
        && getStamina() >= 4
        && getHealth() > 1
        && CombatSlime.matchesEquipment(1, getOffhandItem())
        && mayAttack(attacker)
        && hasLineOfSight(ally)
        && hasLineOfSight(attacker);
  }

  public void recordInterception(float damage) {
    spendStamina(Math.min(getStamina(), Math.max(4, damage * 2)));
    recordCompletedWork();
  }

  boolean blockedInterceptedHit() {
    return blockedLastHit();
  }

  @Override
  protected boolean mayNavigate() {
    return super.mayNavigate() || isInWater();
  }

  @Override
  protected boolean moveWhileIdle() {
    var home = Vec3.atBottomCenterOf(getGuardPost());
    if (distanceToSqr(home) <= 4) return false;
    if (tickCount % 10 == 0
        && mayNavigate()
        && (getNavigation().isDone() || !getGuardPost().equals(getNavigation().getTargetPos())))
      getNavigation().moveTo(home.x, home.y, home.z, 1);
    return true;
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    if (post != null) tag.put("RoyalGuardPost", NbtUtils.writeBlockPos(post));
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    ensureHealthBonus();
    post =
        tag.contains("RoyalGuardPost", 10)
            ? NbtUtils.readBlockPos(tag.getCompound("RoyalGuardPost"))
            : null;
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.ROYAL_GUARD_SLIME.get());
  }
}
