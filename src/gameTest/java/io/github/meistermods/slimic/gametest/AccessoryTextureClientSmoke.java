package io.github.meistermods.slimic.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.meistermods.slimic.client.*;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.SlimeArmorItem;
import io.github.meistermods.slimic.registry.ModEntities;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;

/** Native baked UV coverage and front/rear portraits of every profession and armor surface. */
public final class AccessoryTextureClientSmoke {
  private record Surface(String texture, ModelLayerLocation layer, String... parts) {}

  private static final List<Surface> SURFACES =
      List.of(
          new Surface("mining", MiningSlimeLayer.HELMET, "helmet", "lamp"),
          new Surface("transport", TransportSlimeLayer.PACK, "pack", "straps"),
          new Surface("farmer", FieldSlimeLayer.HATS, "straw", "band"),
          new Surface("lumberjack", FieldSlimeLayer.HATS, "cap"),
          new Surface("healing", HealingSlimeLayer.BAND, "band", "emblem"),
          new Surface("cleaning", ProfessionSlimeLayer.DETAILS, "cleaning_cap", "cleaning_badge"),
          new Surface("combat", ProfessionSlimeLayer.DETAILS, "combat_band", "combat_crest"),
          new Surface("royal_guard", RoyalSlimeLayer.CROWN, "crown", "relic_crest"),
          new Surface("bridle", ProfessionSlimeLayer.DETAILS, "saddle_bridle", "saddle_buckle"),
          new Surface("soggy", ProfessionSlimeLayer.DETAILS, "soggy_drops"),
          new Surface("fishing", AdvancedSlimeLayer.DETAILS, "overalls", "buttons"),
          new Surface("abundance", AdvancedSlimeLayer.DETAILS, "lyre", "strings", "laurel"),
          new Surface("tunnel", AdvancedSlimeLayer.DETAILS, "cart", "wheels"),
          new Surface("dragon", AdvancedSlimeLayer.DETAILS, "bat_left", "bat_right", "horns"),
          new Surface("commander", AdvancedSlimeLayer.DETAILS, "mustache", "baton", "baton_tip"),
          new Surface("guardian_halo", GuardianAngelSlimeLayer.HALO, "ring"),
          new Surface("saddle", SaddleSlimeLayer.SADDLE, "saddle", "stirrups"),
          new Surface("armor_leather", CombatSlimeLayer.ARMOR, "armor"),
          new Surface("armor_chainmail", CombatSlimeLayer.ARMOR, "armor"),
          new Surface("armor_iron", CombatSlimeLayer.ARMOR, "armor"),
          new Surface("armor_golden", CombatSlimeLayer.ARMOR, "armor"),
          new Surface("armor_diamond", CombatSlimeLayer.ARMOR, "armor"),
          new Surface("armor_netherite", CombatSlimeLayer.ARMOR, "armor"));

  private static final List<FriendlySlime> models = new ArrayList<>();
  private static final List<String> labels = new ArrayList<>();
  private static int ticks, page;
  private static boolean ready, viewportRequested;
  private static int faces, samples, packPhase, reloadCycles;
  private static final int RELOAD_CYCLES =
      Math.max(1, Math.min(100, Integer.getInteger("slimic.textureReloadCycles", 1)));
  private static List<String> previousPacks;
  private static Path probePack;
  private static String probeId;
  private static CompletableFuture<Void> packReload;

  private AccessoryTextureClientSmoke() {}

  private static void validate(Minecraft client) {
    for (var surface : SURFACES) {
      var texture = SlimeTextures.accessory(surface.texture);
      try (var stream = client.getResourceManager().open(texture);
          var image = NativeImage.read(stream)) {
        if (image.getWidth() != 256 || image.getHeight() != 256)
          throw new IllegalStateException("Invalid accessory atlas size: " + texture);
        var root = client.getEntityModels().bakeLayer(surface.layer);
        var checker = new Coverage(texture, image);
        for (var part : surface.parts)
          root.getChild(part).render(new PoseStack(), checker, 15728880, 0);
        if (checker.vertices != 0) throw new IllegalStateException("Incomplete quad: " + texture);
      } catch (java.io.IOException e) {
        throw new IllegalStateException("Missing accessory texture " + texture, e);
      }
    }
    for (var gui :
        Map.of(
                "slime",
                new int[] {368, 240},
                "creative_tuner",
                new int[] {360, 230},
                "commander",
                new int[] {310, 235},
                "slots",
                new int[] {36, 18})
            .entrySet())
      try (var stream = client.getResourceManager().open(SlimeTextures.gui(gui.getKey()));
          var image = NativeImage.read(stream)) {
        if (image.getWidth() != gui.getValue()[0] || image.getHeight() != gui.getValue()[1])
          throw new IllegalStateException("Invalid GUI dimensions " + gui.getKey());
      } catch (java.io.IOException e) {
        throw new IllegalStateException(e);
      }
    System.out.println(
        "SLIMIC ACCESSORY UV CHECK PASSED: "
            + SURFACES.size()
            + " atlases, "
            + faces
            + " native faces, "
            + samples
            + " opaque surface samples; 4 GUI sheets");
  }

