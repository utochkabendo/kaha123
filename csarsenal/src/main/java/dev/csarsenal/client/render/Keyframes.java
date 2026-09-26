package dev.csarsenal.client.render;

import net.minecraft.util.Mth;

/**
 * Tiny keyframe animation helper: each key is {t, dx, dy, dz, pitch, yaw, roll} (metres / degrees, camera space),
 * sampled with smooth (cubic Hermite) blending between neighbouring keys.
 */
public final class Keyframes {
    public final float[][] keys;

    public Keyframes(float[]... keys) {
        this.keys = keys;
    }

    /** returns {dx, dy, dz, pitch, yaw, roll} */
    public float[] sample(float t, float[] out) {
        if (keys.length == 0) return out;
        if (t <= keys[0][0]) return copy(keys[0], out);
        if (t >= keys[keys.length - 1][0]) return copy(keys[keys.length - 1], out);
        for (int i = 0; i < keys.length - 1; i++) {
            float[] a = keys[i], b = keys[i + 1];
            if (t >= a[0] && t <= b[0]) {
                float u = (t - a[0]) / Math.max(1e-6f, b[0] - a[0]);
                u = u * u * (3 - 2 * u);
                for (int k = 0; k < 6; k++) out[k] = Mth.lerp(u, a[k + 1], b[k + 1]);
                return out;
            }
        }
        return out;
    }

    private static float[] copy(float[] k, float[] out) {
        System.arraycopy(k, 1, out, 0, 6);
        return out;
    }

    public static float[] k(float t, float dx, float dy, float dz, float pitch, float yaw, float roll) {
        return new float[]{t, dx, dy, dz, pitch, yaw, roll};
    }

    public static final float[] ZERO = new float[6];
}
