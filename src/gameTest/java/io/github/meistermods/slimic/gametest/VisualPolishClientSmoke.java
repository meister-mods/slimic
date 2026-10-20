package io.github.meistermods.slimic.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.meistermods.slimic.block.SlimeChestBlock;
import io.github.meistermods.slimic.block.SlimeChestBlockEntity;
import io.github.meistermods.slimic.registry.*;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.opengl.GL11;

/** Native texture animation, UV models, chest articulation, and localized material presentation. */
public final class VisualPolishClientSmoke {
  private static int phase, ticks;
  private static CompletableFuture<?> task;
  private static long portalHash;
  private static boolean finished;
  private static final BlockPos CHEST = new BlockPos(42, 161, 43);
  private static final Path OUTPUT = Path.of("screenshots/visual-polish");

  private VisualPolishClientSmoke() {}

  private static void check(boolean valid, String message) {
    if (!valid) throw new IllegalStateException(message);
  }

  private static void next() {
    phase++;
    ticks = 0;
  }

  private static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath("slimic", path);
  }

  private static void presentation(Minecraft c, boolean korean) {
    SlimicWorldClientSmoke.checkModels(c);
    check(
        ModCreativeTabs.BUILDING
            .get()
            .getDisplayName()
            .getString()
            .equals(korean ? "Slimic 블록" : "Slimic Blocks"),
        "Short block tab name");
    check(
        ModCreativeTabs.DECORATIONS
            .get()
            .getDisplayName()
            .getString()
            .equals(korean ? "Slimic 장식" : "Slimic Decorations"),
        "Short decoration tab name");
    for (var entry : ModWorldBlocks.entries())
      if (entry.category() == ModWorldBlocks.Category.DECORATION)
        check(
            new ItemStack(entry.item().get())
                    .getTooltipLines(c.player, TooltipFlag.Default.NORMAL)
                    .size()
                == 1,
            "Decoration has no generic extra tooltip: " + entry.id());
    var model =
        c.getItemRenderer()
            .getModel(new ItemStack(ModItems.SLIME_CHEST.get()), c.level, c.player, 0);
    var quads = model.getQuads(null, null, RandomSource.create());
    check(model.isGui3d() && quads.size() == 18, "Chest icon has body, lid and latch");
    check(
        quads.stream()
            .allMatch(q -> q.getSprite().contents().name().equals(id("block/slime_chest"))),
        "All chest parts share a native-layout atlas");
    var state = ModWorldBlocks.block("damp_grass_block").defaultBlockState();
    var grass = c.getBlockRenderer().getBlockModel(state);
    for (var face : Direction.Plane.HORIZONTAL) {
      var side = grass.getQuads(state, face, RandomSource.create());
      check(
          side.size() == 2
              && side.stream()
                  .anyMatch(
                      q ->
                          q.getSprite()
                                  .contents()
                                  .name()
                                  .toString()
                                  .equals("minecraft:block/grass_block_side_overlay")
                              && q.isTinted()),
          "Native grass fringe on " + face);
    }
    var portal =
        c.getModelManager()
            .getAtlas(TextureAtlas.LOCATION_BLOCKS)
            .getSprite(id("block/world/slimic_portal"))
            .contents();
    check(
        portal.width() == 32 && portal.height() == 32 && portal.getUniqueFrames().count() == 16,
        "Native atlas loads sixteen 32x32 portal frames");
  }

  private static long portalPixels(Minecraft c) {
    var atlas = c.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
    var sprite = atlas.getSprite(id("block/world/slimic_portal"));
    int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    RenderSystem.bindTexture(atlas.getId());
    int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
    int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
    try (var pixels = new NativeImage(width, height, false)) {
      pixels.downloadTexture(0, false);
      int x = Math.round(sprite.getU0() * width), y = Math.round(sprite.getV0() * height);
      long hash = 1;
      for (int dx = 0; dx < 32; dx++)
        for (int dy = 0; dy < 32; dy++) hash = hash * 31 + pixels.getPixelRGBA(x + dx, y + dy);
      return hash;
    } finally {
      RenderSystem.bindTexture(previous);
    }
  }

  private static void shot(Minecraft c, String name) {
    try {
      Files.createDirectories(OUTPUT);
      try (var image = Screenshot.takeScreenshot(c.getMainRenderTarget())) {
        image.writeToFile(OUTPUT.resolve(name + ".png"));
      }
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static boolean tick(Minecraft c) {
    if (finished) return true;
    if (task != null) {
      if (!task.isDone()) return false;
      task.join();
      task = null;
    }
    if (c.getOverlay() != null) return false;
    if (++ticks > 600) throw new IllegalStateException("Visual polish phase timeout " + phase);
    switch (phase) {
      case 0 -> {
        c.getLanguageManager().setSelected("en_us");
        c.options.languageCode = "en_us";
        task = c.reloadResourcePacks();
        next();
      }
      case 1 -> {
        presentation(c, false);
        c.options.hideGui = true;
        c.setScreen(null);
        task =
            c.getSingleplayerServer()
                .submit(
                    () -> {
                      var server = c.getSingleplayerServer();
                      var p = server.getPlayerList().getPlayer(c.player.getUUID());
                      var level = server.overworld();
                      level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                      level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                      level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
                      level.setDayTime(6000);
                      level.setWeatherParameters(6000, 0, false, false);
                      level
                          .getEntitiesOfClass(
                              net.minecraft.world.entity.Entity.class,
                              new net.minecraft.world.phys.AABB(35, 155, 30, 60, 175, 55),
                              e -> !(e instanceof net.minecraft.world.entity.player.Player))
                          .forEach(net.minecraft.world.entity.Entity::discard);
                      for (int x = 39; x <= 54; x++)
                        for (int z = 35; z <= 49; z++) {
                          level.setBlock(
                              new BlockPos(x, 160, z), Blocks.STONE.defaultBlockState(), 3);
                          for (int y = 161; y <= 168; y++)
                            level.setBlock(
                                new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                        }
                      var facings =
                          new Direction[] {
                            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
                          };
                      for (int i = 0; i < 4; i++)
                        level.setBlock(
                            CHEST.offset(i * 2, 0, 0),
                            ModBlocks.SLIME_CHEST
                                .get()
                                .defaultBlockState()
                                .setValue(SlimeChestBlock.FACING, facings[i]),
                            3);
                      for (int x = 42; x <= 48; x++)
                        level.setBlock(
                            new BlockPos(x, 161, 46),
                            ModWorldBlocks.block("damp_grass_block").defaultBlockState(),
                            3);
                      for (int x = 49; x <= 52; x++)
                        for (int y = 160; y <= 164; y++)
                          if (x == 49 || x == 52 || y == 160 || y == 164)
                            level.setBlock(
                                new BlockPos(x, y, 45), Blocks.SLIME_BLOCK.defaultBlockState(), 3);
                      p.setGameMode(GameType.CREATIVE);
                      p.getAbilities().flying = true;
                      p.onUpdateAbilities();
                      p.teleportTo(level, 45, 162.5, 36.5, 0, 20);
                      p.setPortalCooldown(200);
                      p.setItemInHand(
                          net.minecraft.world.InteractionHand.MAIN_HAND,
                          new ItemStack(Items.SLIME_BALL));
                      check(
                          io.github.meistermods.slimic.world.SlimicPortalTravel.activate(
                              p,
                              net.minecraft.world.InteractionHand.MAIN_HAND,
                              new BlockPos(50, 161, 45)),
                          "The scene portal is activated through the production path");
                      p.setItemInHand(
                          net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                      p.inventoryMenu.broadcastChanges();
                    });
        next();
      }
      case 2 -> {
        if (ticks < 30 || !c.level.getBlockState(CHEST).is(ModBlocks.SLIME_CHEST.get()))
          return false;
        check(
            c.level.getBlockState(new BlockPos(50, 161, 45)).is(ModWorldBlocks.PORTAL.get()),
            "The animated portal is visible in the world");
        portalHash = portalPixels(c);
        shot(c, "en_us-closed");
        next();
      }
      case 3 -> {
        var chest = (SlimeChestBlockEntity) c.level.getBlockEntity(CHEST);
        chest.triggerEvent(1, 1);
        if (ticks < 25) return false;
        check(chest.getOpenness(1) > .9F, "Native chest lid opens");
        check(portalHash != portalPixels(c), "The GPU portal sprite changes while the world ticks");
        shot(c, "en_us-open");
        chest.triggerEvent(1, 0);
        next();
      }
      case 4 -> {
        if (ticks < 25) return false;
        check(
            ((SlimeChestBlockEntity) c.level.getBlockEntity(CHEST)).getOpenness(1) < .1F,
            "Native chest lid closes");
        c.getLanguageManager().setSelected("ko_kr");
        c.options.languageCode = "ko_kr";
        task = c.reloadResourcePacks();
        next();
      }
      case 5 -> {
        presentation(c, true);
        if (ticks < 20) return false;
        shot(c, "ko_kr-closed");
        next();
      }
      default -> {
        try {
          Files.writeString(
              OUTPUT.resolve("result.txt"),
              "PASS: EN/KR short tabs, 32 minimal tooltips, 86 models, native chest"
                  + " atlas/body/lid/latch, four facings, grass fringe, sixteen portal frames and"
                  + " changing GPU pixels.\n");
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
        System.out.println("VISUAL POLISH CLIENT PASS");
        finished = true;
        return true;
      }
    }
    return false;
  }
}