  private static void prepare(Minecraft client) {
    validate(client);
    for (var value : ModEntities.companionTypes()) {
      var slime = (FriendlySlime) value.get().create(client.level);
      ColonyTestFixtures.appearance(slime);
      slime.setNoAi(true);
      if (slime instanceof MiningSlime miner) {
        miner.setTool(0, new ItemStack(Items.IRON_PICKAXE));
        miner.setTool(1, new ItemStack(Items.IRON_SHOVEL));
      }
      if (slime instanceof FieldSlime field)
        field.setTool(
            new ItemStack(slime instanceof FarmingSlime ? Items.IRON_HOE : Items.IRON_AXE));
      if (slime instanceof FishingSlime fisher) fisher.setRod(new ItemStack(Items.FISHING_ROD));
      if (slime instanceof SaddleSlime) {
        slime.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SADDLE));
        slime.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ELYTRA));
      }
      models.add(slime);
      labels.add(value.getId().getPath());
    }
    if (models.size() != 29) throw new IllegalStateException("Missing companion/form portrait");
    Item[] materials = {
      Items.LEATHER_CHESTPLATE,
      Items.CHAINMAIL_CHESTPLATE,
      Items.IRON_CHESTPLATE,
      Items.GOLDEN_CHESTPLATE,
      Items.DIAMOND_CHESTPLATE,
      Items.NETHERITE_CHESTPLATE,
      Items.LEATHER_CHESTPLATE,
      Items.LEATHER_CHESTPLATE
    };
    for (int i = 0; i < materials.length; i++) {
      var chest = new ItemStack(materials[i]);
      if (i >= 6)
        ((DyeableLeatherItem) chest.getItem()).setColor(chest, i == 6 ? 0x346ad9 : 0xdd3939);
      var armor = SlimeArmorItem.fromChestplate(chest);
      if (i == 7) armor.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 1);
      var slime = ModEntities.COMBAT_SLIME.get().create(client.level);
      slime.setNoAi(true);
      slime.setItemSlot(EquipmentSlot.CHEST, armor);
      if (i == 6) {
        var data = slime.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        data.putInt("SlimeDyeColor", 11);
        slime.load(data);
      }
      models.add(slime);
      labels.add(
          i >= 6
              ? (i == 6 ? "blue leather + dyed body" : "red leather + glint")
              : materials[i].toString());
    }
    client.setScreen(new Portraits());
  }

  public static boolean tick(Minecraft client) {
    if (client.getOverlay() != null) return false;
    if (!ready) {
      if (!viewportRequested) {
        client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        client.options.forceUnicodeFont().set(false);
        client.options.guiScale().set(1);
        org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1280, 960);
        viewportRequested = true;
        return false;
      }
      client.resizeDisplay();
      client.getWindow().setGuiScale(1);
      if (client.getWindow().getGuiScaledWidth() < 1260
          || client.getWindow().getGuiScaledHeight() < 920) {
        if (++ticks > 100)
          throw new IllegalStateException(
              "Accessory portrait viewport: "
                  + client.getWindow().getGuiScaledWidth()
                  + "x"
                  + client.getWindow().getGuiScaledHeight());
        return false;
      }
      prepare(client);
      ready = true;
    }
    int portraitPages = ((models.size() + 5) / 6) * 2;
    if (page >= portraitPages) return resourcePacks(client);
    if (++ticks < 15) return false;
    try {
      var directory = client.gameDirectory.toPath().resolve("accessory-textures-audit");
      Files.createDirectories(directory);
      try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
        image.writeToFile(directory.resolve("portraits-" + page + ".png"));
      }
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
    ticks = 0;
    if (++page < portraitPages) return false;
    client.setScreen(null);
    System.out.println(
        "SLIMIC ACCESSORY TEXTURE CLIENT SMOKE PASSED: all 29 companion forms and 8 armor "
            + "variants from front and rear; leather dye, body dye, glint, tools and wings");
    return false;
  }

  private static boolean resourcePacks(Minecraft client) {
    try {
      var texture = SlimeTextures.accessory("mining");
      var repository = client.getResourcePackRepository();
      if (packPhase == 0) {
        previousPacks = new ArrayList<>(repository.getSelectedIds());
        probePack =
            client
                .gameDirectory
                .toPath()
                .resolve("resourcepacks")
                .resolve("slimic-texture-probe-" + UUID.randomUUID());
        var path = probePack.resolve("assets/slimic/textures/entity/accessories/mining.png");
        Files.createDirectories(path.getParent());
        Files.writeString(
            probePack.resolve("pack.mcmeta"),
            "{\"pack\":{\"pack_format\":15,\"description\":\"Accessory reload verification\"}}");
        // Synthetic high-resolution fixture verifies both pack precedence and GPU replacement.
        try (var image = new NativeImage(512, 512, false)) {
          image.fillRect(0, 0, 512, 512, 0xff22cc77);
          image.writeToFile(path);
        }
        repository.reload();
        probeId = "file/" + probePack.getFileName();
        var selected = new ArrayList<>(previousPacks);
        selected.add(probeId);
        repository.setSelected(selected);
        packReload = client.reloadResourcePacks();
        packPhase = 1;
        return false;
      }
      if (!packReload.isDone()) return false;
      packReload.join();
      var resource = client.getResourceManager().getResourceOrThrow(texture);
      if (packPhase == 1) {
        if (!resource.sourcePackId().equals(probeId))
          throw new IllegalStateException("Accessory override not selected");
        client.getTextureManager().bindForSetup(texture);
        if (org.lwjgl.opengl.GL11.glGetTexLevelParameteri(
                org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH)
            != 512)
          throw new IllegalStateException(
              "Accessory GPU texture did not reload at replacement resolution");
        repository.setSelected(previousPacks);
        packReload = client.reloadResourcePacks();
        packPhase = 2;
        return false;
      }
      if (resource.sourcePackId().equals(probeId))
        throw new IllegalStateException("Accessory override not removed");
      client.getTextureManager().bindForSetup(texture);
      if (org.lwjgl.opengl.GL11.glGetTexLevelParameteri(
              org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH)
          != 256) throw new IllegalStateException("Original accessory GPU texture not restored");
      try (var paths = Files.walk(probePack)) {
        for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
      }
      repository.reload();
      reloadCycles++;
      if (reloadCycles % 10 == 0 || reloadCycles == RELOAD_CYCLES)
        System.out.println(
            "SLIMIC ACCESSORY RELOAD CYCLE "
                + reloadCycles
                + "/"
                + RELOAD_CYCLES
                + "; heap MiB="
                + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
                    / 1048576);
      if (reloadCycles < RELOAD_CYCLES) {
        packPhase = 0;
        return false;
      }
      System.out.println(
          "SLIMIC ACCESSORY RESOURCE PACK SMOKE PASSED: 512px replacement selected, GPU texture"
              + " reloaded, 256px original restored; selected packs retained; cycles="
              + reloadCycles);
      return true;
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final class Portraits extends Screen {
    Portraits() {
      super(Component.literal("Companion texture surfaces"));
    }

    @Override
    public boolean isPauseScreen() {
      return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
      g.fill(0, 0, width, height, 0xff18232e);
      g.drawCenteredString(
          font,
          "Companion textures / " + (page % 2 == 0 ? "Front" : "Rear") + " / " + (page / 2 + 1),
          width / 2,
          22,
          0xffffff);
      for (int cell = 0; cell < 6; cell++) {
        int i = page / 2 * 6 + cell;
        if (i >= models.size()) continue;
        int x = width / 2 - 420 + cell % 3 * 420, y = 450 + cell / 3 * 435;
        g.fill(x - 190, y - 365, x + 190, y + 25, 0xff263849);
        var slime = models.get(i);
        InventoryScreen.renderEntityInInventoryFollowsAngle(
            g,
            x - (slime instanceof AbundanceSlime ? 25 : 0),
            y - 30,
            slime instanceof GuardianAngelSlime
                    || slime instanceof SaddleSlime
                    || slime instanceof DragonSlime
                ? 85
                : 115,
            page % 2 == 0 ? .4F : 9.4F,
            .16F,
            slime);
        g.drawCenteredString(font, labels.get(i), x, y + 10, 0xffffff);
      }
    }
  }

  /** Tests the UVs emitted by Minecraft's baked model, including thin fractional-sized cubes. */
  private static final class Coverage implements VertexConsumer {
    private final ResourceLocation texture;
    private final NativeImage image;
    private final float[] us = new float[4], vs = new float[4];
    private int vertices;
    private float u, v;

    Coverage(ResourceLocation texture, NativeImage image) {
      this.texture = texture;
      this.image = image;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
      return this;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
      return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
      this.u = u;
      this.v = v;
      return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
      return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
      return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
      return this;
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {}

    @Override
    public void unsetDefaultColor() {}

    @Override
    public void endVertex() {
      us[vertices] = u;
      vs[vertices++] = v;
      if (vertices != 4) return;
      float minU = us[0], maxU = us[0], minV = vs[0], maxV = vs[0];
      for (int i = 1; i < 4; i++) {
        minU = Math.min(minU, us[i]);
        maxU = Math.max(maxU, us[i]);
        minV = Math.min(minV, vs[i]);
        maxV = Math.max(maxV, vs[i]);
      }
      if (minU < 0 || minV < 0 || maxU > 1 || maxV > 1)
        throw new IllegalStateException("UV outside atlas " + texture);
      for (int y = 0; y < 9; y++)
        for (int x = 0; x < 9; x++) {
          int px = (int) ((minU + (maxU - minU) * (x + .5F) / 9) * image.getWidth());
          int py = (int) ((minV + (maxV - minV) * (y + .5F) / 9) * image.getHeight());
          if ((image.getPixelRGBA(px, py) >>> 24) != 255)
            throw new IllegalStateException(
                "Transparent accessory face " + texture + " at " + px + "," + py);
          samples++;
        }
      faces++;
      vertices = 0;
    }
  }
}
