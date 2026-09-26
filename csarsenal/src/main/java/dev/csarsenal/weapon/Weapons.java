package dev.csarsenal.weapon;

import dev.csarsenal.weapon.WeaponDef.Category;
import dev.csarsenal.weapon.WeaponDef.FireMode;
import dev.csarsenal.weapon.WeaponDef.Hold;
import dev.csarsenal.weapon.WeaponDef.ReloadStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every CS2 weapon with its stats (CS2 values, units as in the CS weapon scripts). */
public final class Weapons {
    private static final List<WeaponDef> ALL = new ArrayList<>();
    private static final Map<String, WeaponDef> BY_ID = new LinkedHashMap<>();

    // ------------------------------------------------------------------------------------------------ patterns
    static final RecoilPattern AK_PATTERN = RecoilPattern.of(0.05f,
            0, 0, 0.02f, 0.38f, -0.03f, 1.05f, 0.05f, 1.95f, 0.15f, 2.9f, 0.08f, 3.85f, -0.2f, 4.65f, -0.55f, 5.3f, -1.1f, 5.75f, -1.75f, 6.05f,
            -2.35f, 6.2f, -2.8f, 6.3f, -2.55f, 6.45f, -1.7f, 6.5f, -0.6f, 6.55f, 0.5f, 6.6f, 1.5f, 6.7f, 2.3f, 6.75f, 2.85f, 6.8f, 3.2f, 6.95f,
            2.95f, 7.1f, 2.2f, 7.15f, 1.2f, 7.2f, 0.5f, 7.35f, 0.9f, 7.45f, 1.8f, 7.5f, 2.7f, 7.5f, 3.3f, 7.6f, 3.0f, 7.7f, 2.4f, 7.75f);
    static final RecoilPattern M4A4_PATTERN = RecoilPattern.of(0.04f,
            0, 0, 0, 0.3f, 0.05f, 0.85f, 0.1f, 1.55f, 0.05f, 2.3f, -0.1f, 3.05f, 0f, 3.7f, 0.25f, 4.25f, 0.6f, 4.7f, 1.0f, 5.0f,
            1.5f, 5.15f, 1.9f, 5.25f, 2.05f, 5.35f, 1.6f, 5.45f, 0.9f, 5.5f, 0.1f, 5.55f, -0.7f, 5.6f, -1.4f, 5.65f, -1.9f, 5.7f, -2.2f, 5.75f,
            -1.9f, 5.8f, -1.2f, 5.85f, -0.5f, 5.9f, 0.2f, 5.95f, 0.8f, 6.0f, 1.1f, 6.05f, 0.6f, 6.1f, 0f, 6.1f, -0.6f, 6.15f, -1.0f, 6.2f);
    static final RecoilPattern M4A1S_PATTERN = RecoilPattern.of(0.04f,
            0, 0, 0, 0.28f, 0.02f, 0.75f, 0.08f, 1.35f, 0.05f, 2.0f, -0.05f, 2.6f, 0.05f, 3.1f, 0.3f, 3.5f, 0.65f, 3.8f, 0.95f, 4.0f,
            1.1f, 4.1f, 0.9f, 4.2f, 0.4f, 4.25f, -0.2f, 4.3f, -0.8f, 4.35f, -1.2f, 4.4f, -1.3f, 4.45f, -1.0f, 4.5f, -0.5f, 4.55f, 0f, 4.6f);

