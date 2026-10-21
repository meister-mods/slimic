package io.github.meistermods.slimic.entity;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.ItemFishedEvent;

/** Shore fishing uses the native fishing loot table, hook conditions and rod enchantments. */
public final class FishingSlime extends BagSlime {
  public enum Status {
    IDLE,
    SURVEYING,
    MOVING,
    FISHING,
    FULL,
    NO_ROD,
    NO_WATER,
    RESTING,
    UNREACHABLE,
    WAITING,
    FOLLOWING
  }

  private boolean working, resting;
  private Status status = Status.IDLE;
  private BlockPos home, water, bank, avoidedWater, avoidedBank;
  private BlockSurvey survey;
  private double waitTicks;
  private int noCatchTicks, nextSearch, travelTicks;
  private Vec3 lastPosition;
  private final List<ItemStack> pendingCatch = new ArrayList<>();

  public FishingSlime(EntityType<? extends FishingSlime> type, Level level) {
    super(type, level, 27);
    setDropChance(EquipmentSlot.MAINHAND, 2);
    setMaxUpStep(1);
  }

  public static boolean isRod(ItemStack stack) {
    return stack.getItem() instanceof FishingRodItem && SlimeBlockBreaking.usable(stack);
  }

  public ItemStack getRod() {
    return getMainHandItem();
  }

  public void setRod(ItemStack stack) {
    setItemSlot(EquipmentSlot.MAINHAND, stack);
  }

  @Override
  protected String bagTagName() {
    return "FishingBag";
  }

  @Override
  public boolean isWorking() {
    return working && !isOrderedToSit();
  }

  @Override
  public void setWorking(boolean value) {
    working = value;
    setWaiting(false);
    clearSite();
    status = Status.IDLE;
  }

  private void clearSite() {
    water = bank = null;
    survey = null;
    travelTicks = 0;
    lastPosition = null;
    nextSearch = 0;
    getNavigation().stop();
  }

  @Override
  public void onReleased() {
    super.onReleased();
    home = blockPosition();
    clearSite();
  }

  public BlockPos getWorkOrigin() {
    return home == null ? blockPosition() : home;
  }

  public Status getFishingStatus() {
    return isOrderedToSit() ? Status.WAITING : working ? status : Status.FOLLOWING;
  }

  public BlockPos getFishingWater() {
    return water;
  }

  public BlockPos getFishingBank() {
    return bank;
  }

  public int getNoCatchTicks() {
    return noCatchTicks;
  }

  @Override
  protected WorkStatus describeWork() {
    var order = WorkStatus.Order.FISH;
    return switch (status) {
      case FULL ->
          WorkStatus.blocked(
              order, WorkStatus.Reason.INVENTORY_FULL, WorkStatus.Request.CARGO_PICKUP);
      case NO_ROD ->
          WorkStatus.blocked(order, WorkStatus.Reason.TOOL_MISSING, WorkStatus.Request.TOOL_SUPPLY);
      case RESTING -> WorkStatus.blocked(order, WorkStatus.Reason.EXHAUSTED);
      case NO_WATER, UNREACHABLE -> WorkStatus.blocked(order, WorkStatus.Reason.UNREACHABLE);
      default ->
          WorkStatus.of(
              order,
              status == Status.FISHING || status == Status.MOVING
                  ? WorkStatus.State.ACTIVE
                  : WorkStatus.State.IDLE);
    };
  }

  @Override
  public boolean acceptsWorkTool(ItemStack stack) {
    return isRod(stack);
  }

  @Override
  public boolean receiveWorkTool(ItemStack stack) {
    if (!isWorking() || isRod(getRod()) || !isRod(stack)) return false;
    setRod(stack.copyWithCount(1));
    return true;
  }

  private boolean siteValid(BlockPos pos) {
    return pos != null
        && level().hasChunkAt(pos)
        && level().hasChunkAt(pos.above())
        && level().getWorldBorder().isWithinBounds(pos)
        && level().getFluidState(pos).is(FluidTags.WATER)
        && level().getFluidState(pos).isSource()
        && level().getBlockState(pos.above()).isAir();
  }

  private boolean bankValid(BlockPos pos) {
    if (!level().hasChunkAt(pos) || !level().hasChunkAt(pos.offset(1, 1, 1))) return false;
    var body = getBoundingBox().move(Vec3.atBottomCenterOf(pos).subtract(position()));
    return level().getFluidState(pos).isEmpty()
        && level().noCollision(this, body)
        && level().getBlockCollisions(this, body.move(0, -.1, 0)).iterator().hasNext();
  }

