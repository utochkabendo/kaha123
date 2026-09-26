package dev.csarsenal.weapon;

/**
 * Static description of one CS2 weapon. All distances are in Source units (1 unit = 0.025 blocks,
 * a 72 unit tall CS player == a 1.8 block tall Minecraft player), speeds in units/second, angles in degrees,
 * inaccuracy values use the CS weapon script convention (milliradians of spread radius).
 */
public final class WeaponDef {
    public enum Category { PISTOL, SMG, RIFLE, SNIPER, SHOTGUN, MACHINEGUN, KNIFE, TASER, GRENADE, EQUIPMENT, C4 }

    public enum FireMode { SEMI, AUTO, BURST }

    /** How the weapon is held in third person and posed in first person. */
    public enum Hold { RIFLE, PISTOL, DUAL, KNIFE, GRENADE, C4 }

    /** Reload animation family. */
    public enum ReloadStyle { RIFLE, PISTOL, SHOTGUN_SHELLS, SNIPER, MACHINEGUN, REVOLVER, NONE }

    public final String id;
    public int index;
    public final Category category;
    public final Hold hold;
    public final ReloadStyle reloadStyle;
    public final int price;
    public final int killReward;

    public final float damage;
    public final float armorPenetration;   // 0..1, share of damage that ignores kevlar
    public final float rangeModifier;      // damage *= rangeModifier ^ (distance / 500u)
    public final float penetration;        // wall penetration power
    public final float range;              // units
    public final float headMultiplier;
    public final int pellets;

    public final float cycleTime;          // seconds between shots
    public final float cycleTimeAlt;       // burst / alt fire cycle
    public final FireMode fireMode;
    public final FireMode altFireMode;     // right click mode switch (glock/famas burst), or SEMI if unused
    public final int magSize;
    public final int reserve;
    public final float reloadTime;
    public final float deployTime;

    public final float maxSpeed;
    public final float maxSpeedAlt;        // scoped speed

    // accuracy
    public final float spread, inaccStand, inaccCrouch, inaccMove, inaccJump, inaccJumpApex, inaccLand, inaccFire;
    public final float recoveryStand, recoveryCrouch;
    // alternate (scoped / silencer on) accuracy
    public final float spreadAlt, inaccStandAlt, inaccCrouchAlt, inaccMoveAlt, inaccFireAlt;
    public final boolean hasAltAccuracy;

    public final RecoilPattern pattern;
    public final float viewPunch;          // camera kick per shot (deg)
    public final float tagging;            // slow applied to victims (0..1)

    public final float[] zoomFov;          // CS FOV values for each zoom level (base 90)
    public final boolean silencer;         // removable silencer (M4A1-S / USP-S)
    public final boolean integrallySuppressed;
    public final boolean boltAction;       // re-chamber animation after every shot
    public final int tracerFrequency;
    public final String sound;             // sound event group (weapon.<sound>.fire)
    public final String mesh;

