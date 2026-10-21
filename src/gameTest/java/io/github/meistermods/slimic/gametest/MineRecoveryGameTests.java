package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.block.*;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.*;
import io.github.meistermods.slimic.registry.*;
import io.github.meistermods.slimic.world.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MineRecoveryGameTests {
  private static final List<AncientSlime.Role> ROLES =
      List.of(AncientSlime.Role.MINER, AncientSlime.Role.TUNNEL, AncientSlime.Role.MINE_GUARD);

  private record Site(GameTestHelper h, LoadedWorkArea area, Player owner, BlockPos station)
      implements AutoCloseable {
    public void close() {
      area.close();
    }
  }

  private static Site site(GameTestHelper h) {
    var area = new LoadedWorkArea(h);
    area.sealSurroundingFluids();
    var owner = h.makeMockSurvivalPlayer();
    owner.moveTo(area.point(8, 101, 8));
    var station = BlockPos.containing(area.point(3, 101, 3));
    h.getLevel()
        .setBlockAndUpdate(station, ModWorldBlocks.block("command_pedestal").defaultBlockState());
    return new Site(h, area, owner, station);
  }

  private static AncientSlime enemy(ServerLevel level, AncientSlime.Role role) {
    return switch (role) {
      case MINER -> ModEntities.FORGOTTEN_MINER_SLIME.get().create(level);
      case TUNNEL -> ModEntities.FORGOTTEN_TUNNEL_SLIME.get().create(level);
      case MINE_GUARD -> ModEntities.MINE_GUARDIAN_SLIME.get().create(level);
      default -> throw new IllegalArgumentException();
    };
  }

  private static ItemStack core(AncientSlime.Role role) {
    return AncientSlimeCoreItem.fromRemains(role, UUID.randomUUID(), 2);
  }

  private static UseOnContext context(Site s, InteractionHand hand) {
    return new UseOnContext(
        s.owner,
        hand,
        new BlockHitResult(Vec3.atCenterOf(s.station), Direction.UP, s.station, false));
  }

  private static void materials(Site s, int amount) {
    s.owner.getInventory().setItem(9, new ItemStack(ModItems.OLD_COMMAND_FRAGMENT.get(), amount));
    s.owner.getInventory().setItem(10, new ItemStack(Items.SLIME_BALL, amount));
  }

  @GameTest(template = "empty", batch = "mine_recovery", timeoutTicks = 400)
  public static void ancientQuarryClearsVisibleRockBeforeAnOccludedOre(GameTestHelper h) {
    var s = site(h);
    var miner = ModEntities.ANCIENT_MINING_SLIME.get().create(h.getLevel());
    miner.tame(s.owner);
    miner.moveTo(s.area.point(5, 101, 5));
    miner.setTool(0, new ItemStack(Items.IRON_PICKAXE));
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++)
        h.getLevel()
            .setBlockAndUpdate(
                BlockPos.containing(s.area.point(x, 100, z)), Blocks.BEDROCK.defaultBlockState());
    var rock = BlockPos.containing(s.area.point(5, 101, 6));
    var ore = rock.south();
    h.getLevel().setBlockAndUpdate(rock, Blocks.STONE.defaultBlockState());
    h.getLevel().setBlockAndUpdate(ore, Blocks.IRON_ORE.defaultBlockState());
    miner.onReleased();
    miner.setWorking(true);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(h.getLevel().isPositionEntityTicking(miner.blockPosition()), "Ready"))
        .thenExecute(() -> h.getLevel().addFreshEntity(miner))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    h.getLevel().getBlockState(rock).isAir(),
                    "Visible obstruction is cleared without waiting for an unreachable ore"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockState(ore).is(Blocks.IRON_ORE),
                  "Visible rock precedes the hidden ore");
              miner.setWaiting(true);
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "mine_recovery")
  public static void everyRestoredRoleHasUsefulRandomTrainingWithoutRerolls(GameTestHelper h) {
    try (var s = site(h)) {
      materials(s, 12);
      s.owner.getInventory().setItem(11, new ItemStack(ModItems.ANCIENT_COMMAND_SEAL.get(), 12));
      var allocations = new java.util.HashSet<String>();
      for (var role : AncientSlime.Role.values()) {
        for (int attempt = 0; attempt < 2; attempt++) {
          var source =
              AncientSlimeCoreItem.fromRemains(
                  role, new UUID(role.ordinal() + 1, 4381 + attempt), 2);
          s.owner.setItemInHand(InteractionHand.MAIN_HAND, source);
          h.assertTrue(
              source.useOn(context(s, InteractionHand.MAIN_HAND)).consumesAction(),
              "Restore trained role " + role);
          var item = s.owner.getMainHandItem();
          var data = item.getTag().getCompound("SlimeData");
          var type =
              net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(
                  net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem()));
          var pet = (FriendlySlime) type.create(h.getLevel());
          pet.load(data);
          int total = 0;
          for (var stat : SlimeStat.values()) {
            int level = pet.getStatLevel(stat);
            total += level;
            h.assertTrue(
                level >= 0 && level <= stat.maxLevel() && (stat.isUsedBy(pet) || level == 0),
                "Only useful legal upgrades");
          }
          h.assertTrue(
              total >= 20
                  && total <= 24
                  && pet.getStatPoints() == 0
                  && data.getInt("EarnedPoints") == total,
              "Invested veteran points and future growth cost");
          h.assertTrue(
              pet.getHealth() == 1 && pet.isOrderedToSit(), "Training does not heal or begin work");
          allocations.add(data.getCompound("SlimeStats").toString());
          var before = pet.saveWithoutId(new CompoundTag());
          AncientTraining.applyOnce(pet, UUID.randomUUID(), 3);
          h.assertTrue(
              before.equals(pet.saveWithoutId(new CompoundTag())),
              "Repeated restoration callbacks cannot stack training");
          for (int i = 0; i < 4; i++) pet.load(pet.saveWithoutId(new CompoundTag()));
          h.assertTrue(
              before.equals(pet.saveWithoutId(new CompoundTag())),
              "Storage and reload preserve the roll");
        }
      }
      h.assertTrue(allocations.size() >= 6, "Distinct cores produce varied trained companions");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "mine_recovery")
  public static void allMineRolesRestoreAtOneHealthWithNewOwnerAndIdentity(GameTestHelper h) {
    try (var s = site(h)) {
      materials(s, 3);
      for (var role : ROLES) {
        var source = core(role);
        var identity = source.getTag().getUUID("AncientIdentity");
        s.owner.setItemInHand(InteractionHand.MAIN_HAND, source);
        h.assertTrue(
            source.useOn(context(s, InteractionHand.MAIN_HAND)).consumesAction(),
            "Restore " + role);
        var item = s.owner.getMainHandItem();
        var expected =
            role == AncientSlime.Role.MINER
                ? ModItems.ANCIENT_MINING_SLIME.get()
                : role == AncientSlime.Role.TUNNEL
                    ? ModItems.ANCIENT_TUNNEL_SLIME.get()
                    : ModItems.ROYAL_GUARD_SLIME.get();
        var data = item.getTag().getCompound("SlimeData");
        h.assertTrue(
            item.is(expected)
                && data.getUUID("Owner").equals(s.owner.getUUID())
                && !data.getUUID("UUID").equals(identity),
            "Correct new companion and ownership");
        h.assertTrue(
            data.getBoolean("Sitting")
                && data.getFloat("Health") == 1
                && data.getBoolean("AncientOrigin")
                && data.getCompound("SlimeStats").getInt("stamina") >= 2,
            "Wait, one health, veteran training");
        h.assertTrue(ForgottenOrders.get(h.getLevel()).isRedeemed(identity), "Redeemed once");
      }
      h.assertTrue(
          s.owner.getInventory().getItem(9).isEmpty()
              && s.owner.getInventory().getItem(10).isEmpty(),
          "Exactly three fragments and slimeballs consumed");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "mine_recovery")
  public static void miningCoreNeedsFragmentInsteadOfSealAndRejectsMissingMaterials(
      GameTestHelper h) {
    try (var s = site(h)) {
      for (var role : ROLES)
        for (int missing = 0; missing < 2; missing++) {
          materials(s, 1);
          s.owner.getInventory().setItem(9 + missing, ItemStack.EMPTY);
          s.owner.getInventory().setItem(11, new ItemStack(ModItems.ANCIENT_COMMAND_SEAL.get(), 8));
          var source = core(role);
          var before = source.copy();
          s.owner.setItemInHand(InteractionHand.MAIN_HAND, source);
          h.assertTrue(
              source.useOn(context(s, InteractionHand.MAIN_HAND)) == InteractionResult.FAIL
                  && ItemStack.matches(before, s.owner.getMainHandItem()),
              "Missing material preserves core " + role);
          h.assertTrue(
              !ForgottenOrders.get(h.getLevel())
                  .isRedeemed(source.getTag().getUUID("AncientIdentity")),
              "Failure never redeems");
        }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "mine_recovery")
  public static void mineCoresRequireOverworldAndKeepAllMaterialsElsewhere(GameTestHelper h) {
    var level = h.getLevel().getServer().getLevel(SlimicWorld.DIMENSION);
    var pos = new BlockPos(2456000, 300, 2456000);
    level.getChunkAt(pos);
    var old = level.getBlockState(pos);
    level.setBlockAndUpdate(pos, ModWorldBlocks.block("command_pedestal").defaultBlockState());
    var player =
        net.minecraftforge.common.util.FakePlayerFactory.get(
            level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "MineRestorer"));
    try {
      for (var role : ROLES) {
        var source = core(role);
        player.setItemInHand(InteractionHand.MAIN_HAND, source);
        player.getInventory().setItem(9, new ItemStack(ModItems.OLD_COMMAND_FRAGMENT.get()));
        player.getInventory().setItem(10, new ItemStack(Items.SLIME_BALL));
        var before = source.copy();
        var ctx =
            new UseOnContext(
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        h.assertTrue(
            source.useOn(ctx) == InteractionResult.FAIL
                && ItemStack.matches(before, player.getMainHandItem())
                && player.getInventory().getItem(9).getCount() == 1
                && player.getInventory().getItem(10).getCount() == 1,
            "Other dimension cannot consume mining materials");
      }
    } finally {
      level.setBlockAndUpdate(pos, old);
      player.getInventory().clearContent();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "mine_recovery")
  public static void corpseCoreSurvivesSaveAndDropsOnceInsteadOfABlock(GameTestHelper h) {
    try (var s = site(h)) {
      for (var role : ROLES) {
        var pos = s.station.east(3);
        h.getLevel()
            .setBlockAndUpdate(
                pos,
                ModBlocks.BROKEN_SLIME
                    .get()
                    .defaultBlockState()
                    .setValue(BrokenSlimeBlock.MINERALIZED, true));
        var corpse = (BrokenSlimeBlockEntity) h.getLevel().getBlockEntity(pos);
        var original = core(role);
        corpse.setAncientCore(original);
        var saved = corpse.saveWithFullMetadata();
        corpse.load(saved);
        h.assertTrue(
            corpse.getSlime().isEmpty() && !saved.hasUUID("Owner") && !saved.contains("Slime"),
            "No owned-companion snapshot");
        var drops =
            Block.getDrops(
                h.getLevel().getBlockState(pos),
                h.getLevel(),
                pos,
                corpse,
                s.owner,
                new ItemStack(Items.DIAMOND_PICKAXE));
        h.assertTrue(
            drops.size() == 1 && ItemStack.matches(drops.get(0), original),
            "One exact persisted core, no block item");
        h.getLevel().destroyBlock(pos, true, s.owner);
        var actual = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1));
        h.assertTrue(
            actual.size() == 1 && ItemStack.matches(actual.get(0).getItem(), original),
            "Actual destruction yields one core");
        actual.forEach(Entity::discard);
        h.assertTrue(!h.getLevel().destroyBlock(pos, true, s.owner), "Cannot break a corpse twice");
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "mine_recovery")
  public static void corpseRejectsDirectSlimeballRevivalAndReturnsCopies(GameTestHelper h) {
    try (var s = site(h)) {
      h.getLevel().setBlockAndUpdate(s.station, ModBlocks.BROKEN_SLIME.get().defaultBlockState());
      var corpse = (BrokenSlimeBlockEntity) h.getLevel().getBlockEntity(s.station);
      var source = core(AncientSlime.Role.MINER);
      corpse.setAncientCore(source);
      s.owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SLIME_BALL, 3));
      h.assertTrue(
          !BrokenSlimeBlock.revive(h.getLevel(), s.station, s.owner)
              && s.owner.getMainHandItem().getCount() == 3,
          "Cannot revive corpse directly");
      source.shrink(1);
      var read = corpse.getAncientCore();
      read.getOrCreateTag().putString("AncientRole", "BAD");
      h.assertTrue(
          AncientSlimeCoreItem.role(corpse.getAncientCore()) == AncientSlime.Role.MINER,
          "Stored identity never aliases caller stacks");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "mine_recovery")
  public static void emptyLegacyRemainsAndOwnedRecoveryStaySeparate(GameTestHelper h) {
    try (var s = site(h)) {
      h.getLevel().setBlockAndUpdate(s.station, ModBlocks.BROKEN_SLIME.get().defaultBlockState());
      var corpse = (BrokenSlimeBlockEntity) h.getLevel().getBlockEntity(s.station);
      h.assertTrue(corpse.getAncientCore().isEmpty(), "Legacy empty remains do not invent cores");
      var pet = ModEntities.ANCIENT_MINING_SLIME.get().create(h.getLevel());
      pet.tame(s.owner);
      pet.setHealth(0);
      corpse.setAncientCore(core(AncientSlime.Role.MINER));
      corpse.setSlime(FriendlySlimeItem.fromSlime(pet));
      h.assertTrue(
          corpse.getAncientCore().isEmpty() && !corpse.getSlime().isEmpty(),
          "Owned payload takes exclusive precedence");
      corpse.load(corpse.saveWithFullMetadata());
      var drops =
          Block.getDrops(h.getLevel().getBlockState(s.station), h.getLevel(), s.station, corpse);
      h.assertTrue(
          drops.size() == 1 && drops.get(0).is(ModItems.BROKEN_SLIME.get()),
          "Owned companion remains a recovery block");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "mine_recovery")
  public static void copiedMiningCoreCannotRestoreTwiceAfterLedgerReload(GameTestHelper h) {
    try (var s = site(h)) {
      materials(s, 3);
      var source = core(AncientSlime.Role.TUNNEL);
      var copy = source.copy();
      var id = source.getTag().getUUID("AncientIdentity");
      s.owner.setItemInHand(InteractionHand.MAIN_HAND, source);
      h.assertTrue(
          source.useOn(context(s, InteractionHand.MAIN_HAND)).consumesAction(),
          "First restoration");
      var reloaded =
          ForgottenOrders.load(ForgottenOrders.get(h.getLevel()).save(new CompoundTag()));
      h.assertTrue(reloaded.isRedeemed(id), "Ledger survives persistence");
      s.owner.setItemInHand(InteractionHand.MAIN_HAND, copy);
      h.assertTrue(
          copy.useOn(context(s, InteractionHand.MAIN_HAND)) == InteractionResult.FAIL
              && s.owner.getInventory().getItem(9).getCount() == 2
              && s.owner.getInventory().getItem(10).getCount() == 2,
          "Duplicate preserves remaining ingredients");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "mine_recovery")
  public static void creativeMineTemplatesRestoreIndependentlyAndOffhandWorks(GameTestHelper h) {
    try (var s = site(h)) {
      materials(s, 6);
      for (var role : ROLES) {
        var template = AncientSlimeCoreItem.creativeVariant(role);
        for (int i = 0; i < 2; i++) {
          s.owner.setItemInHand(InteractionHand.OFF_HAND, template.copy());
          h.assertTrue(
              s.owner.getOffhandItem().useOn(context(s, InteractionHand.OFF_HAND)).consumesAction(),
              "Independent offhand template " + role);
        }
        h.assertTrue(!template.getTag().hasUUID("AncientIdentity"), "Template remains independent");
      }
      h.assertTrue(
          s.owner.getInventory().getItem(9).isEmpty()
              && s.owner.getInventory().getItem(10).isEmpty(),
          "All six restorations consume ingredients");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "mine_recovery")
  public static void mineDeathsYieldCoreAndFragmentWithNoOwnedRemains(GameTestHelper h) {
    try (var s = site(h)) {
      for (var role : ROLES) {
        var mob = enemy(h.getLevel(), role);
        mob.moveTo(s.area.point(5, 101, 5));
        mob.setNoAi(true);
        h.getLevel().addFreshEntity(mob);
        mob.hurt(h.getLevel().damageSources().playerAttack(s.owner), 1000);
      }
      var drops =
          h.getLevel()
              .getEntitiesOfClass(
                  ItemEntity.class, new AABB(s.area.point(0, 100, 0), s.area.point(16, 106, 16)));
      h.assertTrue(
          drops.stream()
                  .filter(e -> e.getItem().is(ModItems.DAMAGED_ANCIENT_SLIME_CORE.get()))
                  .count()
              == 3,
          "One core per enemy");
      h.assertTrue(
          drops.stream()
                      .filter(e -> e.getItem().is(ModItems.OLD_COMMAND_FRAGMENT.get()))
                      .mapToInt(e -> e.getItem().getCount())
                      .sum()
                  == 3
              && drops.stream()
                  .noneMatch(
                      e ->
                          e.getItem().is(ModItems.ANCIENT_COMMAND_SEAL.get())
                              || e.getItem().is(ModItems.BROKEN_SLIME.get())),
          "Fragments instead of seals; no owned corpse");
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "mine_recovery")
  public static void mineEnemiesRemainAlliedBoundedAndDoNotOwnCompanionData(GameTestHelper h) {
    try (var s = site(h)) {
      var ordinary = ModEntities.ANCIENT_GUARD_SLIME.get().create(h.getLevel());
      for (var role : ROLES) {
        var mob = enemy(h.getLevel(), role);
        mob.moveTo(s.area.point(5, 101, 5));
        mob.bindToRuin(mob.blockPosition(), UUID.randomUUID(), null, null, 2);
        h.assertTrue(
            mob.isAlliedTo(ordinary)
                && !mob.canChangeDimensions()
                && !mob.saveWithoutId(new CompoundTag()).hasUUID("Owner"),
            "Ancient faction contract");
        s.owner.moveTo(mob.getX() + 1, mob.getY(), mob.getZ());
        h.assertTrue(mob.mayAttack(s.owner), "Nearby intruder");
        s.owner.moveTo(mob.getX() + 25, mob.getY(), mob.getZ());
        h.assertTrue(!mob.mayAttack(s.owner), "Home boundary");
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "mine_recovery")
  public static void everyAncientWorkerAndStoredItemStartsWaitingWithoutFreeGear(GameTestHelper h) {
    try (var s = site(h)) {
      for (var type :
          List.of(ModEntities.ANCIENT_MINING_SLIME.get(), ModEntities.ANCIENT_TUNNEL_SLIME.get())) {
        var pet = (MiningSlime) type.create(h.getLevel());
        pet.tame(s.owner);
        h.assertTrue(
            pet.isOrderedToSit()
                && !pet.isWorking()
                && pet.hasAncientOrigin()
                && pet.getPickaxe().isEmpty()
                && pet.getShovel().isEmpty(),
            "Fresh ancient worker waits without tools");
        pet.setTool(0, new ItemStack(Items.DIAMOND_PICKAXE));
        pet.getPickaxe().setDamageValue(37);
        pet.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 7));
        pet.setHealth(3);
        pet.setWorking(true);
        var stored = FriendlySlimeItem.fromSlime(pet);
        var expected = stored.copy();
        s.owner.setItemInHand(InteractionHand.MAIN_HAND, stored);
        var pos = s.station.east(4);
        var ctx =
            new UseOnContext(
                s.owner,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        h.assertTrue(stored.useOn(ctx).consumesAction(), "Stored worker can be released");
        var restored = (MiningSlime) h.getLevel().getEntity(pet.getUUID());
        h.assertTrue(
            restored != null
                && restored.isOrderedToSit()
                && !restored.isWorking()
                && restored.getHealth() == 3
                && restored.getPickaxe().getDamageValue() == 37
                && restored.getBag().getStackInSlot(0).getCount() == 7,
            "Release preserves traits and selects Wait");
        h.assertTrue(
            expected.getTag().getCompound("SlimeData").getUUID("UUID").equals(restored.getUUID()),
            "Identity retained");
        restored.discard();
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "mine_recovery", timeoutTicks = 400)
  public static void ancientQuarryPrioritizesOreAndActuallySpendsHalfWorkStamina(GameTestHelper h) {
    var s = site(h);
    var miner = ModEntities.ANCIENT_MINING_SLIME.get().create(h.getLevel());
    miner.tame(s.owner);
    miner.moveTo(s.area.point(5, 101, 5));
    miner.setTool(0, new ItemStack(Items.IRON_PICKAXE));
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++)
        h.getLevel()
            .setBlockAndUpdate(
                BlockPos.containing(s.area.point(x, 100, z)), Blocks.BEDROCK.defaultBlockState());
    var stone = BlockPos.containing(s.area.point(5, 101, 6));
    var ore = BlockPos.containing(s.area.point(7, 101, 5));
    h.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
    h.getLevel().setBlockAndUpdate(ore, Blocks.IRON_ORE.defaultBlockState());
    h.assertTrue(
        h.getLevel()
            .clip(
                new net.minecraft.world.level.ClipContext(
                    miner.getEyePosition(),
                    Vec3.atCenterOf(ore),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    miner))
            .getBlockPos()
            .equals(ore),
        "Priority fixture exposes the ore above the surrounding floor");
    miner.onReleased();
    miner.setWorking(true);
    SlimeStat.STAMINA.tune(miner, 20);
    miner.setTunedVitals(16, 20, 0);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(h.getLevel().isPositionEntityTicking(miner.blockPosition()), "Ready"))
        .thenExecute(() -> h.getLevel().addFreshEntity(miner))
        .thenWaitUntil(() -> h.assertTrue(h.getLevel().getBlockState(ore).isAir(), "Ore mined"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockState(stone).is(Blocks.STONE),
                  "Ore precedes nearer ordinary rock");
              h.assertTrue(
                  miner.getStamina() >= 19.35F && miner.getStamina() <= 19.5F,
                  "A real work action costs half stamina; remaining="
                      + miner.getStamina()
                      + "; tick="
                      + miner.tickCount);
              miner.setWaiting(true);
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "mine_recovery", timeoutTicks = 250)
  public static void ancientWorkerWithoutToolsCannotBreakOreAndRequestsCarrierTool(
      GameTestHelper h) {
    var s = site(h);
    var miner = ModEntities.ANCIENT_MINING_SLIME.get().create(h.getLevel());
    miner.tame(s.owner);
    miner.moveTo(s.area.point(5, 101, 5));
    var ore = BlockPos.containing(s.area.point(6, 100, 5));
    h.getLevel().setBlockAndUpdate(ore, Blocks.DIAMOND_ORE.defaultBlockState());
    miner.onReleased();
    miner.setWorking(true);
    h.startSequence()
        .thenWaitUntil(
            () ->
                h.assertTrue(h.getLevel().isPositionEntityTicking(miner.blockPosition()), "Ready"))
        .thenExecute(() -> h.getLevel().addFreshEntity(miner))
        .thenIdle(60)
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockState(ore).is(Blocks.DIAMOND_ORE)
                      && miner.needsReplacementTool(),
                  "No unprovided tool or fast breaking");
              h.assertTrue(
                  miner.receiveWorkTool(new ItemStack(Items.IRON_PICKAXE)),
                  "Existing carrier interface supplies tool");
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "mine_recovery")
  public static void restorationStationUsesShapedCraftingAndHasNoStonecuttingRecipe(
      GameTestHelper h) {
    var grid =
        new net.minecraft.world.inventory.TransientCraftingContainer(
            new net.minecraft.world.inventory.AbstractContainerMenu(null, 0) {
              public boolean stillValid(Player p) {
                return true;
              }

              public ItemStack quickMoveStack(Player p, int slot) {
                return ItemStack.EMPTY;
              }
            },
            3,
            3);
    grid.setItem(1, new ItemStack(Items.SLIME_BALL));
    grid.setItem(3, new ItemStack(Items.REDSTONE));
    grid.setItem(4, new ItemStack(Items.GOLD_INGOT));
    grid.setItem(5, new ItemStack(Items.REDSTONE));
    for (int slot = 6; slot < 9; slot++) grid.setItem(slot, new ItemStack(Items.STONE_BRICKS));
    var recipe =
        h.getLevel()
            .getRecipeManager()
            .byKey(SlimicWorld.id("world/command_pedestal"))
            .orElseThrow();
    var shaped = (net.minecraft.world.item.crafting.CraftingRecipe) recipe;
    h.assertTrue(
        shaped.matches(grid, h.getLevel())
            && shaped
                .assemble(grid, h.getLevel().registryAccess())
                .is(ModWorldBlocks.block("command_pedestal").asItem()),
        "The complete shaped recipe makes one restoration station");
    grid.setItem(0, grid.removeItemNoUpdate(1));
    h.assertTrue(!shaped.matches(grid, h.getLevel()), "A shapeless rearrangement is rejected");
    h.assertTrue(
        h
            .getLevel()
            .getRecipeManager()
            .getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.STONECUTTING)
            .stream()
            .noneMatch(
                r ->
                    r.getResultItem(h.getLevel().registryAccess())
                        .is(ModWorldBlocks.block("command_pedestal").asItem())),
        "No station stonecutting path remains");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "mine_recovery")
  public static void ancientTunnelMasonryTagDoesNotBoostOtherMaterials(GameTestHelper h) {
    var tunnel = ModEntities.ANCIENT_TUNNEL_SLIME.get().create(h.getLevel());
    h.assertTrue(
        tunnel.blockWorkMultiplier(ModWorldBlocks.block("slimic_stone").defaultBlockState()) == 1.5
            && tunnel.blockWorkMultiplier(Blocks.OBSIDIAN.defaultBlockState()) == 1
            && tunnel.blockWorkMultiplier(Blocks.DIRT.defaultBlockState()) == 1,
        "Bounded masonry bonus, no blanket speed increase");
    var ordinary = ModEntities.TUNNEL_SLIME.get().create(h.getLevel());
    h.assertTrue(
        ordinary.blockWorkMultiplier(ModWorldBlocks.block("slimic_stone").defaultBlockState()) == 1,
        "Ordinary tunnel worker retains speed");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "mine_recovery", timeoutTicks = 250)
  public static void forgottenWorkerClearsVisibleFaceDespiteOccludedFirstCell(GameTestHelper h) {
    var s = site(h);
    var mob = enemy(h.getLevel(), AncientSlime.Role.TUNNEL);
    mob.moveTo(s.area.point(5.5, 101, 5.5));
    mob.bindToRuin(mob.blockPosition(), UUID.randomUUID(), null, null, 2);
    var face = BlockPos.containing(s.area.point(5, 101, 7));
    for (int x = -1; x <= 1; x++)
      for (int y = 0; y < 3; y++)
        h.getLevel().setBlockAndUpdate(face.east(x).above(y), Blocks.STONE.defaultBlockState());
    var obstruction = face.east().north();
    h.getLevel().setBlockAndUpdate(obstruction, Blocks.BEDROCK.defaultBlockState());
    mob.bindMineFace(face, Direction.SOUTH);
    s.owner.getAbilities().instabuild = true;
    h.startSequence()
        .thenWaitUntil(
            () -> h.assertTrue(h.getLevel().isPositionEntityTicking(mob.blockPosition()), "Ready"))
        .thenExecute(() -> h.getLevel().addFreshEntity(mob))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    mob.saveWithoutId(new CompoundTag()).getInt("AncientMineCompleted") > 0,
                    "Visible cells progress while the first scanned cell is hidden by rubble"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockState(obstruction).is(Blocks.BEDROCK),
                  "Old work never destroys the obstruction outside its saved face");
              mob.discard();
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "mine_recovery", timeoutTicks = 350)
  public static void forgottenWorkerMinesOnlyItsSavedFaceAndStopsAfterNine(GameTestHelper h) {
    var s = site(h);
    var mob = enemy(h.getLevel(), AncientSlime.Role.TUNNEL);
    mob.moveTo(s.area.point(5, 101, 5));
    mob.bindToRuin(mob.blockPosition(), UUID.randomUUID(), null, null, 2);
    var face = BlockPos.containing(s.area.point(5, 101, 7));
    for (int x = -1; x <= 1; x++)
      for (int y = 0; y < 3; y++)
        h.getLevel().setBlockAndUpdate(face.east(x).above(y), Blocks.STONE.defaultBlockState());
    var outside = face.above(3);
    h.getLevel().setBlockAndUpdate(outside, Blocks.DIAMOND_BLOCK.defaultBlockState());
    mob.bindMineFace(face, Direction.SOUTH);
    // Creative owner remains visible but cannot interrupt the old work order as an intruder.
    s.owner.getAbilities().instabuild = true;
    h.startSequence()
        .thenWaitUntil(
            () -> h.assertTrue(h.getLevel().isPositionEntityTicking(mob.blockPosition()), "Ready"))
        .thenExecute(() -> h.getLevel().addFreshEntity(mob))
        .thenWaitUntil(
            () ->
                h.assertTrue(
                    mob.saveWithoutId(new CompoundTag()).getInt("AncientMineCompleted") >= 1,
                    "Native work progresses"))
        .thenExecute(
            () -> {
              var saved = mob.saveWithoutId(new CompoundTag());
              h.assertTrue(
                  saved.contains("AncientMineFace")
                      && h.getLevel().getBlockState(outside).is(Blocks.DIAMOND_BLOCK),
                  "Fixed face excludes buildings");
              saved.putInt("AncientMineCompleted", 9);
              mob.load(saved);
            })
        .thenIdle(80)
        .thenExecute(
            () -> {
              h.assertTrue(
                  mob.saveWithoutId(new CompoundTag()).getInt("AncientMineCompleted") == 9,
                  "Saved work cap is retained");
              s.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "mine_recovery", timeoutTicks = 250)
  public static void forgottenWorkerHonorsCanceledBreakWithoutChangingTheFace(GameTestHelper h) {
    var s = site(h);
    var mob = enemy(h.getLevel(), AncientSlime.Role.MINER);
    mob.moveTo(s.area.point(5, 101, 5));
    mob.bindToRuin(mob.blockPosition(), UUID.randomUUID(), null, null, 2);
    var face = BlockPos.containing(s.area.point(5, 101, 7));
    h.getLevel().setBlockAndUpdate(face, Blocks.STONE.defaultBlockState());
    mob.bindMineFace(face, Direction.SOUTH);
    s.owner.getAbilities().instabuild = true;
    int[] denied = {0};
    Consumer<BlockEvent.BreakEvent> deny =
        e -> {
          if (e.getPos().equals(face)) {
            denied[0]++;
            e.setCanceled(true);
          }
        };
    MinecraftForge.EVENT_BUS.addListener(deny);
    h.startSequence()
        .thenWaitUntil(
            () -> h.assertTrue(h.getLevel().isPositionEntityTicking(mob.blockPosition()), "Ready"))
        .thenExecute(() -> h.getLevel().addFreshEntity(mob))
        .thenIdle(100)
        .thenExecute(
            () -> {
              try {
                h.assertTrue(
                    denied[0] > 0
                        && h.getLevel().getBlockState(face).is(Blocks.STONE)
                        && mob.saveWithoutId(new CompoundTag()).getInt("AncientMineCompleted") == 0,
                    "Denied breaking preserves terrain and progress");
              } finally {
                MinecraftForge.EVENT_BUS.unregister(deny);
                s.close();
              }
            })
        .thenSucceed();
  }
}
