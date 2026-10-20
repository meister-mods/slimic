package io.github.meistermods.slimic.world;

import io.github.meistermods.slimic.entity.CompanionRegistry;
import io.github.meistermods.slimic.entity.CompanionTeleport;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.registry.ModWorldBlocks;
import java.util.*;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.tags.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.*;
import net.minecraftforge.event.ForgeEventFactory;

public final class SlimicPortalTravel {
  public static final TagKey<Block> EXIT_TERRAIN =
      TagKey.create(
          net.minecraft.core.registries.Registries.BLOCK,
          SlimicWorld.id("portal_replaceable_terrain"));

  private record Contact(long tick, int time) {}

  private static final Map<Entity, Contact> CONTACTS = new WeakHashMap<>();
  private static final Set<Entity> TRANSFERRING =
      Collections.newSetFromMap(new IdentityHashMap<>());

  private SlimicPortalTravel() {}

  public static boolean activate(ServerPlayer player, InteractionHand hand, BlockPos start) {
    var level = player.serverLevel();
    if (!SlimicWorld.allowsPortal(level)
        || !player.isAlive()
        || player.isRemoved()
        || player.isSpectator()
        || !player.getAbilities().mayBuild
        || !player.getItemInHand(hand).is(Items.SLIME_BALL)) return false;
    for (var axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
      var shape = SlimicPortalShape.find(level, start, axis);
      if (shape == null || !shape.empty(level)) continue;
      var held = player.getItemInHand(hand);
      Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
      for (int x = 0; x < shape.width(); x++)
        for (int y = 0; y < shape.height(); y++) {
          var pos = shape.cell(x, y);
          if (!level.mayInteract(player, pos) || !level.getWorldBorder().isWithinBounds(pos))
            return false;
          changes.put(
              pos,
              ModWorldBlocks.PORTAL
                  .get()
                  .defaultBlockState()
                  .setValue(SlimicPortalBlock.AXIS, axis));
        }
      if (!place(
          level,
          player,
          changes,
          () ->
              player.isAlive()
                  && !player.isRemoved()
                  && !player.isSpectator()
                  && player.getAbilities().mayBuild
                  && player.level() == level
                  && player.getItemInHand(hand) == held
                  && held.is(Items.SLIME_BALL)
                  && shape.sameFrame(level)
                  && shape.complete(level))) return false;
      if (!player.getAbilities().instabuild) held.shrink(1);
      SlimicPortalIndex.get(level).remember(shape.bottom());
      level.playSound(null, start, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, .5F, 1F);
      return true;
    }
    return false;
  }

  private static boolean place(
      ServerLevel level,
      Entity entity,
      Map<BlockPos, BlockState> changes,
      java.util.function.BooleanSupplier valid) {
    List<BlockSnapshot> snapshots = new ArrayList<>();
    boolean committed = false;
    try {
      for (var change : changes.entrySet()) {
        var pos = change.getKey();
        if (!level.getWorldBorder().isWithinBounds(pos)
            || level.isOutsideBuildHeight(pos)
            || level.getBlockEntity(pos) != null
                && !(level.getBlockEntity(pos)
                        instanceof io.github.meistermods.slimic.block.SlimeLightBlockEntity
                    && change.getValue().is(ModWorldBlocks.PORTAL.get()))
            || entity instanceof ServerPlayer player && !level.mayInteract(player, pos))
          return false;
        if (level.getBlockState(pos).equals(change.getValue())) continue;
        snapshots.add(BlockSnapshot.create(level.dimension(), level, pos));
        if (!level.setBlock(pos, change.getValue(), 18)) return false;
      }
      if (ForgeEventFactory.onMultiBlockPlace(entity, snapshots, Direction.UP)
          || !valid.getAsBoolean()) return false;
      committed = true;
      return true;
    } finally {
      if (!committed) {
        boolean previous = level.restoringBlockSnapshots;
        level.restoringBlockSnapshots = true;
        try {
          for (int i = snapshots.size() - 1; i >= 0; i--) snapshots.get(i).restore(true, false);
        } finally {
          level.restoringBlockSnapshots = previous;
        }
      }
      for (var snapshot : snapshots)
        level.updateNeighborsAt(
            snapshot.getPos(), level.getBlockState(snapshot.getPos()).getBlock());
    }
  }

