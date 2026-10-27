package io.github.meistermods.slimic.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.meistermods.slimic.block.ColonyBondingStationBlockEntity;
import io.github.meistermods.slimic.client.ColonyBondingScreen;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.item.FriendlySlimeItem;
import io.github.meistermods.slimic.menu.ColonyBondingMenu;
import io.github.meistermods.slimic.registry.*;
import io.github.meistermods.slimic.world.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;

/** Real station packets, stored deployment, unbinding and localized native colony rendering. */
public final class ColonyClientSmoke {
  private static final List<String> LANGUAGES = List.of("en_us", "ko_kr", "ja_jp", "es_es");
  private static boolean jeiLanguagesStarted;
  private static int phase, ticks, locale, family, warning;
  private static CompletableFuture<?> action;
  private static volatile int representative, bossEntity;
  private static volatile UUID[] parents;
  private static volatile UUID colonyId;
  private static volatile Vec3 warnedDashDirection;
  private static volatile Vec3 warnedAttackOrigin;
  private static BlockPos origin;
  private static final List<Mob> portraits = new ArrayList<>();
  private static final List<String> portraitNames = new ArrayList<>();
  private static boolean finished;
  private static int packPhase;
  private static List<String> previousPacks;
  private static Path probePack;
  private static String probeId;

  private ColonyClientSmoke() {}

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private static ServerPlayer actor(Minecraft client) {
    return client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
  }

  private static ServerLevel level(Minecraft client) {
    return actor(client).serverLevel();
  }

  private static ColonyBondingStationBlockEntity station(Minecraft client) {
    return (ColonyBondingStationBlockEntity) level(client).getBlockEntity(origin.offset(0, 0, 3));
  }

  private static ItemStack stored(ServerPlayer player) {
    return player.getInventory().items.stream()
        .filter(ColonyBonding::bound)
        .findFirst()
        .orElseThrow();
  }

