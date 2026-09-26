package dev.csarsenal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.csarsenal.anim.Skeleton;
import dev.csarsenal.anim.WeaponGeometry;
import dev.csarsenal.client.ClientData;
import dev.csarsenal.weapon.CsItem;
import dev.csarsenal.weapon.GrenadeItem;
import dev.csarsenal.weapon.WeaponDef;
import dev.csarsenal.weapon.WeaponItem;
import dev.csarsenal.weapon.WeaponState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import java.util.function.Function;

/** Renders players as the CS operator driven by {@link Skeleton} (the same pose the hitboxes use). */
public final class AgentRenderer {

    public static boolean render(Player p, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        Mesh agent = Meshes.get("agent");
        if (agent == null) return false;
        if (p.isSleeping() || p.isFallFlying() || p.isVisuallySwimming() || p.isAutoSpinAttack()) return false;
        if (p.isInvisible()) return true;
        Skeleton sk = AgentPose.compute(p, pt);
        String team = ClientData.teamSkin(p);
        Function<String, ResourceLocation> tex = m -> Meshes.agentTexture(team, m);
        boolean armor = ClientData.armor(p) > 0;
        boolean helmet = ClientData.helmet(p);
        ps.pushPose();
        float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        if (p.deathTime > 0) {
            float f = Math.min(1f, Mth.sqrt((p.deathTime + pt - 1f) / 20f * 1.6f));
            ps.mulPose(Axis.ZP.rotationDegrees(f * 90f));
        }
        MeshRenderer.overlay = LivingEntityRenderer.getOverlayCoords(p, 0f);
        for (int b = 0; b < Skeleton.COUNT; b++) {
            ps.pushPose();
            ps.mulPose(sk.bones[b]);
            MeshRenderer.part(agent, Skeleton.NAMES[b], ps, buf, light, 0xFFFFFFFF, tex);
            if (b == Skeleton.CHEST && armor) MeshRenderer.part(agent, "vest", ps, buf, light, 0xFFFFFFFF, tex);
            if (b == Skeleton.HEAD && helmet) MeshRenderer.part(agent, "helmet", ps, buf, light, 0xFFFFFFFF, tex);
            ps.popPose();
        }
        MeshRenderer.overlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
        // weapon
        ItemStack held = p.getMainHandItem();
        WeaponDef def = CsItem.defOf(held);
        if (def != null && sk.hasGun) {
            Mesh gm = Meshes.get(def.mesh);
            if (gm != null) {
                boolean silencer = def.silencer && held.getItem() instanceof WeaponItem && WeaponItem.state(held).silencer();
                renderWeapon(gm, def, sk, ps, buf, light, silencer, p, pt, false);
                if (sk.hasGun2) renderWeapon(gm, def, sk, ps, buf, light, silencer, p, pt, true);
            }
        }
        ps.popPose();
        return true;
    }

    private static void renderWeapon(Mesh gm, WeaponDef def, Skeleton sk, PoseStack ps, MultiBufferSource buf, int light, boolean silencer, Player p, float pt, boolean second) {
        ps.pushPose();
        ps.mulPose(second ? sk.gun2 : sk.gun);
        for (String part : gm.parts.keySet()) {
            if (part.equals("mag") && sk.magMode != 0) continue;
            if (part.equals("silencer") && !silencer) continue;
            if ((part.equals("pin") || part.equals("spoon") || part.equals("body")) && def.category == WeaponDef.Category.GRENADE && !sk.grenadeVisible) continue;
            MeshRenderer.part(gm, part, ps, buf, light, 0xFFFFFFFF, MeshRenderer.WEAPON_TEX);
        }
        AgentPose.State st = AgentPose.state(p);
        double since = AgentPose.now() - st.lastShot;
        if (def.isGun() && since < 0.05 && !st.silenced && !(def.silencer && silencer)) {
            WeaponGeometry g = WeaponGeometry.get(def.mesh);
            MuzzleFlash.render(ps, buf, new Vector3f(g.muzzle), flashSize(def), 1f - (float) (since / 0.05), (float) (st.lastShot * 997 % 360));
        }
        ps.popPose();
        if (!second && sk.magMode == 1 && gm.has("mag")) {
            ps.pushPose();
            ps.mulPose(sk.magInHand);
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

    private AgentRenderer() {
    }

    @SuppressWarnings("unused")
    private static boolean grenade(ItemStack s) {
        return s.getItem() instanceof GrenadeItem;
    }

    @SuppressWarnings("unused")
    private static WeaponState st(ItemStack s) {
        return WeaponItem.state(s);
    }
}
