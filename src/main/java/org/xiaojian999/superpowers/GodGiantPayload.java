package org.xiaojian999.superpowers;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.xiaojian999.superpowers.network.AutoPayload;
import org.xiaojian999.superpowers.network.PayloadDirection;

@AutoPayload(direction = PayloadDirection.C2S)
public record GodGiantPayload() implements CustomPayload {
    public static final CustomPayload.Id<GodGiantPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Glungus.MOD_ID, "god_giant"));
    public static final PacketCodec<RegistryByteBuf, GodGiantPayload> CODEC =
            PacketCodec.unit(new GodGiantPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
