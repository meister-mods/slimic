package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.Slimic;
import io.netty.buffer.Unpooled;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(Slimic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuidebookGameTests {
  private static final ResourceLocation BOOK = id("guide");
  private static final ResourceLocation RECIPE = id("guide_book");

  private static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, path);
  }

  private static boolean installed() {
    return ModList.get().isLoaded("patchouli");
  }

  private static CraftingRecipe recipe(GameTestHelper h) {
    return (CraftingRecipe) h.getLevel().getRecipeManager().byKey(RECIPE).orElseThrow();
  }

  private static TransientCraftingContainer grid(int width, int height) {
    return new TransientCraftingContainer(
        new AbstractContainerMenu(null, 0) {
          @Override
          public boolean stillValid(Player p) {
            return true;
          }

          @Override
          public ItemStack quickMoveStack(Player p, int slot) {
            return ItemStack.EMPTY;
          }
        },
        width,
        height);
  }

  private static void assertBook(GameTestHelper h, ItemStack stack) {
    h.assertTrue(
        ResourceLocation.fromNamespaceAndPath("patchouli", "guide_book")
                .equals(ForgeRegistries.ITEMS.getKey(stack.getItem()))
            && stack.getCount() == 1
            && stack.hasTag()
            && BOOK.toString().equals(stack.getTag().getString("patchouli:book")),
        "The recipe yields exactly one correctly identified Slimic guide");
  }

  @GameTest(template = "empty", batch = "guidebook")
  public static void guideRecipeIsConditionalOnPatchouli(GameTestHelper h) {
    h.assertTrue(
        h.getLevel().getRecipeManager().byKey(RECIPE).isPresent() == installed(),
        "The guide recipe is available exactly when Patchouli is installed");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "guidebook")
  public static void guideUnlockAdvancementIsConditionalOnPatchouli(GameTestHelper h) {
    h.assertTrue(
        (h.getLevel().getServer().getAdvancements().getAdvancement(id("recipes/misc/guide_book"))
                != null)
            == installed(),
        "The recipe-book unlock never references an absent Patchouli recipe");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "guidebook")
  public static void inventoryCraftingWorksInEveryArrangement(GameTestHelper h) {
    if (!installed()) {
      h.succeed();
      return;
    }
    var recipe = recipe(h);
    h.assertTrue(recipe.canCraftInDimensions(2, 2), "Inventory crafting is supported");
    for (int book = 0; book < 4; book++)
      for (int ball = 0; ball < 4; ball++) {
        if (book == ball) continue;
        var grid = grid(2, 2);
        grid.setItem(book, new ItemStack(Items.BOOK));
        grid.setItem(ball, new ItemStack(Items.SLIME_BALL));
        h.assertTrue(
            recipe.matches(grid, h.getLevel()), "Every two-by-two ingredient arrangement matches");
        assertBook(h, recipe.assemble(grid, h.getLevel().registryAccess()));
      }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "guidebook")
  public static void workbenchCraftingWorksInEveryArrangement(GameTestHelper h) {
    if (!installed()) {
      h.succeed();
      return;
    }
    var recipe = recipe(h);
    for (int book = 0; book < 9; book++)
      for (int ball = 0; ball < 9; ball++) {
        if (book == ball) continue;
        var grid = grid(3, 3);
        grid.setItem(book, new ItemStack(Items.BOOK));
        grid.setItem(ball, new ItemStack(Items.SLIME_BALL));
        h.assertTrue(recipe.matches(grid, h.getLevel()), "Every workbench arrangement matches");
        assertBook(h, recipe.assemble(grid, h.getLevel().registryAccess()));
      }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "guidebook")
  public static void guideRejectsMissingWrongAndExtraIngredients(GameTestHelper h) {
    if (!installed()) {
      h.succeed();
      return;
    }
    var recipe = recipe(h);
    var grid = grid(2, 2);
    grid.setItem(0, new ItemStack(Items.BOOK));
    h.assertTrue(!recipe.matches(grid, h.getLevel()), "A book alone cannot create a guide");
    grid.setItem(1, new ItemStack(Items.SLIME_BLOCK));
    h.assertTrue(
        !recipe.matches(grid, h.getLevel()), "Slime blocks cannot replace the requested slimeball");
    grid.setItem(1, new ItemStack(Items.SLIME_BALL));
    grid.setItem(2, new ItemStack(Items.DIRT));
    h.assertTrue(!recipe.matches(grid, h.getLevel()), "Extra occupied slots are rejected");
    grid.setItem(2, ItemStack.EMPTY);
    grid.setItem(0, new ItemStack(Items.WRITABLE_BOOK));
    h.assertTrue(
        !recipe.matches(grid, h.getLevel()), "A writable book does not replace the plain book");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "guidebook")
  public static void guideKeepsItsIdentityThroughItemStorage(GameTestHelper h) {
    if (!installed()) {
      h.succeed();
      return;
    }
    var grid = grid(2, 2);
    grid.setItem(0, new ItemStack(Items.BOOK, 8));
    grid.setItem(3, new ItemStack(Items.SLIME_BALL, 32));
    var recipe = recipe(h);
    var output = recipe.assemble(grid, h.getLevel().registryAccess());
    assertBook(h, ItemStack.of(output.save(new CompoundTag())));
    h.assertTrue(
        recipe.getRemainingItems(grid).stream().allMatch(ItemStack::isEmpty),
        "No ingredient returns or duplicate book outputs are generated");
    h.succeed();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @GameTest(template = "empty", batch = "guidebook")
  public static void recipeSynchronizationRetainsTheCorrectBookTag(GameTestHelper h) {
    if (!installed()) {
      h.succeed();
      return;
    }
    var recipe = recipe(h);
    var buffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
      RecipeSerializer serializer = recipe.getSerializer();
      serializer.toNetwork(buffer, recipe);
      var received = (CraftingRecipe) serializer.fromNetwork(RECIPE, buffer);
      assertBook(h, received.getResultItem(h.getLevel().registryAccess()));
    } finally {
      buffer.release();
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "guidebook")
  public static void patchouliRegistersTheGuideInTheSlimicCreativeTab(GameTestHelper h)
      throws ReflectiveOperationException {
    if (!installed()) {
      h.succeed();
      return;
    }
    var type = Class.forName("vazkii.patchouli.common.book.BookRegistry");
    var registry = type.getField("INSTANCE").get(null);
    var book = ((Map<?, ?>) type.getField("books").get(registry)).get(BOOK);
    h.assertTrue(book != null, "Patchouli loads the Slimic book definition");
    h.assertTrue(
        id("slimic").equals(book.getClass().getField("creativeTab").get(book)),
        "The guide belongs to the Slimic Creative tab");
    h.assertTrue(
        Boolean.FALSE.equals(book.getClass().getField("i18n").get(book)),
        "Localized resource folders preserve literal text, including percentage signs");
    h.succeed();
  }
}
