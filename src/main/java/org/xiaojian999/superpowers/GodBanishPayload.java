package org.xiaojian999.superpowers;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.xiaojian999.superpowers.network.AutoPayload;
import org.xiaojian999.superpowers.network.PayloadDirection;

@AutoPayload(direction = PayloadDirection.C2S)
public record GodBanishPayload() implements CustomPayload {
    public static final CustomPayload.Id<GodBanishPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Glungus.MOD_ID, "god_banish"));
    public static final PacketCodec<RegistryByteBuf, GodBanishPayload> CODEC =
            PacketCodec.unit(new GodBanishPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}