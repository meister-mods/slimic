package io.github.meistermods.slimic.item;

import io.github.meistermods.slimic.entity.CommandSlime;
import io.github.meistermods.slimic.entity.CompanionRegistry;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.SlimeData;
import io.github.meistermods.slimic.registry.ModEntities;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class FriendlySlimeItem extends Item {
  private static final String SLIME_DATA = "SlimeData";
  private static final ThreadLocal<Set<UUID>> PENDING_RESTORATIONS =
      ThreadLocal.withInitial(HashSet::new);

  public FriendlySlimeItem(Properties properties) {
    super(properties);
  }

  public static ItemStack fromSlime(FriendlySlime slime) {
    return storedSlime(slime, slime.getPickResult().getItem());
  }

  private static ItemStack storedSlime(FriendlySlime slime, Item item) {
    ItemStack stack = new ItemStack(item);
    CompoundTag data = slime.saveWithoutId(new CompoundTag());
    // Passengers and tethers remain in the world; preserve the slime's own saved state.
    data.remove("Passengers");
    data.remove("Leash");
    stack.getOrCreateTag().put(SLIME_DATA, data);
    if (slime.hasCustomName()) {
      stack.setHoverName(slime.getCustomName());
    }
    return stack;
  }

  private static boolean hasStoredSlime(ItemStack stack) {
    return stack.hasTag() && stack.getTag().contains(SLIME_DATA);
  }

  protected EntityType<? extends FriendlySlime> entityType() {
    return ModEntities.FRIENDLY_SLIME.get();
  }

  protected void initializeSlime(FriendlySlime slime, ItemStack stack) {}

  @Override
  public int getMaxStackSize(ItemStack stack) {
    return hasStoredSlime(stack) ? 1 : getMaxStackSize();
  }

  @Override
  public InteractionResult useOn(UseOnContext context) {
    Level level = context.getLevel();
    Player player = context.getPlayer();
    ItemStack stack = context.getItemInHand();
    BlockPos clicked = context.getClickedPos();
    Direction face = context.getClickedFace();
    BlockPos position =
        level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty()
            ? clicked
            : clicked.relative(face);
    if (player == null
        || stack.isEmpty()
        || !level.mayInteract(player, clicked)
        || !player.mayUseItemAt(position, face, stack)) {
      return InteractionResult.FAIL;
    }
    if (!(level instanceof ServerLevel server)) {
      return InteractionResult.SUCCESS;
    }
    boolean restoring = hasStoredSlime(stack);
    if (!spawn(
        server, player, stack, position, true, !clicked.equals(position) && face == Direction.UP)) {
      return InteractionResult.FAIL;
    }
    if (restoring && player.getAbilities().instabuild) {
      // Creative block use restores the old stack count after this method returns.
      player.setItemInHand(context.getHand(), ItemStack.EMPTY);
    }
    return InteractionResult.CONSUME;
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    ItemStack stack = player.getItemInHand(hand);
    BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
    if (hit.getType() != HitResult.Type.BLOCK
        || !(level.getBlockState(hit.getBlockPos()).getBlock() instanceof LiquidBlock)) {
      return InteractionResultHolder.pass(stack);
    }
    if (stack.isEmpty()
        || !level.mayInteract(player, hit.getBlockPos())
        || !player.mayUseItemAt(hit.getBlockPos(), hit.getDirection(), stack)) {
      return InteractionResultHolder.fail(stack);
    }
    if (!(level instanceof ServerLevel server)) {
      return InteractionResultHolder.success(stack);
    }
    if (!spawn(server, player, stack, hit.getBlockPos(), false, false)) {
      return InteractionResultHolder.fail(stack);
    }
    player.awardStat(Stats.ITEM_USED.get(this));
    return InteractionResultHolder.consume(stack.isEmpty() ? ItemStack.EMPTY : stack);
  }

  private boolean spawn(
      ServerLevel level,
      Player player,
      ItemStack stack,
      BlockPos position,
      boolean alignToSurface,
      boolean offsetUp) {
    return spawn(level, player, stack, position, alignToSurface, offsetUp, false);
  }

  public boolean revive(ServerLevel level, Player player, ItemStack stored, BlockPos pos) {
    if (!hasStoredSlime(stored)) return false;
    return spawn(level, player, stored, pos, false, false, true);
  }

  private boolean spawn(
      ServerLevel level,
      Player player,
      ItemStack stack,
      BlockPos position,
      boolean alignToSurface,
      boolean offsetUp,
      boolean reviving) {
    if (entityType() != ModEntities.COMMAND_SLIME.get())
      return spawnWithIdentityGuard(
          level, player, stack, position, alignToSurface, offsetUp, reviving);
    var registry = CompanionRegistry.get(level.getServer());
    var saved = hasStoredSlime(stack) ? stack.getTag().getCompound(SLIME_DATA) : null;
    UUID owner =
        saved != null && saved.hasUUID("Owner") ? saved.getUUID("Owner") : player.getUUID();
    if (!registry.reserveCommand(owner)) {
      player.displayClientMessage(Component.translatable("message.slimic.commander_limit"), true);
      return false;
    }
    boolean spawned = false;
    try {
      spawned =
          spawnWithIdentityGuard(
              level, player, stack, position, alignToSurface, offsetUp, reviving);
      return spawned;
    } finally {
      registry.releaseCommandReservation(owner, spawned);
    }
  }

  private boolean spawnWithIdentityGuard(
      ServerLevel level,
      Player player,
      ItemStack stack,
      BlockPos position,
      boolean alignToSurface,
      boolean offsetUp,
      boolean reviving) {
    if (!hasStoredSlime(stack) || !stack.getTag().getCompound(SLIME_DATA).hasUUID("UUID"))
      return spawnCompanion(level, player, stack, position, alignToSurface, offsetUp, reviving);
    UUID identity = stack.getTag().getCompound(SLIME_DATA).getUUID("UUID");
    Set<UUID> pending = PENDING_RESTORATIONS.get();
    // Join and finalize-spawn callbacks can attempt another restoration before registration.
    if (!pending.add(identity)) return false;
    try {
      return spawnCompanion(level, player, stack, position, alignToSurface, offsetUp, reviving);
    } finally {
      pending.remove(identity);
      if (pending.isEmpty()) PENDING_RESTORATIONS.remove();
    }
  }

  private boolean spawnCompanion(
      ServerLevel level,
      Player player,
      ItemStack stack,
      BlockPos position,
      boolean alignToSurface,
      boolean offsetUp,
      boolean reviving) {
    boolean restoring = hasStoredSlime(stack);
    CompoundTag saved =
        restoring ? SlimeData.migrate(stack.getTag().getCompound(SLIME_DATA)) : null;
    if (restoring) {
      if (!SlimeData.canLoad(saved)
          || stack.getCount() != 1
          || !saved.hasUUID("UUID")
          || !saved.hasUUID("Owner")
          || !saved.contains("Health", Tag.TAG_ANY_NUMERIC)
          || !Float.isFinite(saved.getFloat("Health"))
          || (!reviving && saved.getFloat("Health") <= 0.0F)) {
        return false;
      }
      if (!io.github.meistermods.slimic.world.RoyalSynthesisData.get(level).mayRestore(saved)) {
        player.displayClientMessage(
            Component.translatable("message.slimic.synthesis_consumed"), true);
        return false;
      }
      for (ServerLevel world : level.getServer().getAllLevels()) {
        var existing = world.getEntity(saved.getUUID("UUID"));
        if (existing != null && !existing.isRemoved()) {
          return false;
        }
      }
    }
    FriendlySlime slime =
        entityType()
            .create(
                level,
                null,
                entity -> {
                  if (restoring) {
                    Vec3 spawnPosition = entity.position();
                    float yaw = entity.getYRot();
                    // Rebuild placement and motion at the chosen destination, not the capture
                    // location.
                    saved.remove("Pos");
                    saved.remove("Motion");
                    saved.remove("Rotation");
                    saved.remove("FallDistance");
                    saved.remove("Passengers");
                    saved.remove("Leash");
                    entity.load(saved);
                    if (reviving) {
                      entity.setHealth(1.0F);
                      entity.clearFire();
                      entity.deathTime = entity.hurtTime = 0;
                    }
                    entity.moveTo(spawnPosition.x, spawnPosition.y, spawnPosition.z, yaw, 0.0F);
                    entity.setDeltaMovement(Vec3.ZERO);
                    entity.fallDistance = 0.0F;
                  } else {
                    entity.setOwnerUUID(player.getUUID());
                    entity.setTame(true);
                  }
                  if (stack.hasCustomHoverName()) {
                    entity.setCustomName(stack.getHoverName());
                  }
                  initializeSlime(entity, stack);
                  entity.setWaiting(true);
                },
                position,
                MobSpawnType.SPAWN_EGG,
                alignToSurface,
                offsetUp);
    if (slime instanceof CommandSlime command
        && !CompanionRegistry.get(level.getServer()).prepareCommandSpawn(command)) {
      player.displayClientMessage(Component.translatable("message.slimic.commander_limit"), true);
      return false;
    }
    if (slime instanceof io.github.meistermods.slimic.entity.EnderSlime ender
        && ender.isPinned()
        && !ender.hasAnchorCapacity()) {
      player.displayClientMessage(
          Component.translatable("message.slimic.ender_anchor_limit"), true);
      return false;
    }
    if (slime == null || slime.isSpawnCancelled() || !level.noCollision(slime)) {
      return false;
    }
    UUID identity = slime.getUUID();
    UUID owner = slime.getOwnerUUID();
    boolean admitted = false;
    slime.setPendingSpawn(true);
    try {
      admitted =
          level.addFreshEntity(slime)
              && slime.isAddedToWorld()
              && slime.isAlive()
              && slime.level() == level
              && identity.equals(slime.getUUID())
              && Objects.equals(owner, slime.getOwnerUUID());
      if (admitted
          && slime instanceof io.github.meistermods.slimic.entity.EnderSlime ender
          && ender.isPinned()
          && !ender.hasAnchorCapacity()) {
        admitted = false;
        player.displayClientMessage(
            Component.translatable("message.slimic.ender_anchor_limit"), true);
      }
    } finally {
      slime.setPendingSpawn(false);
      if (!admitted) {
        // Admission callbacks may remove an entity before native registration completes.
        if (slime.isAddedToWorld() && slime.isRemoved()) slime.revive();
        slime.discard();
      }
    }
    if (!admitted) return false;
    if (restoring
        && !io.github.meistermods.slimic.world.RoyalSynthesisData.get(level)
            .released(level, slime)) {
      slime.discard();
      return false;
    }
    slime.onReleased();
    slime.setWaiting(true);
    if (!restoring) {
      slime.tame(player);
      level.broadcastEntityEvent(slime, (byte) 7);
    }
    if (restoring || !player.getAbilities().instabuild) {
      stack.shrink(1);
    }
    level.gameEvent(player, GameEvent.ENTITY_PLACE, slime.position());
    return true;
  }

  @Override
  public void appendHoverText(
      ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
    if (hasStoredSlime(stack)) {
      var ancestry =
          stack
              .getTag()
              .getCompound(SLIME_DATA)
              .getCompound("ForgeData")
              .getCompound("NextGenAncestry");
      if (!ancestry.isEmpty()) {
        tooltip.add(
            Component.translatable(
                    "item.slimic.synthesis.ancestry",
                    ancestry.getString("NormalName"),
                    ancestry.getString("AncientName"))
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(
            Component.translatable(
                    "item.slimic.synthesis.family", ancestry.getLong("SynthesisTime"))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(
            Component.translatable("item.slimic.synthesis.unspent").withStyle(ChatFormatting.GRAY));
        if (flag.isAdvanced()
            && ancestry.hasUUID("NormalParent")
            && ancestry.hasUUID("AncientParent"))
          tooltip.add(
              Component.translatable(
                      "item.slimic.synthesis.parents",
                      ancestry.getUUID("NormalParent"),
                      ancestry.getUUID("AncientParent"))
                  .withStyle(ChatFormatting.DARK_GRAY));
        if (flag.isAdvanced() && ancestry.hasUUID("OriginSanctum"))
          tooltip.add(
              Component.translatable(
                      "item.slimic.synthesis.site", ancestry.getUUID("OriginSanctum"))
                  .withStyle(ChatFormatting.DARK_GRAY));
      }
    }
    tooltip.add(
        Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    tooltip.add(
        Component.translatable(
                hasStoredSlime(stack)
                    ? "item.slimic.friendly_slime.stored_tooltip"
                    : "item.slimic.companion.summon_tooltip")
            .withStyle(ChatFormatting.GRAY));
    tooltip.add(
        Component.translatable("item.slimic.friendly_slime.capture_tooltip")
            .withStyle(ChatFormatting.GRAY));
    tooltip.add(
        Component.translatable("item.slimic.friendly_slime.manage_tooltip")
            .withStyle(ChatFormatting.GRAY));
  }
}
