package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.SlimeStat;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.ModEntities;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MenuReliabilityGameTests {
  private MenuReliabilityGameTests() {}

  @GameTest(template = "empty", batch = "menus")
  public static void hiddenCombatBagRejectsShiftClickMerges(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var fighter = h.spawn(ModEntities.COMBAT_SLIME.get(), new BlockPos(5, 2, 5));
    fighter.setWorking(true);
    fighter.setNoAi(true);
    fighter.tame(owner);
    owner.moveTo(fighter.position());
    fighter.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 10));
    owner.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 5));
    SlimeMenu menu = TestMenus.create(1, owner.getInventory(), fighter);
    h.assertTrue(menu.clickMenuButton(owner, SlimeMenu.TARGET_PAGE_BUTTON), "Open target settings");
    h.assertTrue(
        menu.quickMoveStack(owner, 30).isEmpty()
            && owner.getInventory().getItem(9).getCount() == 5
            && fighter.getBag().getStackInSlot(0).getCount() == 10,
        "Shift-click must not merge into an occupied bag slot hidden by target settings");
    menu.clicked(3, 0, ClickType.SWAP, owner);
    h.assertTrue(
        fighter.getBag().getStackInSlot(0).getCount() == 10,
        "Hotbar swapping must also reject hidden bag slots");
    h.assertTrue(menu.clickMenuButton(owner, SlimeMenu.TARGET_PAGE_BUTTON), "Return to the bag");
    h.assertTrue(
        !menu.quickMoveStack(owner, 30).isEmpty()
            && fighter.getBag().getStackInSlot(0).getCount() == 15,
        "The same merge must succeed on the visible bag page");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "menus", timeoutTicks = 400)
  public static void partialShiftClickUnloadingResumesAFullMiner(GameTestHelper h) {
    LoadedWorkArea arena = new LoadedWorkArea(h);
    Player owner = h.makeMockSurvivalPlayer();
    MiningSlime miner = ModEntities.MINING_SLIME.get().create(h.getLevel());
    miner.setWorking(true);
    miner.moveTo(arena.point(6, 101, 8));
    miner.tame(owner);
    miner.setTool(0, new ItemStack(Items.IRON_PICKAXE));
    miner.onReleased();
    for (int slot = 0; slot < 9; slot++)
      miner.getBag().setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
    for (int slot = 0; slot < 36; slot++)
      owner.getInventory().setItem(slot, new ItemStack(Items.IRON_INGOT, 64));
    owner.getInventory().setItem(8, new ItemStack(Items.COBBLESTONE, 63));
    owner.moveTo(miner.position());
    h.getLevel().addFreshEntity(miner);
    BlockPos stone = BlockPos.containing(arena.point(8, 101, 8));
    h.assertTrue(h.getLevel().noCollision(miner), "Miner starts clear of the work block");
    h.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
    SlimeMenu menu = TestMenus.create(1, owner.getInventory(), miner);
    h.startSequence()
        .thenWaitUntil(() -> h.assertTrue(miner.needsUnloading(), "Wait for the full bag"))
        .thenExecute(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockState(stone).is(Blocks.STONE),
                  "A full bag must leave the block intact");
              h.assertTrue(
                  !menu.quickMoveStack(owner, 2).isEmpty()
                      && miner.getBag().getStackInSlot(0).getCount() == 63
                      && owner.getInventory().getItem(8).getCount() == 64,
                  "Shift-click must remove exactly one item while leaving the bag stack occupied");
            })
        .thenWaitUntil(
            () -> {
              h.assertTrue(
                  h.getLevel().getBlockState(stone).isAir(),
                  "Partial unloading must resume mining");
              h.assertTrue(
                  miner.getBag().getStackInSlot(0).getCount() == 64
                      && miner.getActivityProgress() == 1,
                  "The one mined drop must fill the freed space exactly once");
            })
        .thenExecute(
            () -> {
              miner.discard();
              arena.close();
            })
        .thenSucceed();
  }

  @GameTest(template = "empty", batch = "menus")
  public static void shrinkingCapacityResetsAnOpenTransportPage(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var carrier = h.spawn(ModEntities.TRANSPORT_SLIME.get(), new BlockPos(5, 2, 5));
    carrier.setWorking(true);
    carrier.setNoAi(true);
    carrier.tame(owner);
    owner.moveTo(carrier.position());
    SlimeStat.CAPACITY.tune(carrier, 27);
    SlimeMenu menu = TestMenus.create(1, owner.getInventory(), carrier);
    h.assertTrue(menu.clickMenuButton(owner, SlimeMenu.NEXT_PAGE), "Open the second bag page");
    SlimeStat.CAPACITY.tune(carrier, 9);
    menu.broadcastChanges();
    h.assertTrue(
        menu.data(SlimeMenu.BAG_PAGE) == 0 && menu.getSlot(0).isActive(),
        "Reducing capacity must keep the open menu on an existing page");
    owner.getInventory().setItem(9, new ItemStack(Items.EMERALD, 4));
    h.assertTrue(
        !menu.quickMoveStack(owner, 54).isEmpty()
            && carrier.getBag().getStackInSlot(0).getCount() == 4,
        "Transfers must use the remaining visible page after capacity changes");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "menus")
  public static void missingEntitiesRetainTheMenuSlotLayoutAndRejectInteraction(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    for (EntityType<?> type :
        new EntityType<?>[] {
          ModEntities.FRIENDLY_SLIME.get(), ModEntities.MINING_SLIME.get(),
          ModEntities.TRANSPORT_SLIME.get(), ModEntities.HERALD_SLIME.get(),
          ModEntities.COMBAT_SLIME.get(), ModEntities.CLEANING_SLIME.get()
        }) {
      FriendlySlime slime = (FriendlySlime) type.create(h.getLevel());
      slime.tame(owner);
      SlimeMenu expected = TestMenus.create(1, owner.getInventory(), slime);
      FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
      try {
        buffer.writeVarInt(slime.getId());
        buffer.writeVarInt(BuiltInRegistries.ENTITY_TYPE.getId(type));
        SlimeMenu missing = TestMenus.create(1, owner.getInventory(), buffer);
        h.assertTrue(
            missing.slots.size() == expected.slots.size()
                && missing.slime().getType() == type
                && missing.slime().isRemoved()
                && !missing.stillValid(owner)
                && !missing.clickMenuButton(owner, SlimeMenu.WORK)
                && missing.quickMoveStack(owner, 0).isEmpty(),
            "Missing companions must preserve their slot layout and reject stale interactions");
        missing.initializeContents(
            1, Collections.nCopies(expected.slots.size(), ItemStack.EMPTY), ItemStack.EMPTY);
        h.assertTrue(buffer.readableBytes() == 0, "Opening data must be fully consumed");
      } finally {
        buffer.release();
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "menus")
  public static void removedCompanionsRejectStaleMenuChanges(GameTestHelper h) {
    var owner = h.makeMockSurvivalPlayer();
    var miner = h.spawn(ModEntities.MINING_SLIME.get(), new BlockPos(5, 2, 5));
    miner.setWorking(true);
    miner.setNoAi(true);
    miner.tame(owner);
    owner.moveTo(miner.position());
    miner.getBag().setStackInSlot(0, new ItemStack(Items.DIAMOND, 10));
    SlimeMenu menu = TestMenus.create(1, owner.getInventory(), miner);
    ItemStack stored = FriendlySlimeItem.fromSlime(miner);
    miner.discard();
    h.assertTrue(
        !menu.rename(owner, "stale")
            && !menu.clickMenuButton(owner, SlimeMenu.WORK)
            && menu.quickMoveStack(owner, 2).isEmpty()
            && stored
                .getTag()
                .getCompound("SlimeData")
                .getCompound("MiningBag")
                .equals(miner.getBag().serializeNBT()),
        "A recovered companion must reject stale changes without altering its stored inventory");
    h.succeed();
  }
}
