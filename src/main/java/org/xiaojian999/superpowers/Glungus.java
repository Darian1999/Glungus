package org.xiaojian999.superpowers;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xiaojian999.superpowers.network.PayloadRegistry;

import java.util.Locale;

public class Glungus implements ModInitializer {
    public static final String MOD_ID = "glungus";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Explicitly ban https://modrinth.com/mod/wegui — see README "do not use glungus with these mods"
        if (FabricLoader.getInstance().isModLoaded("wegui")) {
            throw new RuntimeException("listen to the readme, don't use that one chinese worldedit mod");
        }
        ModEntities.register();
        // Auto-generates and registers all C2S/S2C payloads if necessary.
        // - Annotation-driven (@AutoPayload) and reflection-based convention (ID+CODEC)
        // - Bidirectional GenericPayload fallback for dynamic features without a dedicated class
        // - Idempotent: safe if a payload was already registered manually elsewhere
        PayloadRegistry.registerBuiltins();
        PowerManager.initialize();
        if (isDevBuild()) {
            LOGGER.warn("Running Glungus developer build {} — this build is unstable and not for public release.", getVersion());
        }
    }

    /**
     * Returns the resolved mod version string as declared in fabric.mod.json
     * (e.g. "1.0.0-1.21.11" for release, "1.0.0-SNAPSHOT" for dev builds).
     */
    public static String getVersion() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    /**
     * Developer builds are identified purely by version string suffixes such as
     * {@code -SNAPSHOT}, {@code -dev}, {@code -alpha}, {@code -beta},
     * {@code -rc}, {@code -pre}, {@code -nightly}.
     * Release builds use the format {@code 1.0.0-1.21.11} (semver + Minecraft version)
     * which contains a hyphen but is NOT considered a dev build because the suffix
     * is the numeric Minecraft version.
     */
    public static boolean isDevBuild() {
        String v = getVersion().toLowerCase(Locale.ROOT);
        if (v.equals("unknown")) {
            return false;
        }
        return v.contains("snapshot")
                || v.contains("dev")
                || v.contains("alpha")
                || v.contains("beta")
                || v.contains("rc")
                || v.contains("pre")
                || v.contains("nightly");
    }
}
