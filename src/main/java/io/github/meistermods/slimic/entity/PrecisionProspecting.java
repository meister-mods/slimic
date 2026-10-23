package io.github.meistermods.slimic.entity;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

/** Incremental, loaded-terrain-only planning and physical two-by-two excavation. */
public final class PrecisionProspecting {
  private static final int MAX_RADIUS = 32, SCAN_BUDGET = 1024, ROUTE_BUDGET = 96, MAX_NODES = 2048;
  private static final int SECTION_BUDGET = 32;

  private record SurveySection(BlockPos min, BlockPos max) {}

  private final NextGenerationMiningSlime slime;
  private final SlimeBlockBreaking breaking = new SlimeBlockBreaking();
  private final Deque<BlockPos> route = new ArrayDeque<>();
  private final Deque<BlockPos> trail = new ArrayDeque<>();
  private BlockPos pending;
  private final Set<BlockPos> passageFloors = new HashSet<>();
  private final Map<Long, Long> rejected = new HashMap<>();
  private BlockPos shaft, cell, origin, ore, scanOrigin, bestOre;
  private Search search;
  private boolean descended, returning, detouring;
  private int scanCursor, scanRadius, retryAt, lastMoveTick = -1, stalledTicks;
  private double bestDistance = Double.MAX_VALUE, closest = Double.MAX_VALUE;
  private BlockPos breakingPos;
  private final List<SurveySection> scanSections = new ArrayList<>();
  private int scanSectionCursor;

  PrecisionProspecting(NextGenerationMiningSlime slime) {
    this.slime = slime;
  }

  public static int radius(FriendlySlime slime) {
    return (int) Math.min(MAX_RADIUS, Math.max(0, SlimeStat.DETECTION_RANGE.value(slime)));
  }

  public BlockPos shaftFront() {
    return shaft;
  }

  public BlockPos nextCell() {
    return route.isEmpty() ? pending : route.peekFirst();
  }

  public void interrupt() {
    breaking.reset(slime);
    breakingPos = null;
    search = null;
    if (!route.isEmpty()) pending = route.peekFirst();
    route.clear();
    ore = null;
    resetScan();
    lastMoveTick = -1;
    closest = Double.MAX_VALUE;
    stalledTicks = 0;
  }

  public void reset() {
    interrupt();
    shaft = cell = origin = null;
    descended = returning = detouring = false;
    pending = null;
    trail.clear();
    passageFloors.clear();
    rejected.clear();
    retryAt = 0;
  }

  public void relocate(boolean dimensionChanged) {
    if (dimensionChanged || cell != null && point(cell).distanceToSqr(slime.position()) > 16)
      reset();
    else interrupt();
  }

  public void filtersChanged() {
    interrupt();
    if (pending != null && (cell == null || pending.getY() >= cell.getY())) pending = null;
    rejected.clear();
  }

  private void planning() {
    if (slime.status != MiningSlime.Status.NO_TOOL
        && slime.status != MiningSlime.Status.NEEDS_TIER
        && slime.status != MiningSlime.Status.UNREACHABLE) slime.status = MiningSlime.Status.IDLE;
  }

  private void resetScan() {
    scanCursor = 0;
    scanSectionCursor = 0;
    scanSections.clear();
    scanOrigin = bestOre = null;
    bestDistance = Double.MAX_VALUE;
  }

  private static Vec3 point(BlockPos pos) {
    return new Vec3(pos.getX() + 1, pos.getY(), pos.getZ() + 1);
  }

  private boolean near(BlockPos pos) {
    var delta = point(pos).subtract(slime.position());
    return delta.x * delta.x + delta.z * delta.z < .09 && Math.abs(delta.y) < .8;
  }

  private void stop(MiningSlime.Status reason, boolean retry) {
    slime.status = reason;
    slime.getNavigation().stop();
    breaking.reset(slime);
    breakingPos = null;
    if (retry) retryAt = slime.tickCount + 60;
  }

  private boolean loaded(ServerLevel world, BlockPos pos) {
    return !world.isOutsideBuildHeight(pos)
        && world.hasChunkAt(pos)
        && world.getWorldBorder().isWithinBounds(pos);
  }

