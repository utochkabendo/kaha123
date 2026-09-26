package dev.csarsenal.client.gui;

import dev.csarsenal.Config;
import dev.csarsenal.client.ClientData;
import dev.csarsenal.client.Keys;
import dev.csarsenal.network.Packets;
import dev.csarsenal.registry.ModItems;
import dev.csarsenal.weapon.WeaponDef;
import dev.csarsenal.weapon.Weapons;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.csarsenal.client.render.Mesh;
import dev.csarsenal.client.render.MeshRenderer;
import dev.csarsenal.client.render.Meshes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * CS style buy menu (B): categories on the left (keys 1-6), weapon cards on the right (keys 1-9 inside a category),
 * the stats of the weapon under the mouse at the bottom and the player's money at the top.
 */
public final class BuyMenuScreen extends Screen {
    private static final String[][] CATS = {
            {"csarsenal.buy.pistols", "glock", "usp_s", "p2000", "p250", "fiveseven", "tec9", "cz75a", "elite", "deagle", "revolver"},
            {"csarsenal.buy.heavy", "nova", "xm1014", "sawedoff", "mag7", "m249", "negev"},
            {"csarsenal.buy.smgs", "mac10", "mp9", "mp7", "mp5sd", "ump45", "p90", "bizon"},
            {"csarsenal.buy.rifles", "galilar", "famas", "ak47", "m4a4", "m4a1_s", "sg556", "aug", "ssg08", "awp", "g3sg1", "scar20"},
            {"csarsenal.buy.grenades", "hegrenade", "flashbang", "smokegrenade", "molotov", "incgrenade", "decoy"},
            {"csarsenal.buy.gear", "kevlar", "assaultsuit", "defuser", "taser", "knife", "knife_karambit", "c4"},
    };
    private static final int BG = 0xE6161A1F, PANEL = 0xF01C2127, CARD = 0xFF232931, CARD_HOVER = 0xFF303843, ACCENT = 0xFFE8A33D,
            TEXT = 0xFFE9E9E9, DIM = 0xFF8E969F, MONEY = 0xFF8FD35E, RED = 0xFFE0524A;

    private static int cat = 3;   // remembered between openings (rifles first, like the usual buy)
    private boolean keyStage;
    private String hovered;

    // layout (computed in init)
    private int px, py, pw, ph, tabW, gridX, gridY, gridW, gridH, cols, cardW, cardH, statsY;

    public BuyMenuScreen() {
        super(Component.translatable("csarsenal.buy.title"));
    }

    @Override
    protected void init() {
        pw = Math.min(600, width - 12);
        ph = Math.min(330, height - 12);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        tabW = Math.max(78, Math.min(120, pw / 5));
        gridX = px + tabW + 8;
        gridY = py + 26;
        gridW = px + pw - 6 - gridX;
        int statsH = Math.max(34, Math.min(54, ph / 6));
        statsY = py + ph - statsH - 14;
        gridH = statsY - 4 - gridY;
        cols = gridW >= 420 ? 4 : 3;
        cardW = (gridW - (cols - 1) * 4) / cols;
        int maxRows = 4;
        cardH = Math.max(26, Math.min(60, (gridH - (maxRows - 1) * 4) / maxRows));
    }

    private boolean pays() {
        return Config.economy() && Minecraft.getInstance().player != null && !Minecraft.getInstance().player.isCreative();
    }

    private int money() {
        return Minecraft.getInstance().player == null ? 0 : ClientData.money(Minecraft.getInstance().player);
    }

