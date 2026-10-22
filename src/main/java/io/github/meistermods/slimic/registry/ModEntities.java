package io.github.meistermods.slimic.registry;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.AbundanceSlime;
import io.github.meistermods.slimic.entity.AttentionSlime;
import io.github.meistermods.slimic.entity.CleaningSlime;
import io.github.meistermods.slimic.entity.CombatSlime;
import io.github.meistermods.slimic.entity.CommandSlime;
import io.github.meistermods.slimic.entity.DragonSlime;
import io.github.meistermods.slimic.entity.EnderSlime;
import io.github.meistermods.slimic.entity.FarmingSlime;
import io.github.meistermods.slimic.entity.FishingSlime;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.GlowSlime;
import io.github.meistermods.slimic.entity.GuardianAngelSlime;
import io.github.meistermods.slimic.entity.HealingSlime;
import io.github.meistermods.slimic.entity.LoggingSlime;
import io.github.meistermods.slimic.entity.MeowSlime;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.SaddleSlime;
import io.github.meistermods.slimic.entity.SignalingSlime;
import io.github.meistermods.slimic.entity.SoggySlime;
import io.github.meistermods.slimic.entity.TransportSlime;
import io.github.meistermods.slimic.entity.TunnelSlime;
import io.github.meistermods.slimic.entity.WarehouseSlime;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = Slimic.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntities {
  // Registry IDs are persistent save identifiers; Java type names may evolve independently.
  public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
      DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Slimic.MOD_ID);

  public static final RegistryObject<
          EntityType<io.github.meistermods.slimic.entity.NextGenerationMiningSlime>>
      NEXT_GENERATION_MINING_SLIME =
          ENTITY_TYPES.register(
              "next_generation_mining_slime",
              () ->
                  EntityType.Builder.of(
                          io.github.meistermods.slimic.entity.NextGenerationMiningSlime::new,
                          MobCategory.CREATURE)
                      .sized(1.04F, 1.04F)
                      .clientTrackingRange(10)
                      .build(Slimic.MOD_ID + ":next_generation_mining_slime"));

  public static final RegistryObject<
          EntityType<io.github.meistermods.slimic.entity.RoyalGuardSlime>>
      ROYAL_GUARD_SLIME =
          ENTITY_TYPES.register(
              "royal_guard_slime",
              () ->
                  EntityType.Builder.of(
                          io.github.meistermods.slimic.entity.RoyalGuardSlime::new,
                          MobCategory.CREATURE)
                      .sized(1.04F, 1.04F)
                      .clientTrackingRange(10)
                      .build(Slimic.MOD_ID + ":royal_guard_slime"));
  public static final RegistryObject<EntityType<io.github.meistermods.slimic.entity.AncientSlime>>
      ANCIENT_GUARD_SLIME = ancient("ancient_guard_slime");
  public static final RegistryObject<EntityType<io.github.meistermods.slimic.entity.AncientSlime>>
      FORGOTTEN_TRANSPORT_SLIME = ancient("forgotten_transport_slime");
  public static final RegistryObject<EntityType<io.github.meistermods.slimic.entity.AncientSlime>>
      ANCIENT_COMMAND_SLIME = ancient("ancient_commander_slime");

  public static final RegistryObject<EntityType<io.github.meistermods.slimic.entity.AncientSlime>>
      FORGOTTEN_MINING_SLIME = ancient("forgotten_miner_slime");
  public static final RegistryObject<EntityType<io.github.meistermods.slimic.entity.AncientSlime>>
      FORGOTTEN_TUNNEL_SLIME = ancient("forgotten_tunnel_slime");
  public static final RegistryObject<EntityType<io.github.meistermods.slimic.entity.AncientSlime>>
      MINE_GUARDIAN_SLIME = ancient("mine_guardian_slime");
  public static final RegistryObject<
          EntityType<io.github.meistermods.slimic.entity.AncientMiningSlime>>
      ANCIENT_MINING_SLIME =
          ENTITY_TYPES.register(
              "ancient_mining_slime",
              () ->
                  EntityType.Builder.of(
                          io.github.meistermods.slimic.entity.AncientMiningSlime::new,
                          MobCategory.CREATURE)
                      .sized(1.04F, 1.04F)
                      .clientTrackingRange(10)
                      .build(Slimic.MOD_ID + ":ancient_mining_slime"));
  public static final RegistryObject<
          EntityType<io.github.meistermods.slimic.entity.AncientTunnelSlime>>
      ANCIENT_TUNNEL_SLIME =
          ENTITY_TYPES.register(
              "ancient_tunnel_slime",
              () ->
                  EntityType.Builder.of(
                          io.github.meistermods.slimic.entity.AncientTunnelSlime::new,
                          MobCategory.CREATURE)
                      .sized(1.04F, 1.04F)
                      .clientTrackingRange(10)
                      .build(Slimic.MOD_ID + ":ancient_tunnel_slime"));

  private static RegistryObject<EntityType<io.github.meistermods.slimic.entity.AncientSlime>>
      ancient(String name) {
    return ENTITY_TYPES.register(
        name,
        () ->
            EntityType.Builder.of(
                    io.github.meistermods.slimic.entity.AncientSlime::new, MobCategory.MONSTER)
                .sized(1.04F, 1.04F)
                .clientTrackingRange(10)
                .build(Slimic.MOD_ID + ":" + name));
  }

  public static final RegistryObject<EntityType<FriendlySlime>> FRIENDLY_SLIME =
      ENTITY_TYPES.register(
          "friendly_slime",
          () ->
              EntityType.Builder.of(FriendlySlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":friendly_slime"));

  public static final RegistryObject<EntityType<MiningSlime>> MINING_SLIME =
      ENTITY_TYPES.register(
          "mining_slime",
          () ->
              EntityType.Builder.of(MiningSlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":mining_slime"));

  public static final RegistryObject<EntityType<TransportSlime>> TRANSPORT_SLIME =
      ENTITY_TYPES.register(
          "transport_slime",
          () ->
              EntityType.Builder.of(TransportSlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":transport_slime"));

  public static final RegistryObject<EntityType<CombatSlime>> COMBAT_SLIME =
      ENTITY_TYPES.register(
          "combat_slime",
          () ->
              EntityType.Builder.of(CombatSlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":combat_slime"));
  public static final RegistryObject<EntityType<CleaningSlime>> CLEANING_SLIME =
      ENTITY_TYPES.register(
          "cleaning_slime",
          () ->
              EntityType.Builder.of(CleaningSlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":cleaning_slime"));

  public static final RegistryObject<EntityType<SaddleSlime>> SADDLE_SLIME =
      ENTITY_TYPES.register(
          "saddle_slime",
          () ->
              EntityType.Builder.of(SaddleSlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":saddle_slime"));

  public static final RegistryObject<EntityType<FarmingSlime>> FARMING_SLIME =
      ENTITY_TYPES.register(
          "farmer_slime",
          () ->
              EntityType.Builder.of(FarmingSlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":farmer_slime"));
  public static final RegistryObject<EntityType<LoggingSlime>> LOGGING_SLIME =
      ENTITY_TYPES.register(
          "lumberjack_slime",
          () ->
              EntityType.Builder.of(LoggingSlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":lumberjack_slime"));

  public static final RegistryObject<EntityType<GuardianAngelSlime>> GUARDIAN_ANGEL_SLIME =
      ENTITY_TYPES.register(
          "guardian_angel_slime",
          () ->
              EntityType.Builder.of(GuardianAngelSlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":guardian_angel_slime"));

  public static final RegistryObject<EntityType<HealingSlime>> HEALING_SLIME =
      ENTITY_TYPES.register(
          "healing_slime",
          () ->
              EntityType.Builder.of(HealingSlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":healing_slime"));

  public static final RegistryObject<EntityType<WarehouseSlime>> WAREHOUSE_SLIME =
      ENTITY_TYPES.register(
          "warehouse_slime",
          () ->
              EntityType.Builder.of(WarehouseSlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":warehouse_slime"));

  public static final RegistryObject<EntityType<FishingSlime>> FISHING_SLIME =
      ENTITY_TYPES.register(
          "fishing_slime",
          () ->
              EntityType.Builder.of(FishingSlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":fishing_slime"));
  public static final RegistryObject<EntityType<AbundanceSlime>> ABUNDANCE_SLIME =
      ENTITY_TYPES.register(
          "abundance_slime",
          () ->
              EntityType.Builder.of(AbundanceSlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":abundance_slime"));
  public static final RegistryObject<EntityType<TunnelSlime>> TUNNEL_SLIME =
      ENTITY_TYPES.register(
          "tunnel_slime",
          () ->
              EntityType.Builder.of(TunnelSlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":tunnel_slime"));
  public static final RegistryObject<EntityType<DragonSlime>> DRAGON_SLIME =
      ENTITY_TYPES.register(
          "dragon_slime",
          () ->
              EntityType.Builder.of(DragonSlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":dragon_slime"));
  public static final RegistryObject<EntityType<MeowSlime>> MEOW_SLIME =
      ENTITY_TYPES.register(
          "meow_slime",
          () ->
              EntityType.Builder.of(MeowSlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":meow_slime"));

  public static final RegistryObject<EntityType<GlowSlime>> GLOW_SLIME =
      ENTITY_TYPES.register(
          "glow_slime",
          () ->
              EntityType.Builder.of(GlowSlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":glow_slime"));
  public static final RegistryObject<EntityType<CommandSlime>> COMMAND_SLIME =
      ENTITY_TYPES.register(
          "commander_slime",
          () ->
              EntityType.Builder.of(CommandSlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":commander_slime"));
  public static final RegistryObject<EntityType<EnderSlime>> ENDER_SLIME =
      ENTITY_TYPES.register(
          "ender_slime",
          () ->
              EntityType.Builder.of(EnderSlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":ender_slime"));
  public static final RegistryObject<EntityType<AttentionSlime>> ATTENTION_SLIME =
      ENTITY_TYPES.register(
          "attention_slime",
          () ->
              EntityType.Builder.of(AttentionSlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":attention_slime"));

  private ModEntities() {}

  /** Companion-only catalogs exclude hostile remnants, which have no summon-item contract. */
  public static java.util.Collection<RegistryObject<EntityType<?>>> companionTypes() {
    return ENTITY_TYPES.getEntries().stream()
        .filter(entry -> entry.get().getCategory() != MobCategory.MONSTER)
        .toList();
  }

  public static final RegistryObject<EntityType<SoggySlime>> SOGGY_SLIME =
      ENTITY_TYPES.register(
          "soggy_slime",
          () ->
              EntityType.Builder.of(SoggySlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":soggy_slime"));

  public static final RegistryObject<EntityType<SignalingSlime>> SIGNALING_SLIME =
      ENTITY_TYPES.register(
          "herald_slime",
          () ->
              EntityType.Builder.of(SignalingSlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":herald_slime"));

  @SubscribeEvent
  public static void registerAttributes(EntityAttributeCreationEvent event) {
    event.put(NEXT_GENERATION_MINING_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(ANCIENT_MINING_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(ANCIENT_TUNNEL_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(ROYAL_GUARD_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(
        ANCIENT_GUARD_SLIME.get(),
        io.github.meistermods.slimic.entity.AncientSlime.createAttributes(
                io.github.meistermods.slimic.entity.AncientSlime.Role.GUARD)
            .build());
    event.put(
        FORGOTTEN_TRANSPORT_SLIME.get(),
        io.github.meistermods.slimic.entity.AncientSlime.createAttributes(
                io.github.meistermods.slimic.entity.AncientSlime.Role.TRANSPORT)
            .build());
    event.put(
        ANCIENT_COMMAND_SLIME.get(),
        io.github.meistermods.slimic.entity.AncientSlime.createAttributes(
                io.github.meistermods.slimic.entity.AncientSlime.Role.COMMAND)
            .build());
    event.put(
        FORGOTTEN_MINING_SLIME.get(),
        io.github.meistermods.slimic.entity.AncientSlime.createAttributes(
                io.github.meistermods.slimic.entity.AncientSlime.Role.MINING)
            .build());
    event.put(
        FORGOTTEN_TUNNEL_SLIME.get(),
        io.github.meistermods.slimic.entity.AncientSlime.createAttributes(
                io.github.meistermods.slimic.entity.AncientSlime.Role.TUNNEL)
            .build());
    event.put(
        MINE_GUARDIAN_SLIME.get(),
        io.github.meistermods.slimic.entity.AncientSlime.createAttributes(
                io.github.meistermods.slimic.entity.AncientSlime.Role.MINE_GUARD)
            .build());
    event.put(GLOW_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(COMMAND_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(ENDER_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(ATTENTION_SLIME.get(), FriendlySlime.createAttributes().build());

    event.put(FISHING_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(ABUNDANCE_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(TUNNEL_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(
        DRAGON_SLIME.get(),
        FriendlySlime.createAttributes().add(Attributes.FLYING_SPEED, .3).build());
    event.put(MEOW_SLIME.get(), FriendlySlime.createAttributes().build());

    event.put(WAREHOUSE_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(HEALING_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(SOGGY_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(
        GUARDIAN_ANGEL_SLIME.get(),
        FriendlySlime.createAttributes().add(Attributes.FLYING_SPEED, .3).build());
    event.put(LOGGING_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(FARMING_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(SADDLE_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(FRIENDLY_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(MINING_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(TRANSPORT_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(COMBAT_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(CLEANING_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(SIGNALING_SLIME.get(), FriendlySlime.createAttributes().build());
  }
}
