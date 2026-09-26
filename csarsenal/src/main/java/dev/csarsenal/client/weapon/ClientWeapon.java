package dev.csarsenal.client.weapon;

import dev.csarsenal.Config;
import dev.csarsenal.anim.HitShape;
import dev.csarsenal.anim.Hitboxes;
import dev.csarsenal.anim.WeaponGeometry;
import dev.csarsenal.ballistics.Ballistics;
import dev.csarsenal.ballistics.BulletTrace;
import dev.csarsenal.client.fx.ClientFx;
import dev.csarsenal.client.fx.ClientSounds;
import dev.csarsenal.client.move.CsMovement;
import dev.csarsenal.client.move.SubtickInput;
import dev.csarsenal.network.Packets;
import dev.csarsenal.server.ServerWeapons;
import dev.csarsenal.weapon.C4Item;
import dev.csarsenal.weapon.CsItem;
import dev.csarsenal.weapon.GrenadeItem;
import dev.csarsenal.weapon.RecoilPattern;
import dev.csarsenal.weapon.WeaponDef;
import dev.csarsenal.weapon.WeaponItem;
import dev.csarsenal.weapon.WeaponState;
import dev.csarsenal.weapon.Weapons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Client side weapon controller. Runs every rendered frame so shots leave at the exact moment (and with the exact
 * view angles) they were fired - CS2 "sub-tick" shooting - independent of the 20 Hz game tick.
 */
public final class ClientWeapon {
    public static final ClientWeapon INSTANCE = new ClientWeapon();
    public static final double INSPECT_TIME = 4.2, SILENCER_TIME = 2.0, THROW_TIME = 0.55, PIN_TIME = 0.35;
    private static final RandomSource RNG = RandomSource.create();

    public ItemStack stack = ItemStack.EMPTY;
    public WeaponDef def;
    public WeaponGeometry geo;
    public int slot = -1;
    public double now, lastUpdate;

    // animation timers (seconds on the `now` clock)
    public double drawStart = -100, drawDuration = 1;
    public double lastShot = -100;
    public int shotCounter;
    public double reloadStart = -100, reloadDuration = 1;
    public boolean reloading;
    public int shellsToLoad;
    private double lastReloadProgress;
    public boolean reloadWasEmpty;
    public double inspectStart = -100;
    public double silencerStart = -100;
    public boolean silencerOn, silencerTarget;
    public double boltStart = -100;
    public double knifeStart = -100;
    public boolean knifeHeavy;
    public int knifeSide;
    public double pinStart = -100, throwStart = -100;
    public boolean pinPulled;
    public float throwStrength;
    private boolean throwLmb, throwRmb;
    public double revolverPrime = -1;
    public double lastHitConfirm = -100;
    public double landTime = -100;

    // recoil / accuracy
    public final float[] punch = new float[2];
    public float recoilIndex;
    public float penalty;
    public float shakePitch, shakeYaw, shakeRoll;
    public int sprayShots;

    // fire control
    private double nextFire;
    private boolean attackWas, useWas;
    private int burstLeft;
    private double burstNext;
    public int zoom;
    private int zoomRestore = -1;

    // predicted ammo
    public int ammo, reserve;
    private double ammoLock;
    private long shotSeq;
    private boolean walkSent;

    public boolean isScoped() {
        return zoom > 0 && def != null && def.hasScope();
    }

    public boolean holdingCs() {
        return def != null;
    }

    public boolean drawing() {
        return now < drawStart + drawDuration;
    }

    public boolean silencerBusy() {
        return now < silencerStart + SILENCER_TIME;
    }

    public boolean inspecting() {
        return now < inspectStart + INSPECT_TIME;
    }

    public boolean altAccuracy() {
        if (def == null) return false;
        if (def.silencer) return !silencerOn;
        if (def.hasScope()) return isScoped();
        if (def == Weapons.REVOLVER) return revolverFan;
        if (def.altFireMode == WeaponDef.FireMode.BURST) return burstMode();
        return false;
    }

    private boolean revolverFan;

    public boolean burstMode() {
        return def != null && def.altFireMode == WeaponDef.FireMode.BURST && WeaponItem.state(stack).altMode();
    }

    /** CS zoom -> multiplier on tan(fov/2) */
    public double zoomFactor() {
        if (!isScoped()) return 1;
        float z = def.zoomFov[Math.min(zoom, def.zoomFov.length) - 1];
        return Math.tan(Math.toRadians(z / 2)) / Math.tan(Math.toRadians(45));
    }

