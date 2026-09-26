package dev.csarsenal;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config: crosshair, viewmodel, visuals. */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue REPLACE_PLAYER_MODEL;
    public static final ModConfigSpec.BooleanValue DYNAMIC_CROSSHAIR;
    public static final ModConfigSpec.IntValue CROSSHAIR_COLOR;
    public static final ModConfigSpec.DoubleValue CROSSHAIR_SIZE;
    public static final ModConfigSpec.DoubleValue CROSSHAIR_GAP;
    public static final ModConfigSpec.DoubleValue CROSSHAIR_THICKNESS;
    public static final ModConfigSpec.BooleanValue CROSSHAIR_DOT;
    public static final ModConfigSpec.BooleanValue CROSSHAIR_OUTLINE;
    public static final ModConfigSpec.BooleanValue CROSSHAIR_FOLLOW_RECOIL;
    public static final ModConfigSpec.DoubleValue VIEWMODEL_FOV;
    public static final ModConfigSpec.DoubleValue VIEWMODEL_X;
    public static final ModConfigSpec.DoubleValue VIEWMODEL_Y;
    public static final ModConfigSpec.DoubleValue VIEWMODEL_Z;
    public static final ModConfigSpec.BooleanValue LEFT_HANDED;
    public static final ModConfigSpec.DoubleValue ZOOM_SENSITIVITY;
    public static final ModConfigSpec.BooleanValue HIDE_VANILLA_BARS;
    public static final ModConfigSpec.IntValue MAX_DECALS;
    public static final ModConfigSpec.BooleanValue SHELLS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("visuals");
        REPLACE_PLAYER_MODEL = b.comment("Render players with the CS operator model (the hitboxes always follow this skeleton)").define("replacePlayerModel", true);
        HIDE_VANILLA_BARS = b.comment("Hide vanilla hearts/armor bar while holding a CS item (CS HUD shows HP/armor)").define("hideVanillaBars", true);
        MAX_DECALS = b.defineInRange("maxBulletHoles", 256, 0, 4096);
        SHELLS = b.comment("Eject shell casings").define("shellCasings", true);
        b.pop();
        b.push("crosshair");
        DYNAMIC_CROSSHAIR = b.comment("Crosshair gap follows the current inaccuracy (cl_crosshairstyle 2/3)").define("dynamic", true);
        CROSSHAIR_COLOR = b.comment("ARGB colour").defineInRange("color", 0xFF32FF32, Integer.MIN_VALUE, Integer.MAX_VALUE);
        CROSSHAIR_SIZE = b.defineInRange("size", 4.0, 0.0, 30.0);
        CROSSHAIR_GAP = b.defineInRange("gap", 1.0, -5.0, 30.0);
        CROSSHAIR_THICKNESS = b.defineInRange("thickness", 1.0, 0.25, 6.0);
        CROSSHAIR_DOT = b.define("dot", false);
        CROSSHAIR_OUTLINE = b.define("outline", true);
        CROSSHAIR_FOLLOW_RECOIL = b.comment("cl_crosshair_recoil: move the crosshair to where the spray goes").define("followRecoil", false);
        b.pop();
        b.push("viewmodel");
        VIEWMODEL_FOV = b.comment("viewmodel_fov").defineInRange("fov", 68.0, 54.0, 90.0);
        VIEWMODEL_X = b.comment("viewmodel_offset_x (CS units)").defineInRange("offsetX", 1.0, -2.5, 2.5);
        VIEWMODEL_Y = b.comment("viewmodel_offset_y (CS units)").defineInRange("offsetY", 1.0, -2.0, 2.0);
        VIEWMODEL_Z = b.comment("viewmodel_offset_z (CS units)").defineInRange("offsetZ", -1.0, -2.0, 2.0);
        LEFT_HANDED = b.comment("cl_righthand 0").define("leftHanded", false);
        ZOOM_SENSITIVITY = b.comment("zoom_sensitivity_ratio").defineInRange("zoomSensitivityRatio", 1.0, 0.1, 3.0);
        b.pop();
        SPEC = b.build();
    }

    public static boolean bool(ModConfigSpec.BooleanValue v, boolean def) {
        return SPEC.isLoaded() ? v.get() : def;
    }

    public static double num(ModConfigSpec.DoubleValue v, double def) {
        return SPEC.isLoaded() ? v.get() : def;
    }

    public static int integer(ModConfigSpec.IntValue v, int def) {
        return SPEC.isLoaded() ? v.get() : def;
    }

    private ClientConfig() {
    }
}
