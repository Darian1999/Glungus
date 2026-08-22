package org.xiaojian999.superpowers;

import net.minecraft.util.Identifier;
import net.minecraft.network.packet.CustomPayload;
import org.xiaojian999.superpowers.network.AutoPayload;
import org.xiaojian999.superpowers.network.PayloadDirection;

/**
 * Example payload demonstrating auto-generation: no CODEC field is defined,
 * so {@link org.xiaojian999.superpowers.network.PayloadRegistry} will auto-generate
 * a VAR_INT codec from the single {@code int value} component and auto-register
 * it C2S if necessary. This file exists to demonstrate the feature – it is not
 * used by core powers but shows that new payloads require zero boilerplate beyond
 * the record + @AutoPayload.
 */
@AutoPayload(value = "ping", direction = PayloadDirection.C2S)
public record PingPayload(int value) implements CustomPayload {
    public static final CustomPayload.Id<PingPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Glungus.MOD_ID, "ping"));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
