package dev.csarsenal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.function.Function;

/** Emits mesh triangles into Minecraft entity render types (triangles are sent as degenerate quads). */
public final class MeshRenderer {
    public static final Function<String, ResourceLocation> WEAPON_TEX = Meshes::material;

    public static void part(Mesh mesh, String part, PoseStack ps, MultiBufferSource buf, int light, int argb, Function<String, ResourceLocation> tex) {
        if (mesh == null) return;
        for (Mesh.Chunk c : mesh.part(part)) chunk(c, ps.last(), buf, light, argb, tex.apply(c.material), false);
    }

    public static void all(Mesh mesh, PoseStack ps, MultiBufferSource buf, int light, int argb, Function<String, ResourceLocation> tex, String... skip) {
        if (mesh == null) return;
        outer:
        for (var e : mesh.parts.entrySet()) {
            for (String s : skip) if (s.equals(e.getKey())) continue outer;
            for (Mesh.Chunk c : e.getValue()) chunk(c, ps.last(), buf, light, argb, tex.apply(c.material), false);
        }
    }

    public static void emissivePart(Mesh mesh, String part, PoseStack ps, MultiBufferSource buf, int argb, Function<String, ResourceLocation> tex) {
        if (mesh == null) return;
        for (Mesh.Chunk c : mesh.part(part)) chunk(c, ps.last(), buf, 0xF000F0, argb, tex.apply(c.material), true);
    }

    /** overlay used for the next draws (hurt flash); reset to NO_OVERLAY after use */
    public static int overlay = OverlayTexture.NO_OVERLAY;

    private static final Matrix4f M = new Matrix4f();
    private static final Matrix3f N = new Matrix3f();

    public static void chunk(Mesh.Chunk c, PoseStack.Pose pose, MultiBufferSource buf, int light, int argb, ResourceLocation texture, boolean emissive) {
        VertexConsumer vc = buf.getBuffer(emissive ? RenderType.eyes(texture) : RenderType.entityCutoutNoCull(texture));
        Matrix4f m = M.set(pose.pose());
        Matrix3f n = N.set(pose.normal());
        float[] p = c.pos;
        byte[] nr = c.nrm;
        float[] uv = c.uv;
        int[] idx = c.idx;
        int overlay = MeshRenderer.overlay;
        for (int t = 0; t < idx.length; t += 3) {
            for (int k = 0; k < 4; k++) {
                int i = idx[t + Math.min(k, 2)];
                float x = p[i * 3], y = p[i * 3 + 1], z = p[i * 3 + 2];
                float tx = m.m00() * x + m.m10() * y + m.m20() * z + m.m30();
                float ty = m.m01() * x + m.m11() * y + m.m21() * z + m.m31();
                float tz = m.m02() * x + m.m12() * y + m.m22() * z + m.m32();
                float nx = nr[i * 3] / 127f, ny = nr[i * 3 + 1] / 127f, nz = nr[i * 3 + 2] / 127f;
                float tnx = n.m00() * nx + n.m10() * ny + n.m20() * nz;
                float tny = n.m01() * nx + n.m11() * ny + n.m21() * nz;
                float tnz = n.m02() * nx + n.m12() * ny + n.m22() * nz;
                float l = (float) Math.sqrt(tnx * tnx + tny * tny + tnz * tnz);
                if (l > 1e-6f) {
                    tnx /= l;
                    tny /= l;
                    tnz /= l;
                }
                vc.addVertex(tx, ty, tz, argb, uv[i * 2], uv[i * 2 + 1], overlay, light, tnx, tny, tnz);
            }
        }
    }

    private MeshRenderer() {
    }
}