    public float currentInaccuracy() {
        if (def == null || !def.isGun()) return 0;
        CsMovement m = CsMovement.INSTANCE;
        return Ballistics.inaccuracy(def, altAccuracy(), isScoped(), m.horizontalSpeed(), m.onGround, (float) m.vy, m.crouchAmount, penalty)
                * negevFactor();
    }

    private float negevFactor() {
        if (def != Weapons.NEGEV) return 1f;
        return Mth.clamp(1f - Math.max(0, sprayShots - 8) * 0.06f, 0.2f, 1f);
    }

    // ============================================================================================ frame update
    public void frame(Minecraft mc, float pt) {
        LocalPlayer p = mc.player;
        now = System.nanoTime() / 1e9;
        double dt = Mth.clamp(now - lastUpdate, 0, 0.1);
        lastUpdate = now;
        if (p == null) {
            def = null;
            return;
        }
        ItemStack held = p.getMainHandItem();
        int sel = p.getInventory().selected;
        if (held.getItem() != stack.getItem() || sel != slot) switchTo(p, held, sel);
        stack = held;
        // decay recoil + accuracy penalty
        RecoilPattern.decay(punch, (float) dt);
        if (def != null && def.isGun()) {
            float rec = Mth.lerp(CsMovement.INSTANCE.crouchAmount, def.recoveryStand, def.recoveryCrouch);
            penalty *= (float) Math.pow(0.1, dt / Math.max(0.05, rec));
            if (now - lastShot > def.cycleTime * 1.25) {
                recoilIndex = Math.max(0, recoilIndex - (float) dt * 12f);
                if (now - lastShot > 0.4) sprayShots = 0;
            }
        }
        float sh = (float) Math.exp(-dt * 18);
        shakePitch *= sh;
        shakeYaw *= sh;
        shakeRoll *= sh;
        if (def == null) {
            attackWas = useWas = false;
            return;
        }
        // take the authoritative ammo from the server synced stack when no prediction is pending
        if (stack.getItem() instanceof WeaponItem) {
            WeaponState st = WeaponItem.state(stack);
            if (now > ammoLock && !reloading) {
                ammo = st.ammo();
                reserve = st.reserve();
            }
            if (!silencerBusy()) silencerOn = st.silencer();
        }
        boolean input = mc.screen == null && !p.isSpectator() && p.isAlive();
        boolean attack = input && (mc.options.keyAttack.isDown() || testAttack);
        boolean use = input && (mc.options.keyUse.isDown() || testUse);

        if (SubtickInput.walkDown != walkSent) {
            walkSent = SubtickInput.walkDown;
            PacketDistributor.sendToServer(new Packets.Action(Packets.Action.WALK, slot, walkSent ? 1 : 0));
        }

        switch (def.category) {
            case KNIFE -> knifeInput(mc, p, attack, use, pt);
            case GRENADE -> grenadeInput(mc, p, attack, use);
            case C4, EQUIPMENT -> { }
            default -> gunInput(mc, p, attack, use, pt);
        }
        if (reloading) reloadTick(p);
        if (zoomRestore >= 0 && now > boltStart + def.cycleTime - 0.12) {
            zoom = zoomRestore;
            zoomRestore = -1;
        }
        if (silencerTarget != silencerOn && now >= silencerStart + SILENCER_TIME) {
            silencerOn = silencerTarget;
        }
        attackWas = attack;
        useWas = use;
    }

    private void switchTo(LocalPlayer p, ItemStack held, int sel) {
        stack = held;
        slot = sel;
        def = CsItem.defOf(held);
        geo = def != null ? WeaponGeometry.get(def.mesh) : null;
        reloading = false;
        zoom = 0;
        zoomRestore = -1;
        inspectStart = -100;
        silencerStart = -100;
        pinPulled = false;
        burstLeft = 0;
        revolverPrime = -1;
        recoilIndex = 0;
        sprayShots = 0;
        punch[0] = punch[1] = 0;
        if (def == null) return;
        drawStart = now;
        drawDuration = Math.max(0.3, def.deployTime);
        nextFire = now + drawDuration * 0.6;
        if (held.getItem() instanceof WeaponItem) {
            WeaponState st = WeaponItem.state(held);
            ammo = st.ammo();
            reserve = st.reserve();
            silencerOn = silencerTarget = st.silencer();
        }
        ClientSounds.self(def.category == WeaponDef.Category.KNIFE ? "knife.deploy" : "weapon.draw", 0.7f, 1f);
        PacketDistributor.sendToServer(new Packets.Action(Packets.Action.DRAW, sel, 0));
    }