  private static void shot(Minecraft client, String name) {
    try {
      Path out = client.gameDirectory.toPath().resolve("colony-audit");
      Files.createDirectories(out);
      try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
        image.writeToFile(out.resolve(LANGUAGES.get(locale) + "-" + name + ".png"));
      }
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void inputs(Minecraft client) {
    var player = actor(client);
    var station = station(client);
    parents = new UUID[2];
    for (int i = 0; i < 2; i++) {
      BagSlime member =
          family == 0
              ? ModEntities.TRANSPORT_SLIME.get().create(level(client))
              : ModEntities.COMBAT_SLIME.get().create(level(client));
      member.tame(player);
      member.inheritGrowth(3 + i);
      member.setTunedVitals(5 + i * 2, 31 + i * 11, member.getStatPoints());
      member.setCustomName(
          Component.literal((family == 0 ? "Cargo " : "Blade ") + (i == 0 ? "A" : "B")));
      member.setDyeColorId((i == 0 ? DyeColor.LIME : DyeColor.CYAN).getId());
      member
          .getBag()
          .setStackInSlot(0, new ItemStack(i == 0 ? Items.DIAMOND : Items.GOLD_INGOT, 23 + i * 18));
      if (member instanceof CombatSlime fighter) {
        var sword = new ItemStack(i == 0 ? Items.IRON_SWORD : Items.DIAMOND_SWORD);
        sword.setDamageValue(17 + i);
        fighter.setEquipment(0, sword);
        fighter.setEquipment(1, new ItemStack(Items.SHIELD));
      }
      parents[i] = member.getUUID();
      station.setItem(i, FriendlySlimeItem.fromSlime(member));
    }
    NetworkHooks.openScreen(player, station, station.getBlockPos());
  }

  private static void gallery(Minecraft client) {
    portraits.clear();
    portraitNames.clear();
    for (var type :
        List.of(
            ModEntities.AIMLESS_COLONY,
            ModEntities.ATTACK_COLONY_FRAGMENT,
            ModEntities.COHESION_COLONY_FRAGMENT,
            ModEntities.RETURNING_COLONY_FRAGMENT)) {
      Mob model = (Mob) type.get().create(client.level);
      model.setNoAi(true);
      portraits.add(model);
      portraitNames.add(type.getId().getPath());
    }
    for (var type : List.of(ModEntities.BOUND_TRANSPORT_SLIME, ModEntities.BOUND_COMBAT_SLIME)) {
      var model = (FriendlySlime) type.get().create(client.level);
      ColonyTestFixtures.appearance(model);
      if (model instanceof BoundCombatSlime combat) {
        var before = combat.members().save().copy();
        var shared = combat.memberEquipment(0, EquipmentSlot.CHEST).copy();
        require(!shared.isEmpty(), "Representative armor exists");
        combat.setRenderingSecondMember(true);
        require(
            !ItemStack.isSameItemSameTags(shared, combat.getItemBySlot(EquipmentSlot.CHEST)),
            "Second original armor stays distinct");
        require(
            ItemStack.matches(shared, combat.memberEquipment(0, EquipmentSlot.CHEST)),
            "Reading the second appearance preserves first-member armor");
        require(
            combat.getMainHandItem().is(Items.GOLDEN_SWORD),
            "Second hand equipment remains distinct");
        combat.setRenderingSecondMember(false);
        require(
            ItemStack.matches(shared, combat.memberEquipment(0, EquipmentSlot.CHEST))
                && before.equals(combat.members().save()),
            "Rendering does not move or duplicate owned armor");
      }
      model.setNoAi(true);
      portraits.add(model);
      portraitNames.add(type.getId().getPath());
    }
    client.setScreen(new Gallery());
  }

  private static void verifyAssets(Minecraft client) {
    for (String name :
        List.of(
            "colony_heart",
            "cohesion_shard",
            "assembly_clue",
            "last_rally_record",
            "endless_assembly_record",
            "bound_transport_slime_crest",
            "bound_combat_slime_crest")) verifyImage(client, "item/" + name, 16, 16);
    verifyImage(client, "block/colony_bonding_top", 16, 16);
    verifyImage(client, "entity/aimless_colony", 64, 64);
    for (String role : List.of("attack", "cohesion", "returning"))
      verifyImage(client, "entity/colony_fragment_" + role, 64, 32);
  }

  private static void verifyImage(Minecraft client, String path, int w, int h) {
    var resource = ResourceLocation.fromNamespaceAndPath("slimic", "textures/" + path + ".png");
    try (var input = client.getResourceManager().open(resource);
        var image = NativeImage.read(input)) {
      require(
          image.getWidth() == w && image.getHeight() == h, "Native atlas dimensions: " + resource);
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static boolean tick(Minecraft client) {
    if (finished) return true;
    try {
      if (phase == 0) {
        client.options.guiScale().set(3);
        client.options.forceUnicodeFont().set(false);
        client.options.languageCode = LANGUAGES.get(locale);
        client.getLanguageManager().setSelected(LANGUAGES.get(locale));
        action = client.reloadResourcePacks();
        phase = 1;
        return false;
      }
      if (!action.isDone()) return false;
      action.join();
      if (client.getOverlay() != null) return false;
      if (phase == 1) {
        verifyAssets(client);
        action =
            client
                .getSingleplayerServer()
                .submit(
                    () -> {
                      var player = actor(client);
                      var level = player.serverLevel();
                      origin = new BlockPos(100008, 101, 100008);
                      for (int x = -20; x <= 20; x++)
                        for (int z = -20; z <= 20; z++) {
                          level.setBlock(
                              origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
                          for (int y = 0; y < 9; y++)
                            level.setBlock(
                                origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                        }
                      player.setGameMode(GameType.CREATIVE);
                      player.getInventory().clearContent();
                      player.teleportTo(origin.getX() - 5 + .5, origin.getY(), origin.getZ() + .5);
                      level.setBlockAndUpdate(
                          origin.offset(0, 0, 3),
                          ModBlocks.COLONY_BONDING_STATION.get().defaultBlockState());
                      inputs(client);
                    });
        phase = 2;
        ticks = 0;
        return false;
      }
      if (phase == 2) {
        if (!(client.screen instanceof ColonyBondingScreen)
            || !(client.player.containerMenu instanceof ColonyBondingMenu menu)
            || menu.problem() != ColonyBonding.Problem.READY) return false;
        if (++ticks < 8) return false;
        require(
            menu.previewValue(2) == 32 && menu.previewValue(3) == (family == 0 ? 36 : 18),
            "Actual combined preview through numeric packets");
        LocalizedWidgetChecks.record(
            client,
            client.screen,
            "colony/" + LANGUAGES.get(locale) + "/" + family + "/bind",
            320,
            240);
        shot(client, family == 0 ? "transport-ready" : "combat-ready");
        client.gameMode.handleInventoryButtonClick(menu.containerId, ColonyBondingMenu.BIND);
        phase = 3;
        ticks = 0;
        return false;
      }
      if (phase == 3) {
        if (++ticks < 12) return false;
        action =
            client
                .getSingleplayerServer()
                .submit(
                    () -> {
                      var player = actor(client);
                      ItemStack item = stored(player);
                      colonyId = item.getTag().getCompound("SlimeData").getUUID("UUID");
                      player.setItemInHand(InteractionHand.MAIN_HAND, item);
                      BlockPos floor = origin.offset(5, -1, 0);
                      var result =
                          item.getItem()
                              .useOn(
                                  new UseOnContext(
                                      player,
                                      InteractionHand.MAIN_HAND,
                                      new BlockHitResult(
                                          Vec3.atCenterOf(floor).add(0, .5, 0),
                                          Direction.UP,
                                          floor,
                                          false)));
                      require(
                          result.consumesAction(), "Stored colony deploys through native item use");
                      var body = (FriendlySlime) level(client).getEntity(colonyId);
                      representative = body.getId();
                      require(
                          body.isOrderedToSit()
                              && !body.isWorking()
                              && Math.abs(body.getHealth() - 12) < .01,
                          "Deployment starts in Wait with actual remaining life");
                    });
        phase = 4;
        ticks = 0;
        return false;
      }
      if (phase == 4) {
        if (++ticks < 15 || client.level.getEntity(representative) == null) return false;
        client.player.closeContainer();
        gallery(client);
        phase = 5;
        ticks = 0;
        return false;
      }
      if (phase == 5) {
        if (++ticks < 8) return false;
        shot(client, family == 0 ? "colony-gallery" : "colony-gallery-combat");
        client.setScreen(null);
        action =
            client
                .getSingleplayerServer()
                .submit(
                    () -> {
                      var player = actor(client);
                      var body = (FriendlySlime) level(client).getEntity(colonyId);
                      if (family == 1) {
                        body.getMainHandItem().setDamageValue(28);
                        body.hurt(level(client).damageSources().magic(), 4);
                      }
                      require(
                          body.capture(player).consumesAction(),
                          "Controlled colony capture checkpoints its stored authority");
                      ItemStack item = stored(player).copy();
                      for (int i = 0; i < player.getInventory().items.size(); i++)
                        if (ColonyBonding.bound(player.getInventory().getItem(i)))
                          player.getInventory().setItem(i, ItemStack.EMPTY);
                      station(client).setItem(0, item);
                      NetworkHooks.openScreen(
                          player, station(client), station(client).getBlockPos());
                    });
        phase = 6;
        ticks = 0;
        return false;
      }
      if (phase == 6) {
        if (!(client.screen instanceof ColonyBondingScreen)
            || !(client.player.containerMenu instanceof ColonyBondingMenu menu)
            || menu.problem() != ColonyBonding.Problem.READY_UNBIND) return false;
        if (++ticks < 8) return false;
        LocalizedWidgetChecks.record(
            client,
            client.screen,
            "colony/" + LANGUAGES.get(locale) + "/" + family + "/unbind",
            320,
            240);
        shot(client, family == 0 ? "transport-unbind" : "combat-unbind");
        client.gameMode.handleInventoryButtonClick(menu.containerId, ColonyBondingMenu.UNBIND);
        phase = 7;
        ticks = 0;
        return false;
      }
      if (phase == 7) {
        if (++ticks < 12) return false;
        action =
            client
                .getSingleplayerServer()
                .submit(
                    () -> {
                      var first = (BagSlime) level(client).getEntity(parents[0]);
                      var second = (BagSlime) level(client).getEntity(parents[1]);
                      require(
                          first != null
                              && second != null
                              && first.isOrderedToSit()
                              && second.isOrderedToSit(),
                          "Original identities return in Wait");
                      require(
                          Math.abs(first.getHealth() + second.getHealth() - (family == 0 ? 12 : 8))
                              < .01,
                          "Unbinding keeps actual remaining life");
                      require(
                          first.getBag().getStackInSlot(0).getCount() == 23
                              && second.getBag().getStackInSlot(0).getCount() == 41,
                          "Exact native cargo restored");
                      if (family == 1)
                        require(
                            first.getMainHandItem().getDamageValue() == 28
                                && second.getMainHandItem().getDamageValue() == 18,
                            "Real first weapon wear and unchanged second weapon");
                      first.discard();
                      second.discard();
                    });
        phase = 8;
        ticks = 0;
        return false;
      }
      if (phase == 8) {
        client.player.closeContainer();
        if (++family < 2) {
          action = client.getSingleplayerServer().submit(() -> inputs(client));
          phase = 2;
          ticks = 0;
          return false;
        }
        family = 0;
        if (++locale < LANGUAGES.size()) {
          client.options.languageCode = LANGUAGES.get(locale);
          client.getLanguageManager().setSelected(LANGUAGES.get(locale));
          action = client.reloadResourcePacks();
          phase = 9;
          return false;
        }
        locale = LANGUAGES.size() - 1;
        if (Boolean.getBoolean("slimic.colonyLanguageOnly")) {
          phase = 12;
          ticks = 0;
          return false;
        }
        action =
            client
                .getSingleplayerServer()
                .submit(
                    () -> {
                      var player = actor(client);
                      level(client)
                          .getServer()
                          .setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
                      player.setGameMode(GameType.SURVIVAL);
                      player.setHealth(20);
                      var data = AssemblySites.get(level(client));
                      var site = data.register(level(client), origin.offset(8, 0, 0));
                      require(data.awaken(level(client), site), "Client encounter reserved");
                      site.state = AssemblySites.State.ACTIVE;
                      var boss = ModEntities.AIMLESS_COLONY.get().create(level(client));
                      boss.setUUID(site.boss);
                      boss.bind(site);
                      boss.moveTo(site.anchor.getCenter());
                      site.body = boss.saveWithoutId(new CompoundTag());
                      data.flush(level(client));
                      require(level(client).addFreshEntity(boss), "Native boss admitted");
                      bossEntity = boss.getId();
                    });
        phase = 10;
        ticks = 0;
        return false;
      }
      if (phase == 9) {
        action = client.getSingleplayerServer().submit(() -> inputs(client));
        phase = 2;
        ticks = 0;
        return false;
      }
      if (phase == 10) {
        client.setScreen(null);
        client.options.particles().set(ParticleStatus.MINIMAL);
        if (++ticks < 12 || !(client.level.getEntity(bossEntity) instanceof AimlessColony))
          return false;
        Vec3 look =
            client.level.getEntity(bossEntity).position().subtract(client.player.position());
        client.player.setYRot((float) (Math.toDegrees(Math.atan2(look.z, look.x)) - 90));
        client.player.setXRot(8);
        action =
            client
                .getSingleplayerServer()
                .submit(
                    () -> {
                      var boss = (AimlessColony) level(client).getEntity(bossEntity);
                      boss.setHealth(
                          boss.getMaxHealth() * (warning == 0 ? 1 : warning == 1 ? .6F : .3F));
                      if (warning >= 2) boss.setTarget(actor(client));
                      try {
                        var method =
                            AimlessColony.class.getDeclaredMethod(
                                "begin", AimlessColony.Pattern.class);
                        method.setAccessible(true);
                        method.invoke(
                            boss,
                            switch (warning) {
                              case 0 -> AimlessColony.Pattern.SLAM;
                              case 1 -> AimlessColony.Pattern.RALLY;
                              case 2 -> AimlessColony.Pattern.DASH;
                              case 3 -> AimlessColony.Pattern.RING;
                              case 4 -> AimlessColony.Pattern.CROSS;
                              case 5 -> AimlessColony.Pattern.SWEEP;
                              default -> AimlessColony.Pattern.ERUPTION;
                            });
                        if (warning == 2 || warning >= 5) {
                          warnedAttackOrigin = boss.attackOrigin();
                          warnedDashDirection = boss.dashDirection();
                          Vec3 shove =
                              new Vec3(-warnedDashDirection.z, 0, warnedDashDirection.x).scale(2);
                          boss.setPos(boss.position().add(shove));
                          boss.hasImpulse = true;
                        }
                      } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                      }
                    });
        phase = 11;
        ticks = 0;
        return false;
      }
      if (phase == 11) {
        if (++ticks < 8) return false;
        var boss = (AimlessColony) client.level.getEntity(bossEntity);
        require(
            boss != null
                && boss.phase() == Math.min(3, warning + 1)
                && boss.pattern() != AimlessColony.Pattern.IDLE,
            "Real phase and telegraph synchronization with minimal particles");
        if (warning == 2 || warning >= 5) {
          require(
              warnedDashDirection != null
                  && boss.dashDirection().distanceToSqr(warnedDashDirection) < 1.0e-10,
              "Native tracking packets preserve the announced heading after a sideways shove");
          require(
              boss.dashRemainingDistance() >= 0 && boss.dashHalfWidth() == boss.getBbWidth() * .5,
              "The native telegraph uses the shared footprint and remaining travel");
        }
        if (warning >= 5) {
          require(
              warnedAttackOrigin != null
                  && boss.attackOrigin().distanceToSqr(warnedAttackOrigin) < 1.0e-10,
              "Native tracking preserves the marked origin after a shove");
          require(
              warning == 5
                  ? boss.pattern() == AimlessColony.Pattern.SWEEP
                  : boss.pattern() == AimlessColony.Pattern.ERUPTION
                      && boss.eruptionCenters().size() == 5,
              "New attack warnings reach a real client");
        }
        shot(client, "boss-phase-" + (warning + 1) + "-minimal-particles");
        if (++warning < 7) {
          phase = 10;
          ticks = 0;
          return false;
        }
        action =
            client
                .getSingleplayerServer()
                .submit(
                    () -> {
                      var current = (AimlessColony) level(client).getEntity(bossEntity);
                      AssemblySites.get(level(client)).reset(level(client), current.site());
                      current.discard();
                      actor(client).setGameMode(GameType.CREATIVE);
                    });
        phase = 12;
        return false;
      }
      if (phase == 12) {
        if (!jeiLanguagesStarted) {
          jeiLanguagesStarted = true;
          locale = 0;
          client.options.languageCode = LANGUAGES.get(locale);
          client.getLanguageManager().setSelected(LANGUAGES.get(locale));
          action = client.reloadResourcePacks();
          return false;
        }
        if (net.minecraftforge.fml.ModList.get().isLoaded("jei")) {
          var runtime = JeiClientSmoke.runtime();
          if (runtime == null) return false;
          require(
              client.font.split(Component.translatable("jei.slimic.bonding.controls"), 180).size()
                      * client.font.lineHeight
                  <= 27,
              "JEI controls fit before the next paragraph");
          require(
              client.font.split(Component.translatable("jei.slimic.bonding.members"), 204).size()
                      * client.font.lineHeight
                  <= 30,
              "JEI member summary fits");
          require(
              client.font.split(Component.translatable("jei.slimic.bonding.safety"), 204).size()
                      * client.font.lineHeight
                  <= 33,
              "JEI safety summary fits its category");
          var type = io.github.meistermods.slimic.compat.jei.ColonyBondingCategory.TYPE;
          var recipes = runtime.getRecipeManager().createRecipeLookup(type).get().toList();
          require(recipes.size() == 4, "Both professions expose binding and unbinding in JEI");
          require(
              runtime
                  .getRecipeManager()
                  .createRecipeCatalystLookup(type)
                  .getItemStack()
                  .anyMatch(i -> i.is(ModItems.COLONY_BONDING_STATION.get())),
              "Reusable bonding station is the JEI catalyst");
          runtime
              .getRecipesGui()
              .showRecipes(runtime.getRecipeManager().getRecipeCategory(type), recipes, List.of());
        }
        phase = 13;
        ticks = 0;
        return false;
      }
      if (phase == 13) {
        if (++ticks < 12) return false;
        if (net.minecraftforge.fml.ModList.get().isLoaded("jei")) {
          require(
              client.screen != null && client.screen.getClass().getName().contains("RecipesGui"),
              "The actual localized JEI recipe screen is displayed");
          shot(client, "jei-bonding");
        }
        client.setScreen(null);
        if (++locale < LANGUAGES.size()) {
          client.options.languageCode = LANGUAGES.get(locale);
          client.getLanguageManager().setSelected(LANGUAGES.get(locale));
          action = client.reloadResourcePacks();
          phase = 12;
          ticks = 0;
          return false;
        }
        locale = LANGUAGES.size() - 1;
        LocalizedWidgetChecks.finish(client, "colony");
        phase = 14;
        return false;
      }
      if (phase == 14) {
        if (!Boolean.getBoolean("slimic.colonyLanguageOnly") && !resourcePacks(client))
          return false;
        System.out.println(
            "SLIMIC COLONY CLIENT SMOKE PASSED: native bind/deploy/capture/unbind packets for both"
                + " professions, exact cargo/vitals/weapon wear, EN/KR/JA/ES station/gallery/JEI;"
                + " encounter and resource-pack checks="
                + !Boolean.getBoolean("slimic.colonyLanguageOnly"));
        finished = true;
        return true;
      }
      return false;
    } catch (Exception failure) {
      throw new IllegalStateException(
          "Colony client phase=" + phase + " family=" + family, failure);
    }
  }

  private static boolean resourcePacks(Minecraft client) {
    try {
      var texture =
          ResourceLocation.fromNamespaceAndPath("slimic", "textures/entity/aimless_colony.png");
      var repository = client.getResourcePackRepository();
      if (packPhase == 0) {
        previousPacks = new ArrayList<>(repository.getSelectedIds());
        probePack =
            client
                .gameDirectory
                .toPath()
                .resolve("resourcepacks")
                .resolve("slimic-colony-texture-probe-" + UUID.randomUUID());
        var path = probePack.resolve("assets/slimic/textures/entity/aimless_colony.png");
        Files.createDirectories(path.getParent());
        Files.writeString(
            probePack.resolve("pack.mcmeta"),
            "{\"pack\":{\"pack_format\":15,\"description\":\"Colony reload verification\"}}");
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
        action = client.reloadResourcePacks();
        packPhase = 1;
        return false;
      }
      if (!action.isDone()) return false;
      action.join();
      var resource = client.getResourceManager().getResourceOrThrow(texture);
      if (packPhase == 1) {
        if (!resource.sourcePackId().equals(probeId))
          throw new IllegalStateException("Colony override not selected");
        client.getTextureManager().bindForSetup(texture);
        if (org.lwjgl.opengl.GL11.glGetTexLevelParameteri(
                org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH)
            != 512)
          throw new IllegalStateException(
              "Colony GPU texture did not reload at replacement resolution");
        repository.setSelected(previousPacks);
        action = client.reloadResourcePacks();
        packPhase = 2;
        return false;
      }
      if (resource.sourcePackId().equals(probeId))
        throw new IllegalStateException("Colony override not removed");
      client.getTextureManager().bindForSetup(texture);
      if (org.lwjgl.opengl.GL11.glGetTexLevelParameteri(
              org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH)
          != 64) throw new IllegalStateException("Original colony GPU texture not restored");
      try (var paths = Files.walk(probePack)) {
        for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
      }
      repository.reload();
      System.out.println(
          "SLIMIC COLONY RESOURCE PACK SMOKE PASSED: 512px replacement selected, GPU texture"
              + " reloaded, 64px original restored; selected packs retained; cycles="
              + 1);
      return true;
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final class Gallery extends Screen {
    Gallery() {
      super(Component.literal("Colony visual audit"));
    }

    @Override
    public boolean isPauseScreen() {
      return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
      graphics.fill(0, 0, width, height, 0xff23322f);
      for (int i = 0; i < portraits.size(); i++) {
        int cellWidth = width / 3,
            cellHeight = height / 2,
            x = i % 3 * cellWidth,
            y = i / 3 * cellHeight;
        graphics.drawCenteredString(
            font, portraits.get(i).getName(), x + cellWidth / 2, y + 8, 0xffefe8ce);
        InventoryScreen.renderEntityInInventoryFollowsMouse(
            graphics,
            x + cellWidth / 2,
            y + cellHeight - 28,
            i == 0 ? 14 : 45,
            26,
            -7,
            portraits.get(i));
      }
      super.render(graphics, mouseX, mouseY, partial);
    }
  }
}
