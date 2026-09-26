package dev.csarsenal.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** All custom particles. */
public final class CsParticles {
    public static SpriteSet smokeSprites, flameSprites, fireballSprites, impactSprites, sparkSprites, bloodSprites;

    /** base class: exact velocity, no random jitter */
    public abstract static class Base extends TextureSheetParticle {
        protected float startAlpha = 1f;
        protected boolean emissive;
        protected float growth;

        protected Base(ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            super(level, x, y, z);
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        protected int getLightColor(float pt) {
            return emissive ? 0xF000F0 : super.getLightColor(pt);
        }

        @Override
        public float getQuadSize(float pt) {
            return quadSize * (1f + growth * (age + pt) / Math.max(1, lifetime));
        }
    }

    // ------------------------------------------------------------------------------------------------ smoke grenade
    public static class Smoke extends Base {
        public final SmokeManager.Cloud cloud;
        public float targetAlpha;
        private final float rotSpeed;
        public final double homeX, homeY, homeZ;

        public Smoke(ClientLevel level, double x, double y, double z, SmokeManager.Cloud cloud, int life, float size) {
            super(level, x, y, z, 0, 0, 0);
            this.cloud = cloud;
            this.lifetime = life;
            this.quadSize = size;
            this.gravity = 0;
            this.hasPhysics = false;
            this.friction = 0.9f;
            float g = 0.62f + random.nextFloat() * 0.12f;
            this.rCol = g;
            this.gCol = g;
            this.bCol = g * 1.01f;
            this.alpha = 0;
            this.targetAlpha = 0.72f + random.nextFloat() * 0.2f;
            this.roll = random.nextFloat() * TWO_PI;
            this.oRoll = roll;
            this.rotSpeed = (random.nextFloat() - 0.5f) * 0.01f;
            this.homeX = x;
            this.homeY = y;
            this.homeZ = z;
            if (smokeSprites != null) pickSprite(smokeSprites);
        }

        @Override
        public void tick() {
            super.tick();
            oRoll = roll;
            roll += rotSpeed;
            float fadeIn = Mth.clamp(age / 12f, 0, 1);
            float fadeOut = Mth.clamp((lifetime - age) / 40f, 0, 1);
            float hole = cloud != null ? SmokeManager.alphaAt(x, y, z) : 1f;
            float a = targetAlpha * fadeIn * fadeOut * hole;
            alpha += (a - alpha) * 0.35f;
            // drift back home after being pushed by an HE
            xd += (homeX - x) * 0.01;
            yd += (homeY - y) * 0.01;
            zd += (homeZ - z) * 0.01;
            if (cloud != null && cloud.dead) lifetime = Math.min(lifetime, age + 40);
        }

        public void push(double px, double py, double pz, double strength) {
            xd += px * strength;
            yd += py * strength;
            zd += pz * strength;
        }
    }

    public static float TWO_PI = (float) (Math.PI * 2);

    // ------------------------------------------------------------------------------------------------ simple ones
    public static class Impact extends Base {
        Impact(ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            super(l, x, y, z, vx, vy, vz);
            lifetime = 12 + random.nextInt(10);
            quadSize = 0.06f + random.nextFloat() * 0.06f;
            growth = 2.5f;
            gravity = 0.01f;
            friction = 0.85f;
            float g = 0.55f + random.nextFloat() * 0.2f;
            rCol = g; gCol = g * 0.97f; bCol = g * 0.92f;
            alpha = 0.8f;
            if (impactSprites != null) pickSprite(impactSprites);
        }

        @Override
        public void tick() {
            super.tick();
            alpha = 0.8f * (1f - age / (float) lifetime);
        }
    }

    public static class Spark extends Base {
        Spark(ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            super(l, x, y, z, vx, vy, vz);
            lifetime = 5 + random.nextInt(6);
            quadSize = 0.02f + random.nextFloat() * 0.02f;
            gravity = 0.6f;
            friction = 0.92f;
            emissive = true;
            if (sparkSprites != null) pickSprite(sparkSprites);
        }
    }

