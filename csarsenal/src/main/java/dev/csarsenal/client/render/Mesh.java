package dev.csarsenal.client.render;

import com.google.gson.JsonObject;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A mesh made of named parts, each split into per-material chunks (loaded from .csm files). */
public final class Mesh {
    public static final class Chunk {
        public final String material;
        public final float[] pos;    // xyz
        public final byte[] nrm;     // xyz * 127
        public final float[] uv;     // uv
        public final int[] idx;      // triangles

        public Chunk(String material, float[] pos, byte[] nrm, float[] uv, int[] idx) {
            this.material = material;
            this.pos = pos;
            this.nrm = nrm;
            this.uv = uv;
            this.idx = idx;
        }
    }

    public final String name;
    public final Map<String, List<Chunk>> parts = new LinkedHashMap<>();
    public JsonObject meta = new JsonObject();
    public final Vector3f min = new Vector3f(Float.MAX_VALUE), max = new Vector3f(-Float.MAX_VALUE);

    public Mesh(String name) {
        this.name = name;
    }

    public List<Chunk> part(String p) {
        return parts.getOrDefault(p, List.of());
    }

    public boolean has(String p) {
        return parts.containsKey(p);
    }

    void add(String part, Chunk c) {
        parts.computeIfAbsent(part, k -> new ArrayList<>()).add(c);
        for (int i = 0; i < c.pos.length; i += 3) {
            min.min(new Vector3f(c.pos[i], c.pos[i + 1], c.pos[i + 2]));
            max.max(new Vector3f(c.pos[i], c.pos[i + 1], c.pos[i + 2]));
        }
    }

    public float longestSide() {
        return Math.max(max.x - min.x, Math.max(max.y - min.y, max.z - min.z));
    }
}