  private BlockState state(ServerLevel world, BlockPos pos) {
    var chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
    return chunk == null ? Blocks.BEDROCK.defaultBlockState() : chunk.getBlockState(pos);
  }

  private boolean breakable(ServerLevel world, BlockPos pos, BlockState state) {
    return !state.hasBlockEntity()
        && state.getDestroySpeed(world, pos) >= 0
        && state.getFluidState().isEmpty()
        && !state.is(Blocks.MAGMA_BLOCK)
        && !state.is(BlockTags.LOGS)
        && !state.is(BlockTags.LEAVES)
        && !state.is(BlockTags.MINEABLE_WITH_AXE)
        && !state.is(BlockTags.MINEABLE_WITH_HOE)
        && !QuarryStaircaseData.get(world).protects(pos);
  }

  private static boolean inside(BlockPos pos, BlockPos base) {
    return pos.getX() >= base.getX()
        && pos.getX() < base.getX() + 2
        && pos.getZ() >= base.getZ()
        && pos.getZ() < base.getZ() + 2
        && pos.getY() >= base.getY()
        && pos.getY() < base.getY() + 2;
  }

  private boolean stableFloor(ServerLevel world, BlockPos pos) {
    var state = state(world, pos);
    for (int depth = 0; state.getBlock() instanceof FallingBlock; depth++) {
      if (depth >= 32 || !loaded(world, pos.below())) return false;
      pos = pos.below();
      state = state(world, pos);
    }
    return state.getFluidState().isEmpty() && state.isFaceSturdy(world, pos, Direction.UP);
  }

  /** Every prospective passage has a supported floor and no opening into adjacent fluids. */
  private boolean safe(ServerLevel world, BlockPos base) {
    for (int x = 0; x < 2; x++)
      for (int z = 0; z < 2; z++) {
        var foot = base.offset(x, -1, z);
        if (!loaded(world, foot)) return false;
        var ground = state(world, foot);
        if (!ground.getFluidState().isEmpty()
            || ground.is(Blocks.MAGMA_BLOCK)
            || !ground.isFaceSturdy(world, foot, Direction.UP)
            || !stableFloor(world, foot)) return false;
        var roof = base.offset(x, 2, z);
        if (!loaded(world, roof) || state(world, roof).getBlock() instanceof FallingBlock)
          return false;
        for (int y = 0; y < 2; y++) {
          var pos = base.offset(x, y, z);
          if (!loaded(world, pos)) return false;
          var state = state(world, pos);
          if (passageFloors.contains(pos) || !state.isAir() && !breakable(world, pos, state))
            return false;
          for (var direction : Direction.values()) {
            var neighbor = pos.relative(direction);
            if (inside(neighbor, base)) continue;
            if (!loaded(world, neighbor) || !state(world, neighbor).getFluidState().isEmpty())
              return false;
          }
        }
      }
    return true;
  }

  private boolean open(ServerLevel world, BlockPos base) {
    for (int x = 0; x < 2; x++)
      for (int z = 0; z < 2; z++)
        for (int y = 0; y < 2; y++) if (!state(world, base.offset(x, y, z)).isAir()) return false;
    return true;
  }

  private FakePlayer worker(ServerLevel world) {
    var player =
        FakePlayerFactory.get(world, new GameProfile(slime.getOwnerUUID(), "SlimeProspector"));
    player.setGameMode(GameType.SURVIVAL);
    player.moveTo(slime.position());
    player.setOnGround(true);
    return player;
  }

  private boolean visible(BlockPos pos) {
    double reach = Math.min(SlimeStat.REACH.value(slime), SlimeStat.DETECTION_RANGE.value(slime));
    if (slime.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > reach * reach) return false;
    var hit =
        LoadedBlockView.clip(
            slime.level(),
            new ClipContext(
                slime.getEyePosition(),
                Vec3.atCenterOf(pos),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                slime));
    return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
  }

