package io.github.meistermods.slimic.registry;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.AbundanceSlime;
import io.github.meistermods.slimic.entity.CleaningSlime;
import io.github.meistermods.slimic.entity.CombatSlime;
import io.github.meistermods.slimic.entity.DragonSlime;
import io.github.meistermods.slimic.entity.FarmerSlime;
import io.github.meistermods.slimic.entity.FishingSlime;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.GuardianAngelSlime;
import io.github.meistermods.slimic.entity.HealingSlime;
import io.github.meistermods.slimic.entity.HeraldSlime;
import io.github.meistermods.slimic.entity.LumberjackSlime;
import io.github.meistermods.slimic.entity.MeowSlime;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.SaddleSlime;
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
  public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
      DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Slimic.MOD_ID);

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

  public static final RegistryObject<EntityType<FarmerSlime>> FARMER_SLIME =
      ENTITY_TYPES.register(
          "farmer_slime",
          () ->
              EntityType.Builder.of(FarmerSlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":farmer_slime"));
  public static final RegistryObject<EntityType<LumberjackSlime>> LUMBERJACK_SLIME =
      ENTITY_TYPES.register(
          "lumberjack_slime",
          () ->
              EntityType.Builder.of(LumberjackSlime::new, MobCategory.CREATURE)
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

  private ModEntities() {}

  public static final RegistryObject<EntityType<SoggySlime>> SOGGY_SLIME =
      ENTITY_TYPES.register(
          "soggy_slime",
          () ->
              EntityType.Builder.of(SoggySlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":soggy_slime"));

  public static final RegistryObject<EntityType<HeraldSlime>> HERALD_SLIME =
      ENTITY_TYPES.register(
          "herald_slime",
          () ->
              EntityType.Builder.of(HeraldSlime::new, MobCategory.CREATURE)
                  .sized(1.04F, 1.04F)
                  .clientTrackingRange(10)
                  .build(Slimic.MOD_ID + ":herald_slime"));

  @SubscribeEvent
  public static void registerAttributes(EntityAttributeCreationEvent event) {
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
    event.put(LUMBERJACK_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(FARMER_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(SADDLE_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(FRIENDLY_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(MINING_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(TRANSPORT_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(COMBAT_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(CLEANING_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(HERALD_SLIME.get(), FriendlySlime.createAttributes().build());
  }
}
