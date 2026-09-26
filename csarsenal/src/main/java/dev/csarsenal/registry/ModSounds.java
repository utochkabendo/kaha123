package dev.csarsenal.registry;

import dev.csarsenal.CsArsenal;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.Map;

/** All sound events (generated from assets/csarsenal/sounds.json by tools/build_assets.py). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> REGISTER = DeferredRegister.create(Registries.SOUND_EVENT, CsArsenal.MODID);
    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> BY_NAME = new HashMap<>();

    static final String[] NAMES = {
            "bullet.shell",
            "bullet.shell_shotgun",
            "bullet.whiz",
            "c4.armed",
            "c4.beep",
            "c4.defuse",
            "c4.defused",
            "c4.explode",
            "c4.plant",
            "c4.press",
            "grenade.bounce",
            "grenade.flash_explode",
            "grenade.flash_ring",
            "grenade.he_explode",
            "grenade.inferno_extinguish",
            "grenade.inferno_loop",
            "grenade.inferno_start",
            "grenade.molotov_shatter",
            "grenade.pin",
            "grenade.smoke_emit",
            "grenade.throw",
            "impact.concrete",
            "impact.dirt",
            "impact.flesh",
            "impact.glass",
            "impact.headshot",
            "impact.helmet",
            "impact.kevlar",
            "impact.metal",
            "impact.wood",
            "knife.deploy",
            "knife.hit",
            "knife.hitwall",
            "knife.slash",
            "knife.stab",
            "player.death",
            "reload.mg_box",
            "reload.mg_cover",
            "reload.pistol_magin",
            "reload.pistol_magout",
            "reload.pistol_slide",
            "reload.pump",
            "reload.revolver_close",
            "reload.revolver_open",
            "reload.rifle_boltpull",
            "reload.rifle_boltrelease",
            "reload.rifle_magin",
            "reload.rifle_magout",
            "reload.shell_insert",
            "reload.sniper_boltback",
            "reload.sniper_boltforward",
            "ui.buy",
            "weapon.ak47.far",
            "weapon.ak47.fire",
            "weapon.aug.far",
            "weapon.aug.fire",
            "weapon.awp.far",
            "weapon.awp.fire",
            "weapon.bizon.far",
            "weapon.bizon.fire",
            "weapon.cz75a.far",
            "weapon.cz75a.fire",
            "weapon.deagle.far",
            "weapon.deagle.fire",
            "weapon.draw",
            "weapon.dryfire",
            "weapon.elite.far",
            "weapon.elite.fire",
            "weapon.famas.far",
            "weapon.famas.fire",
            "weapon.fiveseven.far",
            "weapon.fiveseven.fire",
            "weapon.g3sg1.far",
            "weapon.g3sg1.fire",
            "weapon.galilar.far",
            "weapon.galilar.fire",
            "weapon.glock.far",
            "weapon.glock.fire",
            "weapon.inspect",
            "weapon.m249.far",
            "weapon.m249.fire",
            "weapon.m4a1_s.far",
            "weapon.m4a1_s.fire",
            "weapon.m4a1_s.fire_sil",
            "weapon.m4a4.far",
            "weapon.m4a4.fire",
            "weapon.mac10.far",
            "weapon.mac10.fire",
            "weapon.mag7.far",
            "weapon.mag7.fire",
            "weapon.mp5sd.fire_sil",
            "weapon.mp7.far",
            "weapon.mp7.fire",
            "weapon.mp9.far",
            "weapon.mp9.fire",
            "weapon.negev.far",
            "weapon.negev.fire",
            "weapon.nova.far",
            "weapon.nova.fire",
            "weapon.p2000.far",
            "weapon.p2000.fire",
            "weapon.p250.far",
            "weapon.p250.fire",
            "weapon.p90.far",
            "weapon.p90.fire",
            "weapon.revolver.far",
            "weapon.revolver.fire",
            "weapon.sawedoff.far",
            "weapon.sawedoff.fire",
            "weapon.scar20.far",
            "weapon.scar20.fire",
            "weapon.sg556.far",
            "weapon.sg556.fire",
            "weapon.silencer_off",
            "weapon.silencer_on",
            "weapon.ssg08.far",
            "weapon.ssg08.fire",
            "weapon.switchmode",
            "weapon.taser_fire",
            "weapon.tec9.far",
            "weapon.tec9.fire",
            "weapon.ump45.far",
            "weapon.ump45.fire",
            "weapon.usp_s.far",
            "weapon.usp_s.fire",
            "weapon.usp_s.fire_sil",
            "weapon.xm1014.far",
            "weapon.xm1014.fire",
            "weapon.zoom"
    };

    static {
        for (String n : NAMES) {
            BY_NAME.put(n, REGISTER.register(n, () -> SoundEvent.createVariableRangeEvent(CsArsenal.id(n))));
        }
    }

    /** Registered sound by event name (e.g. "weapon.ak47.fire"); falls back to an unregistered direct event. */
    public static SoundEvent get(String name) {
        DeferredHolder<SoundEvent, SoundEvent> h = BY_NAME.get(name);
        if (h != null && h.isBound()) return h.get();
        return SoundEvent.createVariableRangeEvent(CsArsenal.id(name));
    }

    public static Holder<SoundEvent> holder(String name) {
        DeferredHolder<SoundEvent, SoundEvent> h = BY_NAME.get(name);
        if (h != null && h.isBound()) return h;
        return Holder.direct(get(name));
    }

    public static boolean exists(String name) {
        return BY_NAME.containsKey(name);
    }

    private ModSounds() {
    }
}