    private WeaponDef(Builder b) {
        this.id = b.id;
        this.category = b.category;
        this.hold = b.hold;
        this.reloadStyle = b.reloadStyle;
        this.price = b.price;
        this.killReward = b.killReward;
        this.damage = b.damage;
        this.armorPenetration = b.armorPen;
        this.rangeModifier = b.rangeMod;
        this.penetration = b.penetration;
        this.range = b.range;
        this.headMultiplier = b.headMult;
        this.pellets = b.pellets;
        this.cycleTime = b.cycle;
        this.cycleTimeAlt = b.cycleAlt < 0 ? b.cycle : b.cycleAlt;
        this.fireMode = b.fireMode;
        this.altFireMode = b.altFireMode;
        this.magSize = b.mag;
        this.reserve = b.reserve;
        this.reloadTime = b.reload;
        this.deployTime = b.deploy;
        this.maxSpeed = b.speed;
        this.maxSpeedAlt = b.speedAlt < 0 ? b.speed : b.speedAlt;
        this.spread = b.spread;
        this.inaccStand = b.stand;
        this.inaccCrouch = b.crouch;
        this.inaccMove = b.move;
        this.inaccJump = b.jump;
        this.inaccJumpApex = b.jumpApex < 0 ? b.jump * 0.3f : b.jumpApex;
        this.inaccLand = b.land;
        this.inaccFire = b.fire;
        this.recoveryStand = b.recStand;
        this.recoveryCrouch = b.recCrouch;
        this.hasAltAccuracy = b.hasAlt;
        this.spreadAlt = b.hasAlt ? b.spreadAlt : b.spread;
        this.inaccStandAlt = b.hasAlt ? b.standAlt : b.stand;
        this.inaccCrouchAlt = b.hasAlt ? b.crouchAlt : b.crouch;
        this.inaccMoveAlt = b.hasAlt ? b.moveAlt : b.move;
        this.inaccFireAlt = b.hasAlt ? b.fireAlt : b.fire;
        this.pattern = b.pattern;
        this.viewPunch = b.viewPunch;
        this.tagging = b.tagging;
        this.zoomFov = b.zoom;
        this.silencer = b.silencer;
        this.integrallySuppressed = b.integral;
        this.boltAction = b.bolt;
        this.tracerFrequency = b.tracer;
        this.sound = b.sound == null ? b.id : b.sound;
        this.mesh = b.mesh == null ? b.id : b.mesh;
    }

    public boolean isGun() {
        return switch (category) {
            case PISTOL, SMG, RIFLE, SNIPER, SHOTGUN, MACHINEGUN, TASER -> true;
            default -> false;
        };
    }

    public boolean isPrimary() {
        return switch (category) {
            case SMG, RIFLE, SNIPER, SHOTGUN, MACHINEGUN -> true;
            default -> false;
        };
    }

    public boolean hasScope() {
        return zoomFov.length > 0;
    }

    public float cycle(boolean alt) {
        return alt ? cycleTimeAlt : cycleTime;
    }

    public float maxSpeed(boolean scoped) {
        return scoped ? maxSpeedAlt : maxSpeed;
    }

    public static Builder builder(String id, Category c) {
        return new Builder(id, c);
    }

    public static final class Builder {
        final String id;
        final Category category;
        Hold hold = Hold.RIFLE;
        ReloadStyle reloadStyle = ReloadStyle.RIFLE;
        int price, killReward = 300;
        float damage = 30, armorPen = 0.75f, rangeMod = 0.98f, penetration = 2f, range = 8192, headMult = 4f;
        int pellets = 1;
        float cycle = 0.1f, cycleAlt = -1;
        FireMode fireMode = FireMode.AUTO, altFireMode = FireMode.SEMI;
        int mag = 30, reserve = 90;
        float reload = 2.5f, deploy = 1.0f;
        float speed = 215, speedAlt = -1;
        float spread = 0.6f, stand = 6f, crouch = 4.5f, move = 150f, jump = 130f, jumpApex = -1, land = 0.2f, fire = 7f;
        float recStand = 0.4f, recCrouch = 0.3f;
        boolean hasAlt;
        float spreadAlt, standAlt, crouchAlt, moveAlt, fireAlt;
        RecoilPattern pattern = RecoilPattern.NONE;
        float viewPunch = 0.3f;
        float tagging = 0.5f;
        float[] zoom = new float[0];
        boolean silencer, integral, bolt;
        int tracer = 3;
        String sound, mesh;

