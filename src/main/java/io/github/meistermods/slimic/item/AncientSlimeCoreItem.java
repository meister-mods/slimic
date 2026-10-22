package io.github.meistermods.slimic.item;

import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.*;
import io.github.meistermods.slimic.registry.*;
import io.github.meistermods.slimic.world.ForgottenOrders;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A hostile remnant is restored as a new companion, never loaded as a tame entity snapshot. */
@Mod.EventBusSubscriber(modid = Slimic.MOD_ID)
public final class AncientSlimeCoreItem extends Item {
  public AncientSlimeCoreItem(Properties properties) {
    super(properties.stacksTo(1).fireResistant());
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void honorPedestalProtection(PlayerInteractEvent.RightClickBlock event) {
    if (event.getUseBlock() == Event.Result.DENY
        && event.getItemStack().getItem() instanceof AncientSlimeCoreItem
        && event
            .getLevel()
            .getBlockState(event.getPos())
            .is(ModWorldBlocks.block("command_pedestal"))) event.setUseItem(Event.Result.DENY);
  }

  public static ItemStack fromSlime(AncientSlime slime) {
    var stack = fromRemains(slime.role(), slime.getUUID(), slime.getVeterancy());
    var data = stack.getOrCreateTag();
    if (slime.hasCustomName())
      data.putString("AncientName", Component.Serializer.toJson(slime.getCustomName()));
    return stack;
  }

  public static ItemStack fromRemains(AncientSlime.Role role, UUID identity, int rank) {
    var stack = new ItemStack(ModItems.DAMAGED_ANCIENT_SLIME_CORE.get());
    var data = stack.getOrCreateTag();
    data.putInt("AncientCoreVersion", 1);
    data.putUUID("AncientIdentity", identity);
    data.putString("AncientRole", role.savedName());
    data.putInt("AncientVeterancy", Math.max(0, Math.min(3, rank)));
    return stack;
  }

  /** Creative entries receive an identity when first used, so tab copies remain independent. */
  public static ItemStack creativeVariant(AncientSlime.Role role) {
    var stack = new ItemStack(ModItems.DAMAGED_ANCIENT_SLIME_CORE.get());
    var data = stack.getOrCreateTag();
    data.putInt("AncientCoreVersion", 1);
    data.putString("AncientRole", role.savedName());
    data.putBoolean("AncientCreativeTemplate", true);
    return stack;
  }

  @Nullable
  public static AncientSlime.Role role(ItemStack stack) {
    if (!stack.is(ModItems.DAMAGED_ANCIENT_SLIME_CORE.get())
        || !stack.hasTag()
        || stack.getTag().getInt("AncientCoreVersion") != 1
        || (!stack.getTag().hasUUID("AncientIdentity")
            && !stack.getTag().getBoolean("AncientCreativeTemplate"))) return null;
    try {
      return AncientSlime.Role.fromSavedName(stack.getTag().getString("AncientRole"));
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  private static int count(Player player, Item item) {
    int total = 0;
    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
      var stack = player.getInventory().getItem(slot);
      if (stack.is(item)) total += stack.getCount();
    }
    return total;
  }

  private static void consumeOne(Player player, Item item) {
    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
      var stack = player.getInventory().getItem(slot);
      if (stack.is(item)) {
        stack.shrink(1);
        return;
      }
    }
  }

  @Override
  public InteractionResult useOn(UseOnContext context) {
    var player = context.getPlayer();
    var level = context.getLevel();
    var stack = context.getItemInHand();
    if (!level.getBlockState(context.getClickedPos()).is(ModWorldBlocks.block("command_pedestal")))
      return InteractionResult.PASS;
    if (player == null
        || !level.mayInteract(player, context.getClickedPos())
        || !player.mayUseItemAt(context.getClickedPos(), context.getClickedFace(), stack))
      return InteractionResult.FAIL;
    if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
    var role = role(stack);
    if (role == null || stack.getCount() != 1) return fail(player, "ancient_core_invalid");
    if (role.isMine() && server.dimension() != Level.OVERWORLD)
      return fail(player, "mine_restoration_overworld");
    Item catalyst =
        role.isMine() ? ModItems.OLD_COMMAND_FRAGMENT.get() : ModItems.ANCIENT_COMMAND_SEAL.get();
    if (count(player, catalyst) < 1 || count(player, Items.SLIME_BALL) < 1)
      return fail(
          player, role.isMine() ? "mine_restoration_materials" : "ancient_restoration_materials");
    if (!stack.getTag().hasUUID("AncientIdentity")) {
      stack.getTag().putUUID("AncientIdentity", UUID.randomUUID());
      stack.getTag().remove("AncientCreativeTemplate");
      player.getInventory().setChanged();
    }
    UUID identity = stack.getTag().getUUID("AncientIdentity");
    var orders = ForgottenOrders.get(server);
    if (!orders.reserve(identity)) return fail(player, "ancient_core_redeemed");
    try {
      var original = stack.copy();
      FriendlySlime companion =
          switch (role) {
            case GUARD, MINE_GUARD -> ModEntities.ROYAL_GUARD_SLIME.get().create(server);
            case TRANSPORT -> ModEntities.TRANSPORT_SLIME.get().create(server);
            case COMMAND -> ModEntities.COMMAND_SLIME.get().create(server);
            case MINING -> ModEntities.ANCIENT_MINING_SLIME.get().create(server);
            case TUNNEL -> ModEntities.ANCIENT_TUNNEL_SLIME.get().create(server);
          };
      if (companion == null) return InteractionResult.FAIL;
      companion.setUUID(UUID.randomUUID());
      companion.tame(player);
      companion.setAncientOrigin(true);
      int rank = Math.max(0, Math.min(3, original.getTag().getInt("AncientVeterancy")));
      var saved = companion.saveWithoutId(new CompoundTag());
      saved.getCompound("SlimeStats").putInt(SlimeStat.STAMINA.key(), rank);
      companion.load(saved);
      AncientTraining.applyOnce(companion, identity, rank);
      companion.setHealth(1);
      companion.setWaiting(true);
      if (original.getTag().contains("AncientName", 8)) {
        try {
          companion.setCustomName(
              Component.Serializer.fromJson(original.getTag().getString("AncientName")));
        } catch (RuntimeException invalid) {
          /* A damaged name must not invalidate the core. */
        }
      }
      var result = FriendlySlimeItem.fromSlime(companion);
      // Entity-construction callbacks may alter the held item or material inventory.
      if (!ItemStack.matches(original, player.getItemInHand(context.getHand()))
          || count(player, catalyst) < 1
          || count(player, Items.SLIME_BALL) < 1
          || !server
              .getBlockState(context.getClickedPos())
              .is(ModWorldBlocks.block("command_pedestal"))) return InteractionResult.FAIL;
      consumeOne(player, catalyst);
      consumeOne(player, Items.SLIME_BALL);
      player.setItemInHand(context.getHand(), result);
      player.getInventory().setChanged();
      orders.redeem(identity);
      CompanionRegistry.get(server).observeStored(result, server, context.getClickedPos());
      player.displayClientMessage(
          Component.translatable("message.slimic.ancient_restored", result.getHoverName()), true);
      return InteractionResult.CONSUME;
    } finally {
      orders.release(identity);
    }
  }

  private static InteractionResult fail(Player player, String key) {
    player.displayClientMessage(Component.translatable("message.slimic." + key), true);
    return InteractionResult.FAIL;
  }

  @Override
  public Component getName(ItemStack stack) {
    var role = role(stack);
    return role != null && role.isMine()
        ? Component.translatable(
            "item.slimic.damaged_ancient_slime_core."
                + role.savedName().toLowerCase(java.util.Locale.ROOT))
        : super.getName(stack);
  }

  @Override
  public void appendHoverText(
      ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
    lines.add(
        Component.translatable(
                role(stack) != null && role(stack).isMine()
                    ? "item.slimic.damaged_ancient_slime_core.mine_tooltip"
                    : "item.slimic.damaged_ancient_slime_core.tooltip")
            .withStyle(ChatFormatting.GRAY));
    var role = role(stack);
    if (role != null)
      lines.add(
          Component.translatable("item.slimic.damaged_ancient_slime_core.training_tooltip")
              .withStyle(ChatFormatting.GRAY));
    if (role != null)
      lines.add(
          Component.translatable(
                  "item.slimic.damaged_ancient_slime_core.role",
                  Component.translatable(
                      "name.slimic.ancient_" + role.savedName().toLowerCase(java.util.Locale.ROOT)))
              .withStyle(ChatFormatting.GOLD));
  }
}
