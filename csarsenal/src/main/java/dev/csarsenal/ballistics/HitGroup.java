package dev.csarsenal.ballistics;

/** CS2 hit groups with their damage multipliers. */
public enum HitGroup {
    GENERIC(1.0f, true),
    HEAD(4.0f, false),
    CHEST(1.0f, true),
    STOMACH(1.25f, true),
    LEFT_ARM(1.0f, true),
    RIGHT_ARM(1.0f, true),
    LEFT_LEG(0.75f, false),
    RIGHT_LEG(0.75f, false);

    public final float multiplier;
    /** covered by kevlar (the head is covered by the helmet) */
    public final boolean kevlar;

    HitGroup(float m, boolean kevlar) {
        this.multiplier = m;
        this.kevlar = kevlar;
    }

    public static HitGroup byOrdinal(int i) {
        HitGroup[] v = values();
        return i >= 0 && i < v.length ? v[i] : GENERIC;
    }
}
