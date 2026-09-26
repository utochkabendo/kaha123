package dev.csarsenal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.csarsenal.ClientConfig;
import dev.csarsenal.anim.WeaponGeometry;
import dev.csarsenal.client.ClientData;
import dev.csarsenal.client.fx.ClientFx;
import dev.csarsenal.client.move.CsMovement;
import dev.csarsenal.client.weapon.ClientWeapon;
import dev.csarsenal.server.ServerWeapons;
import dev.csarsenal.weapon.WeaponDef;
import dev.csarsenal.weapon.WeaponDef.Hold;
import dev.csarsenal.weapon.WeaponDef.ReloadStyle;
import dev.csarsenal.weapon.Weapons;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.function.Function;

/**
 * First person view model: weapon + gloved arms solved with IK onto the weapon, all animation procedural and driven
 * by {@link ClientWeapon} timers. Camera space: +X right, +Y up, -Z forward, metres.
 */
public final class ViewModelRenderer {
    private static final float FP_UPPER = 0.36f, FP_FORE = 0.32f;
    private static final Vector3f SHOULDER_R = new Vector3f(0.21f, -0.34f, 0.13f), SHOULDER_L = new Vector3f(-0.23f, -0.34f, 0.1f);
    private static final Vector3f FIST_R = new Vector3f(0.022f, -0.074f, 0f), FIST_L = new Vector3f(-0.022f, -0.074f, 0f);

    // mouse sway state
    private static float lastYaw, lastPitch, swayX, swayY;
    private static double bobPhase, lastFrame;

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