    // ============================================================================================ guns
    private void gunInput(Minecraft mc, LocalPlayer p, boolean attack, boolean use, float pt) {
        boolean shells = def.reloadStyle == WeaponDef.ReloadStyle.SHOTGUN_SHELLS;
        boolean busy = drawing() || silencerBusy() || (reloading && !shells) || now < boltStart + def.cycleTime * 0.9 && def.boltAction;
        boolean taserCharging = def.category == WeaponDef.Category.TASER && ammo <= 0;
        // right click
        if (use && !useWas && def != Weapons.REVOLVER) alternate(p);
        if (attack || (def == Weapons.REVOLVER && use)) inspectStart = -100;

        if (reloading && shells && attack && !attackWas && ammo > 0) {
            reloading = false;
        }
        boolean fanFire = def == Weapons.REVOLVER && use;
        boolean trigger = attack || fanFire;
        boolean pressed = trigger && !(fanFire ? useWas : attackWas);
        if (trigger && ammo <= 0 && !busy) {
            if (pressed) {
                if (!taserCharging && (reserve > 0 || Config.get(Config.INFINITE_RESERVE, false))) startReload();
                else ClientSounds.self("weapon.dryfire", 0.6f, 1f);
            }
            revolverPrime = -1;
            return;
        }
        if (busy || taserCharging) {
            if (!trigger) revolverPrime = -1;
            processBurst(mc, p, pt);
            return;
        }
        if (def == Weapons.REVOLVER) {
            revolverFan = fanFire && !attack;
            if (revolverFan) {
                if (now >= nextFire) {
                    fire(mc, p, pt);
                    nextFire = now + def.cycleTimeAlt;
                }
            } else if (attack) {
                if (revolverPrime < 0 && now >= nextFire) revolverPrime = now;
                if (revolverPrime >= 0 && now - revolverPrime >= 0.37) {
                    fire(mc, p, pt);
                    revolverPrime = -1;
                    nextFire = now + def.cycleTime;
                }
            } else {
                revolverPrime = -1;
            }
            return;
        }
        WeaponDef.FireMode mode = burstMode() ? WeaponDef.FireMode.BURST : def.fireMode;
        switch (mode) {
            case AUTO -> {
                if (attack) {
                    if (nextFire < now - def.cycleTime) nextFire = now;
                    int guard = 0;
                    while (now >= nextFire && ammo > 0 && guard++ < 4) {
                        fire(mc, p, pt);
                        nextFire += def.cycleTime;
                    }
                }
            }
            case SEMI -> {
                if (pressed && now >= nextFire) {
                    fire(mc, p, pt);
                    nextFire = now + def.cycleTime;
                }
            }
            case BURST -> {
                if (pressed && now >= nextFire && burstLeft == 0) {
                    burstLeft = 3;
                    burstNext = now;
                }
            }
        }
        processBurst(mc, p, pt);
    }

    private void processBurst(Minecraft mc, LocalPlayer p, float pt) {
        if (burstLeft > 0 && now >= burstNext) {
            if (ammo > 0) {
                fire(mc, p, pt);
                burstLeft--;
                burstNext += ServerWeapons.BURST_INTERVAL;
            } else burstLeft = 0;
            if (burstLeft == 0) nextFire = now + def.cycleTimeAlt;
        }
    }

    private void alternate(LocalPlayer p) {
        if (def.hasScope()) {
            if (reloading || drawing()) return;
            zoom = (zoom + 1) % (def.zoomFov.length + 1);
            zoomRestore = -1;
            ClientSounds.self("weapon.zoom", 0.6f, 1f);
            PacketDistributor.sendToServer(new Packets.Action(Packets.Action.SCOPE, slot, zoom));
        } else if (def.silencer) {
            if (reloading || drawing() || silencerBusy()) return;
            silencerStart = now;
            silencerTarget = !silencerOn;
            inspectStart = -100;
            PacketDistributor.sendToServer(new Packets.Action(Packets.Action.SILENCER, slot, 0));
        } else if (def.altFireMode == WeaponDef.FireMode.BURST) {
            ClientSounds.self("weapon.switchmode", 0.6f, 1f);
            PacketDistributor.sendToServer(new Packets.Action(Packets.Action.MODE, slot, 0));
        }
    }

