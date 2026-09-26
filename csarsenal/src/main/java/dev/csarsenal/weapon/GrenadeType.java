package dev.csarsenal.weapon;

/** CS2 grenades. Times in seconds, distances in CS units. */
public enum GrenadeType {
    HE("hegrenade", 1.6f, false),
    FLASH("flashbang", 1.6f, false),
    SMOKE("smokegrenade", 0f, true),
    MOLOTOV("molotov", 2.0f, false),
    INCENDIARY("incgrenade", 2.0f, false),
    DECOY("decoy", 0f, true);

    public final String id;
    /** time from throw to detonation (0 = detonates when it comes to rest) */
    public final float fuse;
    public final boolean detonatesAtRest;

    GrenadeType(String id, float fuse, boolean rest) {
        this.id = id;
        this.fuse = fuse;
        this.detonatesAtRest = rest;
    }

    public boolean isFire() {
        return this == MOLOTOV || this == INCENDIARY;
    }

    public static GrenadeType byOrdinal(int i) {
        GrenadeType[] v = values();
        return i >= 0 && i < v.length ? v[i] : HE;
    }
}
