package org.xiaojian999.superpowers.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xiaojian999.superpowers.Glungus;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads every HUD visual (layout, colors, text, card data) from JSON resource
 * files under {@code assets/glungus/hud} so no visuals are hard-coded in code.
 *
 * <p>Resources are read through the client resource manager so resource packs can
 * override the look, with a classpath fallback that reads straight from the mod jar.
 * A missing or broken config never crashes the game: it is logged and the HUD is
 * skipped until the config becomes loadable again.</p>
 */
final class HudData {
    private static final Logger LOGGER = LoggerFactory.getLogger(HudData.class);
    private static final Gson GSON = new GsonBuilder().create();
    private static final Identifier LAYOUT_ID = Identifier.of(Glungus.MOD_ID, "hud/layout");
    private static final Map<String, Identifier> POWER_IDS = Map.of(
            "ice", Identifier.of(Glungus.MOD_ID, "hud/ice"),
            "air", Identifier.of(Glungus.MOD_ID, "hud/air"),
            "fire", Identifier.of(Glungus.MOD_ID, "hud/fire"),
            "water", Identifier.of(Glungus.MOD_ID, "hud/water"),
            "ghost", Identifier.of(Glungus.MOD_ID, "hud/ghost"),
            "lightning", Identifier.of(Glungus.MOD_ID, "hud/lightning"),
            "nature", Identifier.of(Glungus.MOD_ID, "hud/nature"),
            "god", Identifier.of(Glungus.MOD_ID, "hud/god"),
            "dual", Identifier.of(Glungus.MOD_ID, "hud/dual")
    );

    private static final Map<String, PowerConfig> POWERS = new ConcurrentHashMap<>();
    private static volatile Layout layout;

    private HudData() {
    }

    static Layout layout() {
        Layout cached = layout;
        if (cached == null) {
            synchronized (HudData.class) {
                cached = layout;
                if (cached == null) {
                    cached = load(LAYOUT_ID, Layout.class);
                    layout = cached;
                }
            }
        }
        return cached;
    }

    static PowerConfig power(String name) {
        return POWERS.computeIfAbsent(name, HudData::loadPower);
    }

    static int promptTicks() {
        Layout loaded = layout();
        return loaded == null ? 0 : loaded.promptTicks;
    }

    private static PowerConfig loadPower(String name) {
        Identifier id = POWER_IDS.get(name);
        if (id == null) {
            throw new IllegalArgumentException("No HUD config for power: " + name);
        }
        return load(id, PowerConfig.class);
    }

    private static <T> T load(Identifier id, Class<T> type) {
        InputStream stream = open(id);
        if (stream == null) {
            return null;
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        } catch (IOException e) {
            LOGGER.error("Failed to read HUD config {}", id, e);
            return null;
        } catch (RuntimeException e) {
            LOGGER.error("Failed to parse HUD config {}", id, e);
            return null;
        }
    }

    private static InputStream open(Identifier id) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getResourceManager() != null) {
            Optional<Resource> resource = client.getResourceManager().getResource(id);
            if (resource.isPresent()) {
                try {
                    return resource.get().getInputStream();
                } catch (IOException e) {
                    LOGGER.warn("Could not read HUD config {} from resource manager, trying classpath", id, e);
                }
            }
        }

        InputStream classpath = HudData.class.getResourceAsStream(
                "/assets/" + id.getNamespace() + "/" + id.getPath() + ".json"
        );
        if (classpath != null) {
            LOGGER.warn("Loaded HUD config {} from classpath instead of the resource manager", id);
            return classpath;
        }

        LOGGER.error("HUD config {} not found in the resource manager or on the classpath", id);
        return null;
    }

    /** Parses a hex color string such as "0xFF8DEBFF" into an ARGB int. */
    static int color(String value) {
        if (value == null || value.isEmpty()) {
            return 0xFFFFFFFF;
        }
        String hex = value;
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        } else if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }
        try {
            return (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid HUD color '{}', using white fallback", value, e);
            return 0xFFFFFFFF;
        }
    }

    static final class Layout {
        Panel panel = new Panel();
        Header header = new Header();
        Card card = new Card();
        Footer footer = new Footer();
        Dual dual = new Dual();
        int accentHeight;
        int accentWidth;
        String readyText;
        int promptTicks;

        static final class Panel {
            int x;
            int y;
            int minWidth;
            int maxWidth;
            int height;
            int horizontalMargin;
        }

        static final class Header {
            int height;
            int paddingX;
            int titleY;
            int subtitleY;
            int dividerY;
        }

        static final class Card {
            int gap;
            int top;
            int height;
            int keyBoxX;
            int keyBoxY;
            int keyBoxMaxWidth;
            int keyBoxHeight;
            int keyTextY;
            int titleY;
            int subtitleY;
            int stateY;
            int barOffsetY;
            int barHeight;
            int barMinWidth;
        }

        static final class Footer {
            int y;
        }

        static final class Dual {
            int x;
            int y;
            int minWidth;
            int maxWidth;
            int height;
            int horizontalMargin;
            int headerColumnGap;
            int cardsTop;
            int cardHeight;
            int rowGap;
            int footerY;
        }
    }

    static final class PowerConfig {
        String title;
        String subtitle;
        FooterText footer = new FooterText();
        String accent;
        Palette palette = new Palette();
        List<Decoration> decorations = List.of();
        List<AbilityCard> cards = List.of();

        static final class FooterText {
            String hint;
            String prompt;
        }

        static final class Decoration {
            int right;
            int y;
            int width;
            int height;
            String color;
        }

        static final class Palette {
            String panelBackground;
            String headerBand;
            String divider;
            String cardBackground;
            String cardCooldownBackground;
            String keyBackground;
            String keyText;
            String barBackground;
            String primaryText;
            String subtitleText;
            String cardSubtitleText;
            String dimText;
            String readyText;
            String cooldownText;
            String primedText;
        }

        static final class AbilityCard {
            String key;
            String title;
            String subtitle;
            int maxCooldown;
            String primedText;
            String accent;
        }
    }
}
