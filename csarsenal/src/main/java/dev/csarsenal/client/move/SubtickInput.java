package dev.csarsenal.client.move;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

import java.util.ArrayList;
import java.util.List;

/**
 * CS2 style sub-tick input: key states are sampled every rendered frame with a timestamp. When the (20 Hz)
 * movement tick runs, every physics sub-step uses the exact fraction of its time slice during which each key
 * was held, plus the view yaw at that moment - instead of "whatever was pressed when the tick started".
 */
public final class SubtickInput {
    public static final class Sample {
        long t;
        boolean fwd, back, left, right, walk, crouch;
        float yaw;
    }

    public static final class Step {
        public float forward, side;   // -1..1 (forward positive, right positive)
        public float walk, crouch;    // 0..1
        public float yaw;
    }

    private static final List<Sample> samples = new ArrayList<>();
    private static long lastTickNs = System.nanoTime();
    private static Sample last;

    public static boolean walkDown, crouchDown;

    /** called every frame */
    public static void sample(Minecraft mc) {
        if (mc.player == null) return;
        Options o = mc.options;
        boolean active = mc.screen == null;
        Sample s = new Sample();
        s.t = System.nanoTime();
        s.fwd = active && o.keyUp.isDown();
        s.back = active && o.keyDown.isDown();
        s.left = active && o.keyLeft.isDown();
        s.right = active && o.keyRight.isDown();
        s.walk = active && walkDown;
        s.crouch = active && crouchDown;
        s.yaw = mc.player.getYRot();
        synchronized (samples) {
            samples.add(s);
            if (samples.size() > 2000) samples.remove(0);
        }
        last = s;
    }

    /** consume the samples since the previous tick and integrate them into n equal sub-steps */
    public static Step[] steps(int n, float fallbackYaw) {
        long now = System.nanoTime();
        long start = lastTickNs;
        if (now - start > 250_000_000L || now <= start) start = now - 50_000_000L;
        lastTickNs = now;
        List<Sample> list;
        synchronized (samples) {
            list = new ArrayList<>(samples);
            samples.clear();
        }
        Sample carry = last;
        Step[] out = new Step[n];
        long span = now - start;
        for (int k = 0; k < n; k++) {
            long a = start + span * k / n, b = start + span * (k + 1) / n;
            Step st = new Step();
            double fw = 0, bk = 0, lf = 0, rt = 0, wk = 0, cr = 0, total = 0;
            double yawAcc = 0;
            int yawN = 0;
            // state before the first sample in this slice is the last sample before 'a'
            Sample cur = null;
            for (Sample s : list) {
                if (s.t <= a) cur = s;
            }
            if (cur == null) cur = list.isEmpty() ? carry : list.get(0);
            if (cur == null) {
                st.yaw = fallbackYaw;
                out[k] = st;
                continue;
            }
            long t0 = a;
            for (Sample s : list) {
                if (s.t <= a) continue;
                if (s.t >= b) break;
                double w = (s.t - t0) / 1e9;
                fw += cur.fwd ? w : 0; bk += cur.back ? w : 0; lf += cur.left ? w : 0; rt += cur.right ? w : 0;
                wk += cur.walk ? w : 0; cr += cur.crouch ? w : 0; total += w;
                yawAcc += cur.yaw; yawN++;
                cur = s;
                t0 = s.t;
            }
            double w = (b - t0) / 1e9;
            fw += cur.fwd ? w : 0; bk += cur.back ? w : 0; lf += cur.left ? w : 0; rt += cur.right ? w : 0;
            wk += cur.walk ? w : 0; cr += cur.crouch ? w : 0; total += w;
            yawAcc += cur.yaw; yawN++;
            if (total <= 0) total = 1;
            st.forward = (float) ((fw - bk) / total);
            st.side = (float) ((rt - lf) / total);
            st.walk = (float) (wk / total);
            st.crouch = (float) (cr / total);
            st.yaw = (float) (yawAcc / yawN);
            out[k] = st;
        }
        if (!list.isEmpty()) last = list.get(list.size() - 1);
        return out;
    }

    public static void reset() {
        synchronized (samples) {
            samples.clear();
        }
        last = null;
        lastTickNs = System.nanoTime();
    }

    private SubtickInput() {
    }
}
