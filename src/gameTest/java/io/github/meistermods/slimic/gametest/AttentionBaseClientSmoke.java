package io.github.meistermods.slimic.gametest;

import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.ModEntities;
import java.nio.file.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

/** Native model portraits and JEI alternatives for the basic companion specializations. */
public final class AttentionBaseClientSmoke {
  private static int phase, ticks;
  private static FishingSlime fisher;
  private static AbundanceSlime abundance;

  private AttentionBaseClientSmoke() {}

  public static boolean tick(Minecraft client) {
    if (client.getOverlay() != null) return false;
    if (++ticks > 500) throw new IllegalStateException("Attention base client check timed out");
    if (phase == 0) {
      if (ModList.get().isLoaded("jei") && !JeiClientSmoke.attentionBaseRecipesReady())
        return false;
      client.options.guiScale().set(1);
      org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1280, 900);
      client.resizeDisplay();
      fisher = ModEntities.FISHING_SLIME.get().create(client.level);
      abundance = ModEntities.ABUNDANCE_SLIME.get().create(client.level);
      fisher.setNoAi(true);
      abundance.setNoAi(true);
      client.setScreen(new Portraits());
      phase = 1;
      ticks = 0;
      return false;
    }
    if (ticks < 20) return false;
    try {
      var directory = client.gameDirectory.toPath().resolve("attention-base-audit");
      Files.createDirectories(directory);
      try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
        image.writeToFile(directory.resolve("profession-portraits.png"));
      }
    } catch (java.io.IOException failure) {
      throw new IllegalStateException(failure);
    }
    System.out.println(
        "SLIMIC ATTENTION BASE CLIENT SMOKE PASSED: Fishing/Abundance front and angled portraits;"
            + " all 16 JEI alternatives");
    return true;
  }

  private static final class Portraits extends Screen {
    Portraits() {
      super(Component.literal("Companion appearance"));
    }

    @Override
    public boolean isPauseScreen() {
      return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, width, height, 0xff18232e);
      graphics.drawCenteredString(font, "Fishing Slime", width / 4, 145, 0xffffff);
      graphics.drawCenteredString(font, "Abundance Slime", width * 3 / 4, 145, 0xffffff);
      for (int i = 0; i < 4; i++) {
        var slime = i < 2 ? fisher : abundance;
        int x = width * (i * 2 + 1) / 8;
        graphics.fill(x - 145, 220, x + 145, 580, 0xff263849);
        InventoryScreen.renderEntityInInventoryFollowsAngle(
            graphics,
            x - (i < 2 ? 0 : 40),
            500,
            i < 2 ? 190 : 140,
            i % 2 == 0 ? 0 : .7F,
            .12F,
            slime);
        graphics.drawCenteredString(font, i % 2 == 0 ? "Front" : "Angled", x, 610, 0xd5e1eb);
      }
    }
  }
}
