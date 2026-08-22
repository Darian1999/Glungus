package org.xiaojian999.superpowers.client;

import org.xiaojian999.superpowers.PowerStatusPayload;

/**
 * Central client-side state for both powerset slots. The server sends one
 * {@link PowerStatusPayload} per equipped slot, and every HUD (single or dual)
 * renders from here so the single-power HUDs and the dual HUD never disagree.
 */
final class HudState {
    static final int SLOT_COUNT = 2;

    private static final Slot[] SLOTS = {new Slot(), new Slot()};

    private HudState() {
    }

    static final class Slot {
        /** Lowercase power name ("ice", "air", ...) or null when the slot is empty. */
        String power;
        int flags;
        int beamCooldown;
        int snowballCooldown;
        int ultimateCooldown;
        int possessedMobId = -1;
        float cameraOffsetY;
        int ultimatePromptTicks;
    }

    static Slot slot(int index) {
        if (index < 0 || index >= SLOT_COUNT) {
            return SLOTS[0];
        }
        return SLOTS[index];
    }

    /** True when both powerset slots are equipped — the dual HUD takes over. */
    static boolean isDual() {
        return SLOTS[0].power != null && SLOTS[1].power != null;
    }

    static void applyStatus(int slotIndex, PowerStatusPayload status) {
        if (slotIndex < 0 || slotIndex >= SLOT_COUNT) {
            return;
        }
        Slot slot = SLOTS[slotIndex];
        slot.flags = status.flags();
        slot.power = powerName(status.flags());
        slot.beamCooldown = Math.max(0, status.beamCooldown());
        slot.snowballCooldown = Math.max(0, status.snowballCooldown());
        slot.ultimateCooldown = Math.max(0, status.ultimateCooldown());
        slot.possessedMobId = status.possessedMobId();
        slot.cameraOffsetY = status.cameraOffsetY();
        slot.ultimatePromptTicks = (status.flags() & PowerStatusPayload.ULTIMATE_PRIMED) != 0
                ? HudData.promptTicks()
                : 0;
    }

    static void tick() {
        for (Slot slot : SLOTS) {
            slot.beamCooldown = decrement(slot.beamCooldown);
            slot.snowballCooldown = decrement(slot.snowballCooldown);
            slot.ultimateCooldown = decrement(slot.ultimateCooldown);
            slot.ultimatePromptTicks = decrement(slot.ultimatePromptTicks);
        }
    }

    static boolean isWaterWalking() {
        return hasFlag(PowerStatusPayload.WATER_EQUIPPED);
    }

    static boolean isGhostFormActive() {
        return hasFlag(PowerStatusPayload.GHOST_FORM_ACTIVE);
    }

    static boolean isLightningFormActive() {
        return hasFlag(PowerStatusPayload.LIGHTNING_FORM_ACTIVE);
    }

    static boolean isGodNoClipActive() {
        return hasFlag(PowerStatusPayload.GOD_NOCLIP_ACTIVE);
    }

    private static boolean hasFlag(int flag) {
        for (Slot slot : SLOTS) {
            if ((slot.flags & flag) != 0) {
                return true;
            }
        }
        return false;
    }

    private static int decrement(int value) {
        return Math.max(0, value - 1);
    }

    private static String powerName(int flags) {
        if ((flags & PowerStatusPayload.ICE_EQUIPPED) != 0) {
            return "ice";
        }
        if ((flags & PowerStatusPayload.AIR_EQUIPPED) != 0) {
            return "air";
        }
        if ((flags & PowerStatusPayload.FIRE_EQUIPPED) != 0) {
            return "fire";
        }
        if ((flags & PowerStatusPayload.WATER_EQUIPPED) != 0) {
            return "water";
        }
        if ((flags & PowerStatusPayload.GHOST_EQUIPPED) != 0) {
            return "ghost";
        }
        if ((flags & PowerStatusPayload.LIGHTNING_EQUIPPED) != 0) {
            return "lightning";
        }
        if ((flags & PowerStatusPayload.NATURE_EQUIPPED) != 0) {
            return "nature";
        }
        if ((flags & PowerStatusPayload.GOD_EQUIPPED) != 0) {
            return "god";
        }
        return null;
    }
}
