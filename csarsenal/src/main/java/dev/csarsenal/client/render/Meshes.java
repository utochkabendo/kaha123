package dev.csarsenal.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.csarsenal.CsArsenal;
import dev.csarsenal.anim.WeaponGeometry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Loads assets/csarsenal/meshes/*.csm (+ .json attachment points) on resource reload. */
public final class Meshes extends SimplePreparableReloadListener<Map<String, Mesh>> {
    public static final Meshes INSTANCE = new Meshes();
    private static volatile Map<String, Mesh> MESHES = new HashMap<>();

    public static Mesh get(String name) {
        return MESHES.get(name);
    }

    @Override
    protected Map<String, Mesh> prepare(ResourceManager rm, ProfilerFiller profiler) {
        Map<String, Mesh> out = new HashMap<>();
        try {
            Optional<Resource> idx = rm.getResource(CsArsenal.id("meshes/index.json"));
            if (idx.isEmpty()) {
                CsArsenal.LOG.error("CS Arsenal: meshes/index.json missing");
                return out;
            }
            JsonArray names;
            try (InputStream in = idx.get().open()) {
                names = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
            }
            for (JsonElement e : names) {
                String n = e.getAsString();
                try {
                    Mesh m = load(rm, n);
                    if (m != null) out.put(n, m);
                } catch (Exception ex) {
                    CsArsenal.LOG.error("CS Arsenal: failed to load mesh {}", n, ex);
                }
            }
        } catch (IOException ex) {
            CsArsenal.LOG.error("CS Arsenal: mesh loading failed", ex);
        }
        return out;
    }

    @Override
    protected void apply(Map<String, Mesh> map, ResourceManager rm, ProfilerFiller profiler) {
        MESHES = map;
        for (Mesh m : map.values()) WeaponGeometry.put(m.name, geometry(m));
        CsArsenal.LOG.info("CS Arsenal: loaded {} meshes", map.size());
    }

    private static Mesh load(ResourceManager rm, String name) throws IOException {
        Optional<Resource> res = rm.getResource(CsArsenal.id("meshes/" + name + ".csm"));
        if (res.isEmpty()) return null;
        Mesh mesh = new Mesh(name);
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(res.get().open()))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (magic[0] != 'C' || magic[1] != 'S' || magic[2] != 'M' || magic[3] != '1') throw new IOException("bad magic");
            int parts = in.readInt();
            for (int p = 0; p < parts; p++) {
                String pname = in.readUTF();
                int chunks = in.readInt();
                for (int c = 0; c < chunks; c++) {
                    String mat = in.readUTF();
                    int nv = in.readInt();
                    float[] pos = new float[nv * 3];
                    byte[] nrm = new byte[nv * 3];
                    float[] uv = new float[nv * 2];
                    for (int i = 0; i < nv; i++) {
                        pos[i * 3] = in.readFloat();
                        pos[i * 3 + 1] = in.readFloat();
                        pos[i * 3 + 2] = in.readFloat();
                        nrm[i * 3] = in.readByte();
                        nrm[i * 3 + 1] = in.readByte();
                        nrm[i * 3 + 2] = in.readByte();
                        uv[i * 2] = in.readFloat();
                        uv[i * 2 + 1] = in.readFloat();
                    }
                    int nt = in.readInt();
                    int[] idx = new int[nt * 3];
                    boolean shorts = nv < 32768;
                    for (int i = 0; i < idx.length; i++) idx[i] = shorts ? in.readShort() : in.readInt();
                    mesh.add(pname, new Mesh.Chunk(mat, pos, nrm, uv, idx));
                }
            }
        }
        Optional<Resource> meta = rm.getResource(CsArsenal.id("meshes/" + name + ".json"));
        if (meta.isPresent()) {
            try (InputStream in = meta.get().open()) {
                mesh.meta = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
        return mesh;
    }

    private static void vec(JsonObject pts, String key, org.joml.Vector3f out) {
        if (pts != null && pts.has(key)) {
            JsonArray a = pts.getAsJsonArray(key);
            out.set(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
        }
    }

    static WeaponGeometry geometry(Mesh m) {
        WeaponGeometry g = new WeaponGeometry();
        JsonObject meta = m.meta;
        JsonObject pts = meta.has("points") ? meta.getAsJsonObject("points") : null;
        vec(pts, "grip", g.grip);
        vec(pts, "support", g.support);
        vec(pts, "muzzle", g.muzzle);
        g.muzzleSilenced.set(g.muzzle);
        if (pts != null && pts.has("muzzle_sil")) {
            vec(pts, "muzzle_sil", g.muzzleSilenced);
            g.hasMuzzleSil = true;
        }
        vec(pts, "eject", g.eject);
        vec(pts, "mag", g.mag);
        vec(pts, "bolt", g.bolt);
        if (pts != null && pts.has("silencer")) {
            vec(pts, "silencer", g.silencer);
            g.hasSilencerPoint = true;
        }
        if (pts != null && pts.has("slide")) vec(pts, "slide", g.bolt);
        if (pts != null && pts.has("pump")) vec(pts, "pump", g.bolt);
        if (meta.has("grip_angle")) g.gripAngle = meta.get("grip_angle").getAsFloat();
        g.pistol = bool(meta, "pistol");
        g.dual = bool(meta, "dual");
        g.magTop = bool(meta, "mag_top");
        g.shellReload = bool(meta, "shell_reload");
        g.revolver = bool(meta, "revolver");
        g.knife = bool(meta, "knife");
        g.grenade = bool(meta, "grenade");
        g.mg = bool(meta, "mg");
        String sk = meta.has("support_kind") ? meta.get("support_kind").getAsString() : (g.pistol ? "pistol_under" : "under");
        g.supportKind = switch (sk) {
            case "vertical" -> WeaponGeometry.Support.VERTICAL;
            case "strap" -> WeaponGeometry.Support.STRAP;
            case "mag" -> WeaponGeometry.Support.MAG;
            case "thumbhole" -> WeaponGeometry.Support.UNDER;
            default -> WeaponGeometry.Support.UNDER;
        };
        return g;
    }

    private static boolean bool(JsonObject o, String k) {
        return o.has(k) && o.get(k).getAsBoolean();
    }

    public static ResourceLocation materialTexture(String mat) {
        return CsArsenal.id("textures/material/" + mat + ".png");
    }

    private static final Map<String, ResourceLocation> AGENT_TEX = new HashMap<>();

    public static ResourceLocation agentTexture(String team, String mat) {
        return AGENT_TEX.computeIfAbsent(team + "/" + mat, k -> CsArsenal.id("textures/agent/" + k + ".png"));
    }

    private static final Map<String, ResourceLocation> MAT_TEX = new HashMap<>();

    public static ResourceLocation material(String mat) {
        return MAT_TEX.computeIfAbsent(mat, Meshes::materialTexture);
    }
}
