package org.xiaojian999.superpowers;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.xiaojian999.superpowers.network.AutoPayload;
import org.xiaojian999.superpowers.network.PayloadDirection;

@AutoPayload(direction = PayloadDirection.C2S)
public record GodBlessPayload() implements CustomPayload {
    public static final CustomPayload.Id<GodBlessPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Glungus.MOD_ID, "god_bless"));
    public static final PacketCodec<RegistryByteBuf, GodBlessPayload> CODEC =
            PacketCodec.unit(new GodBlessPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}