    // ------------------------------------------------------------------------------------------------ pistols
    public static final WeaponDef GLOCK = add(WeaponDef.builder("glock", Category.PISTOL).price(200).damage(30, 47).rangeMod(0.85f).rpm(400)
            .altMode(FireMode.BURST).altCycle(0.5f).ammo(20, 120).reload(2.27f).deploy(0.9f).accuracy(2f, 5.6f, 3.9f, 13f, 250f, 56f)
            .alt(4f, 9f, 7f, 20f, 70f).recovery(0.35f, 0.3f).pattern(RecoilPattern.kick(20, 0.55f, 0.12f)).punch(0.9f).mesh("glock").build());
    public static final WeaponDef USP_S = add(WeaponDef.builder("usp_s", Category.PISTOL).price(200).damage(35, 50.5f).rangeMod(0.91f).rpm(352)
            .ammo(12, 24).reload(2.17f).deploy(0.9f).accuracy(2f, 5.1f, 3.6f, 18f, 280f, 50f).alt(2.6f, 6.8f, 4.9f, 20f, 57f)
            .recovery(0.33f, 0.28f).pattern(RecoilPattern.kick(12, 0.7f, 0.1f)).punch(1.0f).silencer().build());
    public static final WeaponDef P2000 = add(WeaponDef.builder("p2000", Category.PISTOL).price(200).damage(35, 50.5f).rangeMod(0.91f).rpm(352)
            .ammo(13, 52).reload(2.27f).deploy(0.9f).accuracy(2f, 5.9f, 4.1f, 20f, 280f, 52f).recovery(0.35f, 0.3f)
            .pattern(RecoilPattern.kick(13, 0.72f, 0.12f)).punch(1.0f).build());
    public static final WeaponDef P250 = add(WeaponDef.builder("p250", Category.PISTOL).price(300).damage(38, 64).rangeMod(0.9f).rpm(400)
            .ammo(13, 26).reload(2.17f).deploy(0.9f).accuracy(2f, 6.8f, 4.5f, 20f, 280f, 52f).recovery(0.36f, 0.3f)
            .pattern(RecoilPattern.kick(13, 0.9f, 0.15f)).punch(1.1f).build());
    public static final WeaponDef FIVESEVEN = add(WeaponDef.builder("fiveseven", Category.PISTOL).price(500).damage(32, 91.15f).rangeMod(0.81f).rpm(400)
            .ammo(20, 100).reload(2.17f).deploy(0.9f).accuracy(2f, 6.3f, 4.2f, 18f, 280f, 50f).recovery(0.34f, 0.3f)
            .pattern(RecoilPattern.kick(20, 0.8f, 0.14f)).punch(1.0f).build());
    public static final WeaponDef TEC9 = add(WeaponDef.builder("tec9", Category.PISTOL).price(500).damage(33, 90.6f).rangeMod(0.831f).rpm(500)
            .ammo(18, 90).reload(2.5f).deploy(0.9f).accuracy(2f, 7.2f, 5.4f, 11f, 260f, 40f).recovery(0.32f, 0.28f)
            .pattern(RecoilPattern.kick(18, 0.7f, 0.2f)).punch(0.9f).build());
    public static final WeaponDef CZ75A = add(WeaponDef.builder("cz75a", Category.PISTOL).price(500).damage(31, 77.65f).rangeMod(0.85f).rpm(600)
            .mode(FireMode.AUTO).ammo(12, 12).reload(2.73f).deploy(1.8f).accuracy(2.5f, 7.5f, 5f, 20f, 260f, 25f).recovery(0.33f, 0.28f)
            .pattern(RecoilPattern.wave(12, 4.2f, 8, 0.5f, 0.9f, 6, 1, 0.05f)).punch(0.8f).mesh("cz75a").build());
    public static final WeaponDef ELITE = add(WeaponDef.builder("elite", Category.PISTOL).hold(Hold.DUAL).price(300).damage(38, 57.5f).rangeMod(0.79f)
            .rpm(500).ammo(30, 120).reload(3.77f).deploy(1.0f).accuracy(2.5f, 7.4f, 5.5f, 22f, 270f, 20f).recovery(0.33f, 0.28f)
            .pattern(RecoilPattern.kick(30, 0.45f, 0.18f)).punch(0.8f).build());
    public static final WeaponDef DEAGLE = add(WeaponDef.builder("deagle", Category.PISTOL).price(700).damage(53, 93.2f).rangeMod(0.81f).penetration(2f)
            .rpm(267).ammo(7, 35).reload(2.2f).deploy(1.0f).speed(230).accuracy(2f, 7.8f, 5.4f, 160f, 350f, 55f).recovery(0.8f, 0.68f)
            .pattern(RecoilPattern.kick(7, 2.6f, 0.35f)).punch(2.4f).tagging(0.55f).build());
    public static final WeaponDef REVOLVER = add(WeaponDef.builder("revolver", Category.PISTOL).reloadStyle(ReloadStyle.REVOLVER).price(600)
            .damage(86, 93.2f).rangeMod(0.94f).penetration(2f).cycle(0.5f).altMode(FireMode.SEMI).altCycle(0.4f).ammo(8, 8).reload(2.3f)
            .deploy(1.0f).speed(220).accuracy(2f, 5f, 3.5f, 150f, 350f, 80f).alt(12f, 60f, 55f, 190f, 110f).recovery(0.8f, 0.7f)
            .pattern(RecoilPattern.kick(8, 2.2f, 0.4f)).punch(2.2f).tagging(0.55f).build());

