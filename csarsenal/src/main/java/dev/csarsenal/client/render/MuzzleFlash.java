package dev.csarsenal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.csarsenal.CsArsenal;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Muzzle flash drawn in weapon model space (the barrel points to -Z). */
public final class MuzzleFlash {
    public static final ResourceLocation FRONT = CsArsenal.id("textures/fx/muzzle_front.png");
    public static final ResourceLocation SIDE = CsArsenal.id("textures/fx/muzzle_side.png");

    public static void render(PoseStack ps, MultiBufferSource buf, Vector3f muzzle, float size, float alpha, float spin) {
        if (alpha <= 0.01f) return;
        int a = (int) (alpha * 255);
        ps.pushPose();
        ps.translate(muzzle.x, muzzle.y, muzzle.z);
        ps.mulPose(Axis.ZP.rotationDegrees(spin));
        // front star (faces along the barrel)
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(FRONT));
        Matrix4f m = ps.last().pose();
        float s = size * 0.6f;
        quad(vc, ps, m, -s, -s, -0.01f, s, -s, -0.01f, s, s, -0.01f, -s, s, -0.01f, a);
        // side flames: two crossed quads along -Z
        VertexConsumer sv = buf.getBuffer(RenderType.eyes(SIDE));
        float L = size * 2.2f, w = size * 0.45f;
        quad(sv, ps, m, 0, -w, 0, 0, -w, -L, 0, w, -L, 0, w, 0, a);
        quad(sv, ps, m, -w, 0, 0, -w, 0, -L, w, 0, -L, w, 0, 0, a);
        ps.popPose();
    }

    private static void quad(VertexConsumer vc, PoseStack ps, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, int a) {
        v(vc, ps, m, x0, y0, z0, 0, 0, a);
        v(vc, ps, m, x1, y1, z1, 1, 0, a);
        v(vc, ps, m, x2, y2, z2, 1, 1, a);
        v(vc, ps, m, x3, y3, z3, 0, 1, a);
    }

    private static void v(VertexConsumer vc, PoseStack ps, Matrix4f m, float x, float y, float z, float u, float vv, int a) {
        vc.addVertex(m, x, y, z).setColor(255, 255, 255, a).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(ps.last(), 0, 0, 1);
    }

    private MuzzleFlash() {
    }
}
