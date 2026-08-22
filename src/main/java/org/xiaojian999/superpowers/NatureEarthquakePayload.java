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
 * Tells every client when a player's Earthquake of Lucifer (the Nature
 * ultimate) starts and ends, so cameras near the epicenter can shake.
 */
@AutoPayload(direction = PayloadDirection.S2C)
public record NatureEarthquakePayload(UUID playerUuid, boolean active) implements CustomPayload {
    public static final CustomPayload.Id<NatureEarthquakePayload> ID =
            new CustomPayload.Id<>(Identifier.of(Glungus.MOD_ID, "nature_earthquake"));
    public static final PacketCodec<RegistryByteBuf, NatureEarthquakePayload> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC,
            NatureEarthquakePayload::playerUuid,
            PacketCodecs.BOOLEAN,
            NatureEarthquakePayload::active,
            NatureEarthquakePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}