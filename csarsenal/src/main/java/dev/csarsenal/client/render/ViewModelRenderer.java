package dev.csarsenal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.csarsenal.ClientConfig;
import dev.csarsenal.anim.WeaponGeometry;
import dev.csarsenal.client.fx.ClientFx;
import dev.csarsenal.client.move.CsMovement;
import dev.csarsenal.client.weapon.ClientWeapon;
import dev.csarsenal.server.ServerWeapons;
import dev.csarsenal.weapon.WeaponDef;
import dev.csarsenal.weapon.WeaponDef.Hold;
import dev.csarsenal.weapon.WeaponDef.ReloadStyle;
import dev.csarsenal.weapon.Weapons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

import static dev.csarsenal.client.render.Keyframes.k;

/**
 * First person view model: the weapon plus the player's own (Minecraft skin) arms holding it.
 * Camera space: +X right, +Y up, -Z forward (metres). All motion is keyframed / procedural from {@link ClientWeapon} timers.
 */
public final class ViewModelRenderer {
    // ------------------------------------------------------------------------------------------------ animations
    // keys: {t, dx, dy, dz (camera metres), pitch, yaw, roll (degrees, about the animation pivot)}
    private static final Keyframes DRAW = new Keyframes(k(0, 0.03f, -0.2f, 0.05f, -45, 8, 20), k(0.5f, 0.004f, -0.015f, 0.006f, -5, 1, 3), k(0.78f, 0, 0.004f, 0, 1.5f, 0, -0.5f), k(1, 0, 0, 0, 0, 0, 0));
    private static final Keyframes DRAW_KNIFE = new Keyframes(k(0, 0.06f, -0.16f, 0.04f, -60, 10, -30), k(0.45f, 0.01f, 0.01f, 0, 6, -4, 30), k(0.75f, 0, 0.004f, 0, 2, 0, -4), k(1, 0, 0, 0, 0, 0, 0));
    /** rifle magazine swap: tilt the gun (mag side towards the player), mag out / new mag in, slap, charging handle */
    private static final Keyframes RELOAD_RIFLE = new Keyframes(
            k(0, 0, 0, 0, 0, 0, 0), k(0.12f, -0.02f, 0.012f, 0.01f, 5, -4, 20), k(0.3f, -0.024f, 0.016f, 0.012f, 7, -5, 23),
            k(0.5f, -0.022f, 0.01f, 0.012f, 5, -4, 21), k(0.66f, -0.022f, 0.024f, 0.012f, 10, -4, 25), k(0.71f, -0.022f, 0.012f, 0.012f, 6, -4, 21),
            k(0.8f, -0.012f, 0.008f, 0.008f, 3, 2, 10), k(0.86f, -0.01f, 0.004f, 0.004f, 1, 4, 6), k(1, 0, 0, 0, 0, 0, 0));
    private static final Keyframes RELOAD_PISTOL = new Keyframes(
            k(0, 0, 0, 0, 0, 0, 0), k(0.14f, -0.012f, 0.014f, 0.01f, 7, -3, 22), k(0.58f, -0.012f, 0.014f, 0.01f, 7, -3, 22),
            k(0.64f, -0.012f, 0.026f, 0.01f, 10, -3, 25), k(0.74f, -0.008f, 0.012f, 0.008f, 4, -2, 10), k(1, 0, 0, 0, 0, 0, 0));
    private static final Keyframes RELOAD_SHELLS = new Keyframes(
            k(0, 0, 0, 0, 0, 0, 0), k(0.1f, -0.015f, 0.018f, 0.012f, 6, -4, 26), k(0.9f, -0.015f, 0.018f, 0.012f, 6, -4, 26), k(1, 0, 0, 0, 0, 0, 0));
    /** look at the right side, then roll the gun over to show the left side / bottom */
    private static final Keyframes INSPECT_GUN = new Keyframes(
            k(0, 0, 0, 0, 0, 0, 0), k(0.14f, -0.05f, 0.035f, 0.03f, 4, 28, 24), k(0.4f, -0.055f, 0.04f, 0.03f, 2, 32, 28),
            k(0.52f, -0.03f, 0.045f, 0.025f, 10, -10, -30), k(0.8f, -0.035f, 0.045f, 0.025f, 12, -14, -34), k(0.92f, -0.005f, 0.005f, 0.004f, 1, 0, 0), k(1, 0, 0, 0, 0, 0, 0));
    private static final Keyframes INSPECT_PISTOL = new Keyframes(
            k(0, 0, 0, 0, 0, 0, 0), k(0.12f, -0.045f, 0.03f, 0.05f, 6, 42, 26), k(0.4f, -0.045f, 0.034f, 0.05f, 4, 46, 30),
            k(0.52f, -0.03f, 0.036f, 0.045f, 14, -14, -36), k(0.8f, -0.03f, 0.036f, 0.045f, 16, -16, -40), k(0.92f, 0, 0.004f, 0.006f, 2, 0, 0), k(1, 0, 0, 0, 0, 0, 0));
    private static final Keyframes SILENCER = new Keyframes(k(0, 0, 0, 0, 0, 0, 0), k(0.15f, -0.035f, 0.02f, 0.02f, 4, -18, -6), k(0.85f, -0.035f, 0.02f, 0.02f, 4, -18, -6), k(1, 0, 0, 0, 0, 0, 0));
    private static final Keyframes BOLT = new Keyframes(k(0, 0, 0, 0, 0, 0, 0), k(0.2f, -0.02f, 0.012f, 0.01f, 5, -3, 16), k(0.7f, -0.02f, 0.012f, 0.01f, 5, -3, 16), k(0.88f, 0, 0, 0, 0, 0, 0));
    // knife: right-to-left slash, backhand left-to-right slash, heavy stab, inspect (turn the blade over, flip)
    private static final Keyframes SLASH_A = new Keyframes(k(0, 0, 0, 0, 0, 0, 0), k(0.1f, 0.04f, 0.02f, 0.02f, 4, -20, -20), k(0.28f, -0.14f, -0.01f, -0.06f, -4, 45, 60),
            k(0.45f, -0.19f, -0.035f, -0.03f, -8, 55, 70), k(0.75f, -0.04f, -0.012f, 0, -2, 10, 12), k(1, 0, 0, 0, 0, 0, 0));
    private static final Keyframes SLASH_B = new Keyframes(k(0, 0, 0, 0, 0, 0, 0), k(0.12f, -0.1f, 0.04f, 0.02f, 6, 40, 60), k(0.32f, 0.06f, -0.01f, -0.06f, -4, -30, -40),
            k(0.5f, 0.075f, -0.03f, -0.02f, -8, -40, -50), k(0.78f, 0.015f, -0.01f, 0, -2, -8, -8), k(1, 0, 0, 0, 0, 0, 0));
    private static final Keyframes STAB = new Keyframes(k(0, 0, 0, 0, 0, 0, 0), k(0.22f, 0.02f, 0.05f, 0.06f, 20, -6, 0), k(0.36f, -0.05f, -0.03f, -0.2f, -12, 8, 4),
            k(0.52f, -0.05f, -0.035f, -0.19f, -12, 8, 4), k(0.8f, -0.01f, 0, -0.03f, -4, 2, 0), k(1, 0, 0, 0, 0, 0, 0));
    private static final Keyframes INSPECT_KNIFE = new Keyframes(k(0, 0, 0, 0, 0, 0, 0), k(0.12f, -0.06f, 0.04f, 0.02f, -6, 36, 30), k(0.36f, -0.065f, 0.045f, 0.02f, -8, 40, 34),
            k(0.48f, -0.05f, 0.05f, 0.02f, -10, -6, -36), k(0.62f, -0.05f, 0.05f, 0.02f, -12, -8, -40), k(0.72f, -0.04f, 0.05f, 0.02f, -4, 10, 0),
            k(0.86f, -0.03f, 0.04f, 0.015f, -4, 12, 4), k(1, 0, 0, 0, 0, 0, 0));
    private static final Keyframes INSPECT_KARAMBIT = new Keyframes(k(0, 0, 0, 0, 0, 0, 0), k(0.14f, -0.03f, 0.025f, -0.02f, 10, 20, 20), k(0.28f, -0.035f, 0.03f, -0.02f, 12, 24, 24),
            k(0.64f, -0.035f, 0.03f, -0.02f, 12, 24, 24), k(0.76f, -0.02f, 0.02f, -0.01f, -6, -20, -20), k(0.9f, -0.02f, 0.02f, -0.01f, -8, -24, -24), k(1, 0, 0, 0, 0, 0, 0));
    private static final Keyframes PIN = new Keyframes(k(0, 0, 0, 0, 0, 0, 0), k(0.4f, -0.02f, 0.02f, 0.015f, 8, -10, -6), k(1, 0.015f, 0.05f, 0.07f, 26, 0, 6));
    private static final Keyframes THROW = new Keyframes(k(0, 0.015f, 0.05f, 0.07f, 26, 0, 6), k(0.3f, -0.04f, 0.0f, -0.24f, -60, -6, -4), k(1, -0.04f, -0.3f, -0.18f, -80, 0, 0));

