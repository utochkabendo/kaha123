package dev.csarsenal.anim;

import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Attachment points of a weapon model (model space, metres: +X right, +Y up, -Z forward).
 * Filled on the client from assets/csarsenal/meshes/&lt;id&gt;.json; the server never needs it.
 */
public final class WeaponGeometry {
    private static final Map<String, WeaponGeometry> REGISTRY = new HashMap<>();

    public enum Support { UNDER, VERTICAL, STRAP, MAG, PISTOL, NONE }

    public final Vector3f grip = new Vector3f();
    public final Vector3f support = new Vector3f(0, 0, -0.25f);
    public final Vector3f muzzle = new Vector3f(0, 0.04f, -0.5f);
    public final Vector3f muzzleSilenced = new Vector3f(0, 0.04f, -0.7f);
    public final Vector3f eject = new Vector3f(0.015f, 0.04f, -0.1f);
    public final Vector3f mag = new Vector3f(0, 0, -0.1f);
    public final Vector3f bolt = new Vector3f(0.02f, 0.04f, -0.15f);
    public final Vector3f silencer = new Vector3f(0, 0.04f, -0.5f);
    public float gripAngle = 20f;
    public Support supportKind = Support.UNDER;
    public boolean pistol, dual, magTop, shellReload, revolver, knife, grenade, mg, hasSilencerPoint, hasMuzzleSil;

    public static WeaponGeometry get(String id) {
        WeaponGeometry g = REGISTRY.get(id);
        if (g == null) {
            g = new WeaponGeometry();
            REGISTRY.put(id, g);
        }
        return g;
    }

    public static void put(String id, WeaponGeometry g) {
        REGISTRY.put(id, g);
    }

    /** Up direction along the pistol grip (towards the receiver). */
    public Vector3f gripUp(Vector3f out) {
        double a = Math.toRadians(gripAngle);
        return out.set(0, (float) Math.cos(a), (float) -Math.sin(a));
    }
}
