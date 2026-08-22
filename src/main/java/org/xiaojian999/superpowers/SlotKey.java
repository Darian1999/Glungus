package org.xiaojian999.superpowers;

import java.util.UUID;

/** Identifies one of a player's two power slots. */
record SlotKey(UUID playerUuid, int slotIndex) {
}