    // ------------------------------------------------------------------------------------------------ placement
    /** resting placement of a hold type: grip position (camera space), base angles, elbow hints of both forearms */
    private record Base(String key, float x, float y, float z, float pitch, float yaw, float roll, float rx, float ry, float rz, float lx, float ly, float lz) {
    }

    private static final Base B_RIFLE = new Base("rifle", 0.225f, -0.13f, -0.41f, 0, 9, -5, 0.34f, -0.48f, -0.12f, -0.1f, -0.46f, -0.4f);
    private static final Base B_PISTOL = new Base("pistol", 0.09f, -0.09f, -0.34f, 0, 6, -1, 0.24f, -0.55f, -0.05f, 0.02f, -0.5f, -0.1f);
    private static final Base B_DUAL = new Base("dual", 0.15f, -0.115f, -0.43f, 0, 2, 0, 0.34f, -0.46f, -0.08f, -0.34f, -0.46f, -0.08f);
    private static final Base B_KNIFE = new Base("knife", 0.14f, -0.11f, -0.33f, 40, 18, -10, 0.36f, -0.46f, -0.02f, 0, 0, 0);
    private static final Base B_KARAMBIT = new Base("karambit", 0.12f, -0.07f, -0.42f, -40, 80, 180, 0.4f, -0.55f, -0.1f, 0, 0, 0);
    private static final Base B_GRENADE = new Base("grenade", 0.11f, -0.09f, -0.34f, 8, 8, 0, 0.36f, -0.46f, -0.04f, -0.3f, -0.46f, -0.08f);
    private static final Base B_C4 = new Base("c4", 0.03f, -0.125f, -0.4f, 0, 90, 30, 0.34f, -0.46f, -0.1f, -0.3f, -0.46f, -0.1f);

