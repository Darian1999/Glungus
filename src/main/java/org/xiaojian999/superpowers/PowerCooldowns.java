package org.xiaojian999.superpowers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-slot cooldown state shared by all power handlers. Each power family reads and
 * writes its own beam/second-power/ultimate cooldowns through this single owner so the
 * per-player cleanup and ticking stay consistent.
 */
final class PowerCooldowns {
    private static final Map<SlotKey, Integer> BEAM = new HashMap<>();
    private static final Map<SlotKey, Integer> SECOND_POWER = new HashMap<>();
    private static final Map<SlotKey, Integer> ULTIMATE = new HashMap<>();

    private PowerCooldowns() {
    }

    static int beamRemaining(SlotKey key) {
        return BEAM.getOrDefault(key, 0);
    }

    static void setBeam(SlotKey key, int ticks) {
        BEAM.put(key, ticks);
    }

    static int secondPowerRemaining(SlotKey key) {
        return SECOND_POWER.getOrDefault(key, 0);
    }

    static void setSecondPower(SlotKey key, int ticks) {
        SECOND_POWER.put(key, ticks);
    }

    static int ultimateRemaining(SlotKey key) {
        return ULTIMATE.getOrDefault(key, 0);
    }

    static void setUltimate(SlotKey key, int ticks) {
        ULTIMATE.put(key, ticks);
    }

    static void removeAll(UUID playerUuid) {
        removeAllForPlayer(BEAM, playerUuid);
        removeAllForPlayer(SECOND_POWER, playerUuid);
        removeAllForPlayer(ULTIMATE, playerUuid);
    }

    static void tickAll() {
        tick(BEAM);
        tick(SECOND_POWER);
        tick(ULTIMATE);
    }

    static void clearAll() {
        BEAM.clear();
        SECOND_POWER.clear();
        ULTIMATE.clear();
    }

    private static <V> void removeAllForPlayer(Map<SlotKey, V> map, UUID playerUuid) {
        map.keySet().removeIf(key -> key.playerUuid().equals(playerUuid));
    }

    private static void tick(Map<SlotKey, Integer> cooldowns) {
        cooldowns.entrySet().removeIf(entry -> {
            int remainingTicks = entry.getValue() - 1;
            if (remainingTicks <= 0) {
                return true;
            }
            entry.setValue(remainingTicks);
            return false;
        });
    }
}
