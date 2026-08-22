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
    private static final int ULTIMATE_DOUBLE_TAP_WINDOW = 20;
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

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));

        // Ensure C2S payloads are registered even if Glungus registration was missed (auto-generate if necessary).
        // This makes PowerManager self-healing for new payloads added without editing Glungus.
        org.xiaojian999.superpowers.network.PayloadRegistry.ensureRegistered(UsePowerPayload.class, org.xiaojian999.superpowers.network.PayloadDirection.C2S);
        org.xiaojian999.superpowers.network.PayloadRegistry.ensureRegistered(GhostFlightSpeedPayload.class, org.xiaojian999.superpowers.network.PayloadDirection.C2S);
        org.xiaojian999.superpowers.network.PayloadRegistry.ensureRegistered(GodLaserPayload.class, org.xiaojian999.superpowers.network.PayloadDirection.C2S);
        org.xiaojian999.superpowers.network.PayloadRegistry.ensureRegistered(GodBlessPayload.class, org.xiaojian999.superpowers.network.PayloadDirection.C2S);
        org.xiaojian999.superpowers.network.PayloadRegistry.ensureRegistered(GodLevitatePayload.class, org.xiaojian999.superpowers.network.PayloadDirection.C2S);
        org.xiaojian999.superpowers.network.PayloadRegistry.ensureRegistered(GodFlightSpeedPayload.class, org.xiaojian999.superpowers.network.PayloadDirection.C2S);
        org.xiaojian999.superpowers.network.PayloadRegistry.ensureRegistered(GodSmitePayload.class, org.xiaojian999.superpowers.network.PayloadDirection.C2S);
        org.xiaojian999.superpowers.network.PayloadRegistry.ensureRegistered(GodAnnihilatePayload.class, org.xiaojian999.superpowers.network.PayloadDirection.C2S);
        org.xiaojian999.superpowers.network.PayloadRegistry.ensureRegistered(GodNovaPayload.class, org.xiaojian999.superpowers.network.PayloadDirection.C2S);
        org.xiaojian999.superpowers.network.PayloadRegistry.ensureRegistered(GodOmnipotencePayload.class, org.xiaojian999.superpowers.network.PayloadDirection.C2S);
        org.xiaojian999.superpowers.network.PayloadRegistry.ensureRegistered(GodBanishPayload.class, org.xiaojian999.superpowers.network.PayloadDirection.C2S);
        org.xiaojian999.superpowers.network.PayloadRegistry.ensureRegistered(GodNoClipPayload.class, org.xiaojian999.superpowers.network.PayloadDirection.C2S);
        org.xiaojian999.superpowers.network.PayloadRegistry.ensureRegistered(GodGiantPayload.class, org.xiaojian999.superpowers.network.PayloadDirection.C2S);
        org.xiaojian999.superpowers.network.PayloadRegistry.ensureRegistered(GodTelekinesisPayload.class, org.xiaojian999.superpowers.network.PayloadDirection.C2S);

        ServerPlayNetworking.registerGlobalReceiver(UsePowerPayload.ID, (payload, context) ->
                context.server().execute(() -> usePower(context.player(), payload.slot())));
        ServerPlayNetworking.registerGlobalReceiver(GhostFlightSpeedPayload.ID, (payload, context) ->
                context.server().execute(() -> GhostPowerHandler.adjustFlightSpeed(context.player(), payload.direction())));
        ServerPlayNetworking.registerGlobalReceiver(GodLaserPayload.ID, (payload, context) ->
                context.server().execute(() -> GodPowerHandler.setLaserActive(context.player(), payload.active())));
        ServerPlayNetworking.registerGlobalReceiver(GodBlessPayload.ID, (payload, context) ->
                context.server().execute(() -> GodPowerHandler.blessTarget(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(GodLevitatePayload.ID, (payload, context) ->
                context.server().execute(() -> GodPowerHandler.levitateMobs(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(GodFlightSpeedPayload.ID, ((payload, context) ->
                context.server().execute(() -> GodPowerHandler.adjustFlightSpeed(context.player(), payload.direction()))));
        ServerPlayNetworking.registerGlobalReceiver(GodSmitePayload.ID, (payload, context) ->
                context.server().execute(() -> GodPowerHandler.smiteTarget(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(GodAnnihilatePayload.ID, (payload, context) ->
                context.server().execute(() -> GodPowerHandler.annihilateArea(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(GodNovaPayload.ID, (payload, context) ->
                context.server().execute(() -> GodPowerHandler.holyNova(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(GodOmnipotencePayload.ID, (payload, context) ->
                context.server().execute(() -> GodPowerHandler.activateOmnipotence(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(GodBanishPayload.ID, (payload, context) ->
                context.server().execute(() -> GodPowerHandler.banishTarget(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(GodNoClipPayload.ID, (payload, context) ->
                context.server().execute(() -> GodPowerHandler.toggleNoClip(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(GodGiantPayload.ID, (payload, context) ->
                context.server().execute(() -> GodPowerHandler.toggleGiant(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(GodTelekinesisPayload.ID, (payload, context) ->
                context.server().execute(() -> GodPowerHandler.toggleTelekinesis(context.player())));

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            if (!(entity instanceof MobEntity mob)
                    || !GhostPowerHandler.isFormActive(serverPlayer.getUuid())
                    || GhostPowerHandler.isPossessed(mob.getUuid())) {
                return ActionResult.PASS;
            }
            GhostPowerHandler.possess(serverPlayer, mob);
            return ActionResult.FAIL;
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof SnowballEntity snowball
                    && snowball.getOwner() instanceof ServerPlayerEntity owner) {
                IcePowerHandler.onSnowballLoaded(snowball, owner);
            }
        });

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            WaterPowerHandler.tick(world);
            AirPowerHandler.tick(world);
            FirePowerHandler.tick(world);
            IcePowerHandler.tick(world);
            NaturePowerHandler.tick(world);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            PowerCooldowns.tickAll();
            GhostPowerHandler.tickServer(server);
            FirePowerHandler.tickServer(server);
            LightningPowerHandler.tickServer(server);
            NaturePowerHandler.tickServer(server);
            GodPowerHandler.tickServer(server);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                // ServerPlayerEntity.tick never reaches PlayerEntity.tick, which is where vanilla
                // sets noClip for spectators, so form-based noclip must be applied here directly.
                player.noClip = isNoClipActive(player);
                AirPowerHandler.tickPlayer(player);
                GhostPowerHandler.tickPlayer(player);
                LightningPowerHandler.tickPlayer(player);
                FirePowerHandler.tickPlayer(player);
                GodPowerHandler.tickPlayer(player);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sendPowerStatus(handler.player);
            LightningPowerHandler.sendActiveFormStates(handler.player, server);
            NaturePowerHandler.sendActiveEarthquakes(handler.player, server);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID playerUuid = handler.player.getUuid();
            CLIENT_WATER_WALKING_PLAYERS.remove(playerUuid);
            AirPowerHandler.removePlayer(playerUuid);
            FirePowerHandler.removePlayer(playerUuid);
            GhostPowerHandler.removePlayer(handler.player);
            LightningPowerHandler.removePlayer(handler.player);
            IcePowerHandler.removePlayer(playerUuid);
            NaturePowerHandler.removePlayer(handler.player);
            GodPowerHandler.removePlayer(handler.player);
            PLAYER_POWERS.remove(playerUuid);
            PLAYER_SECOND_POWERS.remove(playerUuid);
            PowerCooldowns.removeAll(playerUuid);
            LAST_ULTIMATE_PRESSES.keySet().removeIf(key -> key.playerUuid().equals(playerUuid));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                GhostPowerHandler.disableForm(player);
                LightningPowerHandler.disableForm(player);
                GodPowerHandler.removePlayer(player);
            }
            AirPowerHandler.clearAll();
            FirePowerHandler.clearAll();
            GhostPowerHandler.clearAll();
            IcePowerHandler.clearAll();
            WaterPowerHandler.clearAll();
            LightningPowerHandler.clearAll();
            NaturePowerHandler.clearAll();
            GodPowerHandler.clearAll(server);
            PowerCooldowns.clearAll();
            PLAYER_POWERS.clear();
            PLAYER_SECOND_POWERS.clear();
            CLIENT_WATER_WALKING_PLAYERS.clear();
            LAST_ULTIMATE_PRESSES.clear();
        });
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

        Power selectedPower;
        if (requestedPower.equalsIgnoreCase("ice")) {
            selectedPower = Power.ICE;
        } else if (requestedPower.equalsIgnoreCase("air")) {
            selectedPower = Power.AIR;
        } else if (requestedPower.equalsIgnoreCase("fire")) {
            selectedPower = Power.FIRE;
        } else if (requestedPower.equalsIgnoreCase("water")) {
            selectedPower = Power.WATER;
        } else if (requestedPower.equalsIgnoreCase("ghost")) {
            selectedPower = Power.GHOST;
        } else if (requestedPower.equalsIgnoreCase("lightning")) {
            selectedPower = Power.LIGHTNING;
        } else if (requestedPower.equalsIgnoreCase("nature")) {
            selectedPower = Power.NATURE;
        } else if (requestedPower.equalsIgnoreCase("god")) {
            selectedPower = Power.GOD;
        } else {
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
        String powerName = switch (selectedPower) {
            case AIR -> "Air";
            case FIRE -> "Fire";
            case GHOST -> "Ghost";
            case ICE -> "Ice";
            case WATER -> "Water";
            case LIGHTNING -> "Lightning";
            case NATURE -> "Nature";
            case GOD -> "God";
        };
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

    private static int usePower(ServerPlayerEntity player, int slot) {
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

    static void sendPowerStatus(ServerPlayerEntity player) {
        sendPowerStatusForSlot(player, 0);
        sendPowerStatusForSlot(player, 1);
    }

    private static void sendPowerStatusForSlot(ServerPlayerEntity player, int slotIndex) {
        UUID playerUuid = player.getUuid();
        Power equippedPower = getEquippedPower(playerUuid, slotIndex);
        if (equippedPower == null) {
            // Always report an empty slot so the client can drop stale HUD state.
            ServerPlayNetworking.send(player, new PowerStatusPayload(0, 0, 0, 0, -1, 0.0F, slotIndex));
            return;
        }

        SlotKey slotKey = new SlotKey(playerUuid, slotIndex);
        int flags = 0;
        if (equippedPower == Power.ICE) {
            flags |= PowerStatusPayload.ICE_EQUIPPED;
        } else if (equippedPower == Power.AIR) {
            flags |= PowerStatusPayload.AIR_EQUIPPED;
            if (AirPowerHandler.isFlightActive(playerUuid)) {
                flags |= PowerStatusPayload.AIR_FLIGHT_ACTIVE;
            }
        } else if (equippedPower == Power.FIRE) {
            flags |= PowerStatusPayload.FIRE_EQUIPPED;
            if (FirePowerHandler.isImmune(playerUuid)) {
                flags |= PowerStatusPayload.FIRE_IMMUNE_ACTIVE;
            }
            if (FirePowerHandler.isBeamActive(slotKey)) {
                flags |= PowerStatusPayload.FIRE_BEAM_ACTIVE;
            }
        } else if (equippedPower == Power.WATER) {
            flags |= PowerStatusPayload.WATER_EQUIPPED;
        } else if (equippedPower == Power.GHOST) {
            flags |= PowerStatusPayload.GHOST_EQUIPPED;
            if (GhostPowerHandler.isFormActive(playerUuid)) {
                flags |= PowerStatusPayload.GHOST_FORM_ACTIVE;
            }
            if (GhostPowerHandler.isPossessing(playerUuid)) {
                flags |= PowerStatusPayload.GHOST_POSSESSING;
            }
        } else if (equippedPower == Power.LIGHTNING) {
            flags |= PowerStatusPayload.LIGHTNING_EQUIPPED;
            if (LightningPowerHandler.isFormActive(slotKey)) {
                flags |= PowerStatusPayload.LIGHTNING_FORM_ACTIVE;
            }
        } else if (equippedPower == Power.NATURE) {
            flags |= PowerStatusPayload.NATURE_EQUIPPED;
            if (NaturePowerHandler.isFlowerTrailActive(playerUuid)) {
                flags |= PowerStatusPayload.NATURE_FLOWER_TRAIL_ACTIVE;
            }
            if (NaturePowerHandler.isVineRingActive(slotKey)) {
                flags |= PowerStatusPayload.NATURE_VINE_RING_ACTIVE;
            }
            if (NaturePowerHandler.isEarthquakeActive(playerUuid)) {
                flags |= PowerStatusPayload.NATURE_EARTHQUAKE_ACTIVE;
            }
        } else if (equippedPower == Power.GOD) {
            flags |= PowerStatusPayload.GOD_EQUIPPED;
            if (GodPowerHandler.isActive(playerUuid)) {
                flags |= PowerStatusPayload.GOD_MODE_ACTIVE;
            }
            if (GodPowerHandler.isNoClipActive(playerUuid)) {
                flags |= PowerStatusPayload.GOD_NOCLIP_ACTIVE;
            }
            if (GodPowerHandler.isGiant(playerUuid)) {
                flags |= PowerStatusPayload.GOD_GIANT_ACTIVE;
            }
            if (GodPowerHandler.isTelekinesisHolding(playerUuid)) {
                flags |= PowerStatusPayload.GOD_TELEKINESIS_ACTIVE;
            }
        }
        if (equippedPower == Power.ICE && IcePowerHandler.isSnowballPrimed(playerUuid)) {
            flags |= PowerStatusPayload.SNOWBALL_PRIMED;
        }

        Long lastUltimatePress = LAST_ULTIMATE_PRESSES.get(slotKey);
        if (lastUltimatePress != null) {
            long currentTick = player.getEntityWorld().getTime();
            if (currentTick >= lastUltimatePress
                    && currentTick - lastUltimatePress <= ULTIMATE_DOUBLE_TAP_WINDOW) {
                flags |= PowerStatusPayload.ULTIMATE_PRIMED;
            } else {
                LAST_ULTIMATE_PRESSES.remove(slotKey);
            }
        }

        int beamValue = PowerCooldowns.beamRemaining(slotKey);
        int snowballValue = PowerCooldowns.secondPowerRemaining(slotKey);
        if (equippedPower == Power.FIRE) {
            Integer activeBeamTicks = FirePowerHandler.getActiveBeamTicks(slotKey);
            if (activeBeamTicks != null && activeBeamTicks > 0) {
                beamValue = activeBeamTicks;
            }
        }
        if (equippedPower == Power.NATURE) {
            Integer ringTicks = NaturePowerHandler.getVineRingRemaining(slotKey);
            if (ringTicks != null) {
                // While the Vine Ring spins, the second-power meter shows the time
                // remaining in the ring instead of a cooldown.
                snowballValue = ringTicks;
            }
        }
        int ultimateValue = PowerCooldowns.ultimateRemaining(slotKey);
        if (equippedPower == Power.LIGHTNING) {
            Integer formTicks = LightningPowerHandler.getFormRemaining(slotKey);
            if (formTicks != null) {
                // While Storm Form is running, the ultimate meter shows the time
                // remaining in the form instead of a cooldown.
                ultimateValue = formTicks;
            }
        }
        if (equippedPower == Power.NATURE) {
            Integer quakeTicks = NaturePowerHandler.getEarthquakeRemaining(playerUuid);
            if (quakeTicks != null) {
                // While the earthquake rages, the ultimate meter shows the time
                // remaining in the quake instead of a cooldown.
                ultimateValue = quakeTicks;
            }
        }

        int possessedMobId = -1;
        float cameraOffsetY = 0.0F;
        MobEntity possessedMob = GhostPowerHandler.getPossessedMob(player);
        if (possessedMob != null) {
            possessedMobId = possessedMob.getId();
            cameraOffsetY = (float) ((possessedMob.getEyeY() - possessedMob.getY())
                    - (player.getEyeY() - player.getY()));
        }

        ServerPlayNetworking.send(player, new PowerStatusPayload(
                flags,
                beamValue,
                snowballValue,
                ultimateValue,
                possessedMobId,
                cameraOffsetY,
                slotIndex
        ));
    }

    static void sendCooldownMessage(ServerPlayerEntity player, String powerName, int remainingTicks) {
        int remainingSeconds = (remainingTicks + 19) / 20;
        player.sendMessage(Text.literal(powerName + " is on cooldown (" + remainingSeconds + "s)."), true);
    }
}
