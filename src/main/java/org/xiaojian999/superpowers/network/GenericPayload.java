package org.xiaojian999.superpowers.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.xiaojian999.superpowers.Glungus;

import java.util.Arrays;

/**
 * Fallback dynamic payload used when a feature needs network traffic but no
 * dedicated {@link CustomPayload} class exists yet.
 * <p>
 * This is the core of the "auto-generate if necessary" feature: any code can
 * send arbitrary bytes on a named {@code channel} without defining a new record.
 * The payload is registered bidirectionally ({@code C2S} and {@code S2C}) once
 * at startup via {@link PayloadRegistry}, and then multiplexed by {@code channel}.
 * <p>
 * <b>Example – sending without a dedicated class:</b>
 * <pre>{@code
 * // Client -> Server
 * PayloadRegistry.sendGenericC2S(player, "my_feature/toggle", buf -> buf.writeBoolean(true));
 *
 * // Server handler registered via
 * PayloadRegistry.registerGenericC2SHandler("my_feature/toggle", (payload, context) -> {
 *     boolean value = payload.dataAsBuf().readBoolean();
 * });
 * }</pre>
 */
public record GenericPayload(Identifier channel, byte[] data) implements CustomPayload {

    public static final CustomPayload.Id<GenericPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Glungus.MOD_ID, "generic"));

    // Bidirectional codec: channel + raw bytes. Use Identifier codec + byte array.
    @SuppressWarnings("unchecked")
    public static final PacketCodec<RegistryByteBuf, GenericPayload> CODEC = PacketCodec.tuple(
            Identifier.PACKET_CODEC,
            GenericPayload::channel,
            PacketCodecs.BYTE_ARRAY,
            GenericPayload::data,
            GenericPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    /**
     * Convenience factory for a channel under the {@code glungus} namespace.
     */
    public static GenericPayload of(String path, byte[] data) {
        return new GenericPayload(Identifier.of(Glungus.MOD_ID, path), data);
    }

    /**
     * Convenience factory for an arbitrary identifier channel.
     */
    public static GenericPayload of(Identifier channel, byte[] data) {
        return new GenericPayload(channel, data);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GenericPayload that)) return false;
        return channel.equals(that.channel) && Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return 31 * channel.hashCode() + Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return "GenericPayload[channel=" + channel + ", bytes=" + data.length + "]";
    }
}