  public static void inside(Entity entity, BlockPos pos, Direction.Axis axis) {
    if (!(entity.level() instanceof ServerLevel origin)
        || !SlimicWorld.allowsPortal(origin)
        || !entity.isAlive()
        || entity.isPassenger()
        || entity.isVehicle()
        || !entity.canChangeDimensions()
        || TRANSFERRING.contains(entity)) return;
    if (entity.isOnPortalCooldown()) {
      entity.setPortalCooldown();
      CONTACTS.remove(entity);
      return;
    }
    long tick = origin.getServer().getTickCount();
    var old = CONTACTS.get(entity);
    if (old != null && old.tick() == tick) return;
    int time = old == null || old.tick() + 1 != tick ? 1 : old.time() + 1;
    CONTACTS.put(entity, new Contact(tick, time));
    if (time < Math.max(1, entity.getPortalWaitTime())) return;
    var source = SlimicPortalShape.find(origin, pos, axis);
    if (source == null || !source.complete(origin)) return;
    var destination =
        origin
            .getServer()
            .getLevel(
                origin.dimension().equals(SlimicWorld.DIMENSION)
                    ? Level.OVERWORLD
                    : SlimicWorld.DIMENSION);
    if (destination == null) return;
    CONTACTS.remove(entity);
    TRANSFERRING.add(entity);
    try {
      Entity result;
      if (entity instanceof ServerPlayer player) {
        // ServerPlayer.changeDimension removes the player and sends a respawn packet before
        // asking its teleporter for an exit. Resolve failure before using the native move API.
        PortalInfo info = portalInfo(entity, origin, destination, source, axis);
        result = null;
        if (info != null) {
          if (CompanionTeleport.player(
              player,
              destination,
              info.pos,
              () ->
                  source.sameFrame(origin)
                      && source.complete(origin)
                      && !player.isPassenger()
                      && !player.isVehicle())) result = player;
        }
      } else if (entity instanceof FriendlySlime companion) {
        result = companion(companion, origin, destination, source, axis);
      } else
        result =
            entity.changeDimension(
                destination,
                new ITeleporter() {
                  @Override
                  public Entity placeEntity(
                      Entity original,
                      ServerLevel from,
                      ServerLevel to,
                      float yaw,
                      Function<Boolean, Entity> reposition) {
                    return reposition.apply(false);
                  }

                  @Override
                  public PortalInfo getPortalInfo(
                      Entity original, ServerLevel to, Function<ServerLevel, PortalInfo> fallback) {
                    return portalInfo(entity, origin, to, source, axis);
                  }
                });
      if (result != null && result.level() == destination) {
        result.setPortalCooldown();
        result.setDeltaMovement(Vec3.ZERO);
        result.fallDistance = 0;
        if (result instanceof FriendlySlime companion) {
          CompanionTeleport.pause(companion);
          companion.onReleased();
          CompanionTeleport.pause(companion);
          CompanionRegistry.get(destination.getServer()).observe(companion);
        }
      } else {
        entity.setPortalCooldown(40);
        if (entity instanceof ServerPlayer player)
          player.displayClientMessage(
              Component.translatable("message.slimic.portal_unavailable"), true);
      }
    } finally {
      TRANSFERRING.remove(entity);
    }
  }

