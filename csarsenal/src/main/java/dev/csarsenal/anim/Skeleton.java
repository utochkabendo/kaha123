package dev.csarsenal.anim;

import dev.csarsenal.weapon.WeaponDef.Hold;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Procedural CS-style operator skeleton. Produces bone matrices (bone local -> body space) for the renderer and
 * the hitboxes, so what you see is exactly what you hit.
 * <p>
 * Body space: origin between the feet, +X = character's left, +Y = up, +Z = forward (before the body yaw).
 * Bone offsets mirror tools/character.py.
 */
public final class Skeleton {
    public static final int PELVIS = 0, SPINE = 1, CHEST = 2, NECK = 3, HEAD = 4,
            UPPERARM_L = 5, UPPERARM_R = 6, FOREARM_L = 7, FOREARM_R = 8, HAND_L = 9, HAND_R = 10,
            THIGH_L = 11, THIGH_R = 12, SHIN_L = 13, SHIN_R = 14, FOOT_L = 15, FOOT_R = 16, COUNT = 17;
    public static final String[] NAMES = {"pelvis", "spine", "chest", "neck", "head", "upperarm_l", "upperarm_r", "forearm_l", "forearm_r",
            "hand_l", "hand_r", "thigh_l", "thigh_r", "shin_l", "shin_r", "foot_l", "foot_r"};

    public static final float UPPER_ARM = 0.29f, FOREARM = 0.255f, THIGH = 0.42f, SHIN = 0.42f;
    public static final float STAND_PELVIS = 0.97f, CROUCH_PELVIS = 0.6f;
    /** gripped object centre in the hand bone frame */
    public static final Vector3f FIST_R = new Vector3f(0.022f, -0.074f, 0f), FIST_L = new Vector3f(-0.022f, -0.074f, 0f);

    public final Matrix4f[] bones = new Matrix4f[COUNT];
    /** weapon model space -> body space */
    public final Matrix4f gun = new Matrix4f();
    /** second pistol for the dual berettas (left hand) */
    public final Matrix4f gun2 = new Matrix4f();
    public boolean hasGun, hasGun2;
    /** 0 = magazine in the gun, 1 = held by the left hand (use {@link #magInHand}), 2 = hidden */
    public int magMode;
    public final Matrix4f magInHand = new Matrix4f();
    /** grenade visible in the right hand */
    public boolean grenadeVisible = true;

    // scratch
    private final Vector3f tmpA = new Vector3f(), tmpB = new Vector3f(), tmpC = new Vector3f(), tmpD = new Vector3f();
    private final Matrix3f aim = new Matrix3f(), gunRot = new Matrix3f(), handR = new Matrix3f(), handL = new Matrix3f();

    public Skeleton() {
        for (int i = 0; i < COUNT; i++) bones[i] = new Matrix4f();
    }

