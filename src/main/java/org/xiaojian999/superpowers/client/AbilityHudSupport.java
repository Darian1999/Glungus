package org.xiaojian999.superpowers.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.regex.Pattern;

final class AbilityHudSupport {
    private AbilityHudSupport() {
    }

    /**
     * Truncates text so it never exceeds {@code maxWidth} pixels, appending an
     * ellipsis when anything had to be cut. Guarantees HUD text cannot spill
     * outside its card, column, or panel.
     */
    static String fitToWidth(TextRenderer textRenderer, String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int ellipsisWidth = textRenderer.getWidth(ellipsis);
        if (maxWidth <= ellipsisWidth) {
            return "";
        }
        return textRenderer.trimToWidth(text, maxWidth - ellipsisWidth) + ellipsis;
    }

    /** Draws left-aligned text with shadow, clipped to {@code maxWidth}. */
    static void drawTextClipped(
            DrawContext drawContext,
            TextRenderer textRenderer,
            String text,
            int x,
            int y,
            int maxWidth,
            int color
    ) {
        drawContext.drawTextWithShadow(
                textRenderer,
                fitToWidth(textRenderer, text, maxWidth),
                x,
                y,
                color
        );
    }

    /** Draws centered text with shadow, clipped so it stays within {@code maxWidth}. */
    static void drawCenteredTextClipped(
            DrawContext drawContext,
            TextRenderer textRenderer,
            String text,
            int centerX,
            int y,
            int maxWidth,
            int color
    ) {
        drawContext.drawCenteredTextWithShadow(
                textRenderer,
                fitToWidth(textRenderer, text, maxWidth),
                centerX,
                y,
                color
        );
    }

    static void drawPanel(
            DrawContext drawContext,
            int x,
            int y,
            int width,
            int height,
            int accent,
            HudData.Layout layout,
            HudData.PowerConfig config
    ) {
        HudData.PowerConfig.Palette palette = config.palette;
        HudData.Layout.Header header = layout.header;
        int accentHeight = layout.accentHeight;
        int accentWidth = layout.accentWidth;

        drawContext.fill(x, y, x + width, y + height, HudData.color(palette.panelBackground));
        drawContext.fill(x, y, x + width, y + accentHeight, accent);
        drawContext.fill(x, y + accentHeight, x + accentWidth, y + height, accent);
        drawContext.fill(x + accentWidth, y + accentHeight, x + width, y + header.height, HudData.color(palette.headerBand));
        drawContext.fill(
                x + header.paddingX,
                y + header.dividerY,
                x + width - header.paddingX,
                y + header.dividerY + 1,
                HudData.color(palette.divider)
        );
    }

    /** Single-slot panel width for the per-power HUDs. */
    static int panelWidth(MinecraftClient client, HudData.Layout layout) {
        HudData.Layout.Panel panel = layout.panel;
        int screenWidth = client.getWindow().getFramebufferWidth();
        return Math.min(panel.maxWidth, Math.max(panel.minWidth, screenWidth - panel.horizontalMargin));
    }

    /**
     * Shifts keypad references in a label by three keys per extra powerset slot,
     * so slot 2 ("KP1", "KEYPAD 1") reads "KP4", "KEYPAD 4" and so on. Slot 0
     * returns the text unchanged. The double-tap count in "KP3 x2" is untouched.
     */
    static String shiftKeypad(String text, int slotIndex) {
        if (slotIndex == 0 || text == null) {
            return text;
        }
        String shifted = Pattern.compile("(?i)KP(\\d)").matcher(text)
                .replaceAll(match -> "KP" + (Integer.parseInt(match.group(1)) + 3));
        return Pattern.compile("(?i)KEYPAD (\\d)").matcher(shifted)
                .replaceAll(match -> "KEYPAD " + (Integer.parseInt(match.group(1)) + 3));
    }

    static void drawAbilityCard(
            DrawContext drawContext,
            TextRenderer textRenderer,
            int x,
            int y,
            int width,
            int height,
            HudData.Layout layout,
            HudData.PowerConfig.AbilityCard card,
            int cooldown,
            boolean primed,
            HudData.PowerConfig config
    ) {
        drawAbilityCard(
                drawContext,
                textRenderer,
                x,
                y,
                width,
                height,
                layout,
                card,
                cooldown,
                primed,
                config,
                null,
                null
        );
    }

    static void drawAbilityCard(
            DrawContext drawContext,
            TextRenderer textRenderer,
            int x,
            int y,
            int width,
            int height,
            HudData.Layout layout,
            HudData.PowerConfig.AbilityCard card,
            int cooldown,
            boolean primed,
            HudData.PowerConfig config,
            String primedTextOverride
    ) {
        drawAbilityCard(
                drawContext,
                textRenderer,
                x,
                y,
                width,
                height,
                layout,
                card,
                cooldown,
                primed,
                config,
                primedTextOverride,
                null
        );
    }

