package dev.csarsenal.network;

import dev.csarsenal.server.ServerNet;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class Net {
    /** Implemented by the client (set during client setup); never touched on a dedicated server. */
    public interface ClientHandler {
        void shotFx(Packets.ShotFx p);

        void tagged(Packets.Tagged p);

        void hitConfirm(Packets.HitConfirm p);

        void killFeed(Packets.KillFeed p);

        void flash(Packets.Flash p);

        void anim(Packets.Anim p);

        void effect(Packets.Effect p);

        void csData(CsDataPayload p);

        void scoreboard(Packets.Scoreboard p);
    }

    public static volatile ClientHandler client;

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(Packets.Shoot.TYPE, Packets.Shoot.CODEC, (p, c) -> server(c, pl -> ServerNet.shoot(pl, p)));
        r.playToServer(Packets.Reload.TYPE, Packets.Reload.CODEC, (p, c) -> server(c, pl -> ServerNet.reload(pl, p)));
        r.playToServer(Packets.Action.TYPE, Packets.Action.CODEC, (p, c) -> server(c, pl -> ServerNet.action(pl, p)));
        r.playToServer(Packets.Throw.TYPE, Packets.Throw.CODEC, (p, c) -> server(c, pl -> ServerNet.throwGrenade(pl, p)));
        r.playToServer(Packets.Knife.TYPE, Packets.Knife.CODEC, (p, c) -> server(c, pl -> ServerNet.knife(pl, p)));
        r.playToServer(Packets.Buy.TYPE, Packets.Buy.CODEC, (p, c) -> server(c, pl -> ServerNet.buy(pl, p)));

        r.playToClient(Packets.ShotFx.TYPE, Packets.ShotFx.CODEC, (p, c) -> c.enqueueWork(() -> { if (client != null) client.shotFx(p); }));
        r.playToClient(Packets.Tagged.TYPE, Packets.Tagged.CODEC, (p, c) -> c.enqueueWork(() -> { if (client != null) client.tagged(p); }));
        r.playToClient(Packets.HitConfirm.TYPE, Packets.HitConfirm.CODEC, (p, c) -> c.enqueueWork(() -> { if (client != null) client.hitConfirm(p); }));
        r.playToClient(Packets.KillFeed.TYPE, Packets.KillFeed.CODEC, (p, c) -> c.enqueueWork(() -> { if (client != null) client.killFeed(p); }));
        r.playToClient(Packets.Flash.TYPE, Packets.Flash.CODEC, (p, c) -> c.enqueueWork(() -> { if (client != null) client.flash(p); }));
        r.playToClient(Packets.Anim.TYPE, Packets.Anim.CODEC, (p, c) -> c.enqueueWork(() -> { if (client != null) client.anim(p); }));
        r.playToClient(Packets.Effect.TYPE, Packets.Effect.CODEC, (p, c) -> c.enqueueWork(() -> { if (client != null) client.effect(p); }));
        r.playToClient(CsDataPayload.TYPE, CsDataPayload.CODEC, (p, c) -> c.enqueueWork(() -> { if (client != null) client.csData(p); }));
        r.playToClient(Packets.Scoreboard.TYPE, Packets.Scoreboard.CODEC, (p, c) -> c.enqueueWork(() -> { if (client != null) client.scoreboard(p); }));
    }

    private static void server(IPayloadContext c, java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> handler) {
        c.enqueueWork(() -> {
            Player p = c.player();
            if (p instanceof net.minecraft.server.level.ServerPlayer sp && sp.isAlive()) handler.accept(sp);
        });
    }

    private Net() {
    }
}
