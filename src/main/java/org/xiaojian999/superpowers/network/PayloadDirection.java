package org.xiaojian999.superpowers.network;

/**
 * Declares which networking direction a payload is intended for.
 * <p>
 * When {@link AutoPayload} is placed on a {@code CustomPayload} record, this
 * enum tells {@link PayloadRegistry} whether to register the codec with
 * {@code PayloadTypeRegistry.playC2S()}, {@code playS2C()}, or both.
 */
public enum PayloadDirection {
    /** Client to server. */
    C2S,
    /** Server to client. */
    S2C,
    /** Registered for both directions (bidirectional). */
    BOTH
}
