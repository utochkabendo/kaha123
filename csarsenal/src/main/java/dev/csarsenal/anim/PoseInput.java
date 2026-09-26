package dev.csarsenal.anim;

import dev.csarsenal.weapon.WeaponDef;

/** Everything the procedural skeleton needs for one pose. Angles in degrees. */
public final class PoseInput {
    /** head yaw minus body yaw, positive = looking to the right */
    public float yawRel;
    /** view pitch, positive = looking down */
    public float pitch;
    /** 0 = standing, 1 = fully crouched */
    public float crouch;
    /** walk cycle position (Minecraft limb swing) and amount 0..1 */
    public float walkPos, walkAmount;
    /** movement direction relative to the body, degrees, 0 = forward, 90 = strafing right */
    public float moveAngle;
    /** 0 = on ground, 1 = airborne */
    public float air;
    /** null = unarmed */
    public WeaponDef.Hold hold;
    public WeaponGeometry geo;
    /** reload progress 0..1 or -1 */
    public float reload = -1;
    /** fire kick 0..1 (1 = just fired) */
    public float fire;
    /** grenade throw progress 0..1 or -1 */
    public float throwAnim = -1;
    /** weapon draw progress 0..1 (1 = ready) */
    public float deploy = 1;
    /** knife attack progress 0..1 or -1 */
    public float melee = -1;
    /** heavy (stab) knife attack */
    public boolean meleeHeavy;
    public boolean scoped;

    public PoseInput reset() {
        yawRel = pitch = crouch = walkPos = walkAmount = moveAngle = air = fire = 0;
        hold = null;
        geo = null;
        reload = -1;
        throwAnim = -1;
        deploy = 1;
        melee = -1;
        meleeHeavy = false;
        scoped = false;
        return this;
    }
}
