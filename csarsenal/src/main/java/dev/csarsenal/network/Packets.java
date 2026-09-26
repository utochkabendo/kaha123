package dev.csarsenal.network;

import dev.csarsenal.CsArsenal;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** All custom payloads. */
public final class Packets {
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> ptype(String n) {
        return new CustomPacketPayload.Type<>(CsArsenal.id(n));
    }

    static void writeVec(FriendlyByteBuf b, Vec3 v) {
        b.writeDouble(v.x);
        b.writeDouble(v.y);
        b.writeDouble(v.z);
    }

    static Vec3 readVec(FriendlyByteBuf b) {
        return new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
    }

    static void writeDir(FriendlyByteBuf b, Vec3 v) {
        b.writeFloat((float) v.x);
        b.writeFloat((float) v.y);
        b.writeFloat((float) v.z);
    }

    static Vec3 readDir(FriendlyByteBuf b) {
        return new Vec3(b.readFloat(), b.readFloat(), b.readFloat());
    }

    // =============================================================================================== client -> server

    /** a hit the client saw on a pellet: entity, CS hit group, distance along the ray */
    public record Hit(int entity, int group, float distance) {
    }

    public record Pellet(Vec3 dir, List<Hit> hits) {
    }

    /**
     * One shot, sent at the exact frame the trigger fired (sub-tick): eye position and final bullet directions
     * (view angles + recoil + spread) as the shooter saw them.
     */
    public record Shoot(int slot, int weapon, int shotIndex, long seed, Vec3 origin, List<Pellet> pellets, boolean scoped) implements CustomPacketPayload {
        public static final Type<Shoot> TYPE = ptype("shoot");
        public static final StreamCodec<FriendlyByteBuf, Shoot> CODEC = StreamCodec.ofMember(Shoot::write, Shoot::new);

        Shoot(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readLong(), readVec(b), readPellets(b), b.readBoolean());
        }