    // ------------------------------------------------------------------------------------------------ SMGs
    public static final WeaponDef MAC10 = add(WeaponDef.builder("mac10", Category.SMG).price(1050, 600).damage(29, 57.5f).rangeMod(0.8f).rpm(800)
            .ammo(30, 100).reload(3.13f).accuracy(1f, 13f, 10f, 30f, 70f, 6.5f).recovery(0.35f, 0.3f)
            .pattern(RecoilPattern.wave(30, 4.8f, 11, 0.9f, 1.8f, 12, 1, 0.08f)).punch(0.45f).tagging(0.4f).build());
    public static final WeaponDef MP9 = add(WeaponDef.builder("mp9", Category.SMG).price(1250, 600).damage(26, 60).rangeMod(0.87f).rpm(857)
            .ammo(30, 120).reload(2.13f).accuracy(1f, 11f, 8.5f, 28f, 70f, 6f).recovery(0.33f, 0.28f)
            .pattern(RecoilPattern.wave(30, 4.5f, 10, 0.8f, 1.6f, 11, -1, 0.08f)).punch(0.4f).tagging(0.4f).build());
    public static final WeaponDef MP7 = add(WeaponDef.builder("mp7", Category.SMG).price(1500, 600).damage(29, 62.5f).rangeMod(0.85f).rpm(750)
            .ammo(30, 120).reload(3.13f).speed(220).accuracy(1f, 8.8f, 6.6f, 40f, 70f, 5.5f).recovery(0.33f, 0.28f)
            .pattern(RecoilPattern.wave(30, 4.0f, 10, 0.7f, 1.4f, 12, 1, 0.07f)).punch(0.4f).tagging(0.4f).build());
    public static final WeaponDef MP5SD = add(WeaponDef.builder("mp5sd", Category.SMG).price(1500, 600).damage(27, 62.5f).rangeMod(0.85f).rpm(750)
            .ammo(30, 120).reload(2.97f).speed(235).accuracy(1f, 8.5f, 6.4f, 32f, 70f, 5.5f).recovery(0.33f, 0.28f)
            .pattern(RecoilPattern.wave(30, 3.6f, 10, 0.6f, 1.3f, 12, -1, 0.07f)).punch(0.35f).suppressed().tagging(0.4f).build());
    public static final WeaponDef UMP45 = add(WeaponDef.builder("ump45", Category.SMG).price(1200, 600).damage(35, 65).rangeMod(0.75f).rpm(666)
            .ammo(25, 100).reload(3.5f).speed(230).accuracy(1f, 10f, 7.5f, 38f, 70f, 7.5f).recovery(0.36f, 0.3f)
            .pattern(RecoilPattern.wave(25, 5.0f, 9, 0.8f, 2.2f, 10, -1, 0.08f)).punch(0.5f).tagging(0.4f).build());
    public static final WeaponDef P90 = add(WeaponDef.builder("p90", Category.SMG).price(2350, 300).damage(26, 69).rangeMod(0.86f).rpm(857)
            .ammo(50, 100).reload(3.37f).speed(230).accuracy(1f, 11f, 8.3f, 35f, 70f, 4.5f).recovery(0.36f, 0.3f)
            .pattern(RecoilPattern.wave(50, 4.4f, 12, 1.2f, 1.8f, 16, 1, 0.07f)).punch(0.35f).tagging(0.4f).build());
    public static final WeaponDef BIZON = add(WeaponDef.builder("bizon", Category.SMG).price(1400, 600).damage(27, 57.5f).rangeMod(0.8f).rpm(750)
            .ammo(64, 120).reload(2.43f).accuracy(1f, 12f, 9f, 34f, 70f, 5f).recovery(0.36f, 0.3f)
            .pattern(RecoilPattern.wave(64, 4.0f, 12, 1.4f, 1.6f, 18, -1, 0.07f)).punch(0.35f).tagging(0.4f).build());

