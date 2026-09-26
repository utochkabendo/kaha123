package dev.csarsenal;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config (synced to clients). */
public final class Config {
    public enum MovementMode { HOLDING_CS_ITEM, ALWAYS, OFF }

    public enum BuyMode { ECONOMY, FREE, CREATIVE_ONLY, DISABLED }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue DAMAGE_SCALE;
    public static final ModConfigSpec.EnumValue<MovementMode> MOVEMENT;
    public static final ModConfigSpec.EnumValue<BuyMode> BUY;
    public static final ModConfigSpec.BooleanValue BREAK_GLASS;
    public static final ModConfigSpec.BooleanValue FRIENDLY_FIRE;
    public static final ModConfigSpec.BooleanValue INFINITE_RESERVE;
    public static final ModConfigSpec.BooleanValue CS_CROUCH_HITBOX;
    public static final ModConfigSpec.BooleanValue C4_BREAKS_BLOCKS;
    public static final ModConfigSpec.IntValue BOMB_TIMER;
    public static final ModConfigSpec.DoubleValue HIT_TOLERANCE;
    public static final ModConfigSpec.IntValue START_MONEY;
    public static final ModConfigSpec.IntValue MAX_MONEY;
    public static final ModConfigSpec.DoubleValue MOB_REWARD;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("gameplay");
        DAMAGE_SCALE = b.comment("CS damage -> Minecraft damage. 0.2 means 100 CS HP == 20 Minecraft HP (10 hearts), so every weapon needs the same number of hits to kill as in CS2.")
                .defineInRange("damageScale", 0.2, 0.01, 10.0);
        MOVEMENT = b.comment("When the CS2 movement physics (speeds, acceleration, friction, air strafing, shift-walk, ctrl-crouch) is used.")
                .defineEnum("movement", MovementMode.HOLDING_CS_ITEM);
        BUY = b.comment("Buy menu (B): ECONOMY (CS money: start money, kill rewards; creative players buy for free), FREE for everybody, CREATIVE_ONLY or DISABLED")
                .defineEnum("buyMode", BuyMode.ECONOMY);
        START_MONEY = b.comment("Money of a player joining for the first time").defineInRange("startMoney", 800, 0, 1000000);
        MAX_MONEY = b.comment("Money cap").defineInRange("maxMoney", 16000, 0, 1000000);
        MOB_REWARD = b.comment("Kill reward for hostile mobs as a share of the CS kill reward of the weapon (players always give the full reward)")
                .defineInRange("mobKillReward", 0.5, 0.0, 10.0);
        BREAK_GLASS = b.comment("Bullets shatter glass blocks and panes").define("bulletsBreakGlass", true);
        FRIENDLY_FIRE = b.comment("Damage players of the same CS team").define("friendlyFire", true);
        INFINITE_RESERVE = b.comment("Reserve ammo never runs out").define("infiniteReserve", false);
        CS_CROUCH_HITBOX = b.comment("Use CS crouch height (1.35 blocks) instead of Minecraft's 1.5 while in CS mode").define("csCrouchHeight", false);
        C4_BREAKS_BLOCKS = b.comment("The C4 explosion destroys blocks").define("c4BreaksBlocks", false);
        BOMB_TIMER = b.comment("C4 timer in seconds").defineInRange("bombTimer", 40, 10, 300);
        HIT_TOLERANCE = b.comment("Extra distance (blocks) accepted when validating client reported hits (lag compensation)").defineInRange("hitTolerance", 1.0, 0.0, 8.0);
        b.pop();
        SPEC = b.build();
    }

    public static float damageScale() {
        return SPEC.isLoaded() ? DAMAGE_SCALE.get().floatValue() : 0.2f;
    }

    public static MovementMode movement() {
        return SPEC.isLoaded() ? MOVEMENT.get() : MovementMode.HOLDING_CS_ITEM;
    }

    public static BuyMode buyMode() {
        return SPEC.isLoaded() ? BUY.get() : BuyMode.ECONOMY;
    }

    public static boolean economy() {
        return buyMode() == BuyMode.ECONOMY;
    }

    public static int startMoney() {
        return SPEC.isLoaded() ? START_MONEY.get() : 800;
    }

    public static int maxMoney() {
        return SPEC.isLoaded() ? MAX_MONEY.get() : 16000;
    }

    public static double mobRewardScale() {
        return SPEC.isLoaded() ? MOB_REWARD.get() : 0.5;
    }

    public static boolean get(ModConfigSpec.BooleanValue v, boolean def) {
        return SPEC.isLoaded() ? v.get() : def;
    }

    private Config() {
    }
}