    public void startReload() {
        if (def == null || !def.isGun() || reloading || def.category == WeaponDef.Category.TASER) return;
        boolean infinite = Config.get(Config.INFINITE_RESERVE, false);
        if (ammo >= def.magSize || (reserve <= 0 && !infinite) || drawing() || silencerBusy()) return;
        reloading = true;
        reloadStart = now;
        reloadWasEmpty = ammo == 0;
        lastReloadProgress = 0;
        zoom = 0;
        zoomRestore = -1;
        inspectStart = -100;
        burstLeft = 0;
        if (def.reloadStyle == WeaponDef.ReloadStyle.SHOTGUN_SHELLS) {
            shellsToLoad = Math.min(def.magSize - ammo, infinite ? 99 : reserve);
            reloadDuration = ServerWeapons.SHELL_START + shellsToLoad * def.reloadTime + 0.35;
        } else {
            reloadDuration = def.reloadTime;
        }
        PacketDistributor.sendToServer(new Packets.Reload(slot));
    }

    public double reloadProgress() {
        return reloading ? Mth.clamp((now - reloadStart) / reloadDuration, 0, 1) : -1;
    }

    private void reloadTick(LocalPlayer p) {
        double t = now - reloadStart;
        double prog = t / reloadDuration;
        if (def.reloadStyle == WeaponDef.ReloadStyle.SHOTGUN_SHELLS) {
            int inserted = (int) Math.floor((t - ServerWeapons.SHELL_START) / def.reloadTime);
            int before = (int) Math.floor((lastReloadProgress * reloadDuration - ServerWeapons.SHELL_START) / def.reloadTime);
            if (inserted > before && inserted >= 1 && inserted <= shellsToLoad) {
                ammo = Math.min(def.magSize, ammo + 1);
                reserve = Math.max(0, reserve - 1);
                ammoLock = now + 0.6;
                ClientSounds.self("reload.shell_insert", 0.7f, 1f);
            }
            if (inserted >= shellsToLoad && prog >= 1) {
                reloading = false;
                ClientSounds.self("reload.pump", 0.7f, 1f);
            }
        } else {
            reloadEvents(lastReloadProgress, prog);
            if (prog >= 1) {
                boolean infinite = Config.get(Config.INFINITE_RESERVE, false);
                int take = infinite ? def.magSize - ammo : Math.min(def.magSize - ammo, reserve);
                ammo += take;
                if (!infinite) reserve -= take;
                ammoLock = now + 0.8;
                reloading = false;
            }
        }
        lastReloadProgress = Math.min(prog, 1);
    }

    private void reloadEvents(double a, double b) {
        String out, in, bolt;
        double tOut, tIn, tBolt;
        switch (def.reloadStyle) {
            case PISTOL, REVOLVER -> { out = "reload.pistol_magout"; in = "reload.pistol_magin"; bolt = reloadWasEmpty ? "reload.pistol_slide" : null; tOut = 0.18; tIn = 0.62; tBolt = 0.82; }
            case SNIPER -> { out = "reload.rifle_magout"; in = "reload.rifle_magin"; bolt = "reload.sniper_boltback"; tOut = 0.22; tIn = 0.6; tBolt = 0.8; }
            case MACHINEGUN -> { out = "reload.mg_cover"; in = "reload.mg_box"; bolt = "reload.rifle_boltpull"; tOut = 0.15; tIn = 0.55; tBolt = 0.85; }
            default -> { out = "reload.rifle_magout"; in = "reload.rifle_magin"; bolt = "reload.rifle_boltpull"; tOut = 0.26; tIn = 0.68; tBolt = 0.8; }
        }
        if (a < tOut && b >= tOut) ClientSounds.self(out, 0.8f, 1f);
        if (a < tIn && b >= tIn) ClientSounds.self(in, 0.8f, 1f);
        if (bolt != null && a < tBolt && b >= tBolt) ClientSounds.self(bolt, 0.8f, 1f);
        if (def.reloadStyle == WeaponDef.ReloadStyle.SNIPER && a < 0.88 && b >= 0.88) ClientSounds.self("reload.sniper_boltforward", 0.8f, 1f);
        if (def.reloadStyle == WeaponDef.ReloadStyle.RIFLE && bolt != null && a < 0.86 && b >= 0.86) ClientSounds.self("reload.rifle_boltrelease", 0.7f, 1f);
    }