    // ------------------------------------------------------------------------------------------------ rifles
    public static final WeaponDef GALILAR = add(WeaponDef.builder("galilar", Category.RIFLE).price(1800).damage(30, 77.5f).rangeMod(0.98f).rpm(666)
            .ammo(35, 90).reload(3.0f).speed(215).accuracy(0.6f, 6.9f, 5.2f, 150f, 140f, 7.2f).recovery(0.4f, 0.3f)
            .pattern(RecoilPattern.wave(35, 6.0f, 10, 1.2f, 2.3f, 12, -1, 0.1f)).punch(0.55f).build());
    public static final WeaponDef FAMAS = add(WeaponDef.builder("famas", Category.RIFLE).price(2050).damage(30, 70).rangeMod(0.96f).rpm(666)
            .altMode(FireMode.BURST).altCycle(0.55f).ammo(25, 90).reload(3.3f).speed(220).accuracy(0.6f, 6.2f, 4.9f, 140f, 140f, 6.5f)
            .alt(0.6f, 5.0f, 4.0f, 140f, 12f).recovery(0.38f, 0.3f).pattern(RecoilPattern.wave(25, 5.0f, 9, 0.9f, 1.8f, 11, 1, 0.1f))
            .punch(0.5f).build());
    public static final WeaponDef AK47 = add(WeaponDef.builder("ak47", Category.RIFLE).price(2700).damage(36, 77.5f).rangeMod(0.98f).rpm(600)
            .ammo(30, 90).reload(2.43f).speed(215).accuracy(0.6f, 6.41f, 4.81f, 175f, 140f, 7.8f).recovery(0.42f, 0.3f)
            .pattern(AK_PATTERN).punch(0.65f).build());
    public static final WeaponDef M4A4 = add(WeaponDef.builder("m4a4", Category.RIFLE).price(3100).damage(33, 70).rangeMod(0.97f).rpm(666)
            .ammo(30, 90).reload(3.07f).speed(225).accuracy(0.6f, 4.9f, 3.68f, 140f, 140f, 7f).recovery(0.4f, 0.3f)
            .pattern(M4A4_PATTERN).punch(0.55f).build());
    public static final WeaponDef M4A1_S = add(WeaponDef.builder("m4a1_s", Category.RIFLE).price(2900).damage(38, 70).rangeMod(0.99f).rpm(600)
            .ammo(20, 80).reload(3.07f).speed(225).accuracy(0.6f, 4.9f, 3.68f, 140f, 140f, 7f).alt(0.6f, 6.2f, 4.6f, 140f, 9f)
            .recovery(0.4f, 0.3f).pattern(M4A1S_PATTERN).punch(0.5f).silencer().mesh("m4a1_s").build());
    public static final WeaponDef SG556 = add(WeaponDef.builder("sg556", Category.RIFLE).price(3000).damage(30, 100).rangeMod(0.98f).rpm(545)
            .ammo(30, 90).reload(2.8f).speed(210, 150).accuracy(0.6f, 6.5f, 4.9f, 150f, 140f, 7.5f).alt(0.3f, 3.0f, 2.0f, 150f, 5.5f)
            .recovery(0.4f, 0.3f).pattern(RecoilPattern.wave(30, 5.5f, 10, 1.0f, 1.9f, 12, -1, 0.1f)).punch(0.55f).zoom(45f).build());
    public static final WeaponDef AUG = add(WeaponDef.builder("aug", Category.RIFLE).price(3300).damage(28, 90).rangeMod(0.98f).rpm(600)
            .ammo(30, 90).reload(3.8f).speed(220, 150).accuracy(0.6f, 5.5f, 4.1f, 140f, 140f, 6.5f).alt(0.3f, 2.5f, 1.8f, 140f, 5f)
            .recovery(0.4f, 0.3f).pattern(RecoilPattern.wave(30, 5.2f, 10, 1.0f, 1.7f, 12, 1, 0.1f)).punch(0.5f).zoom(45f).build());

