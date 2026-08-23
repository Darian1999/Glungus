package org.xiaojian999.superpowers;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.HitResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrator for the superpowers mod: owns the equipped-power slots, command/event
 * registration, the client-facing API, and the HUD status packets. All per-element
 * behaviour (and its state) lives in the matching {@code *PowerHandler}.
 */
public final class PowerManager {
    static final int ULTIMATE_DOUBLE_TAP_WINDOW = 20;
    private static final int ULTIMATE_COOLDOWN = 600;

    private static final Map<UUID, Power> PLAYER_POWERS = new HashMap<>();
    private static final Map<UUID, Power> PLAYER_SECOND_POWERS = new HashMap<>();
    private static final Set<UUID> CLIENT_WATER_WALKING_PLAYERS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Map<SlotKey, Long> LAST_ULTIMATE_PRESSES = new HashMap<>();

    private PowerManager() {
    }

    static Power getEquippedPower(UUID playerUuid, int slotIndex) {
        return slotIndex == 0
                ? PLAYER_POWERS.get(playerUuid)
                : PLAYER_SECOND_POWERS.get(playerUuid);
    }
    static Long getLastUltimatePress(SlotKey k){ return LAST_ULTIMATE_PRESSES.get(k); }
    static void clearUltimatePress(SlotKey k){ LAST_ULTIMATE_PRESSES.remove(k); }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
        ModEvents.register();
    }
    static void onDisconnect(UUID playerUuid, ServerPlayerEntity player) {
        CLIENT_WATER_WALKING_PLAYERS.remove(playerUuid);
        AirPowerHandler.removePlayer(playerUuid); FirePowerHandler.removePlayer(playerUuid);
        GhostPowerHandler.removePlayer(player); LightningPowerHandler.removePlayer(player);
        IcePowerHandler.removePlayer(playerUuid); NaturePowerHandler.removePlayer(player);
        GodPowerHandler.removePlayer(player); PLAYER_POWERS.remove(playerUuid);
        PLAYER_SECOND_POWERS.remove(playerUuid); PowerCooldowns.removeAll(playerUuid);
        LAST_ULTIMATE_PRESSES.keySet().removeIf(k->k.playerUuid().equals(playerUuid));
    }
    static void onServerStopped(net.minecraft.server.MinecraftServer server){
        for(ServerPlayerEntity p: server.getPlayerManager().getPlayerList()){ GhostPowerHandler.disableForm(p); LightningPowerHandler.disableForm(p); GodPowerHandler.removePlayer(p); }
        AirPowerHandler.clearAll(); FirePowerHandler.clearAll(); GhostPowerHandler.clearAll(); IcePowerHandler.clearAll(); WaterPowerHandler.clearAll(); LightningPowerHandler.clearAll(); NaturePowerHandler.clearAll(); GodPowerHandler.clearAll(server);
        PowerCooldowns.clearAll(); PLAYER_POWERS.clear(); PLAYER_SECOND_POWERS.clear(); CLIENT_WATER_WALKING_PLAYERS.clear(); LAST_ULTIMATE_PRESSES.clear();
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("superpowers")
                .then(CommandManager.argument("power", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                List.of("ice", "air", "fire", "water", "ghost", "lightning", "nature", "god", "none"), builder))
                        .executes(context -> choosePower(context, 0))
                        .then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 2))
                                .executes(context -> choosePower(
                                        context,
                                        IntegerArgumentType.getInteger(context, "slot") - 1))))
                .then(CommandManager.literal("use")
                        .then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 6))
                                .executes(context -> usePower(
                                        context.getSource().getPlayerOrThrow(),
                                        IntegerArgumentType.getInteger(context, "slot"))))));
    }

    private static int choosePower(CommandContext<ServerCommandSource> context, int slotIndex)
            throws CommandSyntaxException {
        String requestedPower = StringArgumentType.getString(context, "power");
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        UUID playerUuid = player.getUuid();

        if (requestedPower.equalsIgnoreCase("none")) {
            clearSlot(player, slotIndex);
            context.getSource().sendFeedback(
                    () -> Text.literal("Slot " + (slotIndex + 1) + " powers cleared."),
                    false
            );
            return 1;
        }

        Power selectedPower = Power.fromString(requestedPower);
        if (selectedPower == null) {
            context.getSource().sendError(Text.literal(
                    "Unknown superpower: " + requestedPower + ". Try ice, air, fire, water, ghost, lightning, nature, god, or none."
            ));
            return 0;
        }

        AirPowerHandler.disableFlight(player);
        FirePowerHandler.clearFireState(player);
        GhostPowerHandler.disableForm(player);
        LightningPowerHandler.disableForm(player);
        NaturePowerHandler.clearState(player);
        GodPowerHandler.removePlayer(player);
        if (slotIndex == 0) {
            PLAYER_POWERS.put(playerUuid, selectedPower);
        } else {
            PLAYER_SECOND_POWERS.put(playerUuid, selectedPower);
        }
        IcePowerHandler.clearPrimedSnowball(playerUuid);
        PowerCooldowns.removeAll(playerUuid);
        LAST_ULTIMATE_PRESSES.remove(new SlotKey(playerUuid, slotIndex));
        sendPowerStatus(player);
        String powerName = selectedPower.displayName();
        String keys = slotIndex == 0 ? "keypad 1-3" : "keypad 4-6";
        String ultimateKey = slotIndex == 0 ? "keypad 3" : "keypad 6";
        String controls = selectedPower == Power.GOD
                ? "toggle with keypad " + (slotIndex == 0 ? "1" : "4")
                        + "; KP2 giant, KP3 telekinesis, KP7 bless, KP8 levitate, KP9 laser, KP0 smite, KP. blast, KPENTER nova, KP* omnipotence, KP/ banish"
                : "use " + keys + " (ultimate = double-tap " + ultimateKey + ")";
        context.getSource().sendFeedback(
                () -> Text.literal("Slot " + (slotIndex + 1) + ": " + powerName
                        + " powers equipped. " + controls + "."),
                false
        );
        return 1;
    }

    private static void clearSlot(ServerPlayerEntity player, int slotIndex) {
        UUID playerUuid = player.getUuid();
        if (slotIndex == 0) {
            PLAYER_POWERS.remove(playerUuid);
        } else {
            PLAYER_SECOND_POWERS.remove(playerUuid);
        }
        IcePowerHandler.clearPrimedSnowball(playerUuid);
        PowerCooldowns.removeAll(playerUuid);
        GodPowerHandler.removePlayer(player);
        LAST_ULTIMATE_PRESSES.remove(new SlotKey(playerUuid, slotIndex));
        sendPowerStatus(player);
    }

    /**
     * Equips GOD in slot 0 for the hardcore 50% ascension. Keeps any existing
     * slot 2 power and clears ascension/ultimate state for a clean revival.
     */
    static void equipGodForHardcore(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        PLAYER_POWERS.put(uuid, Power.GOD);
        IcePowerHandler.clearPrimedSnowball(uuid);
        PowerCooldowns.removeAll(uuid);
        LAST_ULTIMATE_PRESSES.remove(new SlotKey(uuid, 0));
        sendPowerStatus(player);
    }

    static int usePower(ServerPlayerEntity player, int slot) {
        UUID playerUuid = player.getUuid();
        // Slots 1-3 target the first powerset, slots 4-6 the second powerset.
        int slotIndex = (slot - 1) / 3;
        int localSlot = (slot - 1) % 3 + 1;
        SlotKey slotKey = new SlotKey(playerUuid, slotIndex);
        Power power = getEquippedPower(playerUuid, slotIndex);
        if (power == null) {
            sendPowerStatus(player);
            player.sendMessage(Text.literal(slotIndex == 0
                    ? "Choose a superpower first with /superpowers ice, air, fire, water, ghost, lightning, nature, or god."
                    : "Slot 2 is empty. Choose one with /superpowers <power> 2."), true);
            return 0;
        }

        if (localSlot == 3) {
            if (power == Power.GOD) {
                return GodPowerHandler.toggleTelekinesis(player);
            }
            return handleUltimatePress(player, power, slotKey);
        }

        if (localSlot == 1) {
            return switch (power) {
                case AIR -> AirPowerHandler.toggleFlight(player);
                case FIRE -> FirePowerHandler.fireFlamethrower(player, slotKey);
                case GHOST -> GhostPowerHandler.toggleForm(player);
                case ICE -> IcePowerHandler.fireIceBeam(player, slotKey);
                case WATER -> WaterPowerHandler.fireWaterCannon(player, slotKey);
                case LIGHTNING -> LightningPowerHandler.fireChainLightning(player, slotKey);
                case NATURE -> NaturePowerHandler.toggleFlowerTrail(player);
                case GOD -> GodPowerHandler.toggleGodMode(player);
            };
        }

        if (localSlot == 2) {
            return switch (power) {
                case AIR -> AirPowerHandler.pushAirCone(player, slotKey);
                case FIRE -> FirePowerHandler.toggleFireImmunity(player);
                case GHOST -> GhostPowerHandler.wailOfTheDamned(player, slotKey);
                case ICE -> IcePowerHandler.primeIceSnowball(player, slotKey);
                case WATER -> WaterPowerHandler.startTidalWave(player, slotKey);
                case LIGHTNING -> LightningPowerHandler.summonLightningStrike(player, slotKey);
                case NATURE -> NaturePowerHandler.startVineRing(player, slotKey);
                case GOD -> GodPowerHandler.toggleGiant(player);
            };
        }

        return 0;
    }

    private static int handleUltimatePress(ServerPlayerEntity player, Power power, SlotKey slotKey) {
        String ultimateName = switch (power) {
            case AIR -> "Tempest Tornado";
            case FIRE -> "Ring of Fire";
            case GHOST -> "Soul Nova";
            case ICE -> "Glacial Cataclysm";
            case WATER -> "Water Meteor";
            case LIGHTNING -> "Storm Form";
            case NATURE -> "Earthquake of Lucifer";
            case GOD -> "God Mode";
        };
        if (power == Power.LIGHTNING && LightningPowerHandler.isFormActive(slotKey)) {
            LAST_ULTIMATE_PRESSES.remove(slotKey);
            sendPowerStatus(player);
            player.sendMessage(Text.literal(
                    "Storm Form is already active — " + (LightningPowerHandler.getFormRemaining(slotKey) + 19) / 20 + "s left."
            ), true);
            return 0;
        }
        if (power == Power.GOD) {
            player.sendMessage(Text.literal(
                    "God Mode has no ultimate — use the divine keys: KP2 giant, KP3 telekinesis, KP7 bless, KP8 levitate, KP9 laser, KP0 smite, KP. blast, KPENTER nova, KP* omnipotence, KP/ banish."
            ), true);
            return 0;
        }
        if (power == Power.NATURE && NaturePowerHandler.isEarthquakeActive(player.getUuid())) {
            LAST_ULTIMATE_PRESSES.remove(slotKey);
            sendPowerStatus(player);
            player.sendMessage(Text.literal(
                    "The Earthquake of Lucifer is already shaking — "
                            + (NaturePowerHandler.getEarthquakeRemaining(player.getUuid()) + 19) / 20 + "s left."
            ), true);
            return 0;
        }
        int remainingTicks = PowerCooldowns.ultimateRemaining(slotKey);
        if (remainingTicks > 0) {
            LAST_ULTIMATE_PRESSES.remove(slotKey);
            sendPowerStatus(player);
            sendCooldownMessage(player, ultimateName, remainingTicks);
            return 0;
        }

        long currentTick = player.getEntityWorld().getTime();
        Long previousPress = LAST_ULTIMATE_PRESSES.put(slotKey, currentTick);
        if (previousPress == null
                || currentTick < previousPress
                || currentTick - previousPress > ULTIMATE_DOUBLE_TAP_WINDOW) {
            sendPowerStatus(player);
            player.sendMessage(Text.literal(
                    ultimateName + " primed! Press keypad " + (slotKey.slotIndex() == 0 ? "3" : "6")
                            + " again within 1 second."
            ), true);
            return 0;
        }

        LAST_ULTIMATE_PRESSES.remove(slotKey);
        switch (power) {
            case AIR -> AirPowerHandler.startTornado(player);
            case FIRE -> FirePowerHandler.startRingOfFire(player);
            case GHOST -> GhostPowerHandler.unleashSoulNova(player);
            case ICE -> IcePowerHandler.unleashGlacialCataclysm(player);
            case WATER -> WaterPowerHandler.startWaterMeteor(player, slotKey);
            case LIGHTNING -> LightningPowerHandler.startForm(player, slotKey);
            case NATURE -> NaturePowerHandler.startEarthquake(player, slotKey);
            case GOD -> { }
        }
        // Storm Form's cooldown only starts once the form itself ends, and Water Meteor
        // and the Earthquake of Lucifer set their own (longer) cooldowns.
        if (power != Power.WATER && power != Power.LIGHTNING && power != Power.NATURE && power != Power.GOD) {
            PowerCooldowns.setUltimate(slotKey, ULTIMATE_COOLDOWN);
        }
        sendPowerStatus(player);
        return 1;
    }

    // ----- Client-facing API (called from mixins and the client) -----

    public static boolean isAirFlightActive(Entity entity) {
        return AirPowerHandler.isAirFlightActive(entity);
    }

    /** Whether a player currently has God Mode enabled. */
    public static boolean isGodModeActive(Entity entity) {
        return entity instanceof PlayerEntity player
                && (GodPowerHandler.isActive(player.getUuid()) || GodPowerHandler.isClientGodModeActive(player.getUuid()));
    }

    public static void setClientGodModeActive(UUID playerUuid, boolean active) {
        GodPowerHandler.setClientGodModeActive(playerUuid, active);
    }

    /** Whether a player is currently simulating omnipotence (invulnerable + divine buffs). */
    public static boolean isOmnipotenceActive(Entity entity) {
        return entity instanceof PlayerEntity player && GodPowerHandler.isOmnipotenceActive(player.getUuid());
    }

    public static boolean isGodGiantActive(Entity entity) {
        return entity instanceof PlayerEntity player && GodPowerHandler.isGiant(player.getUuid());
    }

    public static boolean isGodTelekinesisActive(Entity entity) {
        return entity instanceof PlayerEntity player && GodPowerHandler.isTelekinesisHolding(player.getUuid());
    }

    public static boolean isWaterWalkingActive(Entity entity) {
        if (!(entity instanceof PlayerEntity player)) {
            return false;
        }

        UUID playerUuid = player.getUuid();
        return getEquippedPower(playerUuid, 0) == Power.WATER
                || getEquippedPower(playerUuid, 1) == Power.WATER
                || CLIENT_WATER_WALKING_PLAYERS.contains(playerUuid);
    }

    public static void setClientWaterWalking(UUID playerUuid, boolean active) {
        if (active) {
            CLIENT_WATER_WALKING_PLAYERS.add(playerUuid);
        } else {
            CLIENT_WATER_WALKING_PLAYERS.remove(playerUuid);
        }
    }

    public static void setClientGhostFormActive(UUID playerUuid, boolean active) {
        GhostPowerHandler.setClientFormActive(playerUuid, active);
    }

    public static void setClientLightningFormActive(UUID playerUuid, boolean active) {
        LightningPowerHandler.setClientFormActive(playerUuid, active);
    }

    public static boolean isClientLightningFormActive(UUID playerUuid) {
        return LightningPowerHandler.isClientFormActive(playerUuid);
    }

    public static void setClientGodNoClipActive(UUID playerUuid, boolean active) {
        GodPowerHandler.setClientNoClipActive(playerUuid, active);
    }

    public static boolean isNoClipActive(Entity entity) {
        if (!(entity instanceof PlayerEntity player)) {
            return false;
        }
        // Vanilla grants spectators noclip in PlayerEntity.tick, but the END_SERVER_TICK
        // loop re-applies this method's result every tick, so spectator noclip must be
        // preserved here or spectators get stuck on walls.
        if (player.isSpectator()) {
            return true;
        }
        UUID playerUuid = player.getUuid();
        boolean ghostFormActive = GhostPowerHandler.isFormActive(playerUuid)
                || GhostPowerHandler.isClientFormActive(playerUuid);
        boolean lightningFormActive = LightningPowerHandler.isFormActive(playerUuid)
                || LightningPowerHandler.isClientFormActive(playerUuid);
        boolean godNoClipActive = GodPowerHandler.isNoClipActive(playerUuid)
                || GodPowerHandler.isClientNoClipActive(playerUuid);
        boolean ghostNoClip = ghostFormActive && player.isCreative() && player.getAbilities().flying;
        boolean lightningNoClip = lightningFormActive && player.getAbilities().flying;
        return ghostNoClip || lightningNoClip || godNoClipActive;
    }

    /** Whether the given player is currently in Lightning (Storm) Form. */
    public static boolean isLightningFormActive(Entity entity) {
        return entity instanceof PlayerEntity player && LightningPowerHandler.isFormActive(player.getUuid());
    }

    public static void handleSnowballCollision(SnowballEntity snowball, HitResult hitResult) {
        IcePowerHandler.handleSnowballCollision(snowball, hitResult);
    }

    // ----- HUD status packets -----
    static void sendPowerStatus(ServerPlayerEntity player) { PowerStatusSender.sendAll(player); }
    private static void sendPowerStatusForSlot(ServerPlayerEntity player, int slotIndex) { PowerStatusSender.sendForSlot(player, slotIndex); }

    static void sendCooldownMessage(ServerPlayerEntity player, String powerName, int remainingTicks) {
        int remainingSeconds = (remainingTicks + 19) / 20;
        player.sendMessage(Text.literal(powerName + " is on cooldown (" + remainingSeconds + "s)."), true);
    }
}
