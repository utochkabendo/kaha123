package dev.csarsenal.network;

import dev.csarsenal.CsArsenal;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** armor / helmet / defuser / team / money of a player, for the HUD, the buy menu and the third person model */
public record CsDataPayload(int entity, int armor, boolean helmet, boolean defuser, int team, int money) implements CustomPacketPayload {
    public static final Type<CsDataPayload> TYPE = new Type<>(CsArsenal.id("cs_data"));
    public static final StreamCodec<FriendlyByteBuf, CsDataPayload> CODEC = StreamCodec.ofMember(
            (p, b) -> { b.writeVarInt(p.entity); b.writeVarInt(p.armor); b.writeBoolean(p.helmet); b.writeBoolean(p.defuser); b.writeByte(p.team); b.writeVarInt(p.money); },
            b -> new CsDataPayload(b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readByte(), b.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