    public static void render(PoseStack ps, MultiBufferSource buf, int light, float pt, LocalPlayer p) {
        ClientWeapon w = ClientWeapon.INSTANCE;
        WeaponDef def = w.def;
        if (def == null) return;
        if (w.isScoped()) return; // CS hides the view model while scoped
        Mesh mesh = Meshes.get(def.mesh);
        Mesh agent = Meshes.get("agent");
        if (mesh == null || agent == null) return;
        WeaponGeometry g = w.geo != null ? w.geo : WeaponGeometry.get(def.mesh);
        double now = w.now;
        double dt = Mth.clamp(now - lastFrame, 0, 0.1);
        lastFrame = now;
        boolean left = ClientConfig.bool(ClientConfig.LEFT_HANDED, false);

        // ---------------------------------------------------------------- base placement
        Hold hold = def.hold;
        Vector3f pos = new Vector3f();
        float yaw, pitch, roll = 0;
        switch (hold) {
            case PISTOL, DUAL -> { pos.set(0.105f, -0.155f, -0.31f); yaw = 4.5f; pitch = 1.5f; }
            case KNIFE -> { pos.set(0.15f, -0.2f, -0.27f); yaw = 14f; pitch = 22f; roll = -18f; }
            case GRENADE -> { pos.set(0.14f, -0.2f, -0.3f); yaw = 8f; pitch = 8f; }
            case C4 -> { pos.set(0.05f, -0.24f, -0.34f); yaw = 0f; pitch = -10f; }
            default -> {
                pos.set(0.115f, -0.165f, -0.2f);
                yaw = 3.2f;
                pitch = 1.2f;
                if (def.category == WeaponDef.Category.SNIPER || def.category == WeaponDef.Category.MACHINEGUN) pos.add(0.01f, -0.01f, 0.03f);
                if (def.category == WeaponDef.Category.SMG) pos.add(0, 0, -0.04f);
                if (g.magTop) pos.add(0, -0.02f, 0);
            }
        }
        pos.add((float) (ClientConfig.num(ClientConfig.VIEWMODEL_X, 1) - 1) * 0.012f, (float) (ClientConfig.num(ClientConfig.VIEWMODEL_Y, 1) - 1) * 0.012f,
                (float) (ClientConfig.num(ClientConfig.VIEWMODEL_Z, -1) + 1) * -0.012f);

        // ---------------------------------------------------------------- procedural layers
        Vector3f off = new Vector3f();
        float dYaw = 0, dPitch = 0, dRoll = 0;
        // idle breathing
        off.y += Mth.sin((float) now * 1.3f) * 0.0016f;
        dRoll += Mth.cos((float) now * 0.9f) * 0.35f;
        // movement bob (figure eight)
        float speed = Mth.clamp(CsMovement.INSTANCE.horizontalSpeed() / 250f, 0, 1.2f) * (CsMovement.INSTANCE.onGround ? 1 : 0.2f);
        bobPhase += dt * (4 + 6 * speed) * speed;
        off.x += Mth.sin((float) bobPhase) * 0.007f * speed;
        off.y += -Math.abs(Mth.cos((float) bobPhase)) * 0.006f * speed;
        dRoll += Mth.sin((float) bobPhase) * 1.2f * speed;
        // landing dip
        off.y -= CsMovement.INSTANCE.landDip * 0.035f;
        // mouse sway (the weapon lags behind the view)
        float yawNow = p.getViewYRot(pt), pitchNow = p.getViewXRot(pt);
        float dy = Mth.wrapDegrees(yawNow - lastYaw), dp = pitchNow - lastPitch;
        lastYaw = yawNow;
        lastPitch = pitchNow;
        float k = (float) Math.exp(-dt * 10);
        swayX = swayX * k + Mth.clamp(-dy * 0.25f, -3, 3) * (1 - k) * 6;
        swayY = swayY * k + Mth.clamp(-dp * 0.25f, -3, 3) * (1 - k) * 6;
        swayX = Mth.clamp(swayX, -5, 5);
        swayY = Mth.clamp(swayY, -5, 5);
        dYaw += swayX;
        dPitch += swayY;
        off.x += swayX * 0.0012f;
        off.y -= swayY * 0.0012f;
        // draw
        double tDraw = (now - w.drawStart) / w.drawDuration;
        if (tDraw < 1) {
            float e = 1f - (float) Math.pow(1 - Mth.clamp(tDraw, 0, 1), 3);
            off.y -= 0.2f * (1 - e);
            off.z += 0.05f * (1 - e);
            dPitch -= 55f * (1 - e);
            dRoll += 25f * (1 - e);
        }
        // fire kick
        double since = now - w.lastShot;
        float kick = (float) Math.exp(-since / 0.055);
        float ks = switch (def.category) {
            case PISTOL -> hold == Hold.DUAL ? 1.1f : 1.5f;
            case SNIPER -> 2.4f;
            case SHOTGUN -> 2.6f;
            case SMG -> 0.7f;
            case MACHINEGUN -> 0.85f;
            default -> 1f;
        };
        if (def.isGun() && since < 0.5) {
            off.z += 0.03f * kick * ks;
            off.y += 0.004f * kick * ks;
            dPitch += 3.5f * kick * ks;
            dYaw += Mth.sin((float) (w.shotCounter * 1.7)) * 0.8f * kick;
        }
        // bolt action cycle (AWP / SSG)
        float boltCycle = 0;
        double tb = def.boltAction ? (now - w.boltStart) / def.cycleTime : 9;
        if (tb >= 0 && tb < 1) {
            boltCycle = window(tb, 0.25, 0.72, 0.1);
            dRoll -= 12f * boltCycle;
            off.add(0.01f * boltCycle, -0.02f * boltCycle, 0.02f * boltCycle);
        }
        // reload
        float r = (float) w.reloadProgress();
        ReloadStyle rs = def.reloadStyle;
        if (r >= 0) {
            float tilt = window(r, 0.02, 0.84, 0.12);
            switch (rs) {
                case PISTOL, REVOLVER -> { dPitch += 22f * tilt; dRoll -= 14f * tilt; off.add(-0.02f * tilt, 0.02f * tilt, 0.02f * tilt); }
                case SHOTGUN_SHELLS -> { dRoll += 28f * tilt; dPitch += 8f * tilt; off.add(-0.02f * tilt, 0.01f * tilt, 0.02f * tilt); }
                default -> { dRoll += 30f * tilt; dPitch += 7f * tilt; off.add(-0.035f * tilt, -0.015f * tilt, 0.03f * tilt); }
            }
        }
        // inspect
        double ti = (now - w.inspectStart) / ClientWeapon.INSPECT_TIME;
        float knifeSpin = 0;
        if (ti >= 0 && ti < 1) {
            if (def.category == WeaponDef.Category.KNIFE) {
                float a = window(ti, 0.0, 0.85, 0.15);
                dYaw += 30 * a;
                dPitch += 10 * a;
                off.add(-0.05f * a, 0.03f * a, 0.03f * a);
                knifeSpin = 720f * smooth(0.25, 0.7, ti);
            } else {
                float a = window(ti, 0.0, 0.42, 0.14);
                float b = window(ti, 0.47, 0.82, 0.14);
                dYaw += 48 * a - 20 * b;
                dRoll += 38 * a - 45 * b;
                dPitch += 6 * a + 18 * b;
                off.add(-0.07f * a - 0.03f * b, 0.035f * a + 0.03f * b, 0.05f * a + 0.03f * b);
                dRoll += Mth.sin((float) (ti * 12)) * 2f * (a + b);
            }
        }
        // knife attacks
        double km = now - w.knifeStart;
        if (def.category == WeaponDef.Category.KNIFE) {
            if (!w.knifeHeavy && km < 0.4) {
                float m = (float) (km / 0.4);
                float s = w.knifeSide == 0 ? 1 : -1;
                float sweep = smooth(0.05, 0.55, m);
                float out = Mth.sin(m * Mth.PI);
                dYaw += s * (40 - 100 * sweep);
                dRoll += s * -30 * out;
                off.add(s * (0.06f - 0.14f * sweep), 0.03f * out, -0.12f * out);
            } else if (w.knifeHeavy && km < 1.0) {
                float m = (float) km;
                float back = window(m, 0.0, 0.22, 0.12);
                float thrust = window(m, 0.3, 0.42, 0.25);
                off.add(0.02f * back, 0.03f * back, 0.08f * back - 0.22f * thrust);
                dPitch += 25 * back - 15 * thrust;
                dYaw -= 10 * thrust;
            }
        }
        // grenades
        boolean grenadeVisible = true, pinVisible = true;
        if (def.category == WeaponDef.Category.GRENADE) {
            double tp = now - w.pinStart, tt = now - w.throwStart;
            if (w.pinPulled) {
                pinVisible = tp < ClientWeapon.PIN_TIME * 0.7;
                float raise = smooth(ClientWeapon.PIN_TIME * 0.5, ClientWeapon.PIN_TIME + 0.15, tp);
                off.add(0.02f * raise, 0.06f * raise, 0.08f * raise);
                dPitch += 25 * raise;
            } else if (tt < ClientWeapon.THROW_TIME) {
                float t = (float) (tt / ClientWeapon.THROW_TIME);
                float swing = smooth(0, 0.35, t);
                off.add(-0.05f * swing, 0.06f - 0.3f * smooth(0.2, 1, t), 0.08f - 0.35f * swing);
                dPitch += 25 - 80 * swing;
                grenadeVisible = t < 0.3f;
                pinVisible = false;
            } else if (tt < ClientWeapon.THROW_TIME + 0.25) {
                return;
            }
        }
        // silencer (un)screwing
        double ts = (now - w.silencerStart) / ClientWeapon.SILENCER_TIME;
        boolean silAnim = ts >= 0 && ts < 1;
        if (silAnim) {
            float a = window(ts, 0.0, 0.85, 0.12);
            dYaw -= 28 * a;
            dRoll -= 10 * a;
            dPitch += 4 * a;
            off.add(-0.06f * a, 0.03f * a, 0.03f * a);
        }
        // tagging flinch (camera shake is separate)
        dPitch += w.shakePitch * 0.4f;

        // ---------------------------------------------------------------- gun matrix
        Matrix4f gun = new Matrix4f().translate(pos.x + off.x, pos.y + off.y, pos.z + off.z)
                .rotateY(rad(yaw + dYaw)).rotateX(rad(pitch + dPitch)).rotateZ(rad(roll + dRoll));
        if (knifeSpin != 0) gun.rotateX(rad(knifeSpin * 0.25f)).rotateY(rad(knifeSpin));
        gun.translate(-g.grip.x, -g.grip.y, -g.grip.z);
        Matrix3f gunRot = gun.get3x3(new Matrix3f());
        if (left) {
            // mirror for cl_righthand 0
            ps.pushPose();
            ps.scale(-1, 1, 1);
        }

        // ---------------------------------------------------------------- moving parts
        float slide = 0;
        if (def.isGun() && since < def.cycleTime * 0.8 && !def.boltAction) {
            float u = (float) (since / Math.max(0.03, def.cycleTime * 0.6));
            slide = u < 1 ? Mth.sin(Math.min(u, 1f) * Mth.PI) : 0;
        }
        boolean emptyLock = def.hold == Hold.PISTOL && w.ammo <= 0 && !(r >= 0 && r > 0.8f);
        float slideTravel = def.hold == Hold.PISTOL || def.hold == Hold.DUAL ? 0.028f : 0.04f;
        float slideZ = Math.max(slide, emptyLock ? 1f : 0f) * slideTravel;
        // magazine
        Matrix4f magM = null;   // null = in gun
        boolean magHidden = false;
        Vector3f leftTarget = null;
        Matrix3f leftRot = null;
        float boltPull = 0;
        if (r >= 0 && rs != ReloadStyle.SHOTGUN_SHELLS) {
            boolean pistol = rs == ReloadStyle.PISTOL || rs == ReloadStyle.REVOLVER;
            double outA = pistol ? 0.1 : 0.2, outB = pistol ? 0.24 : 0.32, inA = pistol ? 0.42 : 0.52, inB = pistol ? 0.6 : 0.68;
            Vector3f magDown = pistol ? g.gripUp(new Vector3f()).mul(-1) : new Vector3f(0, -1, 0);
            if (g.magTop) magDown.set(0, 1, 0);
            if (r > outA && r < outB) {
                float u = smooth(outA, outB, r);
                magM = new Matrix4f(gun).translate(new Vector3f(magDown).mul(0.05f + 0.25f * u * u));
                magHidden = r > outB - 0.03;
            } else if (r >= outB && r < inA) {
                magHidden = true;
            } else if (r >= inA && r < inB) {
                float u = 1 - smooth(inA, inB, r);
                magM = new Matrix4f(gun).translate(new Vector3f(magDown).mul(0.02f + 0.3f * u * u)).rotateZ(rad(-20 * u));
            }
            // left hand path
            Vector3f magWell = gun.transformPosition(new Vector3f(g.mag).add(new Vector3f(magDown).mul(0.05f)));
            Vector3f belt = new Vector3f(-0.1f, -0.5f, -0.2f);
            if (r < outA) leftTarget = null;
            else if (r < outB) leftTarget = magM != null ? magM.transformPosition(new Vector3f(g.mag).add(new Vector3f(magDown).mul(0.05f))) : magWell;
            else if (r < inA) leftTarget = lerp(magWell, belt, window(r, outB, (outB + inA) / 2, (inA - outB) / 3));
            else if (r < inB) leftTarget = magM != null ? magM.transformPosition(new Vector3f(g.mag).add(new Vector3f(magDown).mul(0.05f))) : magWell;
            else if (r < 0.78 && !pistol) leftTarget = magWell;
            else if (r < 0.9 && !pistol && rs != ReloadStyle.MACHINEGUN) {
                leftTarget = gun.transformPosition(new Vector3f(g.bolt).add(0.012f, 0, 0));
                boltPull = window(r, 0.79, 0.84, 0.03);
            }
            if (pistol && r > 0.78 && r < 0.86 && w.reloadWasEmpty) {
                leftTarget = gun.transformPosition(new Vector3f(g.bolt).add(0, 0.03f, 0.03f));
            }
            if (leftTarget != null) leftRot = new Matrix3f(gunRot).mul(new Matrix3f(0, -1, 0, -1, 0, 0, 0, 0, -1));
        } else if (r >= 0) {
            // shotgun shell loading: hand cycles between the pouch and the loading port
            double t = r * w.reloadDuration - ServerWeapons.SHELL_START;
            if (t > 0 && t < w.shellsToLoad * def.reloadTime) {
                float cyc = (float) ((t % def.reloadTime) / def.reloadTime);
                Vector3f port = gun.transformPosition(new Vector3f(g.mag).add(0, -0.03f, 0));
                leftTarget = lerp(new Vector3f(-0.08f, -0.42f, -0.22f), port, Mth.sin(cyc * Mth.PI));
                leftRot = new Matrix3f(gunRot).mul(new Matrix3f(0, -1, 0, -1, 0, 0, 0, 0, -1));
            }
        }
        // pump action after a shotgun shot
        float pump = 0;
        if ((def == Weapons.NOVA || def == Weapons.SAWEDOFF || def == Weapons.MAG7) && since < def.cycleTime) {
            pump = window(since / def.cycleTime, 0.25, 0.55, 0.15);
        }
        if (r >= 0 && rs == ReloadStyle.SHOTGUN_SHELLS && r > 0.93) pump = window(r, 0.93, 0.97, 0.02);

        // ---------------------------------------------------------------- hands
        float a = rad(g.gripAngle);
        float sa = Mth.sin(a), ca = Mth.cos(a);
        Matrix3f handModelR;
        Vector3f fistR;
        if (hold == Hold.KNIFE) {
            handModelR = new Matrix3f(-1, 0, 0, 0, 1, 0, 0, 0, -1);
            fistR = new Vector3f(g.grip);
        } else if (hold == Hold.GRENADE || hold == Hold.C4) {
            handModelR = new Matrix3f(-1, 0, 0, 0, 0, 1, 0, 1, 0);
            fistR = new Vector3f(g.grip);
        } else {
            handModelR = new Matrix3f(-1, 0, 0, 0, sa, ca, 0, ca, -sa);
            fistR = g.gripUp(new Vector3f()).mul(-0.035f).add(g.grip);
        }
        Matrix3f rotR = new Matrix3f(gunRot).mul(handModelR);
        Vector3f wristR = gun.transformPosition(new Vector3f(fistR)).sub(rotR.transform(new Vector3f(FIST_R)));
        // bolt action: right hand works the bolt
        if (boltCycle > 0) {
            Vector3f knob = gun.transformPosition(new Vector3f(g.bolt));
            Vector3f target = lerp(gun.transformPosition(new Vector3f(fistR)), knob, boltCycle);
            wristR = new Vector3f(target).sub(rotR.transform(new Vector3f(FIST_R)));
        }
        Vector3f wristL = null;
        Matrix3f rotL = null;
        if (leftTarget != null) {
            rotL = leftRot;
            wristL = new Vector3f(leftTarget).sub(rotL.transform(new Vector3f(FIST_L)));
        } else if (hold == Hold.RIFLE || hold == Hold.PISTOL) {
            Vector3f sup = new Vector3f(g.support);
            Matrix3f hm;
            switch (g.supportKind) {
                case VERTICAL, STRAP, MAG -> hm = new Matrix3f(-1, 0, 0, 0, 0, 1, 0, 1, 0);
                default -> {
                    if (g.pistol) {
                        Vector3f z = new Vector3f(0, 0.45f, -1).normalize();
                        Vector3f x = new Vector3f(-1, 0, 0);
                        Vector3f y = new Vector3f(z).cross(x);
                        hm = new Matrix3f(x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z);
                    } else {
                        hm = new Matrix3f(0, -1, 0, -1, 0, 0, 0, 0, -1);
                        sup.add(0, 0.022f, 0);
                    }
                }
            }
            if (pump > 0) sup.add(0, 0, 0.07f * pump);
            if (silAnim) sup.set(g.silencer).add(0, 0, -0.08f);
            if (hold == Hold.PISTOL && def.category == WeaponDef.Category.TASER) sup.set(g.support);
            rotL = new Matrix3f(gunRot).mul(hm);
            wristL = gun.transformPosition(sup).sub(rotL.transform(new Vector3f(FIST_L)));
        } else if (hold == Hold.GRENADE && w.pinPulled && now - w.pinStart < ClientWeapon.PIN_TIME) {
            // left hand pulls the pin
            Vector3f pin = gun.transformPosition(new Vector3f(g.grip).add(0, 0.05f, 0.02f));
            float u = window(now - w.pinStart, 0, ClientWeapon.PIN_TIME * 0.6, 0.1);
            Vector3f target = lerp(new Vector3f(-0.15f, -0.45f, -0.25f), pin, u);
            rotL = new Matrix3f(0, -1, 0, -1, 0, 0, 0, 0, -1);
            wristL = new Vector3f(target).sub(rotL.transform(new Vector3f(FIST_L)));
        }
        Matrix4f gun2 = null;
        if (hold == Hold.DUAL) {
            gun2 = new Matrix4f().translate(-pos.x - off.x, pos.y + off.y, pos.z + off.z)
                    .rotateY(rad(-yaw - dYaw)).rotateX(rad(pitch + dPitch)).rotateZ(rad(-roll - dRoll)).translate(-g.grip.x, -g.grip.y, -g.grip.z);
            rotL = new Matrix3f(gun2.get3x3(new Matrix3f())).mul(handModelR);
            wristL = gun2.transformPosition(new Vector3f(fistR)).sub(rotL.transform(new Vector3f(FIST_L)));
        }

        // ---------------------------------------------------------------- draw
        String team = ClientData.teamSkin(p);
        Function<String, ResourceLocation> atex = m -> Meshes.agentTexture(team, m);
        arm(ps, buf, light, agent, atex, SHOULDER_R, wristR, rotR, new Vector3f(0.7f, -0.7f, 0.2f), "r");
        if (wristL != null) arm(ps, buf, light, agent, atex, SHOULDER_L, wristL, rotL, new Vector3f(-0.7f, -0.7f, 0.2f), "l");

        drawGun(ps, buf, light, mesh, def, gun, slideZ, boltPull, pump, magM, magHidden, w, grenadeVisible, pinVisible, silAnim, ts, g, boltCycle);
        if (gun2 != null) drawGun(ps, buf, light, mesh, def, gun2, slideZ, 0, 0, null, magHidden, w, true, true, false, 0, g, 0);

        // muzzle flash
        boolean silenced = def.integrallySuppressed || (def.silencer && w.silencerOn);
        double fs = now - ClientFx.muzzleFlashTime;
        if (def.isGun() && def.category != WeaponDef.Category.TASER && !silenced && fs < 0.045) {
            Vector3f mz = def.silencer && g.hasMuzzleSil && w.silencerOn ? g.muzzleSilenced : g.muzzle;
            ps.pushPose();
            ps.mulPose(gun);
            MuzzleFlash.render(ps, buf, new Vector3f(mz), AgentRenderer.flashSize(def) * 0.9f, 1f - (float) (fs / 0.045), (float) (w.shotCounter * 67 % 360));
            ps.popPose();
        }
        if (left) ps.popPose();
    }