    /** development overrides of the placement numbers (AutoTest "vm" scenes); always empty in normal play */
    public static final Map<String, Float> TUNE = new HashMap<>();

    public static float tune(String key, float def) {
        if (TUNE.isEmpty()) return def;
        Float v = TUNE.get(key);
        return v == null ? def : v;
    }

    public static final float ARM_THICKNESS = 0.32f;
    private static float lastYaw, lastPitch, swayX, swayY;
    private static double bobPhase, lastFrame;
    private static float leftFree;
    private static final float[] TMP = new float[6];

    private static float smooth(double e0, double e1, double x) {
        float t = (float) Mth.clamp((x - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static float window(double x, double a, double b, double fade) {
        return smooth(a, a + fade, x) * (1f - smooth(b, b + fade, x));
    }

    private static float rad(float d) {
        return d * Mth.DEG_TO_RAD;
    }

    /** a swing: the whole forearm carries translation, pitch and yaw; the roll is the wrist turning the blade */
    private static void addSwing(float[] acc, float[] wrist, Keyframes kf, float t) {
        kf.sample(t, TMP);
        for (int i = 0; i < 5; i++) acc[i] += TMP[i];
        wrist[2] += TMP[5];
    }

    private static void addWrist(float[] acc, float[] wrist, Keyframes kf, float t) {
        kf.sample(t, TMP);
        for (int i = 0; i < 3; i++) {
            acc[i] += TMP[i];
            wrist[i] += TMP[i + 3];
        }
    }

    private static void add(float[] acc, Keyframes kf, float t, float weight) {
        kf.sample(t, TMP);
        for (int i = 0; i < 6; i++) acc[i] += TMP[i] * weight;
    }

    public static void render(PoseStack ps, MultiBufferSource buf, int light, float pt, LocalPlayer p) {
        ClientWeapon w = ClientWeapon.INSTANCE;
        WeaponDef def = w.def;
        if (def == null || w.isScoped() || Minecraft.getInstance().getCameraEntity() != p) return;
        Mesh mesh = Meshes.get(def.mesh);
        if (mesh == null) return;
        WeaponGeometry g = w.geo != null ? w.geo : WeaponGeometry.get(def.mesh);
        double now = w.now;
        double dt = Mth.clamp(now - lastFrame, 0, 0.1);
        lastFrame = now;

        // ---------------------------------------------------------------- base placement (grip position in camera space)
        Hold hold = def.hold;
        boolean karambit = def == Weapons.KARAMBIT;
        Base b = switch (hold) {
            case PISTOL -> B_PISTOL;
            case DUAL -> B_DUAL;
            case KNIFE -> karambit ? B_KARAMBIT : B_KNIFE;
            case GRENADE -> B_GRENADE;
            case C4 -> B_C4;
            default -> B_RIFLE;
        };
        String bk = b.key;
        Vector3f pos = new Vector3f(tune(bk + ".x", b.x), tune(bk + ".y", b.y), tune(bk + ".z", b.z));
        float bPitch = tune(bk + ".p", b.pitch), bYaw = tune(bk + ".yw", b.yaw), bRoll = tune(bk + ".r", b.roll);
        Vector3f elbowR = new Vector3f(tune(bk + ".rx", b.rx), tune(bk + ".ry", b.ry), tune(bk + ".rz", b.rz));
        Vector3f elbowL = new Vector3f(tune(bk + ".lx", b.lx), tune(bk + ".ly", b.ly), tune(bk + ".lz", b.lz));
        Vector3f pivot = new Vector3f(g.grip);   // rotation pivot for the animations (model space)
        if (hold == Hold.RIFLE) {
            pivot.set(g.grip).lerp(g.support, 0.5f);
            switch (def.category) {
                case SMG -> pos.add(0, 0.004f, -0.02f);
                case SNIPER -> pos.add(0.005f, -0.004f, 0.02f);
                case MACHINEGUN -> pos.add(0.01f, -0.01f, 0.02f);
                case SHOTGUN -> pos.add(0, -0.004f, 0);
                default -> {
                }
            }
            if (g.magTop) pos.add(0.01f, -0.02f, -0.06f);
        }
        pos.add((float) (ClientConfig.num(ClientConfig.VIEWMODEL_X, 1) - 1) * 0.012f, (float) (ClientConfig.num(ClientConfig.VIEWMODEL_Y, 1) - 1) * 0.012f,
                (float) (ClientConfig.num(ClientConfig.VIEWMODEL_Z, -1) + 1) * -0.012f);

        // ---------------------------------------------------------------- animation layers (additive)
        float[] a = new float[6];   // dx dy dz pitch yaw roll
        float[] aw = new float[3];  // wrist only rotation (pitch yaw roll): the weapon turns in the hand, the forearm stays
        // idle breathing + movement bob + mouse sway
        a[1] += Mth.sin((float) now * 1.4f) * 0.0012f;
        a[5] += Mth.cos((float) now * 0.9f) * 0.25f;
        float speed = Mth.clamp(CsMovement.INSTANCE.horizontalSpeed() / 250f, 0, 1.2f) * (CsMovement.INSTANCE.onGround ? 1 : 0.2f);
        bobPhase += dt * (5 + 5 * speed) * Math.min(1, speed * 2);
        a[0] += Mth.sin((float) bobPhase) * 0.006f * speed;
        a[1] -= Math.abs(Mth.cos((float) bobPhase)) * 0.005f * speed;
        a[5] += Mth.sin((float) bobPhase) * 0.8f * speed;
        a[1] -= CsMovement.INSTANCE.landDip * 0.03f;
        float yawNow = p.getViewYRot(pt), pitchNow = p.getViewXRot(pt);
        float dy = Mth.wrapDegrees(yawNow - lastYaw), dp = pitchNow - lastPitch;
        lastYaw = yawNow;
        lastPitch = pitchNow;
        float kk = (float) Math.exp(-dt * 12);
        swayX = Mth.clamp(swayX * kk + Mth.clamp(-dy, -8, 8) * 0.35f * (1 - kk) * 8, -3.5f, 3.5f);
        swayY = Mth.clamp(swayY * kk + Mth.clamp(-dp, -8, 8) * 0.35f * (1 - kk) * 8, -3.5f, 3.5f);
        a[4] += swayX;
        a[3] += swayY;
        a[0] += swayX * 0.001f;
        a[1] -= swayY * 0.001f;
        // draw
        double tDraw = (now - w.drawStart) / w.drawDuration;
        if (tDraw < 1) add(a, hold == Hold.KNIFE ? DRAW_KNIFE : DRAW, (float) Mth.clamp(tDraw, 0, 1), 1);
        // fire kick
        double since = now - w.lastShot;
        if (def.isGun() && since < 0.4) {
            float kick = (float) Math.exp(-since / 0.05);
            float ks = switch (def.category) {
                case PISTOL -> hold == Hold.DUAL ? 1.1f : 1.4f;
                case SNIPER -> 2.2f;
                case SHOTGUN -> 2.4f;
                case SMG -> 0.7f;
                case MACHINEGUN -> 0.8f;
                default -> 1f;
            };
            a[2] += 0.028f * kick * ks;
            a[3] += 2.5f * kick * ks;
            a[4] += Mth.sin(w.shotCounter * 1.7f) * 0.5f * kick;
        }
        // bolt action
        float boltT = def.boltAction ? (float) ((now - w.boltStart) / def.cycleTime) : 9;
        boolean bolting = boltT >= 0 && boltT < 0.85f;
        if (bolting) add(a, BOLT, boltT, 1);
        // reload
        float r = (float) w.reloadProgress();
        ReloadStyle rs = def.reloadStyle;
        if (r >= 0) {
            switch (rs) {
                case PISTOL, REVOLVER -> add(a, RELOAD_PISTOL, r, 1);
                case SHOTGUN_SHELLS -> add(a, RELOAD_SHELLS, r, 1);
                default -> add(a, RELOAD_RIFLE, r, 1);
            }
        }
        // inspect
        float ti = (float) ((now - w.inspectStart) / ClientWeapon.INSPECT_TIME);
        float ringSpin = 0, onRing = 0, twist = 0;
        if (ti >= 0 && ti < 1) {
            if (karambit) {
                addWrist(a, aw, INSPECT_KARAMBIT, ti);
                // twirl around the index finger: two full spins about the ring
                ringSpin = -360f * (smooth(0.3, 0.46, ti) + smooth(0.48, 0.62, ti));
                onRing = window(ti, 0.24, 0.66, 0.05);
            } else if (def.category == WeaponDef.Category.KNIFE) {
                addWrist(a, aw, INSPECT_KNIFE, ti);
                twist = -360f * smooth(0.64, 0.8, ti);   // flip the blade over in the fingers
            }
            else if (hold == Hold.PISTOL || hold == Hold.DUAL) add(a, INSPECT_PISTOL, ti, 1);
            else add(a, INSPECT_GUN, ti, 1);
        }
        // knife attacks
        if (def.category == WeaponDef.Category.KNIFE) {
            double km = now - w.knifeStart;
            if (!w.knifeHeavy && km < 0.45) addSwing(a, aw, w.knifeSide == 0 ? SLASH_A : SLASH_B, (float) (km / 0.45));
            else if (w.knifeHeavy && km < 1.0) addSwing(a, aw, STAB, (float) km);
        }
        // grenades
        boolean grenadeVisible = true, pinVisible = true;
        if (def.category == WeaponDef.Category.GRENADE) {
            double tp = now - w.pinStart, tt = now - w.throwStart;
            if (w.pinPulled) {
                add(a, PIN, (float) Mth.clamp(tp / (ClientWeapon.PIN_TIME + 0.15), 0, 1), 1);
                pinVisible = tp < ClientWeapon.PIN_TIME * 0.6;
            } else if (tt < ClientWeapon.THROW_TIME) {
                add(a, THROW, (float) (tt / ClientWeapon.THROW_TIME), 1);
                grenadeVisible = tt / ClientWeapon.THROW_TIME < 0.3;
                pinVisible = false;
            } else if (tt < ClientWeapon.THROW_TIME + 0.3) {
                return;
            }
        }
        // silencer
        float ts = (float) ((now - w.silencerStart) / ClientWeapon.SILENCER_TIME);
        boolean silAnim = ts >= 0 && ts < 1;
        if (silAnim) add(a, SILENCER, ts, 1);

        // ---------------------------------------------------------------- gun matrix
        // base orientation, then the animation rotation in camera space about the (rotated) pivot
        Matrix4f base = new Matrix4f().rotateY(rad(bYaw)).rotateX(rad(bPitch)).rotateZ(rad(bRoll));
        Matrix3f animRot = new Matrix3f().rotateY(rad(a[4])).rotateX(rad(a[3])).rotateZ(rad(a[5]));
        Vector3f pr = base.transformDirection(new Vector3f(pivot).sub(g.grip));
        Matrix4f gunRest = new Matrix4f().translate(pos).mul(base).translate(-g.grip.x, -g.grip.y, -g.grip.z);
        Matrix4f gun = new Matrix4f().translate(pos.x + a[0], pos.y + a[1], pos.z + a[2])
                .translate(pr).mul(new Matrix4f().set(animRot)).translate(-pr.x, -pr.y, -pr.z)
                .rotateY(rad(aw[1])).rotateX(rad(aw[0])).rotateZ(rad(aw[2]))
                .mul(base).translate(-g.grip.x, -g.grip.y, -g.grip.z);
        // knife tricks: a twist about the handle, the karambit twirl about its ring (the forearm does not follow these)
        if (twist != 0) gun.translate(g.grip).rotateZ(rad(twist)).translate(-g.grip.x, -g.grip.y, -g.grip.z);
        Vector3f ring = new Vector3f(0, 0, 0.066f);
        if (ringSpin != 0) gun.translate(ring).rotateX(rad(ringSpin)).translate(-ring.x, -ring.y, -ring.z);
        boolean leftHanded = ClientConfig.bool(ClientConfig.LEFT_HANDED, false);
        if (leftHanded) {
            ps.pushPose();
            ps.scale(-1, 1, 1);
        }

        // ---------------------------------------------------------------- moving parts
        float slide = 0;
        if (def.isGun() && since < def.cycleTime * 0.8 && !def.boltAction) {
            float u = (float) (since / Math.max(0.03, def.cycleTime * 0.6));
            slide = u < 1 ? Mth.sin(u * Mth.PI) : 0;
        }
        boolean emptyLock = (hold == Hold.PISTOL || hold == Hold.DUAL) && w.ammo <= 0 && !(r > 0.8f);
        float slideTravel = hold == Hold.PISTOL || hold == Hold.DUAL ? 0.026f : 0.035f;
        float slideZ = Math.max(slide, emptyLock ? 1f : 0f) * slideTravel;
        Matrix4f magM = null;
        Vector3f magOff = null;   // model space offset of the moving magazine (shared by both dual pistols)
        boolean magHidden = false;
        Vector3f leftTarget = null;
        float boltPull = 0;
        boolean sideCharger = def == Weapons.AK47 || def == Weapons.GALILAR || def == Weapons.SG556 || def == Weapons.BIZON || def == Weapons.MP5SD
                || def == Weapons.G3SG1 || def == Weapons.UMP45 || def == Weapons.SCAR20;
        if (r >= 0 && rs != ReloadStyle.SHOTGUN_SHELLS) {
            boolean pistol = rs == ReloadStyle.PISTOL || rs == ReloadStyle.REVOLVER;
            float outA = pistol ? 0.1f : 0.18f, outB = pistol ? 0.25f : 0.32f, inA = pistol ? 0.42f : 0.5f, inB = pistol ? 0.62f : 0.68f;
            Vector3f magDown = pistol ? g.gripUp(new Vector3f()).mul(-1) : new Vector3f(0, -1, 0.15f).normalize();
            if (g.magTop) magDown.set(0, 1, 0);
            if (g.mg) magDown.set(-0.3f, -1, 0).normalize();
            if (r > outA && r < outB) {
                float u = smooth(outA, outB, r);
                magOff = new Vector3f(magDown).mul(0.03f + 0.3f * u * u);
                magM = new Matrix4f(gun).translate(magOff);
                magHidden = r > outB - 0.02f;
            } else if (r >= outB && r < inA) {
                magHidden = true;
            } else if (r >= inA && r < inB) {
                float u = 1 - smooth(inA, inB, r);
                magOff = new Vector3f(magDown).mul(0.01f + 0.3f * u * u);
                magM = new Matrix4f(gun).translate(magOff);
            }
            Vector3f magGrab = new Vector3f(g.mag).add(new Vector3f(magDown).mul(g.magTop ? 0.015f : 0.06f));
            Vector3f well = gun.transformPosition(new Vector3f(magGrab));
            Vector3f below = new Vector3f(-0.12f, -0.55f, -0.25f);
            if (r >= outA && r < outB) leftTarget = magM != null ? magM.transformPosition(new Vector3f(magGrab)) : well;
            else if (r >= outB && r < inA) leftTarget = new Vector3f(well).lerp(below, window(r, outB, (outB + inA) / 2, (inA - outB) / 3));
            else if (r >= inA && r < inB) leftTarget = magM != null ? magM.transformPosition(new Vector3f(magGrab)) : well;
            else if (r >= inB && r < 0.74f && !pistol) leftTarget = well;
            else if (r >= 0.74f && r < 0.9f && !pistol && sideCharger) {
                leftTarget = gun.transformPosition(new Vector3f(g.bolt).add(0.02f, 0, 0));
                boltPull = window(r, 0.79f, 0.84f, 0.03f);
            }
            if (r >= 0.05f && r < outA) leftTarget = new Vector3f(gun.transformPosition(new Vector3f(g.support))).lerp(well, smooth(0.05, outA, r));
        } else if (r >= 0) {
            double t = r * w.reloadDuration - ServerWeapons.SHELL_START;
            if (t > 0 && t < w.shellsToLoad * def.reloadTime) {
                float cyc = (float) ((t % def.reloadTime) / def.reloadTime);
                Vector3f port = gun.transformPosition(new Vector3f(g.mag).add(0, -0.04f, 0));
                leftTarget = new Vector3f(-0.1f, -0.5f, -0.25f).lerp(port, Mth.sin(cyc * Mth.PI));
            }
        }
        float pump = 0;
        if ((def == Weapons.NOVA || def == Weapons.SAWEDOFF) && since < def.cycleTime) pump = window(since / def.cycleTime, 0.25, 0.55, 0.15);
        if (rs == ReloadStyle.SHOTGUN_SHELLS && r > 0.92f) pump = window(r, 0.92, 0.96, 0.02);

        // ---------------------------------------------------------------- hands
        // Each hand: a grip point on the weapon plus the axis of what it holds. The forearm direction is solved once
        // in the rest pose (from the elbow hint, turned to lie across the held handle) and then moves rigidly with
        // the weapon, like a locked wrist; a hand that lets go of the weapon (reloads, pin) aims at its elbow instead.
        Vector3f up = g.gripUp(new Vector3f());
        Vector3f pR, axR = null;
        float perpR = 0.7f, perpL = 0.6f;
        switch (hold) {
            case KNIFE -> {
                // the fist sits over the back of the handle so the pommel stays inside it
                pR = new Vector3f(g.grip).add(0, 0, karambit ? 0 : 0.03f);
                axR = new Vector3f(0, 0, -1);
                perpR = karambit ? 0 : 0.85f;
            }
            case GRENADE -> pR = new Vector3f(g.grip).add(0.004f, -0.042f, 0.026f);
            case C4 -> pR = new Vector3f(g.grip).add(0.0f, -0.012f, 0.1f);
            default -> {
                pR = new Vector3f(g.grip).add(new Vector3f(up).mul(hold == Hold.RIFLE ? -0.04f : -0.046f));
                axR = new Vector3f(up);
                if (hold != Hold.RIFLE) perpR = 0.3f;
            }
        }
        Vector3f pL = null, axL = null;
        switch (hold) {
            case RIFLE -> {
                boolean vertical = g.supportKind == WeaponGeometry.Support.VERTICAL || g.supportKind == WeaponGeometry.Support.MAG;
                pL = new Vector3f(g.support).add(0, vertical ? -0.02f : 0.012f, 0);
                axL = vertical ? new Vector3f(0, 1, 0) : new Vector3f(0, 0, -1);
            }
            case PISTOL -> { pL = new Vector3f(g.grip).add(new Vector3f(up).mul(-0.062f)).add(-0.022f, 0, -0.012f); axL = new Vector3f(up); perpL = 0.25f; }
            case DUAL -> { pL = new Vector3f(pR); axL = new Vector3f(up); perpL = perpR; }
            case C4 -> pL = new Vector3f(g.grip).add(0.0f, -0.012f, -0.1f);
            default -> {
            }
        }
        perpR = tune(bk + ".pr", perpR);
        perpL = tune(bk + ".pl", perpL);
        Vector3f restR = forearm(gunRest.transformPosition(new Vector3f(pR)), elbowR, axR == null ? null : base.transformDirection(new Vector3f(axR)), perpR);
        Vector3f handR = gun.transformPosition(new Vector3f(pR));
        if (onRing > 0) handR.lerp(gun.transformPosition(new Vector3f(ring)), onRing);
        if (bolting) handR.lerp(gun.transformPosition(new Vector3f(g.bolt)), window(boltT, 0.22, 0.66, 0.08));
        Vector3f dirR = animRot.transform(new Vector3f(restR));

        Vector3f handL = null, dirL = null;
        Matrix4f gun2 = null;
        if (hold == Hold.DUAL) {
            // mirrored second pistol
            Matrix4f base2 = new Matrix4f().rotateY(rad(-bYaw)).rotateX(rad(bPitch)).rotateZ(rad(-bRoll));
            Matrix3f animRot2 = new Matrix3f().rotateY(rad(-a[4])).rotateX(rad(a[3])).rotateZ(rad(-a[5]));
            Vector3f pr2 = base2.transformDirection(new Vector3f(pivot).sub(g.grip));
            gun2 = new Matrix4f().translate(-pos.x - a[0], pos.y + a[1], pos.z + a[2])
                    .translate(pr2).mul(new Matrix4f().set(animRot2)).translate(-pr2.x, -pr2.y, -pr2.z)
                    .mul(base2).translate(-g.grip.x, -g.grip.y, -g.grip.z);
            Matrix4f rest2 = new Matrix4f().translate(-pos.x, pos.y, pos.z).mul(base2).translate(-g.grip.x, -g.grip.y, -g.grip.z);
            handL = gun2.transformPosition(new Vector3f(pL));
            dirL = animRot2.transform(forearm(rest2.transformPosition(new Vector3f(pL)), elbowL, base2.transformDirection(new Vector3f(axL)), perpL));
        } else {
            Vector3f rigid = null;
            if (pL != null) {
                Vector3f q = new Vector3f(pL);
                if (hold == Hold.RIFLE && pump > 0) q.add(0, 0, 0.07f * pump);
                if (silAnim && (hold == Hold.RIFLE || hold == Hold.PISTOL)) q.set(g.silencer).add(0, 0, hold == Hold.RIFLE ? -0.06f : -0.05f);
                handL = gun.transformPosition(q);
                rigid = animRot.transform(forearm(gunRest.transformPosition(new Vector3f(pL)), elbowL, axL == null ? null : base.transformDirection(new Vector3f(axL)), perpL));
            }
            if (leftTarget == null && hold == Hold.GRENADE && w.pinPulled && now - w.pinStart < ClientWeapon.PIN_TIME) {
                // the fist closes on the ring beside the grenade, not over it
                Vector3f pin = gun.transformPosition(new Vector3f(g.grip).add(-0.035f, 0.055f, -0.01f));
                leftTarget = new Vector3f(-0.18f, -0.5f, -0.25f).lerp(pin, window(now - w.pinStart, 0, ClientWeapon.PIN_TIME * 0.6, 0.1));
            }
            // blend between the gripping (rigid) forearm and the free one so letting go never pops
            float kf = (float) (1 - Math.exp(-dt * 14));
            leftFree += ((leftTarget != null || rigid == null ? 1 : 0) - leftFree) * kf;
            if (leftTarget != null) handL = leftTarget;
            if (handL != null) {
                Vector3f elbowFree = elbowL;
                if (g.magTop && leftTarget != null) elbowFree = new Vector3f(handL).add(-0.26f, -0.16f, 0.12f);
                else if (hold == Hold.GRENADE) elbowFree = new Vector3f(handL).add(-0.08f, -0.4f, 0.0f);   // reach up from below
                Vector3f free = new Vector3f(handL).sub(elbowFree).normalize();
                dirL = rigid == null ? free : new Vector3f(rigid).lerp(free, leftFree).normalize();
            }
        }

        // ---------------------------------------------------------------- draw
        arm(ps, buf, light, p, true, handR, dirR);
        if (handL != null) arm(ps, buf, light, p, false, handL, dirL);
        drawGun(ps, buf, light, mesh, def, gun, slideZ, boltPull, pump, magM, magHidden, w, grenadeVisible, pinVisible, silAnim, ts, g, bolting ? boltT : -1);
        if (gun2 != null) drawGun(ps, buf, light, mesh, def, gun2, slideZ, 0, 0, magOff == null ? null : new Matrix4f(gun2).translate(magOff), magHidden, w, true, true, false, 0, g, -1);

        boolean silenced = def.integrallySuppressed || (def.silencer && w.silencerOn);
        double fs = now - ClientFx.muzzleFlashTime;
        if (def.isGun() && def.category != WeaponDef.Category.TASER && !silenced && fs < 0.04) {
            ps.pushPose();
            ps.mulPose(gun);
            MuzzleFlash.render(ps, buf, new Vector3f(g.muzzle), GunLayer.flashSize(def) * 0.8f, 1f - (float) (fs / 0.04), (float) (w.shotCounter * 67 % 360));
            ps.popPose();
        }
        if (leftHanded) ps.popPose();
    }

    private static void drawGun(PoseStack ps, MultiBufferSource buf, int light, Mesh mesh, WeaponDef def, Matrix4f gun, float slideZ, float boltPull, float pump,
                                Matrix4f magM, boolean magHidden, ClientWeapon w, boolean grenadeVisible, boolean pinVisible, boolean silAnim, float ts,
                                WeaponGeometry g, float boltT) {
        if (!grenadeVisible) return;
        ps.pushPose();
        ps.mulPose(gun);
        for (String part : mesh.parts.keySet()) {
            switch (part) {
                case "mag" -> {
                    if (magHidden || magM != null) continue;
                    MeshRenderer.part(mesh, part, ps, buf, light, 0xFFFFFFFF, MeshRenderer.WEAPON_TEX);
                }
                case "bolt" -> {
                    ps.pushPose();
                    if (boltT >= 0) {
                        float up = window(boltT, 0.3, 0.6, 0.08);
                        float back = window(boltT, 0.38, 0.52, 0.08);
                        ps.translate(g.bolt.x, g.bolt.y, g.bolt.z);
                        ps.mulPose(new Matrix4f().rotateZ(rad(-70 * up)));
                        ps.translate(-g.bolt.x, -g.bolt.y, -g.bolt.z + 0.09f * back);
                    } else {
                        ps.translate(0, 0, slideZ + 0.06f * boltPull);
                    }
                    MeshRenderer.part(mesh, part, ps, buf, light, 0xFFFFFFFF, MeshRenderer.WEAPON_TEX);
                    ps.popPose();
                }
                case "pump" -> {
                    ps.pushPose();
                    ps.translate(0, 0, 0.07f * pump);
                    MeshRenderer.part(mesh, part, ps, buf, light, 0xFFFFFFFF, MeshRenderer.WEAPON_TEX);
                    ps.popPose();
                }
                case "silencer" -> {
                    if (!(w.silencerOn || silAnim)) continue;
                    ps.pushPose();
                    if (silAnim) {
                        float u = smooth(0.15, 0.85, ts);
                        if (!w.silencerTarget && ts > 0.88f) {
                            ps.popPose();
                            continue;
                        }
                        float spin = 720f * u * (w.silencerTarget ? -1 : 1);
                        float along = w.silencerTarget ? 0.04f * (1 - u) : 0.04f * u;
                        ps.translate(0, g.silencer.y, g.silencer.z - along);
                        ps.mulPose(new Matrix4f().rotateZ(rad(spin)));
                        ps.translate(0, -g.silencer.y, -g.silencer.z);
                    }
                    MeshRenderer.part(mesh, part, ps, buf, light, 0xFFFFFFFF, MeshRenderer.WEAPON_TEX);
                    ps.popPose();
                }
                case "cylinder" -> {
                    ps.pushPose();
                    double since = w.now - w.lastShot;
                    float rot = (float) (w.shotCounter * 45 - 45 * Math.exp(-since / 0.05));
                    ps.translate(0, g.mag.y, 0);
                    ps.mulPose(new Matrix4f().rotateZ(rad(rot)));
                    ps.translate(0, -g.mag.y, 0);
                    MeshRenderer.part(mesh, part, ps, buf, light, 0xFFFFFFFF, MeshRenderer.WEAPON_TEX);
                    ps.popPose();
                }
                case "pin", "spoon" -> {
                    if (pinVisible || part.equals("spoon")) MeshRenderer.part(mesh, part, ps, buf, light, 0xFFFFFFFF, MeshRenderer.WEAPON_TEX);
                }
                default -> MeshRenderer.part(mesh, part, ps, buf, light, 0xFFFFFFFF, MeshRenderer.WEAPON_TEX);
            }
        }
        ps.popPose();
        if (magM != null && !magHidden && mesh.has("mag")) {
            ps.pushPose();
            ps.mulPose(magM);
            MeshRenderer.part(mesh, "mag", ps, buf, light, 0xFFFFFFFF, MeshRenderer.WEAPON_TEX);
            ps.popPose();
        }
    }

    /** forearm direction (elbow to hand), turned by `perp` towards lying across `axis`, the handle held in the fist */
    private static Vector3f forearm(Vector3f hand, Vector3f elbow, Vector3f axis, float perp) {
        Vector3f y = new Vector3f(hand).sub(elbow).normalize();
        if (axis != null && perp > 0) {
            Vector3f ax = new Vector3f(axis).normalize();
            y.sub(new Vector3f(ax).mul(y.dot(ax) * perp));
            if (y.lengthSquared() < 1e-4f) y.set(hand).sub(elbow);
            y.normalize();
        }
        return y;
    }

    /** The player's Minecraft arm (skin + sleeve) placed so that its fist holds `hand`, the forearm pointing along `dir`. */
    private static void arm(PoseStack ps, MultiBufferSource buf, int light, LocalPlayer p, boolean right, Vector3f hand, Vector3f dir) {
        if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(p) instanceof PlayerRenderer renderer)) return;
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        ModelPart arm = right ? model.rightArm : model.leftArm;
        ModelPart sleeve = right ? model.rightSleeve : model.leftSleeve;
        boolean slim = p.getSkin().model() == PlayerSkin.Model.SLIM;
        float cx = (right ? -1f : 1f) * (slim ? 0.5f : 1f);
        Vector3f y = new Vector3f(dir).normalize();
        Vector3f x = new Vector3f(0, 1, 0).cross(y);
        if (x.lengthSquared() < 1e-6f) x.set(1, 0, 0);
        x.normalize();
        if (x.x > 0) x.negate();
        Vector3f z = new Vector3f(x).cross(y).normalize();
        // ModelPart cubes are defined in pixels and divided by 16 when rendered; first person arms are drawn slimmer
        float t = tune("thick", ARM_THICKNESS), l = 1.0f;
        Matrix4f m = new Matrix4f(x.x * t, x.y * t, x.z * t, 0, y.x * l, y.y * l, y.z * l, 0, z.x * t, z.y * t, z.z * t, 0, 0, 0, 0, 1);
        // the fist: palm centre half a hand's thickness in from the end of the arm (y = 10 px)
        Vector3f palm = m.transformPosition(new Vector3f(cx / 16f, (10f - 1.8f * t) / 16f, 0));
        m.setTranslation(hand.x - palm.x, hand.y - palm.y, hand.z - palm.z);
        ResourceLocation skin = p.getSkin().texture();
        float ox = arm.x, oy = arm.y, oz = arm.z, rx = arm.xRot, ry = arm.yRot, rz = arm.zRot;
        float sx = sleeve.x, sy = sleeve.y, sz = sleeve.z, srx = sleeve.xRot, sry = sleeve.yRot, srz = sleeve.zRot;
        boolean sv = sleeve.visible;
        arm.x = arm.y = arm.z = 0;
        arm.xRot = arm.yRot = arm.zRot = 0;
        sleeve.x = sleeve.y = sleeve.z = 0;
        sleeve.xRot = sleeve.yRot = sleeve.zRot = 0;
        sleeve.visible = p.isModelPartShown(right ? net.minecraft.world.entity.player.PlayerModelPart.RIGHT_SLEEVE : net.minecraft.world.entity.player.PlayerModelPart.LEFT_SLEEVE);
        ps.pushPose();
        ps.mulPose(m);
        arm.render(ps, buf.getBuffer(RenderType.entitySolid(skin)), light, OverlayTexture.NO_OVERLAY);
        sleeve.render(ps, buf.getBuffer(RenderType.entityTranslucent(skin)), light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        arm.x = ox; arm.y = oy; arm.z = oz; arm.xRot = rx; arm.yRot = ry; arm.zRot = rz;
        sleeve.x = sx; sleeve.y = sy; sleeve.z = sz; sleeve.xRot = srx; sleeve.yRot = sry; sleeve.zRot = srz;
        sleeve.visible = sv;
    }

    private ViewModelRenderer() {
    }
}
