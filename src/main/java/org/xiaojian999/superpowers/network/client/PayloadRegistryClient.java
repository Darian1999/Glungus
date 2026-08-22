package org.xiaojian999.superpowers.network.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xiaojian999.superpowers.Glungus;
import org.xiaojian999.superpowers.network.GenericPayload;
import org.xiaojian999.superpowers.network.PayloadDirection;
import org.xiaojian999.superpowers.network.PayloadRegistry;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side counterpart to {@link PayloadRegistry}.
 * Keeps client-only imports (ClientPlayNetworking) out of the common registry so
 * the mod remains loadable in a dedicated-server environment if ever switched
 * from {@code environment: client} to a shared environment.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Register client global receivers for S2C payloads.</li>
 *   <li>Dispatch {@link GenericPayload} channels on the client.</li>
 *   <li>Provide {@code createAndRegisterS2C} helper for runtime S2C generation.</li>
 * </ul>
 */
public final class PayloadRegistryClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(Glungus.MOD_ID + "/network-client");

    private static final Map<Identifier, ClientPlayNetworking.PlayPayloadHandler<GenericPayload>> GENERIC_S2C_HANDLERS = new HashMap<>();
    private static boolean genericClientDispatcherRegistered = false;

    private PayloadRegistryClient() {}

    /**
     * Registers the client-side dispatcher for {@link GenericPayload}.
     * Idempotent – safe to call from {@code GlungusClient} init multiple times.
     */
    public static void registerGenericClient() {
        // Ensure codec is registered (common registry handles both directions)
        PayloadRegistry.ensureGenericRegistered();

        if (genericClientDispatcherRegistered) {
            return;
        }
        ClientPlayNetworking.registerGlobalReceiver(GenericPayload.ID, (payload, context) -> {
            var handler = GENERIC_S2C_HANDLERS.get(payload.channel());
            if (handler != null) {
                handler.receive(payload, context);
            } else {
                LOGGER.warn("No S2C handler for generic channel {}", payload.channel());
            }
        });
        genericClientDispatcherRegistered = true;
        LOGGER.info("Registered generic payload dispatcher (S2C)");
    }

    /**
     * Registers a handler for a specific generic channel (S2C).
     */
    public static void registerGenericS2CHandler(Identifier channel, ClientPlayNetworking.PlayPayloadHandler<GenericPayload> handler) {
        registerGenericClient();
        GENERIC_S2C_HANDLERS.put(channel, handler);
        LOGGER.info("Registered generic S2C handler for channel {}", channel);
    }

    /**
     * Overload taking a path under {@code glungus} namespace.
     */
    public static void registerGenericS2CHandler(String path, ClientPlayNetworking.PlayPayloadHandler<GenericPayload> handler) {
        registerGenericS2CHandler(Identifier.of(Glungus.MOD_ID, path), handler);
    }

    /**
     * One-shot helper to define and register an S2C payload at runtime from client setup.
     */
    public static <T extends CustomPayload> void createAndRegisterS2C(
            CustomPayload.Id<T> id,
            PacketCodec<? super RegistryByteBuf, T> codec,
            ClientPlayNetworking.PlayPayloadHandler<T> handler
    ) {
        PayloadRegistry.registerS2C(id, codec);
        ClientPlayNetworking.registerGlobalReceiver(id, handler);
        LOGGER.info("Created and registered S2C payload {} with handler", id.id());
    }

    /**
     * Sends a generic C2S payload from client to server. Auto-registers
     * the GenericPayload if necessary, so callers never need to check.
     */
    public static void sendGenericC2S(Identifier channel, byte[] data) {
        // Ensure codec exists (common side)
        PayloadRegistry.ensureGenericRegistered();
        // Ensure client dispatcher is ready (no-op if already)
        registerGenericClient();
        ClientPlayNetworking.send(new GenericPayload(channel, data));
    }

    /**
     * Overload for glungus-namespace channel.
     */
    public static void sendGenericC2S(String path, byte[] data) {
        sendGenericC2S(Identifier.of(Glungus.MOD_ID, path), data);
    }

    /**
     * Utility to ensure an S2C payload is registered before a handler expects it.
     * Wraps {@link PayloadRegistry#ensureRegistered(Class, PayloadDirection)} but
     * also registers a client receiver in one call if desired.
     */
    public static <T extends CustomPayload> void ensureS2CAndRegister(
            Class<T> clazz,
            ClientPlayNetworking.PlayPayloadHandler<T> handler
    ) {
        PayloadRegistry.ensureRegistered(clazz, PayloadDirection.S2C);
        try {
            @SuppressWarnings("unchecked")
            CustomPayload.Id<T> id = (CustomPayload.Id<T>) clazz.getField("ID").get(null);
            ClientPlayNetworking.registerGlobalReceiver(id, handler);
        } catch (Exception e) {
            LOGGER.warn("ensureS2CAndRegister failed for {}", clazz.getName(), e);
        }
    }

    static void clearForTests() {
        GENERIC_S2C_HANDLERS.clear();
        genericClientDispatcherRegistered = false;
    }
}
