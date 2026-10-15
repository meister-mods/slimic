package io.github.meistermods.slimic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.registry.ModEntities;
import io.github.meistermods.slimic.registry.ModItems;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FriendlySlimeRecoveryGameTests {
  private static final BlockPos CAPTURE_FLOOR = new BlockPos(5, 1, 5);
  private static final BlockPos RELEASE_FLOOR = new BlockPos(8, 1, 8);

  private FriendlySlimeRecoveryGameTests() {}

  @GameTest(template = "empty")
  public static void recoveryPreservesCompleteStateAcrossItemSave(GameTestHelper helper) {
    Player owner = helper.makeMockSurvivalPlayer();
    FriendlySlime slime = ownedSlime(helper, owner);
    UUID identity = slime.getUUID();
    slime.getAttribute(Attributes.MAX_HEALTH).setBaseValue(24.0D);
    slime
        .getAttribute(Attributes.MAX_HEALTH)
        .addPermanentModifier(
            new AttributeModifier(
                UUID.randomUUID(), "recovery health", 6.0D, AttributeModifier.Operation.ADDITION));
    slime.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.45D);
    slime.setHealth(6.5F);
    slime.setCustomName(Component.literal("Jelly"));
    slime.setCustomNameVisible(true);
    slime.setOrderedToSit(true);
    slime.setInSittingPose(true);
    slime.setNoAi(true);
    slime.setSilent(true);
    slime.setInvulnerable(true);
    slime.setAirSupply(150);
    slime.setRemainingFireTicks(80);
    slime.setTicksFrozen(40);
    slime.addTag("slime_helper");
    slime.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND, 3));
    slime.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 2));
    slime.getPersistentData().putInt("SlimeLevel", 7);
    slime.getPersistentData().putString("SlimeJob", "gather");

    ItemStack captured = capture(helper, owner, slime);
    helper.assertTrue(captured.getMaxStackSize() == 1, "A stored companion must not stack");
    ItemStack reloaded = ItemStack.of(captured.save(new CompoundTag()));
    owner.getInventory().clearContent();
    owner.setShiftKeyDown(false);
    owner.setItemInHand(InteractionHand.MAIN_HAND, reloaded);
    helper.assertTrue(
        useOnFloor(helper, owner, RELEASE_FLOOR).consumesAction(),
        "A captured companion must be releasable after an inventory save");
    FriendlySlime restored = releasedSlime(helper);
    helper.assertTrue(identity.equals(restored.getUUID()), "The same identity must return");
    helper.assertTrue(
        owner.getUUID().equals(restored.getOwnerUUID()) && restored.isTame(),
        "Ownership and taming must survive recovery");
    helper.assertTrue(
        restored.getHealth() == 6.5F && restored.getMaxHealth() == 30.0F,
        "Current health, maximum health, and permanent modifiers must survive");
    helper.assertTrue(
        restored.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() == 0.45D,
        "Other attributes must survive");
    helper.assertTrue(
        restored.getName().getString().equals("Jelly") && restored.isCustomNameVisible(),
        "The name and name visibility must survive");
    helper.assertTrue(
        restored.isOrderedToSit() && restored.isInSittingPose(), "The waiting order must survive");
    helper.assertTrue(
        restored.isNoAi() && restored.isSilent() && restored.isInvulnerable(),
        "Saved entity flags must survive");
    helper.assertTrue(
        restored.getAirSupply() == 150
            && restored.getRemainingFireTicks() == 80
            && restored.getTicksFrozen() == 40,
        "Saved air, fire, and freezing state must survive");
    helper.assertTrue(restored.getTags().contains("slime_helper"), "Entity tags must survive");
    ItemStack equipment = restored.getItemBySlot(EquipmentSlot.MAINHAND);
    helper.assertTrue(
        equipment.is(Items.DIAMOND) && equipment.getCount() == 3, "Equipment must survive");
    MobEffectInstance effect = restored.getEffect(MobEffects.MOVEMENT_SPEED);
    helper.assertTrue(
        effect != null && effect.getDuration() == 1200 && effect.getAmplifier() == 2,
        "Active effects and their remaining duration must survive");
    helper.assertTrue(
        restored.getPersistentData().getInt("SlimeLevel") == 7
            && restored.getPersistentData().getString("SlimeJob").equals("gather"),
        "Future traits stored in Forge persistent data must survive");
    helper.assertTrue(
        restored.blockPosition().equals(helper.absolutePos(RELEASE_FLOOR.above())),
        "Release must use the new destination rather than the captured position");
    helper.assertTrue(reloaded.isEmpty(), "A Survival release must consume the captured item");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void repeatedRecoveryDoesNotHealOrDuplicate(GameTestHelper helper) {
    Player owner = helper.makeMockSurvivalPlayer();
    FriendlySlime slime = ownedSlime(helper, owner);
    slime.setHealth(3.5F);
    UUID identity = slime.getUUID();
    for (int cycle = 0; cycle < 3; cycle++) {
      ItemStack captured = capture(helper, owner, slime);
      helper.assertTrue(
          slime.capture(owner) == InteractionResult.FAIL,
          "A removed slime must not be captured twice");
      owner.setShiftKeyDown(false);
      owner.setItemInHand(InteractionHand.MAIN_HAND, captured);
      helper.assertTrue(
          useOnFloor(helper, owner, RELEASE_FLOOR).consumesAction(),
          "Repeated release must succeed");
      slime = releasedSlime(helper);
      helper.assertTrue(
          slime.getHealth() == 3.5F && identity.equals(slime.getUUID()),
          "Repeated recovery must neither heal nor replace the companion");
      helper.assertTrue(
          storedItemCount(owner) == 0, "A released pet must leave no extra stored item");
    }
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void onlyOwnerCanRecover(GameTestHelper helper) {
    Player owner = helper.makeMockSurvivalPlayer();
    Player stranger = helper.makeMockSurvivalPlayer();
    FriendlySlime slime = ownedSlime(helper, owner);
    stranger.setShiftKeyDown(true);
    InteractionResult result =
        ForgeHooks.onInteractEntity(stranger, slime, InteractionHand.MAIN_HAND);
    helper.assertTrue(result == InteractionResult.FAIL, "Only the owner may recover the slime");
    helper.assertTrue(
        slime.isAlive() && !slime.isRemoved() && storedItemCount(stranger) == 0,
        "A rejected recovery must leave the pet in the world");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void fullInventoryPreservesPetInBothModes(GameTestHelper helper) {
    for (boolean creative : new boolean[] {false, true}) {
      Player owner = helper.makeMockSurvivalPlayer();
      owner.getAbilities().instabuild = creative;
      for (int slot = 0; slot < owner.getInventory().items.size(); slot++) {
        owner.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
      }
      FriendlySlime slime = ownedSlime(helper, owner);
      slime.setHealth(4.0F);
      owner.setShiftKeyDown(true);
      helper.assertTrue(
          ForgeHooks.onInteractEntity(owner, slime, InteractionHand.MAIN_HAND)
              == InteractionResult.FAIL,
          "A full inventory must reject recovery even in Creative");
      helper.assertTrue(
          slime.isAlive() && !slime.isRemoved() && slime.getHealth() == 4.0F,
          "A full inventory must leave the companion intact");
      helper.assertTrue(storedItemCount(owner) == 0, "Rejected recovery must not produce an item");
    }
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void shiftRecoveryPrecedesHeldItemInteractions(GameTestHelper helper) {
    for (Item item : new Item[] {Items.NAME_TAG, Items.LEAD}) {
      Player owner = helper.makeMockSurvivalPlayer();
      ItemStack held = new ItemStack(item);
      held.setHoverName(Component.literal("Do not apply"));
      owner.setItemInHand(InteractionHand.MAIN_HAND, held);
      FriendlySlime slime = ownedSlime(helper, owner);
      slime.setCustomName(Component.literal("Original"));
      ItemStack captured = capture(helper, owner, slime);
      helper.assertTrue(
          owner.getMainHandItem() == held && held.getCount() == 1,
          "Shift recovery must not consume or replace the held item");
      helper.assertTrue(
          captured.getHoverName().getString().equals("Original"),
          "Holding a name tag must not rename the pet during recovery");
    }
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void releaseRetainsOriginalOwner(GameTestHelper helper) {
    Player owner = helper.makeMockSurvivalPlayer();
    ItemStack captured = capture(helper, owner, ownedSlime(helper, owner));
    owner.getInventory().clearContent();
    Player carrier = helper.makeMockSurvivalPlayer();
    carrier.setItemInHand(InteractionHand.MAIN_HAND, captured);
    helper.assertTrue(
        useOnFloor(helper, carrier, RELEASE_FLOOR).consumesAction(),
        "A carrier must be able to release the stored slime");
    helper.assertTrue(
        owner.getUUID().equals(releasedSlime(helper).getOwnerUUID()),
        "Releasing a stored slime must not transfer ownership to its carrier");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void blockedReleasePreservesSavedItem(GameTestHelper helper) {
    Player owner = helper.makeMockSurvivalPlayer();
    FriendlySlime slime = ownedSlime(helper, owner);
    slime.setHealth(2.0F);
    ItemStack captured = capture(helper, owner, slime);
    CompoundTag original = captured.getTag().copy();
    for (int x = 7; x <= 9; x++) {
      for (int z = 7; z <= 9; z++) {
        for (int y = 2; y <= 5; y++) {
          helper.setBlock(x, y, z, Blocks.STONE);
        }
      }
    }
    owner.setShiftKeyDown(false);
    owner.setItemInHand(InteractionHand.MAIN_HAND, captured);
    helper.assertTrue(
        useOnFloor(helper, owner, RELEASE_FLOOR) == InteractionResult.FAIL,
        "An obstructed release must fail");
    helper.assertTrue(
        captured.getCount() == 1 && original.equals(captured.getTag()),
        "A failed release must preserve the full saved item without mutation");
    helper.assertTrue(
        helper.getEntities(ModEntities.FRIENDLY_SLIME.get(), CAPTURE_FLOOR, 8).isEmpty(),
        "A failed release must not leave a slime in the world");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void creativeBlockReleaseConsumesStoredPet(GameTestHelper helper) {
    FakePlayer owner = creativePlayer(helper);
    FriendlySlime slime = ownedSlime(helper, owner);
    ItemStack captured = capture(helper, owner, slime);
    ItemStack duplicate = captured.copy();
    owner.setShiftKeyDown(false);
    owner.setItemInHand(InteractionHand.MAIN_HAND, captured);
    InteractionResult result =
        owner.gameMode.useItemOn(
            owner,
            helper.getLevel(),
            captured,
            InteractionHand.MAIN_HAND,
            floorHit(helper, RELEASE_FLOOR));
    helper.assertTrue(
        result.consumesAction() && owner.getMainHandItem().isEmpty(),
        "The real Creative block-use path must consume the stored companion");
    FriendlySlime released = releasedSlime(helper);
    owner.setItemInHand(InteractionHand.MAIN_HAND, duplicate);
    helper.assertTrue(
        useOnFloor(helper, owner, new BlockPos(3, 1, 8)) == InteractionResult.FAIL,
        "An already loaded identity must not be released a second time");
    helper.assertTrue(
        duplicate.getCount() == 1 && !released.isRemoved(),
        "Duplicate rejection must leave both the stored item and active pet intact");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void creativeWaterReleasePreservesHealthAndConsumesPet(GameTestHelper helper) {
    FakePlayer owner = creativePlayer(helper);
    FriendlySlime slime = ownedSlime(helper, owner);
    slime.setHealth(5.0F);
    ItemStack captured = capture(helper, owner, slime);
    helper.setBlock(RELEASE_FLOOR.above(), Blocks.WATER);
    Vec3 position = helper.absoluteVec(new Vec3(8.5D, 3.0D, 8.5D));
    owner.moveTo(position.x, position.y, position.z, 0.0F, 90.0F);
    owner.setShiftKeyDown(false);
    owner.setItemInHand(InteractionHand.MAIN_HAND, captured);
    InteractionResult result =
        owner.gameMode.useItem(owner, helper.getLevel(), captured, InteractionHand.MAIN_HAND);
    helper.assertTrue(
        result.consumesAction() && owner.getMainHandItem().isEmpty(),
        "The real Creative water-use path must consume the stored companion");
    helper.assertTrue(
        releasedSlime(helper).getHealth() == 5.0F,
        "Water release must preserve health rather than create a fresh slime");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void deadPetCannotBeRecovered(GameTestHelper helper) {
    Player owner = helper.makeMockSurvivalPlayer();
    FriendlySlime slime = ownedSlime(helper, owner);
    slime.setHealth(0.0F);
    owner.setShiftKeyDown(true);
    helper.assertTrue(
        ForgeHooks.onInteractEntity(owner, slime, InteractionHand.MAIN_HAND)
            == InteractionResult.FAIL,
        "A dead pet cannot be recovered or resurrected");
    helper.assertTrue(storedItemCount(owner) == 0, "A dead pet must not create a stored item");
    helper.succeed();
  }

  private static FriendlySlime ownedSlime(GameTestHelper helper, Player owner) {
    FriendlySlime slime = helper.spawn(ModEntities.FRIENDLY_SLIME.get(), CAPTURE_FLOOR.above());
    slime.tame(owner);
    return slime;
  }

  private static ItemStack capture(GameTestHelper helper, Player owner, FriendlySlime slime) {
    owner.setShiftKeyDown(true);
    InteractionResult result = ForgeHooks.onInteractEntity(owner, slime, InteractionHand.MAIN_HAND);
    helper.assertTrue(
        result != null && result.consumesAction() && slime.isRemoved(),
        "Owner Shift-right-click must remove the world entity and return an item");
    helper.assertTrue(storedItemCount(owner) == 1, "Recovery must produce exactly one item");
    return owner.getInventory().items.stream()
        .filter(stack -> stack.is(ModItems.FRIENDLY_SLIME.get()))
        .findFirst()
        .orElseThrow();
  }

  private static int storedItemCount(Player player) {
    return player.getInventory().items.stream()
        .filter(stack -> stack.is(ModItems.FRIENDLY_SLIME.get()))
        .mapToInt(ItemStack::getCount)
        .sum();
  }

  private static FriendlySlime releasedSlime(GameTestHelper helper) {
    var slimes = helper.getEntities(ModEntities.FRIENDLY_SLIME.get(), CAPTURE_FLOOR, 8);
    helper.assertTrue(slimes.size() == 1, "Release must restore exactly one companion");
    return slimes.get(0);
  }

  private static InteractionResult useOnFloor(
      GameTestHelper helper, Player player, BlockPos floor) {
    return player
        .getMainHandItem()
        .getItem()
        .useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, floorHit(helper, floor)));
  }

  private static BlockHitResult floorHit(GameTestHelper helper, BlockPos floor) {
    BlockPos position = helper.absolutePos(floor);
    return new BlockHitResult(
        Vec3.atCenterOf(position).add(0.0D, 0.5D, 0.0D), Direction.UP, position, false);
  }

  private static FakePlayer creativePlayer(GameTestHelper helper) {
    FakePlayer player =
        new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "SlimeCarrier"));
    player.setGameMode(GameType.CREATIVE);
    return player;
  }
}
