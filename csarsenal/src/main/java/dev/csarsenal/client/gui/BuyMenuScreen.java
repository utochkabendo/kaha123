package dev.csarsenal.client.gui;

import dev.csarsenal.client.Keys;
import dev.csarsenal.network.Packets;
import dev.csarsenal.weapon.WeaponDef;
import dev.csarsenal.weapon.Weapons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** CS style buy menu (B). */
public final class BuyMenuScreen extends Screen {
    private static final String[][] COLUMNS = {
            {"csarsenal.buy.pistols", "glock", "usp_s", "p2000", "p250", "fiveseven", "tec9", "cz75a", "elite", "deagle", "revolver"},
            {"csarsenal.buy.heavy", "nova", "xm1014", "sawedoff", "mag7", "m249", "negev"},
            {"csarsenal.buy.smgs", "mac10", "mp9", "mp7", "mp5sd", "ump45", "p90", "bizon"},
            {"csarsenal.buy.rifles", "galilar", "famas", "ak47", "m4a4", "m4a1_s", "sg556", "aug", "ssg08", "awp", "g3sg1", "scar20"},
            {"csarsenal.buy.grenades", "hegrenade", "flashbang", "smokegrenade", "molotov", "incgrenade", "decoy"},
            {"csarsenal.buy.gear", "kevlar", "assaultsuit", "defuser", "taser", "knife", "knife_karambit", "c4"},
    };

    private final List<int[]> headers = new ArrayList<>();

    public BuyMenuScreen() {
        super(Component.translatable("csarsenal.buy.title"));
    }

    @Override
    protected void init() {
        headers.clear();
        int cols = COLUMNS.length;
        int colW = Math.min(118, (width - 40) / cols);
        int x0 = (width - colW * cols) / 2;
        int y0 = 44;
        for (int c = 0; c < cols; c++) {
            String[] col = COLUMNS[c];
            int x = x0 + c * colW;
            headers.add(new int[]{x, y0 - 14, c});
            for (int i = 1; i < col.length; i++) {
                String id = col[i];
                WeaponDef d = Weapons.byId(id);
                String price = d != null && d.price > 0 ? "  $" + d.price : "";
                Component label = Component.translatable("item.csarsenal." + id).append(price);
                addRenderableWidget(Button.builder(label, b -> PacketDistributor.sendToServer(new Packets.Buy(id)))
                        .bounds(x + 2, y0 + (i - 1) * 22, colW - 4, 20).build());
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, 14, 0xFFE8C060);
        for (int[] h : headers) {
            g.drawString(font, Component.translatable(COLUMNS[h[2]][0]), h[0] + 4, h[1], 0xFFB0B0B0, true);
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (Keys.BUY.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
