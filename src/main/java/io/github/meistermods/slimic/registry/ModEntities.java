package io.github.meistermods.slimic.registry;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.FriendlySlime;
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

  private ModEntities() {}

  @SubscribeEvent
  public static void registerAttributes(EntityAttributeCreationEvent event) {
    event.put(FRIENDLY_SLIME.get(), FriendlySlime.createAttributes().build());
  }
}