    private static void drawGun(PoseStack ps, MultiBufferSource buf, int light, Mesh mesh, WeaponDef def, Matrix4f gun, float slideZ, float boltPull, float pump,
                                Matrix4f magM, boolean magHidden, ClientWeapon w, boolean grenadeVisible, boolean pinVisible, boolean silAnim, double ts,
                                WeaponGeometry g, float boltCycle) {
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
                    if (def.boltAction && boltCycle > 0) {
                        float up = window(boltCycle, 0.0, 0.8, 0.2);
                        ps.translate(g.bolt.x, g.bolt.y, 0);
                        ps.mulPose(new Matrix4f().rotateZ(rad(-65 * Math.min(1, boltCycle * 2))));
                        ps.translate(-g.bolt.x, -g.bolt.y, 0.09f * up);
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
                    boolean show = w.silencerOn || silAnim;
                    if (!show) continue;
                    ps.pushPose();
                    if (silAnim) {
                        float u = (float) ts;
                        float spin = 720f * smooth(0.15, 0.85, u) * (w.silencerTarget ? -1 : 1);
                        float along = w.silencerTarget ? 0.05f * (1 - smooth(0.15, 0.85, u)) : 0.05f * smooth(0.15, 0.85, u);
                        if (!w.silencerTarget && u > 0.9) {
                            ps.popPose();
                            continue;
                        }
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
                    if (!pinVisible) continue;
                    MeshRenderer.part(mesh, part, ps, buf, light, 0xFFFFFFFF, MeshRenderer.WEAPON_TEX);
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

    private static Vector3f lerp(Vector3f a, Vector3f b, float t) {
        return new Vector3f(a).lerp(b, t);
    }

    /** two bone IK arm in camera space, rendering the operator's sleeve/forearm/glove meshes */
    private static void arm(PoseStack ps, MultiBufferSource buf, int light, Mesh agent, Function<String, ResourceLocation> tex,
                            Vector3f shoulder, Vector3f wrist, Matrix3f handRot, Vector3f pole, String side) {
        Vector3f d = new Vector3f(wrist).sub(shoulder);
        float L = Mth.clamp(d.length(), Math.abs(FP_UPPER - FP_FORE) + 1e-3f, FP_UPPER + FP_FORE - 1e-4f);
        Vector3f dir = d.lengthSquared() > 1e-10f ? new Vector3f(d).normalize() : new Vector3f(0, 0, -1);
        float cosA = Mth.clamp((FP_UPPER * FP_UPPER + L * L - FP_FORE * FP_FORE) / (2 * FP_UPPER * L), -1, 1);
        float sinA = Mth.sqrt(Math.max(0, 1 - cosA * cosA));
        Vector3f pp = new Vector3f(pole).sub(new Vector3f(dir).mul(pole.dot(dir)));
        if (pp.lengthSquared() < 1e-8f) pp.set(0, -1, 0);
        pp.normalize();
        Vector3f elbow = new Vector3f(shoulder).add(new Vector3f(dir).mul(FP_UPPER * cosA)).add(new Vector3f(pp).mul(FP_UPPER * sinA));
        Vector3f w = new Vector3f(shoulder).add(new Vector3f(dir).mul(L));
        if (d.length() < L + 1e-3f) w.set(wrist);
        // upper arm
        Vector3f yU = new Vector3f(shoulder).sub(elbow).normalize();
        Vector3f f = new Vector3f(w).sub(elbow);
        Vector3f zU = f.sub(new Vector3f(yU).mul(f.dot(yU)));
        if (zU.lengthSquared() < 1e-8f) zU.set(pp).cross(yU);
        zU.normalize();
        Vector3f xU = new Vector3f(yU).cross(zU).normalize();
        Matrix4f mU = bone(shoulder, xU, yU, zU).scale(1, FP_UPPER / 0.29f, 1);
        // forearm
        Vector3f yF = new Vector3f(elbow).sub(w).normalize();
        Vector3f hx = handRot.getColumn(0, new Vector3f());
        Vector3f xF = hx.sub(new Vector3f(yF).mul(hx.dot(yF)));
        if (xF.lengthSquared() < 1e-8f) xF.set(xU);
        xF.normalize();
        Vector3f zF = new Vector3f(xF).cross(yF).normalize();
        Matrix4f mF = bone(elbow, xF, yF, zF).scale(1, FP_FORE / 0.255f, 1);
        Matrix4f mH = new Matrix4f().set(handRot).setTranslation(w.x, w.y, w.z);
        draw(ps, buf, light, agent, "upperarm_" + side, mU, tex);
        draw(ps, buf, light, agent, "forearm_" + side, mF, tex);
        draw(ps, buf, light, agent, "hand_" + side, mH, tex);
    }

    private static Matrix4f bone(Vector3f o, Vector3f x, Vector3f y, Vector3f z) {
        return new Matrix4f(x.x, x.y, x.z, 0, y.x, y.y, y.z, 0, z.x, z.y, z.z, 0, o.x, o.y, o.z, 1);
    }

    private static void draw(PoseStack ps, MultiBufferSource buf, int light, Mesh agent, String part, Matrix4f m, Function<String, ResourceLocation> tex) {
        ps.pushPose();
        ps.mulPose(m);
        MeshRenderer.part(agent, part, ps, buf, light, 0xFFFFFFFF, tex);
        ps.popPose();
    }

    private ViewModelRenderer() {
    }
}