    public static class Blood extends Base {
        Blood(ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            super(l, x, y, z, vx, vy, vz);
            lifetime = 14 + random.nextInt(12);
            quadSize = 0.04f + random.nextFloat() * 0.07f;
            gravity = 0.4f;
            friction = 0.9f;
            growth = 0.6f;
            if (bloodSprites != null) pickSprite(bloodSprites);
        }

        @Override
        public void tick() {
            super.tick();
            alpha = 1f - age / (float) lifetime;
        }
    }

    public static class Flame extends Base {
        private final SpriteSet sprites;

        Flame(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
            super(l, x, y, z, vx, vy, vz);
            sprites = s;
            lifetime = 10 + random.nextInt(10);
            quadSize = 0.25f + random.nextFloat() * 0.3f;
            gravity = -0.02f;
            friction = 0.9f;
            emissive = true;
            growth = -0.4f;
            if (s != null) setSpriteFromAge(s);
        }

        @Override
        public void tick() {
            super.tick();
            if (sprites != null) setSpriteFromAge(sprites);
            alpha = Mth.clamp(1.2f - age / (float) lifetime, 0, 1);
        }
    }

    public static class Fireball extends Base {
        private final SpriteSet sprites;

        Fireball(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
            super(l, x, y, z, vx, vy, vz);
            sprites = s;
            lifetime = 8 + random.nextInt(8);
            quadSize = 0.8f + random.nextFloat() * 0.8f;
            gravity = -0.01f;
            friction = 0.8f;
            emissive = true;
            growth = 1.2f;
            if (s != null) setSpriteFromAge(s);
        }

        @Override
        public void tick() {
            super.tick();
            if (sprites != null) setSpriteFromAge(sprites);
            alpha = Mth.clamp(1.3f - age / (float) lifetime, 0, 1);
        }
    }

    public static class MuzzleSmoke extends Base {
        MuzzleSmoke(ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            super(l, x, y, z, vx, vy, vz);
            lifetime = 20 + random.nextInt(20);
            quadSize = 0.05f + random.nextFloat() * 0.05f;
            growth = 4f;
            gravity = -0.004f;
            friction = 0.88f;
            float g = 0.8f;
            rCol = g; gCol = g; bCol = g;
            alpha = 0.25f;
            if (impactSprites != null) pickSprite(impactSprites);
        }

        @Override
        public void tick() {
            super.tick();
            alpha = 0.25f * (1f - age / (float) lifetime);
        }
    }

    // ------------------------------------------------------------------------------------------------ providers
    public record SmokeProvider(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        public SmokeProvider {
            smokeSprites = s;
        }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            Smoke p = new Smoke(l, x, y, z, null, 60 + (int) (Math.random() * 40), 0.5f + (float) Math.random() * 0.4f);
            p.xd = vx; p.yd = vy; p.zd = vz;
            p.targetAlpha = 0.45f;
            return p;
        }
    }

    public record ImpactProvider(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        public ImpactProvider {
            impactSprites = s;
        }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            return new Impact(l, x, y, z, vx, vy, vz);
        }
    }

    public record MuzzleSmokeProvider(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            MuzzleSmoke p = new MuzzleSmoke(l, x, y, z, vx, vy, vz);
            p.pickSprite(s);
            return p;
        }
    }

    public record SparkProvider(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        public SparkProvider {
            sparkSprites = s;
        }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            return new Spark(l, x, y, z, vx, vy, vz);
        }
    }

    public record BloodProvider(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        public BloodProvider {
            bloodSprites = s;
        }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            double sx = vx == 0 && vy == 0 && vz == 0 ? (Math.random() - 0.5) * 0.12 : vx;
            double sy = vx == 0 && vy == 0 && vz == 0 ? Math.random() * 0.1 : vy;
            double sz = vx == 0 && vy == 0 && vz == 0 ? (Math.random() - 0.5) * 0.12 : vz;
            return new Blood(l, x, y, z, sx, sy, sz);
        }
    }

    public record FlameProvider(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        public FlameProvider {
            flameSprites = s;
        }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            return new Flame(l, x, y, z, vx, vy, vz, s);
        }
    }

    public record FireballProvider(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        public FireballProvider {
            fireballSprites = s;
        }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            return new Fireball(l, x, y, z, vx, vy, vz, s);
        }
    }

    private CsParticles() {
    }
}
