package io.github.meistermods.slimic.registry;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.CleaningSlime;
import io.github.meistermods.slimic.entity.CombatSlime;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.HeraldSlime;
import io.github.meistermods.slimic.entity.MiningSlime;
import io.github.meistermods.slimic.entity.SaddleSlime;
import io.github.meistermods.slimic.entity.TransportSlime;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
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

  private ModEntities() {}

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
    event.put(SADDLE_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(FRIENDLY_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(MINING_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(TRANSPORT_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(COMBAT_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(CLEANING_SLIME.get(), FriendlySlime.createAttributes().build());
    event.put(HERALD_SLIME.get(), FriendlySlime.createAttributes().build());
  }
}
