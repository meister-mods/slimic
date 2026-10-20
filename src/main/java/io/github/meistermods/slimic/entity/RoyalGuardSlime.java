package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A combat companion whose work order defends a fixed post instead of pursuing indefinitely. */
public final class RoyalGuardSlime extends CombatSlime {
  private BlockPos post;

  public RoyalGuardSlime(EntityType<? extends RoyalGuardSlime> type, Level level) {
    super(type, level);
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