  private void search(ServerLevel server) {
    if (survey == null)
      survey =
          new BlockSurvey(
              server,
              getWorkOrigin(),
              SlimeStat.DETECTION_RANGE.value(this),
              state -> state.getFluidState().is(FluidTags.WATER));
    status = Status.SURVEYING;
    for (BlockPos candidate : survey.next(1024)) {
      if (candidate.equals(avoidedWater) || !siteValid(candidate)) continue;
      for (Direction side : Direction.Plane.HORIZONTAL)
        for (int castDistance = 1; castDistance <= 4; castDistance++) {
          BlockPos shore = candidate.relative(side, castDistance).above();
          if ((avoidedBank == null || shore.distSqr(avoidedBank) >= 4)
              && bankValid(shore)
              && (bank == null
                  || shore.distToCenterSqr(position()) < bank.distToCenterSqr(position())
                  || (shore.equals(bank)
                      && isOpenWater(server, candidate)
                      && !isOpenWater(server, water)))) {
            water = candidate;
            bank = shore;
          }
        }
    }
    if (survey.isDone()) {
      survey = null;
      nextSearch = tickCount + 100;
      if (bank == null) {
        status = Status.NO_WATER;
        avoidedWater = null;
        avoidedBank = null;
      }
    }
  }

  private FakePlayer worker(ServerLevel server) {
    var player = FakePlayerFactory.get(server, new GameProfile(getOwnerUUID(), "SlimeFisher"));
    player.moveTo(position());
    player.setOnGround(true);
    return player;
  }

  private int nextWait() {
    int lure = EnchantmentHelper.getFishingSpeedBonus(getRod());
    return Math.max(20, 100 + random.nextInt(501) - lure * 100) + 20 + random.nextInt(61);
  }

  private void catchFish(ServerLevel server) {
    ItemStack rod = getRod();
    var player = worker(server);
    var previous = player.getMainHandItem();
    var previousHook = player.fishing;
    FishingHook hook =
        new FishingHook(
            player,
            server,
            EnchantmentHelper.getFishingLuckBonus(rod),
            EnchantmentHelper.getFishingSpeedBonus(rod)) {
          @Override
          public boolean isOpenWaterFishing() {
            return isOpenWater(server, water);
          }
        };
    try {
      hook.setPos(Vec3.atCenterOf(water));
      player.setItemInHand(InteractionHand.MAIN_HAND, rod);
      var params =
          new LootParams.Builder(server)
              .withParameter(LootContextParams.ORIGIN, hook.position())
              .withParameter(LootContextParams.TOOL, rod)
              .withParameter(LootContextParams.THIS_ENTITY, hook)
              .withParameter(LootContextParams.KILLER_ENTITY, player)
              .withLuck(
                  EnchantmentHelper.getFishingLuckBonus(rod)
                      + (getOwner() instanceof net.minecraft.world.entity.player.Player owner
                          ? owner.getLuck()
                          : 0))
              .create(LootContextParamSets.FISHING);
      var drops =
          server
              .getServer()
              .getLootData()
              .getLootTable(BuiltInLootTables.FISHING)
              .getRandomItems(params);
      var event = new ItemFishedEvent(drops, 1, hook);
      boolean cancelled = MinecraftForge.EVENT_BUS.post(event);
      if (!canContinueWork(player) || !siteValid(water) || getRod() != rod) return;
      rod.hurtAndBreak(
          Math.max(0, event.getRodDamage()),
          this,
          slime -> slime.broadcastBreakEvent(EquipmentSlot.MAINHAND));
      if (!cancelled) {
        for (var drop : event.getDrops()) if (!drop.isEmpty()) pendingCatch.add(drop.copy());
        if (!pendingCatch.isEmpty()) {
          spendStamina(1);
          recordCompletedWork();
          noCatchTicks = 0;
          int xp = 0;
          for (var drop : event.getDrops()) if (!drop.isEmpty()) xp += 1 + random.nextInt(6);
          SlimeToolExperience.award(server, position(), rod, xp);
        }
      }
    } finally {
      hook.discard();
      player.fishing = previousHook;
      player.setItemInHand(InteractionHand.MAIN_HAND, previous);
    }
    waitTicks = nextWait();
  }

  public static boolean isOpenWater(ServerLevel server, BlockPos water) {
    if (water == null) return false;
    for (int y = -1; y <= 2; y++)
      for (int x = -2; x <= 2; x++)
        for (int z = -2; z <= 2; z++) {
          var pos = water.offset(x, y, z);
          if (!server.hasChunkAt(pos)) return false;
          var state = server.getBlockState(pos);
          if (y <= 0) {
            var fluid = state.getFluidState();
            if (!fluid.is(FluidTags.WATER)
                || !fluid.isSource()
                || !state.getCollisionShape(server, pos).isEmpty()) return false;
          } else if (!state.isAir() && !state.is(Blocks.LILY_PAD)) return false;
        }
    return true;
  }