        Builder(String id, Category c) {
            this.id = id;
            this.category = c;
            switch (c) {
                case PISTOL -> { hold = Hold.PISTOL; reloadStyle = ReloadStyle.PISTOL; fireMode = FireMode.SEMI; speed = 240; penetration = 1f; range = 4096; tracer = 1; killReward = 300; }
                case SMG -> { speed = 240; penetration = 1f; range = 4096; killReward = 600; }
                case SNIPER -> { reloadStyle = ReloadStyle.SNIPER; fireMode = FireMode.SEMI; penetration = 2.5f; tracer = 1; }
                case SHOTGUN -> { reloadStyle = ReloadStyle.SHOTGUN_SHELLS; fireMode = FireMode.SEMI; penetration = 1f; range = 3000; tracer = 1; killReward = 900; }
                case MACHINEGUN -> { reloadStyle = ReloadStyle.MACHINEGUN; }
                case KNIFE -> { hold = Hold.KNIFE; reloadStyle = ReloadStyle.NONE; speed = 250; killReward = 1500; }
                case TASER -> { hold = Hold.PISTOL; reloadStyle = ReloadStyle.NONE; speed = 240; fireMode = FireMode.SEMI; killReward = 0; }
                case GRENADE -> { hold = Hold.GRENADE; reloadStyle = ReloadStyle.NONE; speed = 245; }
                case EQUIPMENT -> { hold = Hold.GRENADE; reloadStyle = ReloadStyle.NONE; speed = 250; }
                case C4 -> { hold = Hold.C4; reloadStyle = ReloadStyle.NONE; speed = 250; }
                default -> { }
            }
        }

        public Builder hold(Hold h) { this.hold = h; return this; }
        public Builder reloadStyle(ReloadStyle r) { this.reloadStyle = r; return this; }
        public Builder price(int p, int reward) { this.price = p; this.killReward = reward; return this; }
        public Builder price(int p) { this.price = p; return this; }
        public Builder damage(float d, float armorPenPercent) { this.damage = d; this.armorPen = armorPenPercent / 100f; return this; }
        public Builder rangeMod(float r) { this.rangeMod = r; return this; }
        public Builder penetration(float p) { this.penetration = p; return this; }
        public Builder range(float r) { this.range = r; return this; }
        public Builder headshot(float h) { this.headMult = h; return this; }
        public Builder pellets(int p) { this.pellets = p; return this; }
        public Builder rpm(float rpm) { this.cycle = 60f / rpm; return this; }
        public Builder cycle(float c) { this.cycle = c; return this; }
        public Builder altCycle(float c) { this.cycleAlt = c; return this; }
        public Builder mode(FireMode m) { this.fireMode = m; return this; }
        public Builder altMode(FireMode m) { this.altFireMode = m; return this; }
        public Builder ammo(int mag, int reserve) { this.mag = mag; this.reserve = reserve; return this; }
        public Builder reload(float r) { this.reload = r; return this; }
        public Builder deploy(float d) { this.deploy = d; return this; }
        public Builder speed(float s) { this.speed = s; return this; }
        public Builder speed(float s, float scoped) { this.speed = s; this.speedAlt = scoped; return this; }
        public Builder accuracy(float spread, float stand, float crouch, float move, float jump, float fire) {
            this.spread = spread; this.stand = stand; this.crouch = crouch; this.move = move; this.jump = jump; this.fire = fire; return this;
        }
        public Builder apex(float a) { this.jumpApex = a; return this; }
        public Builder recovery(float stand, float crouch) { this.recStand = stand; this.recCrouch = crouch; return this; }
        public Builder alt(float spread, float stand, float crouch, float move, float fire) {
            this.hasAlt = true; this.spreadAlt = spread; this.standAlt = stand; this.crouchAlt = crouch; this.moveAlt = move; this.fireAlt = fire; return this;
        }
        public Builder pattern(RecoilPattern p) { this.pattern = p; return this; }
        public Builder punch(float p) { this.viewPunch = p; return this; }
        public Builder tagging(float t) { this.tagging = t; return this; }
        public Builder zoom(float... fov) { this.zoom = fov; return this; }
        public Builder silencer() { this.silencer = true; return this; }
        public Builder suppressed() { this.integral = true; return this; }
        public Builder bolt() { this.bolt = true; return this; }
        public Builder tracer(int t) { this.tracer = t; return this; }
        public Builder sound(String s) { this.sound = s; return this; }
        public Builder mesh(String m) { this.mesh = m; return this; }

        public WeaponDef build() { return new WeaponDef(this); }
    }
}