    private static int price(String id) {
        WeaponDef d = Weapons.byId(id);
        return d == null ? 0 : d.price;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0xA0080A0C, 0xC0080A0C);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        g.fill(px, py, px + pw, py + ph, BG);
        // title bar + money
        g.fill(px, py, px + pw, py + 20, PANEL);
        g.fill(px, py + 20, px + pw, py + 21, ACCENT);
        g.drawString(font, title.getString().toUpperCase(), px + 8, py + 6, TEXT, false);
        String m = pays() ? "$" + money() : Component.translatable("csarsenal.buy.free").getString();
        g.drawString(font, m, px + pw - 8 - font.width(m), py + 6, MONEY, false);
        // categories
        for (int c = 0; c < CATS.length; c++) {
            int ty = gridY + c * 22;
            boolean sel = c == cat, hov = in(mx, my, px + 4, ty, tabW, 20);
            g.fill(px + 4, ty, px + 4 + tabW, ty + 20, sel ? CARD_HOVER : hov ? CARD : PANEL);
            if (sel) g.fill(px + 4, ty, px + 6, ty + 20, ACCENT);
            g.drawString(font, String.valueOf(c + 1), px + 10, ty + 6, sel ? ACCENT : DIM, false);
            g.drawString(font, Component.translatable(CATS[c][0]).getString().toUpperCase(), px + 22, ty + 6, sel ? TEXT : DIM, false);
        }
        // cards
        hovered = null;
        String[] items = CATS[cat];
        int money = money();
        for (int i = 1; i < items.length; i++) {
            int k = i - 1, cx = gridX + (k % cols) * (cardW + 4), cy = gridY + (k / cols) * (cardH + 4);
            if (cy + cardH > statsY - 2) break;
            String id = items[i];
            boolean hov = in(mx, my, cx, cy, cardW, cardH);
            if (hov) hovered = id;
            boolean afford = !pays() || money >= price(id);
            g.fill(cx, cy, cx + cardW, cy + cardH, hov ? CARD_HOVER : CARD);
            if (hov) g.renderOutline(cx, cy, cardW, cardH, afford ? 0xFFD8D8D8 : RED);
            // model across the card, slot number top left, name and price along the bottom
            Item3D.draw(g, id, cx + 12, cy + 3, cardW - 18, cardH - 16);
            g.drawString(font, String.valueOf(i), cx + 4, cy + 3, DIM, false);
            int p = price(id);
            String ps = p > 0 ? "$" + p : "";
            String name = font.plainSubstrByWidth(Component.translatable("item.csarsenal." + id).getString(), cardW - 12 - font.width(ps));
            g.drawString(font, name, cx + 4, cy + cardH - 10, afford ? TEXT : DIM, false);
            g.drawString(font, ps, cx + cardW - 4 - font.width(ps), cy + cardH - 10, afford ? MONEY : RED, false);
        }
        // stats of the hovered weapon
        stats(g, hovered);
        g.drawCenteredString(font, Component.translatable("csarsenal.buy.keys"), px + pw / 2, py + ph - 11, DIM);
        if (pays() && money < 1000) {
            String hint = Component.translatable("csarsenal.buy.hint").getString();
            g.drawString(font, hint, px + pw - 8 - font.width(hint) - font.width(m) - 12, py + 6, DIM, false);
        }
        // no widgets: vanilla Screen.render would paint the background over the panel again
    }

    private void stats(GuiGraphics g, String id) {
        int sx = gridX, sw = gridW, sy = statsY, sh = py + ph - 14 - statsY;
        g.fill(sx, sy, sx + sw, sy + sh, PANEL);
        WeaponDef d = id == null ? null : Weapons.byId(id);
        if (d == null) return;
        g.drawString(font, Component.translatable("item.csarsenal." + id).getString(), sx + 6, sy + 4, TEXT, false);
        if (d.killReward > 0 && d.isGun() || d.category == WeaponDef.Category.KNIFE) {
            String r = Component.translatable("csarsenal.buy.reward").getString() + " $" + d.killReward;
            g.drawString(font, r, sx + sw - 6 - font.width(r), sy + 4, MONEY, false);
        }
        if (!d.isGun()) return;
        int rows = sh >= 48 ? 2 : 1;
        String[] labels = {"csarsenal.buy.damage", "csarsenal.buy.pen", "csarsenal.buy.rate", "csarsenal.buy.speed"};
        float rpm = 60f / Math.max(0.01f, d.cycleTime);
        float[] vals = {d.damage / 115f, d.armorPenetration, Math.min(1f, rpm / 1100f), d.maxSpeed / 250f};
        String[] txt = {String.valueOf(Math.round(d.damage)), Math.round(d.armorPenetration * 100) + "%", Math.round(rpm) + " RPM", String.valueOf(Math.round(d.maxSpeed))};
        int colW = (sw - 12) / (rows == 2 ? 2 : 4);
        for (int i = 0; i < 4; i++) {
            int col = rows == 2 ? i % 2 : i, row = rows == 2 ? i / 2 : 0;
            int bx = sx + 6 + col * colW, by = sy + 16 + row * 16;
            String l = Component.translatable(labels[i]).getString();
            g.drawString(font, font.plainSubstrByWidth(l, colW / 2 - 4), bx, by, DIM, false);
            int barX = bx + colW / 2, barW = colW / 2 - 34;
            g.fill(barX, by + 2, barX + barW, by + 6, 0xFF3A424C);
            g.fill(barX, by + 2, barX + Math.round(barW * Math.min(1, vals[i])), by + 6, 0xFFD9DCE0);
            g.drawString(font, txt[i], barX + barW + 4, by, TEXT, false);
        }
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private void buy(String id) {
        PacketDistributor.sendToServer(new Packets.Buy(id));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f));
    }

    private void select(int c) {
        if (c < 0 || c >= CATS.length) return;
        cat = c;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6f, 0.25f));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int c = 0; c < CATS.length; c++) {
                if (in(mx, my, px + 4, gridY + c * 22, tabW, 20)) {
                    select(c);
                    keyStage = false;
                    return true;
                }
            }
            if (hovered != null) {
                buy(hovered);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (Keys.BUY.matches(key, scan)) {
            onClose();
            return true;
        }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            int n = key - GLFW.GLFW_KEY_1;
            if (!keyStage) {
                if (n < CATS.length) {
                    select(n);
                    keyStage = true;
                }
            } else {
                String[] items = CATS[cat];
                if (n + 1 < items.length) {
                    buy(items[n + 1]);
                    keyStage = false;
                }
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_0) {
            keyStage = false;
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** weapon portraits: guns as a side view fitted to the card, everything else as its item model */
    static final class Item3D {
        static void draw(GuiGraphics g, String id, int x, int y, int w, int h) {
            WeaponDef d = Weapons.byId(id);
            Mesh mesh = d == null ? null : Meshes.get(d.mesh);
            if (d != null && mesh != null && (d.isGun() || d.category == WeaponDef.Category.KNIFE)) {
                float len = Math.max(0.02f, mesh.max.z - mesh.min.z), hgt = Math.max(0.02f, mesh.max.y - mesh.min.y);
                float s = Math.min(w / len, h / hgt) * 0.92f;
                float cx = (mesh.min.x + mesh.max.x) / 2, cy = (mesh.min.y + mesh.max.y) / 2, cz = (mesh.min.z + mesh.max.z) / 2;
                PoseStack ps = g.pose();
                ps.pushPose();
                ps.translate(x + w / 2f, y + h / 2f, 150);
                ps.scale(s, -s, s);
                ps.mulPose(Axis.YP.rotationDegrees(-90 + 12));   // muzzle to the right, turned a little towards the viewer
                ps.mulPose(Axis.ZP.rotationDegrees(-4));
                ps.translate(-cx, -cy, -cz);
                Lighting.setupForFlatItems();
                MultiBufferSource.BufferSource bs = Minecraft.getInstance().renderBuffers().bufferSource();
                MeshRenderer.all(mesh, ps, bs, LightTexture.FULL_BRIGHT, 0xFFFFFFFF, MeshRenderer.WEAPON_TEX);
                bs.endBatch();
                Lighting.setupFor3DItems();
                ps.popPose();
                return;
            }
            var item = ModItems.get(id);
            if (item == null) return;
            int size = Math.min(w, h);
            float sc = size / 16f;
            g.pose().pushPose();
            g.pose().translate(x + (w - size) / 2f, y + (h - size) / 2f, 0);
            g.pose().scale(sc, sc, 1);
            g.renderItem(new ItemStack(item), 0, 0);
            g.pose().popPose();
        }
    }
}