  private boolean storeCatch() {
    for (int i = 0; i < pendingCatch.size(); ) {
      ItemStack remaining = SlimeBag.insertItemStacked(getBag(), pendingCatch.get(i).copy(), false);
      if (remaining.isEmpty()) pendingCatch.remove(i);
      else {
        pendingCatch.set(i, remaining);
        i++;
      }
    }
    return pendingCatch.isEmpty();
  }

  @Override
  protected void registerGoals() {
    super.registerGoals();
    goalSelector.addGoal(1, new FishingGoal());
  }

  @Override
  public void tick() {
    super.tick();
    if (!level().isClientSide && isWorking() && status != Status.FISHING && tickCount % 20 == 0)
      recoverStamina(getStaminaRecoveryPerSecond());
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.FISHING_SLIME.get());
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putBoolean("FishingWorking", working);
    tag.putBoolean("FishingResting", resting);
    tag.putDouble("FishingWait", waitTicks);
    tag.putInt("FishingNoCatch", noCatchTicks);
    if (home != null) tag.putLong("FishingHome", home.asLong());
    ListTag pending = new ListTag();
    for (var stack : pendingCatch) pending.add(stack.save(new CompoundTag()));
    tag.put("FishingPending", pending);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    working = tag.getBoolean("FishingWorking");
    if (!tag.contains("FishingWorking")) setWaiting(true);
    resting = tag.getBoolean("FishingResting");
    waitTicks =
        Double.isFinite(tag.getDouble("FishingWait"))
            ? Math.max(0, Math.min(1200, tag.getDouble("FishingWait")))
            : 0;
    noCatchTicks = Math.max(0, Math.min(6000, tag.getInt("FishingNoCatch")));
    home = tag.contains("FishingHome") ? BlockPos.of(tag.getLong("FishingHome")) : null;
    pendingCatch.clear();
    for (var entry : tag.getList("FishingPending", Tag.TAG_COMPOUND)) {
      var stack = ItemStack.of((CompoundTag) entry);
      if (!stack.isEmpty()) pendingCatch.add(stack);
    }
    clearSite();
    status = pendingCatch.isEmpty() ? Status.IDLE : Status.FULL;
  }

  private final class FishingGoal extends Goal {
    FishingGoal() {
      setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
      return isWorking() && isTame() && getOwnerUUID() != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
      return true;
    }

    @Override
    public void stop() {
      getNavigation().stop();
    }

    @Override
    public void tick() {
      if (!canUse() || !(level() instanceof ServerLevel server)) return;
      if (!storeCatch()) {
        status = Status.FULL;
        getNavigation().stop();
        return;
      }
      if (!isRod(getRod())) {
        for (int i : getBag().occupiedSlots())
          if (isRod(getBag().getStackInSlot(i))) {
            setRod(getBag().extractItem(i, 1, false));
            break;
          }
        if (!isRod(getRod())) {
          status = Status.NO_ROD;
          getNavigation().stop();
          return;
        }
      }
      if (getStamina() < 1) resting = true;
      if (resting && getStamina() < getMaxStamina() * .25F) {
        status = Status.RESTING;
        getNavigation().stop();
        return;
      }
      resting = false;
      if (++noCatchTicks >= 6000) {
        avoidedWater = water;
        avoidedBank = bank;
        clearSite();
        noCatchTicks = 0;
        waitTicks = 0;
      }
      if (water != null && (!siteValid(water) || !bankValid(bank))) clearSite();
      if (bank == null) {
        if (tickCount >= nextSearch) search(server);
        return;
      }
      if (position().distanceToSqr(Vec3.atBottomCenterOf(bank)) > 1.0) {
        status = Status.MOVING;
        if (tickCount % 10 == 0)
          getNavigation().moveTo(bank.getX() + .5, bank.getY(), bank.getZ() + .5, 1);
        if (lastPosition == null || position().distanceToSqr(lastPosition) > .01) {
          lastPosition = position();
          travelTicks = 0;
        } else if (++travelTicks > 400) {
          avoidedWater = water;
          clearSite();
          status = Status.UNREACHABLE;
          nextSearch = tickCount + 40;
        }
        return;
      }
      status = Status.FISHING;
      getNavigation().stop();
      getLookControl().setLookAt(Vec3.atCenterOf(water));
      if (waitTicks <= 0) waitTicks = nextWait();
      double rate = SlimeStat.WORK_SPEED.value(FishingSlime.this);
      if (server.isRainingAt(water.above())) rate++;
      if (!server.canSeeSky(water.above()) && random.nextBoolean()) rate = 0;
      waitTicks -= rate;
      if (tickCount % 20 == 0)
        server.sendParticles(
            ParticleTypes.FISHING,
            water.getX() + .5,
            water.getY() + .9,
            water.getZ() + .5,
            2,
            .15,
            0,
            .15,
            .01);
      if (waitTicks <= 0) catchFish(server);
    }
  }
}
