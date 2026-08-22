package org.xiaojian999.superpowers;

import net.minecraft.util.Identifier;
import net.minecraft.network.packet.CustomPayload;
import org.xiaojian999.superpowers.network.AutoPayload;
import org.xiaojian999.superpowers.network.PayloadDirection;

/**
 * Example empty payload – demonstrates auto-generation of a unit codec.
 * No CODEC field; registry will generate {@code PacketCodec.unit(new PongPayload())}
 * and register it BOTH directions as an example of bidirectional auto-generation.
 */
@AutoPayload(value = "pong", direction = PayloadDirection.BOTH)
public record PongPayload() implements CustomPayload {
    public static final CustomPayload.Id<PongPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Glungus.MOD_ID, "pong"));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
