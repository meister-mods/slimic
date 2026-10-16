package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.registry.ModItems;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.items.ItemHandlerHelper;

public final class LumberjackSlime extends FieldSlime {
  private static final EntityDataAccessor<ItemStack> REQUIRED_SAPLING =
      SynchedEntityData.defineId(LumberjackSlime.class, EntityDataSerializers.ITEM_STACK);
  private static final EntityDataAccessor<Integer> REQUIRED_COUNT =
      SynchedEntityData.defineId(LumberjackSlime.class, EntityDataSerializers.INT);
  private int observedBagRevision = -1;

  @Override
  protected void defineSynchedData() {
    super.defineSynchedData();
    entityData.define(REQUIRED_SAPLING, ItemStack.EMPTY);
    entityData.define(REQUIRED_COUNT, 0);
  }

  public ItemStack getRequiredSapling() {
    return entityData.get(REQUIRED_SAPLING);
  }

  public int getRequiredSaplingCount() {
    return entityData.get(REQUIRED_COUNT);
  }

  private static final Map<Block, Block> TREES =
      Map.of(
          Blocks.OAK_LOG,
          Blocks.OAK_SAPLING,
          Blocks.SPRUCE_LOG,
          Blocks.SPRUCE_SAPLING,
          Blocks.BIRCH_LOG,
          Blocks.BIRCH_SAPLING,
          Blocks.JUNGLE_LOG,
          Blocks.JUNGLE_SAPLING,
          Blocks.ACACIA_LOG,
          Blocks.ACACIA_SAPLING,
          Blocks.DARK_OAK_LOG,
          Blocks.DARK_OAK_SAPLING,
          Blocks.MANGROVE_LOG,
          Blocks.MANGROVE_PROPAGULE,
          Blocks.CHERRY_LOG,
          Blocks.CHERRY_SAPLING);
  private BlockSurvey survey;
  private UUID pickupTarget;
  private final Map<UUID, Long> unavailablePickups = new HashMap<>();
  private final ArrayDeque<BlockPos> candidates = new ArrayDeque<>();
  private final ArrayDeque<BlockPos> logs = new ArrayDeque<>();
  private final Map<BlockPos, ItemStack> plantings = new LinkedHashMap<>();
  private final ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
  private final Set<BlockPos> visited = new HashSet<>();
  private final Set<BlockPos> tree = new HashSet<>();
  private final Set<BlockPos> roots = new HashSet<>();
  private Block treeBlock;
  private Block plannedLog;
  private BlockPos leafWitness;
  private boolean leafy;
  private int scanDelay;
  private BlockPos skipped;
  private BlockPos discoveryRoot;
  private final Map<BlockPos, Long> unavailable = new HashMap<>();
  private boolean missingSaplings;
  private BlockPos missingRoot;
  private BlockPos missingLeaf;
  private long skippedUntil;

  public LumberjackSlime(EntityType<? extends LumberjackSlime> type, Level level) {
    super(type, level);
  }

  public static boolean isAxe(ItemStack stack) {
    return !stack.isEmpty() && stack.canPerformAction(ToolActions.AXE_DIG);
  }

  @Override
  public boolean matchesTool(ItemStack stack) {
    return isAxe(stack);
  }

  @Override
  protected WorkStatus.Order workOrder() {
    return WorkStatus.Order.LOG;
  }

  @Override
  protected String workTag() {
    return "LoggingWork";
  }

