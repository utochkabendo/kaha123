package dev.csarsenal.anim;

import dev.csarsenal.ballistics.HitGroup;
import dev.csarsenal.weapon.WeaponDef.Hold;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * CS weapon poses for the vanilla Minecraft player model. Produces the ModelPart angles (applied to the real
 * HumanoidModel by a mixin), the weapon transform and the per part hitboxes, all from the same numbers so the
 * hitboxes always match what is rendered.
 * <p>
 * Model space is Minecraft's humanoid model space: pixels, +Y down, -Z forward, -X = the character's right.
 */
public final class McPose {
    public static final class Part {
        public float x, y, z, xRot, yRot, zRot, yScale = 1;

        void set(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            xRot = yRot = zRot = 0;
            yScale = 1;
        }

        public Matrix4f matrix(Matrix4f out) {
            out.identity().translate(x / 16f, y / 16f, z / 16f).rotate(new Quaternionf().rotationZYX(zRot, yRot, xRot));
            if (yScale != 1) out.scale(1, yScale, 1);
            return out;
        }

        /** aim at a point and stretch slightly (max 30%) so the hand reaches it */
        void reach(Vector3f from, Vector3f target) {
            Vector3f d = new Vector3f(target).sub(from);
            aim(d);
            yScale = Mth.clamp(d.length() / ARM_REACH, 0.85f, 1.3f);
        }

        /** point the part's +Y axis (arms/legs hang along +Y) along direction d (model space) */
        void aim(Vector3f d) {
            Vector3f n = new Vector3f(d).normalize();
            float sx = -Mth.sqrt(Math.max(0, 1 - n.y * n.y));
            xRot = (float) Math.acos(Mth.clamp(n.y, -1, 1)) * -1f;
            yRot = Math.abs(sx) < 1e-4f ? 0 : (float) Math.atan2(n.x / sx, n.z / sx);
            zRot = 0;
        }
    }

    public final Part head = new Part(), body = new Part(), rightArm = new Part(), leftArm = new Part(), rightLeg = new Part(), leftLeg = new Part();
    /** weapon model space (m, +X right, +Y up, -Z forward) -> humanoid model space (blocks) */
    public final Matrix4f gun = new Matrix4f(), gun2 = new Matrix4f();
    public boolean hasGun, hasGun2, grenadeVisible = true;
    /** 0 = mag in the gun, 1 = in the left hand ({@link #magInHand}), 2 = hidden */
    public int magMode;
    public final Matrix4f magInHand = new Matrix4f();
    public boolean crouching;
    public boolean armsPosed;

    private static final float ARM_REACH = 9.5f / 16f; // pivot -> palm (blocks)

