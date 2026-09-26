package dev.csarsenal.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Axis;
import dev.csarsenal.ClientConfig;
import dev.csarsenal.CsArsenal;
import dev.csarsenal.client.ClientData;
import dev.csarsenal.entity.C4Entity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * CS style radar: a top down slice of the world at the player's floor (walls bright, floors in their map colour,
 * shaded by height), turning with the view. Teammates are always shown, enemies only while in line of sight,
 * the planted bomb in orange. The map texture is rebuilt a few rows per tick around the player.
 */
public final class Radar {
    private static final int N = 192;                     // texture size = blocks covered
    private static final int ROWS_PER_TICK = 12;
    private static final ResourceLocation ID = CsArsenal.id("radar");
    private static DynamicTexture tex;
    private static int originX, originZ, baseY, row;       // world coords of texel (0,0), slice height
    private static boolean valid;
    private static Level lastLevel;

    public static final int SIZE = 92;                      // gui px

    private static void ensure(Minecraft mc) {
        if (tex == null) {
            tex = new DynamicTexture(N, N, true);
            mc.getTextureManager().register(ID, tex);
        }
    }

    /** client tick: keep the map centred on the player and refresh it progressively */
    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || !CsHud.active() || !ClientConfig.bool(ClientConfig.RADAR, true)) return;
        ensure(mc);
        int px = Mth.floor(p.getX()), pz = Mth.floor(p.getZ()), py = Mth.floor(p.getY() + 0.1);
        int margin = N / 2 - ClientConfig.integer(ClientConfig.RADAR_SCALE, 28) * 3 / 2;
        boolean recentre = !valid || mc.level != lastLevel || Math.abs(px - (originX + N / 2)) > margin || Math.abs(pz - (originZ + N / 2)) > margin
                || Math.abs(py - baseY) >= 3;
        if (recentre) {
            originX = px - N / 2;
            originZ = pz - N / 2;
            baseY = py;
            lastLevel = mc.level;
            valid = true;
            row = 0;
            // clear so stale parts never show at the wrong place
            NativeImage img = tex.getPixels();
            if (img != null) img.fillRect(0, 0, N, N, 0);
        }
        NativeImage img = tex.getPixels();
        if (img == null) return;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int r = 0; r < ROWS_PER_TICK; r++) {
            int z = row;
            for (int x = 0; x < N; x++) img.setPixelRGBA(x, z, sample(mc.level, originX + x, originZ + z, m));
            row = (row + 1) % N;
        }
        tex.upload();
    }

    /** colour (ABGR) of one column around the slice height */
    private static int sample(Level level, int x, int z, BlockPos.MutableBlockPos m) {
        for (int dy = 2; dy >= -10; dy--) {
            m.set(x, baseY + dy, z);
            BlockState st = level.getBlockState(m);
            if (st.isAir() || st.getCollisionShape(level, m).isEmpty()) continue;
            MapColor mc = st.getMapColor(level, m);
            int rgb = mc == MapColor.NONE ? 0x808080 : mc.col;
            int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
            if (dy >= 1) {
                // wall at head height: bright edge like the CS radar outlines
                r = 175 + r / 6;
                g = 178 + g / 6;
                b = 182 + b / 6;
                return abgr(235, r, g, b);
            }
            // floor: map colour pushed towards grey, lighter the higher it is
            float k = Mth.clamp(1f + (dy + 1) * 0.07f, 0.35f, 1.15f);
            r = (int) ((r * 0.45f + 70) * k);
            g = (int) ((g * 0.45f + 72) * k);
            b = (int) ((b * 0.45f + 76) * k);
            return abgr(215, Math.min(255, r), Math.min(255, g), Math.min(255, b));
        }
        return abgr(150, 12, 14, 16);
    }

    private static int abgr(int a, int r, int g, int b) {
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    /** draws the radar at (x, y); returns its height */
    public static int render(GuiGraphics g, int x, int y, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null || tex == null || !valid || !ClientConfig.bool(ClientConfig.RADAR, true)) return 0;
        int S = SIZE;
        float range = ClientConfig.integer(ClientConfig.RADAR_SCALE, 28);
        float scale = (S / 2f) / range;                               // gui px per block
        boolean rotate = ClientConfig.bool(ClientConfig.RADAR_ROTATE, true);
        float yaw = me.getViewYRot(pt);
        float rot = rotate ? 180f - yaw : 0f;
        double mx = Mth.lerp(pt, me.xo, me.getX()), mz = Mth.lerp(pt, me.zo, me.getZ());

        g.fill(x - 2, y - 2, x + S + 2, y + S + 2, 0xB0000000);
        g.fill(x - 1, y - 1, x + S + 1, y + S + 1, 0xFF3A4048);
        g.fill(x, y, x + S, y + S, 0xFF0C0E10);
        g.enableScissor(x, y, x + S, y + S);
        var ps = g.pose();
        ps.pushPose();
        ps.translate(x + S / 2f, y + S / 2f, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(rot));
        ps.scale(scale, scale, 1);
        ps.translate((float) (originX - mx), (float) (originZ - mz), 0);
        g.blit(ID, 0, 0, 0, 0, N, N, N, N);
        ps.popPose();

        // entities
        int myTeam = ClientData.team(me);
        for (Entity e : mc.level.entitiesForRendering()) {
            int col;
            if (e instanceof Player p && p != me && !p.isSpectator()) {
                int t = ClientData.team(p);
                boolean mate = myTeam != 0 && t == myTeam;
                if (!mate && (p.isInvisible() || !me.hasLineOfSight(p))) continue;
                col = mate ? (t == 1 ? 0xFFE8B84A : 0xFF6CA8FF) : 0xFFE0402E;
            } else if (e instanceof C4Entity) {
                col = 0xFFFF9A1E;
            } else continue;
            double dx = Mth.lerp(pt, e.xo, e.getX()) - mx, dz = Mth.lerp(pt, e.zo, e.getZ()) - mz;
            float a = rot * Mth.DEG_TO_RAD;
            float sx = (float) (dx * Mth.cos(a) - dz * Mth.sin(a)) * scale, sy = (float) (dx * Mth.sin(a) + dz * Mth.cos(a)) * scale;
            int ex = Math.round(x + S / 2f + sx), ey = Math.round(y + S / 2f + sy);
            ex = Mth.clamp(ex, x + 2, x + S - 3);
            ey = Mth.clamp(ey, y + 2, y + S - 3);
            g.fill(ex - 2, ey - 2, ex + 2, ey + 2, 0xFF000000);
            g.fill(ex - 1, ey - 1, ex + 1, ey + 1, col);
            if (e instanceof C4Entity) g.fill(ex - 2, ey - 2, ex + 2, ey + 2, col);
        }
        // self: arrow pointing along the view
        ps.pushPose();
        ps.translate(x + S / 2f, y + S / 2f, 0);
        if (!rotate) ps.mulPose(Axis.ZP.rotationDegrees(yaw + 180f));
        for (int i = 0; i < 5; i++) g.fill(-i, -3 + i * 1, i + 1, -2 + i * 1, 0xFFFFFFFF);
        g.fill(-1, 2, 2, 3, 0xFFFFFFFF);
        ps.popPose();
        g.disableScissor();
        return S + 4;
    }

    public static void reset() {
        valid = false;
    }

    private Radar() {
    }
}
