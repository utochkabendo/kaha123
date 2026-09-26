package dev.csarsenal.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.csarsenal.ClientConfig;
import dev.csarsenal.CsArsenal;
import dev.csarsenal.client.render.Mesh;
import dev.csarsenal.client.render.MeshRenderer;
import dev.csarsenal.client.render.Meshes;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Bullet holes, tracers and shell casings living in the world. */
public final class WorldFx {
    private static final RandomSource R = RandomSource.create();

    private record Decal(Vec3 pos, Direction face, ResourceLocation tex, float size, float rot, BlockPos block, long time) {
    }

    private record Tracer(Vec3 a, Vec3 b, long start, boolean thick) {
    }

    private static final class Shell {
        Vec3 pos, prev, vel;
        float yaw, pitch, spin, spinO;
        String mesh;
        int age;
        int bounces;
        boolean resting;
    }

    private record Beam(Vec3 a, Vec3 b, long start) {
    }

    private static final ArrayDeque<Decal> DECALS = new ArrayDeque<>();
    private static final List<Tracer> TRACERS = new ArrayList<>();
    private static final List<Shell> SHELLS = new ArrayList<>();
    private static final List<Beam> BEAMS = new ArrayList<>();

    public static ResourceLocation holeTex(String kind) {
        return CsArsenal.id("textures/fx/hole_" + kind + ".png");
    }

    public static void decal(Vec3 pos, Direction face, String kind, float size, BlockPos block) {
        int max = ClientConfig.integer(ClientConfig.MAX_DECALS, 256);
        if (max <= 0) return;
        DECALS.addLast(new Decal(pos, face, holeTex(kind), size, R.nextFloat() * 360f, block, System.currentTimeMillis()));
        while (DECALS.size() > max) DECALS.removeFirst();
    }

    public static void tracer(Vec3 a, Vec3 b, boolean thick) {
        TRACERS.add(new Tracer(a, b, System.nanoTime(), thick));
    }

    public static void beam(Vec3 a, Vec3 b) {
        BEAMS.add(new Beam(a, b, System.nanoTime()));
    }

    public static void shell(String mesh, Vec3 pos, Vec3 vel, float yaw) {
        if (!ClientConfig.bool(ClientConfig.SHELLS, true)) return;
        Shell s = new Shell();
        s.pos = s.prev = pos;
        s.vel = vel;
        s.yaw = yaw;
        s.pitch = R.nextFloat() * 360f;
        s.spin = 0;
        s.mesh = mesh;
        SHELLS.add(s);
        if (SHELLS.size() > 160) SHELLS.remove(0);
    }