    public void inspect() {
        if (def == null || reloading || drawing() || silencerBusy() || now - lastShot < 0.3 || pinPulled) return;
        inspectStart = now;
        zoom = 0;
        ClientSounds.self("weapon.inspect", 0.5f, 1f);
        PacketDistributor.sendToServer(new Packets.Action(Packets.Action.INSPECT, slot, 0));
    }

    // ============================================================================================ firing
    private void fire(Minecraft mc, LocalPlayer p, float pt) {
        boolean alt = altAccuracy();
        boolean scoped = isScoped();
        float inacc = Ballistics.inaccuracy(def, alt, scoped, CsMovement.INSTANCE.horizontalSpeed(), CsMovement.INSTANCE.onGround,
                (float) CsMovement.INSTANCE.vy, CsMovement.INSTANCE.crouchAmount, penalty) * negevFactor();
        float spread = Ballistics.spread(def, alt);
        float yaw = p.getYRot() + punch[0] * RecoilPattern.RECOIL_SCALE;
        float pitch = p.getXRot() - punch[1] * RecoilPattern.RECOIL_SCALE;
        Vec3 eye = p.getEyePosition(pt);
        long seed = RNG.nextLong();
        int n = Math.max(1, def.pellets);
        double range = def.range * Ballistics.UNIT;
        List<Packets.Pellet> pellets = new ArrayList<>(n);
        boolean silenced = def.integrallySuppressed || (def.silencer && silencerOn);
        shotCounter++;
        boolean tracer = def.tracerFrequency > 0 && (shotCounter % def.tracerFrequency == 0) && !silenced;
        for (int i = 0; i < n; i++) {
            Vec3 dir = Ballistics.spreadDir(yaw, pitch, inacc, spread, seed, i, n);
            List<BulletTrace.Wall> walls = BulletTrace.walls(p.level(), eye, dir, range, Ballistics.MAX_PENETRATIONS + 1);
            double stop = Ballistics.stopDistance(def, walls, range);
            List<Packets.Hit> hits = detect(p, eye, dir, stop, pt);
            pellets.add(new Packets.Pellet(dir, hits));
            ClientFx.bullet(p.level(), def, eye, dir, walls, stop, hits, true, tracer && i == 0, p);
        }
        PacketDistributor.sendToServer(new Packets.Shoot(slot, def.index, (int) (shotSeq++ & 0x7fffffff), seed, eye, pellets, scoped));
        // local effects
        ClientSounds.fire(def, silenced, p.getX(), p.getEyeY(), p.getZ(), true);
        ClientFx.localShot(def, silenced);
        lastShot = now;
        ammo--;
        ammoLock = now + 0.6;
        sprayShots++;
        // recoil
        float[][] kicks = def.pattern.kicks(def.cycle(burstMode()));
        int idx = Mth.clamp((int) recoilIndex, 0, kicks.length - 1);
        float mul = alt && def.silencer ? 1.15f : 1f;
        if (def == Weapons.NEGEV) mul *= negevFactor();
        punch[0] += kicks[idx][0] * mul;
        punch[1] += kicks[idx][1] * mul;
        recoilIndex += 1;
        penalty += alt ? def.inaccFireAlt : def.inaccFire;
        if (def.boltAction) {
            boltStart = now;
            if (zoom > 0) {
                zoomRestore = zoom;
                zoom = 0;
            }
            ClientSounds.selfDelayed("reload.sniper_boltback", 0.7f, 1f, 0.45);
            ClientSounds.selfDelayed("reload.sniper_boltforward", 0.7f, 1f, 0.8);
        }
        inspectStart = -100;
    }

    /** client side hitbox test along a bullet ray (sees exactly the rendered poses) */
    public static List<Packets.Hit> detect(LocalPlayer self, Vec3 o, Vec3 d, double stop, float pt) {
        List<Packets.Hit> out = new ArrayList<>();
        AABB search = new AABB(o, o.add(d.scale(stop))).inflate(1.5);
        for (Entity e : self.level().getEntities(self, search, e -> e instanceof LivingEntity le && le.isAlive() && e.isPickable() && !e.isSpectator())) {
            List<HitShape> shapes = Hitboxes.forEntity(e, pt);
            double[] r = Hitboxes.raycast(shapes, o, d, stop);
            if (r != null) out.add(new Packets.Hit(e.getId(), (int) r[1], (float) r[0]));
        }
        out.sort(Comparator.comparingDouble(Packets.Hit::distance));
        return out.size() > 4 ? new ArrayList<>(out.subList(0, 4)) : out;
    }

