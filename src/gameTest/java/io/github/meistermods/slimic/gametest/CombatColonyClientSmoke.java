package io.github.meistermods.slimic.gametest;

import com.google.gson.GsonBuilder;
import io.github.meistermods.slimic.block.ColonyBondingStationBlockEntity;
import io.github.meistermods.slimic.client.*;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.registry.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Real native tracking, six-slot menu, separate equipment, live joining leap and single recovery.
 */
public final class CombatColonyClientSmoke {
  private static CompletableFuture<?> action;
  private static int phase, ticks, rootId, targetId, language;
  private static final int OFFSET = 600000;
  private static final String[] LANGUAGES = {"en_us", "ko_kr", "ja_jp", "es_es"};
  private static volatile int rootHits, bodyHits, criticalHits;
  private static Object listener;
  private static boolean sawWindup, sawFlight;
  private static final List<Map<String, Object>> frames = new ArrayList<>();

  private CombatColonyClientSmoke() {}

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private static net.minecraft.server.level.ServerPlayer player(Minecraft mc) {
    return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
  }

  private static void clearOtherFixtureEntities(
      net.minecraft.server.level.ServerLevel level, BoundCombatSlime root) {
    var body = root.getLinkedBody();
    var others = new ArrayList<Entity>();
    for (var entity : level.getAllEntities())
      if (!(entity instanceof net.minecraft.world.entity.player.Player)
          && entity != root
          && entity != body
          && entity.distanceToSqr(root) < 64 * 64) others.add(entity);
    others.forEach(Entity::discard);
  }

  private static BoundCombatSlime root(Minecraft mc) {
    return (BoundCombatSlime) mc.level.getEntity(rootId);
  }

