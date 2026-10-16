package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.SaddleSlime;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.recipe.CompanionRecipe;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SaddleSlimeGameTests {
  private SaddleSlimeGameTests() {}

  private static SaddleSlime pet(GameTestHelper h, Player owner) {
    var pet = ModEntities.SADDLE_SLIME.get().create(h.getLevel());
    pet.moveTo(h.absoluteVec(new Vec3(5.5, 2, 5.5)));
    pet.tame(owner);
    owner.moveTo(pet.position());
    h.getLevel().addFreshEntity(pet);
    return pet;
  }

  private static InteractionResult release(GameTestHelper h, Player owner, ItemStack item) {
    var floor = h.absolutePos(new BlockPos(8, 1, 8));
    h.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
    owner.setItemInHand(InteractionHand.MAIN_HAND, item);
    return item.useOn(
        new UseOnContext(
            owner,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false)));
  }

  @GameTest(template = "empty", batch = "saddle")
  public static void craftedSaddleSpecializationPreservesAndSummonsItsCompanion(GameTestHelper h) {
    Player owner = h.makeMockSurvivalPlayer();
    var friendly = ModEntities.FRIENDLY_SLIME.get().create(h.getLevel());
    friendly.tame(owner);
    friendly.setHealth(7);
    friendly.setCustomName(Component.literal("Cloud"));
    UUID identity = friendly.getUUID();
    ItemStack source = FriendlySlimeItem.fromSlime(friendly);
    ItemStack saddle = new ItemStack(Items.SADDLE);
    saddle.setHoverName(Component.literal("Seat"));
    var grid =
        new TransientCraftingContainer(
            new AbstractContainerMenu(null, 1) {
              public ItemStack quickMoveStack(Player p, int i) {
                return ItemStack.EMPTY;
              }

              public boolean stillValid(Player p) {
                return true;
              }
            },
            2,
            2);
    grid.setItem(3, source);
    grid.setItem(0, saddle);
    var recipe =
        new CompanionRecipe(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                Slimic.MOD_ID, "saddle_test"),
            CraftingBookCategory.MISC,
            CompanionRecipe.Kind.SADDLE);
    h.assertTrue(
        recipe.matches(grid, h.getLevel()), "Friendly slime and saddle are shapeless ingredients");
    ItemStack result = recipe.assemble(grid, h.getLevel().registryAccess());
    h.assertTrue(
        result.is(ModItems.SADDLE_SLIME.get())
            && recipe.getRemainingItems(grid).stream().allMatch(ItemStack::isEmpty),
        "The recipe must consume its ingredients without duplicate remainders");
    grid.setItem(1, new ItemStack(Items.STICK));
    h.assertTrue(!recipe.matches(grid, h.getLevel()), "Extra ingredients cannot craft a mount");
    h.assertTrue(
        release(h, owner, result).consumesAction(),
        "A specialized companion must release normally");
    var restored = (SaddleSlime) h.getLevel().getEntity(identity);
    h.assertTrue(
        restored != null
            && restored.getHealth() == 7
            && restored.getOwnerUUID().equals(owner.getUUID())
            && restored.getName().getString().equals("Cloud")
            && ItemStack.matches(restored.getEquipment(0), saddle),
        "Specialization must retain identity, ownership, current health, name and exact saddle");
    restored.discard();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "saddle")
  public static void ownerCanEquipRideAndRemoveSaddleWithoutDuplication(GameTestHelper h) {
    Player owner = h.makeMockSurvivalPlayer();
    var slime = pet(h, owner);
    h.assertTrue(!slime.mount(owner), "An unsaddled slime cannot be ridden");
    owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SADDLE));
    h.assertTrue(
        slime.mobInteract(owner, InteractionHand.MAIN_HAND).consumesAction()
            && owner.getMainHandItem().isEmpty()
            && slime.isSaddled(),
        "Applying a saddle consumes exactly one item");
    h.assertTrue(
        slime.mount(owner) && slime.getControllingPassenger() == owner,
        "The owner controls its saddled slime");
    Player other = h.makeMockSurvivalPlayer();
    other.moveTo(slime.position());
    h.assertTrue(
        !slime.mount(other) && !slime.setRiderInput(other, true, true),
        "Other players cannot board or inject controls");
    slime.setEquipment(0, ItemStack.EMPTY);
    h.assertTrue(
        owner.getVehicle() == null && !slime.isNoGravity(),
        "Removing the saddle ends the ride and transient flight");
    slime.discard();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "saddle")
  public static void wingsRequireAnOwnerRiderAndUsableElytra(GameTestHelper h) {
    Player owner = h.makeMockSurvivalPlayer();
    var slime = pet(h, owner);
    slime.setEquipment(0, new ItemStack(Items.SADDLE));
    ItemStack wings = new ItemStack(Items.ELYTRA);
    slime.setEquipment(1, wings);
    h.assertTrue(
        slime.hasWings() && !slime.isNoGravity(),
        "Equipped wings do not make an unattended companion float");
    h.assertTrue(
        slime.mount(owner) && slime.isFlyingMount() && slime.isNoGravity(),
        "A mounted owner enables flight without rockets");
    h.assertTrue(
        slime.setRiderInput(owner, true, false), "The actual rider may send ascent intent");
    CompoundTag saved = slime.saveWithoutId(new CompoundTag());
    h.assertTrue(
        !saved.getBoolean("NoGravity"),
        "Transient ridden flight must not become permanent saved no-gravity");
    owner.stopRiding();
    h.assertTrue(
        !slime.isNoGravity() && !slime.setRiderInput(owner, true, false),
        "Dismounting disables flight and old controls");
    slime.mount(owner);
    wings.setDamageValue(wings.getMaxDamage() - 1);
    h.assertTrue(!slime.hasWings() && !slime.isNoGravity(), "Unusable elytra do not grant flight");
    slime.setEquipment(1, new ItemStack(Items.ELYTRA));
    slime.setNoGravity(true);
    h.assertTrue(
        slime.saveWithoutId(new CompoundTag()).getBoolean("NoGravity"),
        "An explicit native gravity override remains authoritative");
    owner.stopRiding();
    slime.discard();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "saddle")
  public static void equipmentOnlyMenuSupportsExactShiftTransfers(GameTestHelper h) {
    Player owner = h.makeMockSurvivalPlayer();
    var slime = pet(h, owner);
    var menu = new SlimeMenu(1, owner.getInventory(), slime);
    ItemStack wings = new ItemStack(Items.ELYTRA);
    wings.setDamageValue(41);
    wings.enchant(Enchantments.UNBREAKING, 3);
    owner.getInventory().setItem(9, new ItemStack(Items.SADDLE));
    owner.getInventory().setItem(10, wings.copy());
    owner.getInventory().setItem(11, new ItemStack(Items.DIAMOND, 5));
    h.assertTrue(
        menu.slots.size() == 38
            && !menu.quickMoveStack(owner, 2).isEmpty()
            && !menu.quickMoveStack(owner, 3).isEmpty()
            && slime.isSaddled()
            && ItemStack.matches(slime.getEquipment(1), wings),
        "Both equipment slots receive exact items through Shift-click");
    h.assertTrue(
        menu.quickMoveStack(owner, 4).isEmpty() && owner.getInventory().getItem(11).getCount() == 5,
        "An equipment-only mount must not swallow cargo into nonexistent bag slots");
    h.assertTrue(slime.mount(owner), "The equipped owner can ride");
    h.assertTrue(
        !menu.quickMoveStack(owner, 0).isEmpty() && owner.getVehicle() == null,
        "Shift-removing the saddle returns it and dismounts the rider");
    Player other = h.makeMockSurvivalPlayer();
    h.assertTrue(menu.quickMoveStack(other, 1).isEmpty(), "Equipment transfers remain owner-only");
    slime.discard();
    h.succeed();
  }

  @GameTest(template = "empty", batch = "saddle")
  public static void mountedRecoveryRetainsGearVitalsAndIdentityWithoutFlightFlags(
      GameTestHelper h) {
    Player owner = h.makeMockSurvivalPlayer();
    var slime = pet(h, owner);
    slime.setEquipment(0, new ItemStack(Items.SADDLE));
    ItemStack wings = new ItemStack(Items.ELYTRA);
    wings.setDamageValue(33);
    wings.enchant(Enchantments.MENDING, 1);
    slime.setEquipment(1, wings);
    slime.setTunedVitals(6, 39, 4);
    slime.dye(owner, new ItemStack(Items.BLUE_DYE));
    UUID identity = slime.getUUID();
    slime.mount(owner);
    h.assertTrue(
        slime.capture(owner).consumesAction() && owner.getVehicle() == null,
        "Recovery detaches the rider");
    ItemStack stored =
        owner.getInventory().items.stream()
            .filter(s -> s.is(ModItems.SADDLE_SLIME.get()))
            .findFirst()
            .orElseThrow();
    var payload = stored.getTag().getCompound("SlimeData");
    h.assertTrue(
        !payload.contains("Passengers") && !payload.getBoolean("NoGravity"),
        "Stored companions retain neither passengers nor transient flight");
    h.assertTrue(
        release(h, owner, stored).consumesAction(), "A recovered mount must release normally");
    var restored = (SaddleSlime) h.getLevel().getEntity(identity);
    h.assertTrue(
        restored != null
            && restored.getHealth() == 6
            && restored.getStamina() == 39
            && restored.getStatPoints() == 4
            && restored.getDyeColorId() == 11
            && restored.isSaddled()
            && ItemStack.matches(restored.getEquipment(1), wings)
            && !restored.isVehicle()
            && !restored.isNoGravity(),
        "Exact equipment, identity, dye, health and growth survive release without hovering");
    restored.discard();
    h.succeed();
  }
}
