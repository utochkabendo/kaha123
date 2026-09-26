package dev.csarsenal.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.csarsenal.ClientConfig;
import dev.csarsenal.CsArsenal;
import dev.csarsenal.client.ClientData;
import dev.csarsenal.client.fx.ClientSounds;
import dev.csarsenal.client.weapon.ClientWeapon;
import dev.csarsenal.network.Packets;
import dev.csarsenal.weapon.C4Item;
import dev.csarsenal.weapon.RecoilPattern;
import dev.csarsenal.weapon.WeaponDef;
import dev.csarsenal.weapon.Weapons;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** CS2 style HUD layers. */
public final class CsHud {
    public static final ResourceLocation SCOPE = CsArsenal.id("textures/gui/scope.png");
    public static final ResourceLocation SCOPE_DOT = CsArsenal.id("textures/gui/scope_dot.png");

    private record Kill(Packets.KillFeed k, long time) {
    }

    private static final List<Kill> KILLS = new ArrayList<>();
    private static double flashStart = -100, flashDur, flashStrength;

    public static boolean active() {
        return ClientWeapon.INSTANCE.def != null;
    }

    public static void onKill(Packets.KillFeed k) {
        KILLS.add(new Kill(k, System.currentTimeMillis()));
        if (KILLS.size() > 6) KILLS.remove(0);
    }

    public static void onFlash(Packets.Flash f) {
        double now = System.nanoTime() / 1e9;
        double remaining = flashStart + flashDur - now;
        if (f.duration() > remaining) {
            flashStart = now;
            flashDur = f.duration();
            flashStrength = Mth.clamp(f.strength(), 0.2, 1);
        }
        ClientSounds.self("grenade.flash_ring", (float) Mth.clamp(f.strength() * 1.2, 0.2, 1), 1f);
    }