        private static List<Pellet> readPellets(FriendlyByteBuf b) {
            int n = Math.min(b.readVarInt(), 16);
            List<Pellet> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                Vec3 d = readDir(b);
                int h = Math.min(b.readVarInt(), 8);
                List<Hit> hits = new ArrayList<>(h);
                for (int k = 0; k < h; k++) hits.add(new Hit(b.readVarInt(), b.readByte(), b.readFloat()));
                l.add(new Pellet(d, hits));
            }
            return l;
        }

        void write(FriendlyByteBuf b) {
            b.writeVarInt(slot);
            b.writeVarInt(weapon);
            b.writeVarInt(shotIndex);
            b.writeLong(seed);
            writeVec(b, origin);
            b.writeVarInt(pellets.size());
            for (Pellet p : pellets) {
                writeDir(b, p.dir);
                b.writeVarInt(p.hits.size());
                for (Hit h : p.hits) {
                    b.writeVarInt(h.entity);
                    b.writeByte(h.group);
                    b.writeFloat(h.distance);
                }
            }
            b.writeBoolean(scoped);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Reload(int slot) implements CustomPacketPayload {
        public static final Type<Reload> TYPE = ptype("reload");
        public static final StreamCodec<FriendlyByteBuf, Reload> CODEC = StreamCodec.ofMember((p, b) -> b.writeVarInt(p.slot), b -> new Reload(b.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** misc weapon actions */
    public record Action(int action, int slot, int arg) implements CustomPacketPayload {
        public static final int SILENCER = 0, MODE = 1, INSPECT = 2, DRAW = 3, SCOPE = 4, WALK = 5, DROP = 6;
        public static final Type<Action> TYPE = ptype("action");
        public static final StreamCodec<FriendlyByteBuf, Action> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeVarInt(p.action); b.writeVarInt(p.slot); b.writeVarInt(p.arg); },
                b -> new Action(b.readVarInt(), b.readVarInt(), b.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Throw(int slot, float strength, Vec3 origin, Vec3 dir, Vec3 playerVel) implements CustomPacketPayload {
        public static final Type<Throw> TYPE = ptype("throw");
        public static final StreamCodec<FriendlyByteBuf, Throw> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeVarInt(p.slot); b.writeFloat(p.strength); writeVec(b, p.origin); writeDir(b, p.dir); writeDir(b, p.playerVel); },
                b -> new Throw(b.readVarInt(), b.readFloat(), readVec(b), readDir(b), readDir(b)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** knife swing; entity = -1 on a miss */
    public record Knife(int slot, boolean heavy, int entity, int group, boolean backstab, Vec3 origin, Vec3 dir, boolean hitWall) implements CustomPacketPayload {
        public static final Type<Knife> TYPE = ptype("knife");
        public static final StreamCodec<FriendlyByteBuf, Knife> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeVarInt(p.slot); b.writeBoolean(p.heavy); b.writeVarInt(p.entity); b.writeByte(p.group); b.writeBoolean(p.backstab); writeVec(b, p.origin); writeDir(b, p.dir); b.writeBoolean(p.hitWall); },
                b -> new Knife(b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readByte(), b.readBoolean(), readVec(b), readDir(b), b.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Buy(String id) implements CustomPacketPayload {
        public static final Type<Buy> TYPE = ptype("buy");
        public static final StreamCodec<FriendlyByteBuf, Buy> CODEC = StreamCodec.ofMember((p, b) -> b.writeUtf(p.id, 64), b -> new Buy(b.readUtf(64)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // =============================================================================================== server -> client

    /** a shot fired by somebody else: play the sound, muzzle flash, tracer and impacts */
    public record ShotFx(int shooter, int weapon, Vec3 origin, List<Vec3> dirs, boolean silenced, int shotIndex) implements CustomPacketPayload {
        public static final Type<ShotFx> TYPE = ptype("shot_fx");
        public static final StreamCodec<FriendlyByteBuf, ShotFx> CODEC = StreamCodec.ofMember(ShotFx::write, ShotFx::new);

        ShotFx(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readVarInt(), readVec(b), readDirs(b), b.readBoolean(), b.readVarInt());
        }

        private static List<Vec3> readDirs(FriendlyByteBuf b) {
            int n = Math.min(b.readVarInt(), 16);
            List<Vec3> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(readDir(b));
            return l;
        }

        void write(FriendlyByteBuf b) {
            b.writeVarInt(shooter);
            b.writeVarInt(weapon);
            writeVec(b, origin);
            b.writeVarInt(dirs.size());
            for (Vec3 d : dirs) writeDir(b, d);
            b.writeBoolean(silenced);
            b.writeVarInt(shotIndex);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** you got hit: tagging slowdown + aim punch */
    public record Tagged(float tagging, float damage, float yawFrom, boolean headshot) implements CustomPacketPayload {
        public static final Type<Tagged> TYPE = ptype("tagged");
        public static final StreamCodec<FriendlyByteBuf, Tagged> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeFloat(p.tagging); b.writeFloat(p.damage); b.writeFloat(p.yawFrom); b.writeBoolean(p.headshot); },
                b -> new Tagged(b.readFloat(), b.readFloat(), b.readFloat(), b.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** you hit somebody (hit confirmation for the shooter) */
    public record HitConfirm(int victim, float damage, int group, boolean kill) implements CustomPacketPayload {
        public static final Type<HitConfirm> TYPE = ptype("hit_confirm");
        public static final StreamCodec<FriendlyByteBuf, HitConfirm> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeVarInt(p.victim); b.writeFloat(p.damage); b.writeByte(p.group); b.writeBoolean(p.kill); },
                b -> new HitConfirm(b.readVarInt(), b.readFloat(), b.readByte(), b.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record KillFeed(String killer, int killerTeam, String victim, int victimTeam, int weapon, int flags) implements CustomPacketPayload {
        public static final int HEADSHOT = 1, WALLBANG = 2, NOSCOPE = 4, SMOKE = 8, BLIND = 16, AIR = 32;
        public static final Type<KillFeed> TYPE = ptype("kill_feed");
        public static final StreamCodec<FriendlyByteBuf, KillFeed> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeUtf(p.killer, 64); b.writeByte(p.killerTeam); b.writeUtf(p.victim, 64); b.writeByte(p.victimTeam); b.writeVarInt(p.weapon); b.writeByte(p.flags); },
                b -> new KillFeed(b.readUtf(64), b.readByte(), b.readUtf(64), b.readByte(), b.readVarInt(), b.readByte()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Flash(float strength, float duration, Vec3 pos) implements CustomPacketPayload {
        public static final Type<Flash> TYPE = ptype("flash");
        public static final StreamCodec<FriendlyByteBuf, Flash> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeFloat(p.strength); b.writeFloat(p.duration); writeVec(b, p.pos); },
                b -> new Flash(b.readFloat(), b.readFloat(), readVec(b)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** third person animation events of other players */
    public record Anim(int entity, int anim, int weapon, int arg) implements CustomPacketPayload {
        public static final int RELOAD = 0, DRAW = 1, INSPECT = 2, THROW = 3, KNIFE = 4, KNIFE_HEAVY = 5, SILENCER = 6, RELOAD_CANCEL = 7, SHELL = 8;
        public static final Type<Anim> TYPE = ptype("anim");
        public static final StreamCodec<FriendlyByteBuf, Anim> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeVarInt(p.entity); b.writeByte(p.anim); b.writeVarInt(p.weapon); b.writeVarInt(p.arg); },
                b -> new Anim(b.readVarInt(), b.readByte(), b.readVarInt(), b.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** world effects: explosions etc. */
    public record Effect(int kind, Vec3 pos, float arg) implements CustomPacketPayload {
        public static final int HE = 0, FLASH = 1, MOLOTOV = 2, EXTINGUISH = 3, C4 = 4, TASER = 5, SMOKE_POP = 6, DECOY_SHOT = 7;
        public static final Type<Effect> TYPE = ptype("effect");
        public static final StreamCodec<FriendlyByteBuf, Effect> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeByte(p.kind); writeVec(b, p.pos); b.writeFloat(p.arg); },
                b -> new Effect(b.readByte(), readVec(b), b.readFloat()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private Packets() {
    }
}
