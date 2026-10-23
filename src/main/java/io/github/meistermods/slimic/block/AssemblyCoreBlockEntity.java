package io.github.meistermods.slimic.block;

import io.github.meistermods.slimic.config.SlimicConfig;
import io.github.meistermods.slimic.registry.*;
import io.github.meistermods.slimic.world.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A replaceable doorway to a durable encounter and personal reward mailbox. */
public final class AssemblyCoreBlockEntity extends BlockEntity {
  private BlockPos anchor;

  public AssemblyCoreBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlocks.ASSEMBLY_CORE_ENTITY.get(), pos, state);
  }

  public void bind(BlockPos anchor) {
    this.anchor = anchor.immutable();
    setChanged();
  }

  @Override
  public void onLoad() {
    super.onLoad();
    if (level instanceof ServerLevel server) site(server);
  }

  public AssemblySites.Site site(ServerLevel level) {
    if (!level.dimension().equals(SlimicWorld.DIMENSION)) return null;
    // Moving a copied altar never creates a second interface to another arena.
    if (anchor == null) {
      for (var known : AssemblySites.get(level).sites())
        if (worldPosition.equals(known.anchor.offset(0, 0, -15))) {
          bind(known.anchor);
          break;
        }
    }
    if (anchor == null || !worldPosition.equals(anchor.offset(0, 0, -15))) return null;
    return AssemblySites.get(level).register(level, anchor);
  }

  private static void message(ServerPlayer player, String key, Object... args) {
    player.displayClientMessage(
        Component.translatable("message.slimic.assembly_" + key, args), false);
  }

  public void use(ServerPlayer player) {
    if (!(level instanceof ServerLevel server)
        || player.distanceToSqr(worldPosition.getCenter()) > 64) return;
    var data = AssemblySites.get(server);
    var claim = data.claim(player);
    if (claim != AssemblySites.Claim.NONE) {
      message(player, "claim_" + claim.name().toLowerCase(Locale.ROOT));
      return;
    }
    var site = site(server);
    if (site == null) {
      message(player, "unbound");
      return;
    }
    if (site.state == AssemblySites.State.ACTIVE || site.state == AssemblySites.State.AWAKENING) {
      message(player, "active");
      return;
    }
    boolean peaceful = server.getDifficulty() == Difficulty.PEACEFUL;
    if (peaceful && site.firstReward.contains(player.getUUID())) {
      message(player, "claimed");
      return;
    }
    long cooldown = site.lastClear + SlimicConfig.ASSEMBLY_COOLDOWN.get() - server.getGameTime();
    if (!peaceful && site.cleared && !site.retryFree && cooldown > 0) {
      message(player, "cooldown", (cooldown + 19) / 20);
      return;
    }
    if (site.paymentId != null
        && (!player.getUUID().equals(site.paymentOwner) || peaceful != site.paymentPeaceful)) {
      message(player, "payment_pending");
      return;
    }
    int balls =
        peaceful
            ? SlimicConfig.ASSEMBLY_PEACEFUL_SLIMEBALLS.get()
            : site.retryFree ? 0 : SlimicConfig.ASSEMBLY_REPEAT_SLIMEBALLS.get();
    int fragments =
        peaceful
            ? SlimicConfig.ASSEMBLY_PEACEFUL_FRAGMENTS.get()
            : site.retryFree ? 0 : SlimicConfig.ASSEMBLY_REPEAT_FRAGMENTS.get();
    if ((balls > 0 || fragments > 0) && !pay(player, site, balls, fragments, peaceful)) return;
    boolean success =
        peaceful ? data.peacefulReward(server, site, player.getUUID()) : data.awaken(server, site);
    if (!success) {
      message(player, "claim_storage_error");
      return;
    }
    if (peaceful) {
      message(player, "claim_" + data.claim(player).name().toLowerCase(Locale.ROOT));
    } else message(player, "awakening");
  }

  private boolean pay(
      ServerPlayer player, AssemblySites.Site site, int balls, int fragments, boolean peaceful) {
    var data = AssemblySites.get(player.serverLevel());
    if (site.paymentId != null
        && player.getPersistentData().getBoolean("AssemblyPaid_" + site.paymentId)) return true;
    int availableBalls = 0, availableFragments = 0;
    for (ItemStack stack : player.getInventory().items) {
      if (stack.is(Items.SLIME_BALL)) availableBalls += stack.getCount();
      if (stack.is(ModItems.OLD_COMMAND_FRAGMENT.get())) availableFragments += stack.getCount();
    }
    if (availableBalls < balls || availableFragments < fragments) {
      message(player, "cost", balls, fragments);
      return false;
    }
    if (site.paymentId == null) {
      site.paymentOwner = player.getUUID();
      site.paymentId = UUID.randomUUID();
      site.paymentPeaceful = peaceful;
      if (!data.flush(player.serverLevel())) {
        site.paymentOwner = site.paymentId = null;
        message(player, "claim_storage_error");
        return false;
      }
    }
    String receipt = "AssemblyPaid_" + site.paymentId;
    var before = player.getInventory().items.stream().map(ItemStack::copy).toList();
    int removeBalls = balls, removeFragments = fragments;
    for (ItemStack stack : player.getInventory().items) {
      int remove =
          stack.is(Items.SLIME_BALL)
              ? Math.min(removeBalls, stack.getCount())
              : stack.is(ModItems.OLD_COMMAND_FRAGMENT.get())
                  ? Math.min(removeFragments, stack.getCount())
                  : 0;
      if (stack.is(Items.SLIME_BALL)) removeBalls -= remove;
      else if (stack.is(ModItems.OLD_COMMAND_FRAGMENT.get())) removeFragments -= remove;
      stack.shrink(remove);
    }
    player.getPersistentData().putBoolean(receipt, true);
    if (!DurableWorldData.savePlayer(player)) {
      for (int i = 0; i < before.size(); i++) player.getInventory().items.set(i, before.get(i));
      player.getPersistentData().remove(receipt);
      player.getInventory().setChanged();
      message(player, "claim_storage_error");
      return false;
    }
    player.getInventory().setChanged();
    player.inventoryMenu.broadcastChanges();
    return true;
  }

  public static void tick(
      Level world, BlockPos pos, BlockState state, AssemblyCoreBlockEntity core) {
    if (!(world instanceof ServerLevel level)) return;
    var site = core.site(level);
    tickEncounter(level, site);
  }

  public static void tickEncounter(ServerLevel level, AssemblySites.Site site) {
    if (site == null || site.state != AssemblySites.State.AWAKENING) return;
    BlockPos pos = site.anchor.offset(0, 0, -15);
    if (level.getDifficulty() == Difficulty.PEACEFUL) {
      AssemblySites.get(level).reset(level, site);
      return;
    }
    if (site.awakening > 0) {
      site.awakening--;
      if (site.awakening % 5 == 0)
        level.sendParticles(
            ParticleTypes.END_ROD,
            pos.getX() + .5,
            pos.getY() + 1.3,
            pos.getZ() + .5,
            3,
            .7,
            .5,
            .7,
            .02);
      return;
    }
    var boss = ModEntities.AIMLESS_COLONY.get().create(level);
    if (boss == null) return;
    boss.setUUID(site.boss);
    boss.bind(site);
    boss.moveTo(site.anchor.getX() + .5, site.anchor.getY(), site.anchor.getZ() + .5, 0, 0);
    if (!level.hasChunkAt(site.anchor) || !level.noCollision(boss)) return;
    net.minecraftforge.event.ForgeEventFactory.onFinalizeSpawn(
        boss,
        level,
        level.getCurrentDifficultyAt(site.anchor),
        net.minecraft.world.entity.MobSpawnType.STRUCTURE,
        null,
        null);
    if (boss.isSpawnCancelled()
        || !boss.isAlive()
        || boss.isRemoved()
        || boss.level() != level
        || !site.boss.equals(boss.getUUID())
        || !site.id.equals(boss.siteId())
        || !site.encounter.equals(boss.encounterId())
        || !boss.blockPosition().equals(site.anchor)
        || !level.noCollision(boss)) {
      boss.discard();
      site.awakening = 20;
      return;
    }
    site.state = AssemblySites.State.ACTIVE;
    site.body = boss.saveWithoutId(new CompoundTag());
    if (!AssemblySites.get(level).flush(level)) {
      site.state = AssemblySites.State.AWAKENING;
      return;
    }
    boolean admitted =
        level.addFreshEntity(boss)
            && boss.isAddedToWorld()
            && boss.isAlive()
            && !boss.isRemoved()
            && boss.level() == level
            && site.boss.equals(boss.getUUID())
            && site.id.equals(boss.siteId())
            && site.encounter.equals(boss.encounterId())
            && boss.blockPosition().equals(site.anchor);
    if (!admitted) {
      if (boss.isAddedToWorld() && boss.isRemoved()) boss.revive();
      boss.discard();
      site.state = AssemblySites.State.AWAKENING;
      site.awakening = 20;
      AssemblySites.get(level).flush(level);
      return;
    }
    for (ServerPlayer player : level.players())
      if (AssemblySites.eligiblePosition(player, site)) message(player, "intro");
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    if (anchor != null) tag.put("AssemblyAnchor", NbtUtils.writeBlockPos(anchor));
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    anchor =
        tag.contains("AssemblyAnchor", Tag.TAG_COMPOUND)
            ? NbtUtils.readBlockPos(tag.getCompound("AssemblyAnchor"))
            : null;
  }
}
