package org.xiaojian999.superpowers;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.xiaojian999.superpowers.network.AutoPayload;
import org.xiaojian999.superpowers.network.PayloadDirection;

@AutoPayload(direction = PayloadDirection.C2S)
public record GodLevitatePayload() implements CustomPayload {
    public static final CustomPayload.Id<GodLevitatePayload> ID =
            new CustomPayload.Id<>(Identifier.of(Glungus.MOD_ID, "god_levitate"));
    public static final PacketCodec<RegistryByteBuf, GodLevitatePayload> CODEC =
            PacketCodec.unit(new GodLevitatePayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}