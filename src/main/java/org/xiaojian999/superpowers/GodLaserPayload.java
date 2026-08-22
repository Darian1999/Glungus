package org.xiaojian999.superpowers;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.xiaojian999.superpowers.network.AutoPayload;
import org.xiaojian999.superpowers.network.PayloadDirection;

@AutoPayload(direction = PayloadDirection.C2S)
public record GodLaserPayload(boolean active) implements CustomPayload {
    public static final CustomPayload.Id<GodLaserPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Glungus.MOD_ID, "god_laser"));
    public static final PacketCodec<RegistryByteBuf, GodLaserPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT.xmap(value -> value != 0, active -> active ? 1 : 0),
            GodLaserPayload::active,
            GodLaserPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}