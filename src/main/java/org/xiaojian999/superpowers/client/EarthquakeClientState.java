package org.xiaojian999.superpowers.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Client-side tracker for active Earthquakes of Lucifer, driven by
 * {@code NatureEarthquakePayload}. The camera shakes while the local player is
 * near an epicenter, with intensity fading with distance.
 */
public final class EarthquakeClientState {
    private static final double SHAKE_RADIUS = 32.0D;

    private static final Set<UUID> ACTIVE_EARTHQUAKES = new HashSet<>();

    private EarthquakeClientState() {
    }

    public static void setActive(UUID playerUuid, boolean active) {
        if (active) {
            ACTIVE_EARTHQUAKES.add(playerUuid);
        } else {
            ACTIVE_EARTHQUAKES.remove(playerUuid);
        }
    }

    /** 0 when no quake is shaking the local player, up to 1 right at an epicenter. */
    public static double shakeIntensity() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return 0.0D;
        }
        double intensity = 0.0D;
        for (UUID uuid : ACTIVE_EARTHQUAKES) {
            PlayerEntity owner = client.world.getPlayerByUuid(uuid);
            if (owner == null) {
                continue;
            }
            double distance = Math.sqrt(owner.squaredDistanceTo(client.player));
            if (distance < SHAKE_RADIUS) {
                intensity = Math.max(intensity, 1.0D - distance / SHAKE_RADIUS);
            }
        }
        return intensity;
    }

    public static void clear() {
        ACTIVE_EARTHQUAKES.clear();
    }
}
