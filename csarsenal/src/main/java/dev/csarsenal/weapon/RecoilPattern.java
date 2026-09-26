package dev.csarsenal.weapon;

import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * A CS style spray pattern. The pattern stores where bullet N lands relative to the crosshair (degrees, x = right,
 * y = up) when the whole magazine is sprayed without mouse compensation. Internally this is converted into per-shot
 * "aim punch" kicks that, together with the CS punch decay (exp 8/s + linear 18 deg/s, bullets go to
 * view + 2 * punch), reproduce the pattern exactly for a continuous spray, while behaving like CS when tapping or bursting.
 */
public final class RecoilPattern {
    public static final float RECOIL_SCALE = 2.0f;          // weapon_recoil_scale
    public static final float VIEW_TRACKING = 0.45f;        // view_recoil_tracking
    public static final float DECAY_EXP = 8.0f;             // weapon_recoil_decay2_exp
    public static final float DECAY_LIN = 18.0f;            // weapon_recoil_decay2_lin

    public static final RecoilPattern NONE = new RecoilPattern(new float[]{0}, new float[]{0}, 0f);

    private final float[] px, py;
    /** extra kick per shot after the pattern ran out (keeps drifting up/sideways) */
    private final float tailRise;
    private final Map<Integer, float[][]> kickCache = new HashMap<>();

    private RecoilPattern(float[] px, float[] py, float tailRise) {
        this.px = px;
        this.py = py;
        this.tailRise = tailRise;
    }

    public int length() {
        return px.length;
    }

    public float bulletX(int shot) {
        return shot < px.length ? px[shot] : px[px.length - 1];
    }

    public float bulletY(int shot) {
        return shot < py.length ? py[shot] : py[py.length - 1] + tailRise * (shot - py.length + 1);
    }

    /** Explicit pattern from (x, y) pairs, one per shot. */
    public static RecoilPattern of(float tailRise, float... xy) {
        int n = xy.length / 2;
        float[] x = new float[n], y = new float[n];
        for (int i = 0; i < n; i++) {
            x[i] = xy[i * 2];
            y[i] = xy[i * 2 + 1];
        }
        return new RecoilPattern(x, y, tailRise);
    }

    /**
     * Procedural pattern: vertical climb over the first shots, then horizontal sweeps (like the CS rifles).
     *
     * @param shots        magazine length
     * @param rise         total vertical climb (deg)
     * @param riseShots    shots spent in the vertical part
     * @param plateauRise  extra climb spread over the rest of the magazine
     * @param sweep        horizontal amplitude (deg)
     * @param period       shots per full left-right cycle
     * @param firstDir     -1 = first sweep goes left, +1 = right
     */
    public static RecoilPattern wave(int shots, float rise, int riseShots, float plateauRise, float sweep, float period, int firstDir, float wobble) {
        float[] x = new float[shots], y = new float[shots];
        for (int i = 0; i < shots; i++) {
            float t = Math.min(1f, i / (float) riseShots);
            // ease-in/out climb: small first kick, strong middle, flattening
            float climb = (float) (rise * (0.5 - 0.5 * Math.cos(Math.PI * t)));
            if (i > 0 && i < 3) climb *= 0.8f;
            float after = Math.max(0, i - riseShots);
            climb += plateauRise * after / Math.max(1, shots - riseShots);
            float h = 0;
            if (i > riseShots * 0.7f) {
                float u = (i - riseShots * 0.7f) / period;
                float grow = Math.min(1f, u * 2.5f);
                h = firstDir * sweep * grow * (float) Math.sin(u * Math.PI * 2);
            }
            // deterministic small jitter so the pattern is not a perfect curve
            float j = (float) Math.sin(i * 12.9898 + shots * 78.233) * wobble;
            float k = (float) Math.cos(i * 4.1414 + rise * 17.0) * wobble * 0.5f;
            x[i] = h + (i == 0 ? 0 : j);
            y[i] = climb + (i == 0 ? 0 : k);
        }
        x[0] = 0;
        y[0] = 0;
        return new RecoilPattern(x, y, plateauRise * 0.02f);
    }

    /** Pistol / sniper style: every shot kicks straight up by `perShot` with slight alternating yaw. */
    public static RecoilPattern kick(int shots, float perShot, float yawJitter) {
        float[] x = new float[shots], y = new float[shots];
        for (int i = 1; i < shots; i++) {
            y[i] = y[i - 1] + perShot * (i < 3 ? 0.85f + 0.1f * i : 1.0f);
            x[i] = x[i - 1] + (float) Math.sin(i * 2.3 + perShot) * yawJitter;
        }
        return new RecoilPattern(x, y, perShot);
    }

    /** Punch decay used by both client and pattern compilation. */
    public static void decay(float[] punch, float dt) {
        float e = (float) Math.exp(-DECAY_EXP * dt);
        float x = punch[0] * e, y = punch[1] * e;
        float len = Mth.sqrt(x * x + y * y);
        if (len > 1e-6f) {
            float nl = Math.max(0f, len - DECAY_LIN * dt);
            x *= nl / len;
            y *= nl / len;
        }
        punch[0] = x;
        punch[1] = y;
    }

    /** Inverse of {@link #decay}: the punch that decays into (x, y) after dt seconds. */
    private static float[] undecay(float x, float y, float dt) {
        float len = Mth.sqrt(x * x + y * y);
        if (len < 1e-7f) return new float[]{0, 0};
        float e = (float) Math.exp(-DECAY_EXP * dt);
        float pre = (len + DECAY_LIN * dt) / e;
        return new float[]{x / len * pre, y / len * pre};
    }

    /**
     * Kicks (in punch space) applied right after shot {@code s} is fired, so that - with the CS punch decay running
     * between shots - shot {@code s+1} of a continuous spray lands exactly on the pattern.
     * Tapping / bursting lets the punch decay in between, which gives the usual CS "reset" behaviour.
     */
    public synchronized float[][] kicks(float cycle) {
        int key = Math.round(cycle * 10000);
        float[][] k = kickCache.get(key);
        if (k != null) return k;
        int n = Math.max(px.length, 1) + 64;
        k = new float[n][2];
        for (int s = 0; s < n; s++) {
            float[] q = undecay(bulletX(s + 1) / RECOIL_SCALE, bulletY(s + 1) / RECOIL_SCALE, cycle);
            k[s][0] = q[0] - bulletX(s) / RECOIL_SCALE;
            k[s][1] = q[1] - bulletY(s) / RECOIL_SCALE;
        }
        kickCache.put(key, k);
        return k;
    }
}
