package org.xiaojian999.superpowers;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.xiaojian999.superpowers.network.AutoPayload;
import org.xiaojian999.superpowers.network.PayloadDirection;

@AutoPayload(direction = PayloadDirection.C2S)
public record GhostFlightSpeedPayload(int direction) implements CustomPayload {
    public static final CustomPayload.Id<GhostFlightSpeedPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Glungus.MOD_ID, "ghost_flight_speed"));
    public static final PacketCodec<RegistryByteBuf, GhostFlightSpeedPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT,
            GhostFlightSpeedPayload::direction,
            GhostFlightSpeedPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}