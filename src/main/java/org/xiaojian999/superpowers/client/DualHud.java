package org.xiaojian999.superpowers.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import org.xiaojian999.superpowers.PowerStatusPayload;
import org.xiaojian999.superpowers.Glungus;

import static org.xiaojian999.superpowers.client.AbilityHudSupport.drawAbilityCard;
import static org.xiaojian999.superpowers.client.AbilityHudSupport.drawPanel;
import static org.xiaojian999.superpowers.client.AbilityHudSupport.shiftKeypad;

/**
 * The HUD shown when the player has both powerset slots equipped. It is a
 * single, dedicated panel — not two single-power HUDs — with a split header
 * naming each slot's power and one ability-card row per slot: the top row is
 * powerset slot 1 (keys 1-3), the bottom row is powerset slot 2 (keys 4-6).
 */
public final class DualHud {
    private static final Identifier HUD_ID = Identifier.of(Glungus.MOD_ID, "dual_hud");

    private DualHud() {
    }

    public static void initialize() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, HUD_ID, DualHud::render);
    }

    private static void render(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!HudState.isDual() || client.player == null || client.options.hudHidden) {
            return;
        }

        HudData.Layout layout = HudData.layout();
        HudData.PowerConfig shell = HudData.power("dual");
        HudState.Slot slot0 = HudState.slot(0);
        HudState.Slot slot1 = HudState.slot(1);
        HudData.PowerConfig config0 = HudData.power(slot0.power);
        HudData.PowerConfig config1 = HudData.power(slot1.power);
        if (layout == null || shell == null || config0 == null || config1 == null) {
            return;
        }
        HudData.Layout.Dual dual = layout.dual;
        HudData.Layout.Header header = layout.header;
        HudData.PowerConfig.Palette shellPalette = shell.palette;

        float inverseGuiScale = 1.0F / client.getWindow().getScaleFactor();
        drawContext.getMatrices().pushMatrix();
        drawContext.getMatrices().scale(inverseGuiScale);

        int screenWidth = client.getWindow().getFramebufferWidth();
        int panelWidth = Math.min(dual.maxWidth, Math.max(dual.minWidth, screenWidth - dual.horizontalMargin));
        int panelX = dual.x;
        int panelY = dual.y;

        drawPanel(drawContext, panelX, panelY, panelWidth, dual.height, HudData.color(shell.accent), layout, shell);

        // Split header: slot 1's power on the left, slot 2's power on the right.
        int headerInnerWidth = panelWidth - header.paddingX * 2;
        int columnWidth = (headerInnerWidth - dual.headerColumnGap) / 2;
        int column0X = panelX + header.paddingX;
        int column1X = column0X + columnWidth + dual.headerColumnGap;
        int titleY = panelY + header.titleY;
        int subtitleY = panelY + header.subtitleY;

        AbilityHudSupport.drawTextClipped(
                drawContext,
                client.textRenderer,
                config0.title,
                column0X,
                titleY,
                columnWidth,
                HudData.color(config0.palette.primaryText)
        );
        AbilityHudSupport.drawTextClipped(
                drawContext,
                client.textRenderer,
                config0.subtitle,
                column0X,
                subtitleY,
                columnWidth,
                HudData.color(config0.palette.subtitleText)
        );
        AbilityHudSupport.drawTextClipped(
                drawContext,
                client.textRenderer,
                config1.title,
                column1X,
                titleY,
                columnWidth,
                HudData.color(config1.palette.primaryText)
        );
        AbilityHudSupport.drawTextClipped(
                drawContext,
                client.textRenderer,
                config1.subtitle,
                column1X,
                subtitleY,
                columnWidth,
                HudData.color(config1.palette.subtitleText)
        );

        int row0Y = panelY + dual.cardsTop;
        int row1Y = row0Y + dual.cardHeight + dual.rowGap;
        renderCardRow(client, drawContext, layout, dual, config0, slot0, 0, panelX, panelWidth, row0Y);
        renderCardRow(client, drawContext, layout, dual, config1, slot1, 1, panelX, panelWidth, row1Y);

        int footerY = panelY + dual.footerY;
        boolean promptActive = slot0.ultimatePromptTicks > 0 || slot1.ultimatePromptTicks > 0;
        AbilityHudSupport.drawCenteredTextClipped(
                drawContext,
                client.textRenderer,
                promptActive ? shell.footer.prompt : shell.footer.hint,
                panelX + panelWidth / 2,
                footerY,
                panelWidth - header.paddingX * 2,
                HudData.color(promptActive ? shellPalette.primaryText : shellPalette.dimText)
        );

        drawContext.getMatrices().popMatrix();
    }

    private static void renderCardRow(
            MinecraftClient client,
            DrawContext drawContext,
            HudData.Layout layout,
            HudData.Layout.Dual dual,
            HudData.PowerConfig config,
            HudState.Slot slot,
            int slotIndex,
            int panelX,
            int panelWidth,
            int rowY
    ) {
        HudData.Layout.Header header = layout.header;
        int cardGap = layout.card.gap;
        int cardWidth = (panelWidth - header.paddingX * 2 - cardGap * 2) / 3;
        int firstCardX = panelX + header.paddingX;

        for (int cardIndex = 0; cardIndex < 3; cardIndex++) {
            HudData.PowerConfig.AbilityCard card = config.cards.get(cardIndex);
            drawAbilityCard(
                    drawContext,
                    client.textRenderer,
                    firstCardX + cardIndex * (cardWidth + cardGap),
                    rowY,
                    cardWidth,
                    dual.cardHeight,
                    layout,
                    card,
                    cooldownFor(slot.power, slot, cardIndex),
                    primedFor(slot.power, slot, cardIndex),
                    config,
                    primedOverrideFor(slot.power, slot, cardIndex),
                    shiftKeypad(card.key, slotIndex)
            );
        }
    }

    private static int cooldownFor(String power, HudState.Slot slot, int cardIndex) {
        return switch (cardIndex) {
            // Air flight, ghost form, and the flower trail are instant toggles with no cooldown bar.
            case 0 -> ("air".equals(power) || "ghost".equals(power) || "nature".equals(power) || "god".equals(power)) ? 0 : slot.beamCooldown;
            // Fire immunity is an instant toggle too.
            case 1 -> "fire".equals(power) ? 0 : slot.snowballCooldown;
            default -> slot.ultimateCooldown;
        };
    }

    private static boolean primedFor(String power, HudState.Slot slot, int cardIndex) {
        return switch (cardIndex) {
            case 0 -> switch (power) {
                case "air" -> (slot.flags & PowerStatusPayload.AIR_FLIGHT_ACTIVE) != 0;
                case "ghost" -> (slot.flags & PowerStatusPayload.GHOST_FORM_ACTIVE) != 0
                        || (slot.flags & PowerStatusPayload.GHOST_POSSESSING) != 0;
                case "nature" -> (slot.flags & PowerStatusPayload.NATURE_FLOWER_TRAIL_ACTIVE) != 0;
                case "god" -> (slot.flags & PowerStatusPayload.GOD_MODE_ACTIVE) != 0;
                default -> false;
            };
            case 1 -> switch (power) {
                case "ice" -> (slot.flags & PowerStatusPayload.SNOWBALL_PRIMED) != 0;
                case "fire" -> (slot.flags & PowerStatusPayload.FIRE_IMMUNE_ACTIVE) != 0;
                case "nature" -> (slot.flags & PowerStatusPayload.NATURE_VINE_RING_ACTIVE) != 0;
                default -> false;
            };
            default -> slot.ultimatePromptTicks > 0;
        };
    }

    private static String primedOverrideFor(String power, HudState.Slot slot, int cardIndex) {
        if (cardIndex == 0
                && "ghost".equals(power)
                && (slot.flags & PowerStatusPayload.GHOST_POSSESSING) != 0) {
            return "POSSESSING";
        }
        return null;
    }
}
