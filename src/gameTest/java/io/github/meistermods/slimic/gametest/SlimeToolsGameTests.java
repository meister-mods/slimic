package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.HeraldSlime;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.item.CreativeSlimeTunerItem;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.item.HeraldSlimeItem;
import io.github.meistermods.slimic.menu.CreativeSlimeTunerMenu;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.recipe.HeraldSlimeRecipe;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SlimeToolsGameTests {
  private static final BlockPos PET = new BlockPos(5, 2, 5);

  private SlimeToolsGameTests() {}

  @GameTest(template = "empty")
  public static void dyeAllCompanionTypesAndPreserveColor(GameTestHelper helper) {
    Player owner = helper.makeMockSurvivalPlayer();
    for (var type :
        new net.minecraft.world.entity.EntityType<?>[] {
          ModEntities.FRIENDLY_SLIME.get(),
          ModEntities.MINING_SLIME.get(),
          ModEntities.TRANSPORT_SLIME.get(),
          ModEntities.HERALD_SLIME.get(),
          ModEntities.COMBAT_SLIME.get(),
          ModEntities.CLEANING_SLIME.get(),
          ModEntities.SADDLE_SLIME.get(),
          ModEntities.FARMER_SLIME.get(),
          ModEntities.LUMBERJACK_SLIME.get(),
          ModEntities.GUARDIAN_ANGEL_SLIME.get(),
          ModEntities.SOGGY_SLIME.get(),
          ModEntities.HEALING_SLIME.get(),
          ModEntities.WAREHOUSE_SLIME.get(),
          ModEntities.FISHING_SLIME.get(),
          ModEntities.ABUNDANCE_SLIME.get(),
          ModEntities.TUNNEL_SLIME.get(),
          ModEntities.DRAGON_SLIME.get(),
          ModEntities.MEOW_SLIME.get()
        }) {
      FriendlySlime slime = (FriendlySlime) type.create(helper.getLevel());
      slime.tame(owner);
      ItemStack dye = new ItemStack(Items.WHITE_DYE, 2);
      helper.assertTrue(
          slime.dye(owner, dye)
              && dye.getCount() == 1
              && slime.getDyeColorId() == DyeColor.WHITE.getId(),
          "Every specialization must support dye and consume one item");
      CompoundTag saved = slime.saveWithoutId(new CompoundTag());
      FriendlySlime restored = (FriendlySlime) type.create(helper.getLevel());
      restored.load(saved);
      helper.assertTrue(
          restored.getDyeColorId() == DyeColor.WHITE.getId()
              && FriendlySlimeItem.fromSlime(restored)
                      .getTag()
                      .getCompound("SlimeData")
                      .getInt("SlimeDyeColor")
                  == DyeColor.WHITE.getId(),
          "Color must persist in world and recovered-item data");
      Player stranger = helper.makeMockSurvivalPlayer();
      helper.assertTrue(
          !slime.dye(stranger, new ItemStack(Items.BLUE_DYE)),
          "A non-owner cannot recolor the slime");
    }
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void creativeDyeDoesNotConsumeAndOpensNoManagementMenu(GameTestHelper helper) {
    Player owner = helper.makeMockSurvivalPlayer();
    owner.getAbilities().instabuild = true;
    FriendlySlime pet = helper.spawn(ModEntities.FRIENDLY_SLIME.get(), PET);
    pet.tame(owner);
    owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.RED_DYE));
    pet.mobInteract(owner, InteractionHand.MAIN_HAND);
    helper.assertTrue(
        owner.getMainHandItem().getCount() == 1 && pet.getDyeColorId() == DyeColor.RED.getId(),
        "Creative recoloring must preserve the dye stack");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void tunerIsCreativeOnlyAndValidatesHeldToolAndDistance(GameTestHelper helper) {
    Player player = helper.makeMockSurvivalPlayer();
    FriendlySlime pet = helper.spawn(ModEntities.FRIENDLY_SLIME.get(), PET);
    player.moveTo(pet.position());
    player.setItemInHand(
        InteractionHand.MAIN_HAND, new ItemStack(ModItems.CREATIVE_SLIME_TUNER.get()));
    CreativeSlimeTunerMenu menu = new CreativeSlimeTunerMenu(1, player.getInventory(), pet);
    double[] values = values(pet);
    helper.assertTrue(
        !menu.apply(player, values, 10, 100, 4)
            && CreativeSlimeTunerItem.open(player, pet)
                == net.minecraft.world.InteractionResult.FAIL,
        "Survival players must not tune slimes");
    player.getAbilities().instabuild = true;
    helper.assertTrue(
        menu.apply(player, values, 10, 100, 4) && pet.getHealth() == 10 && pet.getStatPoints() == 4,
        "Creative tuning should apply without ownership requirements");
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    helper.assertTrue(
        !menu.apply(player, values, 10, 100, 4),
        "A stale menu cannot edit after the tuner is put away");
    player.setItemInHand(
        InteractionHand.MAIN_HAND, new ItemStack(ModItems.CREATIVE_SLIME_TUNER.get()));
    player.moveTo(pet.position().add(17, 0, 0));
    helper.assertTrue(!menu.apply(player, values, 10, 100, 4), "Remote tuning must be rejected");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void tunerPreservesModifiersAndRejectsInvalidOrOccupiedCapacity(
      GameTestHelper helper) {
    Player player = helper.makeMockSurvivalPlayer();
    player.getAbilities().instabuild = true;
    var slime = helper.spawn(ModEntities.TRANSPORT_SLIME.get(), PET);
    slime.setWorking(true);
    player.moveTo(slime.position());
    player.setItemInHand(
        InteractionHand.MAIN_HAND, new ItemStack(ModItems.CREATIVE_SLIME_TUNER.get()));
    slime
        .getAttribute(Attributes.MAX_HEALTH)
        .addPermanentModifier(
            new AttributeModifier(
                UUID.randomUUID(), "Bonus", 4, AttributeModifier.Operation.ADDITION));
    CreativeSlimeTunerMenu menu = new CreativeSlimeTunerMenu(2, player.getInventory(), slime);
    double[] values = values(slime);
    values[0] = 40;
    values[4] = 3;
    values[5] = 27;
    values[6] = 800;
    helper.assertTrue(
        menu.apply(player, values, 20, 600, 8)
            && slime.getMaxHealth() == 44
            && slime.getCapacity() == 54,
        "Tuner base values must preserve modifiers and transport's 2x multiplier");
    slime.getBag().setStackInSlot(53, new ItemStack(Items.DIAMOND));
    values[0] = 99;
    values[5] = 9;
    helper.assertTrue(
        !menu.apply(player, values, 20, 600, 8)
            && slime.getMaxHealth() == 44
            && slime.getBag().getStackInSlot(53).is(Items.DIAMOND),
        "Capacity edits must never hide cargo or partially apply stats");
    values[5] = 27;
    values[4] = Double.NaN;
    helper.assertTrue(
        !menu.apply(player, values, 20, 600, 8), "Non-finite stat values must be rejected");
    CompoundTag saved = slime.saveWithoutId(new CompoundTag());
    var restored = ModEntities.TRANSPORT_SLIME.get().create(helper.getLevel());
    restored.setWorking(true);
    restored.load(saved);
    helper.assertTrue(
        restored.getCapacity() == 54
            && restored.getMaxStamina() == 800
            && SlimeStat.WORK_SPEED.value(restored) == 3
            && restored.getStamina() == 600,
        "Tuned stats and current stamina must persist");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void heraldRecipeKeepsExactFireworkAndCompanionData(GameTestHelper helper) {
    FriendlySlime pet = helper.spawn(ModEntities.FRIENDLY_SLIME.get(), PET);
    pet.tame(helper.makeMockSurvivalPlayer());
    pet.setHealth(7);
    ItemStack friendly = FriendlySlimeItem.fromSlime(pet);
    ItemStack rocket = HeraldSlime.defaultFirework();
    rocket.getOrCreateTagElement("Fireworks").putByte("Flight", (byte) 3);
    rocket
        .getTagElement("Fireworks")
        .getList("Explosions", Tag.TAG_COMPOUND)
        .getCompound(0)
        .putByte("Type", (byte) 4);
    rocket
        .getTagElement("Fireworks")
        .getList("Explosions", Tag.TAG_COMPOUND)
        .getCompound(0)
        .putIntArray("Colors", new int[] {0xff0000, 0x00ffff});
    ItemStack result = HeraldSlimeRecipe.specialize(friendly, rocket);
    helper.assertTrue(
        result.is(ModItems.HERALD_SLIME.get())
            && result
                .getTag()
                .getCompound("SlimeData")
                .equals(friendly.getTag().getCompound("SlimeData")),
        "Specialization must retain the recovered companion's traits");
    helper.assertTrue(
        ItemStack.matches(
            rocket, ItemStack.of(result.getTag().getCompound(HeraldSlimeItem.INITIAL_FIREWORK))),
        "Flight, shape, colors, and full rocket data must be copied");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void creativeHeraldUsesWhiteRoundFireworkAndOwnDelay(GameTestHelper helper) {
    HeraldSlime herald = helper.spawn(ModEntities.HERALD_SLIME.get(), PET);
    herald.setMonitoring(true);
    CompoundTag fireworks = herald.getFirework().getTagElement("Fireworks");
    CompoundTag explosion = fireworks.getList("Explosions", Tag.TAG_COMPOUND).getCompound(0);
    helper.assertTrue(
        explosion.getByte("Type") == 0
            && explosion.getIntArray("Colors")[0] == DyeColor.WHITE.getFireworkColor(),
        "Default summons must have a white round firework");
    Player owner = helper.makeMockSurvivalPlayer();
    herald.tame(owner);
    owner.moveTo(herald.position());
    SlimeMenu menu = TestMenus.create(1, owner.getInventory(), herald);
    int previous = herald.getDelayMinutes();
    helper.assertTrue(
        menu.clickMenuButton(owner, SlimeMenu.DELAY_UP) && herald.getDelayMinutes() == previous + 1,
        "Owners must be able to configure alert minutes");
    CompoundTag saved = herald.saveWithoutId(new CompoundTag());
    HeraldSlime restored = ModEntities.HERALD_SLIME.get().create(helper.getLevel());
    restored.setMonitoring(true);
    restored.load(saved);
    helper.assertTrue(
        restored.getDelayMinutes() == previous + 1
            && ItemStack.matches(restored.getFirework(), herald.getFirework()),
        "Alert setting and default design must persist");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 120)
  public static void heraldSignalsBlockedWorkOnlyOnceWithoutConsumingRockets(
      GameTestHelper helper) {
    Player owner = helper.makeMockSurvivalPlayer();
    MiningSlime miner = helper.spawn(ModEntities.MINING_SLIME.get(), PET);
    miner.setWorking(true);
    miner.tame(owner);
    miner.onReleased();
    CompoundTag workerState = miner.saveWithoutId(new CompoundTag());
    workerState.putInt("MiningStatus", MiningSlime.Status.NO_TOOL.ordinal());
    workerState.putInt("BlockedWorkTicks", 1200);
    workerState.putLong("BlockedEpisode", 5);
    miner.load(workerState);
    HeraldSlime herald = helper.spawn(ModEntities.HERALD_SLIME.get(), PET.east(2));
    herald.setMonitoring(true);
    herald.tame(owner);
    herald.setDelayMinutes(1);
    ItemStack design = HeraldSlime.defaultFirework();
    design.getOrCreateTagElement("Fireworks").putByte("Flight", (byte) 3);
    herald.setFirework(design);
    helper.runAfterDelay(
        25,
        () -> {
          long count =
              helper
                  .getEntities(net.minecraft.world.entity.EntityType.FIREWORK_ROCKET, PET, 64)
                  .stream()
                  .filter(
                      rocket ->
                          rocket.getPersistentData().getBoolean(HeraldSlime.SIGNAL_MARKER)
                              && rocket.getOwner() == herald)
                  .count();
          helper.assertTrue(
              count == 1 && ItemStack.matches(herald.getFirework(), design),
              "An incident must launch one matching rocket without consuming its template");
        });
    helper.runAfterDelay(
        105,
        () -> {
          helper.assertTrue(
              helper
                  .getEntities(net.minecraft.world.entity.EntityType.FIREWORK_ROCKET, PET, 64)
                  .stream()
                  .noneMatch(rocket -> rocket.getOwner() == herald),
              "The same incident must not launch another rocket after the cooldown");
          helper.assertTrue(
              herald
                      .saveWithoutId(new CompoundTag())
                      .getCompound("HeraldWarnings")
                      .getLong(miner.getUUID().toString())
                  == 5,
              "The warning episode must be saved");
          helper.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void heraldDoesNotSignalWaitingOrForeignWorkers(GameTestHelper helper) {
    Player owner = helper.makeMockSurvivalPlayer();
    HeraldSlime herald = helper.spawn(ModEntities.HERALD_SLIME.get(), PET.east(2));
    herald.setMonitoring(true);
    herald.tame(owner);
    herald.setDelayMinutes(1);
    MiningSlime waiting = helper.spawn(ModEntities.MINING_SLIME.get(), PET);
    waiting.setWorking(true);
    waiting.tame(owner);
    waiting.setWaiting(true);
    MiningSlime stranger = helper.spawn(ModEntities.MINING_SLIME.get(), PET.south(2));
    stranger.setWorking(true);
    stranger.tame(helper.makeMockSurvivalPlayer());
    for (MiningSlime miner : new MiningSlime[] {waiting, stranger}) {
      CompoundTag state = miner.saveWithoutId(new CompoundTag());
      state.putInt("BlockedWorkTicks", 1200);
      state.putLong("BlockedEpisode", 5);
      miner.load(state);
    }
    helper.runAfterDelay(
        60,
        () -> {
          helper.assertTrue(
              herald.saveWithoutId(new CompoundTag()).getCompound("HeraldWarnings").isEmpty(),
              "Manual waiting and other owners are excluded from alerts");
          helper.succeed();
        });
  }

  @GameTest(template = "empty")
  public static void signalFireworkCannotHurtSlimes(GameTestHelper helper) {
    FriendlySlime pet = helper.spawn(ModEntities.FRIENDLY_SLIME.get(), PET);
    FireworkRocketEntity rocket =
        new FireworkRocketEntity(
            helper.getLevel(), pet.getX(), pet.getY(), pet.getZ(), HeraldSlime.defaultFirework());
    rocket.getPersistentData().putBoolean(HeraldSlime.SIGNAL_MARKER, true);
    pet.hurt(helper.getLevel().damageSources().fireworks(rocket, null), 8);
    helper.assertTrue(pet.getHealth() == pet.getMaxHealth(), "Signal rockets must be harmless");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void heraldRecoveryRetainsDesignDyeTuningAndWarningHistory(GameTestHelper helper) {
    for (int x = 0; x < 12; x++)
      for (int z = 0; z < 12; z++)
        helper.setBlock(x, 1, z, net.minecraft.world.level.block.Blocks.BEDROCK);
    Player owner = helper.makeMockSurvivalPlayer();
    HeraldSlime herald = helper.spawn(ModEntities.HERALD_SLIME.get(), PET);
    herald.setMonitoring(true);
    herald.tame(owner);
    herald.setNoAi(true);
    herald.dye(owner, new ItemStack(Items.PURPLE_DYE));
    SlimeStat.HEALTH.tune(herald, 40);
    SlimeStat.WORK_SPEED.tune(herald, 3);
    herald.setTunedVitals(13, 77, 9);
    herald.setDelayMinutes(11);
    herald.setMonitoring(false);
    ItemStack design = HeraldSlime.defaultFirework();
    design.getOrCreateTagElement("Fireworks").putByte("Flight", (byte) 3);
    herald.setFirework(design);
    CompoundTag state = herald.saveWithoutId(new CompoundTag());
    String incident = UUID.randomUUID().toString();
    state.getCompound("HeraldWarnings").putLong(incident, 19);
    state.putInt("HeraldCooldown", 31);
    herald.load(state);
    UUID identity = herald.getUUID();
    helper.assertTrue(herald.capture(owner).consumesAction(), "Herald must be recoverable");
    ItemStack stored =
        owner.getInventory().items.stream()
            .filter(stack -> stack.is(ModItems.HERALD_SLIME.get()))
            .findFirst()
            .orElseThrow();
    ItemStack reloaded = ItemStack.of(stored.save(new CompoundTag()));
    owner.getInventory().clearContent();
    owner.setItemInHand(InteractionHand.MAIN_HAND, reloaded);
    BlockPos floor = helper.absolutePos(PET.below());
    helper.assertTrue(
        reloaded
            .getItem()
            .useOn(
                new net.minecraft.world.item.context.UseOnContext(
                    owner,
                    InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(floor).add(0, .5, 0),
                        net.minecraft.core.Direction.UP,
                        floor,
                        false)))
            .consumesAction(),
        "Stored herald must release");
    HeraldSlime restored = helper.getEntities(ModEntities.HERALD_SLIME.get(), PET, 6).get(0);
    CompoundTag saved = restored.saveWithoutId(new CompoundTag());
    helper.assertTrue(
        restored.getUUID().equals(identity)
            && restored.getHealth() == 13
            && restored.getMaxHealth() == 40
            && restored.getStamina() == 77
            && restored.getStatPoints() == 9
            && SlimeStat.WORK_SPEED.value(restored) == 3
            && restored.getDyeColorId() == DyeColor.PURPLE.getId()
            && restored.getDelayMinutes() == 11
            && !restored.isWorking()
            && ItemStack.matches(restored.getFirework(), design)
            && saved.getCompound("HeraldWarnings").getLong(incident) == 19
            && saved.getCompound("HeraldWarningCooldowns").getInt(incident) == 31
            && reloaded.isEmpty(),
        "Actual capture, item save, and release must retain every herald trait");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void shiftRightClickWithTunerStillRecoversOwnedSlime(GameTestHelper helper) {
    Player owner = helper.makeMockSurvivalPlayer();
    owner.getAbilities().instabuild = true;
    FriendlySlime pet = helper.spawn(ModEntities.FRIENDLY_SLIME.get(), PET);
    pet.tame(owner);
    owner.setItemInHand(
        InteractionHand.MAIN_HAND, new ItemStack(ModItems.CREATIVE_SLIME_TUNER.get()));
    owner.setShiftKeyDown(true);
    helper.assertTrue(
        pet.mobInteract(owner, InteractionHand.MAIN_HAND).consumesAction()
            && pet.isRemoved()
            && owner.getInventory().items.stream()
                .anyMatch(stack -> stack.is(ModItems.FRIENDLY_SLIME.get())),
        "Shift recovery must retain priority when holding the new creative tool");
    helper.succeed();
  }

  private static double[] values(FriendlySlime slime) {
    double[] values = new double[9];
    for (SlimeStat stat : SlimeStat.values()) values[stat.ordinal()] = stat.tunerValue(slime);
    return values;
  }
}
