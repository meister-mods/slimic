package io.github.meistermods.slimic.entity;

import io.github.meistermods.slimic.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.ToolActions;

public final class FarmerSlime extends FieldSlime {
  private final Set<BlockPos> plots = new LinkedHashSet<>();
  private BlockSurvey survey;
  private int plotCursor;
  private final Map<BlockPos, Block> crops = new HashMap<>();

  public FarmerSlime(EntityType<? extends FarmerSlime> type, Level level) {
    super(type, level);
  }

  public static boolean isHoe(ItemStack stack) {
    return !stack.isEmpty() && stack.canPerformAction(ToolActions.HOE_TILL);
  }

  @Override
  public boolean matchesTool(ItemStack stack) {
    return isHoe(stack);
  }

  @Override
  protected WorkStatus.Order workOrder() {
    return WorkStatus.Order.FARM;
  }

  @Override
  protected String workTag() {
    return "FarmingWork";
  }

  @Override
  protected String bagTagName() {
    return "FarmerBag";
  }

  @Override
  public ItemStack getPickResult() {
    return new ItemStack(ModItems.FARMER_SLIME.get());
  }

  public Set<BlockPos> getFarmPlots() {
    return Set.copyOf(plots);
  }

  @Override
  public void setWorking(boolean value) {
    boolean starting = value && !isWorking();
    super.setWorking(value);
    if (starting && level() instanceof ServerLevel level) {
      home = blockPosition();
      plots.clear();
      crops.clear();
      plotCursor = 0;
      survey =
          new BlockSurvey(
              level, home, SlimeStat.DETECTION_RANGE.value(this), s -> s.is(Blocks.FARMLAND));
      status = Status.SURVEYING;
    }
  }

  @Override
  public void onReleased() {
    super.onReleased();
    if (originChanged) {
      setWorking(false);
      setWaiting(true);
      home = blockPosition();
      plots.clear();
      crops.clear();
      survey = null;
    }
  }

  private boolean soil(BlockState state) {
    return state.getBlock() instanceof io.github.meistermods.slimic.block.SlimicSoilBlock
        || state.is(Blocks.DIRT)
        || state.is(Blocks.GRASS_BLOCK)
        || state.is(Blocks.DIRT_PATH)
        || state.is(Blocks.COARSE_DIRT)
        || state.is(Blocks.ROOTED_DIRT);
  }

  private BlockState seedState(ServerLevel level, BlockPos pos, ItemStack stack) {
    if (stack.getItem() instanceof BlockItem block && block.getBlock() instanceof CropBlock) {
      var state = block.getBlock().defaultBlockState();
      if (state.canSurvive(level, pos)) return state;
    }
    if (stack.getItem() instanceof IPlantable plant) {
      var state = plant.getPlant(level, pos);
      if (state.getBlock() instanceof CropBlock && state.canSurvive(level, pos)) return state;
    }
    return null;
  }

  @Override
  protected void workTick(ServerLevel level) {
    if (survey != null) {
      plots.addAll(survey.next(512));
      if (!survey.isDone()) {
        status = Status.SURVEYING;
        return;
      }
      survey = null;
    }
    if (plots.isEmpty()) {
      status = Status.IDLE;
      getNavigation().stop();
      return;
    }
    var list = new ArrayList<>(plots);
    if (target != null) {
      BlockPos active = plots.contains(target) ? target : target.below();
      if (list.remove(active)) {
        list.add(0, active);
        plotCursor = 0;
      }
    }
    boolean missingSeeds = false;
    for (int n = 0; n < Math.min(32, list.size()); n++) {
      BlockPos plot = list.get(Math.floorMod(plotCursor++, list.size()));
      if (!level.hasChunkAt(plot) || !inArea(plot)) continue;
      BlockState soil = level.getBlockState(plot);
      if (!soil.is(Blocks.FARMLAND) && !soil(soil)) {
        plots.remove(plot);
        crops.remove(plot);
        continue;
      }
      BlockPos above = plot.above();
      BlockState crop = level.getBlockState(above);
      if (crop.getBlock() instanceof CropBlock) crops.put(plot, crop.getBlock());
      if (!soil.is(Blocks.FARMLAND)) {
        if (!crop.isAir()) continue;
        if (!approach(plot)) return;
        var player = worker(level);
        var previous = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, getTool());
        try {
          var context =
              new UseOnContext(
                  player,
                  InteractionHand.MAIN_HAND,
                  new BlockHitResult(Vec3.atCenterOf(plot), Direction.UP, plot, false));
          BlockState tilled = soil.getToolModifiedState(context, ToolActions.HOE_TILL, true);
          if (tilled == null || (!tilled.is(Blocks.FARMLAND) && !tilled.is(Blocks.DIRT))) {
            blocked();
            return;
          }
          if (level.getBlockState(plot) != soil || !place(level, plot, tilled)) return;
          getTool().hurtAndBreak(1, this, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
          completed();
          return;
        } finally {
          player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
      }
      if (crop.getBlock() instanceof CropBlock plant && plant.isMaxAge(crop)) {
        if (!approach(above)) return;
        harvest(level, above);
        return;
      }
      if (crop.isAir()) {
        int seed = -1;
        BlockState state = null;
        for (int i : getBag().occupiedSlots()) {
          state = seedState(level, above, getBag().getStackInSlot(i));
          if (state != null && (!crops.containsKey(plot) || state.is(crops.get(plot)))) {
            seed = i;
            break;
          }
        }
        if (seed < 0) {
          missingSeeds = true;
          continue;
        }
        if (!approach(above)) return;
        if (place(level, above, state)) {
          getBag().extractItem(seed, 1, false);
          completed();
        }
        return;
      }
    }
    status = missingSeeds ? Status.NO_SEEDS : Status.IDLE;
    getNavigation().stop();
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    if (survey != null) {
      while (!survey.isDone()) plots.addAll(survey.next(4096));
      survey = null;
    }
    super.addAdditionalSaveData(tag);
    tag.putLongArray("FarmPlots", plots.stream().mapToLong(BlockPos::asLong).toArray());
    var saved = new ListTag();
    for (var entry : crops.entrySet()) {
      var c = new CompoundTag();
      c.putLong("Pos", entry.getKey().asLong());
      c.putString("Crop", BuiltInRegistries.BLOCK.getKey(entry.getValue()).toString());
      saved.add(c);
    }
    tag.put("FarmCrops", saved);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    plots.clear();
    for (long pos : tag.getLongArray("FarmPlots")) plots.add(BlockPos.of(pos));
    survey = null;
    crops.clear();
    for (var saved : tag.getList("FarmCrops", Tag.TAG_COMPOUND)) {
      var c = (CompoundTag) saved;
      var key = ResourceLocation.tryParse(c.getString("Crop"));
      if (key != null) {
        var b = BuiltInRegistries.BLOCK.get(key);
        if (b instanceof CropBlock && plots.contains(BlockPos.of(c.getLong("Pos"))))
          crops.put(BlockPos.of(c.getLong("Pos")), b);
      }
    }
  }
}
