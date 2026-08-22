package org.xiaojian999.superpowers.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.EntityRendererFactories;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.xiaojian999.superpowers.*;
import org.xiaojian999.superpowers.network.PayloadDirection;
import org.xiaojian999.superpowers.network.PayloadRegistry;
import org.xiaojian999.superpowers.network.client.PayloadRegistryClient;

import java.util.UUID;

public class GlungusClient implements ClientModInitializer {
    private static boolean godLaserPressed;
    private static boolean devNotificationPending = false;

    private static final KeyBinding.Category KEY_CATEGORY = KeyBinding.Category.create(
            Identifier.of(Glungus.MOD_ID, "powers")
    );

    private static final KeyBinding FIRST_POWER_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.glungus.first_power",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_1,
            KEY_CATEGORY
    ));
    private static final KeyBinding SECOND_POWER_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.glungus.second_power",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_2,
            KEY_CATEGORY
    ));
    private static final KeyBinding ULTIMATE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.glungus.ultimate",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_3,
            KEY_CATEGORY
    ));
    private static final KeyBinding FOURTH_POWER_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.glungus.fourth_power",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_4,
            KEY_CATEGORY
    ));
    private static final KeyBinding FIFTH_POWER_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.glungus.fifth_power",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_5,
            KEY_CATEGORY
    ));
    private static final KeyBinding SIXTH_POWER_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.glungus.sixth_power",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_6,
            KEY_CATEGORY
    ));
    private static final KeyBinding GHOST_SPEED_UP_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.glungus.ghost_speed_up",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_ADD,
            KEY_CATEGORY
    ));
    private static final KeyBinding GOD_LASER_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.glungus.god_laser",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_9,
            KEY_CATEGORY
    ));
    private static final KeyBinding GOD_BLESS_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.glungus.god_bless",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_7,
            KEY_CATEGORY
    ));
    private static final KeyBinding GOD_LEVITATE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.glungus.god_levitate",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_8,
            KEY_CATEGORY
    ));
    private static final KeyBinding GOD_SMITE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.glungus.god_smite",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_0,
            KEY_CATEGORY
    ));
    private static final KeyBinding GOD_ANNIHILATE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.glungus.god_annihilate",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_DECIMAL,
            KEY_CATEGORY
    ));
    private static final KeyBinding GOD_NOVA_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.glungus.god_nova",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_ENTER,
            KEY_CATEGORY
    ));
    private static final KeyBinding GOD_OMNIPOTENCE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.glungus.god_omnipotence",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_MULTIPLY,
            KEY_CATEGORY
    ));
    private static final KeyBinding GOD_BANISH_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.glungus.god_banish",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_DIVIDE,
            KEY_CATEGORY
    ));
    private static final KeyBinding GHOST_SPEED_DOWN_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.glungus.ghost_speed_down",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_SUBTRACT,
            KEY_CATEGORY
    ));
    private static final KeyBinding GOD_NOCLIP_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.glungus.god_noclip",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_BACKSLASH,
            KEY_CATEGORY
    ));

    @Override
    public void onInitializeClient() {
        EntityRendererFactories.register(ModEntities.BIG_LIGHTNING, BigLightningEntityRenderer::new);
        IceHud.initialize();
        AirHud.initialize();
        FireHud.initialize();
        WaterHud.initialize();
        GhostHud.initialize();
        LightningHud.initialize();
        NatureHud.initialize();
        GodHud.initialize();
        DualHud.initialize();
        // Ensure S2C payloads are registered on the client if Glungus common init was missed
        // (e.g. in dev env or when payloads are added at runtime). Also registers generic fallback.
        PayloadRegistry.ensureRegistered(PowerStatusPayload.class, PayloadDirection.S2C);
        PayloadRegistry.ensureRegistered(LightningFormStatePayload.class, PayloadDirection.S2C);
        PayloadRegistry.ensureRegistered(NatureEarthquakePayload.class, PayloadDirection.S2C);
        PayloadRegistry.ensureRegistered(GodGiantPayload.class, PayloadDirection.C2S);
        PayloadRegistry.ensureRegistered(GodTelekinesisPayload.class, PayloadDirection.C2S);
        PayloadRegistryClient.registerGenericClient();

        // Dev-build notice: once the world loads, show a chat message if this is a developer build
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!Glungus.isDevBuild()) return;
            devNotificationPending = true;
        });

        ClientPlayNetworking.registerGlobalReceiver(PowerStatusPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    HudState.applyStatus(payload.slotIndex(), payload);
                    if (context.client().player != null) {
                        UUID playerUuid = context.client().player.getUuid();
                        PowerManager.setClientWaterWalking(playerUuid, HudState.isWaterWalking());
                        PowerManager.setClientGhostFormActive(playerUuid, HudState.isGhostFormActive());
                        PowerManager.setClientLightningFormActive(playerUuid, HudState.isLightningFormActive());
                        PowerManager.setClientGodNoClipActive(playerUuid, HudState.isGodNoClipActive());
                        PowerManager.setClientGodModeActive(playerUuid, HudState.isGodModeActive());
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(LightningFormStatePayload.ID, (payload, context) ->
                context.client().execute(() ->
                        PowerManager.setClientLightningFormActive(payload.playerUuid(), payload.active())));
        ClientPlayNetworking.registerGlobalReceiver(NatureEarthquakePayload.ID, (payload, context) ->
                context.client().execute(() ->
                        EarthquakeClientState.setActive(payload.playerUuid(), payload.active())));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            HudState.tick();
            if (client.getNetworkHandler() == null) {
                godLaserPressed = false;
                devNotificationPending = false;
                return;
            }
            // Flush dev-build notification once world is fully loaded and player exists
            if (devNotificationPending && client.player != null) {
                devNotificationPending = false;
                if (Glungus.isDevBuild()) {
                    String version = Glungus.getVersion();
                    client.player.sendMessage(
                            Text.literal("⚠ You are running a developer build of Glungus (" + version + ") — this build may be unstable and is not intended for public release.")
                                    .formatted(Formatting.GOLD, Formatting.BOLD),
                            false
                    );
                }
            }

            boolean laserPressed = GOD_LASER_KEY.isPressed();
            if (laserPressed != godLaserPressed) {
                ClientPlayNetworking.send(new GodLaserPayload(laserPressed));
                godLaserPressed = laserPressed;
            }
            while (GOD_BLESS_KEY.wasPressed()) {
                ClientPlayNetworking.send(new GodBlessPayload());
            }
            while (GOD_LEVITATE_KEY.wasPressed()) {
                ClientPlayNetworking.send(new GodLevitatePayload());
            }
            while (GOD_SMITE_KEY.wasPressed()) {
                ClientPlayNetworking.send(new GodSmitePayload());
            }
            while (GOD_ANNIHILATE_KEY.wasPressed()) {
                ClientPlayNetworking.send(new GodAnnihilatePayload());
            }
            while (GOD_NOVA_KEY.wasPressed()) {
                ClientPlayNetworking.send(new GodNovaPayload());
            }
            while (GOD_OMNIPOTENCE_KEY.wasPressed()) {
                ClientPlayNetworking.send(new GodOmnipotencePayload());
            }
            while (GOD_BANISH_KEY.wasPressed()) {
                ClientPlayNetworking.send(new GodBanishPayload());
            }
            while (GOD_NOCLIP_KEY.wasPressed()) {
                if (GodHud.isGodModeActive()) {
                    ClientPlayNetworking.send(new GodNoClipPayload());
                }
            }
            if (client.player != null && GhostHud.isGhostFormActive()) {
                // Ghost Form grants creative flight and phases through walls only
                // while flying; re-assert flight every tick so the player cannot
                // toggle it off mid-form.
                client.player.getAbilities().flying = true;
            }
            if (client.player != null && HudState.isGodNoClipActive()) {
                // God noclip = flight-locked phasing; keep airborne client-side too
                // so space double-tap can't drop the player before the server corrects it.
                if (!client.player.getAbilities().allowFlying) {
                    client.player.getAbilities().allowFlying = true;
                }
                if (!client.player.getAbilities().flying) {
                    client.player.getAbilities().flying = true;
                }
            }

            while (FIRST_POWER_KEY.wasPressed()) {
                ClientPlayNetworking.send(new UsePowerPayload(1));
            }
            while (SECOND_POWER_KEY.wasPressed()) {
                if (GodHud.isGodModeActive()) {
                    ClientPlayNetworking.send(new GodGiantPayload());
                } else {
                    ClientPlayNetworking.send(new UsePowerPayload(2));
                }
            }
            while (ULTIMATE_KEY.wasPressed()) {
                if (GodHud.isGodModeActive()) {
                    ClientPlayNetworking.send(new GodTelekinesisPayload());
                } else {
                    ClientPlayNetworking.send(new UsePowerPayload(3));
                }
            }
            while (FOURTH_POWER_KEY.wasPressed()) {
                ClientPlayNetworking.send(new UsePowerPayload(4));
            }
            while (FIFTH_POWER_KEY.wasPressed()) {
                if (GodHud.isGodModeActive()) {
                    ClientPlayNetworking.send(new GodGiantPayload());
                } else {
                    ClientPlayNetworking.send(new UsePowerPayload(5));
                }
            }
            while (SIXTH_POWER_KEY.wasPressed()) {
                if (GodHud.isGodModeActive()) {
                    ClientPlayNetworking.send(new GodTelekinesisPayload());
                } else {
                    ClientPlayNetworking.send(new UsePowerPayload(6));
                }
            }
            while (GHOST_SPEED_UP_KEY.wasPressed()) {
                if (GhostHud.isGhostFormActive()) {
                    ClientPlayNetworking.send(new GhostFlightSpeedPayload(1));
                }
                if (GodHud.isGodModeActive()) {
                    ClientPlayNetworking.send(new GodFlightSpeedPayload(1));
                }
            }
            while (GHOST_SPEED_DOWN_KEY.wasPressed()) {
                if (GhostHud.isGhostFormActive()) {
                    ClientPlayNetworking.send(new GhostFlightSpeedPayload(-1));
                }
                if (GodHud.isGodModeActive()) {
                    ClientPlayNetworking.send(new GodFlightSpeedPayload(-1));
                }
            }
        });
    }
}
