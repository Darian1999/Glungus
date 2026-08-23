package org.xiaojian999.superpowers.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.xiaojian999.superpowers.GodPowerHandler;

/**
 * Hardcore death: 50% chance to ascend to godhood instead of becoming a spectator.
 * Vanilla hardcore forces spectator on respawn via ServerPlayNetworkHandler.onClientStatus.
 * This redirect intercepts that spectator assignment and flips a coin.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Redirect(method = "onClientStatus", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;changeGameMode(Lnet/minecraft/world/GameMode;)Z"))
    private boolean superpowers$hardcoreGodChance(ServerPlayerEntity player, GameMode gameMode) {
        if (gameMode == GameMode.SPECTATOR) {
            try {
                MinecraftServer server = null;
                try {
                    server = player.getEntityWorld().getServer();
                } catch (Throwable t2) {
                    server = null;
                }
                if (server != null && server.isHardcore()) {
                    if (GodPowerHandler.handleHardcoreDeath(player)) {
                        // Ascended to god – don't become spectator. Return true to mimic
                        // successful game mode change (vanilla pops the return value).
                        return true;
                    }
                }
            } catch (Throwable ignored) {
                // fall through to vanilla
            }
        }
        return player.changeGameMode(gameMode);
    }
}
