package dev.csarsenal.client.fx;

import dev.csarsenal.entity.InfernoEntity;
import dev.csarsenal.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** looping fire crackle that follows an inferno */
public final class InfernoSound extends AbstractTickableSoundInstance {
    private final InfernoEntity e;

    public InfernoSound(InfernoEntity e) {
        super(ModSounds.get("grenade.inferno_loop"), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.e = e;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0f;
        this.x = e.getX();
        this.y = e.getY();
        this.z = e.getZ();
    }

    @Override
    public void tick() {
        if (e.isRemoved()) {
            stop();
            return;
        }
        x = e.getX();
        y = e.getY();
        z = e.getZ();
        float left = (InfernoEntity.LIFETIME - e.age) / 20f;
        volume = Math.min(1f, Math.max(0f, left));
    }
}