    public static void tick(ClientLevel level) {
        Iterator<Shell> it = SHELLS.iterator();
        while (it.hasNext()) {
            Shell s = it.next();
            s.age++;
            s.prev = s.pos;
            s.spinO = s.spin;
            if (s.age > 20 * 12) {
                it.remove();
                continue;
            }
            if (s.resting) continue;
            s.vel = s.vel.add(0, -0.045, 0).scale(0.98);
            Vec3 next = s.pos.add(s.vel);
            BlockHitResult hit = level.clip(new ClipContext(s.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.BLOCK) {
                Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
                double vn = s.vel.dot(n);
                s.vel = s.vel.subtract(n.scale(vn * 1.45)).scale(0.55);
                s.pos = hit.getLocation().add(n.scale(0.01));
                if (s.bounces++ < 2 && Math.abs(vn) > 0.03) {
                    ClientSounds.at(s.mesh.equals("shell_shotgun") ? "bullet.shell_shotgun" : "bullet.shell", s.pos.x, s.pos.y, s.pos.z, 0.35f, 1f);
                }
                if (n.y > 0.7 && s.vel.lengthSqr() < 0.002) {
                    s.resting = true;
                    s.pitch = 90;
                }
            } else {
                s.pos = next;
                s.spin += 40;
            }
        }
        if (level.getGameTime() % 20 == 0) {
            DECALS.removeIf(d -> level.getBlockState(d.block).isAir());
        }
        long now = System.nanoTime();
        TRACERS.removeIf(t -> now - t.start > 600_000_000L);
        BEAMS.removeIf(b -> now - b.start > 250_000_000L);
    }

    public static void render(PoseStack ps, Camera cam, MultiBufferSource.BufferSource buf, float pt) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 c = cam.getPosition();
        ps.pushPose();
        ps.translate(-c.x, -c.y, -c.z);
        // bullet holes
        for (Decal d : DECALS) {
            if (d.pos.distanceToSqr(c) > 96 * 96) continue;
            int light = LevelRenderer.getLightColor(level, d.block.relative(d.face));
            VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(d.tex));
            ps.pushPose();
            Vec3 p = d.pos.add(Vec3.atLowerCornerOf(d.face.getNormal()).scale(0.003));
            ps.translate(p.x, p.y, p.z);
            ps.mulPose(d.face.getRotation());
            ps.mulPose(Axis.YP.rotationDegrees(d.rot));
            float s = d.size;
            Matrix4f m = ps.last().pose();
            // face rotation maps +Y to the face normal: quad in the XZ plane
            vert(vc, m, ps, -s, 0, -s, 0, 0, light);
            vert(vc, m, ps, -s, 0, s, 0, 1, light);
            vert(vc, m, ps, s, 0, s, 1, 1, light);
            vert(vc, m, ps, s, 0, -s, 1, 0, light);
            ps.popPose();
        }
        // shells
        for (Shell s : SHELLS) {
            Mesh mesh = Meshes.get(s.mesh);
            if (mesh == null) continue;
            Vec3 p = s.prev.lerp(s.pos, pt);
            ps.pushPose();
            ps.translate(p.x, p.y, p.z);
            ps.mulPose(Axis.YP.rotationDegrees(-s.yaw));
            ps.mulPose(Axis.XP.rotationDegrees(s.pitch + Mth.lerp(pt, s.spinO, s.spin)));
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(p));
            MeshRenderer.all(mesh, ps, buf, light, 0xFFFFFFFF, MeshRenderer.WEAPON_TEX);
            ps.popPose();
        }
        // tracers: a bright streak travelling along the bullet path
        long now = System.nanoTime();
        VertexConsumer lv = buf.getBuffer(RenderType.lightning());
        for (Tracer t : TRACERS) {
            double sec = (now - t.start) / 1e9;
            Vec3 dir = t.b.subtract(t.a);
            double len = dir.length();
            if (len < 0.01) continue;
            Vec3 n = dir.scale(1 / len);
            double head = Math.min(len, 12 + sec * 420);
            double tail = Math.max(0, head - 5.5);
            if (tail >= len) continue;
            streak(lv, ps, c, t.a.add(n.scale(tail)), t.a.add(n.scale(head)), t.thick ? 0.028f : 0.016f, 1f, 0.92f, 0.6f, 0.75f);
        }
        for (Beam b : BEAMS) {
            float a = 1f - (now - b.start) / 2.5e8f;
            Vec3 d = b.b.subtract(b.a);
            Vec3 prev = b.a;
            for (int i = 1; i <= 8; i++) {
                Vec3 q = b.a.add(d.scale(i / 8.0)).add(i < 8 ? new Vec3(R.nextGaussian() * 0.06, R.nextGaussian() * 0.06, R.nextGaussian() * 0.06) : Vec3.ZERO);
                streak(lv, ps, c, prev, q, 0.02f, 0.6f, 0.8f, 1f, Math.max(0, a));
                prev = q;
            }
        }
        ps.popPose();
        buf.endBatch(RenderType.lightning());
    }

    private static void vert(VertexConsumer vc, Matrix4f m, PoseStack ps, float x, float y, float z, float u, float v, int light) {
        vc.addVertex(m, x, y, z).setColor(255, 255, 255, 235).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(ps.last(), 0, 1, 0);
    }

    private static void streak(VertexConsumer vc, PoseStack ps, Vec3 cam, Vec3 a, Vec3 b, float w, float r, float g, float bl, float alpha) {
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = b.subtract(a).cross(cam.subtract(mid));
        if (side.lengthSqr() < 1e-10) return;
        side = side.normalize().scale(w);
        Matrix4f m = ps.last().pose();
        int A = (int) (alpha * 255);
        vc.addVertex(m, (float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z)).setColor((int) (r * 255), (int) (g * 255), (int) (bl * 255), 0);
        vc.addVertex(m, (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z)).setColor((int) (r * 255), (int) (g * 255), (int) (bl * 255), 0);
        vc.addVertex(m, (float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z)).setColor((int) (r * 255), (int) (g * 255), (int) (bl * 255), A);
        vc.addVertex(m, (float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z)).setColor((int) (r * 255), (int) (g * 255), (int) (bl * 255), A);
    }

    public static void reset() {
        DECALS.clear();
        TRACERS.clear();
        SHELLS.clear();
        BEAMS.clear();
    }

    private WorldFx() {
    }
}
