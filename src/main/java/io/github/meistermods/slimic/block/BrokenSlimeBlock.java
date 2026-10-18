package io.github.meistermods.slimic.block;

import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.registry.ModBlocks;
import io.github.meistermods.slimic.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class BrokenSlimeBlock extends BaseEntityBlock {
  public BrokenSlimeBlock(Properties properties) {
    super(properties);
  }

  @Override
  public RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new BrokenSlimeBlockEntity(pos, state);
  }

  public static void preserve(FriendlySlime slime) {
    Level level = slime.level();
    ItemStack stored = FriendlySlimeItem.fromSlime(slime);
    BlockPos pos =
        new BlockPos(
            slime.getBlockX(),
            net.minecraft.util.Mth.clamp(
                slime.getBlockY(), level.getMinBuildHeight(), level.getMaxBuildHeight() - 1),
            slime.getBlockZ());
    if (level.isInWorldBounds(pos)
        && level.getBlockState(pos).canBeReplaced()
        && level.setBlockAndUpdate(pos, ModBlocks.BROKEN_SLIME.get().defaultBlockState())
        && level.getBlockEntity(pos) instanceof BrokenSlimeBlockEntity broken) {
      broken.setSlime(stored);
    } else {
      // Never overwrite a solid block or container when a companion collapses inside it.
      var recovery = slime.spawnAtLocation(recoveryItem(stored));
      if (recovery != null && recovery.getY() < level.getMinBuildHeight())
        recovery.setPos(recovery.getX(), level.getMinBuildHeight() + .5, recovery.getZ());
    }
    io.github.meistermods.slimic.entity.CompanionRegistry.get(level.getServer())
        .mark(slime, io.github.meistermods.slimic.entity.CompanionRegistry.Location.BROKEN);
    slime.ejectPassengers();
    slime.dropLeash(true, true);
    slime.playSound(SoundEvents.SLIME_DEATH, .6F, 1);
    slime.discard();
  }

  private static ItemStack recoveryItem(ItemStack stored) {
    ItemStack recovery = new ItemStack(ModItems.BROKEN_SLIME.get());
    CompoundTag data = new CompoundTag();
    data.put("Slime", stored.save(new CompoundTag()));
    BlockItem.setBlockEntityData(recovery, ModBlocks.BROKEN_SLIME_ENTITY.get(), data);
    return recovery;
  }

  @Override
  public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
    if (!level.isClientSide
        && player.getAbilities().instabuild
        && level.getBlockEntity(pos) instanceof BrokenSlimeBlockEntity broken
        && !broken.getSlime().isEmpty()) popResource(level, pos, recoveryItem(broken.getSlime()));
    super.playerWillDestroy(level, pos, state, player);
  }

  @Override
  public InteractionResult use(
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    if (!player.getItemInHand(hand).is(Items.SLIME_BALL)) return InteractionResult.PASS;
    if (player.isSpectator()
        || !level.mayInteract(player, pos)
        || !player.mayUseItemAt(pos, hit.getDirection(), player.getItemInHand(hand)))
      return InteractionResult.FAIL;
    if (level.isClientSide) return InteractionResult.SUCCESS;
    if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
    if (!revive(server, pos, player, hit.getDirection(), player.getItemInHand(hand)))
      return InteractionResult.FAIL;
    if (!player.getAbilities().instabuild) player.getItemInHand(hand).shrink(1);
    return InteractionResult.CONSUME;
  }

  /** Restores the stored companion transactionally; the caller supplies and consumes the ball. */
  public static boolean revive(ServerLevel level, BlockPos pos, Player player) {
    return revive(level, pos, player, Direction.UP, player.getMainHandItem());
  }

  public static boolean revive(
      ServerLevel level, BlockPos pos, Player player, Direction face, ItemStack ball) {
    if (player.isSpectator()
        || !level.hasChunkAt(pos)
        || !level.mayInteract(player, pos)
        || !ball.is(Items.SLIME_BALL)
        || !player.mayUseItemAt(pos, face, ball)
        || !(level.getBlockEntity(pos) instanceof BrokenSlimeBlockEntity broken)) return false;
    ItemStack stored = broken.getSlime();
    if (!(stored.getItem() instanceof FriendlySlimeItem item)
        || !stored.hasTag()
        || !stored.getTag().getCompound("SlimeData").hasUUID("Owner")
        || !io.github.meistermods.slimic.entity.CompanionOwnership.owns(
            level, stored.getTag().getCompound("SlimeData").getUUID("Owner"), player.getUUID()))
      return false;
    BlockState state = level.getBlockState(pos);
    CompoundTag backup = broken.saveWithFullMetadata();
    if (!level.removeBlock(pos, false)) return false;
    if (!item.revive(level, player, stored, pos)) {
      level.setBlockAndUpdate(pos, state);
      if (level.getBlockEntity(pos) instanceof BrokenSlimeBlockEntity restored)
        restored.load(backup);
      return false;
    }
    level.playSound(null, pos, SoundEvents.SLIME_SQUISH, SoundSource.BLOCKS, 1, 1);
    return true;
  }
}