    // ------------------------------------------------------------------------------------------------ snipers
    public static final WeaponDef SSG08 = add(WeaponDef.builder("ssg08", Category.SNIPER).price(1700).damage(88, 85).rangeMod(0.99f).cycle(1.25f)
            .ammo(10, 90).reload(3.7f).speed(230).accuracy(0.3f, 23f, 17f, 110f, 200f, 30f).apex(3.5f).alt(0.3f, 2.3f, 1.6f, 90f, 30f)
            .recovery(0.25f, 0.2f).pattern(RecoilPattern.kick(10, 2.0f, 0.2f)).punch(2.5f).zoom(40f, 15f).bolt().tagging(0.5f).build());
    public static final WeaponDef AWP = add(WeaponDef.builder("awp", Category.SNIPER).price(4750, 100).damage(115, 97.5f).rangeMod(0.99f)
            .cycle(1.463f).ammo(5, 30).reload(3.67f).speed(200, 100).accuracy(0.2f, 80f, 60f, 176f, 300f, 100f).apex(80f)
            .alt(0.2f, 1.5f, 1.0f, 176f, 100f).recovery(0.25f, 0.2f).pattern(RecoilPattern.kick(5, 3.0f, 0.2f)).punch(3.5f)
            .zoom(40f, 10f).bolt().tagging(0.6f).build());
    public static final WeaponDef G3SG1 = add(WeaponDef.builder("g3sg1", Category.SNIPER).price(5000).damage(80, 82.5f).rangeMod(0.98f).cycle(0.25f)
            .ammo(20, 90).reload(4.7f).speed(215, 120).accuracy(0.3f, 26f, 20f, 150f, 200f, 25f).alt(0.3f, 2.2f, 1.6f, 150f, 18f)
            .recovery(0.3f, 0.25f).pattern(RecoilPattern.kick(20, 1.3f, 0.25f)).punch(1.6f).zoom(40f, 15f).build());
    public static final WeaponDef SCAR20 = add(WeaponDef.builder("scar20", Category.SNIPER).price(5000).damage(80, 82.5f).rangeMod(0.98f).cycle(0.25f)
            .ammo(20, 90).reload(3.1f).speed(215, 120).accuracy(0.3f, 26f, 20f, 150f, 200f, 25f).alt(0.3f, 2.2f, 1.6f, 150f, 18f)
            .recovery(0.3f, 0.25f).pattern(RecoilPattern.kick(20, 1.3f, 0.25f)).punch(1.6f).zoom(40f, 15f).build());

    // ------------------------------------------------------------------------------------------------ heavy
    public static final WeaponDef NOVA = add(WeaponDef.builder("nova", Category.SHOTGUN).price(1050).damage(26, 50).rangeMod(0.7f).pellets(9)
            .cycle(0.88f).ammo(8, 32).reload(0.5f).speed(220).accuracy(40f, 9f, 7f, 35f, 80f, 25f).recovery(0.4f, 0.35f)
            .pattern(RecoilPattern.kick(8, 2.6f, 0.4f)).punch(2.5f).build());
    public static final WeaponDef XM1014 = add(WeaponDef.builder("xm1014", Category.SHOTGUN).price(2000).damage(20, 80).rangeMod(0.7f).pellets(6)
            .cycle(0.35f).ammo(7, 32).reload(0.45f).speed(215).accuracy(38f, 10f, 8f, 32f, 80f, 20f).recovery(0.4f, 0.35f)
            .pattern(RecoilPattern.kick(7, 1.9f, 0.5f)).punch(1.8f).build());
    public static final WeaponDef SAWEDOFF = add(WeaponDef.builder("sawedoff", Category.SHOTGUN).price(1100).damage(32, 75).rangeMod(0.45f).range(1400)
            .pellets(8).cycle(0.85f).ammo(7, 32).reload(0.52f).speed(210).accuracy(60f, 12f, 9f, 30f, 80f, 30f).recovery(0.4f, 0.35f)
            .pattern(RecoilPattern.kick(7, 3.0f, 0.4f)).punch(3f).build());
    public static final WeaponDef MAG7 = add(WeaponDef.builder("mag7", Category.SHOTGUN).reloadStyle(ReloadStyle.RIFLE).price(1300).damage(30, 75)
            .rangeMod(0.45f).range(1400).pellets(8).cycle(0.85f).ammo(5, 32).reload(2.5f).speed(225).accuracy(40f, 10f, 8f, 30f, 80f, 25f)
            .recovery(0.4f, 0.35f).pattern(RecoilPattern.kick(5, 3.4f, 0.4f)).punch(3f).build());
    public static final WeaponDef M249 = add(WeaponDef.builder("m249", Category.MACHINEGUN).price(5200).damage(32, 80).rangeMod(0.97f).rpm(750)
            .ammo(100, 200).reload(5.7f).speed(195).accuracy(2f, 8f, 6f, 160f, 140f, 4.6f).recovery(0.5f, 0.4f)
            .pattern(RecoilPattern.wave(100, 5.2f, 14, 2.5f, 2.4f, 18, 1, 0.12f)).punch(0.55f).build());
    public static final WeaponDef NEGEV = add(WeaponDef.builder("negev", Category.MACHINEGUN).price(1700).damage(35, 71).rangeMod(0.97f).rpm(800)
            .ammo(150, 300).reload(5.7f).speed(150).accuracy(2f, 9f, 7f, 170f, 140f, 6f).recovery(0.5f, 0.4f)
            .pattern(RecoilPattern.wave(150, 6.0f, 9, -3.0f, 1.2f, 20, -1, 0.1f)).punch(0.5f).build());