    static void drawAbilityCard(
            DrawContext drawContext,
            TextRenderer textRenderer,
            int x,
            int y,
            int width,
            int height,
            HudData.Layout layout,
            HudData.PowerConfig.AbilityCard card,
            int cooldown,
            boolean primed,
            HudData.PowerConfig config,
            String primedTextOverride,
            String keyOverride
    ) {
        HudData.PowerConfig.Palette palette = config.palette;
        HudData.Layout.Card cardLayout = layout.card;
        boolean coolingDown = cooldown > 0;
        int background = coolingDown
                ? HudData.color(palette.cardCooldownBackground)
                : HudData.color(palette.cardBackground);
        drawContext.fill(x, y, x + width, y + height, background);
        drawContext.fill(x, y, x + width, y + layout.accentHeight, HudData.color(card.accent));
        drawContext.fill(x, y + layout.accentHeight, x + layout.accentWidth, y + height, HudData.color(card.accent));

        String key = keyOverride != null ? keyOverride : card.key;
        int keyWidth = Math.min(width - cardLayout.keyBoxX * 2, cardLayout.keyBoxMaxWidth);
        drawContext.fill(
                x + cardLayout.keyBoxX,
                y + cardLayout.keyBoxY,
                x + cardLayout.keyBoxX + keyWidth,
                y + cardLayout.keyBoxY + cardLayout.keyBoxHeight,
                HudData.color(palette.keyBackground)
        );
        drawContext.drawCenteredTextWithShadow(
                textRenderer,
                fitToWidth(textRenderer, key, keyWidth),
                x + cardLayout.keyBoxX + keyWidth / 2,
                y + cardLayout.keyTextY,
                HudData.color(palette.keyText)
        );

        int textMaxWidth = width - cardLayout.keyBoxX * 2;
        drawContext.drawTextWithShadow(
                textRenderer,
                fitToWidth(textRenderer, card.title, textMaxWidth),
                x + cardLayout.keyBoxX,
                y + cardLayout.titleY,
                HudData.color(palette.primaryText)
        );
        drawContext.drawTextWithShadow(
                textRenderer,
                fitToWidth(textRenderer, card.subtitle, textMaxWidth),
                x + cardLayout.keyBoxX,
                y + cardLayout.subtitleY,
                HudData.color(palette.cardSubtitleText)
        );

        String state;
        int stateColor;
        if (coolingDown) {
            state = formatCooldown(cooldown);
            stateColor = HudData.color(palette.cooldownText);
        } else if (primed) {
            state = primedTextOverride != null ? primedTextOverride : card.primedText;
            stateColor = HudData.color(palette.primedText);
        } else {
            state = layout.readyText;
            stateColor = HudData.color(palette.readyText);
        }
        drawContext.drawTextWithShadow(
                textRenderer,
                fitToWidth(textRenderer, state, textMaxWidth),
                x + cardLayout.keyBoxX,
                y + cardLayout.stateY,
                stateColor
        );

        int barX = x + cardLayout.keyBoxX;
        int barY = y + height - cardLayout.barOffsetY;
        int barWidth = Math.max(cardLayout.barMinWidth, width - cardLayout.keyBoxX * 2);
        drawContext.fill(barX, barY, barX + barWidth, barY + cardLayout.barHeight, HudData.color(palette.barBackground));
        float progress = coolingDown ? (float) cooldown / card.maxCooldown : 1.0F;
        int progressWidth = Math.max(1, Math.round(barWidth * Math.min(1.0F, progress)));
        drawContext.fill(barX, barY, barX + progressWidth, barY + cardLayout.barHeight, stateColor);
    }

    static void drawDecorations(
            DrawContext drawContext,
            int panelX,
            int panelY,
            int panelWidth,
            HudData.PowerConfig config
    ) {
        for (HudData.PowerConfig.Decoration decoration : config.decorations) {
            int x = panelX + panelWidth - decoration.right - decoration.width;
            drawContext.fill(
                    x,
                    panelY + decoration.y,
                    x + decoration.width,
                    panelY + decoration.y + decoration.height,
                    HudData.color(decoration.color)
            );
        }
    }

    static String formatCooldown(int ticks) {
        if (ticks < 20) {
            return String.format(Locale.ROOT, "%.1fs", ticks / 20.0F);
        }
        return ((ticks + 19) / 20) + "s";
    }
}
