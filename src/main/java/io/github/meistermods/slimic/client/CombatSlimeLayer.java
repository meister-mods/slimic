package io.github.meistermods.slimic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.meistermods.slimic.Slimic;
import io.github.meistermods.slimic.entity.BoundCombatSlime;
import io.github.meistermods.slimic.entity.CombatSlime;
import io.github.meistermods.slimic.entity.FriendlySlime;
import io.github.meistermods.slimic.entity.SaddleSlime;
import io.github.meistermods.slimic.item.SlimeArmorItem;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;

public final class CombatSlimeLayer extends RenderLayer<FriendlySlime, SlimeModel<FriendlySlime>> {
  public static final ModelLayerLocation ARMOR =
      new ModelLayerLocation(
          ResourceLocation.fromNamespaceAndPath(Slimic.MOD_ID, "combat_armor"), "main");
  private static final ResourceLocation[] ARMOR_TEXTURES = {
    SlimeTextures.accessory("armor_leather"),
    SlimeTextures.accessory("armor_chainmail"),
    SlimeTextures.accessory("armor_iron"),
    SlimeTextures.accessory("armor_golden"),
    SlimeTextures.accessory("armor_diamond"),
    SlimeTextures.accessory("armor_netherite")
  };
  private final ModelPart armor;
  private final EntityRendererProvider.Context context;

  public CombatSlimeLayer(
      RenderLayerParent<FriendlySlime, SlimeModel<FriendlySlime>> parent,
      EntityRendererProvider.Context context) {
    super(parent);
    this.context = context;
    armor = context.bakeLayer(ARMOR).getChild("armor");
  }

  public static LayerDefinition createLayer() {
    MeshDefinition mesh = new MeshDefinition();
    mesh.getRoot()
        .addOrReplaceChild(
            "armor",
            CubeListBuilder.create().texOffs(1, 1).addBox(-4.5F, 20, -4.5F, 9, 4.1F, 9),
            PartPose.ZERO);
    return LayerDefinition.create(mesh, 64, 64);
  }

  @Override
  public void render(
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      FriendlySlime slime,
      float limbSwing,
      float limbAmount,
      float partialTick,
      float age,
      float headYaw,
      float headPitch) {
    if (!(slime instanceof CombatSlime || slime instanceof SaddleSlime) || slime.isInvisible())
      return;
    ItemStack gear =
        slime instanceof BoundCombatSlime colony
            ? colony.sharedArmor()
            : slime.getItemBySlot(EquipmentSlot.CHEST);
    if (gear.getItem() instanceof SlimeArmorItem) {
      ResourceLocation texture = ARMOR_TEXTURES[SlimeArmorItem.materialIndex(gear)];
      ItemStack original = SlimeArmorItem.baseArmor(gear);
      int color =
          original.getItem() instanceof DyeableLeatherItem leather
              ? leather.getColor(original)
              : 0xffffff;
      armor.render(
          pose,
          ItemRenderer.getArmorFoilBuffer(
              buffers, RenderType.armorCutoutNoCull(texture), false, gear.hasFoil()),
          light,
          OverlayTexture.NO_OVERLAY,
          ((color >> 16) & 255) / 255F,
          ((color >> 8) & 255) / 255F,
          (color & 255) / 255F,
          1);
    }
    if (slime instanceof CombatSlime fighter) {
      SlimeEquipmentRenderer.held(
          context, pose, buffers, light, fighter, fighter.getMainHandItem(), false);
      SlimeEquipmentRenderer.held(
          context, pose, buffers, light, fighter, fighter.getOffhandItem(), true);
    }
  }
}
