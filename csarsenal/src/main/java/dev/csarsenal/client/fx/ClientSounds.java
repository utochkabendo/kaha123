package dev.csarsenal.client.fx;

import dev.csarsenal.registry.ModSounds;
import dev.csarsenal.weapon.WeaponDef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

public final class ClientSounds {
    private static final RandomSource RNG = RandomSource.create();
    private record Pending(String name, float vol, float pitch, double at) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    /** sound attached to the local player (weapon handling) */
    public static void self(String name, float vol, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.get(name), SoundSource.PLAYERS, vol, pitch * (0.97f + RNG.nextFloat() * 0.06f),
                RNG, mc.player.getX(), mc.player.getEyeY(), mc.player.getZ()));
    }

    public static void selfDelayed(String name, float vol, float pitch, double delay) {
        PENDING.add(new Pending(name, vol, pitch, System.nanoTime() / 1e9 + delay));
    }

    public static void tick() {
        double now = System.nanoTime() / 1e9;
        for (int i = PENDING.size() - 1; i >= 0; i--) {
            Pending p = PENDING.get(i);
            if (now >= p.at) {
                PENDING.remove(i);
                self(p.name, p.vol, p.pitch);
            }
        }
    }

    public static void at(String name, double x, double y, double z, float vol, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.level.playLocalSound(x, y, z, ModSounds.get(name), SoundSource.PLAYERS, vol, pitch * (0.96f + RNG.nextFloat() * 0.08f), false);
    }

    /** gun shot: distant variant beyond ~45 blocks, suppressed variant for silenced guns */
    public static void fire(WeaponDef d, boolean silenced, double x, double y, double z, boolean local) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || d == null) return;
        if (d.category == WeaponDef.Category.TASER) {
            at("weapon.taser_fire", x, y, z, 1f, 1f);
            return;
        }
        String base = "weapon." + d.sound;
        String ev;
        double dist = mc.player != null ? mc.player.getEyePosition().distanceTo(new net.minecraft.world.phys.Vec3(x, y, z)) : 0;
        if (silenced && ModSounds.exists(base + ".fire_sil")) ev = base + ".fire_sil";
        else if (!local && dist > 45 && ModSounds.exists(base + ".far")) ev = base + ".far";
        else ev = base + ".fire";
        SoundInstance s = new SimpleSoundInstance(ModSounds.get(ev), SoundSource.PLAYERS, 1.0f, 0.98f + RNG.nextFloat() * 0.04f, RNG, x, y, z);
        mc.getSoundManager().play(s);
    }

    private ClientSounds() {
    }
}