  @Override
  protected String bagTagName() {
    return "LumberjackBag";
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.LUMBERJACK_SLIME.get());
  }

  public int getPendingPlantings() {
    return plantings.size();
  }

  @Override
  public void onReleased() {
    boolean hadHome = home != null;
    super.onReleased();
    if (hadHome && !originChanged) return;
    home = blockPosition();
    survey = null;
    candidates.clear();
    logs.clear();
    resetDiscovery();
    target = null;
    unavailable.clear();
    unavailablePickups.clear();
    pickupTarget = null;
    clearSaplingRequirement();
    // Old stumps remain world edits at their original coordinates; return reserved seeds to cargo.
    for (var seed : plantings.values()) {
      var left = ItemHandlerHelper.insertItemStacked(getBag(), seed.copy(), false);
      if (!left.isEmpty()) spawnAtLocation(left);
    }
    plantings.clear();
  }

  private boolean ground(BlockState state) {
    return state.is(BlockTags.DIRT) || state.is(Blocks.FARMLAND);
  }

  private boolean root(ServerLevel level, BlockPos pos) {
    return level.hasChunkAt(pos.below()) && ground(level.getBlockState(pos.below()));
  }

  private boolean naturalLeaf(BlockState state) {
    return state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT);
  }

  private void resetDiscovery() {
    frontier.clear();
    visited.clear();
    tree.clear();
    roots.clear();
    treeBlock = null;
    leafy = false;
    leafWitness = null;
  }

  private void clearSaplingRequirement() {
    missingSaplings = false;
    missingRoot = null;
    missingLeaf = null;
    entityData.set(REQUIRED_SAPLING, ItemStack.EMPTY);
    entityData.set(REQUIRED_COUNT, 0);
    if (status == Status.NO_SEEDS) status = Status.IDLE;
  }

  private void skip(ServerLevel level, BlockPos pos) {
    skipped = discoveryRoot == null ? pos : discoveryRoot;
    unavailable.put(skipped, level.getGameTime() + 200);
    skippedUntil = level.getGameTime() + 200;
    resetDiscovery();
    logs.clear();
  }

  private boolean discover(ServerLevel level) {
    for (int budget = 0; budget < 64 && !frontier.isEmpty(); budget++) {
      BlockPos pos = frontier.remove();
      if (!visited.add(pos)) continue;
      if (visited.size() > 4096 || !inArea(pos) || !level.hasChunkAt(pos)) {
        BlockPos failed = tree.stream().findFirst().orElse(getWorkHome());
        skip(level, failed);
        status = Status.UNREACHABLE;
        return false;
      }
      if (!level.getBlockState(pos).is(treeBlock)) continue;
      tree.add(pos);
      if (tree.size() > 512) {
        skip(level, pos);
        status = Status.UNREACHABLE;
        return false;
      }
      if (root(level, pos)) roots.add(pos);
      for (int dx = -1; dx <= 1; dx++)
        for (int dy = -1; dy <= 1; dy++)
          for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dy == 0 && dz == 0) continue;
            BlockPos next = pos.offset(dx, dy, dz);
            if (!level.hasChunkAt(next)) {
              skip(level, pos);
              status = Status.UNREACHABLE;
              return false;
            }
            BlockState state = level.getBlockState(next);
            if (naturalLeaf(state)) {
              leafy = true;
              leafWitness = next;
            }
            if (state.is(treeBlock) && !visited.contains(next)) frontier.add(next);
          }
    }
    if (!frontier.isEmpty()) {
      status = missingSaplings ? Status.NO_SEEDS : Status.SURVEYING;
      return false;
    }
    if (!leafy || roots.isEmpty()) {
      resetDiscovery();
      return false;
    }
    Block sapling = TREES.get(treeBlock);
    int available = 0;
    for (int i = 0; i < getCapacity(); i++)
      if (getBag().getStackInSlot(i).is(sapling.asItem()))
        available += getBag().getStackInSlot(i).getCount();
    if (available < roots.size()) {
      entityData.set(REQUIRED_SAPLING, new ItemStack(sapling));
      entityData.set(REQUIRED_COUNT, roots.size());
      missingSaplings = true;
      missingRoot = discoveryRoot;
      missingLeaf = leafWitness;
      if (discoveryRoot != null) unavailable.put(discoveryRoot, level.getGameTime() + 100);
      resetDiscovery();
      status = Status.NO_SEEDS;
      return false;
    }
    if (leafWitness == null
        || !level.hasChunkAt(leafWitness)
        || !naturalLeaf(level.getBlockState(leafWitness))
        || tree.stream()
            .anyMatch(pos -> !level.hasChunkAt(pos) || !level.getBlockState(pos).is(treeBlock))
        || roots.stream().anyMatch(pos -> !root(level, pos))) {
      resetDiscovery();
      return false;
    }
    clearSaplingRequirement();
    plannedLog = treeBlock;
    for (BlockPos root : roots) {
      for (int i = 0; i < getCapacity(); i++)
        if (getBag().getStackInSlot(i).is(sapling.asItem())) {
          plantings.put(root, getBag().extractItem(i, 1, false));
          break;
        }
    }
    tree.stream()
        .sorted(
            Comparator.<BlockPos>comparingInt(p -> p.getY())
                .reversed()
                .thenComparingLong(BlockPos::asLong))
        .forEach(logs::add);
    resetDiscovery();
    return true;
  }

  private boolean replant(ServerLevel level) {
    if (!logs.isEmpty()) return false;
    for (var entry : new ArrayList<>(plantings.entrySet())) {
      BlockPos pos = entry.getKey();
      if (!level.hasChunkAt(pos) || !inArea(pos)) continue;
      if (!level.getBlockState(pos).isAir() || !root(level, pos)) {
        var left = ItemHandlerHelper.insertItemStacked(getBag(), entry.getValue().copy(), false);
        if (!left.isEmpty()) {
          status = Status.FULL;
          return true;
        }
        plantings.remove(pos);
        continue;
      }
      if (!approach(pos)) return true;
      var state = ((BlockItem) entry.getValue().getItem()).getBlock().defaultBlockState();
      if (place(level, pos, state)) {
        plantings.remove(pos);
        completed();
      }
      return true;
    }
    return false;
  }

  private boolean collectMaterial(ServerLevel level) {
    if (tickCount % 20 != 0 && pickupTarget == null) return false;
    unavailablePickups.values().removeIf(until -> until <= level.getGameTime());
    double r = SlimeStat.DETECTION_RANGE.value(this);
    var items =
        level.getEntitiesOfClass(
            ItemEntity.class, new AABB(getWorkHome()).inflate(r), this::collectible);
    var item =
        items.stream()
            .filter(e -> !unavailablePickups.containsKey(e.getUUID()))
            .filter(e -> pickupTarget == null || e.getUUID().equals(pickupTarget))
            .min(Comparator.comparingDouble(this::distanceToSqr))
            .orElse(null);
    if (item == null) {
      pickupTarget = null;
      return false;
    }
    pickupTarget = item.getUUID();
    if (!approach(item.blockPosition())) {
      if (status == Status.UNREACHABLE) {
        unavailablePickups.put(pickupTarget, level.getGameTime() + 200);
        pickupTarget = null;
        target = null;
        return false;
      }
      return true;
    }
    var player = worker(level);
    if (ForgeEventFactory.onItemPickup(item, player) < 0 || !collectible(item)) {
      unavailablePickups.put(pickupTarget, level.getGameTime() + 200);
      pickupTarget = null;
      target = null;
      getNavigation().stop();
      return false;
    }
    var before = item.getItem().copy();
    var left = ItemHandlerHelper.insertItemStacked(getBag(), before.copy(), false);
    int count = before.getCount() - left.getCount();
    if (count == 0) {
      status = Status.FULL;
      getNavigation().stop();
      return true;
    }
    take(item, count);
    item.setItem(left);
    ForgeEventFactory.firePlayerItemPickupEvent(player, item, before.copyWithCount(count));
    if (left.isEmpty()) item.discard();
    pickupTarget = null;
    completed();
    return true;
  }

  private boolean collectible(ItemEntity item) {
    if (!item.isAlive()
        || item.hasPickUpDelay()
        || !inArea(item.blockPosition())
        || !(item.getItem().getItem() instanceof BlockItem block)
        || (!TREES.containsKey(block.getBlock()) && !TREES.containsValue(block.getBlock())))
      return false;
    var data = item.saveWithoutId(new CompoundTag());
    return !data.hasUUID("Owner")
        || item.lifespan - item.getAge() <= 200
        || data.getUUID("Owner").equals(getOwnerUUID());
  }

  @Override
  protected void workTick(ServerLevel level) {
    if (observedBagRevision != bagRevision) {
      observedBagRevision = bagRevision;
      unavailable.clear();
      clearSaplingRequirement();
      survey = null;
      candidates.clear();
      scanDelay = 0;
    }

    if (missingSaplings
        && (missingRoot == null
            || missingLeaf == null
            || !level.hasChunkAt(missingRoot)
            || !level.hasChunkAt(missingLeaf)
            || !root(level, missingRoot)
            || !(getRequiredSapling().getItem() instanceof BlockItem seed)
            || TREES.get(level.getBlockState(missingRoot).getBlock()) != seed.getBlock()
            || !naturalLeaf(level.getBlockState(missingLeaf)))) clearSaplingRequirement();

    if (logs.isEmpty()
        && plantings.isEmpty()
        && (treeBlock == null || status == Status.NO_SEEDS)
        && collectMaterial(level)) return;
    if (replant(level)) return;
    if (!logs.isEmpty()) {
      BlockPos pos = logs.peek();
      if (!level.hasChunkAt(pos) || !inArea(pos)) {
        status = Status.UNREACHABLE;
        getNavigation().stop();
        return;
      }
      if (plannedLog == null || !level.getBlockState(pos).is(plannedLog)) {
        logs.remove();
        return;
      }
      BlockPos stump =
          plantings.keySet().stream()
              .min(Comparator.comparingDouble(p -> p.distSqr(blockPosition())))
              .orElse(pos);
      if (!approach(stump)) return;
      if (harvest(level, pos)) logs.remove();
      return;
    }
    if (treeBlock != null) {
      discover(level);
      return;
    }
    if (candidates.isEmpty()) {
      if (scanDelay > 0) {
        scanDelay--;
        status = missingSaplings ? Status.NO_SEEDS : Status.IDLE;
        getNavigation().stop();
        return;
      }
      if (survey == null) {
        unavailable.values().removeIf(until -> until <= level.getGameTime());
      }
      if (survey == null)
        survey =
            new BlockSurvey(
                level,
                getWorkHome(),
                SlimeStat.DETECTION_RANGE.value(this),
                s -> TREES.containsKey(s.getBlock()));
      candidates.addAll(survey.next(512));
      if (survey.isDone()) {
        survey = null;
        scanDelay = 40;
      }
      if (candidates.isEmpty()) {
        status = missingSaplings ? Status.NO_SEEDS : Status.SURVEYING;
        return;
      }
    }
    for (int i = 0; i < 32 && !candidates.isEmpty(); i++) {
      BlockPos pos = candidates.remove();
      if (!level.hasChunkAt(pos)
          || !inArea(pos)
          || !root(level, pos)
          || unavailable.containsKey(pos)
          || (pos.equals(skipped) && level.getGameTime() < skippedUntil)) continue;
      Block block = level.getBlockState(pos).getBlock();
      if (!TREES.containsKey(block)) continue;
      treeBlock = block;
      discoveryRoot = pos;
      frontier.add(pos);
      status = missingSaplings ? Status.NO_SEEDS : Status.SURVEYING;
      return;
    }
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    if (plannedLog != null)
      tag.putString("LoggingBlock", BuiltInRegistries.BLOCK.getKey(plannedLog).toString());
    tag.putLongArray("LoggingLogs", logs.stream().mapToLong(BlockPos::asLong).toArray());
    ListTag saved = new ListTag();
    for (var entry : plantings.entrySet()) {
      var p = new CompoundTag();
      p.putLong("Pos", entry.getKey().asLong());
      p.put("Seed", entry.getValue().save(new CompoundTag()));
      saved.add(p);
    }
    tag.put("LoggingPlantings", saved);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    var logKey = ResourceLocation.tryParse(tag.getString("LoggingBlock"));
    plannedLog = logKey == null ? null : BuiltInRegistries.BLOCK.get(logKey);
    logs.clear();
    for (long pos : tag.getLongArray("LoggingLogs"))
      if (logs.size() < 512) logs.add(BlockPos.of(pos));
    plantings.clear();
    for (Tag saved : tag.getList("LoggingPlantings", Tag.TAG_COMPOUND)) {
      var p = (CompoundTag) saved;
      var seed = ItemStack.of(p.getCompound("Seed"));
      if (plantings.size() < 512
          && seed.getItem() instanceof BlockItem b
          && TREES.containsValue(b.getBlock()))
        plantings.put(BlockPos.of(p.getLong("Pos")), seed.copyWithCount(1));
    }
    resetDiscovery();
    candidates.clear();
    survey = null;
    unavailable.clear();
    unavailablePickups.clear();
    pickupTarget = null;
    clearSaplingRequirement();
  }
}