    // =================================================================================================== layers
    public static void crosshair(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.options.hideGui || !mc.options.getCameraType().isFirstPerson()) return;
        ClientWeapon w = ClientWeapon.INSTANCE;
        if (w.isScoped()) return;
        WeaponDef d = w.def;
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        if (d.category == WeaponDef.Category.SNIPER) {
            // CS: snipers have no crosshair when unscoped
            return;
        }
        int color = ClientConfig.integer(ClientConfig.CROSSHAIR_COLOR, 0xFF32FF32);
        double size = ClientConfig.num(ClientConfig.CROSSHAIR_SIZE, 4.0);
        double gap = ClientConfig.num(ClientConfig.CROSSHAIR_GAP, 1.0);
        double th = ClientConfig.num(ClientConfig.CROSSHAIR_THICKNESS, 1.0);
        float gy = 0;
        float gx = 0;
        double fovY = Math.toRadians(mc.options.fov().get());
        double pxPerRad = (g.guiHeight() / 2.0) / Math.tan(fovY / 2);
        if (ClientConfig.bool(ClientConfig.DYNAMIC_CROSSHAIR, true) && d.isGun()) {
            gap += Math.tan(w.currentInaccuracy()) * pxPerRad * 0.8;
        }
        if (ClientConfig.bool(ClientConfig.CROSSHAIR_FOLLOW_RECOIL, false)) {
            gy = (float) (-Math.toRadians(w.punch[1] * RecoilPattern.RECOIL_SCALE * (1 - RecoilPattern.VIEW_TRACKING)) * pxPerRad);
            gx = (float) (Math.toRadians(w.punch[0] * RecoilPattern.RECOIL_SCALE * (1 - RecoilPattern.VIEW_TRACKING)) * pxPerRad);
        }
        gap = Math.min(gap, 60);
        boolean outline = ClientConfig.bool(ClientConfig.CROSSHAIR_OUTLINE, true);
        float t = (float) Math.max(0.5, th);
        float x = cx + gx, y = cy + gy;
        float s = (float) size, gp = (float) gap;
        bar(g, x - gp - s, y - t / 2, x - gp, y + t / 2, color, outline);
        bar(g, x + gp, y - t / 2, x + gp + s, y + t / 2, color, outline);
        bar(g, x - t / 2, y - gp - s, x + t / 2, y - gp, color, outline);
        bar(g, x - t / 2, y + gp, x + t / 2, y + gp + s, color, outline);
        if (ClientConfig.bool(ClientConfig.CROSSHAIR_DOT, false)) bar(g, x - t / 2, y - t / 2, x + t / 2, y + t / 2, color, outline);
        // hit confirmation (brief): not in CS by default, keep subtle
        targetId(g, mc);
    }

    private static void bar(GuiGraphics g, float x0, float y0, float x1, float y1, int color, boolean outline) {
        g.pose().pushPose();
        int ix0 = Math.round(x0), iy0 = Math.round(y0), ix1 = Math.max(Math.round(x1), ix0 + 1), iy1 = Math.max(Math.round(y1), iy0 + 1);
        if (outline) g.fill(ix0 - 1, iy0 - 1, ix1 + 1, iy1 + 1, 0xC0000000);
        g.fill(ix0, iy0, ix1, iy1, color);
        g.pose().popPose();
    }

    private static void targetId(GuiGraphics g, Minecraft mc) {
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null) return;
        Vec3 eye = me.getEyePosition();
        Vec3 look = me.getLookAngle();
        Player best = null;
        double bestD = 80;
        for (Player p : mc.level.players()) {
            if (p == me || p.isInvisible()) continue;
            AABB bb = p.getBoundingBox();
            Optional<Vec3> hit = bb.clip(eye, eye.add(look.scale(80)));
            if (hit.isPresent()) {
                double dd = hit.get().distanceTo(eye);
                if (dd < bestD) {
                    bestD = dd;
                    best = p;
                }
            }
        }
        if (best == null) return;
        int team = ClientData.team(best);
        int col = team == 1 ? 0xFFE8B84A : team == 2 ? 0xFF6CA8FF : 0xFFFFFFFF;
        Font f = mc.font;
        String name = best.getDisplayName().getString();
        g.drawString(f, name, g.guiWidth() / 2 - f.width(name) / 2, g.guiHeight() / 2 + 18, col, true);
    }

    public static void scope(GuiGraphics g, DeltaTracker dt) {
        ClientWeapon w = ClientWeapon.INSTANCE;
        if (!active() || !w.isScoped()) return;
        int W = g.guiWidth(), H = g.guiHeight();
        boolean dot = w.def == Weapons.AUG || w.def == Weapons.SG556;
        int size = H;
        int x0 = (W - size) / 2;
        RenderSystem.enableBlend();
        g.blit(dot ? SCOPE_DOT : SCOPE, x0, 0, 0, 0, size, size, size, size);
        if (!dot) {
            g.fill(0, 0, x0 + 1, H, 0xFF000000);
            g.fill(x0 + size - 1, 0, W, H, 0xFF000000);
            // thin cross hair across the lens (CS style), drawn with the spread as a dot size hint
            g.fill(x0, H / 2, x0 + size, H / 2 + 1, 0xFF000000);
            g.fill(W / 2, 0, W / 2 + 1, H, 0xFF000000);
        } else {
            g.fill(0, 0, x0 + 1, H, 0xE0000000);
            g.fill(x0 + size - 1, 0, W, H, 0xE0000000);
        }
    }

    public static void flash(GuiGraphics g, DeltaTracker dt) {
        double now = System.nanoTime() / 1e9;
        double t = now - flashStart;
        if (t < 0 || t > flashDur) return;
        double hold = flashDur * 0.45;
        double a = t < hold ? 1 : 1 - (t - hold) / (flashDur - hold);
        a = Math.pow(Mth.clamp(a, 0, 1), 0.8) * flashStrength;
        int alpha = (int) (Mth.clamp(a, 0, 1) * 255);
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (alpha << 24) | 0xFFFFFF);
    }

    public static void status(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || !active()) return;
        Font f = mc.font;
        int W = g.guiWidth(), H = g.guiHeight();
        ClientWeapon w = ClientWeapon.INSTANCE;
        // health / armor (bottom left)
        int hp = Math.max(0, Math.round(p.getHealth() / Math.max(1e-3f, dev.csarsenal.Config.damageScale())));
        int armor = ClientData.armor(p);
        boolean helmet = ClientData.helmet(p);
        int y = H - 34;
        g.fill(6, y - 4, 150, H - 6, 0x70000000);
        big(g, f, "✚ " + hp, 12, y, hp <= 20 ? 0xFFFF5040 : 0xFFE8E8E8);
        big(g, f, (helmet ? "⛨ " : "◈ ") + armor, 82, y, 0xFFE8E8E8);
        // ammo (bottom right)
        WeaponDef d = w.def;
        String name = Component.translatable("item.csarsenal." + d.id).getString();
        if (d.isGun() && d.category != WeaponDef.Category.TASER) {
            String ammo = String.valueOf(w.ammo);
            String res = " / " + w.reserve;
            int aw = f.width(ammo) * 2 + f.width(res);
            g.fill(W - aw - 22, y - 16, W - 6, H - 6, 0x70000000);
            big(g, f, ammo, W - aw - 14, y, w.ammo <= Math.max(1, d.magSize / 5) ? 0xFFFF5040 : 0xFFE8E8E8);
            g.drawString(f, res, W - 14 - f.width(res), y + 6, 0xFFB0B0B0, true);
            g.drawString(f, name, W - 14 - f.width(name), y - 12, 0xFFB0B0B0, true);
            if (w.burstMode()) g.drawString(f, "BURST", W - 14 - f.width("BURST") - f.width(name) - 8, y - 12, 0xFFE0C060, true);
        } else {
            g.fill(W - f.width(name) - 22, y - 4, W - 6, H - 6, 0x70000000);
            g.drawString(f, name, W - 14 - f.width(name), y + 4, 0xFFE8E8E8, true);
        }
        money(g, f, p);
        // C4 planting progress
        if (p.isUsingItem() && p.getUseItem().getItem() instanceof C4Item) {
            float prog = Mth.clamp((C4Item.PLANT_TICKS - p.getUseItemRemainingTicks()) / (float) C4Item.PLANT_TICKS, 0, 1);
            int bw = 160, bx = W / 2 - bw / 2, by = H / 2 + 40;
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 7, 0xA0000000);
            g.fill(bx, by, bx + (int) (bw * prog), by + 6, 0xFFE0A020);
            String s = Component.translatable("csarsenal.hud.planting").getString();
            g.drawString(f, s, W / 2 - f.width(s) / 2, by - 12, 0xFFFFFFFF, true);
        }
        killFeed(g, f, W);
    }

    private static int lastMoney = -1;
    private static int moneyDelta;
    private static long moneyDeltaTime;

    /** CS money (top left) with the "+$300" of the last change */
    private static void money(GuiGraphics g, Font f, LocalPlayer p) {
        if (!dev.csarsenal.Config.economy() || p.isCreative()) return;
        int m = ClientData.money(p);
        long now = System.currentTimeMillis();
        if (lastMoney >= 0 && m != lastMoney) {
            moneyDelta = now - moneyDeltaTime < 1500 ? moneyDelta + (m - lastMoney) : m - lastMoney;
            moneyDeltaTime = now;
        }
        lastMoney = m;
        String s = "$" + m;
        int w = f.width(s) * 2 + 12;
        g.fill(6, 6, 6 + w, 28, 0x70000000);
        big(g, f, s, 12, 10, 0xFF8FD35E);
        long age = now - moneyDeltaTime;
        if (moneyDelta != 0 && age < 3000) {
            int a = (int) (255 * Math.min(1, (3000 - age) / 600.0));
            String d = (moneyDelta > 0 ? "+$" : "-$") + Math.abs(moneyDelta);
            int col = (moneyDelta > 0 ? 0x8FD35E : 0xE0524A) | (Math.max(8, a) << 24);
            g.drawString(f, d, 12, 32, col, true);
        }
    }

    private static void big(GuiGraphics g, Font f, String s, int x, int y, int col) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(f, s, 0, 0, col, true);
        g.pose().popPose();
    }

    private static void killFeed(GuiGraphics g, Font f, int W) {
        long now = System.currentTimeMillis();
        KILLS.removeIf(k -> now - k.time > 6000);
        int y = 8;
        for (Kill kk : KILLS) {
            Packets.KillFeed k = kk.k;
            WeaponDef d = Weapons.byIndex(k.weapon());
            String weapon = d != null ? Component.translatable("item.csarsenal." + d.id).getString() : "☠";
            StringBuilder flags = new StringBuilder();
            if ((k.flags() & Packets.KillFeed.BLIND) != 0) flags.append(" ☀");
            if ((k.flags() & Packets.KillFeed.AIR) != 0) flags.append(" ⤴");
            if ((k.flags() & Packets.KillFeed.NOSCOPE) != 0) flags.append(" ⌀");
            if ((k.flags() & Packets.KillFeed.SMOKE) != 0) flags.append(" ☁");
            if ((k.flags() & Packets.KillFeed.WALLBANG) != 0) flags.append(" ▦");
            if ((k.flags() & Packets.KillFeed.HEADSHOT) != 0) flags.append(" ◉");
            String mid = "  " + weapon + flags + "  ";
            int wk = f.width(k.killer()), wm = f.width(mid), wv = f.width(k.victim());
            int total = wk + wm + wv;
            int x = W - total - 12;
            boolean mine = Minecraft.getInstance().player != null && k.killer().equals(Minecraft.getInstance().player.getName().getString());
            g.fill(x - 4, y - 3, W - 8, y + 11, 0x90000000);
            if (mine) g.renderOutline(x - 4, y - 3, W - 8 - (x - 4), 14, 0xFFC03030);
            g.drawString(f, k.killer(), x, y, teamColor(k.killerTeam()), true);
            g.drawString(f, mid, x + wk, y, 0xFFE0E0E0, true);
            g.drawString(f, k.victim(), x + wk + wm, y, teamColor(k.victimTeam()), true);
            y += 16;
        }
    }

    private static int teamColor(int team) {
        return team == 1 ? 0xFFE8B84A : team == 2 ? 0xFF6CA8FF : 0xFFFFFFFF;
    }

    public static void reset() {
        KILLS.clear();
        flashStart = -100;
    }

    private CsHud() {
    }
}
