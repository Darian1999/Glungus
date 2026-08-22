package org.xiaojian999.superpowers;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.xiaojian999.superpowers.network.AutoPayload;
import org.xiaojian999.superpowers.network.PayloadDirection;

@AutoPayload(direction = PayloadDirection.S2C)
public record PowerStatusPayload(
        int flags,
        int beamCooldown,
        int snowballCooldown,
        int ultimateCooldown,
        int possessedMobId,
        float cameraOffsetY,
        int slotIndex
) implements CustomPayload {
    public static final int ICE_EQUIPPED = 1;
    public static final int SNOWBALL_PRIMED = 1 << 1;
    public static final int ULTIMATE_PRIMED = 1 << 2;
    public static final int AIR_EQUIPPED = 1 << 3;
    public static final int AIR_FLIGHT_ACTIVE = 1 << 4;
    public static final int FIRE_EQUIPPED = 1 << 5;
    public static final int FIRE_BEAM_ACTIVE = 1 << 6;
    public static final int FIRE_IMMUNE_ACTIVE = 1 << 7;
    public static final int WATER_EQUIPPED = 1 << 8;
    public static final int GHOST_EQUIPPED = 1 << 9;
    public static final int GHOST_FORM_ACTIVE = 1 << 10;
    public static final int GHOST_POSSESSING = 1 << 11;
    public static final int LIGHTNING_EQUIPPED = 1 << 12;
    public static final int LIGHTNING_FORM_ACTIVE = 1 << 13;
    public static final int NATURE_EQUIPPED = 1 << 14;
    public static final int NATURE_FLOWER_TRAIL_ACTIVE = 1 << 15;
    public static final int NATURE_VINE_RING_ACTIVE = 1 << 16;
    public static final int NATURE_EARTHQUAKE_ACTIVE = 1 << 17;
    public static final int GOD_EQUIPPED = 1 << 18;
    public static final int GOD_MODE_ACTIVE = 1 << 19;
    public static final int GOD_NOCLIP_ACTIVE = 1 << 20;
    public static final int GOD_GIANT_ACTIVE = 1 << 21;
    public static final int GOD_TELEKINESIS_ACTIVE = 1 << 22;

    public static final CustomPayload.Id<PowerStatusPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Glungus.MOD_ID, "power_status"));
    public static final PacketCodec<RegistryByteBuf, PowerStatusPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT,
            PowerStatusPayload::flags,
            PacketCodecs.VAR_INT,
            PowerStatusPayload::beamCooldown,
            PacketCodecs.VAR_INT,
            PowerStatusPayload::snowballCooldown,
            PacketCodecs.VAR_INT,
            PowerStatusPayload::ultimateCooldown,
            PacketCodecs.VAR_INT,
            PowerStatusPayload::possessedMobId,
            PacketCodecs.FLOAT,
            PowerStatusPayload::cameraOffsetY,
            PacketCodecs.VAR_INT,
            PowerStatusPayload::slotIndex,
            PowerStatusPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}