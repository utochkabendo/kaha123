package dev.csarsenal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.csarsenal.weapon.CsItem;
import dev.csarsenal.weapon.WeaponDef;
import dev.csarsenal.weapon.WeaponItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** 3D rendering of CS items in inventories, on the ground, in item frames and in the hands of mobs. */
public final class WeaponItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static WeaponItemRenderer INSTANCE;

    public WeaponItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static WeaponItemRenderer get() {
        if (INSTANCE == null) INSTANCE = new WeaponItemRenderer();
        return INSTANCE;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        WeaponDef def = CsItem.defOf(stack);
        if (def == null) return;
        Mesh mesh = Meshes.get(def.mesh);
        if (mesh == null) return;
        boolean silencer = def.silencer && stack.getItem() instanceof WeaponItem && WeaponItem.state(stack).silencer();
        ps.pushPose();
        ps.translate(0.5f, 0.5f, 0.5f);
        float len = Math.max(0.05f, mesh.longestSide());
        float cx = (mesh.min.x + mesh.max.x) / 2, cy = (mesh.min.y + mesh.max.y) / 2, cz = (mesh.min.z + mesh.max.z) / 2;
        boolean flat = def.category == WeaponDef.Category.GRENADE || def.category == WeaponDef.Category.EQUIPMENT || def.category == WeaponDef.Category.C4;
        switch (ctx) {
            case GUI -> {
                float s = 0.95f / len;
                if (flat) {
                    ps.mulPose(Axis.XP.rotationDegrees(20));
                    ps.mulPose(Axis.YP.rotationDegrees(-35));
                } else {
                    ps.mulPose(Axis.YP.rotationDegrees(-90));
                    ps.mulPose(Axis.XP.rotationDegrees(-8));
                    ps.mulPose(Axis.YP.rotationDegrees(-18));
                }
                ps.scale(s, s, s);
            }
            case GROUND -> {
                float s = 0.6f / len;
                ps.translate(0, -0.25f, 0);
                ps.scale(s, s, s);
            }
            case FIXED -> {
                float s = 0.9f / len;
                ps.mulPose(Axis.YP.rotationDegrees(flat ? 180 : 90));
                ps.scale(s, s, s);
            }
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> {
                ps.translate(0, -0.1f, 0.1f);
                ps.mulPose(Axis.XP.rotationDegrees(-90));
                ps.mulPose(Axis.YP.rotationDegrees(180));
                ps.scale(1.1f, 1.1f, 1.1f);
                cx = cy = cz = 0;
            }
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> {
                ps.translate(0, 0, -0.2f);
                ps.mulPose(Axis.YP.rotationDegrees(0));
                cx = cy = cz = 0;
            }
            default -> {
                float s = 0.8f / len;
                ps.scale(s, s, s);
            }
        }
        ps.translate(-cx, -cy, -cz);
        for (String part : mesh.parts.keySet()) {
            if (part.equals("silencer") && def.silencer && !silencer) continue;
            MeshRenderer.part(mesh, part, ps, buf, light, 0xFFFFFFFF, MeshRenderer.WEAPON_TEX);
        }
        ps.popPose();
    }
}
