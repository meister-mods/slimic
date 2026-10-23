package io.github.meistermods.slimic.registry;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.menu.CreativeSlimeTunerMenu;
import io.github.meistermods.slimic.menu.SlimeMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
  public static final DeferredRegister<MenuType<?>> MENUS =
      DeferredRegister.create(ForgeRegistries.MENU_TYPES, Slimic.MOD_ID);
  public static final RegistryObject<MenuType<io.github.meistermods.slimic.menu.ColonyBondingMenu>>
      COLONY_BONDING =
          MENUS.register(
              "colony_bonding",
              () ->
                  IForgeMenuType.create(io.github.meistermods.slimic.menu.ColonyBondingMenu::new));
  public static final RegistryObject<MenuType<SlimeMenu>> SLIME =
      MENUS.register("slime", () -> IForgeMenuType.create(SlimeMenu::new));
  public static final RegistryObject<MenuType<io.github.meistermods.slimic.menu.RoyalSynthesisMenu>>
      ROYAL_SYNTHESIS =
          MENUS.register(
              "royal_synthesis",
              () ->
                  IForgeMenuType.create(io.github.meistermods.slimic.menu.RoyalSynthesisMenu::new));

  public static final RegistryObject<MenuType<io.github.meistermods.slimic.menu.CommandMenu>>
      COMMAND =
          MENUS.register(
              "commander",
              () -> IForgeMenuType.create(io.github.meistermods.slimic.menu.CommandMenu::new));

  private ModMenus() {}

  public static final RegistryObject<MenuType<CreativeSlimeTunerMenu>> CREATIVE_TUNER =
      MENUS.register("creative_tuner", () -> IForgeMenuType.create(CreativeSlimeTunerMenu::new));
}
