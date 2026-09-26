package dev.csarsenal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.csarsenal.anim.McPose;
import dev.csarsenal.anim.WeaponGeometry;
import dev.csarsenal.weapon.CsItem;
import dev.csarsenal.weapon.WeaponDef;
import dev.csarsenal.weapon.WeaponItem;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

/** Draws the CS weapon in the hands of the vanilla player model, using the pose that also drives the hitboxes. */
public final class GunLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public GunLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limb, float amount, float pt, float age, float yaw, float pitch) {
        ItemStack held = p.getMainHandItem();
        WeaponDef def = CsItem.defOf(held);
        if (def == null || AgentPose.special(p) || p.isInvisible()) return;
        Mesh gm = Meshes.get(def.mesh);
        if (gm == null) return;
        McPose pose = AgentPose.compute(p, pt);
        if (!pose.hasGun) return;
        boolean silencer = def.silencer && held.getItem() instanceof WeaponItem && WeaponItem.state(held).silencer();
        draw(gm, def, pose, pose.gun, ps, buf, light, silencer, p, true);
        if (pose.hasGun2) draw(gm, def, pose, pose.gun2, ps, buf, light, silencer, p, false);
    }

    private static void draw(Mesh gm, WeaponDef def, McPose pose, org.joml.Matrix4f m, PoseStack ps, MultiBufferSource buf, int light, boolean silencer,
                             AbstractClientPlayer p, boolean main) {
        ps.pushPose();
        ps.mulPose(m);
        float k = ViewModelRenderer.itemScale(def, gm);
        if (k != 1) ps.scale(k, k, k);
        for (String part : gm.parts.keySet()) {
            if (part.equals("mag") && pose.magMode != 0) continue;
            if (part.equals("silencer") && !silencer) continue;
            if (def.category == WeaponDef.Category.GRENADE && !pose.grenadeVisible) continue;
            MeshRenderer.part(gm, part, ps, buf, light, 0xFFFFFFFF, MeshRenderer.WEAPON_TEX);
        }
        AgentPose.State st = AgentPose.state(p);
        double since = AgentPose.now() - st.lastShot;
        if (def.isGun() && since < 0.05 && !st.silenced) {
            WeaponGeometry g = WeaponGeometry.get(def.mesh);
            MuzzleFlash.render(ps, buf, new Vector3f(silencer && g.hasMuzzleSil ? g.muzzleSilenced : g.muzzle), flashSize(def), 1f - (float) (since / 0.05), (float) (st.lastShot * 997 % 360));
        }
        ps.popPose();
        if (main && pose.magMode == 1 && gm.has("mag")) {
            ps.pushPose();
            ps.mulPose(pose.magInHand);
            MeshRenderer.part(gm, "mag", ps, buf, light, 0xFFFFFFFF, MeshRenderer.WEAPON_TEX);
            ps.popPose();
        }
    }

    public static float flashSize(WeaponDef d) {
        return switch (d.category) {
            case PISTOL -> 0.07f;
            case SMG -> 0.08f;
            case SHOTGUN -> 0.16f;
            case SNIPER -> 0.15f;
            case MACHINEGUN -> 0.12f;
            default -> 0.1f;
        };
    }
}