    // ============================================================================================ knife
    private void knifeInput(Minecraft mc, LocalPlayer p, boolean attack, boolean use, float pt) {
        if (drawing()) return;
        double since = now - knifeStart;
        double cd = knifeHeavy ? def.cycleTimeAlt : def.cycleTime;
        if (since < cd) return;
        if (attack || use) {
            boolean heavy = use && !attack;
            knifeStart = now;
            knifeHeavy = heavy;
            knifeSide ^= 1;
            inspectStart = -100;
            swing(p, heavy, pt);
        }
    }

    private void swing(LocalPlayer p, boolean heavy, float pt) {
        Vec3 eye = p.getEyePosition(pt);
        Vec3 look = p.getViewVector(pt);
        double range = (heavy ? 32 : 48) * Ballistics.UNIT + 0.35;
        BlockHitResult bh = p.level().clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double wall = bh.getType() == HitResult.Type.BLOCK ? bh.getLocation().distanceTo(eye) : range;
        Packets.Hit best = null;
        // hull trace approximation: 5 rays
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = right.cross(look).normalize();
        for (int k = 0; k < 5; k++) {
            Vec3 dir = k == 0 ? look : look.add((k == 1 ? right : k == 2 ? right.reverse() : k == 3 ? up : up.reverse()).scale(0.12)).normalize();
            for (Packets.Hit h : detect(p, eye, dir, wall, pt)) {
                if (best == null || h.distance() < best.distance()) best = h;
            }
        }
        boolean hitWall = best == null && bh.getType() == HitResult.Type.BLOCK;
        if (hitWall) ClientFx.knifeWall(p.level(), bh);
        ClientSounds.self(best != null ? "knife.hit" : hitWall ? "knife.hitwall" : heavy ? "knife.stab" : "knife.slash", 0.8f, 1f);
        PacketDistributor.sendToServer(new Packets.Knife(slot, heavy, best != null ? best.entity() : -1, best != null ? best.group() : 0, false, eye, look, hitWall));
    }

    // ============================================================================================ grenades
    private void grenadeInput(Minecraft mc, LocalPlayer p, boolean attack, boolean use) {
        if (drawing() || now < throwStart + THROW_TIME) return;
        if (!(stack.getItem() instanceof GrenadeItem)) return;
        if (!pinPulled) {
            if ((attack && !attackWas) || (use && !useWas)) {
                pinPulled = true;
                pinStart = now;
                inspectStart = -100;
                ClientSounds.self("grenade.pin", 0.7f, 1f);
            }
            return;
        }
        if (attack) throwLmb = true;
        if (use) throwRmb = true;
        if (!attack && !use && now > pinStart + PIN_TIME) {
            throwStrength = throwLmb && throwRmb ? 0.5f : throwLmb ? 1f : 0f;
            Vec3 eye = p.getEyePosition();
            Vec3 look = p.getLookAngle();
            CsMovement m = CsMovement.INSTANCE;
            Vec3 vel = new Vec3(m.vx, m.vy, m.vz).scale(Ballistics.UNIT / 20.0);
            PacketDistributor.sendToServer(new Packets.Throw(slot, throwStrength, eye, look, vel));
            throwStart = now;
            pinPulled = false;
            throwLmb = throwRmb = false;
        }
    }

    // ============================================================================================ feedback
    public void onTagged(Packets.Tagged t) {
        CsMovement.INSTANCE.tag(t.tagging() * Mth.clamp(t.damage() / 40f, 0.3f, 1f));
    }

    // ---------------------------------------------------------------- dev autotest hooks
    public boolean testAttack, testUse;

    public void testTrigger(boolean on) {
        testAttack = on;
    }

    public void testReload() {
        startReload();
    }

    public void testKnife(boolean heavy) {
        knifeStart = now;
        knifeHeavy = heavy;
        knifeSide ^= 1;
    }

    public void testPin() {
        pinPulled = true;
        pinStart = now;
    }

    public void testScope() {
        if (def != null && def.hasScope()) zoom = 1;
    }

    public boolean isC4() {
        return stack.getItem() instanceof C4Item;
    }

    public void reset() {
        def = null;
        stack = ItemStack.EMPTY;
        slot = -1;
        reloading = false;
        zoom = 0;
        punch[0] = punch[1] = 0;
        penalty = 0;
    }
}