  private static void shot(Minecraft mc, String name) throws java.io.IOException {
    var directory = mc.gameDirectory.toPath().resolve("combat-colony-audit");
    Files.createDirectories(directory);
    try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
      image.writeToFile(directory.resolve(name + ".png"));
    }
  }

  private static void look(Minecraft mc) {
    var root = root(mc);
    if (root == null) return;
    Vec3 center =
        root.getLinkedBody() == null
            ? root.position()
            : root.position().add(root.getLinkedBody().position()).scale(.5);
    Vec3 delta = center.add(0, .7, 0).subtract(mc.player.getEyePosition());
    mc.player.setYRot((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90));
    mc.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())));
  }

  public static boolean tick(Minecraft mc) {
    try {
      if (phase == 7) return true;
      if (action != null && !action.isDone()) return false;
      if (action != null) {
        action.join();
        action = null;
      }
      if (phase == 0) {
        mc.setScreen(null);
        mc.options.guiScale().set(2);
        mc.resizeDisplay();
        mc.options.particles().set(ParticleStatus.MINIMAL);
        mc.options.fov().set(70);
        action =
            mc.getSingleplayerServer()
                .submit(
                    () -> {
                      try {
                        var player = player(mc);
                        var level = player.serverLevel();
                        System.out.println(
                            "SLIMIC COMBAT FIXTURE BORDER: " + level.getWorldBorder().getSize());
                        level.getWorldBorder().setCenter(0, 0);
                        level.getWorldBorder().setSize(59_999_968);
                        player.setGameMode(GameType.CREATIVE);
                        player.getInventory().clearContent();
                        level
                            .getEntitiesOfClass(
                                Entity.class,
                                new net.minecraft.world.phys.AABB(
                                    OFFSET + 8, 100, OFFSET + 8, OFFSET + 49, 112, OFFSET + 49),
                                entity ->
                                    !(entity instanceof net.minecraft.world.entity.player.Player))
                            .forEach(Entity::discard);
                        for (int x = 8; x < 49; x++)
                          for (int z = 8; z < 49; z++) {
                            level.setBlock(
                                new BlockPos(OFFSET + x, 100, OFFSET + z),
                                Blocks.STONE_BRICKS.defaultBlockState(),
                                2);
                            for (int y = 101; y < 111; y++)
                              level.setBlock(
                                  new BlockPos(OFFSET + x, y, OFFSET + z),
                                  Blocks.AIR.defaultBlockState(),
                                  2);
                          }
                        level.setDayTime(6000);
                        level.setWeatherParameters(10000, 0, false, false);
                        player.teleportTo(level, OFFSET + 20.5, 101, OFFSET + 18.5, 0, 0);
                        var stationPos = new BlockPos(OFFSET + 20, 101, OFFSET + 20);
                        level.setBlock(
                            stationPos,
                            ModBlocks.COLONY_BONDING_STATION.get().defaultBlockState(),
                            3);
                        var station =
                            (ColonyBondingStationBlockEntity) level.getBlockEntity(stationPos);
                        var deploy =
                            TransportColonyClientSmoke.class.getDeclaredMethod(
                                "deploy",
                                net.minecraft.server.level.ServerPlayer.class,
                                ColonyBondingStationBlockEntity.class,
                                boolean.class,
                                BlockPos.class);
                        deploy.setAccessible(true);
                        var root =
                            (BoundCombatSlime)
                                deploy.invoke(
                                    null,
                                    player,
                                    station,
                                    true,
                                    new BlockPos(OFFSET + 24, 101, OFFSET + 24));
                        rootId = root.getId();
                        player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                        player.teleportTo(level, OFFSET + 29, 104, OFFSET + 38, 180, 15);
                      } catch (ReflectiveOperationException failure) {
                        throw new IllegalStateException(failure);
                      }
                    });
        phase = 1;
        ticks = 0;
        return false;
      }
      if (phase == 1) {
        if (++ticks > 400) throw new IllegalStateException("No tracked linked combat pair");
        var root = root(mc);
        if (root == null || root.getLinkedBody() == null) return false;
        action =
            mc.getSingleplayerServer()
                .submit(
                    () -> {
                      var server = (BoundCombatSlime) player(mc).serverLevel().getEntity(rootId);
                      var body = server.getLinkedBody();
                      clearOtherFixtureEntities(player(mc).serverLevel(), server);
                      body.setNoAi(true);
                      body.moveTo(OFFSET + 35.5, 101, OFFSET + 24.5);
                      server.moveTo(OFFSET + 24.5, 101, OFFSET + 24.5);
                      server.setWaiting(true);
                      player(mc)
                          .setItemInHand(
                              InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLIME_TUNER.get()));
                    });
        phase = 2;
        ticks = 0;
        return false;
      }
      if (phase == 2) {
        look(mc);
        if (++ticks < 15) return false;
        var root = root(mc);
        var body = root.getLinkedBody();
        require(
            root.isOrderedToSit() && body.getColony() == root,
            "Both linked members initially wait");
        require(
            mc.getEntityRenderDispatcher().getRenderer(root) instanceof BoundColonyRenderer
                && mc.getEntityRenderDispatcher().getRenderer(body)
                    instanceof CombatColonyBodyRenderer,
            "Separate native renderers draw the pair and cord");
        require(
            root.getEquipment(0).is(Items.IRON_SWORD)
                && root.getEquipment(3).is(Items.GOLDEN_SWORD),
            "Both swords reach the client independently");
        var renderer = (CombatColonyBodyRenderer) mc.getEntityRenderDispatcher().getRenderer(body);
        var field = CombatColonyBodyRenderer.class.getDeclaredField("visual");
        field.setAccessible(true);
        var visual = (CombatSlime) field.get(renderer);
        require(
            visual != null
                && !visual.isAddedToWorld()
                && visual.getMainHandItem().is(Items.GOLDEN_SWORD)
                && visual.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.GOLDEN_SLIME_ARMOR.get()),
            "Second body renders its actual gold equipment through one unspawned visual proxy");
        shot(mc, "linked-pair");
        action =
            mc.getSingleplayerServer()
                .submit(
                    () -> {
                      var player = player(mc);
                      var server = (BoundCombatSlime) player.serverLevel().getEntity(rootId);
                      player.teleportTo(
                          player.serverLevel(), OFFSET + 33.5, 101, OFFSET + 24.5, 90, 0);
                      require(
                          player
                              .interactOn(server.getLinkedBody(), InteractionHand.MAIN_HAND)
                              .consumesAction(),
                          "The actual second body opens canonical management");
                    });
        phase = 3;
        ticks = 0;
        return false;
      }
      if (phase == 3) {
        if (mc.getOverlay() != null) return false;
        if (++ticks < 12) return false;
        require(
            mc.screen instanceof SlimeScreen && mc.player.containerMenu instanceof SlimeMenu,
            "Second-body interaction opens the shared menu");
        var menu = (SlimeMenu) mc.player.containerMenu;
        require(
            menu.slots.get(0).getItem().is(Items.IRON_SWORD)
                && menu.slots.get(3).getItem().is(Items.GOLDEN_SWORD)
                && menu.slots.get(5).getItem().is(ModItems.GOLDEN_SLIME_ARMOR.get()),
            "All six native menu slots synchronize the two real equipment sets");
        shot(mc, "six-equipment-slots-" + mc.options.languageCode);
        if (++language < LANGUAGES.length) {
          mc.options.languageCode = LANGUAGES[language];
          mc.getLanguageManager().setSelected(mc.options.languageCode);
          action = mc.reloadResourcePacks();
          ticks = 0;
          return false;
        }
        mc.player.closeContainer();
        action =
            mc.getSingleplayerServer()
                .submit(
                    () -> {
                      var player = player(mc);
                      player.closeContainer();
                      var level = player.serverLevel();
                      var server = (BoundCombatSlime) level.getEntity(rootId);
                      var body = server.getLinkedBody();
                      clearOtherFixtureEntities(level, server);
                      player.teleportTo(level, OFFSET + 29, 104, OFFSET + 38, 180, 15);
                      server.setNoAi(false);
                      body.setNoAi(false);
                      server.setOnGround(true);
                      body.setOnGround(true);
                      var target = EntityType.HUSK.create(level);
                      target.setNoAi(true);
                      target.moveTo(OFFSET + 24.5, 101, OFFSET + 27);
                      target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
                      target.setHealth(200);
                      level.addFreshEntity(target);
                      targetId = target.getId();
                      listener =
                          new Object() {
                            @net.minecraftforge.eventbus.api.SubscribeEvent
                            public void hit(
                                net.minecraftforge.event.entity.living.LivingHurtEvent event) {
                              if (event.getEntity().getId() != targetId) return;
                              var actor = event.getSource().getEntity();
                              if (actor == server) rootHits++;
                              else if (actor == body) {
                                bodyHits++;
                                if (Math.abs(
                                        event.getAmount()
                                            - (server.getAttributeBaseValue(
                                                        Attributes.ATTACK_DAMAGE)
                                                    + 3)
                                                * 1.5)
                                    < .01) criticalHits++;
                              }
                            }
                          };
                      net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(listener);
                      server.setWorking(true);
                    });
        phase = 4;
        ticks = 0;
        return false;
      }
      if (phase == 4) {
        var root = root(mc);
        var visibleBodies =
            mc.level.getEntitiesOfClass(
                LivingEntity.class,
                root.getBoundingBox().inflate(24),
                entity -> entity instanceof FriendlySlime || entity instanceof LinkedColonyBody<?>);
        require(
            ticks < 5 || visibleBodies.size() == 2,
            "The client tracks exactly two colony bodies: " + visibleBodies);
        look(mc);
        ticks++;
        frames.add(
            Map.of(
                "tick",
                ticks,
                "member",
                root.assistMember(),
                "motion_ticks",
                root.assistTicks(),
                "root",
                root.position().toString(),
                "body",
                root.getLinkedBody() == null
                    ? "missing"
                    : root.getLinkedBody().position().toString()));
        if (root.assistMember() != 0 && root.assistTicks() <= 6 && !sawWindup) {
          sawWindup = true;
          shot(mc, "joining-windup");
        }
        if (root.assistMember() != 0 && root.assistTicks() > 8 && !sawFlight) {
          sawFlight = true;
          shot(mc, "joining-flight");
        }
        if (ticks < 100) return false;
        Files.writeString(
            mc.gameDirectory.toPath().resolve("combat-colony-audit/last-attempt.json"),
            new GsonBuilder().setPrettyPrinting().create().toJson(frames));
        require(
            sawWindup && sawFlight && criticalHits == 1 && rootHits > 0 && bodyHits > 0,
            "The native fight shows preparation, flight, one joining critical and attacks by both"
                + " members: windup="
                + sawWindup
                + " flight="
                + sawFlight
                + " critical="
                + criticalHits
                + " roots="
                + rootHits
                + " bodies="
                + bodyHits);
        shot(mc, "after-joining");
        action =
            mc.getSingleplayerServer()
                .submit(
                    () -> {
                      var server = (BoundCombatSlime) player(mc).serverLevel().getEntity(rootId);
                      server.setWaiting(true);
                      require(
                          server.getEquipment(0).getDamageValue() > 0
                              && server.getEquipment(3).getDamageValue() > 0,
                          "Both real swords lose durability in the live fight");
                      if (listener != null)
                        net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);
                      var target = player(mc).serverLevel().getEntity(targetId);
                      if (target != null) target.discard();
                    });
        phase = 5;
        ticks = 0;
        return false;
      }
      if (phase == 5) {
        if (++ticks < 12) return false;
        require(root(mc).assistMember() == 0, "Wait ends any joining motion");
        var output = mc.gameDirectory.toPath().resolve("combat-colony-audit");
        Files.writeString(
            output.resolve("motion.json"),
            new GsonBuilder()
                .setPrettyPrinting()
                .create()
                .toJson(
                    Map.of(
                        "frames",
                        frames,
                        "root_hits",
                        rootHits,
                        "body_hits",
                        bodyHits,
                        "critical_hits",
                        criticalHits)));
        action =
            mc.getSingleplayerServer()
                .submit(
                    () -> {
                      var player = player(mc);
                      var server = (BoundCombatSlime) player.serverLevel().getEntity(rootId);
                      var body = server.getLinkedBody();
                      player.teleportTo(
                          player.serverLevel(), body.getX() + 1, body.getY(), body.getZ(), 90, 0);
                      player.setShiftKeyDown(true);
                      require(
                          player.interactOn(body, InteractionHand.MAIN_HAND).consumesAction()
                              && server.isRemoved()
                              && body.isRemoved(),
                          "Capturing either body retires both physical bodies");
                      int count = 0;
                      for (int i = 0; i < player.getInventory().getContainerSize(); i++)
                        if (player.getInventory().getItem(i).is(ModItems.BOUND_COMBAT_SLIME.get()))
                          count++;
                      require(
                          count == 1,
                          "Capture issues one colony item with both members, never two copies");
                      player.setShiftKeyDown(false);
                    });
        phase = 6;
        return false;
      }
      phase = 7;
      System.out.println(
          "SLIMIC COMBAT COLONY CLIENT SMOKE PASSED: separate equipped bodies, six slots, native"
              + " joining critical and single capture");
      return true;
    } catch (java.io.IOException | ReflectiveOperationException failure) {
      throw new IllegalStateException(failure);
    }
  }
}
