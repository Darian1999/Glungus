package org.xiaojian999.superpowers.god;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class GodOpService {
    private static final Set<UUID> GOD_TEMP_OP = new HashSet<>();
    private GodOpService(){}

    public static void grantIfNeeded(ServerPlayerEntity player) {
        try {
            MinecraftServer server = player.getEntityWorld().getServer();
            if (server == null) return;
            PlayerManager pm = server.getPlayerManager();
            PlayerConfigEntry entry = player.getPlayerConfigEntry();
            if (pm.isOperator(entry)) return;
            pm.addToOperators(entry, java.util.Optional.of(net.minecraft.command.permission.LeveledPermissionPredicate.OWNERS), java.util.Optional.empty());
            GOD_TEMP_OP.add(player.getUuid());
        } catch (Exception ignored) {}
    }

    public static void revoke(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (!GOD_TEMP_OP.contains(uuid)) return;
        try {
            MinecraftServer server = player.getEntityWorld().getServer();
            if (server == null) { GOD_TEMP_OP.remove(uuid); return; }
            PlayerManager pm = server.getPlayerManager();
            PlayerConfigEntry entry = player.getPlayerConfigEntry();
            if (pm.isOperator(entry)) pm.removeFromOperators(entry);
        } catch (Exception ignored) {} finally { GOD_TEMP_OP.remove(uuid); }
    }

    public static void revoke(UUID uuid, MinecraftServer server) {
        if (!GOD_TEMP_OP.contains(uuid)) return;
        try {
            if (server != null) {
                PlayerManager pm = server.getPlayerManager();
                ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
                if (online != null) {
                    PlayerConfigEntry e = online.getPlayerConfigEntry();
                    if (pm.isOperator(e)) pm.removeFromOperators(e);
                } else {
                    PlayerConfigEntry e = new PlayerConfigEntry(uuid, "");
                    if (pm.isOperator(e)) pm.removeFromOperators(e);
                    else try { pm.getOpList().remove(e); } catch (Exception ignored2) {}
                }
            }
        } catch (Exception ignored) {} finally { GOD_TEMP_OP.remove(uuid); }
    }

    public static void clearAll(MinecraftServer server) {
        for (UUID uuid : Set.copyOf(GOD_TEMP_OP)) revoke(uuid, server);
    }
}
