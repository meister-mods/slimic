package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.block.RoyalSynthesisStationBlockEntity;
import io.github.meistermods.slimic.client.*;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.*;
import io.github.meistermods.slimic.menu.*;
import io.github.meistermods.slimic.registry.*;
import io.github.meistermods.slimic.world.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkHooks;

/** Real container packets and native hand rendering in both supported languages. */
final class RoyalSynthesisClientSmoke {
  private static int phase, ticks, language;
  private static CompletableFuture<?> pending;
  private static UUID resultId;
  private static SanctumPlacement.Candidate destination;
  private static final BlockPos STATION = new BlockPos(8, 101, 8);

  private static void check(boolean value, String message) {
    if (!value) throw new IllegalStateException(message);
  }

  private static void server(Minecraft client, Consumer<ServerPlayer> action) {
    pending =
        CompletableFuture.runAsync(
            () ->
                action.accept(
                    client
                        .getSingleplayerServer()
                        .getPlayerList()
                        .getPlayer(client.player.getUUID())),
            client.getSingleplayerServer());
  }

  private static void shot(Minecraft client, String name) {
    try {
      var directory = client.gameDirectory.toPath().resolve("royal-synthesis-audit");
      Files.createDirectories(directory);
      try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
        image.writeToFile(directory.resolve((language == 0 ? "en-" : "ko-") + name + ".png"));
      }
    } catch (java.io.IOException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static ItemStack parent(ServerPlayer owner, boolean ancient, int points) {
    var mob =
        (FriendlySlime)
            (ancient ? ModEntities.ANCIENT_MINING_SLIME.get() : ModEntities.MINING_SLIME.get())
                .create(owner.serverLevel());
    mob.tame(owner);
    mob.inheritGrowth(points);
    for (int i = 0; i < points - 2; i++)
      check(
          mob.upgradeStat(
              i < SlimeStat.HEALTH.maxLevel() ? SlimeStat.HEALTH : SlimeStat.MOVEMENT_SPEED),
          "Legal parent training");
    mob.setCustomName(Component.literal(ancient ? "Archive" : "Quarry"));
    return FriendlySlimeItem.fromSlime(mob);
  }

  static boolean tick(Minecraft client) {
    if (pending != null) {
      if (!pending.isDone()) return false;
      pending.join();
      pending = null;
    }
    if (++ticks % 100 == 0)
      System.out.println(
          "SLIMIC ROYAL CLIENT STATE: phase="
              + phase
              + " screen="
              + client.screen
              + (client.screen instanceof RoyalSynthesisScreen screen
                  ? " status=" + screen.getMenu().status().getString()
                  : ""));
    if (ticks < 12 || client.getOverlay() != null) return false;
    if (phase == 0) {
      System.out.println(
          "SLIMIC ROYAL CLIENT MODS: patchouli="
              + ModList.get().isLoaded("patchouli")
              + " jei="
              + ModList.get().isLoaded("jei"));
      client.options.guiScale().set(2);
      client.resizeDisplay();
      String locale = language == 0 ? "en_us" : "ko_kr";
      client.options.languageCode = locale;
      client.getLanguageManager().setSelected(locale);
      pending = client.reloadResourcePacks();
      phase = 1;
      ticks = 0;
      return false;
    }
    if (phase == 1) {
      server(
          client,
          owner -> {
            owner.closeContainer();
            if (resultId != null && owner.serverLevel().getEntity(resultId) != null)
              owner.serverLevel().getEntity(resultId).discard();
            owner.stopRiding();
            owner.setGameMode(GameType.CREATIVE);
            owner.getInventory().clearContent();
            owner.getInventory().selected = 0;
            for (int x = 5; x <= 13; x++)
              for (int z = 5; z <= 13; z++) {
                owner
                    .serverLevel()
                    .setBlock(new BlockPos(x, 100, z), Blocks.STONE.defaultBlockState(), 2);
                for (int y = 101; y <= 107; y++)
                  owner
                      .serverLevel()
                      .setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
              }
            owner.teleportTo(7.5, 101, 8.5);
            owner
                .serverLevel()
                .setBlockAndUpdate(
                    STATION, ModBlocks.ROYAL_SYNTHESIS_STATION.get().defaultBlockState());
            var station =
                (RoyalSynthesisStationBlockEntity) owner.serverLevel().getBlockEntity(STATION);
            station.setItem(0, parent(owner, false, 25));
            station.setItem(1, parent(owner, true, 18));
            station.setItem(2, new ItemStack(Items.SLIME_BALL, 4));
            station.setItem(3, new ItemStack(ModItems.OLD_COMMAND_FRAGMENT.get(), 2));
            var gear = new net.minecraft.nbt.ListTag();
            gear.add(new ItemStack(Items.DIAMOND_PICKAXE).save(new CompoundTag()));
            gear.add(new CompoundTag());
            station.getItem(0).getTag().getCompound("SlimeData").put("HandItems", gear);
            NetworkHooks.openScreen(owner, station, STATION);
          });
      phase = 2;
      ticks = 0;
      return false;
    }
    if (phase == 2 && client.screen instanceof RoyalSynthesisScreen screen) {
      if (!screen
          .getMenu()
          .status()
          .getString()
          .toLowerCase(Locale.ROOT)
          .contains(language == 0 ? "pickaxe" : "곡괭이")) return false;
      check(!screen.getMenu().ready(), "Equipped parent cannot confirm");
      shot(client, "equipment-refusal");
      server(
          client,
          owner -> {
            var station =
                (RoyalSynthesisStationBlockEntity) owner.serverLevel().getBlockEntity(STATION);
            station.setItem(0, parent(owner, false, 25));
          });
      phase = 3;
      ticks = 0;
      return false;
    }
    if (phase == 3 && client.screen instanceof RoyalSynthesisScreen screen) {
      if (!screen.getMenu().ready()) return false;
      check(
          screen.getMenu().normalPoints() == 25
              && screen.getMenu().ancientPoints() == 18
              && screen.getMenu().totalPoints() == 43,
          "Exact synchronized refund");
      shot(client, "refund-preview");
      client.gameMode.handleInventoryButtonClick(
          screen.getMenu().containerId, RoyalSynthesisMenu.CONFIRM);
      phase = 4;
      ticks = 0;
      return false;
    }
    if (phase == 4
        && client.player.getInventory().items.stream()
            .anyMatch(s -> s.is(ModItems.NEXT_GENERATION_MINING_SLIME.get()))) {
      server(
          client,
          owner -> {
            var item =
                owner.getInventory().items.stream()
                    .filter(s -> s.is(ModItems.NEXT_GENERATION_MINING_SLIME.get()))
                    .findFirst()
                    .orElseThrow();
            resultId = item.getTag().getCompound("SlimeData").getUUID("UUID");
            owner.closeContainer();
            owner
                .getInventory()
                .removeItemNoUpdate(owner.getInventory().findSlotMatchingItem(item));
            owner.setItemInHand(InteractionHand.MAIN_HAND, item);
            var ground = new BlockPos(11, 100, 8);
            check(
                item.useOn(
                        new UseOnContext(
                            owner,
                            InteractionHand.MAIN_HAND,
                            new BlockHitResult(
                                Vec3.atCenterOf(ground), Direction.UP, ground, false)))
                    .consumesAction(),
                "Native result deployment");
            var mob = (NextGenerationMiningSlime) owner.serverLevel().getEntity(resultId);
            check(
                mob != null && mob.isOrderedToSit() && mob.getStatPoints() == 43,
                "New companion arrives waiting with all points");
            mob.setNoAi(true);
            owner.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
            check(SlimeTunerItem.open(owner, mob).consumesAction(), "Native management opening");
          });
      phase = 5;
      ticks = 0;
      return false;
    }
    if (phase == 5 && client.screen instanceof SlimeScreen screen) {
      if (screen.getMenu().points() != 43) return false;
      shot(client, "next-generation-bag");
      client.gameMode.handleInventoryButtonClick(
          screen.getMenu().containerId, SlimeMenu.TARGET_PAGE_BUTTON);
      phase = 6;
      ticks = 0;
      return false;
    }
    if (phase == 6
        && client.screen instanceof SlimeScreen screen
        && screen.getMenu().isPolicyEditor()) {
      client.gameMode.handleInventoryButtonClick(
          screen.getMenu().containerId, SlimeMenu.POLICY_BUTTON + 2);
      phase = 7;
      ticks = 0;
      return false;
    }
    if (phase == 7
        && client.screen instanceof SlimeScreen screen
        && screen.getMenu().data(SlimeMenu.POLICY) == 2) {
      check(!screen.getMenu().isBagVisible(), "Policy overlay hides physical cargo slots");
      shot(client, "selective-policy");
      server(
          client,
          owner -> {
            var mob = (NextGenerationMiningSlime) owner.serverLevel().getEntity(resultId);
            check(
                mob.isOrderedToSit()
                    && mob.policy() == NextGenerationMiningSlime.Policy.SELECTIVE_ORE,
                "Server policy and Wait");
            owner.closeContainer();
          });
      phase = 8;
      ticks = 0;
      return false;
    }
    if (phase == 8) {
      if (ModList.get().isLoaded("jei")) {
        var runtime = JeiClientSmoke.runtime();
        if (runtime == null) return false;
        var type = io.github.meistermods.slimic.compat.jei.RoyalSynthesisCategory.TYPE;
        var recipes = runtime.getRecipeManager().createRecipeLookup(type).get().toList();
        check(recipes.size() == 1, "One enabled synthesis family in JEI");
        check(
            runtime
                .getRecipeManager()
                .createRecipeCatalystLookup(type)
                .getItemStack()
                .anyMatch(s -> s.is(ModItems.ROYAL_SYNTHESIS_STATION.get())),
            "JEI station catalyst");
        runtime
            .getRecipesGui()
            .showRecipes(runtime.getRecipeManager().getRecipeCategory(type), recipes, List.of());
      }
      phase = 9;
      ticks = 0;
      return false;
    }
    if (phase == 9) {
      if (ModList.get().isLoaded("jei")) shot(client, "jei-synthesis");
      client.setScreen(null);
      if (++language < 2) {
        phase = 0;
        ticks = 0;
        return false;
      }
      server(
          client,
          owner -> {
            var level = owner.server.getLevel(SlimicWorld.DIMENSION);
            destination = SanctumPlacement.find(level, new BlockPos(0, 90, 16000), 512);
            check(destination != null, "Native sanctum available for map rendering");
            var chunk = new net.minecraft.world.level.ChunkPos(destination.anchor());
            for (int x = chunk.x - 3; x <= chunk.x + 3; x++)
              for (int z = chunk.z - 3; z <= chunk.z + 3; z++) level.getChunk(x, z);
            owner.teleportTo(
                level,
                destination.anchor().getX() + .5,
                destination.anchor().getY() + 32,
                destination.anchor().getZ() - 56.5,
                0,
                30);
            owner.getAbilities().flying = true;
            owner.onUpdateAbilities();
            var map = new ItemStack(ModItems.ANCIENT_KINGDOM_MAP.get());
            owner.setItemInHand(InteractionHand.MAIN_HAND, map);
            owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            check(
                map.use(level, owner, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "Real unbound map starts its bounded server search");
            owner.inventoryMenu.broadcastChanges();
          });
      phase = 10;
      ticks = 0;
      return false;
    }
    if (phase == 10 && ticks > 80 && client.level.dimension().equals(SlimicWorld.DIMENSION)) {
      if (!KingdomMapItem.bound(client.player.getMainHandItem())) return false;
      check(
          client.player.getMainHandItem().is(ModItems.ANCIENT_KINGDOM_MAP.get()),
          "Native map hand synchronized");
      var map = MapItem.getSavedData(client.player.getMainHandItem(), client.level);
      check(
          map != null && map.getDecorations().iterator().hasNext(),
          "Native map has player/site decorations");
      shot(client, "bound-map-and-sanctum");
      server(
          client,
          owner -> {
            owner.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROYAL_SURVEY_COMPASS.get()));
            ModItems.ROYAL_SURVEY_COMPASS
                .get()
                .use(owner.serverLevel(), owner, InteractionHand.MAIN_HAND);
            check(
                owner.getMainHandItem().hasTag()
                    && owner.getMainHandItem().getTag().contains("LodestonePos"),
                "Survey compass records a horizontal sanctum bearing");
            owner.inventoryMenu.broadcastChanges();
          });
      phase = 11;
      ticks = 0;
      return false;
    }
    if (phase == 11 && ticks > 30) {
      check(
          client.player.getMainHandItem().is(ModItems.ROYAL_SURVEY_COMPASS.get()),
          "Survey compass hand synchronized");
      shot(client, "survey-compass-and-sanctum");
      System.out.println(
          "SLIMIC ROYAL SYNTHESIS CLIENT SMOKE PASSED: EN/KR preview, equipment refusal, packets,"
              + " 43-point result, Wait, policies, optional JEI, native map and compass");
      return true;
    }
    return false;
  }
}