    // ------------------------------------------------------------------------------------------------ melee, taser
    public static final WeaponDef KNIFE = add(WeaponDef.builder("knife", Category.KNIFE).damage(40, 85).cycle(0.4f).altCycle(1.0f).range(48)
            .ammo(0, 0).deploy(1.0f).accuracy(0, 0, 0, 0, 0, 0).build());
    public static final WeaponDef KARAMBIT = add(WeaponDef.builder("knife_karambit", Category.KNIFE).damage(40, 85).cycle(0.4f).altCycle(1.0f)
            .range(48).ammo(0, 0).deploy(1.0f).accuracy(0, 0, 0, 0, 0, 0).mesh("knife_karambit").build());
    public static final WeaponDef TASER = add(WeaponDef.builder("taser", Category.TASER).price(200, 0).damage(500, 92).range(190).cycle(0.15f)
            .ammo(1, 0).reload(30f).deploy(1.0f).accuracy(0.4f, 2f, 2f, 20f, 70f, 0f).tracer(1).build());

    // ------------------------------------------------------------------------------------------------ grenades & gear (for price/speed/hold)
    public static final WeaponDef HEGRENADE = add(WeaponDef.builder("hegrenade", Category.GRENADE).price(300).damage(98, 57.5f).build());
    public static final WeaponDef FLASHBANG = add(WeaponDef.builder("flashbang", Category.GRENADE).price(200).build());
    public static final WeaponDef SMOKEGRENADE = add(WeaponDef.builder("smokegrenade", Category.GRENADE).price(300).build());
    public static final WeaponDef MOLOTOV = add(WeaponDef.builder("molotov", Category.GRENADE).price(400).build());
    public static final WeaponDef INCGRENADE = add(WeaponDef.builder("incgrenade", Category.GRENADE).price(600).build());
    public static final WeaponDef DECOY = add(WeaponDef.builder("decoy", Category.GRENADE).price(50).build());
    public static final WeaponDef C4 = add(WeaponDef.builder("c4", Category.C4).build());
    public static final WeaponDef KEVLAR = add(WeaponDef.builder("kevlar", Category.EQUIPMENT).price(650).build());
    public static final WeaponDef ASSAULTSUIT = add(WeaponDef.builder("assaultsuit", Category.EQUIPMENT).price(1000).build());
    public static final WeaponDef DEFUSER = add(WeaponDef.builder("defuser", Category.EQUIPMENT).price(400).build());

    private static WeaponDef add(WeaponDef d) {
        d.index = ALL.size();
        ALL.add(d);
        BY_ID.put(d.id, d);
        return d;
    }

    public static List<WeaponDef> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static WeaponDef byIndex(int i) {
        return i >= 0 && i < ALL.size() ? ALL.get(i) : null;
    }

    public static WeaponDef byId(String id) {
        return BY_ID.get(id);
    }

    public static void init() {
        // class loading hook
    }

    private Weapons() {
    }
}