  @Nullable
  private static FriendlySlime companion(
      FriendlySlime source,
      ServerLevel origin,
      ServerLevel target,
      SlimicPortalShape frame,
      Direction.Axis axis) {
    if (!net.minecraftforge.common.ForgeHooks.onTravelToDimension(source, target.dimension()))
      return null;
    PortalInfo info = portalInfo(source, origin, target, frame, axis);
    if (info == null) return null;
    var snapshot = source.saveWithoutId(new net.minecraft.nbt.CompoundTag());
    var copy = source.getType().create(target);
    if (!(copy instanceof FriendlySlime pet)) return null;
    pet.restoreFrom(source);
    pet.moveTo(info.pos.x, info.pos.y, info.pos.z, info.yRot, info.xRot);
    pet.dropLeash(false, false);
    CompanionTeleport.pause(pet);
    pet.setPortalCooldown();
    boolean added = false;
    pet.setPendingSpawn(true);
    try {
      added = target.addFreshEntity(pet);
    } finally {
      pet.setPendingSpawn(false);
      if (!added) rejectCompanion(pet, source);
    }
    if (!added) return null;
    if (!pet.isAddedToWorld()
        || !pet.isAlive()
        || pet.level() != target
        || !pet.getUUID().equals(source.getUUID())
        || !Objects.equals(pet.getOwnerUUID(), source.getOwnerUUID())
        || source.level() != origin
        || !source.isAlive()
        || !frame.sameFrame(origin)
        || !frame.complete(origin)
        || !snapshot.equals(source.saveWithoutId(new net.minecraft.nbt.CompoundTag()))) {
      rejectCompanion(pet, source);
      return null;
    }
    // Keep the original until admission succeeds, including Forge spawn callbacks.
    source.dropLeash(true, true);
    source.remove(Entity.RemovalReason.CHANGED_DIMENSION);
    origin.resetEmptyTime();
    target.resetEmptyTime();
    return pet;
  }

  private static void rejectCompanion(FriendlySlime copy, FriendlySlime source) {
    if (copy.isAddedToWorld() && copy.isRemoved()) copy.revive();
    copy.discard();
    if (source.isAlive() && source.level() instanceof ServerLevel current)
      CompanionRegistry.get(current.getServer()).observe(source);
  }

  @Nullable
  private static PortalInfo portalInfo(
      Entity entity,
      ServerLevel origin,
      ServerLevel destination,
      SlimicPortalShape source,
      Direction.Axis axis) {
    if (!entity.isAlive()
        || entity.isRemoved()
        || entity.level() != origin
        || !source.sameFrame(origin)
        || !source.complete(origin)) return null;
    BlockPos target =
        destination.getWorldBorder().clampToBounds(entity.getX(), entity.getY(), entity.getZ());
    Vec3 point = destination(destination, entity, target, axis);
    if (point == null
        || !entity.isAlive()
        || entity.isRemoved()
        || entity.level() != origin
        || entity.isPassenger()
        || entity.isVehicle()
        || !source.sameFrame(origin)
        || !source.complete(origin)) return null;
    var exit = BlockPos.containing(point);
    destination
        .getChunkSource()
        .addRegionTicket(TicketType.PORTAL, new net.minecraft.world.level.ChunkPos(exit), 3, exit);
    return new PortalInfo(point, Vec3.ZERO, entity.getYRot(), entity.getXRot());
  }