    private static float rad(float deg) {
        return deg * Mth.DEG_TO_RAD;
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    /** window: 0 before a, ramps to 1 over fade, 1 until b, ramps down to 0 at b+fade */
    private static float window(float x, float a, float b, float fade) {
        return smooth(a, a + fade, x) * (1f - smooth(b, b + fade, x));
    }

    public Skeleton compute(PoseInput in) {
        Hold hold = in.hold;
        float yaw = Mth.clamp(in.yawRel, -120f, 120f);
        float pitch = Mth.clamp(in.pitch, -89f, 89f);
        float crouch = Mth.clamp(in.crouch, 0f, 1f);
        float walk = Mth.clamp(in.walkAmount, 0f, 1f);
        float phase = in.walkPos * 0.6662f;
        boolean rifle = hold == Hold.RIFLE;
        float blade = rifle ? 28f * in.deploy : 0f;

        // ---------------------------------------------------------------- torso
        float bob = -Math.abs(Mth.sin(phase)) * 0.03f * walk * (1 - in.air);
        float pelvisY = Mth.lerp(crouch, STAND_PELVIS, CROUCH_PELVIS) + bob + in.air * 0.02f;
        float pelvisZ = -0.06f * crouch;
        float lean = 22f * crouch + 4f * walk;
        Matrix4f pelvis = bones[PELVIS].identity().translate(0, pelvisY, pelvisZ).rotateY(rad(-yaw * 0.05f)).rotateX(rad(lean));
        bones[SPINE].set(pelvis).translate(0, 0.10f, 0).rotateY(rad(-yaw * 0.25f - blade * 0.5f)).rotateX(rad(pitch * 0.2f - lean * 0.75f));
        bones[CHEST].set(bones[SPINE]).translate(0, 0.20f, 0).rotateY(rad(-yaw * 0.35f - blade * 0.5f)).rotateX(rad(pitch * 0.25f - lean * 0.15f));
        bones[NECK].set(bones[CHEST]).translate(0, 0.21f, -0.005f).rotateY(rad(-yaw * 0.15f + blade * 0.45f)).rotateX(rad(pitch * 0.2f - lean * 0.1f));
        bones[HEAD].set(bones[NECK]).translate(0, 0.05f, 0.01f).rotateY(rad(-yaw * 0.2f + blade * 0.55f)).rotateX(rad(pitch * 0.35f));

        // aim frame (body space): exactly where the player looks
        aim.identity().rotateY(rad(-yaw)).rotateX(rad(pitch));

        Vector3f shoulderL = bones[CHEST].transformPosition(0.185f, 0.165f, -0.01f, new Vector3f());
        Vector3f shoulderR = bones[CHEST].transformPosition(-0.185f, 0.165f, -0.01f, new Vector3f());
        Vector3f aimFwd = aim.transform(new Vector3f(0, 0, 1));

        hasGun = false;
        hasGun2 = false;
        magMode = 0;
        grenadeVisible = true;
        WeaponGeometry g = in.geo;

        Vector3f wristR = null, wristL = null;
        Vector3f poleR = new Vector3f(-0.55f, -0.8f, -0.25f), poleL = new Vector3f(0.55f, -0.8f, -0.1f);
        float fire = in.fire;

        if (hold != null && g != null) {
            hasGun = true;
            Matrix3f extra = new Matrix3f();
            Vector3f pos = new Vector3f();
            switch (hold) {
                case RIFLE -> {
                    // stock on the right shoulder, gun along the aim line
                    Vector3f stock = new Vector3f(shoulderR).add(aim.transform(new Vector3f(0.045f, -0.005f, 0.06f)));
                    pos.set(stock).add(new Vector3f(aimFwd).mul(0.36f));
                    pos.add(aim.transform(new Vector3f(0, -0.035f, 0)));
                    float r = in.reload;
                    if (r >= 0) {
                        float tilt = window(r, 0.02f, 0.82f, 0.12f);
                        extra.rotateX(rad(18f * tilt)).rotateZ(rad(-25f * tilt));
                        pos.add(aim.transform(new Vector3f(0.03f * tilt, -0.06f * tilt, -0.06f * tilt)));
                    }
                }
                case PISTOL, DUAL -> {
                    Vector3f pivot = bones[CHEST].transformPosition(0, 0.15f, 0.02f, new Vector3f());
                    float ext = in.scoped ? 0.48f : 0.44f;
                    pos.set(pivot).add(aim.transform(new Vector3f(hold == Hold.DUAL ? -0.13f : -0.05f, -0.04f, ext)));
                    if (in.reload >= 0) {
                        float tilt = window(in.reload, 0.02f, 0.8f, 0.12f);
                        extra.rotateX(rad(25f * tilt)).rotateZ(rad(-20f * tilt));
                        pos.add(aim.transform(new Vector3f(0, -0.08f * tilt, -0.12f * tilt)));
                    }
                }
                case KNIFE -> {
                    Vector3f pivot = bones[CHEST].transformPosition(0, 0.0f, 0.0f, new Vector3f());
                    pos.set(pivot).add(aim.transform(new Vector3f(-0.2f, -0.08f, 0.32f)));
                    extra.rotateX(rad(-25f));
                    if (in.melee >= 0) {
                        float m = in.melee;
                        float sw = Mth.sin(m * Mth.PI);
                        extra.rotateY(rad(-70f + 140f * smooth(0.1f, 0.6f, m)));
                        pos.add(aim.transform(new Vector3f(0.2f * smooth(0.1f, 0.6f, m) - 0.1f, 0.05f * sw, 0.15f * sw)));
                    }
                }
                case GRENADE -> {
                    float t = in.throwAnim;
                    Vector3f base = new Vector3f(shoulderR).add(aim.transform(new Vector3f(-0.08f, 0.12f, -0.02f)));
                    if (t >= 0) {
                        float back = smooth(0f, 0.35f, t) * (1 - smooth(0.4f, 0.55f, t));
                        float fwd = smooth(0.4f, 0.6f, t);
                        base.add(aim.transform(new Vector3f(0, 0.1f * back - 0.35f * fwd, -0.2f * back + 0.5f * fwd)));
                        grenadeVisible = t < 0.5f;
                        poleR.set(-0.4f, -0.5f, -0.8f);
                    }
                    pos.set(base);
                }
                case C4 -> {
                    Vector3f pivot = bones[CHEST].transformPosition(0, -0.1f, 0.05f, new Vector3f());
                    pos.set(pivot).add(aim.transform(new Vector3f(0, -0.1f, 0.3f)));
                }
            }
            // draw animation: weapon swings up from below
            float d = 1f - in.deploy;
            if (d > 0) {
                extra.rotateX(rad(55f * d));
                pos.add(0, -0.25f * d, 0);
            }
            // fire kick
            if (fire > 0) {
                float k = hold == Hold.PISTOL || hold == Hold.DUAL ? 1.6f : 1f;
                extra.rotateX(rad(-4f * fire * k));
                pos.sub(new Vector3f(aimFwd).mul(0.025f * fire * k));
            }
            gunRot.set(aim).mul(extra).rotateY(Mth.PI);
            gun.identity().translate(pos).mul(new Matrix4f().set(gunRot)).translate(-g.grip.x, -g.grip.y, -g.grip.z);

            // ------------------------------------------------ hands on the weapon
            float a = rad(g.gripAngle);
            float sa = Mth.sin(a), ca = Mth.cos(a);
            if (hold == Hold.KNIFE) {
                handR.set(-1, 0, 0, 0, 1, 0, 0, 0, -1);
                wristR = wristFor(gun, g.grip, handR, FIST_R);
            } else if (hold == Hold.GRENADE || hold == Hold.C4) {
                handR.set(-1, 0, 0, 0, 0, 1, 0, 1, 0);
                wristR = wristFor(gun, g.grip, handR, FIST_R);
            } else {
                handR.set(-1, 0, 0, 0, sa, ca, 0, ca, -sa);
                Vector3f fist = g.gripUp(new Vector3f()).mul(-0.035f).add(g.grip);
                wristR = wristFor(gun, fist, handR, FIST_R);
            }
            // left hand
            if (hold == Hold.RIFLE || hold == Hold.PISTOL) {
                Vector3f supportPt = new Vector3f(g.support);
                switch (g.supportKind) {
                    case VERTICAL, STRAP, MAG -> handL.set(-1, 0, 0, 0, 0, 1, 0, 1, 0);
                    case PISTOL -> {
                        Vector3f z = new Vector3f(0, 0.45f, -1).normalize();
                        Vector3f x = new Vector3f(-1, 0, 0);
                        Vector3f y = new Vector3f(z).cross(x);
                        handL.set(x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z);
                    }
                    default -> {
                        handL.set(0, -1, 0, -1, 0, 0, 0, 0, -1);
                        supportPt.add(0, 0.022f, 0);
                    }
                }
                if (g.pistol && g.supportKind == WeaponGeometry.Support.UNDER) {
                    Vector3f z = new Vector3f(0, 0.45f, -1).normalize();
                    Vector3f x = new Vector3f(-1, 0, 0);
                    Vector3f y = new Vector3f(z).cross(x);
                    handL.set(x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z);
                    supportPt.set(g.support);
                }
                wristL = wristFor(gun, supportPt, handL, FIST_L);
                // slide the support hand back along the gun until the arm can reach it
                for (int i = 0; i < 12 && wristL.distance(shoulderL) > UPPER_ARM + FOREARM - 0.005f; i++) {
                    supportPt.add(0, 0, 0.02f);
                    wristL = wristFor(gun, supportPt, handL, FIST_L);
                }
                // reload: left hand goes to the magazine, to the vest pouch and back
                float r = in.reload;
                if (r >= 0 && !g.shellReload) {
                    Vector3f magWorld = gun.transformPosition(new Vector3f(g.mag).add(0, -0.04f, 0));
                    Vector3f pouch = bones[CHEST].transformPosition(0.09f, -0.18f, 0.16f, new Vector3f());
                    Vector3f magIn = gun.transformPosition(new Vector3f(g.mag));
                    Matrix3f handMag = new Matrix3f(handL);
                    Vector3f target;
                    if (r < 0.12f) target = lerp(fistOf(wristL, handL, FIST_L), magWorld, smooth(0f, 0.12f, r));
                    else if (r < 0.3f) { target = lerp(magWorld, new Vector3f(magWorld).add(0, -0.2f, 0), smooth(0.12f, 0.3f, r)); magMode = r < 0.28f ? 1 : 2; }
                    else if (r < 0.5f) { target = lerp(new Vector3f(magWorld).add(0, -0.2f, 0), pouch, smooth(0.3f, 0.5f, r)); magMode = 2; }
                    else if (r < 0.7f) { target = lerp(pouch, new Vector3f(magIn).add(0, -0.12f, 0), smooth(0.5f, 0.7f, r)); magMode = 1; }
                    else if (r < 0.8f) { target = lerp(new Vector3f(magIn).add(0, -0.12f, 0), magIn, smooth(0.7f, 0.8f, r)); magMode = r < 0.78f ? 1 : 0; }
                    else target = lerp(magIn, fistOf(wristL, handL, FIST_L), smooth(0.8f, 0.95f, r));
                    Matrix3f bodyRot = new Matrix3f(gunRot).mul(handMag);
                    wristL = new Vector3f(target).sub(bodyRot.transform(new Vector3f(FIST_L)));
                    if (magMode == 1) {
                        magInHand.identity().translate(target).mul(new Matrix4f().set(gunRot)).translate(-g.mag.x, -g.mag.y + 0.03f, -g.mag.z);
                    }
                } else if (r >= 0) {
                    // shotgun shells: hand cycles between the pouch and the loading port
                    float cyc = (r * 4f) % 1f;
                    Vector3f pouch = bones[CHEST].transformPosition(0.05f, -0.22f, 0.14f, new Vector3f());
                    Vector3f port = gun.transformPosition(new Vector3f(g.mag));
                    Vector3f target = lerp(pouch, port, Mth.sin(cyc * Mth.PI));
                    wristL = new Vector3f(target).sub(new Matrix3f(gunRot).mul(handL).transform(new Vector3f(FIST_L)));
                }
            } else if (hold == Hold.DUAL) {
                hasGun2 = true;
                // mirror the right gun to the other side of the aim line
                Vector3f p2 = gun.transformPosition(new Vector3f(g.grip));
                Vector3f side = aim.transform(new Vector3f(0.26f, 0, 0));
                p2.add(side);
                gun2.identity().translate(p2).mul(new Matrix4f().set(gunRot)).translate(-g.grip.x, -g.grip.y, -g.grip.z);
                handL.set(handR);
                Vector3f fist = g.gripUp(new Vector3f()).mul(-0.035f).add(g.grip);
                wristL = wristFor(gun2, fist, handL, FIST_L);
            }
        }

        // ---------------------------------------------------------------- arms
        if (wristR == null) {
            float swing = Mth.cos(phase) * 0.12f * walk;
            wristR = bones[CHEST].transformPosition(-0.24f, -0.44f + crouch * 0.05f, 0.02f + swing, new Vector3f());
            ikArm(shoulderR, wristR, poleR, bones[CHEST].get3x3(new Matrix3f()), true, UPPERARM_R, FOREARM_R, HAND_R);
        } else {
            ikArm(shoulderR, wristR, poleR, new Matrix3f(gunRot).mul(handR), false, UPPERARM_R, FOREARM_R, HAND_R);
        }
        if (wristL == null) {
            float swing = -Mth.cos(phase) * 0.12f * walk;
            wristL = bones[CHEST].transformPosition(0.24f, -0.44f + crouch * 0.05f, 0.02f + swing, new Vector3f());
            Matrix3f rest = bones[CHEST].get3x3(new Matrix3f());
            ikArm(shoulderL, wristL, poleL, rest, true, UPPERARM_L, FOREARM_L, HAND_L);
        } else {
            ikArm(shoulderL, wristL, poleL, new Matrix3f(gunRot).mul(handL), false, UPPERARM_L, FOREARM_L, HAND_L);
        }

        // ---------------------------------------------------------------- legs
        float ma = rad(in.moveAngle);
        Vector3f mdir = new Vector3f(-Mth.sin(ma), 0, Mth.cos(ma));
        float stride = 0.26f * walk * (1 - in.air) * (1 - crouch * 0.4f);
        for (int side = 0; side < 2; side++) {
            boolean left = side == 0;
            float s = left ? 1 : -1;
            float ph = phase + (left ? 0 : Mth.PI);
            Vector3f hip = pelvis.transformPosition(0.095f * s, -0.06f, 0, new Vector3f());
            float along = Mth.sin(ph) * stride;
            float lift = Math.max(0, Mth.cos(ph)) * 0.11f * walk * (1 - in.air);
            float stagger = rifle ? (left ? 0.1f : -0.06f) : (left ? 0.03f : -0.02f);
            stagger += crouch * (left ? 0.12f : -0.1f);
            Vector3f ankle = new Vector3f(hip.x * (1f + crouch * 0.35f) + mdir.x * along, 0.075f + lift, stagger + mdir.z * along);
            if (in.air > 0) {
                ankle.lerp(new Vector3f(hip.x, pelvisY - 0.62f, 0.1f + (left ? 0.08f : -0.05f)), in.air);
            }
            ikLeg(hip, ankle, left ? THIGH_L : THIGH_R, left ? SHIN_L : SHIN_R, left ? FOOT_L : FOOT_R, in.air);
        }
        return this;
    }

    private static Vector3f lerp(Vector3f a, Vector3f b, float t) {
        return new Vector3f(a).lerp(b, t);
    }

    private Vector3f fistOf(Vector3f wrist, Matrix3f handModelRot, Vector3f fistOffset) {
        return new Vector3f(wrist).add(new Matrix3f(gunRot).mul(handModelRot).transform(new Vector3f(fistOffset)));
    }

    /** wrist position (body space) so that the fist grips model point p with the hand frame given in model space */
    private Vector3f wristFor(Matrix4f gunM, Vector3f modelPoint, Matrix3f handModelRot, Vector3f fistOffset) {
        Vector3f p = gunM.transformPosition(new Vector3f(modelPoint));
        Matrix3f rot = gunM.get3x3(new Matrix3f()).mul(handModelRot);
        return p.sub(rot.transform(new Vector3f(fistOffset)));
    }

    private void ikArm(Vector3f shoulder, Vector3f wrist, Vector3f pole, Matrix3f handRot, boolean rest, int upper, int fore, int hand) {
        Vector3f elbow = solve(shoulder, wrist, UPPER_ARM, FOREARM, pole, tmpA);
        Vector3f w = tmpD.set(wrist);
        // clamp wrist to reach
        Vector3f d = new Vector3f(wrist).sub(shoulder);
        float L = d.length();
        if (L > UPPER_ARM + FOREARM - 1e-4f) w.set(shoulder).add(d.mul((UPPER_ARM + FOREARM - 1e-4f) / L));
        // upper arm: -Y along the bone
        Vector3f yU = new Vector3f(shoulder).sub(elbow).normalize();
        Vector3f fwd = new Vector3f(w).sub(elbow);
        Vector3f zU = fwd.sub(new Vector3f(yU).mul(fwd.dot(yU)));
        if (zU.lengthSquared() < 1e-8f) zU.set(pole).cross(yU);
        zU.normalize();
        Vector3f xU = new Vector3f(yU).cross(zU).normalize();
        setBone(bones[upper], shoulder, xU, yU, zU);
        // forearm: twist follows the hand
        Vector3f yF = new Vector3f(elbow).sub(w).normalize();
        Vector3f hx = handRot.getColumn(0, new Vector3f());
        Vector3f xF = hx.sub(new Vector3f(yF).mul(hx.dot(yF)));
        if (xF.lengthSquared() < 1e-8f) xF.set(xU);
        xF.normalize();
        Vector3f zF = new Vector3f(xF).cross(yF).normalize();
        setBone(bones[fore], elbow, xF, yF, zF);
        Matrix4f h = bones[hand].identity();
        if (rest) {
            // relaxed hand continues the forearm
            h.set(bones[fore]).setTranslation(w.x, w.y, w.z);
        } else {
            h.set(handRot).setTranslation(w.x, w.y, w.z);
        }
    }

    private void ikLeg(Vector3f hip, Vector3f ankle, int thigh, int shin, int foot, float air) {
        Vector3f pole = new Vector3f(0, 0, 1);
        Vector3f knee = solve(hip, ankle, THIGH, SHIN, pole, tmpB);
        Vector3f a = new Vector3f(ankle);
        Vector3f d = new Vector3f(ankle).sub(hip);
        float L = d.length();
        if (L > THIGH + SHIN - 1e-4f) a.set(hip).add(d.mul((THIGH + SHIN - 1e-4f) / L));
        Vector3f yT = new Vector3f(hip).sub(knee).normalize();
        Vector3f zT = new Vector3f(pole).sub(new Vector3f(yT).mul(pole.dot(yT))).normalize();
        Vector3f xT = new Vector3f(yT).cross(zT).normalize();
        setBone(bones[thigh], hip, xT, yT, zT);
        Vector3f yS = new Vector3f(knee).sub(a).normalize();
        Vector3f zS = new Vector3f(pole).sub(new Vector3f(yS).mul(pole.dot(yS))).normalize();
        Vector3f xS = new Vector3f(yS).cross(zS).normalize();
        setBone(bones[shin], knee, xS, yS, zS);
        bones[foot].identity().translate(a).rotateX(rad(-25f * air));
    }

    private static void setBone(Matrix4f m, Vector3f origin, Vector3f x, Vector3f y, Vector3f z) {
        m.set(x.x, x.y, x.z, 0, y.x, y.y, y.z, 0, z.x, z.y, z.z, 0, origin.x, origin.y, origin.z, 1);
    }

    /** two bone IK: returns the joint (elbow / knee) position */
    private static Vector3f solve(Vector3f root, Vector3f target, float a, float b, Vector3f pole, Vector3f out) {
        Vector3f d = new Vector3f(target).sub(root);
        float L = Mth.clamp(d.length(), Math.abs(a - b) + 1e-3f, a + b - 1e-4f);
        Vector3f dir = d.lengthSquared() > 1e-10f ? d.normalize() : new Vector3f(0, -1, 0);
        float cosA = Mth.clamp((a * a + L * L - b * b) / (2 * a * L), -1f, 1f);
        float sinA = Mth.sqrt(Math.max(0, 1 - cosA * cosA));
        Vector3f pp = new Vector3f(pole).sub(new Vector3f(dir).mul(pole.dot(dir)));
        if (pp.lengthSquared() < 1e-8f) pp.set(0, 0, 1).sub(new Vector3f(dir).mul(dir.z));
        pp.normalize();
        return out.set(root).add(new Vector3f(dir).mul(a * cosA)).add(pp.mul(a * sinA));
    }

    public Vector3f bonePos(int bone, Vector3f out) {
        return bones[bone].getTranslation(out);
    }
}