    public McPose compute(PoseInput in, boolean crouch, float headYawDeg, float headPitchDeg) {
        crouching = crouch;
        float limb = in.walkPos, amount = Math.min(1f, in.walkAmount);
        head.set(0, 0, 0);
        body.set(0, 0, 0);
        rightArm.set(-5, 2, 0);
        leftArm.set(5, 2, 0);
        rightLeg.set(-1.9f, 12, 0.1f);
        leftLeg.set(1.9f, 12, 0.1f);
        head.yRot = headYawDeg * Mth.DEG_TO_RAD;
        head.xRot = headPitchDeg * Mth.DEG_TO_RAD;
        rightLeg.xRot = Mth.cos(limb * 0.6662f) * 1.4f * amount;
        leftLeg.xRot = Mth.cos(limb * 0.6662f + Mth.PI) * 1.4f * amount;
        rightArm.xRot = Mth.cos(limb * 0.6662f + Mth.PI) * 2f * amount * 0.5f;
        leftArm.xRot = Mth.cos(limb * 0.6662f) * 2f * amount * 0.5f;
        if (in.air > 0.5f) {
            rightLeg.xRot = -0.35f;
            leftLeg.xRot = 0.25f;
        }
        if (crouch) {
            body.xRot = 0.5f;
            rightArm.xRot += 0.4f;
            leftArm.xRot += 0.4f;
            rightLeg.z = 4.0f;
            leftLeg.z = 4.0f;
            rightLeg.y = 12.2f;
            leftLeg.y = 12.2f;
            head.y = 4.2f;
            body.y = 3.2f;
            leftArm.y = 5.2f;
            rightArm.y = 5.2f;
        }
        hasGun = hasGun2 = false;
        magMode = 0;
        grenadeVisible = true;
        armsPosed = false;
        Hold hold = in.hold;
        WeaponGeometry g = in.geo;
        if (hold == null || g == null) return this;
        armsPosed = true;
        hasGun = true;

        // aim frame in model space: same rotation as the head
        Matrix3f aim = new Matrix3f().rotateY(head.yRot).rotateX(head.xRot);
        Vector3f fwd = aim.transform(new Vector3f(0, 0, -1));
        Vector3f shoulderR = new Vector3f(rightArm.x / 16f, rightArm.y / 16f, rightArm.z / 16f);
        Vector3f shoulderL = new Vector3f(leftArm.x / 16f, leftArm.y / 16f, leftArm.z / 16f);
        Matrix3f extra = new Matrix3f();
        Vector3f grip;
        float deploy = 1f - in.deploy;
        switch (hold) {
            case RIFLE -> {
                // shouldered: grip in front of the right side of the chest, the arm reaches the handguard
                grip = new Vector3f(shoulderR).add(aim.transform(new Vector3f(0.12f, 0.2f, -0.38f)));
                if (in.reload >= 0) {
                    float t = window(in.reload, 0.02f, 0.82f, 0.12f);
                    extra.rotateX(-0.35f * t).rotateZ(0.45f * t);
                    grip.add(aim.transform(new Vector3f(0.04f * t, 0.05f * t, 0.06f * t)));
                }
            }
            case PISTOL, DUAL -> {
                Vector3f dir = aim.transform(new Vector3f(hold == Hold.DUAL ? 0.02f : 0.22f, 0.12f, -1f).normalize());
                grip = new Vector3f(shoulderR).add(dir.mul(ARM_REACH));
                if (in.reload >= 0) {
                    float t = window(in.reload, 0.02f, 0.8f, 0.12f);
                    extra.rotateX(0.5f * t);
                    grip.add(aim.transform(new Vector3f(0.05f * t, 0.12f * t, 0.1f * t)));
                }
            }
            case KNIFE -> {
                Vector3f idle = new Vector3f(0.05f, 0.75f, -0.66f).normalize();
                Vector3f dir = new Vector3f(idle);
                if (in.melee >= 0) {
                    float m = in.melee;
                    Vector3f local;
                    if (in.meleeHeavy) {
                        // wind up (knife raised back), thrust forward, recover
                        float wind = smooth(0f, 0.28f, m) * (1 - smooth(0.28f, 0.4f, m));
                        float thrust = smooth(0.28f, 0.4f, m) * (1 - smooth(0.6f, 0.95f, m));
                        local = new Vector3f(0.05f, 0.3f - 0.8f * wind - 0.15f * thrust, -0.75f + 0.75f * wind - 0.35f * thrust);
                    } else {
                        // wide slash from the right (-X) across to the left, the arm out in front at chest height
                        float sweep = smooth(0.08f, 0.5f, m);
                        float out = Mth.sin(Mth.clamp(m / 0.85f, 0f, 1f) * Mth.PI);
                        local = new Vector3f(-0.9f + 1.7f * sweep, 0.15f, -0.85f).normalize().lerp(idle, 1 - out);
                    }
                    dir = aim.transform(local.normalize());
                }
                grip = new Vector3f(shoulderR).add(dir.mul(ARM_REACH));
                extra.rotateX(-0.3f);
            }
            case GRENADE -> {
                Vector3f dir = new Vector3f(0.1f, 0.55f, -0.8f).normalize();
                float t = in.throwAnim;
                if (t >= 0) {
                    float back = smooth(0f, 0.35f, t) * (1 - smooth(0.4f, 0.55f, t));
                    float swing = smooth(0.4f, 0.6f, t);
                    dir = new Vector3f(0.1f, 0.55f - 1.4f * back + 0.4f * swing, -0.8f + 1.2f * back - 0.2f * swing).normalize();
                    grenadeVisible = t < 0.5f;
                }
                grip = new Vector3f(shoulderR).add(dir.mul(ARM_REACH));
            }
            default -> {
                Vector3f dir = new Vector3f(0.35f, 0.55f, -0.75f).normalize();
                grip = new Vector3f(shoulderR).add(dir.mul(ARM_REACH));
            }
        }
        if (deploy > 0) {
            extra.rotateX(1.0f * deploy);
            grip.add(0, 0.2f * deploy, 0);
        }
        if (in.fire > 0 && hold != Hold.KNIFE && hold != Hold.GRENADE) {
            float k = hold == Hold.RIFLE ? 1f : 1.6f;
            extra.rotateX(0.07f * in.fire * k);
            grip.sub(new Vector3f(fwd).mul(0.03f * in.fire * k));
        }
        // weapon: -Z_w -> aim forward, +Y_w -> model up (-Y), +X_w -> character right (-X)
        Matrix3f rot = new Matrix3f(aim).mul(extra).rotateZ(Mth.PI);
        gun.identity().translate(grip).mul(new Matrix4f().set(rot)).translate(-g.grip.x, -g.grip.y, -g.grip.z);
        // palm sits a bit below the grip point along the grip
        Vector3f handR = gun.transformPosition(new Vector3f(g.grip).add(g.gripUp(new Vector3f()).mul(-0.03f)));
        rightArm.reach(shoulderR, handR);
        if (hold == Hold.KNIFE || hold == Hold.GRENADE || hold == Hold.C4) {
            rightArm.reach(shoulderR, grip);
            if (hold == Hold.GRENADE && in.throwAnim < 0) {
                // left hand stays near the chest
                leftArm.aim(new Vector3f(-0.2f, 0.5f, -0.6f));
            }
            return this;
        }
        Vector3f support;
        if (hold == Hold.DUAL) {
            hasGun2 = true;
            Vector3f dir = aim.transform(new Vector3f(-0.02f, 0.12f, -1f).normalize());
            Vector3f grip2 = new Vector3f(shoulderL).add(dir.mul(ARM_REACH));
            gun2.identity().translate(grip2).mul(new Matrix4f().set(rot)).translate(-g.grip.x, -g.grip.y, -g.grip.z);
            support = gun2.transformPosition(new Vector3f(g.grip));
        } else if (in.reload >= 0 && !g.shellReload && window(in.reload, 0.12f, 0.72f, 0.08f) > 0.5f) {
            // reload: left hand to the magazine / belt and back
            float r = in.reload;
            Vector3f mag = gun.transformPosition(new Vector3f(g.mag).add(0, -0.05f, 0));
            Vector3f belt = new Vector3f(0.25f, 0.75f, -0.15f);
            float away = window(r, 0.3f, 0.52f, 0.1f);
            support = new Vector3f(mag).lerp(belt, away);
            magMode = r < 0.32f ? 0 : (r < 0.55f ? 2 : 1);
            if (magMode == 1) {
                magInHand.identity().translate(support).mul(new Matrix4f().set(rot)).translate(-g.mag.x, -g.mag.y + 0.04f, -g.mag.z);
            }
        } else {
            Vector3f sp = new Vector3f(g.support);
            if (g.pistol) sp.set(g.grip).add(0, -0.05f, 0);
            support = gun.transformPosition(sp);
        }
        leftArm.reach(shoulderL, support);
        return this;
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    private static float window(float x, float a, float b, float fade) {
        return smooth(a, a + fade, x) * (1f - smooth(b, b + fade, x));
    }

    // ------------------------------------------------------------------------------------------------ world space
    /** humanoid model space -> world, exactly like LivingEntityRenderer + PlayerRenderer */
    public static Matrix4f modelToWorld(double x, double y, double z, float bodyYaw, boolean crouching, float scale) {
        Matrix4f m = new Matrix4f().translate((float) x, (float) y, (float) z);
        if (crouching) m.translate(0, -0.125f, 0);
        m.rotateY((180f - bodyYaw) * Mth.DEG_TO_RAD);
        m.scale(-1, -1, 1);
        m.scale(scale);
        m.translate(0, -1.501f, 0);
        return m;
    }

    /** hit volumes of the posed model */
    public List<HitShape> shapes(Matrix4f modelToWorld) {
        List<HitShape> out = new ArrayList<>(10);
        Matrix4f m = new Matrix4f();
        // head 8x8x8 (y -8..0)
        out.add(HitShape.box(HitGroup.HEAD, modelToWorld.mul(head.matrix(m), new Matrix4f()), 0, -4 / 16f, 0, 4.2f / 16f * s(modelToWorld), 4.2f / 16f * s(modelToWorld), 4.2f / 16f * s(modelToWorld)));
        // body 8x12x4: upper half chest, lower half stomach
        Matrix4f b = modelToWorld.mul(body.matrix(m), new Matrix4f());
        float sc = s(modelToWorld);
        out.add(HitShape.box(HitGroup.CHEST, b, 0, 3 / 16f, 0, 4 / 16f * sc, 3 / 16f * sc, 2 / 16f * sc));
        out.add(HitShape.box(HitGroup.STOMACH, b, 0, 9 / 16f, 0, 4 / 16f * sc, 3 / 16f * sc, 2 / 16f * sc));
        out.add(HitShape.box(HitGroup.RIGHT_ARM, modelToWorld.mul(rightArm.matrix(m), new Matrix4f()), -1 / 16f, 4 / 16f, 0, 2 / 16f * sc, 6 / 16f * sc * rightArm.yScale, 2 / 16f * sc));
        out.add(HitShape.box(HitGroup.LEFT_ARM, modelToWorld.mul(leftArm.matrix(m), new Matrix4f()), 1 / 16f, 4 / 16f, 0, 2 / 16f * sc, 6 / 16f * sc * leftArm.yScale, 2 / 16f * sc));
        out.add(HitShape.box(HitGroup.RIGHT_LEG, modelToWorld.mul(rightLeg.matrix(m), new Matrix4f()), 0, 6 / 16f, 0, 2 / 16f * sc, 6 / 16f * sc, 2 / 16f * sc));
        out.add(HitShape.box(HitGroup.LEFT_LEG, modelToWorld.mul(leftLeg.matrix(m), new Matrix4f()), 0, 6 / 16f, 0, 2 / 16f * sc, 6 / 16f * sc, 2 / 16f * sc));
        return out;
    }

    private static float s(Matrix4f m) {
        return new Vector3f(m.m00(), m.m01(), m.m02()).length();
    }
}