  @Nullable
  private static Vec3 destination(
      ServerLevel level, Entity entity, BlockPos center, Direction.Axis axis) {
    var index = SlimicPortalIndex.get(level);
    for (var pos : index.near(center)) {
      if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
        index.forget(pos);
        continue;
      }
      var state = level.getBlockState(pos);
      if (!state.is(ModWorldBlocks.PORTAL.get())) {
        index.forget(pos);
        continue;
      }
      var shape = SlimicPortalShape.find(level, pos, state.getValue(SlimicPortalBlock.AXIS));
      if (shape == null || !shape.complete(level)) {
        index.forget(pos);
        continue;
      }
      Vec3 point = landing(level, entity, shape);
      if (point != null) return point;
    }
    for (int radius = 0; radius <= 16; radius += 2)
      for (int x = -radius; x <= radius; x += 2)
        for (int z = -radius; z <= radius; z += 2) {
          if (radius != 0 && Math.abs(x) != radius && Math.abs(z) != radius) continue;
          int worldX = center.getX() + x, worldZ = center.getZ() + z;
          var horizontal = new BlockPos(worldX, 64, worldZ);
          if (!level.getWorldBorder().isWithinBounds(horizontal.offset(-4, 0, -4))
              || !level.getWorldBorder().isWithinBounds(horizontal.offset(4, 0, 4))) continue;
          int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ);
          // A landing platform may stand above water or a void without draining the sea.
          if (y < level.getMinBuildHeight() + 2) y = Math.max(level.getSeaLevel() + 1, 64);
          if (!level.getFluidState(new BlockPos(worldX, y - 1, worldZ)).isEmpty()) y++;
          if (y + 5 >= level.getMaxBuildHeight()) continue;
          var shape = new SlimicPortalShape(new BlockPos(worldX, y, worldZ), axis, 2, 3);
          var changes = exitChanges(level, shape);
          if (changes == null) continue;
          if (!place(
              level,
              entity,
              changes,
              () ->
                  entity.isAlive()
                      && shape.sameFrame(level)
                      && shape.complete(level)
                      && landing(level, entity, shape) != null)) continue;
          Vec3 point = landing(level, entity, shape);
          if (point == null) continue;
          index.remember(shape.bottom());
          return point;
        }
    return null;
  }

  @Nullable
  private static Map<BlockPos, BlockState> exitChanges(ServerLevel level, SlimicPortalShape shape) {
    Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
    Direction normal = shape.across().getClockWise();
    Block support =
        level.dimension().equals(SlimicWorld.DIMENSION)
            ? ModWorldBlocks.block("slimic_stone")
            : Blocks.STONE;
    for (int x = -2; x <= 3; x++)
      for (int z = -2; z <= 2; z++) {
        BlockPos floor = shape.bottom().relative(shape.across(), x).relative(normal, z).below();
        var state = level.getBlockState(floor);
        if (level.getBlockEntity(floor) != null || !state.getFluidState().isEmpty()) return null;
        if (!state.isFaceSturdy(level, floor, Direction.UP)) {
          if (!state.canBeReplaced()) return null;
          changes.put(floor, support.defaultBlockState());
        }
        for (int y = 0; y <= 3; y++) {
          var pos = floor.above(y + 1);
          var air = level.getBlockState(pos);
          if (level.getBlockEntity(pos) != null
              || !air.getFluidState().isEmpty()
              || !air.isAir() && !air.canBeReplaced()) return null;
        }
      }
    for (int x = -1; x <= shape.width(); x++)
      for (int y = -1; y <= shape.height(); y++) {
        var pos = shape.cell(x, y);
        boolean edge = x == -1 || x == shape.width() || y == -1 || y == shape.height();
        if (edge) {
          var old = level.getBlockState(pos);
          if (!old.isAir() && !old.canBeReplaced() && !old.is(EXIT_TERRAIN)) return null;
        }
        changes.put(
            pos,
            edge
                ? Blocks.SLIME_BLOCK.defaultBlockState()
                : ModWorldBlocks.PORTAL
                    .get()
                    .defaultBlockState()
                    .setValue(SlimicPortalBlock.AXIS, shape.axis()));
      }
    return changes;
  }

  @Nullable
  private static Vec3 landing(ServerLevel level, Entity entity, SlimicPortalShape shape) {
    Direction along = shape.across(), normal = along.getClockWise();
    Vec3 center =
        Vec3.atBottomCenterOf(shape.bottom())
            .add(
                along.getStepX() * (shape.width() - 1) * .5,
                0,
                along.getStepZ() * (shape.width() - 1) * .5);
    for (double offset : new double[] {1.5, -1.5, 2, -2, 0}) {
      Vec3 point = center.add(normal.getStepX() * offset, 0, normal.getStepZ() * offset);
      AABB box = entity.getBoundingBox().move(point.subtract(entity.position()));
      if (box.minY <= level.getMinBuildHeight()
          || box.maxY >= level.getMaxBuildHeight()
          || !level.getWorldBorder().isWithinBounds(box)
          || !level.noCollision(entity, box)
          || level.containsAnyLiquid(box)) continue;
      boolean supported = true;
      for (BlockPos floor :
          BlockPos.betweenClosed(
              BlockPos.containing(box.minX + .001, point.y - .001, box.minZ + .001),
              BlockPos.containing(box.maxX - .001, point.y - .001, box.maxZ - .001))) {
        var state = level.getBlockState(floor);
        if (!state.isFaceSturdy(level, floor, Direction.UP)
            || state.is(Blocks.MAGMA_BLOCK)
            || state.is(Blocks.CACTUS)
            || state.is(Blocks.CAMPFIRE)
            || state.is(Blocks.SOUL_CAMPFIRE)) supported = false;
      }
      if (supported) return point;
    }
    return null;
  }
}