  private boolean excavate(ServerLevel world, BlockPos base) {
    var candidates = new ArrayList<BlockPos>();
    for (int x = 0; x < 2; x++)
      for (int z = 0; z < 2; z++)
        for (int y = 1; y >= 0; y--) {
          var pos = base.offset(x, y, z);
          if (!state(world, pos).isAir()) candidates.add(pos);
        }
    if (candidates.isEmpty()) return true;
    candidates.sort(
        Comparator.<BlockPos>comparingInt(p -> slime.allowsOre(state(world, p)) ? 0 : 1)
            .thenComparingDouble(p -> p.distToCenterSqr(slime.getEyePosition())));
    BlockPos target = null;
    for (var candidate : candidates)
      if (visible(candidate)) {
        target = candidate;
        break;
      }
    if (target == null) {
      if (approachFace(world, base, candidates)) return false;
      stop(MiningSlime.Status.UNREACHABLE, true);
      return false;
    }
    var state = state(world, target);
    int slot = slime.toolFor(state);
    if (slot < 0) {
      slime.requestMiningTool(state);
      breaking.reset(slime);
      return false;
    }
    if (!target.equals(breakingPos)) {
      breaking.reset(slime);
      breakingPos = target;
    }
    var tool = slime.getTool(slot);
    var originalTool = tool.copy();
    var player = worker(world);
    var previous = player.getMainHandItem();
    player.setItemInHand(InteractionHand.MAIN_HAND, tool);
    try {
      slime.status = MiningSlime.Status.MINING;
      if (!breaking.advance(slime, player, target, state, tool)) return false;
      int experience = ForgeHooks.onBlockBreakEvent(world, GameType.SURVIVAL, player, target);
      if (experience < 0
          || !world.mayInteract(player, target)
          || !breakable(world, target, state)
          || !slime.canContinueWork(player)
          || !slime.specializedMining()
          || !safe(world, base)
          || !state(world, target).equals(state)
          || !ItemStack.matches(originalTool, slime.getTool(slot))) {
        stop(MiningSlime.Status.UNREACHABLE, true);
        return false;
      }
      var drops =
          world.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)
              ? Block.getDrops(state, world, target, null, player, tool)
              : List.<ItemStack>of();
      var simulation = slime.getBag().copyForSimulation();
      for (var drop : drops)
        if (!SlimeBag.insertItemStacked(simulation, drop.copy(), false).isEmpty()) {
          slime.pauseForFullBag();
          breaking.reset(slime);
          return false;
        }
      state.getBlock().playerWillDestroy(world, target, state, player);
      if (!slime.canContinueWork(player)
          || !slime.specializedMining()
          || !state(world, target).equals(state)
          || !state.onDestroyedByPlayer(
              world, target, player, true, state(world, target).getFluidState())) {
        breaking.reset(slime);
        return false;
      }
      state.getBlock().destroy(world, target, state);
      for (var drop : drops) SlimeBag.insertItemStacked(slime.getBag(), drop, false);
      tool.mineBlock(world, state, target, player);
      slime.spendStamina(1);
      slime.recordCompletedWork();
      if (experience > 0 && world.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS))
        SlimeToolExperience.award(world, Vec3.atCenterOf(target), tool, experience);
      world.levelEvent(2001, target, Block.getId(state));
      breaking.reset(slime);
      breakingPos = null;
      return false;
    } finally {
      player.setItemInHand(InteractionHand.MAIN_HAND, previous);
    }
  }

  private boolean approachFace(ServerLevel world, BlockPos base, List<BlockPos> candidates) {
    Vec3 best = null;
    double nearest = Double.MAX_VALUE;
    double reach = Math.min(SlimeStat.REACH.value(slime), SlimeStat.DETECTION_RANGE.value(slime));
    for (double dx : new double[] {.5, 1, 1.5})
      for (double dz : new double[] {.5, 1, 1.5}) {
        var stand = new Vec3(base.getX() + dx, base.getY(), base.getZ() + dz);
        var delta = stand.subtract(slime.position());
        if (Math.abs(delta.y) > 1.05
            || delta.horizontalDistanceSqr() > 9
            || delta.lengthSqr() < .01) continue;
        var box = slime.getBoundingBox().move(delta);
        var swept =
            slime
                .getBoundingBox()
                .move(0, Math.max(0, delta.y), 0)
                .expandTowards(delta.x, 0, delta.z);
        if (!world.noCollision(slime, box) || !world.noCollision(slime, swept)) continue;
        var eye = stand.add(0, slime.getEyeHeight(), 0);
        boolean sight = false;
        for (var candidate : candidates) {
          if (eye.distanceToSqr(Vec3.atCenterOf(candidate)) > reach * reach) continue;
          var hit =
              LoadedBlockView.clip(
                  world,
                  new ClipContext(
                      eye,
                      Vec3.atCenterOf(candidate),
                      ClipContext.Block.COLLIDER,
                      ClipContext.Fluid.NONE,
                      slime));
          if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(candidate)) {
            sight = true;
            break;
          }
        }
        if (sight && delta.lengthSqr() < nearest) {
          nearest = delta.lengthSqr();
          best = stand;
        }
      }
    if (best == null) return false;
    slime.status = MiningSlime.Status.MOVING;
    slime.getNavigation().stop();
    slime.getMoveControl().setWantedPosition(best.x, best.y, best.z, .6);
    return true;
  }

  private boolean move(BlockPos destination) {
    if (near(destination)) {
      slime.getNavigation().stop();
      var delta = slime.getDeltaMovement();
      slime.setDeltaMovement(0, delta.y, 0);
      lastMoveTick = -1;
      stalledTicks = 0;
      closest = Double.MAX_VALUE;
      return true;
    }
    var target = point(destination);
    double distance = target.distanceToSqr(slime.position());
    if (lastMoveTick != slime.tickCount) {
      lastMoveTick = slime.tickCount;
      if (distance < closest - .01) {
        closest = distance;
        stalledTicks = 0;
      } else stalledTicks++;
    }
    if (stalledTicks > 200) {
      // The next level may already have removed the old cell's floor. Keep that destination so
      // a temporary movement obstruction cannot strand the worker on an invalid old cell.
      interrupt();
      stop(MiningSlime.Status.UNREACHABLE, true);
      return false;
    }
    slime.status = MiningSlime.Status.MOVING;
    slime.getNavigation().stop();
    slime.getMoveControl().setWantedPosition(target.x, target.y, target.z, .7);
    slime.getLookControl().setLookAt(target);
    return false;
  }

  private double sectionDistance(SurveySection section) {
    double dx =
        Math.max(
            Math.max(section.min.getX() - slime.getX(), slime.getX() - section.max.getX() - 1), 0);
    double dy =
        Math.max(
            Math.max(section.min.getY() - slime.getY(), slime.getY() - section.max.getY() - 1), 0);
    double dz =
        Math.max(
            Math.max(section.min.getZ() - slime.getZ(), slime.getZ() - section.max.getZ() - 1), 0);
    return dx * dx + dy * dy + dz * dz;
  }

  private void surveySections(ServerLevel world) {
    var min = scanOrigin.offset(-scanRadius, -scanRadius, -scanRadius);
    var max = scanOrigin.offset(scanRadius, scanRadius, scanRadius);
    int lowY = Math.max(world.getMinBuildHeight(), min.getY());
    int highY = Math.min(world.getMaxBuildHeight() - 1, max.getY());
    for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++)
      for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++)
        for (int y = lowY >> 4; y <= highY >> 4; y++) {
          var section =
              new SurveySection(
                  new BlockPos(
                      Math.max(min.getX(), x << 4),
                      Math.max(lowY, y << 4),
                      Math.max(min.getZ(), z << 4)),
                  new BlockPos(
                      Math.min(max.getX(), (x << 4) + 15),
                      Math.min(highY, (y << 4) + 15),
                      Math.min(max.getZ(), (z << 4) + 15)));
          if (sectionDistance(section) <= (double) scanRadius * scanRadius)
            scanSections.add(section);
        }
    scanSections.sort(Comparator.comparingDouble(this::sectionDistance));
  }

  private boolean scan(ServerLevel world) {
    if (scanOrigin == null) {
      scanOrigin = BlockPos.containing(slime.getX() - .5, slime.getY(), slime.getZ() - .5);
      scanRadius = radius(slime);
      scanCursor = 0;
      bestOre = null;
      bestDistance = Double.MAX_VALUE;
      rejected.values().removeIf(expiry -> expiry <= world.getGameTime());
      surveySections(world);
    }
    int budget = 0;
    for (int sections = 0;
        sections < SECTION_BUDGET
            && budget < SCAN_BUDGET
            && scanSectionCursor < scanSections.size();
        sections++) {
      var section = scanSections.get(scanSectionCursor);
      var chunk =
          world.getChunkSource().getChunkNow(section.min.getX() >> 4, section.min.getZ() >> 4);
      if (chunk == null
          || !chunk.getSections()[chunk.getSectionIndex(section.min.getY())].maybeHas(
              slime::allowsOre)) {
        scanSectionCursor++;
        scanCursor = 0;
        continue;
      }
      int width = section.max.getX() - section.min.getX() + 1;
      int depth = section.max.getZ() - section.min.getZ() + 1;
      int total = width * depth * (section.max.getY() - section.min.getY() + 1);
      while (budget < SCAN_BUDGET && scanCursor < total) {
        int i = scanCursor++;
        budget++;
        var pos = section.min.offset(i % width, i / (width * depth), (i / width) % depth);
        if (!world.getWorldBorder().isWithinBounds(pos) || rejected.containsKey(pos.asLong()))
          continue;
        double distance = pos.distToCenterSqr(slime.position());
        if (distance > (double) scanRadius * scanRadius) continue;
        var state = chunk.getBlockState(pos);
        if (slime.allowsOre(state) && breakable(world, pos, state) && distance < bestDistance) {
          bestDistance = distance;
          bestOre = pos;
        }
      }
      if (scanCursor == total) {
        scanSectionCursor++;
        scanCursor = 0;
      }
    }
    planning();
    if (bestOre == null && scanSectionCursor < scanSections.size()) return false;
    ore = bestOre;
    resetScan();
    return true;
  }

  private void startSearch(ServerLevel world, BlockPos target, boolean bypass) {
    detouring = bypass;
    search = new Search(cell, target, bypass, radius(slime));
  }

  public void tick(ServerLevel world) {
    if (slime.tickCount < retryAt) return;
    if (cell != null && point(cell).distanceToSqr(slime.position()) > 16) reset();
    passageFloors.clear();
    for (var old : trail)
      for (int x = 0; x < 2; x++)
        for (int z = 0; z < 2; z++) passageFloors.add(old.offset(x, -1, z));
    if (shaft == null) {
      cell =
          shaft = origin = BlockPos.containing(slime.getX() - .5, slime.getY(), slime.getZ() - .5);
      descended = false;
    }
    if (slime.oreMask() == 0) {
      stop(MiningSlime.Status.COMPLETE, false);
      return;
    }
    // Older saves can retain the cleared next shaft level after losing the pending route.
    if (descended
        && !returning
        && !detouring
        && pending == null
        && route.isEmpty()
        && shaft.equals(cell.below())
        && safe(world, shaft)
        && open(world, shaft)) pending = shaft;
    if (pending != null && route.isEmpty()) {
      route.add(pending);
      pending = null;
    }
    if (!safe(world, cell) && (route.isEmpty() || !safe(world, route.peekFirst()))) {
      stop(MiningSlime.Status.UNREACHABLE, true);
      return;
    }
    if (!open(world, cell)) {
      excavate(world, cell);
      return;
    }
    if (!route.isEmpty()) {
      var next = route.peekFirst();
      if (!safe(world, next)) {
        if (next.equals(shaft) && next.getY() < cell.getY()) shaft = cell;
        interrupt();
        pending = null;
        detouring = false;
        stop(MiningSlime.Status.UNREACHABLE, true);
        return;
      }
      if (!returning && !detouring && trail.size() >= 256) {
        route.clear();
        pending = null;
        ore = null;
        returning = true;
        return;
      }
      if (!excavate(world, next) || !move(next)) return;
      if (!returning && !detouring && !next.equals(shaft)) trail.addLast(cell);
      cell = next;
      route.removeFirst();
      if (route.isEmpty()) {
        if (detouring) {
          shaft = cell;
          detouring = false;
          trail.clear();
        }
        if (returning && trail.isEmpty()) {
          returning = false;
        }
        if (ore != null && state(world, ore).isAir()) ore = null;
        resetScan();
      }
      return;
    }
    if (!near(cell)) {
      move(cell);
      return;
    }
    if (search != null) {
      var result = search.step(world);
      if (result == null) {
        planning();
        return;
      }
      search = null;
      if (!result.isEmpty()) {
        route.addAll(result);
        return;
      }
      if (detouring) {
        detouring = false;
        stop(MiningSlime.Status.UNREACHABLE, true);
        return;
      }
      if (returning) {
        returning = false;
        stop(MiningSlime.Status.UNREACHABLE, true);
        return;
      }
      if (ore != null) rejected.put(ore.asLong(), world.getGameTime() + 600);
      ore = null;
      resetScan();
      return;
    }
    if (returning && !trail.isEmpty()) {
      route.add(trail.removeLast());
      return;
    }
    if (trail.size() >= 256) returning = true;
    if (descended && !returning) {
      if (!scan(world)) return;
      if (ore != null) {
        int tool = slime.toolFor(state(world, ore));
        if (tool < 0) {
          slime.requestMiningTool(state(world, ore));
          ore = null;
          resetScan();
          return;
        }
        startSearch(world, ore, false);
        return;
      }
    }
    if (!cell.equals(shaft)) {
      if (!trail.isEmpty()) {
        returning = true;
        route.add(trail.removeLast());
        return;
      }
      returning = true;
      search =
          new Search(
              cell,
              shaft,
              false,
              Math.min(64, Math.max(radius(slime), (int) Math.sqrt(cell.distSqr(shaft)) + 2)),
              true);
      return;
    }
    if (shaft.getY() <= world.getMinBuildHeight() + 1) {
      stop(MiningSlime.Status.COMPLETE, false);
      return;
    }
    var down = shaft.below();
    if (safe(world, down)) {
      route.add(down);
      shaft = down;
      descended = true;
      resetScan();
      return;
    }
    startSearch(world, null, true);
  }

  private final class Search {
    private record Node(BlockPos pos, double cost, double estimate) {}

    private final PriorityQueue<Node> open =
        new PriorityQueue<>(Comparator.comparingDouble(n -> n.cost + n.estimate));
    private final Map<BlockPos, Double> cost = new HashMap<>();
    private final Map<BlockPos, Boolean> safety = new HashMap<>();
    private final Map<BlockPos, BlockPos> previous = new HashMap<>();
    private final Set<BlockPos> closed = new HashSet<>();
    private final BlockPos start, goal;
    private final boolean bypass, exact;
    private final int range;

    Search(BlockPos start, BlockPos goal, boolean bypass, int range) {
      this(start, goal, bypass, range, false);
    }

    Search(BlockPos start, BlockPos goal, boolean bypass, int range, boolean exact) {
      this.start = start;
      this.goal = goal;
      this.bypass = bypass;
      this.exact = exact;
      this.range = Math.max(2, range);
      cost.put(start, 0.0);
      open.add(new Node(start, 0, estimate(start)));
    }

    private boolean checked(ServerLevel world, BlockPos pos) {
      return safety.computeIfAbsent(pos, p -> safe(world, p));
    }

    private double estimate(BlockPos pos) {
      return goal == null ? 0 : Math.sqrt(pos.distSqr(goal));
    }

    private boolean goal(ServerLevel world, BlockPos pos) {
      return bypass
          ? !pos.equals(start) && checked(world, pos.below())
          : exact ? pos.equals(goal) : inside(goal, pos);
    }

    List<BlockPos> step(ServerLevel world) {
      for (int budget = 0;
          budget < ROUTE_BUDGET && !open.isEmpty() && closed.size() < MAX_NODES;
          budget++) {
        var node = open.remove();
        var pos = node.pos;
        if (!closed.add(pos)) continue;
        if (goal(world, pos)) {
          var path = new LinkedList<BlockPos>();
          while (!pos.equals(start)) {
            path.addFirst(pos);
            pos = previous.get(pos);
          }
          // A goal in the current cell is excavated before the next search rather than an empty
          // route.
          return path.isEmpty() ? List.of(start) : path;
        }
        var neighbors = new ArrayList<BlockPos>();
        for (var direction : Direction.Plane.HORIZONTAL) {
          neighbors.add(pos.relative(direction));
          if (!bypass)
            for (int rise : new int[] {-1, 1})
              neighbors.add(pos.relative(direction, 2).offset(0, rise, 0));
        }
        for (var next : neighbors) {
          if (Math.abs(next.getX() - start.getX()) > range
              || Math.abs(next.getZ() - start.getZ()) > range
              || Math.abs(next.getY() - start.getY()) > range
              || closed.contains(next)
              || !checked(world, next)) continue;
          double nextCost =
              node.cost
                  + Math.sqrt(pos.distSqr(next))
                  + (open(world, next)
                      ? 0
                      : 3
                              * (Math.abs(next.getX() - pos.getX())
                                  + Math.abs(next.getZ() - pos.getZ()))
                          + 2 * Math.abs(next.getY() - pos.getY()));
          if (nextCost >= cost.getOrDefault(next, Double.MAX_VALUE)) continue;
          cost.put(next, nextCost);
          previous.put(next, pos);
          open.add(new Node(next, nextCost, estimate(next)));
        }
      }
      return open.isEmpty() || closed.size() >= MAX_NODES ? List.of() : null;
    }
  }

  public CompoundTag save() {
    var tag = new CompoundTag();
    tag.putInt("Version", 1);
    tag.putBoolean("Descended", descended);
    tag.putBoolean("Returning", returning);
    tag.putBoolean("Detouring", detouring);
    var next = route.isEmpty() ? pending : route.peekFirst();
    if (next != null) tag.putLong("Pending", next.asLong());
    tag.putLongArray("Trail", trail.stream().mapToLong(BlockPos::asLong).toArray());
    if (shaft != null && cell != null && origin != null) {
      tag.putLong("Shaft", shaft.asLong());
      tag.putLong("Cell", cell.asLong());
      tag.putLong("Origin", origin.asLong());
    }
    return tag;
  }

  public void load(CompoundTag tag) {
    reset();
    if (tag.getInt("Version") != 1
        || !tag.contains("Shaft")
        || !tag.contains("Cell")
        || !tag.contains("Origin")) return;
    var savedShaft = BlockPos.of(tag.getLong("Shaft"));
    var savedCell = BlockPos.of(tag.getLong("Cell"));
    var savedOrigin = BlockPos.of(tag.getLong("Origin"));
    for (var pos : List.of(savedShaft, savedCell, savedOrigin))
      if (slime.level().isOutsideBuildHeight(pos)
          || Math.abs((long) pos.getX()) > 29_999_998
          || Math.abs((long) pos.getZ()) > 29_999_998) return;
    shaft = savedShaft;
    cell = savedCell;
    origin = savedOrigin;
    descended = tag.getBoolean("Descended");
    returning = tag.getBoolean("Returning");
    detouring = tag.getBoolean("Detouring");
    if (tag.contains("Pending")) {
      var next = BlockPos.of(tag.getLong("Pending"));
      if (next.distManhattan(cell) <= 3) pending = next;
    }
    long[] savedTrail = tag.getLongArray("Trail");
    if (savedTrail.length <= 256) {
      BlockPos previous = shaft;
      for (long value : savedTrail) {
        var next = BlockPos.of(value);
        if (slime.level().isOutsideBuildHeight(next) || next.distManhattan(previous) > 3) {
          trail.clear();
          break;
        }
        trail.addLast(next);
        previous = next;
      }
    }
  }
}
