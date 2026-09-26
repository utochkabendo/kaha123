package dev.csarsenal.client.hud;

import dev.csarsenal.client.ClientData;
import dev.csarsenal.network.Packets;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** CS style scoreboard (hold Tab while holding a CS item): teams, money of teammates, K / A / D, HS %, damage, ping. */
public final class CsScoreboard {
    private static List<Packets.Scoreboard.Row> rows = new ArrayList<>();
    private static final int CT = 0xFF6CA8FF, T = 0xFFE8B84A, NONE = 0xFFD0D0D0, TEXT = 0xFFE9E9E9, DIM = 0xFF8E969F;

    public static void update(Packets.Scoreboard p) {
        rows = new ArrayList<>(p.rows());
    }

    public static void reset() {
        rows = new ArrayList<>();
    }

    public static boolean showing() {
        Minecraft mc = Minecraft.getInstance();
        return CsHud.active() && mc.options.keyPlayerList.isDown() && !rows.isEmpty();
    }

    public static void render(GuiGraphics g, DeltaTracker dt) {
        if (!showing()) return;
        Minecraft mc = Minecraft.getInstance();
        Font f = mc.font;
        int W = g.guiWidth(), H = g.guiHeight();
        int pw = Math.min(460, W - 16);
        int myTeam = mc.player == null ? 0 : ClientData.team(mc.player);
        List<List<Packets.Scoreboard.Row>> groups = new ArrayList<>();
        int[] order = {2, 1, 0};
        for (int t : order) {
            List<Packets.Scoreboard.Row> l = new ArrayList<>();
            for (var r : rows) if (r.team() == t) l.add(r);
            l.sort(Comparator.comparingInt(Packets.Scoreboard.Row::kills).reversed().thenComparingInt(Packets.Scoreboard.Row::deaths));
            groups.add(l);
        }
        int lines = 0;
        for (var l : groups) if (!l.isEmpty()) lines += l.size() + 2;
        int rowH = 12;
        int ph = lines * rowH + 20;
        int px = (W - pw) / 2, py = Math.max(8, (H - ph) / 3);
        g.fill(px, py, px + pw, py + ph, 0xD8141719);
        g.fill(px, py, px + pw, py + 14, 0xF01C2127);
        g.drawString(f, "SCOREBOARD", px + 6, py + 3, TEXT, false);
        // columns
        int cPing = px + pw - 26, cDmg = cPing - 34, cHs = cDmg - 30, cD = cHs - 22, cA = cD - 20, cK = cA - 20, cMoney = cK - 50;
        int y = py + 18;
        for (int gi = 0; gi < groups.size(); gi++) {
            var l = groups.get(gi);
            if (l.isEmpty()) continue;
            int team = order[gi];
            int col = team == 2 ? CT : team == 1 ? T : NONE;
            String title = team == 2 ? Component.translatable("csarsenal.team.ct").getString() : team == 1 ? Component.translatable("csarsenal.team.t").getString() : "Players";
            g.fill(px + 4, y, px + pw - 4, y + rowH - 1, (col & 0x00FFFFFF) | 0x50000000);
            g.drawString(f, title.toUpperCase(), px + 8, y + 2, col, false);
            head(g, f, "$", cMoney, y);
            head(g, f, "K", cK, y);
            head(g, f, "A", cA, y);
            head(g, f, "D", cD, y);
            head(g, f, "HS%", cHs, y);
            head(g, f, "DMG", cDmg, y);
            head(g, f, "PING", cPing, y);
            y += rowH;
            for (var r : l) {
                boolean me = mc.player != null && r.id().equals(mc.player.getUUID());
                Player ent = mc.level == null ? null : mc.level.getPlayerByUUID(r.id());
                boolean dead = ent == null || ent.isDeadOrDying();
                if (me) g.fill(px + 4, y, px + pw - 4, y + rowH - 1, 0x30FFFFFF);
                int nameCol = dead ? DIM : col;
                g.drawString(f, f.plainSubstrByWidth(r.name(), cMoney - px - 60), px + 10, y + 2, nameCol, false);
                boolean mate = myTeam == 0 || r.team() == myTeam;
                if (mate) num(g, f, "$" + r.money(), cMoney, y, 0xFF8FD35E);
                num(g, f, String.valueOf(r.kills()), cK, y, TEXT);
                num(g, f, String.valueOf(r.assists()), cA, y, TEXT);
                num(g, f, String.valueOf(r.deaths()), cD, y, TEXT);
                num(g, f, r.kills() > 0 ? Math.round(100f * r.headshots() / r.kills()) + "%" : "0%", cHs, y, TEXT);
                num(g, f, String.valueOf(r.damage()), cDmg, y, TEXT);
                PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(r.id());
                num(g, f, info == null ? "-" : String.valueOf(info.getLatency()), cPing, y, DIM);
                y += rowH;
            }
            y += rowH / 2;
        }
    }

    private static void head(GuiGraphics g, Font f, String s, int x, int y) {
        g.drawString(f, s, x - f.width(s) / 2, y + 2, DIM, false);
    }

    private static void num(GuiGraphics g, Font f, String s, int x, int y, int col) {
        g.drawString(f, s, x - f.width(s) / 2, y + 2, col, false);
    }

    private CsScoreboard() {
    }
}
