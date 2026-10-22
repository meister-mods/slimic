package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.SignalingSlime;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.item.CreativeSlimeTunerItem;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.item.SignalingSlimeItem;
import io.github.meistermods.slimic.menu.CreativeSlimeTunerMenu;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.recipe.SignalingSlimeRecipe;
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
          ModEntities.SIGNALING_SLIME.get(),
          ModEntities.COMBAT_SLIME.get(),
          ModEntities.CLEANING_SLIME.get(),
          ModEntities.SADDLE_SLIME.get(),
          ModEntities.FARMING_SLIME.get(),
          ModEntities.LOGGING_SLIME.get(),
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
  public static void signalingRecipeKeepsExactFireworkAndCompanionData(GameTestHelper helper) {
    FriendlySlime pet = helper.spawn(ModEntities.FRIENDLY_SLIME.get(), PET);
    pet.tame(helper.makeMockSurvivalPlayer());
    pet.setHealth(7);
    ItemStack friendly = FriendlySlimeItem.fromSlime(pet);
    ItemStack rocket = SignalingSlime.defaultFirework();
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
    ItemStack result = SignalingSlimeRecipe.specialize(friendly, rocket);
    helper.assertTrue(
        result.is(ModItems.SIGNALING_SLIME.get())
            && result
                .getTag()
                .getCompound("SlimeData")
                .equals(friendly.getTag().getCompound("SlimeData")),
        "Specialization must retain the recovered companion's traits");
    helper.assertTrue(
        ItemStack.matches(
            rocket, ItemStack.of(result.getTag().getCompound(SignalingSlimeItem.INITIAL_FIREWORK))),
        "Flight, shape, colors, and full rocket data must be copied");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void creativeSignalingUsesWhiteRoundFireworkAndOwnDelay(GameTestHelper helper) {
    SignalingSlime signaling = helper.spawn(ModEntities.SIGNALING_SLIME.get(), PET);
    signaling.setMonitoring(true);
    CompoundTag fireworks = signaling.getFirework().getTagElement("Fireworks");
    CompoundTag explosion = fireworks.getList("Explosions", Tag.TAG_COMPOUND).getCompound(0);
    helper.assertTrue(
        explosion.getByte("Type") == 0
            && explosion.getIntArray("Colors")[0] == DyeColor.WHITE.getFireworkColor(),
        "Default summons must have a white round firework");
    Player owner = helper.makeMockSurvivalPlayer();
    signaling.tame(owner);
    owner.moveTo(signaling.position());
    SlimeMenu menu = TestMenus.create(1, owner.getInventory(), signaling);
    int previous = signaling.getDelayMinutes();
    helper.assertTrue(
        menu.clickMenuButton(owner, SlimeMenu.DELAY_UP)
            && signaling.getDelayMinutes() == previous + 1,
        "Owners must be able to configure alert minutes");
    CompoundTag saved = signaling.saveWithoutId(new CompoundTag());
    SignalingSlime restored = ModEntities.SIGNALING_SLIME.get().create(helper.getLevel());
    restored.setMonitoring(true);
    restored.load(saved);
    helper.assertTrue(
        restored.getDelayMinutes() == previous + 1
            && ItemStack.matches(restored.getFirework(), signaling.getFirework()),
        "Alert setting and default design must persist");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 120)
  public static void signalingSignalsBlockedWorkOnlyOnceWithoutConsumingRockets(
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
    SignalingSlime signaling = helper.spawn(ModEntities.SIGNALING_SLIME.get(), PET.east(2));
    signaling.setMonitoring(true);
    signaling.tame(owner);
    signaling.setDelayMinutes(1);
    ItemStack design = SignalingSlime.defaultFirework();
    design.getOrCreateTagElement("Fireworks").putByte("Flight", (byte) 3);
    signaling.setFirework(design);
    helper.runAfterDelay(
        25,
        () -> {
          long count =
              helper
                  .getEntities(net.minecraft.world.entity.EntityType.FIREWORK_ROCKET, PET, 64)
                  .stream()
                  .filter(
                      rocket ->
                          rocket.getPersistentData().getBoolean(SignalingSlime.SIGNAL_MARKER)
                              && rocket.getOwner() == signaling)
                  .count();
          helper.assertTrue(
              count == 1 && ItemStack.matches(signaling.getFirework(), design),
              "An incident must launch one matching rocket without consuming its template");
        });
    helper.runAfterDelay(
        105,
        () -> {
          helper.assertTrue(
              helper
                  .getEntities(net.minecraft.world.entity.EntityType.FIREWORK_ROCKET, PET, 64)
                  .stream()
                  .noneMatch(rocket -> rocket.getOwner() == signaling),
              "The same incident must not launch another rocket after the cooldown");
          helper.assertTrue(
              signaling
                      .saveWithoutId(new CompoundTag())
                      .getCompound("HeraldWarnings")
                      .getLong(miner.getUUID().toString())
                  == 5,
              "The warning episode must be saved");
          helper.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void signalingDoesNotSignalWaitingOrForeignWorkers(GameTestHelper helper) {
    Player owner = helper.makeMockSurvivalPlayer();
    SignalingSlime signaling = helper.spawn(ModEntities.SIGNALING_SLIME.get(), PET.east(2));
    signaling.setMonitoring(true);
    signaling.tame(owner);
    signaling.setDelayMinutes(1);
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
              signaling.saveWithoutId(new CompoundTag()).getCompound("HeraldWarnings").isEmpty(),
              "Manual waiting and other owners are excluded from alerts");
          helper.succeed();
        });
  }

  @GameTest(template = "empty")
  public static void signalFireworkCannotHurtSlimes(GameTestHelper helper) {
    FriendlySlime pet = helper.spawn(ModEntities.FRIENDLY_SLIME.get(), PET);
    FireworkRocketEntity rocket =
        new FireworkRocketEntity(
            helper.getLevel(),
            pet.getX(),
            pet.getY(),
            pet.getZ(),
            SignalingSlime.defaultFirework());
    rocket.getPersistentData().putBoolean(SignalingSlime.SIGNAL_MARKER, true);
    pet.hurt(helper.getLevel().damageSources().fireworks(rocket, null), 8);
    helper.assertTrue(pet.getHealth() == pet.getMaxHealth(), "Signal rockets must be harmless");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void signalingRecoveryRetainsDesignDyeTuningAndWarningHistory(
      GameTestHelper helper) {
    for (int x = 0; x < 12; x++)
      for (int z = 0; z < 12; z++)
        helper.setBlock(x, 1, z, net.minecraft.world.level.block.Blocks.BEDROCK);
    Player owner = helper.makeMockSurvivalPlayer();
    SignalingSlime signaling = helper.spawn(ModEntities.SIGNALING_SLIME.get(), PET);
    signaling.setMonitoring(true);
    signaling.tame(owner);
    signaling.setNoAi(true);
    signaling.dye(owner, new ItemStack(Items.PURPLE_DYE));
    SlimeStat.HEALTH.tune(signaling, 40);
    SlimeStat.WORK_SPEED.tune(signaling, 3);
    signaling.setTunedVitals(13, 77, 9);
    signaling.setDelayMinutes(11);
    signaling.setMonitoring(false);
    ItemStack design = SignalingSlime.defaultFirework();
    design.getOrCreateTagElement("Fireworks").putByte("Flight", (byte) 3);
    signaling.setFirework(design);
    CompoundTag state = signaling.saveWithoutId(new CompoundTag());
    String incident = UUID.randomUUID().toString();
    state.getCompound("HeraldWarnings").putLong(incident, 19);
    state.putInt("HeraldCooldown", 31);
    signaling.load(state);
    UUID identity = signaling.getUUID();
    helper.assertTrue(signaling.capture(owner).consumesAction(), "Herald must be recoverable");
    ItemStack stored =
        owner.getInventory().items.stream()
            .filter(stack -> stack.is(ModItems.SIGNALING_SLIME.get()))
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
    SignalingSlime restored = helper.getEntities(ModEntities.SIGNALING_SLIME.get(), PET, 6).get(0);
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
  public static void shiftRightClickWithCreativeTunerRecoversOwnedSlime(GameTestHelper helper) {
    Player owner = helper.makeMockSurvivalPlayer();
    owner.getAbilities().instabuild = true;
    FriendlySlime pet = helper.spawn(ModEntities.FRIENDLY_SLIME.get(), PET);
    pet.tame(owner);
    owner.moveTo(pet.position());
    owner.setItemInHand(
        InteractionHand.MAIN_HAND, new ItemStack(ModItems.CREATIVE_SLIME_TUNER.get()));
    owner.setShiftKeyDown(true);
    helper.assertTrue(
        pet.mobInteract(owner, InteractionHand.MAIN_HAND).consumesAction()
            && pet.isRemoved()
            && owner.getInventory().items.stream()
                .anyMatch(stack -> stack.is(ModItems.FRIENDLY_SLIME.get())),
        "Shift with a Creative tuner uses common recovery instead of a work-area preview");
    helper.succeed();
  }

  private static double[] values(FriendlySlime slime) {
    double[] values = new double[9];
    for (SlimeStat stat : SlimeStat.values()) values[stat.ordinal()] = stat.tunerValue(slime);
    return values;
  }
}
