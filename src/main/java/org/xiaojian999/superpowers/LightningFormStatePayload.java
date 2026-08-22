package org.xiaojian999.superpowers;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;
import org.xiaojian999.superpowers.network.AutoPayload;
import org.xiaojian999.superpowers.network.PayloadDirection;

/**
 * Tells every client whether a player has turned into Lightning (Storm) Form,
 * so their model and cape can be hidden while transformed.
 */
@AutoPayload(direction = PayloadDirection.S2C)
public record LightningFormStatePayload(UUID playerUuid, boolean active) implements CustomPayload {
    public static final CustomPayload.Id<LightningFormStatePayload> ID =
            new CustomPayload.Id<>(Identifier.of(Glungus.MOD_ID, "lightning_form_state"));
    public static final PacketCodec<RegistryByteBuf, LightningFormStatePayload> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC,
            LightningFormStatePayload::playerUuid,
            PacketCodecs.BOOLEAN,
            LightningFormStatePayload::active,
            LightningFormStatePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}