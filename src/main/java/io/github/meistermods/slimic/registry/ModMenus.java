package io.github.meistermods.slimic.registry;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.menu.SlimeMenu;
import io.github.meistermods.slimic.menu.TunerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
  public static final DeferredRegister<MenuType<?>> MENUS =
      DeferredRegister.create(ForgeRegistries.MENU_TYPES, Slimic.MOD_ID);
  public static final RegistryObject<MenuType<SlimeMenu>> SLIME =
      MENUS.register("slime", () -> IForgeMenuType.create(SlimeMenu::new));

  private ModMenus() {}

  public static final RegistryObject<MenuType<TunerMenu>> TUNER =
      MENUS.register("tuner", () -> IForgeMenuType.create(TunerMenu::new));
}
