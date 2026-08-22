package org.xiaojian999.superpowers.network;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link net.minecraft.network.packet.CustomPayload} for automatic
 * registration by {@link PayloadRegistry}.
 * <p>
 * When present, {@link PayloadRegistry#autoRegister(Class)} (and bulk helpers) read
 * this annotation to decide which {@code PayloadTypeRegistry} to use. If the
 * annotation is absent the payload is treated as {@link PayloadDirection#C2S}
 * by default when auto-registered via reflection, but explicit
 * {@link PayloadRegistry#registerC2S} / {@link PayloadRegistry#registerS2C}
 * calls still work.
 * <p>
 * Example:
 * <pre>{@code
 * @AutoPayload(value = "use_power", direction = PayloadDirection.C2S)
 * public record UsePowerPayload(int slot) implements CustomPayload { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AutoPayload {
    /**
     * Identifier path (e.g. {@code "use_power"}) under {@code glungus} namespace.
     * If empty, the payload's own {@code ID.id()} path is used.
     */
    String value() default "";

    /** Networking direction for auto-registration. */
    PayloadDirection direction() default PayloadDirection.C2S;
}
